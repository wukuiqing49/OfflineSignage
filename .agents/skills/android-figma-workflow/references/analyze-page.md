# 02 分析页面

## 目录

- [目标](#目标)
- [输入](#输入)
- [分析提示词](#分析提示词)
- [输出格式](#输出格式)
- [分析检查](#分析检查)

## 目标

先理解页面，再决定怎么实现。此阶段只输出分析和计划，不生成代码。

## 输入

- 根目录 `AGENTS.md`
- `.agents/skills/android-ui-workflow/SKILL.md`
- `.agents/rules/figma.md`
- `android-mapping.md`
- 项目 `README.md` 或架构说明
- Figma 结构化数据
- Figma 图层结构报告 `figma_layer_report.md`
- Figma 资源清单 `asset_manifest.md`
- Figma 规范化报告 `figma_normalize_report.md`
- Figma 页面截图
- 目标页面 node id
- 当前项目可复用页面 / 组件 / Adapter / ViewModel

## 分析提示词

```text
先完成 `.agents/workflows/figma-process.md` 的加载步骤。本文件只补充页面分析和任务卡生成细节，不重复项目通用规则。

然后读取 `.agents/rules/figma.md`、`android-mapping.md`、`normalize-figma.md` 和 Figma 当前 Frame。

当前项目默认按 Android XML + ViewBinding 分析；如果目标项目实际使用 Compose、DataBinding 或其他 UI 技术，必须先说明并按项目现状调整。

请不要生成代码，先输出以下内容：

1. 页面结构分析
2. 可复用组件拆分
3. Activity / Fragment 拆分方案
4. Adapter 拆分方案
5. ViewModel / UIState 拆分方案
6. XML 布局方案
7. 图片资源导出清单
8. colors.xml 规划
9. strings.xml 规划
10. dimens.xml 规划
10.1 Android 屏幕适配方案：Figma Frame 宽高、Figma 逻辑屏幕宽度、Android 基准宽度、换算比例、状态栏/导航栏/底部虚拟导航栏 Insets、沉浸式/Edge-to-edge 策略、横竖屏/小屏策略、列表列宽/间距策略、固定尺寸例外清单、视觉高度拆分表、Insets owner 合同
11. 路由 / 入口规划
12. 空态 / 错误态 / loading 规划
13. 和现有代码的复用点
14. 实现风险和待确认问题
15. 页面类型判断：普通页 / 表单页 / 单列表页 / Header + 列表页 / Tab + ViewPager2 页
16. 是否存在项目列表页基类、列表封装或 Adapter 基类可复用
17. 是否存在 TextView 固定宽高风险
18. 架构门禁结论：是否允许当前页面直接实现，还是必须先拆主页容器 / Tab / 子 Fragment
19. 资源门禁结论：图片、SVG、导航图标、Tab 图标、工具栏图标是否完整
20. 缺失资源清单：缺失资源名称、所在区域、Figma node id、阻塞级别
21. 专用实现结论：当前 Frame 对应的 Fragment、ViewModel、UIState、Adapter、XML、item XML 命名和职责
22. 是否存在错误复用风险：是否准备用通用 UiBlock / ScreenFactory / Adapter 替代专用实现
23. 与黄金样板页的对齐点：页面拆分、资源命名、Adapter 结构、字体/间距缩放方式
24. 与黄金样板页的差异点：当前 Frame 特有区域、特有 item、特有状态和必须专用处理的图层
25. 图层生成依据：列出当前实现会参考的关键 Figma 图层、对应 Android XML / item / drawable 归属
26. 主导航 / Tab 页面强制拆分：如果 Frame 包含底部主导航、顶部 Tab、频道 Tab、分段 Tab 或同级页面切换入口，必须列出 ViewPager2 容器、FragmentStateAdapter、每个子 Fragment、每个子页面列表/状态归属；如果不使用 ViewPager2，必须写明用户确认的降级理由和阻塞风险
27. UI 工作流对齐：列出本页面如何遵守 `android-ui-workflow` 的页面结构、Tab/ViewPager2、RecyclerView/Scroll、文字字号/省略、Insets、沉浸式/Edge-to-edge、Dialog/Popup、资源样式规则
28. 结构化门禁字段：页面类型、页面级资源清单、是否需要 ViewPager2 + Fragment、是否需要 FragmentStateAdapter、是否允许降级
```

## 输出格式

### 页面结构分析

- 页面类型：
- 顶部区域：
- 内容区域：
- 底部区域：
- 弹窗 / 浮层：
- 列表 / 分页：

### 组件拆分

- 可复用组件：
- 仅当前页面使用组件：
- 可复用 Adapter：
- 可复用 drawable / shape：

### 资源规划

- 图片：
- 图标：
- 颜色：
- 文案：
- 尺寸：

### 屏幕适配方案

- Figma Frame：
- Figma 逻辑屏幕：
- Android 基准宽度：
- 换算比例：
- dp / sp / px 处理：
- 状态栏 / 底部虚拟导航栏 / Insets：必须遵循 `android-ui-workflow`，优先使用 WindowInsets 或项目已有 inset 工具
- 沉浸式 / Edge-to-edge：说明普通 Edge-to-edge、隐藏系统栏全屏或非沉浸式；说明 top/bottom inset 归属
- Figma 系统栏图层处理：说明 Figma 状态栏、手势条、导航栏、刘海/安全区是忽略、转 Insets，还是作为页面真实内容
- 根布局自适应策略：
- 列表列宽 / 间距策略：
- 图片比例策略：
- 固定尺寸例外：
- 视觉高度拆分：拆分状态栏、BottomNav、手势条、三键导航预览、底部安全区；业务视觉高度不得包含系统预览/安全区
- Insets owner：列出 `statusBars.top`、`navigationBars.bottom` 的唯一 owner；父容器消费后子 Fragment 不得重复消费同一个 inset
- 小屏 / 字体放大风险：

### 视觉高度拆分表

| 区域 | Figma 总高度 | 业务视觉高度 dp | 系统预览/安全区高度 | Android owner | 是否绘制 | 说明 |
| --- | ---: | ---: | ---: | --- | --- | --- |
| 状态栏 |  |  | 系统 Insets |  | 否 |  |
| BottomNav / 底部操作区 |  |  | navigationBars |  | 只绘制业务视觉区 |  |

### Insets owner 合同

| Inset 类型 | Android owner | 消费方式 | 子层是否可再次消费 | 说明 |
| --- | --- | --- | --- | --- |
| statusBars.top |  | paddingTop / marginTop / 不消费 |  |  |
| navigationBars.bottom |  | paddingBottom / marginBottom / 不消费 |  |  |

### 沉浸式 / 弹窗适配

- 顶部状态栏：
- 底部虚拟导航栏：
- 手势导航 / 三键导航：
- 全屏隐藏系统栏：
- Dialog / Popup / BottomSheet：
- 刘海 / 挖孔 / display cutout：
- 父子 Fragment Insets 分工：

### 实现方案

- Activity：
- Fragment：
- 是否需要 ViewPager2：按 `android-ui-workflow` 的 Tab / ViewPager2 规则判断
- 是否需要子 Fragment：按 `android-ui-workflow` 的父 Fragment / 子 Fragment 职责拆分
- ViewPager2 Adapter：需要时必须使用 `FragmentStateAdapter`
- 子 Fragment 清单：
- 是否需要项目列表页基类或列表封装：
- ViewModel：
- UIState：
- Adapter：
- XML：
- 路由：

### UI 工作流对齐

- 页面结构：
- Tab / ViewPager2：
- RecyclerView / Scroll：
- 文字字号 / 省略：
- Insets / Edge-to-edge：
- Dialog / Popup / BottomSheet：
- 资源样式：
- 验证门禁：

### 架构门禁

- 是否为单个页面：
- 是否为主页容器：
- 是否包含主导航 / 底部导航：
- 是否包含顶部 Tab：
- 是否允许使用单 Activity + 单 RecyclerView：
- 是否必须使用 MagicIndicator / TabLayout：
- 是否必须使用 ViewPager2：
- 是否已规划 FragmentStateAdapter：
- 是否需要 FragmentStateAdapter：
- 是否必须拆子 Fragment：
- 禁止方案：
- 是否必须专用 Fragment / Adapter / XML：
- 是否允许复用旧通用 block：
- 是否允许降级：
- 用户确认的降级方案：

### 资源门禁

- 图片资源是否完整：
- SVG / Vector 图标是否完整：
- 底部导航图标是否完整：
- 顶部 Tab 图标是否完整：
- 工具栏图标是否完整：
- normal / selected / pressed 状态是否完整：
- 是否存在占位图标风险：
- 是否需要补拉 Figma 资源：
- 是否阻塞实现：

### 图层生成依据

- 关键容器图层：
- 文本图层：
- 图片图层：
- 图标图层：
- 重复 item 图层：
- Android 文件归属：

## 分析检查

- 是否符合 `.agents/workflows/figma-process.md` 已加载的 Rules 和 Skills。
- 是否符合 `android-mapping.md` 的页面结构映射。
- 是否已经读取 `figma_layer_report.md`。
- 是否已经读取 `asset_manifest.md`。
- 是否已经读取或生成 `figma_normalize_report.md`。
- 是否输出完整 Android 屏幕适配方案；没有则禁止生成。
- 是否输出视觉高度拆分表；没有则禁止生成。
- 是否输出 Insets owner 合同；没有则禁止生成。
- 是否存在同一个 status/navigation inset 被父子 Fragment 或多个 View 重复消费；存在则禁止生成。
- 是否输出 Figma 逻辑屏幕宽度和 Android 基准宽度；没有则禁止生成。
- 是否说明 Figma 状态栏、底部手势条、虚拟导航栏、刘海/安全区的运行时处理方式。
- 是否输出沉浸式 / Edge-to-edge 策略；没有则禁止生成。
- 是否直接把 Figma px 当 dp 使用。
- 是否按 Android 基准宽度换算，并把结果沉淀到 `dimens.xml` / ratio / weight / ConstraintLayout，而不是散落硬编码。
- 是否把 Figma Frame 高清画布宽度误当逻辑屏幕宽度；常见 390/393/402/414 逻辑宽或等比放大稿必须先归一化后换算。
- 是否把 Figma Text 图层宽高直接映射成 TextView 固定宽高。
- 顶部 Tab / 底部主导航 / 同级页面切换是否遵循 `android-ui-workflow`，使用 `Tab/BottomNav + ViewPager2 + FragmentStateAdapter + 子 Fragment`；没有则必须阻塞或记录用户明确降级确认。
- 列表页是否复用项目已有列表页封装和 Adapter 封装；没有封装时是否使用标准 RecyclerView 实现并说明原因。
- 主页、主导航或底部导航是否被错误合并成一个 Activity + 一个 RecyclerView。
- Figma 中出现的导航图标、Tab 图标、工具栏图标是否全部进入资源清单。
- 是否存在用纯文字按钮、通用勾选图标或临时 vector 替代 Figma 图标。
- 是否存在用通用 UiBlock / ScreenFactory / Adapter 替代当前 Frame 专用实现的风险。
- 是否存在照搬黄金样板导致当前 Frame 特有图层丢失的风险。
- 是否存在长文案、多语言、字体缩放风险。
- 是否有固定高度导致小屏溢出风险。
- 是否有状态栏、底部虚拟导航栏、BottomNav、底部按钮、列表最后一项被遮挡风险。
- 是否有 Dialog、Popup、BottomSheet、全屏浮层被状态栏/导航栏/刘海裁切风险。
- 页面类型、页面级资源清单、是否需要 ViewPager2 + Fragment、是否需要 FragmentStateAdapter、是否允许降级等结构化字段是否完整。
- 是否需要真实接口、假数据或占位状态。
- 是否涉及图片、Bitmap、Player、Surface、WebView 等生命周期资源。
- 是否需要新增混淆规则。
- 是否需要新增依赖。
