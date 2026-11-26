# Gson 通过注解反序列化登录令牌；保留默认构造函数和标注字段。
-keep,allowoptimization,allowobfuscation class com.wkq.google.model.GoogleIdTokenClaims {
    public <init>();
    @com.google.gson.annotations.SerializedName <fields>;
}
# Fragment 参数使用 Java Serializable；保留名称和成员以维持默认 serialVersionUID。
# 允许未使用的配置类整体裁剪；普通直接调用的配置/奖励模型无需 keep。
-keep,allowshrinking class com.wkq.google.billing.ui.GoogleBillingPlanConfig { *; }
-keep,allowshrinking class com.wkq.google.billing.ui.GoogleBillingPageConfig { *; }
-keepattributes Signature
-keepattributes *Annotation*
