package io.github.laretframework.model

/**
 * A rule relating several arguments or options of one command, named by
 * argument name or option long name. See the
 * `argument-dependencies-and-grouping` ADR.
 */
sealed interface Constraint {
    val names: List<String>

    /** When [name] is set, every name in [required] must have a value. */
    data class Requires(val name: String, val required: List<String>) : Constraint {
        override val names get() = listOf(name) + required
    }

    /** Exactly one of [members] is set. */
    data class OneOf(val members: List<String>) : Constraint {
        override val names get() = members
    }

    /** Either every one of [members] is set, or none is. */
    data class AllOrNone(val members: List<String>) : Constraint {
        override val names get() = members
    }

    /** When [name] resolves to [equals], every name in [required] must have a value. */
    data class RequiredWhen(val name: String, val equals: String, val required: List<String>) : Constraint {
        override val names get() = listOf(name) + required
    }
}
