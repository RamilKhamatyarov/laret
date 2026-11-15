# Design

## Context

See proposal.md for motivation. Inspection confirms that ConfigLoader searches all
working-directory extensions before home, but omits home `.yaml`; FileLayer alternates
directories per extension. Both currently fall through when an explicit path is absent.
ConfigLayer exposes priority and get only. FileLayer.selectedFile is internal.
CliApp.createConfigRegistry is internal and excludes persistent options from its
declared-default layer. Preserve that existing execution behavior in the public facade.

CliApp.initSuspending loads, validates, applies configuration, then initializes plugins.
Main strips global selectors before calling init; dispatch can initialize again.
ConfigValidator returns string errors and warnings through ValidationResult.
ConfigLoader applies legacy environment overrides only after a file has loaded.
OutputStrategy already supports plain and JSON rendering. checkAll explicitly lists
verification tasks. Existing registry tests use injected environments and file readers.

## Goals / Non-Goals

Goals: one file-selection decision, additive provenance, useful invalid-config reports,
and deterministic validation of committed profiles. Keep changes grouped into the three
requested review stages and avoid replacing command execution or validation abstractions.

Non-goals: deep merge, LARET_PROFILE selection, config editing, new validation DSL,
rollout systems, benchmarks, new runtime dependencies, or broadened internal visibility.

## Decisions

### Shared resolver

Extract a resolver with injectable working directory, home directory, and existence
predicate. Both loaders delegate to it. Order candidates as explicit path, profile
group, base group; each group orders directories before extensions. Retain missing
explicit-path fallback and complete replacement of base files by profiles.
Duplicating fixes in both existing resolvers would leave future divergence possible.

### Additive provenance

Give ConfigLayer.describe a compatibility default and let built-in layers override
concrete source labeling. A candidate records source, priority, and nullable value;
explain returns normalized key, nullable winner, and candidates for every layer.
Missing layers use an explicit generic layer label, not a fabricated file or variable.
Null continues to mean absent. Equal priorities keep insertion order.
Each layer computes its value and source together so direct and bound names cannot
disagree. Keep normal get short-circuiting; only explain enumerates all layers.

Add a public CliApp inspection facade which delegates to existing registry assembly
and stored selection settings. Retain existing internal methods. A command-scoped
facade accepts supplied option values; the demo's --command selects metadata only,
does not invoke that command, and does not treat explain's --format as a target flag.
Without a command, inspect application defaults, selected file, and environment.
Preserve legacy typed override names such as LARET_LOG_LEVEL when describing typed
validation results; do not falsely attribute them to LARET_LOGGING_LEVEL.

### Diagnostic lifecycle

Keep existing initialization signatures and strict behavior through delegation to an
additive policy-bearing path. Determine the actual config/check command after normal
global-selector processing, not by substring matching. Carry report mode through
dispatch reinitialization only for that invocation, with no sticky global bypass.
Capture expected load/parse and validation failures into a diagnostic result. Do not
apply invalid settings or initialize configuration-dependent plugins before reporting.
Other exceptions and other commands retain their existing error path.

Validate the effective AppConfig after existing environment overrides. Do not introduce
new typed override semantics for ordinary no-file startup. Reports distinguish loaded
values, defaults, and unavailable values after a parse failure.

### Structured reporting without a validation DSL change

Keep current ValidationRule and legacy error strings compatible. Add structured
diagnostic details alongside existing results for built-in rules: key, constraint,
fix identifier, and value. Resolve the source from the same effective configuration.
Custom rule failures lacking structured details use explicit unavailable metadata,
not message-string parsing or guessed keys. Localize new report text with config.*
keys in both bundles. Serialize map/list/scalar report data through OutputStrategy
to avoid new Jackson reflection registrations. Give config explain READ_ONLY impact.

Plain and JSON explain output expose the same data with deterministic order. Data
goes to stdout; diagnostics and warnings go to stderr. Warnings remain represented
in the report as data as well. Missing keys are successful; invalid selectors are not.

### Hermetic committed-profile gate

Register configCheck as a JavaExec task for one committed profile, depending on
JavaExec tasks for the remaining profiles, all on the main runtime classpath.
This keeps the requested task type while checking each profile without nativeCompile.
Enumerate profile fixture filenames deterministically, supply explicit paths and
profiles, and set workingDir to config/profiles. Remove inherited LARET_* entries
case-insensitively on Windows. Explicit fixture paths avoid home-file interference.
Each task captures the report and fails unless it names the fixture path and
profile as valid, so the gate cannot pass on a file it did not mean to check.
Connect configCheck to check and checkAll; a failed check must fail the build.

### Unresolved selectors under check

The resolver keeps falling back for ordinary commands, as the configuration spec
requires. For the diagnostic invocation only, the resolver also reports whether
the requested explicit path or profile is what selected the file. An unresolved
selector is a check error with exit 1; with both given, the explicit path decides.
Without this, a typo in `--config` or `--profile` validated defaults and passed.
No base configuration is added at repository root.

### Research adaptation

Tang et al., [Holistic Configuration Management at Facebook, SOSP 2015,
section 3.1](https://sigops.org/s/conferences/sosp/2015/current/2015-Monterey/008-tang-online.pdf)
describes versioned configuration and automatic invariant validation with code review.
Laret adapts these practices to committed profiles and a local build gate. Value-source
explanations are a CLI-scale traceability adaptation, not a claim to reproduce Facebook's
distribution or rollout infrastructure. Provenance identifies a source, not a human author;
human attribution remains in version-control history. The implementation ADR will record
these boundaries and cite the paper.

## Risks / Trade-offs

- Resolver correction changes previously inconsistent selection -> regression tests and
  release documentation identify this intentional compatibility correction.
- Explanations may reveal secrets -> explicit user-requested inspection only, no automatic
  logging of reports; document that users must protect captured output.
- Inspecting losing layers can expose a parse error hidden by short-circuit reads ->
  report that failure explicitly; never silently claim complete provenance.
- Diagnostic mode could accidentally execute hooks with invalid settings -> lifecycle
  tests verify no dependent initialization before a failure report and no bypass elsewhere.
- Native serialization can trigger Kotlin reflection -> reuse safe output values and
  existing native tests; change reflection metadata only if demonstrably required.
- Global environment and filesystem state can make tests flaky -> injected boundaries,
  isolated directories, fixed profiles, and no timing assertions.

## Migration Plan

Stage 1 adds shared resolution and public provenance with regression and external-consumer
API tests. Stage 2 adds commands and localized diagnostics with scoped initialization
handling. Stage 3 adds fixtures, build gates, README guidance, and the short ADR.
No user migration or config rewrite is required. Stages remain separately reviewable;
rollback removes the dependent later stages first. No commits are created automatically.
