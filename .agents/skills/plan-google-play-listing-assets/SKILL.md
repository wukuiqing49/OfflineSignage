---
name: plan-google-play-listing-assets
description: "分析 Android 项目并统一策划 Google Play Listing 的 Feature Graphic 封面、商店截图和 Preview Video：核验真实功能，先推导恰好 10 个 en-US 英文 Seed Keywords 且只向用户展示关键词，再读取 Keyword Planner/ASO 工具 CSV 或 XLSX，统一 ASO/SEO/GEO 定位，并输出素材总策略、三类 Brief、独立 Prompt 和合规检查。用于完整 Google Play 素材包、封面策划、截图组规划、预览视频脚本、关键词生成或回填、提示词文件生成及跨素材一致性验收；不虚构 App UI 或产品能力。"
---

# Google Play 商店素材策划

## 项目上下文

在项目仓库中调用时读取：

- `.agents/config/project-profile.yml`（存在时读取）
- `.agents/rules/execution.md`（存在时读取）
- `.agents/rules/play-assets.md`（存在时读取）
- Manifest、Gradle、导航、页面、ViewModel、Repository、资源和产品文档

只追踪与待宣传能力相关的可达源码链路。排除构建产物、生成代码、缓存、prebuilt 和无关测试夹具。
每次调用都以当前项目为唯一产品事实源，重新核验产品类别、功能、卖点、页面、关键词和素材。不得复用其他项目或上一次运行的产品结论；固定内容仅限 Google Play 规则、流程协议、文件结构和校验格式。

## 初始化工作区

先运行：

```powershell
python .agents/skills/plan-google-play-listing-assets/scripts/init_workspace.py --project-root .
```

将真实品牌素材、截图、录屏和关键词工具原始导出文件放入 `.ai-work/play-assets/input/` 对应目录。不得覆盖或改写关键词原始文件。
初始化同时生成 `.ai-work/play-assets/project-context.json`，记录当前项目路径、项目名、applicationId/namespace 和稳定 Project ID。关键词分析与 Prompt 导出必须通过该身份检查；检测到其他项目的工作区时停止，不复用旧产物。

## 选择任务分支

先选择素材模式：

- `CONCEPT`：根据当前项目 VERIFIED 功能、真实 Icon/品牌资源、项目示例素材和关键词生成封面、功能营销截图与视频概念 Prompt；不要求现成截图或录屏。禁止设备框、假 App UI、虚构点击、导航和屏幕数据。Strategy 使用 `CONCEPT_READY`，分支使用 `READY_FOR_CONCEPT`。
- `PRODUCTION`：使用真实 App 截图和录屏生成可直接制作与提交的 Prompt，保留完整市场元数据、Asset ID、政策和保真门禁。Strategy 使用 `READY`，分支使用 `READY_FOR_PRODUCTION`。

- 请求完整商店素材包：生成 Shared Strategy，并加载封面、截图和视频三个分支。
- 只请求 Feature Graphic：加载 [封面规则](references/feature-graphic.md)，仍引用已有 Shared Strategy；缺失时先生成。
- 只请求 Screenshots：加载 [截图规则](references/screenshots.md)，仍引用已有 Shared Strategy；缺失时先生成。
- 只请求 Preview Video：加载 [视频规则](references/video-brief-schema.md)，仍引用已有 Shared Strategy；缺失时先生成。
- 请求关键词回填：只执行产品事实、关键词和 Shared Strategy，不提前生成正式素材 Brief。

## 共同流程

1. 确定目标市场、素材语言、设备类型和请求的素材分支；Seed Keywords 阶段固定使用 `en-US`，素材阶段再使用已确认的目标 Locale。
2. 按 [产品证据规则](references/product-evidence.md) 核验功能，生成带源码路径和行号的 Product Facts。
3. 查找 `APP_GROWTH_BRIEF.md` 及 ASO、SEO、GEO、Listing、品牌和关键词资料；报告缺失或冲突。
4. 按 [关键词两阶段流程](references/keyword-workflow.md) 决定暂停或继续；生成 Seed 时同时读取 [Seed Keyword 质量门禁](references/seed-keyword-quality.md)。
5. 有合格关键词研究结果后，按 [共享素材策略](references/listing-strategy.md) 生成 `PLAY_ASSET_STRATEGY.md`。
6. 按请求分支生成 `FEATURE_GRAPHIC_BRIEF.md`、`SCREENSHOT_BRIEF.md` 和/或 `VIDEO_BRIEF.md`，在每个 Brief 中完成自包含 Final Prompt。
7. 运行 `export_prompt_files.py`；脚本根据 Asset Mode 检查对应关键词、Strategy 与分支 Brief 门禁，再确定性导出独立 Prompt 文件。不要另写一份可能与 Brief 漂移的 Prompt。
8. 检查 Google 官方最新 Preview Asset 和 Branding 文档；按 [合规规则](references/google-play-listing-compliance.md) 区分官方要求、官方建议和内部建议。
9. 将结果写入 `.ai-work/play-assets/output/`，运行 Brief 和 Prompt 一致性校验并修复错误。

