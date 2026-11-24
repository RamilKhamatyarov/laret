# Tasks

Eight sequential logical change sets. Do not start phase N+1 until phase N passes its applicable local checks and required approvals. All tasks remain open; proposal verification is not implementation evidence. No commits without a later request. Keep an evidence record per phase with changed paths, exact command/test/URL, result and pending deployment checks. Reverify claimed defects before edits and skip already-fixed work with evidence.

## 1. Site configuration and navigation

- [ ] 1.1 Reverify source and live URL findings, enumerate visible commands/groups and inspect generated output; document actual counts, HTTP/access results and already-fixed native-access handling without changing it.
- [ ] 1.2 Introduce the single committed configuration template and consume its fully generated configuration, removing independent authority and ignored competing output. Preserve generic generator defaults; test YAML escaping, template/path resolution and existing library layout behavior.
- [ ] 1.3 Add canonical URL, repository and source-correct edit links, logo/favicon, accessible brand palettes and required Markdown extensions. Add categorized Guide/Reference/Benchmarks/Changelog navigation and localized search/home 404 recovery; verify metadata, diagrams, code-tab anchors, assets and locale base paths with fixtures.
- [ ] 1.4 PHASE CHECK: build a local site, parse every sitemap/alternate URL for the real absolute base, verify header/edit targets, collapsed navigation, keyboard/theme persistence and wrong-URL recovery in desktop/mobile browser checks. Record screenshots/test names and live checks when accessible; do not claim deployed fixes without deployed evidence.

## 2. Publish existing prose

- [ ] 2.1 Assemble existing benchmark and managed-manifest prose as navigable pages; preserve qualifications about incomplete CLI wiring. Test table rendering and generated landing-page path mapping for README docs, repository-only destinations, assets and fragments in both GitHub/site contexts.
- [ ] 2.2 Obtain approval for an exact committed raw-results artifact because none is currently tracked. Verify its identity/provenance; extend existing Python rendering tooling to generate a labeled accessible chart and table equivalent from those values. Add deterministic data-fidelity tests; do not change benchmark numbers or claims without explicit approval.
- [ ] 2.3 Review existing startup/comparison claims against approved evidence; request approval before any correction or removal, and retain unsupported questions as blockers rather than assert causation from concurrency data.
- [ ] 2.4 PHASE CHECK: verify no internal README/site link or asset is missing, both prose pages and chart are reachable, tables render and chart values/axes/provenance agree with committed evidence. Record local proof and independent live URL results when available. Do not advance with unapproved source/claim changes.

## 3. Documentation generator

- [ ] 3.1 Emit locale-relative See Also links from validated command identifiers and extend generated categorized navigation/source-edit mapping; add golden tests for cross-group links, hidden targets and valid configuration consumption.
- [ ] 3.2 Add check-only validation of exact-locale prose coverage, cross-references including cross-page anchors and orphan command prose, distinguishing registered guides. Test missing/broken/orphan and valid cases with byte/path snapshots proving no writes on either success or failure; preserve man and non-strict fallback tests.
- [ ] 3.3 Wire check-only into CI and document temporary advisory coverage until Phase 4. Exercise existing scaffold/guide commands in temporary authoring/idempotence checks; detect missing committed stubs without auto-repairing them. Document generator/template authority and the local authoring workflow.
- [ ] 3.4 PHASE CHECK: run generator/validator tests and fixtures proving missing prose fails the checker, existing authored files are untouched, links are clickable and only generated configuration is effective. Distinguish expected current repository coverage failure from checker failure; do not claim the blocking coverage rollout is complete yet.

## 4. Reference content

- [ ] 4.1 Use scaffold and guide commands to create missing source pages, review the intended prose in the working tree, and translate stubs in English/Spanish without overwriting existing file-create prose. Verify each visible command has an exact-locale source and scaffolding is idempotent; do not stage files or create a Git commit.
- [ ] 4.2 Author purpose, realistic examples and cross-references for doc generate, plugin install, pipe run, watch live and prompt select in both locales; verify examples against actual help and controlled runs without claiming unfinished plugin features.
- [ ] 4.3 Author equivalent substantial content for config explain, new project, mcp serve, update run and completion interactive in both locales; verify with safe fixture/protocol/terminal tests, not a live self-update or destructive user operation.
- [ ] 4.4 Remove advisory coverage handling and make read-only strict validation blocking in relevant CI/docs builds; test that a deliberately omitted page fails and restored reviewed coverage passes.
- [ ] 4.5 PHASE CHECK: run full strict coverage and link checks in both locales, inspect the ten pages for real user-oriented prose, and verify CI cannot mask missing pages by generating stubs. Preserve hidden-command and generic fallback compatibility.

