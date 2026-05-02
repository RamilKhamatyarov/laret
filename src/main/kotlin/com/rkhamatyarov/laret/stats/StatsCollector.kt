package com.rkhamatyarov.laret.stats

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

data class CommandKey(val group: String, val command: String) : Comparable<CommandKey> {
    override fun compareTo(other: CommandKey): Int =
        compareValuesBy(this, other, { it.group }, { it.command })
}

data class CommandStat(
    val count: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val totalDurationMs: Long = 0,
    val lastDurationMs: Long = 0,
    val lastExitCode: Int = 0,
    val lastTimestampEpochMs: Long = 0,
)

data class StatsSnapshot(
    val commands: Map<CommandKey, CommandStat>,
    val startedAtEpochMs: Long,
    val totalCommandCount: Long,
    val enabled: Boolean,
)

/**
 * Records command-execution metrics across CLI invocations.
 *
 * State is persisted to `~/.laret/stats.json` so counters survive process exits.
 * Set `LARET_STATS_DISABLED=1` to make every [record] call a no-op (snapshot
 * reads still work).
 */
object StatsCollector {
    private val lock = Any()

    @Volatile
    private var state: CollectorState = CollectorState.default()

    fun isEnabled(): Boolean = state.enabledProvider()

    fun record(group: String, command: String, durationMs: Long, exitCode: Int) {
        if (!isEnabled()) return
        synchronized(lock) { state.record(group, command, durationMs, exitCode) }
    }

    fun snapshot(): StatsSnapshot = synchronized(lock) { state.snapshot() }

    fun reset() = synchronized(lock) { state.reset() }

    fun configureForTest(
        storagePath: Path,
        enabledProvider: () -> Boolean = { true },
        clock: () -> Long = System::currentTimeMillis,
    ) = synchronized(lock) {
        state = CollectorState(storagePath, enabledProvider, clock)
    }

    fun resetForTest() = synchronized(lock) {
        state = CollectorState.default()
    }
}

internal class CollectorState(
    private val storagePath: Path,
    val enabledProvider: () -> Boolean,
    private val clock: () -> Long,
) {
    private val mapper = jacksonObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }
    private var startedAtEpochMs: Long = clock()
    private val commands: MutableMap<CommandKey, CommandStat> = mutableMapOf()

    init {
        load()
    }

    fun record(group: String, command: String, durationMs: Long, exitCode: Int) {
        val key = CommandKey(group, command)
        val current = commands[key] ?: CommandStat()
        val success = exitCode == 0
        commands[key] = current.copy(
            count = current.count + 1,
            successCount = current.successCount + if (success) 1 else 0,
            failureCount = current.failureCount + if (success) 0 else 1,
            totalDurationMs = current.totalDurationMs + durationMs.coerceAtLeast(0),
            lastDurationMs = durationMs.coerceAtLeast(0),
            lastExitCode = exitCode,
            lastTimestampEpochMs = clock(),
        )
        persist()
    }

    fun snapshot(): StatsSnapshot = StatsSnapshot(
        commands = commands.toMap(),
        startedAtEpochMs = startedAtEpochMs,
        totalCommandCount = commands.values.sumOf { it.count },
        enabled = enabledProvider(),
    )

    fun reset() {
        commands.clear()
        startedAtEpochMs = clock()
        persist()
    }

    private fun load() {
        if (!Files.exists(storagePath)) return
        try {
            val data = mapper.readValue<PersistedStats>(storagePath.toFile())
            startedAtEpochMs = data.startedAtEpochMs
            commands.clear()
            data.entries.forEach { entry ->
                commands[CommandKey(entry.group, entry.command)] = CommandStat(
                    count = entry.count,
                    successCount = entry.successCount,
                    failureCount = entry.failureCount,
                    totalDurationMs = entry.totalDurationMs,
                    lastDurationMs = entry.lastDurationMs,
                    lastExitCode = entry.lastExitCode,
                    lastTimestampEpochMs = entry.lastTimestampEpochMs,
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun persist() {
        try {
            val parent = storagePath.parent
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
            val payload = PersistedStats(
                startedAtEpochMs = startedAtEpochMs,
                entries = commands.entries
                    .sortedBy { it.key }
                    .map { (k, v) ->
                        PersistedEntry(
                            group = k.group,
                            command = k.command,
                            count = v.count,
                            successCount = v.successCount,
                            failureCount = v.failureCount,
                            totalDurationMs = v.totalDurationMs,
                            lastDurationMs = v.lastDurationMs,
                            lastExitCode = v.lastExitCode,
                            lastTimestampEpochMs = v.lastTimestampEpochMs,
                        )
                    },
            )
            val tmp = storagePath.resolveSibling("${storagePath.fileName}.tmp")
            mapper.writeValue(tmp.toFile(), payload)
            try {
                Files.move(tmp, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, storagePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        fun default(): CollectorState = CollectorState(
            storagePath = defaultStoragePath(),
            enabledProvider = ::isEnabledByEnv,
            clock = System::currentTimeMillis,
        )

        private fun defaultStoragePath(): Path {
            val override = System.getenv("LARET_STATS_FILE")
            if (!override.isNullOrBlank()) return Paths.get(override)
            val home = System.getProperty("user.home") ?: "."
            return Paths.get(home, ".laret", "stats.json")
        }

        private fun isEnabledByEnv(): Boolean {
            val raw = System.getenv("LARET_STATS_DISABLED")?.trim()?.lowercase() ?: return true
            return raw !in setOf("1", "true", "yes", "on")
        }
    }
}

internal data class PersistedStats(
    val startedAtEpochMs: Long = 0,
    val entries: List<PersistedEntry> = emptyList(),
)

internal data class PersistedEntry(
    val group: String = "",
    val command: String = "",
    val count: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val totalDurationMs: Long = 0,
    val lastDurationMs: Long = 0,
    val lastExitCode: Int = 0,
    val lastTimestampEpochMs: Long = 0,
)
