# 按实际反射、序列化、路由或 WebView JSBridge 使用情况补充最小 keep 规则。

# Ktor 的 JVM 调试探测引用了 Android 不提供的可选 JDK 管理类。
-dontwarn java.lang.management.**
