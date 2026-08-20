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

广告通过 `GoogleKitConfig.enableAds` 显式开启，并传入对应广告位 ID。评分仅在业务主动调用 `GoogleKit.rate.showIfNeeded(...)` 或 `GoogleKit.rate.show(...)` 时展示。它们属于公共库可选能力，当前 Local Signage 不调用。

## 签名

Debug 与 Release 都使用 `sharedApp` 签名。签名文件和密码只放在被忽略的 `keystore.properties` 或 CI 环境变量中；缺少统一签名时，APK/AAB、安装和签名报告任务直接失败。
