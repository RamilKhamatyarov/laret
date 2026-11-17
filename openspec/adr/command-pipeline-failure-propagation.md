# Command Pipeline Failure Propagation

- Status: Accepted
- Date: 2026-07-20

## Context

CommandPipeline historically executed every stage and exposed only the final captured
stdout. It discarded command exit codes, so a failed stage could be followed by commands
with side effects. Existing callers depend on execute() returning a string and on the
current in-memory stdout hand-off between stages.

## Decision

### Command status

CommandContext.exit(code) lets commands set a process status in the portable 0..255
range. The last valid call wins. Successful execution, including postExecute, returns
that status. An exception remains an execution error with status 1 and invokes onError,
even when a command previously selected another status.

### Pipeline execution

Pipelines stop at the first nonzero stage. A failed stage's stdout remains the pipeline
output, but it is never passed to another stage. Stderr remains attached directly to the
process.

PipelineResult reports:

- output: stdout from the last executed stage
- exitCode: the failed stage status, or 0
- completedStages: the number of successful stages
- failedStage: the one-based failed stage index, or null

The example pipe run command propagates the result status and writes a concise
human-readable failure diagnostic to stderr. The diagnostic is not a stable
machine-readable API.

### Compatibility

The existing CommandPipeline.execute() signature remains available. It delegates to the
status-aware execution path, preserves its string return type, and does not throw for a
nonzero stage. It now stops after the failed stage to prevent later side effects.

Dry-run pipelines use the same status and fail-fast rules.

## Non-Goals

- Streaming stage output
- Pipeline output size limits
- A continue-on-error or keep-going mode
- Shell-compatible parsing beyond the existing --- and | separators
- Capturing or transforming stderr

The current unbounded in-memory buffering model is intentionally preserved to keep this
change small and backward compatible.

## Consequences

Failed commands can no longer silently allow later pipeline stages to run. Callers that
need status details can adopt executeResult() incrementally, while existing callers
continue to compile against execute(). Large pipeline output still consumes memory
proportional to the captured stdout.
