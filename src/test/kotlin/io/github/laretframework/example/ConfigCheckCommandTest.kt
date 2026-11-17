package io.github.laretframework.example

import io.github.laretframework.config.TestCli
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigCheckCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun test_config_check_valid_config_exits_zero() {
        val config = tempDir.resolve("valid.yml").toFile().apply { writeText("app:\n  name: Valid\n") }

        val result = TestCli(buildLaretApp()).run("config", "check", "--config", config.absolutePath)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("valid=true"))
    }

    @Test
    fun test_config_check_fails_when_the_requested_file_does_not_exist() {
        val missing = tempDir.resolve("typo.yml").toFile()

        val result = TestCli(buildLaretApp()).run("config", "check", "--config", missing.absolutePath)

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("valid=false"))
        assertTrue(result.stdout.contains("Configuration file not found: ${missing.absolutePath}"))
    }

    @Test
    fun test_config_check_fails_when_the_requested_profile_has_no_file() {
        val result = TestCli(buildLaretApp()).run("config", "check", "-p", "no-such-profile")

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("valid=false"))
        assertTrue(result.stdout.contains("No configuration file found for profile: no-such-profile"))
    }

    @Test
    fun test_config_check_with_an_existing_explicit_file_does_not_need_a_profile_file() {
        val config = tempDir.resolve("valid.yml").toFile().apply { writeText("app:\n  name: Valid\n") }

        val result = TestCli(buildLaretApp()).run(
            "config",
            "check",
            "--config",
            config.absolutePath,
            "--profile",
            "no-such-profile",
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("profile=no-such-profile"))
        assertTrue(result.stdout.contains("valid=true"))
    }

    @Test
    fun test_other_commands_still_fall_back_when_the_requested_file_does_not_exist() {
        val missing = tempDir.resolve("typo.yml").toFile()

        val result = TestCli(buildLaretApp()).run(
            "--config",
            missing.absolutePath,
            "config",
            "explain",
            "--key",
            "logging.level",
        )

        assertEquals(0, result.exitCode)
    }

    @Test
    fun test_config_check_names_a_selected_file_that_cannot_be_parsed() {
        val config = tempDir.resolve("broken.yml").toFile().apply { writeText("app: [unclosed") }

        val result = TestCli(buildLaretApp()).run("config", "check", "--config", config.absolutePath)

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("config=${config.absolutePath},"))
        assertTrue(result.stdout.contains("valid=false"))
        assertFalse(result.stderr.contains("Exception"))
    }

    @Test
    fun test_config_check_reports_invalid_config_and_returns_one() {
        val config = tempDir.resolve("invalid.yml").toFile().apply { writeText("output:\n  format: xml\n") }

        val result = TestCli(buildLaretApp()).run("config", "check", "--config", config.absolutePath)

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("output.format"))
        assertTrue(result.stdout.contains("suggestedFix"))
        assertTrue(result.stdout.contains(config.absolutePath))
        assertFalse(result.stderr.contains("output.format"))
    }

    @Test
    fun test_config_check_accepts_short_selectors_with_fix_before_command() {
        val config = tempDir.resolve("invalid.yml").toFile().apply { writeText("output:\n  format: xml\n") }

        val result = TestCli(buildLaretApp()).run(
            "--fix",
            "-p",
            "dev",
            "config",
            "check",
            "-c",
            config.absolutePath,
        )

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("output.format"))
    }

    @Test
    fun test_config_check_accepts_equals_selectors_after_command() {
        val config = tempDir.resolve("invalid.yml").toFile().apply { writeText("output:\n  format: xml\n") }

        val result = TestCli(buildLaretApp()).run(
            "config",
            "check",
            "--profile=dev",
            "--config=${config.absolutePath}",
        )

        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("output.format"))
    }

    @Test
    fun test_config_check_diagnostic_mode_does_not_bypass_next_command() {
        val config = tempDir.resolve("invalid.yml").toFile().apply { writeText("output:\n  format: xml\n") }
        val app = buildLaretApp()
        val cli = TestCli(app)

        assertEquals(1, cli.run("config", "check", "--config", config.absolutePath).exitCode)
        kotlin.test.assertFailsWith<RuntimeException> { cli.run("file", "read", "missing.txt") }
    }
}
