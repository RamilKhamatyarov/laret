# Sidecar Plugins Specification

## Purpose

Defines how external executables are installed, verified, discovered, listed, executed, and removed as top-level Laret commands, including the trust and failure rules that govern plugin lifecycle operations.

## Requirements

### Requirement: Plugin installation command

`laret plugin install <name>` MUST accept `--url` (`-u`) with an HTTPS URL, `--sha256` (`-s`) with the expected digest, `--plugin-dir` (`-d`) to override the target directory, and `--force` (`-f`) to replace an existing plugin. On success it MUST install the executable plus `<name>.toml` metadata.

#### Scenario: Successful installation

- **WHEN** the user runs `laret plugin install formatter --url https://example.org/formatter --sha256 <64-hex-digest>`
- **THEN** the executable is written to the plugin directory and `<name>.toml` records its name, source URL, lowercase digest, and an ISO-8601 installation timestamp

#### Scenario: Missing digest

- **WHEN** the user omits `--sha256`
- **THEN** installation fails before downloading and no plugin files are written

### Requirement: Plugin name and digest validation

Plugin names MUST match lowercase kebab-case (`[a-z][a-z0-9-]*`). The expected digest MUST be exactly 64 hexadecimal characters and is compared case-insensitively after lowercasing. Invalid input MUST be rejected before any file is downloaded.

#### Scenario: Uppercase plugin name

- **WHEN** the user attempts to install a plugin named `Formatter`
- **THEN** installation fails with a message requiring lowercase kebab-case

#### Scenario: Malformed digest

- **WHEN** `--sha256` contains fewer than 64 characters or a non-hexadecimal character
- **THEN** installation fails with a message requiring 64 hexadecimal characters and no download starts

### Requirement: HTTPS-only download with bounded redirects

Plugin downloads MUST use HTTPS. Up to five redirects MUST be followed, each resolved target MUST also use HTTPS, and a redirect without a `Location` header MUST fail. Non-2xx responses MUST fail. Downloads MUST be limited to 100 MiB using both the declared content length and the received body size. Connection and request timeouts apply.

#### Scenario: HTTP URL rejected

- **WHEN** `--url` uses `http://`
- **THEN** installation fails before any network request with a message requiring HTTPS

#### Scenario: Redirect downgraded to HTTP

- **WHEN** an HTTPS download responds with a redirect whose target uses `http://`
- **THEN** the download fails and the plugin is not installed

#### Scenario: Excessively large download

- **WHEN** the response declares or delivers more than 100 MiB
- **THEN** the download fails and the temporary file is removed

### Requirement: Plugin directory resolution

`--plugin-dir` MUST be the first search and installation directory. Additional directories MUST come from the platform-path-separated `LARET_PLUGIN_DIR` environment variable, then configured plugin paths (with a leading `~` expanded to the home directory), then `~/.laret/plugins`. Duplicate directories after absolute-path normalization MUST be removed. `plugin install` without `--plugin-dir` MUST target the default `~/.laret/plugins`.

#### Scenario: Directory precedence

- **WHEN** multiple directories are configured and the same plugin name exists in more than one
- **THEN** the entry in the earlier directory is active and entries in later directories are reported as shadowed

#### Scenario: Multiple environment directories

- **WHEN** `LARET_PLUGIN_DIR` contains two paths separated by the platform path separator
- **THEN** both directories are searched in the listed order

### Requirement: Transactional installation and replacement

Installation MUST download to temporary files inside the target directory, verify the digest, then move the executable and metadata into place as a pair. A digest mismatch MUST leave any existing plugin untouched. An existing executable or metadata file MUST cause failure unless `--force` is supplied. With `--force`, the previous pair MUST be backed up and restored if either final move fails.

#### Scenario: Digest mismatch

- **WHEN** the downloaded bytes produce a SHA-256 hash different from `--sha256`
- **THEN** installation fails with the expected and actual digests and the existing plugin remains unchanged

