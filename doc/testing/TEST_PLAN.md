# 功能回归测试计划

## 全面屏与大屏适配增量回归（2026-10-20）

共享 Activity、主题、Manifest 和 Insets 变更影响全部原生页面，选中下列测试；播放/服务/存储保留现有单测回归。真实交易、生产统计、多设备同步与长期播放未在本轮设备验收。

| ID | 关联源码与方法 | 预期 |
| --- | --- | --- |
| EDGE-UNIT | PlaybackOrientationPolicyTest、SystemBarInsetsTest；app/feature_app testDebugUnitTest | 大屏/多窗口遵从系统，小屏保留用户显式方向；系统栏/刘海/IME padding 不累积 |
| EDGE-BUILD | SignageActivity、themes、Manifest；assembleDebug、assembleDebugAndroidTest、assembleRelease；四项静态门禁 | 不固定启动/欢迎方向，允许调整尺寸；现代全面屏入口，R8/签名/打包成功 |
| EDGE-PHONE | DeviceUiAudit compact en-US / 1.0 / system-portrait；1080x2400 density420、浅色、手势导航 | 配对、帮助、文章、计费、法律及隐私滚动前后，系统栏不遮挡、无省略或垂直溢出 |
| EDGE-TABLET | DeviceUiAudit compact en-US / 1.3 / tablet-landscape；2560x1600 density240、深色、三键任务栏 | 大屏遵从系统横屏，页面可滚动，避开任务栏；与手机范围相同 |
| EDGE-RELEASE | 保留数据覆盖 Release；Splash -> Welcome -> Main；欢迎页从平板横屏调整为手机竖屏 | R8 后 ViewBinding 正常，欢迎页面重排，入口可操作 |
| EDGE-REMAINING | 实体设备、实际分屏拖拽、API23–35 UI、Play Console 新版本扫描 | 本轮未执行，不能由模拟器或策略单测代替 |

## 崩溃风险加固（2026-10-20）

- 增量范围：MainActivity 页面绑定、SignagePlaybackController/WebViewRecovery、SignageStore/StorageFailureBoundary、SignageRuntime/启动与服务入口、KtorSignageServer/RetryAttempt，以及存储故障提示的现有九个语言目录。
- CRASH-LIFECYCLE：同一实例 attach/detach，重复 detach 无副作用；WebViewRecoveryTest 检查 owner 清理、失效实例替换、布局位置保留和销毁页面的迟到回调。
- CRASH-STORAGE：StorageFailureBoundaryTest 将隔离测试数据库设为 query_only，验证错误状态保留在内存、诊断写入失败不抛出、恢复写入、初始化失败后使用原 Store 重试；非存储编程错误仍抛出。
- CRASH-RETRY：RetryAttemptTest 验证失败上报后可执行下一轮、CancellationException 原样传播。
- CRASH-REGRESSION：执行 `:feature:feature_app:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`；架构、UI、i18n 门禁；检查日志与 JUnit XML。
- CRASH-DEVICE：真机连续进入退出、真实 renderer 退出、多 WebView 共用 renderer、存储满/损坏、启动重试弹窗的小屏/字体/日夜模式、后台同步恢复未执行。本轮两台连接设备未选目标，不安装覆盖已有应用。
- 保留此前前台服务修复；不变更依赖、Manifest、SDK、计费或统计同意配置。无可靠本轮工作区指纹，不推进 TEST_BASELINE.json。

## 前台服务启动拒绝修复（2026-10-20）

- 增量范围：`MainActivity.kt`、`SignageService.kt`；不变更 Manifest、SDK、开机恢复策略或统计配置。
- FGS-BUILD：执行 `:app:testDebugUnitTest :app:assembleDebug`，验证 app 编译、现有开机恢复策略单测和打包。
- FGS-REJECT：设备上复现服务创建时提升前台被拒绝，预期不崩溃、不初始化 Server/发现/统计、不循环重启；返回 MainActivity 后恢复服务。
- FGS-LIFECYCLE：设备上验证正常启动、返回前台、最近任务移除、系统回收恢复和局域网控制。两台已连接物理设备尚未选择目标，本轮不安装覆盖；设备用例未执行。
- 不复测无关的计费、多语言和统计收数；不推进缺少本轮工作区指纹及设备证据的基线。

## DevHub 增量回归（2026-10-09）

新增依赖和初始化影响 app 壳、core_google、feature_app 与法律页面。本轮保留现有 Firebase 和购买权益；不测试真实交易，不操作物理设备，不清空已有数据。

