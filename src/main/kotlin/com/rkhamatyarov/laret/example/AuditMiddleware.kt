package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.Middleware

/**
 * Demonstrates GROUP scope: registered inside `group("file") { }`, so it runs
 * for every command in that group and for nothing else.
 */
class AuditMiddleware : Middleware {
    override val priority = 0

    override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
        System.err.println("AUDIT: ${ctx.groupName} ${ctx.command.name}")
        next()
    }
}

/**
 * Demonstrates COMMAND scope: registered inside a single `command { }` block,
 * so it runs only for that one command. The priority places it inside
 * [AuditMiddleware].
 */
class DeleteGuardMiddleware : Middleware {
    override val priority = 10

    override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
        System.err.println("GUARD: destructive command ${ctx.command.name}")
        next()
    }
}
