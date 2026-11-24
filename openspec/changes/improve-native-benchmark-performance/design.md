# Design

## Context

See proposal.md for scope. This is an implementation change specification; none of its optimization tasks is implemented by authoring these artifacts.

### Verified evidence

Archive `build/tmp/concurrency-benchmarks.zip` has SHA-256 `5f08f776b82050e08209b6e2cb9f14094a793c0487629e43a398c95fc62ff03e`. Both extracted inputs match their archive members byte-for-byte. Evidence source below, **R**, means `results.json` from that archive: select `measurements` by `target`, `scenario`, and `parameters.tasks` where applicable. Values are recomputed from raw fields, not the rendered table. Local scratch analysis is not required to interpret this document.

R provenance identifies revision `bf3ce3bc9783595c4e8a306a48b579e93ae41120`, November 22 hosted Azure EPYC run, affinity 0-3, schema 2, three ordinary repetitions and nine cancellation repetitions. All thirty aggregated rows passed their recorded checks. This does not prove unmeasured properties.

### Recomputed headline values

Every cell in this table comes from R: wall_clock_ms for A/C, settle_ms minus parameters.debounce_ms for B, cancellation_latency_ms for D. Values in milliseconds; rounding is display-only.

| Target | A tasks=1000 | A tasks=10000 | B residual | C lines=100000 | D TERM | D INT |
|---|---:|---:|---:|---:|---:|---:|
| Cobra | 2.970214 | 5.846333 | 0.455 | 17.031311 | 1.165553 | 1.127828 |
| clap | 2.227764 | 7.849267 | 1.414 | 26.055183 | 3.128007 | 2.622995 |
| picocli | 210.179437 | 252.107848 | 0.174 | 266.415700 | 29.823231 | 30.842412 |
| Laret JVM | 229.007306 | 307.140509 | 1.647 | 266.900934 | 30.714761 | 31.883188 |
| Laret native | 7.007840 | 18.931527 | 0.207 | 28.640398 | 4.779235 | 4.856323 |

R B wall_clock_ms respectively: Cobra 453.673404, clap 454.405379, picocli 646.921382, JVM 831.866072, native 460.460865. This includes deliberate quiet time, not just coalescing work.

R overhead_us_per_unit equals whole-process milliseconds multiplied by 1000 and divided by units. For A1000/A10000/C respectively, in microseconds:

| Target | A1000 | A10000 | C |
|---|---:|---:|---:|
| Cobra | 2.970214 | 0.5846333 | 0.17031311 |
| clap | 2.227764 | 0.7849267 | 0.26055183 |
| picocli | 210.179437 | 25.2107848 | 2.66415700 |
| Laret JVM | 229.007306 | 30.7140509 | 2.66900934 |
| Laret native | 7.007840 | 1.8931527 | 0.28640398 |

Peak MiB below equals R peak_rss_kb / 1024. These are medians of independent high-water marks, not simultaneous resident snapshots.

| Target | A1000 | A10000 | B | C | D TERM | D INT |
|---|---:|---:|---:|---:|---:|---:|
| Cobra | 5.801 | 6.863 | 5.785 | 7.613 | 6.734 | 6.785 |
| clap | 3.867 | 6.945 | 3.578 | 3.602 | 3.637 | 3.570 |
| picocli | 128.461 | 153.820 | 128.504 | 135.402 | 153.691 | 154.645 |
| Laret JVM | 134.555 | 139.102 | 164.813 | 152.645 | 141.348 | 140.473 |
| Laret native | 29.148 | 35.539 | 43.504 | 41.641 | 38.000 | 38.027 |

Native/reference ratios, computed from unrounded R values. Entries follow headline column order; B uses residual, D uses cancellation latency.

| Reference | A1000 | A10000 | B | C | TERM | INT |
|---|---:|---:|---:|---:|---:|---:|
| Cobra | 2.3594 | 3.2382 | 0.4549 | 1.6816 | 4.1004 | 4.3059 |
| clap | 3.1457 | 2.4119 | 0.1464 | 1.0992 | 1.5279 | 1.8514 |
| picocli | 0.0333 | 0.0751 | 1.1897 | 0.1075 | 0.1603 | 0.1575 |
| Laret JVM | 0.0306 | 0.0616 | 0.1257 | 0.1073 | 0.1556 | 0.1523 |

Native/reference peak RSS ratios, same order:

| Reference | A1000 | A10000 | B | C | TERM | INT |
|---|---:|---:|---:|---:|---:|---:|
| Cobra | 5.0249 | 5.1781 | 7.5199 | 5.4695 | 5.6427 | 5.6045 |
| clap | 7.5374 | 5.1170 | 12.1583 | 11.5618 | 10.4490 | 10.6510 |
| picocli | 0.2269 | 0.2310 | 0.3385 | 0.3075 | 0.2472 | 0.2459 |
| Laret JVM | 0.2166 | 0.2555 | 0.2640 | 0.2728 | 0.2688 | 0.2707 |

