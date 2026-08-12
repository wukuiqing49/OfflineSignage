# Local Signage 实施任务

## 目标架构

```text
app                         应用壳、Application、Service、Manifest
core/core_base              WKQ 基础能力包装
core/core_utils             WKQ 工具能力包装
feature/feature_res         共享资源和 Web 控制台资源
feature/feature_app         本地 Server、领域模型、播放器、状态和页面编排
```

后续业务代码按职责划分为：`server`、`device`、`control`、`resource`、`scene`、`playlist`、`player`、`display`、`audio`、`service` 和 `database`。这些是包边界，不自动等同于 Gradle module。

## 阶段任务

### S0：工程基线

- 生成并通过 Android 多模块脚手架门禁。
- 固定 `com.wkq.localsignage`、Kotlin/XML/ViewBinding、MVVM、SDK 基线。
- 保留签名秘密隔离、网络安全、FileProvider、RTL 和 Edge-to-edge 基础配置。

### S1：单设备最小闭环

- 在 `feature_app` 建立 Resource、Scene、Playlist、DeviceState 数据类和 Repository 接口。
- 选择本地存储实现，持久化播放状态、设备设置和 commandRevision。
- 接入本地 HTTP Server，提供静态 Web 控制台入口和健康检查。
- 实现图片播放器、基础 Playlist 播放器、播放控制和状态事件。

验收：设备 IP 的 `:8080` 可访问，上传图片后可以创建 Playlist 并播放。

### S2：视频和可靠性

- 接入 AndroidX Media3，支持本地视频和基础网络视频。
- 实现 Foreground Service、BOOT_COMPLETED、播放 Activity 恢复和 Keep Screen Awake。
- 完成失败策略、错误状态、重试/跳过/fallback 和资源释放。

验收：重启设备后本地 Playlist 自动恢复，播放器错误不终止 Service。

### S3：控制安全与多控制端

- 实现临时 Token、Control Session、Heartbeat、Takeover。
- 完善 HTTP 状态码、统一错误、幂等 `commandId` 和 WebSocket 全量状态重同步。
- 实现按设备持久化的 commandRevision。

验收：两个浏览器可同时查看，只有 Owner 能修改；旧命令不会覆盖新命令。

### S4：多设备

- 使用 NSD/mDNS 发现设备并维护设备状态。
- 通过 SHA-256 和资源存在检查实现本地同步。
- 支持多设备播放、音量、静音和同步进度。

验收：至少两台设备自动发现；Gateway 离线后已同步设备继续播放。

### S5：扩展内容

- Remote Image/Video 缓存。
- HTML/WebView、HLS/RTSP、Text/Ticker、Overlay 和 Blur 背景。
- UDP Discovery 兜底与复杂编辑器。

## 当前模块依赖方向

```text
app -> feature_app -> core_base/core_utils
feature_app -> feature_res
core_* -/-> app 或 feature_app
feature_res -/-> 功能模块
```

网络、文件、存储、播放器和同步能力由 `feature_app` 的职责包统一提供，页面不得重复创建 Server、Player 或 Repository。

## 每个功能提交的最低交付物

- 数据模型和状态转换
- 正常流程、空态、加载态、错误态
- 受影响的资源与多语言文案
- API/事件或 Repository 契约
- 单元测试或可重复 smoke check
- 受影响门禁和 Gradle 任务结果

## 风险清单

- Android 目标 SDK 下 Foreground Service 和开机恢复限制。
- WebView 远程内容、明文局域网 HTTP 与 URL 白名单的安全边界。
- 大文件上传、断点续传、磁盘配额和 Hash 计算的线程模型。
- Media3 播放器、Surface 和 Service 生命周期释放。
- 多 Gateway 下 revision、幂等和 Session 的一致性。
- NSD 在不同网络、热点和 DHCP 变化下的发现稳定性。
