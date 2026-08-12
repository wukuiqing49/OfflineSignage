# 03 生成代码

## 目录

- [目标](#目标)
- [输入](#输入)
- [生成提示词](#生成提示词)
- [代码生成规则](#代码生成规则)
- [生成前阻塞条件](#生成前阻塞条件)
- [输出要求](#输出要求)

## 目标

基于分析结果和项目模板，生成可直接集成到现有工程的 Android 页面代码。

## 输入

- 根目录 `AGENTS.md`
- `.agents/skills/android-ui-workflow/SKILL.md`
- `.agents/rules/figma.md`
- `android-mapping.md`
- 页面分析结果。
- 当前 Figma Frame 的结构数据和截图。
- 当前 Figma Frame 的 `figma_layer_report.md` 和 `asset_manifest.md`。
- 当前 Figma Frame 的 `figma_normalize_report.md` 或页面任务卡中的等价规范化信息。
- 项目已有模板和相似页面代码。
- `templates/` 下的参考模板。

## 生成提示词

```text
开始实现当前 Figma Frame 对应页面。

必须遵守 `.agents/workflows/figma-code.md` 已加载的 Rules 和 Skills，并读取 `android-mapping.md`。项目通用约束以 `.agents/rules/` 为唯一来源，本文件只补充 Figma 代码生成细节。

要求：

1. 默认按 Android XML + ViewBinding 生成；如果目标项目使用其他 UI 技术，先按项目现状调整
2. 不擅自切换到 Compose
3. 不擅自切换到 DataBinding
4. Fragment / Activity 优先基于目标项目已有页面基类；没有基类时使用 AndroidX 标准类并说明原因
5. 公共资源放资源模块
6. 主布局优先使用 ConstraintLayout
7. 禁止直接使用 Figma px 作为 dp
8. 按 Android 360dp 或目标项目声明的设计基准宽度换算尺寸；生成前必须输出换算公式和关键尺寸表
9. 禁止写死 Figma Frame 宽高；Figma Frame 高度只作为区域比例参考，真实页面必须填充屏幕
10. 图片资源默认按项目资源规范存放
11. 文案放 strings.xml
12. 颜色放 colors.xml
13. 尺寸放 dimens.xml
14. 列表使用 RecyclerView
15. 顶部 Tab、底部主导航、频道 Tab、分段 Tab 或同级页面切换入口必须遵循 `android-ui-workflow`，使用 Tab/BottomNav 组件 + ViewPager2 + FragmentStateAdapter + 子 Fragment；只有用户明确要求静态单页视觉稿时才能降级
16. 单列表页优先使用目标项目已有列表页封装；没有封装时使用标准 RecyclerView
17. Adapter 优先使用目标项目已有 Adapter 基类；没有基类时使用标准 Adapter
18. Adapter 不写业务逻辑
19. 普通 TextView 禁止写死 dp 宽高
20. 页面销毁后不能继续回调 UI
21. 错误态必须有兜底
22. 实现前必须确认架构门禁已通过
23. 实现前必须确认资源门禁已通过
24. 主页、主导航、底部导航或 Tab 页面禁止用单 Activity + 单 RecyclerView 数据切换模拟
25. Figma 中存在的图标必须使用真实导出资源或明确说明差异，禁止静默替换成通用图标
26. 禁止使用整页截图、区域截图或截图切片生成页面主体，必须按 Figma 图层结构生成 XML、item、shape、图片和图标
27. Figma 截图只允许用于视觉参考和验收对比，不允许作为页面布局或控件实现
28. Figma 图标缺失、导出失败或暂不可用时，允许使用 Material Design 图标替代，但必须在资源清单、差异说明和验收结果中标注“MD 图标替代”
29. 每个顶层 Frame 默认生成专用 Fragment、ViewModel、UIState、Adapter、XML 和 item XML
30. 禁止用一个通用 UiBlock / ScreenFactory / Adapter 作为最终实现承包多个页面
31. 项目级第一批必须优先生成并验收黄金样板页，后续页面沿用其拆分方式但仍按各自 Frame 图层专用实现
32. 缺失图标可用 Material Design 图标替代，但资源命名、差异说明和验收结果必须标注“MD 图标替代”
33. 生成时必须引用 `figma_layer_report.md` 中的关键图层，并说明图层到 Android 文件的映射
34. 生成时必须实现屏幕适配方案：根布局不写死 Frame 宽高，列表按可用宽度/列数/间距计算，图片用 ratio 或固定视觉例外，状态栏/底部虚拟导航栏使用 WindowInsets 或项目已有 inset 工具
35. 生成时必须实现沉浸式 / Edge-to-edge 方案：说明普通 Edge-to-edge、隐藏系统栏全屏或非沉浸式；顶部 Header/Toolbar、底部 BottomNav/按钮、RecyclerView 最后一项、Dialog/Popup/BottomSheet 都必须有 Insets 归属
36. Figma 状态栏、手势条、虚拟导航栏、刘海/安全区默认只作为设计参考和 Insets 依据，不作为 Android 页面主体绘制；如果它们是业务 UI，必须在图层报告和实现说明中单独证明
37. 生成时必须落实关键尺寸合同：读取页面任务卡的关键尺寸换算表，`dimen/style 名` 中的每个 `dimen` 都要写入资源并与目标 dp/sp 保持一致；默认误差不超过 `2dp` / `1sp`，超过时必须在固定尺寸例外清单中写明原因并等待用户确认
38. 生成时必须落实 Insets owner 合同：同一个 `statusBars.top` / `navigationBars.bottom` 只能由一个 owner 消费；父容器或 BottomNav 消费 bottom inset 后，子 Fragment 的 RecyclerView 不能再次追加同一个 navigation bar inset
39. BottomNav 高度必须拆分为业务视觉高度和运行时 bottom inset；禁止把包含 Figma 手势条、三键导航预览或安全区的底部总高度直接写成 `main_bottom_nav_height`
40. 必须落实“宽度基准与高度填充合同”：Figma 逻辑宽度换算到 Android 360dp/项目基准宽度，固定视觉块按该基准落地，根布局/主页容器/ViewPager2/RecyclerView/ScrollView 用 `match_parent` 或 `0dp + ConstraintLayout 约束` 填充真实屏幕和剩余高度
41. 禁止把 Figma 逻辑屏幕高度、Frame 高度、首屏内容总高等比换算为 Android 根布局、主页容器、ViewPager2、RecyclerView 或主内容区固定高度
42. 必须落实“项目基类 / 组件审计”：按页面任务卡使用项目已有 BaseActivity / BaseFragment / BaseVMFragment / BaseViewModel / Adapter / ViewPager 封装；如果基类来自 AAR 或 `core:core_base`，不得因 feature 源码目录搜不到而降级
43. 必须落实“页面入口链路合同”：Launcher、Splash、Host、Feature Fragment、路由/Entry 职责必须和任务卡一致；新增启动页时 Launcher 必须指向 Splash，Host 只承载目标页面
44. 必须落实“运行时视觉风险清单”：实现后逐项核对顶部 status inset、底部 navigation inset、可滚动区域、FAB/BottomNav、小屏高度降级

请生成：

1. XML 布局文件
2. Fragment / Activity
3. ViewModel
4. Adapter
5. Bean / UIState
6. strings.xml
7. colors.xml
8. dimens.xml
9. drawable / shape
10. 修改文件清单
11. 关键方法备注和类备注
12. 验证方法
```

## 代码生成规则

- 修改前先分析原因，再动手实现。
- 只有分析阶段明确通过架构门禁和资源门禁后，才允许生成代码。
- 只有分析阶段明确通过项目基类 / 组件审计门禁后，才允许生成代码。
- 只有分析阶段明确通过页面入口链路合同后，才允许生成代码。
- 只有分析阶段明确通过屏幕适配门禁后，才允许生成代码。
- 只有 `figma_layer_report.md`、`asset_manifest.md`、`figma_normalize_report.md` 或页面任务卡中的等价规范化信息都存在并已读取后，才允许生成代码。
- 优先复用项目已有基类、工具类、Adapter、图片加载能力和路由。
- 不新增不必要依赖。
- 不随意改 public API、包名、模块名、资源名、XML id。
- 不随意修改外部调用方式。
- Gradle 版本统一放在 gradle/libs.versions.toml。
- 页面逻辑不要堆在 Activity / Fragment。
- ViewModel 不持有 View 或 Context。
- Adapter 只绑定 UI，不做业务判断和网络请求。
- 优先复用项目已有页面和 Adapter 基类；如果项目没有统一基类，不要虚构基类，使用 AndroidX / RecyclerView 标准实现并说明。
- 重复 item 必须抽成 RecyclerView item，不要在页面 XML 里复制多份静态布局。
- 页面必须按 Figma 图层解析生成，不允许把截图作为背景图或整页图片来规避布局实现。
- 缺失图标可以使用 Material Design 图标临时替代，但 Adapter、XML 和资源清单中必须保留清晰命名，后续可替换回 Figma 原始图标。
- 单页实现优先生成专用 Fragment、ViewModel、UIState、Adapter 和 XML；只有形态完全一致的列表 item 才抽公共 item。
- 列表页必须生成专用 UI item/Bean，不允许用松散 Map、通用 block type 或字符串数组承载页面结构。
- 旧通用 block 体系只能作为迁移期兜底，不能作为新页面最终实现。
- 普通 `TextView` 默认 `wrap_content`；需要占满剩余空间时使用 `0dp + ConstraintLayout` 约束。
- 只有按钮、徽章、头像、图标、倒计时格、Tab 指示器等固定视觉元素允许固定宽高。
- 主页容器或 Tab 容器必须按 `android-ui-workflow` 生成专用容器 Fragment/Activity、`ViewPager2`、`FragmentStateAdapter` 和子 Fragment。禁止只画静态底部导航、静态 Tab 或用单个 Fragment/RecyclerView 切换多个主页面。
- Android 适配必须优先使用 `ConstraintLayout` 约束、`0dp`、`layout_weight`、`layout_constraintDimensionRatio`、RecyclerView span/ItemDecoration、WindowInsets；禁止把 Figma 绝对坐标成批转为 margin/top/height。
- Figma 屏幕宽度必须先识别逻辑屏幕：默认 Android 设计基准宽度为 360dp；若项目或设计明确使用 375dp，可使用 375dp 并说明。Figma Frame 宽度在 360-430px 范围内视为移动端逻辑宽；明显大于 430px 但比例接近常见手机屏的高清稿，必须先推断 logicalFrameWidth 再换算。
- 屏幕适配必须按“宽度建基准，高度填充屏幕”：Figma 宽度用于换算 dp/sp；Figma 高度用于首屏可见数量、区域比例和滚动起点参考；Android 真实高度由根布局 `match_parent`、ConstraintLayout `0dp` 约束、ViewPager2/RecyclerView/ScrollView 剩余空间填充承担。
- 固定视觉块与弹性内容区必须分开：Header/Toolbar/BottomNav/按钮/图标/角标可以按 360dp 基准固定视觉尺寸；列表、分页容器、滚动容器、主内容区必须弹性填充。Insets 只能作为 padding/spacer/owner 处理，不能改变业务视觉高度。
- 必须输出关键尺寸换算表，至少覆盖页面左右边距、搜索/Toolbar 高度、状态栏/导航栏 Insets、BottomNav 高度、Tab 图标、中间按钮、分类图标、列表列间距、卡片比例和字号映射。
- 关键尺寸换算表中的 `dimen` 是生成合同。实现时先写入目标值，再考虑触控热区或平台最小值；如需放大视觉尺寸，必须同时满足：任务卡固定尺寸例外为“是”、说明写清原因、最终回复列出与 Figma 的差异。
- 触控热区优先通过父容器 padding、`minHeight`、`foreground` 或点击代理扩大，不要把图标、按钮图、搜索框、BottomNav 视觉尺寸直接放大。
- 普通页面默认 Edge-to-edge + WindowInsets，不默认隐藏系统栏；视频、图片预览、游戏、相机、全屏编辑器等才允许隐藏系统栏，并必须提供退出和恢复策略。
- 有 BottomNav、底部按钮、输入框、FAB 或底部操作栏时，必须处理底部虚拟导航栏；有 RecyclerView/ScrollView 时必须保证最后一项不被底部系统栏或操作栏遮挡。
- BottomNav + ViewPager2 页面优先采用：父容器/BottomNav 消费 `navigationBars.bottom`；`ViewPager2` 约束到 BottomNav 顶部；子 RecyclerView 不再加 `navigationBars.bottom`，只按内容需要保留固定 bottom padding。若改用列表消费 bottom inset，BottomNav 不得再次消费同一个 inset。
- Dialog、Popup、BottomSheet、Drawer、全屏浮层必须按其 Window/锚点/容器处理状态栏、导航栏、软键盘和 display cutout，不能只套用普通 Fragment Insets。
- ViewBinding 注意生命周期释放。
- 网络、数据库、复杂工具类优先放到目标项目已有共享层、基础封装层或公共组件模块。

## 生成前阻塞条件

命中以下任一情况时，必须停止生成代码，只输出问题和补救方案：

- 当前对象是 Section / Folder / Page，但用户没有指定单个 Frame。
- 页面包含主页导航、底部导航或顶部 Tab，但未规划 `ViewPager2 + Fragment`。
- 页面包含主页导航、底部导航或顶部 Tab，但未规划 `FragmentStateAdapter` 和每个子 Fragment。
- 未输出 Android 屏幕适配方案、换算比例和固定尺寸例外清单。
- 未输出项目基类 / 组件审计，或未说明 `core:core_base` / AAR / Gradle 依赖扫描结果。
- 项目存在可用 BaseActivity / BaseFragment / ViewModel / Adapter 基类，但计划使用 AndroidX 标准类且未写明不可用证据。
- 未输出页面入口链路合同，或 Launcher / Host / Feature 页面职责不清。
- 用户要求新增 Splash，但任务卡没有声明 Splash 是 Launcher。
- 未输出运行时视觉风险清单。
- 未输出 Figma 逻辑屏幕宽度、Android 基准宽度和关键尺寸换算表。
- 未输出宽度基准与高度填充合同，或合同没有说明固定视觉块、弹性内容区、Figma 高度用途和 Insets 对高度的影响。
- 计划把 Figma Frame 高度、逻辑屏幕高度或首屏内容总高写成 Android 根布局、主页容器、ViewPager2、RecyclerView 或主内容区固定高度。
- 未输出沉浸式 / Edge-to-edge / 状态栏 / 底部虚拟导航栏 / Insets 归属。
- 未输出 Insets owner 合同，或合同中同一个 inset 出现多个 owner。
- 未输出视觉高度拆分表，或 BottomNav 视觉高度仍包含 Figma 手势条/虚拟导航栏预览。
- Frame 包含 Figma 状态栏、手势条、虚拟导航栏、刘海/安全区但未说明忽略、转 Insets 或真实绘制策略。
- 多个同级主页面被计划合并到一个 Activity 的一个 RecyclerView 中。
- 未输出资源清单。
- 未输出图层结构报告。
- 资源清单中缺少底部导航图标、Tab 图标、工具栏图标或关键操作图标。
- Figma 有 normal / selected 状态，但 Android 资源没有 selector 或状态处理方案。
- 计划使用通用图标、纯文字按钮或占位图替代 Figma 图标，但用户未确认。
- 计划使用整页截图、区域截图、截图切片作为页面主体实现。
- 计划使用一个通用 UiBlock / ScreenFactory / Adapter 承包多个顶层 Frame 的最终实现。

## 输出要求

每次实现完成必须输出：

- 新增文件列表。
- 修改文件列表。
- 新增资源列表。
- 国际化资源变更。
- 混淆规则变更。
- 依赖变更。
- 页面实现说明。
- 和 Figma 的差异说明。
- Figma 屏幕基准和关键尺寸换算表。
- 沉浸式 / Edge-to-edge / 状态栏 / 底部虚拟导航栏 / Dialog/Popup 适配说明。
- 架构门禁结果。
- 资源门禁结果。
- 缺失资源和降级说明。
- 验证结果。
