"""D21D contract checks, not a Minecraft runtime replacement."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
M=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge'
EXP=ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement'

def read(path):return path.read_text(encoding='utf-8')

class D21DComparisonContract(unittest.TestCase):
    def test_second_instrument_is_server_chosen_and_bounded(self):
        menu=read(M/'preview/PowerLoggerMenu.java')
        self.assertIn('nearbyRecorders(ServerLevel server)',menu)
        self.assertIn('for (int dx = -6; dx <= 6; dx++)',menu)
        self.assertIn('server.hasChunkAt(target)',menu)
        self.assertIn('viewer.distanceToSqr(',menu)
        self.assertIn('viewer.level() != level',menu)
        self.assertIn('server.getBlockEntity(secondaryPos) != secondary',menu)
        self.assertIn('secondaryBound()',menu)
        self.assertIn('!stillValid(player)',menu)
        self.assertNotIn('requestPath',menu)
        self.assertNotIn('Files.read',menu)
        self.assertNotIn('setBlockAndUpdate',menu)

    def test_two_pages_share_checksum_and_keep_history_single_channel(self):
        menu=read(M/'preview/PowerLoggerMenu.java')
        self.assertIn('SECONDARY_START',menu)
        self.assertIn('SECONDARY_VISIBLE_COUNT',menu)
        self.assertIn('PowerTraceMenuCodec.checksum',menu)
        self.assertIn('comparisonSamples()',menu)
        self.assertIn('PowerTraceComparison.secondaryWindow(visible, secondaryFiltered)',menu)
        self.assertIn('if (!historical && comparing) clearComparison();',menu)
        self.assertIn('if (!pageReady() || data.get(COMPARE_ACTIVE) != 1)',menu)
        self.assertIn('PowerTraceHistory.Page',menu)

    def test_gui_uses_original_server_samples_only(self):
        screen=read(M/'client/PowerLoggerScreen.java')
        self.assertIn('drawComparison(',screen)
        self.assertIn('PowerTraceComparison.frame(a,b)',screen)
        self.assertIn('sample.gameTick()',screen)
        self.assertIn('sample.valid()',screen)
        self.assertIn('previous = null',screen)
        self.assertIn('BUTTON_COMPARE',screen)
        self.assertIn('BUTTON_NEXT_CHANNEL',screen)
        self.assertNotIn('DiagnosticBeamScan',screen)
        self.assertNotIn('Files.read',screen)

    def test_export_does_not_rewrite_original_csv(self):
        archive=read(EXP/'PowerTraceArchive.java')
        self.assertIn('exportComparisonPage(',archive)
        self.assertIn('.compare-page.csv',archive)
        self.assertIn('.compare-page.svg',archive)
        self.assertIn('Files.isSymbolicLink(file)',archive)
        self.assertIn('PowerTraceComparison.csv(frame)',archive)
        self.assertIn('PowerTraceComparison.svg(frame)',archive)
        self.assertIn('MAX_CSV_BYTES',archive)

    def test_both_languages_survive_placeholder_regeneration(self):
        generator=read(ROOT/'tools/generate_d21_assets.py')
        en=json.loads(read(ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft/lang/en_us.json'))
        zh=json.loads(read(ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft/lang/zh_cn.json'))
        self.assertEqual(set(en),set(zh))
        for suffix in ('compare_off','compare_on','compare_next','compare_note'):
            key='gui.coldatomcraft.logger.'+suffix
            self.assertIn(key,en)
            self.assertIn(key,zh)
            self.assertIn(repr(key),generator)

    def test_ci_and_jar_verification_include_d21d(self):
        ci=read(ROOT/'.github/workflows/ci.yml')
        jar=read(ROOT/'tools/inspect_mod_jar.py')
        self.assertIn('python tools/check_d21d_comparison.py',ci)
        self.assertIn('PowerTraceComparison.class',jar)
        self.assertTrue((EXP/'PowerTraceComparison.java').is_file())
        self.assertTrue((ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/measurement/PowerTraceComparisonTest.java').is_file())

if __name__=='__main__':unittest.main(verbosity=2)
