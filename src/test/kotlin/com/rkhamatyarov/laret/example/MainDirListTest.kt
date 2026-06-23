package com.rkhamatyarov.laret.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainDirListTest {

    @TempDir
    lateinit var tempDir: Path

    private fun write(name: String, content: String = "x"): File {
        val file = tempDir.resolve(name).toFile()
        Files.writeString(file.toPath(), content)
        return file
    }

    private fun writeHidden(name: String, content: String = "x"): File {
        val file = write(name, content)
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            Files.setAttribute(file.toPath(), "dos:hidden", true)
        }
        return file
    }

    @Test
    fun test_lists_visible_entries_sorted_by_name() {
        write("charlie.txt")
        write("alpha.txt")
        write("bravo.txt")

        val names = listDirEntries(tempDir.toFile(), includeHidden = false, maxSize = 0).map { it.name }

        assertEquals(listOf("alpha.txt", "bravo.txt", "charlie.txt"), names)
    }

    @Test
    fun test_excludes_hidden_entries_unless_included() {
        write("visible.txt")
        writeHidden(".hidden")

        val withoutHidden = listDirEntries(tempDir.toFile(), includeHidden = false, maxSize = 0)
        val withHidden = listDirEntries(tempDir.toFile(), includeHidden = true, maxSize = 0)

        assertEquals(listOf("visible.txt"), withoutHidden.map { it.name })
        assertTrue(withHidden.any { it.name == ".hidden" })
    }

    @Test
    fun test_applies_max_size_filter_when_positive() {
        write("small.txt", "ab")
        write("large.txt", "abcdefghij")

        val filtered = listDirEntries(tempDir.toFile(), includeHidden = false, maxSize = 5)

        assertEquals(listOf("small.txt"), filtered.map { it.name })
    }

    @Test
    fun test_max_size_of_zero_disables_the_size_filter() {
        write("small.txt", "ab")
        write("large.txt", "abcdefghij")

        val all = listDirEntries(tempDir.toFile(), includeHidden = false, maxSize = 0)

        assertEquals(2, all.size)
    }

    @Test
    fun test_non_directory_path_yields_no_entries() {
        val file = write("notadir.txt")

        val entries = listDirEntries(file, includeHidden = false, maxSize = 0)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun test_plain_and_structured_counts_share_one_filtered_set() {
        write("keep-a.txt", "ab")
        write("keep-b.txt", "abc")
        writeHidden(".hidden", "ab")
        write("toobig.txt", "abcdefghij")

        val rendered = listDirEntries(tempDir.toFile(), includeHidden = false, maxSize = 5)
        val summaryCount = listDirEntries(tempDir.toFile(), includeHidden = false, maxSize = 5).size

        assertEquals(rendered.size, summaryCount)
        assertEquals(2, rendered.size)
        assertFalse(rendered.any { it.name == ".hidden" || it.name == "toobig.txt" })
    }
}
