# Zeplin Android 图层实现 Skill

将 Zeplin 页面或组件按图层实现为 Android XML + ViewBinding 页面。Skill 位于项目内，启动 Codex 的工作目录为本仓库或其子目录时可被自动发现。

## 使用方式

新开对话或重启 Codex 后，输入 `$` 并选择 `zeplin-android-layer-implementation`，或者在提示词中直接写：

```text
使用 $zeplin-android-layer-implementation，<你的需求>
```

先用分析提示词确认页面结构、资源和业务边界；确认方案后使用“已确认分析后直接实现”提示词。后一种提示词会继承当前对话中的确认结论，不需要重复填写页面目标、组件拆分、资源计划或业务约束。

## 分析提示词

```text
使用 $zeplin-android-layer-implementation，
先分析这个 Zeplin 页面，暂时不要写代码：
<ZEPLIN_URL>

请先读取 Zeplin 图层、设计 Token、可导出资源和现有 Android 工程。
输出：
1. 页面名称、画板尺寸、变体、注释和图层层级；
2. 可复用组件、Activity/Fragment、ViewModel、Adapter 的拆分建议；
3. 重要图层到 Android View/drawable/resource 的映射；
4. 现有资源可复用项与需要从 Zeplin 导出的资产；
5. strings.xml、colors.xml、dimens.xml、drawable 和多语言规划；
6. 现有页面、导航、接口或第三方 SDK 与设计稿的差异；
7. 需要我确认的交互、协议、后端或认证依赖。

不要修改任何代码或下载资产。
```

## 已确认分析后直接实现

当同一对话中已经完成并确认分析时，使用以下提示词。不要重新填写分析阶段已经确认过的内容：

```text
使用 $zeplin-android-layer-implementation，
根据本次对话中已经确认的 Zeplin 页面分析方案直接实现。

复用已确认的 Zeplin URL、目标页面/组件、图层映射、资源计划、导航和业务约束。
不要重复进行页面分析，也不要要求我再次填写上述信息。

继续遵守：
- Android XML + ViewBinding；
- 复用现有导航、ViewModel、网络层和资源模块；
- 修复分析中发现的布局、ViewBinding 或资源问题；
- 仅下载分析中确认缺失的 Zeplin 图层资产；
- 不把 Zeplin px 直接等同于 dp，不实现 iPhone 系统栏图层；
- 外部 SDK、支付、认证或后端能力仅在配置可用时接入真实流程，不得伪造成功结果。

完成后构建受影响模块、进行视觉核对，并输出修改文件、资产、资源/国际化、行为接入、依赖和验证结果。
```

仅在没有做过分析或切换了新的 Zeplin 页面时，才使用以下完整实现提示词。

## 未分析页面的直接实现提示词

```text
使用 $zeplin-android-layer-implementation，
读取并实现这个尚未分析的 Zeplin 页面：
<ZEPLIN_URL>

先在内部读取并分析 Zeplin 图层、设计 Token、可导出资源和现有 Android 工程；没有阻塞项时直接完成实现，不需要先输出方案等待确认。若 MCP、权限、设计数据或关键业务依赖缺失，则停止实现并明确报告，不得仅凭截图还原。

目标范围：
- 目标页面/组件：<目标 Activity、Fragment 或可复用 View>；
- 复用现有导航、ViewModel、网络层和资源模块；
- 使用 Android XML + ViewBinding，不使用 Compose 或 DataBinding；
- 先修复现有布局与 ViewBinding ID 不匹配问题；
- 设计稿中的文本写入 strings.xml，并补齐 zh、en、tr；
- 复用已有资源，仅下载缺失的 Zeplin 图层资产；
- 使用 ConstraintLayout、WindowInsets 和原生 drawable 实现适配；
- 不要把 Zeplin px 直接等同于 dp，不要实现 iPhone 系统栏图层。

交互约束：
- <需要保留的用户确认、权限或前置条件>；
- <设计稿入口与现有路由/业务流程的映射>；
- 外部 SDK、支付、认证或后端能力仅在配置可用时接入真实流程；依赖缺失时不得伪造成功结果，必须明确报告。

完成后：
1. 构建受影响模块并执行可用的定向检查；
2. 对照 Zeplin 预览进行视觉核对；
3. 输出修改文件、资产、资源/国际化、行为接入、依赖与验证结果。
```
