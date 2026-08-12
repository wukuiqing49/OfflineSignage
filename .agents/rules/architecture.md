# Android 架构规则

## 模块边界

- `core/` 和 `feature/` 是目录容器，不直接注册为 Gradle module。
- 实际模块使用 `:core:<module>` 和 `:feature:<module>`。
- `app` 只负责应用壳、全局初始化和顶层导航，不承载大量业务页面。
- `core` 子模块不得依赖 `app` 或 feature 模块。
- feature 模块不得形成循环依赖；`feature_res` 不依赖功能模块。
- 跨功能逻辑放职责匹配的 core 子模块；跨功能资源放共享资源模块。
- 功能专属 UI、状态和编排留在对应 feature 模块。

## 复用优先级

1. 目标项目已有共享模块、基础封装和公共组件。
2. AndroidCoreBase、AndroidCoreUtils、AndroidCoreRouters、AndroidCoreNetwork、XPopup。
3. AndroidX 或已有第三方依赖。
4. 确认前三层不能满足后，才新增本地通用能力或依赖。

## WKQ 能力

- Activity、Fragment、ViewModel、Adapter 按项目约定继承 AndroidCoreBase 对应基类。
- AndroidCoreUtils 的 Kotlin `object` 和扩展函数通过组合或直接调用使用，不要求业务类继承。
- AndroidCoreUtils 只由 app 壳的 `Application` 统一初始化一次。
- 包装模块向下游暴露 WKQ 类型时使用 `api`；完全隔离第三方类型时使用 `implementation`。
- 接入或更新 WKQ 依赖前查询对应 GitHub 仓库最新 tag，并检查与当前构建基线的兼容性。

## 通用能力归属

- 网络、数据库、上传下载、图片、位置、计费、权限、文件、路由、存储和弹窗能力不得在功能模块重复实现。
- 网络安全 XML 和 FileProvider 属于 app 壳平台配置，不放进功能模块。
- 模块归属不明确时，先检查 `settings.gradle`、相关模块构建文件和相邻代码。
