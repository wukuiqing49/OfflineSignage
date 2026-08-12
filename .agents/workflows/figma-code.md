# Figma 页面实现

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- `.agents/rules/ui.md`
- `.agents/rules/i18n.md`
- `.agents/rules/build.md`
- `.agents/rules/figma.md`

Skills：

- `$android-figma-workflow`
- `$android-project-architecture-workflow`
- `$android-ui-workflow`
- `$android-i18n-workflow`
- `$android-build-workflow`

Skill references：

- `.agents/skills/android-figma-workflow/references/figma-code.md`

## 流程

1. 校验页面任务卡、截图、图层、规范化报告和资源索引属于同一 Node ID。
2. 任务卡存在阻塞项时停止，不使用占位资源或静默降级。
3. 检查项目现有 Base、组件、资源以及页面专属旧实现。
4. 按任务卡映射实现布局、页面类、状态、Adapter 和资源。
5. 逐项核对尺寸合同、Insets owner、资源绑定和已知视觉差异。
6. 运行 Figma、UI、i18n 和受影响模块 Gradle 验证，条件允许时截图对比。
