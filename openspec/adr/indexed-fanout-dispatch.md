# Indexed In-Process Fan-Out

- Status: Accepted
- Date: 2026-11-09

## Context

The Nov 7 full-scale benchmark reported Laret native fan-out at 25.17 ms for
10,000 tasks, versus 7.30 ms for Cobra and 12.32 ms for clap. Its Kotlin
benchmark first materialized a list containing one suspend lambda per task;
the Go and Rust payloads launch work directly from an index. The existing
`executeBlocks(List, maxJobs)` API remains useful and must keep its behavior.

## Decision

Add an indexed `executeBlocks(taskCount, maxJobs, block)` overload. It invokes
one indexed suspend block per task and returns results in index order. With
`maxJobs = 0`, it still launches one coroutine per task; positive limits use
the existing ordered worker pool. A child failure continues to cancel sibling
work. Keep the list-based overload unchanged and migrate only the fan-out
benchmark payload to the indexed overload, so task-list construction is not
included in this comparison.

## Measurement

The Nov 7 archive is a prioritization baseline, not proof of a speedup: fan-out
uses three repetitions, below the accepted five-sample minimum, and the JSON
does not retain raw wall-clock samples. The Nov 3 archive is a one-sample quick
run and is not comparable. No post-change performance result is claimed here.
Measure the same workload at five or more timed samples, after warm-up, and
report median and range; performance remains informational, not a CI gate.

## Consequences

Callers that already have a count and indexed work can avoid allocating a list
of per-task suspend lambdas. The new overload still allocates one coroutine per
task in unbounded mode, so scheduler and result-storage costs remain. The old
API remains available with its current semantics. Pipeline, event storm,
cancellation, and JVM startup are outside this decision.
