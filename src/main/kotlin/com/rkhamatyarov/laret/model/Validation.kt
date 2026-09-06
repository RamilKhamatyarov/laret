package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.core.Localization
import java.io.File

/**
 * A single failed validation.
 *
 * @param field    argument name or option long name.
 * @param isOption true when [field] names an option rather than an argument.
 * @param message  human-readable reason, already localized.
 */
data class ValidationError(val field: String, val isOption: Boolean, val message: String)

/**
 * Validates one resolved value.
 *
 * See the `argument-option-validation-dsl` ADR.
 */
interface Validator {
    /** Returns `null` when [value] is acceptable, otherwise the failure message. */
    fun validate(value: String): String?

    /**
     * Whether this validator also runs for blank values. Blanks are skipped by
     * default so a rule on an optional flag does not fire when it is unset.
     */
    val runsOnBlank: Boolean get() = false
}

/** Builds the validator list for one argument or option. */
class ValidationBuilder {
    internal val validators = mutableListOf<Validator>()

    /** Value must match [pattern]. */
    fun regex(pattern: String, message: String? = null) {
        val compiled = Regex(pattern)
        add(message ?: Localization.t("validation.regex", pattern)) { compiled.matches(it) }
    }

    /** Value must be a number greater than or equal to [value]. */
    fun min(value: Long, message: String? = null) {
        addNumeric(message ?: Localization.t("validation.min", value.toString())) { it >= value }
    }

    /** Value must be a number less than or equal to [value]. */
    fun max(value: Long, message: String? = null) {
        addNumeric(message ?: Localization.t("validation.max", value.toString())) { it <= value }
    }

    /** Value must be a number within [min]..[max] inclusive. */
    fun range(min: Long, max: Long, message: String? = null) {
        val text = message ?: Localization.t("validation.range", min.toString(), max.toString())
        addNumeric(text) { it in min..max }
    }

    /** Value must be at least [length] characters long. */
    fun minLength(length: Int, message: String? = null) {
        add(message ?: Localization.t("validation.min.length", length.toString())) { it.length >= length }
    }

    /** Value must be at most [length] characters long. */
    fun maxLength(length: Int, message: String? = null) {
        add(message ?: Localization.t("validation.max.length", length.toString())) { it.length <= length }
    }

    /** Value must be one of [choices]. */
    fun oneOf(vararg choices: String, message: String? = null) {
        val allowed = choices.toList()
        add(message ?: Localization.t("validation.one.of", allowed.joinToString(", "))) { it in allowed }
    }

    /** Value must not be blank; unlike other rules this one also runs on blanks. */
    fun notBlank(message: String? = null) {
        val text = message ?: Localization.t("validation.not.blank")
        validators.add(
            object : Validator {
                override val runsOnBlank = true

                override fun validate(value: String): String? = if (value.isNotBlank()) null else text
            },
        )
    }

    /** Value must name an existing file. */
    fun fileExists(message: String? = null) {
        validators.add(
            object : Validator {
                override fun validate(value: String): String? =
                    if (File(value).isFile) null else message ?: Localization.t("validation.file.exists", value)
            },
        )
    }

    /** Value must name an existing directory. */
    fun dirExists(message: String? = null) {
        validators.add(
            object : Validator {
                override fun validate(value: String): String? =
                    if (File(value).isDirectory) null else message ?: Localization.t("validation.dir.exists", value)
            },
        )
    }

    /** Value must satisfy [predicate]; [message] is used literally when it does not. */
    fun custom(message: String, predicate: (String) -> Boolean) {
        add(message) { predicate(it) }
    }

    private fun add(message: String, predicate: (String) -> Boolean) {
        validators.add(
            object : Validator {
                override fun validate(value: String): String? = if (predicate(value)) null else message
            },
        )
    }

    /** Adds a rule that first requires the value to parse as a number. */
    private fun addNumeric(message: String, predicate: (Long) -> Boolean) {
        validators.add(
            object : Validator {
                override fun validate(value: String): String? {
                    val number = value.trim().toLongOrNull() ?: return Localization.t("validation.number")
                    return if (predicate(number)) null else message
                }
            },
        )
    }
}

/** Compiles a `validate { }` block into its validator list. */
internal fun buildValidators(block: (ValidationBuilder.() -> Unit)?): List<Validator> {
    if (block == null) return emptyList()
    return ValidationBuilder().apply(block).validators.toList()
}
