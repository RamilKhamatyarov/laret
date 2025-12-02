package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.ui.greenBold
import org.fusesource.jansi.AnsiConsole
import java.io.File

fun CliApp.generateCompletion(shell: String = "bash"): String {
    AnsiConsole.systemUninstall()

    val generator =
        when (shell.lowercase()) {
            "bash" -> BashCompletionGenerator()
            "zsh" -> ZshCompletionGenerator()
            "powershell" -> PowerShellCompletionGenerator()
            else -> throw IllegalArgumentException("Unsupported shell: $shell")
        }

    return generator.generate(this)
}

/**
 * Extension function to install completion
 */
fun CliApp.installCompletion(shell: String = "bash") {
    val completion = generateCompletion(shell)
    val homeDir = System.getProperty("user.home")

    val file =
        when (shell.lowercase()) {
            "bash" -> File(homeDir, ".bash_completion.d/$name")
            "zsh" -> File(homeDir, ".zsh_completions/_$name")
            "powershell" -> {
                val profilePath = System.getenv("PROFILE")
                val profileDir =
                    if (profilePath != null) {
                        File(profilePath).parentFile?.absolutePath
                            ?: File(homeDir, "Documents\\PowerShell").absolutePath
                    } else {
                        File(homeDir, "Documents\\PowerShell").absolutePath
                    }
                File(profileDir, "${this.name}_completion.ps1")
            }
            else -> throw IllegalArgumentException("Unsupported shell: $shell")
        }

    file.parentFile?.mkdirs()
    file.writeText(completion)
    file.setExecutable(true)

    println(greenBold("✓ Completion installed: ${file.absolutePath}"))

    if (shell.lowercase() == "powershell") {
        println("\nAdd to your \$PROFILE:")
        println(". '${file.absolutePath}'")
    }
}

/**
 * Extension function to install PowerShell completion (shortcut)
 */
fun CliApp.installPowerShellCompletion() {
    installCompletion("powershell")
}
