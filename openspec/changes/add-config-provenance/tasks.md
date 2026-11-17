# Tasks

## 1. Resolver and provenance API (review stage 1)

- [x] 1.1 Extract the shared injected file resolver and delegate both existing consumers to it; verify cwd TOML beats home YML, home YAML works, explicit/profile/base ordering, missing-path fallback, blank profiles, and no-file defaults with deterministic tests.
- [x] 1.2 Add compatible ConfigLayer.describe and ConfigRegistry.explain APIs with concrete built-in origins; verify four-layer ordering, null/missing candidates, direct and bound environment/flag sources, normalization, ties, and existing custom-layer compatibility.
- [x] 1.3 Add the public CliApp facade using stored selectors and existing per-command assembly; verify from a non-friend consumer compilation that it is accessible without widening internal members, and test defaults, bindings, supplied flags, and no command execution.
- [x] 1.4 Document API semantics and the intentional resolver correction; verify ordinary get still short-circuits and the explanation winner agrees with it for the same inputs.

## 2. Diagnostic commands and localization (review stage 2)

- [x] 2.1 Introduce additive initialization report mode with strict defaults and invocation-scoped dispatch handling; test valid and invalid configurations, malformed files, global flags before/after the command, absence of dependent initialization on invalid settings, and unchanged fail-fast for other commands.
- [x] 2.2 Add structured diagnostic details without changing the validation DSL or legacy error behavior; test all built-in constraints, effective environment overrides including legacy names, warnings, and explicit unavailable details for unstructured failures.
- [x] 2.3 Register read-only config explain with required key, command metadata selection, and existing plain/JSON output strategies; add deterministic golden tests for both formats, missing keys, invalid selectors, and target defaults/bindings without target execution.
- [x] 2.4 Register config check using the diagnostic result; verify selected file/profile, complete error and warning reporting, exit 0 for valid data, exit 1 for invalid data, and no initialization stack trace.
- [x] 2.5 Add English and Spanish config.* messages and command usage documentation; verify localization key parity, stdout/stderr separation, and diagnostic key/value/source/constraint/fix fields in both languages.
- [x] 2.6 Report a requested file or profile that selects no file as a check error with exit 1, leaving fallback for other commands unchanged; test a missing explicit path, a profile without a file, an explicit file with a profile name, and fallback for an ordinary command.

## 3. Versioned profiles and build gates (review stage 3)

- [x] 3.1 Add valid config/profiles/.laret.dev.yml and .laret.prod.yml fixtures without a root base config; verify neither is ignored and normal unselected runs remain unaffected.
- [x] 3.2 Add JavaExec configCheck and per-profile executions on the main runtime classpath, with fixture working directory and inherited LARET_* variables removed; verify every committed profile runs, a deliberately invalid isolated fixture fails despite corrective host overrides, and nativeCompile is not a dependency.
- [x] 3.3 Wire configCheck into check and checkAll; verify both task graphs contain profile validation and existing CI verification invokes the gate, adding a minimal invocation only if it otherwise bypasses both entry points.
- [x] 3.4 Add concise multiline README guidance and a self-contained short ADR citing the SOSP 2015 paper; verify documentation covers review, validation, provenance, sensitive output, profile replacement, and excluded rollout machinery.
- [x] 3.5 Pass each fixture to the gate as an explicit path and assert the report's file, profile and validity; verify a renamed fixture fails the gate.

## 4. Cross-stage verification

- [x] 4.1 Run the full existing tests and new deterministic tests under the project's JUnit setup, using existing assertion dependencies; record results and explain any necessary existing-test changes without weakening assertions.
- [x] 4.2 Run Spotless, ktlint, PMD, check, and checkAll; verify no unrelated formatting changes, no new runtime dependency, and no widened internal API.
- [x] 4.3 Verify config explain/check in the existing native-image verification path, including empty candidate values and error reports; update reflection metadata only if new serialized types require it and record any unavailable native verification honestly.
- [x] 4.4 Review all acceptance scenarios against implementation and assemble the three reviewable change summaries; verify no commits or PRs were created automatically and no non-goals entered scope.
