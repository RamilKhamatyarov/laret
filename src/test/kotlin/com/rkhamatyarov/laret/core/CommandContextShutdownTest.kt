package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandContextShutdownTest {

    @Test
    fun `ctx onShutdown cleanup runs on run teardown`() {
        val cleaned = AtomicBoolean(false)
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "svc") {
                command(name = "start") {
                    action { ctx -> ctx.onShutdown { cleaned.set(true) } }
                }
            }
        }

        assertEquals(0, app.runForTest(arrayOf("svc", "start")))
        assertTrue(cleaned.get(), "onShutdown cleanup should run when the run tears down")
    }

    @Test
    fun `cleanups from a command run LIFO`() {
        val order = mutableListOf<Int>()
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "svc") {
                command(name = "start") {
                    action { ctx ->
                        ctx.onShutdown { order.add(1) }
                        ctx.onShutdown { order.add(2) }
                    }
                }
            }
        }

        app.runForTest(arrayOf("svc", "start"))

        assertEquals(listOf(2, 1), order)
    }

    @Test
    fun `each run gets a fresh scope so cleanups do not accumulate`() {
        val runs = AtomicInteger(0)
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "svc") {
                command(name = "start") {
                    action { ctx -> ctx.onShutdown { runs.incrementAndGet() } }
                }
            }
        }

        app.runForTest(arrayOf("svc", "start"))
        app.runForTest(arrayOf("svc", "start"))

        // Two runs, one cleanup each — not 1 + 2 from a shared scope.
        assertEquals(2, runs.get())
    }

    @Test
    fun `the command sees the app's active scope`() {
        var sameScope = false
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "svc") {
                command(name = "start") {
                    action { ctx -> sameScope = ctx.scope === ctx.app?.cancellationScope }
                }
            }
        }

        app.runForTest(arrayOf("svc", "start"))

        assertTrue(sameScope, "ctx.scope should be the app's active cancellation scope")
    }
}
