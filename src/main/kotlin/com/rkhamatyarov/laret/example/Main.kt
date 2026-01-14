package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.generateCompletion
import com.rkhamatyarov.laret.completion.installCompletion
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.output.JsonOutput
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.output.PlainOutput
import com.rkhamatyarov.laret.output.TomlOutput
import com.rkhamatyarov.laret.output.YamlOutput
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger("laret.main")

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
                            log.error("Failed to install completion: {}", e.message)
                            println("Error: ${e.message}")
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
                            log.warn("File already exists: {}", path)
                            println("Error: File already exists: $path (use --force to overwrite)")
                            return@action
                        }

                        try {
                            file.parentFile?.mkdirs()
                            file.writeText(content)
                            log.info("File created successfully: {}", path)
                            println("File created: $path")
                        } catch (e: Exception) {
                            log.error("Failed to create file: {}", path, e)
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
                            log.warn("File not found: {}", path)
                            println("Error: File not found: $path")
                            return@action
                        }

                        if (file.delete()) {
                            log.info("File deleted: {}", path)
                            println("File deleted: $path")
                        } else {
                            log.error("Failed to delete file: {}", path)
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
                            log.warn("File not found: {}", path)
                            println("Error: File not found: $path")
                            return@action
                        }

                        log.info("Reading file: {}", path)
                        val content = file.readText()
                        println(content)
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
                    option("f", "format", "Output format (plain, json, yaml)", "plain", true)

                    action { ctx ->
                        val path = ctx.argument("path")
                        val long = ctx.optionBool("long")
                        val all = ctx.optionBool("all")
                        val format = ctx.option("format")
                        val dir = File(path)

                        if (!dir.isDirectory) {
                            log.warn("Not a directory: {}", path)
                            println("Error: Not a directory: $path")
                            return@action
                        }

                        log.info("Listing directory: {}", path)
                        val entries =
                            (dir.listFiles() ?: emptyArray())
                                .filter { all || !it.isHidden }
                                .sortedBy { it.name }
                                .map { file ->
                                    mapOf(
                                        "name" to file.name,
                                        "size" to file.length(),
                                        "isDirectory" to file.isDirectory,
                                    )
                                }

                        val formatter: OutputStrategy =
                            when (format) {
                                "json" -> JsonOutput
                                "toml" -> TomlOutput
                                "yaml" -> YamlOutput
                                else -> PlainOutput
                            }

                        when {
                            format == "plain" && long -> {
                                println("Directory: $path")
                                entries.forEach { entry ->
                                    val size = if (entry["isDirectory"] == true) "<dir>" else "${entry["size"]} B"
                                    val type = if (entry["isDirectory"] == true) "d" else "-"
                                    println("$type $size ${entry["name"]}")
                                }
                            }

                            format == "plain" -> {
                                println("Directory: $path")
                                entries.forEach { entry ->
                                    println("  ${entry["name"]}")
                                }
                            }

                            long -> {
                                println(formatter.render(entries))
                            }

                            else -> {
                                println(formatter.render(entries))
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
                            log.warn("Directory already exists: {}", path)
                            println("Error: Directory already exists: $path")
                            return@action
                        }

                        log.info("Creating directory: {}", path)
                        val success = if (parents) dir.mkdirs() else dir.mkdir()

                        if (success) {
                            println("Directory created: $path")
                        } else {
                            log.error("Failed to create directory: {}", path)
                            println("Error: Failed to create directory: $path")
                        }
                    }
                }
            }
        }

    app.registerPlugin(LoggingPlugin())
    app.run(args)
}
