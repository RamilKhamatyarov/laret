# interactive-ux Specification

## Purpose

Defines the interactive shell and terminal user-interface primitives: line editing, completion, prompts with non-interactive fallbacks, and spinner and progress rendering.

## Requirements

### Requirement: Shell lifecycle

`completion interactive` MUST print a startup banner that tells the user to type `exit`, then repeatedly show a `laret > ` prompt, read one line, and dispatch it as a Laret invocation. Blank lines MUST be skipped. The tokens `exit` and `quit`, after trimming, MUST end the shell. An entered line MUST be split on whitespace and MUST NOT be interpreted by a shell.

#### Scenario: Command execution

- **WHEN** the user enters `echo print hi` at the shell prompt
- **THEN** the corresponding Laret invocation runs and its output is shown before the next prompt

#### Scenario: Exit and quit

- **WHEN** the user enters `exit` or `quit`
- **THEN** the shell prints nothing further to the prompt and returns control to the process

#### Scenario: Blank line

- **WHEN** the user presses Enter without typing anything
- **THEN** no command runs and the prompt is shown again

### Requirement: Shell interruption and errors

A command that throws MUST cause an error line to be printed and the shell MUST continue with the next prompt. An interrupt while reading a line MUST report the interruption and end the shell. End of input MUST report end of input and end the shell.

#### Scenario: Command error does not end the shell

- **WHEN** an entered command raises an error
- **THEN** an error message is printed and the next prompt is shown

#### Scenario: End of input exits

- **WHEN** the input stream ends, as with Ctrl+D
- **THEN** an end-of-input notice is printed and the shell ends

#### Scenario: Interrupt exits

- **WHEN** the user presses Ctrl+C while the shell is reading a line
- **THEN** an interruption notice is printed and the shell ends

### Requirement: Line editing and history shortcuts

The shell MUST support the following editing keys: up and down arrows for the previous and next history entry, Ctrl+R for reverse history search, Ctrl+A and Ctrl+E for the start and end of the line, Ctrl+U to clear from the cursor to the start, Ctrl+K to clear from the cursor to the end, and Ctrl+W to delete the preceding word.

#### Scenario: History navigation

- **WHEN** the user presses the up arrow after running a command
- **THEN** the previous command text is placed in the edit buffer

#### Scenario: Editing shortcuts

- **WHEN** the user presses Ctrl+A, Ctrl+E, Ctrl+U, Ctrl+K, or Ctrl+W
- **THEN** the cursor or line content changes as the shortcut describes without executing the line

### Requirement: Tab completion

Tab MUST complete command names and option names in the text before the cursor. Completion MUST modify the edit buffer only and MUST NOT execute the line.

#### Scenario: Command name completion

- **WHEN** the user types a partial command name and presses Tab
- **THEN** the matching command name is completed in the edit buffer

#### Scenario: Option name completion

- **WHEN** the user types a partial option name and presses Tab
- **THEN** the matching option name is completed in the edit buffer

### Requirement: Text and confirmation prompts

A text prompt MUST write `? <prompt>` to standard error, followed by ` [<default>]` when a non-empty default exists, then `: `; it MUST read one line, trim it, and return the default when the line is empty or input ends. A confirmation prompt MUST show `[Y/n]`, or `[y/N]` when the default is no, and MUST treat `y` and `yes` as true, `n` and `no` as false, and empty or unrecognized input as the default.

#### Scenario: Text default

- **WHEN** a text prompt is shown and the user presses Enter
- **THEN** the declared default is returned

#### Scenario: Confirmation answers

- **WHEN** a confirmation prompt with a default of yes receives `n`
- **THEN** it returns false, and an empty answer returns true

### Requirement: Selection prompts

A single-selection prompt MUST list numbered options and read one number. An empty or non-numeric answer MUST select the first option, and a numeric answer MUST be clamped to the valid range. A multi-selection prompt MUST read comma-separated numbers, ignore non-numeric and out-of-range entries, remove duplicates, and return the selected options in the order entered; an empty answer MUST return an empty list. Both prompts MUST reject an empty option list.

