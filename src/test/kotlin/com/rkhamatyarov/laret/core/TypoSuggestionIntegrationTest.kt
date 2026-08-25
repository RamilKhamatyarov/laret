package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypoSuggestionIntegrationTest {
    private lateinit var buf: ByteArrayOutputStream
    private val originalOut = System.out
    private val originalErr = System.err

    private fun app(): CliApp = cli(name = "test", version = "1.0.0") {
        group(name = "file", description = "File operations") {
            command(name = "create") {
                argument("path", "Path")
                option("f", "force", "Overwrite", "", false)
                action { it.arguments["ran"] = "yes" }
            }
            command(name = "delete") { action {} }
        }
        group(name = "dir") { command(name = "list") { action {} } }
    }

    @BeforeEach
    fun setUp() {
        buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        System.setOut(ps)
        System.setErr(ps)
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    private fun output() = buf.toString()

    @Test
    fun `mistyped group suggests the closest group`() {
        val code = app().runForTest(arrayOf("fil", "create", "x"))
        assertEquals(1, code)
        assertTrue(output().contains("Group not found: fil"), output())
        assertTrue(output().contains("Did you mean 'file'?"), output())
    }

    @Test
    fun `mistyped command suggests the closest command`() {
        val code = app().runForTest(arrayOf("file", "creat", "x"))
        assertEquals(1, code)
        assertTrue(output().contains("Command not found: creat"), output())
        assertTrue(output().contains("Did you mean 'create'?"), output())
    }

    @Test
    fun `unknown flag warns with a suggestion but does not fail`() {
        val code = app().runForTest(arrayOf("file", "create", "x", "--focre"))
        assertEquals(0, code)
        assertTrue(output().contains("Unknown flag '--focre'"), output())
        assertTrue(output().contains("Did you mean '--force'?"), output())
    }

    @Test
    fun `a correctly spelled command produces no suggestion noise`() {
        val code = app().runForTest(arrayOf("file", "create", "x", "--force"))
        assertEquals(0, code)
        assertTrue(!output().contains("Did you mean"), output())
        assertTrue(!output().contains("Unknown flag"), output())
    }

    @Test
    fun `an unrelated group name yields no suggestion line`() {
        app().runForTest(arrayOf("zzzzzz", "create"))
        assertTrue(output().contains("Group not found: zzzzzz"), output())
        assertTrue(!output().contains("Did you mean"), output())
    }
}
