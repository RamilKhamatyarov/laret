package com.rkhamatyarov.laret.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

/**
 * Per-run registry of shutdown cleanups, delivered on `SIGINT`/`SIGTERM` via a
 * JVM shutdown hook and also run on normal completion.
 *
 * See the `graceful-shutdown-cancellation-scope` ADR. A fresh scope is created
 * per [CliApp] run so that cancellation of its [job] never leaks between runs.
 *
 * @param gracePeriodMillis total budget for running all cleanups before the
 *   process is force-halted.
 * @param onForceHalt invoked with the exit code when cleanups exceed the grace
 *   budget; defaults to `Runtime.halt`, overridable so tests never kill the JVM.
 */
class CancellationScope(
    private val gracePeriodMillis: Long = DEFAULT_GRACE_PERIOD_MILLIS,
    private val onForceHalt: (Int) -> Unit = { Runtime.getRuntime().halt(it) },
) {
    /** Parent job for the dispatched command; cancelled on shutdown. */
    val job = SupervisorJob()

    /** Context to run cancellable work under, so shutdown unwinds it. */
    val coroutineContext: CoroutineContext get() = job

    private class Registration(val block: suspend () -> Unit)

    private val lock = Any()
    private val cleanups = mutableListOf<Registration>()
    private val shuttingDown = AtomicBoolean(false)

    /** Handle to a registered cleanup, allowing it to be removed before shutdown. */
    class Handle internal constructor(private val disposer: () -> Unit) {
        fun dispose() = disposer()
    }

    /**
     * Register a cleanup to run on shutdown. Cleanups run in LIFO order, so the
     * last registered runs first. Registering after shutdown has begun is a
     * no-op and the returned handle is inert.
     */
    fun onShutdown(block: suspend () -> Unit): Handle {
        val registration = Registration(block)
        synchronized(lock) {
            if (!shuttingDown.get()) cleanups.add(registration)
        }
        return Handle {
            synchronized(lock) { cleanups.remove(registration) }
        }
    }

    val isShuttingDown: Boolean get() = shuttingDown.get()

    /**
     * Cancel running work and run cleanups LIFO within the grace budget.
     * Idempotent: only the first call performs teardown; later calls return
     * immediately. Blocking, so it is safe to call from the shutdown-hook thread.
     *
     * @param exitCode passed to [onForceHalt] if the grace budget is exceeded.
     */
    fun shutdown(exitCode: Int) {
        if (!shuttingDown.compareAndSet(false, true)) return

        job.cancel(CancellationException("CancellationScope shutting down"))

        val pending = synchronized(lock) {
            val snapshot = cleanups.reversed()
            cleanups.clear()
            snapshot
        }
        if (pending.isEmpty()) return

        // A dedicated daemon worker bounds even a cleanup that blocks without
        // suspending: if it overruns we abandon it and force-halt.
        val worker = thread(start = true, isDaemon = true, name = "laret-shutdown") {
            runBlocking {
                for (registration in pending) {
                    try {
                        registration.block()
                    } catch (_: CancellationException) {
                        // A cleanup observing the cancelled job is fine; keep going.
                    } catch (t: Throwable) {
                        System.err.println("Shutdown cleanup failed: ${t.message}")
                    }
                }
            }
        }
        worker.join(gracePeriodMillis)
        if (worker.isAlive) {
            System.err.println("Graceful shutdown exceeded ${gracePeriodMillis}ms; forcing exit")
            onForceHalt(exitCode)
        }
    }

    companion object {
        const val DEFAULT_GRACE_PERIOD_MILLIS = 5_000L

        /** Conventional exit code for an interrupted (SIGINT) run. */
        const val INTERRUPT_EXIT_CODE = 130
    }
}
