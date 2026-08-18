# 产品事实证据规则

## 状态模型

- `VERIFIED`：存在可达的实现链路，且证据足以支持用户可见能力。
- `UNVERIFIED`：存在暗示，但无法确认完整实现或真实可用性。
- `CONTRADICTED`：产品文档、源码、资源或运行结果互相冲突。
- `NOT_FOUND`：在合理检索范围内没有找到实现。

另设 `advertisable: true/false`。只有 `VERIFIED + true` 可以进入视频营销内容。

## 证据强度

按以下顺序判断：

1. 真实运行录屏、设备验证或稳定自动化测试。
2. 从 Manifest/导航入口到页面、业务逻辑和结果的完整可达代码链。
3. 页面和业务代码配合资源、模型、存储或网络实现。
4. 单独存在的 UI、字符串、类名、依赖、README 或规划文档。

第 4 类只能形成线索，不能单独形成 VERIFIED。

## 分析步骤

1. 从 Launcher、路由、导航图和 Feature 入口建立可达页面清单。
2. 对候选营销能力追踪 Screen/Fragment/Activity 到 ViewModel、UseCase、Repository 或 Engine。
3. 核对输入、用户动作、处理过程、可见结果和失败路径。
4. 核对能力是否只存在于测试、实验开关、未注册模块或未启用 Build Variant。
5. 为事实记录 `claim_id`、声明、状态、advertisable、证据路径、行号、符号和说明。

## 高风险声明

- `AI`：确认真实模型/API、调用入口和用户结果，不能因名称含 AI 即通过。
- `Offline/No Cloud`：确认核心流程不依赖远程服务；存在网络权限不等于否定，但必须核对实际链路。
- `Privacy/Safe`：确认数据处理和传输行为，不使用绝对安全承诺。
- `Batch`：确认用户能选择多个输入且处理链实际遍历或批量执行。
- `Cloud/Sync/Collaboration`：确认服务端、账号和同步链路真实存在。
- `Free/Price`：除非有当前市场的明确计费证据，否则不宣传。

## 冲突处理

实际 Gradle、Manifest 和源码事实高于项目档案概览。运行证据与源码冲突时标记 `CONTRADICTED` 并报告，不静默选择有利于营销的一方。
