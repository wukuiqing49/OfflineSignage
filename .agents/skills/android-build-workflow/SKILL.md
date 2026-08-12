---
name: android-build-workflow
description: "用于修改和验证 Android 构建系统：Gradle、版本目录、依赖、插件、KSP/KAPT、SDK、JitPack、applicationId/version、manifest、签名、R8、打包、native、16KB page size 和高 targetSdk 兼容。"
---

# Android 构建能力

## 项目上下文

在项目仓库中调用时读取：

- `.agents/config/project-profile.yml`
- `.agents/rules/execution.md`
- `.agents/rules/build.md`
- 涉及依赖归属时读取 `.agents/rules/architecture.md`

## 检查顺序

1. 读取 settings、根构建、Wrapper、版本目录、相关模块构建文件和 manifest。
2. 从实际文件确认 AGP、Gradle、Kotlin、SDK、Java/Kotlin target、插件、仓库和构建变体。
3. 判断任务影响依赖解析、公开类型、SDK 行为、签名、R8、资源压缩、native 或发布产物的范围。
4. 在集中事实源和正确模块中实施最小修改。
5. 检查传递依赖、JitPack/pluginManagement、敏感信息和高版本行为。
6. 运行构建门禁以及与影响范围匹配的 Gradle 任务。

## 常见能力

- 维护版本目录和插件 alias，协调 AGP/Gradle/Kotlin/KSP/SDK 兼容性。
- 配置普通依赖、KSP/KAPT processor、JitPack 仓库和 Gradle 插件仓库。
- 维护动态 applicationId、语义化 versionName/versionCode 和 flavor/buildType。
- 处理 release 签名、R8/ProGuard、资源压缩、merged manifest 和 APK 验证。
- 检查 network security config、FileProvider、exported 和 URI grant。
- 检查 ABI 交集、native 打包和 16KB page size 风险。

## 验证

```powershell
python .agents/skills/android-build-workflow/scripts/validate_build_output.py --project-root .
```

优先运行受影响模块最小 compile/test。涉及 app 壳、manifest、签名、release 或 native 时按构建规则运行 app 级任务，并明确未执行的设备、凭据或产物检查。
