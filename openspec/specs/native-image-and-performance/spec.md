# native-image-and-performance Specification

## Purpose

Defines the native-image build profile, the native and JVM artifacts the build produces, and the performance targets, benchmark scenarios, and measurement policy the project holds itself to.

## Requirements

### Requirement: Native image build profile

Every native image MUST be built with `--no-fallback`, `-Ob`, `--enable-native-access=ALL-UNNAMED`, `--install-exit-handlers`, and `-H:+ReportExceptionStackTraces`, and MUST select the serial garbage collector with an 8 MiB maximum young generation (`--gc=serial -R:MaxNewSize=8m`). See [laret-native-performance-targets](../../adr/laret-native-performance-targets.md).

#### Scenario: Shared native build arguments

- **WHEN** any native binary is compiled
- **THEN** the build applies the shared argument set above, produces an executable image rather than a fallback image, and the build succeeds

### Requirement: Reflection and resource configuration

Native builds MUST provide the checked-in reflection and resource configuration under the native-image metadata directory, MUST include template resources matching `templates/.*\.tpl$`, and MUST NOT require runtime reflection discovery beyond that configuration. Image-size changes MUST be attributed from the build report's code area, image heap, and total size; runtime size figures MUST NOT gate pull requests. See [lean-core-and-native-image-size](../../adr/lean-core-and-native-image-size.md).

#### Scenario: Native metadata is embedded

- **WHEN** a native image is built
- **THEN** the reflection and resource configuration files and matching templates are embedded, and the image report records code area, image heap, and total size

### Requirement: Native binary targets

The build MUST define `main`, `windows`, and `linux` native binaries with image name `laret`, and a `bench` native binary with image name `laret-bench` whose classpath is the benchmark source set. The `main`, `windows`, and `linux` binaries MUST run the demo application; the `bench` binary MUST run the benchmark application.

#### Scenario: Main native binary runs the demo

- **WHEN** the main native binary is built
- **THEN** `build/native/nativeCompile/laret` exists and running it with `--help` exits 0

#### Scenario: Benchmark native binary is separate

- **WHEN** the benchmark native binary is built
- **THEN** `build/native/nativeBenchCompile/laret-bench` exists as a distinct image from `laret`

### Requirement: Benchmark source isolation

All benchmark payloads and the benchmark entry point MUST live outside the main source set and MUST NOT ship in the published library or demo artifacts. Unit tests MUST see the benchmark source set on their classpath.

#### Scenario: Benchmark commands are absent from the demo

- **WHEN** the demo fat jar is built and `--help` is printed
- **THEN** no benchmark group or benchmark command appears in the listing and the command exits 0

### Requirement: JVM benchmark artifacts

The build MUST produce `build/libs/laret.jar`, `build/libs/laret-fat.jar`, and `build/libs/laret-bench-fat.jar`. The two fat jars MUST name the demo and benchmark entry points respectively, and the benchmark fat jar MUST be the JVM target of the benchmark suite.

#### Scenario: Fat jars are produced

- **WHEN** the shadow jar and benchmark shadow jar tasks complete
- **THEN** both `laret-fat.jar` and `laret-bench-fat.jar` exist under `build/libs` and each starts its own application from its manifest entry point

### Requirement: Start-up floor

Start-up MUST be measured as `laret-bench --version` and `laret-bench bench runtime` against a hello-world native image built with the same flags, and the bare SubstrateVM result (5.1 ms and 8.7 MiB measured) MUST be treated as the lower bound no image can fall below. Any change that raises measured start-up time or peak RSS without an offsetting measured gain MUST be reverted. See [native-startup-floor](../../adr/native-startup-floor.md).

#### Scenario: A start-up lever must justify itself

- **WHEN** a start-up change is evaluated
- **THEN** it is measured by interleaved A/B runs against the previous binary and is kept only if it reduces start-up time or peak RSS without raising the other

### Requirement: Performance targets

On nightly full-scale runs, never on quick runs, Laret native MUST be evaluated against three targets: ahead of picocli everywhere; wall clock within 2x of Cobra and clap on fan-out, pipeline, cancellation latency, and event-storm residual; and peak RSS within 5x of Cobra and clap on every scenario. Targets MUST be reported, never enforced. See [laret-native-performance-targets](../../adr/laret-native-performance-targets.md).

