package com.rkhamatyarov.laret.doc.generators

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.prose.Prose
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.model.Option
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("MarkdownDocGenerator")
class MarkdownDocGeneratorTest {

    private val command = Command(
        name = "create",
        description = "Create a new file",
        arguments = listOf(Argument(name = "path", description = "File path", required = true)),
        options = listOf(Option(short = "f", long = "force", description = "Overwrite", default = "")),
    )

    private val app = CliApp(
        name = "laret",
        version = "1.0.0",
        groups = listOf(CommandGroup(name = "file", commands = listOf(command))),
    )

    private fun providerReturning(prose: Prose): ProseProvider = mockk {
        every { resolve(any(), any(), any()) } returns prose
    }

    private val richProse = Prose(
        title = "Create a file",
        summary = "Create a new file.",
        synopsis = "laret file create <path> [--force]",
        examples = listOf("laret file create notes.txt"),
        seeAlso = listOf("laret-file-delete"),
        body = "Long body text.",
    )

    @Test
    fun test_markdown_generator_creates_correct_directory_structure() {
        val files = MarkdownDocGenerator(providerReturning(richProse)).generate(app, "en")

        assertEquals(1, files.size)
        assertEquals("en/file/create.md", files.first().relativePath)
    }

    @Test
    fun test_markdown_generator_emits_expected_sections() {
        val content = MarkdownDocGenerator(providerReturning(richProse)).generate(app, "en").first().content

        assertTrue(content.startsWith("# Create a file"))
        assertTrue(content.contains("## Synopsis"))
        assertTrue(content.contains("## Examples"))
    }

    @Test
    fun test_markdown_generator_renders_options_and_arguments() {
        val content = MarkdownDocGenerator(providerReturning(richProse)).generate(app, "en").first().content

        assertTrue(content.contains("- `<path>` (required) — File path"))
        assertTrue(content.contains("- `-f, --force` — Overwrite"))
    }

    @Test
    fun test_markdown_generator_falls_back_to_default_synopsis_when_absent() {
        val noSynopsis = richProse.copy(synopsis = null)
        val content = MarkdownDocGenerator(providerReturning(noSynopsis)).generate(app, "en").first().content

        assertTrue(content.contains("laret file create <path> [--force <value>]"))
    }

    @Test
    fun test_markdown_generator_is_pure_and_repeatable() {
        val generator = MarkdownDocGenerator(providerReturning(richProse))

        assertEquals(generator.generate(app, "en"), generator.generate(app, "en"))
    }
}
