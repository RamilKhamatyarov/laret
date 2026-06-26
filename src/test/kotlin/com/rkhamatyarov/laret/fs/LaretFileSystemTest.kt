package com.rkhamatyarov.laret.fs

import com.rkhamatyarov.laret.model.fs.DryRunFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaretFileSystemTest {

    @TempDir
    lateinit var tempDir: Path

    private fun captureStderr(block: () -> Unit): String {
        val original = System.err
        val buffer = ByteArrayOutputStream()
        System.setErr(PrintStream(buffer, true, Charsets.UTF_8))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buffer.toString(Charsets.UTF_8)
    }

    // ----- RealFileSystem: actually performs the operation -----

    @Test
    fun test_real_write_then_read_round_trips() {
        val fs = RealFileSystem()
        val target = tempDir.resolve("note.txt")

        fs.writeText(target, "hello")

        assertTrue(Files.exists(target))
        assertEquals("hello", fs.readText(target))
    }

    @Test
    fun test_real_createDirectories_and_delete() {
        val fs = RealFileSystem()
        val nested = tempDir.resolve("a/b/c")

        fs.createDirectories(nested)
        assertTrue(Files.isDirectory(nested))

        val file = tempDir.resolve("gone.txt")
        Files.writeString(file, "x")
        assertTrue(fs.delete(file))
        assertFalse(Files.exists(file))
    }

    @Test
    fun test_real_listFiles_is_sorted_and_empty_for_non_directory() {
        val fs = RealFileSystem()
        Files.writeString(tempDir.resolve("b.txt"), "x")
        Files.writeString(tempDir.resolve("a.txt"), "x")

        val names = fs.listFiles(tempDir).map { it.fileName.toString() }
        assertEquals(listOf("a.txt", "b.txt"), names)

        assertTrue(fs.listFiles(tempDir.resolve("a.txt")).isEmpty())
    }

    // ----- DryRunFileSystem: narrates, never mutates -----

    @Test
    fun test_dryRun_writeText_does_not_touch_disk_and_reports_bytes() {
        val fs = DryRunFileSystem()
        val target = tempDir.resolve("should-not-exist.txt")

        val err = captureStderr { fs.writeText(target, "hello") }

        assertFalse(Files.exists(target))
        assertContains(err, "[DRY-RUN]")
        assertContains(err, "5 bytes")
        assertContains(err, target.toString())
    }

    @Test
    fun test_dryRun_delete_keeps_file_and_returns_true() {
        val fs = DryRunFileSystem()
        val file = tempDir.resolve("keep.txt")
        Files.writeString(file, "x")

        val err = captureStderr { assertTrue(fs.delete(file)) }

        assertTrue(Files.exists(file))
        assertContains(err, "[DRY-RUN]")
    }

    @Test
    fun test_dryRun_createDirectories_does_not_create_anything() {
        val fs = DryRunFileSystem()
        val nested = tempDir.resolve("x/y/z")

        val err = captureStderr { fs.createDirectories(nested) }

        assertFalse(Files.exists(nested))
        assertContains(err, "[DRY-RUN]")
    }

    @Test
    fun test_dryRun_reads_delegate_to_real_filesystem() {
        val fs = DryRunFileSystem()
        val file = tempDir.resolve("data.txt")
        Files.writeString(file, "payload")

        assertTrue(fs.exists(file))
        assertEquals("payload", fs.readText(file))
    }
}
