# command-piping Specification

## Purpose

Defines the `pipe run` command and the pipeline execution contract: stage splitting, output carry, stdin substitution, failure propagation, and status reporting.

## Requirements

### Requirement: Pipeline invocation

`pipe run` MUST accept the tokens that follow it as a pipeline of Laret command invocations and MUST run the stages in written order. An invocation with no stages, including one containing only separators, MUST print an empty-pipeline message and exit 0 without running anything. The final output of a non-empty pipeline MUST be written to the original standard output.

#### Scenario: Two-stage pipeline

- **WHEN** the user runs `laret pipe run echo print abc --- upper convert`
- **THEN** the first stage produces `abc`, the second stage converts it, and `ABC` is written to standard output

#### Scenario: Empty pipeline

- **WHEN** the user runs `pipe run` with no stage tokens, or with separators only
- **THEN** an empty-pipeline message is printed and the process exits 0 without executing a command

### Requirement: Stage separators

`---` and `|` MUST each be recognized as a stage separator when they arrive as a standalone token. A `|` MUST be quoted in the calling shell so that the shell does not consume it. The two separators MAY be mixed in one invocation. Empty stages created by leading, trailing, or repeated separators MUST be discarded, and stage order MUST be preserved.

#### Scenario: Quoted pipe separator

- **WHEN** the user runs `laret pipe run echo print hello '|' upper convert`
- **THEN** the pipeline has two stages and prints `HELLO`

#### Scenario: Mixed separators

- **WHEN** one invocation uses both `|` and `---` as separators
- **THEN** every separator starts a new stage and the stages run in the written order

#### Scenario: Empty stages are dropped

- **WHEN** the tokens begin with, end with, or repeat a separator
- **THEN** no empty stage is executed

### Requirement: Explicit stdin substitution

In every stage after the first, each argument token exactly equal to `-` MUST be replaced by the complete captured output of the stage that ran immediately before it. The replacement MUST occur at the token's position, MUST replace every such token, and MUST NOT apply to the first stage.

#### Scenario: Substitution at a chosen position

- **WHEN** the second stage is invoked as `upper convert -`
- **THEN** the previous stage's entire output is passed as that argument

#### Scenario: First-stage dash is literal

- **WHEN** the first stage contains a `-` argument token
- **THEN** the token is passed through unchanged because no earlier output exists

### Requirement: Default stdin carry

A stage after the first that contains no `-` MUST receive the preceding stage's complete captured output on standard input as UTF-8; the first stage MUST receive the process's original standard input. A command MAY read all of standard input through the framework's stdin-capture helper, which MUST return the full text without trimming or interpreting it.

#### Scenario: Stage reads carried output from stdin

- **WHEN** a later stage declares no input argument and reads standard input
- **THEN** it receives the previous stage's output exactly as produced

#### Scenario: First stage uses original stdin

- **WHEN** the first stage reads standard input
- **THEN** it reads the process's original standard input, not pipeline output

### Requirement: Output handoff

Each stage's standard output MUST be captured instead of being printed directly, and its standard error MUST remain connected to the process. Only the last executed stage's output MUST be written to the original standard output, after execution finishes. Output from earlier stages MUST NOT be printed by the pipeline itself.

#### Scenario: Intermediate output is not printed

- **WHEN** a three-stage pipeline runs successfully
- **THEN** only the third stage's output appears on standard output

#### Scenario: Stage diagnostics remain visible

- **WHEN** a stage writes to standard error
- **THEN** that text is not captured as pipeline output and remains visible on standard error

### Requirement: Failure propagation

Execution MUST stop at the first stage whose status is nonzero, and later stages MUST NOT run. The failed stage's captured output MUST remain the pipeline output and MUST NOT be passed to another stage. The pipeline status MUST be that stage's status, or 0 when every stage succeeds. `pipe run` MUST exit with the propagated status and MUST print a concise failure diagnostic to standard error. See [command pipeline failure propagation](../../adr/command-pipeline-failure-propagation.md).

#### Scenario: Pipeline stops at the first failure

- **WHEN** stage one exits with status 1 and stage two would succeed
- **THEN** stage two does not run, the pipeline status is 1, and a failure diagnostic names stage one

#### Scenario: Successful pipeline status

- **WHEN** every stage exits 0
- **THEN** the pipeline status is 0 and no failed stage is reported

### Requirement: Programmatic stage splitting

A programmatic stage splitter MUST accept raw tokens and return the stages split on the default separators, dropping empty stages and preserving token order. A caller MAY supply a custom separator set or a single separator string. Splitting an empty token list MUST return no stages.

#### Scenario: Custom separator

- **WHEN** a caller splits tokens using a custom separator that is not `---` or `|`
- **THEN** only that separator starts a new stage

#### Scenario: Default separators

- **WHEN** a caller splits tokens without naming separators
- **THEN** both `---` and `|` are treated as separators and empty stages are omitted

### Requirement: Programmatic execution

A programmatic executor MUST accept a non-empty list of stages and MUST reject an empty list with an error. It MUST apply the same ordering, carry, substitution, and fail-fast rules as `pipe run`. One form MUST return only the final output text and MUST NOT throw when a stage fails; a result form MUST additionally return the status, the number of successful stages, and the one-based failed stage, absent on success. Both forms MUST accept a dry-run flag.

#### Scenario: Output-only form does not throw on failure

- **WHEN** a caller executes stages whose first stage fails
- **THEN** the call returns the failed stage's output rather than throwing

#### Scenario: Empty stage list is rejected

- **WHEN** a caller passes an empty stage list to the executor
- **THEN** execution is rejected with an error before any stage runs

### Requirement: Dry-run interaction

When pipeline execution is requested in dry-run mode, it MUST print a warning to standard error that pipelines may behave unexpectedly because standard output is intercepted. Dry-run MUST NOT skip stages; the same ordering, carry, failure propagation, and status rules MUST apply.

#### Scenario: Dry-run warning

- **WHEN** `pipe run` is invoked with dry-run enabled
- **THEN** standard error receives a warning about stdout interception before the stages execute

#### Scenario: Dry-run still stops at failure

- **WHEN** a dry-run pipeline's first stage fails
- **THEN** later stages do not run and the propagated status is returned

### Requirement: Streaming execution

A streaming execution mode MUST run all stages concurrently, connect consecutive stages through bounded buffers that block a fast producer once roughly 256 KiB is queued, and send the last stage's output directly to a chosen sink. In this mode the returned output MUST be empty, `-` substitution MUST NOT be available, and inter-stage output is batched so a downstream stage MUST NOT assume line-by-line delivery.

#### Scenario: Final stage writes to the sink

- **WHEN** a streaming pipeline completes
- **THEN** the chosen sink receives the last stage's output and the returned output text is empty

#### Scenario: Dash substitution unavailable

- **WHEN** a streaming stage contains a `-` argument token
- **THEN** the framework does not substitute earlier output for that token
