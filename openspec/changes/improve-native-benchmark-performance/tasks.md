# Implementation Tasks

All tasks are open. Artifact analysis is not implementation completion. Each production candidate must be implemented, measured, and retained or rejected explicitly; diagnostics alone do not complete an optimization task.

## 1. Measurement correctness

- [ ] 1.1 Implement complete per-run sample retention and explicit invalidity in the harness; add deterministic tests for missing/nonfinite values, failed samples, mixed statuses and exact summary counts. Document schema compatibility and verify historical schema-2 inputs remain readable with limitation labels.
- [ ] 1.2 Implement minimum sample counts and balanced ordering with bounded additional noisy batches; verify scheduling/count tests cover five ordinary samples, ten cancellation/memory samples, discarded warm-ups, complete target rotations and smoke-only quick runs. Document actual counts and stopping rules.
- [ ] 1.3 Replace blocking readiness admission with deadline-aware reading and implement process-tree termination/reaping for every timeout; test silent targets, partial lines, helper failures and descendants using controlled fixtures. Verify malformed cleanup markers cannot pass.
- [ ] 1.4 Implement binary/source/build/runtime provenance and honest report labels for whole-process amortization, warm-up, wait4 RSS and display tolerances; add renderer/provenance tests and update measurement documentation without overwriting existing uncommitted work.

## 2. Fan-out optimization

- [ ] 2.1 Add separately identified dispatch-region diagnostics to the actual benchmark path and comparable reference boundaries; add bounded scenarios and tests for exactly-once execution and observed maximum concurrency. Verify unchanged unbounded body and separate whole-process reporting.
- [ ] 2.2 Establish compliant same-host baseline batches and allocation/scheduler profiles at existing scales plus positive concurrency limits; record raw evidence, protected metrics, variability rule and diagnostic overhead before evaluating candidates.
- [ ] 2.3 Implement a bounded result-storage candidate eliminating the avoidable final copy while preserving publication/null-result invariants; add regression tests for both overloads, ordering, empty inputs, invalid limits, failures and cancellation. Retain only on repeated measured benefit; otherwise revert the candidate and record rejection.
- [ ] 2.4 Implement an unbounded dispatch candidate targeting the dominant avoidable cost identified in 2.2, without replacing per-unit coroutines or removing yield. Verify full dispatcher correctness and independent interleaved batches; record retained improvement or rejected experiment, never a speculative speedup.

## 3. Cancellation optimization

- [ ] 3.1 Implement optional monotonic phase diagnostics for signal observation, cancellation initiation and cleanup completion, and distinguish helper observation from target exit. Verify phase ordering with deterministic tests and quantify diagnostic perturbation separately on Linux.
- [ ] 3.2 Implement a candidate reducing the dominant attributable framework cancellation cost; test both signals, repeated signals, reverse-order exactly-once hooks, hook failure/disposal and grace-budget behavior. Compare independent matched batches and retain only supported benefit with unchanged defaults; otherwise revert and record the measured limitation.
- [ ] 3.3 Document cancellation phase boundaries, retained/rejected candidates and runtime floors with evidence; verify no claim treats helper latency as pure cleanup time or hides marker-writing/flush cost.

## 4. Attributable memory optimization

- [ ] 4.1 Implement private/PSS and mapping attribution diagnostics with sampling timestamps, permission failures and per-run records; test parsing and unavailable fields deterministically. Document that independent peaks/medians are not additive snapshots.
- [ ] 4.2 Correlate native mapping, image-report and allocation evidence using compliant Linux runs; identify the dominant reducible cost and record the candidate and protected metrics. Verify full-featured benchmark identity remains unchanged.
- [ ] 4.3 Implement one targeted code or native-configuration candidate supported by 4.2, independently from other candidates; add applicable correctness/build tests and evaluate peak RSS, private memory and runtime together. Retain only supported benefit; otherwise revert and document rejection without claiming a memory saving.

## 5. Integrated acceptance

- [ ] 5.1 Run full existing tests, ktlint, PMD and native smoke correctness after retained changes; verify unchanged public defaults, APIs, outputs, failure and cancellation contracts. No timing or RSS threshold may fail a PR.
- [ ] 5.2 Run all benchmark scenarios on Linux with complete comparable samples; verify event-storm coalescing, pipeline counts and bounded transport, fan-out contracts, exact cleanup markers and exit statuses. Publish raw evidence and paired results, including every failed/inconclusive candidate.
- [ ] 5.3 Map every delta scenario to tests or retained measurement evidence and document unresolved limits. Verify every claimed improvement reproduces beyond the predeclared variability rule and no historical cross-host difference is attributed to code.
