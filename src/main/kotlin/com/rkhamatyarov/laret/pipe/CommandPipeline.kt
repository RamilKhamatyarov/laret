package com.rkhamatyarov.laret.pipe

import com.rkhamatyarov.laret.core.CliApp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream

class CommandPipeline(private val app: CliApp) {

    fun splitStages(tokens: Array<String>, separator: String = "---"): List<Array<String>> {
        val stages = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (tok in tokens) {
            if (tok == separator) {
                if (current.isNotEmpty()) stages += current
                current = mutableListOf()
            } else {
                current += tok
            }
        }
        if (current.isNotEmpty()) stages += current
        return stages.map { it.toTypedArray() }
    }

    fun execute(stages: List<Array<String>>): String {
        require(stages.isNotEmpty()) { "Pipeline must contain at least one stage" }

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

        fun captureStdin(input: InputStream = System.`in`): String = input.readBytes().toString(Charsets.UTF_8)
    }
}
