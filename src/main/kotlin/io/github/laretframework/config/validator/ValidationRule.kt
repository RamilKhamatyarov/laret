package io.github.laretframework.config.validator

import io.github.laretframework.config.model.AppConfig

fun interface ValidationRule {
    fun validate(config: AppConfig): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val diagnostics: List<ValidationDiagnostic> = emptyList(),
    val isKnown: Boolean = true,
) {
    fun merge(other: ValidationResult): ValidationResult = ValidationResult(
        isValid = this.isValid && other.isValid,
        errors = this.errors + other.errors,
        warnings = this.warnings + other.warnings,
        diagnostics = this.diagnostics + other.diagnostics,
        isKnown = this.isKnown && other.isKnown,
    )
}

data class ValidationDiagnostic(
    val key: String?,
    val effectiveValue: Any?,
    val constraint: String,
    val suggestedFix: String,
)
