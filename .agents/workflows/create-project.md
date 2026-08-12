# 创建或调整 Android 工程

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- `.agents/rules/build.md`
- `.agents/rules/ui.md`
- `.agents/rules/i18n.md`

Skills：

- `$android-project-scaffold-workflow`
- `$android-project-architecture-workflow`
- `$android-build-workflow`

配置：

- `.agents/config/project-profile.yml`
- `.agents/config/project-scaffold.yml`

## 流程

1. 明确工作流工具根目录和 Android 目标根目录；默认区分两者，用户明确指定项目同根模式时允许它们是同一目录，但禁止把工程生成到 `.agents/` 内部。
2. 判断目标目录是空目录、带 marker 的模板生成工程还是无 marker 的已有工程。
3. 核对脚手架配置、Gradle 事实源、模块边界和现有文件。
4. 空目录使用完整生成器；带 marker 的工程允许受控 `--force`；无 marker 工程只做最小合并。
5. 对比 marker 的 `generatedDigests`，保护已被业务修改的生成文件和 `preservedPaths`，确认公开应用配置不含签名秘密，debug 不使用 release 签名。
6. 检查模块图、WKQ 接入、版本、签名、网络、FileProvider、资源、RTL 和 Edge-to-edge 基础能力：`application` 必须启用 `android:supportsRtl="true"`；XML 布局、`gravity`、边距、内边距和 compound drawable 优先使用 `start/end` 逻辑方向属性，禁止为普通页面新增 `left/right` 物理方向属性；含返回、前进、抽屉、工具栏操作或方向性图标的页面，必须在阿拉伯语等 RTL Locale 下做视觉验收。
7. 真实项目生成到用户明确指定的目标根目录，包括已确认的项目同根目录；Skill 自测只生成到临时目录或 `.ai-work/scaffold-smoke/`。
8. 从工具根目录运行生成器测试和结构门禁，对目标根目录运行聚合门禁、`projects` 与 `assembleDebug`。
9. 汇报生成/修改文件、配置入口、验证结果和未验证项。
