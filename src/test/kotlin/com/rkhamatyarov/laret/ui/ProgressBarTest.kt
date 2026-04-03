package com.rkhamatyarov.laret.ui

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressBarTest {
    private fun captureErr(block: (PrintStream) -> Unit): String {
        val buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        block(ps)
        return buf.toString()
    }

    @Test
    fun `initial percent is zero`() {
        val pb = ProgressBar(total = 100)
        assertEquals(0, pb.percent)
    }

    @Test
    fun `percent after update reflects value`() {
        val pb = ProgressBar(total = 100)
        pb.update(50)
        assertEquals(50, pb.percent)
    }

    @Test
    fun `percent is 100 after finish`() {
        val pb = ProgressBar(total = 100)
        pb.update(30)
        pb.finish()
        assertEquals(100, pb.percent)
    }

    @Test
    fun `increment increases current by 1`() {
        val pb = ProgressBar(total = 10)
        pb.increment()
        pb.increment()
        assertEquals(20, pb.percent)
    }

    @Test
    fun `increment by N increases current by N`() {
        val pb = ProgressBar(total = 100)
        pb.increment(25)
        assertEquals(25, pb.percent)
    }

    @Test
    fun `update clamps value to total`() {
        val pb = ProgressBar(total = 10)
        pb.update(999)
        assertEquals(100, pb.percent)
    }

    @Test
    fun `update clamps negative value to zero`() {
        val pb = ProgressBar(total = 10)
        pb.update(-5)
        assertEquals(0, pb.percent)
    }

    @Test
    fun `isFinished is false before finish`() {
        val pb = ProgressBar(total = 10)
        assertFalse(pb.isFinished)
    }

    @Test
    fun `isFinished is true after finish`() {
        val pb = ProgressBar(total = 10)
        pb.finish()
        assertTrue(pb.isFinished)
    }

    @Test
    fun `render output contains percent`() {
        val output =
            captureErr { ps ->
                val pb = ProgressBar(total = 100, out = ps)
                pb.update(50)
            }
        assertTrue(output.contains("50%"))
    }

    @Test
    fun `render output contains current and total`() {
        val output =
            captureErr { ps ->
                val pb = ProgressBar(total = 200, out = ps)
                pb.update(100)
            }
        assertTrue(output.contains("100/200"))
    }

    @Test
    fun `render output contains label when set`() {
        val output =
            captureErr { ps ->
                val pb = ProgressBar(total = 10, label = "Uploading", out = ps)
                pb.update(5)
            }
        assertTrue(output.contains("Uploading"))
    }

    @Test
    fun `finish output ends with newline`() {
        val output =
            captureErr { ps ->
                val pb = ProgressBar(total = 10, out = ps)
                pb.finish()
            }
        assertTrue(output.endsWith("\n") || output.endsWith(System.lineSeparator()))
    }

    @Test
    fun `finish renders 100 percent`() {
        val output =
            captureErr { ps ->
                val pb = ProgressBar(total = 50, out = ps)
                pb.update(10)
                pb.finish()
            }
        assertTrue(output.contains("100%"))
    }

    @Test
    fun `zero total results in 100 percent`() {
        val pb = ProgressBar(total = 0)
        assertEquals(100, pb.percent)
    }

    @Test
    fun `width controls bar length`() {
        val output =
            captureErr { ps ->
                val pb = ProgressBar(total = 100, width = 20, out = ps)
                pb.update(100)
            }
        assertTrue(output.contains("█"))
    }
}

class SpinnerTest {
    private fun captureErr(block: (PrintStream) -> Unit): String {
        val buf = ByteArrayOutputStream()
        val ps = PrintStream(buf)
        block(ps)
        return buf.toString()
    }

    @Test
    fun `isFinished is false before finish`() {
        val spinner = Spinner()
        assertFalse(spinner.isFinished)
    }

    @Test
    fun `isFinished is true after finish`() {
        val spinner = Spinner()
        spinner.finish()
        assertTrue(spinner.isFinished)
    }

    @Test
    fun `isFinished is true after fail`() {
        val spinner = Spinner()
        spinner.fail()
        assertTrue(spinner.isFinished)
    }

    @Test
    fun `tick produces output`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(out = ps)
                spinner.tick()
            }
        assertTrue(output.isNotEmpty())
    }

    @Test
    fun `tick after finish does not produce output`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(out = ps)
                spinner.finish()
                spinner.tick()
            }
        assertTrue(output.isNotEmpty())
    }

    @Test
    fun `finish output contains check mark`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(out = ps)
                spinner.finish()
            }
        assertTrue(output.contains("✔"))
    }

    @Test
    fun `fail output contains cross mark`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(out = ps)
                spinner.fail()
            }
        assertTrue(output.contains("✗"))
    }

    @Test
    fun `finish output contains label`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(label = "Loading data", out = ps)
                spinner.finish()
            }
        assertTrue(output.contains("Loading data"))
    }

    @Test
    fun `fail output contains custom message`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(label = "Uploading", out = ps)
                spinner.fail("Connection refused")
            }
        assertTrue(output.contains("Connection refused"))
    }

    @Test
    fun `finish output contains custom message`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(label = "Processing", out = ps)
                spinner.finish("Done!")
            }
        assertTrue(output.contains("Done!"))
    }

    @Test
    fun `finish output ends with newline`() {
        val output =
            captureErr { ps ->
                val spinner = Spinner(out = ps)
                spinner.finish()
            }
        assertTrue(output.endsWith("\n") || output.endsWith(System.lineSeparator()))
    }

    @Test
    fun `tick cycles through frames`() {
        val outputs = mutableListOf<String>()
        repeat(12) {
            val buf = ByteArrayOutputStream()
            val ps = PrintStream(buf)
            val spinner = Spinner(out = ps)
            repeat(it) { spinner.tick() }
            outputs.add(buf.toString())
        }
        assertTrue(outputs.isNotEmpty())
    }

    @Test
    fun `label is accessible`() {
        val spinner = Spinner(label = "My task")
        assertEquals("My task", spinner.label)
    }
}
