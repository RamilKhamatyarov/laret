package io.github.laretframework.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class ParallelTask(val command: String, val args: List<String>)

data class ParallelResult(
    val task: ParallelTask,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
)

object ParallelDispatcher {

    /**
     * Run each task as a child process, at most [maxJobs] at a time.
     *
     * Tasks are claimed in submission order and results are returned in that
     * same order, so `maxJobs = 1` is sequential execution of the list as given.
     */
    suspend fun execute(
        tasks: List<ParallelTask>,
        maxJobs: Int,
        onOutput: (task: ParallelTask, line: String, isStderr: Boolean) -> Unit,
    ): List<ParallelResult> {
        require(maxJobs in 1..MAX_PROCESS_JOBS) { "maxJobs must be in range 1..$MAX_PROCESS_JOBS" }
        return pooled(tasks.size, maxJobs, Dispatchers.IO) { index ->
            runTask(tasks[index], onOutput)
        }
    }

    /**
     * Run each block in-process, without spawning anything.
     *
     * The process path is bounded at [MAX_PROCESS_JOBS] because each unit costs
     * an operating-system process; a coroutine costs almost nothing, so this
     * path takes [maxJobs] `0` to mean "all at once, one coroutine per block".
     * A positive [maxJobs] reuses the same ordered worker pool as [execute].
     * Results are returned in submission order either way.
     *
     * Blocks run on [Dispatchers.Default], which is sized for CPU-bound work.
     * A block that blocks a thread should wrap itself in `withContext(IO)`.
     */
    suspend fun <T : Any> executeBlocks(blocks: List<suspend () -> T>, maxJobs: Int = UNBOUNDED): List<T> {
        require(maxJobs >= UNBOUNDED) { "maxJobs must not be negative" }
        if (blocks.isEmpty()) return emptyList()

        if (maxJobs == UNBOUNDED) {
            return coroutineScope {
                blocks.map { block -> async(Dispatchers.Default) { block() } }.awaitAll()
            }
        }
        return pooled(blocks.size, maxJobs, Dispatchers.Default) { index -> blocks[index]() }
    }

    /**
     * A worker pool rather than one coroutine per unit of work.
     *
     * Bounding concurrency needs the limit to cover the whole unit, including
     * any blocking wait inside it, which a limitedParallelism dispatcher does
     * not do. Workers also claim work from a shared cursor, so units start in
     * the order they were given; a semaphore only guarantees mutual exclusion,
     * and with one permit the second unit could still win the race for it.
     */
    private suspend fun <T : Any> pooled(
        size: Int,
        maxJobs: Int,
        dispatcher: CoroutineDispatcher,
        body: suspend (Int) -> T,
    ): List<T> {
        if (size == 0) return emptyList()

        val results = MutableList<T?>(size) { null }
        val cursor = AtomicInteger(0)

        coroutineScope {
            repeat(minOf(maxJobs, size)) {
                launch(dispatcher) {
                    while (true) {
                        val index = cursor.getAndIncrement()
                        if (index >= size) break
                        results[index] = body(index)
                    }
                }
            }
        }

        return results.map { checkNotNull(it) { "Task result was not produced" } }
    }

    private suspend fun runTask(
        task: ParallelTask,
        onOutput: (task: ParallelTask, line: String, isStderr: Boolean) -> Unit,
    ): ParallelResult = coroutineScope {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()

        val process = try {
            ProcessBuilder(listOf(task.command) + task.args)
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            return@coroutineScope ParallelResult(
                task = task,
                exitCode = 1,
                stdout = emptyList(),
                stderr = listOf(e.message ?: "Failed to start process"),
            )
        }

        val stdoutReader = async(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    stdout += line
                    onOutput(task, line, false)
                }
            }
        }
        val stderrReader = async(Dispatchers.IO) {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    stderr += line
                    onOutput(task, line, true)
                }
            }
        }

        val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
        stdoutReader.await()
        stderrReader.await()

        ParallelResult(task, exitCode, stdout, stderr)
    }

    /** Passed as `maxJobs` to [executeBlocks] to dispatch every block at once. */
    const val UNBOUNDED = 0

    /** Upper bound on concurrent child processes, one per operating-system process. */
    const val MAX_PROCESS_JOBS = 16
}
