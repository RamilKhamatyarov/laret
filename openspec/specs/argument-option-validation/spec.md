# argument-option-validation Specification

## Purpose

Defines the declarative rules a Laret command uses to validate argument and option values, how cross-field constraints are enforced, and how failures are reported, ordered, and localized.

## Requirements

### Requirement: Rule attachment

Value rules MUST be declared in an optional validation block attached to an individual argument or option declaration and MUST apply only to that input. Cross-field rules MUST be declared at command level. A declaration without a validation block MUST behave as an empty rule set.

#### Scenario: Inline rule constrains only its input

- **WHEN** an argument declares a numeric range rule and the user supplies a value outside that range
- **THEN** only that argument fails, and no other argument or option is reported

#### Scenario: Declaration without rules

- **WHEN** an argument or option is declared without a validation block
- **THEN** its value is accepted without any value rule running

### Requirement: String-shape rules

A value rule set MUST support matching the entire value against a regular expression, enforcing a minimum and maximum length, and requiring membership in a fixed list of choices. Each rule MUST accept a value that satisfies it and MUST produce one failure message for a value that does not.

#### Scenario: Regular expression mismatch

- **WHEN** a value fails a declared regular expression
- **THEN** the failure message names the rule and the value is rejected

#### Scenario: Length and choice rules

- **WHEN** a value is shorter than `minLength`, longer than `maxLength`, or absent from a declared `oneOf` list
- **THEN** the corresponding rule fails and the value is rejected

### Requirement: Numeric rules

A value rule set MUST support a numeric lower bound, upper bound, and inclusive range. Numeric rules MUST trim surrounding whitespace and parse the value as a whole number; a non-numeric value MUST fail with the number-format message regardless of which bound was declared.

#### Scenario: Inclusive range boundaries

- **WHEN** a range rule is declared for 1 through 65535 and the value is exactly 1 or exactly 65535
- **THEN** the value passes

#### Scenario: Non-numeric value

- **WHEN** a numeric rule receives text that is not a whole number
- **THEN** the failure states that the value must be a number rather than reporting a bound comparison

### Requirement: Filesystem and custom rules

A value rule set MUST support requiring an existing regular file, requiring an existing directory, and an arbitrary predicate supplied by the command author. The file and directory rules MUST accept only an existing file or directory respectively, and the custom rule MUST use its required message literally when its predicate rejects the value.

#### Scenario: Existing file required

- **WHEN** an option declares `fileExists` and the supplied path does not name an existing file
- **THEN** the option fails with a message naming the missing path

#### Scenario: Custom predicate rejection

- **WHEN** a custom rule's predicate returns false for the supplied value
- **THEN** the custom rule's message is reported verbatim

### Requirement: Blank-value handling

Blank values MUST skip every value rule except one that explicitly opts into blank handling. `notBlank` MUST run for blank values and MUST fail for an empty or whitespace-only value, so an absent optional option with an empty default does not produce spurious failures.

#### Scenario: Optional flag left unset

- **WHEN** an optional option with an empty default is omitted while it declares a regular expression rule
- **THEN** the rule does not run and no failure is reported

#### Scenario: Not-blank still fires

- **WHEN** an input declares `notBlank` and the supplied value is empty or whitespace
- **THEN** the not-blank failure is reported

### Requirement: All failures in one pass

Validation MUST run every applicable value rule and report every failure in a single pass rather than stopping at the first. Failures MUST be ordered argument declarations, then option declarations, then mutually-exclusive failures, then cross-field constraint failures; declaration order MUST be preserved within each category. See [the validation DSL ADR](../../adr/argument-option-validation-dsl.md).

#### Scenario: Several independent failures

- **WHEN** one argument and two options each violate a rule in the same invocation
- **THEN** all three failures are printed in one run in argument-then-option declaration order

#### Scenario: Value and constraint failures together

- **WHEN** a value rule and a cross-field constraint both fail
- **THEN** the value failure is reported before the constraint failure

### Requirement: Failure output and timing

Value failures MUST print `Invalid value for argument '<name>': <message>` or `Invalid value for option '--<long>': <message>`, and cross-field failures MUST print `Invalid usage: <message>`. Validation MUST run after parsing and the required-argument check but before the pre-execution hook, middleware, and action; any failure MUST print command help and exit 1.

#### Scenario: Invalid invocation exits early