R cancellation_latency_samples_ms yields native TERM range 2.886502-7.512357 ms and INT 3.309940-6.664030 ms. Raw non-cancellation timing and RSS samples are unavailable, so their variability cannot be reconstructed. R native TERM rss_anon_kb/1024 is 3.75390625 MiB, rss_file_kb/1024 is 33.32421875 MiB, anon_huge_pages_kb is zero. INT is 3.77734375/33.32421875/zero. Other scenarios have null component fields, not measured zero. Independent medians and sampling times prevent additive reconstruction of peak RSS. File-backed residence is not binary disk size, private heap, or PSS.

### Measurement and code evidence

- `benchmarks/harness/rusage_exec.c:28,57,70` uses a monotonic fork/wait interval and wait4 ru_maxrss. Despite renderer prose, peak memory is not currently obtained from VmHWM. Whole-process timing includes startup and termination.
- `benchmarks/harness/harness.py:414-442` combines independent medians; only cancellation samples survive aggregation. Default repetition policy at line 522 does not meet the existing measurement-policy ADR for ordinary scenarios.
- `benchmarks/harness/harness.py:307` blocks on readiness readline, so its surrounding deadline cannot bound a silent target. `run_once` timeout at line 169 kills the helper without equivalent process-group cleanup. Cancellation waits for helper observation, including helper bookkeeping and observer scheduling, not solely target termination.
- `src/bench/kotlin/io/github/laretframework/bench/BenchCommands.kt:77-86` already invokes indexed dispatch with a yielding payload. `ParallelDispatcher.kt:72-126` launches a coroutine per index in unbounded mode; the bounded path allocates a nullable results list and copies it through map. **Hypothesis:** scheduling/allocation cost contributes to A, but no profile in R apportions that cost.
- `BenchCommands.kt:108,183` uses actual watch and pipeline framework paths, whereas Go/Rust provide channel-based payloads (`benchmarks/cobra/main.go:169`, `benchmarks/clap/src/main.rs:205`). Picocli uses virtual-thread execution (`benchmarks/picocli/src/main/java/benchmarks/picocli/BenchMain.java:62`). Equivalent outputs are useful correctness comparisons, not equivalent implementation work or isolated CLI-library tax.
- `CancellationScope.kt:70-102` cancels the job, snapshots cleanup registrations, starts a watchdog, and runs suspending cleanups. `FastSignals.kt:39-61` already runs cleanup on the signal-handler path, flushes, and halts. **Hypothesis:** handler scheduling and cancellation traversal may dominate under yielding workers; R has no phase timestamps to distinguish them from cleanup, marker writing, flushing, or observer delay.
- `CommandRunner.kt` builds and validates context, resolves middleware and invokes actions before benchmark internals; `BenchMain.kt` uses full `cli` plus opt-in fast signals. Native settings at `build.gradle.kts:155-165` already use quick build optimization, serial GC and a capped young generation. **Hypothesis:** image mappings and runtime allocations have different reduction levers; the component snapshot does not identify individual mappings or reachable features.
- Warm-up is a separate discarded process; it cannot warm JIT state of subsequent JVM processes. Rotating targets does not balance all positions unless repetition count completes a rotation cycle. Affinity limits CPU placement, but does not establish equal runtime worker counts or equivalent scheduler behavior. A streaming mode token alone does not prove bounded transport.

### Historical document comparison

The working `docs/benchmarks.md` reports R; HEAD records a different Docker/LinuxKit host, Intel CPU and affinity 0-1. HEAD native A10000 is 31.64 ms, C 74.82 ms, TERM 24.69 ms and D peak RSS 19.5 MiB; R reports the corresponding values in the tables above. Source: HEAD document's target table and provenance, compared using git diff. Runtime versions, repetition counts, host/kernel and CPU placement differ. **Evidence:** these are different observations. **Unproven:** any causal code speedup, kernel-induced memory increase, or effect size attributable to a particular optimization. Historical values are not the optimization acceptance baseline.

## Goals / Non-Goals

Implement demonstrable improvements with preserved contracts. Do not merely add diagnostics and call the optimization complete. Do not promise cross-language parity, a fixed percentage gain, statistical significance from a small range, or an unattainable universal runtime floor. Existing aspirational ratios remain informational. The current artifact is prioritization evidence, not a qualifying before/after baseline.

## Decisions

### P0: Repair comparison validity before accepting production candidates

**Classification:** measurement fix. **Effort:** medium. **Risk:** result-schema compatibility and additional runtime.

Modify `harness.py`, `rusage_exec.c`, `render.py` and harness tests during implementation, preserving current unrelated work. Retain complete per-run records with validity and provenance. Use a versioned additive representation or explicit version transition; keep historical data readable but label limitations. Bound readiness without blocking reads and reap groups on all timeouts. Report actual accepted counts. Require complete balanced rotations, at least five samples ordinarily and at least ten for cancellation and memory; collecting ten across the suite is a simple compliant default. Increase noisy cases in balanced batches with a declared maximum; unresolved variability stays inconclusive, not selectively trimmed.

