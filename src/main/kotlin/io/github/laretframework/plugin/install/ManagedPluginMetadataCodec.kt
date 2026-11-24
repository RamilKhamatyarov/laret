package io.github.laretframework.plugin.install

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import io.github.laretframework.plugin.model.ManagedPluginMetadata
import io.github.laretframework.plugin.model.PluginPlatform

class ManagedPluginMetadataCodec {
    private val manifests = ManagedPluginManifestReader()

    fun write(metadata: ManagedPluginMetadata): String = TomlMapper().writeValueAsString(
        linkedMapOf(
            "schema_version" to metadata.schemaVersion,
            "manifest_document" to manifests.write(metadata.manifest),
            "os" to metadata.platform.os,
            "arch" to metadata.platform.arch,
            "installed_at" to metadata.installedAt,
            "instance" to metadata.instance,
            "integrity_policy" to metadata.integrityPolicy,
            "inventory" to metadata.inventory,
        ),
    )

    fun read(document: String): ManagedPluginMetadata {
        val root = TomlMapper().readTree(document)
        require(root != null && root.isObject) { "Managed metadata must be a TOML object" }
        val fields =
            setOf(
                "schema_version",
                "manifest_document",
                "os",
                "arch",
                "installed_at",
                "instance",
                "integrity_policy",
                "inventory",
            )
        require(root.properties().map { it.key }.toSet() == fields) { "Missing or unsupported managed metadata fields" }
        fun text(key: String): String {
            require(root[key].isTextual) { "$key must be a string" }
            return root[key].textValue()
        }
        require(root["schema_version"].isIntegralNumber && root["schema_version"].canConvertToInt()) {
            "schema_version must be an integer"
        }
        val inventory = root["inventory"]
        require(inventory.isObject && inventory.all { it.isTextual }) { "inventory must map paths to digests" }
        return ManagedPluginMetadata(
            root["schema_version"].intValue(),
            manifests.parse(text("manifest_document")),
            PluginPlatform(text("os"), text("arch")),
            text("installed_at"),
            text("instance"),
            inventory.properties().associate { it.key to it.value.textValue() },
            text("integrity_policy"),
        )
    }
}
