# 功能回归测试报告（最新一轮）

最新结果见文末“修复后回归：控制台预览与窄屏操作区”；前序区块保留各自测试时的历史状态。

## 本轮上下文

- 时间/时区：2026-09-24，Asia/Singapore。
- 模式：增量功能回归。
- 分支/HEAD：`main` / `74f241a`；本轮源码未提交。
- 工作区源码指纹：`git diff --binary HEAD | git hash-object --stdin` = `9a9889b646ff894bb703882c7be7f5f7e141000f`。此指纹覆盖当时所有 tracked 源码改动，排除本报告和构建产物。
- APK：Debug，`com.wkq.localsignage` 1.3.0；SHA-256 `8606CB291E9D022D1FA42AEC45058F6A827D9D4C2E888EB719524B5B197FD0F5`；路径 `app/build/outputs/apk/debug/app-debug.apk`。
- 环境：JDK 17.0.12、Gradle 8.13、AGP 8.13.2、Kotlin 2.3.0、SDK 36；测试变体 Debug。
- adb 检查时有一台 PCAM00 设备和一个 `sdk_gphone64_x86_64` 模拟器在线；本轮未安装、未操作设备，避免触碰现存数据。

## 影响分析

| 修改文件或公共依赖 | 受影响功能/用例 | 选择理由 |
| --- | --- | --- |
| Scene 模型、SignageStore、v15 迁移 | `SCENE-DB-LEGACY`, `SCENE-DB-SPLIT` | 新字段读写、旧数据默认值与资源 FK/删除保护 |
| 播放控制器、播放布局、MainActivity | `SCENE-PLAYER`, `APP-DEBUG` | 共享主画面容器的运行时尺寸与新增侧栏 View |
| Ktor API、JSON、Fleet、备份 | `SCENE-API`, `SCENE-SYNC-MAP`, `SCENE-BACKUP` | API 契约、远端资源映射、备份资源 ID remap |
| Web Console | `SCENE-CONSOLE` | 模板选择、资源筛选和双区预览 |

## 执行结果

| 用例 ID | 状态 | 命令/步骤 | 预期 | 实际结果/退出码 | 证据与环境 | 耗时 |
| --- | --- | --- | --- | --- | --- | --- |
| `SCENE-DB-LEGACY`, `SCENE-DB-SPLIT`, `SCENE-SYNC-MAP` 及 feature 回归 | 本次通过 | `gradlew.bat :feature:feature_app:testDebugUnitTest` | 所有测试通过 | 97 tests，0 failures，0 errors；exit 0 | [feature JUnit 报告](../../feature/feature_app/build/reports/tests/testDebugUnitTest/index.html)，Robolectric/JDK 17 | 约 17 秒最终运行 |
| app JVM 回归 | 本次通过 | `gradlew.bat :app:testDebugUnitTest` | 所有测试通过 | 3 tests，0 failures，0 errors；exit 0 | [app JUnit 报告](../../app/build/reports/tests/testDebugUnitTest/index.html) | 与 feature suite 同轮 |
| 控制台脚本语法 | 本次通过 | Node.js 22 `new Function` 检查全部 inline script | 所有脚本可解析 | 11 段脚本解析成功 | 工作区 `web_console.html` | 小于 1 秒 |
| `APP-DEBUG` | 本次通过 | `gradlew.bat :app:assembleDebug` | 生成 Debug APK | exit 0；APK SHA-256 见上 | [Debug APK](../../app/build/outputs/apk/debug/app-debug.apk) | 限制 Gradle worker 后约 23 秒 |
| `APP-LINT` | 本次通过 | `gradlew.bat :app:lintDebug` | 应用壳 lint 通过 | exit 0 | `app/build/reports/lint-results-debug.html` | 约 4 秒 |
| `FEATURE-LINT` | 失败 | `gradlew.bat :feature:feature_app:lintDebug` | 无 lint error | 4 个 error / 41 个 warning；4 个 error 均为未改动的 `monetization/PurchaseVerifier.kt` 使用 `java.util.Base64` 与 minSdk 23 的 API 兼容提示 | `feature/feature_app/build/reports/lint-results-debug.html` | 约 2 分钟 |
| `git diff --check` | 本次通过 | `git diff --check` | 无空白/补丁格式错误 | exit 0；Git 另提示工作区 LF/CRLF 自动转换警告 | 工作区 | 小于 1 秒 |
| `SCENE-API`, `SCENE-PLAYER`, `SCENE-CONSOLE` 人工操作, `SCENE-BACKUP` | 未执行 | 本轮未在设备或浏览器操作 | 完成相应真实流程验证 | 保留为后续验证项；不据此声称 UI、API 或备份端到端已验收 | 未产生设备证据 | - |

