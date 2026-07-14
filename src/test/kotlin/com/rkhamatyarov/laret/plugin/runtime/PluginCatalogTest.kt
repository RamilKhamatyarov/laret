package com.rkhamatyarov.laret.plugin.runtime

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.rkhamatyarov.laret.plugin.install.PluginInstaller
import com.rkhamatyarov.laret.plugin.model.PluginMetadata
import com.rkhamatyarov.laret.plugin.model.PluginStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginCatalogTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `catalog reports checksum mismatch as invalid`() {
        val executable = tempDir.resolve(PluginInstaller.platformFileName("formatter"))
        Files.writeString(executable, "fixture")
        executable.toFile().setExecutable(true)
        TomlMapper().writeValue(
            tempDir.resolve("formatter.toml").toFile(),
            PluginMetadata("formatter", "https://example.test/formatter", "0".repeat(64), Instant.EPOCH.toString()),
        )

        val entry = PluginCatalog(listOf(tempDir), isWindows = false).refresh().single()

        assertEquals(PluginStatus.INVALID, entry.status)
        assertTrue(entry.reason.orEmpty().contains("checksum"))
    }

    @Test
    fun `catalog marks duplicate plugin in later directory as shadowed`() {
        val first = tempDir.resolve("first")
        val second = tempDir.resolve("second")
        Files.createDirectories(first)
        Files.createDirectories(second)
        writePlugin(first, "formatter")
        writePlugin(second, "formatter")

        val entries = PluginCatalog(listOf(first, second), isWindows = false).refresh()

        assertEquals(listOf(PluginStatus.INSTALLED, PluginStatus.SHADOWED), entries.map { it.status })
    }

    private fun writePlugin(directory: Path, name: String) {
        val executable = directory.resolve(PluginInstaller.platformFileName(name))
        Files.writeString(executable, "fixture")
        executable.toFile().setExecutable(true)
        val digest = PluginInstaller.sha256(executable)
        TomlMapper().writeValue(
            directory.resolve("$name.toml").toFile(),
            PluginMetadata(name, "https://example.test/$name", digest, Instant.EPOCH.toString()),
        )
    }
}
