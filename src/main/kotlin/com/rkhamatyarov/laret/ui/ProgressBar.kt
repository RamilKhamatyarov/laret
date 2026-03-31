package com.rkhamatyarov.laret.ui

import java.io.PrintStream
import kotlin.math.roundToInt

class ProgressBar(
    val total: Int,
    val width: Int = 40,
    val label: String = "",
    private val out: PrintStream = System.err
) {
    private var current: Int = 0
    private var finished: Boolean = false

    val percent: Int
        get() =
            if (total <= 0) 100 else ((current.toDouble() / total) * 100).roundToInt().coerceIn(0, 100)

    val isFinished: Boolean
        get() = finished

    fun update(value: Int) {
        current = value.coerceIn(0, total)
        render()
    }

    fun increment(by: Int = 1) = update(current + by)

    fun finish() {
        current = total
        finished = true
        render()
        out.println()
    }

    private fun render() {
        val filled = ((current.toDouble() / total) * width).roundToInt().coerceIn(0, width)
        val empty = width - filled
        val bar = "${Colors.GREEN_BOLD}${"█".repeat(filled)}${Colors.RESET}${"░".repeat(empty)}"
        val pct = "$percent%".padStart(4)
        val prefix = if (label.isNotEmpty()) "$label " else ""
        out.print("\r$prefix[$bar] $pct ($current/$total)")
    }
}

class Spinner(val label: String = "", val out: PrintStream = System.err) {
    private val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var frameIndex: Int = 0
    private var finished: Boolean = false

    val isFinished: Boolean
        get() = finished

    fun tick() {
        if (finished) return
        val frame = "${Colors.CYAN_BOLD}${frames[frameIndex % frames.size]}${Colors.RESET}"
        val prefix = if (label.isNotEmpty()) " $label" else ""
        out.print("\r$frame$prefix")
        frameIndex++
    }

    fun finish(message: String = "") {
        finished = true
        val check = "${Colors.GREEN_BOLD}✔${Colors.RESET}"
        val suffix =
            if (message.isNotEmpty()) {
                " $message"
            } else if (label.isNotEmpty()) {
                " $label"
            } else {
                ""
            }
        out.println("\r$check$suffix")
    }

    fun fail(message: String = "") {
        finished = true
        val cross = "${Colors.RED_BOLD}✗${Colors.RESET}"
        val suffix =
            if (message.isNotEmpty()) {
                " $message"
            } else if (label.isNotEmpty()) {
                " $label"
            } else {
                ""
            }
        out.println("\r$cross$suffix")
    }
}
