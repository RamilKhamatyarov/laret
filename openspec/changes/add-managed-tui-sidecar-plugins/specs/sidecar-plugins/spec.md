# Managed TUI Sidecar Plugins Delta

## MODIFIED Requirements

### Requirement: Plugin installation command

`laret plugin install <name>` MUST accept `--url` (`-u`) with an HTTPS URL, `--sha256` (`-s`) with the expected digest, `--plugin-dir` (`-d`) to override the target directory, and `--force` (`-f`) to replace an existing plugin. On success this legacy form MUST install the executable plus `<name>.toml` metadata. Installation MUST additionally support selecting a reviewed, version-pinned manifest entry for a managed plugin; that entry supplies its mandatory checksum and platform artifact. Managed installation MUST NOT infer trust from a freshly downloaded checksum file.

#### Scenario: Successful installation

- **WHEN** the user runs `laret plugin install formatter --url https://example.org/formatter --sha256 <64-hex-digest>`
- **THEN** the executable is written to the plugin directory and `<name>.toml` records its name, source URL, lowercase digest, and an ISO-8601 installation timestamp

#### Scenario: Missing digest

- **WHEN** the user omits `--sha256` in the explicit URL installation form
- **THEN** installation fails before downloading and no plugin files are written

#### Scenario: Manifest supplies the pinned artifact

- **WHEN** the user selects an approved managed plugin version with an entry for the current OS and architecture
- **THEN** installation uses exactly that entry's HTTPS URL and reviewed checksum, records its manifest identity, and publishes only after verification

### Requirement: Transactional installation and replacement

Installation MUST download to temporary files inside the target directory, verify the digest, then publish the payload and metadata together. For a single-file installation the payload is the executable; for a directory-shaped installation it is the complete verified directory. A digest mismatch MUST leave any existing plugin untouched. An existing payload or metadata file MUST cause failure unless `--force` is supplied. With `--force`, the previous payload and metadata MUST be restored if publication fails. Managed replacement MUST refuse active or unverifiably stopped owned processes, preserve managed state, and never expose mixed installation shapes.

#### Scenario: Digest mismatch

- **WHEN** the downloaded bytes produce a SHA-256 hash different from `--sha256`
- **THEN** installation fails with the expected and actual digests and the existing plugin remains unchanged

#### Scenario: Name conflict without force

- **WHEN** a plugin of the same name is already installed and `--force` is not supplied
- **THEN** installation fails with a message directing the user to `--force`

#### Scenario: Forced replacement rollback

- **WHEN** a forced replacement cannot move the new metadata into place
- **THEN** the newly written payload is removed and the previous payload and metadata are restored

#### Scenario: Failed fresh archive installation

- **WHEN** archive verification, extraction, or publication fails
- **THEN** no plugin payload, metadata, or staging files remain from the failed fresh installation

#### Scenario: Replacement while a server runs

- **WHEN** the managed plugin has an active server or its liveness cannot be established
- **THEN** replacement fails without changing installed files or state, even with force

### Requirement: Installed plugin discovery and listing

`laret plugin list` MUST inspect each plugin directory and report installed, invalid, shadowed, and disabled conditions with location and reason. Invalid and shadowed entries MUST remain visible. A payload without matching metadata MUST be invalid with "metadata missing". Symlinked entries, reserved names, unparseable or unsupported metadata, filename/name mismatches, missing payloads, and digest mismatches MUST be invalid and MUST NOT execute. Directory payload members MUST NOT be reported as independent plugins. Directory precedence MUST remain unchanged; a disabled or invalid first match MUST NOT activate a later duplicate. Shadowed entries MUST retain inspectable validity and disablement reasons.

#### Scenario: Orphaned executable

- **WHEN** a directory contains an executable file with no corresponding `<name>.toml`
- **THEN** `plugin list` shows that name as invalid with reason "metadata missing"

#### Scenario: Checksum mismatch at discovery

- **WHEN** an executable's current SHA-256 differs from the digest recorded in its metadata
- **THEN** the entry is listed as invalid with reason "checksum mismatch" and is not executable

#### Scenario: Shadowed duplicate

- **WHEN** two searched directories contain metadata for the same plugin name
- **THEN** the first directory's entry is active if valid and enabled, and the second is listed as shadowed

#### Scenario: Disabled installation

- **WHEN** configuration disables a valid discovered entry
- **THEN** listing exposes its disabled condition and invocation returns 126 without activating a shadowed duplicate

#### Scenario: Installation refresh

