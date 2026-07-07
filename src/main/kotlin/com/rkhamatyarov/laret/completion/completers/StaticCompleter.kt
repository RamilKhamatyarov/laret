package com.rkhamatyarov.laret.completion.completers

import com.rkhamatyarov.laret.completion.Completer
import com.rkhamatyarov.laret.completion.CompletionCandidate
import com.rkhamatyarov.laret.completion.CompletionContext
import com.rkhamatyarov.laret.completion.CompletionResult

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
