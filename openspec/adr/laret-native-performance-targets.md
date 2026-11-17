# Laret Native Performance Targets

- Status: Accepted
- Date: 2026-09-22
- Implemented: 2026-09-29

## Context

The concurrency benchmark suite now builds and runs all five targets. Its first
five-target run (commit `9ce1d6a`, pull-request gate, `--quick`, pinned to
cores 0-1 on an Intel Xeon Platinum 8573C) produced the following. Numbers in
milliseconds are wall clock except where marked as latency.

| Scenario | Cobra | clap | picocli | Laret JVM | Laret native |
|---|---|---|---|---|---|
| A fan-out, N=100 | 10.51 | 10.58 | 202.79 | 788.84 | 21.04 |
| B event storm | 456.40 | 456.38 | 649.20 | 1274.77 | 476.54 |
| C pipeline, 1,000 lines | 10.62 | 10.53 | 203.59 | 801.49 | 40.97 |
| D SIGTERM latency | 1.15 | 1.11 | 31.39 | 31.39 | 7.28 |
| Peak RSS, B (MiB) | 7.0 | 3.7 | 65.0 | 129.3 | 68.7 |
| Peak RSS, D (MiB) | 7.1 | 3.8 | 65.4 | 83.9 | 45.2 |

The question behind this ADR was whether Laret native can be made faster than
Cobra, clap and picocli. Reading these numbers closely answers part of it and
shows that the rest cannot yet be answered from this data.

### What the data does show

**Laret native already beats picocli** on every scenario: roughly 10x on fan-out
and five times on the pipeline, four times lower cancellation latency, and
lower peak RSS in cancellation. That gap is far above any measurement error in
the harness.

**Laret native carries a fixed per-invocation cost.** Its fastest run is two
harness polling intervals where Cobra's and clap's are one, and the pipeline
takes two intervals more than fan-out does.

**Laret native's memory is an order of magnitude above Go and Rust** in the
runs that live long enough to be sampled properly: 45 to 69 MiB against 4 to 7
MiB.

### What the data cannot show

**Every Cobra and clap wall clock is the harness's floor, not their speed.**
`run_once` polls the child with `time.sleep(0.01)` between checks, so no run
can report less than one interval of about 10.5 ms. All four Cobra and clap
fan-out and pipeline values sit at exactly that floor, and Laret native's 21.04
and 40.97 are two and four intervals. The true Cobra and clap times are some
unknown amount below 10.5 ms.

**Cancellation latencies fall into `Popen.wait` backoff buckets.**
`subprocess.Popen.wait(timeout=...)` polls with an exponentially growing sleep,
so reported latencies cluster at about 1.1, 7.3 and 31.4 ms. picocli and Laret
JVM both reporting exactly 31.39 ms is the bucket, not a coincidence.

**Peak RSS for the fast runs is `taskset`'s memory, not the target's.** The
84 to 208 KiB reported for Cobra and clap fan-out and pipeline runs is sampled
from the `taskset` process before it hands over to the target binary. No real
Go or Rust program runs in 0.2 MiB.

**The event storm measures its own sleeps.** Every implementation stays open
for window + 2 x debounce + 100 ms after the storm, about 450 ms by design, so
Scenario B's wall clock mostly measures the payload contract. Only the residual
above 450 ms is framework cost.

**`--quick` scale is dominated by start-up.** At N = 100 and 1,000 lines, the
work under measurement is a small fraction of each run.

### What Laret native pays that the other targets do not

- **`-Ob`, quick-build mode, is in `commonArgs`** for every native binary,
  including the ones `release.yml` ships. It trades runtime optimisation for
  build speed and belongs in a local development loop, not in a release.
- **The Laret target is the full demo CLI**, while Cobra, clap and picocli are
  minimal apps. Every bench command therefore also runs `LoggingMiddleware`,
  runs `StatsMiddleware`, which writes `~/.laret/stats.json` on each invocation
  and so breaks the suite's no-I/O rule, and builds a command tree of about
  1,400 lines of DSL.
