package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.Middleware

class LoggingMiddleware : Middleware {
    override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
        println("[LOG] Executing: ${ctx.command.name}")
        val start = System.currentTimeMillis()
        next()
        val elapsed = System.currentTimeMillis() - start
        println("[LOG] Finished: ${ctx.command.name} in ${elapsed}ms")
    }
}
