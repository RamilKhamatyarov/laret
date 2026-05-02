package com.rkhamatyarov.laret.stats

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class StatsFormat(val id: String) {
    PROMETHEUS("prometheus"),
    JSON("json"),
    PLAIN("plain"),
    ;

    companion object {
        fun fromId(id: String): StatsFormat? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

interface StatsFormatter {
    fun render(snapshot: StatsSnapshot): String
}

/**
 * Renders a [StatsSnapshot] using the official Prometheus text exposition
 * format (https://prometheus.io/docs/instrumenting/exposition_formats/).
 *
 * Output is deterministic: metric blocks appear in a fixed order and labels
 * are sorted by (group, command) so diffs between exports stay readable.
 */
class PrometheusFormatter : StatsFormatter {
    override fun render(snapshot: StatsSnapshot): String =
        buildString {
            appendCounter(
                snapshot,
                "laret_command_executions_total",
                "Total number of command executions, partitioned by result.",
            ) { stat ->
                listOf(
                    Sample(extraLabels = mapOf("result" to "success"), value = stat.successCount.toString()),
                    Sample(extraLabels = mapOf("result" to "failure"), value = stat.failureCount.toString()),
                )
            }

            appendSimple(
                snapshot,
                "laret_command_duration_milliseconds_total",
                "counter",
                "Total cumulative duration of command executions in milliseconds.",
            ) { it.totalDurationMs.toString() }

            appendSimple(
                snapshot,
                "laret_command_last_duration_milliseconds",
                "gauge",
                "Duration of the last command execution in milliseconds.",
            ) { it.lastDurationMs.toString() }

            appendSimple(
                snapshot,
                "laret_command_last_exit_code",
                "gauge",
                "Exit code of the last command execution.",
            ) { it.lastExitCode.toString() }

            appendSimple(
                snapshot,
                "laret_command_last_execution_timestamp_seconds",
                "gauge",
                "Unix timestamp of the last command execution.",
            ) { (it.lastTimestampEpochMs / 1000L).toString() }

            append("# HELP laret_stats_started_timestamp_seconds Unix timestamp when stats collection started.\n")
            append("# TYPE laret_stats_started_timestamp_seconds gauge\n")
            append("laret_stats_started_timestamp_seconds ${snapshot.startedAtEpochMs / 1000L}\n")

            append("# HELP laret_stats_enabled 1 if stats collection is currently enabled, 0 otherwise.\n")
            append("# TYPE laret_stats_enabled gauge\n")
            append("laret_stats_enabled ${if (snapshot.enabled) 1 else 0}\n")
        }

    private data class Sample(val extraLabels: Map<String, String> = emptyMap(), val value: String)

    private fun StringBuilder.appendCounter(
        snapshot: StatsSnapshot,
        name: String,
        help: String,
        samples: (CommandStat) -> List<Sample>,
    ) {
        append("# HELP $name $help\n")
        append("# TYPE $name counter\n")
        snapshot.commands.entries
            .sortedBy { it.key }
            .forEach { (key, stat) ->
                samples(stat).forEach { sample ->
                    val labels = renderLabels(
                        mapOf("group" to key.group, "command" to key.command) + sample.extraLabels,
                    )
                    append("$name$labels ${sample.value}\n")
                }
            }
    }

    private fun StringBuilder.appendSimple(
        snapshot: StatsSnapshot,
        name: String,
        type: String,
        help: String,
        value: (CommandStat) -> String,
    ) {
        append("# HELP $name $help\n")
        append("# TYPE $name $type\n")
        snapshot.commands.entries
            .sortedBy { it.key }
            .forEach { (key, stat) ->
                val labels = renderLabels(mapOf("group" to key.group, "command" to key.command))
                append("$name$labels ${value(stat)}\n")
            }
    }

    private fun renderLabels(labels: Map<String, String>): String {
        if (labels.isEmpty()) return ""
        val rendered = labels.entries.joinToString(",") { (k, v) -> "$k=\"${escapeLabel(v)}\"" }
        return "{$rendered}"
    }

    private fun escapeLabel(value: String): String =
        buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    else -> append(ch)
                }
            }
        }
}

class JsonStatsFormatter : StatsFormatter {
    private val mapper = jacksonObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun render(snapshot: StatsSnapshot): String {
        val payload = mapOf(
            "enabled" to snapshot.enabled,
            "startedAtEpochMs" to snapshot.startedAtEpochMs,
            "totalCommandCount" to snapshot.totalCommandCount,
            "commands" to snapshot.commands.entries.sortedBy { it.key }.map { (k, v) ->
                mapOf(
                    "group" to k.group,
                    "command" to k.command,
                    "count" to v.count,
                    "successCount" to v.successCount,
                    "failureCount" to v.failureCount,
                    "totalDurationMs" to v.totalDurationMs,
                    "lastDurationMs" to v.lastDurationMs,
                    "lastExitCode" to v.lastExitCode,
                    "lastTimestampEpochMs" to v.lastTimestampEpochMs,
                )
            },
        )
        return mapper.writeValueAsString(payload)
    }
}

class PlainStatsFormatter : StatsFormatter {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    override fun render(snapshot: StatsSnapshot): String =
        buildString {
            append("Enabled: ${snapshot.enabled}\n")
            append("Started: ${fmt.format(Instant.ofEpochMilli(snapshot.startedAtEpochMs))} UTC\n")
            append("Total commands: ${snapshot.totalCommandCount}\n")
            if (snapshot.commands.isEmpty()) {
                append("No commands recorded yet.\n")
                return@buildString
            }
            append("\n")
            val header = listOf("GROUP", "COMMAND", "COUNT", "OK", "FAIL", "TOTAL_MS", "LAST_MS", "LAST_EXIT")
            val rows = snapshot.commands.entries
                .sortedBy { it.key }
                .map { (k, v) ->
                    listOf(
                        k.group,
                        k.command,
                        v.count.toString(),
                        v.successCount.toString(),
                        v.failureCount.toString(),
                        v.totalDurationMs.toString(),
                        v.lastDurationMs.toString(),
                        v.lastExitCode.toString(),
                    )
                }
            val widths = header.indices.map { col ->
                (listOf(header[col]) + rows.map { it[col] }).maxOf { it.length }
            }
            fun appendRow(row: List<String>) {
                row.forEachIndexed { i, cell ->
                    append(cell.padEnd(widths[i]))
                    if (i < row.lastIndex) append("  ")
                }
                append("\n")
            }
            appendRow(header)
            rows.forEach(::appendRow)
        }
}
