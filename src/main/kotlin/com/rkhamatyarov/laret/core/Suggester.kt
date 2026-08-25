package com.rkhamatyarov.laret.core

import kotlin.math.ceil

/** A candidate name and its edit distance from the mistyped input. */
data class Suggestion(val value: String, val distance: Int)

/**
 * Ranks known names against a mistyped token to produce "did you mean?" hints.
 */
object Suggester {
    private const val MAX_DISTANCE = 2
    const val DEFAULT_LIMIT = 3

    /**
     * The closest [candidates] to [input], within an adaptive distance cap and
     * sorted by ascending distance then alphabetically.
     *
     * The cap is `min(2, ceil(longer / 3))` (at least 1), so short inputs only
     * match near-exact typos while longer words tolerate up to two edits.
     *
     * @param limit maximum number of suggestions to return.
     */
    fun rank(input: String, candidates: Collection<String>, limit: Int = DEFAULT_LIMIT): List<Suggestion> {
        if (input.isEmpty()) return emptyList()
        return candidates
            .distinct()
            .map { Suggestion(it, EditDistance.damerau(input, it)) }
            .filter { it.distance in 1..allowed(input, it.value) }
            .sortedWith(compareBy({ it.distance }, { it.value }))
            .take(limit)
    }

    /**
     * The single candidate exactly one edit from [input], or `null` when there
     * is none or more than one. Used to gate safe interactive auto-correction.
     */
    fun autoFix(input: String, candidates: Collection<String>): String? = candidates
        .distinct().singleOrNull { EditDistance.damerau(input, it) == 1 }

    /** Adaptive edit-distance cap for a given input/candidate pair. */
    private fun allowed(input: String, candidate: String): Int {
        val longer = maxOf(input.length, candidate.length)
        return minOf(MAX_DISTANCE, ceil(longer / 3.0).toInt()).coerceAtLeast(1)
    }
}
