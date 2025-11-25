package com.rkhamatyarov.laret

import com.rkhamatyarov.laret.completion.generateCompletion
import com.rkhamatyarov.laret.completion.installCompletion
import com.rkhamatyarov.laret.completion.installPowerShellCompletion
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellCompletionGeneratorTest {
    private lateinit var app: CliApp
    private val testHomeDir = File("test_home_tmp")
    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var outputStream: ByteArrayOutputStream

    @BeforeEach
    fun setup() {
        testHomeDir.mkdirs()

        outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        System.setErr(PrintStream(outputStream))

        app =
            cli(
                name = "testcli",
                version = "1.0.0",
                description = "Test CLI for completion",
            ) {
                group(
                    name = "file",
                    description = "File operations",
                ) {
                    command(
                        name = "create",
                        description = "Create file",
                    ) {
                        argument("path", "File path", required = true)
                        option("c", "content", "File content", "", true)
                        option("f", "force", "Overwrite", "", false)
                        action { }
                    }

                    command(
                        name = "delete",
                        description = "Delete file",
                    ) {
                        argument("path", "File path", required = true)
                        action { }
                    }
                }

                group(
                    name = "dir",
                    description = "Directory operations",
                ) {
                    command(
                        name = "create",
                        description = "Create directory",
                    ) {
                        argument("path", "Dir path", required = true)
                        option("p", "parents", "Create parents", "", false)
                        action { }
                    }

                    command(
                        name = "list",
                        description = "List directory",
                    ) {
                        argument("path", "Dir path", required = false, optional = true, default = ".")
                        action { }
                    }
                }
            }
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        testHomeDir.deleteRecursively()
    }

    @Test
    fun generateBashCompletion_shouldContainBashHeader() {
        val completion = app.generateCompletion("bash")
        assertContains(completion, "#!/bin/bash")
        assertContains(completion, "# Bash completion for testcli")
    }

    @Test
    fun generateBashCompletion_shouldContainFunctionDefinition() {
        val completion = app.generateCompletion("bash")
        assertContains(completion, "_testcli_complete()")
        assertContains(completion, "complete -o bashdefault -o default -o nospace -F _testcli_complete testcli")
    }

    @Test
    fun generateBashCompletion_shouldContainAllGroups() {
        val completion = app.generateCompletion("bash")
        assertContains(completion, "file")
        assertContains(completion, "dir")
    }

    @Test
    fun generateBashCompletion_shouldContainAllCommands() {
        val completion = app.generateCompletion("bash")
        assertContains(completion, "create")
        assertContains(completion, "delete")
        assertContains(completion, "list")
    }

    @Test
    fun generateBashCompletion_shouldContainOptions() {
        val completion = app.generateCompletion("bash")
        assertContains(completion, "--help")
        assertContains(completion, "--version")
    }

    @Test
    fun generateZshCompletion_shouldContainZshHeader() {
        val completion = app.generateCompletion("zsh")
        assertContains(completion, "#compdef testcli")
    }

    @Test
    fun generateZshCompletion_shouldContainGroupsFunction() {
        val completion = app.generateCompletion("zsh")
        assertContains(completion, "_testcli()")
        assertContains(completion, "'file:")
        assertContains(completion, "'dir:")
    }

    @Test
    fun generateZshCompletion_shouldContainCommandsFunction() {
        val completion = app.generateCompletion("zsh")
        assertContains(completion, "_describe 'commands'")
        assertContains(completion, "'create:")
        assertContains(completion, "'delete:")
        assertContains(completion, "'list:")
    }

    @Test
    fun generateZshCompletion_shouldContainOptionsFunction() {
        val completion = app.generateCompletion("zsh")
        assertContains(completion, "_arguments")
        assertContains(completion, "--help")
        assertContains(completion, "--version")
    }

    @Test
    fun generatePowerShellCompletion_shouldContainPowerShellComment() {
        val completion = app.generateCompletion("powershell")
        assertContains(completion, "# PowerShell completion for testcli")
    }

    @Test
    fun generatePowerShellCompletion_shouldContainScriptBlock() {
        val completion = app.generateCompletion("powershell")
        assertContains(completion, "${'$'}scriptblock = {")
        assertContains(completion, "Register-ArgumentCompleter")
    }

    @Test
    fun generatePowerShellCompletion_shouldRegisterBothCommands() {
        val completion = app.generateCompletion("powershell")
        assertContains(completion, "Register-ArgumentCompleter -CommandName testcli")
        assertContains(completion, "Register-ArgumentCompleter -CommandName testcli.exe")
    }

    @Test
    fun generatePowerShellCompletion_shouldContainGroupCompletion() {
        val completion = app.generateCompletion("powershell")
        assertContains(completion, "'file'")
        assertContains(completion, "'dir'")
    }

    @Test
    fun generatePowerShellCompletion_shouldContainCommandCompletion() {
        val completion = app.generateCompletion("powershell")
        assertContains(completion, "'create'")
        assertContains(completion, "'delete'")
        assertContains(completion, "'list'")
    }

    @Test
    fun generatePowerShellCompletion_shouldContainOptionCompletion() {
        val completion = app.generateCompletion("powershell")
        assertContains(completion, "'--help'")
        assertContains(completion, "'--version'")
        assertContains(completion, "'--content'")
        assertContains(completion, "'--force'")
        assertContains(completion, "'--parents'")
    }

    @Test
    fun installCompletion_bash_createsFile() {
        app.installCompletion("bash")
        val file = File(System.getProperty("user.home"), ".bash_completion.d/testcli")
        assertTrue(file.exists(), "Bash completion file should exist")
        file.delete()
    }

    @Test
    fun installCompletion_zsh_createsFile() {
        app.installCompletion("zsh")
        val file = File(System.getProperty("user.home"), ".zsh_completions/_testcli")
        assertTrue(file.exists(), "Zsh completion file should exist")
        file.delete()
    }

    @Test
    fun installCompletion_powershell_createsFile() {
        app.installCompletion("powershell")
        val homeDir = System.getProperty("user.home")
        val profilePath = System.getenv("PROFILE")
        val profileDir =
            if (profilePath != null) {
                File(profilePath).parentFile?.absolutePath ?: File(homeDir, "Documents\\PowerShell").absolutePath
            } else {
                File(homeDir, "Documents\\PowerShell").absolutePath
            }
        val file = File(profileDir, "testcli_completion.ps1")
        assertTrue(file.exists(), "PowerShell completion file should exist")
        file.delete()
    }

    @Test
    fun installCompletion_bash_fileContainsValidCompletion() {
        app.installCompletion("bash")
        val file = File(System.getProperty("user.home"), ".bash_completion.d/testcli")
        val content = file.readText()
        assertContains(content, "#!/bin/bash")
        assertContains(content, "_testcli_complete")
        file.delete()
    }

    @Test
    fun installCompletion_zsh_fileContainsValidCompletion() {
        app.installCompletion("zsh")
        val file = File(System.getProperty("user.home"), ".zsh_completions/_testcli")
        val content = file.readText()
        assertContains(content, "#compdef testcli")
        assertContains(content, "_testcli()")
        file.delete()
    }

    @Test
    fun installCompletion_powershell_fileContainsValidCompletion() {
        app.installCompletion("powershell")
        val homeDir = System.getProperty("user.home")
        val profilePath = System.getenv("PROFILE")
        val profileDir =
            if (profilePath != null) {
                File(profilePath).parentFile?.absolutePath ?: File(homeDir, "Documents\\PowerShell").absolutePath
            } else {
                File(homeDir, "Documents\\PowerShell").absolutePath
            }
        val file = File(profileDir, "testcli_completion.ps1")
        val content = file.readText()
        assertContains(content, "PowerShell completion for testcli")
        assertContains(content, "Register-ArgumentCompleter")
        file.delete()
    }

    @Test
    fun installPowerShellCompletion_createsFile() {
        app.installPowerShellCompletion()
        val homeDir = System.getProperty("user.home")
        val profilePath = System.getenv("PROFILE")
        val profileDir =
            if (profilePath != null) {
                File(profilePath).parentFile?.absolutePath ?: File(homeDir, "Documents\\PowerShell").absolutePath
            } else {
                File(homeDir, "Documents\\PowerShell").absolutePath
            }
        val file = File(profileDir, "testcli_completion.ps1")
        assertTrue(file.exists(), "PowerShell completion file should exist")
        file.delete()
    }

    @Test
    fun generateCompletion_unsupportedShell_throwsException() {
        try {
            app.generateCompletion("fish")
            assertTrue(false, "Should throw exception for unsupported shell")
        } catch (e: IllegalArgumentException) {
            assertContains(e.message ?: "", "Unsupported shell")
        }
    }

    @Test
    fun installCompletion_unsupportedShell_throwsException() {
        try {
            app.installCompletion("fish")
            assertTrue(false, "Should throw exception for unsupported shell")
        } catch (e: IllegalArgumentException) {
            assertContains(e.message ?: "", "Unsupported shell")
        }
    }

    @Test
    fun generateCompletion_caseInsensitive_bash() {
        val bash1 = app.generateCompletion("bash")
        val bash2 = app.generateCompletion("BASH")
        val bash3 = app.generateCompletion("Bash")
        assertEquals(bash1, bash2)
        assertEquals(bash2, bash3)
    }

    @Test
    fun generateCompletion_caseInsensitive_zsh() {
        val zsh1 = app.generateCompletion("zsh")
        val zsh2 = app.generateCompletion("ZSH")
        val zsh3 = app.generateCompletion("Zsh")
        assertEquals(zsh1, zsh2)
        assertEquals(zsh2, zsh3)
    }

    @Test
    fun generateCompletion_caseInsensitive_powershell() {
        val ps1 = app.generateCompletion("powershell")
        val ps2 = app.generateCompletion("POWERSHELL")
        val ps3 = app.generateCompletion("PowerShell")
        assertEquals(ps1, ps2)
        assertEquals(ps2, ps3)
    }

    @Test
    fun shellCompletionGenerator_withMultipleGroups_generatesCorrectly() {
        val multiGroupApp =
            cli(
                name = "multicli",
                version = "1.0.0",
            ) {
                group(name = "g1", description = "Group 1") {
                    command(name = "c1", description = "Cmd 1") { action { } }
                    command(name = "c2", description = "Cmd 2") { action { } }
                }
                group(name = "g2", description = "Group 2") {
                    command(name = "c3", description = "Cmd 3") { action { } }
                }
                group(name = "g3", description = "Group 3") {
                    command(name = "c4", description = "Cmd 4") { action { } }
                    command(name = "c5", description = "Cmd 5") { action { } }
                    command(name = "c6", description = "Cmd 6") { action { } }
                }
            }

        val bash = multiGroupApp.generateCompletion("bash")
        assertContains(bash, "g1")
        assertContains(bash, "g2")
        assertContains(bash, "g3")
        assertContains(bash, "c1")
        assertContains(bash, "c6")
    }

    @Test
    fun shellCompletionGenerator_withOptions_generatesCorrectly() {
        val optionsApp =
            cli(
                name = "optcli",
                version = "1.0.0",
            ) {
                group(name = "ops", description = "Operations") {
                    command(name = "cmd", description = "Command") {
                        option("v", "verbose", "Verbose", "", false)
                        option("d", "debug", "Debug", "", false)
                        option("o", "output", "Output file", "", true)
                        action { }
                    }
                }
            }

        val bash = optionsApp.generateCompletion("bash")
        assertContains(bash, "--verbose")
        assertContains(bash, "--debug")
        assertContains(bash, "--output")
    }

    @Test
    fun installCompletion_zsh_fileContainsValidZshScript() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val file = File(zshCompletionDir, "_testcli")
        file.parentFile?.mkdirs()
        val completionContent = app.generateCompletion("zsh")
        file.writeText(completionContent)
        val text = file.readText()
        assertContains(text, "#compdef testcli")
        assertContains(text, "_testcli()")
    }

    @Test
    fun installCompletion_bash_createsFileInCorrectLocation() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val completion = app.generateCompletion("bash")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Bash completion file should exist")
        assertTrue(file.isFile, "Should be a file")
    }

    @Test
    fun installCompletion_bash_fileContainsValidBashScript() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val completion = app.generateCompletion("bash")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)

        val content = file.readText()
        assertTrue(content.contains("#!/bin/bash"), "Should contain bash shebang")
        assertTrue(content.contains("_testcli_complete"), "Should contain completion function")
    }

    @Test
    fun installCompletion_bash_makesFileExecutable() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val completion = app.generateCompletion("bash")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()
        file.writeText(completion)
        file.setExecutable(true)

        assertTrue(file.canExecute(), "File should be executable")
    }

    @Test
    fun installCompletion_bash_overwritesExistingFile() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()

        file.writeText("OLD CONTENT")
        assertEquals("OLD CONTENT", file.readText())

        val newCompletion = app.generateCompletion("bash")
        file.writeText(newCompletion)

        val content = file.readText()
        assertFalse(content.contains("OLD CONTENT"), "Old content should be replaced")
        assertTrue(content.contains("#!/bin/bash"), "New content should be present")
    }

    @Test
    fun installCompletion_bash_fileHasCorrectPermissions() {
        val bashCompletionDir = File(testHomeDir, ".bash_completion.d")
        val file = File(bashCompletionDir, app.name)
        file.parentFile?.mkdirs()

        val completion = app.generateCompletion("bash")
        file.writeText(completion)
        file.setExecutable(true)

        assertTrue(file.canRead(), "File should be readable")
        assertTrue(file.canWrite(), "File should be writable")
    }

    @Test
    fun installCompletion_zsh_createsFileInCorrectLocation() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val completion = app.generateCompletion("zsh")
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Zsh completion file should exist")
        assertTrue(file.isFile, "Should be a file")
        assertTrue(file.name.startsWith("_"), "Zsh completion files should start with _")
    }

    @Test
    fun installCompletion_zsh_fileNameStartsWithUnderscore() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val completion = app.generateCompletion("zsh")
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertEquals("_testcli", file.name, "Zsh completion file should start with underscore")
    }

    @Test
    fun installCompletion_zsh_overwritesExistingFile() {
        val zshCompletionDir = File(testHomeDir, ".zsh_completions")
        val file = File(zshCompletionDir, "_${app.name}")
        file.parentFile?.mkdirs()

        file.writeText("OLD CONTENT")
        assertEquals("OLD CONTENT", file.readText())

        val newCompletion = app.generateCompletion("zsh")
        file.writeText(newCompletion)

        val content = file.readText()
        assertFalse(content.contains("OLD CONTENT"), "Old content should be replaced")
        assertTrue(content.contains("#compdef testcli"), "New content should be present")
    }

    @Test
    fun installCompletion_powershell_createsFileWithCorrectName() {
        val profileDir = File(testHomeDir, "Documents\\PowerShell")
        val completion = app.generateCompletion("powershell")
        val file = File(profileDir, "${app.name}_completion.ps1")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "PowerShell completion file should exist")
        assertEquals("${app.name}_completion.ps1", file.name, "Should have correct filename")
    }

    @Test
    fun installCompletion_powershell_fileContainsValidPowerShellScript() {
        val profileDir = File(testHomeDir, "Documents\\PowerShell")
        val completion = app.generateCompletion("powershell")
        val file = File(profileDir, "${app.name}_completion.ps1")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        val content = file.readText()
        assertTrue(content.contains("# PowerShell completion"), "Should contain comment")
        assertTrue(content.contains("Register-ArgumentCompleter"), "Should contain completer registration")
    }

    @Test
    fun installCompletion_powershell_withoutProfileEnv_usesDefaultPath() {
        val defaultPath = File(testHomeDir, "Documents\\PowerShell")
        val completion = app.generateCompletion("powershell")
        val file = File(defaultPath, "${app.name}_completion.ps1")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Should create file in default path")
        assertTrue(defaultPath.path.contains("Documents\\PowerShell"), "Should use default PowerShell path")
    }

    @Test
    fun installCompletion_powershell_withProfileEnv_usesProfilePath() {
        val mockProfilePath = File(testHomeDir, "WindowsPowerShell/profile.ps1")
        mockProfilePath.parentFile?.mkdirs()

        val profileDir =
            mockProfilePath.parentFile?.absolutePath ?: File(testHomeDir, "Documents\\PowerShell").absolutePath
        val completion = app.generateCompletion("powershell")
        val file = File(profileDir, "${app.name}_completion.ps1")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Should create file using PROFILE env path")
    }

    @Test
    fun installCompletion_powershell_createsParentDirectoriesIfNeeded() {
        val profileDir = File(testHomeDir, "deeply/nested/directory/PowerShell")
        val completion = app.generateCompletion("powershell")
        val file = File(profileDir, "${app.name}_completion.ps1")
        file.parentFile?.mkdirs()
        file.writeText(completion)

        assertTrue(file.exists(), "Should create file with parent directories")
        assertTrue(profileDir.isDirectory, "Parent directories should be created")
    }

    @Test
    fun installCompletion_createsParentDirectories() {
        val deepDir = File(testHomeDir, "a/b/c/d")
        assertFalse(deepDir.exists(), "Deep directory should not exist initially")

        deepDir.mkdirs()

        assertTrue(deepDir.exists(), "Parent directories should be created")
        assertTrue(deepDir.isDirectory, "Should be a directory")
    }

    @Test
    fun installCompletion_allShells_produceDifferentOutput() {
        val bashCompletion = app.generateCompletion("bash")
        val zshCompletion = app.generateCompletion("zsh")
        val psCompletion = app.generateCompletion("powershell")

        assertFalse(bashCompletion == zshCompletion, "Bash and zsh completions should be different")
        assertFalse(zshCompletion == psCompletion, "Zsh and PowerShell completions should be different")
        assertFalse(bashCompletion == psCompletion, "Bash and PowerShell completions should be different")
    }

    private fun setEnv(
        key: String,
        value: String,
    ) {
        try {
            val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
            val theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment")
            theEnvironmentField.isAccessible = true
            val env = theEnvironmentField.get(null) as MutableMap<String, String>
            env[key] = value
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearEnv(key: String) {
        try {
            val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
            val theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment")
            theEnvironmentField.isAccessible = true
            val env = theEnvironmentField.get(null) as MutableMap<String, String>
            env.remove(key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
