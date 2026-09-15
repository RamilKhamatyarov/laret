# Credibility Hardening, Cross-Platform CI, and Namespace Migration

- Status: Accepted
- Date: 2026-09-13

## Context

Laret is publicly visible and evaluated by reviewers. Several things undermined
that: CI wrapped failing checks in `|| echo "WARNING ... (known bug)"` so a red
behavior still produced a green build; the README advertised a version, a
dependency posture, a code example, and a source tree that none matched the
code; every integration job ran only on `ubuntu-latest` although GraalVM Native
Image is a headline feature; Java versions and GitHub Action versions drifted
across workflows; and the Maven coordinates pointed at a private GitHub Packages
registry, making the documented install unusable for anyone outside the org.

An audit of the brief against the actual code changed the shape of the work.

### The reported `dir list` bugs no longer exist

The brief lists four root causes to fix in `example/Main.kt`. Running the built
binary shows all four already behave correctly: `Directory: <path>` prints in
both plain modes, `--long` emits the `d`/`-` type column, and JSON and YAML both
carry `name`/`size`/`isDirectory` with matching entry counts. `listDirEntries`
is already the single filtered source for every format. The defects were fixed
at some point without the CI suppressions being removed, so the suppressions
became stale scaffolding that advertised broken behavior the project no longer
had. There are 33 such markers (17 in `ci.yml`, 16 in `ci-zsh.yml`), not the
five the brief estimates.

### Other stale premises

`release.yml` already exists, triggers on `v*.*.*`, validates version
consistency across three files, and builds native binaries in an OS matrix.
`actions/cache@v3` appears twice and `actions/download-artifact@v4` twice
alongside 47 uses of `@v8`; only one `java-version: '24'` remains.

## Decision

### 1. CI honesty

Remove all 33 warning fallbacks and convert each into a hard assertion under
`set -euo pipefail`. `Main.kt` is left untouched because it is already correct;
changing working output-formatting code carries regression risk for no gain.
Every converted assertion is executed against the real binary before being
committed, so CI only gains checks that genuinely pass today.

### 2. Honest public face

Version becomes `0.2.1` everywhere. A Gradle task generates a `BuildInfo`
constant from `project.version`, and the application reads it, so the version
has exactly one source rather than three literals guarded by a release-time
equality check. "Zero Dependencies" is replaced by an accurate statement of the
real surface (JLine, Jackson, Mordant) plus the claim that actually holds: no
runtime reflection under GraalVM Native Image. The custom `OutputStrategy`
example is rewritten against the real signature, `fun <T> render(data: T)`, and
pinned by a compile-checked `CustomOutputStrategyTest`. The project tree is
regenerated from `find src/main/kotlin`.

The MCP server stops being asserted by substring greps and is exercised as a
protocol: `initialize` must return a `serverInfo` carrying a name and version,
`tools/list` must advertise `laret.file.create` and `laret.file.read` and every
tool's `inputSchema` must be a well-formed object schema whose `required` names
resolve to declared properties, `tools/call` must round-trip a file through
create and read, and unknown methods, unknown tool names and missing required
arguments must come back as JSON-RPC errors with codes `-32601` and `-32602`
rather than as results. The assertions are extracted from the workflow and run
against the real native binary before being committed.

### 3. Cross-platform CI, cost-aware

Native image is expensive, so coverage is tiered by event:

| | JVM (3 OS) | Linux native | macOS native | Windows native |
|---|---|---|---|---|
| Pull request | yes | yes | no | no |
| Push to main | yes | yes | no | no |
| Tag `v*` | yes | yes | yes | yes |
| Nightly | no | no | yes | yes |

Linux native is a deliberate exception to the "no native on PRs" rule: 26
integration jobs consume the `laret-native` artifact, so dropping it would
disable the entire integration suite on pull requests and hollow out the
Dependabot auto-merge gate. It is also the cheapest of the three targets. The
expensive part the tiering targets, macOS and Windows native, stays off PRs.

Nightly macOS/Windows native builds live in their own `nightly.yml` so a nightly
failure never reads as a release failure, and `ci.yml` stays scoped to push/PR.

### 4. Toolchain alignment

Every `setup-java` and `setup-graalvm` uses Java 25, matching
`jvmToolchain(25)`. `actions/cache` moves to v4 and `download-artifact` is
unified on v8. Each workflow carries a header comment recording the minimum Node
runtime its pinned actions require, so the next runtime deprecation is auditable
at a glance.

### 5. Namespace and publishing

Packages move from `com.rkhamatyarov.laret.*` to `io.github.laretframework.*`,
dropping the redundant `laret` segment: `io.github.laretframework.core`,
`.dsl`, `.plugin.model`, and so on. The rename lands as a single mechanical
commit containing nothing else, so it stays reviewable as pure find-and-replace,
and `reflect-config.json` is rewritten with it.

`group` becomes `io.github.laretframework` and `version` becomes `0.2.1` with
no `-SNAPSHOT`. Maven Central publishing is configured with signing and a
staging repository. Central verifies an `io.github.X` namespace by checking
control of `github.com/X`, so publishing cannot succeed until a `laretframework`
GitHub org exists and the namespace is verified with Sonatype. That is a manual,
external prerequisite. The publish step is therefore guarded on its credentials
being present: a tag still validates, builds all three native binaries and
attaches them to the GitHub Release, and logs that publishing was skipped when
secrets are absent, rather than failing the release for reasons unrelated to the
code.

Until Central carries a release, the README documents the
`io.github.laretframework:laret` coordinates as primary and JitPack
(`com.github.RamilKhamatyarov:laret`) as the working fallback, so the install
instructions are never aspirational. The `laret new` scaffold templates emit the
new group and the new `io.github.laretframework.dsl` imports, because a
generated project that cannot resolve its own framework is a worse first
impression than no scaffold at all.

## Consequences

CI now fails on the behavior it claims to test, and `grep -ri "known bug"` over
`.github/` returns nothing. The README describes the software that exists. Native
image is proven on three operating systems without paying for three native
builds on every pull request. The version has one source of truth.

The costs: the package rename invalidates every existing import for downstream
users and makes `git blame` cross a rename boundary for all sources; Maven
Central publishing remains inert until an external org and Sonatype namespace
are provisioned; and nightly macOS/Windows native failures will surface a day
after the change that caused them rather than at the pull request.
