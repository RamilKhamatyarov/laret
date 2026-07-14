package com.rkhamatyarov.laret.plugin.install

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
}
