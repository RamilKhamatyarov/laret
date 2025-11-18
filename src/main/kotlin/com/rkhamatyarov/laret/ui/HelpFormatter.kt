package com.rkhamatyarov.laret.ui

/**
 * Formats help text for CLI output
 */
object HelpFormatter {
    fun formatCommandList(commands: List<Pair<String, String>>): String {
        return commands.joinToString("\n") { (name, desc) ->
            "  ${name.padEnd(20)} $desc"
        }
    }

    fun formatOptionsList(options: List<Triple<String, String, String>>): String {
        return options.joinToString("\n") { (short, long, desc) ->
            val flags = "-$short, --$long".padEnd(25)
            "  $flags $desc"
        }
    }
}
