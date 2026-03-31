package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.CompletionCommand
import com.rkhamatyarov.laret.completion.ShellType
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BashCompletionGeneratorTest {
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
    fun `generate creates valid bash script`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("#!/bin/bash"))
        assertTrue(script.contains("# Bash completion for testcli"))
    }

    @Test
    fun `generate includes app name in script`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("testcli"))
    }

    @Test
    fun `generate includes shebang header`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.startsWith("#!/bin/bash"))
    }

    @Test
    fun `generate includes function definition`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("_testcli_complete()"))
        assertTrue(script.contains("complete -F _testcli_complete testcli"))
    }

    @Test
    fun `generate includes all groups`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("file"), "Should contain 'file' group")
        assertTrue(script.contains("dir"), "Should contain 'dir' group")
    }

    @Test
    fun `generate includes all commands`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("create"), "Should contain 'create' command")
        assertTrue(script.contains("delete"), "Should contain 'delete' command")
        assertTrue(script.contains("list"), "Should contain 'list' command")
    }

    @Test
    fun `generate includes global options`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("--help"), "Should contain --help")
        assertTrue(script.contains("-h"), "Should contain -h")
        assertTrue(script.contains("--version"), "Should contain --version")
        assertTrue(script.contains("-v"), "Should contain -v")
    }

    @Test
    fun `generate includes command specific options`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("--content"), "Should contain --content option")
        assertTrue(script.contains("-c"), "Should contain -c option")
        assertTrue(script.contains("--force"), "Should contain --force option")
        assertTrue(script.contains("--parents"), "Should contain --parents option")
    }

    @Test
    fun `generate uses bash array syntax`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("=("), "Should use bash array syntax")
        assertTrue(script.contains("COMPREPLY="), "Should use COMPREPLY variable")
    }

    @Test
    fun `generate uses compgen for completions`() {
        val script = completionCommand.generate(ShellType.BASH)
        assertTrue(script.contains("compgen -W"), "Should use compgen for word completion")
    }

    @Test
    fun `generate has balanced braces`() {
        val script = completionCommand.generate(ShellType.BASH)
        val openBraces = script.count { it == '{' }
        val closeBraces = script.count { it == '}' }
        assertTrue(openBraces == closeBraces, "Should have balanced braces")
    }

    @Test
    fun `generate creates deterministic output`() {
        val script1 = completionCommand.generate(ShellType.BASH)
        val script2 = completionCommand.generate(ShellType.BASH)
        assertTrue(script1 == script2, "Multiple generations should produce identical output")
    }

    @Test
    fun `generate handles empty command list gracefully`() {
        val emptyApp =
            cli(name = "empty", version = "1.0.0", description = "Empty CLI") {
                group(name = "test", description = "Test group") {}
            }
        val command = CompletionCommand(emptyApp)
        val script = command.generate(ShellType.BASH)
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
        val bash = command.generate(ShellType.BASH)
        assertContains(bash, "g1")
        assertContains(bash, "g2")
        assertContains(bash, "g3")
        assertContains(bash, "c1")
        assertContains(bash, "c6")
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
        val bash = command.generate(ShellType.BASH)
        assertContains(bash, "--verbose")
        assertContains(bash, "--debug")
        assertContains(bash, "--output")
    }

    @Test
    fun `install creates file in correct location`() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val completion = completionCommand.generate(ShellType.BASH)
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Bash completion file should exist")
        assertTrue(file.isFile, "Should be a file")
    }

    @Test
    fun `install file contains valid bash script`() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val completion = completionCommand.generate(ShellType.BASH)
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)

        val content = file.readText()
        assertTrue(content.contains("#!/bin/bash"), "Should contain bash shebang")
        assertTrue(content.contains("_testcli_complete"), "Should contain completion function")
    }

    @Test
    fun `install makes file executable`() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val completion = completionCommand.generate(ShellType.BASH)
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)
        file.setExecutable(true)

        assertTrue(file.canExecute(), "File should be executable")
    }

    @Test
    fun `install overwrites existing file`() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()

        file.writeText("OLD CONTENT")
        assertEquals("OLD CONTENT", file.readText())

        val newCompletion = completionCommand.generate(ShellType.BASH)
        file.writeText(newCompletion)

        val content = file.readText()
        assertFalse(content.contains("OLD CONTENT"), "Old content should be replaced")
        assertTrue(content.contains("#!/bin/bash"), "New content should be present")
    }

    @Test
    fun `install file has correct permissions`() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()

        val completion = completionCommand.generate(ShellType.BASH)
        file.writeText(completion)
        file.setExecutable(true)

        assertTrue(file.canRead(), "File should be readable")
        assertTrue(file.canWrite(), "File should be writable")
    }

    @Test
    fun `install creates parent directories if needed`() {
        val deepDir = File(testHomeDir, "a/b/c/d/.bash_completion.d")
        val completion = completionCommand.generate(ShellType.BASH)
        val file = File(deepDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Should create file with parent directories")
        assertTrue(deepDir.isDirectory, "Parent directories should be created")
    }
}
