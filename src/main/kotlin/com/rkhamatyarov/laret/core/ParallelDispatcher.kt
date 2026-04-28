package com.rkhamatyarov.laret.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ParallelTask(val command: String, val args: List<String>)

data class ParallelResult(
    val task: ParallelTask,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
)

object ParallelDispatcher {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun execute(
        tasks: List<ParallelTask>,
        maxJobs: Int,
        onOutput: (task: ParallelTask, line: String, isStderr: Boolean) -> Unit,
    ): List<ParallelResult> {
        require(maxJobs in 1..16) { "maxJobs must be in range 1..16" }
        if (tasks.isEmpty()) return emptyList()

        val dispatcher = Dispatchers.IO.limitedParallelism(maxJobs)
        return withContext(dispatcher) {
            tasks
                .map { task ->
                    async {
                        runTask(task, onOutput)
                    }
                }
                .awaitAll()
        }
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
}
