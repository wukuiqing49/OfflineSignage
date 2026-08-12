# Figma Code 主执行模板

## 目录

- [目标](#目标)
- [前置条件](#前置条件)
- [执行命令](#执行命令)
- [删除旧实现](#删除旧实现)
- [按施工图实现](#按施工图实现)
- [视觉逼真规则](#视觉逼真规则)
- [Insets 实现规则](#insets-实现规则)
- [验证闭环](#验证闭环)
- [输出要求](#输出要求)

## 目标

按 `page_task_<page>.md` 施工图实现当前 Figma Frame。此阶段必须先移除旧页面实现的错误布局影响，再按任务卡重建。

## 前置条件

先完成 `.agents/workflows/figma-code.md` 的加载步骤，再读取 `android-mapping.md`、`generate-code.md`、`validate-result.md` 以及当前页面的 `figma_layer_report.md`、`asset_manifest_<page>.md`、`figma_normalize_report.md`、`page_task_<page>.md`。

进入代码前必须确认页面任务卡已经通过以下硬门槛：

- 项目基类 / 组件审计已完成，且列出 Activity、Fragment、ViewModel、Adapter、ViewPager/FragmentStateAdapter 的采用结论。
- 页面入口链路合同已完成，明确 Launcher、Host、目标 Fragment、路由/创建方法和旧页面替换范围。
- Insets owner 合同已完成，明确 Figma 系统栏预览与 Android 运行时 Insets 的拆分。
- 运行时视觉风险清单已完成，覆盖 status inset、navigation inset、可滚动区域、FAB/BottomNav、小屏高度降级。

## 执行命令

开始实现当前 Figma Frame 对应页面。

要求：

1. Android XML + ViewBinding。
2. 不使用 Compose。
3. 不使用 DataBinding。
4. Fragment / Activity 基于目标项目已有 Base；不存在才用 AndroidX 标准类并说明。
5. 公共资源放资源模块。
6. 主布局优先 ConstraintLayout。
7. 禁止直接使用 Figma px 作为 dp。
8. 按任务卡中的 Android 基准宽度和换算公式落地。
9. 禁止写死 Figma Frame 宽高。
10. 禁止按 Figma Frame 高度等比固定整屏、主页容器、ViewPager2、RecyclerView 或首屏内容高度；根布局填满真实屏幕，弹性内容区吃剩余高度。
11. 文案放 strings.xml。
12. 颜色放 colors.xml。
13. 尺寸放 dimens.xml。
14. 列表使用 RecyclerView。
15. Tab / BottomNav / 同级页面切换必须使用 ViewPager2 + FragmentStateAdapter + 子 Fragment。
16. Adapter 不写业务逻辑。
17. 如果项目基类来自 `core:core_base`、AAR 或外部依赖，必须按任务卡审计结果使用；不能只因当前 feature 模块源码搜不到就降级为 AndroidX 标准类。
18. 实现入口必须严格落地任务卡中的 Launcher / Host / Feature 页面链路；新增 Splash 时 Launcher 必须是 Splash，Host 只承载页面，不混用职责。

请生成或更新：

1. XML 布局文件
2. Fragment / Activity
3. ViewModel
4. Adapter
5. Bean / UIState
6. strings.xml
7. colors.xml
8. dimens.xml
9. drawable / shape / selector
10. 修改文件清单
11. 验证结果

## 删除旧实现

实现前先识别当前页面旧实现：

- 旧 Fragment / Activity / ViewModel / Adapter
- 旧 XML / item / include
- 旧 dimen / style / drawable / selector
- 旧资源映射和占位图标

若用户要求“删除旧的”或旧实现已偏离任务卡，必须按以下规则处理：

- 删除或替换当前页面专属旧文件。
- 保留仍被任务卡映射使用的资源。
- 不删除项目脚手架、公共 Base、公共模块和无关页面。
- 不允许只在旧 UI 上局部修补后声称重建。

## 按施工图实现

逐行对照页面任务卡：

1. 每个关键 Figma 图层必须有 Android 落点。
2. 每个关键 `dimen` 必须与任务卡目标值一致，默认误差不超过 `2dp` / `1sp`。
3. 每个关键 drawable / image / icon 必须来自资源清单或明确降级。
4. 每个用户可见文案必须进 strings。
5. 重复 item 必须进入 RecyclerView item。
6. 图片尺寸优先使用 ratio、列宽计算或任务卡固定视觉例外。

## 视觉逼真规则

- 先落地 Figma 视觉尺寸，再考虑 Android 触控。
- 禁止为了“更好点”放大搜索框、图标、按钮、BottomNav、卡片高度。
- 扩大点击区域时，用容器或 hit slop，不改大视觉元素。
- BottomNav 视觉高度和系统 bottom inset 必须分离。
- Figma 状态栏、手势条、三键导航预览默认不绘制。
- 三键导航和手势导航下都不能让 BottomNav 视觉区被系统栏撑大。
- 首屏区域预算必须接近任务卡；如果直播卡片可见数量明显少于 Figma，继续调整。
- 首屏纵向必须采用“固定视觉块 + 弹性内容区”模型：Header/Tab/BottomNav 等固定区按 360dp 基准换算，RecyclerView/ViewPager2/ScrollView 使用 `0dp + 约束` 填充剩余高度。
- Figma Frame 高度只用于首屏可见数量和区域比例参考，不得落成 Android 根布局高度或主内容高度。

## Insets 实现规则

- 按任务卡的 Insets owner 合同写代码。
- 同一个 bottom inset 只能被一个 owner 消费。
- 父容器或 BottomNav/spacer 消费 `navigationBars.bottom` 后，子 RecyclerView 不再重复加系统 bottom inset。
- Dialog / Popup / BottomSheet 不能套用普通 Fragment Insets，必须按 Window 或锚点处理。
- Header/Toolbar 的运行时高度应按 `statusBars.top + 业务视觉高度` 计算；如果任务卡声明 Figma Header 总高度含状态栏预览，不得再用该总高度叠加 `statusBars.top`。
- BottomNav/底部操作区的运行时高度应按 `业务视觉高度 + navigationBars.bottom` 计算；三键导航和手势导航只改变安全区，不改变业务视觉高度。
- ViewPager2/RecyclerView/ScrollView 必须约束在 Header 与 BottomNav/底部操作区之间，不能被系统栏二次挤压。

## 验证闭环

实现后必须运行：

```powershell
python .agents/skills/android-figma-workflow/scripts/validate_figma_output.py `
  --module-src app/src/main/java --module-src feature/feature_app/src/main/java `
  --module-res app/src/main/res --module-res feature/feature_app/src/main/res --module-res feature/feature_res/src/main/res `
  --asset-manifest .ai-work/figma/output/asset_manifest_<page>.md `
  --analysis-report .ai-work/figma/output/page_task_<page>.md `
  --require-screen-adaptation `
  --require-pager-navigation

python .agents/skills/android-ui-workflow/scripts/validate_ui_output.py `
  --module-src app/src/main/java --module-src feature/feature_app/src/main/java `
  --module-res app/src/main/res --module-res feature/feature_app/src/main/res --module-res feature/feature_res/src/main/res `
  --require-pager-navigation

.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --stacktrace
```

条件允许时继续做运行截图验收：

1. 安装并打开页面。
2. 截取目标设备截图。
3. 对比 Figma 截图。
4. 若 Header 高度、BottomNav、系统栏、直播卡片可见数量、主要间距明显不一致，继续调整后重新验证。
5. 若真机截图出现“顶部空白过大”“BottomNav 被系统三键导航挤压”“首屏可见内容明显少于任务卡”，优先回查 Insets owner 和视觉高度拆分，不要只调 margin。

## 输出要求

最终说明：

- 删除/替换了哪些旧实现
- 新增/修改文件
- 关键尺寸合同落地情况
- Insets owner 落地情况
- 与 Figma 的已知差异
- 验证结果
- 未做的截图/真机验收风险
