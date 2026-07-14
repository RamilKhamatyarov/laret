package com.rkhamatyarov.laret.plugin.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains

class NativePluginReflectionConfigTest {
    @Test
    fun `native reflection config includes plugin metadata models`() {
        val configPath = Path.of(
            "src/main/resources/META-INF/native-image/reflect-config.json",
        )
        val configuredTypes = jacksonObjectMapper()
            .readTree(Files.readString(configPath))
            .map { it.path("name").asText() }
            .toSet()

        assertContains(configuredTypes, PluginConfig::class.qualifiedName)
        assertContains(configuredTypes, PluginMetadata::class.qualifiedName)
    }
}
