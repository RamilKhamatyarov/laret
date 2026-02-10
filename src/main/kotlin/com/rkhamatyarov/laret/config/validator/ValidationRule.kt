package com.rkhamatyarov.laret.config.validator

import com.rkhamatyarov.laret.config.model.AppConfig

fun interface ValidationRule {
    fun validate(config: AppConfig): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    fun merge(other: ValidationResult): ValidationResult =
        ValidationResult(
            isValid = this.isValid && other.isValid,
            errors = this.errors + other.errors,
            warnings = this.warnings + other.warnings,
        )
}