- **`CliApp.run` calls `OldBinaryCleaner.cleanupSilently()` on every
  invocation.** It locates the executable and scans its directory for leftovers
  of a self-update, which is framework-level filesystem work that every Laret
  app pays even when it has no self-update feature.
- **No heap or GC tuning.** The image uses Serial GC with default heap sizing,
  which accounts for most of the RSS gap.
- **The streaming pipeline moves one line per queue operation.** Each stage's
  `PrintStream` is created with `autoFlush = true`, so every `println` becomes
  its own `ByteArray` copy and its own `ArrayBlockingQueue.put`. That is the
  most likely source of the extra pipeline cost relative to fan-out.

### The distribution constraint

Pull requests and releases build with GraalVM Community, while the nightly
benchmark built with Oracle GraalVM, so the suite was measuring a binary nobody
ships. Laret stays on Community everywhere. That rules out profile-guided
optimisation, G1 in native images and `-O3`, which are Oracle GraalVM features,
and it means every gain has to come from build flags available in Community,
runtime options and changes to Laret's own code.

## Decision

### 1. Goal

Laret native commits to three targets, evaluated on nightly full-scale runs
only, never on `--quick`:

1. **Stay ahead of picocli** on every scenario and every metric.
2. **Wall clock within 2x of Cobra and of clap** on Scenarios A (N = 1,000 and
   10,000), C (100,000 lines) and D (cancellation latency), and on the Scenario
   B residual above its designed delay.
3. **Peak RSS within 5x of Cobra and of clap** on every scenario.

The third target is recorded with its risk. Five times clap's 3.8 MiB is about
19 MiB. SubstrateVM's runtime and image heap put a floor under native-image RSS
that Laret code cannot remove, so the target against clap may prove
unreachable, and the ADR says so now rather than after the attempt. Five times
Cobra's 7 MiB, about 35 MiB, is the more comparable bar, since Go also carries a
garbage-collected runtime.

Beating Cobra and clap outright is not a goal. They are native binaries without
a managed runtime, and a claim that Laret beats them would need numbers this
suite does not yet produce and may never produce.

The targets are reported, not enforced. The nightly job renders each target as
met or missed beside the numbers, consistent with the suite's rule that no
timing ever fails a build.

### 2. Fix the measurement before optimising

No optimisation is judged until the harness can resolve the difference between
Laret native and Cobra. That comes first, because otherwise a 5 ms improvement
and a 5 ms regression look the same.

- **Wall clock and peak RSS come from `os.wait4`**, which blocks until the child
  exits and returns its resource usage. The elapsed time is exact rather than
  quantised to 10 ms, and `ru_maxrss` is the kernel's peak for that process
  over its whole life, including after `taskset` has handed over to the target,
  so the sampling loop and its blind spot both go away.
- **CPU pinning moves to `os.sched_setaffinity`** in the child before `exec`,
  so the measured process is the target binary from its first instruction.
- **Scenario D latency is measured by blocking in `os.wait4`** after the signal
  instead of by `Popen.wait`'s backoff loop.
- **Scenario B reports a `settle_ms` field** in its summary line: the time from
  the last event emitted to the coalesced run. All five implementations add it,
  and the wall clock stays in the table as context.
- **The Laret native provenance records the GraalVM version** that built the
  binary, captured at build time, instead of `not measured`.

### 3. Measure the framework, not the demo app

The Laret target becomes a minimal app built with the Laret DSL that contains
only the `bench` group, the same shape as the Cobra, clap and picocli targets.
It gets its own native-image build in the nightly job.

All benchmark code, the payloads in `BenchCommands` as well as the entry point,
lives in a `bench` source set that never ships. An earlier cut left
`BenchCommands` in `src/main` so the demo CLI could keep a hidden `bench` group,
which meant the payloads shipped in the published library jar while only their
entry point did not. The demo no longer carries them, and their tests run
against the bench app, whose source set the unit tests see on their classpath.

This moves `LoggingMiddleware`, `StatsMiddleware` and the demo's command tree
out of the measurement, because they are choices the demo makes. It does not
move `OldBinaryCleaner`, because that runs inside `CliApp.run` for every Laret
app. That call becomes opt-in: it runs only when the application enables
self-update, and the demo, which does, keeps it.

