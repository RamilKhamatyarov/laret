package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.prose.Prose
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("DocGenerateCommand")
class DocGenerateCommandTest {

    @TempDir
    lateinit var outputDir: Path

    private val app = CliApp(
        name = "laret",
        version = "1.0.0",
        groups = listOf(
            CommandGroup(
                name = "file",
                commands = listOf(Command(name = "create", description = "Create a new file")),
            ),
        ),
    )

    private val provider: ProseProvider = mockk {
        every { resolve(any(), any(), any()) } returns Prose(
            title = "Create",
            summary = "Create a file.",
            synopsis = null,
            examples = emptyList(),
            seeAlso = emptyList(),
            body = "Body.",
        )
    }

    @Test
    fun test_markdown_generation_writes_files_to_disk() {
        val written = DocGenerateCommand(app).run(DocFormat.MARKDOWN, "en", outputDir, provider)

        val commandPage = outputDir.resolve("en/file/create.md")
        assertTrue(written.contains(commandPage))
        assertTrue(Files.exists(commandPage))
        assertTrue(Files.exists(outputDir.resolve("mkdocs.yml")), "mkdocs.yml must be generated for Markdown")
        assertTrue(Files.exists(outputDir.resolve("en/index.md")))
    }

    @Test
    fun test_man_generation_writes_flat_man1_tree() {
        DocGenerateCommand(app).run(DocFormat.MAN, "en", outputDir, provider)

        assertTrue(Files.exists(outputDir.resolve("man1/laret-file-create.1")))
    }

    @Test
    fun test_lang_all_fans_out_markdown_over_supported_languages() {
        DocGenerateCommand(app).run(DocFormat.MARKDOWN, DocGenerateCommand.ALL, outputDir, provider)

        assertTrue(Files.exists(outputDir.resolve("en/file/create.md")))
        assertTrue(Files.exists(outputDir.resolve("es/file/create.md")))
    }

    @Test
    fun test_strict_mode_fails_on_missing_prose_file() {
        every { provider.exists(any(), any(), any()) } returns false

        val error = runCatching {
            DocGenerateCommand(app).run(DocFormat.MARKDOWN, "en", outputDir, provider, strict = true)
        }.exceptionOrNull()

        assertTrue(error is DocValidationException)
        assertTrue(error.problems.any { it.contains("missing prose") })
    }

    @Test
    fun test_strict_mode_fails_on_broken_see_also_link() {
        every { provider.exists(any(), any(), any()) } returns true
        every { provider.resolve(any(), any(), any()) } returns Prose(
            title = "Create",
            summary = "Create a file.",
            synopsis = null,
            examples = emptyList(),
            seeAlso = listOf("laret-file-nonexistent"),
            body = "Body.",
        )

        val error = runCatching {
            DocGenerateCommand(app).run(DocFormat.MARKDOWN, "en", outputDir, provider, strict = true)
        }.exceptionOrNull()

        assertTrue(error is DocValidationException)
        assertTrue(error.problems.any { it.contains("broken see_also") })
    }

    @Test
    fun test_strict_mode_fails_on_broken_internal_link_in_prose_body() {
        every { provider.exists(any(), any(), any()) } returns true
        every { provider.resolve(any(), any(), any()) } returns Prose(
            title = "Create",
            summary = "Create a file.",
            synopsis = null,
            examples = emptyList(),
            seeAlso = emptyList(),
            body = "See [the missing page](nope.md) for details.",
        )

        val error = runCatching {
            DocGenerateCommand(app).run(DocFormat.MARKDOWN, "en", outputDir, provider, strict = true)
        }.exceptionOrNull()

        assertTrue(error is DocValidationException)
        assertTrue(error.problems.any { it.contains("broken link") })
    }

    @Test
    fun test_strict_mode_passes_when_files_present_and_links_valid() {
        every { provider.exists(any(), any(), any()) } returns true

        val written = DocGenerateCommand(app).run(DocFormat.MARKDOWN, "en", outputDir, provider, strict = true)

        assertTrue(written.isNotEmpty())
    }

    @Test
    fun test_lang_all_emits_man_pages_once() {
        val written = DocGenerateCommand(app).run(DocFormat.MAN, DocGenerateCommand.ALL, outputDir, provider)

        assertEquals(1, written.size)
    }
}