#### Scenario: Selection defaults to the first option

- **WHEN** a single-selection prompt receives an empty answer
- **THEN** the first listed option is returned

#### Scenario: Multi-selection filters invalid entries

- **WHEN** a multi-selection prompt receives numbers that are repeated, non-numeric, or out of range
- **THEN** only valid, distinct options are returned

#### Scenario: Empty option list

- **WHEN** either selection prompt is given no options
- **THEN** the prompt is rejected with an error

### Requirement: Password prompt

A password prompt MUST write `? <prompt>: ` to standard error, read one line, trim it, and return it. It MUST NOT mask or otherwise alter the entered characters. End of input MUST return an empty string.

#### Scenario: Password text is returned

- **WHEN** the user enters `secret` at a password prompt
- **THEN** the prompt returns `secret` without masking the typed characters

#### Scenario: Password end of input

- **WHEN** input ends at a password prompt
- **THEN** an empty string is returned

### Requirement: Non-interactive fallbacks

When prompting is disabled, prompts MUST write nothing and MUST return their documented fallback without reading input: a text prompt returns its default, a confirmation returns its default, a single selection returns the first option, a multi-selection returns an empty list, and a password prompt returns an empty string.

#### Scenario: Disabled text prompt

- **WHEN** prompting is disabled and a text prompt has a default
- **THEN** the default is returned and nothing is written

#### Scenario: Disabled confirmation

- **WHEN** prompting is disabled and a confirmation defaults to yes
- **THEN** true is returned and nothing is written

#### Scenario: Disabled selection

- **WHEN** prompting is disabled and options are available
- **THEN** the first option is returned and nothing is written

### Requirement: Progress bar

A progress bar MUST write to standard error a carriage-returned line of the form `<label> [<bar>] <pct>% (<current>/<total>)`, with a 40-character bar by default, proportional fill, and a four-character percentage field. Progress MUST be clamped to 0 through the total; the percentage MUST round and clamp to 0 through 100, and a non-positive total MUST report 100%. Finishing MUST fill the bar, mark it finished, and write a newline. A disabled bar MUST update its state without writing.

#### Scenario: Halfway progress

- **WHEN** a bar with total 10 is updated to 5
- **THEN** it renders a half-filled bar and `50%` with `(5/10)`

#### Scenario: Updates are clamped

- **WHEN** an update is below 0 or above the total
- **THEN** the stored progress becomes 0 or the total

#### Scenario: Disabled bar

- **WHEN** a progress bar is disabled
- **THEN** updates and finishing write nothing while the finished state still changes

### Requirement: Spinner

A spinner MUST write to standard error, on each tick, a carriage return followed by the current frame and an optional label. Frames MUST cycle through the spinner sequence and MUST fall back to ASCII frames when Unicode output is unavailable. Finishing MUST write a success mark followed by a message or the label; failing MUST write a failure mark followed by a message or the label. A disabled spinner MUST write nothing, and ticks after finishing MUST be ignored.

#### Scenario: Tick advances the frame

- **WHEN** a spinner ticks repeatedly
- **THEN** each tick renders the next frame in the cycle

#### Scenario: Success and failure marks

- **WHEN** a spinner is finished or failed with a message
- **THEN** the corresponding success or failure mark is written with that message

#### Scenario: Disabled spinner

- **WHEN** a spinner is disabled
- **THEN** ticks, finishing, and failing write nothing

### Requirement: Terminal gating

Progress and spinner rendering MUST be gated by an explicit enabled setting so callers can suppress animation when the destination is not an interactive terminal. When rendering is disabled, no carriage returns, color codes, or frames MUST be written. When rendering is enabled and Unicode is unavailable, the ASCII fallbacks MUST be used.

#### Scenario: Non-terminal destination suppresses animation

- **WHEN** a caller disables rendering because standard error is redirected
- **THEN** no animation or progress lines appear in the redirected output

#### Scenario: ASCII fallback

- **WHEN** rendering is enabled but Unicode is unavailable
- **THEN** the progress bar uses `#` and `-`, and the spinner uses the ASCII frame cycle
