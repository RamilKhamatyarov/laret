# Lean Core and Native Image Size

## Status

Proposed

## Context

Two rows of the concurrency benchmark cannot be won by tuning the code paths
they exercise, because what they measure is the native image itself.

**Start-up-bound wall clock.** At fan-out N=1,000, Cobra and clap finish in
about 2.5 ms on the nightly runner, and Laret native in 5.9-9.8 ms. Locally,
measured in-container (not through the Windows bind mount, which adds about
1 ms per `stat`), Laret reaches `main` 1.6 ms after spawn, spends 0.4 ms
building the app and 0.35 ms dispatching the command, and takes 1.1-1.3 ms
from `run` returning to the process being gone, of which `exit_group`
teardown is about 0.85 ms. The `native-startup-floor` ADR measured a bare
SubstrateVM hello-world at 5.1 ms and 8.7 MiB, so Laret native will not
overtake Cobra or clap on these rows; the question is how close it can get.

**Peak RSS.** On the runner Laret native peaks at 33-43 MiB against Cobra's
5.7-7.6 and clap's 3.5-3.7. Of the 16.6 MiB resident locally at signal time,
1.0 MiB is anonymous memory and 15.6 MiB is file-backed: the pages of the 42
MB binary that start-up and the command touched. Transparent huge pages add at
most 2.6 MiB (see the `signal-latency-rss-and-dispatch-floor` ADR). The runner's
higher figure is in that file-backed part, so the size and layout of the image
is the lever.

