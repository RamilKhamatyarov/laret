# cli-dsl Specification

## Purpose

Defines how a Laret application, its command groups, commands, arguments, and options are declared in Kotlin, and how command actions read resolved input through the execution context.

## Requirements

### Requirement: Application entry point

A Laret application MUST be created by the `cli` function, which takes a name, an optional version that defaults to `1.0.0`, an optional description that defaults to empty, and a builder block declaring the application's groups.

#### Scenario: Minimal application

- **WHEN** a program calls `cli("demo") { }` and runs it with `--help`
- **THEN** an application named `demo` is created and its help output is printed

#### Scenario: Version defaults to 1.0.0

- **WHEN** no version is passed to `cli`
- **THEN** the application reports version `1.0.0`

### Requirement: Command groups

Groups MUST be declared with `group(name, description)` and MUST contain the application's commands. A group MAY declare one or more aliases and MAY be marked hidden.

#### Scenario: Group alias invocation

- **WHEN** a group `file` declares alias `f` and the user runs `laret f create`
- **THEN** the command resolves exactly as if the user had run `laret file create`

#### Scenario: Hidden group is invocable but unlisted

- **WHEN** a group is marked hidden
- **THEN** it is absent from help and completion listings but still runs when invoked by name

### Requirement: Commands

Commands MUST be declared inside a group with `command(name, description)`. A command declares zero or more arguments and options, an optional action, and MAY declare aliases or be marked hidden.

#### Scenario: Command with no action

- **WHEN** a command declares no action and the user invokes it
- **THEN** the command performs no work and exits with code 0

### Requirement: Positional arguments

Arguments MUST be declared in the order the user supplies them. Arguments are required by default; an argument MAY be declared optional with a default value. Arguments MAY declare a value type, a completer, and inline validation rules.

#### Scenario: Optional argument falls back to its default

- **WHEN** an optional argument declares `default = "."` and the user omits it
- **THEN** the action reads `"."` for that argument

#### Scenario: Required argument must be supplied

- **WHEN** a required argument is missing from the invocation
- **THEN** the command fails with an error identifying the missing argument and the action does not run

### Requirement: Named options

Options MUST be declared with a short name, a long name, and a description. An option MAY declare a default, declare that it takes no value, be persistent, declare a config key, declare a value type, a completer, and inline validation rules.

#### Scenario: Boolean option

- **WHEN** an option declares `takesValue = false` and the user passes its long form
- **THEN** the action reads the boolean value `true`

#### Scenario: Persistent option resolution

- **WHEN** a persistent option is omitted on the command line but present in configuration
- **THEN** the configured value is used, and it wins over the declared default

### Requirement: Execution context accessors

Actions MUST receive a context object that exposes typed readers for arguments and options. Missing or non-numeric values MUST read as the reader's zero value rather than throwing.

#### Scenario: Numeric option with invalid text

- **WHEN** an action calls the integer reader for an option whose value is not numeric
- **THEN** the reader returns `0`

#### Scenario: Boolean option absent

- **WHEN** an action calls the boolean reader for an option the user did not pass
- **THEN** the reader returns `false`

### Requirement: Command lifecycle hooks

A command MAY declare pre-execution, post-execution, and error hooks. The pre-execution hook MUST run before the action, the post-execution hook MUST run after a successful action, and the error hook MUST receive the context and the thrown exception.

#### Scenario: Hook ordering

- **WHEN** a command declares all three hooks and its action succeeds
- **THEN** the pre-execution hook runs before the action and the post-execution hook runs after it

### Requirement: Alias resolution

Group aliases and command aliases MUST be accepted anywhere the canonical name is accepted and MUST resolve to the same group or command.

#### Scenario: Command alias

- **WHEN** a command `create` declares alias `new` and the user runs `laret file new`
- **THEN** the `create` command runs

### Requirement: Cross-field constraints

A command MAY declare cross-field constraints. Each declared constraint MUST be enforced before the action runs: requiring inputs when another input is set, requiring exactly one of a set, requiring all or none of a set, and requiring inputs when another input equals a value.

#### Scenario: Exactly-one constraint

- **WHEN** a command declares that exactly one of two options must be set and the user supplies both
- **THEN** the command fails validation before the action runs

#### Scenario: Constraint naming is checked at declaration time

- **WHEN** a constraint references a name that is not a declared argument or option
- **THEN** building the command fails with an error naming the unknown references

### Requirement: Exit codes

A command action MUST be able to set its exit code through the context. Valid exit codes are the inclusive range 0 to 255.

#### Scenario: Custom exit code

- **WHEN** an action sets exit code 2
- **THEN** the process exits with code 2

#### Scenario: Out-of-range exit code

- **WHEN** an action requests exit code 300
- **THEN** the request fails immediately instead of producing an exit code

### Requirement: Impact metadata

A command MAY declare an impact category. The category MUST be descriptive metadata only and MUST NOT change execution.

#### Scenario: Impact category is exported, not enforced

- **WHEN** a command declares the read-only impact category
- **THEN** the category appears in exported command metadata and the command still runs without any additional check