### 4. Optimisation levers, each measured on its own

Each lever lands as its own change with a nightly before-and-after number
attached. A lever that does not move its metric beyond run-to-run variation is
reverted, whatever the theory behind it said.

1. **Drop `-Ob` from shipped builds.** Release, CI and benchmark binaries build
   at the default `-O2`. Quick-build mode stays available for local iteration
   behind a Gradle property, off by default. Expected to move every wall-clock
   number.
2. **Make `OldBinaryCleaner` opt-in** as described above. Expected to move the
   per-invocation floor.
3. **Tune the native heap.** Select Serial GC explicitly and size the young
   generation and maximum heap through `-R:` runtime options until peak RSS
   stops falling without wall clock rising. Expected to move RSS on every
   scenario and to carry most of the RSS target.
4. **Chunk the streaming pipeline.** Non-final stages write through a buffered
   stream with `autoFlush` off, flushed when the buffer fills and at stage
   exit, and `BoundedPipe` capacity is re-expressed as a byte budget rather
   than a count of writes. This trades per-line latency between stages for
   throughput, which is the right trade for the streaming path but has to be
   documented where the path is. Expected to move Scenario C.
5. **Re-profile cancellation** once latency is exact. `CancellationScope`
   starts a thread and runs cleanups under `runBlocking`. Nothing is changed
   there until wait4-based numbers show where the milliseconds actually go.

### 5. Rejected

- **Profile-guided optimisation, G1 and `-O3`.** Oracle GraalVM only, and the
  project stays on Community.
- **`-march=native`.** The binary would only run on CPUs like the build
  machine, and the benchmark would measure something the release does not ship.
- **Epsilon GC.** It would flatter every short benchmark, but a long-running
  `watch live` session would grow without bound.
- **Rewriting the bench payloads around raw threads or hand-rolled loops.** That
  would measure hand-written Kotlin rather than Laret's dispatcher, debouncer
  and pipeline, which is the thing the suite exists to measure.

## Implementation results

Measured in a Linux container on the development machine (Intel i7-10510U,
cores 0-1 pinned), on the fixed harness, as medians. These are one machine's
numbers; the nightly job is the reference.

### The harness needed two more fixes than the ADR foresaw

- **Forking from Python put a floor under RSS.** The kernel carries a
  process's pre-exec memory into `ru_maxrss`, so every target forked from the
  interpreter read at least Python's own 12.5 MiB, and Cobra and clap read
  exactly that on every scenario. Targets are now forked from `rusage_exec`, a
  small C helper the harness compiles on first use, and report their real
  peaks: clap 3.7 to 3.9 MiB, Cobra 5.2 to 7.5 MiB.
- **A fixed target order biased the last target.** Running each target's
  whole suite in turn always put Laret native last, after minutes of sustained
  load. On a machine that throttles, its pipeline measured 154 ms in the full
  run against 67 ms on its own. The harness now runs scenario by scenario,
  rotates the target order on every repeat, and reports the median of
  `--repeat` runs (3 by default, 1 for `--quick`).

### Levers

| Lever | Result | Applied |
|---|---|---|
| 1. Drop `-Ob` | No effect beyond run-to-run noise on any scenario; build time equal | No, per the ADR's own rule |
| 2. `OldBinaryCleaner` opt-in | Not isolated; folded into the minimal app | Yes: `CliBuilder.selfUpdate`, demo opts in |
| 3. Heap: `--gc=serial -R:MaxNewSize=8m` | Pipeline RSS 66 to 29 MiB and 10-15% faster; storm RSS 46 to 29 MiB; the 10,000-task burst pays 3-5 ms for extra young collections | Yes |
| 4. Pipeline chunking (8 KiB, 32 chunks per pipe) | Pipeline 153-160 to 65 ms, 58% faster | Yes |
| 5. Cancellation profile | ~7 ms with no workers; 18-20 ms from 50 to 2,000 busy workers, flat, so not per-cleanup cost but the shutdown thread waiting for CPU behind spinning workers | Profiled, no change |

The minimal bench app was itself worth measuring on the JVM: against the demo
CLI, pipeline 1,250 to 797 ms, fan-out 1,142 to 1,042 ms, RSS 97 to 79 MiB.

