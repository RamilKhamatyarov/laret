package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp

/** Base interface for shell completion generators */
interface CompletionGenerator {
    /**
     * Render the completion script for [app].
     *
     * @param dynamic When `true`, emit a script that queries the binary's
     *                hidden `__complete` command at runtime instead of
     *                embedding a static command/flag list.  Static remains
     *                the default.
     */
    fun generate(app: CliApp, dynamic: Boolean = false): String
}
