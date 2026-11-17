# Project Scaffolding Specification

## Purpose

Defines the `laret new project` command: its interactive wizard, non-interactive inputs, the project layout and files it writes, template substitution rules, shell test generation, and failure behavior.

## Requirements

### Requirement: Command inputs

`laret new project` MUST support `--dir` (`-d`) for the target directory, defaulting to `./<project-name>`; `--yes` (`-y`) to skip the wizard and use defaults; `--name` (`-n`) for the project name; and `--package` (`-p`) for the package name. `--name` and `--package` are used with `--yes`.

#### Scenario: Explicit target directory

- **WHEN** the user runs `new project --yes --name my-cli --package com.example.mycli --dir ./demo`
- **THEN** project files are written under `./demo`

#### Scenario: Default target directory

- **WHEN** `--dir` is omitted for a project named `my-cli`
- **THEN** the project is written under `./my-cli`

### Requirement: Interactive wizard flow

Without `--yes`, the command MUST run a wizard that prompts for the project name, package name, CLI app binary name, optional modules, whether to generate shell precedence tests (and which targets), and whether to enable a GraalVM native-image task. Prompts MUST be written to standard error. The binary name MUST default to the project name.

#### Scenario: Prompt order

- **WHEN** the wizard runs
- **THEN** it asks for the project name first, then the package name, then the binary name, modules, shell tests, and the GraalVM choice

#### Scenario: Declining shell tests

- **WHEN** the user answers no to generating shell precedence tests
- **THEN** no files are written under `tests/`

### Requirement: Interactive validation and retries

The wizard MUST validate the project name as lowercase kebab-case and the package name as dotted lowercase segments. On invalid input it MUST print an example and re-ask, up to three attempts, then fall back to the valid default. A blank answer MUST accept the displayed default.

#### Scenario: Invalid project name

- **WHEN** the user enters `My CLI` as the project name
- **THEN** the wizard reports that lowercase letters, digits, and hyphens are required and asks again

#### Scenario: Three invalid attempts

- **WHEN** the user enters an invalid project name three times
- **THEN** the wizard continues with the default name `my-cli`

### Requirement: Module and shell target selection

The module prompt MUST offer `config`, `completion`, `ui`, and `output`; a blank selection MUST mean all modules. The shell target prompt MUST offer `bash`, `zsh`, and `powershell`; a blank selection MUST mean all three. Selections MUST be entered as comma-separated numbers and out-of-range or non-numeric entries MUST be ignored.

#### Scenario: Blank module selection

- **WHEN** the user presses Enter at the module prompt
- **THEN** all four modules are selected

#### Scenario: Partial shell selection

- **WHEN** the user enters `1, 3` at the shell target prompt
- **THEN** bash and PowerShell tests are generated and zsh tests are not

### Requirement: Non-interactive defaults

With `--yes`, the command MUST skip every prompt and generate from defaults. `--name` MUST override the project name and `--package` MUST override the package name; omitted values MUST use built-in defaults.

#### Scenario: Fully specified non-interactive run

- **WHEN** `--yes --name tool --package com.acme.tool` is supplied
- **THEN** no prompt is displayed and the project is generated with `tool` and `com.acme.tool`

### Requirement: Scaffold configuration validation

Before writing files, the project name MUST match a lowercase kebab-case pattern beginning with a letter, the package name MUST be dotted lowercase segments beginning with a letter and containing at least one dot, and the app name MUST be non-blank. A violation MUST fail the command before any file is written.

#### Scenario: Package without a dot

- **WHEN** the package is `mycli`
- **THEN** the command rejects it and no files are written

### Requirement: Generated project layout

A successful non-interactive run MUST write `build.gradle.kts`, `settings.gradle.kts`, `.gitignore`, `src/main/kotlin/<package-path>/Main.kt`, and `src/main/kotlin/<package-path>/commands/HelloCommand.kt`. When shell tests are selected, it MUST also write `tests/test-precedence.bash`, `tests/test-precedence.zsh`, or `tests/test-precedence.ps1` for each selected target. The package path MUST be the package name with dots replaced by directory separators.

#### Scenario: Generated core files

- **WHEN** a project is generated for package `com.example.mycli`
- **THEN** `Main.kt` and `commands/HelloCommand.kt` are written under `src/main/kotlin/com/example/mycli/`

#### Scenario: Test file names

- **WHEN** bash and PowerShell tests are selected
- **THEN** `tests/test-precedence.bash` and `tests/test-precedence.ps1` are written

### Requirement: Template variables and conditions

Generated contents MUST be derived from project name, package name, package path, app name, uppercase environment prefix, group ID, artifact ID, Laret version, GraalVM choice, module flags, and module list. `${name}` placeholders MUST be replaced, unknown placeholders left unchanged. `{{#if name}}...{{/if}}` blocks MUST render only for truthy values, support nesting, and stay literal when unclosed.

#### Scenario: Environment prefix

- **WHEN** the app name is `my-cli`
- **THEN** the generated uppercase environment prefix is `MY_CLI`

#### Scenario: Disabled module block

- **WHEN** the `ui` module is not selected
- **THEN** conditional sections gated on the `ui` module are omitted from generated files

### Requirement: File writing and collisions

Every target parent directory MUST be created. Files MUST be written as UTF-8 and MUST replace any existing file at the same path without prompting or creating a backup. Executable shell test files MUST have execute permission set on platforms that support it.

#### Scenario: Existing file overwritten

- **WHEN** a target file already exists at a generated path
- **THEN** its contents are replaced by the generated contents

#### Scenario: Bash test is executable

- **WHEN** `tests/test-precedence.bash` is generated
- **THEN** its permissions allow execution

### Requirement: Partial failure behavior

Each generated file MUST be attempted independently. A write failure MUST be reported as a failure naming the target path, and files already written successfully MUST be left in place. The command MUST NOT roll back or delete a partially generated project.

#### Scenario: One file cannot be written

- **WHEN** writing one generated file fails while other files succeed
- **THEN** the failure names that file, the successful files remain, and no automatic cleanup occurs
