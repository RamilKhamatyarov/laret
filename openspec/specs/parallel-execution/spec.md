# parallel-execution Specification

## Purpose

Defines the `parallel run` command and the bounded parallel dispatch contract: task formation, concurrency limits, output interleaving, result ordering, failure handling, and cancellation.

## Requirements

### Requirement: Task invocation

`parallel run` MUST accept one or more tasks separated by a standalone `---` token. Each task's first token MUST be the executable and its remaining tokens MUST be the process arguments, passed as received with no shell interpretation or re-splitting. Empty tasks MUST be discarded. An invocation with no tasks MUST print a no-tasks message and exit 0.

#### Scenario: Two tasks run concurrently

- **WHEN** the user runs `parallel run` with two `---`-separated external commands
- **THEN** both commands run as independent processes

#### Scenario: No tasks provided

- **WHEN** `parallel run` receives no task tokens, or only separators
- **THEN** a no-tasks message is printed and the process exits 0 without running anything

### Requirement: Concurrency limit

At most the configured number of tasks MUST run concurrently. The limit MUST default to 4, MUST be selectable with `-j` or `--jobs`, and MUST be clamped into the inclusive range 1 through 16. Tasks MUST be claimed in submission order, and a limit of 1 MUST run them sequentially in list order.

#### Scenario: Single job runs sequentially

- **WHEN** the limit is 1 and several tasks are submitted
- **THEN** each task starts only after the previous one finishes, in submission order

#### Scenario: Limits are clamped

- **WHEN** the requested limit is below 1 or above 16
- **THEN** the effective limit is 1 or 16 respectively

#### Scenario: Default limit

- **WHEN** no job limit is supplied
- **THEN** at most four tasks run concurrently

### Requirement: Streamed task output

While tasks run, every standard output and standard error line MUST be forwarded as soon as it is read and prefixed with `[<executable>] ` when quiet mode is not requested. Each line MUST be emitted as a unit, but lines from different tasks MAY interleave in completion order, and no cross-task ordering MUST be guaranteed. Quiet mode MUST suppress only these per-task lines.

#### Scenario: Output follows completion order

- **WHEN** one task finishes quickly and another finishes later
- **THEN** the quick task's lines may appear first even though it was submitted later

#### Scenario: Quiet mode

- **WHEN** `-q` or `--quiet` is supplied
- **THEN** per-task lines are not printed, while the summary and failure diagnostics remain

### Requirement: Result ordering and summary

Results MUST be returned and summarized in submission order regardless of completion order. The summary MUST report the total task count and the number of failed tasks, then one line per task giving its one-based position, exit status, standard-output line count, standard-error line count, and reconstructed command line. The summary MUST be written to standard output.

#### Scenario: Summary order is submission order

- **WHEN** tasks complete in a different order than they were submitted
- **THEN** the summary lists them in submission order with their original positions

#### Scenario: Summary counts

- **WHEN** two of three tasks exit nonzero
- **THEN** the summary reports three tasks and two failures, and each task line shows its own exit status

### Requirement: Failure isolation

A failing task MUST NOT stop other tasks from starting or completing. A task whose executable cannot be started MUST be reported with status 1 and a non-empty standard-error description rather than aborting the run. After all tasks finish, the first failed task in submission order MUST be reported on standard error. The `parallel run` command MUST exit 0 whether or not tasks fail.

#### Scenario: One failure does not stop the run

- **WHEN** one task exits nonzero and another would succeed
- **THEN** the successful task still runs and its result is reported

#### Scenario: Executable cannot be started

- **WHEN** a task names an executable that does not exist
- **THEN** that task is reported with status 1 and a standard-error description while other tasks continue

#### Scenario: Failure does not change the process status

- **WHEN** every task fails
- **THEN** the summary reports the failures and the command still exits 0

### Requirement: Task isolation

Each task MUST run as a separate operating-system process with its own exit status and its own captured standard-output and standard-error lines. Output from one task MUST NOT appear in another task's result. The working directory and environment MUST be inherited from the invoking process.

#### Scenario: Results are not mixed

- **WHEN** two tasks each write different output
- **THEN** each task's result contains only its own lines

#### Scenario: Independent exit statuses

- **WHEN** one task fails and another succeeds
- **THEN** the results report status 1 and status 0 for the respective tasks

### Requirement: Dry-run behavior

`parallel run` MUST NOT provide a dry-run mode. A `--dry-run` token MUST be treated as ordinary task text, so passing it does not preview or suppress execution and may become the executable name of the first task.

#### Scenario: Dry-run token is task text

- **WHEN** `--dry-run` is passed to `parallel run`
- **THEN** it is not interpreted as a mode and the task it belongs to attempts to run that text as its executable

### Requirement: Programmatic block dispatch

A programmatic block dispatcher MUST run in-process blocks with the same submission-order and result-order guarantees as tasks. A limit of 0 MUST dispatch one concurrent unit per block; a positive limit MUST use the bounded ordered pool; a negative limit MUST be rejected. Every submitted block's result MUST appear in submission order.

#### Scenario: All blocks at once

- **WHEN** the limit is 0 and several blocks are submitted
- **THEN** every block is dispatched concurrently and results are returned in submission order

#### Scenario: One block at a time

- **WHEN** the limit is 1
- **THEN** blocks run sequentially in submission order

#### Scenario: Negative limit rejected

- **WHEN** a negative limit is supplied
- **THEN** dispatch is rejected with an error

### Requirement: Indexed programmatic dispatch

The programmatic dispatcher MUST provide an indexed overload that accepts a task count and invokes one suspend block per index, returning results in index order. A zero concurrency limit MUST launch one coroutine per task; a positive limit MUST use the bounded ordered pool. A child failure MUST cancel sibling work. The existing list-based overload MUST retain its behavior. See [indexed fan-out dispatch](../../adr/indexed-fanout-dispatch.md).

#### Scenario: Indexed tasks preserve result order

- **WHEN** indexed blocks complete in a different order from their indices
- **THEN** results are returned in index order

#### Scenario: Indexed child failure cancels siblings

- **WHEN** an indexed block fails while sibling blocks are running
- **THEN** sibling work is cancelled and the failure is propagated

### Requirement: Cancellation

If the caller cancels dispatch, cancellation MUST propagate to the caller instead of being converted into a per-task failure with status 1, and a normal result list MUST NOT be produced for that run. A process that fails to start for any other reason MUST be reported as a task with status 1 and a standard-error description.

#### Scenario: Cancelled dispatch propagates

- **WHEN** the enclosing execution is cancelled while tasks are being dispatched
- **THEN** the cancellation reaches the caller and no completed result list is presented as the outcome

#### Scenario: Start failure is a result, not cancellation

- **WHEN** a task process cannot be started for a non-cancellation reason
- **THEN** that task appears in the results with status 1 and a standard-error description
