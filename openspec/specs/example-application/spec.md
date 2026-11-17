# example-application Specification

## Purpose

Documents the demo CLI shipped as the framework's example: the command groups it exposes, the inputs each accepts, and the observable output and exit codes of each. Framework-owned behavior such as dispatch, validation, middleware ordering, output formats, dry-run, completion, MCP/schema, documentation, update, scaffolding, watch, diff, stats, piping, parallel execution, history, undo/redo, locale/i18n, prompts, interactive mode, and plugins is documented by the corresponding capability specs and is not restated here.

## Requirements

### Requirement: Demo application surface

The demo MUST be the `laret` application with the build version (currently 0.2.1), the description `Laret - A Cobra-like CLI framework for Kotlin`, self-update enabled, a logging middleware that prints `START: <command>` and `END: <command> took <n>ms` to stderr, and a stats middleware. `--version` MUST print `laret version <version>`, `--help` MUST list every public group, and a normal exit MUST print `Laret shutting down...` to stderr.

#### Scenario: Version is printed

- **WHEN** `laret --version` runs
- **THEN** it prints `laret version 0.2.1` and exits 0

#### Scenario: Help lists the demo groups

- **WHEN** `laret --help` runs
- **THEN** it prints the banner, the command tree including `file`, `dir`, `proc`, `net`, `env`, `sys`, `check`, `fmt`, `echo`, and `upper`, and exits 0

### Requirement: File operations group

`file` (alias `f`) MUST expose `create` (alias `c`), `delete` (alias `rm`), and `read`, with an audit line `AUDIT: file <command>` on stderr and `GUARD: destructive command delete` for delete. `create` MUST require a path, accept `-c/--content` (stdin when empty) and a persistent `-f/--force`, create parent directories, and refuse an existing path with exit 1 unless force is `true`. `read` and `delete` MUST report a missing file and exit 0.

#### Scenario: Create then read a file

- **WHEN** `laret file create <path> --content "hello world"` runs and `laret file read <path>` follows
- **THEN** the file is created, the read prints `hello world`, and both commands exit 0

#### Scenario: Existing file without force

- **WHEN** `laret file create <existing-path> --content x` runs without a true force value
- **THEN** it prints `Error: File already exists: <path> (use --force to overwrite)` and exits 1

#### Scenario: Dry-run creation is narrated

- **WHEN** `laret file create <path> --content hello --dry-run` runs
- **THEN** it prints `[DRY-RUN] Would write 5 bytes to <path>`, does not create the file, and exits 0

### Requirement: Directory operations group

`dir` (alias `d`) MUST expose `list` (alias `ls`) and `create`. `list` MUST default its path to `.`, accept `-l/--long`, `-a/--all`, `-f/--format` with plain, json, yaml, or toml, and `-m/--max-size`; it MUST print `Directory: <path>` with the matching entries and print `listed <n> entries in <path>` to stderr. `create` MUST require a path, accept `-p/--parents`, create intermediate directories, and report an existing directory without failing.

#### Scenario: Plain listing

- **WHEN** `laret dir list <directory>` runs
- **THEN** it prints `Directory: <directory>` and one indented name per entry, and exits 0

#### Scenario: Long listing

- **WHEN** `laret dir list <directory> --long` runs
- **THEN** each entry is printed as `d` or `-`, its size in bytes or a blank for directories, and its name, and the command exits 0

#### Scenario: Nested directory creation

- **WHEN** `laret dir create <path>/a/b` runs on a path whose parents do not exist
- **THEN** the full path is created and the command exits 0

#### Scenario: Path is not a directory

- **WHEN** `laret dir list <path>` is given a path that is not a directory
- **THEN** it prints `Error: Not a directory: <path>`, reports zero listed entries, and exits 0

### Requirement: Process group

`proc` MUST expose `list` (aliases `ls` and `ps`) and `kill`. `list` MUST accept `-n/--name` for a case-insensitive substring match, `-m/--min-cpu-ms`, and `-f/--format`; plain format MUST print a `PID NAME CPU(ms)` header and rows sorted by CPU time descending, and json format MUST print a structured list of the same fields. `kill` MUST require a PID or process-name substring and accept `-f/--force`; an unknown target MUST print `Error: Process not found: <target>` and exit 0.

#### Scenario: Process listing

- **WHEN** `laret proc list --name java` runs
- **THEN** it prints the table header and only rows whose name or command line contains `java`, and exits 0

#### Scenario: Unknown kill target

- **WHEN** `laret proc kill no-such-process` runs
- **THEN** it prints `Error: Process not found: no-such-process` and exits 0

### Requirement: Network group

`net` MUST expose `ping` and `port`. `ping` MUST require a host, accept `-c/--count` (default 4) and `-t/--timeout` (default 1000 ms), space attempts 200 ms apart, and print `Host: <host>` followed by one numbered `OK` or `TIMEOUT` line per probe. `port` MUST require a host and port and report the port as open or closed. Both MUST exit 0, except that a ping whose probes cannot be performed MUST exit 1.

#### Scenario: Closed port is checked

- **WHEN** `laret net port 127.0.0.1 <closed-port>` runs
- **THEN** the command completes with exit 0

