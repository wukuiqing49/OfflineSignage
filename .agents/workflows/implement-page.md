# 实现或修改 Android 页面

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- `.agents/rules/ui.md`
- `.agents/rules/i18n.md`

Skills：

- `$android-project-architecture-workflow`
- `$android-ui-workflow`
- `$android-i18n-workflow`
- 涉及依赖、theme 或 manifest 时加载 `$android-build-workflow`

## 流程

1. 检查目标模块、相邻页面、Base、Adapter、组件和资源体系。
2. 确认页面结构、状态归属、列表/Tab/弹窗方案和 Insets owner。
3. 复用已有能力并实现 XML、页面类、ViewModel、Adapter 和资源。
4. 检查文本溢出、滚动、系统栏、小屏幕和生命周期场景。
5. 运行 UI、i18n 和受影响模块最小编译验证。
6. 汇报页面结构、复用项、资源、验证结果和未验证项。
