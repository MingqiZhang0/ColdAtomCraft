"""D21 NeoForge wiring checks; static, NOT a game runtime or compiler."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview'
ASSETS = ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft'


def read(path): return path.read_text(encoding='utf-8')

class D21IntegrationContract(unittest.TestCase):
    def test_logger_is_server_only_and_ticking(self):
        src = read(PREVIEW/'DiagnosticPowerLoggerBlock.java')
        self.assertIn('implements EntityBlock', src)
        self.assertIn('getTicker(', src)
        self.assertIn('level.isClientSide()', src)
        self.assertIn('PowerLoggerBlockEntity', src)
        self.assertIn('player.isShiftKeyDown()', src)

    def test_archive_is_in_world_directory_and_bounded(self):
        src = read(PREVIEW/'PowerLoggerBlockEntity.java')
        archive = read(ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement/PowerTraceArchive.java')
        self.assertIn('getWorldPath(LevelResource.ROOT)',src)
        self.assertIn('PowerTraceArchive.append(',src)
        self.assertIn('PowerTraceArchive.exportSvg(',src)
        self.assertIn('MAX_CSV_BYTES = 64L * 1024 * 1024',archive)
        self.assertIn('recording = false;',src)
        self.assertIn('setChanged();',src)
        self.assertNotIn('level.setBlockAndUpdate(',src)

    def test_long_term_samples_use_server_calculated_detector(self):
        src = read(PREVIEW/'PowerLoggerBlockEntity.java')
        meter = read(PREVIEW/'DiagnosticBeamScan.java')
        self.assertIn('DiagnosticBeamScan.sample(level, meterPos)',src)
        self.assertIn('OpticalBenchWorld.trace(level, sources)',meter)
        self.assertIn('PowerTrace.Status.MULTIPLE_METERS',src)
        self.assertIn('PowerTrace.Status.NO_METER',src)
        self.assertIn('PowerTrace.Status.INCOMPLETE',src)
        self.assertIn('PowerTrace.Status.ADJACENT_UNLOADED',src)
        self.assertIn('SAMPLE_PERIOD_GAME_TICKS = 100',src)

    def test_manual_stages_act_on_real_optic_blocks(self):
        src = read(PREVIEW/'DiagnosticStageBlock.java')
        self.assertIn('level.setBlockAndUpdate(target,optic)',src)
        self.assertIn('level.removeBlock(from,false)',src)
        self.assertIn('level.setBlockAndUpdate(mount, changed)',src)
        self.assertIn('identity.confirmControlledChange(',src)
        self.assertIn('level.hasChunkAt(target)',src)
        self.assertIn('DiagnosticApparatusIdentityBlockEntity identity',src)
        self.assertIn('identity.identityUsable()',src)
        self.assertIn('!level.getBlockState(target).isAir()',src)
        self.assertIn('PreviewBlocks.OPTICAL_TABLE.get()',src)
        self.assertNotIn('LaserSettingsBlockEntity',src)
        self.assertNotIn('source.tuning()',src)

    def test_existing_meter_unchanged(self):
        src=read(PREVIEW/'DiagnosticPowerMeterBlock.java')
        self.assertIn('DiagnosticBeamScan.measure(',src)
        self.assertIn('PreviewOpticalBench.aperturePresetCount()',src)
        self.assertNotIn('PowerLoggerBlockEntity',src)

    def test_registration_and_localized_assets(self):
        blocks=read(PREVIEW/'PreviewBlocks.java')
        en=json.loads(read(ASSETS/'lang/en_us.json'))
        zh=json.loads(read(ASSETS/'lang/zh_cn.json'))
        self.assertEqual(set(en),set(zh))
        for name,registration in (
            ('diagnostic_power_logger','LOGGER'),('diagnostic_optical_table','TABLE'),
            ('diagnostic_linear_stage','LINEAR_STAGE'),('diagnostic_rotary_stage','ROTARY_STAGE')):
            with self.subTest(block=name):
                self.assertIn('"'+name+'"',blocks)
                self.assertIn('output.accept('+registration+'_ITEM.get());',blocks)
                self.assertIn('block.coldatomcraft.'+name,en)
                self.assertTrue((ASSETS/'blockstates'/f'{name}.json').is_file())
                self.assertTrue((ASSETS/'items'/f'{name}.json').is_file())
                self.assertTrue((ASSETS/'models/block'/f'{name}.json').is_file())

    def test_ci_runs_measurement_smoke_and_inspects_jar(self):
        ci=read(ROOT/'.github/workflows/ci.yml')
        inspect=read(ROOT/'tools/inspect_mod_jar.py')
        self.assertIn('python tools/check_d21_measurement.py',ci)
        self.assertIn('PowerLoggerBlockEntity.class',inspect)
        self.assertIn('PowerTraceArchive.class',inspect)

if __name__=='__main__': unittest.main(verbosity=2)
