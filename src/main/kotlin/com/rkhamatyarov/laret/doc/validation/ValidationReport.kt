package com.rkhamatyarov.laret.doc.validation

/**
 * Aggregated outcome of a documentation validation pass.
 *
 * [errors] gate `--strict` (they raise
 * [com.rkhamatyarov.laret.doc.DocValidationException]); [warnings] are advisory
 * and only printed. Both are plain strings so they compose with the existing
 * provider-based problem list without changing public contracts.
 */
data class ValidationReport(val errors: List<String> = emptyList(), val warnings: List<String> = emptyList()) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    operator fun plus(other: ValidationReport): ValidationReport =
        ValidationReport(errors + other.errors, warnings + other.warnings)

    companion object {
        val EMPTY = ValidationReport()
    }
}
