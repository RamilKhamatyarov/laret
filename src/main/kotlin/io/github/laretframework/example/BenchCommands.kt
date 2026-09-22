package io.github.laretframework.example

import io.github.laretframework.core.CommandPipeline
import io.github.laretframework.core.ParallelDispatcher
import io.github.laretframework.dsl.GroupBuilder
import io.github.laretframework.watch.GlobMatcher
import io.github.laretframework.watch.LiveWatchSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Payloads for the concurrency benchmark suite. See the
 * `concurrency-benchmark-suite` ADR.
 *
 * Every command is [hidden], so none of this appears in help, completions or
 * generated docs. They are real commands all the same: they run through
 * `CommandRunner`, the middleware chain and the per-run `CancellationScope`,
 * which is precisely what the benchmark is measuring. A harness that called
 * the primitives directly would measure the primitives, not the framework.
 *
 * Each command prints one machine-readable summary line of `key=value` pairs,
 * identical in shape across all five benchmark targets.
 */
object BenchCommands {

    fun register(group: GroupBuilder) {
        fanout(group)
        storm(group)
        pipeline(group)
        cancel(group)
        emit(group)
        filter(group)
        count(group)
    }

    /** Scenario A: fan out N independent units of work, join them, aggregate. */
    private fun fanout(group: GroupBuilder) {
        group.command(name = "fanout", description = "Dispatch N concurrent tasks and aggregate their results") {
            hidden()
            option("t", "tasks", "How many tasks to dispatch", "1000", true)
            option("j", "jobs", "Concurrency limit; 0 dispatches all at once", "0", true)

            action { ctx ->
                val tasks = ctx.optionInt("tasks").coerceAtLeast(0)
                val jobs = ctx.optionInt("jobs").coerceAtLeast(0)

                val blocks = (0 until tasks).map { index ->
                    suspend {
                        yield()
                        index.toLong() + 1L
                    }
                }
                val results = runBlocking { ParallelDispatcher.executeBlocks(blocks, maxJobs = jobs) }

                println("fanout tasks=$tasks jobs=$jobs completed=${results.size} sum=${results.sum()}")
            }
        }
    }

    /** Scenario B: flood the debouncer and verify it coalesces to a single run. */
    private fun storm(group: GroupBuilder) {
        group.command(name = "storm", description = "Fire an event storm and count the runs it coalesces into") {
            hidden()
            option("e", "events", "How many events to fire", "10000", true)
            option("w", "window", "Milliseconds to spread the events over", "50", true)
            option("d", "debounce", "Debounce window in milliseconds", "150", true)

            action { ctx ->
                val events = ctx.optionInt("events").coerceAtLeast(0)
                val windowMillis = ctx.optionInt("window").toLong().coerceAtLeast(0)
                val debounceMillis = ctx.optionInt("debounce").toLong().coerceAtLeast(1)

                val runs = AtomicInteger(0)
                val emitted = AtomicLong(0)

                val session = LiveWatchSession(
                    matcher = GlobMatcher(listOf("*.txt")),
                    runner = {
                        runs.incrementAndGet()
                        0
                    },
                    debounceMillis = debounceMillis,
                    runOnStart = false,
                )
                val summary = runBlocking {
                    session.run(stormFlow(events, windowMillis, debounceMillis, emitted))
                }

                println(
                    "storm events=${emitted.get()} window=$windowMillis debounce=$debounceMillis " +
                        "runs=${runs.get()} restarts=${summary.restarts}",
                )
            }
        }
    }

    /**
     * Emits [events] paths as fast as the collector accepts them, then stays
     * open long enough for the debounce to fire, so the coalesced run is
     * observed rather than being forced out by the flow completing.
     */
    private fun stormFlow(events: Int, windowMillis: Long, debounceMillis: Long, emitted: AtomicLong): Flow<Path> =
        flow {
            val started = System.nanoTime()
            repeat(events) { index ->
                emit(Path.of("storm-$index.txt"))
                emitted.incrementAndGet()
            }
            val spentMillis = (System.nanoTime() - started) / 1_000_000
            if (spentMillis < windowMillis) {
                delay(
                    windowMillis.toDuration(
                        DurationUnit.MILLISECONDS,
                    ) - spentMillis.toDuration(DurationUnit.MILLISECONDS),
                )
            }
            delay(debounceMillis.toDuration(DurationUnit.MILLISECONDS) * 2 + QUIET_TAIL_MILLIS)
        }

