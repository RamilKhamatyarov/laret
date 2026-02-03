package com.rkhamatyarov.laret.output

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlOutputTest {
    @Test
    fun `yaml output should have name 'yaml'`() {
        assertEquals("yaml", YamlOutput.name)
    }

    @Test
    fun `should render simple map to YAML format`() {
        val data =
            mapOf(
                "name" to "Laret",
                "version" to "1.0",
            )
        val result = YamlOutput.render(data)
        assertTrue(result.contains("name"))
        assertTrue(result.contains("Laret"))
        assertTrue(result.contains("version"))
        assertTrue(result.contains("1.0"))
    }

    @Test
    fun `should render nested map to YAML format`() {
        val data =
            mapOf(
                "project" to
                    mapOf(
                        "name" to "Laret CLI",
                        "version" to "1.0.0",
                    ),
                "build" to
                    mapOf(
                        "language" to "Kotlin",
                        "jvm" to "24",
                    ),
            )
        val result = YamlOutput.render(data)
        assertTrue(result.contains("project"))
        assertTrue(result.contains("Laret CLI"))
        assertTrue(result.contains("build"))
        assertTrue(result.contains("Kotlin"))
    }

    @Test
    fun `should render list in YAML format`() {
        val data =
            mapOf(
                "files" to listOf("file1.kt", "file2.kt", "file3.kt"),
            )
        val result = YamlOutput.render(data)
        assertTrue(result.contains("files"))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `should render data consistently`() {
        val data =
            mapOf(
                "app" to "Laret",
                "type" to "CLI Tool",
            )
        val result1 = YamlOutput.render(data)
        val result2 = YamlOutput.render(data)
        assertEquals(result1, result2)
    }

    @Test
    fun `should handle empty map gracefully`() {
        val data = emptyMap<String, Any>()
        val result = YamlOutput.render(data)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `should handle numeric values in YAML format`() {
        val data =
            mapOf(
                "port" to 8080,
                "timeout" to 30,
                "version" to 1.0,
            )
        val result = YamlOutput.render(data)
        assertTrue(result.contains("port"))
        assertTrue(result.contains("8080"))
    }

    @Test
    fun `should handle boolean values in YAML format`() {
        val data =
            mapOf(
                "enabled" to true,
                "debug" to false,
            )
        val result = YamlOutput.render(data)
        assertTrue(result.contains("enabled"))
        assertTrue(result.contains("debug"))
    }

    @Test
    fun `should render complex config-like structure`() {
        val config =
            mapOf(
                "app" to
                    mapOf(
                        "name" to "Laret",
                        "version" to "1.0.0",
                        "enabled" to true,
                    ),
                "server" to
                    mapOf(
                        "host" to "localhost",
                        "port" to 8080,
                        "ssl" to false,
                    ),
                "features" to listOf("json", "yaml", "toml"),
            )
        val result = YamlOutput.render(config)
        assertTrue(result.contains("app"))
        assertTrue(result.contains("server"))
        assertTrue(result.contains("features"))
        assertTrue(result.contains("Laret"))
        assertTrue(result.contains("localhost"))
        assertTrue(result.contains("8080"))
    }

    @Test
    fun `YAML output should contain colons for key-value pairs`() {
        val data = mapOf("key" to "value")
        val result = YamlOutput.render(data)
        assertTrue(result.contains(":"))
    }
}
