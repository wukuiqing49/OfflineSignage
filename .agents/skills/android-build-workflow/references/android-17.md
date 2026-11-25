# Android 17 / API 37 兼容基线

核验日期：2026-10-26。版本是可维护基线，不要求无关项目自动升级。

## 工具与配置

- 当前 DevLib 和脚手架：compileSdk/targetSdk 37，minSdk 26；AGP 9.1.1、Gradle 9.3.1、Kotlin 2.2.10、KSP 2.2.10-2.0.2、JDK 17。
- 以官方 AGP 支持表为准，不用 suppressUnsupportedCompileSdk 掩盖不支持的工具链。SDK 包使用 sdkmanager --list 确认，本次为 platforms;android-37.0；禁止伪造目录或 platform 元数据。
- 本仓库为保留基础库现有 Kotlin 插件与 DSL，显式设置 android.builtInKotlin=false、android.newDsl=false。这是 AGP 9.x 临时过渡，AGP 10 前必须迁移；新项目可在完整迁移插件、KSP 和模板后采用内置 Kotlin，不能仅删除开关。
- 修改版本时同步实际版本目录、Wrapper、project-profile.yml、project-scaffold.yml。生成器仅在临时目录自测，不覆盖迁入源码。

## 按能力检查

1. 分开检查所有运行于 Android 17 的应用变化和仅 target 37+ 的变化。不能把 compileSdk 提升视为运行兼容完成。
2. 局域网：target 37+ 的直接 LAN 通信需 ACCESS_LOCAL_NETWORK 或系统介导的选择器。宿主声明、说明并运行时请求权限；拒绝、永久拒绝、撤销及配置恢复必须有结果。普通公网请求、本地文件操作不应强制依赖 LAN 权限。独立引擎不弹权限 UI。
3. 大屏：检查 sw>=600dp 的可调整大小、方向、多窗口；不能依赖旧方向/宽高比退出开关。使用当前窗口尺寸、单一 Insets owner；旋转、分屏和进程恢复不得丢失输入、选择和结果。
4. 反射：检查 MessageQueue 私有字段/方法和修改 static final 字段；检查依赖 SDK，不通过放宽隐藏 API 规避。
5. 文件分享显式授予 content URI 临时权限；检查跨工作资料回环连接、WebView UA 缩减、TLS/证书透明度、后台音频及内存限制。仅在实际使用这些能力时扩展专项验证。
6. 既有 Edge-to-edge、FileProvider、16KB native 页大小门禁继续适用；API 37 不代替这些检查。

UI 系统栏专项（2026-10-27 补充核验）：普通边到边布局、全屏沉浸式、独立弹窗窗口及桌面标题栏的实现与恢复要求见 [Android 17 系统栏与沉浸式](../../android-ui-workflow/references/system-bars-android17.md)。该补充不代表重新核验本文全部工具链与其他平台行为。

## 验证与汇报

- 执行构建静态校验、生成器测试、app assembleDebug/assembleDebugAndroidTest 和受影响 JVM 测试；release/R8、独立库产物按影响执行。
- 运行验证矩阵至少记录 API 37、API 26 回归、手机/平板、多窗口、权限允许/拒绝/撤销；若用户排除设备测试，标记未执行，不使用构建结果冒充运行兼容。
- 无需创建 CI。报告区分配置、静态、构建、JVM、设备、视觉、生产事务验证。

## 官方依据

- https://developer.android.com/build/releases/about-agp
- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/about/versions/17/summary
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/privacy-and-security/local-network-permission
