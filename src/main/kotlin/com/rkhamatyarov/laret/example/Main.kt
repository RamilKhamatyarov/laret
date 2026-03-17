package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.generateCompletion
import com.rkhamatyarov.laret.completion.installCompletion
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.output.OutputStrategy
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import java.io.File

fun main(args: Array<String>) {
    val app =
        cli(
            name = "laret",
            version = "1.0.0",
            description = "Laret - A Cobra-like CLI framework for Kotlin",
        ) {
            group(
                name = "completion",
                description = "Shell completion",
            ) {
                command(
                    name = "bash",
                    description = "Generate bash completion script",
                ) {
                    action { ctx ->
                        print(ctx.app?.generateCompletion("bash") ?: "")
                    }
                }

                command(
                    name = "zsh",
                    description = "Generate zsh completion script",
                ) {
                    action { ctx ->
                        print(ctx.app?.generateCompletion("zsh") ?: "")
                    }
                }

                command(
                    name = "powershell",
                    description = "Generate PowerShell completion script",
                ) {
                    action { ctx ->
                        print(ctx.app?.generateCompletion("powershell") ?: "")
                    }
                }

                command(
                    name = "install",
                    description = "Install completion script",
                ) {
                    argument("shell", "Shell type (bash, zsh, powershell)", required = true)
                    action { ctx ->
                        val shell = ctx.argument("shell")
                        try {
                            ctx.app?.installCompletion(shell)
                        } catch (e: Exception) {
                            println("Error: ${e.message}")
                        }
                    }
                }

                command(
                    name = "interactive",
                    description = "Start interactive shell",
                ) {
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
                                val line = reader.readLine("laret> ") ?: break
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

            group(
                name = "prompt",
                description = "Interactive prompt commands",
            ) {
                command(
                    name = "text",
                    description = "Ask for text input",
                ) {
                    argument("question", "Prompt text", required = true)
                    option("d", "default", "Default value", "", true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val default = ctx.option("default")
                        val result = ctx.prompt().text(question, default)
                        println(result)
                    }
                }

                command(
                    name = "confirm",
                    description = "Ask a yes/no question",
                ) {
                    argument("question", "Prompt text", required = true)
                    option("d", "default", "Default answer (true/false)", "true", true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val default = ctx.option("default").toBooleanStrictOrNull() ?: true
                        val result = ctx.prompt().confirm(question, default)
                        println(result)
                    }
                }

                command(
                    name = "select",
                    description = "Select one option from a list",
                ) {
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

                command(
                    name = "multiselect",
                    description = "Select multiple options from a list",
                ) {
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

                command(
                    name = "password",
                    description = "Ask for a password",
                ) {
                    argument("question", "Prompt text", required = true)
                    action { ctx ->
                        val question = ctx.argument("question")
                        val result = ctx.prompt().password(question)
                        println(result)
                    }
                }
            }

            group(
                name = "file",
                description = "File operations",
            ) {
                command(
                    name = "create",
                    description = "Create a new file",
                ) {
                    argument("path", "File path", required = true)
                    option("c", "content", "File content", "", true)
                    option("f", "force", "Overwrite if exists", "", false)
                    action { ctx ->
                        val path = ctx.argument("path")
                        val content = ctx.option("content")
                        val force = ctx.optionBool("force")
                        val file = File(path)

                        if (file.exists() && !force) {
                            println("Error: File already exists: $path (use --force to overwrite)")
                            return@action
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

                command(
                    name = "delete",
                    description = "Delete a file",
                ) {
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

                command(
                    name = "read",
                    description = "Read file contents",
                ) {
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

            group(
                name = "dir",
                description = "Directory operations",
            ) {
                command(
                    name = "list",
                    description = "List directory contents",
                ) {
                    argument("path", "Directory path", required = false, optional = true, default = ".")
                    option("l", "long", "Long format", "", false)
                    option("a", "all", "Show hidden files", "", false)
                    option("f", "format", "Output format (plain, json, yaml, toml)", "plain", true)
                    option("m", "max-size", "Max file size in bytes", "0", true)
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
                                    val size = if (entry["isDirectory"] == true) "" else "${entry["size"]} B"
                                    val type = if (entry["isDirectory"] == true) "d" else "-"
                                    println("$type $size ${entry["name"]}")
                                }
                            } else {
                                println("Directory: $path")
                                entries.forEach { entry ->
                                    println(" ${entry["name"]}")
                                }
                            }
                        } else {
                            val strategy = OutputStrategy.byName(format)
                            println(strategy.render(entries))
                        }
                    }
                }

                command(
                    name = "create",
                    description = "Create a new directory",
                ) {
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
    app.run(args)
}
