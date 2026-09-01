package com.rkhamatyarov.laret.watch

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import java.nio.file.Path

/** Why a live watch session ended. */
enum class WatchStopReason { MAX_RESTARTS, MAX_CONSECUTIVE_FAILURES, SOURCE_CLOSED, INTERRUPTED }

/** Outcome of a finished watch session. */
data class WatchRunSummary(val restarts: Int, val lastExitCode: Int, val stopReason: WatchStopReason)

/** Lifecycle notifications a session emits for user-facing logging. */
sealed interface WatchLogEvent {
    data class Running(val attempt: Int, val trigger: Path?) : WatchLogEvent
    data class Succeeded(val attempt: Int) : WatchLogEvent
    data class Failed(val attempt: Int, val exitCode: Int) : WatchLogEvent
    data class Stopped(val reason: WatchStopReason, val restarts: Int) : WatchLogEvent
}

/**
 * Orchestrates a live watch: filters changes by [matcher], debounces them, and
 * re-runs [runner], superseding an in-flight run when a new change arrives.
 *
 * Pure of any filesystem or app coupling (the change source is a [Flow] and the
 * runner is injected), so it is unit-testable with virtual time. See the
 * `live-watch-mode` ADR.
 *
 * @param runner runs the target and returns its exit code; cancelled when a
 *   newer trigger supersedes it.
 * @param maxRestarts stop after this many total runs (0 = unlimited).
 * @param maxConsecutiveFailures stop after this many back-to-back failures (0 = off).
 * @param runOnStart run once immediately before any change.
 */
class LiveWatchSession(
    private val matcher: GlobMatcher,
    private val runner: suspend (attempt: Int) -> Int,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val maxRestarts: Int = 0,
    private val maxConsecutiveFailures: Int = 0,
    private val runOnStart: Boolean = true,
    private val onEvent: (WatchLogEvent) -> Unit = {},
) {
    /** Control-flow signal to break out of the collect loop when a cap is hit. */
    private class StopSignal : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    /** Runs the session until a cap is hit, the source closes, or it is cancelled. */
    @OptIn(FlowPreview::class)
    suspend fun run(changes: Flow<Path>): WatchRunSummary {
        var restarts = 0
        var consecutive = 0
        var lastExit = 0
        var stop: WatchStopReason? = null

        val changeTriggers = changes
            .filter { matcher.matches(it) }
            .debounce(debounceMillis)
        val startTrigger: Flow<Path?> = if (runOnStart) flowOf(null) else emptyFlow()
        val triggers = merge(startTrigger, changeTriggers)

        try {
            coroutineScope {
                triggers.collectLatest { trigger ->
                    restarts++
                    onEvent(WatchLogEvent.Running(restarts, trigger))
                    val exit = runner(restarts)
                    lastExit = exit
                    if (exit == 0) {
                        consecutive = 0
                        onEvent(WatchLogEvent.Succeeded(restarts))
                    } else {
                        consecutive++
                        onEvent(WatchLogEvent.Failed(restarts, exit))
                    }
                    stop = when {
                        maxRestarts in 1..restarts -> WatchStopReason.MAX_RESTARTS
                        maxConsecutiveFailures in 1..consecutive -> WatchStopReason.MAX_CONSECUTIVE_FAILURES
                        else -> null
                    }
                    if (stop != null) throw StopSignal()
                }
            }
            // The change source completed on its own.
            stop = stop ?: WatchStopReason.SOURCE_CLOSED
        } catch (_: StopSignal) {
            // A cap was reached; `stop` already holds the reason.
        }

        val reason = stop ?: WatchStopReason.INTERRUPTED
        onEvent(WatchLogEvent.Stopped(reason, restarts))
        return WatchRunSummary(restarts, lastExit, reason)
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 150L
    }
}
