package com.rkhamatyarov.laret.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
