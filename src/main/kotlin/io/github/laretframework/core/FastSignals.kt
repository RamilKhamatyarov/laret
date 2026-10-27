package io.github.laretframework.core

import sun.misc.Signal
import sun.misc.SignalHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles `SIGTERM` and `SIGINT` without the JVM exit sequence: the first
 * signal runs [scope]'s cleanups and halts with `128 + signal`, a second one
 * halts at once. See the `signal-latency-rss-and-dispatch-floor` ADR.
 */
internal class FastSignals(
    private val scope: CancellationScope,
    private val halt: (Int) -> Unit = { Runtime.getRuntime().halt(it) },
) {
    private val signalled = AtomicBoolean(false)
    private val finished = CountDownLatch(1)
    private val previous = mutableMapOf<Signal, SignalHandler>()

    fun install(): FastSignals = apply {
        for (name in SIGNALS) {
            runCatching {
                val signal = Signal(name)
                previous[signal] = Signal.handle(signal) { onSignal(it.number) }
            }
        }
    }

    fun uninstall() {
        previous.forEach { (signal, handler) -> runCatching { Signal.handle(signal, handler) } }
        previous.clear()
    }

    /**
     * Runs on the thread the JVM starts for each delivered signal, so a second
     * signal arrives on its own thread while the first one's cleanups run.
     */
    fun onSignal(number: Int) {
        val code = SIGNAL_EXIT_BASE + number
        if (!signalled.compareAndSet(false, true)) {
            stop(code)
            return
        }
        try {
            scope.shutdown(code)
            stop(code)
        } finally {
            finished.countDown()
        }
    }

    /** Blocks the returning run until a signal's shutdown has halted the process. */
    fun awaitIfSignalled() {
        if (signalled.get()) finished.await()
    }

    private fun stop(code: Int) {
        System.out.flush()
        System.err.flush()
        halt(code)
    }

    private companion object {
        val SIGNALS = listOf("TERM", "INT")
        const val SIGNAL_EXIT_BASE = 128
    }
}
