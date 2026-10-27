"""Regression tests for portable strict source integrity inventory."""
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

CHECKER = pathlib.Path(__file__).resolve().parents[1] / 'tools' / 'check_source_manifest.py'


class SourceManifestContractTests(unittest.TestCase):
    def setUp(self):
        self.context = tempfile.TemporaryDirectory(prefix='cac-manifest-')
        self.addCleanup(self.context.cleanup)
        self.root = pathlib.Path(self.context.name)
        (self.root / 'tools').mkdir()
        shutil.copyfile(CHECKER, self.root / 'tools/check_source_manifest.py')
        (self.root / '.gitignore').write_text('*.log\n__pycache__/\n', encoding='utf-8')
        (self.root / 'data.txt').write_text('baseline\n', encoding='utf-8')

    def git(self, *args, check=True):
        return subprocess.run(['git', *args], cwd=self.root,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=check, text=True)

    def run_check(self, *flags):
        return subprocess.run([sys.executable, 'tools/check_source_manifest.py', *flags],
                              cwd=self.root, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, text=True)

    def initialize(self):
        self.git('init', '-q')
        self.git('config', 'user.name', 'CAC Test')
        self.git('config', 'user.email', 'cac-test@example.invalid')
        self.git('add', 'data.txt', '.gitignore', 'tools/check_source_manifest.py')
        result = self.run_check('--mode', 'checkout', '--update')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.git('add', 'MANIFEST_SHA256.txt')
        self.git('commit', '-qm', 'baseline')
        return result

    def test_checkout_ignores_untracked_historical_logs_but_not_source(self):
        result = self.initialize()
        self.assertIn('WROTE 3 checkout SHA-256 entries', result.stdout)
        (self.root / 'verification').mkdir()
        (self.root / 'verification' / 'old-qa.log').write_text('historical artifact')
        (self.root / 'scratch.java').write_text('untracked experimentation')
        result = self.run_check('--mode', 'checkout')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_index_change_without_manifest_regeneration_fails(self):
        self.initialize()
        (self.root / 'data.txt').write_text('new staged content\n', encoding='utf-8')
        self.git('add', 'data.txt')
        result = self.run_check('--mode', 'checkout')
        self.assertEqual(result.returncode, 1)
        self.assertIn('FAIL MISMATCH: data.txt', result.stdout)

    def test_unstaged_tracked_change_fails_closed(self):
        self.initialize()
        (self.root / 'data.txt').write_text('unreviewed working tree change')
        result = self.run_check('--mode', 'checkout')
        self.assertEqual(result.returncode, 1)
        self.assertIn('unstaged changes', result.stderr)

    def test_git_canonical_blobs_support_crlf_worktree(self):
        (self.root / 'data.txt').write_bytes(b'first\r\nsecond\r\n')
        (self.root / '.gitattributes').write_text('*.txt text\n', encoding='utf-8')
        self.git('init', '-q')
        self.git('config', 'user.name', 'CAC Test')
        self.git('config', 'user.email', 'cac-test@example.invalid')
        self.git('add', '.gitattributes', '.gitignore', 'data.txt', 'tools/check_source_manifest.py')
        updated = self.run_check('--mode', 'checkout', '--update')
        self.assertEqual(updated.returncode, 0, updated.stdout + updated.stderr)
        self.git('add', 'MANIFEST_SHA256.txt')
        self.git('commit', '-qm', 'normalized')
        result = self.run_check('--mode', 'checkout')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.root / 'data.txt').read_bytes(), b'first\r\nsecond\r\n')

    def test_archive_mode_remains_strict_without_git(self):
        updated = self.run_check('--mode', 'archive', '--update')
        self.assertEqual(updated.returncode, 0, updated.stdout + updated.stderr)
        self.assertEqual(self.run_check('--mode', 'archive').returncode, 0)
        (self.root / 'unexpected.txt').write_text('new source not listed')
        result = self.run_check('--mode', 'archive')
        self.assertEqual(result.returncode, 1)
        self.assertIn('FAIL MISSING: unexpected.txt', result.stdout)

    def test_checkout_refuses_missing_git_repo(self):
        result = self.run_check('--mode', 'checkout')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('requires Git repository root', result.stderr)


if __name__ == '__main__':
    unittest.main()
