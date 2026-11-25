---
name: android-project-architecture-workflow
description: "用于分析和实现 Android 项目架构与共享能力：模块边界、依赖方向、基础封装、公共组件、WKQ 框架、网络、路由、存储、权限、弹窗、多媒体、计费和上传下载。新增模块、通用工具或第三方能力前使用。"
---

# Android 项目架构能力

## 项目上下文

以下项目路径均相对目标工程；脚本和 references 相对本 Skill。跨项目调用读取目标规则与事实，缺失时从实际源码/构建核实并说明，不套用 DevLib 的包名、模块或执行策略。脚手架生成参数使用用户选定配置。

在目标项目中读取适用且存在的文件：

- `.agents/config/project-profile.yml`
- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- 涉及依赖时读取 `.agents/rules/build.md`
- 目标为 DevLib 且存在 `.agents/rules/library-organization.md` 时读取该项目规则；从模块清单读取分类与物理目录，并检查 settings 的 projectDir 映射，不按前缀或来源推断归属。其他项目不套用 DevLib 分类或数量。

## 分析方法

1. 读取 `settings.gradle`、版本目录、相关模块构建文件和相邻实现。
2. 搜索现有共享模块、基础封装、公共组件和相同职责代码。
3. 判断能力是功能专属、跨功能共享、平台配置还是第三方包装；区分架构职责、独立功能、三方服务适配与内部支撑，功能域路由不充当库分类。
4. 按架构规则选择现有能力、WKQ 框架、AndroidX 或新增本地实现。
   导航按目标架构规则的“导航与路由选型”评估，记录实际需求、既有契约及成本，不将模块数量当作结论。
5. 明确模块归属、依赖方向、公开类型和 `api/implementation` 边界。
6. 实施最小修改并运行架构门禁与受影响模块编译。

## WKQ 能力索引

- AndroidCoreBase：Activity/Fragment/ViewModel/Adapter/Dialog 和页面基础能力。
- AndroidCoreUtils：存储、文件/Uri、设备、Intent、图片、日志及通用工具。
- AndroidCoreRouters：路由、参数注入、拦截器、服务发现和 KSP 路由表；适用条件以目标架构规则为准。
- AndroidCoreNetwork：Retrofit/OkHttp/Gson/协程、错误映射、动态 Header/BaseUrl 和上传下载。
- XPopup：Center、Bottom、Attach、Drawer、ImageViewer、FullScreen、Position 等弹窗。

## 页面基础能力映射

页面设计前核实本轮相关能力，结果合入原设计交接，不另维护重复 API 清单：

| 交互职责 | 优先核实的能力 | 证据与边界 |
| --- | --- | --- |
| 页面与窗口 | 已有 BaseActivity/BaseFragment、全屏 Base | 真实继承链、系统栏和 Insets owner、恢复行为 |
| 状态与任务 | BaseViewModel、已有任务或持久化能力 | 输入/选择归属、取消、重复提交和进程重建；ViewModel 不等于持久化 |
| 列表与分类 | Base Adapter、项目 Tab/分页封装 | 实际更新与选择 API，不为使用 DiffUtil 强制替换 Base |
| 弹层与预览 | XPopup、已有 Dialog/Popup | 具体类型和调用示例、结果传递、窗口/键盘与关闭语义 |
| 导航 | 简单导航或已有 Router | 按架构规则选型，核实参数、返回栈、外部入口及结果 |
| 工具与领域任务 | Utils、按需 Network 及对应功能库 | 从源码/依赖版本核实公开 API，不猜测库能力或把业务策略下沉 |

每项只记录所需的“职责 → 具体类型/资源及代码位置 → 缺口/最小方案”；已有上游调查直接复用。设计由 ui-design 主导，交互专项补充行为，实现消费同一映射。未接入的能力仅在本轮确有需要时按架构与构建规则处理，不把能力索引当作必装清单。

仅在接入或更新上游 WKQ 依赖版本时查询对应仓库 tag；普通功能开发与现有本地源码模块复用不要求查最新版本，也不自动切换为 Maven AAR：

```text
https://github.com/wukuiqing49/AndroidCoreBase
https://github.com/wukuiqing49/AndroidCoreUtils
https://github.com/wukuiqing49/AndroidCoreRouters
https://github.com/wukuiqing49/AndroidCoreNetwork
https://github.com/wukuiqing49/XPopup
```

版本写入集中版本管理；仓库和 pluginManagement 配置由构建 Skill 处理。

## 验证

```powershell
python .agents/skills/android-project-architecture-workflow/scripts/validate_architecture.py --project-root .
```

新增依赖后补充依赖解析和受影响模块编译；涉及运行时网络、路由、文件或弹窗能力时补充对应 smoke check。

架构脚本检查字面量 include、显式 projectDir、位置/命名 path 依赖、分层边界及静态循环。动态表达式、类型安全访问器、条件配置与外部脚本需另核实 Gradle 实际图；警告或静态检查通过不等于完整依赖图已验证。维护脚本运行 `.agents/scripts/tests/test_validate_architecture.py`；示例与未注册模块不参与主工程依赖环检查。

## Android 17 / API 37

涉及 SDK 37、Android 17 迁移、局域网权限或大屏行为时，读取 [Android 17 兼容基线](../android-build-workflow/references/android-17.md)。同步项目与生成配置，按能力选择检查；构建通过不能代替运行验证。

功能实现必须按已加载规则复用 Base；新增及修改的类型与方法写中文 KDoc/JavaDoc。页面同时执行 UI、国际化及屏幕/系统适配检查，完成时说明复用项和未验证项。
