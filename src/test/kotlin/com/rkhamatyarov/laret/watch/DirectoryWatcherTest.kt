package com.rkhamatyarov.laret.watch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class DirectoryWatcherTest {
    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("laret-watch-test-")
    }

    @AfterEach
    fun tearDown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { it.toFile().delete() }
    }

    @Nested
    inner class HappyPath {
        @Test
        fun emitsCreateEventForNewFile() {
            val events = mutableListOf<WatchEvent>()
            val watcher = DirectoryWatcher(tempDir, WatchOptions(maxEvents = 1, durationSeconds = 5))

            val result = runWithMutator(watcher, events) {
                tempDir.resolve("new_file.txt").createFile()
            }

            assertThat(result.stopReason).isEqualTo(StopReason.MAX_EVENTS_REACHED)
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(WatchEventType.CREATE)
            assertThat(events[0].path.fileName.toString()).isEqualTo("new_file.txt")
        }

        @Test
        fun emitsDeleteEventForRemovedFile() {
            val target = tempDir.resolve("victim.txt").also { it.createFile() }
            val events = mutableListOf<WatchEvent>()
            val watcher = DirectoryWatcher(
                tempDir,
                WatchOptions(maxEvents = 1, durationSeconds = 5, acceptedTypes = setOf(WatchEventType.DELETE)),
            )

            val result = runWithMutator(watcher, events) { target.deleteIfExists() }

            assertThat(result.stopReason).isEqualTo(StopReason.MAX_EVENTS_REACHED)
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(WatchEventType.DELETE)
            assertThat(events[0].path.fileName.toString()).isEqualTo("victim.txt")
        }

        @Test
        fun emitsModifyEventForChangedFile() {
            val target = tempDir.resolve("doc.txt").also { it.createFile() }
            val events = mutableListOf<WatchEvent>()
            val watcher = DirectoryWatcher(
                tempDir,
                WatchOptions(maxEvents = 1, durationSeconds = 5, acceptedTypes = setOf(WatchEventType.MODIFY)),
            )

            val result = runWithMutator(watcher, events) { target.writeText("changed") }

            assertThat(result.stopReason).isEqualTo(StopReason.MAX_EVENTS_REACHED)
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(WatchEventType.MODIFY)
        }
    }

    @Nested
    inner class StopConditions {
        @Test
        fun durationExpiredWhenNoEventsArrive() {
            val watcher = DirectoryWatcher(tempDir, WatchOptions(durationSeconds = 1))
            val collected = mutableListOf<WatchEvent>()

            val start = System.currentTimeMillis()
            val summary = watcher.watch { collected += it }
            val elapsed = System.currentTimeMillis() - start

            assertThat(summary.stopReason).isEqualTo(StopReason.DURATION_EXPIRED)
            assertThat(collected).isEmpty()
            assertThat(elapsed).isBetween(900L, 3_000L)
        }

        @Test
        fun maxEventsStopsCollectionAtLimit() {
            val watcher = DirectoryWatcher(tempDir, WatchOptions(maxEvents = 2, durationSeconds = 5))
            val events = mutableListOf<WatchEvent>()

            val result = runWithMutator(watcher, events) {
                repeat(5) { i -> tempDir.resolve("f$i.txt").createFile() }
            }

            assertThat(result.stopReason).isEqualTo(StopReason.MAX_EVENTS_REACHED)
            assertThat(events).hasSize(2)
        }
    }

    @Nested
    inner class Validation {
        @Test
        fun rejectsNonDirectoryRoot() {
            val notADir = tempDir.resolve("regular.txt").also { it.createFile() }
            val watcher = DirectoryWatcher(notADir, WatchOptions(durationSeconds = 1))

            assertThrows<IllegalArgumentException> {
                watcher.watch { /* no-op */ }
            }
        }
    }

    @Nested
    inner class Recursive {
        @Test
        fun detectsEventsInPreexistingSubdirectory() {
            val sub = tempDir.resolve("nested").also { it.createDirectory() }
            val watcher = DirectoryWatcher(
                tempDir,
                WatchOptions(recursive = true, maxEvents = 1, durationSeconds = 5),
            )
            val events = mutableListOf<WatchEvent>()

            val result = runWithMutator(watcher, events) {
                sub.resolve("inside.txt").createFile()
            }

            assertThat(result.stopReason).isEqualTo(StopReason.MAX_EVENTS_REACHED)
            assertThat(events).hasSize(1)
            assertThat(events[0].path.toString()).contains("nested")
            assertThat(events[0].path.fileName.toString()).isEqualTo("inside.txt")
        }
    }

    @Nested
    inner class EventFilter {
        @Test
        fun ignoresEventsOutsideAcceptedSet() {
            val target = tempDir.resolve("only_create.txt")
            val watcher = DirectoryWatcher(
                tempDir,
                WatchOptions(
                    maxEvents = 1,
                    durationSeconds = 3,
                    acceptedTypes = setOf(WatchEventType.CREATE),
                ),
            )
            val events = mutableListOf<WatchEvent>()

            val result = runWithMutator(watcher, events) {
                target.createFile()
                target.writeText("after")
                target.deleteIfExists()
            }

            assertThat(result.stopReason).isEqualTo(StopReason.MAX_EVENTS_REACHED)
            assertThat(events.map { it.type }).containsOnly(WatchEventType.CREATE)
        }
    }

    private fun runWithMutator(
        watcher: DirectoryWatcher,
        events: MutableList<WatchEvent>,
        mutate: () -> Unit,
    ): WatchSummary {
        val started = CountDownLatch(1)
        var summary: WatchSummary? = null

        val thread = Thread {
            started.countDown()
            summary = watcher.watch { events += it }
        }
        thread.isDaemon = true
        thread.start()

        started.await(2, TimeUnit.SECONDS)
        Thread.sleep(REGISTRATION_GRACE_MS)
        mutate()
        thread.join(THREAD_JOIN_TIMEOUT_MS)
        if (thread.isAlive) {
            watcher.close()
            thread.join(1_000)
        }

        return checkNotNull(summary) { "Watcher did not return a summary in time" }
    }

    companion object {
        private const val REGISTRATION_GRACE_MS = 250L
        private const val THREAD_JOIN_TIMEOUT_MS = 6_000L
    }
}
