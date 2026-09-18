import importlib.util
from pathlib import Path
import subprocess
import unittest
import tempfile
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "validate_release_readiness.py"
SPEC = importlib.util.spec_from_file_location("release_readiness", SCRIPT)
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class SigningMaterialTest(unittest.TestCase):
    def check(self, output="", returncode=0):
        errors, warnings = [], []
        with patch.object(validator.shutil, "which", return_value="git"), patch.object(
            validator.subprocess, "run",
            return_value=subprocess.CompletedProcess([], returncode, output, ""),
        ):
            validator.check_signing_material(Path("."), errors, warnings)
        return errors, warnings

    def test_tracked_signing_material_blocks_release(self):
        errors, _ = self.check("app/private.jks\0keystore.properties\0")
        self.assertEqual(1, len(errors))
        self.assertIn("keystore.properties", errors[0])

    def test_untracked_local_material_does_not_block_release(self):
        self.assertEqual(([], []), self.check())

    def test_git_failure_is_not_reported_as_success(self):
        errors, _ = self.check(returncode=128)
        self.assertEqual(1, len(errors))


class TestResultsTest(unittest.TestCase):
    def reports(self, root, attributes):
        for module in ("app", "feature/feature_app", "core/core_google"):
            directory = root / module / "build/test-results/testDebugUnitTest"
            directory.mkdir(parents=True)
            (directory / "TEST-suite.xml").write_text(f"<testsuite {attributes}/>", encoding="utf-8")

    def test_missing_reports_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = []
            validator.check_test_results(Path(directory), errors)
            self.assertEqual(3, len(errors))

    def test_skipped_or_failed_tests_fail(self):
        for attributes in ('tests="2" failures="1"', 'tests="2" skipped="1"'):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.reports(root, attributes)
                errors = []
                validator.check_test_results(root, errors)
                self.assertEqual(3, len(errors))

    def test_executed_passing_tests_succeed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.reports(root, 'tests="2" failures="0" errors="0" skipped="0"')
            errors = []
            validator.check_test_results(root, errors)
            self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
