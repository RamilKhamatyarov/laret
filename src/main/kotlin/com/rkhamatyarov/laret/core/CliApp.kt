package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.ui.redBold
import java.awt.SystemColor

/**
 * Represents a complete CLI application
 */
data class CliApp(
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val groups: List<CommandGroup> = emptyList()
) {
    fun run(args: Array<String>) {
        when {
            args.isEmpty() -> showHelp()
            args[0] == "--help" || args[0] == "-h" -> showHelp()
            args[0] == "--version" || args[0] == "-v" -> println("$name version $version")
            else -> executeCommand(args)
        }
    }

    private fun executeCommand(args: Array<String>) {
        val groupName = args.getOrNull(0) ?: return

        SystemColor.text
        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = groups.find { it.name == groupName }
            if (group != null) {
                group.showHelp()
                return
            }
        }

        val commandName = args.getOrNull(1) ?: return
        val cmdArgs = args.drop(2)

        val group = groups.find { it.name == groupName }
            ?: run {
                println(redBold("Group not found: $groupName"))
                showHelp()
                return
            }

        val command = group.commands.find { it.name == commandName }
            ?: run {
                println(redBold("Command not found: $commandName"))
                group.showHelp()
                return
            }

        command.execute(cmdArgs.toTypedArray(), this)
    }


     fun showHelp() {
        println(
            """
        ========================================
        laret  $name v$version
        Laret - A Cobra-like CLI for Kotlin
        ========================================
        
        USAGE:
          laret [COMMAND] [OPTIONS]
        
        COMMANDS:
          file                 File operations
            create             Create a new file
            delete             Delete a file
            read               Read file contents
            
          dir                  Directory operations
            list               List directory contents
            create             Create a new directory
        
        GLOBAL OPTIONS:
          -h, --help          Show this help message
          --version           Show version
        
        EXAMPLES:
          laret file create /tmp/test.txt --content "hello"
          laret dir list . --long --all
          laret file read /tmp/test.txt
        
        For more information on a command, use:
          laret [COMMAND] --help
    """.trimIndent()
        )
    }
}