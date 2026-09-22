# Concurrency and Parallel Execution Benchmarks

Laret measured against four other CLI stacks on the orchestration work a
framework does *after* start-up: fan-out scheduling, event coalescing, streaming
pipelines and cancellation under load. Cold-start numbers are a different
question and are not on this page.

This file is written by hand from the harness output. It is not generated into
`site/docs`, because `laret doc generate` overwrites that tree.

> **Status: no measurements yet.** The harness and the five implementations are
> being built; this page is their committed home. Every cell below reads
> `not measured` until the first full nightly run fills it in. Nothing here is
> an estimate, a projection or a number from another machine.

## Provenance

Numbers are only meaningful next to the machine that produced them. Every
refresh of this page must update this block from the `results.json` header it
was rendered from.

| Field | Value |
|---|---|
| Laret commit | not measured |
| Date | not measured |
| Host | not measured |
| Kernel | not measured |
| CPU / pinned cores | not measured |
| Go toolchain | not measured |
| Rust toolchain | not measured |
| JDK | not measured |
| GraalVM | not measured |

## Targets

| Target | Build |
|---|---|
| Cobra (Go) | `go build -trimpath -ldflags="-s -w"` |
| clap (Rust) | `cargo build --release` |
| picocli (Java) | `java -jar` |
| Laret JVM | `java -jar` (shadow jar) |
| Laret native | GraalVM Native Image |

All five implement the same four commands with the same flags and the same
stdout contract, so the harness invokes them identically.

## Rules

- **No external I/O.** Payloads are CPU or memory bound. No network, no
  database, no heavy disk writes.
- **Pinned and isolated.** Each process is pinned with `taskset`; each scenario
  finishes and releases its memory before the next begins.
- **Warmed up.** A discarded dry run of each scenario precedes the timed runs.
- **Linux only.** Pinning and peak RSS (`/proc/<pid>/status` `VmHWM`) are
  Linux-specific, and numbers are not comparable across operating systems. The
  harness refuses to run elsewhere.
- **Native abstractions.** Where a framework has its own parallel dispatch or
  pipeline, that is what is measured; otherwise the language's standard
  primitives are used inside the framework's command lifecycle.

## Scenario A — Parallel fan-out and fan-in

Dispatch N independent sub-tasks concurrently, wait for all of them, aggregate
the results. Each task yields briefly and returns a success token. This measures
the tax the framework adds on top of the language's own concurrency model.

### N = 1,000

| Target | Wall clock | Overhead per task | Peak RSS |
|---|---|---|---|
| Cobra | not measured | not measured | not measured |
| clap | not measured | not measured | not measured |
| picocli | not measured | not measured | not measured |
| Laret JVM | not measured | not measured | not measured |
| Laret native | not measured | not measured | not measured |

### N = 10,000

| Target | Wall clock | Overhead per task | Peak RSS |
|---|---|---|---|
| Cobra | not measured | not measured | not measured |
| clap | not measured | not measured | not measured |
| picocli | not measured | not measured | not measured |
| Laret JVM | not measured | not measured | not measured |
| Laret native | not measured | not measured | not measured |

Laret dispatches these through `ParallelDispatcher`'s in-process path, which
shares the worker-pool ordering and `maxJobs` bound of the process path without
paying a `ProcessBuilder` spawn per task. Process dispatch is measured
separately, at a scale where process creation does not dominate the result.

## Scenario B — Event storm

The CLI enters watch mode; 10,000 events are fired within a 50 ms window. The
framework must filter, debounce and coalesce them into exactly one execution of
the target command.

| Target | Wall clock | Peak RSS | Coalescing |
|---|---|---|---|
| Cobra | not measured | not measured | not measured |
| clap | not measured | not measured | not measured |
| picocli | not measured | not measured | not measured |
| Laret JVM | not measured | not measured | not measured |
| Laret native | not measured | not measured | not measured |

Coalescing is strict pass/fail: exactly one target execution, or the row fails.
Laret drives this through `LiveWatchSession`, whose change source is an
injectable flow, so the storm is synthetic and never touches the filesystem.

## Scenario C — Concurrent streaming pipeline

A three-stage pipeline with all stages live at once: stage 1 emits 100,000
lines, stage 2 filters and transforms concurrently, stage 3 aggregates a count.
This measures I/O multiplexing, buffer management and whether the pipeline can
stay synchronised without deadlocking, dropping lines or exhausting memory.

| Target | Wall clock | Peak RSS | Lines correct |
|---|---|---|---|
| Cobra | not measured | not measured | not measured |
| clap | not measured | not measured | not measured |
| picocli | not measured | not measured | not measured |
| Laret JVM | not measured | not measured | not measured |
| Laret native | not measured | not measured | not measured |

Laret's original `CommandPipeline` could not run this scenario at all: it ran
stages strictly in sequence and buffered each stage's entire stdout into a
string. The streaming pipeline added for this suite connects stages with bounded
channels, so a slow consumer applies backpressure instead of the pipeline
accumulating the stream. The buffered path remains the default; streaming is
opt-in.

## Scenario D — Cancellation storm

A long-running command spawns 500 background workers. 200 ms after start-up a
termination signal is injected. The framework must propagate cancellation to all
500, wait for acknowledgement, run the registered cleanup hooks in reverse
registration order, and exit with the right code.

SIGTERM (exit 143) is the primary case. A non-interactive shell sets SIGINT to
`SIG_IGN` for backgrounded children and both the JVM and native images respect
that, so SIGINT (exit 130) is measured separately under job control.

### SIGTERM

| Target | Cancellation latency | Exit code | Cleanup order | Peak RSS |
|---|---|---|---|---|
| Cobra | not measured | not measured | not measured | not measured |
| clap | not measured | not measured | not measured | not measured |
| picocli | not measured | not measured | not measured | not measured |
| Laret JVM | not measured | not measured | not measured | not measured |
| Laret native | not measured | not measured | not measured | not measured |

### SIGINT

| Target | Cancellation latency | Exit code | Cleanup order | Peak RSS |
|---|---|---|---|---|
| Cobra | not measured | not measured | not measured | not measured |
| clap | not measured | not measured | not measured | not measured |
| picocli | not measured | not measured | not measured | not measured |
| Laret JVM | not measured | not measured | not measured | not measured |
| Laret native | not measured | not measured | not measured | not measured |

## Reproducing

```bash
# Linux only.
benchmarks/harness/build.sh           # build all five targets
python3 benchmarks/harness/harness.py --out results.json
python3 benchmarks/harness/render.py results.json > docs/benchmarks.md
```

CI does not gate on any timing. Pull requests run the scenarios once at reduced
scale and assert only the deterministic properties — coalescing accuracy,
aggregate counts, exit codes, cleanup ordering. The full five-target suite runs
nightly and uploads `results.json` and the rendered table as artifacts.

See [the ADR](../.github/adr/concurrency-benchmark-suite.md) for why the suite
is shaped this way.
