package com.rkhamatyarov.laret.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.UUID

object CommandHistory {

    data class CommandEntry(val id: String, val args: List<String>, val timestamp: Long)

    const val MAX_SIZE = 100

    private val entries = ArrayDeque<CommandEntry>()

    @Volatile
    private var suppressDepth: Int = 0

    private var historyFile: Path = defaultHistoryFile()

    fun configureForTest(path: Path) {
        historyFile = path
        entries.clear()
    }

    fun resetForTest() {
        historyFile = defaultHistoryFile()
        entries.clear()
    }

    fun record(args: Array<String>) {
        if (suppressDepth > 0) return
        if (args.isEmpty()) return
        entries.addLast(
            CommandEntry(
                id = UUID.randomUUID().toString(),
                args = args.toList(),
                timestamp = System.currentTimeMillis(),
            ),
        )
        while (entries.size > MAX_SIZE) entries.removeFirst()
        persist()
    }

    fun last(): CommandEntry? = entries.lastOrNull()

    fun get(index: Int): CommandEntry? {
        if (index < 1 || index > entries.size) return null
        return entries.toList()[index - 1]
    }

    fun list(): List<CommandEntry> = entries.toList()

    fun clear() {
        entries.clear()
        persist()
    }

    fun size(): Int = entries.size

    fun <T> withSuppressedRecording(block: () -> T): T {
        suppressDepth++
        return try {
            block()
        } finally {
            suppressDepth--
        }
    }

    fun load() {
        if (!Files.exists(historyFile)) return
        try {
            deserialize(Files.readString(historyFile))
        } catch (e: Exception) {
            System.err.println("Warning: failed to load command history from $historyFile: ${e.message}")
        }
    }

    private fun persist() {
        try {
            Files.createDirectories(historyFile.parent)
            Files.writeString(historyFile, serialize())
        } catch (e: Exception) {
            System.err.println("Warning: failed to persist command history to $historyFile: ${e.message}")
        }
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String = String(Base64.getDecoder().decode(s), Charsets.UTF_8)

    private fun encodeEntry(e: CommandEntry): String = buildList {
        add(b64(e.id))
        add(e.timestamp.toString())
        add(e.args.size.toString())
        e.args.forEach { add(b64(it)) }
    }.joinToString("\t")

    private fun decodeEntry(encoded: String): CommandEntry? = runCatching {
        val parts = encoded.split("\t")
        var i = 0
        val id = unb64(parts[i++])
        val timestamp = parts[i++].toLong()
        val count = parts[i++].toInt()
        val args = (0 until count).map { unb64(parts[i++]) }
        CommandEntry(id, args, timestamp)
    }.getOrNull()

    private fun serialize(): String = buildString {
        entries.forEach { appendLine(encodeEntry(it)) }
    }

    private fun deserialize(text: String) {
        val parsed = mutableListOf<CommandEntry>()
        for (line in text.lines()) {
            if (line.isBlank()) continue
            decodeEntry(line)?.let { parsed.add(it) }
        }
        entries.clear()
        parsed.forEach { entries.addLast(it) }
    }

    private fun defaultHistoryFile(): Path = Paths.get(
        System.getProperty("user.home") ?: ".",
        ".laret",
        "cmd_history.txt",
    )
}
