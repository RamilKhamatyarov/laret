package io.github.laretframework.config.validator

import io.github.laretframework.config.model.AppConfig
import io.github.laretframework.config.model.AppMetadata
import io.github.laretframework.config.model.LoggingConfig
import io.github.laretframework.config.model.OutputConfig
import io.github.laretframework.plugin.model.PluginConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConfigValidatorDiagnosticsTest {
    @Test
    fun test_invalid_output_format_has_structured_key_value_constraint_and_fix() {
        val result = ConfigValidator().validate(AppConfig(output = OutputConfig(format = "xml")))

        val diagnostic = result.diagnostics.single()
        assertEquals("output.format", diagnostic.key)
        assertEquals("xml", diagnostic.effectiveValue)
        assertEquals("config.constraint.output.format", diagnostic.constraint)
        assertEquals("config.fix.output.format", diagnostic.suggestedFix)
    }

    @Test
    fun test_all_builtin_failures_include_structured_details() {
        val invalid = AppConfig(
            app = AppMetadata(name = ""),
            output = OutputConfig(format = "xml"),
            logging = LoggingConfig(level = "VERBOSE"),
            plugins = PluginConfig(paths = listOf(" ")),
        )

        val diagnostics = ConfigValidator().validate(invalid).diagnostics

        assertEquals(
            listOf("app.name", "output.format", "logging.level", "plugins.paths"),
            diagnostics.map { it.key },
        )
        assertEquals(
            4,
            diagnostics.count {
                it.constraint.startsWith("config.constraint.") &&
                    it.suggestedFix.startsWith("config.fix.")
            },
        )
    }

    @Test
    fun test_custom_rule_warning_is_preserved_without_diagnostics() {
        val result = ConfigValidator().addRule {
            ValidationResult(isValid = true, warnings = listOf("review this setting"))
        }.validate(AppConfig())

        assertEquals(listOf("review this setting"), result.warnings)
        assertEquals(emptyList(), result.diagnostics)
    }

    @Test
    fun test_custom_rule_failure_has_explicit_unavailable_detail() {
        val result = ConfigValidator().addRule {
            error("custom check failed")
        }.validate(AppConfig())

        val diagnostic = result.diagnostics.single()
        assertEquals(null, diagnostic.key)
        assertEquals("config.constraint.unavailable", diagnostic.constraint)
        assertEquals("config.fix.unavailable", diagnostic.suggestedFix)
    }
}