| ID | 功能与源码 | 前置条件 / 命令 | 预期与方法 |
| --- | --- | --- | --- |
| DEVHUB-CONSENT | `GoogleCommercialManager.kt`, `DevHubConsentTest.kt` | `:feature:feature_app:testDebugUnitTest`；测试 key 与保留域名 `.invalid`，不向生产上报 | 未授权不生成安装身份或活跃队列；授权状态持久化；撤回清除活跃队列；关闭后服务触发不再入队。Robolectric |
| DEVHUB-BUILD | 版本目录、settings、app conventions、Application | `:app:assembleDebug :app:assembleRelease :core:core_google:testDebugUnitTest` | 私有依赖解析、两变体编译、Release R8 打包成功；现有 core 单测通过 |
| DEVHUB-LEGAL-UI | `LegalCenterActivity.kt`, `activity_legal_center.xml`, strings/raw | 静态 UI/i18n 门禁；模拟器安装并从应用打开法律与隐私页面 | 默认开关关闭；Debug 默认禁用；说明可滚动；小屏/字体/横竖屏/日夜模式未测场景单独记录 |
| DEVHUB-DAILY-SERVICE | `SignageService.kt`, `GoogleCommercialManager.reportActive()` | 前台服务每小时回调；服务销毁取消回调 | 常驻设备跨 UTC 日期上报，不重复计数；真实跨日和后台计数需单独验收 |
| DEVHUB-PRODUCTION | App Key、Play Integrity、生产 CMS | Play 测试轨授权启用后检查安装及活跃记录 | 生产收到记录、断网恢复、撤回及 Play Integrity。当前不以构建或本地开关成功替代后台验收 |

已有 feature/core 单测作为共享初始化、存储、播放器与购买策略的增量回归；其他硬件、多设备同步、真实交易和公网统计验收不在本轮通过范围。新增用例的源码未提交，未建立覆盖未跟踪文件的可靠指纹前不推进通过基线。

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
| FEATURE-LINT | Feature lint | `feature/feature_app` | Feature Kotlin 与资源 | Debug variant | `:feature:feature_app:lintDebug` | 无 lint error | Gradle lint |
| BILLING-PRICE | Play 本地化价格展示 | `BillingActivity.kt`, `BillingPriceFormatter.kt` | 年度商品目录 | `BillingPriceFormatterTest` | 保留 Play `formattedPrice`；空价格不自行拼币种或展示伪价格 | JUnit |
| BILLING-VERIFY | 购买签名及包名/商品校验 | `PurchaseVerifier.kt` | API 23 Base64 兼容 | `PurchaseVerifierTest` | 有效签名可通过；篡改、错误包名、未知商品或无效 JSON 被拒绝 | Robolectric/JUnit |
| BILLING-ACCESS | 试用后免费额度及权益状态 | `CommercialAccessPolicy.kt`, `EntitlementPolicy.kt` | 7 天试用、7 天离线宽限 | `CommercialAccessPolicyTest`, `EntitlementPolicyTest` | 免费模式限 10 个资源和 1 个基础轮播；Play 未验证不视为真实交易验收 | Robolectric/JUnit |
| BILLING-UI-STATE | 计费目录加载与错误状态 | `EntitlementModels.kt`, `BillingActivity.kt` | Billing 查询 loading/error 状态 | `MonetizationUiStateTest` | 初始/加载状态显示等待；终态错误结束 spinner 并允许显示不可用提示 | JUnit |
| BILLING-UI-ERROR-AVD | Play Billing 不可用时的计费页 | `BillingActivity.kt`, `MonetizationRepository.kt` | 新装 Debug 包、无 Play Billing 服务 | 从主界面打开“Plans and license” | 显示本地权益与不可用提示，加载圈消失，恢复按钮可重试；不发起真实购买 | Android AVD 手工 |
| BILLING-PLAY-MANUAL | Play 实际购买与恢复 | Billing Activity/Repository | Play 内部测试轨道、License Tester | 验证年度购买、取消、续订、退款、恢复及离线降级 | 交易状态与权益一致，播放不因计费失败中断 | 手工；当前未执行 |

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
- `PurchaseVerifier.kt` 的 API 23/Base64 lint 错误已修复；2026-09-24 的 `:feature:feature_app:lintDebug` 通过，未建立 lint baseline。

## 2026-10-21 横屏与页面适配增量

