# CAC-M0-R2.1-D10 — Placeholder apparatus code audit

This checkpoint intentionally replaces the **missing art asset dependency** with two temporary vanilla-cuboid block models. It does **not** replace the Owner's scientific equations, create a model approval, or present fictional instrument calibration as real.

## Deliverable content

- Two block registrations, items and a separate creative preview tab: `diagnostic_laser`, `diagnostic_power_meter`.
- Laser state `facing` + `enabled`; detector state `facing` + aperture index `0..2`. These are synced and persisted *by Minecraft BlockState* once the mod actually loads.
- Preview geometry, different ON/OFF and aperture appearances, `en_us`/`zh_cn`, self-drop loot tables, new client item mappings.
- Right-click actions are intended to run state updates on the **server**. No client-originating measurement power value is trusted.
- Thin `DiagnosticBeamScan` adapter queries a maximum of 24 loaded cells; `PreviewBeamRoute` performs an independently testable fail-closed route evaluation.
- `PreviewOpticalBench` uses existing D9 `GaussianBeam` and `IdealOpticalPowerMeter` reference code to compute a static ideal W reading.
- **No** new material constants, spectrum response, heating, entities, lasers in flight, game damage, sound or actual beam rendering.

## Scientific assumption and validation limits

All fixture source parameters (20 mW, 780 nm, 0.5 mm, waist at port) and the 1 metre/block mapping are clearly labelled *DEV-only*, not scientific approval or final laboratory instrument configuration.

D9's ideal power meter is a **virtual non-invasive observer**: numerical detection does not subtract power from the beam, deposit energy or change world state. The receiver must face the laser and the intermediate world route is restricted to air only.

The `simulatedTimeNanos` field is set to 0 for the static preview; Minecraft game ticks are not falsely treated as physical optical time.

## Validation and open gates

| Gate | Status | Evidence |
|---|---|---|
| D9 ZIP integrity | PASS | Original D9 source ZIP extracted safely and validated |
| Core Java21 compilation, strict lint | PASS | `verification/D10-placeholder/run_preview_offline.sh` |
| D10 pure-Java optical fixture smoke | PASS | See `verification/D10-placeholder/*` |
| D10 state/route JUnit-source tests | NON-JUNIT PASS | Offline reflection harness; this is NOT an official Jupiter run |
| Placeholder blockstate/item/model JSON checks | PASS | `tools/test_preview_assets.py` |
| D10 Gradle/JUnit under JDK25 | BLOCKED | Current host has only JDK21; Gradle/NeoForge distribution network unavailable |
| Minecraft 26.3 NeoForge startup for D10 | NOT RUN | Requires real dev host |
| D10 game click and visuals | NOT RUN | Requires in-game screenshot/test log |
| D10 release JAR independent install | NOT RUN | No D10 JDK25 build yet |
| Owner Blender artwork | NOT APPLICABLE | Temporary preview models, planned replacement |
| Owner-approved scientific parameter set | NOT APPLICABLE | Diagnostic-only parameters |

**Do not mark M0 CLOSED on these results.** Formal Windows validation and game playtest are necessary. Run `python tools/verify_m0.py`, then `./gradlew.bat :neoforge-mod:runClient`, and follow `docs/D10_PLACEHOLDER_PLAYTEST.md`.

## Final independently executed evidence (container, JDK 21)

| Executed check | Result |
|---|---|
| Original D9 ZIP SHA-256 (baseline) | 348/348 MATCH |
| Existing engineering Java smoke | 383/383 PASS |
| Existing + new `*Test.java` methods under offline reflection shim | 307/307 PASS (NON-JUNIT) |
| D10 preview numeric/route Java smoke | 52/52 PASS |
| Existing Python engineering tests | 30/30 PASS |
| D10 model/blockstate/item/loot/lang tests | 8/8 PASS |
| Old historical pre-D10 JAR rejection | PASS (inspector correctly fails its mandatory D10 class/resource checks) |
| Formal `tools/verify_m0.py` | BLOCKED (exit code 2: Java 21, Java 25 required) |
| NeoForge D10 build, client and server test | NOT RUN |

Evidence is under `verification/D10-placeholder/`. The 307 Java tests are executed via a custom **NON-JUNIT** offline reflection harness and do not substitute for Gradle's JUnit Platform. The `PreviewBenchSmoke` is a separate, small targeted check. An in-game playtest with the exact **D10 packaged JAR** remains required.

The 3D JSON models are intentionally temporary and have not yet been rendered inside Minecraft; a passing asset checker is **not** evidence of runtime model baking or texture resolution.
