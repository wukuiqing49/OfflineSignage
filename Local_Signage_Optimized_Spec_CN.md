# Local Signage

## Android 本地广告机系统 --- Web 控制端与 Android 实现规格

> 目标：将 Android
> 手机、平板、电视盒子等设备变成可长期运行的本地数字广告机。\
> Android 设备本身同时承担
> **本地服务器、资源存储、播放器和设备发现**；电脑或手机只需浏览器即可控制。\
> 第一版坚持 **Local-first / Offline-first / No Cloud / No Account**。

## 产品定位与第一版边界

本项目不与传统 Cloud Digital Signage SaaS 正面竞争。

核心定位：

> **Turn any Android device into an offline digital sign. Control it
> from any browser on your local network.**

中文理解：

> **把任意 Android
> 手机、平板、电视盒子变成广告屏，通过局域网中的任意浏览器直接控制，无需云端。**

第一版最重要的不是复杂编辑能力，而是：

``` text
安装 App
↓
显示控制地址 / QR Code
↓
浏览器打开
↓
上传图片 / 视频或添加网页
↓
立即播放 / 创建 Playlist
↓
关闭浏览器
↓
广告机继续独立播放
↓
设备重启后自动恢复
```

第一版优先解决以下真实使用痛点：

-   没有 Internet 也能工作
-   不需要 Cloud CMS
-   不需要注册账号
-   不需要专门服务器
-   控制电脑不需要安装软件
-   旧 Android 手机 / 平板可以直接利用
-   多台 Android 广告机可以在局域网中自动发现
-   图片和视频同步到本机后独立播放
-   断电 / 重启后能够恢复
-   长时间播放不会自动息屏
-   浏览器可以远程控制播放和声音

产品关键词方向：

``` text
Offline Digital Signage
Android Digital Signage
Android Signage Player
Local Digital Signage
Local Network Signage
No Cloud Digital Signage
Digital Signage Without Internet
Digital Menu Board
Android TV Signage
Tablet Signage
Kiosk Display
```

产品开发时应始终围绕：

> **Local + Simple + Reliable**

避免第一版演变成复杂 CMS。

------------------------------------------------------------------------

# 0. 核心原则

1.  Android 广告机自己就是本地 Server。
2.  Web 浏览器只是控制端，不承担局域网扫描和资源播放。
3.  所有广告机都是平等节点，不设置永久 Master。
4.  用户访问哪台广告机，哪台设备临时作为 Gateway。
5.  IP 地址不是设备身份，永久 `deviceId` 才是。
6.  图片、视频等资源尽量同步到每台广告机本地后再播放。
7.  核心内容模型采用：`Resource → Scene → Playlist`。
8.  多个控制端可以查看，但同一控制范围同时只有一个写入 Session。
9.  广告机断电或重启后应尽可能自动恢复 Server 和上次播放状态。
10. 第一版不引入云账号、云存储和公网 SaaS。

------------------------------------------------------------------------

# 第一部分：Web 控制端

## 1. Web 控制端定位

Web 控制端通过浏览器访问广告机：

``` text
http://192.168.1.105:8080
```

Web 页面由 Android 广告机自己的 HTTP Server 提供。

控制端不需要：

-   安装 App
-   注册账号
-   云服务器
-   云存储
-   手动连接每一台广告机

Web 负责表达"用户希望广告机做什么"，Android 负责真正执行。

------------------------------------------------------------------------

## 2. 首次连接流程

广告机启动后，播放端首先显示一个简单的连接页面：

``` text
Local Signage

Device: Entrance Screen
Status: Ready

Control this screen:

http://192.168.1.105:8080

[ QR Code ]

No Internet Required
```

用户可以：

1.  在电脑浏览器输入地址，再输入广告屏显示的一次性电脑配对码；
2.  使用手机扫描 QR Code；
3.  两种方式分别签发浏览器 Web Token，并进入 Web 控制页面。

QR Code 中只保存当前局域网控制地址和必要的临时连接 Token。电脑使用独立的 6 位数字一次性配对码；两种凭据均为 5 分钟有效且分别在使用后失效，避免手机扫码影响电脑正在进行的配对。电脑码交换接口必须限制连续失败次数。

