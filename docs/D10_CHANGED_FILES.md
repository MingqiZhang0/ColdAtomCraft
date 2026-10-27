# D9 -> D10 source change inventory

- Added: 37
- Modified: 12
- Deleted: 0

This inventory was computed from the **original D9 ZIP entries** and the D10 working tree before the final SHA manifest was regenerated.

## Added
- `docs/D10_ENGINEERING_AUDIT.md`
- `docs/D10_PLACEHOLDER_PLAYTEST.md`
- `experiment-core/src/main/java/dev/coldatomcraft/experiment/PreviewBeamRoute.java`
- `experiment-core/src/main/java/dev/coldatomcraft/experiment/PreviewOpticalBench.java`
- `experiment-core/src/test/java/dev/coldatomcraft/experiment/PreviewBeamRouteTest.java`
- `experiment-core/src/test/java/dev/coldatomcraft/experiment/PreviewOpticalBenchTest.java`
- `neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticBeamScan.java`
- `neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticLaserBlock.java`
- `neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticPowerMeterBlock.java`
- `neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/PreviewBlocks.java`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/blockstates/diagnostic_laser.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/blockstates/diagnostic_power_meter.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/items/diagnostic_laser.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/items/diagnostic_power_meter.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/lang/en_us.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/lang/zh_cn.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/models/block/diagnostic_laser_off.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/models/block/diagnostic_laser_on.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/models/block/diagnostic_power_meter_0.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/models/block/diagnostic_power_meter_1.json`
- `neoforge-mod/src/main/resources/assets/coldatomcraft/models/block/diagnostic_power_meter_2.json`
- `neoforge-mod/src/main/resources/data/coldatomcraft/loot_table/blocks/diagnostic_laser.json`
- `neoforge-mod/src/main/resources/data/coldatomcraft/loot_table/blocks/diagnostic_power_meter.json`
- `tools/generate_preview_assets.py`
- `tools/test_preview_assets.py`
- `verification/D10-placeholder/PreviewBenchSmoke.java`
- `verification/D10-placeholder/README.md`
- `verification/D10-placeholder/engineering-python.log`
- `verification/D10-placeholder/formal-jdk25/java-version.log`
- `verification/D10-placeholder/formal-jdk25/javac-version.log`
- `verification/D10-placeholder/formal-jdk25/report.json`
- `verification/D10-placeholder/formal-preflight.log`
- `verification/D10-placeholder/full-core-offline.log`
- `verification/D10-placeholder/old-jar-negative-control.json`
- `verification/D10-placeholder/preview-assets.log`
- `verification/D10-placeholder/preview-smoke.log`
- `verification/D10-placeholder/run_preview_offline.sh`

## Modified
- `.github/workflows/ci.yml`
- `README.md`
- `docs/BLENDER_ASSET_HANDOFF.md`
- `docs/CHANGELOG.md`
- `docs/DECISIONS.md`
- `docs/TEST_PLAN.md`
- `gradle.properties`
- `neoforge-mod/build.gradle`
- `neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/ColdAtomCraft.java`
- `neoforge-mod/src/main/templates/META-INF/neoforge.mods.toml`
- `tools/inspect_mod_jar.py`
- `tools/verify_m0.py`

## Deleted
