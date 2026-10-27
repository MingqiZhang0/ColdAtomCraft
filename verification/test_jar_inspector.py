"""Synthetic metadata/classfile tests; NOT a NeoForge installed-JAR check."""
from __future__ import annotations

from pathlib import Path
import shutil
import uuid
import sys
import unittest
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from inspect_mod_jar import REQUIRED, inspect


MOD_METADATA = '''license="All Rights Reserved"
[[mods]]
modId="coldatomcraft"
version="0.0.1-dev"
[[dependencies.coldatomcraft]]
modId="minecraft"
type="required"
versionRange="[26.3]"
[[dependencies.coldatomcraft]]
modId="neoforge"
type="required"
versionRange="[26.3.0.99,)"
'''


def fake_class(major: int = 69) -> bytes:
    return b"\xca\xfe\xba\xbe" + b"\x00\x00" + major.to_bytes(2, "big")


class JarInspectorTest(unittest.TestCase):
    def setUp(self):
        # Match existing asset tests: Windows sandbox temporary directories may
        # have a restrictive ACL even when the project workspace is writable.
        self.root = Path(__file__).resolve().parents[1] / 'build' / 'python-test-tmp' / uuid.uuid4().hex
        self.root.mkdir(parents=True)
        self.addCleanup(lambda: shutil.rmtree(self.root, ignore_errors=True))
        (self.root / "gradle.properties").write_text(
            "mod_id=coldatomcraft\nmod_version=0.0.1-dev\n", encoding="utf-8")
        self.jar = self.root / "mod.jar"
        self.classes = {}
        for module, filename in [
            ("physics-core", "dev/coldatomcraft/physics/Vec3.class"),
            ("experiment-core", "dev/coldatomcraft/experiment/world/ExperimentRunScheduler.class"),
            ("neoforge-mod", "dev/coldatomcraft/neoforge/ColdAtomCraft.class"),
        ]:
            path = self.root / module / "build/classes/java/main" / filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(fake_class())
            self.classes[module] = filename

    def make_jar(self, *, omit=None, major=69, additional=None, metadata=MOD_METADATA):
        with ZipFile(self.jar, "w") as z:
            for name in REQUIRED:
                if name == omit:
                    continue
                z.writestr(name, metadata if name.endswith(".toml") else fake_class(major))
            for name, contents in (additional or {}).items():
                z.writestr(name, contents)

    def test_valid_compiled_inventory_and_metadata_pass(self):
        self.make_jar()
        self.assertEqual("PASS", inspect(self.jar, self.root).status)

    def test_missing_required_class_fails(self):
        self.make_jar(omit="dev/coldatomcraft/physics/Vec3.class")
        self.assertTrue(any("missing" in x for x in inspect(self.jar, self.root).findings))

    def test_missing_individual_compiled_class_fails(self):
        self.make_jar()
        extra = self.root / "experiment-core/build/classes/java/main/dev/coldatomcraft/experiment/world/FutureClass.class"
        extra.write_bytes(fake_class())
        self.assertTrue(any("FutureClass" in x for x in inspect(self.jar, self.root).findings))

    def test_wrong_java_version_fails(self):
        self.make_jar(major=65)
        self.assertTrue(any("major 65" in x for x in inspect(self.jar, self.root).findings))

    def test_forbidden_test_class_fails(self):
        self.make_jar(additional={"OfflineTestRunner.class": fake_class()})
        self.assertTrue(any("probe" in x for x in inspect(self.jar, self.root).findings))

    def test_invalid_mod_version_fails(self):
        self.make_jar(metadata=MOD_METADATA.replace('version="0.0.1-dev"', 'version="incorrect"'))
        self.assertTrue(any("ID/version" in x for x in inspect(self.jar, self.root).findings))

    def test_missing_build_outputs_fails_in_formal_mode(self):
        self.make_jar()
        (self.root / "physics-core/build/classes/java/main/dev/coldatomcraft/physics/Vec3.class").unlink()
        # Directory still exists and contains no files.
        self.assertTrue(any("empty compiled" in x for x in inspect(self.jar, self.root).findings))


if __name__ == "__main__":
    unittest.main()
