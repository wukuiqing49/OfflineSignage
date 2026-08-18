# Google Play 商店素材策划 Skill

`plan-google-play-listing-assets` 用于分析 Android 项目、核验可宣传功能，并统一策划三类 Google Play Listing 素材：

1. Feature Graphic / 封面
2. Screenshots / 商店截图
3. Preview Video / 预览视频

Skill 根据项目真实功能生成制作 Brief，再从 Brief 自动导出独立执行 Prompt 文件，不虚构 App UI。实际生成或编辑封面、截图位图时由上层 Workflow 联动 `imagegen`。

每次调用都分析当时所在的 Android 项目。Skill 不内置具体产品类别、功能词、卖点、页面或关键词，也不复用其他项目的运行产物；固定的只有工作流程、Google Play 规则、目录结构和校验格式。

## 使用方式

```text
使用 $plan-google-play-listing-assets 为当前 Android App 策划完整 Google Play 素材包。
```

也可以只请求封面、截图或视频分支。

## 素材模式

- `CONCEPT`：不需要预先提供 App 截图或录屏。根据当前项目 VERIFIED 功能、真实 Icon、品牌资源、项目示例图片和关键词生成封面、功能营销截图与视频概念 Prompt。画面不使用设备框，不生成 App UI，不虚构按钮、点击、导航或屏幕数据。
- `PRODUCTION`：面向最终 Google Play 制作，要求真实截图、录屏、完整目标市场元数据和当前政策核验，使用不可修改的真实 UI Asset ID。

没有说明时，缺少真实 UI 素材则使用 `CONCEPT`；输入完整时使用 `PRODUCTION`。

## 复制到其他项目

将整个 Skill 目录复制到新 Android 项目：

```text
<new-project>/.agents/skills/plan-google-play-listing-assets/
```

在新项目根目录调用时，Skill 会重新读取该项目的 Manifest、Gradle、源码、导航、页面、资源和产品文档，并生成该项目专属的 Product Facts、Seed Keywords、素材 Brief 和 Prompt。不会继承原项目或上一次运行的产品结论。

只复制 Skill 目录，不要复制旧项目的 `.ai-work/play-assets/`。在新项目根目录先运行：

```powershell
python .agents/skills/plan-google-play-listing-assets/scripts/init_workspace.py --project-root .
```

初始化会生成 `project-context.json`，绑定当前项目路径、项目名和 applicationId/namespace。后续关键词分析与 Prompt 导出会核对该身份；误复制其他项目的 `.ai-work` 时会停止。然后将新项目自己的品牌素材、真实截图、录屏和关键词工具导出放入新创建的 `.ai-work/play-assets/input/`。项目级 profile、rules 和 Workflow 存在时会一并读取；不存在时以实际 Gradle、Manifest、源码和 Skill 自带 references 为事实与规则来源。使用 `AGENTS.md` 路由的项目应在对应入口中注册本 Skill，确保 Google Play 素材任务能够自动触发。

## 两阶段关键词流程

没有可靠关键词数据时，Skill 固定从真实产品功能推导 10 个 `en-US` 英文 Seed Keywords，然后暂停：

- `seed-keywords.md` 只展示 10 个关键词，每行一个，不附带翻译、分类或解释。
- `keyword-research-input.csv` 保存相同的 10 个关键词及内部证据字段，供校验和关键词工具使用。

把 Keyword Planner 或 ASO 工具导出的原始 CSV/XLSX 放入 `.ai-work/play-assets/input/keywords/`，再次运行后生成正式素材策略和 Brief。

## 工作目录

```text
.ai-work/play-assets/
├── project-context.json
├── input/
│   ├── keywords/
│   ├── brand/
│   ├── screenshots/
│   └── recordings/
└── output/
    ├── strategy/
    ├── feature-graphic/
    │   ├── FEATURE_GRAPHIC_BRIEF.md
    │   └── FEATURE_GRAPHIC_PROMPT.md
    ├── screenshots/
    │   ├── SCREENSHOT_BRIEF.md
    │   └── prompts/
    │       ├── SCREENSHOT_01_PROMPT.md
    │       └── ...
    └── video/
        ├── VIDEO_BRIEF.md
        └── VIDEO_PROMPT.md
```

Brief 保存项目事实、关键词定位、素材映射和制作决策；独立 Prompt 文件是可以直接交给图片或视频工具的执行产物。独立文件由脚本从 Brief 导出，避免重复编写造成内容不一致。

`keyword-research-analysis.csv` 中的自动分数只是词面预筛。Skill 必须结合当前项目 VERIFIED Product Facts 完成语义复核：可选词填写 `semantic_status=SELECTABLE` 和具体 `semantic_reason`，不匹配词填写 `REJECTED`。未复核、低相关但未说明或带未解决意图风险的词不能进入正式 Strategy。

导出器会再次检查关键词工具目录、Strategy、Brief 状态、真实 Asset ID 和关键 Prompt 参数。`CONCEPT` 模式要求关键词数据可读取、Strategy 为 `CONCEPT_READY`、分支为 `READY_FOR_CONCEPT`；`PRODUCTION` 模式要求完整关键词市场元数据、Strategy 为 `READY`、分支为 `READY_FOR_PRODUCTION`。任一模式不满足对应门禁时都不会写出 Prompt 文件。

## 常用命令

```powershell
python .agents/skills/plan-google-play-listing-assets/scripts/init_workspace.py --project-root .
python .agents/skills/plan-google-play-listing-assets/scripts/inspect_keyword_inputs.py --project-root . --require-metadata --require-data
python .agents/skills/plan-google-play-listing-assets/scripts/analyze_keyword_inputs.py --project-root .
python .agents/skills/plan-google-play-listing-assets/scripts/analyze_keyword_inputs.py --project-root . --allow-unconfirmed-market
python .agents/skills/plan-google-play-listing-assets/scripts/validate_seed_keywords.py --csv .ai-work/play-assets/output/strategy/keyword-research-input.csv --markdown .ai-work/play-assets/output/strategy/seed-keywords.md
python .agents/skills/plan-google-play-listing-assets/scripts/validate_listing_briefs.py --strategy .ai-work/play-assets/output/strategy/PLAY_ASSET_STRATEGY.md --keyword-analysis .ai-work/play-assets/output/strategy/keyword-research-analysis.csv --feature-graphic .ai-work/play-assets/output/feature-graphic/FEATURE_GRAPHIC_BRIEF.md --screenshots .ai-work/play-assets/output/screenshots/SCREENSHOT_BRIEF.md --video .ai-work/play-assets/output/video/VIDEO_BRIEF.md --require-complete-package
python .agents/skills/plan-google-play-listing-assets/scripts/validate_video_brief.py --brief .ai-work/play-assets/output/video/VIDEO_BRIEF.md
python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py --project-root .
python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py --project-root . --check
python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py --project-root . --asset-types screenshots --prune-stale
```

默认导出封面、截图和视频三个分支。只导出某一分支时使用：

```powershell
python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py --project-root . --asset-types feature-graphic
python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py --project-root . --asset-types screenshots
python .agents/skills/plan-google-play-listing-assets/scripts/export_prompt_files.py --project-root . --asset-types video
```

`--prune-stale` 只清理带脚本生成标记的过期截图 Prompt；未知文件和手工文件会保留并报告。

真实素材和关键词原始数据只放在被 Git 忽略的 `.ai-work/`。需要恢复暂停任务时，保留原始关键词导出文件并再次调用本 Skill。
