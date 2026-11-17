# Signal Latency, Runner RSS and the Dispatch Floor

## Status

Accepted

## Context

The nightly concurrency benchmark of 2026-10-24 (AMD EPYC 7763, cores 0-3,
kernel 7.0.0-1017-azure) is the first in which Laret native meets a wall-clock
target: the pipeline, at 36.3 ms against Cobra's 22.4 and clap's 31.3, after
the per-line batching recorded in the `laret-native-performance-targets` ADR.
Three gaps remain.

| Metric | Laret native | Cobra | clap | Ratio |
|---|---|---|---|---|
| D SIGTERM latency | 8.1 ms | 1.2 ms | 1.0 ms | 6.5x, 8.2x |
| D SIGINT latency | 6.5 ms | 1.1 ms | 3.0 ms | 6.0x, 2.2x |
| A fan-out N=1,000 wall | 9.8 ms | 3.1 ms | 2.5 ms | 3.2x, 3.9x |
| A fan-out N=10,000 wall | 27.3 ms | 6.1 ms | 9.3 ms | 4.5x, 2.9x |
| B, C, D peak RSS | 37.0-42.6 MiB | 5.8-7.6 MiB | 3.5-3.7 MiB | 5.5-12x |

**Cancellation is fixed cost, not contention.** An earlier profile measured
about 7 ms from signal to exit with no workers at all. A signal currently
travels the JVM's whole exit sequence before Laret sees it: the runtime's
handler calls `System.exit`, which starts a thread per registered shutdown
hook, one of which is Laret's, which then cancels the scope and runs the
cleanups, after which the remaining JVM exit hooks run. Cobra and clap catch
the signal and return.

**RSS differs by environment, not by code.** The same bench binary peaks at
23-25 MiB in a local Linux container and 37-43 MiB on the runner. Nothing in
the framework varies by 15 MiB between machines. The leading suspect is
transparent huge pages: with the kernel's THP mode at `always`, the first
touch of a 2 MiB-aligned anonymous region can be served by a huge page, and
SubstrateVM reserves its heap in aligned chunks. The harness records neither
the kernel's THP mode nor how much of a target's memory is huge pages, so the
suspicion cannot be confirmed from the published results.

**Fan-out at N=1,000 is a start-up question.** Locally, `--version`, a
zero-task fan-out and a one-task fan-out are about 3 ms apart each. The first
gap is command dispatch (validation, middleware, scope); the second is the
first dispatch onto `Dispatchers.Default`, which starts its worker threads
lazily. Above that, each further coroutine costs about 2 us.

## Decision

Each lever is measured on the Linux native bench binary with an interleaved
A/B runner against the binary without it, and is kept only if it helps
without raising peak RSS. Rejected levers are recorded with their numbers.

### 1. Opt-in fast signal path

`cli { fastSignals() }` makes Laret handle `SIGTERM` and `SIGINT` itself for
the duration of `CliApp.run`:

- The first signal cancels the run's `CancellationScope` and runs its
  cleanups in reverse registration order, within the existing grace period
  and its watchdog, then flushes `System.out` and `System.err` and halts with
  `128 + signal number`.
- A second signal while cleanups are running halts immediately with the same
  code, skipping the cleanups that have not run. This is the usual CLI
  convention for pressing Ctrl-C twice.
- The work runs on the thread the JVM starts for each delivered signal
  (`jdk.internal.misc.Signal.dispatch`), so a second signal still arrives on
  a thread of its own. Starting another thread from the handler was measured
  and costs 7-15 ms under load; see the results.
- The previous handlers are restored when `run` returns.

It is opt-in because halting skips every JVM shutdown hook registered outside
Laret. An application that registers its own `Runtime.addShutdownHook` keeps
today's behaviour by not calling `fastSignals()`. The bench app opts in; the
demo app does not.

### 2. Runner RSS: diagnose, then fix in the binary

The harness records the kernel's THP mode (the selected value of
`/sys/kernel/mm/transparent_hugepage/enabled`) in provenance, and in Scenario
D reads `AnonHugePages` from the target's `/proc/<pid>/smaps_rollup` just
before signalling it, using the pid every target already prints in its ready
line. Both appear in the rendered table. This is diagnostic only: no timing or
peak RSS figure changes, and the environment of Cobra and clap is untouched.

Any fix belongs in the Laret binary, never in the runner. If THP is confirmed
as the cause, locally with THP forced to `always`, the native binary opts out
of THP for its own process at start-up, on Linux only.

### 3. Fan-out dispatch floor

Profile the zero-task and one-task fan-out on the native binary, and remove
whatever the framework does there that a command which has not asked for it
does not need. Starting `Dispatchers.Default` workers while arguments are
parsed is one candidate. Initialising kotlinx.coroutines at image build time
is excluded: the dispatchers would size themselves from the build machine's
CPU count, as the `native-startup-floor` ADR found.

## Rejected

- **Fast signals by default.** Faster for every app, but silently skips
  shutdown hooks that applications register themselves.
