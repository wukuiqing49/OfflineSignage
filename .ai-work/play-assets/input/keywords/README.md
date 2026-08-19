# 关键词工具结果输入目录

将 Keyword Planner、ASO 平台或其他关键词研究工具导出的原始 CSV/XLSX 放在本目录。

## 流程

1. 使用 Skill 生成 10 个 `en-US` 英文关键词；`output/strategy/seed-keywords.md` 只展示关键词，`output/strategy/keyword-research-input.csv` 用于工具交接。
2. 将 Seed Keywords 提交给关键词工具。
3. 将工具原始导出文件放入本目录。
4. 填写同目录的 `keyword-research-metadata.json`，记录工具、目标市场、Locale、导出日期和指标定义。
5. 再次调用 `$plan-google-play-listing-assets` 生成关键词分析；结合当前项目 VERIFIED Product Facts 完成逐词语义复核后，再生成素材总策略、封面/截图/视频 Brief，以及对应的独立 Prompt 文件。

## 文件要求

- CSV 推荐 UTF-8 或 UTF-8 with BOM；支持 XLSX。
- 保留原始表头、工作表和指标，不覆盖原文件。
- 文件名建议为 `<tool>-<locale>-<YYYYMMDD>.csv`，例如 `keyword-planner-en-US-20260814.csv`。
- 记录工具、市场、语言和导出日期。
- `keyword-research-metadata.json` 中不得保留方括号占位符；市场必须与工具导出时的实际地域设置一致。
- 不放入账号、Token、个人信息或其他敏感数据。
