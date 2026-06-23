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
        val paths = files.map { it.relativePath }

        assertEquals("en/file/create.md", files.first().relativePath)
        assertTrue(paths.contains("en/file/index.md"), "group index expected")
        assertTrue(paths.contains("en/index.md"), "root index expected")
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

    @Test
    fun test_markdown_generator_lists_aliases_on_command_page() {
        val aliased = command.copy(aliases = listOf("c", "new"))
        val app = CliApp(name = "laret", groups = listOf(CommandGroup(name = "file", commands = listOf(aliased))))

        val page = MarkdownDocGenerator(providerReturning(richProse))
            .generate(app, "en").first { it.relativePath == "en/file/create.md" }.content

        assertTrue(page.contains("**Aliases:** `c`, `new`"))
    }

    @Test
    fun test_hidden_command_excluded_unless_include_hidden() {
        val hidden = Command(name = "secret", description = "Internal", hidden = true)
        val app = CliApp(name = "laret", groups = listOf(CommandGroup(name = "file", commands = listOf(hidden))))
        val generator = MarkdownDocGenerator(providerReturning(richProse))

        val withoutHidden = generator.generate(app, "en", includeHidden = false).map { it.relativePath }
        val withHidden = generator.generate(app, "en", includeHidden = true)

        assertTrue(withoutHidden.none { it == "en/file/secret.md" })
        val page = withHidden.first { it.relativePath == "en/file/secret.md" }.content
        assertTrue(page.contains("[INTERNAL]"), "hidden page must carry an [INTERNAL] badge")
    }

    @Test
    fun test_hidden_command_absent_from_group_index_and_nav() {
        val visible = Command(name = "create", description = "Create")
        val hidden = Command(name = "secret", description = "Internal", hidden = true)
        val app = CliApp(
            name = "laret",
            groups = listOf(CommandGroup(name = "file", commands = listOf(visible, hidden))),
        )
        val generator = MarkdownDocGenerator(providerReturning(richProse))

        val groupIndex = generator.generate(app, "en", includeHidden = true)
            .first { it.relativePath == "en/file/index.md" }.content
        val nav = generator.mkdocsYaml(app, listOf("en")).content

        assertTrue(groupIndex.contains("create.md"))
        assertTrue(!groupIndex.contains("secret.md"), "hidden command must not appear in the index listing")
        assertTrue(!nav.contains("secret.md"), "hidden command must not appear in mkdocs nav")
    }

    @Test
    fun test_mkdocs_yaml_covers_every_requested_language() {
        val nav = MarkdownDocGenerator(providerReturning(richProse)).mkdocsYaml(app, listOf("en", "es"))

        assertEquals("mkdocs.yml", nav.relativePath)
        assertTrue(nav.content.contains("site_name: laret"))
        assertTrue(nav.content.contains("en/file/create.md"))
        assertTrue(nav.content.contains("es/file/create.md"))
    }
}
