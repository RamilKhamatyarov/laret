package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.CompletionCommand
import com.rkhamatyarov.laret.completion.ShellType
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.man.ManPageGenerator
import com.rkhamatyarov.laret.output.OutputStrategy
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val app =
        cli(
            name = "laret",
            version = "1.0.0",
            description = "Laret - A Cobra-like CLI framework for Kotlin",
        ) {
            use(LoggingMiddleware())

            onAppInit = { println("Laret initializing...") }
            onAppShutdown = { println("Laret shutting down...") }

            group(name = "completion", description = "Shell completion") {
                command(name = "bash", description = "Generate bash completion script") {
                    action { ctx ->
                        val command = CompletionCommand(ctx.app!!)
                        print(command.generate(ShellType.BASH))
                    }
                }
                command(name = "zsh", description = "Generate zsh completion script") {
                    action { ctx ->
                        val command = CompletionCommand(ctx.app!!)
                        print(command.generate(ShellType.ZSH))
                    }
                }
                command(name = "powershell", description = "Generate PowerShell completion script") {
                    action { ctx ->
                        val command = CompletionCommand(ctx.app!!)
                        print(command.generate(ShellType.POWERSHELL))
                    }
                }
                command(name = "install", description = "Install completion script") {
                    argument("shell", "Shell type (bash, zsh, powershell)", required = true)
                    action { ctx ->
                        val shellName = ctx.argument("shell")
                        try {
                            val shellType = ShellType.valueOf(shellName.uppercase())
                            val command = CompletionCommand(ctx.app!!)
                            command.generate(shellType, File(getCompletionPath(shellType, ctx.app.name)))
                            println("Completion installed for $shellName")
                        } catch (e: IllegalArgumentException) {
                            println("Error: Unsupported shell '$shellName' $e. Supported: bash, zsh, powershell")
                        }
                    }
                }
                command(name = "man", description = "Generate man page (Groff format)") {
                    option(
                        "o",
                        "output",
                        "Write to file instead of stdout (e.g. /usr/share/man/man1/laret.1)",
                        "",
                        true,
                    )
                    option(
                        "g",
                        "group",
                        "Generate for specific group (e.g. file, dir). Omit for overview page.",
                        "",
                        true,
                    )
                    option("c", "command", "Generate for specific command within --group", "", true)
                    action { ctx ->
                        val outputPath = ctx.option("output")
                        val groupFilter = ctx.option("group")
                        val commandFilter = ctx.option("command")
                        val app = ctx.app ?: return@action
                        val generator = ManPageGenerator()

                        val content = when {
                            groupFilter.isNotBlank() && commandFilter.isNotBlank() -> {
                                val grp = app.groups.find { it.matches(groupFilter) }
                                    ?: run {
                                        println("Error: group '$groupFilter' not found")
                                        return@action
                                    }
                                val cmd = grp.commands.find { it.matches(commandFilter) }
                                    ?: run {
                                        println("Error: command '$commandFilter' not found in group '$groupFilter'")
                                        return@action
                                    }
                                generator.generate(cmd, app.name, app.version, grp.name)
                            }
                            else -> {
                                buildString {
                                    app.groups.forEach { grp ->
                                        grp.commands.forEach { cmd ->
                                            append(generator.generate(cmd, app.name, app.version, grp.name))
                                            append("\n")
                                        }
                                    }
                                }
                            }
                        }

                        if (outputPath.isNotBlank()) {
                            val file = java.io.File(outputPath)
                            file.parentFile?.mkdirs()
                            file.writeText(content)
                            println("Man page written to $outputPath")
                        } else {
                            print(content)
                        }
                    }
                }

                command(name = "interactive", description = "Start interactive shell") {
                    action { ctx ->
                        val terminal = TerminalBuilder.builder().system(true).build()
                        val reader =
                            LineReaderBuilder
                                .builder()
                                .terminal(terminal)
                                .appName("laret")
                                .build()

                        println("Laret Interactive Shell. Type 'exit' to quit.")

                        while (true) {
                            try {
                                val line = reader.readLine("laret > ") ?: break
                                if (line.trim() == "exit" || line.trim() == "quit") break
                                if (line.isBlank()) continue

                                val args = line.trim().split("\\s+".toRegex()).toTypedArray()
                                ctx.app?.run(args)
                            } catch (e: UserInterruptException) {
                                println("\nInterrupted ${e.message}")
                                break
                            } catch (e: EndOfFileException) {
                                println("\nEnd of input ${e.message}")
                                break
                            } catch (e: Exception) {
                                println("Error: ${e.message}")
                            }
                        }
                    }
                }
            }

            group(name = "prompt", description = "Interactive prompt commands") {
                command(name = "text", description = "Ask for text input") {
                    argument("question", "Prompt text", required = true)
                    option("d", "default", "Default value", "", true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val default = ctx.option("default")
                        val result = ctx.prompt().text(question, default)
                        println(result)
                    }
                }

                command(name = "confirm", description = "Ask a yes/no question") {
                    argument("question", "Prompt text", required = true)
                    option("d", "default", "Default answer (true/false)", "true", true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val default = ctx.option("default").toBooleanStrictOrNull() ?: true
                        val result = ctx.prompt().confirm(question, default)
                        println(result)
                    }
                }

                command(name = "select", description = "Select one option from a list") {
                    argument("question", "Prompt text", required = true)
                    option("o", "options", "Comma-separated list of options", "", true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val options =
                            ctx
                                .option("options")
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        if (options.isEmpty()) {
                            println("Error: --options must not be empty")
                            return@action
                        }
                        val result = ctx.prompt().select(question, options)
                        println(result)
                    }
                }

                command(name = "multiselect", description = "Select multiple options from a list") {
                    argument("question", "Prompt text", required = true)
                    option("o", "options", "Comma-separated list of options", "", true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val options =
                            ctx
                                .option("options")
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        if (options.isEmpty()) {
                            println("Error: --options must not be empty")
                            return@action
                        }
                        val results = ctx.prompt().multiSelect(question, options)
                        results.forEach { println(it) }
                    }
                }

                command(name = "password", description = "Ask for a password") {
                    argument("question", "Prompt text", required = true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val result = ctx.prompt().password(question)
                        println(result)
                    }
                }
            }

            group(name = "file", description = "File operations") {
                aliases("f")
                command(name = "create", description = "Create a new file") {
                    aliases("c")
                    argument("path", "File path", required = true)
                    option("c", "content", "File content", "", true)
                    option("f", "force", "Overwrite if exists", "", true, persistent = true)

                    preExecute = { ctx ->
                        val path = ctx.argument("path")
                        require(path.isNotBlank()) { "Path cannot be empty" }
                    }

                    postExecute = { ctx ->
                        System.err.println("File operation completed for ${ctx.argument("path")}")
                    }

                    action { ctx ->
                        val path = ctx.argument("path")
                        val content = ctx.option("content")
                        val force = ctx.optionBool("force")
                        val file = File(path)

                        if (file.exists() && !force) {
                            System.err.println("Error: File already exists: $path (use --force to overwrite)")
                            throw RuntimeException("File already exists")
                        }

                        val spinner = ctx.spinner("Creating $path")
                        try {
                            spinner.tick()
                            file.parentFile?.mkdirs()
                            spinner.tick()
                            file.writeText(content)
                            spinner.finish("File created: $path")
                        } catch (e: Exception) {
                            spinner.fail("Failed to create file: ${e.message}")
                        }
                    }
                }

                command(name = "delete", description = "Delete a file") {
                    aliases("rm")
                    argument("path", "File path", required = true)
                    action { ctx ->
                        val path = ctx.argument("path")
                        val file = File(path)

                        if (!file.exists()) {
                            println("Error: File not found: $path")
                            return@action
                        }

                        val spinner = ctx.spinner("Deleting $path")
                        spinner.tick()
                        if (file.delete()) {
                            spinner.finish("File deleted: $path")
                        } else {
                            spinner.fail("Failed to delete file: $path")
                        }
                    }
                }

                command(name = "read", description = "Read file contents") {
                    argument("path", "File path", required = true)
                    action { ctx ->
                        val path = ctx.argument("path")
                        val file = File(path)

                        if (!file.exists()) {
                            println("Error: File not found: $path")
                            return@action
                        }

                        val content = file.readText()
                        println(ctx.render(content))
                    }
                }
            }

            group(name = "dir", description = "Directory operations") {
                aliases("d")
                command(name = "list", description = "List directory contents") {
                    aliases("ls")
                    argument("path", "Directory path", required = false, optional = true, default = ".")
                    option("l", "long", "Long format", "", false)
                    option("a", "all", "Show hidden files", "", false)
                    option("f", "format", "Output format (plain, json, yaml, toml)", "plain", true, persistent = true)
                    option("m", "max-size", "Max file size in bytes", "0", true)

                    postExecute = { ctx ->
                        val path = ctx.argument("path")
                        val dir = File(path)
                        val count = dir.listFiles()?.size ?: 0
                        println("listed $count entries in $path")
                    }

                    action { ctx ->
                        val path = ctx.argument("path")
                        val long = ctx.optionBool("long")
                        val all = ctx.optionBool("all")
                        val format = ctx.option("format")
                        val maxSize = ctx.optionInt("max-size")
                        val dir = File(path)

                        if (!dir.isDirectory) {
                            println("Error: Not a directory: $path")
                            return@action
                        }

                        val files =
                            (dir.listFiles() ?: emptyArray())
                                .filter { all || !it.isHidden }
                                .filter { maxSize <= 0 || it.length() <= maxSize }
                                .sortedBy { it.name }

                        val bar = ctx.progressBar(total = files.size, label = "Scanning")
                        val entries =
                            files.map { file ->
                                bar.increment()
                                mapOf(
                                    "name" to file.name,
                                    "size" to file.length(),
                                    "isDirectory" to file.isDirectory,
                                )
                            }
                        bar.finish()

                        if (format == "plain") {
                            if (long) {
                                println("Directory: $path")
                                entries.forEach { entry ->
                                    val size = if (entry["isDirectory"] == true) " " else "${entry["size"]} B"
                                    val type = if (entry["isDirectory"] == true) "d" else "-"
                                    println("$type $size ${entry["name"]} ")
                                }
                            } else {
                                println("Directory: $path")
                                entries.forEach { entry -> println(" ${entry["name"]} ") }
                            }
                        } else {
                            val strategy = OutputStrategy.byName(format)
                            println(strategy.render(entries))
                        }
                    }
                }

                command(name = "create", description = "Create a new directory") {
                    argument("path", "Directory path", required = true)
                    option("p", "parents", "Create parent directories", "", false)
                    action { ctx ->
                        val path = ctx.argument("path")
                        val parents = ctx.optionBool("parents")
                        val dir = File(path)

                        if (dir.exists()) {
                            println("Error: Directory already exists: $path")
                            return@action
                        }

                        val spinner = ctx.spinner("Creating directory $path")
                        spinner.tick()
                        val success = if (parents) dir.mkdirs() else dir.mkdir()

                        if (success) {
                            spinner.finish("Directory created: $path")
                        } else {
                            spinner.fail("Failed to create directory: $path")
                        }
                    }
                }
            }
        }

    app.init()
    val exitCode = app.run(args)
    exitProcess(exitCode)
}

fun getCompletionPath(shellType: ShellType, appName: String): String {
    val homeDir = System.getProperty("user.home")
    return when (shellType) {
        ShellType.BASH -> "$homeDir/.bash_completion.d/$appName"
        ShellType.ZSH -> "$homeDir/.zsh_completions/_$appName"
        ShellType.POWERSHELL -> {
            val profilePath = System.getenv("PROFILE")
            val profileDir =
                if (profilePath != null) {
                    File(profilePath).parentFile?.absolutePath
                        ?: "$homeDir/Documents/PowerShell"
                } else {
                    "$homeDir/Documents/PowerShell"
                }
            "$profileDir/${appName}_completion.ps1"
        }
    }
}
