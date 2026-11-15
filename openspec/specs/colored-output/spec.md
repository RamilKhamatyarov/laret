# colored-output Specification

## Purpose

Defines the color helpers Laret exposes for terminal text, the ANSI escape sequences they emit, and the detection rules that decide when styling is applied or stripped.

## Requirements

### Requirement: Color helper set

Laret MUST provide helpers for styled terminal text: bold red, green, yellow, blue, and cyan; italic yellow and red; plain bold; plain green; and plain blue. Each helper MUST take text and MUST return the styled text when color is supported, or the input text unchanged when it is not.

#### Scenario: Styled success message

- **WHEN** color is supported and `greenBold("File created")` is called
- **THEN** the result is the input text wrapped in the green-bold sequence and a reset

#### Scenario: Styling is unavailable

- **WHEN** color is not supported by the terminal
- **THEN** every helper returns exactly the input text with no escape sequences

### Requirement: ANSI escape sequences

When styling is applied, each helper MUST prefix the text with one SGR escape sequence and MUST suffix it with the reset sequence, leaving the text itself unmodified. The base codes MUST be reset `0`, bold `1`, red `31`, green `32`, yellow `33`, blue `34`, cyan `36`, and italic `3`.

#### Scenario: Bold red

- **WHEN** `redBold("Error")` is styled
- **THEN** the output is `ESC[1;31mErrorESC[0m`, where `ESC` is the escape character

#### Scenario: Bold only

- **WHEN** `bold("Title")` is styled
- **THEN** the output is `ESC[1mTitleESC[0m`

#### Scenario: Italic yellow

- **WHEN** `yellowItalic("Note")` is styled
- **THEN** the output is `ESC[3;33mNoteESC[0m`

#### Scenario: Plain green

- **WHEN** `green("ok")` is styled
- **THEN** the output is `ESC[32mokESC[0m`

### Requirement: Detection precedence

Color MUST be enabled only when the detection rules allow it, evaluated in this order: a non-empty `NO_COLOR` disables color; a non-empty `CLICOLOR_FORCE` other than `0` enables it; output that is not an interactive terminal disables it; otherwise the platform rule decides. An explicit disable MUST win over a forced enable.

#### Scenario: NO_COLOR wins over CLICOLOR_FORCE

- **WHEN** both `NO_COLOR=1` and `CLICOLOR_FORCE=1` are set
- **THEN** color is disabled

#### Scenario: Forced color survives a pipe

- **WHEN** output is redirected and `CLICOLOR_FORCE=1` is set
- **THEN** helpers emit escape sequences anyway

#### Scenario: Zero does not force

- **WHEN** `CLICOLOR_FORCE=0` is set and output is not a terminal
- **THEN** color is disabled

### Requirement: Non-terminal output is unstyled

When standard output is not attached to an interactive console and color is not forced, every helper MUST return unaltered text. Piped, redirected, and captured output MUST therefore contain no escape sequences.

#### Scenario: Piping to a file

- **WHEN** `laret` output is piped to a file without `CLICOLOR_FORCE`
- **THEN** the file contains the plain text with no escape sequences

#### Scenario: Captured test output

- **WHEN** output is captured by a non-terminal harness
- **THEN** helpers return unstyled text, keeping captured output predictable

### Requirement: Platform color rules

On Windows, color MUST be enabled when the terminal check passes and the OS major version is at least `10`, compared numerically. On other platforms, color MUST be enabled when the terminal check passes and `TERM` is set to a value other than `dumb`.

#### Scenario: Windows 10 console

- **WHEN** the OS reports a Windows version of `10.0` or later and output is a console
- **THEN** color is enabled

#### Scenario: Older Windows

- **WHEN** the OS reports Windows `6.3` and output is a console
- **THEN** color is disabled

#### Scenario: TERM is dumb

- **WHEN** `TERM=dumb` on a non-Windows platform
- **THEN** color is disabled

#### Scenario: TERM is set

- **WHEN** `TERM=xterm-256color` on a non-Windows platform with a terminal
- **THEN** color is enabled

### Requirement: Native-image-safe detection

Color detection MUST NOT depend on a native helper library, console-mode negotiation, or output rewriting. The same raw ANSI escape sequences MUST be emitted by the JAR and the native binary, and the decision MUST rely only on environment variables, OS name and version, and terminal status. See [the colored-output ADR](../../adr/colored-output-native-image-and-detection.md).

#### Scenario: Native binary in a modern terminal

- **WHEN** the native binary runs in a terminal that renders ANSI sequences
- **THEN** styled text appears colored rather than plain or with literal escape codes

#### Scenario: No stripping layer

- **WHEN** a helper emits an escape sequence
- **THEN** that sequence reaches the output unchanged and is not stripped by a console wrapper

### Requirement: Unicode and ASCII glyph fallbacks

Laret MUST provide a glyph selector that returns either a preferred Unicode glyph or an ASCII alternative. The mode MUST default to ASCII when running on Windows with an attached console and MUST be overridable by the `laret.ui.ascii` system property, then by the `LARET_UI_ASCII` environment variable.

#### Scenario: Default Windows console

- **WHEN** the application runs on Windows with an attached console and no override
- **THEN** the selector returns the ASCII alternative

#### Scenario: Explicit override

- **WHEN** `LARET_UI_ASCII=false` is set on Windows with an attached console
- **THEN** the selector returns the Unicode glyph

#### Scenario: Property beats environment

- **WHEN** both `-Dlaret.ui.ascii=true` and `LARET_UI_ASCII=false` are supplied
- **THEN** the system property wins and the ASCII alternative is returned
