package com.rkhamatyarov.laret.core

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class ParallelDispatcherTest {
    @Nested
    inner class HappyPath {
        @Test
        fun executesTwoTasksConcurrently() = runTest {
            val tasks = listOf(command("hello", delayMillis = 900), command("world", delayMillis = 900))
            val outputs = mutableListOf<String>()

            val elapsed = measureTimeMillis {
                val results = ParallelDispatcher.execute(tasks, maxJobs = 2) { _, line, _ ->
                    outputs += line.trim()
                }

                assertThat(results).hasSize(2)
                assertThat(results.map { it.exitCode }).containsOnly(0)
                assertThat(outputs).containsExactlyInAnyOrder("hello", "world")
            }
            assertThat(elapsed).isLessThan(1_600)
        }

        @Test
        fun singleTaskBehavesLikeSequentialExecution() = runTest {
            val task = command("single")

            val results = ParallelDispatcher.execute(listOf(task), maxJobs = 4) { _, _, _ -> }

            assertThat(results).hasSize(1)
            assertThat(results.single().stdout.map { it.trim() }).containsExactly("single")
            assertThat(results.single().exitCode).isZero()
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun emptyTaskListReturnsEmptyResults() = runTest {
            val results = ParallelDispatcher.execute(emptyList(), maxJobs = 4) { _, _, _ -> }

            assertThat(results).isEmpty()
        }

        @Test
        fun jobsOneForcesSequentialExecution() = runTest {
            val tasks = listOf(command("first", delayMillis = 300), command("second", delayMillis = 300))
            val outputOrder = mutableListOf<String>()

            ParallelDispatcher.execute(tasks, maxJobs = 1) { _, line, _ ->
                outputOrder += line.trim()
            }

            assertThat(outputOrder).containsExactly("first", "second")
        }

        @Test
        fun separatesStdoutAndStderrPerTask() = runTest {
            val task = stdoutAndStderrCommand()

            val result = ParallelDispatcher.execute(listOf(task), maxJobs = 1) { _, _, _ -> }.single()

            assertThat(result.stdout.map { it.trim() }).containsExactly("out")
            assertThat(result.stderr.map { it.trim() }).containsExactly("err")
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun aggregatesExitCodesCorrectly() = runTest {
            val tasks = listOf(command("ok"), failingCommand())

            val results = ParallelDispatcher.execute(tasks, maxJobs = 2) { _, _, _ -> }

            assertThat(results.map { it.exitCode }).containsExactly(0, 1)
            assertThat(results.first { it.exitCode == 1 }.stderr.map { it.trim() }).contains("failed")
        }
    }

    private fun command(output: String, delayMillis: Int = 0): ParallelTask =
        shellTask(script("sleep", delayMillis, "stdout", output, "exit", 0))

    private fun failingCommand(): ParallelTask = shellTask(script("stderr", "failed", "exit", 1))

    private fun stdoutAndStderrCommand(): ParallelTask = shellTask(script("stdout", "out", "stderr", "err", "exit", 0))

    private fun shellTask(script: String): ParallelTask = if (isWindows()) {
        ParallelTask("cmd", listOf("/c", script))
    } else {
        ParallelTask("sh", listOf("-c", script))
    }

    private fun script(vararg tokens: Any): String {
        val parts = tokens.toList()
        return if (isWindows()) {
            parts.chunked(2).joinToString(" & ") { (operation, value) ->
                when (operation) {
                    "sleep" -> "ping -n ${value.toString().toInt() / 1000 + 2} 127.0.0.1 > nul"
                    "stdout" -> "echo $value"
                    "stderr" -> "echo $value 1>&2"
                    "exit" -> "exit /b $value"
                    else -> error("Unknown operation $operation")
                }
            }
        } else {
            parts.chunked(2).joinToString("; ") { (operation, value) ->
                when (operation) {
                    "sleep" -> "sleep ${value.toString().toDouble() / 1000.0}"
                    "stdout" -> "printf '%s\\n' '$value'"
                    "stderr" -> "printf '%s\\n' '$value' >&2"
                    "exit" -> "exit $value"
                    else -> error("Unknown operation $operation")
                }
            }
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
}
