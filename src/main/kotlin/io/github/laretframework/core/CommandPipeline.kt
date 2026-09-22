package io.github.laretframework.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream

data class PipelineResult(val output: String, val exitCode: Int, val completedStages: Int, val failedStage: Int?)

class CommandPipeline(private val app: CliApp) {

    private data class StageResult(val output: String, val exitCode: Int)

    /**
     * Split [tokens] into stages wherever a separator token is found.
     * Both `---` and `|` are recognised by default; empty stages are dropped.
     */
    fun splitStages(tokens: Array<String>, separators: Set<String> = DEFAULT_SEPARATORS): List<Array<String>> {
        val stages = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (tok in tokens) {
            if (tok in separators) {
                if (current.isNotEmpty()) stages += current
                current = mutableListOf()
            } else {
                current += tok
            }
        }
        if (current.isNotEmpty()) stages += current
        return stages.map { it.toTypedArray() }
    }

    /** Single-separator overload kept for backward compatibility. */
    fun splitStages(tokens: Array<String>, separator: String): List<Array<String>> =
        splitStages(tokens, setOf(separator))

    fun execute(stages: List<Array<String>>, dryRun: Boolean = false): String = executeResult(stages, dryRun).output

    fun executeResult(stages: List<Array<String>>, dryRun: Boolean = false): PipelineResult {
        require(stages.isNotEmpty()) { "Pipeline must contain at least one stage" }

        if (dryRun) {
            System.err.println(
                "[WARNING] Pipelines in --dry-run mode may behave unexpectedly due to stdout interception.",
            )
        }

        val originalOut = System.out
        val originalIn = System.`in`
        var carry = ""
        var completedStages = 0
        var failedStage: Int? = null
        var exitCode = 0

        try {
            for ((index, rawStage) in stages.withIndex()) {
                val stageArgs = if (index == 0) rawStage else substituteDash(rawStage, carry)
                val result = runStage(stageArgs, carry.takeIf { index > 0 })
                carry = result.output

                if (result.exitCode != 0) {
                    failedStage = index + 1
                    exitCode = result.exitCode
                    break
                }
                completedStages++
            }
        } finally {
            System.setOut(originalOut)
            System.setIn(originalIn)
        }

        originalOut.print(carry)
        return PipelineResult(
            output = carry,
            exitCode = exitCode,
            completedStages = completedStages,
            failedStage = failedStage,
        )
    }

    /**
     * Run every stage at once, connected by bounded pipes.
     *
     * The buffered [executeResult] runs stages strictly in sequence and holds
     * each stage's entire stdout in a string before the next one starts, so a
     * long stream is bounded only by heap. Here each stage runs on its own
     * thread and a full pipe blocks its producer, so memory stays flat however
     * long the stream is.
     *
     * Stages read their input from `System.in` as usual. `-` substitution is
     * not available, because in streaming mode no earlier stage's output has
     * been collected into a value by the time a later stage starts.
     *
     * [PipelineResult.output] is empty: the final stage writes straight through
     * to [sink], which is the point of streaming. Pass a buffer as [sink] to
     * capture it in a test.
     *
     * @param sink where the last stage's output goes; defaults to the real stdout.
     * @param capacityChunks writes a producer may run ahead before it blocks.
     */
    fun executeStreamingResult(
        stages: List<Array<String>>,
        sink: OutputStream? = null,
        capacityChunks: Int = BoundedPipe.DEFAULT_CAPACITY_CHUNKS,
    ): PipelineResult {
        require(stages.isNotEmpty()) { "Pipeline must contain at least one stage" }

        val originalOut = System.out
        val originalIn = System.`in`
        val target = sink ?: originalOut

        val pipes = List(stages.size - 1) { BoundedPipe(capacityChunks) }
        val exitCodes = MutableList(stages.size) { 0 }

        val router = ThreadRoutedPrintStream(originalOut)
        val inputRouter = ThreadRoutedInputStream(originalIn)

        try {
            System.setOut(router)
            System.setIn(inputRouter)

            val threads = stages.mapIndexed { index, stage ->
                Thread({
                    runStreamedStage(index, stage, pipes, target, router, inputRouter, exitCodes)
                }, "laret-stage-${index + 1}")
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
        } finally {
            System.setOut(originalOut)
            System.setIn(originalIn)
        }

        val failedIndex = exitCodes.indexOfFirst { it != 0 }
        return PipelineResult(
            output = "",
            exitCode = if (failedIndex == -1) 0 else exitCodes[failedIndex],
            completedStages = exitCodes.count { it == 0 },
            failedStage = if (failedIndex == -1) null else failedIndex + 1,
        )
    }

    private fun runStreamedStage(
        index: Int,
        stage: Array<String>,
        pipes: List<BoundedPipe>,
        target: OutputStream,
        router: ThreadRoutedPrintStream,
        inputRouter: ThreadRoutedInputStream,
        exitCodes: MutableList<Int>,
    ) {
        val isLast = index == exitCodes.lastIndex
        val out = if (isLast) target else pipes[index].sink
        router.bind(PrintStream(out, true, Charsets.UTF_8))
        if (index > 0) inputRouter.bind(pipes[index - 1].source)

        try {
            exitCodes[index] = app.runForTest(stage)
        } catch (e: Exception) {
            exitCodes[index] = 1
            System.err.println("Stage ${index + 1} failed: ${e.message}")
        } finally {
            router.unbind()
            inputRouter.unbind()
            if (!isLast) pipes[index].sink.close()
            if (index > 0) pipes[index - 1].source.close()
        }
    }

    private fun runStage(args: Array<String>, stdin: String?): StageResult {
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        if (stdin != null) {
            System.setIn(ByteArrayInputStream(stdin.toByteArray(Charsets.UTF_8)))
        }

        val exitCode = app.runForTest(args)
        return StageResult(captured.toString(Charsets.UTF_8), exitCode)
    }

    internal fun substituteDash(args: Array<String>, carry: String): Array<String> =
        args.map { if (it == "-") carry else it }.toTypedArray()

    companion object {
        const val STAGE_SEPARATOR: String = "---"
        const val PIPE_SEPARATOR: String = "|"

        /** Both separators are active by default; quote `|` in shells to pass it as a token. */
        val DEFAULT_SEPARATORS: Set<String> = setOf(STAGE_SEPARATOR, PIPE_SEPARATOR)

        fun captureStdin(input: InputStream = System.`in`): String = input.readBytes().toString(Charsets.UTF_8)
    }
}
