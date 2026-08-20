---
name: android-i18n-workflow
description: "用于实现和验证 Android 国际化：strings、plurals、多语言目录、占位符、格式化、产品/法律/计费文案、raw HTML/WebView 文案、fallback、编码和语言覆盖。"
---

# Android 国际化能力

## 项目上下文

在项目仓库中调用时读取：

- `.agents/config/project-profile.yml`
- `.agents/rules/execution.md`
- `.agents/rules/i18n.md`
- 涉及布局适配时读取 `.agents/rules/ui.md`

## 处理方法

1. 扫描默认语言、已有语言目录、同类 key、领域术语和加载逻辑。
2. 判断使用 string、plurals、格式化占位符、服务端文案或 raw HTML。
3. 按现有命名和分组修改资源，保持占位符类型与顺序一致。
4. 对产品、订阅、计费、法律和隐私文案检查跨语言术语一致性。
5. 对 raw HTML/WebView 检查 UTF-8、资源选择、内嵌脚本和 JSBridge 暴露面。
6. 运行 i18n 门禁、脚本语法检查和受影响模块编译。

## 验证

```powershell
python .agents/skills/android-i18n-workflow/scripts/validate_i18n_resources.py `
  --res-dir app/src/main/res `
  --res-dir feature/feature_app/src/main/res `
  --res-dir feature/feature_res/src/main/res
```

门禁覆盖多语言 key、占位符、plurals quantity、BCP-47 目录和 raw HTML 内联脚本。无法提供可靠翻译时记录 fallback 和未验证语言。
