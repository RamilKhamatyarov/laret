# Tasks

Implement the generic managed TUI lifecycle with code and tests. Preserve legacy single-binary behavior and the in-process plugin API. Use controlled fixtures, temporary directories, and injected transport/process/terminal boundaries. Do not install or run Herdr, invent trusted hashes, create commits, or create pull requests. Unchecked tasks represent unfinished implementation or verification.

## 1. Trusted Manifests and Metadata

- [x] 1.1 Add versioned manifest and metadata support for installation shapes, inventories, isolation, and foreground/persistent lifecycles; verify round-trip, invalid-schema, and unchanged legacy metadata tests, and document the format.
- [x] 1.2 Select reviewed local manifest entries by pinned version and OS/architecture without remote trust discovery; test missing/malformed hashes, unsupported targets, missing assets, and actionable pre-download errors. Document explicit manifest trust selection.
- [x] 1.3 Validate relative literal start/stop declarations and isolation requirements; test shell-evaluation rejection and persistent-only identity/stop/deadline requirements while admitting foreground-only tools.

## 2. Verified Installation

- [x] 2.1 Implement bounded ZIP and tar.gz extraction without new runtime dependencies; test traversal, links, special files, absolute/drive paths, duplicate/case-colliding destinations, expansion limits, and companion-file preservation. Document archive constraints.
- [x] 2.2 Verify pinned artifact bytes before extraction and record complete payload inventories; test wrong hashes, download failures and limits, both layouts, and cleanup of failed fresh installs.
- [ ] 2.3 Publish and replace payload plus metadata transactionally without mixed shapes or partial visibility; test rollback, concurrent lifecycle operations, live/uncertain process refusal, and preservation of managed identity/state during reinstall.

## 3. Discovery and Integrity

- [ ] 3.1 Extend discovery and inspection for installed, invalid, disabled, and shadowed conditions; test priority, invalid/disabled first matches, reserved names, orphan payloads, unsupported metadata, symlinks, and refresh after install. Document status output.
- [ ] 3.2 Verify full inventories before launch and stop; test missing/changed/unexpected files, companion runtime mutation, and self-update refusal. Document reviewed-reinstall recovery without accepting new hashes.

## 4. Isolation and Interactive Execution

- [ ] 4.1 Establish dedicated owned roots and instance identity without adopting shared/pre-existing state; test unsafe roots, conflicting launch overrides, child-only environment changes, and standalone-instance separation. Document ownership boundaries.
- [ ] 4.2 Implement terminal admission before any process starts; test noninteractive exit 126, untouched stdout, inherited terminal streams, literal arguments, exit codes, and absence of host output during terminal ownership.
- [ ] 4.3 Coordinate managed waiting, Ctrl-C, resize, restoration, and host shutdown without the legacy kill deadline; test interruption/startup/exit failures, retain legacy interrupt tests, and record real-terminal verification per platform.
- [ ] 4.4 Observe owned foreground and persistent processes across host invocations using verifiable identities rather than names/PIDs alone; test client detach, persistent servers, uncertain liveness, and reused identities.

## 5. Removal and Purge

- [ ] 5.1 Implement plain removal refusal for live/uncertain processes even with force, preserving declared state after stopped removal; test both layouts, confirmation, retained-path reporting, and unchanged legacy removal. Document remove versus purge.
- [ ] 5.2 Implement explicit purge confirmation and verified bounded shutdown; test foreground-only tools without fictitious stop commands, persistent stop success/failure/timeout, partial shutdown, invalid stop payload, and no deletion or forced termination on failure.
- [ ] 5.3 Delete only declared proven-owned state, then installed files; test symlink rejection, shared/pre-existing roots, protected projects, deletion failures, retained retry metadata, and final status. Document partial-failure recovery.

## 6. CLI and Integration

- [ ] 6.1 Wire manifest installation, inspection, and purge into existing plugin commands while retaining URL-plus-checksum flags and built-in precedence; add end-to-end CLI tests and README usage.
- [ ] 6.2 Exercise controlled foreground-only tar.gz and bundled persistent TUI fixtures through the lifecycle, including companion runtime integrity; verify generic behavior without consumer-name branches or third-party downloads.
- [ ] 6.3 Add deterministic CI coverage using existing workflow patterns while retaining strict legacy integration assertions; report native-image and real-terminal checks separately without timing gates or unverified platform claims.

## 7. Final Verification

- [ ] 7.1 Run the full existing suite and applicable formatting/static-analysis gates; fix regressions without weakening assertions, confirm no runtime dependencies or in-process API changes, and record executed checks and residual limitations.
- [ ] 7.2 Validate OpenSpec coherence and map every acceptance scenario to evidence; keep Herdr illustrative and uncertified until release-specific hashes, paths, isolation, and shutdown evidence are reviewed.

## Workflow Follow-up

- Admit third-party release/platform combinations separately; missing evidence means unsupported, not reduced guarantees.
- Review and archive only after implementation and required verification. Do not commit, create a pull request, or publish a release.
