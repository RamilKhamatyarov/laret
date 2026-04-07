package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.CompletionCommand
import com.rkhamatyarov.laret.completion.ShellType
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZshCompletionGeneratorTest {
    private lateinit var app: CliApp
    private lateinit var completionCommand: CompletionCommand
    private val testHomeDir = File("test_home_tmp")

    @BeforeEach
    fun setup() {
        testHomeDir.mkdirs()

        app =
            cli(name = "testcli", version = "1.0.0", description = "Test CLI for completion") {
                group(name = "file", description = "File operations") {
                    command(name = "create", description = "Create file") {
                        argument("path", "File path", required = true)
                        option("c", "content", "File content", "", true)
                        option("f", "force", "Overwrite", "", false)
                        action {}
                    }

                    command(name = "delete", description = "Delete file") {
                        argument("path", "File path", required = true)
                        action {}
                    }
                }

                group(name = "dir", description = "Directory operations") {
                    command(name = "create", description = "Create directory") {
                        argument("path", "Dir path", required = true)
                        option("p", "parents", "Create parents", "", false)
                        action {}
                    }

                    command(name = "list", description = "List directory") {
                        argument("path", "Dir path", required = false, optional = true, default = ".")
                        action {}
                    }
                }
            }

        completionCommand = CompletionCommand(app)
    }

    @AfterEach
    fun cleanup() {
        testHomeDir.deleteRecursively()
    }

    @Test
    fun `generate creates valid zsh script`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("#compdef testcli"))
        assertTrue(script.contains("# Zsh completion for testcli"))
    }

    @Test
    fun `generate includes app name in script`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("testcli"))
    }

    @Test
    fun `generate includes compdef header`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("#compdef testcli"))
    }

    @Test
    fun `generate includes function definition`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("_testcli()"))
    }

    @Test
    fun `generate includes all groups`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("'file:"), "Should contain 'file' group")
        assertTrue(script.contains("'dir:"), "Should contain 'dir' group")
    }

    @Test
    fun `generate includes all commands`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("'create:"), "Should contain 'create' command")
        assertTrue(script.contains("'delete:"), "Should contain 'delete' command")
        assertTrue(script.contains("'list:"), "Should contain 'list' command")
    }

    @Test
    fun `generate includes global options`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("--help[Show help]"), "Should contain --help with description")
        assertTrue(script.contains("-h[Show help]"), "Should contain -h with description")
        assertTrue(script.contains("--version[Show version]"), "Should contain --version")
        assertTrue(script.contains("-v[Show version]"), "Should contain -v")
    }

    @Test
    fun `generate includes command specific options`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("--content"), "Should contain --content option")
        assertTrue(script.contains("-c"), "Should contain -c option")
        assertTrue(script.contains("--force"), "Should contain --force option")
        assertTrue(script.contains("--parents"), "Should contain --parents option")
    }

    @Test
    fun `generate uses zsh _describe function`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("_describe 'commands'"), "Should use _describe for commands")
    }

    @Test
    fun `generate uses zsh _arguments function`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("_arguments"), "Should use _arguments for options")
    }

    @Test
    fun `generate has balanced braces`() {
        val script = completionCommand.generate(ShellType.ZSH)
        val openBraces = script.count { it == '{' }
        val closeBraces = script.count { it == '}' }
        assertTrue(openBraces == closeBraces, "Should have balanced braces")
    }

    @Test
    fun `generate creates deterministic output`() {
        val script1 = completionCommand.generate(ShellType.ZSH)
        val script2 = completionCommand.generate(ShellType.ZSH)
        assertTrue(script1 == script2, "Multiple generations should produce identical output")
    }

    @Test
    fun `generate handles empty command list gracefully`() {
        val emptyApp =
            cli(name = "empty", version = "1.0.0", description = "Empty CLI") {
                group(name = "test", description = "Test group") {}
            }
        val command = CompletionCommand(emptyApp)
        val script = command.generate(ShellType.ZSH)
        assertTrue(script.contains("empty"))
        assertTrue(script.contains("test"))
    }

    @Test
    fun `generate with multiple groups generates correctly`() {
        val multiGroupApp =
            cli(name = "multicli", version = "1.0.0") {
                group(name = "g1", description = "Group 1") {
                    command(name = "c1", description = "Cmd 1") { action {} }
                    command(name = "c2", description = "Cmd 2") { action {} }
                }
                group(name = "g2", description = "Group 2") {
                    command(name = "c3", description = "Cmd 3") { action {} }
                }
                group(name = "g3", description = "Group 3") {
                    command(name = "c4", description = "Cmd 4") { action {} }
                    command(name = "c5", description = "Cmd 5") { action {} }
                    command(name = "c6", description = "Cmd 6") { action {} }
                }
            }

        val command = CompletionCommand(multiGroupApp)
        val zsh = command.generate(ShellType.ZSH)
        assertContains(zsh, "g1")
        assertContains(zsh, "g2")
        assertContains(zsh, "g3")
        assertContains(zsh, "c1")
        assertContains(zsh, "c6")
    }

    @Test
    fun `generate with options generates correctly`() {
        val optionsApp =
            cli(name = "optcli", version = "1.0.0") {
                group(name = "ops", description = "Operations") {
                    command(name = "cmd", description = "Command") {
                        option("v", "verbose", "Verbose", "", false)
                        option("d", "debug", "Debug", "", false)
                        option("o", "output", "Output file", "", true)
                        action {}
                    }
                }
            }

        val command = CompletionCommand(optionsApp)
        val zsh = command.generate(ShellType.ZSH)
        assertContains(zsh, "--verbose")
        assertContains(zsh, "--debug")
        assertContains(zsh, "--output")
    }

    @Test
    fun `install creates file in correct location`() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val completion = completionCommand.generate(ShellType.ZSH)
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Zsh completion file should exist")
        assertTrue(file.isFile, "Should be a file")
    }

    @Test
    fun `install file name starts with underscore`() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val completion = completionCommand.generate(ShellType.ZSH)
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertEquals("_testcli", file.name, "Zsh completion file should start with underscore")
    }

    @Test
    fun `install file contains valid zsh script`() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val completion = completionCommand.generate(ShellType.ZSH)
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        val content = file.readText()
        assertTrue(content.contains("#compdef testcli"), "Should contain compdef")
        assertTrue(content.contains("_testcli()"), "Should contain completion function")
    }

    @Test
    fun `install overwrites existing file`() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()

        file.writeText("OLD CONTENT")
        assertEquals("OLD CONTENT", file.readText())

        val newCompletion = completionCommand.generate(ShellType.ZSH)
        file.writeText(newCompletion)

        val content = file.readText()
        assertFalse(content.contains("OLD CONTENT"), "Old content should be replaced")
        assertTrue(content.contains("#compdef testcli"), "New content should be present")
    }

    @Test
    fun `install creates parent directories if needed`() {
        val deepDir = File(testHomeDir, "a/b/c/d/.zsh_completions")
        val completion = completionCommand.generate(ShellType.ZSH)
        val file = File(deepDir, "_${app.name}")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Should create file with parent directories")
        assertTrue(deepDir.isDirectory, "Parent directories should be created")
    }

    @Test
    fun `install file is readable and writable`() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()

        val completion = completionCommand.generate(ShellType.ZSH)
        file.writeText(completion)

        assertTrue(file.canRead(), "File should be readable")
        assertTrue(file.canWrite(), "File should be writable")
    }

    @Test
    fun `generate uses zsh array syntax`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("local -a"), "Should use zsh local array syntax")
    }

    @Test
    fun `generate includes option descriptions`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertTrue(script.contains("["), "Should contain option description brackets")
        assertTrue(script.contains("]"), "Should contain option description brackets")
    }

    @Test
    fun `generate does not execute app name at end`() {
        val script = completionCommand.generate(ShellType.ZSH)

        assertFalse(
            script.contains("${app.name} \"\$@\""),
            "Zsh completion should not execute app name at end",
        )

        assertTrue(
            script.trimEnd().endsWith("}"),
            "Zsh completion should end with function closing brace",
        )
    }

    @Test
    fun `generate does not contain command execution line`() {
        val script = completionCommand.generate(ShellType.ZSH)
        assertFalse(
            script.contains("${app.name} \"\$@\""),
            "Zsh completion script should not contain command execution line",
        )
    }
}
