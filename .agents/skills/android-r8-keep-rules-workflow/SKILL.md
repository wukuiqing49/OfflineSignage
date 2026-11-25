---
name: android-r8-keep-rules-workflow
description: "用于审计、收敛和验证任意 Android 应用、多模块工程及 AAR 库的 R8 Keep Rules 与 consumer-rules.pro，满足 Google Play DEX 代码优化门槛（Shrinking/Optimization/Obfuscation）。用于排查整包 -keep、跨命名空间通配符污染、吞没三方/子模块规则、-keepnames 滥用及 Release 体积/混淆优化；不用于纯 Debug 功能开发或无关构建配置。"
---

# Android R8 Keep Rules 与 DEX 优化工作流

依据 Google Android 官方 **Ship smarter keep rules** 指引与 Google Play DEX 代码优化要求，在保证 Release 运行时零反射/JNI/序列化崩溃的前提下，最大化 R8 的裁剪（Shrinking）、优化（Optimization）与混淆（Obfuscation）效果。

本 Skill 跨项目通用，不绑死任何特定包名、模块名或仓库结构；脚本与参考文档均相对本 Skill 目录。

## 适用边界与前置读取

- 涉及 Gradle 构建、签名、R8 开关或打包配置变更时，与项目内 `android-build-workflow` 协同执行。
- 在目标项目中按需读取存在的文件：
  - `.agents/rules/execution.md`
  - `.agents/rules/build.md`
  - `.agents/config/project-profile.yml`
- 详细反模式、改写模板与 Google Play 门槛口径见 [Keep Rules 速查清单](references/keep-rules-checklist.md)。

## 标准执行流程

### 1. 基线静态审计（先量化，不凭感觉改）
在目标 Android 工程根目录运行内置审计脚本，盘点所有 `proguard-rules.pro` 与 `consumer-rules.pro`：

```powershell
python -B <skill目录>/scripts/audit_keep_rules.py --project-root .
```

需要机读结果时附加 `--json`。重点关注脚本标出的 `CRITICAL` 与 `HIGH` 条目：
- 跨命名空间无前缀通配符（如 `**.model.**`、`**.request.**`、`**.response.**`）
- 全局禁用标志（`-dontoptimize`、`-dontobfuscate`、`-dontshrink`）
- 对主流第三方库命名空间的整包 `-keep`（如 `com.google.android.gms.**`、`coil3.**`、`okhttp3.**` 等）
- 宿主 `app/proguard-rules.pro` 整包保留了已自带 `consumer-rules.pro` 的子模块包名（覆盖并吞没库的精细规则）
- 无软保留修饰符的业务/库整包 `-keep class xxx.** { *; }`
- 滥用 `-keepnames` 导致混淆率下降

### 2. 逐条核实真实反射 / JNI / 序列化入口
修改任何规则前，必须从真实源码核对：
1. **反射入口**：搜索 `Class.forName`、`getDeclaredMethod`、`getDeclaredConstructor`、`newInstance`、`GenericTypeResolver`、`ViewBinding.inflate`、ViewModel 构造等。
2. **序列化 DTO**：核对 Gson / Moshi / Kotlinx Serialization / Jackson 用法，优先用 `@SerializedName`、编译期 Codegen（如 Moshi codegen / kotlinx.serialization）替代按包名整包 keep。
3. **JNI / Native**：确认 `native <methods>;` 及 C/C++ 层实际反射回调的 Java/Kotlin 类、方法签名与字段名，绝不为单个回调类保留整个包。
4. **第三方与子模块**：核实依赖库是否已自带 `consumer-rules.pro`，宿主严禁重复整包 keep 覆盖上游精细规则。

### 3. 按“最窄入口 + 软保留 + 自治归属”收敛规则
- **归属原则**：库的规则放进库自己的 `consumer-rules.pro` 随 AAR/JAR 分发，不要求宿主在 `app/proguard-rules.pro` 手抄，避免升级后残留僵尸规则。
- **入口最小化**：只保留反射进入的具体类、构造函数或方法（如 `{ public <init>(); }`），R8 会沿引用图自动保留下游实际调用链并裁剪未用功能。
- **善用软保留修饰符**：仅需保留泛型签名、类名或元数据而不应阻止裁剪/优化时，显式添加 `allowshrinking,allowoptimization,allowobfuscation`（按实际需要组合）。
- **可选模块用条件规则**：对可选扩展/适配器使用 `-if` 条件 Keep 规则，或使用 Jetpack Keep Annotations 在源码标注反射调用，让构建期自动生成窄规则。
- **禁止用 `-keepnames` 换取可读堆栈**：正常开启完整混淆，通过上传 `mapping.txt` 到 Play Console / Firebase Crashlytics / SDK Console 或在 Android Studio Logcat 自动反混淆还原堆栈。

### 4. 构建与 R8 优化效果验证
1. 再次运行 `audit_keep_rules.py --project-root .`，确认目标风险项清零或已记录明确、有范围的业务例外。
2. 执行受影响模块的 Release 编译/打包（如 `.\gradlew.bat :app:assembleRelease` 或 `:app:bundleRelease`，遵守目标项目签名与构建规则），核对未压缩 DEX 体积与 `mapping.txt` 生成情况。
3. 有条件时结合 **R8 Configuration Analyzer**（`configanalyzer.html` / `r8.json`）、**Android CLI `analyze`** 与 Play Console DEX 优化报告复核三项百分比（Shrinking / Optimization / Obfuscation）。
4. **运行时回归门禁**：Keep Rules 变更属于高风险行为，**编译通过绝不等于运行时无崩溃**。必须在 Release 变体上回归核心链路（启动、反射页面/ViewBinding/ViewModel、网络序列化、JNI/Native、弹窗、支付/推送等）；因无设备或无签名凭据未实测时，必须在交付汇报中明确标为**未验证风险**。

## Skill 本地自检

维护本 Skill 或修改审计脚本后执行：

```powershell
python -B -m unittest discover -s <skill目录>/scripts -p "test_*.py" -v
```
