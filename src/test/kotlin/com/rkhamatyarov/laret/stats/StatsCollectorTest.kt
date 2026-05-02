package com.rkhamatyarov.laret.stats

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StatsCollectorTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storagePath: Path

    @BeforeEach
    fun setUp() {
        storagePath = tempDir.resolve("stats.json")
        StatsCollector.configureForTest(storagePath, enabledProvider = { true }, clock = { 1_700_000_000_000L })
    }

    @AfterEach
    fun tearDown() {
        StatsCollector.resetForTest()
    }

    @Test
    fun aggregatesCountsBySuccessAndFailure() {
        StatsCollector.record("file", "create", durationMs = 50, exitCode = 0)
        StatsCollector.record("file", "create", durationMs = 70, exitCode = 0)
        StatsCollector.record("file", "create", durationMs = 30, exitCode = 1)

        val snapshot = StatsCollector.snapshot()
        val stat = snapshot.commands.getValue(CommandKey("file", "create"))

        assertThat(stat.count).isEqualTo(3)
        assertThat(stat.successCount).isEqualTo(2)
        assertThat(stat.failureCount).isEqualTo(1)
        assertThat(stat.totalDurationMs).isEqualTo(150)
        assertThat(stat.lastDurationMs).isEqualTo(30)
        assertThat(stat.lastExitCode).isEqualTo(1)
        assertThat(snapshot.totalCommandCount).isEqualTo(3)
    }

    @Test
    fun separatesDifferentCommandKeys() {
        StatsCollector.record("file", "create", 10, 0)
        StatsCollector.record("file", "delete", 20, 0)
        StatsCollector.record("dir", "create", 30, 0)

        val snapshot = StatsCollector.snapshot()
        assertThat(snapshot.commands).hasSize(3)
        assertThat(snapshot.commands.keys)
            .containsExactlyInAnyOrder(
                CommandKey("file", "create"),
                CommandKey("file", "delete"),
                CommandKey("dir", "create"),
            )
    }

    @Test
    fun persistsAndReloadsStateAcrossInstances() {
        StatsCollector.record("file", "create", 42, 0)
        StatsCollector.record("file", "create", 8, 1)
        assertThat(Files.exists(storagePath)).isTrue()

        // Reconfigure → triggers a fresh load() from the same file.
        StatsCollector.configureForTest(storagePath, enabledProvider = { true }, clock = { 1_700_000_000_000L })

        val snapshot = StatsCollector.snapshot()
        val stat = snapshot.commands.getValue(CommandKey("file", "create"))
        assertThat(stat.count).isEqualTo(2)
        assertThat(stat.successCount).isEqualTo(1)
        assertThat(stat.failureCount).isEqualTo(1)
        assertThat(stat.totalDurationMs).isEqualTo(50)
    }

    @Test
    fun resetClearsCommandsAndUpdatesStartTime() {
        StatsCollector.record("file", "create", 10, 0)
        var nowMs = 2_000_000_000_000L
        StatsCollector.configureForTest(storagePath, enabledProvider = { true }, clock = { nowMs })

        nowMs = 2_000_000_001_000L
        StatsCollector.reset()

        val snapshot = StatsCollector.snapshot()
        assertThat(snapshot.commands).isEmpty()
        assertThat(snapshot.totalCommandCount).isZero()
        assertThat(snapshot.startedAtEpochMs).isEqualTo(2_000_000_001_000L)
    }

    @Test
    fun recordIsNoOpWhenDisabled() {
        StatsCollector.configureForTest(storagePath, enabledProvider = { false }, clock = { 1L })

        StatsCollector.record("file", "create", 10, 0)

        val snapshot = StatsCollector.snapshot()
        assertThat(snapshot.commands).isEmpty()
        assertThat(snapshot.enabled).isFalse()
    }

    @Test
    fun corruptStatsFileDoesNotCrashLoad() {
        Files.writeString(storagePath, "{not valid json")

        StatsCollector.configureForTest(storagePath, enabledProvider = { true }, clock = { 1L })

        val snapshot = StatsCollector.snapshot()
        assertThat(snapshot.commands).isEmpty()
    }
}
