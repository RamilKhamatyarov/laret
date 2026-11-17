# history-undo-redo Specification

## Purpose

Defines command history and the undo/redo stacks: which invocations are recorded and where they persist, the show, save, aliases, replay, and clear commands, saved alias invocation, and last-in-first-out undo and redo behavior, including empty-stack handling.

## Requirements

### Requirement: History recording and capacity

Each command that executes successfully MUST be appended to history with its full argument list and a timestamp, oldest first. Recording MUST be skipped for dry runs, for invocations rejected before execution, for commands that throw, and for history, undo, redo, and replay commands including the commands they run. History MUST retain at most the 100 newest entries, discarding the oldest beyond that.

#### Scenario: Successful command recorded

- **WHEN** a command completes with exit code 0
- **THEN** the invocation's arguments appear as the newest history entry

#### Scenario: Dry run excluded

- **WHEN** a command runs with `--dry-run`
- **THEN** no history entry is added for it

#### Scenario: Failed invocation excluded

- **WHEN** a command is rejected for a missing required argument or its action fails and the process exits non-zero
- **THEN** no history entry is added for it

#### Scenario: Replay excluded

- **WHEN** a command is executed through `history replay`
- **THEN** neither the replay invocation nor the replayed command is added to history

### Requirement: History persistence

History MUST persist at `~/.laret/cmd_history.txt` with one record per line. Entry lines MUST be tab-separated Base64 fields for the id, the timestamp in epoch milliseconds, the argument count, and one field per argument. Alias lines MUST start with `@alias` followed by the Base64 alias name, the argument count, and the Base64 arguments. Loading MUST skip malformed lines and MUST warn rather than fail when the file cannot be read.

#### Scenario: History and aliases survive invocations

- **WHEN** a command is recorded and an alias saved, then a later process lists both
- **THEN** the entry is present with its arguments and index and the alias is present

#### Scenario: Malformed line skipped

- **WHEN** the history file contains a line that cannot be decoded
- **THEN** that line is ignored and the remaining entries load

### Requirement: history show

`history show` MUST list entries oldest first, numbered from 1, each as the index, a period, and the space-joined arguments. `--limit`/`-n` MUST restrict the listing to the most recent N entries while preserving their original indices. With no entries it MUST report `No command history.`.

#### Scenario: Limit keeps recent entries

- **WHEN** three commands have been recorded and `-n 2` is passed
- **THEN** only entries 2 and 3 are listed, with their original numbering

#### Scenario: Empty history

- **WHEN** no command has been recorded
- **THEN** `No command history.` is reported

### Requirement: history save

`history save <name> [index]` MUST save a history entry as a reusable alias. The name MUST match lowercase letters, digits, and hyphens, starting with a letter. The index MUST use the numbering shown by `history show`, where 0 selects the most recent entry and is the default. A successful save MUST report the saved arguments and how to run them; an invalid name MUST be rejected with naming guidance; an unusable index MUST report that there is no history to save.

#### Scenario: Save a specific entry

- **WHEN** `history save build 1` is invoked with history present
- **THEN** the entry numbered 1 is stored under the alias `build` and the confirmation names both

#### Scenario: Invalid alias name

- **WHEN** the name contains uppercase letters or underscores
- **THEN** the save is rejected with `Invalid alias name. Use lowercase letters, numbers, and hyphens; start with a letter.`

### Requirement: history aliases

`history aliases` MUST list saved aliases as two leading spaces, the alias name, a colon, and the space-joined arguments. When no aliases are saved it MUST report `No saved command aliases.`.

#### Scenario: Aliases listed

- **WHEN** an alias named `build` was saved
- **THEN** a line of the form `  build: <arguments>` is written

#### Scenario: No aliases

- **WHEN** no alias has been saved
- **THEN** `No saved command aliases.` is reported

### Requirement: history replay

`history replay [alias-or-index]` MUST resolve its argument as a saved alias or a history index, defaulting to the most recent entry, report `Replaying: <arguments>`, and execute those arguments in-process. A failed replay MUST report `Replay failed (exit <code>)` and MUST exit with the replayed command's code; a successful replay MUST exit 0. An unrecognized argument MUST fall back to the most recent entry, and empty history MUST report `No command history.` and exit 0.

