"""Test evidence parser without invoking Gradle or claiming game validation."""
from pathlib import Path
import sys
import shutil
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from verify_m0 import junit_evidence, missing_junit_suites, MINIMUM_JUNIT_TEST_COUNT


class EvidenceParserTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[1] / 'build' / 'python-test-tmp' / uuid.uuid4().hex
        self.root.mkdir(parents=True)
        self.addCleanup(lambda: shutil.rmtree(self.root, ignore_errors=True))

    def write_xml(self, module, filename, *, tests, failures=0, errors=0, skipped=0):
        folder = self.root / module / 'build/test-results/test'
        folder.mkdir(parents=True, exist_ok=True)
        (folder / filename).write_text(
            f'<testsuite tests="{tests}" failures="{failures}" '
            f'errors="{errors}" skipped="{skipped}"/>', encoding='utf-8')

    def test_empty_results_must_not_look_like_success(self):
        self.assertEqual({'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'files': 0},
                         junit_evidence(self.root))

    def test_reports_from_both_modules_are_counted(self):
        self.write_xml('physics-core', 'TEST-Physics.xml', tests=9)
        self.write_xml('experiment-core', 'TEST-Experiment.xml',
                       tests=MINIMUM_JUNIT_TEST_COUNT - 9)
        counts = junit_evidence(self.root)
        self.assertEqual(MINIMUM_JUNIT_TEST_COUNT, counts['tests'])
        self.assertEqual(2, counts['files'])
        self.assertEqual(0, counts['failures'])

    def test_failures_errors_and_skips_are_not_ignored(self):
        self.write_xml('experiment-core', 'TEST-Bad.xml', tests=6, failures=1, errors=2, skipped=1)
        counts = junit_evidence(self.root)
        self.assertEqual((6, 1, 2, 1),
                         (counts['tests'], counts['failures'], counts['errors'], counts['skipped']))

    def test_missing_suite_is_detected_even_when_total_test_count_is_high(self):
        src = self.root / 'physics-core/src/test/java/example/JonesMatrixTest.java'
        src.parent.mkdir(parents=True)
        src.write_text('class JonesMatrixTest {}', encoding='utf-8')
        self.write_xml('physics-core','TEST-Unrelated.xml',tests=MINIMUM_JUNIT_TEST_COUNT)
        self.assertEqual(['physics-core:example.JonesMatrixTest'], missing_junit_suites(self.root))

    def test_each_expected_test_source_must_have_matching_junit_xml(self):
        for module, cls in [('physics-core','example.JonesMatrixTest'),
                            ('experiment-core','example.WorldEffectLedgerTest')]:
            src=self.root/module/'src/test/java'/Path(*cls.split('.')).with_suffix('.java')
            src.parent.mkdir(parents=True,exist_ok=True)
            src.write_text('class Test {}',encoding='utf-8')
            self.write_xml(module, 'TEST-'+cls+'.xml', tests=1)
        self.assertEqual([], missing_junit_suites(self.root))

    def test_wrong_xml_element_is_rejected(self):
        folder = self.root / 'physics-core/build/test-results/test'
        folder.mkdir(parents=True, exist_ok=True)
        (folder / 'TEST-Wrong.xml').write_text('<not-testsuite/>', encoding='utf-8')
        with self.assertRaises(ValueError):
            junit_evidence(self.root)


if __name__ == '__main__':
    unittest.main()
