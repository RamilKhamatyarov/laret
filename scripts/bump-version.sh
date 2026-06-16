#!/usr/bin/env bash
# Usage: ./scripts/bump-version.sh <version>
# Example: ./scripts/bump-version.sh 0.2.0
set -euo pipefail

VERSION=${1:-}
if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>"
  echo "Example: $0 0.2.0"
  exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
  echo "Invalid version format: '$VERSION' (expected MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-SNAPSHOT)"
  exit 1
fi

WIZARD_FILE="src/main/kotlin/com/rkhamatyarov/laret/scaffold/wizard/InteractiveWizard.kt"
MAIN_FILE="src/main/kotlin/com/rkhamatyarov/laret/example/Main.kt"

sed -i "s/^version = \"[^\"]*\"/version = \"$VERSION\"/" build.gradle.kts
sed -i "s/DEFAULT_LARET_VERSION = \"[^\"]*\"/DEFAULT_LARET_VERSION = \"$VERSION\"/" "$WIZARD_FILE"
sed -i "s/version = \"[^\"]*\",/version = \"$VERSION\",/" "$MAIN_FILE"

echo "Version bumped to $VERSION"
echo "Changed files:"
echo "  build.gradle.kts"
echo "  $WIZARD_FILE"
echo "  $MAIN_FILE"
if [[ "$VERSION" != *-SNAPSHOT ]]; then
  echo ""
  echo "Next: git tag v$VERSION && git push origin v$VERSION"
fi
