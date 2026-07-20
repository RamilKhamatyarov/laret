package com.rkhamatyarov.laret.core

/**
 * A [Middleware] together with the scope it was registered at.
 *
 * @property target Human-readable registration target: `*` for global,
 *   the group name for group scope, `<group> <command>` for command scope.
 */
data class MiddlewareRegistration(
    val middleware: Middleware,
    val scope: MiddlewareScope,
    val target: String,
) {
    val name: String get() = middleware.name
    val priority: Int get() = middleware.priority
}

/**
 * Immutable per-application middleware registry.
 *
 * Built by `CliBuilder` and carried on [CliApp], so two applications in the
 * same JVM cannot clobber each other's registrations and tests need no reset.
 */
class MiddlewareRegistry(
    private val global: List<MiddlewareRegistration> = emptyList(),
    private val group: Map<String, List<MiddlewareRegistration>> = emptyMap(),
    private val command: Map<String, List<MiddlewareRegistration>> = emptyMap(),
) {
    /** Every registration, in declaration order, regardless of scope. */
    fun all(): List<MiddlewareRegistration> = global + group.values.flatten() + command.values.flatten()

    /**
     * Registrations applying to [groupName] `/` [commandName], ordered
     * outermost first: sorted by ascending priority with ties keeping
     * registration order.
     */
    fun resolve(groupName: String, commandName: String): List<MiddlewareRegistration> {
        val applicable = global +
            (group[groupName] ?: emptyList()) +
            (command[commandKey(groupName, commandName)] ?: emptyList())
        return applicable.sortedBy { it.priority }
    }

    fun isEmpty(): Boolean = global.isEmpty() && group.isEmpty() && command.isEmpty()

    companion object {
        val EMPTY = MiddlewareRegistry()

        /**
         * Command registrations are keyed by the qualified pair: bare command
         * names such as `list` and `show` repeat across groups, so a bare key
         * would leak middleware between them.
         */
        fun commandKey(groupName: String, commandName: String): String = "$groupName:$commandName"

        fun of(
            global: List<MiddlewareRegistration>,
            group: List<MiddlewareRegistration>,
            command: List<MiddlewareRegistration>,
        ): MiddlewareRegistry = MiddlewareRegistry(
            global = global,
            group = group.groupBy { it.target },
            command = command.groupBy { it.target.replace(' ', ':') },
        )
    }
}
