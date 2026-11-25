---
name: devhub-basic-integration
description: 为本 Skill 所属 Android 项目接入 DevHub 基础统计，配置私有 JitPack token 引用、SDK 依赖、Application 初始化、手机基础信息及设备 Locale 国家语言。适用于简单接入和配置检查，不扩展到支付、权益、优惠码、活动页面或其他三方 SDK。
---

# DevHub 基础接入

用户只需要基础统计：安装、日活、手机厂商/型号/Android 版本、App 版本和设备 Locale 国家/语言。使用 `AnalyticsMode.CORE_ONLY`，不增加自定义事件。高级业务交给消费项目按需对接。

## 执行

1. 默认目标是本 Skill 所在 `.agents` 或 `.agent` 的父目录；用户指定项目则以其为准。读取目标项目规范、Gradle、Application 和既有授权流程，不从终端 cwd 或其他产品复制包名和配置。
2. 查找现有 DevHub 初始化及依赖，避免重复实例、同时引入 AAR/源码/Maven。只有用户要求接入时才修改目标 Android 项目；安装这个 Skill 不代表给 DevLib 或其他项目自动加依赖。
3. 按 [基础配置与调用](references/basic-integration.md) 配置仓库、版本和初始化。私有仓库 token 只引用本机 Gradle 属性或环境变量，检查存在性不显示值；不让用户把真实 token 粘贴进对话，不写入 Skill、源码、BuildConfig、资源或项目受跟踪配置。
4. 需要确认的非秘密参数只有目标发行包、DevHub API 地址、对应 App Key、SDK 版本及已有授权状态来源。可从项目可靠配置取得时直接复用；不能把 CMS 地址当 API，不能继承其他项目的 App Key。
5. 复用现有 Application 初始化，使用 `CORE_ONLY`、`analyticsEnabled=true`、`collectDeviceInfo=true`、`collectLocale=true`。总开关先关闭，仅在宿主确认适用告知与授权有效后启用；已有授权状态应从宿主恢复。不要为了让数据立刻出现而直接跳过该流程。
6. 运行依赖解析和受影响模块编译；修改 Application/Manifest 时验证 App 构建。按目标设备执行策略选择运行验证，远端上报没有实际核验就标为未验证。

## 数据含义与范围

- 国家是 `Locale.getDefault().country` 的有效两位区域码，语言是该 Locale 的语言标签；可能受系统/应用语言设置影响，可能缺失，不能描述成真实位置、IP 国家或 SIM 国家。不新增定位、READ_PHONE_STATE 权限或 IP 地理服务。
- 手机信息只用 SDK 的基础设备字段，不额外收集 IMEI、序列号、手机号、Android ID 或硬件唯一标识。安装身份由 SDK 自身维护，不另建一套设备标识。
- `analyticsEnabled=false` 会阻止这套基础统计，不能用它代替 CORE_ONLY。CORE_ONLY 不保存普通自定义埋点，但不意味着服务端没有其他可用业务接口。
- 授权开关回调成功不等于安装/日活已送达；日活按 SDK 的 UTC 日去重，不在每个页面重复调用初始化或强制 flush。
- 本范围不接入登录、支付验单、Pro、兑换码、活动页、反馈截图、Play Integrity 或后台管理，也不自动安装 Firebase 等三方库。SDK 自带的传递依赖按 POM 保留，不为缩小范围强行排除。
- 保留宿主备份规则；接入时按所用 SDK 版本核对安装身份/数据库排除项，不使用虚构文件名或通配符规则。

## 完成说明

列出依赖版本、配置入口、实际启用字段、授权恢复/撤回位置、构建结果及远端未验证项。项目已有 Base、UI、国际化和系统适配规则继续适用；纯 Application 接入不新增页面、弹窗或基础封装。

示例请求：“用 devhub-basic-integration 给当前项目接入基础统计，只要手机信息和国家语言。”
