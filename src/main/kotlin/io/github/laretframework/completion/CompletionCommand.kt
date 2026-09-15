package io.github.laretframework.completion

import io.github.laretframework.completion.generators.BashCompletionGenerator
import io.github.laretframework.completion.generators.PowerShellCompletionGenerator
import io.github.laretframework.completion.generators.ZshCompletionGenerator
import io.github.laretframework.core.CliApp
import java.io.File

class CompletionCommand(private val app: CliApp) {
    fun generate(shellType: ShellType, outputFile: File? = null, dynamic: Boolean = false): String {
        val generator =
            when (shellType) {
                ShellType.POWERSHELL -> PowerShellCompletionGenerator()
                ShellType.BASH -> BashCompletionGenerator()
                ShellType.ZSH -> ZshCompletionGenerator()
            }

        val script = generator.generate(app, dynamic)
        outputFile?.writeText(script)
        return script
    }

    fun generateAll(outputDir: File) {
        ShellType.values().forEach { shell ->
            val file = File(outputDir, "${app.name}_completion.${shell.extension}")
            generate(shell, file)
            println("Generated: ${file.absolutePath}")
        }
    }
}
