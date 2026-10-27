#!/usr/bin/env bash
# Compare frozen D3 and current D4 claim/abandon behavior on one JDK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="$(cd "$ROOT/.." && pwd)/ColdAtomCraft-M0-R2.1-D3"
PROBE="$ROOT/verification/M0-R2.1-D4/probes/ClaimHistoryBenchmark.java"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
for version in D3 D4; do
  if [[ "$version" == D3 ]]; then R="$BASE"; else R="$ROOT"; fi
  mkdir -p "$TEMP/$version"
  find "$R/physics-core/src/main/java" "$R/experiment-core/src/main/java" -name '*.java' > "$TEMP/$version/sources.txt"
  echo "$PROBE" >> "$TEMP/$version/sources.txt"
  javac --release 21 -encoding UTF-8 -d "$TEMP/$version" @"$TEMP/$version/sources.txt"
  echo "### $version"
  for repetition in 1 2 3; do java -Xms128m -Xmx512m -cp "$TEMP/$version" dev.coldatomcraft.verification.ClaimHistoryBenchmark 16000 4000; done
done
