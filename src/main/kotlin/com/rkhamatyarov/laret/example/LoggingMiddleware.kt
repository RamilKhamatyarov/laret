package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.Middleware

class LoggingMiddleware : Middleware {
    override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
        System.err.println("START: ${ctx.command.name}")
        val start = System.currentTimeMillis()
        next()
        val elapsed = System.currentTimeMillis() - start
        System.err.println("END: ${ctx.command.name} took ${elapsed}ms")
    }
}
