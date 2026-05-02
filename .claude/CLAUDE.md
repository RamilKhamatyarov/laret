# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build -x test          # Compile without tests
./gradlew test                   # Run all tests
./gradlew test --tests "com.rkhamatyarov.laret.diff.*"   # Run one test class/package
./gradlew checkAll               # Spotless + ktlint + PMD + tests
./gradlew shadowJar              # Build fat JAR → build/libs/laret-fat.jar
./gradlew nativeCompile          # Build GraalVM native binary
```

Linting runs automatically on build. Fix formatting with:
```bash
./gradlew spotlessApply ktlintFormat
```

## Architecture

Laret is a **Kotlin DSL framework for building CLI apps**. `example/Main.kt` is the demo app that exercises the full framework.

### Execution flow

```
cli { ... }           ← DSL builds CliApp (dsl/ package)
app.run(args)         ← CommandRunner resolves group+command, builds middleware chain
MiddlewareChain       ← executes middlewares sorted by priority (lower int = outermost)
command.action(ctx)   ← final handler receives CommandContext
```

### Core packages

| Package | Responsibility |
|---|---|
| `core` | `CliApp`, `CommandRunner`, `CommandContext`, `Middleware` interface + chain, `FlagPersistence`, `ParallelDispatcher` |
| `dsl` | `cli {}` entry point, `CliBuilder`, `GroupBuilder`, `CommandBuilder` |
| `model` | `Command`, `CommandGroup`, `Argument`, `Option` data classes |
| `output` | `OutputStrategy` interface + JSON/YAML/TOML/Plain/Table implementations |
| `ui` | ANSI colors, `ProgressBar`, `Spinner`, `InteractivePrompt` |
| `completion` | Bash/Zsh/PowerShell completion generators |
| `config` | YAML/TOML/JSON config loading, model, validation |
| `i18n` | `Localization.t(key, ...args)` — ResourceBundle facade |
| `man` | Groff man-page generator |
| `pipe` | `CommandPipeline` (pipe one command's output into another) |
| `watch` | `DirectoryWatcher` (file-system event monitoring) |
| `stats` | `StatsCollector` (persist metrics to `~/.laret/stats.json`), `StatsMiddleware` (priority -1000, outermost), formatters |
| `diff` | LCS diff engine, unified/plain/JSON formatters |
| `example` | Demo `Main.kt`, `LoggingMiddleware`, `LoggingPlugin` |

### Adding a new command group

1. Implement logic in a new package (e.g., `myfeature/MyEngine.kt`).
2. Wire it in `Main.kt`:
```kotlin
group(name = "myfeature", description = "...") {
    command(name = "run", description = "...") {
        argument("input", "Input path", required = true)
        option("f", "format", "Output format", "plain", true)
        action { ctx ->
            val result = doWork(ctx.argument("input"))
            println(result)
        }
    }
}
```
3. Add i18n keys to `src/main/resources/i18n/messages.properties` (and `messages_es.properties`). Use `{0}`, `{1}` placeholders.
4. Register middleware via `use(MyMiddleware())` on the `CliBuilder` if needed.

### Middleware

`priority: Int` — lower value = wraps more of the chain. Default is `0`. `StatsMiddleware` uses `-1000` to be the outermost timer. `MiddlewareScope` is `GLOBAL`, `GROUP`, or `COMMAND`.

```kotlin
class MyMiddleware : Middleware {
    override val priority = 10
    override val scope = MiddlewareScope.GLOBAL
    override suspend fun handle(ctx: CommandContext, next: suspend () -> Unit) {
        // before
        next()
        // after
    }
}
```

### Testing

Test isolation for `StatsCollector` (global object):
```kotlin
@BeforeEach fun setUp() = StatsCollector.configureForTest(tempDir.resolve("stats.json"))
@AfterEach  fun tearDown() = StatsCollector.resetForTest()
```

Use `AppMother.buildApp()` (in `src/test/.../example/AppMother.kt`) for integration tests that need a full CLI app instance. Coroutine tests use `runTest {}` from `kotlinx-coroutines-test`.
