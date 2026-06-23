package com.rkhamatyarov.laret.scaffold.wizard

import com.rkhamatyarov.laret.scaffold.model.Module
import com.rkhamatyarov.laret.scaffold.model.ScaffoldConfig
import com.rkhamatyarov.laret.scaffold.model.ShellTarget
import com.rkhamatyarov.laret.ui.Colors
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Owns a single [BufferedReader] so chained prompts share the same underlying buffer.
 * (Constructing a fresh BufferedReader per prompt would lose buffered bytes from earlier reads.)
 */
class InteractiveWizard(
    input: InputStream = System.`in`,
    private val out: PrintStream = System.err,
    private val defaultLaretVersion: String = DEFAULT_LARET_VERSION,
) {
    private val reader: BufferedReader = BufferedReader(InputStreamReader(input))

    fun runWizard(): ScaffoldConfig {
        banner()
        val projectName = askProjectName()
        val packageName = askPackageName(projectName)
        val appName = text("CLI app binary name", default = projectName)
        val modules = askModules()
        val shellTests = if (confirm("Generate shell precedence tests?", default = true)) {
            askShellTargets()
        } else {
            emptySet()
        }
        val graalvm = confirm("Enable GraalVM native-image task?", default = false)

        return ScaffoldConfig(
            projectName = projectName,
            packageName = packageName,
            appName = appName,
            laretVersion = defaultLaretVersion,
            modules = modules,
            shellTests = shellTests,
            graalvm = graalvm,
        )
    }

    private fun banner() {
        out.println()
        out.println("${Colors.CYAN_BOLD}Welcome to Laret CLI scaffolder${Colors.RESET}")
        out.println("${Colors.BLUE}Configure your new project below.${Colors.RESET}")
        out.println()
    }

    private fun askProjectName(): String {
        repeat(MAX_RETRIES) {
            val value = text("Project name (lower-kebab)", default = "my-cli")
            if (value.matches(ScaffoldConfig.PROJECT_NAME_PATTERN)) return value
            out.println("${Colors.RED}Invalid name. Use lowercase letters, digits, hyphens.${Colors.RESET}")
        }
        return "my-cli"
    }

    private fun askPackageName(projectName: String): String {
        val suggested = "com.example.${projectName.replace("-", "")}"
        repeat(MAX_RETRIES) {
            val value = text("Package name", default = suggested)
            if (value.matches(ScaffoldConfig.PACKAGE_NAME_PATTERN)) return value
            out.println("${Colors.RED}Invalid package. Example: com.example.mycli${Colors.RESET}")
        }
        return suggested
    }

    private fun askModules(): Set<Module> {
        val options = Module.entries.map { "${it.id} : ${it.description}" }
        val picked = multiSelect("Optional modules (comma-separated, blank = all)", options)
        if (picked.isEmpty()) return Module.entries.toSet()
        return picked.mapNotNull { label ->
            val id = label.substringBefore(" :").trim()
            Module.fromId(id)
        }.toSet()
    }

    private fun askShellTargets(): Set<ShellTarget> {
        val options = ShellTarget.entries.map { it.id }
        val picked = multiSelect("Shell targets for tests (blank = all)", options)
        if (picked.isEmpty()) return ShellTarget.entries.toSet()
        return picked.mapNotNull { ShellTarget.fromId(it) }.toSet()
    }

    private fun text(prompt: String, default: String = ""): String {
        val hint = if (default.isNotEmpty()) " [$default]" else ""
        out.print("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt$hint: ")
        val line = reader.readLine()?.trim() ?: ""
        return line.ifEmpty { default }
    }

    private fun confirm(prompt: String, default: Boolean): Boolean {
        val hint = if (default) "Y/n" else "y/N"
        out.print("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt [$hint]: ")
        val line = reader.readLine()?.trim()?.lowercase() ?: ""
        return when {
            line.isEmpty() -> default
            line == "y" || line == "yes" -> true
            line == "n" || line == "no" -> false
            else -> default
        }
    }

    private fun multiSelect(prompt: String, options: List<String>): List<String> {
        out.println("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt")
        options.forEachIndexed { index, option ->
            out.println("  ${Colors.BLUE_BOLD}${index + 1})${Colors.RESET} $option")
        }
        out.print("  Enter numbers [1-${options.size}]: ")
        val line = reader.readLine()?.trim() ?: ""
        if (line.isEmpty()) return emptyList()
        return line
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..options.size }
            .distinct()
            .map { options[it - 1] }
    }

    companion object {
        const val DEFAULT_LARET_VERSION = "0.2.0"
        private const val MAX_RETRIES = 3
    }
}
