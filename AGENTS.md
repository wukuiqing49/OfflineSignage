# Local Signage Agent 入口

本仓库是 Local Signage Android 广告机项目。产品与功能事实来源：

- `Local_Signage_Optimized_Spec_CN.md`
- `docs/LOCAL_SIGNAGE_FEATURE_SPEC_CN.md`
- `docs/LOCAL_SIGNAGE_IMPLEMENTATION_PLAN_CN.md`

## 任务路由

- 创建或调整 Android 工程：`.agents/workflows/create-project.md`
- 调整模块或共享能力：`.agents/workflows/change-architecture.md`
- 实现 Android 页面：`.agents/workflows/implement-page.md`
- 修改 Gradle、Manifest 或发布配置：`.agents/workflows/change-build.md`
- 修复 Android 问题：`.agents/workflows/fix-bug.md`
- 修改多语言资源：`.agents/workflows/localize-content.md`

## 项目事实

- Kotlin + XML + ViewBinding，MVVM。
- `app` 是应用壳；`core/` 和 `feature/` 是模块容器。
- 播放、Server、同步和恢复逻辑不得依赖 Activity。
- 产品默认 Local-first / Offline-first，不引入账号、云端或公网管理。
- 内容模型固定为 `Resource -> Scene -> Playlist`。
- 公开配置在 `app-config.properties`，签名秘密只能来自未跟踪配置或环境变量。

## 红线

- 不提交密钥、证书、密码、Token 或用户隐私数据。
- 不把局域网控制 API 暴露为公网服务。
- 不用降低 targetSdk、吞异常或删除功能规避兼容问题。
- 完成修改后运行受影响的门禁，并说明未验证范围。

执行时先读取相关 Workflow，再读取它声明的 Rules、Skills 和配置事实。
