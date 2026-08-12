# 修复 Android Bug

## 加载

始终读取：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/config/project-profile.yml`

根据错误模块继续加载对应规则、Workflow 和 Skills：

- UI/页面：`.agents/workflows/implement-page.md`
- Gradle/依赖/打包：`.agents/workflows/change-build.md`
- strings/翻译/raw HTML：`.agents/workflows/localize-content.md`
- 模块边界/共享能力：`.agents/workflows/change-architecture.md`
- Figma 实现偏差：`.agents/workflows/figma-code.md`

## 流程

1. 读取完整错误、复现条件、相关代码和最近变更，定位根因。
2. 指出关键错误行、模块和触发链，区分根因与表象。
3. 选择对应领域规则和 Skill，实施最小修复。
4. 检查是否需要同步修改 Gradle、Manifest、ProGuard/R8 或资源。
5. 运行复现路径、静态门禁和受影响模块测试/编译。
6. 汇报根因、修改文件、验证方式和剩余风险。
