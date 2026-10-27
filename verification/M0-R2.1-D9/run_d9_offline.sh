#!/usr/bin/env sh
# D9 local scientific checks. NOT Gradle/JUnit Platform or game runtime.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
sh "$ROOT/verification/M0-R2.1-D8/run_d8_offline.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" "$ROOT/experiment-core/src/main/java" \
  -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/PowerMeterSmoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.PowerMeterSmoke
python3 "$ROOT/verification/M0-R2.1-D9/verify_detector_reference.py"
echo 'NOTICE: this scientific reference run is not official JDK25/Gradle/JUnit or NeoForge runtime.'
