# Design

## Context

The ten-question review selected sequential verified delivery; complete Spanish; fully generated configuration from one committed template; reviewed committed stubs with read-only CI; approved committed benchmark evidence; move-not-delete preservation; reproducible real-command demos; deferred API/versioning; reviewed release summaries/private security reporting; and local automated/browser proof with deployed checks when available.

### Reverified starting evidence

| Starting claim | Repository evidence and disposition |
|---|---|
| Missing site configuration/navigation | Confirmed in `mkdocs.yml`: only site name/description, basic Material features, search/i18n and generated-config exclusion are present. No requested canonical/repository/edit URL, branding, palette, extensions or nav. |
| Invalid published sitemap and hreflang | Not independently confirmed: browser-tool access to the site and sitemap failed. Require HTTP/build inspection during Phase 1; do not claim a deployed defect is fixed based only on source. |
| Existing prose not published | `.github/workflows/docs.yml` generates command Markdown and a README landing page, copies GIFs, then builds MkDocs. It does not copy `docs/benchmarks.md` or `docs/managed-sidecar-manifest.md`. Exact public 404 status remains unverified because live fetch failed. |
| Sparse reference | `src/main/resources/docs` contains only `en/file/create.md` and `es/file/create.md`: one command in both locales. Exact current visible command/group totals have not been derived; do not hard-code the supplied approximate counts. |
| Plain See Also and English headings | Confirmed in `MarkdownDocGenerator.kt`: raw See Also list values; literal English section headings, root/group descriptions and navigation labels. Spanish file-create prose itself is translated, so it must not be reported as English. |
| Duplicate site configuration | `DocGenerateCommand.run` adds generated `mkdocsYaml`; root configuration excludes it and the docs workflow uses the root configuration. |
| Strict checks advisory | `.github/workflows/ci.yml` step `Validate documentation (strict, advisory)` has `continue-on-error: true`. Docs deployment generation is non-strict. Existing strict mode already reports missing prose, unknown See Also identifiers and orphans, but it is not a no-write success mode. |
| Three overlapping demos, no cache | Three tapes are tracked: overview, piping and dry-run. They show file-oriented operations; workflow renders every tape on every build. Overview dimensions/timing differ from the other tapes. |
| Native-access warnings | Already addressed in docs generation and demo launcher with `--enable-native-access=ALL-UNNAMED` and a warning probe. Preserve; do not redo or regress. |
| README defects | README starts with `** A...`, has 24 feature bullets, including contentless Type-Safe. Installation gives library dependencies; the long structure tree is inline. Self-update lists macos-x86_64, but release matrix has Linux x86_64, macOS aarch64 and Windows x86_64 only. Static Kotlin/license badges exist; retain their useful information when adding live CI/release status. |
| Project furniture absent | Tracked-file query found no CONTRIBUTING, CHANGELOG, SECURITY, issue templates or PR template. A private security route was not verified; the GitHub security page fetch failed. |
| Chart input exists | No tracked `*results*.json` was found. Rendered tables and ignored downloaded artifacts are not substitutes for approved committed raw evidence. |
| Runtime requirements | `build.gradle.kts` sets Kotlin 2.4.21 and `jvmToolchain(25)`; docs/release workflows select Java/GraalVM 25. Reverify exact supported versions when authoring; do not repeat earlier conversational JDK 21 assumptions. |

Browser-tool failures are access limitations, not proof of HTTP 404 or service failure. Recheck live URLs when possible; use deterministic local generated-site tests regardless. No application build, demo render or deployed-site fix has been performed for this proposal.

## Goals / Non-Goals

Make the current product discoverable and truthful, not enlarge its capabilities. Preserve existing prose and library generation contracts unless explicitly modified by the delta. Do not silently correct benchmark claims, enable unfinished managed-sidecar CLI behavior, add macOS Intel builds, publish versioned/API docs, delete user content, drop Spanish, or rewrite the README wholesale. No commits or deployments are authorized by creating this change.

## Decisions

### One generated configuration, introduced without duplicate authority

