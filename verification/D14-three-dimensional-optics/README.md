# D14 three-dimensional optical bench: independent evidence

## Scientific/math smoke

Command: `python tools/check_d14_optics.py`.

This compiles the real D14 pure Java `physics-core` and `experiment-core` source
sets using the installed JDK 21 compiler with warnings as errors, then runs
`OpticalBench3DSmoke`. It deliberately does not use NeoForge classes, mocks of
the Minecraft API or a fake JUnit runner. The smoke asserts 235 fixture
conditions (including 24 old D10 power-meter separations, six directions,
lens/port power conservation, polarisation, loops and fail-closed handling).

## Regression suite

- `sh verification/M0-R2.1-D3/run_local_offline.sh`: 383 pure engineering
  smoke checks and 344 actual JUnit-source methods via a reflection harness,
  **NOT** formal JUnit XML. Do not advertise this as official JUnit 5.
- `python -m unittest discover -s verification -p 'test_*.py' -q`:
  62 static/asset/engineering assertions across tools and integration.
- `python tools/test_preview_assets.py -q`: 9 D10-D14 JSON / model validators.
- `python tools/check_source_manifest.py`: all shipped source digests and
  their complete file set match, with no self-hash or cache files. Run after
  any source edit and use `--update` only for a deliberate new snapshot.
- `python tools/check_multi_beam.py`: 13,020 historical D13 seeded budget
  configurations. The number is a test-case count, not a benchmark or FPS.

The command outputs must be re-run on any changed source before reporting
PASS. Prior historical GitHub CI for D12 does not validate this D14 tree.

## Evidence not yet available

This environment does not contain JDK 25, official Minecraft 26.3 / NeoForge
compiled dependencies or a graphical Minecraft runtime. No D14 JDK 25 Gradle
build, proper JUnit XML, release JAR, Minecraft GUI screenshot, dedicated
server load/save test, multiplayer test, FPS/TPS benchmark, or Owner approval
is claimed.

See `docs/D14_THREE_DIMENSIONAL_BENCH.md` for the exact physical validity
bounds, playable layout instructions, and acceptance steps.
