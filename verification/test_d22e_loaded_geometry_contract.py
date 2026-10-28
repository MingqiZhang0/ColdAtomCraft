"""D22E integration gates; static checks are NOT a NeoForge runtime proof."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / 'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview'
CORE = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion'

def text(path):
    return path.read_text(encoding='utf-8')

class D22ELoadedGeometryContract(unittest.TestCase):
    def test_pure_snapshot_has_no_minecraft_or_power_mutation(self):
        content = text(CORE/'LoadedApparatusSnapshot.java')
        for forbidden in ('import net.minecraft.', 'import net.neoforged.',
                          'new PowerTrace.Sample(', 'setBlockAndUpdate(',
                          'sendBlockUpdated(', 'hurt(', 'commitReservation('):
            self.assertNotIn(forbidden, content)
        for required in ('READ_ONLY_SERVER_SNAPSHOT', 'BLOCK_ENTITY_INSTANCE',
                         'BLOCKSTATE_ONLY', 'TICK_CHANGED', 'SERVER_CHANGED',
                         'DIMENSION_CHANGED', 'CHUNK_UNLOADED', 'BLOCK_ENTITY_REPLACED'):
            self.assertIn(required, content)

    def test_adapter_server_only_and_does_not_force_chunk_load(self):
        content = text(PREVIEW/'LoadedLabGeometryWorld.java')
        self.assertIn('ServerLevel', content)
        self.assertIn('getServer().isSameThread()', content)
        self.assertIn('level.hasChunkAt(pos)', content)
        self.assertIn('level.getBlockState(pos)', content)
        self.assertIn('level.getBlockEntity(pos)', content)
        self.assertNotIn('level.getChunk(', content)
        self.assertNotIn('level.getChunkAt(', content)
        self.assertNotIn('setBlockAndUpdate(', content)
        self.assertNotIn('sendBlockUpdated(', content)
        self.assertNotIn('getSavedData(', content)
        self.assertIn('dimension().identifier().toString()', content)

    def test_all_game_observables_and_orientation_semantics(self):
        content = text(PREVIEW/'LoadedLabGeometryWorld.java')
        for required in ('DiagnosticLaserBlock.FACING', 'DiagnosticLaserBlock.ENABLED',
                         'configurationValid()', 'laser.tuning()',
                         'DiagnosticPowerMeterBlock.FACING', 'DiagnosticPowerMeterBlock.APERTURE',
                         'ports.incoming()', 'ports.selector()',
                         'Kind.OPTICAL_TABLE', 'Kind.AUTO_PRESET_STAGE'):
            self.assertIn(required, content)
        self.assertIn('PORT is incoming LIGHT TRAVEL', content)
        self.assertNotIn('getHorizontalDirection().getOpposite()', content)

    def test_existing_optical_table_now_exposes_read_only_diagnostic(self):
        registry = text(PREVIEW/'PreviewBlocks.java')
        table = text(PREVIEW/'DiagnosticOpticalTableBlock.java')
        self.assertIn('DiagnosticOpticalTableBlock::new', registry)
        self.assertIn('"diagnostic_optical_table"', registry)
        self.assertIn('LoadedLabGeometryWorld.captureNominal', table)
        self.assertIn('LoadedLabGeometryWorld.verify', table)
        self.assertIn('useWithoutItem(', table)
        self.assertNotIn('setBlockAndUpdate(', table)
        self.assertNotIn('DiagnosticLaserBlock.ENABLED', table)

    def test_auto_scan_reacquires_and_verifies_per_tick(self):
        content = text(PREVIEW/'AutoPresetStageBlockEntity.java')
        self.assertGreaterEqual(content.count('LoadedLabGeometryWorld.captureNominal'),2)
        self.assertGreaterEqual(content.count('LoadedLabGeometryWorld.verify'),2)
        self.assertIn('server.getBlockEntity(worldPosition) == this',content)
        self.assertIn('if (valid)',content)
        self.assertIn('server.hasChunkAt(mounted)',content)
        self.assertNotIn('enableLaser(',content)

    def test_model_does_not_forge_true_authentication_for_stateless_blocks(self):
        model = text(CORE/'LoadedApparatusSnapshot.java')
        self.assertIn('stateless', model.lower())
        self.assertIn('authenticatedHardwarePose() { return false; }',model)
        self.assertIn('worldIdentity',model)
        self.assertIn('if (reader.gameTick() != tick)',model)
        self.assertIn('current.blockEntityIdentity() != item.observed().blockEntityIdentity()',model)

    def test_ci_and_formal_test_are_present(self):
        ci = text(ROOT/'.github/workflows/ci.yml')
        for script in ('check_d22e_live_geometry.py','check_d22e_game_adapter.py',
                       'check_d22e_junit_source.py'):
            self.assertIn('python tools/'+script,ci)
            self.assertTrue((ROOT/'tools'/script).is_file())
        self.assertIn(':physics-core:test :experiment-core:test :neoforge-mod:build',ci)
        junit = text(ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/LoadedApparatusSnapshotTest.java')
        self.assertIn('org.junit.jupiter.api.Test',junit)
        self.assertGreaterEqual(junit.count('@Test'),18)
        inspector = text(ROOT/'tools/inspect_mod_jar.py')
        for class_name in ('LoadedApparatusSnapshot.class', 'LoadedLabGeometryWorld.class',
                           'DiagnosticOpticalTableBlock.class'):
            self.assertIn(class_name, inspector)

    def test_document_clearly_labels_model_limitations(self):
        doc=text(ROOT/'docs/D22E_LOADED_LAB_SNAPSHOT.md')
        for key in ('not', 'BlockEntity', 'BLOCKSTATE_ONLY', 'one tick',
                    'NeoForge', 'not a measurement', 'Create'):
            self.assertIn(key.lower(),doc.lower())

if __name__=='__main__':unittest.main()
