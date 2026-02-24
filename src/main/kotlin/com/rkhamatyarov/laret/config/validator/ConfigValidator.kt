package com.rkhamatyarov.laret.config.validator

import com.rkhamatyarov.laret.config.model.AppConfig

class ConfigValidator {
    private val rules = mutableListOf<ValidationRule>()

    init {
        addRule(AppNameRule())
        addRule(OutputFormatRule())
        addRule(LoggingLevelRule())
        addRule(PluginPathsRule())
    }

    fun addRule(rule: ValidationRule): ConfigValidator {
        rules.add(rule)
        return this
    }

    fun validate(config: AppConfig): ValidationResult {
        var result = ValidationResult(isValid = true)

        for (rule in rules) {
            try {
                result = result.merge(rule.validate(config))
            } catch (e: Exception) {
                System.err.println("ERROR: $e")
                result =
                    result.copy(
                        isValid = false,
                        errors = result.errors + "Validation rule failed: ${e.message}",
                    )
            }
        }

        return result
    }
}

private class AppNameRule : ValidationRule {
    override fun validate(config: AppConfig): ValidationResult {
        val isValid = config.app.name.isNotBlank() && config.app.name.length <= 100
        return if (isValid) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("App name must be non-empty and max 100 characters"),
            )
        }
    }
}

private class OutputFormatRule : ValidationRule {
    override fun validate(config: AppConfig): ValidationResult {
        val validFormats = listOf("json", "yaml", "toml", "plain")
        val isValid = config.output.format in validFormats
        return if (isValid) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("Invalid output format: ${config.output.format}. Valid: $validFormats"),
            )
        }
    }
}

private class LoggingLevelRule : ValidationRule {
    override fun validate(config: AppConfig): ValidationResult {
        val validLevels = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
        val isValid = config.logging.level.uppercase() in validLevels
        return if (isValid) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("Invalid log level: ${config.logging.level}. Valid: $validLevels"),
            )
        }
    }
}

private class PluginPathsRule : ValidationRule {
    override fun validate(config: AppConfig): ValidationResult {
        val invalidPaths =
            config.plugins.paths
                .filter { path -> path.isBlank() }

        return if (invalidPaths.isEmpty()) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("Plugin paths cannot be empty"),
            )
        }
    }
}