- ORIENT-UNIT：共享 SignageOrientationPolicyTest；验证新设备默认横屏、显式竖屏、跟随设备、大屏和多窗口行为。方向策略迁移到 feature_app，启动、欢迎、播放、帮助、授权和法律页面共用。
- ORIENT-BUILD：app assembleDebug / assembleDebugAndroidTest 与 app、feature_app testDebugUnitTest；配合 UI、架构、i18n、构建静态门禁。
- ORIENT-UI：复用 DeviceUiAudit compact，在专用模拟器验证横屏手机和窄窗口大字体的连接、帮助、文章、授权、法律页面共 14 个状态；检查截图、实际方向、ellipsis/verticalOverflow。保留素材和购买状态，恢复临时窗口与字体设置。
- ORIENT-REMAINING：真实购买商品、实体电视遥控、旋转后播放连续性、分屏拖动和欢迎页首次安装为单独设备验收，不以构建或规则单测替代。


## 2026-10-21 UI 样式与虚拟机优化增量

- UI-UNIT/BUILD/GATES：共享方向包含 Android16 大屏和旧版平板差异；设置 PATCH 保留未提交字段和拒绝非法方向；执行两个模块单测、Debug/测试 APK 构建及四类门禁。
- UI-DEVICE：仅独立 AVD；横屏英文浅色1.0、竖屏中文深色1.3、平板横屏中文深色1.3，每组16状态；首次欢迎、连接、帮助、文章、授权错误/重试、法律正文均检查实际方向、语言、溢出及截图。
- UI-PLAYBACK：真实 HTTP 配对/局部设置/图片上传/暂停/恢复/轮播/断网/进程重启恢复，最后清理测试数据。只在新建虚拟机执行，不访问物理设备。
- 保留缺口：视频硬解、实际分屏、系统重启、24小时运行、真实购买及 Release 发布另行验收。结果见 TEST_REPORT 最新节。


## 2026-10-21 混播与系统重启增量

选中原因：上一轮只验证 PNG 与进程重启，未覆盖 Media3/WebView 的真实画面以及系统重启后的平台策略。继续使用独立 Android36 AVD，不触碰物理设备。

| 用例 | 关联实现 | 预期 |
| --- | --- | --- |
| MIXED-VIDEO | SignagePlaybackController / Media3 | 本地 H.264 位置与实际视频帧均推进 |
| MIXED-HTML | SignagePlaybackController / WebView | 正文可见，JS计数器推进 |
| MIXED-OFFLINE | Playlist 调度 / SignageService | 断网后视频、PNG、HTML三类继续循环，无错误状态 |
| MIXED-BOOT | SignageBootReceiver / BootRestorePolicy / Runtime | Android36开机延迟恢复；人工打开后原列表恢复 |
| MIXED-SOAK | Player / Runtime / Store | 300秒持续混播，三素材循环，进程不重启；记录前后内存，不据此声称无泄漏 |
| MIXED-CLEANUP | API场景/资源/列表 | 只删除本轮内容，恢复原设置；清理失败不计通过 |

可复跑入口：[scripts/run_virtual_mixed_media.py](scripts/run_virtual_mixed_media.py)，环境与操作说明：[scripts/README.md](scripts/README.md)。本轮未改生产 Kotlin/XML，不重复上一轮已通过的完整页面矩阵和122项单测。24小时、物理硬解、流媒体和旧版本设备保持未执行。

- MIXED-ROTATE：在上述混播期间临时关闭自动旋转、锁定user-rotation 1，确认1200×1920竖屏截图后恢复原user-rotation/accelerometer设置；确认1920×1200横屏截图及连续混播状态。此项为本轮设备命令验收，未集成到脚本。


## 2026-10-21 开放月度、年度与永久套餐

- BILLING-PLANS-UNIT：BillingPlansTest 覆盖三档精确商品/offer、历史pro-mouth、仅永久可用、价格或offer缺失、SUBS/INAPP单侧失败、双侧失败。全量feature_app单测检查共享计费权益消费者。
- BILLING-PLANS-UI：DeviceUiAudit的mode=billing仅在测试APK通过页面状态夹具验证全部可用、仅永久可用、全部不可用；三档面板始终显示，按钮只按可购性启用。每种状态包含顶部和滚动底部；英文宽屏1.0、中文竖屏1.3各6个状态，检查截图和文字溢出。
- 测试价格与offer为夹具，不能冒充Play实际商品；不点击真实购买、不发起扣款。恢复购买和真实交易需要Play测试账号另行验收。
- 生产行为：保留Google Play动态价格与新鲜offer校验；没有返回有效价格/offer的套餐显示价格不可用并禁用按钮。帮助文案9个资源目录与中英购买说明同步。
