---
name: android-project-scaffold-workflow
description: "用于创建或调整 Android 工程脚手架：从配置生成 app、core/feature 子模块、Gradle Wrapper、版本目录、Kotlin/XML/ViewBinding 源码、签名文件保留策略、网络安全、FileProvider、Edge-to-edge 和资源骨架。新建工程、初始化模块结构或维护本仓库生成器时使用。"
---

# Android 项目脚手架能力

## 项目上下文

在项目仓库中调用时读取：

- `.agents/config/project-profile.yml`
- `.agents/config/project-scaffold.yml`
- `.agents/rules/execution.md`
- `.agents/rules/architecture.md`
- `.agents/rules/build.md`

## 能力边界

- 从空目录完整生成 Android 多模块工程和 Wrapper。
- 校验 YAML 参数并一次性渲染模块、源码、资源和发布配置。
- 将公开应用配置与签名秘密分离，通过独立 app convention 统一版本、签名和 release 行为。
- 对带 `.android-scaffold-generated.json` 标记的生成工程提供受控 `--force` 更新。
- 在 `--force` 更新时通过 marker 的 SHA-256 基线保留所有本地修改，并额外保留显式用户配置。
- 不覆盖没有生成标记的已有 Android 工程；已有工程由 Workflow 按最小补丁合并。
- 模块边界和依赖归属由架构规则决定；Gradle、签名和发布约束由构建规则决定。

## 生成

创建真实项目时，先明确目标根目录。支持两种模式：

- 独立目标模式：工作流工具仓库与 Android 目标工程是不同目录。
- 项目同根模式：用户明确指定当前仓库同时作为真实 Android 项目，`.agents/`、`AGENTS.md` 与 `app/`、`core/`、`feature/`、`gradle/` 位于同一级。

项目同根模式下，必须将 `--target` 指向包含 `.agents/` 的仓库根目录，不得指向 `.agents/` 内部。已有 `.agents/`、入口文件和文档不是冲突；如果目标位置已经存在生成器准备写入的 Android 文件，则按 marker 和已有工程规则处理。

从工作流工具根目录运行：

```powershell
python .agents/skills/android-project-scaffold-workflow/scripts/render_project_scaffold.py `
  --config .agents/config/project-scaffold.yml `
  --target C:\path\to\target
```

生成器必须拒绝未知或未消费的关键配置，不得把 `{{...}}` 模板标记写入目标工程。
维护 Skill 或运行生成器自测时只在临时目录生成烟雾工程；只有用户明确指定项目同根模式时，才允许在工作流仓库根目录生成真实 Android 工程。

## 生成内容

- 根构建、settings、版本目录、Gradle properties、app convention 和 Wrapper。
- `app`、`core/core_base`、`core/core_utils`、`feature/feature_app`、`feature/feature_res`。
- 动态 Application、MainActivity、功能入口和 WKQ 真实使用点。
- release/R8/consumer rules、共享签名配置、network security config 和 FileProvider。
- app 与共享资源模块的 values、drawable、night 和多语言资源骨架。
- 普通页面 Edge-to-edge 入口、主题和系统栏基础配置。

模板位于 `assets/scaffold/`。修改生成行为时优先修改生成器和模板，不在 Skill 中复制模板内容。

## 已有工程处理

1. 检查 settings、版本目录、各模块构建文件和 `.android-scaffold-generated.json`。
2. 目标只有 `.agents/`、入口文件或文档，没有 Android 生成文件时，允许按项目同根模式首次生成。
3. 没有生成标记时禁止使用 `--force`。
4. 有生成标记时只覆盖内容仍等于生成基线的产物；保留哈希已变化的业务文件和 `preservedPaths` 声明的用户配置。
5. 没有生成标记的已有 Android 工程只做最小合并，保留现有包名、API、资源和发布行为。
6. 将新增固定参数写入 `project-scaffold.yml`，将当前工程技术栈概览同步到 `project-profile.yml`。

## 验证

```powershell
python .agents/skills/android-project-scaffold-workflow/scripts/test_render_project_scaffold.py -v
python .agents/scripts/validate_android_workflows.py --project-root C:\path\to\target --tool-root . --skip-figma
.\gradlew.bat projects
.\gradlew.bat :app:assembleDebug
```

后两条 Gradle 命令必须在目标 Android 项目根目录运行。聚合验证器从工作流仓库加载工具，从 `--project-root` 加载目标工程。

涉及 release、签名、manifest、native 或 16KB page size 时，按 `.agents/rules/build.md` 扩大验证并说明凭据或设备限制。
