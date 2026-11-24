package io.github.laretframework.plugin.model

import java.time.Instant
import java.util.Locale

data class ManagedPluginMetadata(
    val schemaVersion: Int,
    val manifest: ManagedPluginManifest,
    val platform: PluginPlatform,
    val installedAt: String,
    val instance: String,
    val inventory: Map<String, String>,
    val integrityPolicy: String = "strict",
) {
    init {
        require(schemaVersion == 1) { "Unsupported managed metadata schema: $schemaVersion" }
        require(integrityPolicy == "strict") { "Unsupported integrity policy: $integrityPolicy" }
        Instant.parse(installedAt)
        require(instance.matches(Regex("[a-zA-Z0-9-]{1,128}"))) { "Invalid managed instance identity" }
        val artifact = manifest.select(manifest.version, platform)
        require(inventory.isNotEmpty() && artifact.start.executable in inventory) {
            "Inventory must contain the start executable"
        }
        artifact.stop?.let {
            require(it.command.executable in inventory) { "Inventory must contain the stop executable" }
        }
        inventory.forEach { (path, digest) ->
            requirePluginRelativePath(path)
            require(digest.matches(Regex("[a-f0-9]{64}"))) { "Inventory digest must be lowercase SHA-256" }
        }
        require(inventory.keys.map { it.lowercase(Locale.ROOT) }.distinct().size == inventory.size) {
            "Inventory has case-colliding paths"
        }
    }
}
