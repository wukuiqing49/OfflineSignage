# Android 构建规则

## 事实来源

- 依赖、插件和 SDK 版本以 `gradle/libs.versions.toml` 或项目既有集中版本管理为准。
- Gradle 版本以 `gradle/wrapper/gradle-wrapper.properties` 为准。
- namespace、applicationId 与 versionName 以 `app-config.properties` 或项目既有公开应用配置为准。
- 签名秘密只以被忽略的 `keystore.properties` 或 CI 环境变量为准，不得进入公开应用配置。
- `.agents/config/project-scaffold.yml` 只负责脚手架生成基线，不替代已生成工程的 Gradle 事实。

## 版本与依赖

- 不随意升级 AGP、Gradle、Kotlin、KSP、compileSdk、targetSdk 或 minSdk。
- 新增依赖前检查项目共享模块、基础封装、公共组件、私有库和 WKQ 能力。
- 依赖与插件版本不得散落在模块构建文件中。
- 默认使用 `implementation`；只有下游 API 暴露类型时使用 `api`。
- 普通 JitPack 依赖检查 dependency repositories；JitPack 插件同时检查 `pluginManagement.repositories`。
- 接入 XPopup 等 UI 库时检查传递依赖及 Glide、Material、RecyclerView 等冲突。

## 发布与平台配置

- 不破坏 signing、flavor、buildType、manifest placeholder、输出命名、R8、资源压缩和 release 行为。
- release 签名只从被忽略的本地配置或 CI 环境变量读取，不使用 debug 签名兜底。
- 正式发布缺失必需签名时失败；本地 unsigned release 必须明确标注。
- ProGuard/R8 keep 规则保持最小范围并说明原因。
- Android 7+ 对外文件使用 FileProvider/content URI；provider 不导出并使用临时 URI 授权。
- 默认禁止明文流量；HTTP、自签 CA 或用户证书只做明确业务域的最小例外。

## 高版本与 Native

- `targetSdk >= 35` 时把 Edge-to-edge 作为强制兼容门禁。
- 不通过降低 targetSdk、全局 `fitsSystemWindows` 或全局隐藏系统栏规避遮挡。
- 修改 theme、manifest 或系统栏配置时扩大验证到 Android 15+、导航模式、横竖屏和 cutout 风险。
- native ABI 以启动及核心路径必需库的兼容交集为准。
- 涉及 `.so`、release 或打包配置时检查 16KB page size 兼容并说明未验证风险。