#### Scenario: Name conflict without force

- **WHEN** a plugin of the same name is already installed and `--force` is not supplied
- **THEN** installation fails with a message directing the user to `--force`

#### Scenario: Forced replacement rollback

- **WHEN** a forced replacement cannot move the new metadata into place
- **THEN** the newly written executable is removed and the previous executable and metadata are restored

### Requirement: Installed plugin discovery and listing

`laret plugin list` MUST inspect each plugin directory and report every entry as installed, invalid, or shadowed. It MUST show invalid and shadowed entries rather than hiding them. An executable without matching metadata MUST be listed as invalid with "metadata missing". Symlinked entries, reserved names, unparseable metadata, filename/name mismatches, missing executables, and digest mismatches MUST be invalid and MUST NOT execute.

#### Scenario: Orphaned executable

- **WHEN** a directory contains an executable file with no corresponding `<name>.toml`
- **THEN** `plugin list` shows that name as invalid with reason "metadata missing"

#### Scenario: Checksum mismatch at discovery

- **WHEN** an executable's current SHA-256 differs from the digest recorded in its metadata
- **THEN** the entry is listed as invalid with reason "checksum mismatch" and is not executable

#### Scenario: Shadowed duplicate

- **WHEN** two searched directories contain metadata for the same plugin name
- **THEN** the first directory's entry is active and the second is listed as shadowed

### Requirement: Built-in command precedence

A name that belongs to a built-in Laret command MUST always resolve to the built-in command, and a plugin entry using a reserved built-in name MUST be treated as invalid and MUST NOT execute.

#### Scenario: Collision with built-in command

- **WHEN** a plugin is present whose name equals a built-in command name
- **THEN** invoking that name runs the built-in command and the plugin is never executed

### Requirement: Per-run integrity verification

Before every execution, Laret MUST recompute the executable's SHA-256 and compare it with the recorded digest. A mismatch MUST abort execution, MUST NOT start the child process, and MUST tell the user to reinstall with `--force`.

#### Scenario: Tampered executable

- **WHEN** the plugin executable is modified after installation
- **THEN** invocation fails with an integrity-verification error and the process is not started

### Requirement: Plugin environment and stream inheritance

The child process MUST inherit the parent environment, standard input, standard output, and standard error. Laret MUST override only `LARET_PLUGIN_NAME` with the plugin name, `LARET_DRY_RUN` with `true` or `false`, and `LARET_PROFILE` with the active profile or an empty string when no profile is active.

#### Scenario: Dry-run propagation

- **WHEN** the parent invocation runs in dry-run mode
- **THEN** the sidecar receives `LARET_DRY_RUN=true`

#### Scenario: Profile propagation

- **WHEN** no profile is active
- **THEN** the sidecar receives an empty `LARET_PROFILE`

### Requirement: Exit codes, removal, and interrupts

A plugin's exit code MUST be returned unchanged. A missing plugin name MUST return 127; invalid, disabled, or unexecutable plugins MUST return 126. `plugin remove <name>` MUST require an existing name, MUST ask for confirmation unless `--force` is supplied, and MUST require `--force` to remove invalid or shadowed entries. Removal MUST delete both the executable and its metadata. An interrupt MUST terminate the child gracefully first and forcibly after two seconds, returning 130.

#### Scenario: Child exit code passthrough

- **WHEN** the plugin process exits with code 3
- **THEN** Laret exits with code 3

#### Scenario: Unknown plugin

- **WHEN** the user invokes a name with no discovered plugin entry
- **THEN** Laret prints that the plugin was not found and exits with 127

#### Scenario: Removing an invalid entry

- **WHEN** the user runs `plugin remove broken` for an invalid entry without `--force`
- **THEN** removal fails with an instruction to use `--force`

See [the sidecar plugin decision](../../adr/sidecar-plugin-installation.md) for the constraints behind external executables and explicit trust.
