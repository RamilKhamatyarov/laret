package com.rkhamatyarov.laret.watch

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class LiveWatchSessionTest {

    private val ktMatcher = GlobMatcher(listOf("*.kt"))

    private fun session(
        runner: suspend (Int) -> Int,
        matcher: GlobMatcher = ktMatcher,
        debounce: Long = 0,
        maxRestarts: Int = 0,
        maxConsecutiveFailures: Int = 0,
        runOnStart: Boolean = true,
    ) = LiveWatchSession(
        matcher = matcher,
        runner = runner,
        debounceMillis = debounce,
        maxRestarts = maxRestarts,
        maxConsecutiveFailures = maxConsecutiveFailures,
        runOnStart = runOnStart,
    )

    @Test
    fun `runs once on start and stops at the restart cap`() = runTest {
        val runs = AtomicInteger(0)
        val summary = session(runner = {
            runs.incrementAndGet()
            0
        }, maxRestarts = 1).run(emptyFlow())

        assertEquals(1, runs.get())
        assertEquals(1, summary.restarts)
        assertEquals(WatchStopReason.MAX_RESTARTS, summary.stopReason)
    }

    @Test
    fun `a matching change triggers a run`() = runTest {
        val runs = AtomicInteger(0)
        val summary = session(runner = {
            runs.incrementAndGet()
            0
        }, runOnStart = false)
            .run(flowOf(Path.of("A.kt")))

        assertEquals(1, runs.get())
        assertEquals(WatchStopReason.SOURCE_CLOSED, summary.stopReason)
    }

    @Test
    fun `a non-matching change is ignored`() = runTest {
        val runs = AtomicInteger(0)
        val summary = session(runner = {
            runs.incrementAndGet()
            0
        }, runOnStart = false)
            .run(flowOf(Path.of("notes.txt")))

        assertEquals(0, runs.get())
        assertEquals(WatchStopReason.SOURCE_CLOSED, summary.stopReason)
    }

    @Test
    fun `stops after consecutive failures`() = runTest {
        val summary = session(runner = { 1 }, maxConsecutiveFailures = 2).run(flowOf(Path.of("A.kt")))

        assertEquals(WatchStopReason.MAX_CONSECUTIVE_FAILURES, summary.stopReason)
        assertEquals(2, summary.restarts)
        assertEquals(1, summary.lastExitCode)
    }

    @Test
    fun `a success resets the consecutive-failure counter`() = runTest {
        val exits = ArrayDeque(listOf(1, 0, 1))
        val changes = flow {
            emit(Path.of("A.kt"))
            delay(50.milliseconds)
            emit(Path.of("B.kt"))
        }
        val summary = session(
            runner = { exits.removeFirst() },
            debounce = 10,
            maxConsecutiveFailures = 2,
            runOnStart = true,
        ).run(changes)

        assertEquals(WatchStopReason.SOURCE_CLOSED, summary.stopReason)
        assertEquals(3, summary.restarts)
    }

    @Test
    fun `rapid changes are debounced into one run`() = runTest {
        val runs = AtomicInteger(0)
        val changes = flow {
            emit(Path.of("A.kt"))
            delay(10.milliseconds)
            emit(Path.of("B.kt"))
        }
        session(runner = {
            runs.incrementAndGet()
            0
        }, debounce = 100, runOnStart = false).run(changes)

        assertEquals(1, runs.get())
    }
}
