---
name: android-project-architecture-workflow
description: "用于分析和实现 Android 项目架构与共享能力：模块边界、依赖方向、基础封装、公共组件、WKQ 框架、网络、路由、存储、权限、弹窗、多媒体、计费和上传下载。新增模块、通用工具或第三方能力前使用。"
---

# Android 项目架构能力

## 项目上下文

在项目仓库中调用时读取：

- `.agents/config/project-profile.yml`
- `.agents/rules/execution.md`
- `.agents/rules/architecture.md`
- 涉及依赖时读取 `.agents/rules/build.md`

## 分析方法

1. 读取 `settings.gradle`、版本目录、相关模块构建文件和相邻实现。
2. 搜索现有共享模块、基础封装、公共组件和相同职责代码。
3. 判断能力是功能专属、跨功能共享、平台配置还是第三方包装。
4. 按架构规则选择现有能力、WKQ 框架、AndroidX 或新增本地实现。
5. 明确模块归属、依赖方向、公开类型和 `api/implementation` 边界。
6. 实施最小修改并运行架构门禁与受影响模块编译。

## WKQ 能力索引

- AndroidCoreBase：Activity/Fragment/ViewModel/Adapter/Dialog 和页面基础能力。
- AndroidCoreUtils：存储、文件/Uri、设备、Intent、图片、日志及通用工具。
- AndroidCoreRouters：路由、参数注入、拦截器、服务发现和 KSP 路由表。
- AndroidCoreNetwork：Retrofit/OkHttp/Gson/协程、错误映射、动态 Header/BaseUrl 和上传下载。
- XPopup：Center、Bottom、Attach、Drawer、ImageViewer、FullScreen、Position 等弹窗。

新增或更新前查询对应仓库 tag：

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
