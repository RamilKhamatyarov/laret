package io.github.laretframework.plugin.install

import io.github.laretframework.plugin.model.ManagedPluginMetadata
import io.github.laretframework.plugin.model.PluginPlatform
import io.github.laretframework.plugin.model.PluginProcessLifecycle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedPluginManifestReaderTest {
    @TempDir
    lateinit var directory: Path

    private val reader = ManagedPluginManifestReader()

    @Test
    fun `platform aliases normalize without guessing unsupported architectures`() {
        assertEquals(PluginPlatform("windows", "x86_64"), PluginPlatform.current("Windows 11", "amd64"))
        assertEquals(PluginPlatform("macos", "aarch64"), PluginPlatform.current("Mac OS X", "arm64"))
        assertFailsWith<IllegalArgumentException> { PluginPlatform.current("Linux", "riscv64") }
    }

    @Test
    fun `metadata rejects unsafe incomplete and ambiguous inventories`() {
        val metadata = ManagedPluginMetadata(
            1,
            reader.parse(manifest()),
            PluginPlatform("linux", "x86_64"),
            "2026-01-01T00:00:00Z",
            "instance-1",
            mapOf("bin/viewer" to "b".repeat(64)),
        )
        listOf(
            emptyMap(),
            mapOf("../viewer" to "b".repeat(64)),
            mapOf("bin/viewer" to "B".repeat(64)),
            mapOf("bin/viewer" to "b".repeat(64), "BIN/viewer" to "b".repeat(64)),
        ).forEach { assertFailsWith<IllegalArgumentException> { metadata.copy(inventory = it) } }
    }

    @Test
    fun `empty argument arrays round trip and unknown metadata is rejected`() {
        val manifest = reader.parse(manifest().replace("[\"--literal\", \"hello; echo not-a-shell\"]", "[]"))
        assertEquals(manifest, reader.parse(reader.write(manifest)))
        val codec = ManagedPluginMetadataCodec()
        assertFailsWith<IllegalArgumentException> { codec.read("schema_version = 2") }
    }

    @Test
    fun `manifest and metadata round trip without reflective model serialization`() {
        val manifest = reader.parse(manifest())
        assertEquals(manifest, reader.parse(reader.write(manifest)))
        val metadata = ManagedPluginMetadata(
            1,
            manifest,
            PluginPlatform("linux", "x86_64"),
            "2026-01-01T00:00:00Z",
            "instance-1",
            mapOf("bin/viewer" to "b".repeat(64)),
        )
        val codec = ManagedPluginMetadataCodec()
        assertEquals(metadata, codec.read(codec.write(metadata)))
        assertFailsWith<IllegalArgumentException> { metadata.copy(schemaVersion = 2) }
        assertFailsWith<IllegalArgumentException> { metadata.copy(integrityPolicy = "accept-updates") }
    }

    @Test
    fun `reviewed local manifest selects exact foreground artifact`() {
        val path = directory.resolve("reviewed.toml")
        Files.writeString(path, manifest())
        val parsed = reader.readReviewed(path)
        val artifact = parsed.select("1.0.0", PluginPlatform("linux", "x86_64"))
        assertEquals(PluginProcessLifecycle.FOREGROUND, artifact.lifecycle)
        assertNull(artifact.stop)
        assertEquals(listOf("--literal", "hello; echo not-a-shell"), artifact.start.arguments)
    }

    @Test
    fun `unsupported platform lists approved targets`() {
        val error = assertFailsWith<IllegalArgumentException> {
            reader.parse(manifest()).select("1.0.0", PluginPlatform("windows", "aarch64"))
        }
        assertTrue(error.message.orEmpty().contains("approved targets: linux/x86_64"))
    }

    @Test
    fun `unpinned or different version is rejected`() {
        assertFailsWith<IllegalArgumentException> { reader.parse(manifest().replace("1.0.0", "latest")) }
        assertFailsWith<IllegalArgumentException> {
            reader.parse(manifest()).select("2.0.0", PluginPlatform("linux", "x86_64"))
        }
    }

    @Test
    fun `missing malformed checksum and HTTP source are rejected`() {
        listOf(
            manifest().replace("sha256 = \"${"a".repeat(64)}\"", ""),
            manifest().replace("a".repeat(64), "not-a-digest"),
            manifest().replace("https://", "http://"),
        ).forEach { assertFailsWith<IllegalArgumentException> { reader.parse(it) } }
    }

    @Test
    fun `unknown schema fields and duplicate platforms fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            reader.parse(manifest().replace("schema_version = 1", "schema_version = 2"))
        }
        assertFailsWith<IllegalArgumentException> { reader.parse(manifest() + "\nunreviewed = true\n") }
        val parsed = reader.parse(manifest())
        assertFailsWith<IllegalArgumentException> { parsed.copy(artifacts = parsed.artifacts + parsed.artifacts) }
    }

    @Test
    fun `portable paths reject traversal absolute drive and shell commands`() {
        listOf("../viewer", "/viewer", "C:/viewer.exe", "bin//viewer", "bin/sh").forEach { path ->
            assertFailsWith<IllegalArgumentException> { reader.parse(manifest().replace("bin/viewer", path)) }
        }
    }

    @Test
    fun `persistent plugin requires bounded instance scoped stop`() {
        val persistent = manifest().replace("FOREGROUND", "PERSISTENT")
        assertFailsWith<IllegalArgumentException> { reader.parse(persistent) }
        val stop = """

            [artifacts.stop]
            identity = "viewer-{instance}"
            timeout_seconds = 10
            [artifacts.stop.command]
            executable = "bin/viewer"
            arguments = ["server", "stop"]
        """.trimIndent()
        assertEquals(10, reader.parse(persistent + "\n" + stop).artifacts.single().stop?.timeoutSeconds)
        assertFailsWith<IllegalArgumentException> { reader.parse(persistent + "\n" + stop.replace("= 10", "= 0")) }
        assertFailsWith<IllegalArgumentException> {
            reader.parse(persistent + "\n" + stop.replace("viewer-{instance}", "shared"))
        }
    }

    @Test
    fun `isolation rejects shared roots reserved variables and missing placeholders`() {
        listOf(
            manifest().replace("data_root = \"data\"", "data_root = \"config\""),
            manifest().replace("VIEWER_CONFIG", "LARET_PROFILE"),
            manifest().replace("{data}", "/shared/data"),
        ).forEach { assertFailsWith<IllegalArgumentException> { reader.parse(it) } }
    }

    private fun manifest(): String = """
        schema_version = 1
        name = "viewer"
        version = "1.0.0"
        revision = "review-42"
        [[artifacts]]
        os = "linux"
        arch = "x86_64"
        url = "https://example.test/releases/1.0.0/viewer.tar.gz"
        sha256 = "${"a".repeat(64)}"
        kind = "TAR_GZ"
        shape = "DIRECTORY"
        lifecycle = "FOREGROUND"
        [artifacts.start]
        executable = "bin/viewer"
        arguments = ["--literal", "hello; echo not-a-shell"]
        [artifacts.isolation]
        config_root = "config"
        data_root = "data"
        [artifacts.isolation.environment]
        VIEWER_CONFIG = "{config}"
        VIEWER_DATA = "{data}"
    """.trimIndent()
}