- **WHEN** installation succeeds and the application reaches its existing discovery refresh boundary
- **THEN** the installed plugin is discoverable and runnable without an application restart

### Requirement: Per-run integrity verification

Before every execution, Laret MUST recompute the executable's SHA-256 and compare it with the recorded digest. A mismatch MUST abort execution, MUST NOT start the child process, and MUST tell the user to reinstall with `--force`. For directory-shaped plugins, verification MUST cover the complete installed payload inventory anchored to the verified artifact, including bundled runtime files. Missing, changed, or unexpected payload files MUST invalidate the entry. Managed stop operations MUST receive the same integrity checks; an invalid payload MUST NOT execute for cleanup.

#### Scenario: Tampered executable

- **WHEN** the plugin executable is modified after installation
- **THEN** invocation fails with an integrity-verification error and the process is not started

#### Scenario: Changed companion runtime

- **WHEN** a bundled runtime file changes while the main executable remains unchanged
- **THEN** discovery reports invalid integrity and launch and stop execution are refused

#### Scenario: Self-update

- **WHEN** the plugin updates its own installed payload
- **THEN** the next integrity check rejects it, does not accept new hashes automatically, and directs the user to verified reinstallation from a reviewed manifest

### Requirement: Plugin environment and stream inheritance

The child process MUST inherit the parent environment, standard input, standard output, and standard error. For legacy entries, Laret MUST override only `LARET_PLUGIN_NAME` with the plugin name, `LARET_DRY_RUN` with `true` or `false`, and `LARET_PROFILE` with the active profile or an empty string when no profile is active. Managed TUI entries MUST additionally apply only reviewed isolation settings to their child environment and declared arguments. These settings MUST NOT modify the parent or global environment and MUST NOT permit escaping the managed instance through conflicting caller overrides.

#### Scenario: Dry-run propagation

- **WHEN** the parent invocation runs in dry-run mode
- **THEN** the sidecar receives `LARET_DRY_RUN=true`

#### Scenario: Profile propagation

- **WHEN** no profile is active
- **THEN** the sidecar receives an empty `LARET_PROFILE`

#### Scenario: Isolated child configuration

- **WHEN** a managed TUI requires a reviewed configuration-root override
- **THEN** its child receives that setting, the parent environment is unchanged, and a conflicting launch override is rejected before startup

### Requirement: Exit codes, removal, and interrupts

A plugin's exit code MUST be returned unchanged. A missing plugin name MUST return 127; invalid, disabled, or unexecutable plugins MUST return 126. `plugin remove <name>` MUST require an existing name, MUST ask for confirmation unless `--force` is supplied, and MUST require `--force` to remove invalid or shadowed entries. Legacy removal MUST delete the executable and its metadata. A legacy interrupt MUST terminate the child gracefully first and forcibly after two seconds, returning 130. Managed TUI removal and interruption MUST instead follow the managed removal, purge, and terminal ownership requirements below; force MUST NOT bypass their safety preconditions.

#### Scenario: Child exit code passthrough

- **WHEN** the plugin process exits with code 3
- **THEN** Laret exits with code 3

#### Scenario: Unknown plugin

- **WHEN** the user invokes a name with no discovered plugin entry
- **THEN** Laret prints that the plugin was not found and exits with 127

#### Scenario: Removing an invalid entry

- **WHEN** the user runs `plugin remove broken` for an invalid entry without `--force`
- **THEN** removal fails with an instruction to use `--force`

## ADDED Requirements

### Requirement: Trusted managed artifact selection

Managed installation MUST select an exact OS/architecture entry from a reviewed version-pinned manifest. Each entry MUST declare an HTTPS URL and SHA-256 from the reviewed manifest. Missing hashes, missing assets, unsupported targets, and unproven lifecycle compatibility MUST fail with actionable diagnostics. Remote install scripts and shell pipelines MUST NOT be executed.

#### Scenario: Missing checksum

- **WHEN** a selected manifest entry lacks a valid SHA-256
- **THEN** installation fails before downloading and writes no plugin files

#### Scenario: Wrong checksum

- **WHEN** a downloaded managed artifact differs from its pinned checksum
- **THEN** installation fails, reports expected and actual digests, and leaves no new installation or staging files

#### Scenario: Unsupported platform

- **WHEN** the current OS/architecture has no approved entry
- **THEN** installation fails before download and identifies the requested target and approved targets without guessing an emulated alternative

#### Scenario: Missing release asset

