#!/usr/bin/env zsh
# Config precedence: CLI flag > ENV var > config file > default
set -euo pipefail

TEST_DIR=$(mktemp -d -t ${appName}-precedence-XXXXXX)
trap 'rm -rf "$TEST_DIR"' EXIT
cd "$TEST_DIR"

cat > .${appName}.yml <<EOF
greeting:
  name: "from-file"
EOF

export ${envPrefix}_GREETING_NAME="from-env"

OUTPUT=$(${appName} hello run --name "from-flag")

if [[ "$OUTPUT" == *"Hello, from-flag!"* ]]; then
  print -- "PASS: CLI flag wins over ENV and FILE"
  print -- "Precedence test passed"
  exit 0
fi

print -- "FAIL: CLI flag should win"
print -- "Output: $OUTPUT"
exit 1
