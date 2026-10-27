#!/usr/bin/env sh
# Offline-only engineering test harness, not official JUnit / Gradle / NeoForge.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
printf 'OFFLINE JAVA ENVIRONMENT\n'
java -version 2>&1
find "$ROOT/physics-core/src/main/java" "$ROOT/experiment-core/src/main/java" \
  "$ROOT/physics-core/src/test/java" "$ROOT/experiment-core/src/test/java" \
  "$ROOT/verification/M0-R2.1-D2/offline-harness" -name '*.java' -print > "$TMP/sources.txt"
printf '%s\n' "$ROOT/verification/Smoke.java" >> "$TMP/sources.txt"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" dev.coldatomcraft.verification.Smoke
set --
for base in physics-core experiment-core; do
  for path in $(find "$ROOT/$base/src/test/java" -name '*Test.java' -print); do
    name=${path#"$ROOT/$base/src/test/java/"}
    name=${name%.java}
    class_name=$(printf '%s' "$name" | tr '/' '.')
    set -- "$@" "$class_name"
  done
done
java -cp "$TMP/classes" OfflineTestRunner "$@"
python3 -m unittest discover -s "$ROOT/verification" -p test_asset_validator.py -v
printf 'WARNING: offline assertion shim is NOT real JUnit; JDK 25/Gradle/NeoForge NOT RUN\n'