## 产品事实门禁

仅允许 `VERIFIED` 且 `advertisable: true` 的能力进入封面文案、截图标题、Storyboard、旁白和执行 Prompt。

- 把 `UNVERIFIED`、`CONTRADICTED` 和 `NOT_FOUND` 放入禁止宣传清单。
- 不以类名、资源名、依赖、README 单一描述或未接入模块证明功能存在。
- 对 Offline、Privacy、AI、Cloud、Batch、Billing 等高风险声明核对完整可达调用链。
- 为每张截图和每个 Scene 填写 Product Feature Evidence。

## 关键词暂停点

未发现可靠关键词工具结果时：

1. 先按 [Seed Keyword 质量门禁](references/seed-keyword-quality.md) 从 VERIFIED 产品事实推导 30–50 个内部候选，完成搜索意图、自然度、类别代表性和去重筛选后，再保留恰好 10 个英文搜索短语；固定 `locale=en-US`，不生成翻译词或其他 Locale。
2. 使用 `assets/seed-keywords.template.md` 生成 `strategy/seed-keywords.md`。该文件只保留标题和一个 `text` 代码块，每行一个关键词，不写产品事实、分类、理由、状态、排名或说明。
3. 使用 `assets/seed-keywords.template.csv` 生成 `strategy/keyword-research-input.csv`。CSV 保留产品证据和选择理由供内部校验与工具交接，每行标记为 `CANDIDATE_SEED`。
4. 不填写搜索量、竞争度、趋势、CPC 或市场优先级，不把 Seed 表述为研究结论。
5. 同时传入 CSV 与 Markdown 运行 `validate_seed_keywords.py --strict-quality`；必须通过“恰好 10 行、全部 `en-US`、关键词仅含英文 ASCII 文本、Markdown 仅展示关键词且顺序一致、类别覆盖与近义重复”等门禁。脚本通过后仍要人工复核搜索语言自然度。
6. 向用户回复时只输出与 Markdown/CSV 顺序一致的 10 个关键词，每行一个；不附带中文翻译、编号、表格、解释、证据或下一步说明。
7. 停止正式素材策划，等待用户把工具原始结果放入 `input/keywords/` 后再次调用本 Skill。

发现 CSV/XLSX 后运行：

```powershell
python .agents/skills/plan-google-play-listing-assets/scripts/inspect_keyword_inputs.py --project-root .
```

PRODUCTION 调用时增加 `--require-metadata --require-data`，仅当报告为 `status=ready` 时继续。CONCEPT 只要求可用研究数据；市场元数据不完整时运行 `analyze_keyword_inputs.py --allow-unconfirmed-market` 并将相应字段标记为 `UNCONFIRMED`，不得形成市场已验证声明。脚本只做结构化和词面排序；必须依据当前项目 VERIFIED Product Facts 完成语义复核，将可选词标记为 `SELECTABLE` 并填写 `semantic_reason`。正式 Strategy 只能选择该分析表中可追溯且已通过语义复核的词。

优先选择与当前项目的 VERIFIED 功能、Android App 和目标搜索意图一致的词。不得内置产品类别、功能词、同义词或卖点；Online/Web/AI/Cloud/Team 等词仅在当前项目没有对应 VERIFIED 证据时标记风险，不能跨项目一律拒绝。

## 三类素材分工

- Feature Graphic：回答“这个 App 是什么”，承担第一视觉和 Preview Video 封面协同。
- Screenshots：回答“它有哪些真实能力”，用有顺序的真实 UI 证明功能和差异化。
- Preview Video：回答“用户如何完成任务”，展示真实操作、导航和结果，保证静音可理解。

