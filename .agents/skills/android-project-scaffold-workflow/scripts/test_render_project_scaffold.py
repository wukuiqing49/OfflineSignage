#!/usr/bin/env python3

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_DIR = SCRIPT_DIR.parent
AGENTS_DIR = SKILL_DIR.parent.parent
GENERATOR = SCRIPT_DIR / "render_project_scaffold.py"
BASE_CONFIG = AGENTS_DIR / "config" / "project-scaffold.yml"


def base_config_values() -> dict[str, str]:
    config = yaml.safe_load(BASE_CONFIG.read_text(encoding="utf-8"))
    package_name = config["packageName"]
    package_dir = "/".join(package_name.split("."))
    application_class = config["application"].get("applicationClassName", "")
    if not application_class:
        application_class = "LocalSignageApplication"
    return {
        "package_dir": package_dir,
        "package_name": package_name,
        "application_class": application_class,
        "version_name": config["application"]["versionName"],
        "signing_keystore": config["release"]["signingKeystoreFile"],
    }


class RenderProjectScaffoldTest(unittest.TestCase):
    def run_generator(self, config: Path, target: Path, *extra: str, expect_ok: bool = True):
        env = dict(os.environ)
        env["PYTHONUTF8"] = "1"
        result = subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--config",
                str(config),
                "--target",
                str(target),
                "--wkq-base-version",
                "v1.0.8",
                "--wkq-utils-version",
                "v0.0.4",
                *extra,
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
            env=env,
        )
        if expect_ok and result.returncode != 0:
            self.fail(result.stdout + result.stderr)
        if not expect_ok and result.returncode == 0:
            self.fail("Generator unexpectedly succeeded")
        return result

    def test_generates_complete_project_and_refuses_accidental_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "project"
            self.run_generator(BASE_CONFIG, target)
            values = base_config_values()
            app_source = f"app/src/main/java/{values['package_dir']}"

            expected = [
                "settings.gradle",
                "build.gradle",
                "gradlew",
                "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar",
                "gradle/libs.versions.toml",
                "gradle/app-conventions.gradle",
                "app-config.properties",
                "app/build.gradle",
                f"{app_source}/MainActivity.kt",
                f"{app_source}/{values['application_class']}.kt",
                "app/src/main/res/drawable/bg_scaffold_surface.xml",
                "app/src/main/res/drawable-night/bg_scaffold_surface.xml",
                "app/src/main/res/values/dimens.xml",
                "app/src/main/res/values/styles.xml",
                "app/src/main/res/values-night/colors.xml",
                "app/src/main/res/values-en/strings.xml",
                "app/src/main/res/values-zh-rCN/strings.xml",
                "core/core_base/build.gradle",
                "core/core_utils/build.gradle",
                "feature/feature_app/build.gradle",
                "feature/feature_res/build.gradle",
                "feature/feature_res/src/main/res/drawable/bg_scaffold_surface.xml",
                "feature/feature_res/src/main/res/drawable-night/bg_scaffold_surface.xml",
                "feature/feature_res/src/main/res/values/colors.xml",
                "feature/feature_res/src/main/res/values/dimens.xml",
                "feature/feature_res/src/main/res/values/strings.xml",
                "feature/feature_res/src/main/res/values/styles.xml",
                "feature/feature_res/src/main/res/values-night/colors.xml",
                "feature/feature_res/src/main/res/values-en/strings.xml",
                "feature/feature_res/src/main/res/values-zh-rCN/strings.xml",
            ]
            for relative in expected:
                self.assertTrue((target / relative).is_file(), relative)

            app_build = (target / "app/build.gradle").read_text(encoding="utf-8")
            app_conventions = (target / "gradle/app-conventions.gradle").read_text(encoding="utf-8")
            main_activity = (target / app_source / "MainActivity.kt").read_text(encoding="utf-8")
            manifest = (target / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
            main_layout = (target / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
            theme = (target / "app/src/main/res/values/themes.xml").read_text(encoding="utf-8")
            self.assertIn("Base", main_activity)
            self.assertIn("enableEdgeToEdge()", main_activity)
            self.assertIn("android:statusBarColor", theme)
            self.assertIn("android:navigationBarColor", theme)
            self.assertIn("<item name=\"android:windowLightStatusBar\">true</item>", theme)
            self.assertIn("<item name=\"android:windowLightNavigationBar\">true</item>", theme)
            self.assertIn("<item name=\"android:enforceNavigationBarContrast\">false</item>", theme)
            self.assertIn("CoreUtils.init", (target / app_source / f"{values['application_class']}.kt").read_text(encoding="utf-8"))
            self.assertIn("apply from: rootProject.file('gradle/app-conventions.gradle')", app_build)
            self.assertNotIn("storePassword", app_build)
            self.assertIn("calculatedVersionCode", app_conventions)
            self.assertEqual(2, app_conventions.count("signingConfig = signingConfigs.sharedApp"))
            self.assertIn(
                "requireReleaseSigning=true",
                (target / "app-config.properties").read_text(encoding="utf-8"),
            )
            self.assertIn('android:supportsRtl="true"', manifest)
            self.assertIn("layout_constraintStart_toStartOf", main_layout)
            self.assertIn("layout_constraintEnd_toEndOf", main_layout)
            self.assertNotRegex("\n".join(path.read_text(encoding="utf-8", errors="ignore") for path in target.rglob("*") if path.is_file() and path.suffix != ".jar"), r"\{\{[A-Z0-9_]+\}\}")

            self.run_generator(BASE_CONFIG, target, expect_ok=False)
            user_config = (target / "app-config.properties").read_text(encoding="utf-8").replace(
                f"versionName={values['version_name']}",
                "versionName=2.3.4\ncustomPublicValue=keep_me",
            )
            (target / "app-config.properties").write_text(user_config, encoding="utf-8")
            user_colors = (target / "feature/feature_res/src/main/res/values/colors.xml").read_text(encoding="utf-8").replace(
                "</resources>",
                '    <color name="business_color">#123456</color>\n</resources>',
            )
            (target / "feature/feature_res/src/main/res/values/colors.xml").write_text(user_colors, encoding="utf-8")
            self.run_generator(BASE_CONFIG, target, "--force")
            self.assertEqual(user_config, (target / "app-config.properties").read_text(encoding="utf-8"))
            self.assertEqual(user_colors, (target / "feature/feature_res/src/main/res/values/colors.xml").read_text(encoding="utf-8"))
            marker = yaml.safe_load((target / ".android-scaffold-generated.json").read_text(encoding="utf-8"))
            self.assertEqual(3, marker["schemaVersion"])
            self.assertTrue(marker["rtlEnabled"])
            self.assertTrue(marker["rtlPreferRelativeAttributes"])
            self.assertTrue(marker["rtlRequireVisualValidationForDirectionalUi"])
            self.assertEqual(
                ["app-config.properties", "keystore.properties", values["signing_keystore"]],
                marker["preservedPaths"],
            )
            self.assertFalse((target / "keystore.properties.example").exists())
            self.assertIn("feature/feature_res/src/main/res/values/colors.xml", marker["generatedDigests"])

    def test_generates_beside_project_local_agent_files(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "project"
            config_path = target / ".agents/config/project-scaffold.yml"
            config_path.parent.mkdir(parents=True)
            config_path.write_text(BASE_CONFIG.read_text(encoding="utf-8"), encoding="utf-8")
            agents_entry = target / "AGENTS.md"
            agents_entry.write_text("# Project-local Agent entry\n", encoding="utf-8")
            usage = target / "doc/AGENT_USAGE.md"
            usage.parent.mkdir(parents=True)
            usage.write_text("# Existing usage guide\n", encoding="utf-8")

            self.run_generator(config_path, target)

            self.assertEqual("# Project-local Agent entry\n", agents_entry.read_text(encoding="utf-8"))
            self.assertEqual("# Existing usage guide\n", usage.read_text(encoding="utf-8"))
            self.assertTrue(config_path.is_file())
            self.assertTrue((target / "app/build.gradle").is_file())
            self.assertTrue((target / "core/core_base/build.gradle").is_file())
            self.assertTrue((target / ".android-scaffold-generated.json").is_file())

    def test_compatibility_switches_disable_optional_outputs(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = yaml.safe_load(BASE_CONFIG.read_text(encoding="utf-8"))
            config["androidCompatibility"]["networkSecurity"]["enabled"] = False
            config["androidCompatibility"]["fileProvider"]["enabled"] = False
            config["ui"]["edgeToEdge"]["enabled"] = False
            config["release"]["signingEnvironment"] = {
                "storeFile": "CUSTOM_STORE_FILE",
                "storePassword": "CUSTOM_STORE_PASSWORD",
                "keyAlias": "CUSTOM_KEY_ALIAS",
                "keyPassword": "CUSTOM_KEY_PASSWORD",
            }
            config_path = root / "config.yml"
            config_path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
            target = root / "project"
            self.run_generator(config_path, target)

            manifest = (target / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
            app_conventions = (target / "gradle/app-conventions.gradle").read_text(encoding="utf-8")
            values = base_config_values()
            main_activity = (target / "app/src/main/java" / values["package_dir"] / "MainActivity.kt").read_text(encoding="utf-8")
            self.assertNotIn("networkSecurityConfig", manifest)
            self.assertNotIn("FileProvider", manifest)
            self.assertNotIn("enableEdgeToEdge", main_activity)
            self.assertFalse((target / "app/src/main/res/xml/network_security_config.xml").exists())
            self.assertFalse((target / "app/src/main/res/xml/file_paths.xml").exists())
            self.assertFalse((target / "keystore.properties.example").exists())
            self.assertIn("CUSTOM_STORE_FILE", app_conventions)

            enabled_target = root / "enabled-project"
            self.run_generator(BASE_CONFIG, enabled_target)
            network_path = enabled_target / "app/src/main/res/xml/network_security_config.xml"
            self.assertTrue(network_path.exists())
            customized_network = network_path.read_text(encoding="utf-8").replace(
                "</network-security-config>",
                "    <!-- business override -->\n</network-security-config>",
            )
            network_path.write_text(customized_network, encoding="utf-8")
            self.run_generator(config_path, enabled_target, "--force")
            self.assertEqual(customized_network, network_path.read_text(encoding="utf-8"))
            self.assertFalse((enabled_target / "app/src/main/res/xml/file_paths.xml").exists())

    def test_network_and_provider_values_are_config_driven(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = yaml.safe_load(BASE_CONFIG.read_text(encoding="utf-8"))
            config["androidCompatibility"]["networkSecurity"]["cleartextTrafficPermitted"] = True
            config["androidCompatibility"]["networkSecurity"]["trustSystemCertificatesOnly"] = False
            config["androidCompatibility"]["fileProvider"]["sharedSubdirectory"] = "outbound/"
            config_path = root / "config.yml"
            config_path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
            target = root / "project"
            self.run_generator(config_path, target)

            network = (target / "app/src/main/res/xml/network_security_config.xml").read_text(encoding="utf-8")
            paths = (target / "app/src/main/res/xml/file_paths.xml").read_text(encoding="utf-8")
            self.assertIn('cleartextTrafficPermitted="true"', network)
            self.assertIn('certificates src="user"', network)
            self.assertIn('path="outbound/"', paths)

    def test_resource_skeleton_is_config_driven(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = yaml.safe_load(BASE_CONFIG.read_text(encoding="utf-8"))
            config["resources"]["localizedValueDirs"] = ["values-ja", "values-zh-rTW"]
            config["resources"]["includeNightResources"] = False
            config["resources"]["includeDrawableNightResources"] = False
            config_path = root / "config.yml"
            config_path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
            target = root / "project"
            self.run_generator(config_path, target)

            self.assertTrue((target / "app/src/main/res/values-ja/strings.xml").is_file())
            self.assertTrue((target / "feature/feature_res/src/main/res/values-zh-rTW/strings.xml").is_file())
            self.assertFalse((target / "app/src/main/res/values-night/colors.xml").exists())
            self.assertFalse((target / "feature/feature_res/src/main/res/drawable-night/bg_scaffold_surface.xml").exists())

    def test_rtl_compatibility_is_required(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = yaml.safe_load(BASE_CONFIG.read_text(encoding="utf-8"))
            config["ui"]["rtl"]["enabled"] = False
            config_path = root / "config.yml"
            config_path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")

            result = self.run_generator(config_path, root / "project", expect_ok=False)
            self.assertIn("ui.rtl.enabled must be true", result.stderr)

    def test_application_class_and_source_root_can_be_overridden(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = yaml.safe_load(BASE_CONFIG.read_text(encoding="utf-8"))
            config["application"]["applicationClassName"] = "WorkflowApplication"
            config["language"]["sourceRoot"] = "src/main/kotlin"
            config_path = root / "config.yml"
            config_path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
            target = root / "project"
            self.run_generator(config_path, target)

            values = base_config_values()
            application = target / "app/src/main/kotlin" / values["package_dir"] / "WorkflowApplication.kt"
            manifest = (target / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
            self.assertTrue(application.is_file())
            self.assertIn("class WorkflowApplication", application.read_text(encoding="utf-8"))
            self.assertIn('android:name=".WorkflowApplication"', manifest)
            self.assertTrue((target / "feature/feature_app/src/main/kotlin" / values["package_dir"] / "feature/app/FeatureAppEntry.kt").is_file())


if __name__ == "__main__":
    unittest.main()
