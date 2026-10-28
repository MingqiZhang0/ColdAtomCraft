"""D22D read-only continuous SI geometric integration; no false motor claims."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PHYS = ROOT / 'physics-core/src/main/java/dev/coldatomcraft/physics/optics/FinitePlaneGaussianFlux.java'
FRAME = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion/LaboratoryMountFrame.java'
PREDICT = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion/StageSurfacePrediction.java'


class D22DContinuousGeometryContract(unittest.TestCase):
    def test_all_sources_and_formal_junit_tests_exist(self):
        for path in (PHYS, FRAME, PREDICT,
                     ROOT/'physics-core/src/test/java/dev/coldatomcraft/physics/optics/FinitePlaneGaussianFluxTest.java',
                     ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/LaboratoryMountFrameTest.java',
                     ROOT/'verification/D22D-geometry/ContinuousGeometrySmoke.java',
                     ROOT/'verification/D22D-geometry/GeometryDemo.java'):
            self.assertTrue(path.is_file(), str(path))
        self.assertIn('org.junit.jupiter.api.Test',
                      (ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/LaboratoryMountFrameTest.java').read_text())

    def test_reference_stays_separate_from_authentic_power_samples(self):
        all_source = ''.join(path.read_text(encoding='utf-8') for path in (PHYS,FRAME,PREDICT))
        for forbidden in ('net.minecraft.', 'net.neoforged.',
                          'PowerTrace.Sample', 'setBlockAndUpdate(',
                          'getBlockEntity(', 'server.send', 'enableLaser(',
                          'reserveWatts('):
            # The type name may occur in DOCUMENTATION as an explicit prohibition.
            stripped = '\n'.join(line for line in all_source.splitlines()
                                 if not line.lstrip().startswith(('*', '/**', '//')))
            self.assertNotIn(forbidden, stripped)
        self.assertIn('REFERENCE_ONLY', all_source)
        self.assertNotIn('new PowerTrace.Sample', all_source)

    def test_integer_anchor_subtraction_precedes_float_conversion(self):
        frame = FRAME.read_text(encoding='utf-8')
        self.assertIn('Math.subtractExact(block.x(), originBlock.x())',frame)
        self.assertIn('Math.subtractExact(block.y(), originBlock.y())',frame)
        self.assertIn('Math.subtractExact(block.z(), originBlock.z())',frame)
        self.assertIn('nanometre representation',frame)

    def test_axis_miss_is_not_treated_as_no_gaussian_power(self):
        source=PHYS.read_text(encoding='utf-8')
        self.assertIn('axisIntersectsDisk',source)
        self.assertIn('GaussianCircularAperture.capture',source)
        self.assertIn('OBLIQUE_PARAXIAL_QUADRATURE',source)
        self.assertIn('projected flux',source.lower())

    def test_tilted_flux_has_physical_domain_and_convergence_checks(self):
        source=PHYS.read_text(encoding='utf-8')
        for marker in ('MAX_PARAXIAL_DIVERGENCE','MAX_AXIAL_SHEAR_OVER_ZR',
                       'MAX_APERTURE_OVER_SPOT','MIN_FORWARD_COSINE',
                       'integration failed convergence budget',
                       'flux exceeds source power'):
            self.assertIn(marker,source)

    def test_existing_cac_22c_and_d14_semantics_unchanged(self):
        d22c=(ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion/ContinuousBenchScanReference.java').read_text()
        d14=(ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticalBenchWorld.java').read_text()
        self.assertIn('GaussianCircularAperture.Capture footprint',d22c)
        self.assertIn('PreviewOpticalNetwork3D.solve',d14)

    def test_ci_has_offline_and_formal_gates(self):
        workflow=(ROOT/'.github/workflows/ci.yml').read_text(encoding='utf-8')
        self.assertIn('python tools/check_d22d_geometry.py', workflow)
        self.assertIn(':physics-core:test :experiment-core:test :neoforge-mod:build',workflow)
        self.assertIn('java-version: \'25\'',workflow)

    def test_research_document_names_instrumentation_limits(self):
        notes=(ROOT/'docs/D22D_LOCAL_GEOMETRY_AND_TILTED_DETECTOR.md').read_text(encoding='utf-8')
        for key in ('REFERENCE_ONLY','encoder', 'not', 'Gauss', 'Simpson', 'NeoForge'):
            self.assertIn(key.lower(),notes.lower())


if __name__ == '__main__': unittest.main(verbosity=2)