#### Scenario: A missed target is reported, not fatal

- **WHEN** the nightly report renders a target as missed at fan-out N=1,000, fan-out N=10,000, or pipeline 100,000 lines
- **THEN** the miss is displayed beside the measurement and the benchmark job still exits 0

### Requirement: Benchmark scenarios

The suite MUST implement four scenarios: parallel fan-out and fan-in, an event storm coalesced into exactly one execution, a three-stage streaming pipeline, and cancellation under load with SIGTERM primary and SIGINT secondary. Every target MUST implement the same commands, flags, and stdout contract. Correctness assertions MUST gate; no timing assertion may fail a build. See [concurrency-benchmark-suite](../../adr/concurrency-benchmark-suite.md).

#### Scenario: Only correctness gates a pull request

- **WHEN** the pull-request job runs reduced-scale fan-out, event-storm, pipeline, and cancellation scenarios
- **THEN** it asserts exactly one coalesced execution, correct fan-out and pipeline counts, exit code 143 on SIGTERM and 130 on SIGINT, and reverse-order cleanup hooks, and it fails only on those correctness checks

### Requirement: Measurement policy

Every reported performance result MUST have at least five timed samples per measured case after a discarded warm-up, preserve the individual samples, and report the median and observed range. Provenance MUST record revision, timestamp, OS and kernel, CPU and affinity, memory settings, runtime and compiler versions, build flags, workload parameters, and sample counts. See [performance-benchmark-measurement-policy](../../adr/performance-benchmark-measurement-policy.md).

#### Scenario: Quick runs are smoke checks only

- **WHEN** a suite produces a single-sample or reduced-scale quick run
- **THEN** the result is treated as a correctness smoke check and MUST NOT be used to update performance claims or evaluate targets

### Requirement: Harness measurement environment

The harness MUST run on Linux only, MUST pin each target to the same cores, and MUST obtain wall clock and peak RSS from the kernel for the measured process itself. It MUST perform a discarded warm-up, run each scenario in isolation, rotate the target order across repeats, and report the median of the configured repeats. See [performance-benchmark-measurement-policy](../../adr/performance-benchmark-measurement-policy.md).

#### Scenario: Non-Linux hosts are refused

- **WHEN** the harness is started on a non-Linux operating system
- **THEN** it refuses with a clear message and exits non-zero

### Requirement: Benchmark reproducibility

Every published benchmark number MUST be reproducible from the documented commands: `benchmarks/harness/build.sh`, `python3 benchmarks/harness/harness.py --out results.json`, and `python3 benchmarks/harness/render.py results.json > docs/benchmarks.md`. The rendered benchmark document MUST carry its provenance and the reproducing commands.

#### Scenario: Documented commands regenerate the report

- **WHEN** the documented commands are followed on Linux
- **THEN** `results.json` and a freshly rendered benchmark document contain the same suite, scenario, target, scale, and provenance fields

### Requirement: Signal and memory diagnostics

On Linux, benchmark provenance MUST record the selected transparent-huge-page mode. The cancellation scenario MUST report the target's anonymous, file-backed, and huge-page memory captured immediately before signalling. These diagnostics MUST NOT alter timing or peak-RSS measurements. See [signal latency, runner RSS and dispatch floor](../../adr/signal-latency-rss-and-dispatch-floor.md).

#### Scenario: Cancellation report includes memory context

- **WHEN** the Linux cancellation scenario is measured and its target prints its process ID
- **THEN** the report includes THP mode and the target's anonymous, file-backed, and huge-page memory captured before the signal

### Requirement: Build verification

`checkAll` MUST run formatting, linting, static analysis, and tests, and MUST fail when any stage fails. It MUST cover `spotlessApply`, `ktlintCheck`, `spotlessCheck`, `pmdMain`, `pmdTest`, and `test` in that order.

#### Scenario: A failed check fails the build

- **WHEN** `checkAll` is run and one of its checks fails
- **THEN** the build exits non-zero
