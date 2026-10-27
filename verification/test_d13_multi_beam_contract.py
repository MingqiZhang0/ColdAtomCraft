"""D13 fair multi-beam particle budget must survive D14 segment routing."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
CLIENT=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/client/DiagnosticBeamParticles.java'
ADAPTER=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticalBenchWorld.java'
POLICY=ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/PreviewBeamParticleBudget.java'
CI=ROOT/'.github/workflows/ci.yml'

class MultiBeamContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client=CLIENT.read_text(encoding='utf-8')
        cls.adapter=ADAPTER.read_text(encoding='utf-8')
        cls.policy=POLICY.read_text(encoding='utf-8')
        cls.ci=CI.read_text(encoding='utf-8')
    def test_no_fixed_laser_limit(self):
        self.assertNotIn('MAX_VISIBLE_LASERS',self.client)
        self.assertNotIn('if (++active',self.client)
        self.assertIn('var sources = OpticalBenchWorld.sourcesNear(',self.client)
        self.assertIn('sources.add(new Source(',self.adapter)
    def test_particle_budget_bounds_and_fairness(self):
        self.assertIn('DEFAULT_PARTICLES_PER_REFRESH = 1200',self.policy)
        self.assertIn('particleCost(result) <= budget',self.policy)
        self.assertIn('refreshIndex % n',self.policy)
        self.assertIn('visible = Math.min(n, budget / 3)',self.policy)
        self.assertIn('if (steps[i] > 0) draw(',self.client)
    def test_d12_server_physics_untouched(self):
        self.assertNotIn('setBlockAndUpdate(',self.client)
        self.assertNotIn('sendParticles(',self.client)
        self.assertIn('state.getValue(DiagnosticLaserBlock.ENABLED)',self.adapter)
        self.assertIn('LaserSettingsBlockEntity source',self.adapter)
        self.assertIn('level.hasChunkAt(worldPos)',self.adapter)
    def test_ci_runs_smoke_and_packages_core(self):
        self.assertIn('python tools/check_multi_beam.py',self.ci)
        self.assertIn('D14-optical-bench-neoforge',self.ci)
        self.assertIn('PreviewBeamParticleBudget.class',
                      (ROOT/'tools/inspect_mod_jar.py').read_text(encoding='utf-8'))

if __name__=='__main__': unittest.main()
