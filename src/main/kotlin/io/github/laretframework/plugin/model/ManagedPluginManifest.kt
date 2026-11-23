package io.github.laretframework.plugin.model

import java.net.URI
import java.util.Locale

enum class PluginArtifactKind { BINARY, ZIP, TAR_GZ }

enum class PluginInstallShape { SINGLE_FILE, DIRECTORY }

enum class PluginProcessLifecycle { FOREGROUND, PERSISTENT }

data class PluginPlatform(val os: String, val arch: String) {
    init {
        require(os in setOf("linux", "macos", "windows")) { "Unsupported plugin OS: $os" }
        require(arch in setOf("x86_64", "aarch64")) { "Unsupported plugin architecture: $arch" }
    }

    override fun toString(): String = "$os/$arch"

    companion object {
        fun current(os: String = System.getProperty("os.name"), arch: String = System.getProperty("os.arch")) =
            PluginPlatform(
                when {
                    os.startsWith("Windows", true) -> "windows"
                    os.startsWith("Mac", true) -> "macos"
                    os.equals("Linux", true) -> "linux"
                    else -> throw IllegalArgumentException("Unsupported plugin OS: $os")
                },
                when (arch.lowercase(Locale.ROOT)) {
                    "amd64", "x86_64" -> "x86_64"
                    "arm64", "aarch64" -> "aarch64"
                    else -> throw IllegalArgumentException("Unsupported plugin architecture: $arch")
                },
            )
    }
}

data class PluginStartCommand(val executable: String, val arguments: List<String> = emptyList()) {
    init {
        requirePluginRelativePath(executable)
        require(executable.substringAfterLast('/').lowercase(Locale.ROOT) !in SHELLS) {
            "Plugin commands must execute a payload program directly, not a shell"
        }
        require(arguments.none { '\u0000' in it }) { "Plugin arguments must not contain NUL" }
    }

    companion object {
        private val SHELLS =
            setOf("sh", "bash", "zsh", "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh", "pwsh.exe")
    }
}

data class PluginIsolation(val configRoot: String, val dataRoot: String, val environment: Map<String, String>) {
    init {
        requirePluginRelativePath(configRoot)
        requirePluginRelativePath(dataRoot)
        require(
            configRoot != dataRoot && !configRoot.startsWith("$dataRoot/") && !dataRoot.startsWith("$configRoot/"),
        ) {
            "Configuration and data roots must be distinct, non-overlapping managed paths"
        }
        require(environment.isNotEmpty()) { "Managed plugins must declare isolation environment settings" }
        require(environment.keys.all { it.matches(Regex("[A-Z_][A-Z0-9_]*")) && !it.startsWith("LARET_") }) {
            "Invalid or reserved isolation environment name"
        }
        require(environment.values.none { '\u0000' in it }) { "Isolation values must not contain NUL" }
        require(environment.values.any { "{config}" in it } && environment.values.any { "{data}" in it }) {
            "Isolation must reference both {config} and {data} managed roots"
        }
        require(
            environment.values.all { value ->
                !Regex("\\{[^}]*}").findAll(value).any { it.value !in setOf("{config}", "{data}", "{instance}") }
            },
        ) { "Unknown isolation placeholder" }
    }
}

data class PluginStopContract(val command: PluginStartCommand, val identity: String, val timeoutSeconds: Int) {
    init {
        require(identity.isNotBlank() && "{instance}" in identity) { "Persistent identity must include {instance}" }
        require(timeoutSeconds in 1..300) { "Stop timeout must be between 1 and 300 seconds" }
    }
}

data class ManagedPluginArtifact(
    val platform: PluginPlatform,
    val url: String,
    val sha256: String,
    val kind: PluginArtifactKind,
    val shape: PluginInstallShape,
    val start: PluginStartCommand,
    val isolation: PluginIsolation,
    val lifecycle: PluginProcessLifecycle = PluginProcessLifecycle.FOREGROUND,
    val stop: PluginStopContract? = null,
) {
    init {
        val uri = URI(url)
        require(
            uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.fragment == null,
        ) {
            "Managed artifact URL must be an absolute HTTPS URL without credentials or fragment"
        }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) {
            "Artifact SHA-256 must contain exactly 64 hexadecimal characters"
        }
        require(kind == PluginArtifactKind.BINARY || shape == PluginInstallShape.DIRECTORY) {
            "Archives require a directory-shaped installation"
        }
        require(shape != PluginInstallShape.SINGLE_FILE || '/' !in start.executable) {
            "Single-file entry point must be a filename"
        }
        require((lifecycle == PluginProcessLifecycle.PERSISTENT) == (stop != null)) {
            "Only persistent plugins require a stop contract"
        }
    }
}

data class ManagedPluginManifest(
    val schemaVersion: Int,
    val name: String,
    val version: String,
    val revision: String,
    val artifacts: List<ManagedPluginArtifact>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported managed manifest schema: $schemaVersion" }
        require(name.matches(Regex("[a-z][a-z0-9-]*"))) { "Plugin name must use lowercase kebab-case" }
        require(version.isNotBlank() && version !in setOf("latest", "stable", "preview")) {
            "Plugin version must be pinned"
        }
        require(revision.isNotBlank()) { "Reviewed manifest revision is required" }
        require(artifacts.isNotEmpty()) { "Manifest has no platform artifacts" }
        require(artifacts.map { it.platform }.distinct().size == artifacts.size) { "Duplicate platform artifacts" }
    }

    fun select(version: String, platform: PluginPlatform): ManagedPluginArtifact {
        require(version == this.version) { "Version '$version' is not the reviewed version '${this.version}'" }
        return artifacts.singleOrNull { it.platform == platform }
            ?: throw IllegalArgumentException(
                "Unsupported target $platform for $name $version; approved targets: ${artifacts.joinToString {
                    it.platform.toString()
                }}",
            )
    }
}

internal fun requirePluginRelativePath(value: String) {
    require(value.isNotBlank() && value.none { it == '\\' || it == ':' || it == '\u0000' } && !value.startsWith('/')) {
        "Plugin path must be a portable relative path: $value"
    }
    require(value.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "Plugin path contains an empty or traversal segment: $value"
    }
}
