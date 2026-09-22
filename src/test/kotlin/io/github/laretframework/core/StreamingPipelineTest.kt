package io.github.laretframework.core

import io.github.laretframework.dsl.cli
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

class StreamingPipelineTest {

    private val liveStages = AtomicInteger(0)
    private val peakLiveStages = AtomicInteger(0)

    private fun app() = cli(name = "streamtest", version = "0", description = "streaming pipeline fixture") {
        group(name = "gen", description = "producers") {
            command(name = "lines", description = "Emit numbered lines") {
                option("n", "count", "How many lines", "10", true)
                action { ctx ->
                    enter()
                    try {
                        val count = ctx.option("count").toInt()
                        repeat(count) { index -> println("line-$index") }
                    } finally {
                        leave()
                    }
                }
            }
        }
        group(name = "text", description = "consumers") {
            command(name = "grep", description = "Keep lines containing a needle") {
                option("p", "pattern", "Substring to keep", "", true)
                action { ctx ->
                    enter()
                    try {
                        val needle = ctx.option("pattern")
                        readStdinLines { line -> if (needle.isEmpty() || needle in line) println(line) }
                    } finally {
                        leave()
                    }
                }
            }
            command(name = "count", description = "Count lines on stdin") {
                action {
                    enter()
                    try {
                        var seen = 0
                        readStdinLines { seen++ }
                        println(seen)
                    } finally {
                        leave()
                    }
                }
            }
            command(name = "head", description = "Print the first line and stop reading") {
                action {
                    enter()
                    try {
                        val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
                        println(reader.readLine().orEmpty())
                    } finally {
                        leave()
                    }
                }
            }
            command(name = "boom", description = "Fail after draining stdin") {
                action { ctx ->
                    enter()
                    try {
                        readStdinLines { }
                        ctx.exit(3)
                    } finally {
                        leave()
                    }
                }
            }
        }
    }

    private fun enter() {
        val now = liveStages.incrementAndGet()
        peakLiveStages.updateAndGet { seen -> maxOf(seen, now) }
    }

    private fun leave() {
        liveStages.decrementAndGet()
    }

    private fun readStdinLines(onLine: (String) -> Unit) {
        BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).forEachLine(onLine)
    }

    private fun run(vararg stages: Array<String>, capacityChunks: Int = 16): Pair<PipelineResult, String> {
        val sink = ByteArrayOutputStream()
        val result = CommandPipeline(app()).executeStreamingResult(stages.toList(), sink, capacityChunks)
        return result to sink.toString(Charsets.UTF_8)
    }

    @Test
    fun streamsLinesFromProducerToConsumer() {
        val (result, output) = run(
            arrayOf("gen", "lines", "--count", "5"),
            arrayOf("text", "count"),
        )

        assertThat(result.exitCode).isZero()
        assertThat(result.completedStages).isEqualTo(2)
        assertThat(result.failedStage).isNull()
        assertThat(output.trim()).isEqualTo("5")
    }

    @Test
    fun threeStagesFilterAndAggregate() {
        val (result, output) = run(
            arrayOf("gen", "lines", "--count", "30"),
            arrayOf("text", "grep", "--pattern", "1"),
            arrayOf("text", "count"),
        )

        assertThat(result.exitCode).isZero()
        assertThat(result.completedStages).isEqualTo(3)
        assertThat(output.trim()).isEqualTo("12")
    }

    @Test
    fun allStagesAreLiveAtTheSameTime() {
        liveStages.set(0)
        peakLiveStages.set(0)

        run(
            arrayOf("gen", "lines", "--count", "2000"),
            arrayOf("text", "grep", "--pattern", "line"),
            arrayOf("text", "count"),
        )

        assertThat(peakLiveStages.get()).isEqualTo(3)
        assertThat(liveStages.get()).isZero()
    }

    @Test
    fun aFullPipeBlocksTheProducerInsteadOfBuffering() {
        val (result, output) = run(
            arrayOf("gen", "lines", "--count", "50000"),
            arrayOf("text", "count"),
            capacityChunks = 2,
        )

        assertThat(result.exitCode).isZero()
        assertThat(output.trim()).isEqualTo("50000")
    }

    @Test
    fun aConsumerThatStopsReadingDoesNotHangTheProducer() {
        val (result, output) = run(
            arrayOf("gen", "lines", "--count", "20000"),
            arrayOf("text", "head"),
            capacityChunks = 2,
        )

        assertThat(output.trim()).isEqualTo("line-0")
        assertThat(result.completedStages).isEqualTo(2)
    }

    @Test
    fun aFailingStageIsReportedByPosition() {
        val (result, _) = run(
            arrayOf("gen", "lines", "--count", "10"),
            arrayOf("text", "boom"),
        )

        assertThat(result.exitCode).isEqualTo(3)
        assertThat(result.failedStage).isEqualTo(2)
        assertThat(result.completedStages).isEqualTo(1)
    }

    @Test
    fun aSingleStageStillStreamsToTheSink() {
        val (result, output) = run(arrayOf("gen", "lines", "--count", "3"))

        assertThat(result.exitCode).isZero()
        assertThat(result.completedStages).isEqualTo(1)
        assertThat(output.lines().filter { it.isNotBlank() })
            .containsExactly("line-0", "line-1", "line-2")
    }

    @Test
    fun systemOutIsRestoredAfterwards() {
        val before = System.out

        run(arrayOf("gen", "lines", "--count", "1"), arrayOf("text", "count"))

        assertThat(System.out).isSameAs(before)
    }
}
