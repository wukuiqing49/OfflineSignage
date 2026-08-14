# Local Signage 剩余工作

> 更新日期：2026-08-14
>
> 本文只记录尚未实现、尚未完成物理验收或明确延期的内容。支付不在当前范围。

## 1. 当前结论

第一阶段代码已完成，具备商业试运行所需的单设备播放、局域网管理、多设备基础控制、安全凭据、诊断与发布构建能力。以下能力已实现，但必须按 `LOCAL_SIGNAGE_COMMERCIAL_RELEASE_CHECKLIST_CN.md` 在真实硬件上验收后，才能标记为商业发布通过：

- Android 12、13、14、15、16 的安装、启动、前台服务和开机恢复。
- 手机、平板、Android TV、常见电视盒子的全屏显示与遥控器/触摸兼容。
- 两台设备的发现、配对、资源同步、Scene/Playlist 同步和统一控制。
- 断网、断电、重启、磁盘不足、损坏媒体和大文件中断。
- 24 小时连续播放与内存、温度、网络、服务稳定性。
- 正式签名 APK 的安装、覆盖升级和 R8 后运行。

## 2. 第一阶段已实现

| 能力 | 状态 | 说明 |
|---|---|---|
| 全屏图片/视频播放 | 已实现 | Media3/ExoPlayer；Android 端只展示暂停/继续和状态，无进度条 |
| 首次欢迎页 | 已实现 | 首次人工启动显示三步引导；已有内容升级和开机恢复直接进入播放页 |
| 首次连接页 | 已实现 | 设备名、IP、网络状态、临时二维码；无资源时显示 |
| 临时配对 Token | 已实现 | 5 分钟有效、一次性消费、轮换、撤销；同时提供手机二维码和电脑短码，两种入口共用消费状态 |
| Web 短期 Token | 已实现 | 8 小时有效、临期轮换/撤销、浏览器本地持久存储；未过期凭据跨 Android 进程重启保留，存储不可用时降级到标签页会话 |
| 配对鉴权 | 已实现 | 匿名页面不签发 Token，数据与操作 API 均鉴权 |
| 设备间长期 Token | 已实现 | Android Keystore AES-GCM；密钥失效时轮换并要求重新配对 |
| Control Session | 已实现 | Acquire、Heartbeat、Release、Takeover、双 Header 兼容 |
| commandId/revision | 已实现 | 幂等结果缓存、请求指纹、单调 revision |
| Resource/Scene/Playlist Web 管理 | 已实现 | 创建、列表、播放、删除；Playlist Scene 多选 |
| 多设备状态与控制 | 已实现 | NSD、配对、状态轮询、同步、播放、暂停、音量、静音 |
| 远程媒体缓存 | 已实现 | 仅 HTTPS、公网地址限制、重定向/大小/MIME/配额校验 |
| 诊断与日志导出 | 已实现 | 设备、系统、播放、数量、存储、最近错误；不导出凭据和内容 |
| 备份与网络安全声明 | 已实现 | 禁止系统备份；Manifest 中局域网 HTTP 为显式产品例外 |
| 设置行为闭环 | 已实现，待真机验收 | Auto Resume 参与启动恢复；常亮和全屏设置对当前 Activity 即时生效 |
| 网络切换发现恢复 | 已实现，待双真机验收 | 默认网络、能力或地址变化后防抖重启 NSD/UDP，保留 Ktor 监听 |
| 核心规则单元测试 | 已实现 | Token、配对过期、revision、commandId、Playlist 引用 |

## 3. 商业发布前未完成

这些不是可由桌面构建替代的测试，当前状态统一为“待现场执行”：

1. Android 12-16 与目标电视盒子/平板测试矩阵。
2. 双真机发现、配对、同步、控制和 Gateway 离线验证。
3. 断网、恢复网络、强制断电、开机恢复和 Activity 重建。
4. 24 小时连续图片/视频/Playlist 混播。
5. 低存储、满配额、损坏媒体和中断上传/下载。
6. 正式签名 Release APK 全新安装和覆盖升级。
7. R8 后 Ktor、WebSocket、Media3、JSON、二维码完整 smoke test。

每项都必须记录设备、Android 版本、APK SHA-256、时间、结果、证据和问题编号，不允许用模拟器结果替代电视盒子结论。

## 4. 第二阶段代码实现状态

