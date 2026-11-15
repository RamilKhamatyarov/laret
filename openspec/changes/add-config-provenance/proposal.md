# Proposal

## Why

Typed application configuration and command configuration can select different files,
and callers cannot explain which source supplied an effective value. Configuration
validation also aborts initialization before a diagnostic command can report problems.

## What Changes

- Unify file selection: explicit path, profile candidates, then base candidates;
  working directory before home within each group, with all four extensions.
- Add public, read-only provenance for every layer without exposing internal file selectors.
- Add localized `config explain` and `config check` commands to the example application.
- Allow only the diagnostic check invocation to report invalid configuration instead
  of aborting initialization; preserve fail-fast behavior elsewhere.
- Validate committed development and production profiles through hermetic Gradle gates.
- Document configuration review practices and a short research-backed ADR.

## Capabilities

### New Capabilities

- `config-as-code`: Effective-value explanations, diagnostic validation reports,
  and automated validation of versioned profile fixtures.

### Modified Capabilities

- `configuration`: Unify file discovery across consumers and distinguish exhaustive
  provenance inspection from short-circuit value resolution.

## Impact

Changes affect configuration loading and registry APIs, application initialization
and registry access, example command registration, localization resources, profile
fixtures, Gradle verification, tests, README, and an ADR. Public APIs remain additive,
existing internal members stay internal, and no runtime dependencies are added.
The file-selection discrepancy is intentionally corrected to match documented behavior.

Delivery is divided into three reviewable stages: resolver and provenance; diagnostic
commands; then fixtures, build gates, and documentation. No commits or PR publication
are part of this planning change.

Profile merging, environment-selected profiles, configuration editing, rollout
machinery, validation DSL changes, and performance work are excluded.
