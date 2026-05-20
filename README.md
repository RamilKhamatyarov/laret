# Laret

** A Cobra-like CLI framework for Kotlin** - Build beautiful, feature-rich command-line applications with a clean DSL.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Features

- **Intuitive DSL**
- **Command Groups**
- **Arguments & Options**
- **Colored Output**
- **Multiple Output Formats**
- **Shell Completion**
- **Zero Dependencies**
- **Type-Safe**
- **12Factor Configuration**

## Quick Start

```kt

fun main(args: Array<String>) {
    val app = cli(
        name = "laret",
        version = "1.0.0",
        description = "Laret - A Cobra-like CLI framework for Kotlin"
    ) {
        group(
            name = "file",
            description = "File operations"
        ) {
            command(
                name = "create",
                description = "Create a new file"
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
                        println(yellowItalic("File already exists: $path (use --force to overwrite)"))
                        return@action
                    }

                    file.writeText(content)
                    println(greenBold("File created: $path"))
                }
            }

            command(
                name = "read",
                description = "Read file contents"
            ) {
                argument("path", "File path", required = true)

                action { ctx ->
                    val path = ctx.argument("path")
                    val file = File(path)

                    if (!file.exists()) {
                        println(redBold("File not found: $path"))
                        return@action
                    }

                    println(cyanBold("Reading: $path"))
                    println(file.readText())
                }
            }
        }

        group(
            name = "dir",
            description = "Directory operations"
        ) {
            command(
                name = "list",
                description = "List directory contents"
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

                    println(cyanBold("Listing: $path"))
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
        }
    }

    app.run(args)
}

```

**Usage:**

```bash
$ laret file create hello.txt --content "Hello, World!"
File created: hello.txt
```

## Documentation

### Creating a CLI Application

The `cli` function is the entry point for creating your application:

```kt
val app = cli(
    name = "laret",           // App name (used in help text and completions)
    version = "1.0.0",        // Version string
    description = "App description"
) {
    // Define groups and commands here
}
```

### Command Groups

Organize related commands into groups:

```kt
group(
    name = "file",
    description = "File operations"
) {
    // Define commands here
}
```

### Commands

Define executable commands within groups:

```kt
command(
    name = "create",
    description = "Create a new file"
) {
    // Define arguments, options, and actions
}
```

### Arguments

Positional arguments (required by default):

```kt
argument("path", "File path", required = true)
argument("name", "Optional name", required = false, optional = true, default = "default")
```

**Access in action:**

```kt

action { ctx ->
    val path = ctx.argument("path")
}

```

### Options

Named flags with short and long forms:

```kt

option("c", "content", "File content", "", takesValue = true)
option("f", "force", "Force operation", "", takesValue = false)

```

**Access in action:**

```kt

action { ctx ->
    val content = ctx.option("content")
    val force = ctx.optionBool("force")
    val count = ctx.optionInt("count")
}

```

### Actions

Define command behavior:

```kt

action { ctx ->
    // ctx.argument("name") - Get argument
    // ctx.option("flag") - Get option value
    // ctx.app - Access parent application

    println(greenBold("Success!"))
}

```

### Colored Output

Built-in color helpers with automatic terminal detection:

```kt

println(redBold("Error"))
println(greenBold("Success"))
println(yellowBold("Warning"))
println(blueBold("Info"))
println(cyanBold("Data"))
println(yellowItalic("Note: Something"))
println(redItalic("Deprecated"))

```

Colors automatically disable on unsupported terminals.

### Output Formats (JSON/YAML)

Laret supports multiple output formats for structured data. Use the pluggable output strategy system:

```kt

command(
    name = "list",
    description = "List items"
) {
    option("f", "format", "Output format (json, toml, yaml, plain)", "plain", true)

    action { ctx ->
        val format = ctx.option("format")
        val items = listOf(
            mapOf("name" to "file1.txt", "size" to 1024),
            mapOf("name" to "file2.txt", "size" to 2048)
        )

        val formatter: OutputStrategy = when (format) {
            "json" -> JsonOutput
            "toml" -> TomlOutput
            "yaml" -> YamlOutput
            else -> PlainOutput
        }

        println(formatter.render(items))
    }
}

```

