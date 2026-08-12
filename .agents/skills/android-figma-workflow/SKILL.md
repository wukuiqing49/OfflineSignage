---
name: android-figma-workflow
description: "用于把 Figma 文件、Frame 或 node 转成可验证的 Android XML 页面：拉取结构化图层和资源、规范化屏幕、生成页面任务卡、映射 Fragment/ViewModel/Adapter/XML、实现代码并进行资源与视觉验收。"
---

# Android Figma 能力

## 项目上下文

在项目仓库中调用时读取：

- `.agents/config/project-profile.yml`
- `.agents/rules/figma.md`
- 分析阶段读取本 Skill 的 `references/figma-process.md`。
- 实现阶段读取本 Skill 的 `references/figma-code.md`。

本 Skill 与系统 `$figma` 配合，负责 Android 侧规范化、映射、产物和门禁，不替代 Android UI、架构、i18n 或构建 Skills。

## Reference 路由

- 分析主模板：[figma-process.md](references/figma-process.md)
- 实现主模板：[figma-code.md](references/figma-code.md)
- MCP/API 拉取：[pull-figma.md](references/pull-figma.md)
- 屏幕和图层规范化：[normalize-figma.md](references/normalize-figma.md)
- 页面分析：[analyze-page.md](references/analyze-page.md)
- Android 类型和文件映射：[android-mapping.md](references/android-mapping.md)
- 代码生成细节：[generate-code.md](references/generate-code.md)
- 结果验收：[validate-result.md](references/validate-result.md)

只读取当前阶段需要的 references。项目通用规则来自 `.agents/rules/`，不要再读取或维护 Skill 内的第二套项目规则。

## 工作数据

- 输入和中间产物：`.ai-work/figma/`
- 模板：`assets/`
- 页面任务卡：`.ai-work/figma/output/page_task_<page>.md`
- 页面资源清单：`.ai-work/figma/output/asset_manifest_<page>.md`

## 脚本

```powershell
python .agents/skills/android-figma-workflow/scripts/figma_sync.py --figma-url "<Figma URL>"
python .agents/skills/android-figma-workflow/scripts/validate_figma_output.py `
  --module-src <模块源码目录> --module-res <模块资源目录> `
  --asset-manifest .ai-work/figma/output/asset_manifest_<page>.md `
  --analysis-report .ai-work/figma/output/page_task_<page>.md
```

实现阶段还必须运行 UI/i18n 门禁和受影响模块 Gradle 编译；主页、Tab 或主导航页面按任务卡增加 pager 导航强校验。
