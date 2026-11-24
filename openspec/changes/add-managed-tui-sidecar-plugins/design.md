# Managed TUI Sidecar Design

## Context

See [proposal](proposal.md) for motivation. This design guides implementation of the generic managed TUI lifecycle with production code, deterministic tests, CLI integration, and documentation. Third-party release certification remains separate.

The inspected sidecar implementation installs one executable and adjacent TOML metadata containing name, source URL, digest, and timestamp. Discovery verifies executable hashes, retains invalid and shadowed entries, and filters disabled entries from execution without a distinct disabled listing state. Execution refreshes discovery, inherits streams, and returns the child exit status; thread interruption initiates termination with a two-second fallback. Removal deletes the executable and metadata without persistent-server or data ownership checks. These observations are not claims that managed TUI support already exists.

## Goals / Non-Goals

**Goals:** Define a reusable, verifiable managed TUI lifecycle and preserve legacy sidecar behavior. Herdr supplies acceptance evidence, never a privileged exception.

**Non-Goals:** Installation or execution of Herdr, new runtime dependencies, changes to the in-process plugin API, terminal emulation, remote install scripts, redistribution, automatic updates, and signing infrastructure. Generic verification uses controlled fixtures, not third-party binaries. No Herdr release is certified by this change.

## Decisions

### 1. Reviewed manifests establish trust

A reviewed, version-pinned manifest binds each supported OS/architecture to an exact HTTPS artifact URL, SHA-256, artifact kind, and lifecycle declarations. The approved manifest revision is recorded with the installation. Release checksum files are review inputs, not automatically trusted install-time authority. Checksums establish correspondence to reviewed bytes, not publisher authenticity or safe behavior.

Alternative: fetching a release checksum at installation time reduces maintenance but makes the trust decision depend on mutable remote content. Existing explicit URL-plus-checksum installation remains supported as an explicit caller trust decision; it does not implicitly confer managed TUI certification.

### 2. One model, two shapes

The legacy layout stays `<root>/<name>` (or `<name>.exe`) plus `<root>/<name>.toml`. Directory-shaped plugins use `<root>/<name>/` plus the same adjacent metadata name. The directory contains the complete verified payload, with a relative entry point preserving companion-file relationships. One logical name occupies one discovery slot regardless of shape; replacement cannot leave both shapes active.

Artifact kind is binary, ZIP, or tar.gz; shape is single-file or directory, not a separate plugin family. Metadata for managed entries adds a schema version, release version, platform/architecture, manifest identity, artifact digest, payload inventory, relative start executable and literal arguments, interactive mode, isolation settings, owned data roots, process lifecycle, and strict integrity policy. Foreground-only tools declare no persistent processes; tools with persistent processes additionally declare their isolated identities, stop operations, and deadlines. Start/stop declarations are not shell programs. Cleanup is limited to declared roots, not arbitrary cleanup scripts.

Alternative: a separate bundle family makes the distinction explicit but duplicates lifecycle rules. Older metadata keeps its existing meaning; missing new fields never imply isolation or purge permission.

### 3. Bounded archives and transactional publication

Verification precedes extraction. Archives must reject traversal, absolute/drive paths, symlinks, hard links, special files, duplicate or case-colliding destinations, and entries escaping the installation root. Existing 100 MiB download and HTTPS/redirect constraints remain. Proposed extraction ceilings are 1 GiB expanded bytes and 10,000 entries; exceeding either rejects the artifact without publication. These conservative planning defaults are not measured Herdr requirements.

Payload and metadata become visible together after validation; failures preserve an earlier installation and remove staging files. Concurrent launch, replacement, and removal cannot expose partial payloads. No installer or post-install script runs. Archive support must not introduce runtime dependencies; unsupported archive constructs fail rather than invoke external extraction scripts.

### 4. Strict integrity, including bundled runtime files

The artifact hash validates downloaded bytes; an inventory anchored to that verified artifact validates installed payload files before each launch or stop operation. Missing, changed, or unexpected payload files invalidate the installation. Mutable state lives outside the payload. No automatic hash acceptance is permitted.

A plugin may physically update itself because this is not a sandbox. Such mutation is unsupported and detected at the next integrity check; recovery is verified reinstallation from a reviewed manifest. Alternative explicit local acceptance was rejected because it bypasses review. Reinstallation preserves managed state and identity, and refuses while owned processes are active.

### 5. Isolated managed instances

Managed installation requires supported plugin settings that separate configuration, data, endpoint identity, and servers from standalone installations. Manifest-approved environment overrides are confined to that child and do not alter the user's global environment. Named sessions alone are not evidence of full configuration isolation.

Ownership requires a dedicated root created for the managed instance and recorded identity; a declaration alone is insufficient. Pre-existing/shared roots cannot be adopted automatically. Project workspaces, repositories, external credentials, remote sessions, and user-selected worktrees are not purge targets. An application unable to meet isolation requirements is unsupported, not silently installed with reduced guarantees.

### 6. Interactive terminal lease

Interactive input and output are prerequisites. Failure occurs before child startup, with exit 126, an actionable stderr message, and untouched stdout. The foreground TUI owns input, alternate-screen behavior, terminal modes, and resize events. Laret does not interleave banners, progress, or shutdown output during that ownership interval. Terminal state must be usable after completion or failed startup.