    /** Scenario C: three concurrent stages over a bounded pipe. */
    private fun pipeline(group: GroupBuilder) {
        group.command(name = "pipeline", description = "Run a three-stage streaming pipeline over N lines") {
            hidden()
            option("l", "lines", "How many lines stage one emits", "100000", true)
            option("p", "pattern", "Substring stage two keeps", "7", true)
            option("b", "buffered", "Use the sequential buffered pipeline instead", "", false)

            action { ctx ->
                val app = ctx.app ?: return@action
                val lines = ctx.optionInt("lines").coerceAtLeast(0)
                val pattern = ctx.option("pattern")

                val stages = listOf(
                    arrayOf("bench", "emit", "--count", lines.toString()),
                    arrayOf("bench", "filter", "--pattern", pattern),
                    arrayOf("bench", "count"),
                )
                val pipeline = CommandPipeline(app)

                if (ctx.optionBool("buffered")) {
                    val result = pipeline.executeResult(stages)
                    println(
                        "pipeline lines=$lines mode=buffered stages=${result.completedStages} kept=${result.output.trim()}",
                    )
                    return@action
                }

                val sink = java.io.ByteArrayOutputStream()
                val result = pipeline.executeStreamingResult(stages, sink)
                println(
                    "pipeline lines=$lines mode=streaming stages=${result.completedStages} " +
                        "kept=${sink.toString(Charsets.UTF_8).trim()}",
                )
            }
        }
    }

    /** Scenario D: many workers, cleanup hooks, and a signal arriving mid-flight. */
    private fun cancel(group: GroupBuilder) {
        group.command(name = "cancel", description = "Spawn N workers and wait to be cancelled") {
            hidden()
            option("w", "workers", "How many background workers to spawn", "500", true)
            option("m", "marker", "File recording cleanup order, one index per line", "", true)
            option("s", "seconds", "Give up after this long if no signal arrives", "30", true)

            action { ctx ->
                val workers = ctx.optionInt("workers").coerceAtLeast(0)
                val marker = ctx.option("marker")
                val timeoutSeconds = ctx.optionInt("seconds").coerceAtLeast(1)

                val ticks = AtomicLong(0)
                val cleanupOrder = java.util.Collections.synchronizedList(mutableListOf<Int>())
                val allCleaned = CountDownLatch(1)

                val scope = CoroutineScope(ctx.scope.coroutineContext)
                repeat(workers) { index ->
                    scope.launch {
                        while (true) {
                            ticks.incrementAndGet()
                            yield()
                        }
                    }
                    ctx.onShutdown {
                        cleanupOrder += index
                        if (cleanupOrder.size == workers) {
                            if (marker.isNotBlank()) {
                                File(marker).writeText(cleanupOrder.joinToString("\n", postfix = "\n"))
                            }
                            allCleaned.countDown()
                        }
                    }
                }

                println("cancel workers=$workers pid=${ProcessHandle.current().pid()} ready=true")
                System.out.flush()

                allCleaned.await(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                System.err.println("cancel ticks=${ticks.get()} cleaned=${cleanupOrder.size}")
            }
        }
    }

    /** Pipeline stage one. */
    private fun emit(group: GroupBuilder) {
        group.command(name = "emit", description = "Emit N numbered lines on stdout") {
            hidden()
            option("c", "count", "How many lines", "1000", true)
            action { ctx ->
                repeat(ctx.optionInt("count").coerceAtLeast(0)) { index -> println("line-$index") }
            }
        }
    }

    /** Pipeline stage two. */
    private fun filter(group: GroupBuilder) {
        group.command(name = "filter", description = "Pass through stdin lines containing a substring") {
            hidden()
            option("p", "pattern", "Substring to keep", "", true)
            action { ctx ->
                val needle = ctx.option("pattern")
                BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).forEachLine { line ->
                    if (needle.isEmpty() || needle in line) println(line)
                }
            }
        }
    }

    /** Pipeline stage three. */
    private fun count(group: GroupBuilder) {
        group.command(name = "count", description = "Count the lines arriving on stdin") {
            hidden()
            action {
                var seen = 0
                BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).forEachLine { seen++ }
                println(seen)
            }
        }
    }

    /** Quiet time after the storm, so the debounce fires before the flow closes. */
    private val QUIET_TAIL_MILLIS = 100.toDuration(DurationUnit.MILLISECONDS)
}
