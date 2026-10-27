# CAC-D10 — Placeholder Device Interaction Playtest

> **Status:** SOURCE IMPLEMENTED / GAME RUNTIME NOT VERIFIED ON CURRENT HOST.
> This document is a test plan, not a claim that Minecraft 26.3 has run D10.
> All blocks are **DEV PREVIEW**; not the Owner's Blender models or approved equipment.

## 0. Prerequisites

- Windows with a working **JDK 25** (`java -version` and `javac -version` must both say 25), Gradle dependency access and Minecraft 26.3 / NeoForge 26.3.0.99.
- Start with an expendable *new creative test world*, **not** an existing valuable save.
- D10 is a changed source version: **do not use any D9/R2 packaged JAR** to infer D10 behavior.
- This checkpoint is currently **unbuilt** in the local container because it only has JDK 21 and no access to Gradle/NeoForge dependency hosts. Run the commands and inspect results; any failure is an unverified issue to fix, not evidence of success.

Windows PowerShell from the extracted project root:

```powershell
java -version
javac -version
python .\tools\verify_m0.py
.\gradlew.bat :neoforge-mod:runClient
```

If `runClient` is absent, inspect `./gradlew.bat tasks --all` and the MDK 26.3 run configuration; do not substitute an old JAR. The verification script runs *build and JUnit*, not the game launch.

## 1. First load and appearance

1. Launch the dev client and create a new **Creative** world.
2. In creative inventory, find **CAC 实验交互测试（开发） / CAC Diagnostic Lab (DEV)**.
3. Find both **测试激光器（占位模型）** and **测试光功率计（占位模型）**.
4. Verify each shows a blocky **3D provisional model**, not purple/black missing textures. The emitter has an indicator that changes from grey to red when enabled; the meter has a coloured preset knob.
5. For deterministic alignment, use the following test commands **without moving between commands**:

```mcfunction
/setblock ~1 ~ ~ coldatomcraft:diagnostic_laser[facing=east,enabled=true]
/setblock ~5 ~ ~ coldatomcraft:diagnostic_power_meter[facing=west,aperture=0]
```

Clear any obstruction in the three intermediate cells. Commands require operator/cheats permission. Note: for adjacent placements, the actual model bodies are blocks; the optical port anchors are 7/16 m from the block centres.

## 2. Click interactions and expected values

Use an **empty hand** on the equipment. Readouts appear in the **action bar** and are **not** persistent HUD elements.

| Test | Action | Expected server-authoritative result |
|---|---|---|
| P01 | Right-click meter with source ON and clear 4-block separation | ideal power **~3.42960 mW**, distance **4 blocks** |
| P02 | Shift + right-click meter once, then measure | preset **1.0 mm**, **~10.57585 mW** |
| P03 | Shift + right-click again, then measure | preset **2.0 mm**, **~19.01399 mW** |
| P04 | Right-click emitter to turn OFF, then measure | shutter closed; **no numeric positive reading** |
| P05 | Right-click emitter back ON; Shift + right-click to rotate | emitter changes facing, meter says source orientation wrong |
| P06 | Rotate back, put any non-air block between the instruments | optical route blocked; no numeric positive reading |
| P07 | Remove blockage, measure again | valid measured result returns |
| P08 | Move meter 4 more blocks along axis; repeat at same 0.5 mm aperture | power **decreases** with distance |
| P09 | Exit/re-enter the test world | enabled state, facing and aperture index persist as blockstates |
| P10 | Test after breaking the source | no source / obstruction; **no stale reading** |

For controls: emitter **right-click** toggles power, **Shift + right-click** rotates clockwise. Receiver **right-click** samples, **Shift + right-click** cycles the 0.5 / 1.0 / 2.0 mm aperture radius. Re-place the receiver to change its front direction.

## 3. What exactly is simulated?

| Quantity | Current DEV-only assumption |
|---|---|
| Source power | 20 mW continuous-wave Gaussian |
| Vacuum wavelength | 780 nm |
| Waist radius | 0.5 mm |
| Waist position | exactly the *placeholder* emitter output port |
| World scale | 1 Minecraft block = 1 metre, for this preview ONLY |
| Model ports | 7/16 m from block centre, matching provisional cuboid faces |
| Received quantity | ideal circular-aperture collected power [W] from D9 mathematics |
| Timestamp | static diagnostic sample, `simulatedTimeNanos = 0` |
| World interaction | readonly scan of loaded blocks, **no energy deposition** |

This test does **not** model material absorption, reflection/refraction, arbitrary alignment, electrical responsivity, instrument noise, polarization, coherent multi-source beam combining or damage to Minecraft entities and blocks. Any non-air intervening block is a conservative **obstacle**; we do not invent glass transmission parameters.

## 4. Acceptance boundary

**Can be checked in code now:** JSON references/state coverage, pure Java fixture calculations, route failure-state logic, numeric power-budget checks.

**Must be checked on a JDK25/NeoForge host:** block registration, inventory placement, correct 3D model rendering, physical click routing, two logical sides, state synchronization, chunk-load handling, world save/re-entry, packaged JAR independent instance.

Record test world version, mod JAR SHA-256, screenshot of each model (ON/OFF), screenshot of action bar, and `latest.log` if something fails. Do not mark in-game interactions PASS until observed on a real Minecraft 26.3 instance.
