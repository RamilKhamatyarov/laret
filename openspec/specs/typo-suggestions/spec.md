# typo-suggestions Specification

## Purpose

Defines how Laret recognizes mistyped group, command, and flag names, what correction it suggests, and when an opt-in interactive correction may run.

## Requirements

### Requirement: Edit-distance comparison

Suggestion matching MUST use a restricted Damerau-Levenshtein distance in which one insertion, deletion, substitution, or adjacent transposition each costs one edit. Comparison MUST be case-insensitive, while a suggested name MUST be displayed in its canonical spelling. Names that are identical to the input after case folding MUST NOT be offered as corrections.

#### Scenario: Adjacent swap counts as one edit

- **WHEN** the input is `craete` and the known name is `create`
- **THEN** the pair is treated as one edit apart and `create` is eligible to be suggested

#### Scenario: Case difference alone is not a typo

- **WHEN** the input differs from a known name only in letter case
- **THEN** the distance is zero and no suggestion is produced for that name

### Requirement: Suggestion ranking and limits

Candidates MUST be de-duplicated and kept only when their distance is from 1 through an adaptive cap. The cap MUST be 1 when the longer of the two names has at most three characters and MUST otherwise be at most 2. Suggestions MUST be sorted by ascending distance, then alphabetically, and truncated to the three closest. An empty input MUST yield no suggestions.

#### Scenario: Short inputs only match near-exact typos

- **WHEN** the input has three or fewer characters and a known name is two edits away
- **THEN** that name is excluded from the suggestions

#### Scenario: Nearest candidate is listed first

- **WHEN** one candidate is one edit away and another is two edits away
- **THEN** the one-edit candidate is listed before the two-edit candidate

#### Scenario: Suggestion count is capped

- **WHEN** more than three candidates fall within the adaptive cap
- **THEN** only the three closest are returned, ordered by distance and then alphabetically

#### Scenario: Empty input

- **WHEN** the mistyped input is empty
- **THEN** no suggestions are produced

### Requirement: Mistyped group guidance

When a group name is not found, the existing `Group not found: <name>` diagnostic MUST remain unchanged and a suggestion line MUST be appended when a close candidate exists. One candidate MUST produce `Did you mean '<name>'?`; several MUST produce `Did you mean one of: <name>, <name>?`. Group suggestions MUST use canonical group names and MUST NOT be offered when no name is within the adaptive cap.

#### Scenario: Single close group

- **WHEN** the user runs `laret fil create notes.txt` and the group `file` exists
- **THEN** `Group not found: fil` is followed by `Did you mean 'file'?`

#### Scenario: No close group

- **WHEN** the mistyped group name is too far from every known group name
- **THEN** only the `Group not found` diagnostic is printed

### Requirement: Mistyped command guidance

When a command name is not found inside a valid group, the existing command-not-found diagnostic MUST remain, followed by a suggestion line when a close command name exists. The candidates MUST be the group's canonical primary command names, and hidden commands MUST NOT be offered. The singular and plural message forms MUST match those used for groups.

#### Scenario: Several close commands

- **WHEN** the user runs `laret file creat notes.txt` and the group contains `create` and `read`
- **THEN** the command-not-found diagnostic is followed by `Did you mean one of: create, read?`

#### Scenario: Hidden command is not suggested

- **WHEN** the closest command name is marked hidden
- **THEN** that hidden name does not appear in the suggestion line

### Requirement: Unknown flag warnings

An unrecognized flag MUST produce a warning on standard error and MUST NOT fail the invocation. The command MUST still run with its remaining arguments and MUST return the status it would have returned had the stray flag been absent. The warning MUST read `Unknown flag '<flag>'.` and MUST append a suggestion when a declared option name is close enough. A bare `-` and negative numeric values MUST NOT be reported.

#### Scenario: Warning does not change execution

- **WHEN** a valid command is invoked with an extra `--focre` flag and a `--force` option exists
- **THEN** standard error receives `Unknown flag '--focre'. Did you mean '--force'?`, the command still runs, and its exit code is unchanged

#### Scenario: No close option

- **WHEN** the unknown flag is too far from every declared option name
- **THEN** only `Unknown flag '<flag>'.` is printed and the command still runs

#### Scenario: Negative number is not a flag

- **WHEN** a negative number or a bare `-` appears where a value is expected
- **THEN** it is not reported as an unknown flag

### Requirement: Suggestion suppression

Suggestions MUST be enabled by default. Setting the `LARET_NO_SUGGEST` environment variable to any non-empty value MUST suppress group and command suggestion lines, unknown-flag warnings, and interactive correction prompts. Base error diagnostics and command behavior MUST remain unchanged.

#### Scenario: Suppressed group suggestion

- **WHEN** `LARET_NO_SUGGEST` is non-empty and the user mistypes a group name
- **THEN** only `Group not found: <name>` is printed, with no suggestion line

#### Scenario: Suppressed flag warning

- **WHEN** `LARET_NO_SUGGEST` is non-empty and the user passes an unknown flag
- **THEN** no unknown-flag warning is printed and the command still runs

### Requirement: Interactive correction eligibility

The global `--fix` flag MUST opt into interactive correction and MUST be removed before command dispatch. A correction MUST be offered only when suggestions are enabled, the process has an interactive terminal, and exactly one candidate is one edit away from a mistyped group or command name. A non-interactive process, zero candidates, two or more distance-1 candidates, or a nearest candidate two edits away MUST produce no prompt.

#### Scenario: Non-interactive process never prompts

- **WHEN** `--fix` is passed in a script or CI process without an interactive terminal
- **THEN** no confirmation is requested, no correction runs, and the original invocation fails normally

#### Scenario: Ambiguous correction is withheld

- **WHEN** two or more candidate names are exactly one edit away from the mistyped name
- **THEN** no correction is offered

#### Scenario: Candidate two edits away is withheld

- **WHEN** the only close candidate is two edits away
- **THEN** no correction is offered

### Requirement: Correction consent

An eligible correction MUST be confirmed before it runs. The prompt MUST read `Did you mean '<name>'?` with a default of yes, so an empty answer accepts. Confirmation MUST run the corrected invocation in place of the mistyped one; a refusal MUST leave the original invocation to fail and MUST NOT run the correction. No correction may run unless the confirmation returns success.

#### Scenario: Confirmed correction runs

- **WHEN** the prompt is answered `y` for a mistyped command that resolves to `create`
- **THEN** the invocation runs as if `create` had been typed

#### Scenario: Default answer accepts

- **WHEN** the prompt is answered with an empty line
- **THEN** the correction is accepted and the corrected invocation runs

#### Scenario: Refused correction does not run

- **WHEN** the prompt is answered `n`
- **THEN** the correction is not run and the original invocation fails