#### Scenario: Ping completes its probes

- **WHEN** `laret net ping 127.0.0.1 --count 1` runs and the runtime performs the probe
- **THEN** it prints `Host: 127.0.0.1` and one numbered `OK` or `TIMEOUT` line, and exits 0

#### Scenario: Probe cannot be performed

- **WHEN** `laret net ping <host> --count 1` runs where the runtime refuses the reachability probe
- **THEN** no host or result line is printed and the command exits 1

### Requirement: Environment group

`env` MUST expose `list` and `set`. `list` MUST accept `-n/--name` and `-s/--scope` (user, machine, process), print `Scope: <scope>` then `KEY=VALUE` lines sorted by name, and read the current process environment for every scope. `set` MUST require name and value, accept `-s/--scope` and `-p/--permanent`, print `Set (process): <name>=<value>` for process scope, and otherwise print that permanent changes are unsupported.

#### Scenario: Filtered environment listing

- **WHEN** `laret env list --name LARET_` runs
- **THEN** it prints `Scope: process` and the matching `KEY=VALUE` lines, and exits 0

#### Scenario: Process-scope assignment

- **WHEN** `laret env set DEMO_X 1 --scope process` runs
- **THEN** it prints `Set (process): DEMO_X=1` and exits 0

### Requirement: System group

`sys` MUST expose `info` and `sleep`. `info` MUST print the OS and architecture, Java version, CPU count, memory usage, and a usage bar. `sleep` MUST accept `-s/--seconds` (default 30, coerced to at least 1) and `-m/--marker`, print `Sleeping <n>s (pid <pid>)`, and register shutdown cleanup that writes `cleaned` to the marker and prints `sys sleep: shutdown cleanup ran`. On interruption it MUST print `sys sleep: interrupted before <n>s` and exit 130; otherwise it prints completion and exits 0.

#### Scenario: System overview

- **WHEN** `laret sys info` runs
- **THEN** it prints the OS, Java, CPU, and memory lines plus a usage bar, and exits 0

#### Scenario: Sleep completes

- **WHEN** `laret sys sleep --seconds 1` runs
- **THEN** it prints `Sleeping 1s`, then `sys sleep: completed after 1s`, runs the cleanup, and exits 0

#### Scenario: Sleep is interrupted

- **WHEN** a termination signal arrives while `laret sys sleep --seconds 30 --marker <path>` is waiting
- **THEN** the cleanup writes `cleaned` to the marker, prints `sys sleep: interrupted before 30s`, and the command requests exit code 130

### Requirement: Validation demo group

`check` MUST expose `run` and `connect`. `run` MUST require a port in 1..65535 and enforce its email, format, existing-file, and mutually exclusive `--json`/`--yaml` rules; success prints `check ok: port=<port>` and exits 0, a violation exits 1. `connect` MUST require one of file, url, or stdin, TLS requiring certificate and key, user/password all-or-none, and host/port for remote mode; success prints `connect ok: target=<target> mode=<mode>` and exits 0, a violation exits 1.

#### Scenario: Valid inputs pass

- **WHEN** `laret check run 8080` runs
- **THEN** it prints `check ok: port=8080` and exits 0

#### Scenario: Out-of-range port fails

- **WHEN** `laret check run 70000` runs
- **THEN** it prints `Invalid value for argument 'port': must be a number between 1 and 65535` with the command usage and exits 1

#### Scenario: Connect constraint violation

- **WHEN** `laret check connect --url http://x --tls` runs without certificate and key
- **THEN** it prints `Invalid usage: --tls requires --cert, --key` with the listed constraints and exits 1

#### Scenario: Connect success

- **WHEN** `laret check connect --url http://x` runs
- **THEN** it prints `connect ok: target=http://x mode=local` and exits 0

### Requirement: Formatting demo group

`fmt` MUST expose `json`, which reads JSON text from standard input. Blank input MUST print `Error: No input provided via stdin` and exit 0. Non-blank input MUST be echoed to standard output unchanged followed by a newline; `-c/--compact` and `-q/--query` MUST be accepted but MUST NOT change the emitted text.

#### Scenario: Input is echoed

- **WHEN** `{"a":1}` is supplied on stdin to `laret fmt json`
- **THEN** the command prints the same text followed by a newline and exits 0

#### Scenario: Blank input is rejected

- **WHEN** `laret fmt json` receives no input
- **THEN** it prints `Error: No input provided via stdin` and exits 0

### Requirement: Pipe demo commands

`echo print` MUST accept an optional text argument and write it to standard output without a trailing newline. `upper convert` MUST accept an optional text argument and uppercase it, or read standard input when the argument is empty, writing the result without a trailing newline. Both MUST exit 0. Piping, `pipe run`, and `parallel run` are owned by the command-piping capability.

#### Scenario: Echo prints without a newline

- **WHEN** `laret echo print "hi there"` runs
- **THEN** `hi there` is written to standard output without a trailing newline and the command exits 0

#### Scenario: Uppercase conversion reads stdin

- **WHEN** `piped` is supplied on stdin to `laret upper convert` with no text argument
- **THEN** it writes `PIPED` without a trailing newline and exits 0