早期一次同步契约测试因未使用 Robolectric 而触发 Android `org.json` 的 JVM stub 异常；调整测试 runner 后重跑通过。一次额外 APK 构建因 Gradle daemon native memory 分配失败退出；限制 worker 后重建成功。上述失败均未作为最终通过结果。

## 未复测与排除

| 用例 ID/范围 | 状态 | 原因 | 历史日期/提交（如有） |
| --- | --- | --- | --- |
| `SCENE-API` | 未执行 | Pro 权限与 HTTP Scene 往返未自动化 | - |
| `SCENE-PLAYER` | 未执行 | 尚未在模拟器/目标屏目视检查分区显示、媒体适配与切换 | - |
| `SCENE-CONSOLE` 浏览器交互 | 未执行 | 仅有静态脚本语法解析，未验证 WebView 操作与视觉 | - |
| `SCENE-BACKUP` | 未执行 | 双区备份导入/导出 round-trip 未新增自动化 | - |
| 物理设备安装与交互 | 排除 | 保留现有设备数据，本轮不做覆盖安装和清理 | - |

## 问题与后续复测

| 问题 | 严重度 | 复现步骤 | 预期/实际 | 关联用例 | 后续动作 |
| --- | --- | --- | --- | --- | --- |
| Feature lint 有 4 个既有 API 兼容 error | 中 | 运行 `:feature:feature_app:lintDebug` | 预期无 error；实际均指向未改动的 `PurchaseVerifier.kt:29,34` | `FEATURE-LINT` | 单独评估 Base64 API/minSdk 兼容修复；本功能不改该模块 |
| WebView 与真实播放视觉未验证 | 中 | 用 Debug APK 创建图片主区 + 文字/图片侧栏并播放 | 需要核对比例、适配、字幕、列表切换 | `SCENE-PLAYER`, `SCENE-CONSOLE` | 在模拟器或验收屏完成手工验证，保留截图 |

## 范围内结论

本轮通过 6；失败 1；受阻 0；未执行 4；历史通过未复测 0；用户确认 0；排除 1。数据库迁移/持久化、资源策略、远端 ID payload、Android 单测、控制台脚本解析、差异格式检查和 Debug 构建均通过；Feature lint 因既有错误未通过。真实设备交互、HTTP Scene 往返和项目备份往返仍未验证。本轮没有改动或清理任何设备数据。只将本轮实际通过且带 dirty-worktree 指纹的条目加入 [`TEST_BASELINE.json`](TEST_BASELINE.json)。

## 补充回归：控制台模拟器 E2E

- 时间/时区：2026-09-24，Asia/Singapore。
- 工作区源码指纹：`git diff --binary HEAD | git hash-object --stdin` = `f4c6cc3cc6e8f4bb4044f98c84c21e11fafe7e05`；本补充回归未修改产品源码。
- APK：Debug，`com.wkq.localsignage` 1.3.0；SHA-256 `F951B0D438A9E98B915920CC37B22929C894B5E12D595E6DF97F1124E593E22B`。
- 环境：专用 AVD `codex_local_signage_test_api36`，Android 16/API 36，模拟器序列 `emulator-5556`；宿主 Chrome 154.0.8037.57、Node.js 22.19.0。Chrome 经 ADB 端口转发连接模拟器内真实 Ktor 服务；没有操作物理设备或原有模拟器。
- 配对：首次浏览器会话真实提交六位配对码并成功认证；后续同轮复测只在进程环境变量中复用短时凭据，未写入日志、报告或截图。
- 自动化脚本与截图位于被 Git 忽略的 `build/functional-test/console-ui-20260924-01/`，不属于持久化测试代码；测试报告保留本轮结果。

| 用例 | 结果 | 实际结果 |
| --- | --- | --- |
| 配对、控制权与会话初始化 | 通过 | 实际配对成功；控制台认证并取得控制权；设备处于 Trial 模式 |
| 语言与无障碍语义 | 通过 | 中英文导航/语言控件标签正确更新 |
| 心跳与状态轮询 | 通过 | 15 秒与 30 秒定时器各创建一次，重复初始化不重复创建 |
| 内容标签键盘导航 | 通过 | Tab 角色、选中态、Home/End、方向键及面板关联正确 |
| 图片上传、资源库与 Scene API | 通过 | 两张测试图上传成功；双区 Scene 保存后从真实 `/api/scenes` 读回主/侧资源引用 |
| Scene 布局键盘、预览结构与编辑回填 | 通过 | Radio 键盘切换、双区域 DOM、保存后编辑器回填均正确 |
| Scene 本地图片预览 | 失败 | 两张图片请求均 HTTP 401；`web_console.html` 直接将受保护的 `/media/{id}` 赋给 `<img src>`，浏览器请求没有 `X-Local-Signage-Token`；服务端 `/media/{id}` 要求该鉴权头 |
| 390px 窄屏内容操作区 | 失败 | 无水平溢出，但 6 个操作按钮均约 `53x144px`，标签被挤成窄列，实际不便阅读和操作 |
| 浏览器异常与窄屏宽度 | 通过 | 0 个未捕获 JS 异常；页面宽度为 390px，无横向溢出 |

