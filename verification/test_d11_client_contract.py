"""Static regression guard for D11 preview rendering (not a compiled NeoForge test)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/client/DiagnosticBeamParticles.java'
TRACE = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/PreviewBeamDisplay.java'
GRADLE = ROOT / 'neoforge-mod/build.gradle'

class PreviewBeamClientContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.render = CLIENT.read_text(encoding='utf-8')
        cls.trace = TRACE.read_text(encoding='utf-8')
        cls.gradle = GRADLE.read_text(encoding='utf-8')

    def test_subscriber_is_client_only(self):
        self.assertIn('@EventBusSubscriber(modid = ColdAtomCraft.MOD_ID, value = Dist.CLIENT)',self.render)
        self.assertIn('ClientTickEvent.Post',self.render)
        self.assertNotIn('ServerLevel',self.render)

    def test_source_state_remains_authoritative(self):
        self.assertIn('state.getValue(DiagnosticLaserBlock.ENABLED)',self.render)
        self.assertIn('state.getValue(DiagnosticLaserBlock.FACING)',self.render)
        self.assertNotIn('setBlockAndUpdate(',self.render)
        self.assertNotIn('sendParticles(',self.render)

    def test_ray_does_not_force_chunk_loads(self):
        self.assertIn('level.hasChunkAt(next)',self.render)
        self.assertIn('PreviewBeamDisplay.Cell.UNLOADED',self.render)
        self.assertNotIn('getChunk(',self.render)

    def test_detector_face_is_recognized(self):
        self.assertIn('ahead.is(PreviewBlocks.METER.get())',self.render)
        self.assertIn('facing.getOpposite()',self.render)
        self.assertIn('return PreviewBeamDisplay.Cell.RECEIVER;',self.render)

    def test_existing_gaussian_core_is_reused(self):
        self.assertIn('GaussianBeam',self.trace)
        self.assertIn('BEAM.radiusMeters',self.trace)
        self.assertIn('displayedRadiusBlocks',self.trace)

    def test_particles_have_bounded_radius_and_local_scan(self):
        self.assertIn('MAX_VISIBLE_LASERS = 3',self.render)
        self.assertIn('SCAN_RADIUS_HORIZONTAL = 16',self.render)
        self.assertIn('REFRESH_EVERY_TICKS = 4',self.render)

    def test_d11_core_and_visual_classes_required_in_jar(self):
        self.assertIn('dev/coldatomcraft/experiment/PreviewBeamDisplay.class',self.gradle)
        self.assertIn('dev/coldatomcraft/neoforge/client/DiagnosticBeamParticles.class',self.gradle)

    def test_visualization_is_not_an_instrument_calibration(self):
        self.assertIn('false-colour',self.render)
        self.assertIn('No ray is created on the server',self.render)

if __name__ == '__main__':
    unittest.main()
