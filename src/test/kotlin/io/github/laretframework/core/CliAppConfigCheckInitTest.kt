package io.github.laretframework.core

import io.github.laretframework.example.buildLaretApp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CliAppConfigCheckInitTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun test_validation_state_is_unknown_before_initialization() {
        val result = CliApp("test").getConfigValidationResult()

        assertFalse(result.isKnown)
        assertFalse(result.isValid)
    }

    @Test
    fun test_config_check_init_keeps_validation_errors_for_reporting() {
        val config = tempDir.resolve("invalid.yml").toFile().apply {
            writeText("output:\n  format: xml\n")
        }
        val app = buildLaretApp()

        val exitCode = app.runForTest(arrayOf("config", "check", "--config", config.absolutePath))

        assertTrue(exitCode != 0)
        assertTrue(app.getConfigValidationResult().isKnown)
        assertFalse(app.getConfigValidationResult().isValid)
        kotlin.test.assertNull(app.getConfigLoadError())
    }

    @Test
    fun test_fail_fast_default_still_rejects_invalid_config() {
        val config = tempDir.resolve("invalid.yml").toFile().apply {
            writeText("output:\n  format: xml\n")
        }
        val app = CliApp("test")

        assertFailsWith<RuntimeException> { app.init(config.absolutePath) }
    }

    @Test
    fun test_diagnostic_init_captures_config_load_error() {
        val config = tempDir.resolve("broken.yml").toFile().apply { writeText("output: [") }
        val app = CliApp("test")

        val exitCode = app.runForTest(arrayOf("config", "check", "--config", config.absolutePath))

        assertTrue(exitCode != 0)
        assertNotNull(app.getConfigLoadError())
    }

    @Test
    fun test_malformed_config_check_skips_initialization_hooks() {
        val config = tempDir.resolve("broken.yml").toFile().apply { writeText("output: [") }
        val app = buildLaretApp()
        var hookCalled = false
        app.onInitHook = { hookCalled = true }

        app.runForTest(arrayOf("config", "check", "--config", config.absolutePath))

        assertFalse(hookCalled)
    }
}
