# 关键词两阶段流程

## 阶段 A：Seed Keywords

固定生成恰好 10 个 `en-US` 英文关键词。用户可见内容只展示关键词本身，每行一个，不生成中文翻译、其他 Locale、编号、表格或解释。生成前必须读取 [Seed Keyword 质量门禁](seed-keyword-quality.md)。

先从以下维度推导 30–50 个内部候选词：

- 产品类别
- 核心用户任务
- VERIFIED 主要功能
- VERIFIED 差异化
- 用户问题
- Android 平台和使用场景

先把产品事实转换成自然搜索语言，再按产品相关性、搜索意图、自然度、类别代表性和独特性筛成 10 个。禁止把功能声明直接拼成关键词；低层功能没有独立搜索意图时留给 Listing 文案。使用 `assets/seed-keywords.template.csv`，每行填写：

- `seed_keyword`
- `locale`
- `category`
- `search_intent`
- `product_evidence`
- `rationale`
- `status=CANDIDATE_SEED`

同时使用 `assets/seed-keywords.template.md` 生成纯关键词清单。Markdown 只允许标题和 `text` 代码块，代码块中的 10 行必须与 CSV 的 `seed_keyword` 顺序完全一致。

不得填写搜索量、竞争度、趋势、Keyword Difficulty、CPC 或市场优先级。不要把 Seed 表述为研究结论。同时传入 CSV 和 Markdown 运行 `validate_seed_keywords.py --strict-quality`，校验恰好 10 行、全部为 `en-US`、关键词无重复且只含英文 ASCII 文本、类别与意图覆盖合理，并确认 Markdown 没有关键词以外的展示内容。脚本无法证明短语真实存在市场需求，必须在提交关键词工具前人工复核自然度和搜索意图。

向用户回复时只展示相同的 10 个关键词，每行一个，不补充分析或操作说明。随后暂停正式素材策划，等待用户使用 Keyword Planner、ASO 平台或其他工具扩词。

## 阶段 B：工具结果

读取 `.ai-work/play-assets/input/keywords/` 中的 CSV/XLSX。先运行 `inspect_keyword_inputs.py`，确认：

- 文件可读取
- 工作表、表头和行数
- 工具名称
- 目标国家或地区
- Locale
- 导出日期
- 指标含义和单位

存在多个导出文件时，先确认它们属于同一项目、目标市场、Locale 和 Seed 研究轮次。旧轮次、其他市场或不同工具口径的文件必须移出本次活动输入目录并保留归档，不得在一份分析中静默混合。

使用 `--require-metadata` 检查。只有报告 `status=ready` 时才能进入正式素材策划。`ready` 要求至少一个工作表同时具备非空数据行、可识别的关键词列和研究指标列，并且 `keyword-research-metadata.json` 已填写工具、真实目标市场、Locale、导出日期和指标定义。`needs_metadata` 或 `no_usable_research_data` 必须继续暂停。

门禁通过后运行 `analyze_keyword_inputs.py`，生成标准化 `keyword-research-analysis.csv`。脚本的词面相关度只负责排序，初始 `semantic_status=PENDING_REVIEW`。随后必须结合当前项目 VERIFIED Product Facts 和真实搜索意图逐词复核：区分产品/App、硬件、服务商、品牌、价格、教程、相邻场景和其他产品类型；分类标准必须由当前项目决定，不使用跨项目行业黑名单。可正式选择的词改为 `SELECTABLE` 并填写 `semantic_reason`，不匹配的词改为 `REJECTED`。正式 Strategy 只能选择 `SELECTABLE` 词，并保留来源、原始指标、对应 Seed 和 Product Fact。

不得覆盖原始导出文件。无法识别列语义时保留原列并标记待确认，不凭列位置猜测。

## 筛选顺序

1. 产品相关性：词必须对应 VERIFIED 能力或准确产品类别。
2. 搜索意图：优先与 Android App 获取或使用场景一致的意图。
3. 定位一致性：不得偏离已确认的 ASO、SEO、GEO 主定位。
4. 市场数据：在前三项通过后再比较搜索量、竞争度、趋势和难度。
5. 视频可表达性：核心词必须能通过真实 UI 和操作在短视频中证明。

Keyword Planner 等 Web 搜索数据用于验证用词、需求方向和相对竞争，不等同于 Google Play 商店搜索量。不得把 Web 指标直接表述为 Play 商店搜索量或排名；可用 Play Console 或专用 ASO 数据做后续验证。

把与当前项目不匹配的 `online`、`web tool` 或不存在的 AI/Cloud/Team 能力放入拒绝清单，即使搜索量更高。当前项目已经由 VERIFIED 证据确认的能力不得因固定词表被拒绝。不得在 Skill 中内置某个产品领域的类别词、功能词、同义词或卖点。

## 输出分类

- `SEED`：项目推导的原始候选词。
- `EXPANDED`：外部工具扩展得到的词。
- `SELECTED_PRIMARY`：最终主定位词。
- `SELECTED_SECONDARY`：支持功能或场景词。
- `REJECTED`：与事实、平台、意图或定位不匹配。

为最终采用词记录来源文件、原始指标、选择理由和对应 Product Fact。缺少工具数据时仅输出 DRAFT，不生成正式的素材总策略、封面 Brief、截图 Brief 或视频 Brief。
