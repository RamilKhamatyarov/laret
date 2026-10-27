package io.github.laretframework.core

import io.github.laretframework.dsl.CliBuilder
import io.github.laretframework.dsl.cli
import io.github.laretframework.plugin.model.LaretPlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class RunCleanupRegistrationTest {

    private fun pendingDuringRun(configure: CliBuilder.() -> Unit = {}, inAction: (CliApp) -> Unit = {}): Int {
        var pending = -1
        lateinit var app: CliApp
        app = cli(name = "c", version = "0") {
            configure()
            group(name = "g") {
                command(name = "c") {
                    action {
                        inAction(app)
                        pending = app.cancellationScope.pendingCleanups
                    }
                }
            }
        }
        assertThat(app.run(arrayOf("g", "c"))).isZero()
        return pending
    }

    @Test
    fun anAppWithoutHooksOrPluginsRegistersNoCleanups() {
        assertThat(pendingDuringRun()).isZero()
    }

    @Test
    fun anAppShutdownHookIsRegisteredAndRunsOnce() {
        val calls = AtomicInteger()

        assertThat(pendingDuringRun({ onAppShutdown = { calls.incrementAndGet() } })).isEqualTo(1)
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun aPluginRegisteredDuringTheRunIsShutDownAtTheEnd() {
        val shutdowns = AtomicInteger()
        val plugin = object : LaretPlugin {
            override val name = "late"
            override fun shutdown() {
                shutdowns.incrementAndGet()
            }
        }

        assertThat(pendingDuringRun(inAction = { it.registerPlugin(plugin) })).isEqualTo(1)
        assertThat(shutdowns.get()).isEqualTo(1)
    }
}
