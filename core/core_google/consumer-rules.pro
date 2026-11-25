# Gson 通过注解反序列化登录令牌；保留默认构造函数和标注字段。
-keep,allowoptimization,allowobfuscation class com.wkq.google.model.GoogleIdTokenClaims {
    public <init>();
    @com.google.gson.annotations.SerializedName <fields>;
}
# Fragment 参数使用 Java Serializable；保留成员以维持默认 serialVersionUID。
-keep class com.wkq.google.billing.ui.GoogleBillingPlanConfig { *; }
-keep class com.wkq.google.billing.ui.GoogleBillingPageConfig { *; }
-keep class com.wkq.google.ads.GoogleReward { *; }
-keep class com.wkq.google.GoogleKitConfig { *; }
-keepclassmembers class com.wkq.google.ads.GoogleReward {
    <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*
