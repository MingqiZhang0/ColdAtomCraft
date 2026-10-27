#!/usr/bin/env sh
# Standalone D7 scientific and engineering verification. NOT official JUnit.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
sh "$ROOT/verification/M0-R2.1-D6/run_d6_offline.sh"
echo '=== D7 standalone 21-compatible physics smoke ==='
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/OpticalPathSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.OpticalPathSmoke
echo '=== D7 independent 80-digit optical path oracle ==='
python3 "$ROOT/verification/M0-R2.1-D7/verify_optical_path_reference.py"
echo 'NOTICE: this script is an offline, non-JUnit verification only.'