**CLI Usage:**

```bash

$ laret list
file1.txt: 1024 B
file2.txt: 2048 B

$ laret list --format json
[
  {"name": "file1.txt", "size": 1024},
  {"name": "file2.txt", "size": 2048}
]

$ laret list --format yaml
- name: file1.txt
  size: 1024
- name: file2.txt
  size: 2048

```

#### Creating Custom Output Formats

Implement the `OutputStrategy` interface:

```kt

object CsvOutput : OutputStrategy {
    override val name = "csv"

    override fun render(data: T): String {
        return csvString
    }
}

```

Then use it in your command:

```kt

val formatter = when (format) {
    "csv" -> CsvOutput
    "json" -> JsonOutput
    else -> PlainOutput
}
println(formatter.render(data))

```

###  Interactive Mode

```bash

$ laret interactive
Laret Interactive Shell. Type 'exit' to quit.
laret> file create test.txt --content "Hello World"
File created: test.txt
laret> file read test.txt
Hello World
laret> dir list --long --format json
[
  {
    "name": ".",
    "size": 4096,
    "isDirectory": true
  },
  {
    "name": "test.txt",
    "size": 12,
    "isDirectory": false
  }
]
laret> exit

```

Keyboard Shortcuts

| Key      | Action                    |
| -------- | ------------------------- |
| ↑/↓      | Previous/next command     |
| Ctrl+R   | Reverse search history    |
| Ctrl+A/E | Start/end of line         |
| Ctrl+U/K | Clear line/backward       |
| Ctrl+W   | Delete word               |
| Tab      | Command/option completion |
| Ctrl+C   | Interrupt current command |
| Ctrl+D   | Exit shell                |

```text

# .laret.yml
output:
  colorized: true
logging:
  level: INFO

```

## Complete Example

```kt

fun main(args: Array<String>) {
    val app = cli(
        name = "laret",
        version = "1.0.0",
        description = "Laret - A Cobra-like CLI framework for Kotlin"
    ) {
        group(
            name = "file",
            description = "File operations"
        ) {
            command(
                name = "create",
                description = "Create a new file"
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
                        println(yellowItalic("File already exists: $path (use --force to overwrite)"))
                        return@action
                    }

                    file.writeText(content)
                    println(greenBold("File created: $path"))
                }
            }

            command(
                name = "read",
                description = "Read file contents"
            ) {
                argument("path", "File path", required = true)

                action { ctx ->
                    val path = ctx.argument("path")
                    val file = File(path)

                    if (!file.exists()) {
                        println(redBold("File not found: $path"))
                        return@action
                    }

                    println(cyanBold("Reading: $path"))
                    println(file.readText())
                }
            }
        }

        group(
            name = "dir",
            description = "Directory operations"
        ) {
            command(
                name = "list",
                description = "List directory contents"
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

                    println(cyanBold("Listing: $path"))
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
        }
    }

    app.run(args)
}

```

## Shell Completion

Laret can generate completion scripts

### Generate Completion Script

```bash

# Bash
$ laret completion bash > /etc/bash_completion.d/laret

# Zsh
$ laret completion zsh > ~/.zsh_completions/_laret

# PowerShell
$ laret completion powershell > laret_completion.ps1

```

### Auto-Install Completion

```bash

# Install for bash
$ laret completion install bash

# Install for zsh
$ laret completion install zsh

# Install for PowerShell
$ laret completion install powershell

```

### Adding Completion to Your App

```kt

group(
    name = "completion",
    description = "Shell completion"
) {
    command(
        name = "bash",
        description = "Generate bash completion script"
    ) {
        action { ctx ->
            if (ctx.app != null) {
                println(ctx.app.generateCompletion("bash"))
            }
        }
    }

    command(
        name = "install",
        description = "Install completion script"
    ) {
        argument("shell", "Shell type (bash, zsh, or powershell)", required = true)
        action { ctx ->
            val shell = ctx.argument("shell")
            if (ctx.app != null) {
                ctx.app.installCompletion(shell)
            }
        }
    }
}

```

## LLM Function Calling

Export your Laret CLI as OpenAI functions or Anthropic tools:

