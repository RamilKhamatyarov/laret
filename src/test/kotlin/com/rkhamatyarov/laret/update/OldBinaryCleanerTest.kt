package com.rkhamatyarov.laret.update

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OldBinaryCleanerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun test_cleanup_deletes_laret_old_files() {
        val old = tempDir.resolve("laret.old")
        Files.writeString(old, "stale binary")

        val deleted = OldBinaryCleaner.cleanup(tempDir)

        assertEquals(listOf(old), deleted)
        assertFalse(Files.exists(old))
    }

    @Test
    fun test_cleanup_deletes_renamed_binary_old_files_too() {
        val old = tempDir.resolve("laret-linux-x86_64.old")
        Files.writeString(old, "stale binary")

        OldBinaryCleaner.cleanup(tempDir)

        assertFalse(Files.exists(old))
    }

    @Test
    fun test_cleanup_leaves_unrelated_files_alone() {
        Files.writeString(tempDir.resolve("laret"), "live binary")
        Files.writeString(tempDir.resolve("laret.new"), "staged binary")
        Files.writeString(tempDir.resolve("notes.old"), "user file")

        val deleted = OldBinaryCleaner.cleanup(tempDir)

        assertTrue(deleted.isEmpty())
        assertTrue(Files.exists(tempDir.resolve("laret")))
        assertTrue(Files.exists(tempDir.resolve("laret.new")))
        assertTrue(Files.exists(tempDir.resolve("notes.old")))
    }

    @Test
    fun test_cleanup_of_missing_directory_is_a_silent_noop() {
        val deleted = OldBinaryCleaner.cleanup(tempDir.resolve("does-not-exist"))

        assertTrue(deleted.isEmpty())
    }

    @Test
    fun test_cleanup_silently_never_throws() {
        OldBinaryCleaner.cleanupSilently()
    }
}
