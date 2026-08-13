# Local Signage 功能规格

## 1. 文档目的

本文档把产品总纲收敛为可实现的第一版功能契约。原始产品背景和完整设想见 `Local_Signage_Optimized_Spec_CN.md`；本文件用于拆分功能、状态、权限、失败处理和验收范围。

## 2. 第一版目标

用户安装 Android 应用后，在 1 分钟内完成：

```text
启动设备 -> 打开局域网地址或扫描二维码 -> 上传图片/视频 -> 创建 Playlist -> 播放
```

第一版必须无账号、无云端、无专用控制端安装要求。Android 设备本地提供 Web 控制台、媒体存储和播放器。

## 3. 第一版边界

### P0：首个可用版本

- 单设备本地 HTTP Server 与 Web 控制台
- 本地图片、本地视频、Playlist
- Resource / Scene / Playlist 基础模型
- FIT、FILL、STRETCH、CENTER、CROP 显示模式
- 播放、暂停、停止、上一项、下一项
- 播放器音量、静音、Scene 音量
- 本地资源持久化、错误记录、播放失败跳过或 fallback
- Foreground Service、开机恢复、上次 Playlist 恢复、屏幕常亮、播放全屏
- 首次连接二维码和临时连接 Token

### P1：多设备版本

- NSD/mDNS 发现
- Gateway 发现设备并读取设备状态
- SHA-256 资源去重和本地同步
- 多设备统一播放、音量、静音
- 多浏览器只读查看与单写入 Control Session
- Heartbeat、超时释放、Take Control
- 按设备持久化的 Command Revision

### P2：第二阶段增强

- Remote Image / Video 缓存策略
- Local HTML 与 Remote WebView
- HLS、RTSP、DASH 直播流
- Text、Ticker、Overlay、Blur 背景
- UDP Discovery 兜底
- 复杂 Scene 编辑器、分屏、统计和云能力（后续阶段）

当前代码已实现 Remote Image/Video 缓存、Local HTML/Remote WebView、HLS/DASH/RTSP、Text/Ticker/Overlay、图片 Blur 背景和 UDP Discovery 兜底。状态为“代码完成、待目标设备与真实网络验收”，不得据此直接判定商业发布通过。

## 4. 核心数据模型

### Resource

```json
{
  "id": "resource-001",
  "hash": "sha256:...",
  "kind": "LOCAL_FILE|WEB|STREAM|TEXT",
  "source": "LOCAL_UPLOAD",
  "name": "promo.mp4",
  "mimeType": "video/mp4",
  "sizeBytes": 123456,
  "localPath": "shared/resources/...",
  "sourceUri": null,
  "content": null,
  "refreshIntervalMs": null,
  "createdAt": 0,
  "updatedAt": 0
}
```

资源是文件和来源信息，不保存 Scene 专属的显示模式。删除被 Scene 引用的资源时，API 必须返回冲突并提示先解除引用。

### Scene

```json
{
  "id": "scene-001",
  "name": "Promotion",
  "primary": {"kind": "RESOURCE", "resourceId": "resource-001"},
  "display": {"fitMode": "FILL", "cropGravity": "CENTER", "background": "BLACK"},
  "volume": null,
  "muted": false,
  "overlays": []
}
```

### Playlist

```json
{
  "id": "playlist-001",
  "name": "Default",
  "items": [{"sceneId": "scene-001", "durationMs": null, "enabled": true}],
  "loop": true,
  "updatedAt": 0
}
```

### DeviceState

设备身份使用持久化 `deviceId`，不使用 IP。状态至少包含：`deviceId`、`name`、`ip`、`port`、`appVersion`、`online`、`currentSceneId`、`currentPlaylistId`、`playState`、`positionMs`、`masterVolume`、`muted`、`keepScreenAwake` 和 `commandRevision`。

## 5. Web/API 契约

所有修改请求必须携带：

```text
X-Local-Signage-Token: <temporary-token>
X-Control-Session: <session-id>
X-Command-Id: <uuid>
```

错误统一为：

```json
{"error":{"code":"RESOURCE_NOT_FOUND","message":"Resource not found","details":{}}}
```

最小接口：