#### Scenario: Replay by index or alias

- **WHEN** `history replay 1` or `history replay build` is invoked
- **THEN** the resolved arguments are executed and narrated as being replayed

#### Scenario: Replay failure propagates

- **WHEN** the replayed command exits with code 1
- **THEN** `Replay failed (exit 1)` is reported and the invocation exits with status 1

#### Scenario: Replay with empty history

- **WHEN** `history replay` is invoked with no recorded commands
- **THEN** `No command history.` is reported and the invocation exits 0

### Requirement: Alias invocation

When the entire command line consists of a single argument that matches a saved alias name, it MUST be replaced by the alias arguments before dispatch, unless that name is a reserved group or command. When additional arguments follow, no alias expansion MUST occur. Saved aliases MUST persist across invocations.

#### Scenario: Alias run directly

- **WHEN** `laret build` is invoked and `build` is a saved alias
- **THEN** the alias arguments execute as the command

#### Scenario: Extra arguments prevent expansion

- **WHEN** an alias name is followed by additional arguments
- **THEN** the alias is not expanded and normal dispatch applies

### Requirement: history clear

`history clear` MUST remove every history entry, persist the empty list, and report `Command history cleared.` Saved aliases MUST be retained and MUST remain usable after the clear.

#### Scenario: Entries cleared

- **WHEN** `history clear` runs
- **THEN** a later `history show` reports no history

#### Scenario: Aliases survive clear

- **WHEN** history is cleared after an alias was saved
- **THEN** the alias is still listed and still runs

### Requirement: Undo entry registration

A command that changes state MUST register an undo entry through the execution context, holding a description, the arguments that undo the change, and the arguments that redo it. Entries MUST be kept in a last-in-first-out undo stack, and registering a new entry MUST clear the redo stack. Dry runs MUST NOT register entries. Entries MUST persist at `~/.laret/history.txt`.

#### Scenario: State change registers an entry

- **WHEN** a file-creating command succeeds
- **THEN** an undo entry describing that creation is on the undo stack

#### Scenario: Dry run registers nothing

- **WHEN** a state-changing command runs with `--dry-run`
- **THEN** the undo stack is unchanged

### Requirement: undo run

`undo run` MUST take the most recent undo entry, report `Undoing: <description>`, execute its undo arguments, report `Undo complete.`, and move the entry to the redo stack. When the inverse command reports an error the entry MUST still move and completion MUST still be reported. With an empty undo stack it MUST report `Nothing to undo.` and exit 0; a completed undo also exits 0.

#### Scenario: Most recent action undone

- **WHEN** two state-changing commands have run and `undo run` is invoked
- **THEN** the second command's inverse executes and it moves to the redo stack

#### Scenario: Inverse error still completes

- **WHEN** the inverse command reports that its target is gone
- **THEN** `Undo complete.` is still reported and the entry moves to the redo stack

#### Scenario: Empty undo stack

- **WHEN** `undo run` is invoked with nothing to undo
- **THEN** `Nothing to undo.` is reported and the invocation exits 0

### Requirement: undo history

`undo history` MUST print an `Undo stack` heading with its numbered entries in oldest-to-newest order, and when redo entries exist it MUST print a `Redo stack` heading with numbered redo entries. With both stacks empty it MUST report `No history recorded.`.

#### Scenario: Both stacks listed

- **WHEN** one entry was undone
- **THEN** the undo stack is listed and the redo stack section lists the undone entry

#### Scenario: Empty stacks

- **WHEN** no undoable command has run
- **THEN** `No history recorded.` is reported

### Requirement: redo run

`redo run` MUST take the most recent redo entry, report `Redoing: <description>`, execute its redo arguments, report `Redo complete.`, and push the entry back onto the undo stack. With an empty redo stack it MUST report `Nothing to redo.` and exit 0.

#### Scenario: Undone action redone

- **WHEN** an entry was undone and `redo run` is invoked
- **THEN** the entry's redo arguments execute and the entry returns to the undo stack

#### Scenario: Empty redo stack

- **WHEN** `redo run` is invoked with nothing to redo
- **THEN** `Nothing to redo.` is reported and the invocation exits 0
