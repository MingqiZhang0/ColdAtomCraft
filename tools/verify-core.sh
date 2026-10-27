#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" "$ROOT/experiment-core/src/main/java" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/Smoke.java" "$ROOT/verification/OpticsSmoke.java" \
  "$ROOT/verification/CoherentSmoke.java" "$ROOT/verification/OpticalPathSmoke.java" \
  "$ROOT/verification/GeometryOpticsSmoke.java" "$ROOT/verification/PowerMeterSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 25 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.Smoke

java -cp "$TMP/classes" dev.coldatomcraft.verification.OpticsSmoke
java -cp "$TMP/classes" dev.coldatomcraft.verification.CoherentSmoke
java -cp "$TMP/classes" dev.coldatomcraft.verification.OpticalPathSmoke

java -cp "$TMP/classes" dev.coldatomcraft.verification.GeometryOpticsSmoke
java -cp "$TMP/classes" dev.coldatomcraft.verification.PowerMeterSmoke
