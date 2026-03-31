package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.completion.generators.BashCompletionGenerator
import com.rkhamatyarov.laret.completion.generators.PowerShellCompletionGenerator
import com.rkhamatyarov.laret.completion.generators.ZshCompletionGenerator
import com.rkhamatyarov.laret.core.CliApp
import java.io.File

class CompletionCommand(private val app: CliApp) {
    fun generate(shellType: ShellType, outputFile: File? = null): String {
        val generator =
            when (shellType) {
                ShellType.POWERSHELL -> PowerShellCompletionGenerator()
                ShellType.BASH -> BashCompletionGenerator()
                ShellType.ZSH -> ZshCompletionGenerator()
            }

        val script = generator.generate(app)
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