进入正常播放状态后，不需要持续显示连接信息。

------------------------------------------------------------------------

## 3. Web 页面结构

建议第一版包含：

``` text
Dashboard
Devices
Resources
Slideshows
Scenes
Playlists
Now Playing
Sync
Settings
```

------------------------------------------------------------------------

## 4. Dashboard

显示整个局域网广告机的概览。

需要展示：

-   在线设备数量
-   离线设备
-   当前播放内容
-   当前 Playlist
-   资源同步状态
-   播放错误
-   当前控制端
-   是否存在其他浏览器正在控制

示例：

``` text
4 Devices Online

Entrance       Playing     Summer Promotion
Counter        Playing     Menu
Window         Syncing     68%
Meeting Room   Offline
```

------------------------------------------------------------------------

## 5. Devices 设备管理

显示自动发现的广告机。

每个设备展示：

-   Device Name
-   deviceId
-   IP
-   Port
-   App Version
-   Online / Offline
-   当前 Scene
-   当前 Playlist
-   音量
-   静音状态
-   屏幕常亮状态

支持：

-   单选
-   多选
-   全选
-   重命名
-   查看状态
-   同步内容
-   立即播放
-   调整音量
-   静音 / 取消静音

------------------------------------------------------------------------

## 6. Resources 资源库

资源按照内容类型分类。

### 5.1 图片

支持：

-   本地上传图片
-   网络图片 URL

常见格式：

``` text
JPG
JPEG
PNG
WebP
GIF（可选）
```

------------------------------------------------------------------------

### 5.2 视频

支持：

-   本地上传视频
-   网络视频 URL

第一版重点：

``` text
MP4
WebM（设备支持时）
HTTP(S) Video
```

------------------------------------------------------------------------

### 5.3 HTML / Web

支持：

``` text
Local HTML
Remote Web URL
```

例如：

``` text
本地活动页面
菜单网页
公司 Dashboard
实时数据页面
```

------------------------------------------------------------------------

### 5.4 直播流

支持：

``` text
HLS
DASH
RTSP
```

第一版优先：

``` text
HLS
RTSP
```

------------------------------------------------------------------------

### 5.5 Text

支持：

-   普通文字
-   公告
-   跑马灯
-   多行文字

可配置：

-   字号
-   字体粗细
-   对齐
-   文字颜色
-   背景颜色
-   滚动方向
-   滚动速度

------------------------------------------------------------------------

### 5.6 Overlay

Overlay 是覆盖在主内容上的元素。

支持：

``` text
Logo
文字
角标
日期
时间
滚动字幕
```

例如：

``` text
主视频
+
右上角 Logo
+
左下角时间
+
底部跑马灯
```

------------------------------------------------------------------------

## 7. Local / Remote 来源

资源类型和资源来源必须分开。

``` text
Resource Type:
IMAGE
VIDEO
WEB
HTML
STREAM
TEXT
OVERLAY

Source:
LOCAL
REMOTE
GENERATED
```

不要设计成：

``` text
LocalImage
RemoteImage
LocalVideo
RemoteVideo
```

避免模型膨胀。

------------------------------------------------------------------------

## 8. Slideshow 轮播

Slideshow 是组合内容，不是单个 Resource 文件。

第一版支持：

``` text
本地图片
+
网络图片
```

配置：

-   顺序
-   单张显示时长
-   Loop
-   Fade
-   下一张 / 上一张

后续可扩展：

``` text
图片 + 视频 + HTML
```

------------------------------------------------------------------------

## 9. Scene

Scene 表示广告机完整的一屏。

结构：

``` text
Scene
├── Primary Content
└── Overlay[]
```

Primary Content 可以是：

``` text
Image
Video
HTML / Web
Stream
Text
Slideshow
```

------------------------------------------------------------------------

## 10. 图片 / 视频填充方式

这是 Scene 的显示配置，不应该永久绑定在 Resource 上。

同一个视频可以：

``` text
Scene A → FIT
Scene B → FILL
```

支持：

### FIT

保持原始比例，完整显示内容。

可能出现留边。

### FILL

保持比例并铺满屏幕。

