package com.rkhamatyarov.laret.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CancellationScopeTest {

    @Test
    fun `cleanups run in LIFO order`() {
        val scope = CancellationScope(onForceHalt = { })
        val order = mutableListOf<Int>()

        scope.onShutdown { order.add(1) }
        scope.onShutdown { order.add(2) }
        scope.onShutdown { order.add(3) }

        scope.shutdown(0)

        assertEquals(listOf(3, 2, 1), order)
    }

    @Test
    fun `shutdown is idempotent`() {
        val scope = CancellationScope(onForceHalt = { })
        val runs = AtomicInteger(0)
        scope.onShutdown { runs.incrementAndGet() }

        scope.shutdown(0)
        scope.shutdown(0)

        assertEquals(1, runs.get())
    }

    @Test
    fun `disposed cleanup does not run`() {
        val scope = CancellationScope(onForceHalt = { })
        val ran = AtomicBoolean(false)
        val handle = scope.onShutdown { ran.set(true) }

        handle.dispose()
        scope.shutdown(0)

        assertFalse(ran.get())
    }

    @Test
    fun `a cleanup failure does not abort the remaining cleanups`() {
        val scope = CancellationScope(onForceHalt = { })
        val ran = mutableListOf<String>()

        scope.onShutdown { ran.add("first") }
        scope.onShutdown { throw RuntimeException("boom") }
        scope.onShutdown { ran.add("third") }

        scope.shutdown(0)

        assertEquals(listOf("third", "first"), ran)
    }

    @Test
    fun `shutdown cancels work running under the scope job`() = runBlocking {
        val scope = CancellationScope(onForceHalt = { })
        val cleanupRan = AtomicBoolean(false)

        val job = launch(scope.coroutineContext + Dispatchers.Default) {
            try {
                delay(10.seconds)
            } finally {
                cleanupRan.set(true)
            }
        }

        while (!job.isActive) { /* spin */ }
        delay(50.milliseconds)
        scope.shutdown(0)
        job.join()

        assertTrue(job.isCancelled, "job should be cancelled by shutdown")
        assertTrue(cleanupRan.get(), "the coroutine's finally should have run")
    }

    @Test
    fun `registration after shutdown is a no-op`() {
        val scope = CancellationScope(onForceHalt = { })
        scope.shutdown(0)

        val ran = AtomicBoolean(false)
        scope.onShutdown { ran.set(true) }

        assertTrue(scope.isShuttingDown)
        assertFalse(ran.get(), "late registration must not run")
    }

    @Test
    @Timeout(5)
    fun `a cleanup exceeding the grace period forces a halt`() {
        val haltCode = AtomicInteger(-1)
        val scope = CancellationScope(
            gracePeriodMillis = 100,
            onForceHalt = { haltCode.set(it) },
        )
        scope.onShutdown { Thread.sleep(2_000) }

        scope.shutdown(143)

        assertEquals(143, haltCode.get(), "force-halt should be invoked with the exit code")
    }

    @Test
    fun `empty scope shutdown completes without forcing a halt`() {
        val halted = AtomicBoolean(false)
        val scope = CancellationScope(onForceHalt = { halted.set(true) })

        scope.shutdown(0)

        assertFalse(halted.get())
    }
}
