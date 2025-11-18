package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.generateCompletion
import com.rkhamatyarov.laret.completion.installCompletion
import com.rkhamatyarov.laret.completion.installPowerShellCompletion
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.ui.blueBold
import com.rkhamatyarov.laret.ui.cyanBold
import com.rkhamatyarov.laret.ui.greenBold
import com.rkhamatyarov.laret.ui.redBold
import com.rkhamatyarov.laret.ui.yellowItalic
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
                        if (ctx.app != null) {
                            println(ctx.app.generateCompletion("bash"))
                        } else {
                            println(redBold("laretError: app not available"))
                        }
                    }
                }

                command(
                    name = "zsh",
                    description = "Generate zsh completion script",
                ) {
                    action { ctx ->
                        if (ctx.app != null) {
                            println(ctx.app.generateCompletion("zsh"))
                        } else {
                            println(redBold("laretError: app not available"))
                        }
                    }
                }

                command(
                    name = "powershell",
                    description = "Generate PowerShell completion script",
                ) {
                    action { ctx ->
                        if (ctx.app != null) {
                            println(ctx.app.generateCompletion("powershell"))
                        } else {
                            println(redBold("laretError: app not available"))
                        }
                    }
                }

                command(
                    name = "install",
                    description = "Install completion script",
                ) {
                    argument("shell", "Shell type (bash, zsh, or powershell)", required = true)
                    action { ctx ->
                        val shell = ctx.argument("shell")
                        if (ctx.app != null) {
                            when (shell) {
                                "bash" -> ctx.app.installCompletion("bash")
                                "zsh" -> ctx.app.installCompletion("zsh")
                                "powershell" -> ctx.app.installPowerShellCompletion()
                                else -> println(redBold("laretUnsupported shell: $shell"))
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
                            println(yellowItalic("⚠️  File already exists: $path (use --force to overwrite)"))
                            return@action
                        }

                        file.writeText(content)
                        println(greenBold("File created: $path"))
                    }
                }

                command(
                    name = "delete",
                    description = "Delete a file",
                ) {
                    argument("path", "File path", required = true)
                    option("f", "force", "Force deletion without confirmation", "", false)
                    action { ctx ->
                        val path = ctx.argument("path")
                        val file = File(path)

                        if (!file.exists()) {
                            println(redBold("File not found: $path"))
                            return@action
                        }

                        if (file.delete()) {
                            println(greenBold("File deleted: $path"))
                        } else {
                            println(redBold("Failed to delete file: $path"))
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
                            println(redBold("File not found: $path"))
                            return@action
                        }

                        println(cyanBold("📄 Reading: $path"))
                        println(file.readText())
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
                    action { ctx ->
                        val path = ctx.argument("path").ifEmpty { "." }
                        val long = ctx.optionBool("long")
                        val all = ctx.optionBool("all")
                        val dir = File(path)

                        if (!dir.isDirectory) {
                            println(redBold("Not a directory: $path"))
                            return@action
                        }

                        println(cyanBold("📁 Listing: $path"))

                        val files = dir.listFiles() ?: emptyArray()
                        files.filter { all || !it.isHidden }
                            .sortedBy { it.name }
                            .forEach { file ->
                                if (long) {
                                    val size = if (file.isDirectory) "" else "${file.length()} B"
                                    val type = if (file.isDirectory) blueBold("d") else "-"
                                    println("$type $size ${file.name}")
                                } else {
                                    if (file.isDirectory) {
                                        println(blueBold("${file.name}/"))
                                    } else {
                                        println(file.name)
                                    }
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

                        val success = if (parents) dir.mkdirs() else dir.mkdir()

                        if (success) {
                            println(greenBold("Directory created: $path"))
                        } else {
                            println(redBold("Failed to create directory: $path"))
                        }
                    }
                }
            }
        }

    app.run(args)
}
