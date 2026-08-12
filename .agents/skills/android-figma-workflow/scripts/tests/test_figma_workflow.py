from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace


SCRIPTS_DIR = Path(__file__).resolve().parents[1]


def load_module(name: str, filename: str):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS_DIR / filename)
    if not spec or not spec.loader:
        raise RuntimeError(f"无法加载测试模块: {filename}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


figma_sync = load_module("figma_sync", "figma_sync.py")
validator = load_module("validate_figma_output", "validate_figma_output.py")


class FigmaSyncTest(unittest.TestCase):
    def test_parse_design_url(self) -> None:
        file_key, node_id = figma_sync.parse_figma_url(
            "https://www.figma.com/design/abc123/demo?node-id=181-1386"
        )
        self.assertEqual("abc123", file_key)
        self.assertEqual("181:1386", node_id)


class ValidatorTest(unittest.TestCase):
    def test_dimension_contract_expands_width_height_shorthand(self) -> None:
        report = """
| 用途 | Android dp/sp | dimen/style 名 |
| --- | --- | --- |
| 关注按钮 | 35 x 14dp | `home_follow_button_min_width/height` |
"""
        contracts = validator.extract_dimen_contracts(report)
        self.assertEqual(
            [("home_follow_button_min_width", 35.0), ("home_follow_button_min_height", 14.0)],
            [(item["name"], item["min"]) for item in contracts],
        )

    def test_strong_validation_requires_local_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = SimpleNamespace(
                require_screen_adaptation=True,
                figma_output_dir=str(root),
                analysis_report=None,
                reference_screenshot=None,
            )
            errors: list[str] = []
            validator.validate_reference_artifacts(args, errors)
            self.assertTrue(any("figma_screenshot" in error for error in errors))
            self.assertTrue(any("figma_asset_index.json" in error for error in errors))

    def test_unconfirmed_state_downgrade_is_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "page_task_home.md"
            manifest = root / "asset_manifest_home.md"
            report.write_text(
                "\n".join(
                    [
                        "- 是否允许降级：否",
                        "- 用户确认的降级方案：无",
                        "- 是否存在缺失导航 / Tab / 工具栏图标：是",
                    ]
                ),
                encoding="utf-8",
            )
            manifest.write_text(
                "\n".join(
                    [
                        "| 节点名称 | Android 文件名 | 是否必需 | 是否已落地 | 处理建议 |",
                        "| --- | --- | --- | --- | --- |",
                        "| 首页 | ic_home_selected.png | 是 | 已落地 | 缺 normal 状态 |",
                    ]
                ),
                encoding="utf-8",
            )
            args = SimpleNamespace(asset_manifest=str(manifest), analysis_report=str(report))
            errors: list[str] = []
            validator.validate_manifest_semantics(args, errors)
            self.assertGreaterEqual(len(errors), 2)


if __name__ == "__main__":
    unittest.main()
