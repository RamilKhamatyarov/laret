# Config-as-Code Spec Delta

## Purpose

Make effective configuration traceable and let users and automated checks validate
versioned profiles before using them, without introducing configuration editing.

## ADDED Requirements

### Requirement: Complete provenance

The public configuration API SHALL explain a normalized key with a nullable winner
and one candidate per registered layer in descending priority order. Each candidate
SHALL contain source, priority, and value. Missing or null values SHALL not win.
Equal priorities SHALL retain registration order, matching ordinary lookup.

#### Scenario: Four layers define a key
- **WHEN** defaults, file, environment, and flags define the same normalized key
- **THEN** the explanation contains priorities 30, 20, 10, and 0 in that order and the flag value wins

#### Scenario: Missing key
- **WHEN** no layer defines the requested key
- **THEN** the winner is absent and all layers remain visible as missing candidates

### Requirement: Concrete source identities

Built-in candidates SHALL identify the selected file path, exact environment variable,
provided flag name, or `default`. Bound and direct lookups SHALL describe the same
source that supplies the value; missing candidates SHALL be distinguishable from
defined values without inventing a concrete origin.

#### Scenario: Environment binding
- **WHEN** a key is supplied by a bound `LARET_PORT` variable rather than its direct name
- **THEN** its environment candidate identifies `LARET_PORT`

#### Scenario: Direct environment value wins within its layer
- **WHEN** both a direct variable and a bound variable supply a key
- **THEN** the direct variable supplies the candidate and its exact name is reported

### Requirement: Application-level public inspection

External consumers SHALL be able to inspect configuration using the application's
stored explicit path and profile. Command-scoped inspection SHALL use the same
option defaults, bindings, and supplied flags as command execution without invoking
the command or exposing previously internal file-selection members.

#### Scenario: Bound command flag
- **WHEN** an external consumer inspects a command with a supplied bound flag
- **THEN** its winner matches that command's resolved configuration value

### Requirement: Explain command

The demo SHALL provide `config explain` with required `--key`, optional
`--command "group command"`, and `--format plain|json`. It SHALL declare read-only
impact and display the normalized key, winner, and all candidates using the selected
output format without executing the inspected command.

#### Scenario: Profile explanation
- **WHEN** `config explain --key watch.debounce-ms --profile dev` is invoked
- **THEN** the report identifies the selected profile file and every layer's contribution

#### Scenario: Unknown key is successful
- **WHEN** no layer defines the requested key
- **THEN** the command reports "no layer defines this key" in the active language and exits 0

#### Scenario: Invalid command selector
- **WHEN** the requested command does not exist
- **THEN** a localized diagnostic is emitted and the command exits nonzero without invoking another command

### Requirement: Configuration check report

The demo SHALL provide `config check` accepting explicit file and profile selection.
It SHALL report selected path, active profile, all validation errors, and warnings
for the effective application configuration after environment overrides. It SHALL
exit 1 when errors exist and 0 otherwise. A requested file or profile that selects
no file SHALL be an error, never a validation of fallback or default values. When
both are given, the explicit file is the one validated and only it must exist.

#### Scenario: Invalid effective environment override
- **WHEN** a valid file is overridden with an invalid output format
- **THEN** the report includes that error and its environment source and exits 1

#### Scenario: Valid profile
- **WHEN** the selected profile produces a valid effective configuration
- **THEN** the report identifies that file and profile and exits 0

#### Scenario: Requested file does not exist
- **WHEN** `config check --config typo.yml` names a file that does not exist
- **THEN** the report states that the file was not found, is not valid, and the command exits 1

#### Scenario: Requested profile has no file
- **WHEN** `config check --profile unknown` finds no `.laret.unknown.*` file and no explicit file is given
- **THEN** the report states that the profile has no file, is not valid, and the command exits 1, even if a base file exists

#### Scenario: Explicit file with a profile name
- **WHEN** an existing explicit file is given together with a profile that has no file of its own
- **THEN** the explicit file is validated and the profile is reported as given

### Requirement: Diagnostic initialization exception

Only the demo's `config check` invocation SHALL bypass initialization fail-fast to
produce a report. Other invocations SHALL retain existing fail-fast behavior.
Unreadable or malformed selected files SHALL yield a check report with exit 1,
not an initialization stack trace or silent fallback to a successful configuration.

#### Scenario: Invalid config under check
- **WHEN** initialization encounters invalid configuration for `config check`
- **THEN** validation errors are reported together instead of aborting before dispatch

#### Scenario: Invalid config under another command
- **WHEN** the same configuration is used by an ordinary command
- **THEN** initialization fails as before and the action does not run

### Requirement: Localized reports and stream separation

New user-facing text SHALL use English and Spanish localization resources. Report
data SHALL go to stdout and diagnostics and warnings to stderr. Validation errors
SHALL state key, effective value, source, violated constraint, and suggested fix;
unavailable details in loading errors SHALL be explicitly identified as unavailable.

#### Scenario: Spanish diagnostic
- **WHEN** an invalid profile is checked with Spanish localization
- **THEN** human-readable diagnostic labels and guidance are Spanish and stdout remains report data

### Requirement: Versioned profile validation gate

Committed development and production profiles SHALL reside under `config/profiles/`
and be included in version control. `configCheck` SHALL check every committed profile
using the demo's JVM runtime, without native compilation, from that directory with
all inherited `LARET_*` variables removed. The gate SHALL pass each fixture as an
explicit path and SHALL fail unless the report names that file and profile as valid.
Both `check` and `checkAll` SHALL depend on it.

#### Scenario: Host override cannot hide invalid fixture
- **WHEN** an invalid committed profile is checked with a caller environment that would override its invalid value
- **THEN** the gate still fails and identifies the invalid profile

#### Scenario: Renamed or deleted fixture
- **WHEN** a committed profile fixture is renamed or deleted
- **THEN** the gate fails instead of validating home or default configuration

#### Scenario: Inert fixtures
- **WHEN** a normal application run does not select a committed profile
- **THEN** adding the fixtures does not change its configuration discovery

### Requirement: Compatibility and deterministic verification

The feature SHALL add no runtime dependencies, preserve existing public APIs and
internal visibility, and remain usable in native images. Verification SHALL use
deterministic assertions rather than timing thresholds. Existing behavior SHALL
remain unchanged except the documented resolver correction and diagnostic check.

#### Scenario: Existing layer implementation
- **WHEN** a consumer uses an existing custom configuration layer without provenance customization
- **THEN** ordinary resolution continues to work and inspection uses a documented generic source label
