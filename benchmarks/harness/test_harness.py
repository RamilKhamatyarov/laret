"""Unit tests for render.py and harness.combine: python3 -m unittest discover benchmarks/harness"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import harness  # noqa: E402
import render  # noqa: E402


def measurement(target, scenario, **fields):
    base = {
        "target": target, "scenario": scenario, "parameters": {}, "wall_clock_ms": None,
        "peak_rss_kb": None, "cancellation_latency_ms": None, "settle_ms": None,
        "exit_code": None, "checks": {}, "passed": True, "error": None,
    }
    base.update(fields)
    return base


def storm(target, settle_ms):
    return measurement(target, "B-storm", parameters={"debounce_ms": 150}, settle_ms=settle_ms)


def document(measurements, **extra):
    return {"provenance": {}, "measurements": measurements, "repeats": 3, **extra}


def picocli_cell(rendered, label):
    row = next(line for line in rendered.splitlines() if line.startswith(f"| {label} "))
    return row.rstrip(" |").rsplit("|", 1)[1].strip()


class VersusTest(unittest.TestCase):

    def test_a_gap_under_the_absolute_floor_is_level(self):
        self.assertEqual(render.versus(0.16, 0.15, "wall"), "level")
        self.assertEqual(render.versus(0.15, 0.15, "wall"), "level")

    def test_a_gap_under_ten_percent_is_level(self):
        self.assertEqual(render.versus(105.0, 100.0, "wall"), "level")

    def test_a_clear_gap_is_ahead_or_behind(self):
        self.assertEqual(render.versus(0.15, 0.96, "wall"), "ahead")
        self.assertEqual(render.versus(0.31, 0.19, "wall"), "**behind**")

    def test_rss_rows_use_only_the_relative_tolerance(self):
        self.assertEqual(render.versus(40.0, 40.04, "rss"), "level")
        self.assertEqual(render.versus(33.0, 130.0, "rss"), "ahead")


class RenderTest(unittest.TestCase):

    def test_a_settle_time_within_noise_of_picocli_is_level(self):
        rendered = render.render(document([
            storm("Laret native", 150.16), storm("picocli", 150.15),
            storm("Cobra", 150.5), storm("clap", 151.4),
        ]))

        self.assertEqual(picocli_cell(rendered, "B settle above debounce"), "level")
        self.assertIn("ahead of or level with picocli", rendered)

    def test_cancellation_latency_shows_the_range_of_its_runs(self):
        rendered = render.render(document([
            measurement("Laret native", "D-cancel-SIGTERM", cancellation_latency_ms=6.8,
                        cancellation_latency_samples_ms=[4.9, 6.8, 8.1], exit_code=143),
        ], repeats_cancel=9))

        self.assertIn("6.8 ms (4.9-8.1)", rendered)
        self.assertIn("median of 9 runs, with the range", rendered)

    def test_results_without_samples_still_render_the_median(self):
        rendered = render.render(document([
            measurement("Laret native", "D-cancel-SIGTERM", cancellation_latency_ms=6.8, exit_code=143),
        ]))

        self.assertIn("| 6.8 ms |", rendered)


class CombineTest(unittest.TestCase):

    def test_combine_keeps_every_latency_and_reports_the_median(self):
        runs = [
            harness.Measurement(target="t", scenario="D-cancel-SIGTERM", parameters={},
                                cancellation_latency_ms=value)
            for value in (8.0, 2.0, 5.0)
        ]

        combined = harness.combine(runs)

        self.assertEqual(combined.cancellation_latency_ms, 5.0)
        self.assertEqual(combined.cancellation_latency_samples_ms, [8.0, 2.0, 5.0])


if __name__ == "__main__":
    unittest.main()
