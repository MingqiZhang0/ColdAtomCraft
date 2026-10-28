"""D21B integration guards: static checks, NOT NeoForge runtime evidence."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge'

def read(path): return path.read_text(encoding='utf-8')

class WorkstationContractTest(unittest.TestCase):
    def test_menu_registered_and_client_screen_wired(self):
        blocks=read(SRC/'preview/PreviewBlocks.java')
        screen=read(SRC/'client/PowerLoggerScreen.java')
        self.assertIn('MenuType<PowerLoggerMenu>',blocks)
        self.assertIn('PowerLoggerMenu::new',blocks)
        self.assertIn('RegisterMenuScreensEvent',screen)
        self.assertIn('event.register(PreviewBlocks.LOGGER_MENU.get()',screen)
        self.assertIn('AbstractContainerScreen<PowerLoggerMenu>',screen)

    def test_block_opens_authoritative_workstation(self):
        block=read(SRC/'preview/DiagnosticPowerLoggerBlock.java')
        be=read(SRC/'preview/PowerLoggerBlockEntity.java')
        self.assertIn('serverPlayer.openMenu(logger)',block)
        self.assertIn('implements MenuProvider',be)
        self.assertIn('new PowerLoggerMenu(id, inventory, this)',be)
        self.assertIn('recording = false;',be) # reload cannot auto-enable

    def test_menu_enforces_authority_and_no_raw_file_reads(self):
        menu=read(SRC/'preview/PowerLoggerMenu.java')
        self.assertIn('instanceof ServerLevel server',menu)
        self.assertIn('instanceof ServerPlayer',menu)
        self.assertIn('!stillValid(player)',menu)
        self.assertIn('level.getBlockEntity(pos) == logger',menu)
        self.assertIn('player.distanceToSqr(',menu)
        self.assertIn('player.level() == level',menu)
        self.assertIn('logger.export(server)',menu)
        self.assertNotIn('Files.read',menu)
        self.assertNotIn('java.nio.file.',menu)
        self.assertNotIn('setBlockAndUpdate',menu)
        self.assertIn('nextExportTick',menu)

    def test_graph_preserves_invalid_samples_and_uses_game_tick_axis(self):
        screen=read(SRC/'client/PowerLoggerScreen.java')
        self.assertIn('sample.valid()',screen)
        self.assertIn('PowerTrace.MAX_CONNECTED_TICK_GAP',screen)
        self.assertIn('previous = null',screen)
        self.assertIn('selected.gameTick()',screen)
        self.assertNotIn('DiagnosticBeamScan',screen)
        self.assertNotIn('getWorldPath(',screen)
        self.assertIn('BUTTON_RANGE',screen)
        self.assertIn('BUTTON_REFRESH',screen)

    def test_wire_codec_is_signed_short_lossless_and_bounded(self):
        code=read(ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement/PowerTraceMenuCodec.java')
        menu=read(SRC/'preview/PowerLoggerMenu.java')
        self.assertIn('WORDS_PER_SAMPLE = 9',code)
        self.assertIn('Double.doubleToRawLongBits',code)
        self.assertIn('Double.longBitsToDouble',code)
        self.assertIn('& 0xffff',code)
        self.assertIn('PowerTraceMenuCodec.checksum',menu)
        self.assertIn('if (!pageReady()) return List.of()',menu)
        self.assertIn('PowerTracePage.PAGE_SIZE',menu)
        self.assertIn('snapshotLastTick != logger.recentLastTick()',menu)

    def test_locale_parity_and_asset_compatibility(self):
        path=ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft/lang'
        en=json.loads(read(path/'en_us.json'))
        zh=json.loads(read(path/'zh_cn.json'))
        self.assertEqual(set(en),set(zh))
        self.assertIn('gui.coldatomcraft.logger.title',en)
        for name in ('diagnostic_laser','diagnostic_power_meter','diagnostic_power_logger'):
            self.assertIn('block.coldatomcraft.'+name,en)

    def test_legacy_asset_regeneration_keeps_workstation_translations(self):
        generator=read(ROOT/'tools/generate_d21_assets.py')
        en=json.loads(read(ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft/lang/en_us.json'))
        self.assertIn('D21B_WORKSTATION_TRANSLATIONS',generator)
        for key in (k for k in en if k.startswith('gui.coldatomcraft.logger.')):
            self.assertIn(repr(key),generator)

    def test_ci_and_distribution_include_workstation(self):
        ci=read(ROOT/'.github/workflows/ci.yml')
        inspector=read(ROOT/'tools/inspect_mod_jar.py')
        self.assertIn('python tools/check_d21b_ui.py',ci)
        self.assertIn('PowerLoggerScreen.class',inspector)
        self.assertIn('PowerLoggerMenu.class',inspector)
        self.assertIn('PowerTraceMenuCodec.class',inspector)

if __name__=='__main__': unittest.main(verbosity=2)
