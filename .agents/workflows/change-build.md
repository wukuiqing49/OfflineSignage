# 修改 Android 构建与发布配置

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- `.agents/rules/build.md`

Skills：

- `$android-build-workflow`
- 涉及依赖归属时加载 `$android-project-architecture-workflow`

## 流程

1. 读取项目档案和 Gradle 事实源，确认当前版本、仓库、插件及变体。
2. 判断修改是否影响依赖图、SDK 行为、manifest、签名、R8、native 或 release。
3. 在集中版本管理和正确模块中实施最小修改。
4. 检查高版本行为、传递依赖、敏感信息和发布配置。
5. 运行构建门禁以及与影响范围匹配的 Gradle 任务。
6. 汇报版本入口、依赖/仓库变化、验证结果和发布风险。
