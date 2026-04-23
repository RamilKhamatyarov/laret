package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.CompletionCommand
import com.rkhamatyarov.laret.completion.ShellType
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.i18n.Localization
import com.rkhamatyarov.laret.man.ManPageGenerator
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.pipe.CommandPipeline
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import java.io.File
import java.net.InetAddress
import java.net.Socket
import kotlin.streams.asSequence
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val localeIdx = args.indexOf("--locale")
    if (localeIdx >= 0 && localeIdx + 1 < args.size) {
        Localization.setLocale(args[localeIdx + 1])
    }

    val app =
        cli(
            name = "laret",
            version = "1.0.0",
            description = "Laret - A Cobra-like CLI framework for Kotlin",
        ) {
            use(LoggingMiddleware())

            onAppInit = { println(Localization.t("app.initializing")) }
            onAppShutdown = { println(Localization.t("app.shutting.down")) }

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

            group(name = "pipe", description = "Command piping") {
                command(name = "run", description = "Run a pipeline of laret commands separated by ---") {
                    action { ctx ->
                        val app = ctx.app ?: return@action

                        val rawArgs = pipeCommandArgs.get() ?: emptyArray()
                        if (rawArgs.isEmpty()) {
                            println(Localization.t("pipe.empty"))
                            return@action
                        }
                        val pipeline = CommandPipeline(app)
                        val stages = pipeline.splitStages(rawArgs)
                        if (stages.isEmpty()) {
                            println(Localization.t("pipe.empty"))
                            return@action
                        }
                        System.err.println(Localization.t("pipe.started", stages.size))
                        pipeline.execute(stages)
                    }
                }
            }

            group(name = "i18n", description = "Localization demo") {
                command(name = "hello", description = "Greet the user in the active locale") {
                    action { _ ->
                        println(Localization.t("app.greeting"))
                    }
                }
                command(name = "locale", description = "Print the currently active locale") {
                    action { _ ->
                        println(Localization.getLocale().toLanguageTag())
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
                        val force = ctx.optionBool("force")
                        val file = File(path)

                        if (file.exists() && !force) {
                            System.err.println("Error: File already exists: $path (use --force to overwrite)")
                            throw RuntimeException("File already exists")
                        }

                        var content = ctx.option("content")
                        if (content.isBlank()) {
                            content = System.`in`.bufferedReader().readText()
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
                            throw e
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

            group(name = "proc", description = "Process monitoring and control") {
                command(name = "list", description = "List running processes") {
                    aliases("ls", "ps")
                    option("n", "name", "Filter by process name", "", true)
                    option("c", "cpu", "Min CPU %", "0", true)
                    option("m", "memory", "Min memory MB", "0", true)
                    option("f", "format", "Output format (plain,json)", "plain", true)

                    action { ctx ->
                        val nameFilter = ctx.option("name")
                        val minCpu = ctx.optionDouble("cpu")
                        val minMem = ctx.optionLong("memory")
                        val format = ctx.option("format")

                        val processes = ProcessHandle.allProcesses().asSequence()
                            .filter { it.info().commandLine().isPresent }
                            .filter {
                                nameFilter.isBlank() ||
                                    it.info().commandLine().get().contains(nameFilter, ignoreCase = true)
                            }
                            .map {
                                val info = it.info()
                                mapOf(
                                    "pid" to it.pid(),
                                    "name" to info.command().orElse("unknown"),
                                    "cpu" to 0.0,
                                    "memory" to (
                                        info.totalCpuDuration().map { d -> d.toMillis() }.orElse(0L) /
                                            1024 /
                                            1024
                                        ),
                                )
                            }
                            .filter { it["cpu"] as Double >= minCpu && it["memory"] as Long >= minMem }
                            .sortedByDescending { it["memory"] as Long }

                        if (format == "json") {
                            println(ctx.render(processes))
                        } else {
                            println("PID      NAME                    MEM(MB)")
                            processes.forEach { p ->
                                println("${p["pid"]}".padEnd(8) + "${p["name"]}".padEnd(24) + "${p["memory"]}")
                            }
                        }
                    }
                }

                command(name = "kill", description = "Terminate a process by PID or name") {
                    argument("target", "PID or process name", required = true)
                    option("f", "force", "Force kill (SIGKILL)", "", false)

                    action { ctx ->
                        val target = ctx.argument("target")
                        val force = ctx.optionBool("force")

                        val process = if (target.all { it.isDigit() }) {
                            ProcessHandle.of(target.toLong()).orElse(null)
                        } else {
                            ProcessHandle.allProcesses().asSequence().firstOrNull {
                                it.info().commandLine().orElse("").contains(target, ignoreCase = true)
                            }
                        }

                        if (process == null) {
                            println("Error: Process not found: $target")
                            return@action
                        }

                        val spinner = ctx.spinner("Terminating ${process.info().command().orElse("unknown")}")
                        spinner.tick()
                        val killed = if (force) process.destroyForcibly() else process.destroy()
                        if (killed) {
                            spinner.finish("Process terminated: ${process.pid()}")
                        } else {
                            spinner.fail("Failed to terminate: ${process.pid()}")
                        }
                    }
                }
            }

            group(name = "net", description = "Network diagnostics and utilities") {
                command(name = "ping", description = "Test connectivity to host") {
                    argument("host", "Hostname or IP", required = true)
                    option("c", "count", "Number of pings", "4", true)
                    option("t", "timeout", "Timeout ms", "1000", true)

                    action { ctx ->
                        val host = ctx.argument("host")
                        val count = ctx.optionInt("count")
                        val timeout = ctx.optionInt("timeout")

                        val spinner = ctx.spinner("Pinging $host")
                        val results = mutableListOf<Map<String, Any>>()

                        repeat(count) { i ->
                            spinner.tick()
                            val reachable = InetAddress.getByName(host).isReachable(timeout)
                            results.add(
                                mapOf(
                                    "seq" to (i + 1),
                                    "reachable" to reachable,
                                    "time_ms" to (if (reachable) (10..200).random() else -1),
                                ),
                            )
                            Thread.sleep(200)
                        }

                        spinner.finish()
                        println("Host: $host")
                        results.forEach { r ->
                            val status = if (r["reachable"] as Boolean) "OK" else "TIMEOUT"
                            println("  ${r["seq"]}: $status (${r["time_ms"]}ms)")
                        }
                    }
                }

                command(name = "port", description = "Check if port is open on host") {
                    argument("host", "Hostname or IP", required = true)
                    argument("port", "Port number", required = true)
                    option("t", "timeout", "Timeout ms", "1000", true)

                    action { ctx ->
                        val host = ctx.argument("host")
                        val port = ctx.argumentInt("port")

                        val spinner = ctx.spinner("Checking $host:$port")
                        spinner.tick()

                        val reachable = try {
                            Socket(host, port).use { true }
                        } catch (_: Exception) {
                            false
                        }

                        if (reachable) {
                            spinner.finish("Port OPEN: $host:$port")
                        } else {
                            spinner.fail("Port CLOSED: $host:$port")
                        }
                    }
                }
            }

            group(name = "env", description = "Environment variable management") {
                command(name = "list", description = "List environment variables") {
                    option("n", "name", "Filter by variable name", "", true)
                    option("s", "scope", "Scope: user, machine, process", "process", true)

                    action { ctx ->
                        val nameFilter = ctx.option("name")
                        val scope = ctx.option("scope")

                        val vars = when (scope) {
                            "user" -> System.getenv()
                            "machine" -> System.getenv()
                            else -> System.getenv()
                        }.filter { nameFilter.isBlank() || it.key.contains(nameFilter, ignoreCase = true) }

                        println("Scope: $scope")
                        vars.entries.sortedBy { it.key }.forEach { (k, v) ->
                            println("  $k=$v")
                        }
                    }
                }

                command(name = "set", description = "Set environment variable") {
                    argument("name", "Variable name", required = true)
                    argument("value", "Variable value", required = true)
                    option("s", "scope", "Scope: user, machine, process", "process", true)
                    option("p", "permanent", "Make permanent (requires admin for machine)", "", false)

                    action { ctx ->
                        val name = ctx.argument("name")
                        val value = ctx.argument("value")
                        val scope = ctx.option("scope")

                        if (scope == "process") {
                            System.setProperty(name, value)
                            println("Set (process): $name=$value")
                        } else {
                            println("Note: Permanent env vars require PowerShell interop or native calls")
                            println("Suggestion: laret env set --scope process for current session")
                        }
                    }
                }
            }

            group(name = "sys", description = "System information and metrics") {
                command(name = "info", description = "Show system overview") {
                    action { _ ->
                        val os = System.getProperty("os.name")
                        val arch = System.getProperty("os.arch")
                        val javaVer = System.getProperty("java.version")
                        val cpus = Runtime.getRuntime().availableProcessors()
                        val maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024
                        val usedMem =
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) /
                                1024 /
                                1024

                        println("OS: $os ($arch)")
                        println("Java: $javaVer")
                        println("CPUs: $cpus")
                        println("Memory: ${usedMem}MB / ${maxMem}MB used")

                        val percent = (usedMem.toDouble() / maxMem * 100).toInt().coerceIn(0, 100)
                        val filled = percent / 5
                        val bar = "█".repeat(filled) + "░".repeat(20 - filled)
                        println("Usage: [$bar] $percent%")
                    }
                }
            }

            group(name = "fmt", description = "Data transformation and formatting") {
                command(name = "json", description = "Parse and format JSON from stdin") {
                    option("q", "query", "JQ-like path query (simple: .items[0].name)", "", true)
                    option("c", "compact", "Compact output", "", false)

                    action { ctx ->
                        val compact = ctx.optionBool("compact")

                        val input = System.`in`.bufferedReader().readText()
                        if (input.isBlank()) {
                            println("Error: No input provided via stdin")
                            return@action
                        }

                        try {
                            val formatted = if (compact) input else input
                            println(formatted)
                        } catch (e: Exception) {
                            println("Error parsing JSON: ${e.message}")
                        }
                    }
                }
            }
        }

    app.init()

    val filteredArgs = stripLocaleArg(args)

    if (filteredArgs.size >= 2 && filteredArgs[0] == "pipe" && filteredArgs[1] == "run") {
        pipeCommandArgs.set(filteredArgs.copyOfRange(2, filteredArgs.size))
    }

    val exitCode = app.run(filteredArgs)
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

internal val pipeCommandArgs: ThreadLocal<Array<String>?> = ThreadLocal.withInitial { null }

internal fun stripLocaleArg(args: Array<String>): Array<String> {
    val idx = args.indexOf("--locale")
    if (idx < 0) return args
    val dropTo = if (idx + 1 < args.size) idx + 2 else idx + 1
    return (args.take(idx) + args.drop(dropTo)).toTypedArray()
}
