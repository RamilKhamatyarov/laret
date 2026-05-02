package com.rkhamatyarov.laret.stats

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.Middleware
import com.rkhamatyarov.laret.core.MiddlewareScope
import kotlinx.coroutines.CancellationException

/**
 * Records every command execution into [StatsCollector].
 *
 * Runs at the lowest priority so it wraps all other middleware — its measured
 * duration includes the work other middlewares do, which is what users want
 * when reasoning about end-to-end command time.
 */
class StatsMiddleware(
    private val clock: () -> Long = System::currentTimeMillis,
) : Middleware {
    override val scope: MiddlewareScope = MiddlewareScope.GLOBAL
    override val priority: Int = -1000

    override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
        val start = clock()
        var exitCode = 0
        try {
            next()
        } catch (e: Throwable) {
            if (e !is CancellationException) exitCode = 1
            throw e
        } finally {
            val durationMs = clock() - start
            StatsCollector.record(ctx.groupName, ctx.command.name, durationMs, exitCode)
        }
    }
}
