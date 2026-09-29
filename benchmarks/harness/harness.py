#!/usr/bin/env python3
"""Concurrency and parallel execution benchmark harness.

Runs the four scenarios of the concurrency contract against every configured
target and writes a schema-versioned results.json. See
.github/adr/concurrency-benchmark-suite.md.

Linux only: pinning uses sched_setaffinity and timing and peak RSS come from
wait4. Numbers are not comparable across operating systems, so the harness
refuses to pretend.

Every target is started by rusage_exec, a small C helper compiled on first
use, which forks the target, reaps it with wait4 and reports its exit code,
ru_maxrss and a monotonic elapsed time. That gives exact timings rather than
ones quantised to a polling interval, and the true peak RSS of the target.

Forking from the helper rather than from Python matters: the kernel carries a
process's pre-exec memory into ru_maxrss, so a child forked from the
interpreter can never report less than the interpreter's own footprint. An
earlier version polled every 10 ms and read /proc/<pid>/status, which put a
10.5 ms floor under every wall clock and reported taskset's memory for any
target that finished before the first sample.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
from dataclasses import dataclass, field, asdict
from pathlib import Path

# 2: timing and RSS from wait4, settle_ms in Scenario B, target_runtime in
#    provenance. Version 1 results were quantised and are not comparable.
SCHEMA_VERSION = 2
HERE = Path(__file__).resolve().parent
BENCHMARKS = HERE.parent
REPO = BENCHMARKS.parent

# Scenario D injects its signal this long after the process reports readiness.
SIGNAL_DELAY_SECONDS = 0.2
# A target gets this long to finish shutting down before the harness gives up.
SHUTDOWN_GRACE_SECONDS = 15.0
READY_TIMEOUT_SECONDS = 60.0


@dataclass
class Target:
    """One benchmark subject: a name and the argv prefix that invokes it."""

    name: str
    argv: list[str]

    def available(self) -> bool:
        first = self.argv[0]
        if first in ("java",):
            return shutil.which(first) is not None and all(
                Path(a).exists() for a in self.argv if a.endswith(".jar")
            )
        return Path(first).exists() or shutil.which(first) is not None


@dataclass
class Measurement:
    target: str
    scenario: str
    parameters: dict
    wall_clock_ms: float | None = None
    overhead_us_per_unit: float | None = None
    peak_rss_kb: int | None = None
    cancellation_latency_ms: float | None = None
    settle_ms: float | None = None
    exit_code: int | None = None
    checks: dict = field(default_factory=dict)
    passed: bool = True
    error: str | None = None


def require_linux() -> None:
    if platform.system() != "Linux":
        sys.exit(
            "This harness runs on Linux only: pinning uses sched_setaffinity and peak "
            "RSS comes from wait4's rusage. Results from different operating systems "
            "are not comparable, so there is no degraded mode.\n"
            f"Detected: {platform.system()}"
        )


def parse_cpus(spec: str) -> set[int]:
    """Parse a taskset-style list such as "0-3" or "0,2,4-5"."""
    cpus: set[int] = set()
    for part in spec.split(","):
        part = part.strip()
        if "-" in part:
            low, high = part.split("-", 1)
            cpus.update(range(int(low), int(high) + 1))
        elif part:
            cpus.add(int(part))
    if not cpus:
        raise ValueError(f"empty CPU list: {spec!r}")
    return cpus


def pin_to(cpus: set[int]):
    """A preexec_fn that pins the child before exec, so the measured process
    is the target from its first instruction rather than a taskset wrapper."""
    def pin() -> None:
        os.sched_setaffinity(0, cpus)
    return pin


HELPER_SOURCE = HERE / "rusage_exec.c"
HELPER_BINARY = HERE / ".bin" / "rusage_exec"


def helper() -> Path:
    """Compile rusage_exec on first use, or again when its source changes."""
    if HELPER_BINARY.exists() and HELPER_BINARY.stat().st_mtime >= HELPER_SOURCE.stat().st_mtime:
        return HELPER_BINARY
    compiler = shutil.which("cc") or shutil.which("gcc")
    if compiler is None:
        sys.exit("A C compiler (cc or gcc) is needed to build the rusage_exec helper.")
    HELPER_BINARY.parent.mkdir(exist_ok=True)
    subprocess.run([compiler, "-O2", "-o", str(HELPER_BINARY), str(HELPER_SOURCE)], check=True)
    return HELPER_BINARY


def read_result(path: Path) -> dict:
    """Parse the exit=..., maxrss_kb=..., elapsed_ns=... line rusage_exec writes."""
    fields = dict(pair.split("=", 1) for pair in path.read_text().split() if "=" in pair)
    return {
        "code": int(fields["exit"]),
        "rss": int(fields["maxrss_kb"]),
        "elapsed_ms": int(fields["elapsed_ns"]) / 1_000_000.0,
    }


def run_once(argv: list[str], cpus: set[int], timeout: float = 300.0) -> tuple[int, str, str, float, int]:
    """Run to completion and return exit code, stdout, stderr, elapsed ms and peak RSS KiB.

    Output goes to temporary files rather than pipes, so a chatty target can
    never block on a pipe nobody is reading.
    """
    with tempfile.TemporaryDirectory() as scratch, \
            tempfile.TemporaryFile("w+") as out, tempfile.TemporaryFile("w+") as err:
        result_file = Path(scratch) / "result"
        process = subprocess.Popen(
            [str(helper()), str(result_file), *argv],
            stdout=out, stderr=err, text=True, preexec_fn=pin_to(cpus),
        )
        try:
            process.wait(timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        out.seek(0)
        err.seek(0)
        if not result_file.exists():
            return process.returncode, out.read(), err.read(), float("nan"), 0
        result = read_result(result_file)
        return result["code"], out.read(), err.read(), result["elapsed_ms"], result["rss"]


def summary_fields(stdout: str, prefix: str) -> dict:
    """Parse the one `prefix key=value ...` line every target prints."""
    for line in stdout.splitlines():
        if line.startswith(prefix + " "):
            return dict(
                pair.split("=", 1) for pair in line.strip().split(" ")[1:] if "=" in pair
            )
    return {}


def scenario_fanout(target: Target, cpus: set[int], tasks: int) -> Measurement:
    params = {"tasks": tasks, "jobs": 0}
    argv = [*target.argv, "bench", "fanout", "--tasks", str(tasks), "--jobs", "0"]
    code, stdout, stderr, elapsed, rss = run_once(argv, cpus)
    fields = summary_fields(stdout, "fanout")

    expected_sum = tasks * (tasks + 1) // 2
    checks = {
        "completed": fields.get("completed") == str(tasks),
        "sum": fields.get("sum") == str(expected_sum),
    }
    return Measurement(
        target=target.name,
        scenario="A-fanout",
        parameters=params,
        wall_clock_ms=elapsed,
        overhead_us_per_unit=(elapsed * 1000.0 / tasks) if tasks else None,
        peak_rss_kb=rss,
        exit_code=code,
        checks=checks,
        passed=code == 0 and all(checks.values()),
        error=stderr.strip()[-400:] or None if code != 0 else None,
    )


def scenario_storm(target: Target, cpus: set[int], events: int, window: int, debounce: int) -> Measurement:
    params = {"events": events, "window_ms": window, "debounce_ms": debounce}
    argv = [
        *target.argv, "bench", "storm",
        "--events", str(events), "--window", str(window), "--debounce", str(debounce),
    ]
    code, stdout, stderr, elapsed, rss = run_once(argv, cpus)
    fields = summary_fields(stdout, "storm")

    # The wall clock is mostly the storm's own designed quiet period. settle_ms,
    # from the last event emitted to the coalesced run, is the part the
    # framework controls: at least the debounce window, plus its own overhead.
    try:
        settle = float(fields["settle_ms"])
    except (KeyError, ValueError):
        settle = None
    checks = {
        "events_delivered": fields.get("events") == str(events),
        "coalesced_to_one_run": fields.get("runs") == "1",
        "settle_reported": settle is not None,
        "settled_after_debounce": settle is not None and settle >= debounce,
    }
    return Measurement(
        target=target.name,
        scenario="B-storm",
        parameters=params,
        wall_clock_ms=elapsed,
        settle_ms=settle,
        peak_rss_kb=rss,
        exit_code=code,
        checks=checks,
        passed=code == 0 and all(checks.values()),
        error=stderr.strip()[-400:] or None if code != 0 else None,
    )


def scenario_pipeline(target: Target, cpus: set[int], lines: int, pattern: str) -> Measurement:
    params = {"lines": lines, "pattern": pattern}
    argv = [*target.argv, "bench", "pipeline", "--lines", str(lines), "--pattern", pattern]
    code, stdout, stderr, elapsed, rss = run_once(argv, cpus)
    fields = summary_fields(stdout, "pipeline")

    expected_kept = sum(1 for index in range(lines) if pattern in f"line-{index}")
    checks = {
        "all_stages_completed": fields.get("stages") == "3",
        "lines_correct": fields.get("kept") == str(expected_kept),
        "streaming": fields.get("mode") == "streaming",
    }
    return Measurement(
        target=target.name,
        scenario="C-pipeline",
        parameters=params,
        wall_clock_ms=elapsed,
        overhead_us_per_unit=(elapsed * 1000.0 / lines) if lines else None,
        peak_rss_kb=rss,
        exit_code=code,
        checks=checks,
        passed=code == 0 and all(checks.values()),
        error=stderr.strip()[-400:] or None if code != 0 else None,
    )


def scenario_cancel(target: Target, cpus: set[int], workers: int, sig: signal.Signals, marker: Path) -> Measurement:
    """Start, wait for readiness, inject a signal, measure how long exiting takes.

    A thread blocks in wait4 from the moment the signal is sent and stamps the
    instant the kernel reports the exit, so latency is exact instead of falling
    into Popen.wait's exponential backoff buckets.
    """
    params = {"workers": workers, "signal": sig.name}
    marker.unlink(missing_ok=True)
    argv = [
        *target.argv, "bench", "cancel",
        "--workers", str(workers), "--marker", str(marker), "--seconds", "30",
    ]

    with tempfile.TemporaryDirectory() as scratch, tempfile.TemporaryFile("w+") as err:
        result_file = Path(scratch) / "result"
        process = subprocess.Popen(
            [str(helper()), str(result_file), *argv],
            stdout=subprocess.PIPE,
            stderr=err,
            text=True,
            preexec_fn=pin_to(cpus),
            # SIGINT is ignored by a backgrounded child of a non-interactive
            # shell, so the target gets its own process group and the signal
            # goes there. rusage_exec ignores it; the target does not.
            start_new_session=True,
        )

        ready_line = ""
        ready_deadline = time.perf_counter() + READY_TIMEOUT_SECONDS
        while time.perf_counter() < ready_deadline:
            line = process.stdout.readline()
            if not line:
                break
            if line.startswith("cancel "):
                ready_line = line
                break

        # Anything the target prints after readiness is drained on a thread,
        # so a chatty shutdown can never block on a full stdout pipe.
        drainer = threading.Thread(target=process.stdout.read, daemon=True)
        drainer.start()

        exited: dict = {}

        def wait_for_exit() -> None:
            # A blocking wait with no timeout is a single waitpid, stamped the
            # moment the kernel reports the exit: no backoff buckets.
            process.wait()
            exited["at"] = time.perf_counter()

        waiter = threading.Thread(target=wait_for_exit)
        waiter.start()

        if not ready_line:
            os.killpg(process.pid, signal.SIGKILL)
            waiter.join()
            return Measurement(
                target=target.name, scenario=f"D-cancel-{sig.name}", parameters=params,
                passed=False, error="target never reported readiness",
            )

        time.sleep(SIGNAL_DELAY_SECONDS)
        signalled_at = time.perf_counter()
        os.killpg(process.pid, sig)

        waiter.join(SHUTDOWN_GRACE_SECONDS)
        timed_out = waiter.is_alive()
        if timed_out:
            os.killpg(process.pid, signal.SIGKILL)
            waiter.join()
        drainer.join(1.0)

        latency_ms = None if timed_out else (exited["at"] - signalled_at) * 1000.0
        result = read_result(result_file) if result_file.exists() else {"code": process.returncode, "rss": None}
        err.seek(0)
        stderr = err.read()

    expected_code = 128 + int(sig)
    recorded = marker.read_text().split() if marker.exists() else []
    order = [int(value) for value in recorded if value.strip().isdigit()]
    checks = {
        "exit_code": result["code"] == expected_code,
        "all_cleanups_ran": len(order) == workers,
        "reverse_registration_order": order == list(range(workers - 1, -1, -1)),
        "within_grace_period": not timed_out,
    }
    passed = all(checks.values())
    return Measurement(
        target=target.name,
        scenario=f"D-cancel-{sig.name}",
        parameters={**params, "expected_exit_code": expected_code},
        cancellation_latency_ms=latency_ms,
        peak_rss_kb=result["rss"],
        exit_code=result["code"],
        checks=checks,
        passed=passed,
        error=None if passed else (stderr.strip()[-400:] or None),
    )


def combine(runs: list[Measurement]) -> Measurement:
    """Fold repeated runs of one scenario into a single row.

    Timings and memory are the median, which one slow run cannot drag around
    the way it drags a mean. A check passes only if it passed on every run.
    """
    import statistics

    def median(field_name: str):
        values = [getattr(r, field_name) for r in runs if getattr(r, field_name) is not None]
        return statistics.median(values) if values else None

    first = runs[0]
    checks = {name: all(r.checks.get(name, False) for r in runs) for name in first.checks}
    return Measurement(
        target=first.target,
        scenario=first.scenario,
        parameters={**first.parameters, "repeats": len(runs)},
        wall_clock_ms=median("wall_clock_ms"),
        overhead_us_per_unit=median("overhead_us_per_unit"),
        peak_rss_kb=median("peak_rss_kb"),
        cancellation_latency_ms=median("cancellation_latency_ms"),
        settle_ms=median("settle_ms"),
        exit_code=first.exit_code if all(r.exit_code == first.exit_code for r in runs) else None,
        checks=checks,
        passed=all(r.passed for r in runs),
        error=next((r.error for r in runs if r.error), None),
    )


def target_runtime(target: Target) -> str | None:
    """What a Laret target actually runs on, as reported by the binary itself.

    A native image reports the GraalVM that built it through
    java.vendor.version, which is the only reliable way to learn it when the
    binary was built in another job.
    """
    if not target.name.startswith("Laret"):
        return None
    try:
        done = subprocess.run([*target.argv, "bench", "runtime"], capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.SubprocessError):
        return None
    fields = summary_fields(done.stdout, "runtime")
    return fields.get("vendor_version", "").replace("_", " ") or fields.get("vm")


def provenance(cpus: str, targets: list[Target]) -> dict:
    def capture(*argv: str) -> str | None:
        try:
            return subprocess.run(argv, capture_output=True, text=True, timeout=30).stdout.strip().splitlines()[0]
        except (OSError, subprocess.SubprocessError, IndexError):
            return None

    return {
        # A dirty tree is flagged: the numbers then describe uncommitted code.
        "laret_commit": capture("git", "-C", str(REPO), "describe", "--always", "--dirty", "--abbrev=40"),
        "date": time.strftime("%Y-%m-%d %H:%M:%S %z"),
        "host": platform.node(),
        "kernel": platform.release(),
        "cpu": capture("bash", "-c", "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2-"),
        "pinned_cpus": cpus,
        # The toolchain that built the Cobra binary, which go.mod may have
        # switched away from whatever `go` is on the PATH.
        "go": capture("go", "version", str(BENCHMARKS / "cobra" / "bench-cobra")) or capture("go", "version"),
        "rust": capture("cargo", "--version"),
        "jdk": capture("bash", "-c", "java -version 2>&1"),
        "graalvm": next(
            (target_runtime(t) for t in targets if t.name == "Laret native"), None
        ) or capture("bash", "-c", "${GRAALVM_HOME:-/nonexistent}/bin/native-image --version 2>/dev/null"),
        "target_runtime": {t.name: target_runtime(t) for t in targets if t.name.startswith("Laret")},
    }


def default_targets() -> list[Target]:
    return [
        Target("Cobra", [str(BENCHMARKS / "cobra" / "bench-cobra")]),
        Target("clap", [str(BENCHMARKS / "clap" / "target" / "release" / "bench-clap")]),
        Target("picocli", ["java", "-jar", str(BENCHMARKS / "picocli" / "build" / "libs" / "bench-picocli.jar")]),
        # Both Laret targets are the minimal bench app, not the demo CLI, so
        # they measure the framework in the same shape as the other three.
        Target("Laret JVM", ["java", "-jar", str(REPO / "build" / "libs" / "laret-bench-fat.jar")]),
        Target("Laret native", [str(REPO / "build" / "native" / "nativeBenchCompile" / "laret-bench")]),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default="results.json", help="where to write results")
    parser.add_argument("--cpus", default="0-3", help="CPU list, e.g. 0-3 or 0,2, to pin every target to")
    parser.add_argument("--tasks", type=int, nargs="+", default=[1000, 10000], help="Scenario A task counts")
    parser.add_argument("--events", type=int, default=10000, help="Scenario B event count")
    parser.add_argument("--window", type=int, default=50, help="Scenario B storm window in ms")
    parser.add_argument("--debounce", type=int, default=150, help="Scenario B debounce in ms")
    parser.add_argument("--lines", type=int, default=100000, help="Scenario C line count")
    parser.add_argument("--pattern", default="7", help="Scenario C filter substring")
    parser.add_argument("--workers", type=int, default=500, help="Scenario D worker count")
    parser.add_argument("--only", nargs="+", help="restrict to these target names")
    parser.add_argument("--quick", action="store_true", help="reduced scales, for the correctness gate in CI")
    parser.add_argument("--no-warmup", action="store_true", help="skip the discarded warm-up run")
    parser.add_argument("--repeat", type=int, default=None,
                        help="runs per scenario, reported as the median (default 3, or 1 with --quick)")
    args = parser.parse_args()

    require_linux()
    cpus = parse_cpus(args.cpus)
    unavailable = cpus - os.sched_getaffinity(0)
    if unavailable:
        sys.exit(f"CPUs {sorted(unavailable)} are not available to this process; cannot pin to {args.cpus}")

    if args.repeat is None:
        args.repeat = 1 if args.quick else 3
    if args.repeat < 1:
        sys.exit("--repeat must be at least 1")

    if args.quick:
        args.tasks = [100]
        args.events = 1000
        args.lines = 1000
        args.workers = 20

    all_names = [t.name for t in default_targets()]
    targets = [t for t in default_targets() if not args.only or t.name in args.only]
    # Targets excluded by --only were never asked for; targets that are simply
    # not built were asked for and could not run. The rendered table must be
    # able to tell those two apart, so they are recorded separately.
    not_requested = [name for name in all_names if name not in [t.name for t in targets]]
    missing = [t.name for t in targets if not t.available()]
    targets = [t for t in targets if t.available()]
    if not targets:
        sys.exit(f"No benchmark targets are built. Missing: {', '.join(missing) or 'all'}")
    if missing:
        print(f"Skipping targets that are not built: {', '.join(missing)}", file=sys.stderr)

    marker = HERE / "cleanup-order.txt"
    measurements: list[Measurement] = []

    # Warm up thread pools and hot paths once per target, then discard.
    if not args.no_warmup:
        for target in targets:
            print(f"== warm-up {target.name}", file=sys.stderr)
            run_once([*target.argv, "bench", "fanout", "--tasks", "100", "--jobs", "0"], cpus, timeout=120)

    # Scenario-major, with the target order rotated on every repeat. Running
    # each target's whole suite in turn put the same target last every time,
    # after minutes of sustained load; on a machine that throttles, that alone
    # made the last target's pipeline more than twice as slow as when it ran on
    # its own. Rotating keeps a scenario's samples for every target close
    # together in time and spreads every target across every position. Each
    # run is still its own process, so no scenario's memory leaks into another.
    scenarios: list[tuple[str, callable]] = []
    for tasks in args.tasks:
        scenarios.append((f"A{tasks}", lambda t, n=tasks: scenario_fanout(t, cpus, n)))
    scenarios.append(("B", lambda t: scenario_storm(t, cpus, args.events, args.window, args.debounce)))
    scenarios.append(("C", lambda t: scenario_pipeline(t, cpus, args.lines, args.pattern)))
    for sig in (signal.SIGTERM, signal.SIGINT):
        scenarios.append((f"D-{sig.name}", lambda t, s=sig: scenario_cancel(t, cpus, args.workers, s, marker)))

    for key, run in scenarios:
        print(f"== scenario {key}", file=sys.stderr)
        samples: dict[str, list[Measurement]] = {t.name: [] for t in targets}
        for repeat in range(args.repeat):
            shift = repeat % len(targets)
            for target in targets[shift:] + targets[:shift]:
                samples[target.name].append(run(target))
        for target in targets:
            measurements.append(combine(samples[target.name]))

    marker.unlink(missing_ok=True)

    document = {
        "schema_version": SCHEMA_VERSION,
        "provenance": provenance(args.cpus, targets),
        "requested_targets": [t.name for t in targets],
        "not_requested_targets": not_requested,
        "skipped_targets": missing,
        "quick": args.quick,
        "repeats": args.repeat,
        "measurements": [asdict(m) for m in measurements],
    }
    Path(args.out).write_text(json.dumps(document, indent=2) + "\n")

    failures = [m for m in measurements if not m.passed]
    for measurement in failures:
        failed = [name for name, ok in measurement.checks.items() if not ok] or ["run"]
        print(
            f"FAIL {measurement.target} {measurement.scenario}: {', '.join(failed)}"
            + (f" ({measurement.error})" if measurement.error else ""),
            file=sys.stderr,
        )
    print(f"{len(measurements) - len(failures)}/{len(measurements)} measurements passed", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
