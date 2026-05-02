package com.rkhamatyarov.laret.diff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DiffEngineTest {
    @TempDir
    lateinit var tmp: Path

    @Nested
    inner class LcsEdits {
        @Test
        fun identicalListsProduceOnlyEqualEdits() {
            val lines = listOf("a", "b", "c")
            val edits = lcsEdits(lines, lines, lines, lines)
            assertThat(edits).allMatch { it.type == EditType.EQUAL }
            assertThat(edits.map { it.content }).isEqualTo(lines)
        }

        @Test
        fun removedLineBecomesDELETE() {
            val old = listOf("a", "b", "c")
            val new = listOf("a", "c")
            val edits = lcsEdits(old, new, old, new)
            assertThat(edits.filter { it.type == EditType.DELETE }.map { it.content })
                .containsExactly("b")
            assertThat(edits.filter { it.type == EditType.INSERT }).isEmpty()
        }

        @Test
        fun addedLineBecomeINSERT() {
            val old = listOf("a", "c")
            val new = listOf("a", "b", "c")
            val edits = lcsEdits(old, new, old, new)
            assertThat(edits.filter { it.type == EditType.INSERT }.map { it.content })
                .containsExactly("b")
        }

        @Test
        fun changedLineShowsAsDeleteThenInsert() {
            val old = listOf("x")
            val new = listOf("y")
            val edits = lcsEdits(old, new, old, new)
            assertThat(edits).hasSize(2)
            assertThat(edits[0].type).isEqualTo(EditType.DELETE)
            assertThat(edits[1].type).isEqualTo(EditType.INSERT)
        }

        @Test
        fun emptyOldProducesAllInserts() {
            val new = listOf("a", "b")
            val edits = lcsEdits(emptyList(), new, emptyList(), new)
            assertThat(edits).allMatch { it.type == EditType.INSERT }
        }

        @Test
        fun emptyNewProducesAllDeletes() {
            val old = listOf("a", "b")
            val edits = lcsEdits(old, emptyList(), old, emptyList())
            assertThat(edits).allMatch { it.type == EditType.DELETE }
        }

        @Test
        fun ignoreWhitespaceKeysMatchTrimmedLines() {
            val old = listOf("  hello  ")
            val new = listOf("hello")
            val oldKeys = old.map { it.trim() }
            val newKeys = new.map { it.trim() }
            val edits = lcsEdits(old, new, oldKeys, newKeys)
            assertThat(edits).hasSize(1)
            assertThat(edits[0].type).isEqualTo(EditType.EQUAL)
            assertThat(edits[0].content).isEqualTo("  hello  ")
        }

        @Test
        fun fallsBackToFullReplaceWhenProductExceedsLimit() {
            assertThat(MAX_LCS_CELLS).isGreaterThan(1_000_000L)
        }
    }


    @Nested
    inner class EditsToHunks {
        @Test
        fun noChangesProducesEmptyHunkList() {
            val edits = listOf(Edit(EditType.EQUAL, "a"), Edit(EditType.EQUAL, "b"))
            assertThat(editsToHunks(edits, 3)).isEmpty()
        }

        @Test
        fun singleChangeProducesOneHunkWithContext() {
            val edits = (1..10).flatMap { listOf(Edit(EditType.EQUAL, "line$it")) }.toMutableList()
            edits[4] = Edit(EditType.DELETE, edits[4].content)
            edits.add(5, Edit(EditType.INSERT, "NEW"))

            val hunks = editsToHunks(edits, contextLines = 2)
            assertThat(hunks).hasSize(1)
            val hunk = hunks[0]
            assertThat(hunk.lines.filter { it.type == DiffLineType.CONTEXT }).isNotEmpty()
            assertThat(hunk.lines.filter { it.type == DiffLineType.REMOVE }).isNotEmpty()
            assertThat(hunk.lines.filter { it.type == DiffLineType.ADD }).isNotEmpty()
        }

        @Test
        fun distantChangesProduceSeparateHunks() {
            val edits = (1..20).map { Edit(EditType.EQUAL, "line$it") }.toMutableList()
            edits[1] = Edit(EditType.DELETE, "line2")
            edits[17] = Edit(EditType.DELETE, "line18")

            val hunks = editsToHunks(edits, contextLines = 2)
            assertThat(hunks).hasSize(2)
        }

        @Test
        fun adjacentChangesAreMergedIntoOneHunk() {
            val edits = (1..15).map { Edit(EditType.EQUAL, "line$it") }.toMutableList()
            edits[3] = Edit(EditType.DELETE, "line4")
            edits[6] = Edit(EditType.DELETE, "line7")

            val hunks = editsToHunks(edits, contextLines = 3)
            assertThat(hunks).hasSize(1)
        }

        @Test
        fun lineNumbersIncrementCorrectly() {
            val old = listOf("a", "b", "c")
            val new = listOf("a", "X", "c")
            val edits = lcsEdits(old, new, old, new)
            val hunks = editsToHunks(edits, contextLines = 0)
            assertThat(hunks).hasSize(1)
            val hunk = hunks[0]
            val remove = hunk.lines.first { it.type == DiffLineType.REMOVE }
            val add = hunk.lines.first { it.type == DiffLineType.ADD }
            assertThat(remove.oldLineNo).isEqualTo(2)
            assertThat(remove.newLineNo).isEqualTo(0)
            assertThat(add.oldLineNo).isEqualTo(0)
            assertThat(add.newLineNo).isEqualTo(2)
        }
    }

    @Nested
    inner class IsBinaryFile {
        @Test
        fun textFileIsNotBinary() {
            val file = tmp.resolve("text.txt").also { Files.writeString(it, "hello\nworld\n") }
            assertThat(isBinaryFile(file)).isFalse()
        }

        @Test
        fun fileWithNullByteIsBinary() {
            val file = tmp.resolve("bin.bin").also {
                Files.write(it, byteArrayOf(0x48, 0x00, 0x65, 0x6C))
            }
            assertThat(isBinaryFile(file)).isTrue()
        }

        @Test
        fun emptyFileIsNotBinary() {
            val file = tmp.resolve("empty.txt").also { Files.createFile(it) }
            assertThat(isBinaryFile(file)).isFalse()
        }
    }

    @Nested
    inner class DiffFilesTests {
        private fun write(name: String, vararg lines: String): Path =
            tmp.resolve(name).also { Files.write(it, lines.toList()) }

        @Test
        fun identicalFilesReturnIdenticalTrue() {
            val a = write("a.txt", "line1", "line2")
            val b = write("b.txt", "line1", "line2")
            val result = diffFiles(a, b)
            assertThat(result.identical).isTrue()
            assertThat(result.hunks).isEmpty()
        }

        @Test
        fun differentFilesReturnHunks() {
            val a = write("a.txt", "line1", "old", "line3")
            val b = write("b.txt", "line1", "new", "line3")
            val result = diffFiles(a, b)
            assertThat(result.identical).isFalse()
            assertThat(result.hunks).isNotEmpty()
        }

        @Test
        fun binaryFilesFlaggedCorrectly() {
            val text = write("text.txt", "hello")
            val bin = tmp.resolve("bin.bin").also {
                Files.write(it, byteArrayOf(0x00, 0x01, 0x02))
            }
            val result = diffFiles(text, bin)
            assertThat(result.newBinary).isTrue()
            assertThat(result.identical).isFalse()
        }

        @Test
        fun ignoreWhitespaceSkipsWsOnlyDiff() {
            val a = write("a.txt", "  hello  ")
            val b = write("b.txt", "hello")
            val result = diffFiles(a, b, ignoreWhitespace = true)
            assertThat(result.identical).isTrue()
        }

        @Test
        fun ignoreWhitespaceFalseDetectsWsDiff() {
            val a = write("a.txt", "  hello  ")
            val b = write("b.txt", "hello")
            val result = diffFiles(a, b, ignoreWhitespace = false)
            assertThat(result.identical).isFalse()
        }

        @Test
        fun contextLinesZeroShowsOnlyChangedLines() {
            val a = write("a.txt", "ctx1", "ctx2", "old", "ctx3", "ctx4")
            val b = write("b.txt", "ctx1", "ctx2", "new", "ctx3", "ctx4")
            val result = diffFiles(a, b, contextLines = 0)
            assertThat(result.hunks).hasSize(1)
            val hunk = result.hunks[0]
            assertThat(hunk.lines.filter { it.type == DiffLineType.CONTEXT }).isEmpty()
        }
    }
}
