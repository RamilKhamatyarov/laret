package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.generateCompletion
import com.rkhamatyarov.laret.completion.installCompletion
import com.rkhamatyarov.laret.dsl.cli
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

                        try {
                            file.parentFile?.mkdirs()
                            file.writeText(content)
                            println("File created: $path")
                        } catch (e: Exception) {
                            println("Error: Failed to create file: ${e.message}")
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

                        if (file.delete()) {
                            println("File deleted: $path")
                        } else {
                            println("Error: Failed to delete file: $path")
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

                        val entries =
                            (dir.listFiles() ?: emptyArray())
                                .filter { all || !it.isHidden }
                                .filter { maxSize <= 0 || it.length() <= maxSize }
                                .sortedBy { it.name }
                                .map { file ->
                                    mapOf(
                                        "name" to file.name,
                                        "size" to file.length(),
                                        "isDirectory" to file.isDirectory,
                                    )
                                }

                        when {
                            format == "plain" && long -> {
                                println("Directory: $path")
                                entries.forEach { entry ->
                                    val size = if (entry["isDirectory"] == true) "" else "${entry["size"]} B"
                                    val type = if (entry["isDirectory"] == true) "d" else "-"
                                    println("$type $size ${entry["name"]}")
                                }
                            }

                            format == "plain" -> {
                                println("Directory: $path")
                                entries.forEach { entry ->
                                    println(" ${entry["name"]}")
                                }
                            }

                            else -> {
                                println(ctx.render(entries))
                            }
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

                        val success = if (parents) dir.mkdirs() else dir.mkdir()

                        if (success) {
                            println("Directory created: $path")
                        } else {
                            println("Error: Failed to create directory: $path")
                        }
                    }
                }
            }
        }

    app.init()
    app.run(args)
}
