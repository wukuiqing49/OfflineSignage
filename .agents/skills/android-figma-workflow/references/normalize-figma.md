# 01.5 规范化 Figma

## 目录

- [目标](#目标)
- [输入](#输入)
- [必须输出](#必须输出)
- [系统栏与沉浸式判断](#系统栏与沉浸式判断)
- [阻塞条件](#阻塞条件)

## 目标

把 MCP / API 拉取到的 Figma 原始结构转成 Android 可实现的稳定输入，避免把高清 Frame 宽度、Figma 状态栏或绝对坐标直接带进代码。

## 输入

- `figma_node.json`
- `figma_layer_report.md`
- `asset_manifest.md`
- Figma 截图
- 目标项目 UI / Figma 工作流规则

## 必须输出

生成或补齐 `.ai-work/figma/output/figma_normalize_report.md`，并把关键结论同步写入页面任务卡。

### Figma 屏幕归一化

- Figma Frame 宽高：
- Figma 逻辑屏幕宽度：
- Figma 逻辑屏幕高度：
- 是否高清放大稿：
- 高清放大倍率：
- Android 基准宽度：默认 360dp；项目或设计明确声明时可用 375dp。
- 换算公式：
- 换算比例：
- 高度策略：Figma 高度只做区域比例参考，Android 页面用 `match_parent` / `0dp + 约束` 填充真实屏幕。

判断规则：

- Frame 宽度在 `360..430px` 时，默认视为移动端逻辑屏幕宽度。
- Frame 宽度大于 `430px` 但宽高比例接近常见手机设计稿时，必须推断逻辑宽度，例如 `853x1844` 可疑似由 `390x844` 等比例放大得到。
- 无法推断逻辑宽度时，停止生成代码，只输出待确认问题。
- 禁止直接把高清 Frame 宽度当作 Android 换算基准。
- 禁止直接把 Figma Frame 高度、逻辑屏幕高度或首屏总高度写成 Android 根布局高度、ViewPager2 高度、RecyclerView 高度。

### 宽度基准与高度填充合同

必须输出并同步到页面任务卡：

| 项 | 合同 |
| --- | --- |
| 宽度基准 | Figma 逻辑宽度按 360dp/项目基准宽度换算 |
| 固定视觉块 | Header/Toolbar/BottomNav/按钮/图标/角标按宽度基准换算 |
| 弹性内容区 | ViewPager2/RecyclerView/ScrollView 使用 `0dp + 约束` 或 `match_parent` 填充剩余高度 |
| Figma 高度 | 只用于区域比例和首屏可见数量参考，不落成整屏固定高度 |
| Insets | status/navigation inset 只作为 padding/spacer/WindowInsets owner，不参与视觉高度换算 |

规则：

- 页面根布局必须 `match_parent`。
- 主内容容器必须由约束填充，不能用 Figma 换算出的整屏高度。
- 列表区域必须在固定顶部区和固定底部区之间填充剩余空间。
- 三键导航与手势导航只影响 inset owner 的运行时安全区，不改变 `main_bottom_nav_height` 等业务视觉高度。

### 关键图层 Bounds 表

至少记录这些图层：

| 用途 | Figma 图层 | Node ID | x | y | width | height | Android 归属 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 页面根 |  |  |  |  |  |  | root |
| 状态栏/安全区 |  |  |  |  |  |  | Insets |
| 顶部 Header/Toolbar/Search |  |  |  |  |  |  | XML |
| 主内容列表 |  |  |  |  |  |  | RecyclerView |
| 底部导航/底部操作区 |  |  |  |  |  |  | Insets + XML |
| 弹窗/浮层 |  |  |  |  |  |  | Dialog/Popup |

### 关键尺寸换算表

至少覆盖页面左右边距、顶部栏/搜索框高度、状态栏/导航栏 Insets、BottomNav 高度、Tab 图标、中间按钮、分类图标、列表列间距、卡片比例、主要字号。

关键尺寸表是代码生成合同，不是参考文本。`dimen/style 名` 中出现的 `dimen` 后续必须落到资源文件；除非该行明确写入固定尺寸例外和原因，否则实现值必须与 `Android dp/sp` 一致，默认误差不超过 `2dp` / `1sp`。

| 用途 | Figma px | 逻辑 px | Android dp/sp | dimen/style 名 | 固定尺寸例外 | 说明 |
| --- | ---: | ---: | ---: | --- | --- | --- |
|  |  |  |  |  | 否 |  |

### 视觉高度拆分表

当 Frame 包含状态栏、手势条、三键导航预览、BottomNav、底部按钮或贴边操作区时，必须额外输出拆分表：

| 区域 | Figma 总高度 | 业务视觉高度 dp | 系统预览/安全区高度 | Android owner | 是否绘制 | 说明 |
| --- | ---: | ---: | ---: | --- | --- | --- |
| 状态栏 |  |  | 系统 Insets | Header/Toolbar/root | 否 |  |
| BottomNav |  |  | navigationBars | BottomNav/父容器 | 只绘制业务视觉区 |  |

规则：

- BottomNav 的 Android 视觉高度只能来自业务导航内容，不包含 Figma 手势条、三键导航预览或安全区。
- `navigationBars.bottom` 作为运行时 inset 单独追加，不能折算进 `main_bottom_nav_height` 等视觉 `dimen`。
- 如果 Figma 底部区域混合了导航背景和手势条，必须在表中拆开；无法拆开时停止生成代码。

### Insets owner 合同

必须输出 top/bottom inset 唯一归属：

| Inset 类型 | Android owner | 消费方式 | 子层是否可再次消费 | 说明 |
| --- | --- | --- | --- | --- |
| statusBars.top |  | paddingTop/marginTop/不消费 | 否/是，条件 |  |
| navigationBars.bottom |  | paddingBottom/marginBottom/不消费 | 否/是，条件 |  |

规则：

- 同一个 inset 只能有一个 owner。父容器消费后，子 Fragment 不再叠加同一个 inset。
- BottomNav + ViewPager2 场景：若 BottomNav 消费 `navigationBars.bottom` 且 ViewPager2 已约束到 BottomNav 顶部，子列表只保留内容余量，不再加 `navigationBars.bottom`。
- 若列表直接贴底且没有底部操作区，列表可以作为 `navigationBars.bottom` owner，并使用 `clipToPadding=false`。

## 系统栏与沉浸式判断

- Figma 状态栏、手势条、虚拟导航栏、刘海/安全区默认视为设计预览辅助，不绘制为 Android 页面主体。
- 如果 Figma 图层是业务 UI，必须在图层报告和页面任务卡中说明证据。
- 普通页面默认 Edge-to-edge + WindowInsets，不默认隐藏系统栏。
- 全屏隐藏系统栏只用于视频、图片预览、游戏、相机、全屏编辑器等场景，并说明退出和恢复策略。

## 阻塞条件

命中任一情况必须停止生成代码：

- 无法识别 Figma 逻辑屏幕宽度。
- 未输出关键图层 Bounds 表。
- 未输出关键尺寸换算表。
- 未说明 Figma 系统栏图层处理方式。
- 未说明顶部状态栏和底部虚拟导航栏 Insets 归属。
- 未输出视觉高度拆分表。
- 未输出 Insets owner 合同，或同一个 inset 有多个 owner。