| 能力 | 状态 | 说明 |
|---|---|---|
| Local HTML / Remote WebView | 已实现，待真机验收 | Local HTML 使用隔离 HTTPS base URL；Remote Web 仅 HTTPS；禁止 file/content/mixed-content/地理位置访问，无 JSBridge，主页面错误和超时进入 Retry/Skip/Fallback |
| HLS / DASH / RTSP | 已实现，待真机验收 | Media3 HLS/DASH/RTSP 官方模块；流地址禁用户信息；1/2/4/8/30 秒重试后进入 Skip/Fallback |
| Text 主内容 | 已实现，待真机验收 | 独立全屏 TextView，由统一 Scene 调度器管理时长、暂停和继续 |
| Text / Ticker Overlay | 已实现，待真机验收 | Scene Overlay 持久化、设备同步、层级/位置/颜色/字号/速度；Ticker 暂停与继续冻结动画 |
| Blur 背景 | 已实现，待真机验收 | Android 12+ 本地图片使用降采样 Bitmap + RenderEffect；低版本、视频、流和 Web 明确降级为背景色，不做高成本实时模糊 |
| UDP Discovery fallback | 已实现，待双真机验收 | UDP 18080 广播探测，与 NSD 结果合并；报文不含 Token，只接受站点本地 IPv4，60 秒淘汰过期结果 |
| Web 管理页扩展 | 已实现，待浏览器验收 | 可创建 Remote Web、Local HTML、HLS/DASH/RTSP、Text，并可为 Scene 添加静态文本或跑马灯 Overlay |
| SQLite schema v6 | 已实现，待升级验收 | 以新增列迁移 Resource kind/source/content/refresh 和 Scene overlays，不重建旧表 |

第二阶段仍不能标记为商业发布通过。必须完成 HLS/DASH/RTSP 服务端兼容、目标设备 WebView、双真机 UDP、Activity 重建、弱网恢复和 24 小时混合 Playlist 测试。

## 5. 真正剩余或后续阶段功能

- 可视化复杂 Scene 编辑器、分屏、排期和统计。
- TLS 或基于可信网络边界的局域网传输增强。
- Web 控制台拆为独立前端工程、CSP 和完整多语言。
- MDM/企业分发、远程升级编排和设备策略管理。
- SQLite/Ktor/播放器的 Android instrumentation 与端到端自动化测试。

## 6. 当前已知边界

- 服务监听 `0.0.0.0`，依靠局域网部署边界和 Token 鉴权；不得做公网端口映射。
- 动态私网 IP 无法通过 Android Network Security Config 按固定域名或 CIDR 收窄，所以 Manifest 保留应用级明文 HTTP 例外；构建门禁会持续警告，TLS 升级前不得公网暴露。
- Web Token 与到期时间位于同源 `localStorage`，用于在 8 小时有效期内恢复控制；存储不可用时降级到 `sessionStorage`。控制台仅在临近过期时轮换，服务端有界保留最近 32 个去重后的有效 Token。它仍无法达到 HttpOnly Cookie 的脚本隔离强度。
- Android 12+ 对后台和开机启动的设备厂商限制需要目标硬件逐台确认。
- 当前自动化测试集中在纯业务规则；SQLite、Ktor 路由、播放器和双设备链路仍需增加集成测试。
- 内嵌 Web 管理页已对字符串模板中的业务名称、属性值和事件参数做上下文编码；拆为独立前端时仍应改为 DOM `textContent` 渲染并增加内容安全策略。
- Remote Web 允许 JavaScript 和 DOM Storage 以兼容看板页面，但不提供 JSBridge；页面自身供应链与 CSP 仍由内容提供方负责。
- RTSP 允许局域网或明确配置的可信主机，当前校验协议、Host 和禁 URL 内嵌凭据；部署仍必须阻止设备访问非预期网络。
- Android 12 以下和非图片内容的 Blur 降级为 Scene 背景色；这是性能与兼容策略，不属于实时视频模糊。
- Local HTML 当前通过 JSON API/管理页提交，未开放任意 HTML 文件上传；内容上限 100 KB。

## 7. 下一阶段优先顺序

1. 完成并留证第一阶段真机验收，修复所有阻断问题。
2. 按商业清单完成第二阶段 WebView、HLS/DASH/RTSP、Overlay、Blur、UDP 双真机验收。
3. 增加 SQLite/Ktor 集成测试和 Media3/WebView instrumented smoke test。
4. 在确认部署网络模型后决定 TLS、MDM 和升级编排方案。
