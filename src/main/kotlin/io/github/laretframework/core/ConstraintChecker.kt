package io.github.laretframework.core

import io.github.laretframework.model.Command
import io.github.laretframework.model.Constraint
import io.github.laretframework.model.ValidationError

/**
 * Evaluates and describes a command's [Constraint]s.
 *
 * A name is *set* when its resolved value is non-blank and differs from its
 * declared default; it *has a value* when its resolved value is non-blank.
 * Resolution covers the command line, environment and config files.
 */
internal object ConstraintChecker {

    fun errors(command: Command, ctx: CommandContext): List<ValidationError> {
        val values = Values(command, ctx)
        return command.constraints.mapNotNull { constraint ->
            when (constraint) {
                is Constraint.Requires -> requires(constraint, values)
                is Constraint.OneOf -> oneOf(constraint, values)
                is Constraint.AllOrNone -> allOrNone(constraint, values)
                is Constraint.RequiredWhen -> requiredWhen(constraint, values)
            }
        }
    }

    fun describe(command: Command): List<String> = command.constraints.map { constraint ->
        val label = { name: String -> label(command, name) }
        when (constraint) {
            is Constraint.Requires ->
                Localization.t("help.constraint.requires", label(constraint.name), list(constraint.required, label))

            is Constraint.OneOf ->
                Localization.t("help.constraint.one.of", list(constraint.members, label))

            is Constraint.AllOrNone ->
                Localization.t("help.constraint.all.or.none", list(constraint.members, label))

            is Constraint.RequiredWhen ->
                Localization.t(
                    "help.constraint.required.when",
                    list(constraint.required, label),
                    label(constraint.name),
                    constraint.equals,
                )
        }
    }

    private fun requires(rule: Constraint.Requires, values: Values): ValidationError? {
        if (!values.isSet(rule.name)) return null
        val missing = rule.required.filterNot(values::hasValue).ifEmpty { return null }
        return values.error(
            rule.name,
            "validation.constraint.requires",
            values.label(rule.name),
            values.list(missing),
        )
    }

    private fun oneOf(rule: Constraint.OneOf, values: Values): ValidationError? {
        val set = rule.members.filter(values::isSet)
        return when {
            set.isEmpty() ->
                values.error(rule.members.first(), "validation.constraint.one.of.none", values.list(rule.members))

            set.size > 1 ->
                values.error(
                    set.first(),
                    "validation.constraint.one.of.many",
                    values.list(rule.members),
                    values.list(set),
                )

            else -> null
        }
    }

    private fun allOrNone(rule: Constraint.AllOrNone, values: Values): ValidationError? {
        val set = rule.members.filter(values::isSet)
        if (set.isEmpty() || set.size == rule.members.size) return null
        return values.error(
            set.first(),
            "validation.constraint.all.or.none",
            values.list(rule.members),
            values.list(rule.members - set.toSet()),
        )
    }

    private fun requiredWhen(rule: Constraint.RequiredWhen, values: Values): ValidationError? {
        if (values.valueOf(rule.name) != rule.equals) return null
        val missing = rule.required.filterNot(values::hasValue).ifEmpty { return null }
        return values.error(
            missing.first(),
            "validation.constraint.required.when",
            values.list(missing),
            values.label(rule.name),
            rule.equals,
        )
    }

    private fun label(command: Command, name: String): String =
        if (command.options.any { it.long == name }) "--$name" else "<$name>"

    private fun list(names: List<String>, label: (String) -> String): String = names.joinToString(", ") { label(it) }

    private class Values(private val command: Command, private val ctx: CommandContext) {
        private fun isOption(name: String) = command.options.any { it.long == name }

        fun valueOf(name: String): String = if (isOption(name)) ctx.option(name) else ctx.argument(name)

        private fun defaultOf(name: String): String = command.options.firstOrNull { it.long == name }?.default
            ?: command.arguments.firstOrNull { it.name == name }?.default.orEmpty()

        fun hasValue(name: String): Boolean = valueOf(name).isNotBlank()

        fun isSet(name: String): Boolean = hasValue(name) && valueOf(name) != defaultOf(name)

        fun label(name: String): String = label(command, name)

        fun list(names: List<String>): String = list(names, ::label)

        fun error(field: String, key: String, vararg args: Any): ValidationError =
            ValidationError(field, isOption(field), Localization.t(key, *args), isConstraint = true)
    }
}
