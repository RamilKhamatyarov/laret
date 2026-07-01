package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.model.fs.DryRunFileSystem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("DocScaffoldCommand")
class DocScaffoldCommandTest {

    @TempDir
    lateinit var outputDir: Path

    private val app = CliApp(
        name = "laret",
        groups = listOf(
            CommandGroup(
                name = "file",
                commands = listOf(
                    Command(
                        name = "create",
                        description = "Create a new file",
                        arguments = listOf(Argument(name = "path", description = "Target path", required = true)),
                    ),
                    Command(name = "secret", description = "Internal", hidden = true),
                ),
            ),
        ),
    )

    @Test
    fun test_scaffold_creates_skeleton_with_empty_frontmatter() {
        val created = DocScaffoldCommand(app).run("en", outputDir)

        val page = outputDir.resolve("en/file/create.md")
        assertTrue(created.contains(page))
        val content = Files.readString(page)
        assertTrue(content.startsWith("---"))
        assertTrue(content.contains("examples: []"))
        assertTrue(content.contains("see_also: []"))
    }

    @Test
    fun test_scaffold_skips_hidden_commands_by_default() {
        DocScaffoldCommand(app).run("en", outputDir)

        assertTrue(!Files.exists(outputDir.resolve("en/file/secret.md")))
    }

    @Test
    fun test_scaffold_includes_hidden_when_requested() {
        DocScaffoldCommand(app).run("en", outputDir, includeHidden = true)

        assertTrue(Files.exists(outputDir.resolve("en/file/secret.md")))
    }

    @Test
    fun test_scaffold_fills_synopsis_usage_and_todo_body() {
        DocScaffoldCommand(app).run("en", outputDir)

        val content = Files.readString(outputDir.resolve("en/file/create.md"))
        assertTrue(content.contains("synopsis: laret file create <path>"))
        assertTrue(content.contains("TODO: Write detailed explanation and examples for `create`."))
    }

    @Test
    fun test_scaffold_with_dry_run_filesystem_writes_nothing() {
        val created = DocScaffoldCommand(app, DryRunFileSystem()).run("en", outputDir)

        assertTrue(created.isNotEmpty(), "the dry run still reports which files it would create")
        assertTrue(!Files.exists(outputDir.resolve("en/file/create.md")))
    }

    @Test
    fun test_scaffold_does_not_overwrite_existing_files() {
        val page = outputDir.resolve("en/file/create.md")
        Files.createDirectories(page.parent)
        Files.writeString(page, "AUTHORED CONTENT")

        val created = DocScaffoldCommand(app).run("en", outputDir)

        assertEquals("AUTHORED CONTENT", Files.readString(page))
        assertTrue(!created.contains(page), "existing files must be skipped, not reported as created")
    }
}
