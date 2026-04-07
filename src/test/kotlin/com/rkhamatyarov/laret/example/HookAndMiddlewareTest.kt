package com.rkhamatyarov.laret.example

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
import kotlin.test.assertTrue

class HookAndMiddlewareTest {

    private lateinit var outStream: ByteArrayOutputStream
    private lateinit var errStream: ByteArrayOutputStream
    private lateinit var originalOut: PrintStream
    private lateinit var originalErr: PrintStream

    @BeforeEach
    fun redirectStreams() {
        outStream = ByteArrayOutputStream()
        errStream = ByteArrayOutputStream()
        originalOut = System.out
        originalErr = System.err
        System.setOut(PrintStream(outStream))
        System.setErr(PrintStream(errStream))
    }

    @AfterEach
    fun restoreStreams() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun `postExecute hook logs completion message to stderr`() {
        val testFile = File("/tmp/postexec_test_${System.currentTimeMillis()}.txt")
        testFile.deleteOnExit()

        val app = buildApp()
        val exitCode = app.runForTest(arrayOf("file", "create", testFile.absolutePath, "-c", "post test"))

        val stderr = errStream.toString()

        assertEquals(0, exitCode, "Command should succeed")
        assertTrue(stderr.contains("File operation completed"), "postExecute message not found in stderr")
    }

    @Test
    fun `onError hook handles duplicate file gracefully`() {
        val testFile = File("/tmp/error_test_${System.currentTimeMillis()}.txt")
        testFile.writeText("original")
        testFile.deleteOnExit()

        val app = buildApp()
        val exitCode = app.runForTest(arrayOf("file", "create", testFile.absolutePath, "-c", "overwrite"))

        val stderr = errStream.toString()
        val stdout = outStream.toString()

        assertEquals(1, exitCode, "Command should fail when file exists without --force")
        assertTrue(stderr.contains("File already exists"), "Error message not found in stderr")
        assertTrue(!stdout.contains("File already exists"), "Error message should not be on stdout")
    }

    @Test
    fun `global middleware logs START and END to stderr`() {
        val testFile = File("/tmp/read_test_${System.currentTimeMillis()}.txt")
        testFile.writeText("content")
        testFile.deleteOnExit()

        val app = buildApp()
        val exitCode = app.runForTest(arrayOf("file", "read", testFile.absolutePath))

        val stderr = errStream.toString()

        assertEquals(0, exitCode)
        assertTrue(stderr.contains("START: read"), "Global middleware START log missing")
        assertTrue(stderr.contains("END: read took"), "Global middleware END log missing")
    }

    @Test
    fun `context data sharing - middleware can access values set by previous middleware`() {
        val app = cli(
            name = "context-test",
            version = "1.0",
            description = "Test context sharing",
        ) {
            use(object : Middleware {
                override val priority = 0
                override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                    ctx.arguments["user"] = "test-user"
                    next()
                }
            })
            use(object : Middleware {
                override val priority = 1
                override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
                    val user = ctx.arguments["user"]
                    System.err.println("User from context: $user")
                    next()
                }
            })

            group(name = "test") {
                command(name = "echo") {
                    action {
                        println("OK")
                    }
                }
            }
        }

        val exitCode = app.runForTest(arrayOf("test", "echo"))
        val stderr = errStream.toString()

        assertEquals(0, exitCode)
        assertTrue(stderr.contains("User from context: test-user"), "Context data not shared")
    }
}
