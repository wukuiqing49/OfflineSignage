# core_google 接入说明

`core_google` 是可复用的 Google 平台能力模块，完整保留以下能力：

- Google 登录：Credential Manager + Google ID Token。
- Google Play Billing：订阅和一次性商品。
- AdMob 广告：Banner、插屏和激励广告。
- Google Play 应用内评分与反馈引导。
- Firebase Analytics：仅 Release 变体打包和启用，Debug 使用空实现。

Local Signage 当前只配置和调用 Google 登录、Google Play Billing；广告配置保持关闭，评分能力不触发。公共库代码和 API 不会因此删除。

## 宿主配置

宿主 `app` 模块应用 `google-services` 插件，并在本地放置与 `applicationId` 匹配的：

```text
app/google-services.json
```

该文件用于生成 Google 登录所需的 `default_web_client_id`，也为 Release Firebase 提供项目配置。文件应保持未跟踪，不提交到仓库。

应用初始化时由壳层传入 Web Client ID，再由功能模块配置商品：

```kotlin
GoogleKit.initialize(
    context = applicationContext,
    config = GoogleKitConfig(
        serverClientId = googleServerClientId,
        billingInAppProductIds = listOf("pro_lifetime"),
        billingSubscriptionIds = listOf("pro_subscription"),
        billingRequireAppAccount = false,
        enableFirebaseAnalytics = !debug
    )
)
```

`serverClientId` 必须是 Web OAuth Client ID，不是 Android Client ID。包名和统一签名证书的 SHA-1/SHA-256 必须同时登记到 Google Cloud/Firebase/Play Console。

## Google 登录

```kotlin
val result = GoogleKit.auth.signIn(activity)
result.onSuccess { account ->
    // account.idToken 可交给可信后端校验；本项目当前不依赖云端账号。
}

GoogleKit.auth.signOut(context)
```

## Google Play Billing

商品 ID 必须先在 Play Console 创建并激活。Local Signage 当前不要求自建账号，因此 `billingRequireAppAccount=false`。

```kotlin
val catalog = GoogleKit.billing.queryConfiguredCatalog()
val entitlement = GoogleKit.billing.queryEntitlement()
```

发布包还需要配置 Play 许可公钥，且只能来自被忽略的 `keystore.properties` 或 `PLAY_LICENSE_PUBLIC_KEY` 环境变量。

## Firebase 变体边界

- Debug：不解析 Firebase Analytics 依赖，`GoogleKit.firebase` 的调用全部返回不可用。
- Release：打包 Firebase Analytics，并由 `enableFirebaseAnalytics=true` 启用。
- `google-services` 插件在两个变体都保留，因为 Google 登录也需要它生成 OAuth 资源；这不代表 Debug 启用了 Firebase。

## 可选能力

### DevHub 安装与日活统计

- 参考 SiteReport 的 `GoogleKit.commercial` 封装，使用 DevHub `1.2.0`，仅开启 `CORE_ONLY`。
- `collectDeviceInfo=false`、`collectLocale=false`，不发送业务事件、媒体或局域网信息。
- DevHub 默认未授权；用户在“法律与隐私 > 使用统计”主动开启。关闭后 SDK 停止请求并清除本地待上报队列，已上报记录不自动删除。
- Firebase 保持既有 Release 行为，该开关只控制 DevHub。
- `SignageService` 运行时每小时调用 `reportActive()`，SDK 按 UTC 日期去重；服务销毁时取消回调，Activity 恢复时由 SDK 补报。
- Debug 默认不初始化 DevHub；本地测试可显式设置 `DEVHUB_DEBUG_ENABLED=true`，仍需用户授权。
- SDK 初始化或上报失败不阻断本地运行。采集开关成功表示本地 SDK 状态更新成功，不代表后台已收到数据。

共享 API 地址、客户端 App Key 和默认开关在已跟踪的 `app-config.properties`，拉取仓库即可取得。可选本机覆盖放在被忽略的 `devhub.properties`，模板见根目录 `devhub.properties.example`。读取优先级为同名环境变量、显式 `-P` 参数、本机覆盖、公开应用配置；不读取用户级 Gradle 文件中的同名 DevHub 配置，避免跨项目串用 App Key：

```properties
DEVHUB_API_URL=https://devhub.wukuiqing0409.workers.dev
DEVHUB_APP_KEY=
DEVHUB_ENABLED=true
DEVHUB_DEBUG_ENABLED=false
DEVHUB_PLAY_INTEGRITY_PROJECT_NUMBER=
```

JitPack 私有授权来自用户级 Gradle `authToken` 或 `JITPACK_TOKEN` 环境变量，不写入工程配置和日志。App Key 是客户端标识，会进入 APK；按用户要求随公开应用配置进入 Git。管理员密钥、JitPack Token 和签名密码仍不得提交。生产后台强制 Play Integrity 时必须配置匹配的项目编号，并在 Play 测试轨验证。

本次未迁移 SiteReport 的登录关联、购买验单、活动及优惠码，也不调用报告专属的 `reportFirstReportStarted()`。现有本地购买与权益逻辑继续负责解锁。

广告通过 `GoogleKitConfig.enableAds` 显式开启，并传入对应广告位 ID。评分仅在业务主动调用 `GoogleKit.rate.showIfNeeded(...)` 或 `GoogleKit.rate.show(...)` 时展示。它们属于公共库可选能力，当前 Local Signage 不调用。

## 签名

Debug 与 Release 都使用 `sharedApp` 签名。签名文件和密码只放在被忽略的 `keystore.properties` 或 CI 环境变量中；缺少统一签名时，APK/AAB、安装和签名报告任务直接失败。
