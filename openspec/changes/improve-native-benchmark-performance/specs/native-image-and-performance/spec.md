## ADDED Requirements

### Requirement: Complete comparison samples

Performance comparisons MUST retain every timed sample with its checks, workload, target identity, measurement boundary, and failure status. Reports MUST derive counts, medians, and observed ranges from retained valid samples, distinguish unavailable values from zero, and display failed samples without silently replacing them.

#### Scenario: Incomplete measurement
- **WHEN** a sample times out, lacks a required measurement, or contains a non-finite value
- **THEN** it is retained as invalid, correctness or harness validity fails, and no successful comparison is inferred from the remaining samples

#### Scenario: Comparable sample counts
- **WHEN** a full comparison is published
- **THEN** each scenario has at least five timed samples after a separately identified discarded warm-up, and cancellation and memory comparisons have at least ten samples per case with balanced target positions

#### Scenario: Historical artifact
- **WHEN** a historical artifact lacks complete samples or meets neither sample minimum
- **THEN** its numbers remain readable as historical evidence, but are not presented as meeting the current comparison policy

### Requirement: Honest execution boundaries

Reports MUST distinguish whole-process time from in-process dispatch time. Whole-process time divided by task or line count MUST be labeled amortized whole-process cost, not isolated framework overhead. Separate-process warm-up MUST NOT be described as warming the measured process's JIT.

#### Scenario: Fan-out comparison
- **WHEN** fan-out performance is reported
- **THEN** both whole-process time and the interval from dispatch through completion are separately reported for the unchanged unbounded workload and an explicitly bounded workload with more tasks than slots

#### Scenario: Scenario interpretation
- **WHEN** event-storm or pipeline figures are rendered
- **THEN** imposed quiet time is distinguished from residual settle latency, and a streaming claim requires verified bounded transport and correctness rather than only a target's mode label

### Requirement: Bounded and attributable measurement

The harness MUST bound readiness and completion waits, terminate and reap measured process trees on timeout, and distinguish target termination from helper observation. Cancellation phase measurements MUST use a documented common monotonic time basis or explicitly report independent intervals without subtracting incompatible clocks.

#### Scenario: Silent target
- **WHEN** a target emits no complete readiness line or never exits
- **THEN** the configured deadline expires, the run fails visibly, and no target descendant remains running

#### Scenario: Cancellation attribution
- **WHEN** cancellation is measured for either supported signal
- **THEN** signal-to-exit remains the headline, while application observation and cleanup completion are separately attributable; instrumentation overhead and the helper-observation boundary are disclosed

### Requirement: Attributable memory reduction

Memory optimization MUST target an identified cost using peak RSS and separately identified private-memory and mapping diagnostics. Point-in-time anonymous, file-backed, huge-page, and proportional or private measurements MUST retain their sampling time and availability; they MUST NOT be summed with independent peak or median observations as one coherent snapshot.

#### Scenario: Choosing a memory optimization
- **WHEN** diagnostics identify the dominant reducible private or mapped-memory cost
- **THEN** a targeted implementation is evaluated against the same baseline with both peak RSS and private-memory results retained, and a shared-mapping reduction is not claimed as an equivalent private-memory saving

### Requirement: Evidence-backed optimization acceptance

Dispatcher, cancellation, and attributable memory optimizations MUST be implemented and evaluated against interleaved baseline/candidate runs on the same host and configuration. Retained production changes MUST show repeatable benefit beyond observed variability without correctness regressions or a repeatable worsening of other protected performance metrics. Unsuccessful candidates MUST be rejected and their evidence recorded; investigation alone MUST NOT be reported as completed optimization.

#### Scenario: Successful candidate
- **WHEN** independent comparable batches reproduce a candidate's benefit beyond their observed variability
- **THEN** the candidate may be retained with raw evidence, its affected metric, trade-offs, and unchanged behavior checks

#### Scenario: Inconclusive candidate
- **WHEN** differences are inconsistent, within observed variability, or obtained under materially different environments
- **THEN** no performance benefit is claimed and the speculative production change is not retained

#### Scenario: Cancellation correctness
- **WHEN** a cancellation optimization is evaluated
- **THEN** both signals retain their existing exit codes, exactly-once reverse-order cleanup, grace-budget behavior, and repeated-signal semantics without making fast signal handling the default

#### Scenario: Non-gating performance
- **WHEN** timing or memory targets are missed on a shared runner
- **THEN** the miss is reported without failing the PR; correctness and measurement-validity failures remain strict

### Requirement: Comparable provenance and claims

Comparisons MUST identify measured binary hashes, source revision and dirty state, build flags, runtime/compiler versions, CPU affinity, effective runtime parallelism, relevant memory settings, host and kernel, workload, and actual sample counts. Material differences MUST prevent causal claims about code changes. Missing provenance MUST be explicitly reported.

#### Scenario: Different hardware
- **WHEN** an older Docker run and a newer hosted-runner result differ
- **THEN** each remains a distinct observation and neither speedup nor memory regression is attributed to code without a controlled comparison

#### Scenario: Cross-runtime comparison
- **WHEN** different language stacks use their native schedulers and pipeline transports
- **THEN** the report describes an application-stack comparison, records those differences, and does not claim to isolate CLI-library overhead or statistical equivalence from a display tolerance
