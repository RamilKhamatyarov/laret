package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.ui.greenBold
import org.fusesource.jansi.AnsiConsole
import java.io.File

fun CliApp.generateCompletion(shell: String = "bash"): String {
    AnsiConsole.systemUninstall()

    val shellType = ShellType.valueOf(shell.uppercase())
    val command = CompletionCommand(this)

    return command.generate(shellType)
}

fun CliApp.installCompletion(shell: String = "bash") {
    val shellType = ShellType.valueOf(shell.uppercase())
    val completion = generateCompletion(shell)
    val homeDir = System.getProperty("user.home")

    val file =
        when (shellType) {
            ShellType.BASH -> File(homeDir, ".bash_completion.d/$name")
            ShellType.ZSH -> File(homeDir, ".zsh_completions/_$name")
            ShellType.POWERSHELL -> {
                val profilePath = System.getenv("PROFILE")
                val profileDir =
                    if (profilePath != null) {
                        File(profilePath).parentFile?.absolutePath
                            ?: File(homeDir, "Documents/PowerShell").absolutePath
                    } else {
                        File(homeDir, "Documents/PowerShell").absolutePath
                    }
                File(profileDir, "${this.name}_completion.ps1")
            }
        }

    file.parentFile?.mkdirs()
    file.writeText(completion)
    file.setExecutable(true)

    println(greenBold("Completion installed: ${file.absolutePath}"))

    if (shellType == ShellType.POWERSHELL) {
        println("\nAdd to your \$PROFILE:")
        println(". '${file.absolutePath}'")
    }
}

fun CliApp.installPowerShellCompletion() {
    installCompletion("powershell")
}

fun CliApp.generateBashCompletion(): String = generateCompletion("bash")

fun CliApp.generateZshCompletion(): String = generateCompletion("zsh")

fun CliApp.generatePowerShellCompletion(): String = generateCompletion("powershell")

fun CliApp.installBashCompletion() {
    installCompletion("bash")
}

fun CliApp.installZshCompletion() {
    installCompletion("zsh")
}

fun CliApp.installPowerShellCompletionExplicit() {
    installCompletion("powershell")
}
