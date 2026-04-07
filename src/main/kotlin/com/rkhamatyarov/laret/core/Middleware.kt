package com.rkhamatyarov.laret.core

/**
 * Middleware for cross-cutting concerns (logging, auth, metrics, etc.)
 *
 * @property priority Lower values execute first (0 = highest priority)
 * @property scope When this middleware should be applied
 */
interface Middleware {
    val priority: Int get() = 0
    val scope: MiddlewareScope get() = MiddlewareScope.GLOBAL

    /**
     * Process the command context and optionally proceed to the next middleware/action.
     * Call `next()` to continue the chain.
     */
    suspend fun handle(ctx: CommandContext, next: suspend () -> Unit)
}

enum class MiddlewareScope {
    /** Applied to every command execution */
    GLOBAL,

    /** Applied only to commands in a specific group (group name matched) */
    GROUP,

    /** Applied only to a specific command (group + command name matched) */
    COMMAND,
}

/**
 * Chains middlewares and executes them in priority order.
 * The final action is the actual command implementation.
 */
internal class MiddlewareChain(
    private val middlewares: List<Middleware>,
    private val finalAction: suspend (CommandContext) -> Unit,
) {
    suspend fun execute(ctx: CommandContext) {
        var index = 0
        suspend fun next() {
            if (index < middlewares.size) {
                val middleware = middlewares[index++]
                middleware.handle(ctx, ::next)
            } else {
                finalAction(ctx)
            }
        }
        next()
    }
}
