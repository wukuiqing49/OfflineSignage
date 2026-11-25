# Android 17 系统栏与沉浸式

核验日期：2026-10-27。适用于 DevLib 的 XML/ViewBinding 页面及其独立窗口；当前 targetSdk 37、minSdk 26。其他项目按真实 SDK 和基础封装使用。稳定规则以 `.agents/rules/ui.md` 为准。

## 实现顺序

1. 读取页面所继承的 Base、主题及窗口初始化链路，确认现有 Edge-to-edge、系统栏图标与 Insets 处理。已有能力优先复用；基础库源码保持用户确认的边界，确有缺口时在宿主适配。
2. 普通页面保留系统导航，使用已有 Edge-to-edge 入口；缺失时选择与实际 AndroidX 版本兼容的 `enableEdgeToEdge()` 等入口。Android 15+ 且 target 35+ 已有边到边要求，Android 17 继续适用，不把它描述成 API 37 新增 API。
3. 根据背景设置状态栏和导航栏图标明暗，覆盖深浅主题及页面切换。三键导航的对比度保护按实际效果处理，不全局关闭保护，也不依赖旧系统栏着色 API 完成适配。
4. 用 `ViewCompat.setOnApplyWindowInsetsListener` 按实际 View 分配 `systemBars()`、`displayCutout()` 和 `ime()`；保留初始 padding/margin，每次基于初始值计算，避免重复分发后累加。键盘与导航栏的底部避让按几何重叠关系取值，不盲目相加；不要无条件消费 Insets 导致子 View 或兄弟 View 无法处理。

## 全屏沉浸式

- 图片预览、阅读、视频及全屏编辑等确有需求时，通过 `WindowCompat.getInsetsController(window, window.decorView)` 获取控制器，使用 `hide/show(WindowInsetsCompat.Type.systemBars())` 或明确需要的栏类型；使用 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 支持边缘手势临时显示系统栏。
- 新增业务代码不使用 `SYSTEM_UI_FLAG_*`、`systemUiVisibility` 或 `FLAG_FULLSCREEN` 拼装实现；兼容旧系统交由 AndroidX。既有基础库出现旧 API 时记录适配范围，不借此批量重写。
- 系统栏状态属于 Window。共享 Activity 的 Fragment 由宿主协调当前页面策略；进入、离开、返回及重建时恢复对应状态、图标明暗和行为。不能只 hide 而无恢复，也不能在每次焦点变化时无条件强制隐藏，干扰权限框、键盘或临时系统栏。
- 全屏背景可延伸到边缘，关闭按钮和重要操作仍避让刘海、系统手势及可见系统栏。退出方式必须可发现且无障碍可用。
- 桌面窗口模式的系统标题栏可持续显示，不能假定 hide 会清除所有系统 UI；使用 `systemBars()` 或 `captionBar()` 获取实际避让区域，保留窗口控制的可操作性。

## 弹窗与可调整窗口

- Dialog、BottomSheet 和全屏 Popup 按其实际 Window 配置系统栏、cutout 与 IME；先检查 XPopup/Base 已处理部分，避免复制 Activity 的 Insets 后重复留白。独立窗口关闭后核对宿主策略。
- Android 17 / target 37 在适用的大屏场景取消方向与可调整大小限制的开发者退出机制。使用当前窗口尺寸适配旋转、分屏、自由窗口及外接显示，不用锁方向或固定整屏高度规避；具体条件和例外以官方行为变更为准。

## 验收

任务说明中记录：页面模式、Base 复用点、Window 策略负责人、top/bottom/IME Insets owner、进入与退出恢复方式。

按影响检查 API 26、35/36 与 37，手势/三键导航、深浅主题、键盘、刘海、横竖屏、字体缩放、分屏/桌面窗口；全屏场景增加边缘临时唤出、返回、弹窗和页面重建。明确哪些是静态审查、构建、实际运行或视觉验证。

本项目设备命令按 executionPolicy 排除，未运行场景记录未验证。现有 UI 静态脚本只能提示部分风险，不能根据关键字存在就认定沉浸式正确。

## 官方依据

- [边到边布局](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [隐藏系统栏与沉浸式](https://developer.android.com/develop/ui/views/layout/immersive)
- [Android 17 目标版本行为变更](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Android 17 大屏方向与可调整窗口说明](https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html)
