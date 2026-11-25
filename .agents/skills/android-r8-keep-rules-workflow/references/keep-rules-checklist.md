# Android R8 Keep Rules 与 DEX 优化速查清单

参考依据：Google Android Developers Blog — *Ship smarter keep rules: How library providers can unblock app-level DEX code optimization*（2026-11-17）及 Google Play 技术质量要求。

---

## 1. Google Play DEX 优化硬门槛概览

- **适用对象**：AAB 中未压缩 DEX 体积超过 **10 MB** 的普通应用（游戏为 **50 MB**）。
- **考核维度**：**收缩（Shrinking）**、**优化（Optimization）**、**混淆（Obfuscation）**三项指标（官方指引要求各达标阈值，计划 2027 年 2 月起全面纳入 Play 技术质量与分发/曝光考核）。
- **合并机制**：Play Console 统计的是**宿主 App 规则 + 所有内部模块与三方库 `consumer-rules.pro` 合并后的最终结果**；任意一条宽泛的整包规则都会覆盖并抵消其他模块的精细优化。

---

## 2. 常见反模式与正确改写对照

| 反模式（严禁新增，存量优先治理） | 危害 | 正确写法 |
| --- | --- | --- |
| `-keep class **.model.** { *; }`<br>`-keep class **.response.** { *; }` | 跨命名空间污染全工程，连带锁死 Firebase/GMS/AndroidX 与所有业务模型 | 删除无前缀通配符；DTO 统一使用 `@SerializedName` + `-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }`，或迁移到编译期 Codegen（kotlinx.serialization / Moshi codegen） |
| `-keep class com.google.android.gms.** { *; }`<br>`-keep class coil3.** { *; }` | 吞没（subsume）三方库自带的精细 consumer rules，造成严重体积膨胀 | 直接删除；三方库若确有缺漏，只保留具体报错的那一个类/方法并注明上游版本与移除条件 |
| 宿主 `app/proguard-rules.pro` 写 `-keep class com.example.base.** { *; }` | 覆盖子模块 `consumer-rules.pro` 精心写的软保留规则，且升级后变僵尸规则 | 从宿主移除，全部交回对应库模块的 `consumer-rules.pro` 自治 |
| `-keep class com.example.sdk.** { *; }` | 即使库已预混淆，仍锁死宿主全程序死代码裁剪，宿主未用到的库功能全被保留 | 仅保留反射/JNI 真正进入的入口点，例如：<br>`-keep class com.example.sdk.OptionalDecoder { public <init>(); }` |
| 为反射读泛型签名写硬 `-keep` | 阻止整条继承链的裁剪、内联与混淆 | 使用软保留修饰符：<br>`-keep,allowoptimization,allowshrinking,allowobfuscation class * extends com.example.BaseActivity` |
| 为可选适配器/模块无条件 `-keep` | 宿主未集成该可选功能时代码依然被强制保留 | 使用 `-if` 条件 Keep 规则，或使用 Jetpack Keep Annotations 编译期生成条件规则 |
| 为看懂崩溃堆栈加 `-keepnames` | 允许裁剪但禁止重命名，直接拉低 Play Console 混淆率指标 | 移除 `-keepnames`；正常全量混淆，通过上传 `mapping.txt` 到 Play Console / Crashlytics / SDK Console 自动反混淆 |
| `-dontoptimize` / `-dontobfuscate` / `-dontshrink` | 直接关闭整项 R8 能力，必挂 Play 优化门槛 | 仅允许在临时本地排查时使用，严禁提交到 Release 规则 |

---

## 3. 多模块工程与 Library Provider 职责边界

1. **库模块（`core_*` / AAR）**：
   - 必须通过 `consumerProguardFiles("consumer-rules.pro")` 随产物自带最小规则，**不在 README 里要求宿主手动复制规则**。
   - 仅保留本模块内部实际通过反射或 JNI 访问的入口点；公开普通调用 API 无需 keep（宿主调用时 R8 自然保留）。
2. **宿主模块（`app`）**：
   - 仅保留宿主自身直接反射/序列化/JSBridge 的最小规则。
   - 定期审计是否存在“覆盖了三方库或内部库精细规则”的历史整包 keep。

---

## 4. 量化排查与验证工具

1. **内置静态体检**：
   `python -B <skill目录>/scripts/audit_keep_rules.py --project-root .`
2. **R8 Configuration Analyzer**：
   查看构建产物中的 R8 配置分析报告（`configanalyzer.html` / `r8.json`），定位每条 keep 规则实际锁死的类、方法、字段数量。
3. **Android CLI `analyze` 与 Play Console**：
   在发版前通过 Android CLI `analyze` 或 Play Console 的 **App size / DEX code optimization** 面板核对 Shrinking / Optimization / Obfuscation 三项百分比。
4. **Release 真机回归**：
   收敛任何一条规则后，务必在开启 R8 的 Release 包上回归：冷启动、反射装填页面、网络请求/JSON 解析、JNI Native 能力、弹窗与计费/推送回调。
