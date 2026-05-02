package com.rkhamatyarov.laret.diff

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

enum class DiffFormat(val id: String) {
    UNIFIED("unified"),
    PLAIN("plain"),
    JSON("json"),
    ;

    companion object {
        fun fromId(id: String): DiffFormat? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

interface DiffFormatter {
    fun render(result: DiffResult): String
}

/**
 * Renders in the standard unified diff format, compatible with `patch` and
 * code-review tools that consume the `---`/`+++`/`@@` convention.
 */
class UnifiedFormatter : DiffFormatter {
    override fun render(result: DiffResult): String = buildString {
        when {
            result.oldBinary && result.newBinary ->
                append("Binary files ${result.oldPath} and ${result.newPath} differ\n")
            result.oldBinary ->
                append("Binary file ${result.oldPath} and text file ${result.newPath} differ\n")
            result.newBinary ->
                append("Text file ${result.oldPath} and binary file ${result.newPath} differ\n")
            result.identical -> return ""
            else -> {
                append("--- ${result.oldPath}\n")
                append("+++ ${result.newPath}\n")
                result.hunks.forEach { hunk ->
                    append(hunkHeader(hunk))
                    hunk.lines.forEach { line ->
                        append(linePrefix(line.type))
                        append(line.content)
                        append("\n")
                    }
                }
            }
        }
    }

    private fun hunkHeader(h: DiffHunk): String {
        val old = if (h.oldCount == 1) "${h.oldStart}" else "${h.oldStart},${h.oldCount}"
        val new = if (h.newCount == 1) "${h.newStart}" else "${h.newStart},${h.newCount}"
        return "@@ -$old +$new @@\n"
    }

    private fun linePrefix(type: DiffLineType) = when (type) {
        DiffLineType.CONTEXT -> " "
        DiffLineType.ADD -> "+"
        DiffLineType.REMOVE -> "-"
    }
}

/**
 * Human-readable marker-based output: `+ ` for additions, `- ` for removals,
 * two spaces for context.  Hunks separated by `...` when not adjacent.
 */
class PlainFormatter : DiffFormatter {
    override fun render(result: DiffResult): String = buildString {
        when {
            result.oldBinary || result.newBinary ->
                append("Binary files differ: ${result.oldPath} vs ${result.newPath}\n")
            result.identical ->
                append("Files are identical: ${result.oldPath}\n")
            else -> {
                append("--- ${result.oldPath}\n")
                append("+++ ${result.newPath}\n")
                result.hunks.forEachIndexed { idx, hunk ->
                    if (idx > 0) append("...\n")
                    hunk.lines.forEach { line ->
                        val prefix = when (line.type) {
                            DiffLineType.CONTEXT -> "  "
                            DiffLineType.ADD -> "+ "
                            DiffLineType.REMOVE -> "- "
                        }
                        append(prefix)
                        append(line.content)
                        append("\n")
                    }
                }
            }
        }
    }
}

/**
 * Machine-readable JSON output — suitable for programmatic diff consumers
 * and CI integrations that need structured change data.
 */
class JsonDiffFormatter : DiffFormatter {
    private val mapper = jacksonObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun render(result: DiffResult): String {
        val payload = mapOf(
            "oldPath" to result.oldPath,
            "newPath" to result.newPath,
            "identical" to result.identical,
            "oldBinary" to result.oldBinary,
            "newBinary" to result.newBinary,
            "hunks" to result.hunks.map { hunk ->
                mapOf(
                    "oldStart" to hunk.oldStart,
                    "oldCount" to hunk.oldCount,
                    "newStart" to hunk.newStart,
                    "newCount" to hunk.newCount,
                    "lines" to hunk.lines.map { line ->
                        mapOf(
                            "type" to line.type.name,
                            "content" to line.content,
                            "oldLineNo" to line.oldLineNo,
                            "newLineNo" to line.newLineNo,
                        )
                    },
                )
            },
        )
        return mapper.writeValueAsString(payload)
    }
}