- **WHEN** the pinned URL no longer supplies the asset
- **THEN** installation fails without publication and directs the user to a reviewed manifest update rather than a latest-release fallback

### Requirement: Archive and single-file layouts

Plugins MUST support binary, ZIP, and tar.gz artifacts within one sidecar model. Single files MUST retain `<root>/<name>` or `<name>.exe` plus `<name>.toml`. Archives MUST use `<root>/<name>/` with adjacent `<name>.toml`, preserving companion relationships. The start executable MUST be a relative path inside that payload. Mutable state MUST remain outside the payload.

#### Scenario: Windows bundled runtime

- **WHEN** an approved Windows TUI ZIP is installed
- **THEN** its executable and companion terminal runtime remain together in one directory-shaped plugin, and the runtime is included in integrity verification

#### Scenario: Generic compressed archive

- **WHEN** a TUI plugin supplies an approved tar.gz artifact
- **THEN** the same layout and lifecycle rules apply without consumer-specific handling

### Requirement: Safe bounded archive admission

Archives MUST be verified before extraction and MUST NOT escape the payload root. Absolute or drive paths, traversal, symbolic/hard links, special files, and duplicate or case-colliding destinations MUST be rejected. Expanded content MUST NOT exceed 1 GiB or 10,000 entries. Existing HTTPS, redirect, download-size, and timeout constraints MUST remain applicable.

#### Scenario: Malicious archive member

- **WHEN** an archive contains an escaping path or link
- **THEN** installation fails without writing outside staging or modifying an existing installation

#### Scenario: Expansion limit

- **WHEN** verified compressed content exceeds either extraction ceiling
- **THEN** extraction stops, staging is removed, and no plugin is published

### Requirement: Managed lifecycle metadata

Metadata MUST identify name, version, source, reviewed manifest, OS/architecture, artifact kind, shape, digest, inventory, and install time. It MUST declare relative start executable and literal arguments, terminal mode, isolation, owned roots, cleanup scope, integrity policy, and foreground-only or persistent lifecycle. Persistent tools MUST declare isolated process identity, stop command, and finite timeout. Unsupported declarations MUST fail closed.

#### Scenario: Lifecycle inspection

- **WHEN** a user inspects a managed plugin
- **THEN** the recorded identity, start/stop contract, ownership roots, integrity policy, status, and reasons are available without launching its TUI

#### Scenario: Unsafe lifecycle declaration

- **WHEN** a start/stop declaration requires arbitrary shell evaluation or escapes the payload
- **THEN** admission fails with a diagnostic identifying the invalid declaration

### Requirement: Managed instance isolation

Managed TUI installation MUST require supported settings that isolate configuration, mutable data, server endpoints, and identity from standalone installations. A declared path alone MUST NOT prove ownership. Shared or pre-existing roots MUST NOT be automatically adopted. If isolation cannot be established for a release/platform, installation MUST fail before publication.

#### Scenario: Named session is insufficient

- **WHEN** a plugin supports named sessions but still shares global configuration or server identity with a standalone instance
- **THEN** managed installation is rejected until complete isolation is demonstrated

#### Scenario: Isolated instance admitted

- **WHEN** all managed roots and endpoint identities are proven isolated for the selected release/platform
- **THEN** launch targets only that instance and leaves standalone instances unchanged

### Requirement: Interactive terminal ownership

Managed TUI launch MUST require interactive input and output before starting any process. The plugin MUST own input, alternate screen, terminal modes, and resize handling during its foreground lifetime. Laret MUST NOT interleave output or restore the terminal prematurely. Completion or startup failure MUST leave the terminal usable; no terminal emulator is introduced.

#### Scenario: Interactive launch

- **WHEN** the installed managed TUI launches in a supported interactive terminal
- **THEN** it receives raw input and resize events as expected, controls its screen, and returns its own exit code with a usable terminal afterward

#### Scenario: Noninteractive launch

- **WHEN** input or output is not an interactive terminal
- **THEN** launch returns 126, starts no plugin process or server, reports an actionable error on stderr, and leaves stdout untouched

### Requirement: TUI interrupt and shutdown coordination

Ctrl-C MUST reach the managed TUI according to its terminal semantics without host reinterpretation as a forced shutdown deadline. The host MUST await foreground completion and preserve its exit code. Host-requested graceful shutdown MUST coordinate with the terminal owner without premature teardown or success reporting. Normal client exit MUST NOT implicitly stop a persistent server.

#### Scenario: Raw-input Ctrl-C

