---
name: zeplin-android-layer-implementation
description: 根据 Zeplin 页面或组件 URL、图层树、导出资源和设计 Token，分析或实现 Android XML + ViewBinding 页面。当用户要求还原 Zeplin 设计、查看 Zeplin 图层、将 Zeplin 页面转为 Android Activity/Fragment，或同步现有 Android UI 与 Zeplin 设计时使用。
---

# Zeplin Android 图层实现

将 Zeplin 设计实现为可维护的 Android UI。Zeplin 图层几何信息表达的是设计意图，不是可直接照搬的 Android 绝对坐标。

## 必需的设计读取

1. 存在 `AGENTS.md` 时先读取；编码前检查目标模块、资源归属、现有导航和相似页面。
2. 先确认 Zeplin MCP 服务及任务所需工具可用。页面 URL 调用 `get_screen`，组件 URL 调用 `get_component`，并首先使用用户提供的精确 URL。页面过大时，再以 `includeVariants: false` 调用；只实现局部时使用 `targetLayerName` 聚焦。
3. 记录画板尺寸、根背景、注释、组件实例、文案、颜色、字体、可导出图层和设计 Token。响应中出现项目或样式库资源 ID 时调用 `get_design_tokens`。
4. Zeplin 或仓库提供预览图时，将其作为视觉核对依据，但不得用截图替代图层数据。
5. 新建组件或资源前先搜索仓库。缺失资产通过 `download_layer_asset` 下载到已校验的临时目录，检查格式、命名、冲突和 Android 兼容性后再移入资源目录；完成或失败都清理临时文件。详细流程见 [references/zeplin.md](references/zeplin.md)。

禁止仅根据截图实现页面。不得虚构 Zeplin 注释、仓库现状或用户需求中未定义的交互、文案和图层。

## MCP 失败处理

- 工具未注册、服务无法启动或连接失败时，停止设计读取和实现，报告缺失工具或服务状态以及已完成的本地检查。
- `400` 通常表示 URL 或参数格式错误，应检查并报告原始 URL 与参数；`401` 表示 Token 无效或过期；`403` 表示当前账号无项目权限；`404` 表示页面、组件或资源不存在。无法修正输入时停止并说明用户需要采取的动作。
- 其他服务端错误保留错误码和关键信息后停止，不得把截图、缓存印象或猜测当作图层数据继续实现。
- MCP 失败后可以检查现有工程，但禁止据此声称已读取或还原 Zeplin 设计。

## 编码前分析

除非用户明确要求立即编码，否则先输出以下简要方案并等待确认：

1. 页面层级与可复用组件。
2. 仅在导航、状态或列表确有需要时，拆分 Activity/Fragment、UI State 和 Adapter。
3. 图层到 Android View 的映射，以及导出资源清单。
4. `strings.xml`、`colors.xml`、`dimens.xml`、drawable 和国际化影响。
5. 设计无法表达的行为缺口或后端依赖。

分析与交付模板见 [references/zeplin-process.md](references/zeplin-process.md)。

同一对话中已经存在用户确认的分析方案时，直接复用其中的 Zeplin URL、目标组件、图层映射、资源计划和业务约束开始实现。不要重复输出分析，也不要要求用户再次填写这些信息。仅在以下情况暂停确认：链接缺失、设计数据可能已变更、确认方案存在互相冲突的约束，或实现依赖缺少必要的外部配置。

## 实现规则


- 使用 Android XML + ViewBinding。除非目标模块已使用或用户明确指定，否则不得引入 Compose 或 DataBinding。
- 优先使用 `ConstraintLayout`、约束、Chain、Barrier 和比例约束；禁止通过硬编码画板完整宽高或每个图层的 x/y 坐标复刻页面。
- Zeplin px 只是设计空间值。应按画板宽度相对项目基准换算；仓库没有其他规范时以 `360dp` 为基准。确需固定尺寸时使用 `round(设计px * 360 / 画板宽度)dp`。
- 状态栏、导航栏、挖孔和 IME 使用 Android `WindowInsets` 处理；禁止把 iPhone 状态栏或 Home Indicator 实现为应用内容。
- 可见文案放入 `strings.xml`，颜色放入 `colors.xml`，可复用尺寸放入 `dimens.xml`，可复用填充和状态放入 drawable XML。目标模块存在既定资源归属时优先遵循它。
- 有设计 Token 时必须优先复用；没有时只增加当前页面真正需要的颜色和尺寸，不得凭空建立大而全的 Token 体系。
- 使用语义化资源命名：`ic_`、`img_`、`bg_`、`shape_`、`selector_`、`activity_`、`fragment_`、`item_`、`dialog_`。
- 字体只可复用仓库已有字体，或引入 Zeplin/用户明确提供且使用权清晰的字体文件；缺失字体必须报告，不得静默下载、臆造或替换。
- Adapter 只承担 View 绑定。仅在页面具有状态、列表或明确异步行为时引入 ViewModel、UI State、分页或 Adapter。
- 可行时点击区域至少 48dp。设计视觉控件较小时，保持视觉尺寸，并在不改变布局的前提下扩大不可见点击区域。
- 应用存在这些语言时检查 `zh`、`en`、`tr` 的文案长度；禁止硬编码字符串。

图层映射和资源规则见 [references/zeplin.md](references/zeplin.md)，编码规则见 [references/zeplin-code.md](references/zeplin-code.md)。

## 验证与交付

1. 构建受影响的 Android 模块，并在条件允许时执行定向测试或 lint。
2. 至少在与设计画板宽度对应的视口和一个更小的 Android 视口下，将运行页面截图与 Zeplin 预览对照，验证系统 Insets、文本换行、对齐、间距、颜色、图片比例以及设计中定义的交互状态。
3. 记录可观察差异及其原因和处理结论；报告新增与修改文件、资产、资源/国际化变更、依赖、行为接入和验证结果，并明确说明尚未实现的设计或后端依赖。

没有视觉参考或可运行构建时，禁止宣称达到像素级还原。
