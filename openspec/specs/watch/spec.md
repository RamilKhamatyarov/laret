# watch Specification

## Purpose

Defines the two filesystem-watch commands: `watch run`, which streams raw create, modify, and delete events from a directory, and `watch live`, which re-runs a target command whenever glob-matched files change. It covers event formats, recursive watching, glob filtering, debouncing, run caps, and clean interruption.

## Requirements

### Requirement: Event stream format

`watch run <path>` MUST write one line per filesystem event to stdout, formatted as the event type, a tab character, and the absolute path of the affected entry. The event type MUST be one of `CREATE`, `MODIFY`, or `DELETE`. A single change MAY produce several lines, so consumers MUST treat repeated lines for one path as separate notifications.

#### Scenario: File creation reported

- **WHEN** a file is created inside the watched directory
- **THEN** a line containing `CREATE`, a tab, and the file's absolute path is written to stdout

#### Scenario: File removal reported

- **WHEN** a file inside the watched directory is deleted
- **THEN** a line containing `DELETE` and the file's absolute path is written to stdout

### Requirement: Watch root, announcement, and startup state

`watch run` MUST announce the resolved root on stderr as `Watching <path> (recursive=<bool>, duration=<n>s, max-events=<n>)` before the first event. Omitting the path MUST fail with `watch run requires a directory path`; a path that is not an existing directory MUST fail with `Not a directory: <path>`. Files already present at startup MUST NOT be reported.

#### Scenario: Announcement precedes events

- **WHEN** `watch run` starts on an existing directory
- **THEN** the announcement appears before any event line

#### Scenario: Pre-existing files are not reported

- **WHEN** the watched directory already contains files at startup
- **THEN** no event lines are emitted until one of those files changes

#### Scenario: Path is not a directory

- **WHEN** `watch run` is given a regular file or a nonexistent path
- **THEN** the message `Error: Not a directory: <path>` is reported and no events are emitted

### Requirement: Recursive watching

By default only entries directly inside the root MUST be reported. With `--recursive`, changes in existing subdirectories MUST also be reported, and directories created after watching begins MUST be registered so that later changes inside them are reported.

#### Scenario: Non-recursive watch ignores nested changes

- **WHEN** a file inside a subdirectory changes without `--recursive`
- **THEN** no event line is emitted for that file

#### Scenario: Recursive watch covers existing and new directories

- **WHEN** `--recursive` is active, a file in an existing subdirectory changes, and later a file is written into a newly created subdirectory
- **THEN** both changes produce event lines with absolute paths

### Requirement: Event filtering and stop summary

`--events` MUST restrict output to a comma-separated subset of `create`, `modify`, and `delete`; omitting it emits all three. `--max-events N` MUST stop after N emitted events, where 0 means unlimited, and `--duration S` MUST stop after S seconds, where 0 means run until interrupted. Every stop MUST write `Watch stopped: <count> event(s) emitted, reason=<reason>`, with the reason being `DURATION_EXPIRED`, `MAX_EVENTS_REACHED`, `INTERRUPTED`, or `WATCH_ROOT_GONE`.

#### Scenario: Event filter suppresses other types

- **WHEN** `--events create,delete` is used and a watched file is modified
- **THEN** no `MODIFY` line is emitted, but create and delete events are emitted

#### Scenario: Event cap stops the watch

- **WHEN** `--max-events 1` is used and the first event is emitted
- **THEN** watching stops with `Watch stopped: 1 event(s) emitted, reason=MAX_EVENTS_REACHED`

### Requirement: Live watch invocation

`watch live <path> [options] -- <group> <command> [args...]` MUST re-run the command and arguments that follow the `--` separator. The path MUST be supplied: omitting it fails with `watch live: a directory path is required`. Omitting the separator or the target fails with `watch live: no target command after '--'`. Both failures exit with status 1. Matching, debounce, cancellation, and cap semantics MUST follow [the live watch ADR](../../adr/live-watch-mode.md).

#### Scenario: Target after separator runs

- **WHEN** `watch live ./src -- build compile` starts
- **THEN** the `build compile` command is executed as the target

#### Scenario: Missing target is an error

- **WHEN** `watch live` is invoked without a target after `--`
- **THEN** `watch live: no target command after '--'` is reported and the invocation exits with status 1

### Requirement: Glob pattern filtering

`--pattern` MAY be repeated. Patterns MUST use NIO glob syntax (`*`, `**`, `?`, `{a,b}`, `[a-z]`) and MUST be evaluated against the changed file's path relative to the watch root and against its file name alone. A pattern prefixed with `!` is an exclusion. A change triggers a run only when it matches at least one include (or there are no includes) and matches no exclusion.

