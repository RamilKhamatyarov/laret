package com.rkhamatyarov.laret.config

import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.ui.InteractivePrompt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestCliTest {
    @Test
    fun `captures stdout stderr and exit code`() {
        val result = TestCli(testApp()).run("test", "streams")

        result
            .assertSuccess()
            .assertStdoutContains("standard output")
            .assertStderrContains("diagnostic output")
            .assertOutputContains("standard output")
    }

    @Test
    fun `simulates interactive stdin`() {
        val result = TestCli(testApp()).run(args = listOf("test", "prompt"), stdin = "Ada\n")

        result
            .assertSuccess()
            .assertStdoutContains("Hello Ada")
            .assertStderrContains("Name")
    }

    @Test
    fun `restores system streams after execution`() {
        val previousIn = System.`in`
        val previousOut = System.out
        val previousErr = System.err

        TestCli(testApp()).run("test", "streams")

        assertSame(previousIn, System.`in`)
        assertSame(previousOut, System.out)
        assertSame(previousErr, System.err)
    }

    @Test
    fun `normalizes ansi while preserving raw output`() {
        val result = TestCli(testApp()).run("test", "ansi")

        result
            .assertStdoutEquals("red\n")
            .assertStdoutRawContains("\u001B[31m")
    }

    @Test
    fun `supports structured json assertions`() {
        val result = TestCli(testApp()).run("test", "json")

        result
            .assertStdoutJson("""{"status":"ok","items":[{"id":7}]}""")
            .assertJsonPath("$.items[0].id", 7)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `suspending execution uses caller test scheduler`() = runTest {
        val result = TestCli(testApp()).runSuspending("test", "delayed")

        result.assertSuccess().assertStdoutContains("done")
        assertEquals(1_000, testScheduler.currentTime)
    }

    private fun testApp() = cli(name = "test") {
        group("test") {
            command("streams") {
                action {
                    println("standard output")
                    System.err.println("diagnostic output")
                }
            }
            command("prompt") {
                action {
                    val name = InteractivePrompt().text("Name")
                    println("Hello $name")
                }
            }
            command("ansi") {
                action { println("\u001B[31mred\u001B[0m") }
            }
            command("json") {
                action { println("""{"status":"ok","items":[{"id":7}]}""") }
            }
            command("delayed") {
                preExecute = { delay(1_000) }
                action { println("done") }
            }
        }
    }
}