超出屏幕部分裁剪。

### STRETCH

强制拉伸到屏幕大小。

可能产生变形。

### CENTER

保持原始比例并居中。

### CROP

裁剪显示。

支持裁剪位置：

``` text
CENTER
TOP
BOTTOM
LEFT
RIGHT
```

------------------------------------------------------------------------

## 11. 图片背景方式

FIT 等模式出现空白区域时支持：

``` text
BLACK
WHITE
CUSTOM COLOR
BLUR
```

Web UI：

``` text
Fit Mode
[ Fill ▼ ]

Background
[ Black ▼ ]

Crop Position
[ Center ▼ ]
```

------------------------------------------------------------------------

## 12. Scene 音频设置

Scene 可以配置：

``` text
Volume
Mute
```

例如：

``` text
Promotion Video → 50%
Menu → Mute
Live Stream → 30%
```

Scene Volume 可以为空。

为空时：

``` text
使用设备 Master Volume
```

------------------------------------------------------------------------

## 13. Playlist

Playlist 管理多个 Scene。

配置：

-   Scene 顺序
-   展示时长
-   Loop
-   Enable / Disable

例如：

``` text
Scene A   10 秒
↓
Scene B   15 秒
↓
Scene C   视频播放完成
↓
Scene A
```

直播 Scene 默认持续播放，直到收到切换命令或配置结束条件。

------------------------------------------------------------------------

## 14. Now Playing 即时控制

需要支持：

``` text
Play
Pause
Stop
Previous
Next
Play Scene
Play Playlist
Refresh Web
```

同时显示：

-   当前设备
-   当前资源
-   当前 Scene
-   当前 Playlist
-   播放进度
-   播放状态
-   错误状态

------------------------------------------------------------------------

## 15. Web 音量控制

Web 必须能够远程控制广告机声音。

支持：

``` text
Volume Slider
Mute
Unmute
```

例如：

``` text
Volume

0 ─────────●──── 100

[ Mute ]
```

多设备：

``` text
☑ Entrance
☑ Counter
☑ Window

Volume: 30%

[ Apply ]
```

Web 必须读取 Android 返回的真实音量状态，而不是只保存浏览器滑块值。

------------------------------------------------------------------------

## 16. 屏幕控制

Web Settings 至少提供：

``` text
Keep Screen Awake
Fullscreen
Auto Resume After Reboot
Orientation
Brightness（后续）
Kiosk Mode（后续）
```

第一版核心：

``` text
☑ Keep Screen Awake

☑ Auto Resume After Reboot

☑ Fullscreen
```

Orientation：

``` text
AUTO
LANDSCAPE
PORTRAIT
```

------------------------------------------------------------------------

## 17. 多控制端

可能同时存在：

``` text
PC Browser
Phone Browser
Tablet Browser
```

第一版规则：

> 多个浏览器可以同时查看，但同一管理范围同时只有一个 Control Session
> 拥有写权限。

其他浏览器：

``` text
Read Only
```

显示：

``` text
Currently controlled by:
Chrome on PC
```

------------------------------------------------------------------------

## 18. Take Control

其他浏览器可以执行：

``` text
Take Control
```

抢占控制权。

例如：

``` text
PC → Owner

Phone → Take Control

↓

PC 失去写权限

Phone → Owner
```

------------------------------------------------------------------------

## 19. Heartbeat

拥有控制权的浏览器定期发送：

``` text
Heartbeat
```

建议：

``` text
每 15 秒
```

超过：

``` text
60 秒
```

未收到 Heartbeat：

``` text
自动释放控制权
```

避免：

-   浏览器关闭
-   网络断开
-   电脑休眠
-   浏览器崩溃

造成永久锁定。

------------------------------------------------------------------------

## 20. 多设备控制

用户可以选择：

``` text
☑ Entrance
☑ Counter
☐ Window
☑ Meeting Room
```

然后统一：

-   播放 Scene
-   播放 Playlist
-   暂停
-   调音量
-   静音
-   同步资源

------------------------------------------------------------------------

## 21. Sync 页面

展示资源同步状态：

``` text
promo.mp4

Entrance     READY
Counter      READY
Window       68%
Meeting      FAILED
```

