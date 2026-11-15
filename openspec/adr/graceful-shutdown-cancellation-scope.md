# Graceful Shutdown with CancellationScope

- Status: Accepted
- Date: 2026-08-21

## Context

Laret had no shutdown handling. `CliApp.run()` executed `runBlocking { dispatch(args) }` and, in a `finally`, called `shutdownPlugins()`. The DSL's `onAppShutdown` hook was invoked only by the public `shutdown()` method, which nothing on the normal path calls, so the hook never fired on exit. Long-running commands — `watch`, `parallel` — ran inside `runBlocking` with no cancellation path, so a `SIGINT` (Ctrl-C) or `SIGTERM` killed the process abruptly with no chance to release resources. Only the sidecar plugin executor handled interruption, translating `InterruptedException` into exit code 130.

Commands need a way to register cleanup that runs when the process is asked to stop, and long-running work needs to actually stop rather than be killed mid-operation.

## Decision

Introduce a `CancellationScope` delivered on `SIGINT`/`SIGTERM` for cleanup registration.

### Signal delivery

A single JVM shutdown hook (`Runtime.getRuntime().addShutdownHook`) catches `SIGINT`, `SIGTERM`, and normal exit uniformly. It is portable, needs no reflection or native-image configuration (consistent with the recent removal of Jansi for native-image reasons), and lets the JVM produce the conventional exit codes itself — `130` for `SIGINT`, `143` for `SIGTERM` — after the hook completes. The accepted trade-off is that the hook cannot distinguish which signal arrived.

### Scope and registration

`CancellationScope` owns a `SupervisorJob` and an ordered list of cleanup callbacks. `CommandContext` carries an injected scope and exposes `onShutdown(block: suspend () -> Unit): Handle`, which delegates to the scope; framework internals hold the scope directly. `onShutdown` mirrors the existing context helpers (`prompt()`, `spinner()`, `registerUndo()`) and returns a handle so a registration can be disposed. Cleanup blocks are suspending, so asynchronous teardown is expressible; synchronous cleanups are simply non-suspending bodies.

### Cancellation and ordering

`run()` executes `dispatch` under the scope's job (`runBlocking(scope.coroutineContext)`). On shutdown the scope cancels that job, so suspending commands unwind at their next suspension point and their `finally` blocks run. The scope then runs registered cleanups in LIFO order — last registered first — so nested resource acquisition unwinds correctly. A `CancellationException` surfacing from `dispatch` is treated as an interrupted run.

### Grace period

Cleanups run on a dedicated daemon worker bounded by a grace budget (default 5s, injectable). If they finish in time the process exits normally; if they exceed it, the scope logs and calls a force-halt action (`Runtime.getRuntime().halt(exitCode)` in production, injectable for tests) so a stuck cleanup can never wedge the process. Shutdown is idempotent: the shutdown hook and the normal-exit `finally` both call it, and the first wins.

### Relationship to existing hooks

`onAppShutdown` and `shutdownPlugins()` are registered into the scope as the outermost cleanups, so they fire on `SIGINT`/`SIGTERM` and on normal completion, after per-command cleanups. This fixes the latent gap where `onAppShutdown` never ran on exit and unifies one shutdown path. A fresh scope is created per run so that `runForTest` — which does not install the JVM hook, register `onAppShutdown`, or force-halt — stays isolated and reusable across many invocations in one JVM.

## Package Boundaries

`CancellationScope` lives in `core` beside `CommandContext`, `CliApp`, and `CommandRunner`, which are the only types that wire it. No new package or dependency is added.

## Consequences

Commands can register cleanup that reliably runs on interruption, and suspending long-running work stops gracefully instead of being killed. The shutdown path is unified and `onAppShutdown` now works. Because `onAppShutdown` fires on every production run, the demo's shutdown message is emitted on normal exit as well — expected, and asserted only by grep-based CI checks, not by empty-stderr checks. The shutdown hook cannot tell `SIGINT` from `SIGTERM`, so the distinction is deferred to the JVM's own exit code rather than observed in code. A cleanup that blocks without suspending is bounded by the daemon-worker join and the force-halt, not by coroutine cancellation.
