package io.github.laretframework.example

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.readText

class BenchCommandsTest {

    private fun run(vararg args: String): String {
        val captured = ByteArrayOutputStream()
        val originalOut = System.out
        return try {
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))
            buildLaretApp().runForTest(arrayOf(*args))
            captured.toString(Charsets.UTF_8)
        } finally {
            System.setOut(originalOut)
        }
    }

    private fun fields(line: String): Map<String, String> = line
        .trim()
        .split(" ")
        .drop(1)
        .associate { pair -> pair.substringBefore('=') to pair.substringAfter('=') }

    private fun summary(output: String, prefix: String): Map<String, String> =
        fields(output.lines().first { it.startsWith(prefix) })

    @Test
    fun benchCommandsAreHiddenFromHelp() {
        val help = run("--help")

        assertThat(help).doesNotContain("bench")
    }

    @Test
    fun fanoutAggregatesEveryTask() {
        val result = summary(run("bench", "fanout", "--tasks", "1000", "--jobs", "0"), "fanout")

        assertThat(result["tasks"]).isEqualTo("1000")
        assertThat(result["completed"]).isEqualTo("1000")
        assertThat(result["sum"]).isEqualTo("500500")
    }

    @Test
    fun fanoutAggregatesTheSameWayWhenBounded() {
        val result = summary(run("bench", "fanout", "--tasks", "1000", "--jobs", "4"), "fanout")

        assertThat(result["jobs"]).isEqualTo("4")
        assertThat(result["completed"]).isEqualTo("1000")
        assertThat(result["sum"]).isEqualTo("500500")
    }

    @Test
    fun fanoutHandlesZeroTasks() {
        val result = summary(run("bench", "fanout", "--tasks", "0"), "fanout")

        assertThat(result["completed"]).isEqualTo("0")
        assertThat(result["sum"]).isEqualTo("0")
    }

    @Test
    fun stormCoalescesEveryEventIntoOneRun() {
        val result = summary(
            run("bench", "storm", "--events", "10000", "--window", "50", "--debounce", "150"),
            "storm",
        )

        assertThat(result["events"]).isEqualTo("10000")
        assertThat(result["runs"]).isEqualTo("1")
    }

    @Test
    fun pipelineStreamsAndCountsCorrectly() {
        val result = summary(run("bench", "pipeline", "--lines", "1000", "--pattern", "7"), "pipeline")

        assertThat(result["mode"]).isEqualTo("streaming")
        assertThat(result["stages"]).isEqualTo("3")
        assertThat(result["kept"]).isEqualTo("271")
    }

    @Test
    fun bufferedPipelineAgreesWithTheStreamingOne() {
        val streamed = summary(run("bench", "pipeline", "--lines", "1000", "--pattern", "7"), "pipeline")
        val buffered = summary(run("bench", "pipeline", "--lines", "1000", "--pattern", "7", "--buffered"), "pipeline")

        assertThat(buffered["mode"]).isEqualTo("buffered")
        assertThat(buffered["kept"]).isEqualTo(streamed["kept"])
    }

    @Test
    fun cancelRunsEveryCleanupInReverseRegistrationOrder(@TempDir tmp: Path) {
        val marker = tmp.resolve("cleanup-order.txt")
        val output = run("bench", "cancel", "--workers", "50", "--marker", marker.toString(), "--seconds", "20")

        assertThat(output).contains("cancel workers=50")
        val recorded = marker.readText().trim().lines().map { it.toInt() }
        assertThat(recorded).isEqualTo((49 downTo 0).toList())
    }
}
