package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.model.Option

/**
 * Runtime backend for dynamic shell completion — the hidden `__complete`
 * command, mirroring Cobra's protocol.
 *
 * The shell invokes `laret __complete <words…> <wordToComplete>` on every TAB
 * press; [complete] parses the partial command line **without executing
 * anything** (no middleware, no history, no stats — interception happens
 * before dispatch in [CliApp]) and prints one candidate per line as
 * `value<TAB>description`, terminated by a `:<directive>` line whose bitmask
 * is defined on [CompletionResult].
 *
 * Cursor-position resolution walks the two-level `group command` grammar:
 * no prior words → group names; one → command names (hidden excluded); past
 * the command → flags for a `-` prefix, the matching [Option.completer] after
 * a value-taking flag, else the positional [com.rkhamatyarov.laret.model.Argument.completer].
 */
class CompletionEngine(private val app: CliApp) {

    /** Renders the full wire response for the words following `__complete`. */
    fun complete(words: List<String>): String {
        val result = resolve(words)
        return buildString {
            result.candidates.forEach { candidate ->
                if (candidate.description.isBlank()) {
                    appendLine(candidate.value)
                } else {
                    appendLine("${candidate.value}\t${candidate.description}")
                }
            }
            appendLine(":${result.directive}")
        }
    }

    internal fun resolve(words: List<String>): CompletionResult {
        val wordToComplete = words.lastOrNull() ?: ""
        val prior = if (words.isEmpty()) emptyList() else words.dropLast(1)

        if (prior.firstOrNull() == COMPLETE_COMMAND || wordToComplete == COMPLETE_COMMAND) {
            return CompletionResult.EMPTY
        }

        if (prior.isEmpty()) return groupCandidates(wordToComplete)

        val group = app.groups.find { it.matches(prior[0]) } ?: return CompletionResult.EMPTY
        if (prior.size == 1) return commandCandidates(group, wordToComplete)

        val command = group.commands.find { it.matches(prior[1]) } ?: return CompletionResult.EMPTY
        return commandLevel(group, command, prior.drop(2), wordToComplete)
    }

    private fun groupCandidates(prefix: String): CompletionResult = CompletionResult(
        app.groups
            .filter { it.name.startsWith(prefix) }
            .map { CompletionCandidate(it.name, it.description) },
    )

    private fun commandCandidates(group: CommandGroup, prefix: String): CompletionResult = CompletionResult(
        group.commands
            .filter { !it.hidden && it.name.startsWith(prefix) }
            .map { CompletionCandidate(it.name, it.description) },
    )

    private fun commandLevel(
        group: CommandGroup,
        command: Command,
        cmdWords: List<String>,
        wordToComplete: String,
    ): CompletionResult {
        val context = CompletionContext(wordToComplete, cmdWords, group.name, command.name)

        if (wordToComplete.startsWith("-")) return flagCandidates(command, wordToComplete)

        val previous = cmdWords.lastOrNull()
        if (previous != null && previous.startsWith("-") && previous != DRY_RUN_FLAG) {
            val option = findOption(command, previous)
            if (option != null && option.takesValue) {
                return option.completer?.complete(context)
                    ?: CompletionResult(emptyList(), CompletionResult.DEFAULT)
            }
        }

        val argument = command.arguments.getOrNull(positionalIndex(command, cmdWords))
            ?: return CompletionResult.EMPTY
        return argument.completer?.complete(context)
            ?: CompletionResult(emptyList(), CompletionResult.DEFAULT)
    }

    private fun flagCandidates(command: Command, prefix: String): CompletionResult {
        val flags = command.options.map { CompletionCandidate("--${it.long}", it.description) } +
            CompletionCandidate(DRY_RUN_FLAG, "Preview side-effects without applying them")
        return CompletionResult(flags.filter { it.value.startsWith(prefix) })
    }

    /** Index of the positional argument under the cursor, skipping flags and their values. */
    private fun positionalIndex(command: Command, cmdWords: List<String>): Int {
        var positionals = 0
        var i = 0
        while (i < cmdWords.size) {
            val token = cmdWords[i]
            if (token.startsWith("-")) {
                val option = findOption(command, token)
                i += if (option != null && option.takesValue) 2 else 1
            } else {
                positionals++
                i++
            }
        }
        return positionals
    }

    private fun findOption(command: Command, token: String): Option? =
        command.options.find { "--${it.long}" == token || "-${it.short}" == token }

    companion object {
        /** The hidden command name shells use to request completions. */
        const val COMPLETE_COMMAND = "__complete"

        private const val DRY_RUN_FLAG = "--dry-run"
    }
}
