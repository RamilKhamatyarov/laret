# graceful-shutdown Specification

## Purpose

Defines how a Laret command registers cleanup work, when that cleanup runs and in what order, how interruption and normal completion affect exit codes, and the trade-offs of the optional fast signal path.

## Requirements

### Requirement: Cleanup registration

A command MUST be able to register a cleanup through the execution context's `onShutdown` registration, and a cleanup body MUST be allowed to suspend. Registration MUST return a disposable handle that removes the cleanup when disposed. Framework cleanups for application shutdown hooks and plugins MUST be registered before any command cleanup.

#### Scenario: Command registers a cleanup

- **WHEN** a command registers cleanup work through its context and the command finishes
- **THEN** the cleanup runs before the process exits

#### Scenario: Framework cleanups are outermost

- **WHEN** an application shutdown hook and plugins are present alongside command cleanups
- **THEN** the command cleanups run first and the application and plugin cleanups run after them

### Requirement: LIFO cleanup order

On shutdown, registered cleanups MUST run in last-in-first-out order: the last cleanup registered runs first. Disposing a handle before shutdown MUST remove that cleanup so it does not run, and disposing after shutdown MUST have no effect.

#### Scenario: Nested resources unwind correctly

- **WHEN** a command registers cleanup A and then cleanup B
- **THEN** B runs before A

#### Scenario: Disposed cleanup is skipped

- **WHEN** a registered cleanup's handle is disposed before the run ends
- **THEN** that cleanup does not run at shutdown while the remaining cleanups still do

### Requirement: Normal-completion cleanup

When a command line finishes normally, the run MUST execute all remaining cleanups before the process exits, and the command's exit code MUST be preserved. The demo `sys sleep` MUST write its marker file and print its cleanup message on normal completion as well as on a signal.

#### Scenario: Cleanup runs without a signal

- **WHEN** `sys sleep` is run with a marker path and finishes normally
- **THEN** it prints its completion and cleanup messages, writes the marker file, and exits with the command's exit code

#### Scenario: Command exit code survives cleanup

- **WHEN** a command sets a non-zero exit code and its cleanups complete
- **THEN** the process still exits with that command's code

### Requirement: Signal-triggered shutdown

In the default signal mode, `SIGINT` and `SIGTERM` MUST trigger the same cleanup path through the JVM shutdown sequence. The process MUST exit 130 for `SIGINT` and 143 for `SIGTERM`, and an interrupted dispatch MUST be treated as exit code 130. See [the graceful shutdown ADR](../../adr/graceful-shutdown-cancellation-scope.md).

#### Scenario: Interrupt runs cleanups

- **WHEN** `SIGINT` or `SIGTERM` arrives while a command is running
- **THEN** registered cleanups run and the process exits with the conventional signal-derived code

### Requirement: Cancellation of running work

Shutdown MUST cancel the run's work before running cleanups so that work which suspends unwinds at its next suspension point and its `finally` blocks run. Work that blocks without suspending MUST NOT be interrupted by cancellation and is bounded only by the grace period.

#### Scenario: Suspending command unwinds

- **WHEN** a long-running command that suspends is interrupted
- **THEN** it stops at its next suspension point, its `finally` blocks run, and registered cleanups follow

#### Scenario: Blocking work is not cancelled

- **WHEN** a command performs work that never suspends during shutdown
- **THEN** that work is not interrupted and the cleanup path proceeds only within the grace period

### Requirement: Idempotent shutdown

Shutdown MUST be idempotent: only the first invocation performs teardown, and later invocations MUST return immediately. Registering a cleanup after shutdown has begun MUST be a no-op that returns an inert handle.

#### Scenario: Repeated shutdown is harmless

- **WHEN** the signal path and normal completion both request shutdown
- **THEN** the first request runs the cleanups and the later request returns immediately without running them again

#### Scenario: Late registration is ignored

- **WHEN** a command attempts to register a cleanup after shutdown has begun
- **THEN** the registration is not stored, the returned handle is inert, and no error is raised

### Requirement: Grace period and cleanup failures

Cleanups MUST run within a grace period of 5000 ms by default. A cleanup that fails MUST print `Shutdown cleanup failed: <message>` and MUST NOT prevent the remaining cleanups from running. Exceeding the grace period MUST print `Graceful shutdown exceeded <n>ms; forcing exit` and force the process to halt with the shutdown exit code.

#### Scenario: One failing cleanup

- **WHEN** one registered cleanup fails while others remain
- **THEN** its failure is reported and the remaining cleanups still run

#### Scenario: Grace period exceeded

- **WHEN** cleanups do not finish within the grace period
- **THEN** the timeout message is printed and the process is force-halted instead of hanging

### Requirement: Fast signal mode

An application MAY opt into fast signal handling. In that mode the first `SIGINT` or `SIGTERM` MUST cancel the run, run cleanups within the grace period, flush stdout and stderr, and halt with 128 plus the signal number; a second signal MUST halt immediately with the same code without running remaining cleanups. Previous signal handlers MUST be restored when the run returns. See [the signal-handling ADR](../../adr/signal-latency-rss-and-dispatch-floor.md).

#### Scenario: First signal cleans up then halts

- **WHEN** the first `SIGINT` arrives in fast signal mode
- **THEN** cleanups run, both output streams are flushed, and the process halts with code 130

#### Scenario: Second signal skips the rest

- **WHEN** a second signal arrives while the first signal's cleanups are still running
- **THEN** the process halts immediately with the same signal-derived code and remaining cleanups are skipped

### Requirement: Fast signal trade-off

Fast signal handling MUST be opt-in because halting skips JVM shutdown hooks registered outside the framework. An application that registers its own shutdown hooks outside the framework MUST leave fast signals disabled to preserve them.

#### Scenario: Default mode preserves foreign hooks

- **WHEN** an application does not opt into fast signals and registers a shutdown hook outside the framework
- **THEN** that hook still runs as part of the JVM shutdown sequence

#### Scenario: Opting in accepts the trade-off

- **WHEN** an application opts into fast signals
- **THEN** the process halts directly after cleanups without running shutdown hooks registered outside the framework

### Requirement: Exit semantics

A normal run MUST return the command's exit code. A shutdown that interrupts dispatch MUST return 130. In fast signal mode the process exit code MUST be 128 plus the signal number regardless of the command's own exit code.

#### Scenario: Normal return

- **WHEN** a command completes successfully without interruption
- **THEN** the run returns the context's exit code, defaulting to 0

#### Scenario: Interrupted default return

- **WHEN** shutdown cancels dispatch in the default signal mode
- **THEN** the interrupted run returns 130
