package com.rkhamatyarov.laret.diff

import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path


enum class DiffLineType { CONTEXT, ADD, REMOVE }

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNo: Int,
    val newLineNo: Int,
)

data class DiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val lines: List<DiffLine>,
)

data class DiffResult(
    val oldPath: String,
    val newPath: String,
    val hunks: List<DiffHunk>,
    val identical: Boolean,
    val oldBinary: Boolean = false,
    val newBinary: Boolean = false,
)


/**
 * Compare two files and return a [DiffResult].
 *
 * Uses a pure-Kotlin LCS algorithm — zero external dependencies, GraalVM
 * native-image compatible.  Files larger than [MAX_LCS_CELLS] cells fall back
 * to a full-replace diff to avoid OOM.
 */
fun diffFiles(
    oldPath: Path,
    newPath: Path,
    ignoreWhitespace: Boolean = false,
    contextLines: Int = 3,
): DiffResult {
    val oldStr = oldPath.toString()
    val newStr = newPath.toString()

    val oldBinary = isBinaryFile(oldPath)
    val newBinary = isBinaryFile(newPath)
    if (oldBinary || newBinary) {
        return DiffResult(oldStr, newStr, emptyList(), identical = false, oldBinary, newBinary)
    }

    val oldLines = try {
        Files.readAllLines(oldPath)
    } catch (_: MalformedInputException) {
        return DiffResult(oldStr, newStr, emptyList(), identical = false, oldBinary = true)
    }
    val newLines = try {
        Files.readAllLines(newPath)
    } catch (_: MalformedInputException) {
        return DiffResult(oldStr, newStr, emptyList(), identical = false, newBinary = true)
    }

    val oldKeys = if (ignoreWhitespace) oldLines.map { it.trim() } else oldLines
    val newKeys = if (ignoreWhitespace) newLines.map { it.trim() } else newLines

    if (oldKeys == newKeys) return DiffResult(oldStr, newStr, emptyList(), identical = true)

    val edits = lcsEdits(oldLines, newLines, oldKeys, newKeys)
    if (edits.none { it.type != EditType.EQUAL }) {
        return DiffResult(oldStr, newStr, emptyList(), identical = true)
    }

    return DiffResult(oldStr, newStr, editsToHunks(edits, contextLines), identical = false)
}

/**
 * Returns true when [path] contains a null byte in its first 8 KiB.
 * Null bytes are the standard heuristic used by `git` and GNU `diff` to
 * classify a file as binary.
 */
fun isBinaryFile(path: Path): Boolean {
    if (!Files.isReadable(path)) return false
    Files.newInputStream(path).use { stream ->
        val buffer = ByteArray(8192)
        val read = stream.read(buffer)
        if (read > 0) return buffer.take(read).any { it.toInt() == 0 }
    }
    return false
}

internal enum class EditType { EQUAL, INSERT, DELETE }
internal data class Edit(val type: EditType, val content: String)

internal const val MAX_LCS_CELLS = 4_000_000L

internal fun lcsEdits(
    oldLines: List<String>,
    newLines: List<String>,
    oldKeys: List<String>,
    newKeys: List<String>,
): List<Edit> {
    val m = oldLines.size
    val n = newLines.size

    if (m == 0) return newLines.map { Edit(EditType.INSERT, it) }
    if (n == 0) return oldLines.map { Edit(EditType.DELETE, it) }

    if (m.toLong() * n.toLong() > MAX_LCS_CELLS) {
        return oldLines.map { Edit(EditType.DELETE, it) } +
            newLines.map { Edit(EditType.INSERT, it) }
    }

    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
        for (j in 1..n) {
            dp[i][j] = if (oldKeys[i - 1] == newKeys[j - 1]) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }

    val result = ArrayDeque<Edit>(m + n)
    var i = m
    var j = n
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && oldKeys[i - 1] == newKeys[j - 1] -> {
                result.addFirst(Edit(EditType.EQUAL, oldLines[i - 1]))
                i--; j--
            }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                result.addFirst(Edit(EditType.INSERT, newLines[j - 1]))
                j--
            }
            else -> {
                result.addFirst(Edit(EditType.DELETE, oldLines[i - 1]))
                i--
            }
        }
    }
    return result.toList()
}

internal fun editsToHunks(edits: List<Edit>, contextLines: Int): List<DiffHunk> {
    data class Numbered(val type: EditType, val content: String, val oldNo: Int, val newNo: Int)

    var oldNo = 1
    var newNo = 1
    val numbered = edits.map { e ->
        Numbered(
            e.type, e.content,
            if (e.type == EditType.INSERT) 0 else oldNo,
            if (e.type == EditType.DELETE) 0 else newNo,
        ).also {
            if (e.type != EditType.INSERT) oldNo++
            if (e.type != EditType.DELETE) newNo++
        }
    }

    val changeIdxs = numbered.indices.filter { numbered[it].type != EditType.EQUAL }
    if (changeIdxs.isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    var rs = maxOf(0, changeIdxs.first() - contextLines)
    var re = minOf(numbered.lastIndex, changeIdxs.first() + contextLines)

    for (idx in changeIdxs.drop(1)) {
        val ns = maxOf(0, idx - contextLines)
        val ne = minOf(numbered.lastIndex, idx + contextLines)
        if (ns <= re + 1) {
            re = maxOf(re, ne)
        } else {
            ranges += rs..re
            rs = ns; re = ne
        }
    }
    ranges += rs..re

    return ranges.map { range ->
        val hunkLines = range.map { idx ->
            val ne = numbered[idx]
            DiffLine(
                type = when (ne.type) {
                    EditType.EQUAL -> DiffLineType.CONTEXT
                    EditType.INSERT -> DiffLineType.ADD
                    EditType.DELETE -> DiffLineType.REMOVE
                },
                content = ne.content,
                oldLineNo = ne.oldNo,
                newLineNo = ne.newNo,
            )
        }
        val oldSide = hunkLines.filter { it.type != DiffLineType.ADD }
        val newSide = hunkLines.filter { it.type != DiffLineType.REMOVE }
        DiffHunk(
            oldStart = if (oldSide.isEmpty()) 0 else oldSide.first().oldLineNo,
            oldCount = oldSide.size,
            newStart = if (newSide.isEmpty()) 0 else newSide.first().newLineNo,
            newCount = newSide.size,
            lines = hunkLines,
        )
    }
}
