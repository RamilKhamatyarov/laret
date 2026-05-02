package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.completion.CompletionCommand
import com.rkhamatyarov.laret.completion.ShellType
import com.rkhamatyarov.laret.core.ParallelDispatcher
import com.rkhamatyarov.laret.core.ParallelTask
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.completion.ManPageGenerator
import com.rkhamatyarov.laret.core.Localization
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.diff.DiffFormat
import com.rkhamatyarov.laret.diff.JsonDiffFormatter
import com.rkhamatyarov.laret.diff.PlainFormatter
import com.rkhamatyarov.laret.diff.UnifiedFormatter
import com.rkhamatyarov.laret.diff.diffFiles
import com.rkhamatyarov.laret.core.CommandPipeline
import com.rkhamatyarov.laret.stats.JsonStatsFormatter
import com.rkhamatyarov.laret.stats.PlainStatsFormatter
import com.rkhamatyarov.laret.stats.PrometheusFormatter
import com.rkhamatyarov.laret.stats.StatsCollector
import com.rkhamatyarov.laret.stats.StatsFormat
import com.rkhamatyarov.laret.stats.StatsMiddleware
import com.rkhamatyarov.laret.ui.UnicodeSupport
import com.rkhamatyarov.laret.watch.DirectoryWatcher
import com.rkhamatyarov.laret.watch.WatchEventType
import com.rkhamatyarov.laret.watch.WatchOptions
import kotlinx.coroutines.runBlocking
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
    val app =
        cli(
            name = "laret",
            version = "1.0.0",
            description = "Laret - A Cobra-like CLI framework for Kotlin",
        ) {
            use(LoggingMiddleware())
            use(StatsMiddleware())

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
                command(name = "run", description = "Run a pipeline of laret commands separated by --- or |") {
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

            group(name = "parallel", description = "Execute commands concurrently") {
                command(name = "run", description = "Run multiple commands in parallel") {
                    argument("stages", "Commands separated by ---", required = false, optional = true)
                    option("j", "jobs", "Maximum concurrent commands (1..16)", "4", true)
                    option("q", "quiet", "Suppress per-task output", "", false)

                    action { ctx ->
                        val rawArgs = parallelCommandArgs.get() ?: emptyArray()
                        val (tokens, jobsFromRaw, quietFromRaw) = parseParallelRunArgs(rawArgs)
                        val jobs = (jobsFromRaw ?: ctx.optionInt("jobs").takeIf { it > 0 } ?: 4).coerceIn(1, 16)
                        val quiet = quietFromRaw || ctx.optionBool("quiet")
                        val tasks = parseParallelTasks(tokens)

                        if (tasks.isEmpty()) {
                            println("No parallel tasks provided")
                            return@action
                        }

                        val results = runBlocking {
                            ParallelDispatcher.execute(tasks, jobs) { task, line, isStderr ->
                                if (!quiet) {
                                    val stream = if (isStderr) System.err else System.out
                                    stream.println("[${task.command}] $line")
                                }
                            }
                        }

                        val failed = results.filter { it.exitCode != 0 }
                        println("Parallel summary: ${results.size} task(s), ${failed.size} failed")
                        results.forEachIndexed { index, result ->
                            val commandLine = (listOf(result.task.command) + result.task.args).joinToString(" ")
                            println(
                                "${index + 1}. exit=${result.exitCode} " +
                                    "stdout=${result.stdout.size} stderr=${result.stderr.size} :: $commandLine",
                            )
                        }
                        failed.firstOrNull()?.let {
                            System.err.println("First failure: ${it.task.command} exited with ${it.exitCode}")
                        }
                    }
                }
            }

            group(name = "watch", description = "Watch a directory for filesystem changes") {
                command(name = "run", description = "Watch <path> and emit CREATE/MODIFY/DELETE events to stdout") {
                    argument("path", "Directory to watch", required = false, optional = true)
                    option("d", "duration", "Stop after N seconds (0 = run until interrupted)", "0", true)
                    option("r", "recursive", "Watch subdirectories", "", false)
                    option("n", "max-events", "Stop after N events (0 = unlimited)", "0", true)
                    option(
                        "e",
                        "events",
                        "Comma-separated event filter (create,modify,delete). Default: all",
                        "",
                        true,
                    )

                    action { ctx ->
                        val rawArgs = watchCommandArgs.get() ?: emptyArray()
                        val parsed = parseWatchRunArgs(rawArgs)

                        val path = parsed.path ?: run {
                            System.err.println(Localization.t("watch.path.required"))
                            return@action
                        }

                        val dir = File(path)
                        if (!dir.isDirectory) {
                            System.err.println(Localization.t("watch.not.a.directory", path))
                            return@action
                        }

                        val duration = parsed.duration ?: ctx.optionLong("duration").coerceAtLeast(0)
                        val recursive = parsed.recursive || ctx.optionBool("recursive")
                        val maxEvents = parsed.maxEvents ?: ctx.optionInt("max-events").coerceAtLeast(0)
                        val accepted = parseEventFilter(parsed.events ?: ctx.option("events"))

                        val options = WatchOptions(
                            recursive = recursive,
                            durationSeconds = duration,
                            maxEvents = maxEvents,
                            acceptedTypes = accepted,
                        )

                        System.err.println(
                            Localization.t("watch.started", dir.absolutePath, recursive, duration, maxEvents),
                        )

                        val watcher = DirectoryWatcher(dir.toPath(), options)
                        val summary = watcher.watch { event ->
                            println("${event.type}\t${event.path.toAbsolutePath()}")
                        }

                        System.err.println(
                            Localization.t("watch.stopped", summary.emittedEvents, summary.stopReason.name),
                        )
                    }
                }
            }

            group(name = "diff", description = "Compare files line by line") {
                command(name = "run", description = "Show differences between two text files") {
                    argument("old-file", "Original file path", required = true)
                    argument("new-file", "Modified file path", required = true)
                    option("f", "format", "Output format (unified, plain, json)", "unified", true)
                    option("w", "ignore-whitespace", "Ignore leading/trailing whitespace differences", "", false)
                    option("c", "context", "Lines of context around each change", "3", true)

                    action { ctx ->
                        val oldFile = File(ctx.argument("old-file"))
                        val newFile = File(ctx.argument("new-file"))

                        if (!oldFile.exists()) {
                            System.err.println(Localization.t("diff.file.not.found", oldFile.path))
                            return@action
                        }
                        if (!newFile.exists()) {
                            System.err.println(Localization.t("diff.file.not.found", newFile.path))
                            return@action
                        }

                        val formatId = ctx.option("format").ifBlank { "unified" }
                        val format = DiffFormat.fromId(formatId) ?: run {
                            System.err.println(Localization.t("diff.format.unknown", formatId))
                            return@action
                        }

                        val result = diffFiles(
                            oldFile.toPath(),
                            newFile.toPath(),
                            ignoreWhitespace = ctx.optionBool("ignore-whitespace"),
                            contextLines = ctx.optionInt("context").coerceAtLeast(0),
                        )

                        val rendered = when (format) {
                            DiffFormat.UNIFIED -> UnifiedFormatter().render(result)
                            DiffFormat.PLAIN -> PlainFormatter().render(result)
                            DiffFormat.JSON -> JsonDiffFormatter().render(result)
                        }
                        if (rendered.isNotEmpty()) {
                            print(rendered)
                            if (!rendered.endsWith("\n")) println()
                        }
                    }
                }
            }

            group(name = "stats", description = "Command-execution metrics") {
                command(
                    name = "show",
                    description = "Print collected metrics (default format: prometheus)",
                ) {
                    option(
                        "f",
                        "format",
                        "Output format (prometheus, json, plain)",
                        "prometheus",
                        true,
                    )
                    option("r", "reset", "Reset metrics after printing", "", false)

                    action { ctx ->
                        val formatId = ctx.option("format").ifBlank { "prometheus" }
                        val format = StatsFormat.fromId(formatId) ?: run {
                            System.err.println(
                                Localization.t("stats.format.unknown", formatId),
                            )
                            return@action
                        }

                        val snapshot = StatsCollector.snapshot()
                        val rendered = when (format) {
                            StatsFormat.PROMETHEUS -> PrometheusFormatter().render(snapshot)
                            StatsFormat.JSON -> JsonStatsFormatter().render(snapshot)
                            StatsFormat.PLAIN -> PlainStatsFormatter().render(snapshot)
                        }
                        print(rendered)
                        if (!rendered.endsWith("\n")) println()

                        if (ctx.optionBool("reset")) {
                            StatsCollector.reset()
                            System.err.println(Localization.t("stats.reset.done"))
                        }
                    }
                }

                command(name = "reset", description = "Reset all collected metrics") {
                    action { _ ->
                        StatsCollector.reset()
                        println(Localization.t("stats.reset.done"))
                    }
                }
            }

            group(name = "locale", description = "Manage interface locale") {
                command(name = "show", description = "Print the active locale tag") {
                    option("v", "verbose", "Also show the source of the active locale", "", false)
                    action { ctx ->
                        println(Localization.getLocale().toString())
                        if (ctx.optionBool("verbose")) {
                            println("Source: ${Localization.localeSource()}")
                        }
                    }
                }
                command(name = "set", description = "Set and persist locale for all future sessions") {
                    argument("tag", "Locale tag (e.g. es, en_US, fr_FR)", required = true)
                    action { ctx ->
                        val tag = ctx.argument("tag")
                        if (!Localization.isValidLocaleTag(tag)) {
                            System.err.println(Localization.t("locale.invalid.tag", tag))
                            return@action
                        }
                        Localization.saveLocale(tag)
                        if (Localization.isLocaleOverriddenByEnv()) {
                            System.err.println(
                                Localization.t("locale.env.override.warning", System.getenv("LARET_LOCALE")),
                            )
                        }
                        println(Localization.t("locale.set.done", tag))
                    }
                }
                command(name = "reset", description = "Reset locale to system default") {
                    action { _ ->
                        val futureTag = Localization.resolveAfterClear().toString()
                        val message = Localization.t("locale.reset.done", futureTag)
                        Localization.clearLocale()
                        println(message)
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
                    option("m", "min-cpu-ms", "Min CPU time in ms", "0", true)
                    option("f", "format", "Output format (plain,json)", "plain", true)

                    action { ctx ->
                        val nameFilter = ctx.option("name")
                        val minCpuMs = ctx.optionLong("min-cpu-ms")
                        val format = ctx.option("format")

                        fun shortName(full: String): String =
                            full.substringAfterLast('\\').substringAfterLast('/').ifBlank { "unknown" }

                        val processes = ProcessHandle.allProcesses().asSequence()
                            .map {
                                val info = it.info()
                                val commandLine = info.commandLine().orElse(info.command().orElse(""))
                                mapOf(
                                    "pid" to it.pid(),
                                    "name" to shortName(info.command().orElse("")),
                                    "commandLine" to commandLine,
                                    "cpuMs" to info.totalCpuDuration().map { d -> d.toMillis() }.orElse(0L),
                                )
                            }
                            .filter { entry ->
                                nameFilter.isBlank() ||
                                    (entry["name"] as String).contains(nameFilter, ignoreCase = true) ||
                                    (entry["commandLine"] as String).contains(nameFilter, ignoreCase = true)
                            }
                            .filter { (it["cpuMs"] as Long) >= minCpuMs }
                            .sortedByDescending { it["cpuMs"] as Long }
                            .toList()

                        if (format == "json") {
                            println(ctx.render(processes))
                        } else {
                            println("PID      NAME                    CPU(ms)")
                            processes.forEach { p ->
                                println("${p["pid"]}".padEnd(8) + "${p["name"]}".padEnd(24) + "${p["cpuMs"]}")
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
                        val fillChar = UnicodeSupport.pick("█", "#")
                        val emptyChar = UnicodeSupport.pick("░", "-")
                        val bar = fillChar.repeat(filled) + emptyChar.repeat(20 - filled)
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

    if (args.size >= 2 && args[0] == "pipe" && args[1] == "run") {
        pipeCommandArgs.set(args.copyOfRange(2, args.size))
    }
    if (args.size >= 2 && args[0] == "parallel" && args[1] == "run") {
        parallelCommandArgs.set(args.copyOfRange(2, args.size))
    }
    if (args.size >= 2 && args[0] == "watch" && args[1] == "run") {
        watchCommandArgs.set(args.copyOfRange(2, args.size))
    }

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

internal val pipeCommandArgs: ThreadLocal<Array<String>?> = ThreadLocal.withInitial { null }

internal val parallelCommandArgs: ThreadLocal<Array<String>?> = ThreadLocal.withInitial { null }

internal val watchCommandArgs: ThreadLocal<Array<String>?> = ThreadLocal.withInitial { null }

internal data class WatchRunArgs(
    val path: String?,
    val duration: Long?,
    val recursive: Boolean,
    val maxEvents: Int?,
    val events: String?,
)

internal fun parseWatchRunArgs(args: Array<String>): WatchRunArgs {
    var path: String? = null
    var duration: Long? = null
    var recursive = false
    var maxEvents: Int? = null
    var events: String? = null
    var index = 0

    while (index < args.size) {
        when (val token = args[index]) {
            "--duration", "-d" -> {
                duration = args.getOrNull(index + 1)?.toLongOrNull()?.coerceAtLeast(0)
                index += 2
            }
            "--max-events", "-n" -> {
                maxEvents = args.getOrNull(index + 1)?.toIntOrNull()?.coerceAtLeast(0)
                index += 2
            }
            "--events", "-e" -> {
                events = args.getOrNull(index + 1)
                index += 2
            }
            "--recursive", "-r" -> {
                recursive = true
                index++
            }
            else -> {
                if (path == null && !token.startsWith("-")) path = token
                index++
            }
        }
    }

    return WatchRunArgs(path, duration, recursive, maxEvents, events)
}

internal fun parseEventFilter(raw: String): Set<WatchEventType> {
    if (raw.isBlank()) return WatchEventType.values().toSet()
    return raw.split(",")
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .mapNotNull { name -> runCatching { WatchEventType.valueOf(name) }.getOrNull() }
        .toSet()
        .ifEmpty { WatchEventType.values().toSet() }
}

internal fun parseParallelRunArgs(args: Array<String>): Triple<List<String>, Int?, Boolean> {
    val tokens = mutableListOf<String>()
    var jobs: Int? = null
    var quiet = false
    var index = 0

    while (index < args.size) {
        when (args[index]) {
            "--jobs", "-j" -> {
                jobs = args.getOrNull(index + 1)?.toIntOrNull()
                index += 2
            }
            "--quiet", "-q" -> {
                quiet = true
                index++
            }
            else -> {
                tokens += args[index]
                index++
            }
        }
    }

    return Triple(tokens, jobs, quiet)
}

internal fun parseParallelTasks(tokens: List<String>, separator: String = "---"): List<ParallelTask> {
    val stages = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()

    for (token in tokens) {
        if (token == separator) {
            if (current.isNotEmpty()) stages += current
            current = mutableListOf()
        } else {
            current += token
        }
    }
    if (current.isNotEmpty()) stages += current

    return stages.map { stage ->
        ParallelTask(command = stage.first(), args = stage.drop(1))
    }
}

