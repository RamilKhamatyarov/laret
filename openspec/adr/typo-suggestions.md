# Typos & Smart Suggestions

- Status: Accepted
- Date: 2026-08-25

## Context

When a user mistyped a group (`fil`), command (`creat`), or flag (`--focre`), Laret gave no help. Unknown groups printed `Group not found` plus the full application help; unknown commands printed `Command not found` plus group help; unknown flags were **silently ignored** by `Command.parseArgumentsAndOptions` (the non-matching token was skipped). There was no string-distance utility in the codebase.

A CLI should nudge users toward the intended name, the way git, cargo, and kubectl do.

## Decision

Add Levenshtein-based "did you mean?" suggestions for mistyped groups, commands, and flags, with an opt-in interactive correction.

### Algorithm and ranking

The `core` package holds an `EditDistance` (Optimal String Alignment — restricted Damerau-Levenshtein: insertion, deletion, substitution, and adjacent transposition each cost 1) and a `Suggester`. Transpositions are among the most common typos (`craete`→`create`), so counting them as distance 1 ranks the intended word first. Comparison is case-insensitive; suggestions are shown in canonical case.

`Suggester.rank` returns up to 3 candidates within an adaptive threshold — `min(2, ceil(longer/3))`, at least 1 — sorted by ascending distance then alphabetically. The scaled cap keeps short inputs from matching noise (a 1-char input only matches distance-1 typos) while letting longer words tolerate 2 edits. `Suggester.autoFix` returns the single candidate at distance 1, or null when there are zero or several.

### Where suggestions attach

- **Groups / commands**: `CliApp.executeCommand` keeps its existing `Group not found` / `Command not found` output (so existing behavior and tests are preserved) and appends a `Did you mean …` line. Candidates are the primary names; hidden commands are excluded.
- **Flags**: `Command.parseArgumentsAndOptions` collects unrecognized `-x` / `--xyz` tokens and prints `Unknown flag '--focre'. Did you mean '--force'?` to stderr. This is a **warning only** — the command still runs and the exit code is unchanged — so no existing script or test that passes stray flags breaks. Negative-number values and bare dashes are excluded from detection.

### Interactive correction

A global `--fix` flag, stripped early in `dispatch`, enables interactive correction. When set, the process is interactive (`System.console() != null`), and there is exactly one distance-1 candidate, Laret prompts `Did you mean 'create'? [Y/n]` and, on confirmation, re-runs the corrected invocation. Without `--fix`, or in a non-TTY (scripts, CI), it never prompts and never auto-runs — corrections require explicit consent.

### Opt-out

Suggestions are on by default. Setting `LARET_NO_SUGGEST` (non-empty) suppresses them, mirroring the `NO_COLOR`-style gate already used for colored output, so callers that need a stable, minimal error string can turn them off. (A config-level switch can layer on later; the env gate covers the immediate need.)

## Package Boundaries

`EditDistance`, `Suggester`, and `Suggestions` live in `core`, alongside `CliApp`, `CommandContext`, and `CommandRunner`. `Suggestions` uses `ui` for the confirmation prompt (a dependency `core` already has), and `model.Command` already depends on `core`. No new package, cycle, or third-party dependency is introduced.

## Consequences

Mistyped names now produce actionable guidance, and `--fix` offers a safe, consent-based correction. Flag typos are surfaced without changing lenient parsing, so the change is backward compatible. The cost is one reserved global flag (`--fix`), one env var (`LARET_NO_SUGGEST`), and a small dependency from `model`/`core` onto `suggest`. Because flag detection is heuristic, exotic argument shapes (a bare `-` or a negative number used as a positional) are deliberately not reported to avoid false positives.
