package com.rkhamatyarov.laret.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.UUID

object UndoManager {

    data class HistoryEntry(
        val id: String,
        val description: String,
        val timestamp: Long,
        val undoArgs: List<String>,
        val redoArgs: List<String>,
    )

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    @Volatile
    private var suppressDepth: Int = 0

    private var historyFile: Path = defaultHistoryFile()

    fun configureForTest(path: Path) {
        historyFile = path
        undoStack.clear()
        redoStack.clear()
    }

    fun resetForTest() {
        historyFile = defaultHistoryFile()
        undoStack.clear()
        redoStack.clear()
    }

    fun push(entry: HistoryEntry, isDryRun: Boolean = false) {
        if (suppressDepth > 0) return
        if (isDryRun) return
        undoStack.addLast(entry)
        redoStack.clear()
        persist()
    }

    fun popUndo(): HistoryEntry? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        persist()
        return entry
    }

    fun popRedo(): HistoryEntry? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        persist()
        return entry
    }

    fun peekUndo(): HistoryEntry? = undoStack.lastOrNull()

    fun peekRedo(): HistoryEntry? = redoStack.lastOrNull()

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undoHistory(): List<HistoryEntry> = undoStack.toList()

    fun redoHistory(): List<HistoryEntry> = redoStack.toList()

    fun <T> withSuppressedRecording(block: () -> T): T {
        suppressDepth++
        return try {
            block()
        } finally {
            suppressDepth--
        }
    }

    fun load() {
        try {
            if (!Files.exists(historyFile)) return
            deserialize(Files.readString(historyFile))
        } catch (_: Exception) {}
    }

    private fun persist() {
        try {
            Files.createDirectories(historyFile.parent)
            Files.writeString(historyFile, serialize())
        } catch (_: Exception) {}
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String = String(Base64.getDecoder().decode(s), Charsets.UTF_8)

    private fun encodeEntry(e: HistoryEntry): String = buildList {
        add(b64(e.id))
        add(b64(e.description))
        add(e.timestamp.toString())
        add(e.undoArgs.size.toString())
        e.undoArgs.forEach { add(b64(it)) }
        add(e.redoArgs.size.toString())
        e.redoArgs.forEach { add(b64(it)) }
    }.joinToString("\t")

    private fun decodeEntry(encoded: String): HistoryEntry? = runCatching {
        val parts = encoded.split("\t")
        var i = 0
        val id = unb64(parts[i++])
        val description = unb64(parts[i++])
        val timestamp = parts[i++].toLong()
        val undoCount = parts[i++].toInt()
        val undoArgs = (0 until undoCount).map { unb64(parts[i++]) }
        val redoCount = parts[i++].toInt()
        val redoArgs = (0 until redoCount).map { unb64(parts[i++]) }
        HistoryEntry(id, description, timestamp, undoArgs, redoArgs)
    }.getOrNull()

    private fun serialize(): String = buildString {
        undoStack.forEach { appendLine("U|${encodeEntry(it)}") }
        redoStack.forEach { appendLine("R|${encodeEntry(it)}") }
    }

    private fun deserialize(text: String) {
        undoStack.clear()
        redoStack.clear()
        for (line in text.lines()) {
            if (line.length < 3) continue
            val entry = decodeEntry(line.substring(2)) ?: continue
            when (line[0]) {
                'U' -> undoStack.addLast(entry)
                'R' -> redoStack.addLast(entry)
            }
        }
    }

    fun newEntry(description: String, undoArgs: List<String>, redoArgs: List<String> = emptyList()) = HistoryEntry(
        id = UUID.randomUUID().toString(),
        description = description,
        timestamp = System.currentTimeMillis(),
        undoArgs = undoArgs,
        redoArgs = redoArgs,
    )

    private fun defaultHistoryFile(): Path = Paths.get(
        System.getProperty("user.home") ?: ".",
        ".laret",
        "history.txt",
    )
}
