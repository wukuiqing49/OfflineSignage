# 基础配置与调用

## 目录

- [私有库 token 与依赖](#私有库-token-与依赖)
- [初始化与最小调用](#初始化与最小调用)
- [基础字段](#基础字段)
- [验证边界](#验证边界)

本参考随 Skill 一起迁移，不依赖本机其他工程。示例按 DevHub 本地 `v1.2.0` 标签核对，源码包名 `com.devhub.sdk`，minSdk 23、JVM 17；不代表已验证 JitPack 远端产物或 Android 17 运行。不能根据 SDK 自身 compileSdk 35 降低消费项目的 compileSdk/targetSdk。

## 私有库 token 与依赖

沿用项目已有仓库认证方式。新配置时，token 由用户在用户级 Gradle 的 gradle.properties 中设置 `authToken`，或由运行环境提供 `JITPACK_AUTH_TOKEN`。只检查变量是否配置，不读取/输出其值，也不输出整个 Gradle 用户配置或环境。

JitPack token 仅用于构建下载；与 DevHub App Key、CMS 密码或管理员 API Key 无关。不要将其传入 Android 运行时。没有私有库授权时说明缺口，不能假装依赖已经解析成功。

Kotlin DSL 的 settings 仓库片段：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            credentials {
                username = providers.gradleProperty("authToken")
                    .orElse(providers.environmentVariable("JITPACK_AUTH_TOKEN"))
                    .orNull
            }
            content { includeGroup("com.github.wukuiqing49") }
        }
    }
}
```

Groovy DSL 的同等仓库片段，合并进现有 repositories，不重复创建 dependencyResolutionManagement：

```groovy
maven {
    url = uri('https://jitpack.io')
    credentials {
        username = providers.gradleProperty('authToken')
            .orElse(providers.environmentVariable('JITPACK_AUTH_TOKEN')).orNull
    }
    content { includeGroup('com.github.wukuiqing49') }
}
```

复用已有 JitPack 仓库时，检查其他私有依赖是否也使用相同凭据和 group，不能覆盖原过滤规则。凭据尚未配置时先处理环境，避免带空值反复请求。

依赖示例：`com.github.wukuiqing49:DevHub:1.2.0`。宿主已有版本目录时把版本、坐标和别名写入该目录，在正确的宿主集成模块使用 implementation；不把这个示例版本当作永远最新，不自动依赖 main-SNAPSHOT。公开仓库不需要私有认证时保留普通仓库配置即可。

## 初始化与最小调用

下面是可复用的函数示例，应合并到已有 Application 集成位置，不机械新增单独管理器或改写 Application 继承关系。`apiUrl` 和 `appKey` 来自目标项目的非管理员配置；不在 Skill 保存真实值。App Key 是 App 标识，不应当作可以隐藏在 APK 的服务端秘密。

```kotlin
import android.content.Context
import com.devhub.sdk.AnalyticsMode
import com.devhub.sdk.DevHub
import com.devhub.sdk.DevHubConfig

/** 初始化基础安装与活跃统计；授权有效后由宿主显式启用。 */
fun initializeBasicDevHub(context: Context, apiUrl: String, appKey: String): DevHub =
    DevHub.initialize(
        context.applicationContext,
        DevHubConfig(
            apiUrl = apiUrl,
            appKey = appKey,
            collectionEnabled = false,
            analyticsEnabled = true,
            analyticsMode = AnalyticsMode.CORE_ONLY,
            collectDeviceInfo = true,
            collectLocale = true,
            allowedEventProperties = emptySet(),
            allowFeedbackImages = false,
        ),
    )
```

在 Application 创建时调用一次，保留实例，检查 `initializationError`。宿主恢复已保存且仍有效的授权，或用户首次完成授权后，调用：

```kotlin
devhub.setCollectionEnabled(true) { result ->
    if (!result.successful) {
        // 将 result.error 接入宿主既有诊断，不能当作数据已送达。
    }
}
```

撤回时调用 `setCollectionEnabled(false)` 并处理清理失败；开关不会删除服务端历史数据。示例中的 devhub 是 Application 持有的同一实例，不在每个 Activity 重建。配置/App Key 冲突不能通过重复 initialize 解决；按 SDK 的 close/重建流程处理。

正常启用后 SDK 管理安装注册、Activity 恢复时的日活及已有重试机制。无需增加页面生命周期监听、定时器、自定义 track、首次报告事件或手动轮询。日活是 UTC 日去重，不将回调次数当活跃人数。

## 基础字段

| 配置或能力 | 本次用途 |
| --- | --- |
| analyticsMode = CORE_ONLY | 不保存普通自定义事件 |
| analyticsEnabled = true | 保留安装与日活统计链路 |
| collectDeviceInfo = true | manufacturer、model、androidVersion |
| collectLocale = true | Locale 区域 country 和语言标签 language |
| SDK 基础注册 | App 版本、安装身份和首次启用时间 |

国家可能为空；只接受 SDK 支持的有效区域码。不要补造国家，不增加定位权限，不声称设备 Locale 是真实所在地。

## 验证边界

- 对照所选版本核实上述字段存在，校验 API 为目标部署的 HTTPS 根地址，App Key 与目标包对应；不要拿 CMS 页面地址初始化。
- 验证私有库解析、宿主编译、Manifest 合并及 Application 初始化；不创建 CI、不自动发布 SDK。
- 沿用现有隐私流程与备份规则，核实初始关闭、恢复授权及撤回，不输出 token 或安装密钥。
- 只有实际运行后确认后台安装、设备字段及日活记录，才能报告远端上报成功。SDK 开关成功、App 构建成功或后台已有历史数据均不等价。
- 本次创建 Skill 不给当前 DevLib 添加 SDK 依赖，也不改变它排除 DevHub 来源模块的事实。
