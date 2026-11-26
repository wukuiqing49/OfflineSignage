import tempfile
import unittest
from pathlib import Path

from audit_keep_rules import audit_project


class AuditKeepRulesTest(unittest.TestCase):
    def test_framework_annotation_hierarchy_and_member_filters_are_not_package_keeps(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "consumer-rules.pro").write_text(
                "-keep @androidx.annotation.Keep class * { *; }\n"
                "-keepclassmembers class ** { @androidx.lifecycle.OnLifecycleEvent *; }\n"
                "-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }\n"
                "-keep,allowshrinking,allowoptimization,allowobfuscation class com.google.android.gms.** { *; }\n"
                "-keepnames class * implements com.example.ReflectedParcelable\n"
                "-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer\n",
                encoding="utf-8",
            )
            self.assertEqual(audit_project(root)["findings"], [])

    def test_partial_soft_modifier_still_reports_package_restrictions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "consumer-rules.pro").write_text(
                "-keep,allowoptimization class com.example.sdk.** { *; }\n"
                "-keep,allowoptimization,allowshrinking,allowobfuscation class com.example.free.** { *; }\n",
                encoding="utf-8",
            )
            findings = audit_project(root)["findings"]
            self.assertEqual(len(findings), 1)
            self.assertEqual(findings[0]["rule_id"], "BROAD_PACKAGE_KEEP")

    def test_merged_rules_include_transitive_and_exact_class_findings(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = root / "build" / "configuration.txt"
            config.parent.mkdir()
            config.write_text(
                "-keep class com.vendor.Model { *; }\n"
                "-keep class com.vendor.Entry { public <init>(); }\n"
                "-keep class com.google.android.gms.** { *; }\n",
                encoding="utf-8",
            )
            self.assertEqual(audit_project(root)["findings"], [])
            report = audit_project(root, config)
            self.assertEqual(report["counts"], {"CRITICAL": 1, "HIGH": 0, "INFO": 1})
            self.assertIn("build/configuration.txt", report["scanned_files"])

    def test_global_single_star_keep_and_missing_configuration(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "consumer-rules.pro").write_text("-keep class * { *; }\n", encoding="utf-8")
            self.assertEqual(audit_project(root)["counts"]["CRITICAL"], 1)
            with self.assertRaises(ValueError):
                audit_project(root, root / "missing.txt")

    def test_detects_all_major_antipatterns_and_allows_narrow_soft_rules(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "app").mkdir()
            (root / "core" / "core_base").mkdir(parents=True)
            (root / "core" / "core_net").mkdir(parents=True)

            # Good narrow/soft consumer rules in core_base
            (root / "core" / "core_base" / "consumer-rules.pro").write_text(
                "\n".join(
                    [
                        "-keep,allowoptimization,allowshrinking,allowobfuscation class * extends com.example.base.BaseActivity",
                        "-keep class com.example.base.DialogKit { public <init>(); }",
                    ]
                ),
                encoding="utf-8",
            )

            # Bad global wildcard in core_net
            (root / "core" / "core_net" / "consumer-rules.pro").write_text(
                "-keep class **.model.** { *; }\n",
                encoding="utf-8",
            )

            # Host app with multiple anti-patterns
            (root / "app" / "proguard-rules.pro").write_text(
                "\n".join(
                    [
                        "-dontobfuscate",
                        "-keep class com.google.android.gms.** { *; }",
                        "-keep class com.example.base.** { *; }",
                        "-keep class com.example.feature.** { *; }",
                        "-keepnames class com.example.sdk.**",
                        # Compliant narrow rule (should not trigger)
                        "-keep class com.example.feature.ExactEntry { public <init>(); }",
                    ]
                ),
                encoding="utf-8",
            )

            report = audit_project(root)
            rule_ids = {f["rule_id"] for f in report["findings"]}

            self.assertIn("GLOBAL_WILDCARD_PACKAGE", rule_ids)
            self.assertIn("DISABLE_R8_GLOBAL_FLAG", rule_ids)
            self.assertIn("THIRD_PARTY_PACKAGE_KEEP", rule_ids)
            self.assertIn("HOST_DUPLICATES_CONSUMER_PACKAGE", rule_ids)
            self.assertIn("BROAD_PACKAGE_KEEP", rule_ids)
            self.assertIn("KEEPNAMES_OBFUSCATION_BLOCK", rule_ids)
            self.assertEqual(report["counts"]["CRITICAL"], 3)
            self.assertEqual(report["counts"]["HIGH"], 3)

            # Ensure compliant narrow & soft rules were NOT flagged
            flagged_stmts = " ".join(f["statement"] for f in report["findings"])
            self.assertNotIn("ExactEntry", flagged_stmts)
            self.assertNotIn("BaseActivity", flagged_stmts)
            self.assertNotIn("DialogKit", flagged_stmts)


if __name__ == "__main__":
    unittest.main()
