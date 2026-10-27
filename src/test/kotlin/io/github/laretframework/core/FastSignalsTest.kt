package io.github.laretframework.core

import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import sun.misc.Signal
import sun.misc.SignalHandler
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FastSignalsTest {

    private val halts = LinkedBlockingQueue<Int>()
    private val order = Collections.synchronizedList(mutableListOf<String>())

    private fun nextHalt(): Int? = halts.poll(5, TimeUnit.SECONDS)

    @Test
    fun firstSignalRunsCleanupsInReverseThenHaltsWithSignalCode() {
        val scope = CancellationScope()
        scope.onShutdown { order += "first" }
        scope.onShutdown { order += "second" }
        val signals = FastSignals(scope) { halts += it }

        signals.onSignal(15)

        assertThat(nextHalt()).isEqualTo(143)
        assertThat(order).containsExactly("second", "first")
        assertThat(scope.job.isCancelled).isTrue()
    }

    @Test
    fun secondSignalHaltsWithoutWaitingForCleanups() {
        val scope = CancellationScope()
        val cleanupStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        scope.onShutdown { order += "skipped" }
        scope.onShutdown {
            cleanupStarted.countDown()
            release.await()
        }
        val signals = FastSignals(scope) { halts += it }

        thread { signals.onSignal(2) }
        assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue()
        signals.onSignal(2)

        assertThat(nextHalt()).isEqualTo(130)
        assertThat(order).isEmpty()
        release.countDown()
        assertThat(nextHalt()).isEqualTo(130)
    }

    @Test
    fun awaitIfSignalledReturnsAtOnceWithoutASignal() {
        FastSignals(CancellationScope()) { halts += it }.awaitIfSignalled()

        assertThat(halts).isEmpty()
    }

    @Test
    fun awaitIfSignalledWaitsForTheSignalShutdown() {
        val scope = CancellationScope()
        scope.onShutdown { Thread.sleep(50) }
        val signals = FastSignals(scope) { halts += it }

        thread { signals.onSignal(15) }
        while (halts.isEmpty() && scope.isShuttingDown.not()) Thread.onSpinWait()
        signals.awaitIfSignalled()

        assertThat(halts).containsExactly(143)
    }

    @Test
    fun uninstallRestoresThePreviousHandler() {
        val signal = Signal("INT")
        val before = current(signal)
        val signals = FastSignals(CancellationScope()) { halts += it }

        signals.install()
        assumeTrue(current(signal).toString() != before.toString(), "this JVM does not hand SIGINT to applications")
        signals.uninstall()

        assertThat(current(signal)).hasToString(before.toString())
    }

    @Test
    fun theBuilderSwitchesItOn() {
        assertThat(cli(name = "s", version = "0") {}.fastSignals).isFalse()
        assertThat(cli(name = "s", version = "0") { fastSignals() }.fastSignals).isTrue()
    }

    private fun current(signal: Signal): SignalHandler {
        val handler = Signal.handle(signal, SignalHandler.SIG_IGN)
        Signal.handle(signal, handler)
        return handler
    }
}
