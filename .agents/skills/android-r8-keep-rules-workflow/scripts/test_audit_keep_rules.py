import tempfile
import unittest
from pathlib import Path

from audit_keep_rules import audit_project


class AuditKeepRulesTest(unittest.TestCase):
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