### Where Laret native stands

Against picocli: ahead on every row. Against Cobra and clap, of the ADR's
targets: event-storm settle time met against both, faster than either; peak RSS
met against Cobra on three of four scenarios and against clap on fan-out only;
every wall-clock and latency target still missed, pipeline by about 3x and
fan-out and cancellation by 4-7x.

### The floor the ADR suspected, measured

A bare SubstrateVM hello-world, built with the same flags, takes 5.1 ms and
8.7 MiB. clap's whole invocation is 1.4 ms, so **within 2x of clap on
start-up-bound runs is out of reach for any native image**, not only for
Laret. Laret adds 3.5-7 ms and about 16.6 MiB on top of that floor, and that
start-up work, not scheduling, is most of what separates it from Cobra on
fan-out and cancellation. None of this ADR's levers addresses it; reducing what
the framework initialises before the first command is the follow-up.

### Second round: per-task and per-line cost

The nightly run of 2026-10-20 (AMD EPYC 7763, cores 0-3) still missed every
wall-clock target: fan-out 9.3 and 29.9 ms at N=1,000 and 10,000 against
Cobra's 2.8 and 5.5, the pipeline 63.2 ms against 20.8. With the start-up floor
lowered by the `native-startup-floor` ADR, what remains is cost per task and
per line, so this round attacks those two and keeps the heap flags, and with
them peak RSS, unchanged.

Measured in a Linux container on the development machine, cores 0-3 pinned.
That machine was noisy, so each lever was compared against the previous
binary with an interleaved A/B runner, 30 to 40 alternating runs of each, and
the medians are reported side by side from the same run rather than across
runs.

| Lever | Result | Applied |
|---|---|---|
| 6. Unbounded `executeBlocks` launches into a results array instead of `async` + `awaitAll` | Fan-out N=10,000 33.8 to 29.5 ms (-13%) and 25.3 to 23.4 MiB; N=1,000 unchanged. One `Deferred` and one `awaitAll` completion handler per block go away; ordering and failure semantics are the same, since a failing child still cancels its siblings through `coroutineScope` | Yes |
| 7. `BatchingPrintStream` for intermediate pipeline stages | Pipeline 92.6 to 42.9 ms (-54%), RSS flat. `PrintStream` runs its charset encoder and flushes its internal writers twice per `println`; the stage stream now collects text and encodes it in 8 KiB blocks, which are also the pipe's chunk size. Raw byte writes drain pending text first, and a high surrogate at a block boundary is held back so a split pair is never encoded alone. The last stage keeps an auto-flushing `PrintStream`, so output to a terminal is still timely | Yes |

Against the original binary in one final interleaved run: fan-out N=10,000
-17% and -2 MiB, pipeline -50%, fan-out N=1,000 and the event storm unchanged,
and every correctness check of the harness passing. Fan-out at N=1,000 is still
dominated by start-up and the first dispatch onto `Dispatchers.Default`: on that
machine, `--version`, a zero-task fan-out and a one-task fan-out are about 3 ms
apart each, so a further gain there is a start-up question again.

## Consequences

The next report on Laret against Cobra and clap will rest on numbers that can
actually tell them apart, and each optimisation will have a measured effect
attached rather than an argument. Release binaries lose `-Ob`, which should make
them faster at the cost of longer native builds in CI. `OldBinaryCleaner`
becomes something an app switches on, so apps without self-update stop paying
for it.

The costs are real. The harness changes alter every historical number, so
earlier results are not comparable with later ones and have to be labelled as
such. The minimal Laret target adds a second native-image build to the nightly
job. Every implementation has to be edited to emit `settle_ms`. Chunking the
pipeline changes the latency characteristics of the streaming path.

The RSS target against clap may fail after every lever has been applied. If it
does, the ADR's own reasoning already explains why, and the right response is
to record that result rather than to change the scenarios until it passes.

The Laret JVM target, which is currently four times slower than picocli, is out
of scope here. The minimal bench app will show how much of that gap belongs to
the demo app and how much to the framework, which is the question a follow-up
ADR would need answered first.
