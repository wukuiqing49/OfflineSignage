# Local Signage 未完成功能与明日实施计划

> 更新时间：2026-08-12
>
> 当前基线：P0 单设备播放和 P1 多设备控制主链路已经实现；本文只记录尚未完成、部分完成或尚未经过真实设备验收的内容。

## 1. 当前结论

当前工程已经具备：

- Android 全屏 Media3/ExoPlayer 图片和视频播放。
- Ktor HTTP Server、WebSocket 状态推送和内置 Web 控制页。
- Resource、Scene、Playlist 持久化和默认内容恢复。
- 播放失败 Retry、Skip、Fallback、错误记录和 Service 存活。
- Foreground Service、开机恢复、Keep Screen Awake、音量和静音。
- Control Session、Heartbeat、Takeover、Command Revision。
- NSD/mDNS 设备发现、设备配对持久化和设备 Token 鉴权。
- SHA-256 资源去重、资源同步、Scene/Playlist 同步和多设备控制。
- HTTPS 远程图片/视频缓存，下载后转为本地资源，断网可继续播放。

本轮新增远程资源接口：

```text
POST /api/resources/remote
{
  "url": "https://example.com/promo.mp4",
  "name": "promo.mp4"
}
```

远程下载当前限制 HTTPS、拒绝内网地址、限制重定向和文件大小，并复用本地资源配额与哈希去重。

## 2. 未实现功能总表

| 优先级 | 功能 | 当前状态 | 明日建议 |
|---|---|---|---|
| P1 收口 | 二维码首次连接 | 未实现 | 优先实现 |
| P1 收口 | 临时 Token 生命周期 | 部分实现，当前是持久控制 Token | 优先整改 |
| P1 收口 | Token 轮换与撤销 | 未实现 | 与二维码一起实现 |
| P1 收口 | 多设备实时状态聚合 | 部分实现，只返回操作结果 | 优先实现 |
| P1 收口 | API 契约和错误码统一 | 部分实现 | 与测试一起收口 |
| P2 | Local HTML / Remote WebView | 未实现 | 第二阶段 |
| P2 | HLS / DASH / RTSP | 未实现 | 按协议拆分 |
| P2 | Text / Ticker | 未实现 | 可先做静态 Text |
| P2 | Overlay | 未实现 | 依赖 Scene 模型升级 |
| P2 | Blur 背景 | 未实现 | 图片 FIT 模式后实现 |
| P2 | UDP Discovery fallback | 未实现 | NSD 不稳定时补充 |
| P2 | 复杂 Scene 编辑器 | 未实现 | 最后实现 |
| 质量 | 自动化测试 | 基础校验，无业务测试 | 明日同步补齐 |
| 质量 | 双真实设备联调 | 未完成 | 必须安排 |
| 质量 | 长时间、断电、重启测试 | 未完成 | 发布前必须完成 |

## 3. 明日第一优先级：二维码与 Token 生命周期

### 3.1 当前问题

当前 Web 页面和 `/api/device` 可以拿到设备控制 Token。它适合内部调试，但不符合正式产品的临时连接设计：

- Token 没有明确的短期有效期。
- 没有 Token 轮换。
- 没有 Token 撤销。
- 没有二维码生成和展示。
- 没有区分只读访问、控制访问和设备间同步访问。

### 3.2 建议接口

```text
GET  /api/pairing
POST /api/pairing/rotate
POST /api/pairing/revoke
```

建议返回：

```json
{
  "url": "http://192.168.1.10:8080/?pairingToken=...",
  "token": "...",
  "expiresAt": 0
}
```

要求：

- 二维码只保存局域网地址、端口和短期 Token。
- Token 使用随机高强度值，数据库只保存必要信息。
- 连接成功后可以立即撤销或轮换。
- 轮换后旧 Token 立即失效。
- Token 不写入日志和错误响应。
- Web 页面不直接展示长期设备密钥。

### 3.3 实施文件

- `SignageStore.kt`：保存 Token、过期时间、撤销状态。
- `SignageRuntime.kt`：暴露 pairing API。
- `KtorSignageServer.kt`：增加接口和二维码页面。
- `feature_app/build.gradle`：如无现有二维码库，增加稳定二维码生成依赖；否则先提供二维码内容 URL。
- `app/src/main/AndroidManifest.xml`：确认局域网访问边界。

### 3.4 验收

