# 🚀 Laret

**A Cobra-like CLI framework for Kotlin** - Build beautiful, feature-rich command-line applications with a clean DSL.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## ✨ Features

- 🎯 **Intuitive DSL** - Clean, readable syntax for defining CLI structure
- 🔧 **Command Groups** - Organize related commands logically
- 📝 **Arguments & Options** - Full support for positional arguments and flags
- 🎨 **Colored Output** - Built-in ANSI color support with fallback for unsupported terminals
- 🔄 **Shell Completion** - Generate completion scripts for Bash, Zsh, and PowerShell
- 📦 **Zero Dependencies** - Pure Kotlin, no external dependencies required
- 🧩 **Type-Safe** - Leverage Kotlin's type system for safer CLI apps

## 🚀 Quick Start

```kt
import com.rkhamatyarov.laret.dsl.cli
import com.rkhamatyarov.laret.ui.*
import java.io.File

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
                        println(yellowItalic("⚠️ File already exists: $path (use --force to overwrite)"))
                        return@action
                    }

                    file.writeText(content)
                    println(greenBold("✓ File created: $path"))
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
                        println(redBold("❌ File not found: $path"))
                        return@action
                    }

                    println(cyanBold("📄 Reading: $path"))
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
                        println(redBold("❌ Not a directory: $path"))
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
        }
    }

    app.run(args)
}

```
**Usage:**

$ laret file create hello.txt --content "Hello, World!"
✓ File created: hello.txt

## 📚 Documentation

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
option("f", "force", "Force operation", "", takesValue = false)  // Boolean flag
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
    println(redBold("❌ Error"))
    println(greenBold("✓ Success"))
    println(yellowBold("⚠️ Warning"))
    println(blueBold("ℹ️ Info"))
    println(cyanBold("📄 Data"))
    println(yellowItalic("Note: Something"))
    println(redItalic("Deprecated"))
```
Colors automatically disable on unsupported terminals.

## 🎯 Complete Example

```kt
import com.rkhamatyarov.*
import java.io.File

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
                        println(yellowItalic("⚠️ File already exists: $path (use --force to overwrite)"))
                        return@action
                    }
                    
                    file.writeText(content)
                    println(greenBold("✓ File created: $path"))
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
                        println(redBold("❌ File not found: $path"))
                        return@action
                    }
                    
                    println(cyanBold("📄 Reading: $path"))
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
                        println(redBold("❌ Not a directory: $path"))
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
        }
    }
    
    app.run(args)
}
```

## 🔄 Shell Completion

Laret can generate completion scripts for your CLI application:

### Generate Completion Script

# Bash
$ laret completion bash > /etc/bash_completion.d/laret

# Zsh
$ laret completion zsh > ~/.zsh_completions/_laret

# PowerShell
$ laret completion powershell > laret_completion.ps1

### Auto-Install Completion

# Install for bash
$ laret completion install bash

# Install for zsh
$ laret completion install zsh

# Install for PowerShell
$ laret completion install powershell

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

## 🏗️ Project Structure

```ascii
com.rkhamatyarov.laret/
├── core/
│   ├── CliApp.kt              # Main application class
│   ├── CommandContext.kt      # Execution context
│   └── CommandRunner.kt       # Command execution logic
├── dsl/
│   ├── LaretDsl.kt           # DSL entry point
│   ├── CliBuilder.kt         # App builder
│   ├── GroupBuilder.kt       # Group builder
│   └── CommandBuilder.kt     # Command builder
├── model/
│   ├── CommandGroup.kt       # Group model
│   ├── Command.kt            # Command model
│   ├── Argument.kt           # Argument model
│   └── Option.kt             # Option model
├── completion/
│   ├── CompletionGenerator.kt          # Base interface
│   ├── BashCompletionGenerator.kt      # Bash implementation
│   ├── ZshCompletionGenerator.kt       # Zsh implementation
│   ├── PowerShellCompletionGenerator.kt # PowerShell implementation
│   └── CompletionExtensions.kt         # Extension functions
└── ui/
    ├── Colors.kt             # ANSI color codes
    ├── ColorHelpers.kt       # Color helper functions
    └── HelpFormatter.kt      # Help text formatting
```

## 🎨 Design Philosophy

Laret is inspired by [Cobra](https://github.com/spf13/cobra) (Go) and aims to bring similar simplicity and power to Kotlin CLI applications:

1. **Declarative DSL** - Express CLI structure naturally
2. **Type Safety** - Leverage Kotlin's type system
3. **Zero Dependencies** - Keep it lightweight
4. **Beautiful Output** - Colors and formatting out of the box
5. **Developer Experience** - Make CLI building enjoyable

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📦 Installation

### Gradle (Kotlin DSL)
```kts
dependencies {
    implementation("com.rkhamatyarov:laret:1.0.0")
}
```

### Gradle (Groovy)

```yaml
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

## 🏗️ Building Native Image

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

### Native Image Benefits

✅ **Fast Startup** - Millisecond startup time vs JVM seconds  
✅ **Low Memory** - ~10-50MB RAM vs JVM 100-200MB  
✅ **Single Binary** - No JRE required for distribution  
✅ **Instant Response** - Perfect for CLI tools

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

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by [Cobra](https://github.com/spf13/cobra) (Go)
- Built with ❤️ using Kotlin


## 🗺️ Roadmap

- [x] GraalVM Native Image support
- [ ] Plugin system for extensibility
- [ ] Built-in config file support (YAML, TOML, JSON)
- [ ] Interactive prompts and menus
- [ ] Progress bars and spinners
- [ ] Table rendering utilities
- [ ] Command aliases
- [ ] Persistent flag values
- [ ] Middleware/hooks system
- [ ] Man page generation
- [ ] Localization support

---

**Made with ❤️ and Kotlin**