Ctrl-C follows the plugin's terminal semantics, including raw input; the host does not reinterpret it as its legacy two-second kill deadline. It waits for foreground completion and propagates the actual exit status. Background servers may persist after normal client detach. Host-requested graceful shutdown coordinates with the foreground owner instead of restoring the terminal or reporting completion prematurely. Uncatchable OS termination cannot be guaranteed against.

### 7. Removal and purge are distinct

For managed entries, plain removal refuses while owned clients or servers run, or their status is uncertain. Once stopped, it removes payload and installation metadata but retains state, reporting exact retained paths and why. Retained state is not an installed plugin and is not adopted by an unrelated installation.

Purge requires explicit destructive confirmation (or an explicit noninteractive confirmation), shows its owned targets, checks for owned processes, and skips shutdown only when none are running. An active foreground-only session must exit before purge; Laret does not invent a stop command for it. For a running persistent instance, purge requests verified shutdown and confirms exit within its declared deadline. Timeout, nonzero stop result, unverifiable ownership, or uncertain liveness aborts without deleting data or installed files. The operation may already have stopped some processes; that is reported, not rolled back. No forced termination is bundled into purge.

After confirmed shutdown, only proven-owned declared state is deleted, followed by installed files and metadata. Unsafe targets are rejected; unrelated/shared state is retained with an explanation. A deletion failure returns failure, reports what remains, and retains management metadata for retry. Purge cannot promise rollback of already deleted data. The legacy force flag never bypasses managed ownership, integrity, or live-process safeguards.

### 8. Status and compatibility

Installed, invalid, shadowed, and disabled conditions remain visible with reasons and location. Shadowing follows existing directory priority; validity and disablement remain inspectable even for shadowed entries. An invalid or disabled first match does not activate a later duplicate. Discovery refresh makes successful installations runnable at existing refresh boundaries without a process restart.

Legacy entries retain current invocation, stream/environment inheritance, removal, and interrupt behavior. Managed interactive metadata explicitly selects the additional lifecycle rules. No behavior is keyed to the name Herdr. JVM and native-image users receive the same contract.

## Worked Example: Herdr TUI

Upstream documentation was reviewed on 2026-11-22; moving documentation is background evidence, not a pinned release certification.

- The upstream [license](https://raw.githubusercontent.com/herdrdev/herdr/master/LICENSE) identifies Apache-2.0.
- The [installation guide](https://raw.githubusercontent.com/herdrdev/herdr/master/docs/preview/website/src/content/docs/install.mdx) lists `herdr-linux-x86_64`, `herdr-linux-aarch64`, `herdr-macos-x86_64`, `herdr-macos-aarch64`, and `herdr-windows-x86_64.zip`. The Windows directory includes `herdr.exe` and app-local ConPTY and must remain intact. It describes `herdr update` as intended for upstream-installer-managed installs; updates can leave older servers alive. Laret must use reviewed replacement instead. These names are candidate mappings, not approved URLs or hashes.
- The [configuration guide](https://herdr.dev/tui/docs/configuration/) documents `~/.config/herdr/config.toml` on Linux/macOS and `%APPDATA%\herdr\config.toml` on Windows. It also describes a default worktree location under `~/.herdr/worktrees`; worktrees must not be treated as disposable plugin state.
- The [CLI reference](https://herdr.dev/tui/docs/cli-reference/) documents persistent session attachment, named sessions, server status, `herdr server stop`, and `herdr session stop <name>`. The [session-state guide](https://herdr.dev/tui/docs/session-state/) is the evidence entry point for persisted session behavior. A selected release still needs an authoritative inventory of session, log, socket, configuration, and data paths on every supported platform.

Acceptance must bind an exact release and trusted hashes to these assets, preserve the Windows runtime, prove all relevant state/servers are isolated, and demonstrate bounded stop and safe purge. No exact release, digests, isolation override, complete data-root inventory, or server ownership probe is asserted here. Until that evidence is reviewed, Herdr managed support is blocked, consistent with decision 10. Generic tar.gz acceptance requires a separate non-Herdr case; no claim is made that Herdr publishes tar.gz assets.

## Risks / Trade-offs

- Upstream layout or isolation changes -> require release-specific evidence; fail closed rather than weaken the contract.
- Shared servers or PID reuse -> names/PIDs alone never establish ownership; uncertain status prevents deletion.
- Unsupported archive features or large releases -> explicit rejection under documented limits, not a runtime dependency or script fallback.
- Payload mutation makes the stop executable untrusted -> refuse executing it and preserve files; require independent safe shutdown before verified reinstall.
- Purge can destroy user work -> dedicated roots, explicit confirmation, no workspaces or external roots, and no forced process termination.
- Terminal behavior varies by platform -> support is conditional on observed terminal ownership and restoration, not merely inherited streams.
- A native-compatible contract is not native validation -> report JVM, native-image, and real-terminal verification separately; never claim unexecuted platform checks passed.

## Migration Plan

Existing installations are not automatically rewritten. Preserve old metadata and explicitly opt managed entries into the new contract. Unsupported metadata fails safely rather than falling back to a guessed executable. Implement the verified stages in tasks.md. Failed replacement restores the prior installation. Rollback must not reinterpret managed metadata as legacy metadata or discard owned state. No commit or release publication is part of this work.

## Open Questions

- Which exact Herdr release and manifest revision first satisfy the fixed acceptance contract on each platform?
- What release-pinned upstream evidence proves complete isolation, data locations, server ownership, and stop completion?
- What finite stop deadline is justified for each approved platform entry?

These are admission evidence questions, not permission to weaken requirements. A missing answer means the corresponding release/platform remains unsupported.