## 5. README

- [ ] 5.1 Fix emphasis in place, add a concise contents list, condense features to approximately ten linked groups while preserving substantive details, and move the intact structure tree into a linked appendix. Give Type-Safe a verified concrete meaning or obtain approval for removal; test anchors and content preservation.
- [ ] 5.2 Document verified JDK/GraalVM build requirements, distinguish native runtime needs, and separate CLI release/checksum installation from library dependencies. Explicitly identify unavailable macOS Intel release support without adding a build; verify asset names/platform selection against actual release artifacts.
- [ ] 5.3 Add live CI/release badges while retaining useful language/license information, and test GitHub plus assembled-site links/assets/fragments and rendered emphasis.
- [ ] 5.4 PHASE CHECK: inspect both rendered contexts, execute the supported release-install/checksum/first-run walkthrough in a disposable environment and record elapsed user steps separately from prerequisites/network. No unsupported two-minute or benchmark guarantee; request approval before changes outside the agreed README scope.

## 6. Demos and visuals

- [ ] 6.1 Implement a real-command hero story for scaffolding, generated-project execution, help and completion; preserve old file demos in a linked gallery. Verify generated-project build prerequisites and actual terminal output in isolated workspaces, retaining native-access warning protection.
- [ ] 6.2 Add consistent interactive-completion, prompt-selection, live-watch and plugin-install tapes using supported commands and a repository-owned checksum-pinned fixture. Verify expected output, terminal interactions and process cleanup; unsupported scenarios are blockers, not simulated recordings.
- [ ] 6.3 Cache validated recording outputs by tapes plus executable/source, shared style, launcher, fixtures, tool/font versions and environment inputs; skip rendering/tool setup on complete hits. Test cold cache, exact hit, changed tape, changed binary/shared input and missing/corrupt assets.
- [ ] 6.4 Add static social preview, descriptive text/transcripts and accessible landing-page media. PHASE CHECK: verify real recordings, consistent presentation and no stale/missing assets; demonstrate an unchanged warm build does not invoke VHS and retain evidence of cold rendering.

## 7. Locales

- [ ] 7.1 Complete reviewed Spanish navigation, headings, descriptions, guide/landing prose and 404; provide a Spanish landing source rather than copying English README. Preserve command/code identifiers and generic library fallback behavior; test exact-locale publication coverage and localized navigation paths.
- [ ] 7.2 Add publication checks rejecting missing translations and fallback-only pages, with explicit exceptions for technical identifiers/provenance. Include human language review of generated pages rather than treating heuristics as translation proof.
- [ ] 7.3 PHASE CHECK: build both locales, run locale coverage/link checks and review navigation/body/headings/error pages for language consistency. Keep Spanish in the switcher; no phase pass with English explanatory pages silently published as Spanish.

## 8. Project furniture

- [ ] 8.1 Add CONTRIBUTING covering actual build/test/native tasks and documentation/template/scaffold/guide/locale conventions; verify the commands and internal links in a clean documented environment.
- [ ] 8.2 Publish reviewed release summaries with actual release links, retaining one canonical changelog source. Verify release identity/date/content against releases and use an honest no-release state if applicable, never invented entries.
- [ ] 8.3 Verify private vulnerability reporting or obtain an approved private contact, then add SECURITY and issue/PR templates that avoid exposing sensitive reports. Test template links and document the verified reporting route; lack of a route remains a blocker.
- [ ] 8.4 Record API-reference and versioned-documentation deferrals with reasons and reconsideration prerequisites in contributor/architecture guidance; verify no unapproved API generator or versioning infrastructure was introduced.
- [ ] 8.5 PHASE CHECK: verify contributor instructions, changelog navigation and security/template paths, then rerun integrated strict docs/site/link/browser checks and affected tests/quality gates. Produce the phase/status/evidence table, unresolved defects, approvals and deliberate non-goals; distinguish local results from deployed checks and leave all changes uncommitted.