状态：

``` text
PENDING
TRANSFERRING
READY
FAILED
```

支持：

``` text
Retry
Cancel
```

------------------------------------------------------------------------

# 第一部分补充：广告机可靠性要求

## 播放可靠性优先级

广告机属于长时间无人值守设备，可靠性优先于动画效果和复杂编辑功能。

第一版必须优先保证：

``` text
Auto Start
Auto Resume
Keep Screen Awake
Local Playback
Error Recovery
State Persistence
```

### 断电 / 重启

设备重新启动后：

``` text
BOOT_COMPLETED
↓
SignageService
↓
Server
↓
Discovery
↓
Player
↓
Last Playlist
```

整个过程尽可能不要求人工干预。

### 浏览器退出

浏览器只是管理工具。

浏览器关闭后：

``` text
Android Player
```

必须继续独立播放。

### Gateway 退出

Gateway 只负责当前管理和资源分发。

资源同步完成后：

``` text
Device B
Device C
Device D
```

不得依赖 Gateway 持续在线。

### 网络中断

本地图片、本地视频、本地 HTML 和已缓存 Remote Resource：

``` text
继续播放
```

Web URL / Live Stream 等必须联网的内容：

``` text
Retry
或
Fallback Scene
```

### 播放错误

单个 Resource 出错不得导致：

``` text
整个 App 崩溃
SignageService 停止
Playlist 永久停止
```

应记录错误并根据策略：

``` text
Retry
Skip
Fallback
```

------------------------------------------------------------------------

# 第二部分：Web ↔ Android API 协议

## 22. Device API

``` text
GET /api/device
GET /api/devices
GET /api/status
```

------------------------------------------------------------------------

## 23. Control API

``` text
POST /api/control/acquire
POST /api/control/heartbeat
POST /api/control/release
POST /api/control/takeover
```

Control Session：

``` json
{
  "sessionId": "xxx",
  "clientName": "Chrome on PC",
  "deviceIds": ["device-a"],
  "lastHeartbeat": 123456789,
  "expiresAt": 123456999
}
```

------------------------------------------------------------------------

## 24. Resource API

``` text
GET    /api/resources
POST   /api/resources/upload
POST   /api/resources/remote
DELETE /api/resources/{id}
GET    /api/resources/{hash}/exists
```

------------------------------------------------------------------------

## 25. Scene API

``` text
GET    /api/scenes
POST   /api/scenes
DELETE /api/scenes/{id}
```

------------------------------------------------------------------------

## 26. Playlist API

``` text
GET    /api/playlists
POST   /api/playlists
DELETE /api/playlists/{id}
```

------------------------------------------------------------------------

## 27. Device Control API

``` text
POST /api/devices/sync
POST /api/devices/play
POST /api/devices/pause
POST /api/devices/stop
POST /api/devices/volume
POST /api/devices/mute
```

多设备播放示例：

``` json
{
  "deviceIds": [
    "device-a",
    "device-b"
  ],
  "playlistId": "playlist-001",
  "revision": 104
}
```

------------------------------------------------------------------------

## 28. WebSocket

建议事件：

``` text
PING
PONG

DEVICE_STATUS

NOW_PLAYING

CONTROL_OWNER_CHANGED

RESOURCE_SYNC_PROGRESS

PLAY
PAUSE
STOP
NEXT
PREVIOUS

PLAY_SCENE
PLAY_PLAYLIST

SET_VOLUME
MUTE
UNMUTE

ERROR
```

------------------------------------------------------------------------

## 29. Command Revision

为了避免：

``` text
Browser → Device A Gateway
```

和：

``` text
Browser → Device B Gateway
```

同时控制同一设备产生旧命令覆盖新命令的问题，每台广告机维护：

``` text
commandRevision
```

例如：

``` text
101
102
103
```

命令：

``` json
{
  "action": "PLAY_PLAYLIST",
  "playlistId": "playlist-001",
  "revision": 103
}
```

如果设备当前已经是：

``` text
revision = 103
```

又收到：

``` text
revision = 102
```

则拒绝旧命令。

第一版采用：

> Last Valid Revision Wins

无需实现复杂分布式一致性。

