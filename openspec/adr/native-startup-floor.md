# Native Start-up Floor

- Status: Accepted
- Date: 2026-10-06

## Context

The laret-native-performance-targets ADR closed with one gap none of its
levers addressed. A bare SubstrateVM hello-world, built with Laret's flags,
starts in 5.1 ms with 8.7 MiB peak RSS. The minimal Laret bench app answering
`--version` takes 8.6 to 12.5 ms and 25.3 MiB. That 3.5 to 7 ms and roughly
17 MiB is Laret's own start-up, and it sits inside every benchmark row: it is
most of fan-out at N=1,000, part of cancellation latency, and a floor under
peak RSS on every scenario.

Reading the path from `main` to a command's action shows where it can come
from:

- **Three Jackson mappers per app, built eagerly.** Every `CliApp` constructs a
  `ConfigLoader`, which builds a JSON mapper with the Kotlin module, a YAML
  mapper and a TOML mapper in its field initialisers, whether or not any
  configuration file is ever read. Each command's config registry can build a
  second `ConfigLoader` when a file layer is consulted.
- **`kotlin-reflect` in the image.** `jackson-module-kotlin` depends on it, and
  eight framework files use the Kotlin-module mapper: config loading, output
  formats, stats, diff, MCP, doc prose and the release fetcher. The reflection
  metadata it carries is image heap that is paged in, which is RSS.
- **Config files probed on every command.** `FileLayer` resolves its file in
  its initialiser, checking up to eight candidate paths across the working and
  home directories, even when no option ever reads a config value.
- **All framework class initialisation happens at run time.** Native Image can
  run side-effect-free static initialisers at build time and store the result in
  the image, which nothing in the build asks it to do today.

## Decision

This round attacks the start-up floor only. Cancellation, the pipeline and
fan-out keep their current implementations; a lower floor improves all three
regardless.

### 1. Make configuration lazy

`ConfigLoader` builds each mapper on first use, so a run that reads no config
file builds none, and a run that reads YAML builds only the YAML mapper.
`FileLayer` resolves its file on the first lookup rather than at construction,
so a command that never reads a config-backed value never touches the
filesystem for it. Neither change alters which file is chosen or what is read
from it, only when.

### 2. Measure kotlin-reflect, then decide

Build the bench native image with and without `jackson-module-kotlin` and
compare start-up time and peak RSS. If removing it is a measurable win, migrate
the eight call sites to plain Jackson, which can construct the models through
the no-argument constructors Kotlin generates for classes whose parameters all
have defaults. Every migrated model gets a round-trip test proving it
serialises and deserialises exactly as before. If it is not a win, the Kotlin
module stays and the ADR records the measurement.

### 3. Build-time initialisation, audited

Initialise at build time only those Laret packages whose static initialisers
have no side effects: no environment, system property, clock, filesystem or
network reads, and no threads. A test scans the classes registered for
build-time initialisation and fails if one gains such a read later, because a
value captured on the build machine would otherwise ship silently inside every
binary. The Kotlin standard library and kotlinx.coroutines stay at run time:
coroutine dispatchers size themselves from the CPU count, and a build machine's
CPU count must never become every user's.

### 4. Proof

Each lever is measured on its own against the binary before it, interleaved in
the same run so that drift on the measuring machine affects both sides
equally, as in the previous round. A lever that does not move start-up time or
peak RSS beyond run-to-run variation is reverted. `docs/benchmarks.md` is
refreshed at the end, and the Linux nightly is the reference.

The measured quantity is the floor itself: `laret-bench --version` and
`laret-bench bench runtime`, against a native hello-world built with the same
flags, plus the full suite to show the floor's effect on every scenario.

## Results

Measured in a Linux container on the development machine, interleaved, as
medians of 21 runs for start-up and 5 runs for the full suite.

### Lever 1, lazy configuration: applied

| | Before | After | Native hello-world |
|---|---|---|---|
| `--version` | 9.6 ms, 25.0 MiB | 5.5 ms, 16.7 MiB | 3.6 ms, 8.8 MiB |
| `bench runtime` | 11.8 ms, 25.3 MiB | 4.8 ms, 17.2 MiB | 3.4 ms |
| fan-out N=1,000 | 13.3 ms, 26.1 MiB | 6.5 ms, 18.6 MiB | |

