package com.rkhamatyarov

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.Middleware
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiddlewareHooksIntegrationTest {
    private lateinit var app: CliApp
    private lateinit var buf: ByteArrayOutputStream
    private val testDir = File("test_middleware_tmp")
    private val originalOut = System.out
    private val originalErr = System.err

    @BeforeEach
    fun setup() {
        testDir.mkdirs()
        buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        System.setOut(ps)
        System.setErr(ps)
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        testDir.deleteRecursively()
    }

    private fun output() = buf.toString()
    private fun clear() = buf.reset()

    @Test
    fun `AuthMiddleware blocks unauthenticated file create command`() {
        var authenticated = false
        val authMiddleware = object : Middleware {
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                if (authenticated) {
                    next()
                } else {
                    println("Unauthorized: authentication required")
                }
            }
        }

        app = cli(name = "test", version = "1.0") {
            use(authMiddleware)
            group("file") {
                command("create") {
                    argument("path")
                    action {
                        println("Creating file...")
                    }
                }
            }
        }

        app.runForTest(arrayOf("file", "create", "some.txt"))
        val out = output()
        assertTrue(out.contains("Unauthorized: authentication required"))
        assertFalse(out.contains("Creating file"))
    }

    @Test
    fun `LoggingMiddleware records command execution with timing`() {
        val logged = mutableListOf<String>()
        val loggingMiddleware = object : Middleware {
            override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                logged.add("START: ${ctx.command.name}")
                val start = System.currentTimeMillis()
                next()
                val elapsed = System.currentTimeMillis() - start
                logged.add("END: ${ctx.command.name} took ${elapsed}ms")
            }
        }

        app = cli(name = "test", version = "1.0") {
            use(loggingMiddleware)
            group("dir") {
                command("list") {
                    action { Thread.sleep(10) }
                }
            }
        }

        app.runForTest(arrayOf("dir", "list"))
        assertTrue(logged.any { it.startsWith("START: list") })
        assertTrue(logged.any { it.startsWith("END: list took") })
    }

    @Test
    fun `preExecute validates file path before spinner starts`() {
        var preExecuted = false
        var actionExecuted = false
        val validPath = File(testDir, "good.txt").absolutePath

        val appValid = cli(name = "test", version = "1.0") {
            group("file") {
                command("create") {
                    argument("path")
                    preExecute = { ctx ->
                        preExecuted = true
                        val path = ctx.argument("path")
                        require(path.isNotBlank()) { "Path cannot be empty" }
                    }
                    action { ctx ->
                        actionExecuted = true
                        File(ctx.argument("path")).writeText("data")
                        val spinner = ctx.spinner("Creating")
                        spinner.tick()
                        spinner.finish()
                    }
                }
            }
        }

        clear()
        preExecuted = false
        actionExecuted = false
        appValid.runForTest(arrayOf("file", "create", validPath))
        assertTrue(preExecuted, "preExecute should be called for valid path")
        assertTrue(actionExecuted, "Action should execute for valid path")
        assertTrue(File(validPath).exists(), "File should be created for valid path")

        preExecuted = false
        actionExecuted = false

        val appInvalid = cli(name = "test", version = "1.0") {
            group("file") {
                command("create") {
                    argument("path")
                    preExecute = { ctx ->
                        preExecuted = true
                        val path = ctx.argument("path")
                        require(path.isNotBlank()) { "Path cannot be empty" }
                    }
                    action { ctx ->
                        actionExecuted = true
                        File(ctx.argument("path")).writeText("data")
                    }
                }
            }
        }

        val invalidPath = " "
        appInvalid.runForTest(arrayOf("file", "create", invalidPath))

        assertTrue(preExecuted, "preExecute should be called even for invalid path")
        assertFalse(actionExecuted, "Action should NOT be executed when preExecute fails")
        assertFalse(File(invalidPath).exists(), "No file should be created with invalid name")
    }

    @Test
    fun `postExecute logs result after progressBar finishes`() {
        var postExecuted = false
        var entryCount = 0

        app = cli(name = "test", version = "1.0") {
            group("dir") {
                command("list") {
                    argument("path", optional = true, default = ".")
                    postExecute = { ctx ->
                        val files = File(ctx.argument("path")).listFiles() ?: emptyArray()
                        entryCount = files.size
                        postExecuted = true
                    }
                    action { ctx ->
                        val bar = ctx.progressBar(10)
                        repeat(10) {
                            bar.increment()
                            Thread.sleep(1)
                        }
                        bar.finish()
                    }
                }
            }
        }

        File(testDir, "a.txt").createNewFile()
        File(testDir, "b.txt").createNewFile()
        app.runForTest(arrayOf("dir", "list", testDir.absolutePath))

        assertTrue(postExecuted)
        assertEquals(2, entryCount)
    }

    @Test
    fun `onError handles file not found with graceful message`() {
        var errorMessage = ""

        app = cli(name = "test", version = "1.0") {
            group("file") {
                command("read") {
                    argument("path")
                    onError = { ctx, e ->
                        errorMessage = "Failed to read ${ctx.argument("path")}: ${e.message}"
                        println(errorMessage)
                    }
                    action { ctx ->
                        val content = File(ctx.argument("path")).readText()
                        println(content)
                    }
                }
            }
        }

        val missingPath = File(testDir, "missing.txt").absolutePath
        app.runForTest(arrayOf("file", "read", missingPath))

        val out = output()
        assertTrue(out.contains("Failed to read $missingPath"))
        assertTrue(errorMessage.contains("Failed to read"))
    }
}
