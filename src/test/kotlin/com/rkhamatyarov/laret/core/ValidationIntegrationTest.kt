package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationIntegrationTest {
    private lateinit var buf: ByteArrayOutputStream
    private val originalOut = System.out
    private val originalErr = System.err
    private var ran = false

    private fun app() = cli(name = "test", version = "1.0.0") {
        group(name = "check") {
            command(name = "run") {
                argument("port", "Port") { range(1, 65535) }
                option("e", "email", "Email", "", true) { regex("^[^@]+@[^@]+\\.[^@]+$") }
                option("f", "format", "Format", "", true) { oneOf("json", "yaml") }
                option("i", "input", "Input file", "", true) { fileExists() }
                option("j", "json", "JSON", "", false)
                option("y", "yaml", "YAML", "", false)
                mutuallyExclusive("json", "yaml")
                action { ran = true }
            }
        }
    }

    @BeforeEach
    fun setUp() {
        ran = false
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
    fun `a valid invocation runs the action`() {
        assertEquals(0, app().runForTest(arrayOf("check", "run", "8080", "--format", "json")))
        assertTrue(ran, "action should have run")
    }

    @Test
    fun `an out-of-range argument fails before the action`() {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "99999")))
        assertTrue(!ran, "action must not run on invalid input")
        assertTrue(output().contains("Invalid value for argument 'port'"), output())
        assertTrue(output().contains("between 1 and 65535"), output())
    }

    @Test
    fun `a non-numeric argument reports the number rule`() {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "abc")))
        assertTrue(output().contains("must be a number"), output())
    }

    @Test
    fun `an option failing regex is reported`() {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "80", "--email", "nope")))
        assertTrue(output().contains("Invalid value for option '--email'"), output())
    }

    @Test
    fun `an option outside oneOf is reported`() {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "80", "--format", "xml")))
        assertTrue(output().contains("must be one of: json, yaml"), output())
    }

    @Test
    fun `a missing file is reported and an existing one passes`(@TempDir dir: Path) {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "80", "--input", dir.resolve("no.txt").toString())))
        assertTrue(output().contains("file does not exist"), output())

        val real = Files.createFile(dir.resolve("yes.txt"))
        assertEquals(0, app().runForTest(arrayOf("check", "run", "80", "--input", real.toString())))
    }

    @Test
    fun `mutually exclusive options are rejected when both are supplied`() {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "80", "--json", "--yaml")))
        assertTrue(output().contains("mutually exclusive"), output())
        assertTrue(output().contains("--json"), output())
        assertTrue(output().contains("--yaml"), output())
    }

    @Test
    fun `supplying only one of an exclusive pair is allowed`() {
        assertEquals(0, app().runForTest(arrayOf("check", "run", "80", "--json")))
        assertTrue(ran)
    }

    @Test
    fun `unset optional options skip their validators`() {
        assertEquals(0, app().runForTest(arrayOf("check", "run", "80")))
        assertTrue(ran)
        assertTrue(!output().contains("Invalid value"), output())
    }

    @Test
    fun `all failures are collected in one run`() {
        assertEquals(1, app().runForTest(arrayOf("check", "run", "abc", "--email", "bad", "--format", "xml")))
        val reported = output().split("Invalid value").size - 1
        assertEquals(3, reported, "expected three reported failures in:\n${output()}")
    }
}
