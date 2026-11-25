---
name: devhub-push-web-integration
description: 为 Android 宿主配置 DevHub 与 FCM 推送、通知图标和点击路由，接入 CMS 内容展示、内置 WebView、沉浸式及 JavaScript 原生交互，并完成指定设备联调。用于 DevHub 推送和网页交互接入，不用于 Git push 或仅基础统计接入。
---

# DevHub 推送与内置 Web / JS

完成“CMS 配置 → 服务端发送 → Android 接收展示 → 内部页面 → JS 回调”的已授权部分。先读取目标项目 AGENTS.md、现有接入文档、构建配置、Application、FirebaseMessagingService、路由和 Web 容器。同步本 Skill 到 DevLib 不代表向 DevLib 迁入业务代码、SDK 或账户。

## 先确认真实实现

- 分别定位宿主、DevHub API Worker、CMS Pages，核对实际部署版本和本地代码；CMS 首页不必然等于 API 根地址，是否同源代理按实际配置判断。
- DevHub SDK 只负责登记，不自带 Firebase 接收器、通知、WebView 或业务桥。复用宿主 Base、网络、路由与现有 Firebase 服务，避免重复初始化或重复通知。
- 核对 minSdk、Firebase BOM、SDK/API 版本及版本目录。依赖支持推送登记的正式版或不可变提交，不复制其他 App 的版本、App ID、包名、Google Client ID、Topic 或域名。
- 配置和发送读 [配置与诊断](references/configuration.md)；WebView、沉浸式、H5/原生协议读 [内置页面与 JS](references/web-js.md)。引用的源码路径相对找到的目标仓库，不依赖某台电脑的绝对路径。

版本相关 API、迁移编号和示例协议必须按 [版本追溯记录](references/configuration.md#版本追溯记录) 关联实际源码与部署版本；历史核对日期不代表本轮已验证。

## Android 接入

1. 沿用现有 Firebase 项目配置、Application 和 FirebaseMessagingService。启动主动读取当前 Token，也处理 onNewToken；登录/切换身份和退出分别沿用 identifyGoogle / clearIdentity。定向推送查询当前设备关联，不能用历史安装关系代替。
2. 运营偏好与系统通知权限、渠道开关分别管理。Android 13+ 按实际 targetSdk 请求 POST_NOTIFICATIONS，拒绝不阻塞主功能。渠道 ID 稳定，不更换 ID 绕过用户关闭。
3. 调用当前 SDK 的 updatePushRegistration(token, permission, enabled)，权限枚举按版本确认。关闭偏好登记 enabled=false 并退订公共 Topic；DevHub collectionEnabled 不替代 Firebase 退订。启动、Token/权限/偏好变化、回前台时同步，复用节流与失败重试，不在 UI 重绘循环请求。
4. 状态区分登记中、权限不足、配置错误、Token 获取失败、网络失败和登记确认。等待有超时和恢复入口，迟到回调不能覆盖新尝试。登记成功不能显示成“推送已送达”。
5. 追踪前台手动展示与后台 SDK 自动展示两条路径。`setSmallIcon` 和 Manifest `com.google.firebase.messaging.default_notification_icon` 使用同一专用品牌轮廓图标；透明底、单色、24dp 内清晰，由系统着色。不要把不透明启动图标或业务通用文档图标当品牌小图标。`setLargeIcon`/大图不修复状态栏，展开通知和顶部状态栏分别检查。
6. 校验消息版本、ID、URL 后内部路由；覆盖首次 Intent 和 onNewIntent。不同通知使用唯一 PendingIntent，保留冷启动主入口与返回栈，不能改为外部浏览器绕开 JS 需求。图片下载不放主线程，消息重复投递与过期行为按既有协议处理。

## 验证与交付

按影响先编译资源/合并 Manifest，再执行现有 JVM/JS 检查与 app 构建；设备策略遵循目标项目与当前会话授权，授权已明确不重复询问。多设备只操作选定目标，保留 App 数据，不清库或卸载。DevLib 自身的免设备规则不扩散到消费项目。

需要真实联调时先准备明确测试对象、内容与环境，只发指定测试设备。超时/未知结果先查同一任务，不能换 requestId 盲目重发；测试权限不扩大为生产群发。图标更新安装后用新通知验证，旧通知不能当新资源证据。本地合成通知只证明显示，不代表真实 FCM。

逐阶段记录：配置检查、validate_only、登记、FCM accepted、设备收到、状态栏/展开通知、内部正文、JS 请求及回调。前台、后台、进程退出点击分开验证；进程回收不等于用户强行停止。未获真实登录/兑换/付款/反馈提交授权时只测能力和导航，不执行这些写操作。

报告列出实际修改文件、配置名称、源码/构建来源、脱敏证据、失败/受阻及未测项；有固定测试报告的项目合并现有记录，历史通过不冒充本轮通过。资源编译不等于视觉通过，mock 不等于线上联调，FCM accepted 不等于送达，打开会员页不等于付款成功。

凭据不进入 APK、H5、Skill、Git 或日志。执行配置、部署、发送和 Git 提交均按用户当前授权；安装/同步 Skill 不自动授权部署或创建 CI。保持自动发现，不增加无依据的审批步骤。

维护和接入验收使用 [行为案例](references/evaluation-cases.md)，按影响选择场景，区分静态检查、mock 和真实设备/服务端证据。
