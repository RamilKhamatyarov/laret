# Laret — OpenSpec Documentation

Self-contained OpenSpec documentation for **Laret**, a Cobra-like CLI framework for Kotlin
with GraalVM Native Image support. Everything needed to understand the documented behavior
lives in this directory.

## Layout

| Path | Contents |
|------|----------|
| `config.yaml` | OpenSpec project configuration (`schema: spec-driven`) |
| `specs/` | Current-state capability specifications, one directory per capability |
| `adr/` | Architecture Decision Records that explain cross-cutting constraints |
| `changes/` | Change proposals; `archive/` holds completed ones (none in flight) |

## What Laret is

Laret is a Kotlin framework for building command-line applications from a declarative
`cli { group { command { } } }` DSL. It provides typed positional arguments and named options,
a declarative validation DSL, priority-ordered middleware with GLOBAL/GROUP/COMMAND scoping,
graceful shutdown, colored output, JSON/YAML/TOML/table/plain rendering, shell completion for
bash/zsh/PowerShell, Damerau-Levenshtein typo suggestions, command piping, bounded parallel
execution, directory watching and live watch mode, an LCS diff engine, command metrics,
history/undo/redo, interactive prompts, 12-factor configuration, man-page and Markdown
documentation generation, an MCP server plus schema export, verified sidecar plugins,
self-update, project scaffolding, and GraalVM native-image packaging.

The `laret` binary built from this repository is a demo application that exposes those
capabilities as commands; its surface is specified in `specs/example-application/spec.md`.

## Specification index

| Capability | Covers |
|------------|--------|
| [cli-dsl](specs/cli-dsl/spec.md) | Application, group, command, argument, and option declaration; execution-context readers; lifecycle hooks; cross-field constraints; exit codes |
| [command-dispatch](specs/command-dispatch/spec.md) | `group command [args]` grammar, option parsing forms, help and version flags, unknown-input errors, exit codes, hidden commands, `__complete` |
| [argument-option-validation](specs/argument-option-validation/spec.md) | Validator DSL, single-pass failure aggregation, ordering relative to hooks and the action, localization of messages |
| [middleware](specs/middleware/spec.md) | Middleware contract, priority ordering, scope resolution, `middleware list` chain inspection |
| [graceful-shutdown](specs/graceful-shutdown/spec.md) | Shutdown cleanups on SIGINT/SIGTERM, LIFO order, handles, fast-signal mode |
| [configuration](specs/configuration/spec.md) | Defaults < files < environment < flags precedence, profiles, typed getters, persistent flags, config validation |
| [dry-run](specs/dry-run/spec.md) | `--dry-run` filesystem swap, stderr narration, history/undo exclusions |
| [output-formats](specs/output-formats/spec.md) | JSON, YAML, TOML, table, plain rendering and custom output strategies |
| [colored-output](specs/colored-output/spec.md) | Color helpers, terminal detection, `NO_COLOR` and `CLICOLOR_FORCE` |
| [typo-suggestions](specs/typo-suggestions/spec.md) | "Did you mean?" ranking, warning-only unknown flags, `--fix`, `LARET_NO_SUGGEST` |
| [command-piping](specs/command-piping/spec.md) | `pipe run`, `---` and `|` separators, stdin carry, failure propagation, programmatic API |
| [parallel-execution](specs/parallel-execution/spec.md) | `parallel run`, bounded job pool, output and exit-code behavior |
| [interactive-ux](specs/interactive-ux/spec.md) | Interactive shell loop, prompts, spinners, progress bars |
| [shell-completion](specs/shell-completion/spec.md) | bash/zsh/PowerShell script generation, install, completers, `__complete` protocol |
| [mcp-and-schema](specs/mcp-and-schema/spec.md) | MCP stdio server, tool mapping and impact profiles, schema export formats |
| [documentation-generation](specs/documentation-generation/spec.md) | Man pages, `doc generate/scaffold/guide/index`, link validation |
| [sidecar-plugins](specs/sidecar-plugins/spec.md) | Verified HTTPS install, SHA-256 checks, discovery, precedence, execution |
| [self-update](specs/self-update/spec.md) | Release selection, checksum verification, atomic binary swap, rollback |
| [project-scaffolding](specs/project-scaffolding/spec.md) | `new project` wizard, generated layout, collision handling |
| [locale-i18n](specs/locale-i18n/spec.md) | Locale resolution and persistence, message bundles, fallbacks |
| [watch](specs/watch/spec.md) | `watch run` event stream, `watch live` globbing, debounce, failure caps |
| [file-diff](specs/file-diff/spec.md) | LCS diff engine, unified/plain/JSON formats, binary detection |
| [stats-metrics](specs/stats-metrics/spec.md) | Recorded metrics, persistence, Prometheus/JSON/plain output |
| [history-undo-redo](specs/history-undo-redo/spec.md) | History recording and replay, aliases, undo/redo stacks |
| [native-image-and-performance](specs/native-image-and-performance/spec.md) | Native-image build constraints and the performance targets the project holds itself to |
| [example-application](specs/example-application/spec.md) | Demo command groups (file, dir, proc, net, env, sys, check, fmt, echo, upper) |

