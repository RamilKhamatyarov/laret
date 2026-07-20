package com.rkhamatyarov.laret.core

/**
 * Middleware for cross-cutting concerns (logging, auth, metrics, etc.)
 *
 * @property priority Lower value wraps more of the chain. A middleware with
 *   priority `-1000` (see `StatsMiddleware`) runs *outside* one with priority
 *   `0`: it enters first and exits last, so it observes the whole execution
 *   including every inner middleware. Priority sorts flatly across all scopes,
 *   so a command-scoped middleware with a low enough value can wrap a global
 *   one. Equal priorities keep registration order.
 * @property scope Ignored at runtime. Effective scope is derived from where the
 *   middleware is registered — see [MiddlewareScope].
 * @property name Label shown by `middleware list`.
 */
interface Middleware {
    val priority: Int get() = 0

    @Deprecated("Scope is derived from the registration site and this value is ignored at runtime.")
    val scope: MiddlewareScope get() = MiddlewareScope.GLOBAL

    /**
     * Override to give this middleware a stable label. The default reads the
     * simple class name, which is `null` for anonymous `object : Middleware`
     * declarations and is not guaranteed under GraalVM native image.
     */
    val name: String get() = this::class.simpleName ?: "<anonymous>"

    /**
     * Process the command context and optionally proceed to the next middleware/action.
     * Call `next()` to continue the chain.
     */
    suspend fun handle(ctx: CommandContext, next: suspend () -> Unit)
}

/**
 * Where a middleware was registered, which determines what it applies to.
 *
 * Scope is positional: it follows from the builder the middleware was handed
 * to, not from anything the middleware class declares.
 */
enum class MiddlewareScope {
    /** Registered on the `cli { }` builder — applied to every command execution. */
    GLOBAL,

    /** Registered inside a `group { }` block — applied to commands in that group. */
    GROUP,

    /** Registered inside a `command { }` block — applied to that command only. */
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
