# configuration Specification

## Purpose

Defines how a Laret application resolves configuration values from layered defaults, configuration files, environment variables, and command-line flags, and how applications validate and read the resolved values.

## Requirements

### Requirement: Layered precedence

Configuration MUST be resolved with the precedence defaults < configuration files < environment variables < command-line flags. When a key is present in more than one layer, the value from the highest-precedence layer wins, and a lower layer MUST NOT be consulted for that key.

#### Scenario: Environment overrides a configuration file

- **WHEN** a configuration file sets `output.format` to `plain` and `LARET_OUTPUT_FORMAT` is `json`
- **THEN** the resolved output format is `json`

#### Scenario: Command-line flag overrides the environment

- **WHEN** `LARET_DIR_FORMAT` is `json` and the invocation supplies `--format plain`
- **THEN** the resolved format for that command is `plain`

### Requirement: Configuration file discovery

The application MUST look for a configuration file named `.laret.yml`, `.laret.yaml`, `.laret.toml`, or `.laret.json`, searching the working directory before the user home directory. An explicitly supplied path MUST be tried before discovered files. The first existing candidate MUST be loaded; when none exists, built-in defaults MUST apply without an error.

#### Scenario: Working-directory file is preferred

- **WHEN** both the working directory and the home directory contain `.laret.yml`
- **THEN** the working-directory file is loaded

#### Scenario: First matching name wins

- **WHEN** both `.laret.yml` and `.laret.toml` exist in the working directory
- **THEN** `.laret.yml` is loaded and `.laret.toml` is ignored

#### Scenario: No configuration file

- **WHEN** no candidate configuration file exists
- **THEN** the application starts with built-in defaults and reports no error

### Requirement: Profile-specific configuration

A `--profile <name>` selection MUST make the application prefer `.laret.<name>.<ext>` files over base `.laret.<ext>` files, searching the working directory before the home directory. When no profile-specific file exists, the application MUST fall back to the base files. A blank profile MUST be treated as no profile.

#### Scenario: Profile file is preferred

- **WHEN** `.laret.dev.yml` and `.laret.yml` both exist and the invocation passes `--profile dev`
- **THEN** `.laret.dev.yml` supplies the configuration

#### Scenario: Missing profile falls back to the base file

- **WHEN** `--profile prod` is passed and no `.laret.prod.*` file exists
- **THEN** the base `.laret.yml`, `.laret.yaml`, `.laret.toml`, or `.laret.json` file is loaded

### Requirement: Supported configuration formats

Configuration files MUST be read as YAML for `.yml` and `.yaml`, TOML for `.toml`, and JSON for `.json`. Keys unknown to the application MUST be ignored. A file that cannot be read or parsed MUST fail with a `Config loading failed` diagnostic, and an unsupported file extension MUST fail with `Unsupported config format for file: <name>`.

#### Scenario: Unknown keys are ignored

- **WHEN** a configuration file contains a key the application does not define
- **THEN** the file still loads and the unknown key has no effect

#### Scenario: Malformed file

- **WHEN** a configuration file contains malformed YAML, TOML, or JSON
- **THEN** loading fails with a `Config loading failed` diagnostic naming the underlying parse error

#### Scenario: Unsupported extension

- **WHEN** an explicit configuration path ends in an extension other than `.yml`, `.yaml`, `.toml`, or `.json`
- **THEN** loading fails with `Unsupported config format for file: <name>`

### Requirement: Environment variables

Configuration keys MUST also be readable from environment variables named `LARET_` plus the key in upper case with dots replaced by underscores. Key matching MUST ignore case, hyphens, and underscores, so `LARET_DIR_FORMAT` supplies `dir.format`. Environment values MUST override configuration-file values. When a file is loaded, `LARET_OUTPUT_FORMAT`, `LARET_COLORIZED`, `LARET_VERBOSE`, `LARET_LOG_LEVEL`, and `LARET_LOG_FILE` MUST override the corresponding `output` and `logging` settings.

#### Scenario: Nested key from the environment

- **WHEN** `LARET_DIR_FORMAT=json` is set
- **THEN** reading the key `dir.format` returns `json`

#### Scenario: Hyphens and case are normalized

- **WHEN** an environment variable `LARET_WATCH_DEBOUNCE_MS` is set
- **THEN** it supplies the key `watch.debounce-ms`

#### Scenario: Output format override

- **WHEN** the file sets `output.format: yaml` and `LARET_OUTPUT_FORMAT=json`
- **THEN** the loaded configuration reports output format `json`

#### Scenario: Log level override

