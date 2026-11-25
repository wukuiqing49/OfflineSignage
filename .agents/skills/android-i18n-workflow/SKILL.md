---
name: android-i18n-workflow
description: "用于实现和验证 Android 国际化：strings、plurals、多语言目录、占位符、格式化、产品/法律/计费文案、raw HTML/WebView 文案、fallback、编码和语言覆盖。"
---

# Android 国际化能力

## 项目上下文

以下项目路径均相对目标工程；脚本和 references 相对本 Skill。跨项目调用读取目标规则与事实，缺失时从实际源码/构建核实并说明，不套用 DevLib 的包名、模块或执行策略。脚手架生成参数使用用户选定配置。

在目标项目中读取适用且存在的文件：

- `.agents/config/project-profile.yml`
- `.agents/rules/execution.md`
- `.agents/rules/i18n.md`
- 涉及布局适配时读取 `.agents/rules/ui.md`

## 处理方法

1. 扫描默认语言、已有语言目录、同类 key、领域术语和加载逻辑。
2. 判断使用 string、plurals、格式化占位符、服务端文案或 raw HTML。
3. 按现有命名和分组修改资源，保持参数索引、类型与引用次数兼容，允许显式编号参数合法重排。
4. 对产品、订阅、计费、法律和隐私文案检查跨语言术语一致性。
5. 对 raw HTML/WebView 检查 UTF-8、资源选择、内嵌脚本和 JSBridge 暴露面。
6. 运行 i18n 门禁、脚本语法检查和受影响模块编译。

## 验证

验收前读取目标档案 `android.requiredHostLocales`，收集实际受影响模块的资源目录；不要照抄示例目录后声称覆盖全部功能。DevLib 完整宿主验收使用：

```powershell
python -B .agents/scripts/validate_android_workflows.py --project-root . --skip-figma --strict-i18n
```

定向验收使用本 Skill 的脚本，重复传入真实资源目录与目标要求的语言：`--res-dir <目录> --strict-missing --required-locale <语言>`。跨项目时使用工具脚本的实际路径，聚合入口可指定 `--tool-root`。不带 `--strict-missing` 的运行只作诊断，缺译警告不是验收通过；具体 fallback 要记录 key、原因、替代验证和复查条件。

语言基础目录提供覆盖，夜间、宽度、API 等条件目录可部分覆写并继承基础翻译；仅有夜间目录不能满足全天候语言要求。脚本支持常见限定符和同语言/基础语言继承，不等价于 Android 完整资源选择算法；复杂脚本地区匹配、非语言配置新增 key 及多 sourceSet 合并还需资源编译和人工核对。

门禁覆盖多语言 key、占位符、plurals quantity、BCP-47 目录和 raw HTML 内联脚本。无法提供可靠翻译时记录 fallback 和未验证语言。

功能开发必须按国际化规则覆盖实际受影响的业务模块及动态提示，不只检查 app/shared 资源。涉及 Kotlin/Java 声明变更时同时遵守 `.agents/rules/android.md` 的中文注释要求。
