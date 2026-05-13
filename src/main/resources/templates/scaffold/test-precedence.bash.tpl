#!/usr/bin/env bash
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

if echo "$OUTPUT" | grep -q "Hello, from-flag!"; then
  echo "PASS: CLI flag wins over ENV and FILE"
  echo "Precedence test passed"
  exit 0
fi

echo "FAIL: CLI flag should win"
echo "Output: $OUTPUT"
exit 1
