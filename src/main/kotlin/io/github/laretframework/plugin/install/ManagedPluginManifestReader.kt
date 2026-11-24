package io.github.laretframework.plugin.install

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import io.github.laretframework.plugin.model.ManagedPluginArtifact
import io.github.laretframework.plugin.model.ManagedPluginManifest
import io.github.laretframework.plugin.model.PluginArtifactKind
import io.github.laretframework.plugin.model.PluginInstallShape
import io.github.laretframework.plugin.model.PluginIsolation
import io.github.laretframework.plugin.model.PluginPlatform
import io.github.laretframework.plugin.model.PluginProcessLifecycle
import io.github.laretframework.plugin.model.PluginStartCommand
import io.github.laretframework.plugin.model.PluginStopContract
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Local input is an explicit caller trust decision, not publisher authentication. */
class ManagedPluginManifestReader {
    fun write(manifest: ManagedPluginManifest): String = TomlMapper().writeValueAsString(
        linkedMapOf(
            "schema_version" to manifest.schemaVersion,
            "name" to manifest.name,
            "version" to manifest.version,
            "revision" to manifest.revision,
            "artifacts" to manifest.artifacts.map { artifact ->
                linkedMapOf<String, Any>(
                    "os" to artifact.platform.os,
                    "arch" to artifact.platform.arch,
                    "url" to artifact.url,
                    "sha256" to artifact.sha256,
                    "kind" to artifact.kind.name,
                    "shape" to artifact.shape.name,
                    "lifecycle" to artifact.lifecycle.name,
                    "start" to commandMap(artifact.start),
                    "isolation" to mapOf(
                        "config_root" to artifact.isolation.configRoot,
                        "data_root" to artifact.isolation.dataRoot,
                        "environment" to artifact.isolation.environment,
                    ),
                ).apply {
                    artifact.stop?.let { stop ->
                        put(
                            "stop",
                            mapOf(
                                "command" to commandMap(stop.command),
                                "identity" to stop.identity,
                                "timeout_seconds" to stop.timeoutSeconds,
                            ),
                        )
                    }
                }
            },
        ),
    )

    private fun commandMap(command: PluginStartCommand): Map<String, Any> =
        mapOf("executable" to command.executable, "arguments" to command.arguments.toTypedArray())

    fun readReviewed(path: Path): ManagedPluginManifest {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            "Reviewed manifest must be a regular local file"
        }
        require(Files.size(path) <= 1024 * 1024) { "Reviewed manifest exceeds 1 MiB" }
        return parse(Files.readString(path))
    }

    fun parse(document: String): ManagedPluginManifest {
        val root = TomlMapper().readTree(document)
        require(root != null && root.isObject) { "Managed manifest must be a TOML object" }
        root.only("schema_version", "name", "version", "revision", "artifacts")
        val artifacts = root.requiredNode("artifacts")
        require(artifacts.isArray) { "artifacts must be an array" }
        return ManagedPluginManifest(
            root.int("schema_version"),
            root.text("name"),
            root.text("version"),
            root.text("revision"),
            artifacts.map(::artifact),
        )
    }

    private fun artifact(node: JsonNode): ManagedPluginArtifact {
        node.only("os", "arch", "url", "sha256", "kind", "shape", "start", "isolation", "lifecycle", "stop")
        val isolation = node.requiredNode("isolation")
        isolation.only("config_root", "data_root", "environment")
        val environment = isolation.requiredNode("environment")
        require(environment.isObject && environment.all { it.isTextual }) { "environment must contain string values" }
        val stop = node.get("stop")?.let {
            it.only("command", "identity", "timeout_seconds")
            PluginStopContract(command(it.requiredNode("command")), it.text("identity"), it.int("timeout_seconds"))
        }
        return ManagedPluginArtifact(
            PluginPlatform(node.text("os"), node.text("arch")), node.text("url"), node.text("sha256"),
            PluginArtifactKind.valueOf(node.text("kind")), PluginInstallShape.valueOf(node.text("shape")),
            command(node.requiredNode("start")),
            PluginIsolation(
                isolation.text("config_root"),
                isolation.text("data_root"),
                environment.properties().associate { it.key to it.value.textValue() },
            ),
            PluginProcessLifecycle.valueOf(node.text("lifecycle")), stop,
        )
    }

    private fun command(node: JsonNode): PluginStartCommand {
        node.only("executable", "arguments")
        val arguments = node.requiredNode("arguments")
        require(arguments.isArray && arguments.all { it.isTextual }) { "arguments must be an array of literal strings" }
        return PluginStartCommand(node.text("executable"), arguments.map { it.textValue() })
    }

    private fun JsonNode.only(vararg names: String) {
        require(isObject) { "Expected a TOML table" }
        require(properties().all { it.key in names }) { "Unsupported managed manifest field" }
    }

    private fun JsonNode.requiredNode(key: String): JsonNode = get(key)
        ?: throw IllegalArgumentException("Missing manifest field: $key")

    private fun JsonNode.text(key: String): String = requiredNode(key).let {
        require(it.isTextual) { "$key must be a string" }
        it.textValue()
    }

    private fun JsonNode.int(key: String): Int = requiredNode(key).let {
        require(it.isIntegralNumber && it.canConvertToInt()) { "$key must be an integer" }
        it.intValue()
    }
}
