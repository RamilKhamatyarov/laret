# Concurrent and Parallel Execution Benchmark Suite

- Status: Accepted
- Date: 2026-09-21

## Context

Cold-start benchmarks measure single-threaded initialization. They say nothing
about what a CLI framework costs once it starts orchestrating work: how much tax
it adds over the language's native concurrency model, whether it survives an
event storm without dropping or duplicating work, whether it can multiplex
streaming stages without deadlocking, and whether it shuts down cleanly under
load.

A four-scenario contract measures exactly that, across five build targets:

| Target | Build |
|---|---|
| Cobra (Go) | `go build -trimpath -ldflags="-s -w"` |
| clap (Rust) | `cargo build --release` |
| picocli (Java) | `java -jar` |
| Laret JVM | `java -jar` (shadow jar) |
| Laret native | GraalVM Native Image |

Auditing the four scenarios against what Laret actually has produced two
findings that shape this work rather than merely being measured by it.

**`CommandPipeline` does not stream.** `executeResult` runs stages strictly one
after another, capturing each stage's stdout into a `String` and handing it to
the next as `carry`. Scenario C requires all stages to run simultaneously over
100,000 lines; the current pipeline would instead buffer the whole stream in
memory and run the stages in sequence. The scenario is unimplementable against
it, not merely slow.

**`ParallelDispatcher` only dispatches OS processes.** Every unit of work is a
`ProcessBuilder` spawn. Scenario A's 10,000 concurrent tasks would be 10,000
process spawns, which violates the specification's no-external-I/O rule and
would measure the operating system's process table rather than Laret's
scheduling.

The other two scenarios map cleanly onto primitives that already exist:
`LiveWatchSession` already debounces a `Flow<Path>` behind an injectable source,
and `CancellationScope` already runs cleanups in LIFO order behind a grace
budget with an overridable force-halt.

## Decision

### 1. Close the two gaps in the framework first

Both findings are real product gaps that the benchmark merely exposed, so they
are fixed in the framework rather than worked around in the harness.

A **streaming `CommandPipeline`** runs every stage concurrently, connected by
bounded channels so a slow consumer exerts backpressure on its producer instead
of the pipeline accumulating the stream in memory. The existing buffered path
stays as the compatibility default; streaming is opt-in per invocation, so no
current pipeline behaviour changes underneath users. `PipelineResult` keeps its
shape.

**`ParallelDispatcher` gains in-process dispatch**: an overload that takes
suspend blocks rather than `ParallelTask` process descriptors, sharing the same
worker-pool ordering and `maxJobs` semantics as the process path. This is what
the specification is actually asking to measure — the framework's abstraction
tax over coroutines — and it is useful well beyond the benchmark. Process
dispatch remains for the cases that genuinely need a child process, and is
measured separately at a scale where process spawn does not dominate.

### 2. Payloads are real commands, hidden from the public face

The specification requires each scenario to run inside the framework's command
execution lifecycle, so the payloads are real Laret commands: a `bench` group in
the demo `Main.kt` with `fanout`, `storm`, `pipeline` and `cancel` subcommands,
each marked `hidden()`. They run through the real `CommandRunner`, middleware
chain and `CancellationScope`, which is the thing under measurement, while
staying out of `--help`, completions and generated docs. The credibility work
that made the demo CLI's public surface honest is not undone by adding synthetic
load generators to it.

Every target implements the same four commands with the same flags and the same
stdout contract, so the harness invokes all five identically.

> **Superseded in part** by the laret-native-performance-targets ADR: the
> payloads moved out of the demo CLI into a `bench` source set with its own
> minimal app, so they no longer ship in the published jar or appear in the
> demo at all.

### 3. Layout

`benchmarks/` in this repository, holding `cobra/`, `clap/`, `picocli/` and
`harness/`. It is excluded from the Gradle build and from Super-Linter.
Versioning the implementations alongside the framework means a
Laret change and the benchmark that measures it move in one commit, at the cost
of Go and Rust toolchains appearing in this repository's CI surface.

