package com.rkhamatyarov.laret.stats

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrometheusFormatterTest {
    private val formatter = PrometheusFormatter()

    @Test
    fun emitsHelpAndTypeLinesForEveryMetric() {
        val output = formatter.render(emptySnapshot())

        listOf(
            "laret_command_executions_total",
            "laret_command_duration_milliseconds_total",
            "laret_command_last_duration_milliseconds",
            "laret_command_last_exit_code",
            "laret_command_last_execution_timestamp_seconds",
            "laret_stats_started_timestamp_seconds",
            "laret_stats_enabled",
        ).forEach { metric ->
            assertThat(output).contains("# HELP $metric")
            assertThat(output).contains("# TYPE $metric")
        }
    }

    @Test
    fun emitsSuccessAndFailureSamplesForExecutionsCounter() {
        val snapshot = StatsSnapshot(
            commands = mapOf(
                CommandKey("file", "create") to CommandStat(
                    count = 5,
                    successCount = 4,
                    failureCount = 1,
                    totalDurationMs = 250,
                    lastDurationMs = 50,
                    lastExitCode = 0,
                    lastTimestampEpochMs = 1_700_000_000_000L,
                ),
            ),
            startedAtEpochMs = 1_700_000_000_000L,
            totalCommandCount = 5,
            enabled = true,
        )

        val output = formatter.render(snapshot)

        assertThat(output).contains(
            "laret_command_executions_total{group=\"file\",command=\"create\",result=\"success\"} 4",
        )
        assertThat(output).contains(
            "laret_command_executions_total{group=\"file\",command=\"create\",result=\"failure\"} 1",
        )
        assertThat(output).contains(
            "laret_command_duration_milliseconds_total{group=\"file\",command=\"create\"} 250",
        )
        assertThat(output).contains(
            "laret_command_last_duration_milliseconds{group=\"file\",command=\"create\"} 50",
        )
    }

    @Test
    fun convertsTimestampsFromMillisToSeconds() {
        val snapshot = StatsSnapshot(
            commands = mapOf(
                CommandKey("g", "c") to CommandStat(
                    count = 1,
                    successCount = 1,
                    lastTimestampEpochMs = 1_700_000_000_000L,
                ),
            ),
            startedAtEpochMs = 1_700_000_000_000L,
            totalCommandCount = 1,
            enabled = true,
        )

        val output = formatter.render(snapshot)

        assertThat(output).contains(
            "laret_command_last_execution_timestamp_seconds{group=\"g\",command=\"c\"} 1700000000",
        )
        assertThat(output).contains("laret_stats_started_timestamp_seconds 1700000000")
    }

    @Test
    fun escapesQuotesAndBackslashesInLabels() {
        val snapshot = StatsSnapshot(
            commands = mapOf(
                CommandKey("g\"\\n", "cmd") to CommandStat(count = 1, successCount = 1),
            ),
            startedAtEpochMs = 0,
            totalCommandCount = 1,
            enabled = true,
        )

        val output = formatter.render(snapshot)

        assertThat(output).contains("group=\"g\\\"\\\\n\"")
    }

    @Test
    fun emitsEnabledGaugeReflectingSnapshotState() {
        val enabled = formatter.render(emptySnapshot(enabled = true))
        val disabled = formatter.render(emptySnapshot(enabled = false))

        assertThat(enabled).contains("laret_stats_enabled 1")
        assertThat(disabled).contains("laret_stats_enabled 0")
    }

    @Test
    fun outputIsDeterministicAcrossRuns() {
        val snapshot = StatsSnapshot(
            commands = mapOf(
                CommandKey("z", "z") to CommandStat(count = 1, successCount = 1),
                CommandKey("a", "a") to CommandStat(count = 1, successCount = 1),
                CommandKey("a", "b") to CommandStat(count = 1, successCount = 1),
            ),
            startedAtEpochMs = 0,
            totalCommandCount = 3,
            enabled = true,
        )

        val first = formatter.render(snapshot)
        val second = formatter.render(snapshot)
        assertThat(first).isEqualTo(second)

        val aaIdx = first.indexOf("group=\"a\",command=\"a\"")
        val abIdx = first.indexOf("group=\"a\",command=\"b\"")
        val zzIdx = first.indexOf("group=\"z\",command=\"z\"")
        assertThat(aaIdx).isLessThan(abIdx)
        assertThat(abIdx).isLessThan(zzIdx)
    }

    private fun emptySnapshot(enabled: Boolean = true): StatsSnapshot =
        StatsSnapshot(
            commands = emptyMap(),
            startedAtEpochMs = 0,
            totalCommandCount = 0,
            enabled = enabled,
        )
}
