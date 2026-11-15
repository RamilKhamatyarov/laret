# Configuration Spec Delta

## MODIFIED Requirements

### Requirement: Layered precedence

Configuration MUST be resolved with the precedence defaults < configuration files < environment variables < command-line flags. Ordinary value lookup MUST stop at the highest-priority non-null value. Explicit provenance inspection MUST inspect every layer without changing the winner or the ordinary lookup behavior.

#### Scenario: Environment overrides a configuration file
- **WHEN** a configuration file sets `output.format` to `plain` and `LARET_OUTPUT_FORMAT` is `json`
- **THEN** the resolved output format is `json`

#### Scenario: Command-line flag overrides the environment
- **WHEN** `LARET_DIR_FORMAT` is `json` and the invocation supplies `--format plain`
- **THEN** the resolved format for that command is `plain`

#### Scenario: Inspection includes losing layers
- **WHEN** provenance is requested for a key defined by flags and a file
- **THEN** both contributions are visible and the flag remains the winner

### Requirement: Configuration file discovery

The application MUST look for `.laret.yml`, `.laret.yaml`, `.laret.toml`, and `.laret.json` in that order within each directory, searching the working directory before home. An explicit path MUST be tried first. Typed application configuration and command configuration MUST select the same first existing candidate for identical inputs. When none exists, built-in defaults MUST apply without an error.

#### Scenario: Working-directory file is preferred
- **WHEN** both the working directory and the home directory contain `.laret.yml`
- **THEN** the working-directory file is loaded

#### Scenario: First matching name wins
- **WHEN** both `.laret.yml` and `.laret.toml` exist in the working directory
- **THEN** `.laret.yml` is loaded and `.laret.toml` is ignored

#### Scenario: No configuration file
- **WHEN** no candidate configuration file exists
- **THEN** the application starts with built-in defaults and reports no error

#### Scenario: Directory takes precedence over extension
- **WHEN** only working-directory `.laret.toml` and home `.laret.yml` exist
- **THEN** both application and command configuration select the working-directory TOML file

#### Scenario: Home yaml extension
- **WHEN** only home `.laret.yaml` exists
- **THEN** both application and command configuration load that file

#### Scenario: Missing explicit candidate
- **WHEN** the explicit path does not exist but a discovered candidate exists
- **THEN** both consumers retain the existing fallback behavior and select the discovered candidate

#### Scenario: Diagnostic check does not fall back silently
- **WHEN** the explicit path or requested profile selects no file under `config check`
- **THEN** discovery is unchanged, and the check reports the unresolved selection as an error

### Requirement: Profile-specific configuration

A `--profile <name>` selection MUST prefer `.laret.<name>.<ext>` over base files, after the explicit path. Within each group, the working directory MUST precede home, using `yml`, `yaml`, `toml`, `json` order. A selected profile file MUST replace, not merge with, the base. Missing profiles MUST fall back to base files; blank profiles MUST mean no profile. `LARET_PROFILE` MUST NOT select a profile.

#### Scenario: Profile file is preferred
- **WHEN** `.laret.dev.yml` and `.laret.yml` both exist and the invocation passes `--profile dev`
- **THEN** `.laret.dev.yml` supplies the configuration

#### Scenario: Missing profile falls back to the base file
- **WHEN** `--profile prod` is passed and no `.laret.prod.*` file exists
- **THEN** the base `.laret.yml`, `.laret.yaml`, `.laret.toml`, or `.laret.json` file is loaded

#### Scenario: Home profile precedes working-directory base
- **WHEN** the requested profile exists only in home and a base file exists in the working directory
- **THEN** both consumers select the home profile file without merging the base
