import tempfile
import unittest
from pathlib import Path
from validate_build_output import validate_android17


class Android17GateTest(unittest.TestCase):
    def check(self, compile_sdk="37", target=37, agp="9.1.1", gradle="9.3.1", kotlin="2.2.10", flags=""):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "gradle/wrapper").mkdir(parents=True)
            (root / "gradle/libs.versions.toml").write_text(
                f'compileSdk = "{compile_sdk}"\nagp = "{agp}"\nkotlin = "{kotlin}"\n', encoding="utf-8")
            (root / "gradle/wrapper/gradle-wrapper.properties").write_text(
                f'distributionUrl=https://services.gradle.org/distributions/gradle-{gradle}-bin.zip', encoding="utf-8")
            (root / "gradle.properties").write_text(flags, encoding="utf-8")
            errors, warnings = [], []
            validate_android17(root, target, errors, warnings)
            return errors, warnings

    def test_supported_toolchain(self):
        self.assertEqual(self.check(), ([], []))

    def test_old_agp_rejected_even_when_target_not_upgraded(self):
        self.assertTrue(any("AGP >=" in x for x in self.check(target=36, agp="8.13.2")[0]))

    def test_old_gradle_rejected(self):
        self.assertTrue(any("Gradle >=" in x for x in self.check(gradle="8.13")[0]))

    def test_compile_below_target_rejected(self):
        self.assertTrue(any("below targetSdk" in x for x in self.check(compile_sdk="36")[0]))

    def test_old_kotlin_rejected(self):
        self.assertTrue(any("Kotlin >=" in x for x in self.check(kotlin="2.1.21")[0]))

    def test_half_opt_out_rejected(self):
        self.assertTrue(self.check(flags="android.builtInKotlin=false\n")[0])
        self.assertEqual(self.check(flags="android.newDsl=false\n"), ([], []))
        self.assertEqual(self.check(flags="android.builtInKotlin=false\nandroid.newDsl=false\n"), ([], []))

    def test_older_project_not_forced_to_upgrade(self):
        self.assertEqual(self.check(compile_sdk="36", target=36, agp="8.13.2", gradle="8.13"), ([], []))


if __name__ == "__main__":
    unittest.main()
