package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp

class ZshCompletionGenerator : CompletionGenerator {
    override fun generate(app: CliApp): String {
        val appName = app.name

        val groupLines = app.groups.joinToString("\n") { group ->
            "    '${group.name}:${group.description}'"
        }

        val commandLines = app.groups.joinToString("\n") { group ->
            """
        ${group.name})
            _values 'commands' \
                ${
                group.commands.joinToString(" \\\n                ") { cmd ->
                    "'${cmd.name}:${cmd.description}'"
                }
            }
            ;;
        """.trimIndent()
        }

        val commandOptions = app.groups
            .flatMap { it.commands }
            .flatMap { it.options }
            .map { "'--${it.long}:${it.description}'" }
            .distinct()

        val globalOptions = listOf(
            "'--help:Show help message'",
            "'-h:Show help message'",
            "'--version:Show version'",
            "'-v:Show version'"
        )

        val allOptions = (commandOptions + globalOptions)
            .distinct()
            .joinToString("\n        ")

        return """
        #compdef $appName
        
        _${appName}() {
            local state line
            _arguments \
                '1: :->group' \
                '2: :->command' \
                '*: :->options'
            
            case ${'$'}state in
                group)
                    _values 'groups' \
        $groupLines
                    ;;
                command)
                    case ${'$'}line[1] in
        $commandLines
                    esac
                    ;;
                options)
                    _values 'options' \
        $allOptions
                    ;;
            esac
        }
        
        _${appName}
    """.trimIndent()
    }
}