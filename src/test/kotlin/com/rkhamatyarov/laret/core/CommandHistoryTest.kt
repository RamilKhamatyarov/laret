package com.rkhamatyarov.laret.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandHistoryTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        CommandHistory.configureForTest(tempDir.toPath().resolve("cmd_history.txt"))
    }

    @AfterEach
    fun tearDown() {
        CommandHistory.resetForTest()
    }

    @Test
    fun `record adds entry to history`() {
        CommandHistory.record(arrayOf("file", "create", "/tmp/x"))
        assertEquals(1, CommandHistory.size())
        assertEquals(listOf("file", "create", "/tmp/x"), CommandHistory.last()?.args)
    }

    @Test
    fun `record does nothing for empty args`() {
        CommandHistory.record(emptyArray())
        assertEquals(0, CommandHistory.size())
    }

    @Test
    fun `last returns most recently recorded entry`() {
        CommandHistory.record(arrayOf("file", "create", "/tmp/a"))
        CommandHistory.record(arrayOf("dir", "list", "/tmp"))
        assertEquals(listOf("dir", "list", "/tmp"), CommandHistory.last()?.args)
    }

    @Test
    fun `last returns null when history is empty`() {
        assertNull(CommandHistory.last())
    }

    @Test
    fun `get returns entry at 1-based absolute index`() {
        CommandHistory.record(arrayOf("cmd", "one"))
        CommandHistory.record(arrayOf("cmd", "two"))
        CommandHistory.record(arrayOf("cmd", "three"))
        assertEquals(listOf("cmd", "one"), CommandHistory.get(1)?.args)
        assertEquals(listOf("cmd", "two"), CommandHistory.get(2)?.args)
        assertEquals(listOf("cmd", "three"), CommandHistory.get(3)?.args)
    }

    @Test
    fun `get returns null for out-of-range index`() {
        CommandHistory.record(arrayOf("cmd", "one"))
        assertNull(CommandHistory.get(0))
        assertNull(CommandHistory.get(2))
        assertNull(CommandHistory.get(-1))
    }

    @Test
    fun `list returns all entries in chronological order`() {
        CommandHistory.record(arrayOf("a"))
        CommandHistory.record(arrayOf("b"))
        CommandHistory.record(arrayOf("c"))
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), CommandHistory.list().map { it.args })
    }

    @Test
    fun `clear empties the history`() {
        CommandHistory.record(arrayOf("file", "create", "/tmp/x"))
        CommandHistory.clear()
        assertEquals(0, CommandHistory.size())
        assertNull(CommandHistory.last())
    }

    @Test
    fun `max size evicts oldest entries`() {
        val max = CommandHistory.MAX_SIZE
        repeat(max + 5) { i -> CommandHistory.record(arrayOf("cmd", "$i")) }
        assertEquals(max, CommandHistory.size())
        assertEquals(listOf("cmd", "5"), CommandHistory.get(1)?.args)
        assertEquals(listOf("cmd", "${max + 4}"), CommandHistory.last()?.args)
    }

    @Test
    fun `withSuppressedRecording prevents record from adding entries`() {
        CommandHistory.withSuppressedRecording {
            CommandHistory.record(arrayOf("file", "create", "/tmp/x"))
        }
        assertEquals(0, CommandHistory.size())
    }

    @Test
    fun `withSuppressedRecording is re-entrant`() {
        CommandHistory.withSuppressedRecording {
            CommandHistory.withSuppressedRecording {
                CommandHistory.record(arrayOf("inner"))
            }
            CommandHistory.record(arrayOf("outer still suppressed"))
        }
        assertEquals(0, CommandHistory.size())
    }

    @Test
    fun `withSuppressedRecording re-enables recording after outermost block exits`() {
        CommandHistory.withSuppressedRecording {
            CommandHistory.withSuppressedRecording { }
        }
        CommandHistory.record(arrayOf("after"))
        assertEquals(1, CommandHistory.size())
    }

    @Test
    fun `withSuppressedRecording restores recording after exception`() {
        runCatching {
            CommandHistory.withSuppressedRecording { throw RuntimeException("boom") }
        }
        CommandHistory.record(arrayOf("after exception"))
        assertEquals(1, CommandHistory.size())
    }

    @Test
    fun `withSuppressedRecording returns value from block`() {
        val result = CommandHistory.withSuppressedRecording { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `record writes file to disk`() {
        val path = tempDir.toPath().resolve("cmd_history.txt")
        CommandHistory.record(arrayOf("file", "create", "/tmp/x"))
        assertTrue(Files.exists(path))
        assertTrue(Files.size(path) > 0)
    }

    @Test
    fun `load restores entries from persisted file`() {
        val path = tempDir.toPath().resolve("cmd_history.txt")
        CommandHistory.record(arrayOf("file", "create", "/tmp/restore"))

        CommandHistory.configureForTest(path)
        assertEquals(0, CommandHistory.size())

        CommandHistory.load()
        assertEquals(1, CommandHistory.size())
        assertEquals(listOf("file", "create", "/tmp/restore"), CommandHistory.last()?.args)
    }

    @Test
    fun `args with special characters survive round-trip`() {
        val weird = "line one\nline two\ttab\r\nwindows \"quoted\""
        CommandHistory.record(arrayOf("file", "create", "/tmp/x", "-c", weird))

        val path = tempDir.toPath().resolve("cmd_history.txt")
        CommandHistory.configureForTest(path)
        CommandHistory.load()

        assertEquals(weird, CommandHistory.last()?.args?.get(4))
    }

    @Test
    fun `clear persists empty state to disk`() {
        val path = tempDir.toPath().resolve("cmd_history.txt")
        CommandHistory.record(arrayOf("file", "create", "/tmp/x"))
        CommandHistory.clear()

        CommandHistory.configureForTest(path)
        CommandHistory.load()
        assertEquals(0, CommandHistory.size())
    }

    @Test
    fun `entry has non-blank id and positive timestamp`() {
        CommandHistory.record(arrayOf("test"))
        val entry = CommandHistory.last()
        assertNotNull(entry)
        assertTrue(entry.id.isNotBlank())
        assertTrue(entry.timestamp > 0)
    }
}