**Why the image is 42 MB.** The build report of the bench binary: 19.6 MB of
code, 9.2 MB of it from the Laret jar and its dependencies, 7.8 MB from
`java.base`; 22.2 MB of image heap, led by code metadata (5.7 MB) and strings
(5.9 MB). The bench app uses parsing, the middleware chain, cancellation,
`ParallelDispatcher`, `CommandPipeline` and the live-watch debouncer. It gets
far more, because `CliApp` holds or calls `ConfigLoader` and
`ConfigValidator` (and with them Jackson's YAML, TOML and JSON mappers),
`PluginManager`, `PluginInstaller`, `PluginCatalog` and `PluginExecutor`,
`CompletionEngine` and `OldBinaryCleaner`, and option resolution always
builds a `FileLayer` over `ConfigLoader`. Native-image keeps every method
reachable from `main` through those references, whether or not a run uses it.
The earlier ADR also found `kotlin-reflect` always reachable, through a static
initialiser in `kotlin.jvm.internal.Reflection`, for as long as it is on the
classpath.

### Options considered

| Option | Acts on | Expected | Cost or risk |
|---|---|---|---|
| Make heavy features reachable only when an app uses them | code area, image heap, file-backed RSS, start-up page faults, exit teardown | largest; most of the 9.2 MB of Laret-side code is features the bench app never calls | API work; see the decision |
| Trim the image heap: locales, charsets, resources, metadata | image heap, RSS | small to medium | low |
| Static linking with musl (`--static --libc=musl`) | dynamic loader and relocation at start-up, shared-library pages | about 0.5-1 ms and a few MiB, unmeasured | Linux only; a musl toolchain in CI |
| Opt-in fast exit: halt after `run` | the user-space part of exit, about 0.3 ms | small | skips JVM shutdown hooks |
| Remove `kotlin-reflect` | code area, image heap | small to medium | eight reflective call sites and Jackson's Kotlin module |
| Oracle GraalVM with profile-guided optimisation | start-up, by laying code and heap out in first-use order | medium | licence |
| Build-time initialisation of the app model | start-up | potentially large | blocked, see Rejected |
| Epsilon GC or a smaller young generation | fan-out bursts | mixed | raises RSS |
| Separate Gradle modules per feature | same as the first option | same, plus smaller JVM jars | users change dependencies; multi-module build |
| `ServiceLoader` discovery | nothing | none | native-image includes every registered provider |

## Decision

### 1. `cliCore { }`: a lean entry point, one artifact

`cliCore { }` builds a `CliApp` with the core only: argument and option
parsing, help, validation and constraints, the middleware chain, the
cancellation scope, `fastSignals()`, and typo suggestions. Its messages are
English. Its option values resolve from the command line, `LARET_*`
environment variables and declared defaults; there is no config-file layer.

Everything else is an explicit call in the builder:

| Call | Adds |
|---|---|
| `config(yaml(), toml(), json())` | the config-file layer, one format per argument, with only those formats' mappers reachable |
| `plugins()` | the plugin runtime, sidecar catalog and installer |
| `completion()` | the hidden `__complete` protocol and the completion engine |
| `middlewareInspection()` | the auto-registered `middleware` group |
| `locales("es")` | localized framework messages beyond English |
| `selfUpdate = true` | unchanged: the old-binary cleanup |

`cli { }` keeps today's behaviour exactly: it is `cliCore { }` with every call
above applied, so there is one code path and existing apps change nothing.
New and size-sensitive apps choose `cliCore { }`; the README says when to.
The same feature calls and behavior are available on JVM and GraalVM; the
reachability benefit is a native-image build property, not a runtime-specific
API.

Feature dependencies are declared by the feature installer. Enabling a feature
computes and installs its required feature set, so callers do not need to know
internal prerequisites. Dependency resolution is static and deterministic;
runtime discovery is not used.

**The point is reachability, not configuration.** A feature counts as opt-in
only when nothing reachable from a `cliCore { }` app references its classes,
including in branches that never run. `CliApp` therefore stops holding the
feature objects. Each opt-in call installs a small feature object through an
internal interface: dispatch interception (completion, sidecar plugins),
extra config layers (file formats), extra groups (middleware inspection) and
shutdown (plugins). `CliApp` only knows the interface. Native-image's
points-to analysis then sees a feature's classes only in an app whose builder
called its installer. CI verifies that a `cliCore { }` native image report
excludes optional feature packages that were not selected. This is a
deterministic reachability check; image size and runtime measurements remain
informational and do not gate pull requests.

The bench app moves to `cliCore { }`. The demo app stays on `cli { }`.

### 2. Heap trimming

Native builds include only the locales an app ships (`en` for core, plus what
`locales(...)` names), no charsets beyond the defaults, and only the resources
they reference: the `.tpl` templates of completion and scaffolding leave the core image. The
resource configuration that currently lists every bundle and template becomes
per-feature, so the image of a `cliCore { }` app does not carry them.

### 3. Remove `kotlin-reflect`

Migrate the eight reflective call sites and the Jackson Kotlin module so that
`kotlin-reflect` leaves the classpath. Where Jackson needs Kotlin data-class
support, the config feature supplies it without `kotlin-reflect`, or the
affected models gain explicit constructors.

### 4. musl static linking, bench first

Build the bench binary with `--static --libc=musl` and measure it against the
dynamically linked one. Linux release binaries switch only if start-up and
peak RSS both improve and the release job can install a musl toolchain;
macOS and Windows are unaffected.

### 5. Opt-in fast exit

`cli { fastExit() }`, like `fastSignals()`, halts with the run's exit code
when `run` returns, after flushing `System.out` and `System.err`, instead of
going through `System.exit` and its shutdown sequence. It skips JVM shutdown
hooks registered outside Laret, which is why it is opt-in. The bench app opts
in; the demo app does not.

### Order and measurement

The levers land in the order above, each measured on its own. Every lever
records the bench image's code area, image heap and total size from the
native-image build report, so a change in size is attributed rather than
guessed. Run-time figures are interleaved A/B runs of the bench binary from
the container's own filesystem, never through a bind mount, and the nightly
on the runner is the reference. A lever stays only if it reduces size or
start-up without raising peak RSS.

### Targets

Measured on the nightly, Laret native bench app on `cliCore { }`:

- Peak RSS within 5x of Cobra on all four scenarios, every night.
- Fan-out N=1,000 within 2.5x of Cobra.
- File-backed resident memory at signal time halved, against the first
  nightly that records it.

These are not targets to overtake Cobra or clap. The SubstrateVM floor rules
that out on start-up-bound rows and on peak RSS, and the ADR says so rather
than promising it.

## Rejected

- **Separate Gradle modules per feature.** The strongest isolation, but every
  user changes dependencies, and the opt-in builder achieves the same
  reachability in the native image with one artifact.
- **`ServiceLoader` discovery.** Native-image includes every provider
  registered on the classpath, so it trims nothing.
- **A lean `cli { }` by default.** Smaller images for everyone, but every
  existing app would have to add calls to keep working; `cliCore { }` gives
  new apps the lean path without breaking old ones.
- **Oracle GraalVM and profile-guided optimisation.** Profile-guided layout
  would reduce touched pages, but it would put the release binaries under the
  GFTC licence. The project stays on GraalVM Community.
- **Build-time initialisation of the app model.** Blocked, as the
  `native-startup-floor` ADR found: Laret's objects hold Kotlin collections, and
  native-image refuses them in the image heap unless the Kotlin standard
  library is initialised at build time too.
- **Epsilon GC, or a young generation below 8 MiB.** Faster bursts in some
  cases, but peak RSS grows with allocation, against this ADR's purpose.
- **Compressing the binary (UPX and similar).** Smaller on disk, but the image
  is unpacked into anonymous memory at start-up: slower, and resident memory
  no longer shares the page cache.

## Consequences

Apps that choose `cliCore { }` get a smaller native image, faster start-up and
exit, and lower peak RSS, and say exactly which features they carry. `cli { }`
users see no change. `CliApp` becomes a core that knows its features only
through one internal interface, which is the larger part of the work and
touches dispatch, option resolution, help (for the `middleware` group) and
shutdown.

The costs: two entry points to document and test, a per-feature resource
configuration for native builds, a build test that inspects image reports,
and, if musl pays, a musl toolchain in the release job. Removing
`kotlin-reflect` may require explicit constructors on config models. Core
messages in `cliCore { }` are English until an app calls `locales(...)`.

The benchmark compares the bench app, which moves to `cliCore { }`, so its
numbers will no longer describe what a `cli { }` app costs. The results
section of this ADR records both, so the difference is visible.

## Implementation results

Not implemented yet.