Laret's overhead above the SubstrateVM floor fell from about 6 ms and 16 MiB to
about 1.5 to 2 ms and 8 MiB. Almost all of it had been Jackson mappers that no
run used. Tests pin the laziness: a new loader builds no mapper, reading YAML
builds only the YAML mapper, a file layer probes nothing until asked, and a
command that reads no configuration leaves every mapper unbuilt. They fail
against the eager loader.

### Lever 2, dropping the Kotlin module: measured, not applied

An image without the Kotlin module on any reachable path was 65 KB smaller out
of 42 MB, started within noise of the lazy build, and used 0.9 MiB more peak
RSS. It does not pay, so the module stays. The measurement also showed why:
`kotlin-reflect` remains reachable whenever it is on the classpath at all,
because `kotlin.jvm.internal.Reflection` loads its implementation by name in a
static initialiser, which native-image follows. Removing it would mean taking
`kotlin-reflect` off the classpath entirely, not migrating eight call sites.

Getting a valid measurement needed one correction worth recording: the fat jar
embeds Laret's own `META-INF/native-image/reflect-config.json`, which registers
`KotlinModule` for reflection. Native-image reads embedded metadata
automatically, so overriding the file on the command line does not remove it.

### Lever 3, build-time initialisation: blocked

Initialising Laret's classes at build time fails immediately: their static
fields hold Kotlin collections such as `EmptyList`, and native-image refuses to
put an object in the image heap unless its class is also initialised at build
time. That would put the Kotlin standard library at build time, which this ADR
rules out. Nearly every Laret `object` holds a Kotlin collection, so no useful
audited subset exists.

### Effect on the suite

| | Before | After | Target vs Cobra |
|---|---|---|---|
| fan-out N=1,000 | 13.0 ms | 10.5 ms | 4.2x to 2.5x, still missed |
| pipeline | 89 ms | 75 ms | 3.3x to 2.8x, still missed |
| peak RSS, storm | 29.6 MiB | 24.5 MiB | 5.4x to 4.4x, now met |
| peak RSS, cancellation | 27.2 MiB | 19.5 MiB | 4.1x to 2.9x |

Peak RSS is now within 5x of Cobra on all four scenarios, up from three.

## Consequences

Laret apps that never read configuration stop paying for it. If the Kotlin
module goes, Laret's dependency surface shrinks by a reflection library, at the
cost of eight call sites that now depend on constructor conventions rather
than on Kotlin metadata, which the round-trip tests are there to hold in place.
Build-time initialisation makes the build responsible for proving a class is
safe to snapshot, and the audit test makes that proof mechanical rather than a
matter of review.

The floor cannot fall below SubstrateVM's own 5.1 ms and 8.7 MiB, so the
targets against clap on start-up-bound runs stay out of reach whatever this
round achieves.

## Checked on CI

The nightly benchmark ran 21 times from 2026-10-04 to 2026-10-24: three
nights before this change (`c7696526`) and seven with it (`74ecae8d`), on the
same runner image but five different CPU models. Because the hardware changes
from night to night, each figure below is Laret native divided by Cobra on the
same night, as the median over the nights; RSS is in MiB.

| Claim above | Before | After | Verdict |
|---|---|---|---|
| fan-out N=1,000, 4.2x to 2.5x of Cobra | 5.98x | 3.04x | Holds |
| pipeline, 3.3x to 2.8x of Cobra | 2.82x | 2.98x | Does not hold: no gain on CI |
| storm RSS 29.6 to 24.5 MiB | 46.7 | 43.3 | Holds in direction, 3.4 MiB rather than 5.1 |
| cancellation RSS 27.2 to 19.5 MiB | 43.6 | 37.6 | Holds, 6.0 MiB rather than 7.7 |
| peak RSS within 5x of Cobra on all four scenarios | | fan-out 3.2-4.0x, pipeline 3.8-4.7x, cancellation 4.1-4.3x, storm 4.8-6.3x | Does not hold: the storm misses 5x on 5 of the seven nights |

The margin is thin because the runner's absolute RSS is high: Laret peaks at
19-25 MiB in the local container and at 37-43 MiB on the runner with the same
binary. Most of
that resident set is the binary's file-backed pages; see the
`signal-latency-rss-and-dispatch-floor` ADR.
