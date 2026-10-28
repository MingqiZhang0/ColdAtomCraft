"""D22B structural gates: server-only actuation, genuine D14 measurement, no auto laser ON."""
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
PRE='neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/'
def read(name): return (R/name).read_text(encoding='utf-8')

class D22BPresetIntegration(unittest.TestCase):
    def test_registered_block_entity_creative_and_server_ticker(self):
        src=read(PRE+'PreviewBlocks.java')
        block=read(PRE+'AutoPresetStageBlock.java')
        for text in ('AUTO_PRESET_STAGE = BLOCKS.registerBlock(', 'AUTO_PRESET_STAGE_BLOCK_ENTITY = BLOCK_ENTITIES.register(',
                     'AUTO_PRESET_STAGE_ITEM = ITEMS.registerSimpleBlockItem(',
                     'output.accept(AUTO_PRESET_STAGE_ITEM.get());'):
            self.assertIn(text,src)
        self.assertIn('if (level.isClientSide()',block)
        self.assertIn('stage.serverTick(world)',block)
    def test_block_entity_mutates_actual_optic_but_not_laser(self):
        src=read(PRE+'AutoPresetStageBlockEntity.java')
        self.assertIn('server.setBlockAndUpdate(mounted, target)',src)
        self.assertIn('OpticPortPreset.of(',src)
        self.assertIn('DiagnosticBeamScan.sample(server, meter)',src)
        self.assertNotIn('DiagnosticLaserBlock.ENABLED',src)
        self.assertNotIn('forceChunk',src)
        self.assertNotIn('server.getChunk(',src)
    def test_never_restart_or_auto_run_unloaded_stage(self):
        src=read(PRE+'AutoPresetStageBlockEntity.java')
        self.assertIn('scan = null;',src)
        self.assertIn('server.hasChunkAt(mounted)',src)
        self.assertIn('server.hasChunkAt(meterPos)',src)
        self.assertIn('output.putString(DATA_KEY, DiscretePresetReport.csv(lastCompleted))',src)
        self.assertNotIn('output.putInt("scanning"',src)
    def test_dataset_is_bounded_and_self_describing(self):
        scan=read('experiment-core/src/main/java/dev/coldatomcraft/experiment/motion/DiscretePresetSweep.java')
        report=read('experiment-core/src/main/java/dev/coldatomcraft/experiment/motion/DiscretePresetReport.java')
        self.assertIn('public static final int PRESETS = 4',scan)
        self.assertIn('public static final int SETTLE_TICKS = 5',scan)
        self.assertIn('NO_BEAM',read(PRE+'AutoPresetStageBlockEntity.java'))
        self.assertIn('MAX_SAVED_CHARS',report)
        self.assertIn('server_game_tick,status,watts',report)
        self.assertNotIn('net.minecraft',report+scan)
    def test_ci_and_assets_are_wired(self):
        self.assertIn('python tools/check_d22b_preset_scan.py',read('.github/workflows/ci.yml'))
        asset=read('tools/generate_d21_assets.py')
        self.assertIn('diagnostic_auto_preset_stage',asset)
        self.assertIn('diagnostic_auto_preset_stage',read('tools/test_preview_assets.py'))

if __name__=='__main__': unittest.main()
