# Sidecar Plugin Installation

- Status: Accepted
- Date: 2026-07-13

## Context

Laret must support optional plugins without dynamic class loading, including in GraalVM Native Image builds. Plugins therefore run as external sidecar executables. Installation must be explicit, verifiable, platform-aware, and usable in automation.

## Decision

Laret uses a sidecar executable lifecycle with three commands:

- `laret plugin install <name> --url <https-url> --sha256 <digest>`
- `laret plugin list`
- `laret plugin remove <name>`

Plugin names use lowercase kebab-case. Built-in commands win over plugin names, and reserved-name collisions are rejected. Installation requires HTTPS and a SHA-256 digest. Downloads use a 10-second connection timeout, a 5-minute request timeout, HTTPS-only redirects, and a 100 MiB limit.

Installed plugins are stored in `~/.laret/plugins` by default. Directory resolution merges `--plugin-dir`, `LARET_PLUGIN_DIR`, configured plugin paths, and the default directory in that order, removing duplicates. The first matching plugin is active; later matches are reported as shadowed. A plugin executable is paired with `<name>.toml` metadata containing `name`, `source_url`, `sha256`, and `installed_at`.

Installation downloads to temporary files, verifies the digest, applies executable and metadata permissions, and replaces the pair transactionally. Existing plugins require `--force` to update. Removal is interactive by default and requires `--force` for automation or invalid/orphaned entries.

Before every execution, Laret verifies the recorded digest again. The sidecar inherits the current environment and receives only these Laret context overrides:

- `LARET_PLUGIN_NAME`
- `LARET_DRY_RUN`
- `LARET_PROFILE`

Standard input, output, and error are proxied transparently. The child exit code is returned unchanged. Missing plugins return 127; invalid or unexecutable plugins return 126. Interrupts are forwarded with graceful wait and force fallback, returning 130 when required.

## Package Boundaries

The implementation is split into:

- `plugin.model`: metadata and configuration models
- `plugin.install`: HTTPS download, digest verification, and transactional installation
- `plugin.runtime`: discovery, validation, execution, and removal

Deprecated typealiases preserve compatibility with the previous plugin model and manager packages.

## Consequences

This design is GraalVM-compatible and keeps plugin execution isolated from the main process classpath. It adds filesystem metadata and executable permission handling, but provides deterministic discovery, explicit trust decisions, checksum verification, and predictable CLI behavior. Plugin integration is covered by deterministic unit tests and CI smoke tests using a repository-owned fixture.