- **WHEN** the file sets `logging.level: INFO` and `LARET_LOG_LEVEL=debug`
- **THEN** the loaded configuration reports logging level `debug`

### Requirement: Command-line flag layer

Command-line option values MUST form the highest-priority configuration layer and MUST override environment and file values for the same key. An option MAY be bound to a configuration key, in which case that option's supplied value supplies the key; an option that was not supplied MUST fall through to the lower layers.

#### Scenario: Flag supplies a bound key

- **WHEN** the `format` option is bound to the key `dir.format` and the user passes `--format plain`
- **THEN** reading `dir.format` returns `plain`

### Requirement: Typed configuration readers

Reading a configuration value MUST support string, integer, long, double, boolean, and string-slice readers. A missing key MUST return the supplied fallback. A value that cannot be converted MUST return the fallback rather than throwing and MUST be recorded only as a fine-level diagnostic.

#### Scenario: Boolean literals

- **WHEN** a key holds the text `yes`, `on`, `1`, `true`, `no`, `off`, `0`, or `false`
- **THEN** the boolean reader returns the corresponding `true` or `false` value

#### Scenario: Invalid number

- **WHEN** the integer reader reads a key whose value is `abc` and the fallback is `7`
- **THEN** the reader returns `7` without throwing

#### Scenario: String slice

- **WHEN** the slice reader reads the text `a, b,,c`
- **THEN** it returns the list `a`, `b`, `c` with blanks dropped

### Requirement: Persistent flag resolution

For an option declared persistent that was not supplied on the command line, a configured flag value MUST be looked up in the order `<group>.<command>.<flag>`, `<command>.<flag>`, then `global.<flag>`, following nested mappings. The first key with a value MUST win, and that value MUST override the option's declared default.

#### Scenario: Group-specific value wins

- **WHEN** `flags` configures `file.create.force: true` and `global.force: false` and `file create` omits `--force`
- **THEN** the command reads `force` as enabled

#### Scenario: Global fallback

- **WHEN** no command-specific value exists but `global.format: json` is configured
- **THEN** every command declaring a persistent `format` option that omits it reads `json`

#### Scenario: Explicit flag wins

- **WHEN** the user passes the option explicitly on the command line
- **THEN** the supplied value is used and configured flag values are not consulted

### Requirement: Configuration validation

Configuration validation MUST check that the application name is non-blank and at most 100 characters, that the output format is one of `json`, `yaml`, `toml`, or `plain`, that the logging level is one of `TRACE`, `DEBUG`, `INFO`, `WARN`, or `ERROR` (case-insensitively), and that every configured plugin path is non-blank. All rules MUST run and every failure MUST be reported in one pass.

#### Scenario: Invalid output format

- **WHEN** the configured output format is `xml`
- **THEN** validation reports `Invalid output format: xml. Valid: [json, yaml, toml, plain]`

#### Scenario: Multiple failures in one pass

- **WHEN** the application name is blank and the logging level is `VERBOSE`
- **THEN** validation reports both failures together and remains usable

#### Scenario: Rule execution failure

- **WHEN** a validation rule throws while examining the configuration
- **THEN** validation records `Validation rule failed: <message>` and continues with the remaining rules

### Requirement: Default configuration values

When a setting is absent from every layer, the application MUST use its built-in default: output format `plain`, colorized `true`, verbose `false`, prettify `true`, logging level `INFO`, an empty log file, log pattern `%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n`, maximum log size `10MB`, and maximum log history `10`.

#### Scenario: No configuration supplied

- **WHEN** the application loads with no configuration file and no overrides
- **THEN** output format is `plain`, colorized is `true`, and logging level is `INFO`

### Requirement: Watch configuration defaults

Defaults for live watch MUST be read from the `watch` section: `debounce-ms` (default `150`), `patterns` (default empty), `max-restarts` (default `0`, meaning unlimited), and `max-consecutive-failures` (default `0`, meaning disabled). CLI flags MUST override the configured values.

#### Scenario: Configured debounce

- **WHEN** the `watch.debounce-ms` setting is `200`
- **THEN** live watch uses a 200 millisecond coalesce window unless `--debounce` overrides it

### Requirement: Configuration access from actions

A command action MUST be able to read the fully resolved configuration through the execution context using the same typed readers and the same precedence result as the application. Reading configuration MUST NOT mutate it, and repeated reads of the same key MUST return the same value.

#### Scenario: Action sees the resolved value

- **WHEN** an action reads the key `dir.format` after `LARET_DIR_FORMAT=json` and `--format plain`
- **THEN** it reads `plain`