- **WHEN** a running managed TUI consumes Ctrl-C without exiting
- **THEN** Laret remains waiting and does not apply the legacy two-second termination fallback

#### Scenario: Client detaches

- **WHEN** the foreground client exits while its isolated background server remains active
- **THEN** the client exit code is preserved and the server remains observable for later lifecycle operations

### Requirement: Managed plain removal

Plain removal MUST refuse active owned clients/servers or uncertain liveness and explain how to stop them or request purge. Once stopped, removal MUST delete the payload and installation metadata, preserve configuration/session data, and report retained paths and reasons. Force MUST NOT override these checks. Retained data MUST NOT be listed as an installed plugin.

#### Scenario: Running server blocks removal

- **WHEN** plain removal finds a running managed server
- **THEN** it fails without stopping the server or deleting files, even with force

#### Scenario: Stopped installation removed

- **WHEN** plain removal is confirmed and all owned processes are stopped
- **THEN** installed files and metadata are gone, configuration/session data remains, and discovery no longer reports that installation

### Requirement: Purge shutdown precondition

Purge MUST identify targets, require destructive confirmation, and verify no owned processes remain before deletion. An active foreground-only client MUST exit first. Running persistent processes require verified instance-scoped shutdown. Failure, timeout, or uncertain ownership/liveness MUST abort without deleting files or data. Purge MUST NOT force termination or execute an integrity-invalid stop command.

#### Scenario: Stop never completes

- **WHEN** the declared stop deadline expires while a server remains active
- **THEN** purge fails, reports the remaining instance, preserves files and data, and does not report success

#### Scenario: Stop returns failure

- **WHEN** the stop operation returns failure even though some processes stopped
- **THEN** purge performs no deletion and reports the partial shutdown honestly

### Requirement: Purge ownership and completion

After verified shutdown, purge MUST delete only declared, proven-owned configuration/data roots, then payload and installation metadata. Shared/pre-existing roots, user projects, external credentials, and remote state MUST be retained with explanations. Unsafe paths and symlink traversal MUST be rejected. Deletion failure MUST report remaining targets and retain management metadata for retry; success MUST mean all authorized targets are removed.

#### Scenario: Complete managed purge

- **WHEN** shutdown succeeds and all authorized deletions succeed
- **THEN** owned servers are stopped, owned data and installed files are gone, and discovery reflects removal

#### Scenario: Unrelated data encountered

- **WHEN** cleanup encounters a shared root, user repository, or symlink to another directory
- **THEN** it does not traverse or delete that content and explains the retained or rejected target

#### Scenario: Data deletion fails

- **WHEN** a declared owned data root cannot be deleted
- **THEN** purge returns failure, reports completed and pending deletions, and retains installation metadata for retry

### Requirement: Backward-compatible generic lifecycle

Existing single-binary metadata, URL-plus-checksum installation, discovery priority, built-in precedence, execution, and legacy removal/interrupt behavior MUST remain compatible. Managed TUI behavior MUST be selected by explicit lifecycle declarations, never by plugin name. The in-process plugin API MUST remain unchanged. The contract MUST add no runtime dependency and MUST remain usable in JVM and native images.

#### Scenario: Existing executable plugin

- **WHEN** a user installs or invokes an existing legacy executable plugin
- **THEN** its established layout, environment, streams, exit code, and lifecycle behavior remain unchanged

### Requirement: Release admission for managed TUIs

Each release/platform MUST have reviewed hashes, asset mapping, an inventory of owned state, and verified terminal and lifecycle behavior. A foreground-only tool MUST leave no owned processes running after exit. A persistent tool MUST additionally demonstrate isolated server identity and verifiable shutdown. Missing evidence MUST block admission for that release/platform.

#### Scenario: Foreground-only tool

- **WHEN** an admitted foreground-only TUI exits and no owned processes remain
- **THEN** its exit code is preserved and purge can remove its declared owned state and installation without invoking a server stop command

#### Scenario: Persistent tool lifecycle acceptance

- **WHEN** a pinned TUI with a persistent server meets release/platform admission requirements
- **THEN** acceptance covers verified installation, interactive launch, noninteractive refusal, interruption, retained state after plain removal, complete managed purge, and rejection after self-update using the same rules as another TUI plugin

#### Scenario: Persistent tool compatibility gap

- **WHEN** a persistent TUI release lacks evidence for complete isolation or safe server shutdown
- **THEN** managed installation is refused with the specific compatibility gap and no support claim is made
