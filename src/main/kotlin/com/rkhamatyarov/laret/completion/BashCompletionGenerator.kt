package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.core.CliApp

class BashCompletionGenerator : CompletionGenerator {
    override fun generate(app: CliApp): String {
        val appName = app.name
        val groupNames = app.groups.joinToString(" ") { it.name }

        val groupCommands = app.groups.joinToString("\n") { group ->
            "    ${group.name}) commands=\"${group.commands.joinToString(" ") { it.name }}\" ;;"
        }

        val allOptions = app.groups.flatMap { it.commands }.flatMap { it.options }.map { "--${it.long}" }.distinct()
            .joinToString(" ")

        return """
            #!/bin/bash
            # Bash completion for $appName
            
            _${appName}_complete() {
                local cur prev words cword
                COMPREPLY=()
                cur="${'$'}{COMP_WORDS[COMP_CWORD]}"
                prev="${'$'}{COMP_WORDS[COMP_CWORD-1]}"
                words=("${'$'}{COMP_WORDS[@]}")
                cword=${'$'}COMP_CWORD
                
                local groups="$groupNames"
                local commands=""
                
                case "${'$'}{words[1]}" in
            $groupCommands
                esac
                
                case ${'$'}cword in
                    1)
                        COMPREPLY=( ${'$'}(compgen -W "${'$'}groups" -- ${'$'}cur) )
                        ;;
                    2)
                        COMPREPLY=( ${'$'}(compgen -W "${'$'}commands" -- ${'$'}cur) )
                        ;;
                    *)
                        COMPREPLY=( ${'$'}(compgen -W "$allOptions --help -h --version -v" -- ${'$'}cur) )
                        ;;
                esac
                
                return 0
            }
            
            complete -o bashdefault -o default -o nospace -F _${appName}_complete $appName
        """.trimIndent()
    }
}