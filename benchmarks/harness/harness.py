#!/usr/bin/env python3
"""Concurrency and parallel execution benchmark harness.

Runs the four scenarios of the concurrency contract against every configured
target and writes a schema-versioned results.json. See
.github/adr/concurrency-benchmark-suite.md.

Linux only: CPU pinning uses taskset and peak RSS comes from /proc. Numbers are
not comparable across operating systems, so the harness refuses to pretend.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import time
from dataclasses import dataclass, field, asdict
from pathlib import Path

SCHEMA_VERSION = 1
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
    exit_code: int | None = None
    checks: dict = field(default_factory=dict)
    passed: bool = True
    error: str | None = None


def require_linux() -> None:
    if platform.system() != "Linux":
        sys.exit(
            "This harness runs on Linux only: CPU pinning needs taskset and peak "
            "RSS is read from /proc/<pid>/status. Results from different operating "
            "systems are not comparable, so there is no degraded mode.\n"
            f"Detected: {platform.system()}"
        )
    if shutil.which("taskset") is None:
        sys.exit("taskset not found. Install util-linux, or the pinning contract cannot be met.")


def peak_rss_kb(pid: int) -> int | None:
    """VmHWM: the high-water mark, which survives the process shrinking again."""
    try:
        status = Path(f"/proc/{pid}/status").read_text()
    except OSError:
        return None
    match = re.search(r"^VmHWM:\s+(\d+) kB", status, re.M)
    return int(match.group(1)) if match else None


def pinned(argv: list[str], cpus: str) -> list[str]:
    return ["taskset", "-c", cpus, *argv]


def run_once(argv: list[str], cpus: str, timeout: float = 300.0) -> tuple[int, str, str, float, int | None]:
    """Run to completion, sampling peak RSS just before the process exits."""
    started = time.perf_counter()
    process = subprocess.Popen(
        pinned(argv, cpus),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    rss = None
    # Sample while it is alive; VmHWM is only readable before the pid is reaped.
    while process.poll() is None:
        sampled = peak_rss_kb(process.pid)
        if sampled is not None:
            rss = sampled if rss is None else max(rss, sampled)
        if time.perf_counter() - started > timeout:
            process.kill()
            break
        time.sleep(0.01)
    stdout, stderr = process.communicate()
    elapsed_ms = (time.perf_counter() - started) * 1000.0
    return process.returncode, stdout, stderr, elapsed_ms, rss


def summary_fields(stdout: str, prefix: str) -> dict:
    """Parse the one `prefix key=value ...` line every target prints."""
    for line in stdout.splitlines():
        if line.startswith(prefix + " "):
            return dict(
                pair.split("=", 1) for pair in line.strip().split(" ")[1:] if "=" in pair
            )
    return {}


def scenario_fanout(target: Target, cpus: str, tasks: int) -> Measurement:
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


def scenario_storm(target: Target, cpus: str, events: int, window: int, debounce: int) -> Measurement:
    params = {"events": events, "window_ms": window, "debounce_ms": debounce}
    argv = [
        *target.argv, "bench", "storm",
        "--events", str(events), "--window", str(window), "--debounce", str(debounce),
    ]
    code, stdout, stderr, elapsed, rss = run_once(argv, cpus)
    fields = summary_fields(stdout, "storm")

    checks = {
        "events_delivered": fields.get("events") == str(events),
        "coalesced_to_one_run": fields.get("runs") == "1",
    }
    return Measurement(
        target=target.name,
        scenario="B-storm",
        parameters=params,
        wall_clock_ms=elapsed,
        peak_rss_kb=rss,
        exit_code=code,
        checks=checks,
        passed=code == 0 and all(checks.values()),
        error=stderr.strip()[-400:] or None if code != 0 else None,
    )


def scenario_pipeline(target: Target, cpus: str, lines: int, pattern: str) -> Measurement:
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


def scenario_cancel(target: Target, cpus: str, workers: int, sig: signal.Signals, marker: Path) -> Measurement:
    """Start, wait for readiness, inject a signal, measure how long exiting takes."""
    params = {"workers": workers, "signal": sig.name}
    marker.unlink(missing_ok=True)
    argv = [
        *target.argv, "bench", "cancel",
        "--workers", str(workers), "--marker", str(marker), "--seconds", "30",
    ]

    process = subprocess.Popen(
        pinned(argv, cpus),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        # SIGINT is ignored by a backgrounded child of a non-interactive shell,
        # so the target gets its own process group and the signal goes there.
        start_new_session=True,
    )

    ready_deadline = time.perf_counter() + READY_TIMEOUT_SECONDS
    ready_line = ""
    while time.perf_counter() < ready_deadline:
        line = process.stdout.readline()
        if not line:
            break
        if line.startswith("cancel "):
            ready_line = line
            break

    rss = peak_rss_kb(process.pid)
    if not ready_line:
        process.kill()
        process.communicate()
        return Measurement(
            target=target.name, scenario=f"D-cancel-{sig.name}", parameters=params,
            passed=False, error="target never reported readiness",
        )

    time.sleep(SIGNAL_DELAY_SECONDS)
    signalled_at = time.perf_counter()
    os.killpg(os.getpgid(process.pid), sig)

    try:
        process.wait(timeout=SHUTDOWN_GRACE_SECONDS)
        timed_out = False
    except subprocess.TimeoutExpired:
        timed_out = True
        process.kill()
    latency_ms = (time.perf_counter() - signalled_at) * 1000.0
    sampled = peak_rss_kb(process.pid)
    if sampled is not None:
        rss = sampled if rss is None else max(rss, sampled)
    process.communicate()

    expected_code = 128 + int(sig)
    recorded = marker.read_text().split() if marker.exists() else []
    order = [int(value) for value in recorded if value.strip().isdigit()]
    checks = {
        "exit_code": process.returncode == expected_code,
        "all_cleanups_ran": len(order) == workers,
        "reverse_registration_order": order == list(range(workers - 1, -1, -1)),
        "within_grace_period": not timed_out,
    }
    return Measurement(
        target=target.name,
        scenario=f"D-cancel-{sig.name}",
        parameters={**params, "expected_exit_code": expected_code},
        cancellation_latency_ms=latency_ms,
        peak_rss_kb=rss,
        exit_code=process.returncode,
        checks=checks,
        passed=all(checks.values()),
    )


def provenance(cpus: str) -> dict:
    def capture(*argv: str) -> str | None:
        try:
            return subprocess.run(argv, capture_output=True, text=True, timeout=30).stdout.strip().splitlines()[0]
        except (OSError, subprocess.SubprocessError, IndexError):
            return None

    return {
        "laret_commit": capture("git", "-C", str(REPO), "rev-parse", "HEAD"),
        "date": time.strftime("%Y-%m-%d %H:%M:%S %z"),
        "host": platform.node(),
        "kernel": platform.release(),
        "cpu": capture("bash", "-c", "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2-"),
        "pinned_cpus": cpus,
        "go": capture("go", "version"),
        "rust": capture("cargo", "--version"),
        "jdk": capture("bash", "-c", "java -version 2>&1"),
        "graalvm": capture("bash", "-c", "${GRAALVM_HOME:-/nonexistent}/bin/native-image --version 2>/dev/null"),
    }


def default_targets() -> list[Target]:
    return [
        Target("Cobra", [str(BENCHMARKS / "cobra" / "bench-cobra")]),
        Target("clap", [str(BENCHMARKS / "clap" / "target" / "release" / "bench-clap")]),
        Target("picocli", ["java", "-jar", str(BENCHMARKS / "picocli" / "build" / "libs" / "bench-picocli.jar")]),
        Target("Laret JVM", ["java", "-jar", str(REPO / "build" / "libs" / "laret-fat.jar")]),
        Target("Laret native", [str(REPO / "build" / "native" / "nativeCompile" / "laret")]),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default="results.json", help="where to write results")
    parser.add_argument("--cpus", default="0-3", help="taskset CPU list to pin every target to")
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
    args = parser.parse_args()

    require_linux()

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

    for target in targets:
        print(f"== {target.name}", file=sys.stderr)

        # Warm up thread pools and hot paths, then discard.
        if not args.no_warmup:
            run_once([*target.argv, "bench", "fanout", "--tasks", "100", "--jobs", "0"], args.cpus, timeout=120)

        # Scenarios run one at a time, each finishing before the next starts,
        # so one scenario's memory is never attributed to the next.
        for tasks in args.tasks:
            measurements.append(scenario_fanout(target, args.cpus, tasks))
        measurements.append(scenario_storm(target, args.cpus, args.events, args.window, args.debounce))
        measurements.append(scenario_pipeline(target, args.cpus, args.lines, args.pattern))
        measurements.append(scenario_cancel(target, args.cpus, args.workers, signal.SIGTERM, marker))
        measurements.append(scenario_cancel(target, args.cpus, args.workers, signal.SIGINT, marker))

    marker.unlink(missing_ok=True)

    document = {
        "schema_version": SCHEMA_VERSION,
        "provenance": provenance(args.cpus),
        "requested_targets": [t.name for t in targets],
        "not_requested_targets": not_requested,
        "skipped_targets": missing,
        "quick": args.quick,
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
