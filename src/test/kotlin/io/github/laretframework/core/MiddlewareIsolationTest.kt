package io.github.laretframework.core

import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MiddlewareIsolationTest {

    @AfterEach
    fun tearDown() {
        @Suppress("DEPRECATION")
        CommandRunner.globalMiddlewares = emptyList()
    }

    private class Counting : Middleware {
        val calls = AtomicInteger(0)

        override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
            calls.incrementAndGet()
            next()
        }
    }

    @Test
    fun anAppBuiltLaterDoesNotLeakItsMiddlewareIntoAnEarlierOne() {
        val plain = cli(name = "plain", version = "0", description = "no middleware") {
            group(name = "g") { command(name = "c") { action { } } }
        }
        val counting = Counting()
        cli(name = "instrumented", version = "0", description = "has middleware") {
            use(counting)
            group(name = "g") { command(name = "c") { action { } } }
        }

        plain.runForTest(arrayOf("g", "c"))

        assertThat(counting.calls.get()).isZero()
    }

    @Test
    fun anAppStillRunsItsOwnMiddleware() {
        val counting = Counting()
        val app = cli(name = "instrumented", version = "0", description = "has middleware") {
            use(counting)
            group(name = "g") { command(name = "c") { action { } } }
        }
        cli(name = "later", version = "0", description = "built afterwards") {}

        app.runForTest(arrayOf("g", "c"))

        assertThat(counting.calls.get()).isEqualTo(1)
    }
}