```text
GET    /api/device
GET    /api/status
GET    /api/resources
POST   /api/resources/upload
POST   /api/resources/remote
POST   /api/resources/virtual
DELETE /api/resources/{id}
GET    /api/scenes
POST   /api/scenes
DELETE /api/scenes/{id}
GET    /api/playlists
POST   /api/playlists
DELETE /api/playlists/{id}
POST   /api/control/acquire
POST   /api/control/heartbeat
POST   /api/control/release
POST   /api/control/takeover
POST   /api/devices/play
POST   /api/devices/pause
POST   /api/devices/stop
POST   /api/devices/volume
POST   /api/devices/mute
```

状态码约定：`200` 成功查询或修改，`201` 创建成功，`204` 删除成功，`400` 参数错误，`401` Token 无效，`403` 无写入权限，`404` 资源不存在，`409` revision/session 冲突，`413` 上传过大，`415` MIME 不支持，`500` 可恢复的服务错误。

WebSocket 用于实时状态，不替代 HTTP 资源 CRUD。连接建立后先发送完整 `DEVICE_STATUS`，断线重连后重新发送完整状态；客户端不能只依赖事件增量恢复界面。

## 6. 控制权与命令版本

- 同一设备只有一个写入 Owner，其他客户端可以读状态。
- Acquire 失败返回当前 Owner 的匿名显示名和过期时间。
- Heartbeat 建议 15 秒一次，60 秒无心跳自动释放。
- Takeover 立即使旧 Session 失效，并广播 `CONTROL_OWNER_CHANGED`。
- `commandRevision` 在目标设备本地持久化、按设备单调递增；设备只接受大于当前 revision 的命令。
- 相同 `X-Command-Id` 必须幂等，重复请求返回第一次执行结果。

## 7. 播放与恢复

播放器、HTTP Server、WebSocket、资源同步和恢复状态由 `SignageService` 管理，Activity 只负责显示和用户设置。

单资源失败按 `RETRY -> SKIP -> FALLBACK` 策略处理，不得停止 Service。开机顺序为：启动 Service、恢复 Server、恢复状态、检查本地资源、恢复 Playlist 和播放位置。资源播放优先使用本地文件。

## 8. 安全与限制

- 默认只监听局域网可达地址，不设计公网穿透。
- 上传限制 MIME、扩展名、单文件大小和总存储配额。
- 所有路径通过应用私有目录解析，拒绝 `..` 和符号链接逃逸。
- Remote 文件缓存和 Remote Web 仅允许 `https`；流媒体允许 HTTPS HLS/DASH 或 RTSP，禁止 URL 内嵌用户信息。
- WebView 禁止 file/content access、mixed content、地理位置和 JSBridge；Local HTML 使用隔离 HTTPS base URL。
- Token 不写入日志，不持久化为长期凭据；二维码只承载临时地址和 Token。

## 9. P0 验收

- 无互联网可打开 Web 控制台并上传图片/视频。
- 创建 Scene 和 Playlist 后可以播放、暂停、停止和切换。
- Android 重启后 Server、播放器、Playlist 和本地资源恢复。
- Activity 重建不停止 Server 或播放器服务。
- 音量、静音、常亮和全屏设置可恢复。
- 损坏资源不会导致应用或 Service 崩溃。
- `:app:assembleDebug`、结构门禁、架构门禁、构建门禁和 i18n 门禁通过。

## 10. 手机 Web 操作台

手机控制流程、工作区职责、控制权、安全约束和后续方向见 `LOCAL_SIGNAGE_WEB_MOBILE_WORKFLOW_CN.md`。控制台必须优先保证扫码连接、当前状态、快速播放和上传路径，不能把低频设备诊断与高频播放操作混在同一长页面中。

## 11. 首次启动与显示设置

- 新安装首次人工启动显示欢迎页，说明同局域网连接、扫码配对和全屏播放三步流程。
- 用户完成欢迎页后，后续桌面启动直接进入播放页；已有内容的覆盖升级自动跳过欢迎页。
- 开机恢复显式进入播放页，不依赖欢迎页完成状态，避免无人值守设备停在引导界面。
- `autoResume=false` 时进程重建不自动继续播放；常亮和全屏设置变更应立即作用于当前播放 Activity。
- 默认网络、链路属性或地址变化后，设备发现能力应防抖重启；Ktor Server 保持监听，无需因 DHCP 地址变化重建。