```bash
laret schema export --format openai > functions.json
laret schema export --format anthropic --output tools.json
```

## Command Piping

Laret lets you chain multiple commands together so the output of one becomes the input of the next — similar to Unix pipes.

### Basic usage

Use `pipe run` with the `---` separator:

```bash
# Two stages: list a directory, then write the output to a file
$ laret pipe run dir list /tmp --- file create /tmp/listing.txt
```

### Pipe `|` separator

The `|` token is also recognised as a stage separator. In a shell you must quote it so the shell does not interpret it as a native pipe:

```bash
$ laret pipe run echo print hello '|' upper convert
HELLO
```

Chain four commands in a row:

```bash
$ laret pipe run cmd1 group '|' cmd2 group '|' cmd3 group '|' cmd4 group
```

Mixed separators work in a single call:

```bash
$ laret pipe run echo print abc '|' upper convert --- upper convert
ABC
```

### Explicit stdin substitution with `-`

Place `-` as an argument token to inject the previous stage's output at a specific position:

```bash
$ laret pipe run echo print "hello world" '|' upper convert -
HELLO WORLD
```

Without `-`, the carry is fed into the next stage via `System.in` and your command can read it with `CommandPipeline.captureStdin()`.

### Writing a pipeable command

```kt
command(name = "convert", description = "Uppercase input") {
    argument("text", required = false, optional = true, default = "")
    action { ctx ->
        val input = if (ctx.argument("text").isNotEmpty()) {
            ctx.argument("text")
        } else {
            CommandPipeline.captureStdin()   // reads from the pipe
        }
        print(input.uppercase())
    }
}
```
## 12Factor Configuration

Laret resolves command configuration with predictable Cobra/Viper-style precedence:
defaults < config files < environment variables < CLI flags. Profile-specific files such as
`.laret.dev.yml` or `.laret.prod.toml` are selected with `--profile`, falling back to base
`.laret.yml`, `.laret.toml`, or `.laret.json` files when a profile file is missing.

```bash
LARET_DIR_FORMAT=json laret --profile dev dir list /tmp --format plain
```

Command actions can read resolved values from `ctx.config`, for example
`ctx.config.getString("dir.format")`.

#### Programmatic API

```kt
val pipeline = CommandPipeline(app)

// Split a token array into stages
val stages = pipeline.splitStages(
    arrayOf("echo", "print", "hello", "|", "upper", "convert")
)

// Execute and get the final output
val result = pipeline.execute(stages)  // returns "HELLO"
```

## Project Structure

```

com.rkhamatyarov.laret/
├── core/                          # Framework engine
│   ├── CliApp.kt                  # Main application class
│   ├── CommandContext.kt          # Execution context + registerUndo()
│   ├── CommandRunner.kt           # Command dispatch and middleware chain
│   ├── CommandPipeline.kt         # Stage-based command piping (--- and |)
│   ├── FlagPersistence.kt         # Persistent flag loading from config
│   ├── LaretPlugin.kt             # Plugin interface
│   ├── Localization.kt            # i18n facade (ResourceBundle + persistence)
│   ├── Middleware.kt              # Middleware interface + chain
│   ├── ParallelDispatcher.kt      # Concurrent command execution
│   ├── PluginManager.kt           # Plugin registry and lifecycle
│   └── UndoManager.kt             # Undo/redo stack with file persistence
├── dsl/                           # Builder DSL
│   ├── LaretDsl.kt                # cli {} entry point
│   ├── CliBuilder.kt              # App builder
│   ├── GroupBuilder.kt            # Group builder
│   └── CommandBuilder.kt          # Command builder
├── model/                         # Data classes
│   ├── CommandGroup.kt
│   ├── Command.kt
│   ├── Argument.kt
│   └── Option.kt
├── output/                        # Output strategies
│   ├── OutputStrategy.kt          # Strategy interface
│   ├── JsonOutput.kt
│   ├── YamlOutput.kt
│   ├── TomlOutput.kt
│   ├── PlainOutput.kt
│   └── TableOutput.kt
├── completion/                    # Shell completion + man pages
│   ├── BashCompletionGenerator.kt
│   ├── ZshCompletionGenerator.kt
│   ├── PowerShellCompletionGenerator.kt
│   ├── ManPageGenerator.kt        # Groff man-page generator
│   ├── GroffFormatter.kt
│   └── ManSection.kt
├── config/                        # Config file loading
│   ├── AppConfig.kt
│   ├── ConfigLoader.kt            # YAML/TOML/JSON loader
│   └── ConfigValidator.kt
├── diff/                          # File diff engine
│   ├── DiffEngine.kt              # LCS-based diff
│   ├── UnifiedFormatter.kt
│   ├── PlainFormatter.kt
│   └── JsonDiffFormatter.kt
├── stats/                         # Command metrics
│   ├── StatsCollector.kt          # Singleton collector (~/.laret/stats.json)
│   ├── StatsMiddleware.kt         # Priority -1000 outermost middleware
│   ├── PrometheusFormatter.kt
│   ├── JsonStatsFormatter.kt
│   └── PlainStatsFormatter.kt
├── watch/                         # Filesystem event monitoring
│   ├── DirectoryWatcher.kt
│   ├── WatchEventType.kt
│   └── WatchOptions.kt
├── ui/                            # Terminal UI components
│   ├── Colors.kt
│   ├── ProgressBar.kt
│   ├── Spinner.kt
│   ├── InteractivePrompt.kt
│   └── HelpFormatter.kt
└── example/                       # Demo application
    ├── Main.kt                    # Full feature showcase
    └── LoggingPlugin.kt           # Example middleware/plugin

```