## Architecture Decision Records

| ADR | Decision |
|-----|----------|
| [argument-dependencies-and-grouping](adr/argument-dependencies-and-grouping.md) | Argument dependency rules (`requires`, `oneOf`, `allOrNone`, `requiredWhen`) and how they group with mutually-exclusive options |
| [argument-option-validation-dsl](adr/argument-option-validation-dsl.md) | Declarative validator DSL attached at argument/option declaration site |
| [colored-output-native-image-and-detection](adr/colored-output-native-image-and-detection.md) | Dropping Jansi in favor of direct terminal detection, for native-image compatibility |
| [command-pipeline-failure-propagation](adr/command-pipeline-failure-propagation.md) | How a failed pipe stage stops the chain and surfaces its exit code |
| [concurrency-benchmark-suite](adr/concurrency-benchmark-suite.md) | The concurrent/parallel execution benchmark suite and its scenarios |
| [graceful-shutdown-cancellation-scope](adr/graceful-shutdown-cancellation-scope.md) | Shutdown cleanups delivered through a cancellation scope |
| [indexed-fanout-dispatch](adr/indexed-fanout-dispatch.md) | Indexed in-process fan-out for bounded parallel execution |
| [laret-native-performance-targets](adr/laret-native-performance-targets.md) | Performance targets relative to Cobra, clap, and picocli |
| [lean-core-and-native-image-size](adr/lean-core-and-native-image-size.md) | Keeping the core lean to bound native-image size |
| [live-watch-mode](adr/live-watch-mode.md) | Glob-based live re-run loop with debounce and failure caps |
| [middleware-priority-and-scoping](adr/middleware-priority-and-scoping.md) | Priority ordering and GLOBAL/GROUP/COMMAND scope resolution |
| [native-startup-floor](adr/native-startup-floor.md) | The start-up floor the native image must not regress |
| [performance-benchmark-measurement-policy](adr/performance-benchmark-measurement-policy.md) | Pinned cores, kernel-reported wall clock and peak RSS, reproducible runs |
| [si-1-contract-first-command-model](adr/contract-first-command-model.md) | Contract-first command model used for MCP and schema export |
| [sidecar-plugin-installation](adr/sidecar-plugin-installation.md) | Verified, checksummed, transactional plugin installation |
| [signal-latency-rss-and-dispatch-floor](adr/signal-latency-rss-and-dispatch-floor.md) | Signal latency, runner RSS, and dispatch-floor targets |
| [typo-suggestions](adr/typo-suggestions.md) | Damerau-Levenshtein suggestions, warning-only flags, opt-in correction |

## Conventions

- Specs state externally observable behavior: inputs, outputs, errors, exit codes, ordering,
  limits, files, and protocols. They do not name internal classes or functions.
- Requirement text uses SHALL or MUST for normative behavior. Every requirement has at least
  one `#### Scenario:` block with WHEN/THEN bullets.
- Specs link to an ADR only when the ADR explains a constraint, using a relative link such as
  `../../adr/typo-suggestions.md`. The copies under `adr/` are the authoritative record for
  this documentation set.
- `openspec validate --specs --strict` passes for every spec in this directory.