------------------------------------------------------------------------

# 第三部分：Android 广告机实现

## 30. Android 核心模块

建议：

``` text
app/

server/
├── HttpServer
├── WebSocketServer
└── ApiRoutes

discovery/
├── NsdDiscovery
└── UdpDiscovery

device/
├── DeviceManager
└── DeviceRepository

control/
├── ControlSessionManager
└── CommandRevisionManager

resource/
├── ResourceRepository
├── ResourceStorage
└── ResourceSyncManager

scene/
├── SceneRepository
└── SceneManager

playlist/
├── PlaylistRepository
└── PlaylistManager

player/
├── PlayerController
├── ImagePlayer
├── VideoPlayer
├── WebPlayer
├── StreamPlayer
├── SlideshowPlayer
└── OverlayRenderer

display/
├── ScreenAwakeController
├── FullscreenController
├── OrientationController
└── DisplayModeController

audio/
└── AudioController

service/
└── SignageService

database/

ui/

assets/web/
├── index.html
├── js/
└── css/
```

------------------------------------------------------------------------

## 31. Android 本地 Server

Android 启动：

``` text
HTTP Server
+
WebSocket Server
```

HTTP Server 负责：

-   返回 Web 页面
-   REST API
-   文件上传
-   资源下载
-   设备控制

WebSocket 负责：

-   即时控制
-   状态同步
-   播放状态
-   同步进度
-   Heartbeat

------------------------------------------------------------------------

## 32. SignageService

核心逻辑必须运行在：

``` text
Foreground Service
```

不要依赖 Activity。

SignageService 负责：

``` text
HTTP Server
WebSocket
Device Discovery
Resource Manager
Resource Sync
Player State
Recovery
```

Activity 负责：

``` text
显示播放器 UI
设置 UI
```

------------------------------------------------------------------------

## 33. 开机自动恢复

平台约束：Android 15 及以上禁止普通应用从 `BOOT_COMPLETED` 直接启动 `mediaPlayback` 前台服务。普通 Play 设备应在用户首次打开应用后恢复；无人值守自动恢复需要厂商自启动、Kiosk/Launcher、Device Owner 或等效的专用设备能力。

目标：

> Android 设备断电 /
> 重启以后，无需人工操作，自动恢复广告机服务和播放状态。

流程：

``` text
BOOT_COMPLETED
    ↓
Start SignageService
    ↓
Start HTTP Server
    ↓
Start WebSocket
    ↓
Start Device Discovery
    ↓
Load Last State
    ↓
Check Resources
    ↓
Restore Playlist
    ↓
Continue Playing
```

------------------------------------------------------------------------

## 34. 状态持久化

至少保存：

``` text
lastPlaylistId
lastSceneId
playState
lastPosition
loop
masterVolume
mute
orientation
keepScreenAwake
autoResume
fullscreen
```

不能只保存在内存。

------------------------------------------------------------------------

## 35. 屏幕常亮

播放模式需要支持：

``` text
Keep Screen Awake
```

普通 Android 第一版使用：

``` kotlin
window.addFlags(
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
)
```

或者：

``` kotlin
view.keepScreenOn = true
```

退出广告播放模式时释放：

``` kotlin
window.clearFlags(
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
)
```

第一版不要直接修改系统全局：

``` text
SCREEN_OFF_TIMEOUT
```

避免永久改变用户系统设置。

------------------------------------------------------------------------

## 36. Fullscreen

播放页面进入沉浸式全屏。

目标：

-   隐藏 App Toolbar
-   隐藏不必要 UI
-   尽可能减少状态栏 / 导航栏干扰
-   用户退出播放模式后恢复正常界面

------------------------------------------------------------------------

## 37. Kiosk

第一版：

``` text
Fullscreen
Keep Screen Awake
Auto Resume
```

后续专业版：

``` text
Device Owner
Lock Task Mode
Kiosk Mode
Launcher Mode
Boot Directly Into Signage
```

------------------------------------------------------------------------

## 38. Android 音频控制

需要区分两层。

### Player Volume

控制当前 App 播放器：

``` kotlin
player.volume = 0.4f
```

第一版优先实现。

### System Media Volume

