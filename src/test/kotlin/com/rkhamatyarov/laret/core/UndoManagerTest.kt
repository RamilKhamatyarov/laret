package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoManagerTest {

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

    private fun entry(
        description: String,
        undoArgs: List<String> = emptyList(),
        redoArgs: List<String> = emptyList(),
    ) = UndoManager.HistoryEntry(
        id = UUID.randomUUID().toString(),
        description = description,
        timestamp = System.currentTimeMillis(),
        undoArgs = undoArgs,
        redoArgs = redoArgs,
    )

    @Test
    fun `push adds entry to undo stack`() {
        UndoManager.push(entry("create file"))
        assertEquals(1, UndoManager.undoHistory().size)
        assertEquals("create file", UndoManager.undoHistory()[0].description)
    }

    @Test
    fun `popUndo returns the most recently pushed entry`() {
        UndoManager.push(entry("first"))
        UndoManager.push(entry("second"))
        val popped = UndoManager.popUndo()
        assertEquals("second", popped?.description)
        assertEquals(1, UndoManager.undoHistory().size)
    }

    @Test
    fun `popUndo moves entry to redo stack`() {
        UndoManager.push(entry("action"))
        UndoManager.popUndo()
        assertEquals(0, UndoManager.undoHistory().size)
        assertEquals(1, UndoManager.redoHistory().size)
        assertEquals("action", UndoManager.redoHistory()[0].description)
    }

    @Test
    fun `popUndo on empty stack returns null`() {
        assertNull(UndoManager.popUndo())
    }

    @Test
    fun `popRedo returns the most recently undone entry`() {
        UndoManager.push(entry("action"))
        UndoManager.popUndo()
        val redone = UndoManager.popRedo()
        assertNotNull(redone)
        assertEquals("action", redone.description)
    }

    @Test
    fun `popRedo moves entry back to undo stack`() {
        UndoManager.push(entry("action"))
        UndoManager.popUndo()
        UndoManager.popRedo()
        assertEquals(1, UndoManager.undoHistory().size)
        assertEquals(0, UndoManager.redoHistory().size)
    }

    @Test
    fun `popRedo on empty redo stack returns null`() {
        assertNull(UndoManager.popRedo())
    }

    @Test
    fun `push clears the redo stack`() {
        UndoManager.push(entry("first"))
        UndoManager.popUndo()
        assertEquals(1, UndoManager.redoHistory().size)
        UndoManager.push(entry("new action"))
        assertEquals(0, UndoManager.redoHistory().size)
    }

    @Test
    fun `canUndo returns false when stack is empty`() {
        assertFalse(UndoManager.canUndo())
    }

    @Test
    fun `canUndo returns true after push`() {
        UndoManager.push(entry("test"))
        assertTrue(UndoManager.canUndo())
    }

    @Test
    fun `canRedo returns false when nothing has been undone`() {
        assertFalse(UndoManager.canRedo())
    }

    @Test
    fun `canRedo returns true after popUndo`() {
        UndoManager.push(entry("test"))
        UndoManager.popUndo()
        assertTrue(UndoManager.canRedo())
    }

    @Test
    fun `withSuppressedRecording returns value from block`() {
        val result = UndoManager.withSuppressedRecording { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `peek then conditional pop leaves undo stack intact when execution fails`() {
        UndoManager.push(entry("action"))

        val peeked = UndoManager.peekUndo()
        assertNotNull(peeked)
        val fakeExitCode = 1
        if (fakeExitCode == 0) UndoManager.popUndo()

        assertEquals(1, UndoManager.undoHistory().size)
        assertEquals(0, UndoManager.redoHistory().size)
        assertEquals("action", UndoManager.undoHistory()[0].description)
    }

    @Test
    fun `peek then conditional pop leaves redo stack intact when execution fails`() {
        UndoManager.push(entry("action"))
        UndoManager.popUndo()

        val peeked = UndoManager.peekRedo()
        assertNotNull(peeked)
        val fakeExitCode = 1
        if (fakeExitCode == 0) UndoManager.popRedo()

        assertEquals(0, UndoManager.undoHistory().size)
        assertEquals(1, UndoManager.redoHistory().size)
        assertEquals("action", UndoManager.redoHistory()[0].description)
    }

    @Test
    fun `withSuppressedRecording prevents push from adding entries`() {
        UndoManager.withSuppressedRecording {
            UndoManager.push(entry("suppressed"))
        }
        assertEquals(0, UndoManager.undoHistory().size)
    }

    @Test
    fun `withSuppressedRecording restores recording even after an exception`() {
        runCatching {
            UndoManager.withSuppressedRecording { throw RuntimeException("boom") }
        }
        UndoManager.push(entry("after exception"))
        assertEquals(1, UndoManager.undoHistory().size)
    }

    @Test
    fun `withSuppressedRecording is re-entrant - inner finally does not re-enable push`() {
        UndoManager.withSuppressedRecording {
            UndoManager.withSuppressedRecording {
                UndoManager.push(entry("inner suppressed"))
            }
            UndoManager.push(entry("outer still suppressed"))
        }
        assertEquals(0, UndoManager.undoHistory().size)
    }

    @Test
    fun `withSuppressedRecording re-enables push only after outermost block exits`() {
        UndoManager.withSuppressedRecording {
            UndoManager.withSuppressedRecording { }
        }
        UndoManager.push(entry("after both blocks"))
        assertEquals(1, UndoManager.undoHistory().size)
    }

    @Test
    fun `peekUndo returns last entry without removing it`() {
        UndoManager.push(entry("action"))
        val peeked = UndoManager.peekUndo()
        assertEquals("action", peeked?.description)
        assertEquals(1, UndoManager.undoHistory().size)
    }

    @Test
    fun `peekUndo returns null on empty stack`() {
        assertNull(UndoManager.peekUndo())
    }

    @Test
    fun `peekRedo returns last redo entry without removing it`() {
        UndoManager.push(entry("action"))
        UndoManager.popUndo()
        val peeked = UndoManager.peekRedo()
        assertEquals("action", peeked?.description)
        assertEquals(1, UndoManager.redoHistory().size)
    }

    @Test
    fun `undoHistory returns entries in push order oldest first`() {
        UndoManager.push(entry("first"))
        UndoManager.push(entry("second"))
        UndoManager.push(entry("third"))
        assertEquals(
            listOf("first", "second", "third"),
            UndoManager.undoHistory().map { it.description },
        )
    }

    @Test
    fun `redoHistory returns entries with most recently undone last`() {
        UndoManager.push(entry("first"))
        UndoManager.push(entry("second"))
        UndoManager.popUndo()
        UndoManager.popUndo()
        assertEquals(
            listOf("second", "first"),
            UndoManager.redoHistory().map { it.description },
        )
    }

    @Test
    fun `push writes history file to disk`() {
        val path = tempDir.toPath().resolve("history.txt")
        UndoManager.push(entry("disk test", listOf("file", "delete", "/tmp/x")))
        assertTrue(Files.exists(path))
        assertTrue(Files.size(path) > 0)
    }

    @Test
    fun `load restores undo stack from persisted file`() {
        val path = tempDir.toPath().resolve("history.txt")
        val original = entry("persisted", listOf("file", "delete", "/tmp/y"), listOf("file", "create", "/tmp/y"))
        UndoManager.push(original)

        UndoManager.configureForTest(path)
        assertEquals(0, UndoManager.undoHistory().size)

        UndoManager.load()
        assertEquals(1, UndoManager.undoHistory().size)
        with(UndoManager.undoHistory()[0]) {
            assertEquals("persisted", description)
            assertEquals(listOf("file", "delete", "/tmp/y"), undoArgs)
            assertEquals(listOf("file", "create", "/tmp/y"), redoArgs)
        }
    }

    @Test
    fun `load restores redo stack from persisted file`() {
        val path = tempDir.toPath().resolve("history.txt")
        UndoManager.push(entry("action"))
        UndoManager.popUndo()

        UndoManager.configureForTest(path)
        UndoManager.load()
        assertEquals(0, UndoManager.undoHistory().size)
        assertEquals(1, UndoManager.redoHistory().size)
        assertEquals("action", UndoManager.redoHistory()[0].description)
    }

    @Test
    fun `entry args with newlines and special characters survive round-trip`() {
        val weirdContent = "line one\nline two\ttab\r\nwindows \"quoted\""
        val original = entry("special", undoArgs = listOf("file", "create", "/tmp/x", "-c", weirdContent))
        UndoManager.push(original)

        val path = tempDir.toPath().resolve("history.txt")
        UndoManager.configureForTest(path)
        UndoManager.load()

        assertEquals(weirdContent, UndoManager.undoHistory()[0].undoArgs[4])
    }

    @Test
    fun `newEntry helper creates entry with correct fields`() {
        val e = UndoManager.newEntry("desc", listOf("a", "b"), listOf("c", "d"))
        assertEquals("desc", e.description)
        assertEquals(listOf("a", "b"), e.undoArgs)
        assertEquals(listOf("c", "d"), e.redoArgs)
        assertTrue(e.id.isNotBlank())
        assertTrue(e.timestamp > 0)
    }
}
