package com.rkhamatyarov.laret.completion

/** One completion candidate: the [value] the shell inserts plus an optional [description]. */
data class CompletionCandidate(val value: String, val description: String = "")

/**
 * The outcome of a completion query: candidates plus a shell [directive]
 * (Cobra-compatible bitmask, see the constants below) that tells the shell
 * how to behave after the candidates are shown.
 */
data class CompletionResult(
    val candidates: List<CompletionCandidate> = emptyList(),
    val directive: Int = NO_FILE_COMP,
) {
    companion object {
        /** Shell may fall back to its own file completion when no candidates match. */
        const val DEFAULT = 0

        /** An error occurred; the shell should ignore the candidates. */
        const val ERROR = 1

        /** The shell should not append a space after the inserted value. */
        const val NO_SPACE = 2

        /** The shell must not fall back to file completion. */
        const val NO_FILE_COMP = 4

        val EMPTY = CompletionResult()

        fun of(vararg values: String): CompletionResult = CompletionResult(values.map { CompletionCandidate(it) })
    }
}

/**
 * Supplies dynamic completion candidates for an argument or option value.
 *
 * Implementations must be fast and side-effect free: the shell invokes the
 * hidden `__complete` command on every TAB press, outside the middleware
 * chain, history, and stats.  Being a `fun interface`, ad-hoc completers can
 * be lambdas in the DSL.  No reflection or dynamic loading is involved, so
 * every implementation is GraalVM native-image friendly.
 */
fun interface Completer {
    fun complete(context: CompletionContext): CompletionResult
}
