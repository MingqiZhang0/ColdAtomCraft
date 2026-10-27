#!/usr/bin/env sh
# Research-only reference checks. Uses locally installed Java, NOT formal JUnit/Gradle.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
printf '%s\n' '=== Existing core + test-method sweep (NON-JUNIT) ==='
sh "$ROOT/verification/M0-R2.1-D3/run_local_offline.sh"
printf '%s\n' '=== Dedicated OPT-001 smoke ==='
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/OpticsSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.OpticsSmoke
printf '%s\n' '=== Independent Decimal(80-digit) scientific oracle ==='
python3 "$ROOT/verification/M0-R2.1-D5/verify_optics_reference.py"
printf '%s\n' '=== All Python tests (asset + engineering tools) ==='
python3 -m unittest discover -s "$ROOT/verification" -p 'test_*.py' -v
printf '%s\n' 'NOTICE: offline evidence is NOT official JDK25/JUnit/NeoForge verification.'
