# 修改 Android 文案与多语言

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/i18n.md`

Skills：

- `$android-i18n-workflow`
- 涉及布局适配时加载 `$android-ui-workflow`

## 流程

1. 检查默认语言、已有语言目录、同类 key、术语和 fallback 策略。
2. 判断 strings、plurals、格式化占位符或 raw HTML 的正确承载方式。
3. 按现有命名和分组修改默认语言及目标语言资源。
4. 检查占位符、plurals、编码、BCP-47 目录和脚本语法。
5. 运行 i18n 门禁及受影响模块编译。
6. 汇报修改 key、覆盖语言、fallback、验证结果和未验证项。
