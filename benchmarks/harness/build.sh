#!/usr/bin/env bash
# Build every benchmark target. Missing toolchains are reported and skipped
# rather than failing the script, so a partial comparison is still possible;
# harness.py records which targets were skipped.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCHMARKS="$(dirname "$HERE")"
REPO="$(dirname "$BENCHMARKS")"

built=()
skipped=()

note_skip() {
  skipped+=("$1")
  echo "SKIP $1: $2" >&2
}

echo "== Cobra (Go)"
if command -v go > /dev/null; then
  (cd "$BENCHMARKS/cobra" && go build -trimpath -ldflags="-s -w" -o bench-cobra .)
  built+=("Cobra")
else
  note_skip Cobra "go not found"
fi

echo "== clap (Rust)"
if command -v cargo > /dev/null; then
  (cd "$BENCHMARKS/clap" && cargo build --release)
  built+=("clap")
else
  note_skip clap "cargo not found"
fi

echo "== picocli (Java)"
if command -v java > /dev/null; then
  (cd "$BENCHMARKS/picocli" && ./gradlew --quiet --no-daemon shadowJar)
  built+=("picocli")
else
  note_skip picocli "java not found"
fi

echo "== Laret JVM"
(cd "$REPO" && ./gradlew --quiet --console=plain shadowJar)
built+=("Laret JVM")

echo "== Laret native"
if [ -n "${GRAALVM_HOME:-}" ] && [ -x "${GRAALVM_HOME}/bin/native-image" ]; then
  (cd "$REPO" && ./gradlew --quiet --console=plain nativeCompile)
  built+=("Laret native")
else
  note_skip "Laret native" "GRAALVM_HOME is unset or has no native-image"
fi

echo
echo "Built:   ${built[*]:-none}"
echo "Skipped: ${skipped[*]:-none}"
