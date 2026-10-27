#!/bin/sh
# Offline partial verification. This is NOT the Java25 NeoForge game test.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" "$ROOT/experiment-core/src/main/java" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/D10-placeholder/PreviewBenchSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.PreviewBenchSmoke
python3 "$ROOT/tools/test_preview_assets.py"
printf '%s\n' 'NOTE: placeholder NeoForge registration, interactions and rendering NOT tested without JDK25/dependencies.'
