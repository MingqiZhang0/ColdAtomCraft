"""D21C static integration contracts; does not substitute for Minecraft runtime."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD = ROOT / 'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge'
EXP = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement'


def read(path): return path.read_text(encoding='utf-8')


class HistoryContract(unittest.TestCase):
    def test_archive_is_bounded_and_never_interprets_bad_rows_as_zero(self):
        source = read(EXP/'PowerTraceHistory.java')
        for text in ('MAX_QUERY_BYTES = 16 * 1024','MAX_ROW_BYTES = 128',
                     'StandardOpenOption.READ','LinkOption.NOFOLLOW_LINKS',
                     'new IOException("invalid archive CSV sample"',
                     'PowerTrace.Status.MEASURED'):
            self.assertIn(text, source)
        self.assertNotIn('StandardOpenOption.WRITE', source)
        self.assertNotIn('StandardOpenOption.CREATE', source)

    def test_history_is_bound_to_the_real_recorder_not_a_client_path(self):
        be = read(MOD/'preview/PowerLoggerBlockEntity.java')
        menu = read(MOD/'preview/PowerLoggerMenu.java')
        self.assertIn('PowerTraceHistory.newest(root(server), safeId(server))', be)
        self.assertIn('PowerTraceHistory.older(root(server), safeId(server)', be)
        self.assertIn('PowerTraceHistory.newer(root(server), safeId(server)', be)
        self.assertIn('!stillValid(player)', menu)
        self.assertIn('player.level() == level', menu)
        self.assertIn('level.getBlockEntity(pos) == logger', menu)
        self.assertIn('PowerTraceHistory.Page', menu)
        self.assertNotIn('Files.read', menu)
        self.assertNotIn('java.nio.file.', menu)

    def test_history_does_not_modify_manual_laser_or_physical_solver(self):
        screen = read(MOD/'client/PowerLoggerScreen.java')
        menu = read(MOD/'preview/PowerLoggerMenu.java')
        self.assertIn('BUTTON_HISTORY', screen)
        self.assertIn('VIEW_HISTORY', screen)
        self.assertIn('historyPage', menu)
        self.assertIn('nextHistoryTick', menu)
        self.assertIn('PowerTraceMenuCodec.checksum', menu)
        self.assertNotIn('DiagnosticBeamScan', screen)
        self.assertNotIn('setBlockAndUpdate', menu)

    def test_languages_survive_regeneration(self):
        en = json.loads(read(ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft/lang/en_us.json'))
        zh = json.loads(read(ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft/lang/zh_cn.json'))
        gen = read(ROOT/'tools/generate_d21_assets.py')
        self.assertEqual(set(en),set(zh))
        for suffix in ('show_history','show_recent','archive_page','archive_note','history_error'):
            key = 'gui.coldatomcraft.logger.'+suffix
            self.assertIn(key,en)
            self.assertIn(repr(key),gen)

    def test_ci_and_distribution_include_the_reader(self):
        ci = read(ROOT/'.github/workflows/ci.yml')
        jar = read(ROOT/'tools/inspect_mod_jar.py')
        self.assertIn('python tools/check_d21c_history.py',ci)
        self.assertIn('PowerTraceHistory.class',jar)
        self.assertTrue((ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/measurement/PowerTraceHistoryTest.java').is_file())

if __name__=='__main__': unittest.main(verbosity=2)
