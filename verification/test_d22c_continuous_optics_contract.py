"""Static D22C architecture checks; do not claim game or motor integration."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion/ContinuousBenchScanReference.java'


class D22CReferenceContract(unittest.TestCase):
    def test_proven_existing_optical_primitives_and_local_frame_used(self):
        code = FILE.read_text(encoding='utf-8')
        for name in ('GaussianBeam', 'IdealCircularDetectorFace',
                     'GaussianCircularAperture', 'JonesMatrix.idealLinearPolarizer',
                     'JonesMatrix.halfWave', 'RayFrame3'):
            self.assertIn(name, code)
        self.assertIn('beam.waistPositionLabMeters()', code)
        self.assertIn('frame.xAxis()', code)
        self.assertIn('frame.yAxis()', code)

    def test_no_fake_authoritative_server_or_actuator(self):
        code = FILE.read_text(encoding='utf-8')
        for prohibited in ('net.minecraft.', 'net.neoforged.', 'setBlockAndUpdate(',
                           'setBlock(', 'getBlockState(', 'enableLaser(', 'reserveWatts('):
            self.assertNotIn(prohibited, code)
        self.assertIn('REFERENCE_ONLY', code)
        self.assertIn('PredictedPoint', code)
        self.assertNotIn('PowerTrace.Sample', code)

    def test_d22c_offline_and_formal_junit_are_in_ci(self):
        ci = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
        self.assertIn('python tools/check_d22c_continuous_optics.py', ci)
        test = ROOT / 'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/ContinuousBenchScanReferenceTest.java'
        self.assertIn('org.junit.jupiter.api.Test', test.read_text(encoding='utf-8'))
        self.assertIn(':experiment-core:test', ci)
        self.assertIn(':neoforge-mod:build', ci)

    def test_old_d22a_smoke_does_not_glob_future_motion_sources(self):
        checker = (ROOT / 'tools/check_d22_stage_scan.py').read_text(encoding='utf-8')
        self.assertNotIn("glob('*.java')", checker)
        self.assertIn('StageScanController.java', checker)
        self.assertIn('StageScanDataset.java', checker)

    def test_documentary_scientific_boundaries_are_explicit(self):
        docs = (ROOT / 'docs/D22C_CONTINUOUS_LAB_REFERENCE.md').read_text(encoding='utf-8')
        self.assertIn('REFERENCE_ONLY', docs)
        self.assertIn('motor', docs)
        self.assertIn('NOT represented', docs)
        self.assertIn('PENDING', docs)