后续可通过：

``` text
AudioManager
```

控制系统媒体音量。

第一版默认不要强制修改整个系统音量。

------------------------------------------------------------------------

## 39. Master Volume

每台广告机保存：

``` text
Master Volume
Mute
```

例如：

``` text
Master Volume = 70%
```

Scene：

``` text
Scene Volume = 50%
```

最终可以按照：

``` text
Master Volume × Scene Volume
```

计算播放器音量。

------------------------------------------------------------------------

## 40. 图片播放器

支持：

``` text
FIT
FILL
STRETCH
CENTER
CROP
```

Android 根据统一的 `ContentDisplayConfig` 转换成对应 ImageView / Compose
ContentScale。

不要在业务层直接保存 Android 的：

``` text
CENTER_CROP
FIT_CENTER
```

协议层使用跨平台语义：

``` text
FIT
FILL
STRETCH
CENTER
CROP
```

------------------------------------------------------------------------

## 41. 视频播放器

使用：

``` text
AndroidX Media3
```

支持：

``` text
Local Video
HTTP Video
HLS
DASH
RTSP
```

视频同样支持：

``` text
FIT
FILL
STRETCH
```

必要时支持：

``` text
CROP
```

由 Android 映射到 Media3 对应 Resize Mode。

------------------------------------------------------------------------

## 42. Web / HTML

使用：

``` text
WebView
```

支持：

``` text
Local HTML
Remote URL
```

需要：

-   安全限制
-   页面加载错误处理
-   网络断开 fallback
-   可配置刷新
-   禁止不必要的 file access

------------------------------------------------------------------------

## 43. Stream

直播流优先：

``` text
HLS
RTSP
```

使用 Media3。

断流后：

``` text
自动重连
```

建议指数退避：

``` text
1s
2s
4s
8s
30s
```

达到最大间隔后持续重试。

可配置：

``` text
Fallback Scene
```

------------------------------------------------------------------------

## 44. Slideshow Player

负责：

``` text
图片切换
Timer
Transition
Loop
```

资源已经缓存时优先播放本地资源。

------------------------------------------------------------------------

## 45. Overlay Renderer

Overlay 独立于主播放器。

例如：

``` text
FrameLayout / Compose Box

├── Primary Player
├── Logo
├── Clock
└── Ticker
```

Overlay 不应该修改视频本身。

------------------------------------------------------------------------

## 46. ContentDisplayConfig

建议统一：

``` kotlin
ContentDisplayConfig {
    fitMode:
        FIT
        FILL
        STRETCH
        CENTER
        CROP

    cropGravity:
        CENTER
        TOP
        BOTTOM
        LEFT
        RIGHT

    backgroundType:
        BLACK
        WHITE
        COLOR
        BLUR

    backgroundColor: String?
}
```

该配置属于：

``` text
Scene / ContentRef
```

而不是 Resource。

------------------------------------------------------------------------

## 47. Device Discovery

每台设备第一次启动生成：

``` text
UUID deviceId
```

Device：

``` kotlin
Device {
    deviceId: String
    name: String
    ip: String
    port: Int
    status: ONLINE | OFFLINE
    version: String
    currentSceneId: String?
    currentPlaylistId: String?
}
```

------------------------------------------------------------------------

## 48. NSD / mDNS

优先：

``` text
Android NSD
```

服务：

``` text
_localsignage._tcp
```

每台广告机广播：

-   deviceId
-   Device Name
-   Port
-   App Version

当前 Gateway 自动发现其他广告机。

------------------------------------------------------------------------

## 49. UDP Discovery

作为兼容兜底。

Gateway：

``` text
WHO_IS_SIGNAGE?
```

其他设备：

``` text
SIGNAGE
deviceId=xxx
name=Entrance
port=8080
```

------------------------------------------------------------------------

## 50. 资源存储

本地上传资源：

``` text
Browser
↓
Gateway
↓
Local Storage
```

保存后：

``` text
计算 SHA-256
↓
创建 Resource
```

------------------------------------------------------------------------

## 51. 多设备资源同步

例如：

``` text
promo.mp4
```

Gateway 先询问目标设备：

``` text
GET /api/resources/{hash}/exists
```

