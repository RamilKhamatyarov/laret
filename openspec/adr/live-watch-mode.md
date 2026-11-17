# Live Watch Mode with File Globbing

- Status: Accepted
- Date: 2026-08-25

## Context

Laret's `watch run` prints filesystem events but cannot re-run a command when files change. A live "watch and re-run" mode — the nodemon / cargo-watch workflow — needs glob filtering, debouncing, and orchestrated re-execution on top of the existing `DirectoryWatcher`, integrated with the `CancellationScope` shutdown mechanism and the configuration system.

## Decision

Add a separate `watch live <path> [flags] -- <group> <command> [args]` command. The target invocation follows a `--` separator and is captured through the same raw-args ThreadLocal that `pipe run` uses. `watch run` is left unchanged.

### Re-execution model

The target is re-run **in-process** with `app.runForTest(args)` — the same mechanism `CommandPipeline` uses — so any built-in command, group, or pipeline works with no executable-path resolution. Each run executes inside a `collectLatest` block, so when a new debounced trigger arrives the in-flight run's coroutine is cancelled before the next starts (cancel + bounded join). Cancellation is cooperative: a genuinely blocking command finishes its current run rather than being force-killed, which is safe because runs never overlap. The target runs once immediately on start, then again on each qualifying change; the initial run counts toward the restart cap.

### Glob matching

`GlobMatcher` compiles patterns with Java NIO `FileSystems.getPathMatcher("glob:…")` (supporting `*`, `**`, `?`, `{a,b}`, `[a-z]`). A pattern prefixed with `!` is an exclude. Each changed file is matched by its path **relative to the watch root**, and — to make extension patterns like `*.kt` match at any depth and to sidestep NIO's `**/`-needs-a-separator quirk — also by its filename alone. A file triggers a re-run when it matches at least one include (or there are no includes) and no exclude. This MVP supports one target command with repeatable include/exclude patterns; distinct pattern-sets driving distinct commands is a documented non-goal for now.

### Orchestration and debounce

`LiveWatchSession` is the pure orchestrator: it takes a `Flow<Path>` of changes, a compiled matcher, a debounce window (default 150 ms), the caps, and a `suspend (attempt) -> Int` runner. It filters by the matcher, debounces (coalescing bursts such as a save-all or branch switch into one re-run), and drives `collectLatest`. Keeping the FS watcher, the app, and the clock behind these seams makes the session unit-testable with virtual time and a fake runner. `Main.kt` bridges the blocking `DirectoryWatcher` (recursive) to the session via a channel on `Dispatchers.IO`.

### Resilience

A non-zero exit is reported and watching continues. `--max-restarts N` (0 = unlimited) caps total runs so bounded and CI sessions terminate; `--max-consecutive-failures N` (0 = off) stops the session after N back-to-back failures. Only completed (non-superseded) runs update these counters.

### Configuration and lifecycle

A `watch` section in `AppConfig` supplies defaults for debounce, patterns, and the caps; CLI flags override config per the existing 12-factor order. The session registers `ctx.onShutdown { watcher.close() }` on the `CancellationScope`, so `Ctrl-C` closes the watch service (unblocking the watch loop), cancels the running child, and runs cleanups. Under `--dry-run` the session starts and narrates each would-be run without executing it. Because re-runs go through `app.runForTest`, they flow through the global `StatsMiddleware` and are recorded like any other command execution.

## Package Boundaries

`GlobMatcher` and `LiveWatchSession` join the existing `watch` package beside `DirectoryWatcher`. `WatchConfig` lives in `config.model`. The `watch live` wiring is in the demo `Main.kt`. No new third-party dependency is added.

## Consequences

Users get a real watch-and-re-run loop over any Laret command, with glob include/exclude, debouncing, failure resilience, and clean interrupt handling. The in-process model keeps termination cooperative rather than a hard process kill, which is the accepted trade-off for staying within Laret's execution model. Native watching is whatever the JVM `WatchService` provides (inotify on Linux, `ReadDirectoryChangesW` on Windows, polling on macOS); a dedicated polling backend beyond the JVM's is deferred. Multi-command pattern-sets are deferred.
