package com.rkhamatyarov.laret.completion.completers

import com.rkhamatyarov.laret.completion.CompletionContext
import com.rkhamatyarov.laret.completion.CompletionResult
import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("FileCompleter")
class FileCompleterTest {

    private val fs = mockk<LaretFileSystem>()

    private fun stubDir(dir: Path, entries: List<Path>, directories: Set<Path> = emptySet()) {
        every { fs.exists(dir) } returns true
        every { fs.listFiles(dir) } returns entries
        entries.forEach { every { fs.isDirectory(it) } returns (it in directories) }
    }

    @Test
    fun test_empty_prefix_lists_current_directory() {
        stubDir(Paths.get("."), listOf(Paths.get("a.txt"), Paths.get("b.txt")))

        val result = FileCompleter(fs).complete(CompletionContext(""))

        assertEquals(listOf("a.txt", "b.txt"), result.candidates.map { it.value })
        assertEquals(CompletionResult.NO_FILE_COMP, result.directive)
    }

    @Test
    fun test_name_prefix_filters_entries() {
        stubDir(Paths.get("."), listOf(Paths.get("notes.md"), Paths.get("build.log")))

        val result = FileCompleter(fs).complete(CompletionContext("no"))

        assertEquals(listOf("notes.md"), result.candidates.map { it.value })
    }

    @Test
    fun test_directory_prefix_lists_that_directory_and_keeps_prefix() {
        val src = Paths.get("src/")
        stubDir(
            src,
            listOf(Paths.get("src/main"), Paths.get("src/test")),
            directories = setOf(Paths.get("src/main"), Paths.get("src/test")),
        )

        val result = FileCompleter(fs).complete(CompletionContext("src/"))

        assertEquals(listOf("src/main/", "src/test/"), result.candidates.map { it.value })
    }

    @Test
    fun test_single_directory_candidate_sets_no_space_directive() {
        stubDir(Paths.get("."), listOf(Paths.get("src")), directories = setOf(Paths.get("src")))

        val result = FileCompleter(fs).complete(CompletionContext("sr"))

        assertEquals(listOf("src/"), result.candidates.map { it.value })
        assertEquals(CompletionResult.NO_SPACE or CompletionResult.NO_FILE_COMP, result.directive)
    }

    @Test
    fun test_missing_directory_returns_empty_without_touching_listing() {
        every { fs.exists(any<Path>()) } returns false

        val result = FileCompleter(fs).complete(CompletionContext("ghost/"))

        assertTrue(result.candidates.isEmpty())
        assertEquals(CompletionResult.NO_FILE_COMP, result.directive)
    }
}
