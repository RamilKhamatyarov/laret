package com.rkhamatyarov.laret.scaffold.generator

import com.rkhamatyarov.laret.scaffold.model.ScaffoldConfig
import com.rkhamatyarov.laret.scaffold.model.ShellTarget
import com.rkhamatyarov.laret.scaffold.template.TemplateEngine

class ShellTestGenerator(private val engine: TemplateEngine = TemplateEngine()) {
    data class ShellTestArtifact(val relativePath: String, val content: String, val executable: Boolean)

    fun generate(config: ScaffoldConfig): List<ShellTestArtifact> {
        if (config.shellTests.isEmpty()) return emptyList()
        val context = mapOf(
            "appName" to config.appName,
            "envPrefix" to envPrefix(config.appName),
        )
        return config.shellTests.map { target ->
            val template = engine.copyResourceTemplate(resourceFor(target))
            ShellTestArtifact(
                relativePath = "tests/${fileFor(target)}",
                content = engine.render(template, context),
                executable = target != ShellTarget.POWERSHELL,
            )
        }
    }

    private fun resourceFor(target: ShellTarget): String = when (target) {
        ShellTarget.BASH -> "templates/scaffold/test-precedence.bash.tpl"
        ShellTarget.ZSH -> "templates/scaffold/test-precedence.zsh.tpl"
        ShellTarget.POWERSHELL -> "templates/scaffold/test-precedence.ps1.tpl"
    }

    private fun fileFor(target: ShellTarget): String = when (target) {
        ShellTarget.BASH -> "test-precedence.bash"
        ShellTarget.ZSH -> "test-precedence.zsh"
        ShellTarget.POWERSHELL -> "test-precedence.ps1"
    }

    private fun envPrefix(appName: String): String = appName.uppercase().replace(Regex("[^A-Z0-9]"), "_").trim('_')
}