本轮汇总：17 项通过、2 项失败、0 项受阻；一组隔离图片资源由此前成功上传的同一模拟器会话复用。第一次试跑的资源断言曾错误依赖展示名称，已改为按 API 返回 ID 复核并完整重跑，不计为产品缺陷。场景预览 HTTP 401 与窄屏按钮尺寸是复跑确认的真实问题。截图：[`console-desktop.png`](../../build/functional-test/console-ui-20260924-01/console-desktop.png)、[`scene-preview-desktop.png`](../../build/functional-test/console-ui-20260924-01/scene-preview-desktop.png)、[`console-mobile.png`](../../build/functional-test/console-ui-20260924-01/console-mobile.png)。

该初始 E2E 有功能/视觉失败，因此当时未更新通过基线。Android WebView、播放器双区实际播放以及项目备份往返仍未验证；测试模拟器保持运行，测试图片、轮播和场景留在该隔离 AVD 中。修复后复测如下。

## 修复后回归：控制台预览与窄屏操作区

- 时间/时区：2026-09-24 13:11，Asia/Singapore。
- 分支/HEAD：`main` / `74f241a`；产品源码未提交。
- 工作区源码指纹：`git diff --binary HEAD | git hash-object --stdin` = `5d76156ab3e4772d446e52d259b7baf7a9b744ee`；指纹覆盖 tracked 源码改动，排除本报告与构建产物。
- APK：Debug，`com.wkq.localsignage` 1.3.0；SHA-256 `BF5DDC0956D721DDE8BB8A964D3F35249497EFD64480467ADFCA127846B97EC4`。
- 环境：专用 AVD `codex_local_signage_test_api36`，Android 16/API 36，`emulator-5556`；Chrome 154.0.8037.57、Node.js 22.19.0。Chrome 经 ADB 端口转发访问模拟器内真实 Ktor 服务；未操作物理设备或原有模拟器。
- 执行：`gradlew.bat :app:assembleDebug`；`node build/functional-test/console-ui-20260924-01/console-ui.test.cjs`。有效访问凭据仅通过进程环境变量传递，未写入报告、日志或截图。
- 截图与原始结果保存在 Git 忽略的 `build/functional-test/console-ui-20260924-01/`，测试脚本不是仓库持久化测试代码。

| 用例 | 状态 | 实际结果 |
| --- | --- | --- |
| 控制台认证、双语导航、轮询、内容标签键盘操作 | 本次通过 | 相关断言全部通过；轮询间隔各创建一次（15s、30s） |
| 图片夹具与资源库 | 复用/通过 | 复用隔离模拟器中两张测试图片；资源列表与真实 API 匹配 |
| Scene 布局选择、双区预览与保存 | 本次通过 | 键盘切换、两区 DOM、真实 `/api/scenes` 保存/读回及编辑回填通过 |
| Scene 本地图片鉴权与解码 | 本次通过 | 两次 `/media/` 响应均 HTTP 200，图片均解码成功；没有 401 |
| Scene 双区预览几何 | 本次通过 | 画布 520×292.5；主区 351.5×292.5、侧区 165.5×292.5，区域宽度无溢出 |
| 390px 窄屏内容操作区 | 本次通过 | 无横向溢出；6 个操作按钮均约 175×44px，中文标签可读 |
| 320px 窄屏内容操作区 | 本次通过 | 无横向溢出；6 个操作按钮均约 140×44px |
| 浏览器异常 | 本次通过 | 0 个未捕获异常 |
| Debug 构建 | 本次通过 | `:app:assembleDebug` 成功；APK SHA-256 见上 |
| raw HTML 内联脚本与多语言门禁 | 本次通过 | 11 段脚本解析通过；i18n 校验通过，提示 `app` 没有 string/plurals 资源（既有结构） |

本轮汇总：21 项通过、0 项失败、0 项受阻；图片夹具 setup 为复用状态，不计为断言。结果文件：[E2E 结果](../../build/functional-test/console-ui-20260924-01/result.json)；截图：[场景预览](../../build/functional-test/console-ui-20260924-01/scene-preview-desktop.png)、[390px 控制台](../../build/functional-test/console-ui-20260924-01/console-mobile.png)。本次通过项已按工作区指纹记录在 [`TEST_BASELINE.json`](TEST_BASELINE.json)。

Android WebView 内的窗口/字体/滚动、播放器实际双区播放、项目备份导入导出及物理设备仍未验证；测试图片、轮播和场景留在专用测试 AVD 中。
