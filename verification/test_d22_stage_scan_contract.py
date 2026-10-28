"""D22A release guards; static contract checks do not claim in-game movement."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOTION = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion'


class D22StageScanContract(unittest.TestCase):
    def test_stage_scan_uses_si_axes_and_no_minecraft_dependencies(self):
        for name in ('StageScanPlan.java','StageScanController.java','StageScanDataset.java'):
            self.assertTrue((MOTION/name).is_file())
            body = (MOTION/name).read_text(encoding='utf-8')
            self.assertNotIn('net.minecraft.', body)
            self.assertNotIn('net.neoforged.', body)
            self.assertNotIn('setBlockAndUpdate(', body)
            self.assertNotIn('hurt(', body)
        plan = (MOTION/'StageScanPlan.java').read_text(encoding='utf-8')
        self.assertIn('NANOMETRE, NANORADIAN', plan)
        self.assertIn('MAX_POINTS = 128', plan)

    def test_feedback_is_separate_from_command_and_requires_stopped(self):
        controller = (MOTION/'StageScanController.java').read_text(encoding='utf-8')
        self.assertIn('long encoderUnits', controller)
        self.assertIn('boolean motionStopped', controller)
        self.assertIn('boolean interlockApproved', controller)
        self.assertIn('feedback.motionStopped()', controller)
        self.assertIn('revision != feedback.revision()', controller)
        self.assertIn('tick != lastTick + 1', controller)
        self.assertIn('Action.STOP', controller)
        self.assertNotIn('setBlock(',controller)

    def test_scan_preserves_invalid_readings_and_encoder_value(self):
        plot = (MOTION/'StageScanDataset.java').read_text(encoding='utf-8')
        self.assertIn('sample.status()', plot)
        self.assertIn('encoder_', plot)
        self.assertIn('point.meanActualUnits()', plot)
        self.assertIn('if (!reading.sample().valid()) return OptionalDouble.empty()', plot)
        self.assertIn('if (sample.valid()) csv.append', plot)

    def test_d22_is_exercised_by_ci_and_formal_junit_source(self):
        workflow = (ROOT/'.github/workflows/ci.yml').read_text(encoding='utf-8')
        self.assertIn('python tools/check_d22_stage_scan.py', workflow)
        java_test = ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/StageScanControllerTest.java'
        self.assertTrue(java_test.is_file())
        self.assertIn('org.junit.jupiter.api.Test',java_test.read_text(encoding='utf-8'))

    def test_no_fake_automatic_game_motor_integration_claimed(self):
        docs = (ROOT/'docs/D22_STAGE_SCAN_REFERENCE.md').read_text(encoding='utf-8')
        self.assertIn('NOT integrated with Minecraft motor blocks',docs)
        self.assertIn('No automatic laser ON',docs)


if __name__ == '__main__':
    unittest.main(verbosity=2)
