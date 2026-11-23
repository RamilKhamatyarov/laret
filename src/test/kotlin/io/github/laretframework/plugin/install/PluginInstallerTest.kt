package io.github.laretframework.plugin.install

import io.github.laretframework.plugin.model.PluginPlatform
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginInstallerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `install verifies checksum and writes executable and metadata`() {
        val payload = "#!/bin/sh\nprintf 'fixture:%s\\n' \"\$*\"\n".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val client = PluginDownloadClient { _, target ->
            Files.write(target, payload)
            Result.success(payload.size.toLong())
        }

        val result = PluginInstaller(client).install(
            name = "ci-fixture",
            url = "https://example.test/ci-fixture",
            expectedSha256 = digest,
            directory = tempDir,
        )

        assertTrue(result.isSuccess)
        assertTrue(Files.exists(tempDir.resolve("ci-fixture.toml")))
        assertTrue(Files.exists(tempDir.resolve(PluginInstaller.platformFileName("ci-fixture"))))
    }

    @Test
    fun `install removes temporary artifact when checksum mismatches`() {
        val client = PluginDownloadClient { _, target ->
            Files.writeString(target, "wrong")
            Result.success(5L)
        }

        val result = PluginInstaller(client).install(
            name = "ci-fixture",
            url = "https://example.test/ci-fixture",
            expectedSha256 = "0".repeat(64),
            directory = tempDir,
        )

        assertTrue(result.isFailure)
        assertFalse(Files.exists(tempDir.resolve("ci-fixture.toml")))
        assertFalse(Files.list(tempDir).use { it.anyMatch { file -> file.fileName.toString().contains("download") } })
    }

    @Test
    fun `install rejects non HTTPS URL`() {
        val result = PluginInstaller(downloadClient = PluginDownloadClient { _, _ -> Result.success(0L) }).install(
            name = "ci-fixture",
            url = "http://example.test/ci-fixture",
            expectedSha256 = "0".repeat(64),
            directory = tempDir,
        )

        assertTrue(result.isFailure)
        assertEquals(0, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `managed binary install verifies manifest digest and records complete inventory`() {
        val payload = "binary-fixture".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val manifest = managedManifest(digest, "BINARY", "SINGLE_FILE")
        val client = PluginDownloadClient { _, target ->
            Files.write(target, payload)
            Result.success(payload.size.toLong())
        }

        val result = PluginInstaller(
            client,
        ).installManaged(manifest, "1.2.3", PluginPlatform("linux", "x86_64"), tempDir)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        val executable = tempDir.resolve(PluginInstaller.platformFileName("managed-fixture"))
        assertEquals(payload.toList(), Files.readAllBytes(executable).toList())
        val metadata = ManagedPluginMetadataCodec().read(Files.readString(tempDir.resolve("managed-fixture.toml")))
        assertEquals(mapOf("managed-fixture" to digest), metadata.inventory)
    }

    @Test
    fun `managed archive install records all companion files`() {
        val archive = tempDir.resolve("fixture.zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            listOf("bin/tool" to "exe", "bin/runtime" to "dll").forEach { (name, content) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        val digest = PluginInstaller.sha256(archive)
        val client = PluginDownloadClient { _, target ->
            Files.copy(archive, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            Result.success(Files.size(target))
        }
        val manifest = managedManifest(digest, "ZIP", "DIRECTORY", "bin/tool")

        val result = PluginInstaller(
            client,
        ).installManaged(manifest, "1.2.3", PluginPlatform("linux", "x86_64"), tempDir)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals("dll", Files.readString(tempDir.resolve("managed-fixture/bin/runtime")))
        val metadata = ManagedPluginMetadataCodec().read(Files.readString(tempDir.resolve("managed-fixture.toml")))
        assertEquals(setOf("bin/tool", "bin/runtime"), metadata.inventory.keys)
    }

    @Test
    fun `managed wrong digest and failed download publish no installation or staging files`() {
        val wrongDigest = managedManifest("0".repeat(64), "BINARY", "SINGLE_FILE")
        val installer = PluginInstaller(
            PluginDownloadClient { _, target ->
                Files.writeString(target, "wrong")
                Result.success(5L)
            },
        )
        assertTrue(installer.installManaged(wrongDigest, "1.2.3", PluginPlatform("linux", "x86_64"), tempDir).isFailure)
        assertEquals(0, Files.list(tempDir).use { it.count() })

        val failedDownload = PluginInstaller(PluginDownloadClient { _, _ -> Result.failure(IOException("offline")) })
        assertTrue(
            failedDownload.installManaged(wrongDigest, "1.2.3", PluginPlatform("linux", "x86_64"), tempDir).isFailure,
        )
        assertEquals(0, Files.list(tempDir).use { it.count() })
    }

    private fun managedManifest(digest: String, kind: String, shape: String, executable: String = "managed-fixture") =
        ManagedPluginManifestReader().parse(
            """
                schema_version = 1
                name = "managed-fixture"
                version = "1.2.3"
                revision = "reviewed-fixture"
                [[artifacts]]
                os = "linux"
                arch = "x86_64"
                url = "https://example.test/fixture"
                sha256 = "$digest"
                kind = "$kind"
                shape = "$shape"
                lifecycle = "FOREGROUND"
                [artifacts.start]
                executable = "$executable"
                arguments = []
                [artifacts.isolation]
                config_root = "config"
                data_root = "data"
                [artifacts.isolation.environment]
                FIXTURE_CONFIG = "{config}"
                FIXTURE_DATA = "{data}"
            """.trimIndent(),
        )
}
