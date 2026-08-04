package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandContextTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        UndoManager.configureForTest(tempDir.toPath().resolve("history.txt"))
    }

    @AfterEach
    fun tearDown() {
        UndoManager.resetForTest()
    }

    @Test
    fun test_default_context_is_not_dry_run() {
        val ctx = CommandContext(command = Command(name = "test"), groupName = "test")

        assertFalse(ctx.isDryRun)
    }

    @Test
    fun test_exit_defaults_to_success() {
        val ctx = CommandContext(command = Command(name = "test"), groupName = "test")

        assertEquals(0, ctx.exitCode)
    }

    @Test
    fun test_exit_uses_last_valid_code() {
        val ctx = CommandContext(command = Command(name = "test"), groupName = "test")

        ctx.exit(7)
        ctx.exit(3)

        assertEquals(3, ctx.exitCode)
    }

    @Test
    fun test_exit_rejects_codes_outside_process_range() {
        val ctx = CommandContext(command = Command(name = "test"), groupName = "test")

        assertFailsWith<IllegalArgumentException> { ctx.exit(-1) }
        assertFailsWith<IllegalArgumentException> { ctx.exit(256) }
    }

    @Test
    fun test_registerUndo_skips_entry_when_context_is_dry_run() {
        val ctx = CommandContext(command = Command(name = "test"), groupName = "test", isDryRun = true)

        ctx.registerUndo("test action", undoArgs = arrayOf("test", "undo"))

        assertFalse(UndoManager.canUndo())
    }

    @Test
    fun test_registerUndo_adds_entry_when_context_is_not_dry_run() {
        val ctx = CommandContext(command = Command(name = "test"), groupName = "test")

        ctx.registerUndo("test action", undoArgs = arrayOf("test", "undo"))

        assertTrue(UndoManager.canUndo())
    }
}