- **Disabling THP on the benchmark runner.** It would change the environment
  of every target, and real users run Laret on kernels configured however
  their distribution chose.
- **A larger young generation for fan-out.** Faster bursts at the cost of
  peak RSS, which this round must not raise.

## Consequences

Apps that opt in exit on a signal without running foreign shutdown hooks,
and say so by calling `fastSignals()`. The benchmark's results gain two
diagnostic fields, so a later RSS change can be judged against the kernel it
was measured on. Each lever's measured effect, kept or rejected, is appended
below.

## Implementation results

Measured in a Linux container on the development machine, cores 0-3 pinned,
with interleaved A/B runs (40 per binary, 20 for signals) and medians reported
side by side from the same run.

### The local setup was hiding a floor that does not exist

The first profiles ran the binary from the repository, which Docker Desktop
bind-mounts from Windows. Every `stat` there takes about 1 ms, so the eight
config-file probes of the first option resolved from the file layer cost 3.6
to 5.5 ms, and fan-out appeared to have a 3 ms dispatch floor. Run from the
container's own filesystem, the file layer takes 0.03-0.06 ms. Earlier local
numbers in the `laret-native-performance-targets` ADR were taken the same way
and include this cost; their ratios still hold, their absolute values do not.
The real local timeline of a 1,000-task fan-out, from spawn:

| Stage | ms |
|---|---|
| `main` entered (SubstrateVM start-up) | 1.6 |
| app built, `run` entered | +0.4 |
| command dispatch, parsing, validation | 0.35 |
| first dispatch onto `Dispatchers.Default` | 0.24 |
| 1,000 tasks | 1.4 |
| `run` returned to process gone | 1.1-1.3, of which `exit_group` teardown about 0.85 |

### Where signal latency goes

A probe binary timestamped each step on the harness's monotonic clock. With
50 spinning workers, the JVM's per-signal handler thread started 6.4-15.3 ms
after the signal, a second thread started from the handler another 7-15 ms
later, and cancelling, running every cleanup and halting took under 0.5 ms.
With no workers the whole path is about 2 ms. The cost is new threads waiting
to be scheduled behind CPU-bound ones, not cleanup work, which is why it is
flat from 50 to 2,000 workers.

### Runner RSS

With THP forced to `always`, Laret native gains 2 MiB of huge pages and 1.7 to
2.6 MiB of peak RSS: real, but not the 15 MiB difference between the runner
and the local container. The resident set itself explains more: of 16.6 MiB,
1.0 MiB is anonymous and 15.6 MiB is file-backed, the pages of the 42 MB
binary. A newer kernel that faults file pages in larger folios would inflate
exactly that part. It is not kernel 7.0 specifically: the nightlies of
2026-10-23 and 2026-10-24 ran on 7.0 and peak at the same 37-43 MiB as every
6.17 night with the same code, so the difference lies between 5.10 locally and
6.x on the runner, or in the runner's filesystem. The harness now records
anonymous, file-backed and huge-page memory in Scenario D, so the next nightly
run can confirm it. No THP opt-out
was added: at most 2.6 MiB is not worth a native `prctl` call in every app.

### Levers

| Lever | Result | Applied |
|---|---|---|
| 1. `fastSignals()`, handler on its own thread | SIGTERM 21.3 to 21.4 ms, SIGINT 17.4 to 17.5 ms with 500 workers: no gain, the extra thread start ate it | No, replaced by 1b |
| 1b. `fastSignals()`, work on the JVM's signal thread | SIGTERM 16.5 to 12.4 ms, SIGINT 16.5 to 9.3 ms with 500 workers; 2.0 to 1.7 ms with none | Yes, opt-in; the bench app opts in |
| 2. Harness memory diagnostic | THP mode in provenance; anonymous, file-backed and huge-page memory at signal time in Scenario D | Yes |
| 3a. Register the app shutdown hook only when set, and the plugin cleanup only with plugins | A run with nothing to clean up no longer starts the grace-period watchdog thread: zero-task fan-out 3.80 to 3.65 ms, N=1,000 5.21 to 4.99 ms; SIGTERM 12.4 to 9.2 ms in the same run | Yes |
| 3b. Prestart `Dispatchers.Default` workers | Upper bound 0.24 ms, the whole first dispatch | No, not worth the start-up work for commands that never dispatch |

Against HEAD in the final run: SIGTERM -45% and SIGINT -43% with 500 workers;
fan-out and pipeline unchanged within noise. Peak RSS rose 0.1 to 0.5 MiB,
from installing the signal handlers; this round's rule was not to raise it,
and the trade is recorded here rather than hidden.

### What is left

Signal latency now sits on the JVM's thread-per-signal dispatch, which no
public API avoids. Peak RSS and exit teardown both scale with the image: 9.2
MB of the code area and most of the 22 MB image heap come from what the
framework makes reachable for every app (config formats, plugins and the
rest). Making those reachable only when an app uses them is the next lever
for both.
