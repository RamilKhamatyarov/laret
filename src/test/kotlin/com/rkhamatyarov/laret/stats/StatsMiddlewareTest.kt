package com.rkhamatyarov.laret.stats

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.model.Command
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class StatsMiddlewareTest {
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        StatsCollector.configureForTest(
            tempDir.resolve("stats.json"),
            enabledProvider = { true },
            clock = { 1_700_000_000_000L },
        )
    }

    @AfterEach
    fun tearDown() {
        StatsCollector.resetForTest()
    }

    @Test
    fun recordsSuccessfulCommandWithDuration() {
        var nowMs = 1_700_000_000_000L
        val middleware = StatsMiddleware(clock = { nowMs })
        val ctx = ctx("file", "create")

        runBlocking {
            middleware.handle(ctx) { nowMs += 75 }
        }

        val stat = StatsCollector.snapshot().commands.getValue(CommandKey("file", "create"))
        assertThat(stat.count).isEqualTo(1)
        assertThat(stat.successCount).isEqualTo(1)
        assertThat(stat.failureCount).isZero()
        assertThat(stat.lastDurationMs).isEqualTo(75)
        assertThat(stat.lastExitCode).isZero()
    }

    @Test
    fun recordsFailureWhenActionThrows() {
        val middleware = StatsMiddleware(clock = { 1L })
        val ctx = ctx("file", "delete")

        try {
            runBlocking {
                middleware.handle(ctx) { throw RuntimeException("boom") }
            }
        } catch (_: RuntimeException) {
            // expected — middleware must rethrow so CommandRunner can call onError
        }

        val stat = StatsCollector.snapshot().commands.getValue(CommandKey("file", "delete"))
        assertThat(stat.count).isEqualTo(1)
        assertThat(stat.failureCount).isEqualTo(1)
        assertThat(stat.successCount).isZero()
        assertThat(stat.lastExitCode).isEqualTo(1)
    }

    @Test
    fun runsAtLowerPriorityThanDefaultMiddleware() {
        // Confirms the middleware wraps everything else, so its measured
        // duration includes the work of any logging/metrics middleware too.
        assertThat(StatsMiddleware().priority).isLessThan(0)
    }

    private fun ctx(group: String, command: String): CommandContext {
        val cmd = Command(name = command, description = "")
        return CommandContext(command = cmd, app = null, groupName = group)
    }
}
