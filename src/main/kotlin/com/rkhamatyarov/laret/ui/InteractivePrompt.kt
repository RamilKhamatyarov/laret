package com.rkhamatyarov.laret.ui

import java.io.InputStream
import java.io.PrintStream

class InteractivePrompt(
    val input: InputStream = System.`in`,
    val out: PrintStream = System.err,
    private val enabled: Boolean = true,
) {
    private val reader
        get() = input.bufferedReader()

    fun text(prompt: String, default: String = ""): String {
        if (!enabled) return default
        val hint = if (default.isNotEmpty()) " [$default]" else ""
        out.print("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt$hint: ")
        val line = reader.readLine()?.trim() ?: ""
        return line.ifEmpty { default }
    }

    fun confirm(prompt: String, default: Boolean = true): Boolean {
        if (!enabled) return default
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

    fun select(prompt: String, options: List<String>): String {
        require(options.isNotEmpty()) { "options must not be empty" }
        if (!enabled) return options.first()
        out.println("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt")
        options.forEachIndexed { index, option ->
            out.println("  ${Colors.BLUE_BOLD}${index + 1})${Colors.RESET} $option")
        }
        out.print("  Enter number [1-${options.size}]: ")
        val line = reader.readLine()?.trim() ?: "1"
        val index = (line.toIntOrNull() ?: 1).coerceIn(1, options.size) - 1
        return options[index]
    }

    fun multiSelect(prompt: String, options: List<String>): List<String> {
        require(options.isNotEmpty()) { "options must not be empty" }
        if (!enabled) return emptyList()
        out.println("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt (comma-separated numbers)")
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

    fun password(prompt: String): String {
        if (!enabled) return ""
        out.print("${Colors.CYAN_BOLD}?${Colors.RESET} $prompt: ")
        return reader.readLine()?.trim() ?: ""
    }
}