Use the existing generator path to produce the complete effective MkDocs configuration from one committed template. The template owns canonical/repository settings, extensions, asset paths, localized section names and navigation category definitions. Command metadata supplies reference entries. There is no separately maintained effective root configuration; generated output is disposable, consumed explicitly by the build and excluded from source tracking as appropriate. Preserve generic Markdown layout defaults and the existing public generator signatures through additive configuration inputs/defaults. Template schema, path bases and YAML quoting require tests; never concatenate unescaped user titles into YAML.

Phase 1 establishes the generated-template authority and baseline categorized navigation. Phase 3 extends generator behavior, source mapping and checks; it does not defer removal of competing configurations until after Phase 1. Guide, Reference, Benchmarks and Changelog must initially route to honest overview pages, with existing verified repository/release destinations where richer content lands in later phases. No invented release summary or fake benchmark page is allowed.

Use a restrained emerald brand accent with neutral backgrounds, verified contrast, paired light/dark palettes, and accessible named toggles. Reuse an existing brand asset if suitable; otherwise create a simple original wordmark/icon without implying an established logo. Configure tables, admonitions/details, tabbed fenced code with stable anchors, and Mermaid support with accessible alternatives. No animated media is the sole carrier of information.

Canonical URL is `https://ramilkhamatyarov.github.io/laret/`; verify base-path handling for English, Spanish, sitemap, 404 and assets. Source-edit URLs must map generated pages to actual source prose or template/README/guide sources, not nonexistent generated paths. Group categories expand only on request; a reference directory may link categories but must not dump every command/group on every page.

### Content assembly and link compatibility

Copy existing benchmark and managed-manifest prose into the assembled site with controlled path mapping. Preserve technical qualifications: managed foundations are not a promise of wired install/launch support. Repository-only links such as source, LICENSE and build files become canonical repository URLs in the generated landing page; doc/asset links map to assembled destinations. Resolve GitHub and site fragments with tests, including images, reference links and nested relative paths. Keep source README edits minimal and preserve author content. Do not indiscriminately replace links inside code fences.

No raw benchmark results are tracked now. Phase 2 must request approval for an exact committed artifact and provenance before chart work can pass. Extend the existing Python renderer with deterministic chart output rather than manually drawing data. Do not fetch latest results at docs build time. Tables and charts include units, scenario boundaries, source identity, accessible text and measurement caveats. The README currently claims native is ahead of picocli everywhere and within five times Cobra memory; newer evidence may conflict. Flag disputed claims and seek explicit permission before changing any. Concurrency whole-process metrics do not independently establish a native-startup cause. Keep the existing startup caveat pending evidence review; do not strengthen it, fabricate supporting values, or silently remove it. An unresolved required claim/source approval blocks Phase 2.

### Check-only validation and authored coverage

Extend the existing generation/validation pipeline additively with check-only execution that renders in memory and validates source coverage, See Also targets, local/cross-page anchors and orphan source pages. It creates no files/directories even on success and aggregates actionable diagnostics. Preserve default non-strict behavior for external library consumers, Groff output and hidden-command rules.

Resolve canonical See Also identifiers against metadata and emit relative locale-specific links rather than treating hyphenated command identifiers as arbitrary paths. Include published guides in a declared content manifest so they are not incorrectly reported as orphaned command prose. Hidden targets must not leak through visible navigation.

Phase 3 tests validator failures with controlled missing/broken/orphaned fixtures and wires the real CI invocation, temporarily retaining the acknowledged advisory coverage status until Phase 4. This phase passes on checker correctness, not on pretending sparse repository coverage is already complete. Phase 4 commits reviewed stubs using scaffold and guide commands, writes substantial priority pages in both locales, then removes advisory behavior. CI may verify scaffolding idempotence in a temporary tree and fail if new stubs would be necessary; it must not repair source coverage automatically. Record this distinction explicitly in workflow comments and author guidance.

### README preservation and installation accuracy

Reduce the opening list to roughly ten linked groups, moving rather than deleting distinct feature information. Preserve Type-Safe only if a concrete supported contract can be explained; otherwise move the existing label into the reviewed content-preservation record pending a meaningful replacement, not silent deletion. Move the existing structure tree intact into a linked appendix rather than replacing it with an unsupported architecture drawing.

Document native release installation separately from JVM library use/development. Use actual release asset names and SHA256SUMS handling, executable permissions and platform-appropriate commands. Do not promise an Intel macOS artifact: retain the platform discussion but explicitly describe its unavailable release-build status. A two-minute first-run walkthrough is tested from an available release; record prerequisites/network separately and do not make network timing a CI gate. No new release architecture is part of this documentation change.

