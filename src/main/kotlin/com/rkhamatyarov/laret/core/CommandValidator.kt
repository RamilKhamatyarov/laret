package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.ValidationError
import com.rkhamatyarov.laret.model.Validator

/**
 * Runs a command's declared validators against the parsed context.
 *
 * Collects every failure rather than stopping at the first, so a single run
 * surfaces all problems. See the `argument-option-validation-dsl` ADR.
 */
object CommandValidator {

    /** All validation failures for [command] given the parsed [ctx]; empty when valid. */
    fun validate(command: Command, ctx: CommandContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        command.arguments.forEach { argument ->
            val value = ctx.argument(argument.name)
            errors += run(argument.validators, value).map { ValidationError(argument.name, false, it) }
        }

        command.options.forEach { option ->
            val value = ctx.option(option.long)
            errors += run(option.validators, value).map { ValidationError(option.long, true, it) }
        }

        errors += exclusiveErrors(command, ctx)
        return errors
    }

    /** Messages from [validators] for [value], skipping blanks unless a rule opts in. */
    private fun run(validators: List<Validator>, value: String): List<String> {
        if (validators.isEmpty()) return emptyList()
        val blank = value.isBlank()
        return validators
            .filter { !blank || it.runsOnBlank }
            .mapNotNull { it.validate(value) }
    }

    /** Fires when two or more options of an exclusive group were explicitly provided. */
    private fun exclusiveErrors(command: Command, ctx: CommandContext): List<ValidationError> =
        command.exclusiveGroups.mapNotNull { group ->
            val supplied = group.filter { it in ctx.providedOptions }.sorted()
            if (supplied.size < 2) {
                null
            } else {
                ValidationError(
                    supplied.first(),
                    true,
                    Localization.t("validation.mutually.exclusive", supplied.joinToString(", ") { "--$it" }),
                )
            }
        }
}