## Design Philosophy

Laret is inspired by [Cobra](https://github.com/spf13/cobra) (Go) and aims to bring similar simplicity and power to Kotlin CLI applications:

1. **Declarative DSL** - Express CLI structure naturally
2. **Type Safety** - Leverage Kotlin's type system
3. **Zero Dependencies** - Keep it lightweight
4. **Beautiful Output** - Colors and formatting out of the box
5. **Flexible Output** - Multiple formats for different use cases
6. **Developer Experience** - Make CLI building enjoyable

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Installation

### Gradle (Kotlin DSL)

```kts

dependencies {
    implementation("com.rkhamatyarov:laret:1.0.0")
}

```

### Gradle (Groovy)

```gradle

dependencies {
    implementation 'com.rkhamatyarov:laret:1.0.0'
}

```

### Maven

```xml

<dependency>
    <groupId>com.rkhamatyarov</groupId>
    <artifactId>laret</artifactId>
    <version>1.0.0</version>
</dependency>

```

## Building Native Image

Laret supports **GraalVM Native Image** compilation for ultra-fast startup times and minimal memory footprint.

### Setup Gradle Native Image Plugin

Add the GraalVM Native Image plugin to your `build.gradle.kts`:

```kts

plugins {
    kotlin("jvm") version "1.9.20"
    id("org.graalvm.buildtools.native") version "0.9.28"
}

graalvmNative {
    binaries {
        create("windows") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
        }

        create("linux") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
        }
    }
}

```

### Build Native Executable

```bash

# Build native image
./gradlew nativeCompile

# The executable will be in build/native/nativeCompile/
./build/native/nativeCompile/laret --help

```

### Example Build Configuration

Complete `build.gradle.kts` for native image:

```kts

plugins {
    kotlin("jvm") version "1.9.20"
    application
    id("org.graalvm.buildtools.native") version "0.9.28"
}

application {
    mainClass.set("com.rkhamatyarov.laret.examples.MainKt")
}

graalvmNative {
    binaries {
        create("windows") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
        }

        create("linux") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
        }
    }
}

```

### Distribution

```bash

# Build optimized native binary
./gradlew nativeCompile

# Distribute single executable
cp build/native/nativeCompile/laret /usr/local/bin/

# Or create installation package
tar -czf laret-linux-x64.tar.gz -C build/native/nativeCompile laret

```

## Docker

```bash

# compile native laret binary file
docker build -f Dockerfile.builder -t laret-builder .

# copy the laret binary file to current directory
docker create --name temp-builder laret-builder
docker cp temp-builder:/build/build/native/nativeCompile/laret ./build/native/nativeCompile/
docker rm temp-builder

```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by [Cobra](https://github.com/spf13/cobra) (Go)
