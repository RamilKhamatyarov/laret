# Evidence-Backed Native Benchmark Optimization

## Why

The verified November benchmark exposes fan-out, cancellation, and resident-memory gaps, but its sampling and metric boundaries cannot establish their causes. Implement reliable measurement first, then optimize the actual dispatcher, cancellation, and attributable memory costs without changing public behavior.

## What Changes

- Retain complete samples, enforce comparable measurement conditions, correct metric labels, and make harness failures bounded and observable.
- Measure dispatch separately from whole-process execution; exercise both unbounded and explicitly bounded fan-out.
- Implement and evaluate allocation/scheduling improvements in the existing dispatcher, retaining only demonstrated improvements.
- Separate signal delivery, application cancellation, cleanup, and process-exit observations; implement evidence-supported cancellation improvements without skipping cleanup.
- Attribute resident memory before implementing a targeted reduction; preserve peak RSS alongside private-memory diagnostics.
- Keep pipeline and event-storm scenarios as strict correctness and informational performance regressions.
- Reject unsuccessful optimization candidates with recorded results rather than retaining speculative changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `native-image-and-performance`: Strengthen measurement, comparison, attribution, and optimization acceptance requirements.
- `parallel-execution`: Require evidence-backed dispatcher optimization while retaining all existing execution guarantees.

## Impact

Implementation will affect the benchmark harness and targets, dispatcher internals, and only those cancellation/native configuration paths supported by measurements. No new runtime dependency, public API change, default change, or runtime-specific behavior is authorized. Timing and memory remain informational, not PR gates.

This change contains implementable requirements and implementation tasks, not a read-only research deliverable. Creating this proposal does not implement those tasks. Existing uncommitted benchmark documentation and harness edits remain untouched.

Out of scope: replacing coroutines, changing unbounded execution into bounded execution, weakening cleanup, enabling fast signals by default, a lean-core migration, dependency removal, alternate GraalVM editions, PGO, G1, musl, marketplaces, and unrelated optimization work.
