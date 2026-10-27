#!/usr/bin/env python3
"""Fail-closed *content* inspection of a built NeoForge JAR (not a runtime test).

Uses the actual Gradle-compiled output directories as an independent class
inventory, rather than checking only a small list of classes chosen in 2026.
The command does NOT establish that Minecraft can load the JAR.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import struct
import sys
import tomllib
from zipfile import ZipFile, BadZipFile

CORE_MODULES = ("physics-core", "experiment-core", "neoforge-mod")
REQUIRED = (
    "META-INF/neoforge.mods.toml",
    "dev/coldatomcraft/neoforge/ColdAtomCraft.class",
    "dev/coldatomcraft/experiment/world/ExperimentRunScheduler.class",
    "dev/coldatomcraft/experiment/world/ExperimentReservation.class",
    "dev/coldatomcraft/experiment/world/WorldEffectLedger.class",
    "dev/coldatomcraft/physics/Vec3.class",
    "dev/coldatomcraft/experiment/PreviewOpticalBench.class",
    "dev/coldatomcraft/experiment/PreviewBeamRoute.class",
    "dev/coldatomcraft/experiment/PreviewBeamDisplay.class",
    "dev/coldatomcraft/neoforge/client/DiagnosticBeamParticles.class",
    "dev/coldatomcraft/neoforge/preview/PreviewBlocks.class",
    "dev/coldatomcraft/experiment/LaserTuning.class",
    "dev/coldatomcraft/experiment/PreviewBeamColour.class",
    "dev/coldatomcraft/neoforge/preview/LaserSettingsBlockEntity.class",
    "dev/coldatomcraft/neoforge/preview/LaserControlMenu.class",
    "dev/coldatomcraft/neoforge/client/LaserControlScreen.class",
    "dev/coldatomcraft/neoforge/client/LaserControlScreen$Register.class",
    "dev/coldatomcraft/neoforge/preview/DiagnosticLaserBlock.class",
    "dev/coldatomcraft/neoforge/preview/DiagnosticPowerMeterBlock.class",
    "dev/coldatomcraft/neoforge/preview/DiagnosticBeamScan.class",
    "assets/coldatomcraft/blockstates/diagnostic_laser.json",
    "assets/coldatomcraft/blockstates/diagnostic_power_meter.json",
    "assets/coldatomcraft/models/block/diagnostic_laser_off.json",
    "assets/coldatomcraft/models/block/diagnostic_laser_on.json",
    "assets/coldatomcraft/models/block/diagnostic_power_meter_0.json",
    "assets/coldatomcraft/models/block/diagnostic_power_meter_1.json",
    "assets/coldatomcraft/models/block/diagnostic_power_meter_2.json",
    "assets/coldatomcraft/items/diagnostic_laser.json",
    "assets/coldatomcraft/items/diagnostic_power_meter.json",
    "assets/coldatomcraft/lang/en_us.json",
    "assets/coldatomcraft/lang/zh_cn.json",
    "data/coldatomcraft/loot_table/blocks/diagnostic_laser.json",
    "data/coldatomcraft/loot_table/blocks/diagnostic_power_meter.json",
)
FORBIDDEN = (
    "dev/coldatomcraft/verification/Smoke.class",
    "dev/coldatomcraft/experiment/world/FakeWorldState.class",
    "org/junit/jupiter/api/Test.class",
    "OfflineTestRunner.class",
)
EXPECTED_CLASS_MAJOR_JAVA25 = 69


@dataclass
class Inspection:
    status: str
    sha256: str
    class_count: int
    module_class_counts: dict[str, int]
    findings: list[str]


def props_from(root: Path) -> dict[str, str]:
    out = {}
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def inspect(jar_path: Path, root: Path, *, require_compiled: bool = True) -> Inspection:
    findings: list[str] = []
    counts: dict[str, int] = {}
    sha = hashlib.sha256(jar_path.read_bytes()).hexdigest()
    props = props_from(root)
    try:
        with ZipFile(jar_path) as archive:
            members = archive.namelist()
            names = set(members)
            if len(names) != len(members):
                findings.append("duplicate ZIP entry paths")
            for required in REQUIRED:
                if required not in names:
                    findings.append(f"missing required entry: {required}")
            for forbidden in FORBIDDEN:
                if forbidden in names:
                    findings.append(f"unexpected test/probe entry: {forbidden}")
            for module in CORE_MODULES:
                compiled = root / module / "build" / "classes" / "java" / "main"
                if not compiled.is_dir():
                    if require_compiled:
                        findings.append(f"missing Gradle compiled output: {module}")
                    counts[module] = 0
                    continue
                classes = sorted(compiled.rglob("*.class"))
                counts[module] = len(classes)
                if not classes:
                    findings.append(f"empty compiled output: {module}")
                for classfile in classes:
                    name = classfile.relative_to(compiled).as_posix()
                    if name not in names:
                        findings.append(f"compiled class missing from JAR: {module}/{name}")
            classfiles = [n for n in names if n.endswith(".class")]
            for name in classfiles:
                data = archive.read(name)
                if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                    findings.append(f"invalid classfile magic: {name}")
                elif struct.unpack(">H", data[6:8])[0] != EXPECTED_CLASS_MAJOR_JAVA25:
                    major = struct.unpack(">H", data[6:8])[0]
                    findings.append(f"unexpected classfile major {major} (expected 69 for Java 25): {name}")
            if "META-INF/neoforge.mods.toml" in names:
                try:
                    metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
                    mods = metadata.get("mods")
                    if not isinstance(mods, list) or not any(
                        m.get("modId") == props["mod_id"] and m.get("version") == props["mod_version"]
                        for m in mods if isinstance(m, dict)
                    ):
                        findings.append("mod ID/version do not match gradle.properties")
                    dependencies = metadata.get("dependencies", {}).get(props["mod_id"], [])
                    if not {"minecraft", "neoforge"}.issubset(
                        {d.get("modId") for d in dependencies if isinstance(d, dict)}
                    ):
                        findings.append("minecraft / neoforge dependencies missing from metadata")
                except (ValueError, UnicodeError, KeyError, TypeError, AttributeError) as exc:
                    findings.append(f"invalid mod metadata: {exc}")
            return Inspection("PASS" if not findings else "FAIL", sha, len(classfiles), counts, findings)
    except (BadZipFile, OSError, RuntimeError) as exc:
        return Inspection("FAIL", sha, 0, counts, [f"cannot read JAR: {exc}"])


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jar", required=True, type=Path)
    p.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    p.add_argument("--allow-missing-build-outputs", action="store_true",
                   help="history/diagnostic ONLY; disallowed for formal D4 release validation")
    p.add_argument("--report", type=Path)
    args = p.parse_args(argv)
    try:
        result = inspect(args.jar, args.root,
                         require_compiled=not args.allow_missing_build_outputs)
    except (OSError, KeyError) as exc:
        print(f"JAR INSPECTION FAIL: {exc}", file=sys.stderr)
        return 1
    report = json.dumps(asdict(result), indent=2, ensure_ascii=False) + "\n"
    print(report, end="")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")
    return 0 if result.status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
