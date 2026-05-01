package com.rkhamatyarov.laret.watch

import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory

enum class WatchEventType {
    CREATE,
    MODIFY,
    DELETE,
}

data class WatchEvent(val type: WatchEventType, val path: Path)

data class WatchOptions(
    val recursive: Boolean = false,
    val durationSeconds: Long = 0,
    val maxEvents: Int = 0,
    val acceptedTypes: Set<WatchEventType> = WatchEventType.values().toSet(),
)

class DirectoryWatcher(
    private val root: Path,
    private val options: WatchOptions = WatchOptions(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val keyToPath = mutableMapOf<WatchKey, Path>()

    fun watch(onEvent: (WatchEvent) -> Unit): WatchSummary {
        require(root.isDirectory()) { "Not a directory: $root" }
        registerAll(root)

        val deadline = if (options.durationSeconds > 0) {
            clock() + TimeUnit.SECONDS.toMillis(options.durationSeconds)
        } else {
            Long.MAX_VALUE
        }
        var emitted = 0
        var stopReason: StopReason

        try {
            loop@ while (true) {
                val now = clock()
                if (now >= deadline) {
                    stopReason = StopReason.DURATION_EXPIRED
                    break
                }
                val remaining = if (deadline == Long.MAX_VALUE) Long.MAX_VALUE else deadline - now
                val pollMillis = remaining.coerceAtMost(POLL_INTERVAL_MS)

                val key = try {
                    watchService.poll(pollMillis, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    stopReason = StopReason.INTERRUPTED
                    break
                } catch (_: ClosedWatchServiceException) {
                    stopReason = StopReason.INTERRUPTED
                    break
                } ?: continue

                val dir = keyToPath[key] ?: run {
                    key.reset()
                    continue
                }

                for (rawEvent in key.pollEvents()) {
                    val kind = rawEvent.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue
                    val context = rawEvent.context() as? Path ?: continue
                    val resolved = dir.resolve(context)

                    val type = when (kind) {
                        StandardWatchEventKinds.ENTRY_CREATE -> WatchEventType.CREATE
                        StandardWatchEventKinds.ENTRY_MODIFY -> WatchEventType.MODIFY
                        StandardWatchEventKinds.ENTRY_DELETE -> WatchEventType.DELETE
                        else -> continue
                    }

                    if (options.recursive &&
                        type == WatchEventType.CREATE &&
                        resolved.isDirectory()
                    ) {
                        registerAll(resolved)
                    }

                    if (type !in options.acceptedTypes) continue

                    onEvent(WatchEvent(type, resolved))
                    emitted++
                    if (options.maxEvents in 1..emitted) {
                        stopReason = StopReason.MAX_EVENTS_REACHED
                        break@loop
                    }
                }

                if (!key.reset()) {
                    keyToPath.remove(key)
                    if (keyToPath.isEmpty()) {
                        stopReason = StopReason.WATCH_ROOT_GONE
                        break
                    }
                }
            }
        } finally {
            close()
        }

        return WatchSummary(emittedEvents = emitted, stopReason = stopReason)
    }

    fun close() {
        try {
            watchService.close()
        } catch (_: Exception) {
        }
    }

    private fun registerAll(start: Path) {
        register(start)
        if (!options.recursive) return
        Files.walk(start).use { stream ->
            stream
                .filter { it != start && Files.isDirectory(it) }
                .forEach { register(it) }
        }
    }

    private fun register(dir: Path) {
        val key = dir.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE,
        )
        keyToPath[key] = dir
    }

    companion object {
        private const val POLL_INTERVAL_MS = 200L
    }
}

enum class StopReason {
    DURATION_EXPIRED,
    MAX_EVENTS_REACHED,
    INTERRUPTED,
    WATCH_ROOT_GONE,
}

data class WatchSummary(
    val emittedEvents: Int,
    val stopReason: StopReason,
)
