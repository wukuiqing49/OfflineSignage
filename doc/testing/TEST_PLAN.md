# 功能回归测试计划

## 环境与入口

- 项目：Local Signage Android，Kotlin + XML + ViewBinding，MVVM。
- 受影响模块：`app` 播放 Activity 与布局；`feature:feature_app` Scene 模型、SQLite、播放器、控制台、Ktor API、设备同步和项目备份。
- 依赖方向：`app -> feature:feature_app`；控制台 Scene API 依赖存储；播放器读取 Scene 与 Resource；Fleet 同步和项目备份消费 Scene 资源引用。
- 构建环境：JDK 17、Gradle Wrapper 8.13、AGP 8.13.2、Kotlin 2.3.0、compile/target SDK 36、min SDK 23。
- Debug 包：`com.wkq.localsignage`，版本 `1.3.0`；JVM 测试以 Robolectric/JUnit 为主。
- 常用命令：`gradlew.bat :feature:feature_app:testDebugUnitTest :app:testDebugUnitTest`；`gradlew.bat :app:assembleDebug`；`gradlew.bat :app:lintDebug`；`gradlew.bat :feature:feature_app:lintDebug`。
- 核心冒烟：旧 Scene 仍是全屏；双区 Scene 读写和侧栏资源保护；设备同步用目标端资源 ID；Debug APK 可构建；控制台脚本可解析。
- 测试数据隔离：Robolectric 使用测试应用上下文与数据库；端到端测试使用专用 Android 36 模拟器，不清理或覆盖物理设备。
- 最新报告：[`TEST_REPORT.md`](TEST_REPORT.md)。
- 通过基线：[`TEST_BASELINE.json`](TEST_BASELINE.json)。

## 功能用例与影响关系

| ID | 功能 | 源码路径 | 上游依赖/受影响用例 | 前置数据 | 操作/命令 | 预期与断言 | 方法 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SCENE-DB-LEGACY | v14 及更早 Scene 升级 | `feature/feature_app/.../storage/SignageStore.kt` | 数据库迁移、全屏播放器 | 旧版数据库含 Scene | `SignageStoreIntegrationTest.versionOneDatabaseUpgradesWithoutLosingPlaylistContent` | 升级到 v15；旧 Scene 为 `FULLSCREEN` 且无侧栏 | Robolectric |
| SCENE-DB-SPLIT | 双区保存、校验和资源删除保护 | `SignageStore.kt`, `SignageModels.kt` | 资源存储、Scene 编辑 | 图片主资源、文字侧栏、视频测试资源 | `SignageStoreIntegrationTest` 双区用例 | 双区字段持久化；被侧栏引用的资源不可删；视频侧栏拒绝 | Robolectric |
| SCENE-SYNC-MAP | 同步侧栏资源 ID | `device/LocalDeviceClient.kt`, `device/SignageDeviceFleet.kt` | 资源上传/复用、目标设备 Scene API | 主区和侧栏各有本机及目标端 ID | `LocalDeviceClientContractTest.sceneSyncUsesMappedRemoteIdsForBothRegions` | payload 中主/侧资源 ID 均为目标端 ID | Robolectric/JUnit |
| SCENE-API | 创建/读取双区 Scene 与非法类型校验 | `server/KtorSignageServer.kt` | 控制会话、Pro 权限、SQLite | 有效控制会话和主/侧 Resource | 通过真实 HTTP 创建后读取 `/api/scenes` | 返回并持久化模板和侧栏 ID；拒绝非法侧栏 | 模拟器真实 HTTP 已验证有效往返；非法输入未覆盖 |
| SCENE-PLAYER | 分区播放与旧全屏播放 | `player/SignagePlaybackController.kt`, `app/src/main/res/layout/activity_main.xml` | Scene/Resource、Coil、Media3、ViewPager2 | 一张主图/视频与一张侧栏图/文字 | 在目标屏播放两种模板及混合图片播放列表 | 主区 68%、侧区 32%；切换时两区资源同步；旧 Scene 全屏 | 模拟器/真机视觉测试；本轮未执行 |
| SCENE-CONSOLE | 模板选择、键盘操作、组合预览与保存 | `feature/feature_app/src/main/res/raw/web_console.html` | 双语文案、Scene API、资源列表、媒体鉴权 | 两张本地图片和 Pro/Trial 会话 | Android 模拟器真实 Ktor 服务，经端口转发由 Chrome 自动化编辑、预览、保存、复读 | 布局键盘、媒体鉴权、双区几何、390px 操作区均通过修复后 E2E；Android WebView 未覆盖 | Playwright E2E |
| SCENE-BACKUP | 项目导入/导出侧栏引用 | `server/KtorSignageServer.kt` | Manifest 资源清单、资源 ID remap、回滚 | 含双区 Scene 的项目备份 | 导出并导入后比对资源引用 | 侧栏 ID remap 正确；缺失/非法侧栏拒绝导入 | 集成测试；本轮未执行 |
| APP-DEBUG | 应用 Debug 构建 | `app`, `feature/feature_app` | 所有 Android 编译资源和模块依赖 | Debug variant | `:app:assembleDebug` | APK 生成成功 | Gradle |
| APP-LINT | 应用 lint | `app` | Android 资源与应用壳 | Debug variant | `:app:lintDebug` | 任务成功 | Gradle lint |
| FEATURE-LINT | Feature lint | `feature/feature_app` | Feature Kotlin 与资源 | Debug variant | `:feature:feature_app:lintDebug` | 无 lint error | Gradle lint；当前存在未改动的 API 兼容错误，详见报告 |

## 排除范围

| 范围 | 原因 | 用户确认来源 | 有效版本/期限 |
| --- | --- | --- | --- |
| 清除真实设备数据、卸载并重装 | 设备中可能有用户内容；本轮可用 JVM 测试和构建验证 | 本轮按保留数据策略 | 本轮 |
| 三列/多区域布局、独立区域播放列表、多视频 | 不属于已确认的首期主画面 + 侧栏范围 | 当前任务约定 | 首期 |

## 覆盖缺口

- 尚无播放器分区几何与图片/文字侧栏的自动 UI 测试；需在模拟器或目标屏检查画面比例、遮挡和资源切换。
- 场景预览的本地图片鉴权和双区媒体尺寸、390px 内容操作按钮布局已于 2026-09-24 修复并通过 E2E 复测；历史失败和修复后证据见 [`TEST_REPORT.md`](TEST_REPORT.md)。
- 控制台已在桌面 Chrome 完成自动交互；Android WebView 的字体、滚动与窗口行为仍未验证。
- 项目备份的双区导入/导出往返未纳入本轮自动测试。
- Feature lint 受现存 `PurchaseVerifier.kt` API 23/Base64 lint error 阻断；未改动该文件，也未建立 lint baseline。
