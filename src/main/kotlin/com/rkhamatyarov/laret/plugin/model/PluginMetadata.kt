package com.rkhamatyarov.laret.plugin.model

data class PluginMetadata(val name: String, val source_url: String, val sha256: String, val installed_at: String)

enum class PluginStatus {
    INSTALLED,
    INVALID,
    SHADOWED,
}

data class InstalledPlugin(
    val name: String,
    val executable: java.nio.file.Path?,
    val metadataFile: java.nio.file.Path?,
    val status: PluginStatus,
    val reason: String? = null,
    val metadata: PluginMetadata? = null,
)
