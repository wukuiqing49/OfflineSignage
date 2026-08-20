# Local Signage 商业发布验收清单

> 适用范围：第一、第二阶段商业试运行，支付除外。
>
> 状态只能填写 `PASS`、`FAIL`、`BLOCKED`、`N/A`。没有证据的项目不得填写 `PASS`。

## 1. 发布样本

每轮验收先记录：

```text
版本名称：
versionCode：
Git commit：
APK 文件：
APK SHA-256：
签名证书 SHA-256：
构建时间：
测试负责人：
```

APK Hash：

```powershell
Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256
```

签名检查：

```powershell
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## 2. 设备矩阵

至少覆盖下表。Android 16 可以先使用官方设备/模拟器验证平台行为，但电视盒子结论必须来自真机。

| 类别 | Android | 设备/芯片 | 屏幕 | 网络 | 结果 | 证据/问题 |
|---|---:|---|---|---|---|---|
| 手机 | 12 |  | 竖屏/横屏 | Wi-Fi |  |  |
| 平板 | 13 |  | 横屏 | Wi-Fi |  |  |
| Android TV | 14 |  | 1080p/4K | 有线/Wi-Fi |  |  |
| 电视盒子 | 15 |  | 1080p/4K | 有线/Wi-Fi |  |  |
| 平台前瞻 | 16 |  | 横屏 | Wi-Fi |  |  |

每台设备检查：全新安装、权限/通知、首次启动、沉浸全屏、无资源首屏、二维码、上传后自动播放、暂停/继续状态、无进度和多余控制、Activity 重建、Service 存活，以及符合该设备系统能力的开机恢复路径。

- [ ] 新安装首次人工启动显示欢迎页，中文/英文、小屏和横屏下内容完整且按钮可达。
- [ ] 完成欢迎页后再次从桌面启动直接进入播放页；已有内容覆盖升级不会重新阻塞在欢迎页。
- [ ] Android 14 及以下或具备专用自启动能力的设备开机后直接恢复；Android 15 及以上普通设备首次人工打开后直接恢复且不经过欢迎页。

## 3. 首次连接与安全

- [ ] 未配对浏览器打开 `/` 不获得 Web Token，数据 API 返回 `401`。
- [ ] Android 无资源首屏显示正确设备名、局域网 IPv4、Ready/网络状态和二维码。
- [ ] 扫码后 URL 中 pairingToken 被地址栏清理，控制台可读取状态。
- [ ] 同一个 pairingToken 第二次使用返回 `401`。
- [ ] pairingToken 超过 5 分钟、主动轮换或撤销后失效。
- [ ] Web Token 超过 8 小时或撤销后失效并要求重新扫码；有效期内关闭后重新打开浏览器可恢复授权。
- [ ] 第二浏览器可查看；无有效 Control Session 时不能执行写操作。
- [ ] Takeover 后旧 Session 失效；Heartbeat 停止 60 秒后释放。
- [ ] 相同 commandId 同请求返回首次结果；相同 ID 不同内容被拒绝。
- [ ] 旧 revision 被拒绝，新 revision 被接受，重启后仍保持单调。
- [ ] 诊断导出、API、日志均不包含 Web/Pairing/设备 Token 和资源绝对路径。
- [ ] 路由器无公网端口映射，外网无法访问 8080。
- [ ] Control Session 状态和冲突响应不包含其他浏览器的 `sessionId`。
- [ ] 页面响应包含 `no-store`、CSP、`no-referrer`、`nosniff` 和禁止 iframe 嵌入策略。

## 3.1 手机 Web 操作台

- [ ] Android Chrome、Samsung Internet 和 iOS Safari 完成扫码、刷新、前后台切换和重新打开测试。
- [ ] 320/360/390/430 px 竖屏和手机横屏下，顶部状态、底部导航、按钮、表单和 Dialog 无重叠或横向溢出。
- [ ] 当前设备、发送内容和播放控制三个主入口可到达；更多设置可从当前设备进入；只读状态仍可导航和查看允许的数据。
- [ ] 第二浏览器显示匿名 Owner 与过期时间；Take control 后旧浏览器写操作立即失败。
- [ ] 正常离开页面后 Session 及时释放；浏览器强杀后 60 秒内释放。
- [ ] 手机选择照片/视频并上传时显示进度；成功后资源出现并播放，中断时显示可恢复错误。
- [ ] 删除 Resource、Scene、Playlist、设备、错误和撤销 Access 前均显示二次确认。
- [ ] 页面进入后台后不持续高频轮询，回到前台时状态和当前工作区自动恢复。
- [ ] 200% 浏览器文字缩放、长中英文名称、超长错误码和无数据状态无不可接受溢出。

## 4. Resource、Scene、Playlist

- [ ] 上传 JPG、PNG、WebP、MP4 等目标格式并核对 MIME、大小和 SHA-256 去重。
- [ ] 超过单文件限制返回 `413`，超过总配额给出明确错误。
- [ ] 非图片/视频返回 `415`，文件名包含 HTML/引号时页面不执行脚本。
- [ ] HTTPS 远程图片/视频缓存成功，断网后继续本地播放。
- [ ] HTTP、私网目标、用户信息 URL、非 443 端口、错误 MIME 和超大响应被拒绝。
- [ ] Scene 的 FIT/FILL/CROP/STRETCH/CENTER、背景色、音量和静音生效。
- [ ] Playlist 顺序、Loop、禁用项、Next/Previous、删除引用约束符合预期。
- [ ] 删除当前资源/Scene/Playlist 后状态可恢复，不崩溃、不保留悬空引用。

## 5. 双设备与网络

使用 A 为 Gateway、B 为目标设备：

- [ ] 同一局域网自动 NSD 发现，设备名、IP、端口正确。
- [ ] 配对后重启 A，B 的凭据仍可用；诊断不暴露设备 Token。
- [ ] Resource 按 Hash 同步，重复同步跳过，Scene/Playlist 引用完整。
- [ ] A 同时控制 A/B 播放、暂停、音量、静音，各设备结果独立返回。
- [ ] 关闭 B 后 A 仍能返回其他设备状态，B 标记 OFFLINE/TIMEOUT。
- [ ] 恢复 B 后状态自动恢复。
- [ ] 关闭 Gateway A 后，B 继续播放已同步本地内容。
- [ ] DHCP 地址变化后重新发现，不使用旧 IP 误控其他设备。
- [ ] Wi-Fi 断开、恢复和有线/Wi-Fi 切换后 Server、NSD、二维码地址恢复。
- [ ] DHCP 地址变化后旧二维码地址失效，新二维码、NSD 和 UDP 使用新地址，Ktor 控制台可重新访问。
- [ ] 禁用或阻断 mDNS 后，UDP fallback 在 60 秒内发现同网段设备；报文抓包不含 Token、内容或隐私数据。
- [ ] UDP 设备离线超过 60 秒后从发现列表移除，NSD 仍在线的同一设备不被误删。

## 5.1 第二阶段内容类型

- [ ] Local HTML 在无外网时显示；Remote Web 仅接受 HTTPS，HTTP/file/content/intent/javascript 导航被拒绝。
- [ ] Web 页面主加载超时、DNS 失败、TLS 失败和离线按 Retry/Skip/Fallback 处理，Service 不崩溃。
- [ ] WebView 无 JSBridge，文件/内容访问、mixed content、地理位置和调试均关闭；Activity 重建无泄漏或残留页面。
- [ ] HLS、DASH、RTSP 各使用目标客户实际服务连续播放；鉴权、重定向、分片切换和直播窗口符合预期。
- [ ] 流断开后按 1/2/4/8/30 秒退避重试，恢复后继续；持续失败后进入下一 Scene 或 fallback。
- [ ] Text 主内容在横竖屏、1080p/4K、系统字体 100%-200% 下无不可接受的截断或溢出。
- [ ] TEXT/TICKER Overlay 的位置、层级、颜色、字号、背景和速度正确，切 Scene 后旧动画和 View 完全移除。
- [ ] 暂停时视频、Web、Scene timer 和 Ticker 同时暂停；继续后从原状态恢复，无跳 Scene 或重复计时。
- [ ] Android 12+ 本地图片 Blur 正常；Android 6-11、视频、流和 Web 按设计降级为背景色，无持续高 CPU/GPU。
- [ ] 本地与虚拟资源混合 Playlist 的 Next/Previous、Loop、单 Scene 播放、同步和设备重启恢复正确。

## 6. 断电、重启与故障

- [ ] 播放中强制断电，重启后 Server 与 Playlist 按配置恢复。
- [ ] Android 14 及以下或专用设备自启动环境在 `BOOT_COMPLETED` 后恢复；Android 15 及以上普通设备不触发受禁止的媒体前台服务启动，并在用户首次打开后恢复。
- [ ] 强杀 Activity 不停止 Server/播放器；重建后画面和状态正确。
- [ ] 损坏视频按 Retry/Skip/Fallback 处理，Service 不崩溃。
- [ ] 删除或截断正在播放文件后错误可诊断并继续策略生效。
- [ ] 磁盘接近满和配额已满时拒绝写入，不破坏已有文件。
- [ ] 上传和远程下载中断不留下可播放的半文件，临时文件最终清理。
- [ ] 快速连续命令不会倒退 revision、重复执行或造成播放器崩溃。

## 7. 24 小时稳定性

Playlist 至少包含短视频、长视频、横图、竖图和一个故障资源。开始和结束均导出诊断并记录：

- [ ] 连续播放 24 小时无崩溃、ANR、黑屏常驻或 Service 消失。
- [ ] 循环边界、视频结束、图片定时和暂停/继续正确。
- [ ] 内存没有持续增长趋势；设备温度、存储和电量/供电稳定。
- [ ] Web 控制台周期性连接不会产生无限 Session、WebSocket 或线程增长。
- [ ] 测试期间至少执行一次断网恢复和一次 Gateway 离线。

建议每小时记录：时间、当前 Scene/Playlist、PSS、CPU、温度、网络、错误数和截图。

## 8. Release 与升级

- [ ] `assembleRelease` 使用预期正式/测试共用签名，证书指纹已登记。
- [ ] keystore、密码、Token、证书和 `keystore.properties` 未进入 Git。
- [ ] Release APK 在未安装设备上全新安装成功。
- [ ] 从上一商业版本覆盖升级成功，Resource/Scene/Playlist、设置和设备 ID 保留。
- [ ] 不允许使用不同签名覆盖；确认失败信息符合 Android 签名机制。
- [ ] R8 后 Ktor HTTP、WebSocket、Media3、JSON、二维码、NSD 和诊断均通过。
- [ ] Android 12-16 前台服务、通知、开机广播和后台启动限制均通过目标设备验证。
- [ ] 4K 设备无 UI 溢出、非预期裁剪、状态遮挡或系统栏残留。

升级操作原则：先导出诊断并记录版本，确认 APK 签名一致，再覆盖安装；升级失败不得清除应用数据作为默认处理。涉及数据库 schema 的版本必须先验证从上一商业版本逐级升级。

## 9. 自动门禁

按顺序执行：

```powershell
./gradlew.bat :feature:feature_app:testDebugUnitTest --no-parallel
./gradlew.bat :app:assembleDebug --no-parallel
./gradlew.bat :app:assembleRelease --no-parallel
python -X utf8 .agents/scripts/validate_android_workflows.py --project-root . --skip-figma
python -X utf8 .agents/skills/android-build-workflow/scripts/validate_build_output.py --project-root .
python -X utf8 .agents/skills/android-project-architecture-workflow/scripts/validate_architecture.py --project-root .
python -X utf8 .agents/skills/android-ui-workflow/scripts/validate_ui_output.py --module-src app/src/main/java --module-src feature/feature_app/src/main/java --module-res app/src/main/res --module-res feature/feature_app/src/main/res --module-res feature/feature_res/src/main/res
python -X utf8 .agents/skills/android-i18n-workflow/scripts/validate_i18n_resources.py --res-dir app/src/main/res --res-dir feature/feature_res/src/main/res
git diff --check
```

## 10. 发布判定

以下任一情况必须阻断发布：签名不符、鉴权绕过、崩溃/ANR、目标设备无法按其系统支持的恢复路径恢复、已有资源损坏、24 小时测试失败、目标电视盒子无法全屏稳定播放、Token/路径出现在诊断导出、外网可直接访问控制端口。

所有阻断项关闭、自动门禁通过、设备矩阵和 24 小时测试有证据后，第一阶段才能从“代码完成”变为“商业发布验收通过”。
