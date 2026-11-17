# Colored Output: Drop Jansi, Detect Support Directly

- Status: Accepted
- Date: 2026-07-21

## Context

Colored output did not appear when laret ran as a GraalVM native binary in
PowerShell with Starship inside the WezTerm terminal on Windows 10. Error
messages, help, and other styled text rendered as plain, uncolored text — the
ANSI escape sequences were absent entirely, not shown literally.

laret writes raw ANSI escapes from `ui/Colors.kt`, gated by
`Colors.isColorSupported()`, and `CliApp.run()` wrapped every invocation in
Jansi's `AnsiConsole.systemInstall()` / `systemUninstall()`. Jansi (2.4.3) also
supplied the `Ansi` builder used by `core/HelpFormatter`.

Two defects combined:

Jansi relies on a bundled Windows native helper library, loaded at runtime by
its `JansiLoader` via `System.load`. The GraalVM native-image configuration
(`resource-config.json`, `reflect-config.json`) contains no Jansi entries, so
the native library is neither included nor loadable in the binary. Unable to
confirm that stdout is a terminal or to negotiate virtual-terminal processing,
`AnsiConsole.systemInstall()` degrades to strip mode and deletes every escape
sequence laret emits. The sequences never reach WezTerm, which renders VT
natively and would have displayed them correctly. The fat JAR does not hit this
because the native library loads there (the same load that triggered the Java 25
restricted-native-access warning addressed earlier in the docs workflow).

Independently, `isColorSupported()` returned `true` for any Windows version
whose `os.version` string compared lexicographically `>= "10.0"`. String
comparison is wrong — `"6.3"` (Windows 8.1) sorts after `"10.0"` — and the
check never verified that stdout was an actual terminal, so piping laret to a
file embedded color codes.

Jansi's ANSI-to-Win32 translation exists for legacy consoles that predate
virtual-terminal support. Every terminal in the failing stack — WezTerm,
Windows Terminal, and any ConPTY host — renders VT natively, and laret already
performs its own color gating, making Jansi a redundant layer whose only net
effect here was to suppress color.

## Decision

Remove Jansi entirely: the dependency, `AnsiConsole.systemInstall()` /
`systemUninstall()` in `CliApp` and `completion`, and the `Ansi` builder use in
`core/HelpFormatter`, which is replaced by laret's own `Colors` helpers. laret
writes raw ANSI and lets the terminal render it, which is correct for WezTerm,
Windows Terminal, ConPTY, and Unix terminals.

No Win32 `SetConsoleMode` call is added. Modern terminals enable
virtual-terminal processing themselves; adding a Foreign Function call to cover
legacy bare `conhost.exe` would reintroduce the native-image fragility being
removed, for terminals laret's users do not run. Bare-legacy-console users can
force color with `CLICOLOR_FORCE`.

Color support is decided by a pure function, `ColorSupport.detect`, taking an
environment lookup, OS name, OS version, and a terminal flag, so it is unit
testable without a real console. `Colors.isColorSupported()` delegates to it,
passing `System.console() != null` as the terminal flag — the same TTY signal
`UnicodeSupport` already uses. Precedence:

1. `NO_COLOR` present and non-empty disables color (no-color.org convention).
2. `CLICOLOR_FORCE` present, non-empty, and not `0` forces color on, overriding
   the terminal check so forced output survives a pipe.
3. stdout is not a terminal disables color, fixing the pipe-to-file bug.
4. Otherwise: Windows major version `>= 10` (parsed numerically, not compared as
   a string) enables color; on other platforms `TERM` must be set and not
   `dumb`.

`NO_COLOR` is checked before `CLICOLOR_FORCE`, so an explicit disable wins.

The `--enable-native-access=ALL-UNNAMED` argument is retained: it covers jline's
native terminal access and is unrelated to Jansi.

## Package Boundaries

`ColorSupport` joins `Colors` and the `ColorHelpers` functions in the `ui`
package. `core/HelpFormatter` depends on `ui` for styling, as it already did for
`redBold`. No new package or dependency is introduced; one dependency is
removed.

## Consequences

Colored output works in the native binary across modern terminals, and a class
of native-image native-library problems disappears along with the Java 25
restricted-native-access warning. Piping now produces clean, code-free text, and
`NO_COLOR` / `CLICOLOR_FORCE` interoperate with Starship, CI, and scripting.

laret no longer translates ANSI to the Win32 console API, so a legacy
`conhost.exe` without virtual-terminal processing would show literal escape
codes; this is accepted and mitigated by the standard environment overrides. The
detection change also means color is off by default when output is not a
terminal, including under the test harness, which makes captured output more
predictable.
