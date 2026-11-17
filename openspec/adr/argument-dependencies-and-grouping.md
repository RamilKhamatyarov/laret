# Argument Dependencies and Grouping

- Status: Accepted
- Date: 2026-10-12

## Context

The validation DSL checks each argument and option on its own, plus one
relationship between them: `mutuallyExclusive`, which fails when two options of
a group are both typed on the command line. Real CLIs need more relationships
than that. `--tls` is useless without `--cert` and `--key`; a command reads its
input from exactly one of a file, a URL or stdin; `--user` and `--password` make
sense together or not at all; `--host` is mandatory only when `--mode remote`.
Today each command has to check these by hand inside its action, after argument
parsing, with its own wording and no mention in help.

## Decision

### Rules

Four rules join `mutuallyExclusive` on the command builder:

- `requires(name, vararg required)`: when `name` is set, every name in
  `required` must have a value.
- `oneOf(vararg names)`: exactly one of `names` is set. None is an error, and so
  is more than one. This is the case `mutuallyExclusive` cannot express, since
  it allows zero.
- `allOrNone(vararg names)`: either every name is set or none is.
- `requiredWhen(name, equals, vararg required)`: when `name` resolves to the
  value `equals`, every name in `required` must have a value.

Rules are declared at command level, beside `mutuallyExclusive`, rather than
as option parameters, because each one relates several names and belongs to
none of them.

### Presence

Laret resolves options from the command line, environment and config files,
so presence follows the resolved value rather than only what was typed:

- A name is **set** when its resolved value is non-blank and differs from its
  declared default. This is what triggers `requires`, and what `oneOf` and
  `allOrNone` count. An option with a non-empty default therefore never sets off
  a rule on its own.
- A name **has a value** when its resolved value is non-blank, wherever it came
  from. This is what satisfies `requires` and `requiredWhen`, so `cert:` in
  `.laret.yml` satisfies `--tls requires --cert`.

Positional arguments have no default, so for them both mean non-blank.

`mutuallyExclusive` keeps its existing meaning, counting only what was typed.

### Names

Rules name options by long name and arguments by name, in one namespace.
`build()` fails fast with a clear message if a rule names something the command
does not declare, or a name that is both an argument and an option, because a
typo in a rule would otherwise silently disable it.

### Reporting

Violations join the existing validation errors: every failure is collected and
reported together, with English and Spanish messages, before the action runs.
Command help gains a Constraints section listing each rule in the same words,
so a user can see a rule before breaking it.

## Consequences

Commands can declare their dependencies instead of hand-checking them, and the
checks behave the same everywhere, including when values come from config or
the environment. Help text documents the rules automatically.

The presence rules are a deliberate split from `mutuallyExclusive`, which still
counts only typed values; changing it would alter existing behaviour. A rule
that names an option with a non-empty default can never be triggered by that
option, which is the intended reading of "ignoring defaults" but is worth
knowing when declaring one. The rules are not yet rendered in generated
Markdown and man pages beyond what help text carries, nor in the schema export.
