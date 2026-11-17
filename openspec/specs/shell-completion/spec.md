# shell-completion Specification

## Purpose

Defines how a Laret application generates and installs shell completion scripts for bash, zsh, and PowerShell, and how the hidden runtime completion protocol answers a shell's candidate requests.

## Requirements

### Requirement: Completion script commands

The completion group MUST provide one command per supported shell — `bash`, `zsh`, and `powershell` — each printing that shell's completion script for the running application to standard output. Every one of those commands MUST accept `-d`/`--dynamic`; omitting the flag MUST produce the static script.

#### Scenario: Static script by default

- **WHEN** the user runs `app completion bash`
- **THEN** a bash completion script for `app` is printed to standard output and no file is created

#### Scenario: Dynamic script on request

- **WHEN** the user runs `app completion powershell --dynamic`
- **THEN** the printed script requests candidates from the application at completion time instead of embedding them

### Requirement: Static script contents

A static script MUST embed the completion data gathered from the visible command tree: every visible group name and the declared commands of those groups. Options MUST be embedded as long and short forms, and script generation MUST NOT invoke the application at completion time.

#### Scenario: Hidden group is not embedded

- **WHEN** a group is marked hidden and a static script is generated
- **THEN** the script contains no completion word for that group

#### Scenario: Generation is offline

- **WHEN** a static script is generated and inspected
- **THEN** it contains no call to the hidden completion protocol and completes from its embedded word lists only

### Requirement: Shell-specific script shape

Each script MUST be plain text the target shell can source, and MUST register itself for the application name: bash through `complete -F`, zsh through a leading `#compdef` line, and PowerShell through an argument completer for both `<app>` and `<app>.exe`. Bash MUST offer group and command words at the first argument and option words deeper, and PowerShell MUST branch on the positional count.

#### Scenario: Sourcing registers completion

- **WHEN** the generated bash script is sourced in a bash session
- **THEN** tab completion for the application name is handled by the script's function

#### Scenario: Option words at later positions

- **WHEN** a user completes the second or later word and presses TAB in bash
- **THEN** the candidate list contains the embedded option words rather than only command names

#### Scenario: Zsh descriptions survive quoting

- **WHEN** a command description contains an apostrophe or square brackets and the zsh script is generated
- **THEN** the description is emitted with those characters escaped inside its `name:description` entry

#### Scenario: PowerShell second position

- **WHEN** a user has typed the application name and one group in PowerShell
- **THEN** the candidates are that group's command names

#### Scenario: PowerShell unknown group

- **WHEN** the typed group does not exist and completion is requested in PowerShell
- **THEN** the candidates are visible group names filtered by the typed prefix

### Requirement: Dynamic script behaviour

A dynamic script MUST NOT embed commands, options, or descriptions. On completion it MUST invoke `<app> __complete` with the already-typed words followed by the word under the cursor, discard the command's standard error, and read the candidate lines and the trailing directive. It MUST suppress the trailing space when the no-space bit is set and MUST fall back to the shell's file completion only when the no-file-completion bit is absent.

#### Scenario: Candidates come from the binary

- **WHEN** a dynamic script completes a word
- **THEN** the candidate list is exactly what the application printed for that partial command line

#### Scenario: No-space directive respected

- **WHEN** the application answers a dynamic query with a directive containing the no-space bit
- **THEN** the shell does not append a space after the inserted value

### Requirement: Installation targets

`completion install <shell>` MUST write the static script to a user-level location: `~/.bash_completion.d/<app>` for bash, `~/.zsh_completions/_<app>` for zsh, and `<profile-dir>/<app>_completion.ps1` for PowerShell, where the profile directory is the parent of the file named by the `PROFILE` environment variable when set and `~/Documents/PowerShell` otherwise. Missing parent directories MUST be created and the written file MUST be marked executable.

#### Scenario: Bash target path

- **WHEN** the user runs `app completion install bash`
- **THEN** the bash script is written to `~/.bash_completion.d/app`

#### Scenario: PowerShell profile hint

- **WHEN** installation succeeds for PowerShell
- **THEN** the output tells the user to source the installed script from their profile

### Requirement: Installation overwrite and errors

Installation MUST replace an existing target file without prompting and without keeping a backup. An unsupported shell name MUST be reported as an error and MUST NOT write any file. A missing shell argument MUST be rejected by required-argument checking.

