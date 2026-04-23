package com.rkhamatyarov.laret.man

import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ManPageGeneratorTest {

    private val generator = ManPageGenerator()

    private fun simpleCommand(
        name: String = "create",
        description: String = "Create a resource",
        arguments: List<Argument> = emptyList(),
        options: List<Option> = emptyList(),
    ) = Command(name = name, description = description, arguments = arguments, options = options)

    private fun generate(
        command: Command,
        appName: String = "laret",
        version: String = "1.0.0",
        groupName: String = "",
        seeAlso: List<String> = emptyList(),
    ) = generator.generate(command, appName, version, groupName, seeAlso)

    @Test
    fun `all sections have non-blank headings`() {
        ManSection.entries.forEach { section ->
            assertTrue(section.heading.isNotBlank(), "$section has blank heading")
        }
    }

    @Test
    fun `SEE_ALSO heading is SEE ALSO`() {
        assertEquals("SEE ALSO", ManSection.SEE_ALSO.heading)
    }

    @Test
    fun `six sections are defined in display order`() {
        assertEquals(6, ManSection.entries.size)
    }

    @Test
    fun `escape replaces hyphen with backslash-hyphen`() {
        assertEquals("no\\-op", GroffFormatter.escape("no-op"))
    }

    @Test
    fun `escape replaces apostrophe`() {
        assertEquals("it\\'s", GroffFormatter.escape("it's"))
    }

    @Test
    fun `escape replaces leading dot to avoid macro interpretation`() {
        val result = GroffFormatter.escape(".TH looks like a macro")
        assertTrue(result.startsWith("\\&."), "Leading dot not escaped: $result")
    }

    @Test
    fun `escape does not modify plain text`() {
        assertEquals("hello world", GroffFormatter.escape("hello world"))
    }

    @Test
    fun `bold wraps text with fB and fR`() {
        val result = GroffFormatter.bold("myopt")
        assertTrue(result.startsWith("\\fB"), "Missing bold start: $result")
        assertTrue(result.endsWith("\\fR"), "Missing reset: $result")
        assertTrue(result.contains("myopt"), "Content missing: $result")
    }

    @Test
    fun `italic wraps text with fI and fR`() {
        val result = GroffFormatter.italic("value")
        assertTrue(result.startsWith("\\fI"))
        assertTrue(result.endsWith("\\fR"))
    }

    @Test
    fun `titleHeading produces TH macro line`() {
        val result = GroffFormatter.titleHeading("create", 1, "2025-01-01", "laret 1.0.0")
        assertTrue(result.startsWith(".TH CREATE"), "Expected .TH CREATE, got: $result")
        assertTrue(result.contains("1"))
        assertTrue(result.contains("laret 1.0.0"))
    }

    @Test
    fun `sectionHeading produces SH macro line`() {
        assertEquals(".SH NAME", GroffFormatter.sectionHeading(ManSection.NAME))
        assertEquals(".SH OPTIONS", GroffFormatter.sectionHeading(ManSection.OPTIONS))
        assertEquals(".SH SEE ALSO", GroffFormatter.sectionHeading(ManSection.SEE_ALSO))
    }

    @Test
    fun `taggedParagraph produces TP macro with tag and body`() {
        val result = GroffFormatter.taggedParagraph("-f, --force", "Force overwrite")
        assertTrue(result.startsWith(".TP"))
        assertTrue(result.contains("-f, --force"))
        assertTrue(result.contains("Force overwrite"))
    }

    @Test
    fun `generates NAME section`() {
        val out = generate(simpleCommand())
        assertContains(out, ".SH NAME")
    }

    @Test
    fun `generates SYNOPSIS section`() {
        assertContains(generate(simpleCommand()), ".SH SYNOPSIS")
    }

    @Test
    fun `generates DESCRIPTION section`() {
        assertContains(generate(simpleCommand()), ".SH DESCRIPTION")
    }

    @Test
    fun `generates EXAMPLES section`() {
        assertContains(generate(simpleCommand()), ".SH EXAMPLES")
    }

    @Test
    fun `generates SEE ALSO section`() {
        assertContains(generate(simpleCommand()), ".SH SEE ALSO")
    }

    @Test
    fun `generates OPTIONS section when options present`() {
        val cmd = simpleCommand(
            options = listOf(Option("f", "force", "Force", "", false)),
        )
        assertContains(generate(cmd), ".SH OPTIONS")
    }

    @Test
    fun `does NOT generate OPTIONS section when no options`() {
        val out = generate(simpleCommand(options = emptyList()))
        assertTrue(!out.contains(".SH OPTIONS"), "OPTIONS section should be absent")
    }

    @Test
    fun `NAME section contains escaped description with dash`() {
        val cmd = simpleCommand(name = "file-create", description = "Create a file - with dash")

        val out = generate(cmd, appName = "laret", version = "1.0.0")

        assertContains(out, ".SH NAME")
        assertContains(out, "laret\\-file\\-create")
        assertContains(out, "\\-")
    }

    @Test
    fun `NAME section uses groupName in full name`() {
        val cmd = simpleCommand(name = "create")
        val out = generate(cmd, appName = "laret", version = "1.0.0", groupName = "file")
        assertContains(out, "laret\\-file\\-create")
    }

    @Test
    fun `NAME section without group has two-part name`() {
        val cmd = simpleCommand(name = "help")
        val out = generate(cmd, appName = "laret", version = "1.0.0", groupName = "")
        assertContains(out, "laret\\-help")
    }

    @Test
    fun `TH macro has correct section number 1`() {
        val out = generate(simpleCommand())
        assertTrue(out.lines().first().startsWith(".TH"), "First line must be .TH")
        assertTrue(out.lines().first().contains(" 1 "), "Section number 1 missing")
    }

    @Test
    fun `TH macro contains app name and version in source field`() {
        val out = generate(simpleCommand(), appName = "mycli", version = "2.5.0")
        assertContains(out.lines().first(), "mycli 2.5.0")
    }

    @Test
    fun `TH macro title is uppercase`() {
        val out = generate(simpleCommand(name = "create"), groupName = "file")
        assertTrue(
            out.lines().first().contains("LARET-FILE-CREATE"),
            "Expected uppercase title: ${out.lines().first()}",
        )
    }

    @Test
    fun `option flag appears with short and long form`() {
        val cmd = simpleCommand(
            options = listOf(Option("f", "force", "Force overwrite", "", false)),
        )
        val out = generate(cmd)
        assertContains(out, "\\fB\\-f\\fR")
        assertContains(out, "\\fB\\-\\-force\\fR")
    }

    @Test
    fun `option description is included`() {
        val cmd = simpleCommand(
            options = listOf(Option("v", "verbose", "Enable verbose output", "", false)),
        )
        assertContains(generate(cmd), "Enable verbose output")
    }

    @Test
    fun `persistent option notes config-file support`() {
        val cmd = simpleCommand(
            options = listOf(Option("f", "format", "Output format", "plain", true, persistent = true)),
        )
        val out = generate(cmd)
        assertContains(out, "persistent")
    }

    @Test
    fun `option default value is included when non-blank`() {
        val cmd = simpleCommand(
            options = listOf(Option("f", "format", "Format", "plain", true)),
        )
        assertContains(generate(cmd), "Default: plain")
    }

    @Test
    fun `required argument appears without brackets in synopsis`() {
        val cmd = simpleCommand(
            arguments = listOf(Argument("path", "File path", required = true)),
        )
        val out = generate(cmd)
        assertContains(out, "<path>")
    }

    @Test
    fun `optional argument appears with brackets in synopsis`() {
        val cmd = simpleCommand(
            arguments = listOf(Argument("path", "File path", required = false, optional = true)),
        )
        val out = generate(cmd)
        assertContains(out, "[<path>]")
    }

    @Test
    fun `argument name appears in DESCRIPTION`() {
        val cmd = simpleCommand(
            arguments = listOf(Argument("target", "Target resource", required = true)),
        )
        assertContains(generate(cmd), "target")
    }

    @Test
    fun `SEE ALSO always contains top-level app reference`() {
        val out = generate(simpleCommand(), appName = "laret", groupName = "file")
        assertContains(out, "laret(1)")
    }

    @Test
    fun `SEE ALSO contains group page when groupName is given`() {
        val out = generate(simpleCommand(), appName = "laret", groupName = "file")
        assertContains(out, "laret-file(1)")
    }

    @Test
    fun `SEE ALSO does not contain group page when groupName is blank`() {
        val out = generate(simpleCommand(), appName = "laret", groupName = "")
        assertTrue(
            !out.contains("laret-(1)"),
            "Should not have malformed group ref when groupName is blank",
        )
    }

    @Test
    fun `extra seeAlso entries appear with section suffix`() {
        val out = generate(simpleCommand(), seeAlso = listOf("bash(1)", "zsh"))
        assertContains(out, "bash(1)")
        assertContains(out, "zsh(1)")
    }

    @Test
    fun `command with no arguments and no options produces minimal valid page`() {
        val cmd = simpleCommand(description = "Simple command")

        val out = generate(cmd)

        assertContains(out, ".SH NAME")
        assertContains(out, ".SH SYNOPSIS")
        assertContains(out, ".SH DESCRIPTION")
        assertContains(out, ".SH EXAMPLES")
        assertContains(out, ".SH SEE ALSO")
        assertTrue(!out.contains(".SH OPTIONS"))
    }

    @Test
    fun `apostrophe in description is escaped`() {
        val cmd = simpleCommand(description = "Don't do this")
        assertContains(generate(cmd), "Don\\'t")
    }

    @Test
    fun `dot at line start in description is escaped`() {
        val cmd = simpleCommand(description = ".hidden option")
        assertContains(generate(cmd), "\\&.")
    }

    @Test
    fun `multiple hyphens in description are all escaped()`() {
        val cmd = simpleCommand(description = "opt-a opt-b opt-c")
        val out = generate(cmd)
        val nameLine = out.lineSequence()
            .dropWhile { !it.startsWith(".SH NAME") }
            .drop(1)
            .firstOrNull() ?: ""
        val descriptionPart = nameLine.substringAfter(" \\- ")
        val hyphenCount = "\\-".toRegex().findAll(descriptionPart).count()
        assertEquals(3, hyphenCount)
    }

    @Test
    fun `blank appName throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate(simpleCommand(), appName = "", version = "1.0.0")
        }
    }

    @Test
    fun `blank version throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate(simpleCommand(), appName = "laret", version = "")
        }
    }

    @Test
    fun `output ends with single newline`() {
        val out = generate(simpleCommand())
        assertTrue(out.endsWith("\n"), "Output must end with newline")
        assertTrue(!out.endsWith("\n\n"), "Output must not end with double newline")
    }

    @Test
    fun `output is deterministic across multiple calls`() {
        val cmd = simpleCommand(
            options = listOf(Option("f", "force", "Force", "", false)),
            arguments = listOf(Argument("path", "Path")),
        )
        val out1 = generate(cmd)
        val out2 = generate(cmd)
        assertEquals(out1, out2)
    }

    @Test
    fun `generate writes to temp file with correct content`(@TempDir tmp: File) {
        val cmd = simpleCommand(
            name = "create",
            description = "Create a resource",
            options = listOf(Option("f", "force", "Force", "", false)),
            arguments = listOf(Argument("path", "Target path")),
        )
        val outFile = File(tmp, "laret-create.1")

        val content = generator.generate(cmd, "laret", "1.0.0", "file")
        outFile.writeText(content)

        assertTrue(outFile.exists(), "File should be created")
        assertTrue(outFile.length() > 0, "File should not be empty")
        assertContains(outFile.readText(), ".TH LARET-FILE-CREATE 1")
        assertContains(outFile.readText(), ".SH NAME")
    }

    @Test
    fun `generateGroup writes one file per command`(@TempDir tmp: File) {
        val commands = listOf(
            Command(name = "create", description = "Create"),
            Command(name = "delete", description = "Delete"),
        )
        val group = com.rkhamatyarov.laret.model.CommandGroup(
            name = "file",
            description = "File operations",
            commands = commands,
        )

        val files = generator.generateGroup(group, "laret", "1.0.0", tmp)

        assertEquals(2, files.size, "One file per command")
        assertTrue(files.all { it.exists() }, "All files must be written")
        assertTrue(files.any { it.name == "laret-file-create.1" })
        assertTrue(files.any { it.name == "laret-file-delete.1" })
    }
}
