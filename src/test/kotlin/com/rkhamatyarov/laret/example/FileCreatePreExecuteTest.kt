package com.rkhamatyarov.laret.example

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileCreatePreExecuteTest {

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
    fun `file create with empty path fails with validation message and no middleware logs`() {
        val app = buildApp()
        val exitCode = app.runForTest(arrayOf("file", "create", "", "-c", "test"))

        val stderr = errStream.toString()
        val stdout = outStream.toString()

        assertEquals(1, exitCode, "Exit code should be 1 for validation failure")
        assertTrue(stderr.contains("Path cannot be empty"), "Expected error message not found in stderr")
        assertTrue(!stdout.contains("START: create"), "Middleware START log should NOT appear")
        assertTrue(!stdout.contains("END: create took"), "Middleware END log should NOT appear")
    }
}
