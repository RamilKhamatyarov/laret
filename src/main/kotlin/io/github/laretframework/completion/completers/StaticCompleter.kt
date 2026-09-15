package io.github.laretframework.completion.completers

import io.github.laretframework.completion.Completer
import io.github.laretframework.completion.CompletionCandidate
import io.github.laretframework.completion.CompletionContext
import io.github.laretframework.completion.CompletionResult

/**
 * Completes from a fixed candidate list, filtered by the typed prefix.
 * Use the map constructor to attach per-value descriptions.
 */
class StaticCompleter(private val candidates: List<CompletionCandidate>) : Completer {

    constructor(vararg values: String) : this(values.map { CompletionCandidate(it) })

    constructor(values: Map<String, String>) : this(values.map { (value, desc) -> CompletionCandidate(value, desc) })

    override fun complete(context: CompletionContext): CompletionResult =
        CompletionResult(candidates.filter { it.value.startsWith(context.wordToComplete) })
}
