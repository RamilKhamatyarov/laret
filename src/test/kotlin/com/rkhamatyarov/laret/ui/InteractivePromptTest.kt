package com.rkhamatyarov.laret.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class InteractivePromptTest {
    private fun makePrompt(inputLines: String): Pair<InteractivePrompt, ByteArrayOutputStream> {
        val inStream = ByteArrayInputStream(inputLines.toByteArray())
        val outBuf = ByteArrayOutputStream()
        val outStream = PrintStream(outBuf)
        return InteractivePrompt(input = inStream, out = outStream) to outBuf
    }

    @Test
    fun `text returns typed input`() {
        val (p, _) = makePrompt("hello world\n")
        assertEquals("hello world", p.text("Enter name"))
    }

    @Test
    fun `text returns default when input is blank`() {
        val (p, _) = makePrompt("\n")
        assertEquals("defaultVal", p.text("Enter name", default = "defaultVal"))
    }

    @Test
    fun `text returns empty string when no default and blank input`() {
        val (p, _) = makePrompt("\n")
        assertEquals("", p.text("Enter name"))
    }

    @Test
    fun `text trims whitespace from input`() {
        val (p, _) = makePrompt("  trimmed  \n")
        assertEquals("trimmed", p.text("Enter value"))
    }

    @Test
    fun `text output contains prompt`() {
        val (p, buf) = makePrompt("answer\n")
        p.text("What is your name")
        assertTrue(buf.toString().contains("What is your name"))
    }

    @Test
    fun `text output contains default hint when default is set`() {
        val (p, buf) = makePrompt("\n")
        p.text("Enter value", default = "myDefault")
        assertTrue(buf.toString().contains("myDefault"))
    }

    @Test
    fun `confirm returns true for y`() {
        val (p, _) = makePrompt("y\n")
        assertEquals(true, p.confirm("Are you sure"))
    }

    @Test
    fun `confirm returns true for yes`() {
        val (p, _) = makePrompt("yes\n")
        assertEquals(true, p.confirm("Are you sure"))
    }

    @Test
    fun `confirm returns false for n`() {
        val (p, _) = makePrompt("n\n")
        assertEquals(false, p.confirm("Are you sure"))
    }

    @Test
    fun `confirm returns false for no`() {
        val (p, _) = makePrompt("no\n")
        assertEquals(false, p.confirm("Are you sure"))
    }

    @Test
    fun `confirm returns true default when blank and default is true`() {
        val (p, _) = makePrompt("\n")
        assertEquals(true, p.confirm("Continue", default = true))
    }

    @Test
    fun `confirm returns false default when blank and default is false`() {
        val (p, _) = makePrompt("\n")
        assertEquals(false, p.confirm("Continue", default = false))
    }

    @Test
    fun `confirm is case insensitive`() {
        val (p, _) = makePrompt("YES\n")
        assertEquals(true, p.confirm("Are you sure"))
    }

    @Test
    fun `confirm output contains prompt text`() {
        val (p, buf) = makePrompt("y\n")
        p.confirm("Delete everything")
        assertTrue(buf.toString().contains("Delete everything"))
    }

    @Test
    fun `confirm output shows Y-n hint when default is true`() {
        val (p, buf) = makePrompt("y\n")
        p.confirm("Continue", default = true)
        assertTrue(buf.toString().contains("Y/n"))
    }

    @Test
    fun `confirm output shows y-N hint when default is false`() {
        val (p, buf) = makePrompt("n\n")
        p.confirm("Continue", default = false)
        assertTrue(buf.toString().contains("y/N"))
    }

    @Test
    fun `select returns chosen option by number`() {
        val (p, _) = makePrompt("2\n")
        assertEquals("beta", p.select("Pick one", listOf("alpha", "beta", "gamma")))
    }

    @Test
    fun `select returns first option for input 1`() {
        val (p, _) = makePrompt("1\n")
        assertEquals("first", p.select("Pick one", listOf("first", "second")))
    }

    @Test
    fun `select clamps out-of-range input to last option`() {
        val (p, _) = makePrompt("99\n")
        assertEquals("c", p.select("Pick one", listOf("a", "b", "c")))
    }

    @Test
    fun `select clamps zero input to first option`() {
        val (p, _) = makePrompt("0\n")
        assertEquals("a", p.select("Pick one", listOf("a", "b", "c")))
    }

    @Test
    fun `select falls back to first option on non-numeric input`() {
        val (p, _) = makePrompt("abc\n")
        assertEquals("x", p.select("Pick one", listOf("x", "y")))
    }

    @Test
    fun `select output contains prompt`() {
        val (p, buf) = makePrompt("1\n")
        p.select("Choose format", listOf("json", "yaml"))
        assertTrue(buf.toString().contains("Choose format"))
    }

    @Test
    fun `select output lists all options`() {
        val (p, buf) = makePrompt("1\n")
        p.select("Pick", listOf("apple", "banana", "cherry"))
        val out = buf.toString()
        assertTrue(out.contains("apple"))
        assertTrue(out.contains("banana"))
        assertTrue(out.contains("cherry"))
    }

    @Test
    fun `select output contains numbered options`() {
        val (p, buf) = makePrompt("1\n")
        p.select("Pick", listOf("one", "two"))
        val out = buf.toString()
        assertTrue(out.contains("1)"))
        assertTrue(out.contains("2)"))
    }

    @Test
    fun `multiSelect returns selected options by comma-separated numbers`() {
        val (p, _) = makePrompt("1,3\n")
        assertEquals(listOf("a", "c"), p.multiSelect("Pick", listOf("a", "b", "c")))
    }

    @Test
    fun `multiSelect returns empty list for blank input`() {
        val (p, _) = makePrompt("\n")
        assertTrue(p.multiSelect("Pick", listOf("a", "b")).isEmpty())
    }

    @Test
    fun `multiSelect deduplicates repeated selections`() {
        val (p, _) = makePrompt("1,1,2\n")
        assertEquals(listOf("x", "y"), p.multiSelect("Pick", listOf("x", "y")))
    }

    @Test
    fun `multiSelect ignores out-of-range numbers`() {
        val (p, _) = makePrompt("1,99\n")
        assertEquals(listOf("a"), p.multiSelect("Pick", listOf("a", "b")))
    }

    @Test
    fun `multiSelect output contains prompt`() {
        val (p, buf) = makePrompt("1\n")
        p.multiSelect("Select items", listOf("a", "b"))
        assertTrue(buf.toString().contains("Select items"))
    }

    @Test
    fun `multiSelect output lists all options`() {
        val (p, buf) = makePrompt("1\n")
        p.multiSelect("Pick", listOf("foo", "bar"))
        val out = buf.toString()
        assertTrue(out.contains("foo"))
        assertTrue(out.contains("bar"))
    }

    @Test
    fun `password returns typed input`() {
        val (p, _) = makePrompt("s3cr3t\n")
        assertEquals("s3cr3t", p.password("Enter password"))
    }

    @Test
    fun `password output contains prompt`() {
        val (p, buf) = makePrompt("pass\n")
        p.password("Enter password")
        assertTrue(buf.toString().contains("Enter password"))
    }

    @Test
    fun `password returns empty string on blank input`() {
        val (p, _) = makePrompt("\n")
        assertEquals("", p.password("Enter password"))
    }
}