#### Scenario: Reinstall overwrites

- **WHEN** `completion install zsh` runs twice
- **THEN** the second run overwrites `~/.zsh_completions/_app` with the freshly generated script

#### Scenario: Unsupported shell

- **WHEN** the user runs `app completion install fish`
- **THEN** an error names the unsupported shell and the supported ones, and no file is written

### Requirement: Completer kinds

Declared value completers MUST support three built-in kinds plus arbitrary custom logic: a fixed candidate list, optionally with a per-value description; an enumeration whose constant names are offered lowercased; and filesystem paths. Every kind MUST filter its candidates by the typed prefix. A declared completer MUST be consulted for the value of the option it annotates and for the positional argument it annotates.

#### Scenario: Prefix filters a fixed list

- **WHEN** an option declares the fixed list `md`, `man` and the typed prefix is `ma`
- **THEN** only `man` is offered as a candidate

#### Scenario: Enumeration is lowercased

- **WHEN** an option declares an enumeration completer and the user types an empty prefix
- **THEN** the candidate values are the constant names in lowercase

### Requirement: Path completion

The path completer MUST split the typed word at its last `/`, treating `\` as `/`, list the directory named by the leading part (the working directory when empty), and return entries matching the trailing part as `<dir-prefix><name>`, with a trailing `/` appended to directories. It MUST report no candidates when that directory does not exist, and MUST request no file fallback.

#### Scenario: Single directory candidate

- **WHEN** exactly one entry matches and it is a directory
- **THEN** the candidate ends with `/` and the result requests no trailing space and no file fallback

#### Scenario: Missing directory

- **WHEN** the typed word names a directory that does not exist
- **THEN** the result contains no candidates and requests no file fallback

### Requirement: Runtime completion protocol

Completion MUST also be served by a hidden `__complete` command that MUST NOT appear in help or completion listings. The tokens after it MUST be read as a partial command line: the final token is the word under the cursor and every preceding token is already typed. The response MUST be one candidate per line, either the value alone or `value<TAB>description`, followed by a final line `:<directive>`.

#### Scenario: Candidate with description

- **WHEN** a group description is non-blank and its name is offered
- **THEN** the line is the value, a tab, then the description

#### Scenario: Empty answer still emits a directive

- **WHEN** the request names no known group or command
- **THEN** the output contains no candidate lines and ends with a directive line

### Requirement: Candidate resolution

Resolution MUST depend on how much of the command line is already typed: no preceding word yields visible group names; one preceding word yields the resolved group's non-hidden command names; past the command, a word starting with `-` yields option long names plus `--dry-run`; the word after a value-taking option yields that option's completer result; otherwise the positional argument under the cursor is used.

#### Scenario: Flag completion

- **WHEN** the word under the cursor starts with `-` at command level
- **THEN** the candidates are `--` prefixed long option names filtered by the typed prefix, plus `--dry-run`

#### Scenario: Positional index skips flags

- **WHEN** the typed words include a value-taking flag and its value before the cursor
- **THEN** the positional index is computed from non-flag words only, so the flag and its value are not counted as positionals

### Requirement: Directive codes

Every response MUST end with `:<n>` where `<n>` is a bitmask: `0` for no request, `1` when an error occurred, `2` to suppress the trailing space, and `4` to forbid shell file completion. Bits MAY be combined. A query that produces no candidates MUST still emit the directive line.

#### Scenario: Default directive

- **WHEN** a completer returns candidates without requesting special shell behaviour
- **THEN** the final line is `:4`, forbidding file fallback

#### Scenario: Combined bits

- **WHEN** the only candidate is a directory
- **THEN** the directive line reports the no-space bit combined with the no-file-completion bit

### Requirement: Completion has no side effects

Completing MUST resolve candidates without executing the command: no action, middleware, prompt, history entry, or statistics update occurs, and no application file is changed. A completed query MUST exit with code 0 even when it returns no candidates.

#### Scenario: Completion does not run the action

- **WHEN** a user requests completion for a command with a side-effecting action
- **THEN** the action does not run and no command side effect occurs

#### Scenario: Unknown group exits cleanly

- **WHEN** completion is requested for a group name that does not exist
- **THEN** the process exits with code 0 and prints only the directive line
