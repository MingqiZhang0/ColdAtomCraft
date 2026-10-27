#!/usr/bin/env sh
# Scientific reference checks only. NOT real JUnit / Java25 / NeoForge.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
sh "$ROOT/verification/M0-R2.1-D7/run_d7_offline.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
find "$ROOT/physics-core/src/main/java" -name '*.java' -print > "$TMP/physics-sources.txt"
printf '%s\n' "$ROOT/verification/GeometryOpticsSmoke.java" >> "$TMP/physics-sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$TMP/classes" @"$TMP/physics-sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.GeometryOpticsSmoke
python3 "$ROOT/verification/M0-R2.1-D8/verify_fresnel_reference.py"
echo 'NOTICE: D8 offline checks do not replace Java25 Gradle/JUnit or NeoForge runtime gates.'
