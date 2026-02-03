package com.rkhamatyarov.laret.output

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonOutputTest {
    @Test
    fun `json output should have name 'json'`() {
        assertEquals("json", JsonOutput.name)
    }

    @Test
    fun `should render simple map to JSON format`() {
        val data =
            mapOf(
                "name" to "Laret",
                "version" to "1.0",
            )
        val result = JsonOutput.render(data)
        assertTrue(result.contains("name"))
        assertTrue(result.contains("Laret"))
        assertTrue(result.contains("version"))
        assertTrue(result.contains("1.0"))
    }

    @Test
    fun `should render nested map to JSON format`() {
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
        val result = JsonOutput.render(data)
        assertTrue(result.contains("project"))
        assertTrue(result.contains("Laret CLI"))
        assertTrue(result.contains("build"))
        assertTrue(result.contains("Kotlin"))
    }

    @Test
    fun `should render list in JSON format`() {
        val data =
            mapOf(
                "files" to listOf("file1.kt", "file2.kt", "file3.kt"),
            )
        val result = JsonOutput.render(data)
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
        val result1 = JsonOutput.render(data)
        val result2 = JsonOutput.render(data)
        assertEquals(result1, result2)
    }

    @Test
    fun `should handle empty map gracefully`() {
        val data = emptyMap<String, Any>()
        val result = JsonOutput.render(data)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `should handle numeric values in JSON format`() {
        val data =
            mapOf(
                "port" to 8080,
                "timeout" to 30,
                "version" to 1.0,
            )
        val result = JsonOutput.render(data)
        assertTrue(result.contains("port"))
        assertTrue(result.contains("8080"))
    }

    @Test
    fun `should handle boolean values in JSON format`() {
        val data =
            mapOf(
                "enabled" to true,
                "debug" to false,
            )
        val result = JsonOutput.render(data)
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
        val result = JsonOutput.render(config)
        assertTrue(result.contains("app"))
        assertTrue(result.contains("server"))
        assertTrue(result.contains("features"))
        assertTrue(result.contains("Laret"))
        assertTrue(result.contains("localhost"))
        assertTrue(result.contains("8080"))
    }

    @Test
    fun `JSON output should be valid JSON format`() {
        val data = mapOf("key" to "value")
        val result = JsonOutput.render(data)
        assertTrue(result.startsWith("{"))
        assertTrue(result.endsWith("}"))
    }
}
