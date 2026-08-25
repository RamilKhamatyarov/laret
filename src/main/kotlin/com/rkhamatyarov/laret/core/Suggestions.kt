package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.ui.InteractivePrompt

/**
 * Facade wiring [Suggester] into the CLI: gating, message formatting, the
 * unknown-flag warning, and opt-in interactive correction.
 */
object Suggestions {
    private const val DISABLE_ENV = "LARET_NO_SUGGEST"

    /** Whether suggestions are enabled (disabled when [DISABLE_ENV] is set non-empty). */
    fun enabled(): Boolean = System.getenv(DISABLE_ENV).isNullOrEmpty()

    /**
     * A `Did you mean …` line for a mistyped token, or `null` when suggestions
     * are disabled or nothing is close enough.
     */
    fun didYouMean(input: String, candidates: Collection<String>): String? {
        if (!enabled()) return null
        val ranked = Suggester.rank(input, candidates)
        if (ranked.isEmpty()) return null
        return if (ranked.size == 1) {
            "Did you mean '${ranked.first().value}'?"
        } else {
            "Did you mean one of: ${ranked.joinToString(", ") { it.value }}?"
        }
    }

    /**
     * Warns (never fails) about an unrecognized [flag], appending a suggestion
     * drawn from the command's [optionNames] when one is close enough.
     */
    fun warnUnknownFlag(flag: String, optionNames: Collection<String>) {
        if (!enabled()) return
        val hint = didYouMean(flag, optionNames)
        System.err.println(if (hint != null) "Unknown flag '$flag'. $hint" else "Unknown flag '$flag'.")
    }

    /**
     * For an unknown group/command with `--fix`: when interactive and exactly
     * one candidate is a single edit away, prompt for confirmation and return
     * the correction to run; otherwise return `null` (no prompt, no auto-run).
     */
    fun promptFix(input: String, candidates: Collection<String>): String? {
        if (!enabled() || System.console() == null) return null
        val fix = Suggester.autoFix(input, candidates) ?: return null
        val confirmed = InteractivePrompt(enabled = true).confirm("Did you mean '$fix'?", default = true)
        return if (confirmed) fix else null
    }
}
