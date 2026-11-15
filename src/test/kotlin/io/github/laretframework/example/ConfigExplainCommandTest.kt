package io.github.laretframework.example

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.laretframework.config.TestCli
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigExplainCommandTest {
    @TempDir
    lateinit var tempDir: Path

    private fun app() = buildLaretApp().init(
        tempDir.resolve("config.yml").toFile().apply { writeText("app:\n  name: Test\n") }.absolutePath,
    )

    @Test
    fun test_explain_plain_missing_key_succeeds_with_localized_message() {
        val result = TestCli(app()).run("config", "explain", "--key", "unconfigured.value")

        assertEquals(0, result.exitCode)
        assertEquals("No layer defines this key: unconfigured.value", result.stdout.trim())
    }

    @Test
    fun test_explain_json_returns_key_winner_and_candidates() {
        val result = TestCli(app()).run("config", "explain", "--key", "unconfigured.value", "--format", "json")
        val json = jacksonObjectMapper().readTree(result.stdout)

        assertEquals(0, result.exitCode)
        assertEquals("unconfigured.value", json["key"].asText())
        assertTrue(json["winner"].isNull)
        assertTrue(json["candidates"].isArray)
    }

    @Test
    fun test_explain_app_default_reports_logging_info() {
        val explanation = buildLaretApp().explainConfigWithEnvironment("logging.level") { emptyMap() }

        assertEquals("INFO", explanation.winner?.value)
        assertEquals("default", explanation.winner?.source)
    }

    @Test
    fun test_explain_legacy_logging_environment_reports_exact_variable() {
        val explanation = buildLaretApp().explainConfigWithEnvironment("logging.level") {
            mapOf("LARET_LOG_LEVEL" to "DEBUG")
        }

        assertEquals("DEBUG", explanation.winner?.value)
        assertEquals("LARET_LOG_LEVEL", explanation.winner?.source)
    }

    @Test
    fun test_explain_selected_command_includes_declared_option_default() {
        val result = TestCli(app()).run(
            "config",
            "explain",
            "--key",
            "completion.dynamic",
            "--command",
            "completion bash",
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("completion.dynamic"))
        assertTrue(result.stdout.contains("default"))
    }

    @Test
    fun test_explain_rejects_unsupported_format() {
        val result = TestCli(app()).run("config", "explain", "--key", "some.key", "--format", "xml")

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("Format must be plain or json"))
    }

    @Test
    fun test_explain_rejects_unknown_target_command() {
        val result = TestCli(app()).run(
            "config",
            "explain",
            "--key",
            "some.key",
            "--command",
            "missing command",
        )

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("Cannot inspect command"))
    }
}