三类素材共享定位和视觉语言，但不要重复同一句标题、同一画面和同一功能列表。

## 输出

第一阶段：

- `.ai-work/play-assets/output/strategy/seed-keywords.md`
- `.ai-work/play-assets/output/strategy/keyword-research-input.csv`
- `.ai-work/play-assets/output/strategy/keyword-research-analysis.csv`（关键词工具数据回填后）

最终阶段：

- `.ai-work/play-assets/output/strategy/PLAY_ASSET_STRATEGY.md`
- `.ai-work/play-assets/output/feature-graphic/FEATURE_GRAPHIC_BRIEF.md`
- `.ai-work/play-assets/output/screenshots/SCREENSHOT_BRIEF.md`
- `.ai-work/play-assets/output/video/VIDEO_BRIEF.md`
- `.ai-work/play-assets/output/feature-graphic/FEATURE_GRAPHIC_PROMPT.md`
- `.ai-work/play-assets/output/screenshots/prompts/{device}/SCREENSHOT_01_PROMPT.md`（每个设备集合、每张截图一份）
- `.ai-work/play-assets/output/video/VIDEO_PROMPT.md`

只生成用户请求的分支。完整素材包必须生成四份策划文档、一个封面 Prompt、与截图数量一致的截图 Prompt，以及一个视频 Prompt。独立 Prompt 必须由 Brief 导出，引用真实 Asset ID，并禁止生成、重绘、翻译或改写 App UI。

## 验证

```powershell
python .agents/skills/plan-google-play-listing-assets/scripts/validate_seed_keywords.py `
  --csv .ai-work/play-assets/output/strategy/keyword-research-input.csv `
  --markdown .ai-work/play-assets/output/strategy/seed-keywords.md `
  --strict-quality

python .agents/skills/plan-google-play-listing-assets/scripts/analyze_keyword_inputs.py `
  --project-root .

python .agents/skills/plan-google-play-listing-assets/scripts/validate_listing_briefs.py `
  --strategy .ai-work/play-assets/output/strategy/PLAY_ASSET_STRATEGY.md `
  --keyword-analysis .ai-work/play-assets/output/strategy/keyword-research-analysis.csv `
  --feature-graphic .ai-work/play-assets/output/feature-graphic/FEATURE_GRAPHIC_BRIEF.md `
  --screenshots .ai-work/play-assets/output/screenshots/SCREENSHOT_BRIEF.md `
  --video .ai-work/play-assets/output/video/VIDEO_BRIEF.md `
  --require-complete-package

python .agents/skills/plan-google-play-listing-assets/scripts/validate_video_brief.py `
  --brief .ai-work/play-assets/output/video/VIDEO_BRIEF.md

python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py `
  --project-root .

python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py `
  --project-root . `
  --check
```

## Concept Prompt Rules

CONCEPT prompt files are production layout specifications, not a request for an image model to draw final typography or logos.

- Ask the image step to generate background geometry and media motifs only.
- Reserve a named text-safe region and keep exact copy outside the image-generation step.
- Add headlines, supporting text, endpoint labels, and `BRAND-ICON-01` later with a deterministic compositor using a real system font and the original icon file.
- State exact canvas size, orientation, opaque output, and post-composition checks in every Feature Graphic and Screenshot prompt.
- Do not call a CONCEPT panel a real App screenshot. Switch to `PRODUCTION` only after real device captures are available.

The validator accepts both portrait and landscape screenshots by default. Pass `--screenshot-orientation` and `--screenshot-size` when a branch has a fixed production contract.

完整素材包默认导出三个分支。单分支任务传入 `--asset-types feature-graphic`、`--asset-types screenshots` 或 `--asset-types video`；导出后用相同参数加 `--check` 验证文件与 Brief 完全一致。

截图数量减少后，先检查 `--check` 报告，再按需传入 `--prune-stale`。该参数只删除包含本脚本生成标记的过期 `SCREENSHOT_XX_PROMPT.md`，不删除无法识别或手工维护的文件。

实际生成位图后继续运行 `.agents/scripts/validate_play_assets.py`。报告未执行的联网规则核验、真实设备录屏、YouTube 设置、版权、音乐授权、Badge 来源和 Play Console 提交风险。
