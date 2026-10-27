# D4 recommended: reproducible JDK 25 evidence capture

Open PowerShell in the **D4** project root, with Java 25 JDK and Python 3.11+
installed, then execute:

```powershell
python .\tools\verify_m0.py
```

This opt-in script runs actual standalone Java25 smoke, Python unit tests, asset
spec validation, Gradle 9.2.1 full clean build, actual JUnit XML audit and
class-by-class distributable JAR inspection. Evidence is written under
`verification/local-jdk25/<UTC timestamp>/report.json` with logs. If the JDK
is not 25, it exits with code **2 / BLOCKED**, not a false PASS. Failed builds
exit with code **1 / FAIL**. Code 0 means ONLY that build, unit tests and JAR
contents passed; it does **not** mean Minecraft client, server or independent
installed-JAR runtime was exercised.

The script never changes `eula.txt`, installs a JAR, opens Minecraft, modifies
worlds, publishes files or changes the user's Java/Gradle installation. The
independent installed-JAR server/client verification remains a separate manual
Gate after the script passes. Keep the report and the actual JAR for director
review. It will not retroactively validate historical R2 or D3 artifacts.

---

# D3 Windows build/review gate (not yet executed for D3)

The current D3 source was checked offline with Linux JDK21; to **actually** validate
Minecraft Java 26.3 + NeoForge 26.3.0.99, extract the final D3 ZIP into a fresh
folder on a Windows machine with JDK25 and network access to official Gradle/
NeoForge repositories. Run from the project root:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean :physics-core:test :experiment-core:test :neoforge-mod:build --no-build-cache --rerun-tasks
.\gradlew.bat :neoforge-mod:verifyDistributableJar
```

These commands are **instructions, not completed D3 evidence**. Record full logs,
JUnit XMLs and the distributable JAR SHA-256, then perform new D3 development-client
and dedicated-server launch/snapshot tests. A cleanly built D3 JAR must still be
installed in an isolated production instance to earn that separate validation.
Only logs from these exact D3 bytes count; historical R2 build and screenshots do
not validate the new scheduler. The trusted NeoForge ServerLevel integration and
world mutation are NOT IMPLEMENTED in this checkpoint.

Important API migration: `ExperimentRunScheduler.createExperiment()` now requires
an `ExperimentReservation` and returns `CreateOutcome`; `ServerMutationFacts`
requires the server-authoritative tick as a seventh component. Do NOT synthesize
that tick or apparatus identity from client network traffic; until the future
server adapter is built, use these APIs only for pure Java tests/research.

---

## Archived Windows setup guide (historical)

# M0 verification on Windows

## 1. Install and verify tools

- Install a Java 25 **JDK** (validated with Eclipse Temurin `25.0.3+9-LTS`), not only a JRE.
- Gradle does **not** need to be installed globally: the official `9.2.1` wrapper is checked in.
- For D4 automated certification: Python 3.11+ (`tomllib` is used); optional for older asset checks: Python 3.10+ (validated with Python 3.14.2).
- Optional: IntelliJ IDEA with the Gradle JDK set to JDK 25.

In a new PowerShell terminal in the project root:

```powershell
java -version
javac -version
.\gradlew.bat --version
```

`.\gradlew.bat --version` must report Gradle `9.2.1`, launcher JVM 25, and `OS: Windows ... amd64`.

## 2. Offline checks

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-core.ps1
python -m unittest discover -s verification -p 'test_*.py'
python .\tools\validate_assets.py .\assets\manifests\laser_source.example.json
```

Expected results for M0-R2:

- standalone smoke: `PASS: 46 standalone engineering checks; no physics models claimed`
- Python: 17 tests, all pass
- example manifest: `PASS ... SPEC ONLY (no model files claimed)`

## 3. Build

```powershell
.\gradlew.bat clean :physics-core:test :experiment-core:test :neoforge-mod:build --no-daemon --console=plain
```

The project uses the official ModDevGradle pipeline:

```groovy
neoForge {
    enable {
        version = project.neo_version
        disableRecompilation = true
    }
}
```

`disableRecompilation` skips the local Minecraft decompile/recompile stage and uses NeoForge's precompiled development artifacts. It **does not** skip compilation of this mod's own Java.

The default NeoFormRuntime decompile/recompile path failed on the M0-R2 authoring host while writing `package-info.class`; see `docs/M0_R2_AUDIT.md`. Do not treat a no-test GameTestServer or a cached task as a substitute for a real build.

On success, inspect:

```powershell
Get-ChildItem .\neoforge-mod\build\libs
jar tf .\neoforge-mod\build\libs\coldatomcraft-0.0.1-dev.jar
```

The build's `verifyDistributableJar` task also checks that `physics-core`, `experiment-core`, the mod entrypoint and `META-INF/neoforge.mods.toml` are present.

## 4. Development client

```powershell
.\gradlew.bat :neoforge-mod:runClient --no-daemon
```

Normal launch starts at the Minecraft main menu. For a repeatable save/re-entry smoke test after a world exists, an optional run property selects it:

```powershell
.\gradlew.bat :neoforge-mod:runClient -PcacQuickPlayWorld="New World" --no-daemon
```

The optional property does not change the default client launch.

## 5. Dedicated server

```powershell
.\gradlew.bat :neoforge-mod:runServer --no-daemon
```

A normal dedicated server may require the user to review and accept the Minecraft EULA. The M0-R2 development server did not create an EULA file, but that is not a general waiver. Do not commit `eula=true` or accept it on the owner's behalf.

For deterministic local stop/restart testing, RCON can be enabled in `neoforge-mod/run/server.properties` and the `stop` command sent to the local server. RCON credentials must stay out of the repository; use a throwaway local password.

## 6. Evidence and failures

Retain:

- the complete Gradle output;
- JUnit XML under `build/test-results/test/`;
- client/server `run/logs/latest.log` and launcher stdout;
- the built JAR and `jar tf` listing;
- `docs/M0_R2_AUDIT.md` status classifications.

If a command fails, save full output:

```powershell
.\gradlew.bat :neoforge-mod:build --stacktrace --no-daemon *> build-failure.log
```

Do not report a build or launch as successful without the retained log. Do not hide a blocked EULA, missing GPU/display, missing owner asset or no-test GameTest exit.

## D5 experimental optics preflight (no world mutation)

With Python and a working Java toolchain, run:

```powershell
python .\verification\M0-R2.1-D5\verify_optics_reference.py
```

This compiles only the independent physics-core and compares optical results
against 80-digit Python Decimal calculations (36 scalar values). It does not
require or run Minecraft, accept its EULA, or certify production laser physics.

After installing **JDK 25** and ensuring Maven/Gradle access, run:

```powershell
python .\tools\verify_m0.py
```

The D5 runner rejects a JDK other than 25, runs the optics oracle, and requires
at least **171 formal JUnit5 XML test cases** and an actual D5 distributable
JAR inventory check. To accept the mod for play, separately install the exact
JAR and validate the client and dedicated server; developer launches from
older R2 builds do not count as D5 evidence.
