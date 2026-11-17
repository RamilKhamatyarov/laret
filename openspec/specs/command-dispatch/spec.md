# command-dispatch Specification

## Purpose

Defines how a Laret application parses its command line, selects a command group and command, and executes that command, including help, version, global flags, unknown input, hidden built-ins, and run composition.

## Requirements

### Requirement: Invocation grammar

A run MUST accept the two-level form `group command [args]`. Group and command names MUST match by primary name or by alias, and a leading `group:command` token MUST be accepted as shorthand for the same invocation. With no arguments an application MUST print its help and exit 0; with only a group name it MUST exit 0 without output.

#### Scenario: Group and command aliases resolve together

- **WHEN** a group `file` declares alias `f`, its command `create` declares alias `c`, and the user runs `laret f c out.txt`
- **THEN** the `file create` command runs with `out.txt` as its first positional argument

#### Scenario: Colon shorthand

- **WHEN** the user runs `laret file:create out.txt`
- **THEN** the group and command are selected exactly as for `laret file create out.txt`

#### Scenario: Empty or group-only invocation

- **WHEN** the user runs the application with no arguments, or with only a group name
- **THEN** the no-argument form prints application help and exits 0, and the group-only form exits 0 without output

### Requirement: Application-level help and version

The first token `--help` or `-h` MUST print application help to stdout and exit 0. The first token `--version` or `-v` MUST print `<name> version <version>` to stdout, followed by the configured application description when one is present, and exit 0. Both forms MUST be honored only in the first position.

#### Scenario: Version output

- **WHEN** the user runs `laret --version` or `laret -v`
- **THEN** the output starts with `laret version <version>` and the process exits 0

#### Scenario: Help only in first position

- **WHEN** the user runs `laret --help file`
- **THEN** application help is printed and no command is dispatched

### Requirement: Global options

`--config <path>` and `--profile <name>` MUST be removed from any position in the invocation, applied to configuration, and the remaining tokens re-dispatched; an empty remainder MUST print application help and exit 0. `--fix` MUST be removed wherever it appears and MUST enable interactive correction of a mistyped group or command name.

#### Scenario: Global config option in any position

- **WHEN** the user runs `laret file list --config custom.yml`
- **THEN** the named configuration file is applied before the remaining `file list` invocation is dispatched

#### Scenario: Fix mode rewrites a mistyped name

- **WHEN** the user runs `laret --fix flie create out.txt` and accepts the suggested correction
- **THEN** the corrected invocation runs, while declining the suggestion leaves the unknown-group error and exit code 1

### Requirement: Group and command help

`<group> --help` or `<group> -h` with exactly two tokens MUST print group help listing the group's commands and exit 0. `--help` or `-h` anywhere in a command's arguments MUST print that command's help and exit 0 before parsing, validation, middleware, or the action, unless the command declares a `help` long option or an `h` short option.

#### Scenario: Group help

- **WHEN** the user runs `laret file --help`
- **THEN** the group name, description, and command list are printed and the process exits 0

#### Scenario: Command help wins over execution

- **WHEN** the user runs a command with `--help` after otherwise valid arguments
- **THEN** command help is printed, the action does not run, and the process exits 0

### Requirement: Unrecognized group or command

An unknown group MUST print `Group not found: <input>` to stdout, an optional did-you-mean suggestion to stderr, and application help, then exit 1. An unknown command MUST print `Error: Command not found: <input>` to stdout, an optional suggestion to stderr, and group help, then exit 1. In both cases the requested action MUST NOT run.

#### Scenario: Unknown group

- **WHEN** the user runs `laret nosuch`
- **THEN** `Group not found: nosuch` and application help are printed and the process exits 1

#### Scenario: Unknown command within a known group

- **WHEN** the user runs `laret file nosuch`
- **THEN** `Error: Command not found: nosuch` and the file group help are printed and the process exits 1

### Requirement: Option syntax

Options MUST be recognized only as an exact `--long` or `-x` token, with the following token consumed as the value when the option takes one; a boolean option MUST become true by presence alone. `--long=value`, attached `-xVALUE`, and grouped short flags MUST NOT select an option. An unrecognized flag-like token MUST print an unknown-flag warning to stderr and MUST NOT change the exit code or stop parsing.

#### Scenario: Separate long and short values

