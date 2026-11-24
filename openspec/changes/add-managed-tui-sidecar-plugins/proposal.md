# Managed TUI Sidecar Plugins

## Why

The current sidecar contract manages a verified executable and metadata, but cannot describe a bundled TUI's terminal ownership, persistent server, or safe data removal. The contract must cover both foreground-only tools and tools that keep a server running after the terminal client exits.

## What Changes

- Specify reviewed, version-pinned platform manifests with mandatory artifact checksums and HTTPS sources.
- Extend the existing sidecar model with directory-shaped installations from ZIP and tar.gz archives, retaining the existing single-file layout.
- Specify lifecycle metadata, isolated managed instances, terminal ownership, shutdown coordination, and observable status.
- Distinguish plain removal from ownership-restricted purge; refuse destructive operations while safe shutdown cannot be confirmed.
- Retain strict integrity: self-modified payloads become invalid until verified reinstallation.
- Use Herdr as a release-specific acceptance consumer, not a special implementation path. Unsupported isolation or lifecycle guarantees block support rather than reduce safety.
- Implement the generic lifecycle with production code, deterministic tests, CLI integration, and documentation. Do not install or run Herdr, certify unverified releases, create commits, or create pull requests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `sidecar-plugins`: Add managed interactive lifecycle requirements, archive shapes, trusted manifests, isolated state ownership, and complete removal semantics while preserving legacy single-binary behavior.

## Impact

The proposed contract affects installation, metadata interpretation, discovery, interactive execution, and removal. Existing explicit URL-plus-checksum installs and the in-process plugin API remain compatible. Future realization must add no runtime dependency and must work in JVM and native images.

Out of scope: vendoring, forking, patching or redistributing Herdr; remote install scripts; a terminal emulator or TUI framework; host-managed automatic updates; signing infrastructure, marketplace, rollout, or telemetry. No Herdr release is certified by these planning documents.
