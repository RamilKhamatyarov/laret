package io.github.laretframework.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ParallelDispatcherBlocksTest {

    @Test
    fun aggregatesResultsInSubmissionOrder() = runTest {
        val blocks = (1..100).map { n ->
            suspend {
                yield()
                n * 2
            }
        }

        val results = ParallelDispatcher.executeBlocks(blocks)

        assertThat(results).isEqualTo((1..100).map { it * 2 })
    }

    @Test
    fun boundedDispatchAlsoPreservesSubmissionOrder() = runTest {
        val blocks = (1..100).map { n ->
            suspend {
                yield()
                n * 2
            }
        }

        val results = ParallelDispatcher.executeBlocks(blocks, maxJobs = 4)

        assertThat(results).isEqualTo((1..100).map { it * 2 })
    }

    @Test
    fun emptyBlockListReturnsEmptyResults() = runTest {
        val results = ParallelDispatcher.executeBlocks(emptyList<suspend () -> Int>())

        assertThat(results).isEmpty()
    }

    @Test
    fun unboundedDispatchRunsEveryBlockAtOnce() = runTest {
        val count = 500
        val arrived = AtomicInteger(0)
        val blocks = (1..count).map {
            suspend {
                arrived.incrementAndGet()
                while (arrived.get() < count) delay(1.toDuration(DurationUnit.MILLISECONDS))
                true
            }
        }

        val results = ParallelDispatcher.executeBlocks(blocks)

        assertThat(results).hasSize(count).containsOnly(true)
        assertThat(arrived.get()).isEqualTo(count)
    }

    @Test
    fun boundedDispatchNeverExceedsItsLimit() = runTest {
        val maxJobs = 3
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val blocks = (1..60).map {
            suspend {
                val now = inFlight.incrementAndGet()
                peak.updateAndGet { seen -> maxOf(seen, now) }
                delay(2.toDuration(DurationUnit.MILLISECONDS))
                inFlight.decrementAndGet()
                now
            }
        }

        ParallelDispatcher.executeBlocks(blocks, maxJobs = maxJobs)

        assertThat(peak.get()).isLessThanOrEqualTo(maxJobs)
        assertThat(inFlight.get()).isZero()
    }

    @Test
    fun oneJobRunsBlocksStrictlyInOrder() = runTest {
        val observed = ConcurrentLinkedQueue<String>()
        val blocks = (1..5).map { n ->
            suspend {
                observed += "start-$n"
                delay(5.toDuration(DurationUnit.MILLISECONDS))
                observed += "end-$n"
                n
            }
        }

        ParallelDispatcher.executeBlocks(blocks, maxJobs = 1)

        assertThat(observed).containsExactly(
            "start-1", "end-1",
            "start-2", "end-2",
            "start-3", "end-3",
            "start-4", "end-4",
            "start-5", "end-5",
        )
    }

    @Test
    fun failureInOneBlockPropagates() = runTest {
        val blocks = listOf<suspend () -> Int>(
            { 1 },
            { throw IllegalStateException("block two failed") },
            { 3 },
        )

        val thrown = catching { ParallelDispatcher.executeBlocks(blocks) }

        assertThat(thrown)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("block two failed")
    }

    @Test
    fun rejectsNegativeMaxJobs() = runTest {
        val thrown = catching { ParallelDispatcher.executeBlocks(listOf<suspend () -> Int>({ 1 }), -1) }

        assertThat(thrown)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must not be negative")
    }

    @Test
    fun processPathStillRejectsAnOversizedJobCount() = runTest {
        val thrown = catching {
            ParallelDispatcher.execute(
                listOf(ParallelTask("true", emptyList())),
                maxJobs = ParallelDispatcher.MAX_PROCESS_JOBS + 1,
            ) { _, _, _ -> }
        }

        assertThat(thrown)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("1..${ParallelDispatcher.MAX_PROCESS_JOBS}")
    }

    /** Nesting runTest inside an assertion lambda is rejected, so catch by hand. */
    private suspend fun catching(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Throwable) {
        e
    }
}
