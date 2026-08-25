package com.rkhamatyarov.laret.core

/**
 * String edit distance used to rank typo suggestions.
 *
 * See the `typo-suggestions` ADR.
 */
object EditDistance {
    /**
     * Optimal String Alignment distance (restricted Damerau-Levenshtein).
     *
     * Insertions, deletions, substitutions, and adjacent transpositions each
     * cost 1, so a swap like `craete` → `create` scores 1 rather than 2.
     * Comparison is case-insensitive.
     *
     * @return the number of single-character edits between [a] and [b].
     */
    fun damerau(a: String, b: String): Int {
        val s = a.lowercase()
        val t = b.lowercase()
        val n = s.length
        val m = t.length
        if (n == 0) return m
        if (m == 0) return n

        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                val deletion = d[i - 1][j] + 1
                val insertion = d[i][j - 1] + 1
                val substitution = d[i - 1][j - 1] + cost
                d[i][j] = minOf(deletion, insertion, substitution)

                if (i > 1 && j > 1 && s[i - 1] == t[j - 2] && s[i - 2] == t[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[n][m]
    }
}