### 4. Linux-only harness

`benchmarks/harness/harness.py` refuses to run anywhere but Linux, with a clear
message. Pinning uses `taskset`, peak RSS comes from `/proc/<pid>/status`
`VmHWM`. Measurements are only comparable within one operating system anyway,
`ubuntu-latest` is the cheapest runner, and Python 3 is already a dependency of
this repository's CI. The cost is that the suite cannot produce numbers on a
Windows or macOS development machine.

The harness performs a discarded warm-up run of each scenario before the timed
runs, and runs each scenario in isolation so one scenario's memory is released
before the next begins.

### 5. Scenario D contract: SIGTERM primary

SIGTERM with exit code 143 is the primary cancellation measurement. A
non-interactive shell sets SIGINT to `SIG_IGN` for backgrounded children, which
both the JVM and a native image inherit and respect; that is precisely what made
the graceful-shutdown CI work flaky earlier in this project. SIGINT with exit
code 130 runs as a secondary case under `set -m`. Cobra, clap and picocli get
explicit handlers so all five targets honour the same contract.

Laret needs no new exit-code constant for this. The HotSpot JVM and a GraalVM
native image built with `--install-exit-handlers` both terminate with `128 +
signal` once their shutdown hooks have run, so 143 and 130 come from the
runtime. `INTERRUPT_EXIT_CODE = 130` stays what it is: the code Laret returns
when it catches a `CancellationException` itself, rather than the code the
process exits with under a signal.

### 6. CI policy: all targets everywhere, assertions on correctness only

Timing thresholds on shared runners would flap, so no timing assertion ever
gates a pull request. What does gate is correctness: exactly one coalesced
execution out of the event storm, the correct aggregate count from fan-out and
from the pipeline, the correct interrupted exit code, and cleanup hooks observed
in reverse registration order.

Every pull request builds all five targets and runs all four scenarios at
reduced scale, rendering the comparison into the job summary. Restricting the
gate to the Laret targets was the cheaper option and was tried first, but it
produced a table listing five targets with rows for two, which reads as three
failures rather than as three absences. Paying for the Go and Rust toolchains on
each pull request buys a comparison that is legible without a footnote, and the
job additionally smoke-runs each foreign target right after building it, so a
broken implementation is reported against its own build step.

The nightly job reruns the same suite at the scales the contract names and
uploads `results.json` and the rendered table as artifacts.

### 7. Output

The harness emits a schema-versioned `results.json` — target, scenario,
parameters, wall clock, per-unit scheduling overhead, peak RSS, cancellation
latency, and the pass/fail accuracy metrics — plus a rendered Markdown table.
`docs/benchmarks.md` is committed and refreshed periodically so the comparison
is readable without digging through CI artifacts. It carries the machine,
kernel, toolchain versions and commit it was produced on, in the document
itself, because a benchmark table without its provenance is exactly the kind of
unbacked claim the credibility ADR removed from the README. It lives in a
hand-written `docs/` directory rather than under `site/docs`, because that tree
is regenerated by `laret doc generate` and anything committed into it would be
overwritten.

## Consequences

Laret gains a genuinely streaming pipeline and an in-process parallel dispatch
API, both driven by an external contract rather than invented, and both usable
outside the benchmark. The four scenarios become runnable and comparable across
five build targets.

The costs are real. This repository acquires Go and Rust toolchains in CI. The
benchmark can only be run on Linux, so it cannot be exercised on the primary
development machine. Five implementations of the same contract must be kept in
step by hand, and a scenario added later has to be written five times.
`docs/benchmarks.md` carries runner-specific numbers in git history and will go
stale between refreshes, which the provenance header mitigates but does not remove.
The streaming pipeline is additive, so the buffered path and its behaviour
remain to be maintained alongside it.
