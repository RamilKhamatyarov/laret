package io.github.laretframework.completion.completers

import io.github.laretframework.completion.Completer
import io.github.laretframework.completion.CompletionCandidate
import io.github.laretframework.completion.CompletionContext
import io.github.laretframework.completion.CompletionResult

/**
 * Completes from an enum's constants, lowercased (e.g. `ShellType.BASH` → `bash`).
 *
 * Callers pass the constants explicitly (`EnumCompleter(ShellType.entries)`),
 * so no reflection is needed — GraalVM native-image safe.
 */
class EnumCompleter(entries: Collection<Enum<*>>) : Completer {

    private val values = entries.map { it.name.lowercase() }

    override fun complete(context: CompletionContext): CompletionResult = CompletionResult(
        values.filter { it.startsWith(context.wordToComplete) }.map { CompletionCandidate(it) },
    )
}
