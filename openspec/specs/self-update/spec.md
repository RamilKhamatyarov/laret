# Self-Update Specification

## Purpose

Defines how the native Laret executable checks for, downloads, verifies, and installs a newer GitHub release, including version comparison, platform asset selection, swap safety, and refusal conditions.

## Requirements

### Requirement: Update check

`laret update check` MUST query the latest published release, report the current version, the latest version, and whether an update is available, and MUST NOT modify any file. It MUST fail when the running application version cannot be parsed.

#### Scenario: Update available

- **WHEN** the current version is `0.2.1` and the latest release is `0.3.0`
- **THEN** the command reports both versions and states that an update is available and how to run it

#### Scenario: Up to date

- **WHEN** the latest release version equals the current version
- **THEN** the command reports that no update is available and writes nothing

#### Scenario: Unparseable current version

- **WHEN** the current application version is not a valid version string
- **THEN** the check fails with an error naming the unparseable version

### Requirement: Update run

`laret update run` MUST locate the running executable, fetch the latest release, download and verify the platform binary, and swap it into place. `--force` (`-f`) MUST skip the version comparison and install the latest release unconditionally. Without `--force`, the command MUST refuse to install when the release is not strictly newer.

#### Scenario: Already current without force

- **WHEN** the latest release is not newer than the current version and `--force` is absent
- **THEN** the command fails with an "Already up to date" message and leaves the executable untouched

#### Scenario: Forced reinstall

- **WHEN** the latest release equals the current version and `--force` is supplied
- **THEN** the release binary is downloaded, verified, and installed again

### Requirement: Strict version comparison

Versions MUST be parsed as `MAJOR.MINOR.PATCH` with an optional leading `v` and optional `-SNAPSHOT` suffix. Numeric components MUST be compared in order. For equal numeric components, a `-SNAPSHOT` build MUST sort below its release. An update is available only when the remote version is strictly newer; unparseable versions MUST never be considered newer.

#### Scenario: Snapshot ordering

- **WHEN** the current version is `0.2.0-SNAPSHOT` and the latest is `0.2.0`
- **THEN** the release is considered newer

#### Scenario: Equal versions

- **WHEN** both versions are `0.2.0`
- **THEN** no update is offered

### Requirement: Major version confirmation

Without `--force`, a strictly newer release that increments the major version MUST require explicit confirmation. The prompt MUST require an answer of `y` or `yes`; any other answer MUST abort. When no interactive console is available, confirmation MUST default to no so unattended runs do not silently take a breaking upgrade.

#### Scenario: Confirmed major upgrade

- **WHEN** the current version is `1.4.0`, the latest is `2.0.0`, and the user answers `yes`
- **THEN** the update proceeds

#### Scenario: Unattended major upgrade

- **WHEN** a major upgrade is required and no console is attached
- **THEN** the update aborts with guidance to re-run with `--force`

### Requirement: Platform asset selection

The latest release MUST be resolved for the current operating system and architecture. Windows MUST select `laret-windows-x86_64.exe`; macOS MUST select `laret-macos-aarch64` on ARM64 and `laret-macos-x86_64` otherwise; Linux on x86_64 MUST select `laret-linux-x86_64`. Unsupported platforms MUST fail without downloading. A leading `v` MUST be stripped from the release tag to form the version.

#### Scenario: Apple Silicon

- **WHEN** the OS is macOS and the architecture is `aarch64` or `arm64`
- **THEN** the `laret-macos-aarch64` asset is selected

#### Scenario: Unsupported platform

- **WHEN** the platform has no published asset
- **THEN** the command fails with an unsupported-platform message and no download occurs

#### Scenario: Missing platform asset

- **WHEN** the selected release does not contain the expected asset name
- **THEN** the command fails identifying the missing asset

### Requirement: Checksum verification

The downloaded binary MUST be verified against the release's `SHA256SUMS.txt`. The entry for the selected asset MUST be a 64-hexadecimal SHA-256 digest; a missing checksum file, a missing asset entry, or a digest mismatch MUST fail the update. On failure the staged download MUST be deleted and the live executable MUST remain untouched.

#### Scenario: Checksum mismatch

- **WHEN** the downloaded file's SHA-256 differs from the published entry
- **THEN** the update fails with both hashes and the current binary keeps running

#### Scenario: Release without checksums

- **WHEN** the release has no `SHA256SUMS.txt` asset
- **THEN** the update fails with an error naming the missing checksum file

### Requirement: Same-directory staging

The verified download MUST be staged next to the running executable as `<executable>.new`. Any stale staged file MUST be removed before downloading, and a staging or verification failure MUST remove the staged file.

#### Scenario: Stale staged file

- **WHEN** a previous `<executable>.new` file exists
- **THEN** it is deleted before the new download starts

### Requirement: Atomic swap with rollback

A successful update MUST rename the live executable to `<executable>.old` and then move the staged binary onto the live name, using an atomic move where supported and a replacing move otherwise. If the final move fails, the previous executable MUST be restored and the command MUST fail with a message stating that the previous version was restored. On success the executable bit MUST be set.

#### Scenario: Swap failure rollback

- **WHEN** moving the staged binary onto the live name fails
- **THEN** the executable renamed to `.old` is moved back and the update reports that the previous version was restored

#### Scenario: Successful swap

- **WHEN** the staged binary is moved successfully
- **THEN** the live path contains the new binary and the old binary is left as `<executable>.old`

### Requirement: Leftover cleanup on next launch

On the next launch, Laret MUST delete files in the executable's directory whose names start with `laret` and end with `.old`. Files still locked by another process MUST be skipped silently and retried later. Cleanup MUST never prevent startup, and MUST be a no-op when the process is not a native Laret binary.

#### Scenario: Locked old binary

- **WHEN** a `.old` file cannot be deleted because another process holds it
- **THEN** startup continues normally and the file is left in place

### Requirement: Native runtime requirement

Self-update MUST only proceed when the current process executable's basename is `laret` or starts with `laret-`, ignoring a `.exe` suffix and case. Running under a JVM (for example a JAR or a development run) MUST fail with a clear message explaining that the released native binary is required.

#### Scenario: JAR execution

- **WHEN** `update run` is invoked from a process whose executable is `java`
- **THEN** the update is refused before any download and no JVM file is modified

### Requirement: Protected install locations

Update commands MUST refuse to replace a binary whose directory is a protected system location: `/usr/bin`, `/usr/sbin`, `/usr/local/bin`, `/usr/local/sbin`, `/bin`, `/sbin`, `/opt`, `C:\Program Files`, `C:\Program Files (x86)`, or `C:\Windows`, including subdirectories. The refusal MUST explain that elevated privileges or the original package manager are required.

#### Scenario: Protected directory

- **WHEN** the running binary is located under `/usr/local/bin`
- **THEN** the update is refused with guidance to update using elevated privileges or the package manager

### Requirement: GitHub API access and errors

Release metadata and assets MUST be fetched from the GitHub Releases API. When `GITHUB_TOKEN` is set and non-blank it MUST be sent as a bearer token; otherwise requests are unauthenticated. HTTP errors, missing tags, and network failures MUST surface as update errors without modifying the installation. If the latest-release endpoint reports no latest release, the most recent published release MUST be used.

#### Scenario: Authenticated request

- **WHEN** `GITHUB_TOKEN` is set
- **THEN** release API and asset requests include bearer authentication

#### Scenario: API failure

- **WHEN** the release API returns an unexpected HTTP status
- **THEN** the command reports the status and does not download or replace anything