- 浏览器或手机扫描二维码可打开 Web 控制页。
- Token 过期后返回 `401`。
- 轮换后旧 Token 不能控制设备。
- 两个浏览器可以读状态，但只有一个 Control Session 能写入。
- 重启后未过期的临时配对行为符合产品决定；默认建议重启后仍可恢复设备服务，但配对 Token 重新生成。

## 4. 第二优先级：多设备实时状态聚合

### 当前状态

当前可以发现设备、读取单台状态、同步资源和发送控制，但 Web 页面主要展示：

- 设备是否配对。
- 操作是否成功。
- 返回的错误码。

还缺少稳定的设备状态聚合：

- online/offline。
- 当前 Scene、Playlist、资源。
- 播放/暂停状态。
- 音量、静音、错误。
- 最后心跳时间。
- 目标设备命令 revision。

### 建议实现

在 `SignageDeviceFleet` 增加：

```kotlin
fun statuses(targets: List<PairedDevice>): List<FleetStatus>
```

每台设备使用短连接超时请求：

```text
GET /api/status
```

新增接口：

```text
GET /api/devices/status
```

要求：

- 并发读取，单台失败不影响其他设备。
- 明确区分 `OFFLINE`、`UNAUTHORIZED`、`TIMEOUT`、`INVALID_RESPONSE`。
- 不在 API 返回 Token。
- WebSocket 可推送 Gateway 自身状态；多设备状态先采用轮询，后续再做设备间 WebSocket。

### 验收

- 关闭一台目标设备后，其他设备状态仍正常返回。
- 页面显示最后在线时间和失败原因。
- 恢复设备后状态自动恢复。
- 同步或控制结果能关联到最新设备状态。

## 5. 第三优先级：API 契约收口

当前接口已经可用，但与规格还有以下差异或风险：

- 规格使用 `X-Control-Session`，实现使用 `X-Local-Signage-Session`，需要统一或兼容两者。
- 规格要求 `X-Command-Id` 幂等，当前主要依赖 `commandRevision`，还没有完整 commandId 去重记录。
- 部分错误只返回 `code`，缺少稳定的 `message` 和 `details`。
- 删除接口部分返回 `200`，规格允许但建议统一为 `204` 或固定 JSON。
- 上传过大、MIME 不支持、远程资源失败需要稳定映射到 `413/415/400/502`。
- 设备 Token、控制 Token、Session Token 的语义还需要在文档中明确区分。

### 建议

建立统一错误结构：

```json
{
  "error": {
    "code": "REMOTE_URL_PROTOCOL_NOT_ALLOWED",
    "message": "Remote URL protocol is not allowed",
    "details": {}
  }
}
```

建立 command result 表或轻量缓存，保存：

```text
deviceId / commandId / revision / action / result / createdAt
```

相同 `commandId` 重复请求返回第一次结果，不重复执行。

## 6. P2 播放能力

### 6.1 Local HTML / Remote WebView

未实现内容：

- HTML Resource 类型。
- 本地 HTML 上传和本地播放。
- Remote WebView 场景。
- 页面加载超时、失败页和离线策略。
- WebView 安全配置。

建议先做 Local HTML，再做 Remote WebView：

1. Resource 增加 `text/html` 类型和 HTML 文件保存。
2. 播放页增加 WebView 播放容器。
3. 关闭不必要的 `file access`、Universal Access From File URLs 和调试开关。
4. Remote WebView 默认只允许 HTTPS。
5. 所有 WebView 页面设置加载超时和错误状态。

风险：WebView 与 ExoPlayer 的全屏生命周期、进程崩溃、远程脚本和本地文件权限。

### 6.2 HLS / DASH / RTSP

目前只有本地图片/视频资源，Media3 直播流没有资源类型、URL 白名单和重连策略。

建议顺序：

1. HLS：Media3 默认支持，先实现 HTTPS `.m3u8`。
2. DASH：增加 MPD 资源和 DRM/错误策略评估。
3. RTSP：最后实现，明确 UDP/TCP、认证和断线重连。

每种流都需要：

- URL 协议白名单。
- 连接和缓冲超时。
- 断线重连。
- 播放失败 Retry/Skip/Fallback。
- 不把直播 URL 当作本地文件同步。
- Scene/Playlist 持久化流配置，但不保存敏感凭据。

### 6.3 Text / Ticker / Overlay