### Authentic demos and complete cache identity

Hero story executes project scaffolding, the generated project, help and completion in a pseudo-terminal. Differentiator recordings exercise actual interactive completion, prompt select, live watch and legacy verified plugin installation. Use a reviewed repository-owned HTTPS fixture and pinned checksum; no fabricated managed manifest capability, third-party installation or simulated success. Preserve existing tapes/GIF access via a gallery. Record commands/preconditions, use temporary workspaces and clean up processes without touching user directories.

Cache output per recording using tape plus shared style, fixture hashes, executable/source identity, launcher, renderer/tool versions, fonts and render environment. A tape-only cache could serve stale command output after a product change; include those dependencies even though tapes are the primary key input. Exact hits skip VHS and unnecessary render-tool installation; misses render only affected assets. Missing/corrupt outputs invalidate a hit. Preserve native-access flags and warning tests. Produce a static preview frame with appropriate metadata and text alternatives.

### Full Spanish and trustworthy project guidance

Finish both locale trees: source command prose/stubs, headings, navigation, overview/guide pages, README-derived landing content and 404. Spanish landing content must have its own reviewed translation source rather than copying English README as Spanish. Keep generic ResourceProseProvider fallback for library compatibility, but make publication validation reject fallback-only content. Technical identifiers and quoted provenance may remain unchanged; automated coverage is supplemented by human language review, not a claim that language heuristics prove translation quality.

Release summaries are reviewed committed snapshots linked to actual GitHub releases, not build-time API dependency or invented entries. Verify current releases before writing. Verify private vulnerability reporting or obtain an approved private contact; do not send sensitive reports to public issues by default. Contribution guidance uses actual Gradle tasks/toolchain requirements and documents template/generation, authoring and localization conventions. Add issue/PR templates requesting reproduction, docs/tests and privacy hygiene. API reference is deferred until supported API scope and tooling are chosen; versioning is deferred until release retention and translation maintenance policies exist. Record both decisions and prerequisites in the contribution/architecture documentation, not just this proposal.

## Delivery and Acceptance

Each phase is one logical working-tree change set, with its own tests/docs and a passing evidence record before continuing. Preserve checkpoints in reviewable patches or evidence summaries without creating commits. Phase 1 uses local canonical/site/navigation/theme/404 tests; Phase 2 uses publication/link/table/chart tests; Phase 3 uses generator/checker tests; Phase 4 uses full strict checks; Phase 5 validates both README contexts and the supported-install walkthrough; Phase 6 verifies real recordings and warm-cache behavior; Phase 7 validates complete locale coverage plus language review; Phase 8 validates contribution instructions and verified release/security paths.

Deployment checks are performed when available without authorizing a deployment. Report local passes and pending public verification separately; never label a deployed defect fixed solely from a local build. Do not begin the next phase with failed local checks or unresolved required approvals. Existing English material may remain during staged work, but Phase 7 cannot pass until mixed-language publication is eliminated.

## Risks / Trade-offs

- Full translation requires substantial authored content; stubs count as reviewed coverage, not proof of substantive quality. Priority pages must not be bare stubs.
- Fully generated site settings can disrupt library consumers; use additive defaults and regression tests for existing layouts and man output.
- Missing committed raw results and unverified security routing are explicit approval/evidence blockers, not invitations to invent sources.
- Strict validation can deadlock staged work if enabled before coverage; checker correctness lands in Phase 3, production blocking enforcement in Phase 4.
- Demos may need network/tool downloads and real terminal support; reproducibility checks disclose these dependencies rather than fake offline independence.
- External link outages are distinct from deterministic broken internal links; record URL, timestamp and result, use bounded retries, and never interpret an unavailable browser tool as a server response.

## Open Questions Requiring Evidence During Implementation

Which exact raw benchmark artifact will the user approve? Which existing private security-reporting route is enabled, or which contact will be approved? These do not alter the chosen design: Phase 2 and Phase 8 respectively remain blocked until resolved. Exact public URL defects and current command counts must be reverified rather than assumed. No further product-design questions remain from the ten-question interview.
