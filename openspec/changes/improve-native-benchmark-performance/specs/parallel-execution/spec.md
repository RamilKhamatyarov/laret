## ADDED Requirements

### Requirement: Behavior-preserving dispatcher optimization

Dispatcher optimization MUST reduce demonstrated scheduling or allocation cost without changing public APIs, default settings, submission/result ordering contracts, concurrency limits, failure propagation, or cancellation behavior. The existing unbounded mode MUST continue dispatching one coroutine per submitted unit rather than silently using a bounded pool.

#### Scenario: Unbounded optimization
- **WHEN** unbounded indexed or list-based dispatch is optimized
- **THEN** every submitted unit executes exactly once, results retain submission order, child failure cancels siblings, and caller cancellation propagates without a normal result list

#### Scenario: Bounded optimization
- **WHEN** more units than slots are dispatched with a positive concurrency limit
- **THEN** every unit executes exactly once, concurrent execution never exceeds the requested limit, results retain submission order, and a single slot preserves sequential execution

#### Scenario: Allocation candidate
- **WHEN** a candidate changes result storage or scheduling
- **THEN** existing empty-input, invalid-limit, failure, cancellation, indexed, list-based, and process-dispatch tests remain strict, and the candidate is retained only under the evidence-backed optimization acceptance policy