Expected effect: trustworthy attribution, not a claimed speedup. Verify synthetic timeouts, descendants, malformed markers, nonfinite/missing measurements, summaries and historical artifacts deterministically. Record binary hashes, build flags and effective runtime parallelism rather than assuming affinity guarantees scheduler equivalence.

### P1: Optimize dispatch storage and measured scheduling cost

**Classification:** code change plus measurement fix. **Effort:** medium. **Risk:** ordering and structured cancellation.

Target R native A10000 3.2382x Cobra / 2.4119x clap, not the already-competitive C path. Add an opt-in diagnostic interval immediately around existing dispatch through completion in `BenchCommands.kt`, without replacing its actual framework invocation or yielding body. Keep cold whole-process metrics and distinguish instrumentation-enabled runs. Extend all reference targets with equivalent region boundaries where comparing regions.

Implement and evaluate eliminating the bounded result-list copy in `ParallelDispatcher.kt:111-126`, preserving safe publication and the existing null-result invariant. Then profile unbounded task allocations/scheduler events and implement only an identified avoidable cost. Already-indexed dispatch is not a new candidate. Do not introduce batching that breaks one-coroutine-per-unit semantics or remove yield. Test indexed/list overloads, bounded concurrency, ordering, empty inputs, failures and cancellation. Verify both existing scales and positive-limit runs with more tasks than slots. Expected effect: lower dispatch allocation/time; size of benefit remains unknown until controlled Linux runs.

### P1: Optimize attributable cancellation work

**Classification:** code change after measurement. **Effort:** medium/high. **Risk:** shutdown races and altered cleanup semantics.

Target R native TERM/INT gaps to Cobra, while acknowledging overlapping native/clap ranges. Instrument handler entry, cancellation initiation and cleanup completion in `FastSignals.kt`, `CancellationScope.kt` and bench diagnostics on a common time basis; preserve uninstrumented headline runs and measure instrumentation perturbation. Separate helper observation from target-exit evidence. Implement reduction of the dominant avoidable cancellation/cleanup cost if attribution supports it, with job traversal, cleanup snapshot allocation and watchdog startup as candidates, not established causes. Do not spawn another handler thread, skip hooks, weaken deadlines, or change defaults. Verify single/repeated signals, cleanup exceptions/disposal, caller cancellation and exact exit codes. Expected effect: reduced attributable latency; native runtime signal-delivery cost may remain beyond framework control. An unfixable measured floor is a documented limit, not permission to bypass safety.

### P2: Reduce the dominant attributable memory cost

**Classification:** diagnostic measurement followed by code or configuration change. **Effort:** high. **Risk:** increased startup, GC cost, changed image behavior.

Target R native RSS ratios rather than assuming heap dominates. Add pre-signal private/PSS and mapping attribution on Linux, with unavailable permissions explicit. Correlate file mappings with native-image code/heap reports; collect allocation/GC evidence separately for dispatch. Implement one independently measured, behavior-preserving reduction in the dominant attributable component. Evaluate narrower allocation lifetime/result storage before changing native GC settings. Any build-flag experiment keeps Community GraalVM and is measured independently. Do not initiate lean-core, reflection removal or heap trimming under this change. Expected effect: lower attributable memory and a separately reported peak-RSS effect, not an unsupported absolute RSS promise.

### Acceptance and rejection rule

Use same-host interleaved baseline/candidate runs with matched inputs, build settings and affinity. Predeclare affected and protected metrics, batch counts, variability method and stopping limit. Require independent repeated batches to show a consistent benefit beyond the chosen observed-variability rule, with no repeatable protected-metric worsening; ranges alone are not confidence intervals. Preserve all failed and unsuccessful trials. Retain only supported changes, revert candidates otherwise, and document the outcome per path. No production optimization task is marked successful merely because instrumentation exists. Pipeline and storm remain regressions; optimize them only on new bottleneck evidence. Correctness and harness validity gate; shared-runner timing/RSS never gate PRs.

## Risks / Trade-offs

- More samples and diagnostics increase runtime; keep diagnostic and headline runs identified and separate.
- A file-backed reduction may not reduce private cost; retain both views rather than substituting a favorable metric.
- Runtime scheduling may dominate cancellation; record an evidence-backed limit if no compatible framework reduction survives evaluation.
- Existing dirty harness edits can conflict; integrate during implementation without overwriting their intent.
- Linux-only execution is unavailable here. No allocation profile, phase attribution, private-memory result, before/after improvement, or reproduced benchmark is claimed.

## Migration Plan

Land measurement correctness first with deterministic tests and updated metric documentation. Establish a new compliant baseline, then evaluate each dispatch, cancellation and memory candidate separately. Keep existing result readers compatible or explicitly version them. Retain successful changes individually; revert unsuccessful production candidates while retaining their evidence. Run full correctness and quality gates before reporting completion.

## Open Questions

The exact dominant unbounded scheduling cost, private mapping composition, instrumentation perturbation and signal-phase breakdown require the specified Linux measurements. These determine which compatible candidate survives, not whether to preserve behavior or how acceptance works.
