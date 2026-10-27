#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" "$ROOT/experiment-core/src/main/java" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/D13-multi-beam/MultiBeamBudgetSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.MultiBeamBudgetSmoke
printf '%s\n' 'D13 allocation smoke is pure Java. Live particle rendering requires NeoForge 26.3 and in-game validation.'