#### Scenario: Extension pattern matches at any depth

- **WHEN** `--pattern '**/*.kt'` is used and `src/deep/File.kt` changes
- **THEN** the target is re-run

#### Scenario: Non-matching file is ignored

- **WHEN** only `**/*.kt` includes are configured and `notes.txt` changes
- **THEN** the target is not re-run

#### Scenario: Exclusion wins

- **WHEN** patterns include `**/*.kt` and exclude `!**/skip/**`, and `skip/Generated.kt` changes
- **THEN** the target is not re-run

### Requirement: Run scheduling and supersession

The target MUST run once immediately at startup and then once per qualifying change. `--debounce` sets the coalescing window in milliseconds and defaults to 150; changes inside one window MUST produce a single run. A qualifying change during a run MUST cancel that run before the next starts, so runs never overlap. The startup run MUST count toward the restart cap.

#### Scenario: Startup run is first

- **WHEN** a live session starts
- **THEN** a line identifying run #1 as a startup run appears before any change-driven run

#### Scenario: Burst is coalesced

- **WHEN** several matching files change within the debounce window
- **THEN** only one re-run is started for the burst

#### Scenario: Change during a long run

- **WHEN** a qualifying change arrives while the target is still executing
- **THEN** the current run is cancelled and the next run number starts only after it stops

### Requirement: Run outcome narration

The session MUST narrate each run to stderr: `run #<n> (startup) -> <target>` or `run #<n> (changed <relative path>) -> <target>` when starting, `run #<n> ok` when the target exits 0, and `run #<n> failed (exit <code>)` when it exits non-zero.

#### Scenario: Success narration

- **WHEN** the target exits with code 0
- **THEN** a `run #<n> ok` line is written

#### Scenario: Change trigger names the file

- **WHEN** `src/b.kt` changes and triggers a run
- **THEN** the narration names `changed b.kt`

### Requirement: Failure resilience, caps, and exit status

A non-zero exit MUST NOT end watching unless a cap applies. `--max-restarts N` MUST cap total runs, where 0 means unlimited; `--max-consecutive-failures N` MUST stop the session after N back-to-back failures, where 0 disables the cap. A run that exits 0 MUST reset that counter. On a cap-triggered stop the session MUST report `stopped after <n> run(s): <reason>`. The invocation MUST exit 0 for a restart-cap stop and 1 for a consecutive-failure stop.

#### Scenario: Failure does not end watching

- **WHEN** a target run exits with code 1 and no cap is reached
- **THEN** the failure is narrated and the session keeps watching for further changes

#### Scenario: Restart cap ends the session

- **WHEN** `--max-restarts 1` is used
- **THEN** the session stops after the startup run, reports `MAX_RESTARTS`, and exits with status 0

#### Scenario: Consecutive-failure cap ends the session

- **WHEN** `--max-consecutive-failures 1` is used and the first run fails
- **THEN** the session stops, reports `MAX_CONSECUTIVE_FAILURES`, and exits with status 1

### Requirement: Clean interruption

Ctrl-C MUST stop the session cleanly: the watch service closes, any in-flight run is cancelled, registered shutdown cleanups run, and the session reports its stop reason instead of terminating abruptly.

#### Scenario: Interrupt stops without further runs

- **WHEN** the user presses Ctrl-C during a live session
- **THEN** no further runs start and the session reports an interruption stop

### Requirement: Dry-run narration

With the global `--dry-run` flag, `watch live` MUST start the session and, for every would-be run, write `[DRY-RUN] would run: <target>` instead of executing the target. Each narrated run MUST count as successful and toward the caps.

#### Scenario: Dry run narrates instead of executing

- **WHEN** `watch live ... --dry-run -- file create out.txt` performs its startup run
- **THEN** the dry-run narration is written, the target is not executed, and the run is reported as ok

### Requirement: Watch configuration defaults

A `watch` section in the configuration file MUST supply defaults for `debounce-ms` (default 150), `patterns` (default empty, meaning all files), `max-restarts` (default 0), and `max-consecutive-failures` (default 0). Command-line flags MUST override the configured values.

#### Scenario: Config supplies the debounce window

- **WHEN** the configuration sets `debounce-ms: 200` and no `--debounce` flag is given
- **THEN** the session uses a 200 ms debounce window

#### Scenario: CLI flag beats config

- **WHEN** the configuration sets `max-restarts: 0` and the command line passes `--max-restarts 2`
- **THEN** the session stops after two runs
