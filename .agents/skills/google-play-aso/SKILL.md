---
name: google-play-aso
description: 分析本 Skill 所在项目的真实代码与资源，使用内置脚本读取对应 App 的授权 Google Play 商店、评价和统计数据，生成中文数据分析及 ASO 优化方案。支持指定其他项目、离线报表和未上架项目分析，无需外部工具工程或网页。
---

# 项目与 Google Play ASO 分析

这是自包含的项目 Skill。脚本与数据引擎随目录一起迁移，不查找或调用外部 GooglePlay-Manager，不使用其配置、环境或输出目录。Python 3.10+ 与在线调用所需 Google SDK 依赖由本 Skill 的 requirements.txt 声明，凭据不随 Skill 分发。

## 工作流程

项目内已有关键词资料时，先读取目标项目根目录的 `doc/aso/README.md`（存在时）及对应来源说明，复用已有材料。Google Ads Keyword Planner 报表可用于关键词与简介研究，但不是 Play Console 搜索词报表，不能传给 `import_reports.py --kind search`。只使用目标项目自己的资料，路径不存在则按正常取证流程继续，不跨项目借用数据。

1. **定位所属项目。** 默认以当前 Skill 路径向上找到 `.agents` 或 `.agent`，其父目录就是目标项目，不使用任意终端 cwd。用户指定其他项目时使用 `--project`。多个项目逐个执行，不扫描整台电脑或账号内全部 App。
2. **核实真实能力。** 先运行 [统一入口](scripts/run_connector.py) 的 `inspect`，再读取所属项目的 AGENTS、档案、README、实际页面入口、业务实现和资源。静态索引不代替代码分析；区分已实现、演示、计划和未验收能力，记录源码位置、提交与工作区状态。
3. **确认发行身份。** `inspect` 的 applicationId 只是候选。结合 Gradle 变量、flavor、suffix 和发行变体确认最终包名；不能使用 namespace 或模块目录猜测。明确包名后直接用 `--app`，或写入所属项目的非秘密配置供后续使用。仅在无法确定映射时询问缺失项；内部库不是独立上架 App。
4. **读取对应数据。** 按 [配置与运行](references/connector.md) 执行离线 `check`、范围 `--dry-run`，再执行 `analyze`。默认 30 天、最多取回 500 条评价，配置语言优先、缺失时标记 en-US fallback。逐 App 独立超时、立即保存结果，不因一个失败丢弃其他结果。
5. **结合项目分析。** 按 [数据口径](references/data-sources.md) 将评价主题、商店声明和真实功能交叉核对。缺权限时继续本地分析；用户提供导出报表时按 [离线报表](references/offline-reports.md) 处理。请求失败不等于未上架，没有证据不推断排名、DAU、市场搜索量或增长。
6. **交付报告。** 按 [报告格式](references/report-format.md) 完成数据分析、ASO 优化两份中文 Markdown，保存到本项目本次运行目录。引擎仅生成数据基础稿；源码事实、匿名主题、完整建议文案与理由由执行 Skill 的 agent 完成。将三个完整文案字段保存为 JSON，用 `validate-copy` 校验。

## 完成与边界

- 使用本轮 manifest 的 evidenceFile，核对项目、包名、locale、周期、runId、抓取时间；不把历史输出当作新数据。多语言分别运行、分别交付。
- 原始证据默认存入项目 `.google-play-aso/runs/`，目录自带忽略规则。供分享的报告不得包含评价原文、用户标识、凭据路径；迁到正式文档目录前脱敏并按用户要求处理。
- 只允许取证所需的读取；listing 读取创建临时 edit，但不提交、不修改商店内容、不发布、不回复评价、不操作订单。内置兼容 CLI 不是扩大账号范围的入口，使用统一脚本。
- 认证文件由 SDK 在运行时读取，agent 不打开、不显示、不复制密钥内容。缺配置时说明缺口并继续本地分析，不借用其他项目账号或配置。
- 不自动重试整个取证任务；文案超长只重跑本地校验。脚本通过不代表数据完整、母语/法律审校或 Google 控制台验证通过。
- 不创建网页、Android 模块或 CI；本 Skill 不实现 App 的登录、支付与权益。

## 示例

- “分析这个项目的实际功能和最近 30 天 Google Play 数据，输出优化方案。”
- “把这个 Skill 放进另一个项目后，分析它自己的源码和商店表现。”
- “这个项目还没上架，基于实际功能准备商店文案，不编造运营数据。”
