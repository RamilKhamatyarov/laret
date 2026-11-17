package io.github.laretframework.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class ConfigLoaderEnvironmentTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun test_typed_config_applies_injected_environment_overrides() {
        val file = tempDir.resolve("app.yml").toFile().apply {
            writeText("output:\n  format: plain\nlogging:\n  level: INFO\n")
        }
        val env = mapOf("LARET_OUTPUT_FORMAT" to "json", "LARET_LOG_LEVEL" to "DEBUG")
        val loader = ConfigLoader(
            envProvider = env::get,
            fileResolver = ConfigFileResolver(
                workDir = File(tempDir.toUri()),
                homeDir = File(tempDir.toUri()),
            ),
        )

        val config = loader.load(file.absolutePath)

        assertEquals("json", config.output.format)
        assertEquals("DEBUG", config.logging.level)
    }

    @Test
    fun test_typed_config_applies_environment_overrides_without_a_file() {
        val loader = ConfigLoader(
            envProvider = mapOf("LARET_LOG_LEVEL" to "DEBUG")::get,
            fileResolver = ConfigFileResolver(
                workDir = File(tempDir.toUri()),
                homeDir = File(tempDir.toUri()),
                exists = { false },
            ),
        )

        assertEquals("DEBUG", loader.load().logging.level)
    }
}
