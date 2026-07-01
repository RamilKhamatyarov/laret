package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.completion.Completer

/**
 * A positional command argument.
 *
 * @param completer Optional dynamic completion source queried by the hidden
 *                  `__complete` command; `null` leaves the shell's default
 *                  (file) completion in charge.
 */
data class Argument(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    val optional: Boolean = false,
    val default: String = "",
    val completer: Completer? = null,
)
