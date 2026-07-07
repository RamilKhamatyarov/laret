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

@DisplayName("DocIndexCommand")
class DocIndexCommandTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeReadme(text: String): Path {
        val readme = tempDir.resolve("README.md")
        Files.writeString(readme, text)
        return readme
    }

    @Test
    fun test_fromReadme_injects_frontmatter_and_strips_leading_h1() {
        val readme = writeReadme("# Laret\n\nA CLI framework.\n\n## Install\n")
        val docsDir = tempDir.resolve("docs")

        val written = DocIndexCommand().fromReadme(readme, docsDir, title = "Laret")

        assertEquals(docsDir.resolve("index.md"), written)
        val content = Files.readString(written)
        assertTrue(content.startsWith("---\ntitle: Laret\n---"))
        assertFalse(content.contains("# Laret"), "leading H1 should be stripped to avoid a duplicate title")
        assertTrue(content.contains("A CLI framework."))
        assertTrue(content.contains("## Install"), "non-leading headings must be preserved")
    }

    @Test
    fun test_fromReadme_does_not_modify_the_source_readme() {
        val original = "# Laret\n\nBody.\n"
        val readme = writeReadme(original)

        DocIndexCommand().fromReadme(readme, tempDir.resolve("docs"))

        assertEquals(original, Files.readString(readme))
    }

    @Test
    fun test_fromReadme_honours_language_subdirectory() {
        val readme = writeReadme("# Laret\n\nBody.\n")
        val docsDir = tempDir.resolve("docs")

        val written = DocIndexCommand().fromReadme(readme, docsDir, lang = "en")

        assertEquals(docsDir.resolve("en/index.md"), written)
    }

    @Test
    fun test_dry_run_filesystem_narrates_without_writing_index() {
        val readme = writeReadme("# Laret\n\nBody.\n")
        val docsDir = tempDir.resolve("docs")

        DocIndexCommand(DryRunFileSystem()).fromReadme(readme, docsDir)

        assertFalse(Files.exists(docsDir.resolve("index.md")))
    }
}
