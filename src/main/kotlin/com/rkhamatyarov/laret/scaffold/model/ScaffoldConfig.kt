package com.rkhamatyarov.laret.scaffold.model

enum class Module(val id: String, val description: String) {
    CONFIG("config", "12-factor config loading (YAML/TOML/JSON)"),
    COMPLETION("completion", "Bash/Zsh/PowerShell completion generators"),
    UI("ui", "Colors, ProgressBar, Spinner, InteractivePrompt"),
    OUTPUT("output", "Pluggable JSON/YAML/Plain/Table formatters"),
    ;

    companion object {
        fun fromId(id: String): Module? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class ShellTarget(val id: String) {
    BASH("bash"),
    ZSH("zsh"),
    POWERSHELL("powershell"),
    ;

    companion object {
        fun fromId(id: String): ShellTarget? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

data class ScaffoldConfig(
    val projectName: String,
    val packageName: String,
    val appName: String,
    val laretVersion: String,
    val modules: Set<Module>,
    val shellTests: Set<ShellTarget>,
    val graalvm: Boolean,
) {
    init {
        require(projectName.matches(PROJECT_NAME_PATTERN)) {
            "Invalid project name: '$projectName' (expected: lower-kebab, e.g. my-cli)"
        }
        require(packageName.matches(PACKAGE_NAME_PATTERN)) {
            "Invalid package name: '$packageName' (expected: dotted lowercase, e.g. com.example.cli)"
        }
        require(appName.isNotBlank()) { "App name must not be blank" }
    }

    val groupId: String get() = packageName.substringBeforeLast('.', missingDelimiterValue = packageName)
    val artifactId: String get() = projectName

    companion object {
        val PROJECT_NAME_PATTERN = Regex("""^[a-z][a-z0-9-]*$""")
        val PACKAGE_NAME_PATTERN = Regex("""^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$""")
    }
}