当前 Scene 只有单一 Resource，没有 Overlay 数组，也没有文字渲染模型。

建议新增：

```text
Overlay
  id
  type: TEXT | TICKER | IMAGE
  content
  x / y / width / height
  textSize
  textColor
  backgroundColor
  durationMs
  animation
  zIndex
```

先实现静态 Text，再实现 Ticker，最后实现动画。Overlay 应独立于视频播放器，避免修改视频文件。

### 6.4 Blur 背景

当前支持背景类型字段，但实际只保证基础背景。Blur 需要：

- 图片 FIT 时生成或实时绘制模糊背景。
- 控制 CPU/GPU 消耗。
- 图片切换时避免闪烁。
- 低端设备降级为纯色背景。

## 7. UDP Discovery fallback

当前已有 NSD/mDNS，但没有 UDP 兜底。

建议：

- 使用固定局域网 UDP 广播端口。
- 广播内容只包含设备 ID、名称、HTTP 端口、协议版本。
- 不广播控制 Token。
- 收到响应后仍必须通过 HTTP `/api/device` 校验设备。
- 只接受局域网接口和有效地址。
- 广播生命周期跟随 Service，停止时释放 socket。

## 8. 质量与发布前工作

### 自动化测试

当前已通过构建、架构、UI、i18n 校验，但缺少业务自动化测试。至少补充：

- `SignageStore`：上传、哈希去重、配额、删除、远程 URL 校验。
- Control Session：占用、Heartbeat、超时、Takeover。
- Command Revision：旧命令拒绝、新命令接受。
- Playlist：重复同步、未知 Scene、禁用项。
- Remote Download：重定向、非法协议、内网地址、超大文件、错误 MIME。
- Device Fleet：一台离线不影响其他设备。

### 双设备联调

- 两台 Android 设备连接同一局域网。
- 自动 NSD 发现。
- 配对并读取设备状态。
- 资源 Hash 去重同步。
- Scene/Playlist 同步。
- 统一播放、暂停、音量、静音。
- 关闭 Gateway 后目标设备继续播放本地文件。

### 稳定性测试

- 连续播放 24 小时。
- Activity 重建。
- Android 重启和 `BOOT_COMPLETED`。
- 网络断开后恢复。
- 存储空间不足。
- 损坏媒体文件。
- 大文件上传和远程下载中断。
- 快速连续点击控制命令。

### 发布检查

- 正式签名 APK 安装和升级。
- Release APK 启动、Service、全屏、播放和 Web 控制。
- 签名证书不进入 Git。
- R8 后检查 Media3、Ktor、WebSocket 和 JSON。
- 明确局域网明文 HTTP 是当前业务例外，并评估后续 TLS 或局域网限制。
- 检查 16 KB page size 风险和目标 SDK 兼容性。

## 9. 明天推荐执行顺序

### 上午：P1 收口

1. 统一 Header 和错误响应契约。
2. 实现临时配对 Token、轮换、撤销。
3. 实现二维码内容和 Web 页面展示。
4. 为配对与 Token 加测试。

### 下午：多设备可运维性

5. 实现 `/api/devices/status`。
6. Web 页面增加在线状态、最后心跳和错误原因。
7. 补齐 commandId 幂等缓存。
8. 做双设备联调 smoke check。

### 后续阶段

9. Local HTML/WebView。
10. HLS，再评估 DASH、RTSP。
11. Text/Ticker/Overlay。
12. Blur 背景。
13. UDP Discovery fallback。
14. 复杂 Scene Editor、统计和云能力。

## 10. 明日开始前的验证基线

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleRelease
python -X utf8 .agents/scripts/validate_android_workflows.py --project-root . --skip-figma
git diff --check
```

当前构建结果：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release.apk`
- 工作流校验：通过。
- 已知 warning：Manifest 开启局域网明文流量，且未声明 network security config；这是当前设备间 HTTP 通信的已知风险，不是构建失败。

## 11. 不要重复做的内容

明天继续时无需重新实现以下模块：

- Resource / Scene / Playlist 基础 CRUD。
- 本地图片和视频播放。
- 全屏暂停/继续状态页。
- Ktor Server 和 WebSocket 基础状态。
- Control Session、Heartbeat、Takeover、Command Revision。
- NSD/mDNS 发现和配对设备持久化。
- SHA-256 资源同步和多设备基础控制。
- HTTPS 远程图片/视频缓存。