- **WHEN** the user supplies `--format json` or `-f json` for a value-taking option
- **THEN** the option resolves to `json` and neither token is treated as a positional argument

#### Scenario: Unsupported attached and grouped forms

- **WHEN** the user supplies `--format=json` or the grouped flags `-jy`
- **THEN** the token is reported as an unknown flag and the options it appears to name are not set

#### Scenario: Unknown flag does not fail the command

- **WHEN** a command with all required inputs supplied also receives an unrecognized `--nope` flag
- **THEN** an unknown-flag warning is printed, the command still runs, and the exit code is unchanged

### Requirement: Positional assignment and end-of-options

Positional arguments MUST be assigned in declaration order from tokens that do not begin with `-` and are not the value of a value-taking option; surplus positional tokens MUST be ignored. A `--` token MUST be discarded and MUST suppress unknown-flag warnings for later tokens, without stopping option matching or suppressing command help. A missing required argument MUST print `Error: Required argument '<name>' not provided`, print command help, and exit 1 before validation.

#### Scenario: Positional order and surplus tokens

- **WHEN** a command declares two positional arguments and the user supplies three non-option tokens
- **THEN** the first two tokens fill the arguments in declaration order and the third is ignored

#### Scenario: Separator suppresses warnings only

- **WHEN** the user passes `--` followed by an option-like token
- **THEN** the token produces no unknown-flag warning, is still matched as an option or excluded from positionals, and command help still fires when it is `--help`

#### Scenario: Missing required argument

- **WHEN** a command whose first positional argument is required receives no such token
- **THEN** the missing-argument error and command help are printed and the process exits 1

### Requirement: Hidden commands

A hidden command MUST be omitted from application help and from completion candidates but MUST remain invocable by its name or alias and MUST execute exactly like a visible command.

#### Scenario: Hidden command still runs

- **WHEN** the user invokes a hidden command by its primary name or alias
- **THEN** the command executes normally even though it is absent from the help listings

### Requirement: Built-in middleware group

Every application MUST automatically expose a `middleware` group containing a `list` command. The group name MUST be reserved against sidecar plugin names. `middleware list` MUST list registrations, and `middleware list --command "<group> <command>"` MUST preview a command's resolved chain.

#### Scenario: Middleware group is available without declaration

- **WHEN** an application that never declares a `middleware` group prints its help
- **THEN** the `middleware` group with its `list` command is listed

### Requirement: Completion query command

When `__complete` is the first token, a run MUST treat the remaining tokens as the words being completed, write one `name<TAB>description` candidate per line to stdout, then a directive line beginning with `:`, and exit 0. This hidden command MUST bypass help, validation, middleware, and normal command execution.

#### Scenario: Group completion candidates

- **WHEN** the completion query is given a group name followed by an empty word for a group
- **THEN** each matching command name and description is printed on its own tab-separated line, followed by the directive line, and the process exits 0

#### Scenario: No candidates still yields a directive

- **WHEN** the completion query reaches a position with no candidate values
- **THEN** only the directive line is printed and the process exits 0

### Requirement: Feature installers

Installed features MUST receive a before-run notification in installation order before dispatch. Their dispatch interceptions MUST be consulted in installation order, and the first non-null result MUST become the run's exit code with no further dispatch. Their contributed command groups and configuration layers MUST be combined in installation order.

#### Scenario: First interception wins

- **WHEN** two installed features both intercept the same invocation and the first returns an exit code
- **THEN** that exit code is returned and the second feature and normal dispatch are not consulted

#### Scenario: Before-run ordering

- **WHEN** several features are installed before a run starts
- **THEN** their before-run notifications occur in installation order before any command is dispatched

### Requirement: Command execution order

After selection, a run MUST parse the command line, reject missing required arguments and validation failures, invoke the pre-execution hook, execute the middleware chain and then the action, and invoke the post-execution hook. A failure in the pre-execution hook, middleware, action, or post-execution hook MUST prevent normal completion and exit 1. The normal exit code MUST default to 0 and MAY be set by the action.

#### Scenario: Successful execution order

- **WHEN** a command with hooks, middleware, and a valid invocation succeeds
- **THEN** parsing, validation, the pre-execution hook, middleware, the action, and the post-execution hook occur in that order

#### Scenario: Action failure routes to the error hook

- **WHEN** the action or a middleware fails while the chain is executing
- **THEN** the command's error hook receives the failure and the process exits 1
