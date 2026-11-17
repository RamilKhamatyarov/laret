# stats-metrics Specification

## Purpose

Defines command-execution metrics: what is recorded for each command, where the counters are persisted, the shape of the Prometheus, JSON, and plain renderings, reset scope, enable/disable behavior, and concurrency guarantees.

## Requirements

### Requirement: Recorded metrics

For every group and command pair the collector MUST accumulate the execution count, success count, failure count, cumulative duration, last duration, last exit code, and last execution timestamp. An exit code of 0 MUST count as a success; any other exit code MUST count as a failure. Negative duration contributions MUST be clamped to zero.

#### Scenario: Successful run counted

- **WHEN** a command exits with code 0
- **THEN** its count and success count each increase by one and its last exit code becomes 0

#### Scenario: Failed run counted

- **WHEN** a command execution throws
- **THEN** its count and failure count each increase by one and its last exit code becomes 1

#### Scenario: Durations accumulate

- **WHEN** the same command runs twice
- **THEN** its cumulative duration is the sum of both runs and its last duration is the second run's duration

### Requirement: Persistence and loading

Counters MUST persist as JSON in `~/.laret/stats.json`, overridable with the `LARET_STATS_FILE` environment variable, and MUST be loaded when a process starts so totals survive invocations. Every update MUST rewrite the whole file, replacing it atomically when the platform supports it. Unreadable or malformed state MUST be ignored instead of failing the command.

#### Scenario: Counters survive a process exit

- **WHEN** one invocation records a command and a later invocation reads the statistics
- **THEN** the earlier counts and start timestamp are present

#### Scenario: Corrupt state is ignored

- **WHEN** the statistics file contains invalid JSON
- **THEN** the command still runs and reporting starts from an empty state

#### Scenario: Missing parent directory is created

- **WHEN** the statistics file's parent directory does not exist
- **THEN** it is created before the first write

### Requirement: Enable and disable

When `LARET_STATS_DISABLED` is set to `1`, `true`, `yes`, or `on`, compared case-insensitively after trimming, recording MUST be a no-op while snapshots remain readable and report the collector as disabled (`laret_stats_enabled 0` in Prometheus, `enabled: false` in JSON).

#### Scenario: Disabled collector skips recording

- **WHEN** statistics are disabled and a command runs
- **THEN** the persisted counters are unchanged

#### Scenario: Snapshot reports disabled state

- **WHEN** a report is rendered while statistics are disabled
- **THEN** the output states that collection is disabled

### Requirement: Prometheus output

`stats show` MUST default to the Prometheus text exposition format. It MUST emit, in fixed order, `laret_command_executions_total` with `result="success"` and `result="failure"` samples, `laret_command_duration_milliseconds_total`, `laret_command_last_duration_milliseconds`, `laret_command_last_exit_code`, `laret_command_last_execution_timestamp_seconds`, then the global `laret_stats_started_timestamp_seconds` and `laret_stats_enabled` gauges.

#### Scenario: Two samples per command

- **WHEN** a command has been recorded in both outcomes
- **THEN** the executions counter has one success sample and one failure sample for it

#### Scenario: Timestamps rendered in seconds

- **WHEN** a last execution timestamp is rendered
- **THEN** it appears as whole Unix seconds, truncated from milliseconds

### Requirement: Prometheus series ordering and escaping

Prometheus series MUST be sorted by group and then command so repeated exports are stable. Each metric MUST be preceded by its `# HELP` and `# TYPE` lines, and label values MUST escape backslashes, double quotes, and newlines. When no command has been recorded, the output MUST contain only the metric headers and the two global gauges.

#### Scenario: Deterministic series order

- **WHEN** statistics exist for several commands
- **THEN** series appear in group-then-command order regardless of recording order

#### Scenario: Empty store

- **WHEN** no command has been recorded
- **THEN** only `# HELP`/`# TYPE` lines and the `laret_stats_started_timestamp_seconds` and `laret_stats_enabled` gauges are written

### Requirement: JSON output

`stats show --format json` MUST render a pretty-printed object with `enabled`, `startedAtEpochMs`, `totalCommandCount`, and a `commands` array sorted by group and command. Each entry MUST carry `group`, `command`, `count`, `successCount`, `failureCount`, `totalDurationMs`, `lastDurationMs`, `lastExitCode`, and `lastTimestampEpochMs`.

