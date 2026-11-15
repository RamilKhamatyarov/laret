# Argument & Option Validation DSL

- Status: Accepted
- Date: 2026-08-25

## Context

Laret validated almost nothing. `CommandRunner.executeCommand` checked only that required positional arguments were present; every other rule — a port in range, an email shape, a config file that actually exists, two mutually exclusive output flags — had to be hand-written at the top of each `action { }`. That duplicated logic across commands, produced inconsistent messages, and left errors untranslated while the rest of the CLI used `Localization`.

## Decision

Add a declarative validation DSL attached to the argument and option declarations themselves.

### Shape

`argument()` and `option()` gain an optional trailing `validate` block, so rules sit next to the declaration they constrain. Cross-field rules are not tied to a single field, so they are declared at command level:

```kotlin
command("run") {
    argument("port", "Port") { range(1, 65535) }
    option("e", "email", "Contact") { regex("^[^@]+@[^@]+\\.[^@]+$") }
    option("c", "config", "Path") { fileExists() }
    mutuallyExclusive("json", "yaml")
}
```

Both parameters are defaulted, so every existing declaration compiles unchanged.

### Validators

A `Validator` returns `null` for a valid value or a failure message. The builder ships `regex`, `min`, `max`, `range`, `minLength`, `maxLength`, `oneOf`, `notBlank`, `fileExists`, `dirExists`, and `custom(message) { predicate }`. Everything is a plain object with no reflection, so it stays GraalVM-native-safe.

### Blank values

Validators run only on non-blank values. An unset option falls back to an empty default, and validating that would make a regex on an optional flag fail spuriously; absent-but-required is already caught by the existing missing-argument check. `notBlank()` opts in via `runsOnBlank`, so it still fires where a blank is genuinely the error.

### Reporting

`CommandValidator` runs every validator across all arguments and options and collects **all** failures rather than stopping at the first, so one run surfaces every problem. Failures print one line each, then the command help, and the command exits 1 — the same shape as the existing missing-argument path. Validation runs in `CommandRunner.executeCommand` after parsing and the required-argument check, before `preExecute`, so an invalid invocation never reaches user code or middleware.

`mutuallyExclusive` fires only when two or more of the named options were **explicitly provided** on the command line, not when their defaults happen to coincide. That requires knowing which options the user actually passed, so `CommandContext` now exposes `providedOptions`, populated during parsing.

### Localization

Built-in validators format their messages through `Localization.t("validation.*", …)`, with keys added to both `messages.properties` and `messages_es.properties`. A message passed to a builder (e.g. `custom("must be even")`) is used literally — an app author who wants it translated calls `Localization.t` themselves. This ships translated errors by default without forcing bundle-key management for one-off rules.

## Package Boundaries

`Validator`, `ValidationBuilder`, and `ValidationError` live in `model` beside `Argument` and `Option`, which hold them. `CommandValidator` — which needs `CommandContext` and the help formatter — lives in `core`. No new package and no new dependency.

## Consequences

Validation becomes declarative, consistent, and translated, and invalid input is rejected before any command logic runs. The cost is a slightly larger `model` surface and two new defaulted parameters on the DSL. Because validators skip blanks, a rule on an optional field only applies when the user supplies a value — intentional, but worth knowing when a rule appears not to fire. `mutuallyExclusive` is limited to options (not positional arguments), which matches how exclusivity is expressed on a command line.
