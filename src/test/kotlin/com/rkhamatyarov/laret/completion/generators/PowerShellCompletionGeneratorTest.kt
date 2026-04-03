package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.CompletionCommand
import com.rkhamatyarov.laret.completion.ShellType
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PowerShellCompletionGeneratorTest {
    private lateinit var completionCommand: CompletionCommand
    private lateinit var app: CliApp

    @BeforeEach
    fun setup() {
        app =
            cli(name = "laret", version = "1.0.0", description = "Test CLI framework") {
                group(name = "file", description = "File operations") {
                    command(name = "create", description = "Create a new file") {
                        option("c", "content", "File content", "", true)
                        option("f", "force", "Overwrite if exists", "", false)
                    }
                    command(name = "delete", description = "Delete a file") {
                        option("f", "force", "Force deletion", "", false)
                    }
                    command(name = "read", description = "Read file contents") {}
                }
                group(name = "dir", description = "Directory operations") {
                    command(name = "list", description = "List directory contents") {
                        option("l", "long", "Long format", "", false)
                        option("a", "all", "Show hidden files", "", false)
                    }
                    command(name = "create", description = "Create a new directory") {
                        option("p", "parents", "Create parent directories", "", false)
                    }
                }
            }
        completionCommand = CompletionCommand(app)
    }

    @Test
    fun `generate creates valid powershell script`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("# PowerShell completion for laret"))
        assertTrue(script.contains("\$scriptblock = {"))
        assertTrue(script.contains("Register-ArgumentCompleter"))
    }

    @Test
    fun `generate includes app name in script`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("laret"))
        assertTrue(script.contains("laret.exe"))
    }

    @Test
    fun `generate includes all groups at level 0`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("'file'"), "Should contain 'file' group")
        assertTrue(script.contains("'dir'"), "Should contain 'dir' group")
    }

    @Test
    fun `generate includes level 1 subcommands for file group`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("'create'"), "Should contain 'create' command")
        assertTrue(script.contains("'delete'"), "Should contain 'delete' command")
        assertTrue(script.contains("'read'"), "Should contain 'read' command")
    }

    @Test
    fun `generate includes level 1 subcommands for dir group`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("'list'"), "Should contain 'list' command")
        assertTrue(script.contains("'create'"), "Should contain 'create' command")
    }

    @Test
    fun `generate includes global options`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("'--help'"), "Should contain --help")
        assertTrue(script.contains("'-h'"), "Should contain -h")
        assertTrue(script.contains("'--version'"), "Should contain --version")
        assertTrue(script.contains("'-v'"), "Should contain -v")
    }

    @Test
    fun `generate includes command specific options`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("'--content'"), "Should contain --content option")
        assertTrue(script.contains("'-c'"), "Should contain -c option")
        assertTrue(script.contains("'--force'"), "Should contain --force option")
        assertTrue(script.contains("'-l'"), "Should contain -l option")
        assertTrue(script.contains("'-a'"), "Should contain -a option")
    }

    @Test
    fun `generate has level 0 switch case`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("if (\$argCount -eq 0)"), "Should have level 0 check")
    }

    @Test
    fun `generate has level 1 switch case`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("elseif (\$argCount -eq 1)"), "Should have level 1 check")
        assertTrue(script.contains("switch (\$group)"), "Should have group switch")
    }

    @Test
    fun `generate has level 2 switch case`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("elseif (\$argCount -eq 2)"), "Should have level 2 check")
    }

    @Test
    fun `generate has filtering logic for partial input`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("Where-Object { \$_ -like"), "Should have filtering logic")
    }

    @Test
    fun `generate has completion result formatting`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("CompletionResult"), "Should format completion results")
    }

    @Test
    fun `generate has balanced braces`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(
            script.count { it == '{' } >= script.count { it == '}' },
            "Should have balanced braces",
        )
    }

    @Test
    fun `generate uses PowerShell array syntax`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("@("), "Should use PowerShell array syntax")
    }

    @Test
    fun `generate contains switch cases for all groups`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("'file' {"), "Should have case for 'file' group")
        assertTrue(script.contains("'dir' {"), "Should have case for 'dir' group")
    }

    @Test
    fun `generate has default case for unknown groups`() {
        val script = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("default {"), "Should have default case for unknown groups")
    }

    @Test
    fun `generate creates deterministic output`() {
        val script1 = completionCommand.generate(ShellType.POWERSHELL)
        val script2 = completionCommand.generate(ShellType.POWERSHELL)
        assertTrue(script1 == script2, "Multiple generations should produce identical output")
    }

    @Test
    fun `generate handles empty command list gracefully`() {
        val emptyApp =
            cli(name = "empty", version = "1.0.0", description = "Empty CLI") {
                group(name = "test", description = "Test group") {}
            }
        val command = CompletionCommand(emptyApp)
        val script = command.generate(ShellType.POWERSHELL)
        assertTrue(script.contains("empty"))
        assertTrue(script.contains("'test'"))
    }
}
