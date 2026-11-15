# dry-run Specification

## Purpose

Defines the global `--dry-run` mode: how side-effecting filesystem operations are intercepted, narrated, and withheld, while reads and all other command behavior remain unchanged.

## Requirements

### Requirement: Global dry-run flag

`--dry-run` MUST be accepted as a global flag on any invocation and MUST have no short form. When present, the invocation MUST run in dry-run mode, the flag MUST be consumed by the framework rather than being delivered to the command as an argument or option value, and the command action MUST run normally.

#### Scenario: Flag accepted after command inputs

- **WHEN** a user runs `laret file create notes.txt --content hello --dry-run`
- **THEN** the command action runs in dry-run mode and `notes.txt` is not written

#### Scenario: No short form

- **WHEN** a user passes `-n` instead of `--dry-run`
- **THEN** dry-run mode is not enabled and `-n` is reported as an unknown flag

#### Scenario: Action is still invoked

- **WHEN** a command runs under `--dry-run`
- **THEN** its action executes, so user-visible output and non-filesystem behavior are those of a normal run

### Requirement: Intercepted filesystem operations

Filesystem mutations performed through the execution context's filesystem accessor MUST be routed to a real implementation in a normal run and to a narrating, non-mutating implementation under `--dry-run`. The intercepted operations MUST be writing text, deleting a file, and creating directories; command actions MUST NOT need to check a dry-run flag to avoid side effects.

#### Scenario: Write is withheld

- **WHEN** a dry run writes text to a path
- **THEN** no file is created and no existing file is modified

#### Scenario: Delete is withheld

- **WHEN** a dry run deletes an existing file
- **THEN** the file still exists afterward

#### Scenario: Directory creation is withheld

- **WHEN** a dry run creates directories
- **THEN** no directory is created

### Requirement: Journal of suppressed operations

Each intercepted operation MUST produce exactly one narration line on standard error and MUST keep standard output free of narration. The narrations MUST be, verbatim:

- `[DRY-RUN] Would write <bytes> bytes to <path>`
- `[DRY-RUN] Would delete <path>`
- `[DRY-RUN] Would create directories <path>`

`<bytes>` MUST be the UTF-8 byte length of the content, and `<path>` the path the operation addressed. A suppressed delete MUST report success to its caller.

#### Scenario: Write narration

- **WHEN** a dry run writes the content `hello` to `notes.txt`
- **THEN** standard error receives `[DRY-RUN] Would write 5 bytes to notes.txt`

#### Scenario: Multi-byte content

- **WHEN** a dry run writes content containing non-ASCII characters
- **THEN** the reported byte count is the UTF-8 length, not the character count

#### Scenario: Delete narration

- **WHEN** a dry run deletes an existing path
- **THEN** standard error receives `[DRY-RUN] Would delete <path>` and the delete reports success

#### Scenario: Directory narration

- **WHEN** a dry run creates a directory tree
- **THEN** standard error receives `[DRY-RUN] Would create directories <path>`

#### Scenario: Normal runs stay quiet

- **WHEN** a command writes or deletes in a normal run
- **THEN** no `[DRY-RUN]` line is produced

### Requirement: Reads remain real

Reads through the filesystem accessor MUST behave identically in both modes: existence checks, directory checks, text reads, and directory listings MUST query the real filesystem, so a dry run observes the actual disk state.

#### Scenario: Existence check in a dry run

- **WHEN** a dry run checks whether an existing file exists
- **THEN** the check reports `true` exactly as in a normal run

#### Scenario: Read of a missing file

- **WHEN** a dry run reads a file that does not exist
- **THEN** the read fails exactly as it would in a normal run

#### Scenario: Listing a directory

- **WHEN** a dry run lists a directory
- **THEN** it receives the real entries, sorted, as in a normal run

### Requirement: Piped invocations

When a pipeline runs under `--dry-run`, the application MUST print `[WARNING] Pipelines in --dry-run mode may behave unexpectedly due to stdout interception.` to standard error, and MUST still execute every stage for real. Dry-run MUST NOT suppress a piped stage's effects.

#### Scenario: Warning on a dry-run pipeline

- **WHEN** a user runs `pipe run ... --dry-run`
- **THEN** the warning line appears on standard error before the stages run

#### Scenario: Stages still execute

- **WHEN** a piped invocation carries `--dry-run`
- **THEN** each stage runs and produces its normal output, including real side effects

### Requirement: No history or undo recording

A dry run MUST NOT be added to command history and MUST NOT push an undo entry, so replaying history, listing history, and undo state are unchanged by a dry run.

#### Scenario: History is unchanged

- **WHEN** a command is invoked with `--dry-run` and the user later lists history
- **THEN** the dry-run invocation does not appear

#### Scenario: Undo stack is unchanged

- **WHEN** a dry-run command performs an intercepted operation
- **THEN** no undo entry is recorded for it

### Requirement: Limits of interception

Dry-run mode MUST intercept only the three filesystem mutation operations. It MUST NOT be treated as a sandbox: effects a command produces without using the filesystem accessor still occur.

#### Scenario: Non-filesystem side effects still occur

- **WHEN** an action starts a process or performs a network operation under `--dry-run`
- **THEN** that operation is not intercepted or narrated
