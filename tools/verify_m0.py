#!/usr/bin/env python3
"""Opt-in, non-destructive JDK25 release-build evidence runner.

Does NOT accept a Minecraft EULA, launch an independent production server,
mutate an installed world, push to GitHub, or claim M0/Owner signoff.
Exit 0: build/JUnit/JAR-content checks passed (NOT a runtime certificate).
Exit 1: one attempted check failed. Exit 2: preflight missing prerequisite.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import platform
import re
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

from inspect_mod_jar import inspect

MINIMUM_JUNIT_TEST_COUNT = 344  # D13 331 methods + 12 D14 optics + 1 D14 corruption regression
GRADLE_TASKS = (
    "clean", ":physics-core:test", ":experiment-core:test",
    ":neoforge-mod:build", ":neoforge-mod:verifyDistributableJar",
    "--no-build-cache", "--rerun-tasks", "--no-daemon", "--stacktrace",
)


def junit_evidence(root: Path) -> dict:
    flat = sorted(path for module in ("physics-core", "experiment-core")
                  for path in (root / module / "build/test-results/test").glob("TEST-*.xml"))
    counts = dict(tests=0, failures=0, errors=0, skipped=0, files=len(flat))
    for path in flat:
        tree = ET.parse(path)
        report = tree.getroot()
        if report.tag != "testsuite":
            raise ValueError(f"JUnit XML is not a <testsuite>: {path}")
        for key in ("tests", "failures", "errors", "skipped"):
            counts[key] += int(report.attrib.get(key, "0"))
    return counts


def missing_junit_suites(root: Path) -> list[str]:
    """Require XML evidence for every currently present top-level *Test.java class.

    Counts alone are insufficient: unrelated tests could hide a missing new
    optics test suite after a misconfigured source set or Gradle filter.
    """
    missing: list[str] = []
    for module in ("physics-core", "experiment-core"):
        sources = root / module / "src/test/java"
        results = root / module / "build/test-results/test"
        for source in sorted(sources.rglob("*Test.java")):
            class_name = ".".join(source.relative_to(sources).with_suffix("").parts)
            if not (results / f"TEST-{class_name}.xml").is_file():
                missing.append(f"{module}:{class_name}")
    return missing


def run(name: str, argv: list[str], root: Path, evidence: Path) -> dict:
    log = evidence / (name + ".log")
    start = time.monotonic()
    print(f"RUN {name}: {' '.join(argv)}", flush=True)
    try:
        with log.open("wb") as stream:
            result = subprocess.run(argv, cwd=root, stdout=stream,
                                    stderr=subprocess.STDOUT, check=False)
        status = "PASS" if result.returncode == 0 else "FAIL"
        return dict(status=status, exit_code=result.returncode, log=str(log.relative_to(evidence)),
                    seconds=round(time.monotonic() - start, 3))
    except OSError as exc:
        log.write_text(f"Cannot start command: {exc}\n", encoding="utf-8")
        return dict(status="BLOCKED", exit_code=None, log=str(log.relative_to(evidence)),
                    reason=str(exc))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--evidence", type=Path,
                        help="directory for local logs, default <root>/verification/local-jdk25/<timestamp>")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    evidence = (args.evidence or (root / "verification/local-jdk25" /
                                  (stamp + "-" + uuid.uuid4().hex[:8]))).resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    report = dict(
        source_root=str(root),
        timestamp_utc=stamp,
        os=platform.platform(),
        scope="D14 3D optics / D13 budget / D12 GUI / build / JUnit / JAR verification only; NOT game startup or Owner scientific approval",
        checks={},
        actual_installed_jar_runtime="NOT RUN",
        minecraft_client_runtime="NOT RUN",
        minecraft_dedicated_server_runtime="NOT RUN",
        actual_blender_export="NOT RUN",
    )

    def save() -> None:
        (evidence / "report.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("Evidence report:", evidence / "report.json", flush=True)

    try:
        for name, command in (("java-version", ["java", "-version"]),
                              ("javac-version", ["javac", "-version"])):
            check = run(name, command, root, evidence)
            report["checks"][name] = check
            if check["status"] != "PASS":
                report["status"] = "BLOCKED"
                save()
                return 2
        compiler_version = (evidence / "javac-version.log").read_text(
            encoding="utf-8", errors="replace")
        runtime_version = (evidence / "java-version.log").read_text(
            encoding="utf-8", errors="replace")
        compiler25 = re.search(r"\bjavac\s+25(?:\.|\s|$)", compiler_version)
        runtime25 = re.search(r'\b(?:openjdk|java) version "25(?:\.|"|\s)', runtime_version)
        if not compiler25 or not runtime25:
            report["checks"]["jdk25-toolchain"] = dict(
                status="BLOCKED", reason="both java and javac must be Java 25",
                java=runtime_version.strip(), javac=compiler_version.strip())
            report["status"] = "BLOCKED"
            save()
            return 2
        report["checks"]["jdk25-toolchain"] = dict(
            status="PASS", java=runtime_version.strip(), javac=compiler_version.strip())

        if sys.platform == "win32":
            smoke = ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                     str(root / "tools/verify-core.ps1")]
            gradle = ["cmd", "/c", str(root / "gradlew.bat")]
        else:
            smoke = ["bash", "tools/verify-core.sh"]
            gradle = ["bash", "gradlew"]
        steps = (
            ("java25-smoke", smoke),
            ("optics-high-precision-oracle", [sys.executable, "verification/M0-R2.1-D5/verify_optics_reference.py"]),
            ("coherent-high-precision-oracle", [sys.executable, "verification/M0-R2.1-D6/verify_coherent_reference.py"]),
            ("optical-path-high-precision-oracle", [sys.executable, "verification/M0-R2.1-D7/verify_optical_path_reference.py"]),
            ("fresnel-high-precision-oracle", [sys.executable, "verification/M0-R2.1-D8/verify_fresnel_reference.py"]),
            ("aperture-high-precision-oracle", [sys.executable, "verification/M0-R2.1-D9/verify_detector_reference.py"]),
            ("preview-beam-geometry", [sys.executable, "tools/check_beam_visual.py"]),
            ("multi-beam-budget", [sys.executable, "tools/check_multi_beam.py"]),
            ("preview-asset-tests", [sys.executable, "tools/test_preview_assets.py"]),
            ("python-tests", [sys.executable, "-m", "unittest", "discover",
                              "-s", "verification", "-p", "test_*.py", "-v"]),
            ("asset-schema", [sys.executable, "tools/validate_assets.py",
                              "assets/manifests/laser_source.example.json"]),
            ("gradle-version", gradle + ["--version"]),
            ("gradle-clean-build", gradle + list(GRADLE_TASKS)),
        )
        for name, command in steps:
            check = run(name, command, root, evidence)
            report["checks"][name] = check
            if check["status"] != "PASS":
                report["status"] = "FAIL" if check["status"] == "FAIL" else "BLOCKED"
                save()
                return 1 if report["status"] == "FAIL" else 2

        junit = junit_evidence(root)
        junit["missing_required_suites"] = missing_junit_suites(root)
        junit["status"] = "PASS" if (junit["tests"] >= MINIMUM_JUNIT_TEST_COUNT and
                                      junit["failures"] == junit["errors"] == junit["skipped"] == 0 and
                                      junit["files"] >= 2 and
                                      not junit["missing_required_suites"]) else "FAIL"
        report["checks"]["real-junit-xml"] = junit
        if junit["status"] != "PASS":
            report["status"] = "FAIL"
            save()
            return 1

        jars = sorted((root / "neoforge-mod/build/libs").glob("coldatomcraft-*.jar"))
        if len(jars) != 1:
            report["checks"]["distributable-jar"] = dict(
                status="FAIL", reason=f"expected one distributable JAR, found {len(jars)}")
            report["status"] = "FAIL"
            save()
            return 1
        inspection = inspect(jars[0], root, require_compiled=True)
        report["checks"]["distributable-jar"] = dict(
            status=inspection.status, sha256=inspection.sha256,
            path=str(jars[0].relative_to(root)), entries_checked=inspection.class_count,
            compiled_class_counts=inspection.module_class_counts,
            findings=inspection.findings)
        report["status"] = "PASS" if inspection.status == "PASS" else "FAIL"
        save()
        return 0 if inspection.status == "PASS" else 1
    except Exception as exc:
        report["checks"]["runner-error"] = dict(status="FAIL", error=repr(exc))
        report["status"] = "FAIL"
        save()
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