如果：

``` text
TRUE
```

不重复上传。

如果：

``` text
FALSE
```

再同步文件。

------------------------------------------------------------------------

## 52. 每台设备独立播放

禁止设计成：

``` text
Device B
↓
长期从 Device A 流式读取 promo.mp4
```

正确方式：

``` text
Device A
↓
同步 promo.mp4
↓
Device B 本地保存
↓
Device B 本地播放
```

这样 Gateway：

-   重启
-   关闭
-   离线

其他设备仍然正常播放。

------------------------------------------------------------------------

## 53. 网络资源缓存

对于：

``` text
Remote Image
Remote Video
```

支持：

``` text
DIRECT
CACHE
```

默认建议：

``` text
CACHE
```

因为广告机首先追求稳定。

------------------------------------------------------------------------

## 54. 网络失败

### Remote Image / Video

``` text
网络失败
↓
使用缓存
```

### Web

``` text
网络失败
↓
Fallback Scene
```

### Stream

``` text
断流
↓
重连
↓
必要时显示 Fallback
```

资源失败不能导致：

``` text
SignageService Crash
```

------------------------------------------------------------------------

## 55. 多控制端 Android 实现

Android 维护：

``` text
ControlSessionManager
```

负责：

-   Acquire
-   Heartbeat
-   Release
-   Takeover
-   Timeout

其他 Web 客户端仍可以订阅状态，但不能发送修改命令。

------------------------------------------------------------------------

## 56. Gateway

每台广告机都拥有：

``` text
HTTP Server
Discovery
Resource Sync
Device Control
```

所以任何一台都可以成为 Gateway。

不存在：

``` text
永久 Master
```

------------------------------------------------------------------------

## 57. 安全

第一版至少考虑：

-   局域网 Server 默认不暴露公网
-   可设置控制 PIN
-   首次连接 Token
-   修改类 API 需要 Session
-   文件上传限制 MIME / Size
-   防止路径穿越
-   WebView 限制危险 file access
-   Remote URL 做协议白名单

------------------------------------------------------------------------

# 第四部分：MVP 实现顺序

## MVP 优先级

### P0：必须稳定

``` text
Local HTTP Server
Web Control
Image
Video
Web URL
Playlist
Local Storage
Keep Screen Awake
Fullscreen
Volume / Mute
Boot Recovery
Auto Resume
```

### P1：形成差异化

``` text
NSD / mDNS
Multiple Devices
Resource Hash Sync
Multi-device Play
Multi-device Volume
Control Session
```

### P2：发布后增强

``` text
Live Stream
Overlay
Advanced Slideshow
Blur Background
Command Revision
UDP Discovery Fallback
Scene Advanced Settings
```

如果开发时间不足，优先保证 P0 和 P1。

不要为了 Overlay、动画或复杂 Scene Editor 延迟第一版发布。

## Step 1

实现：

``` text
Android HTTP Server
```

浏览器可以打开：

``` text
http://device-ip:8080
```

------------------------------------------------------------------------

## Step 2

实现：

``` text
Browser
↓
WebSocket
↓
Android
↓
显示文字
```

验证完整控制链路。

------------------------------------------------------------------------

## Step 3

实现：

``` text
图片上传
本地保存
图片播放
FIT / FILL
```

------------------------------------------------------------------------

## Step 4

接入 Media3：

``` text
本地视频
网络视频
音量
Mute
FIT / FILL
```

------------------------------------------------------------------------

## Step 5

实现：

``` text
Web / HTML
HLS
RTSP
```

------------------------------------------------------------------------

## Step 6

实现：

``` text
Resource
Slideshow
Scene
Playlist
```

------------------------------------------------------------------------

## Step 7

实现广告机基础能力：

``` text
Keep Screen Awake
Fullscreen
Orientation
状态持久化
```

------------------------------------------------------------------------

## Step 8

实现：

``` text
BOOT_COMPLETED
SignageService
Auto Resume
Playlist Restore
```

------------------------------------------------------------------------

## Step 9

实现：

``` text
NSD / mDNS
```

发现多台广告机。

------------------------------------------------------------------------

## Step 10

实现：

