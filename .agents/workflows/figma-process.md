# Figma 页面分析

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- `.agents/rules/ui.md`
- `.agents/rules/i18n.md`
- `.agents/rules/figma.md`

Skills：

- 系统 `$figma`
- `$android-figma-workflow`
- `$android-project-architecture-workflow`
- `$android-ui-workflow`

Skill references：

- `.agents/skills/android-figma-workflow/references/figma-process.md`

## 流程

1. 拉取指定 Frame 的结构化图层、截图和资源信息并绑定 Node ID。
2. 规范化逻辑屏幕、关键 bounds、视觉高度和系统栏预览图层。
3. 分析 Activity/Fragment/ViewModel/Adapter、列表、Tab 和资源归属。
4. 输出尺寸合同、Insets owner、区域预算和 Android 文件映射。
5. 生成页面任务卡、规范化报告和页面级资源清单。
6. 只汇报分析与阻塞项，等待用户确认，不修改 Android 代码。
