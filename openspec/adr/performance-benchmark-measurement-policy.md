# Performance Benchmark Measurement Policy

- Status: Accepted
- Date: 2026-10-31

## Context

Laret has multiple performance benchmark suites, including concurrency and
cold-start measurements. Their metrics and workload contracts differ, but
repetition counts, variability reporting, and interpretation of shared-runner
results should be consistent. Recent concurrency results use three samples for
most scenarios and nine for cancellation; the newest result archive also
captures per-signal ranges and RSS composition. Single-run and quick-mode data
cannot support performance conclusions with the same confidence as repeated
measurements.

## Decision

Apply one measurement policy to every performance benchmark suite while
keeping each suite's scenarios and metrics distinct:

- A reported performance result has at least five timed samples per measured
  case, after a discarded warm-up. Increase the sample count for metrics with
  meaningful run-to-run variability, especially latency and RSS, and record
  the count used.
- Preserve individual samples in machine-readable results. Summaries report
  the median and observed range; suite-specific summaries may add metrics such
  as percentiles when supported by enough samples.
- Record provenance needed to assess comparability: revision, timestamp,
  operating system and kernel, CPU and affinity, relevant memory settings,
  runtime/compiler versions, build flags, workload parameters, warm-up and
  sample counts.
- Keep correctness assertions strict. Timing and memory targets are reported
  for trend analysis and do not fail pull requests on shared runners.
- Treat reduced-scale or single-sample quick runs as correctness smoke checks,
  not comparable performance results. Do not use them to update performance
  claims or evaluate targets.
- Compare results only when suite, scenario, scale, target, and material
  measurement conditions match. Keep suite-specific metrics separate rather
  than combining unlike measures into one score.

## Consequences

Performance reports become easier to interpret and compare across suites, and
raw samples make unusually noisy results visible instead of hiding them behind
a single aggregate. More repetitions increase benchmark runtime, especially
for cancellation and cold-start suites. Shared-runner variation still limits
the conclusions; provenance and ranges describe that uncertainty but do not
eliminate it. Historical reports with fewer samples remain useful as records,
but should not be presented as equivalent to results produced under this
policy.