- **WHEN** a command's validation fails
- **THEN** its value failures, the command help, and exit code 1 are produced without running hooks, middleware, or the action

#### Scenario: Field labels distinguish arguments and options

- **WHEN** an argument and an option both fail their rules
- **THEN** the argument failure uses the argument name and the option failure uses the `--`-prefixed long name

### Requirement: Mutually exclusive options

A command-level mutual-exclusion rule MUST name two or more options and MUST fail only when at least two of them were explicitly supplied on the command line; values matching defaults or configuration MUST NOT trigger it. The failure MUST name every supplied member and MUST be reported as an option failure for the alphabetically first supplied member.

#### Scenario: Two exclusive flags supplied

- **WHEN** a command declares `--json` and `--yaml` mutually exclusive and the user passes both
- **THEN** one option failure names both flags and the command exits 1

#### Scenario: Defaults do not trigger exclusivity

- **WHEN** two mutually exclusive options both resolve to non-empty defaults but neither was passed
- **THEN** the mutual-exclusion rule does not fail

### Requirement: Cross-field constraints

A cross-field rule MUST treat an input as set when its resolved value is non-blank and differs from its declared default, considering command line, environment, and configuration. See [argument dependencies and grouping](../../adr/argument-dependencies-and-grouping.md).

#### Scenario: Requires rule

- **WHEN** `--tls` is set but `--cert` and `--key` are not
- **THEN** the failure states that `--tls` requires `--cert` and `--key`

#### Scenario: Exactly-one rule

- **WHEN** an exactly-one constraint over a file argument and two options receives no member, or more than one
- **THEN** the failure states that exactly one is required, or that only one may be given and which members were supplied

#### Scenario: All-or-none and required-when rules

- **WHEN** only one of an all-or-none pair is set, or a required-when trigger has its value but a demanded input is missing
- **THEN** the failure names the missing inputs and the triggering value

### Requirement: Cross-field rule behavior

`requires` MUST demand all listed inputs when triggered; `oneOf` MUST require exactly one member; `allOrNone` MUST require all or none; and `requiredWhen` MUST demand its listed inputs when the trigger resolves to the specified value. Each failure MUST name the missing or conflicting inputs.

#### Scenario: Required dependencies are enforced

- **WHEN** `--tls` is set without `--cert` and `--key`
- **THEN** validation reports both missing dependencies

#### Scenario: Exactly-one and all-or-none are distinct

- **WHEN** an exactly-one rule receives no member, or an all-or-none pair receives only one member
- **THEN** validation reports the corresponding cardinality violation

### Requirement: Required input presence

Any non-blank resolved value MUST satisfy a required input, even when it equals that input's declared default. Positional arguments have no defaults, so set and value-presence checks for them are both non-blank checks.

#### Scenario: Required input resolved from configuration

- **WHEN** `--tls` is set and `--cert` resolves to a non-blank value from configuration, even if that value equals its declared default
- **THEN** `--cert` satisfies the requires rule

### Requirement: Constraint name resolution

Each cross-field rule name MUST resolve to exactly one declared argument or option. An undeclared name or a name shared by an argument and option MUST fail command construction with a clear message.

#### Scenario: Invalid rule name fails construction

- **WHEN** a constraint names an undeclared input or a name shared by an argument and option
- **THEN** command construction fails with a message identifying the invalid name

### Requirement: Message override and localization

Every built-in value rule MUST accept an optional message that replaces the default text and is used verbatim. Default messages MUST be localized through the active interface locale, while a message supplied by the command author MUST NOT be localized. A custom rule MUST require a message and MUST use it literally.

#### Scenario: Author-supplied message

- **WHEN** a rule is declared with `message = "use vN form"`
- **THEN** a failing value reports `use vN form` instead of the built-in wording

#### Scenario: Localized default message

- **WHEN** the active locale is Spanish and a built-in rule fails without an override
- **THEN** the failure text is produced in Spanish by the framework's validation messages

### Requirement: Constraints in help

Command help MUST list declared cross-field constraints when any exist, describing each with option long names prefixed by `--` and argument names enclosed in `<` and `>`, and MUST omit the constraints section when none are declared.

#### Scenario: Constraints section shown

- **WHEN** the user requests help for a command that declares cross-field constraints
- **THEN** a constraints section lists each rule in terms of its declared input names

#### Scenario: No constraints declared

- **WHEN** the user requests help for a command with no cross-field constraints
- **THEN** no constraints section is printed
