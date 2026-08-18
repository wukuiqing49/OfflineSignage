package com.wkq.google

import com.wkq.google.billing.gate.GoogleFeatureGateConfig
import com.wkq.google.billing.ui.GoogleBillingPlanConfig

/**
 * Google 工具模块统一配置。
 *
 * 外部 App 在 Application 中初始化 GoogleKit 时传入此配置。
 */
data class GoogleKitConfig(
    /** Google 登录使用的 Web Client ID，必须来自 Google Cloud / Firebase 控制台。 */
    val serverClientId: String = "",
    /** 是否启用广告。关闭时广告相关方法会直接返回空或失败结果。 */
    val enableAds: Boolean = false,
    /** Banner 广告位 ID。 */
    val bannerAdUnitId: String = "",
    /** 插屏广告位 ID。 */
    val interstitialAdUnitId: String = "",
    /** 激励广告位 ID。 */
    val rewardedAdUnitId: String = "",
    /** 一次性内购商品 ID 列表，例如去广告、永久会员。 */
    val billingInAppProductIds: List<String> = emptyList(),
    /** 订阅商品 ID 列表，例如月会员、年会员。 */
    val billingSubscriptionIds: List<String> = emptyList(),
    /** 支付页展示和购买使用的方案配置，由宿主 App 初始化时传入。 */
    val billingPlans: List<GoogleBillingPlanConfig> = emptyList(),
    /** 返回 false 时禁止发起购买，例如宿主要求用户先登录账号。 */
    val billingPurchaseAllowed: () -> Boolean = { true },
    /** 当前应用账号 ID；计费层仅使用其 SHA-256 值关联订单，不传递原始账号信息。 */
    val billingAccountIdProvider: () -> String = { "" },
    /** 是否强制按宿主应用账号过滤购买；不自建账号的应用应保持 false。 */
    val billingRequireAppAccount: Boolean = false,
    /** 功能付费规则配置，用于统一控制哪些能力需要付费权益。 */
    val featureGateConfig: GoogleFeatureGateConfig = GoogleFeatureGateConfig(),
    /** 是否启用 Firebase Analytics。关闭时埋点方法会直接返回 false。 */
    val enableFirebaseAnalytics: Boolean = false,
    /** 评分弹窗展示的应用名称。为空时默认读取应用 label。 */
    val appName: String = "",
    /** 用户反馈接收邮箱，评分较低时用于拉起邮件客户端。 */
    val feedbackEmail: String = ""
)
