package com.rkhamatyarov.laret.update

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinaryReplacerTest {

    @TempDir
    lateinit var tempDir: Path

    private val replacer = BinaryReplacer()

    private fun liveBinary(content: String = "old binary"): Path {
        val live = tempDir.resolve("laret")
        Files.writeString(live, content)
        return live
    }

    private fun stagedBinary(content: String = "new binary"): Path {
        val staged = tempDir.resolve("laret.new")
        Files.writeString(staged, content)
        return staged
    }

    @Test
    fun test_replace_swaps_staged_binary_onto_live_name() {
        val live = liveBinary()
        val staged = stagedBinary()

        val result = replacer.replace(staged, live)

        assertTrue(result.isSuccess)
        assertEquals("new binary", Files.readString(live))
        assertFalse(Files.exists(staged))
    }

    @Test
    fun test_replace_leaves_old_binary_for_next_startup_cleanup() {
        val live = liveBinary()
        val staged = stagedBinary()

        replacer.replace(staged, live).getOrThrow()

        val old = tempDir.resolve("laret.old")
        assertTrue(Files.exists(old))
        assertEquals("old binary", Files.readString(old))
    }

    @Test
    fun test_replace_overwrites_stale_old_file_from_previous_update() {
        val live = liveBinary()
        val staged = stagedBinary()
        Files.writeString(tempDir.resolve("laret.old"), "ancient binary")

        val result = replacer.replace(staged, live)

        assertTrue(result.isSuccess)
        assertEquals("old binary", Files.readString(tempDir.resolve("laret.old")))
    }

    @Test
    fun test_replace_fails_when_staged_binary_missing() {
        val live = liveBinary()
        val missing = tempDir.resolve("laret.new")

        val result = replacer.replace(missing, live)

        assertTrue(result.isFailure)
        assertEquals("old binary", Files.readString(live))
    }

    @Test
    fun test_failed_install_rolls_back_to_previous_binary() {
        val live = liveBinary()
        val staged = stagedBinary()
        val failing = BinaryReplacer(installMove = { _, _ -> throw java.io.IOException("disk died") })

        val result = failing.replace(staged, live)

        assertTrue(result.isFailure)
        assertTrue(Files.isRegularFile(live), "rollback must restore the live binary")
        assertEquals("old binary", Files.readString(live))
    }
}
