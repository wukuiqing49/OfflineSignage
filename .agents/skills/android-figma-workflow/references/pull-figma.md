# 01 拉取 Figma

## 目标

把 Figma 中的页面结构、截图、图片资源和元数据拉到本地，为后续分析和代码生成提供依据。

## 输入

- Figma 文件链接或 Frame / Node 链接。
- Figma file key。
- Figma node id，可选。
- Figma token，建议通过环境变量传入。
- 目标资源目录，例如 `<公共资源模块>/src/main/assets/figma` 或 `<目标模块>/src/main/assets/figma`。

## MCP 优先流程

如果当前环境可用 Figma MCP，按以下顺序执行：

1. 使用 `get_design_context` 获取当前 node 的结构化信息。
2. 如果返回过大或被截断，先使用 `get_metadata` 获取节点地图，再只拉取目标 node。
3. 使用 `get_screenshot` 获取当前页面截图。
4. 下载 MCP 返回的图片 / SVG 资源。
5. 保存 node id、截图路径、结构化图层报告、资源清单和设计基准宽度。
6. 进入规范化阶段前，确认截图、结构化数据和资源清单都已落地到本地路径；只有内联截图时停止生成，先补成本地位图文件。

MCP 拉取完成后，也要落地以下文件，供后续阶段引用：

- `figma_node.json`：当前 node 的结构化数据。
- `figma_screenshot.png`：当前 node 的本地视觉基线；允许显式传入其他本地 PNG/JPG/WebP 路径。
- `figma_layer_report.md`：当前 node 的图层结构报告。
- `figma_asset_index.json` / `figma_asset_index.md`：Node ID、节点名、原始尺寸、导出路径和 SHA-256 索引。
- `asset_manifest.md`：当前输出目录的默认资源清单。
- `asset_manifest_<page>.md`：页面级资源清单；多页面任务优先生成和绑定这个文件，避免不同 Frame 资源混在一起。
- `figma_extract_report.md`：拉取摘要。
- `figma_normalize_report.md`：可在规范化阶段生成，记录逻辑屏幕、关键 bounds、系统栏图层和尺寸换算表。

## 资源拉取硬规则

拉取阶段必须生成资源清单。没有资源清单时，不允许进入代码生成阶段。

资源清单至少包含：

- 资源来源节点名称。
- Figma node id。
- 资源类型：图片 / SVG / Vector / Icon / 状态 selector。
- Android 目标文件名。
- Android 目标目录，例如 `drawable`、`drawable-nodpi`、`mipmap`。
- 使用位置，例如底部导航、顶部 Tab、工具栏、卡片、空态。
- 状态信息，例如 normal / selected / disabled / pressed。
- 导出文件的原始尺寸和 SHA-256；资源落地时用 Node ID + 尺寸 + 哈希复核，禁止只凭相似文件名映射。
- 是否已落地到本地资源目录。
- 缺失原因和处理建议。

导航、Tab 和工具栏图标必须单独列出。不能只记录大图、截图或 hero 图片。

如果 Figma MCP 返回 SVG / 图片的 localhost 地址，必须优先下载或引用该资源，并在清单里记录。禁止在未说明原因的情况下用通用 vector、文字按钮或占位图代替 Figma 图标。

## API / 脚本流程

如果使用本工作流自带脚本：

```powershell
$env:FIGMA_TOKEN="你的 token"
$env:FIGMA_FILE_KEY="你的 file key"
python .agents\skills\android-figma-workflow\scripts\figma_sync.py --file-key <file_key> --node-id <node_id> --out-dir .ai-work\figma\output --asset-dir <资源目录>\src\main\assets\figma
```

只基于本地已有 JSON / images 生成报告：

```powershell
python .agents\skills\android-figma-workflow\scripts\figma_sync.py --report-existing --out-dir .ai-work\figma\output --asset-dir <资源目录>\src\main\assets\figma
```

也可以直接传 Figma 链接：

```powershell
python .agents\skills\android-figma-workflow\scripts\figma_sync.py --figma-url "<Figma Frame URL>" --out-dir .ai-work\figma\output --asset-dir <资源目录>\src\main\assets\figma
```

## 输出

- `figma_node.json`：Figma 结构数据。
- `figma_screenshot.png`：页面视觉验收基线。
- 传给 `--asset-dir` 的目录：按 `<节点名>__<node_id>.png` 导出的候选图片和图标。
- `figma_asset_index.json` / `figma_asset_index.md`：候选资源的 Node ID、尺寸、路径和哈希。
- `figma_layer_report.md`：图层结构报告。
- `figma_extract_report.md`：结构、文本、颜色、图片引用报告。
- `asset_manifest.md` 或 `asset_manifest_<page>.md`：图片、SVG、图标、导航图标和缺失资源清单。多页面任务必须在页面任务卡中写清绑定的页面级资源清单路径。
- `figma_normalize_report.md`：逻辑屏幕、关键图层 bounds、关键尺寸换算表和系统栏图层判断。
- 单页面任务记录，参考 `assets/page_task_template.md`。

## 注意事项

- 不要把 token 写入仓库。
- 拉取失败时不要删除旧资源。
- 资源目录必须和实际 Android 模块一致。
- 如果只处理单个页面，优先传 node id，避免拉取整个文件导致数据过大。
- 如果没有本地 `figma_screenshot.png`（或显式本地截图）、`figma_layer_report.md`、`figma_asset_index.json` 和资源清单，不允许进入代码生成阶段；多页面任务优先使用页面任务卡绑定的 `asset_manifest_<page>.md`。
- 如果没有 `figma_normalize_report.md` 或页面任务卡没有关键尺寸换算表，不允许进入代码生成阶段。
- 如果资源清单里存在缺失的导航 / Tab / 工具栏图标，必须先停下来让用户确认补拉资源或接受降级方案。
