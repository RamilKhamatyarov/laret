package com.rkhamatyarov.laret.completion

/**
 * Immutable snapshot of the command line handed to a [Completer].
 *
 * @param wordToComplete The partial word under the cursor (possibly empty).
 * @param words          Words typed after `group command`, excluding [wordToComplete].
 * @param groupName      Resolved group name, when the cursor is past the group token.
 * @param commandName    Resolved command name, when the cursor is past the command token.
 */
data class CompletionContext(
    val wordToComplete: String,
    val words: List<String> = emptyList(),
    val groupName: String? = null,
    val commandName: String? = null,
)
