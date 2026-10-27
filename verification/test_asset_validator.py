"""Tests for the asset metadata contract; uses Python standard library only."""
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import unittest
import uuid

ROOT = Path(__file__).parents[1]
SCRIPT = ROOT / 'tools' / 'validate_assets.py'
spec = importlib.util.spec_from_file_location('cac_asset_validation', SCRIPT)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)
EXAMPLE = json.loads((Path(__file__).parents[1] / 'assets' / 'manifests' /
                      'laser_source.example.json').read_text(encoding='utf-8'))


def make_glb(document=None):
    """Return a structurally valid minimal GLB 2.0 containing a JSON chunk."""
    document = document or {"asset": {"version": "2.0", "generator": "ColdAtomCraft test"}}
    payload = json.dumps(document).encode('utf-8')
    payload += b' ' * ((4 - len(payload) % 4) % 4)
    total = 12 + 8 + len(payload)
    return (struct.pack('<4sII', b'glTF', 2, total)
            + struct.pack('<I4s', len(payload), b'JSON') + payload)


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.manifest = copy.deepcopy(EXAMPLE)
        self.folder = ROOT / 'build' / 'python-test-tmp' / uuid.uuid4().hex
        self.folder.mkdir(parents=True)

    def tearDown(self):
        shutil.rmtree(self.folder, ignore_errors=True)

    def validate(self):
        return validator.validate_manifest(self.manifest, self.folder)

    def test_reference_is_spec_only(self):
        self.assertIn('SPEC ONLY', self.validate())

    def test_malicious_json_types_fail_as_manifest_errors(self):
        for key, bad in [('status', []), ('blenderUnitMeters', True), ('anchors', 'x')]:
            m = copy.deepcopy(EXAMPLE)
            m[key] = bad
            with self.assertRaises(validator.ManifestError):
                validator.validate_manifest(m, self.folder)

        m = copy.deepcopy(EXAMPLE)
        m['anchors'][0]['role'] = []
        with self.assertRaises(validator.ManifestError):
            validator.validate_manifest(m, self.folder)
        m = copy.deepcopy(EXAMPLE)
        m['anchors'][0]['forward'] = []
        with self.assertRaises(validator.ManifestError):
            validator.validate_manifest(m, self.folder)

    def test_wrong_schema(self):
        self.manifest['schemaVersion'] = 2
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_blender_axes_must_be_explicit(self):
        self.manifest['axes']['up'] = '+Y'
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_duplicate_anchor(self):
        self.manifest['anchors'].append(self.manifest['anchors'][0])
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_invalid_or_unsafe_paths(self):
        self.manifest['source'] = '../laser_source.blend'
        with self.assertRaises(validator.ManifestError): self.validate()
        self.manifest['source'] = 'laser_source.blend'
        self.manifest['interchange'] = '/tmp/payload.glb'
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_invalid_coordinates(self):
        self.manifest['anchors'][0]['localPositionMeters'] = [0, float('nan'), 0]
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_invalid_device_id(self):
        self.manifest['deviceId'] = 'LaserSource'
        with self.assertRaises(validator.ManifestError): self.validate()

    def prepare_ready(self):
        self.manifest['status'] = 'ready_for_integration'
        self.manifest['interchangeAxes'] = {'up': '+Y', 'forward': '+Z'}
        (self.folder / 'laser_source.blend').write_bytes(b'BLENDER-v300')
        (self.folder / 'laser_source.glb').write_bytes(make_glb())
        for anchor in self.manifest['anchors']:
            anchor['localPositionMeters'] = [0, 0, 0]
        self.manifest['anchors'][0]['forwardVectorLocal'] = [0, -1, 0]

    def test_ready_requires_real_files_and_coordinates(self):
        self.manifest['status'] = 'ready_for_integration'
        with self.assertRaises(validator.ManifestError): self.validate()
        self.prepare_ready()
        self.assertEqual('READY', self.validate())

    def test_ready_requires_interchange_axes_and_optical_vector(self):
        self.prepare_ready()
        del self.manifest['interchangeAxes']
        with self.assertRaises(validator.ManifestError): self.validate()
        self.prepare_ready()
        del self.manifest['anchors'][0]['forwardVectorLocal']
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_ready_rejects_impostor_blend_header(self):
        self.prepare_ready()
        (self.folder / 'laser_source.blend').write_text('not blender')
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_ready_rejects_malformed_glb_chunks(self):
        self.prepare_ready()
        (self.folder / 'laser_source.glb').write_bytes(
            struct.pack('<4sII', b'glTF', 2, 20) + b'12345678')
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_anchor_prefix_must_match_role(self):
        self.manifest['anchors'][1]['role'] = 'optical_output'
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_forward_axis_and_vector_must_agree(self):
        self.manifest['anchors'][0]['forwardVectorLocal'] = [0, 0, 1]
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_optical_forward_must_be_unit(self):
        self.manifest['anchors'][0]['forwardVectorLocal'] = [2, 0, 0]
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_bad_numeric_unit(self):
        self.manifest['blenderUnitMeters'] = 0
        with self.assertRaises(validator.ManifestError): self.validate()

    def test_optical_output_requires_forward(self):
        del self.manifest['anchors'][0]['forward']
        with self.assertRaises(validator.ManifestError): self.validate()


if __name__ == '__main__':
    unittest.main()
