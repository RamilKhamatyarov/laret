package com.rkhamatyarov.laret.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream

class CommandPipeline(private val app: CliApp) {

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

    fun execute(stages: List<Array<String>>, dryRun: Boolean = false): String {
        require(stages.isNotEmpty()) { "Pipeline must contain at least one stage" }

        if (dryRun) {
            System.err.println(
                "[WARNING] Pipelines in --dry-run mode may behave unexpectedly due to stdout interception.",
            )
        }

        val originalOut = System.out
        val originalIn = System.`in`
        var carry = ""

        try {
            for ((index, rawStage) in stages.withIndex()) {
                val stageArgs = if (index == 0) rawStage else substituteDash(rawStage, carry)

                val captured = ByteArrayOutputStream()
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))
                if (index > 0) {
                    System.setIn(ByteArrayInputStream(carry.toByteArray(Charsets.UTF_8)))
                }

                app.runForTest(stageArgs)
                carry = captured.toString(Charsets.UTF_8)
            }
        } finally {
            System.setOut(originalOut)
            System.setIn(originalIn)
        }

        originalOut.print(carry)
        return carry
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