#### Scenario: JSON entry per command

- **WHEN** JSON output is requested after a command has run
- **THEN** one entry exists for that group and command with all listed fields

#### Scenario: Totals reported

- **WHEN** several commands have run
- **THEN** `totalCommandCount` equals the sum of their counts

### Requirement: Plain output

`stats show --format plain` MUST render `Enabled: <bool>`, `Started: <yyyy-MM-dd HH:mm:ss> UTC`, and `Total commands: <n>`, followed by a blank line and a column-aligned table with the headers `GROUP`, `COMMAND`, `COUNT`, `OK`, `FAIL`, `TOTAL_MS`, `LAST_MS`, and `LAST_EXIT`. With no recorded commands it MUST write `No commands recorded yet.` after the totals.

#### Scenario: Table rows aligned

- **WHEN** plain output lists commands of different name lengths
- **THEN** each column is padded to the width of its widest value

#### Scenario: Empty plain report

- **WHEN** no command has been recorded
- **THEN** the report ends with `No commands recorded yet.`

### Requirement: Resetting statistics

`stats reset` MUST clear every recorded command, set the collection start timestamp to the current time, persist the empty state, and report completion. `stats show --reset` MUST render the current snapshot first and then perform the same reset. The invocation that performs the reset MUST itself be recorded afterwards, so a later report lists it.

#### Scenario: Reset clears previous counters

- **WHEN** `stats reset` runs after commands were recorded
- **THEN** a later report shows no entries from before the reset

#### Scenario: Show then reset

- **WHEN** `stats show --reset` runs
- **THEN** the pre-reset snapshot is printed, the state is cleared, and the command reports the reset

#### Scenario: Reset is itself recorded

- **WHEN** `stats reset` completes and a later report is rendered
- **THEN** the reset invocation appears with its own count

### Requirement: Recording scope and exclusions

Every command execution that reaches its action MUST be recorded, including dry runs, framework commands such as `stats show`, and commands re-run by `watch live`. A command rejected before execution, such as one missing a required argument, MUST NOT be recorded. Displaying help executes no command and MUST NOT be recorded. An action that prints an error but returns normally MUST count as a success.

#### Scenario: Dry run recorded

- **WHEN** a command runs with `--dry-run`
- **THEN** its execution is counted like any other run

#### Scenario: Invalid invocation not recorded

- **WHEN** a command is rejected for a missing required argument
- **THEN** no metric is added for it

#### Scenario: Printed error is not a failure

- **WHEN** an action reports an error but exits with code 0
- **THEN** the execution is counted as a success

### Requirement: Concurrency and serialization

Reads and mutations MUST be serialized by a single lock so that a snapshot is internally consistent and no update is lost within one process. The persisted file MUST always contain a complete document. Processes do not lock the file, so concurrent invocations can overwrite each other's updates and the last writer wins.

#### Scenario: Consistent snapshot under concurrent updates

- **WHEN** several command executions record concurrently while a report is rendered
- **THEN** the rendered snapshot reflects a consistent state in which each completed record is applied exactly once

#### Scenario: Concurrent processes

- **WHEN** two processes record at the same time
- **THEN** each writes a complete document and the later write replaces the earlier one

### Requirement: Middleware integration and priority

Metrics MUST be collected by a global middleware with priority -1000, making it the outermost wrapper of every command, so the measured duration includes work done by all other middleware. The record MUST be written even when execution throws, and the exception MUST be rethrown unchanged.

#### Scenario: Duration includes other middleware

- **WHEN** other middleware performs work before the action runs
- **THEN** the measured duration covers the whole wrapped execution

#### Scenario: Throwing command still recorded

- **WHEN** the action or a later middleware throws
- **THEN** the failure is recorded and the exception propagates

### Requirement: Format selection and errors

`--format`/`-f` MUST accept `prometheus`, `json`, and `plain`, matched case-insensitively, and MUST default to `prometheus`. An unsupported value MUST be reported as `Error: unknown stats format: <value> (expected prometheus, json, plain)` and MUST NOT change the exit status.

#### Scenario: Unknown format reported

- **WHEN** `stats show -f yaml` is invoked
- **THEN** the unknown-format message is reported and the command exits with status 0

#### Scenario: Format is case-insensitive

- **WHEN** `stats show -f JSON` is invoked
- **THEN** JSON output is produced
