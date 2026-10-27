#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
find physics-core/src/main/java experiment-core/src/main/java -name '*.java' -print > "$OUT/sources.txt"
printf '%s\n' verification/D12-laser-gui/LaserSettingsSmoke.java >> "$OUT/sources.txt"
javac --release 21 -encoding UTF-8 -d "$OUT" @"$OUT/sources.txt"
java -cp "$OUT" dev.coldatomcraft.verification.LaserSettingsSmoke
