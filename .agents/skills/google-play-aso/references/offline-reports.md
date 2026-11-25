# 离线报表

用户提供符合下列字段的 Play Console 导出 CSV 时使用内置 import_reports.py，只需 Python 标准库，不需要 Google SDK 或认证。Google Ads Keyword Planner 导出不适用此导入器，应按其实际编码、分隔符及报表头单独读取并保留来源口径；项目已有资料见项目根目录 `doc/aso/README.md`（存在时读取）。

```powershell
python -X utf8 -B .agents/skills/google-play-aso/scripts/import_reports.py --app com.example.product --kind installs --csv <用户提供的CSV> --days 30 --end-date 2026-10-30
```

包名必须先核实；--end-date 按用户要求的分析截止日期填写，省略则为当天。同一类型多个月份可重复 --csv；不同类型分别运行。同一个日期/维度重复会拒绝，避免重复计数。不要把数据转换到支持的格式时擅自补零。

支持的 --kind 与 CSV 列：
- installs：Date、Package Name、Country、Daily User Installs、Daily User Uninstalls。
- search：Date、Package Name、Traffic Source、Search Term、Store Listing Acquisitions、Store Listing Visitors。Google Play Search 与 Other 分开统计，不把其他来源并入具体搜索词。
- ratings：Date、Package Name、Daily Average Rating、Total Average Rating。
- crashes：Date、Package Name、Daily Crashes、Daily ANRs。

文件编码按内置 decode_report 支持 UTF-8/BOM、UTF-16；格式不支持时保留原文件并说明缺失，不能把解析失败当作无数据。

结果写入所属项目 .google-play-aso/runs/offline-…，记录输入文件名、SHA-256、导入时间、请求周期、覆盖日期。导入时间不是 Google 抓取时间；此类统计没有商店语言维度，不伪造 locale。最终报告注明是用户导出数据，不是本次在线 API 验证。

其他格式的商店文案或评价由 agent 核对来源、包名、语言、导出日期后分析，明确其格式未经过此脚本校验，不自动套用 CSV 解析器。