``` text
SHA-256
Resource Exists
Resource Sync
```

------------------------------------------------------------------------

## Step 11

实现：

``` text
多设备播放
多设备音量
多设备静音
```

------------------------------------------------------------------------

## Step 12

实现：

``` text
Control Session
Heartbeat
Take Control
```

------------------------------------------------------------------------

## Step 13

实现：

``` text
Command Revision
冲突保护
```

------------------------------------------------------------------------

# 第五部分：第一版暂不实现

第一版不要做：

-   云账号
-   云素材库
-   公网远程管理
-   多门店 SaaS
-   团队复杂权限
-   广告播放统计
-   AI 内容生成
-   模板商城
-   复杂自由分屏设计器
-   企业 MDM

优先把：

``` text
局域网控制
+
稳定播放
+
重启恢复
+
资源本地化
+
多设备
+
多控制端
```

做好。

------------------------------------------------------------------------

## 第一版产品体验目标

用户第一次安装后，应尽量在 **1 分钟内完成首次播放**：

``` text
安装
↓
打开
↓
扫描 QR Code
↓
上传图片
↓
Play
```

不要强制：

``` text
注册
登录
创建 Workspace
创建 Organization
创建 Location
绑定 Cloud Device
```

这就是本产品与传统 Digital Signage SaaS 最重要的体验差异之一。

------------------------------------------------------------------------

# 第六部分：验收标准

## 单设备

必须：

-   浏览器可访问广告机
-   无互联网也能管理
-   上传图片
-   上传视频
-   添加网络图片
-   添加网络视频
-   播放 HTML / Web
-   播放直播流
-   Text / Ticker
-   Slideshow
-   Scene
-   Playlist
-   音量控制
-   Mute
-   图片 / 视频填充方式
-   Keep Screen Awake
-   Fullscreen

------------------------------------------------------------------------

## 重启

必须：

-   Android 重启后恢复 SignageService
-   HTTP Server 恢复
-   Device Discovery 恢复
-   上次 Playlist 恢复
-   Master Volume 恢复
-   Mute 状态恢复
-   Keep Screen Awake 配置恢复
-   本地资源仍可播放

------------------------------------------------------------------------

## 多设备

必须：

-   自动发现至少两台广告机
-   不要求用户手工录入所有 IP
-   可以选择多台设备
-   可以同步资源
-   相同 Hash 不重复同步
-   可以统一播放 Playlist
-   可以统一调整音量
-   可以统一 Mute
-   Gateway 退出后其他设备继续播放

------------------------------------------------------------------------

## 多控制端

必须：

-   多个浏览器可以同时查看
-   同一管理范围只有一个写入 Owner
-   支持 Take Control
-   Heartbeat 超时自动释放
-   所有浏览器能实时看到设备状态
-   旧 Revision 不覆盖新命令

------------------------------------------------------------------------

## 稳定性

必须：

-   网络图片失败不崩溃
-   网络视频失败不崩溃
-   Web 加载失败不崩溃
-   直播断流自动重连
-   播放器错误不终止 SignageService
-   Activity 重建不终止 Server
-   DHCP IP 改变不改变设备身份

------------------------------------------------------------------------

# 最终架构总结

``` text
                    Web Browser
                         │
                  HTTP / WebSocket
                         │
                         ▼
               Temporary Gateway
                         │
           ┌─────────────┼─────────────┐
           ▼             ▼             ▼
       Device A       Device B       Device C
           │             │             │
       Local Files    Local Files    Local Files
           │             │             │
         Player         Player         Player
```

每台 Android 广告机都是完整节点：

``` text
Server
+
Storage
+
Player
+
Discovery
+
Sync
```

控制层：

``` text
Web
```

内容层：

``` text
Resource
↓
Scene
↓
Playlist
```

可靠性层：

``` text
Foreground Service
+
Boot Recovery
+
Local Cache
+
Keep Screen Awake
+
State Persistence
```

多设备层：

``` text
NSD / mDNS
+
UDP Fallback
+
Hash Sync
```

并发控制层：

``` text
Control Session
+
Heartbeat
+
Take Control
+
Command Revision
```

这就是第一版 Local Signage 的完整基础架构。
