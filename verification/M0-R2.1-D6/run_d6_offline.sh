#!/usr/bin/env sh
# Run all prior offline reference checks and the new D6 coherent field suite.
# Explicitly NON-JUNIT and NOT a Java25/NeoForge game build.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
printf '%s\n' '=== Existing D5 + new D6 test method sweep ==='
sh "$ROOT/verification/M0-R2.1-D5/run_d5_offline.sh"
printf '%s\n' '=== Dedicated D6 complex field smoke ==='
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/CoherentSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.CoherentSmoke
printf '%s\n' '=== Independent 70-digit coherent/Jones oracle ==='
python3 "$ROOT/verification/M0-R2.1-D6/verify_coherent_reference.py"
printf '%s\n' 'NOTICE: offline evidence is NOT formal JUnit, JDK25 or NeoForge runtime validation.'
