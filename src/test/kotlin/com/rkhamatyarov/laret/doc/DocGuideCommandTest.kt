package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.model.fs.DryRunFileSystem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("DocGuideCommand")
class DocGuideCommandTest {

    @TempDir
    lateinit var outputDir: Path

    @Test
    fun test_create_writes_guide_with_frontmatter_and_todo_body() {
        val written = DocGuideCommand().create("Quick Start", outputDir, lang = "en")

        assertEquals(outputDir.resolve("en/guides/quick-start.md"), written)
        val content = Files.readString(written)
        assertTrue(content.startsWith("---"))
        assertTrue(content.contains("title: Quick Start"))
        assertTrue(content.contains("TODO: Write guide."))
    }

    @Test
    fun test_create_without_lang_places_guide_at_docs_root() {
        val written = DocGuideCommand().create("installation", outputDir)

        assertEquals(outputDir.resolve("guides/installation.md"), written)
        assertTrue(Files.exists(written))
    }

    @Test
    fun test_exists_reflects_presence_on_disk() {
        val guide = DocGuideCommand()
        assertFalse(guide.exists("installation", outputDir, lang = "en"))

        guide.create("installation", outputDir, lang = "en")
        assertTrue(guide.exists("installation", outputDir, lang = "en"))
    }

    @Test
    fun test_dry_run_filesystem_narrates_without_touching_disk() {
        DocGuideCommand(DryRunFileSystem()).create("installation", outputDir, lang = "en")

        assertFalse(Files.exists(outputDir.resolve("en/guides/installation.md")))
    }
}
