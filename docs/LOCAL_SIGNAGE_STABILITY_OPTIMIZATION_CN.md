# 商业稳定性优化与验证记录

日期：2026-09-18。范围：现有功能的性能、可靠性、安全边界、兼容性和可重复验证；排期、复杂分屏、云管理等新增产品能力不在本轮实现范围。

## 1. 已完成的代码优化

| 领域 | 修改 | 目的 |
|---|---|---|
| 上传 | ResourceUploadWriter 在状态锁外读取流、写临时文件并计算 Hash；提交阶段串行去重与复核配额 | 慢速上传不长期占用播放状态锁 |
| 上传异常 | 输入流明确关闭；异常/超限清理临时文件；取消前登记已提交资源用于回滚 | 减少半成品和孤立资源 |
| 持久化 | CoalescingWriter 后台合并进度快照；内存立即更新；场景切换使旧快照失效 | 避免每秒主线程写库和积压队列 |
| 持久化失败 | 写入失败显示 PLAYBACK_CHECKPOINT_WRITE_FAILED，后续成功写入恢复；相同元数据跳过写盘 | 明确诊断，减少无效写入 |
| 资源查询 | 按 ID/Hash 查询资源、按 ID 查询 Scene | 避免单条读取反复扫描整张表 |
| 资源恢复 | 回收超过 24 小时、由应用生成且未被引用的临时文件与孤立资源文件 | 控制崩溃残留的磁盘占用 |
| 播放生命周期 | 旧视图不会释放新页面的播放器绑定；WebView 明确销毁；过期文字动画回调被拦截 | 避免 Activity 重建后的资源泄漏和相互干扰 |
| 网页播放 | 切换到其他内容时停止隐藏网页，再次播放网页时恢复安全配置 | 避免隐藏网页继续执行和播放声音 |
| 图片背景 | 使用现有 Coil 加载及缩放模糊背景，取消过期请求 | 将图片解码移出主线程 |
| 命令超时 | 撤销尚未执行的超时/中断主线程命令 | 防止已返回失败的排队命令稍后生效 |
| 状态广播 | 后台合并状态事件，发送限时并淘汰失效连接 | 慢浏览器不阻塞播放、不无限积压状态任务 |
| WebSocket 鉴权 | 发送和接收时重新检查 Token，撤销凭据后关闭现有连接 | 凭据撤销对已建立的长连接也生效 |
| 多设备网络 | DeviceHttpTransport 限制响应大小、流式上传、禁止携带凭据跟随重定向、异常时断开连接 | 降低大文件内存占用及连接泄漏风险 |
| 多设备调度 | 控制请求与传输分别限制并发，按 deviceId 去重；检查返回的设备身份 | 防止同步耗尽控制请求线程和身份错配 |
| Android 6.0 | 替换 API 24 的集合/Content-Length API，文件提交使用同目录重命名 | 保持 minSdk 23 的既有支持范围 |
| Media3 | 对实际使用的 UnstableApi 明确 opt-in | 通过模块 Lint，并显式承认 API 兼容责任 |
| 服务错误 | Server 启动失败清理监听与后台任务；Service 记录错误并退出；网络监听失败可诊断 | 避免初始化失败遗留运行资源或静默失败 |
| 签名 | 签名文件与配置停止 Git 跟踪、保留磁盘原件；新增忽略规则与发布扫描 | 继续使用既有本地签名，防止材料再次入库 |

主线程仍有低频播放选择、配置等同步数据库操作，本轮未把所有存储调用改成异步，也未重写整个播放器状态机。已经开始执行的命令无法被超时强制回滚。

## 2. 自动化验证

- Kotlin/JUnit：app 3 项、feature_app 86 项、core_google 4 项，共 93 项，失败/错误/跳过均为 0。
- Python 发布及素材检查回归：12 项通过。
- SQLite 集成：Android API 23 和 28，覆盖 v1→v12 迁移保留内容、并发重复上传、数据库事务失败后的文件回滚、进度恢复及落盘失败恢复。
- 真实 Ktor HTTP/WebSocket：匿名拒绝、配对凭据一次性消费、写入 Session、接管后旧 Session 失效、上传去重、Token 撤销后长连接关闭。
- 播放控制集成：主线程恢复后，已经超时的排队命令不会补执行。
- 上传/网络纯测试：大小边界、Hash、读取中断、临时文件清理、状态锁可用、流式传输、重定向禁止、超大响应和连接释放。
- app / feature_app / core_google 的 Debug Lint：通过；保留非阻断警告，不降低门禁、不删除功能。
- 架构、构建、UI、国际化与 Agent 结构门禁：通过。现存警告包括局域网 HTTP、内部媒体 file URI 和启发式 UI 检查。
- Debug APK：构建通过，资源语言检查通过。
- Release APK：R8 压缩与签名构建通过；APK 签名及 ZIP 对齐检查通过，语言资源检查通过。文件为 app/build/outputs/apk/release/app-release.apk（16,663,618 字节）。
- Release SHA-256：`9b507f023998e316cc7ffb955276da2029f31a52084e58c267f090bc32dc5495`。
- 最终日志：build/commercial-final.log；签名日志：build/apk-signature-verification.log；原生库报告：build/commercial-native-report.json。

Robolectric 仅作为测试依赖，不进入生产 APK。接入方式参考 [官方测试配置](https://robolectric.org/getting-started/)。桌面 Android 测试不能替代厂商硬件、真实 Android Keystore、MediaCodec 和 WebView 渲染验收。

## 3. 可重复执行

```powershell
# 默认：Python 测试、工作流门禁、三个模块测试与 Lint、Debug 和 Release 构建、APK 语言与测试报告检查、签名与 ZIP 对齐检查。
./.agents/scripts/verify_commercial.ps1

# 本地不打正式包时：
./.agents/scripts/verify_commercial.ps1 -SkipRelease
```

Release 继续要求原有本地签名或 CI 签名环境变量及 Play 许可公钥。新检出可参考 keystore.properties.example，在本地创建被忽略的 keystore.properties；不得为覆盖升级随意更换签名。报告为空、测试失败或跳过均不能通过测试报告门禁。构建检查不表示发布或上线，当前代码尚未提交。

## 4. 原生库与 16KB 验证边界

当前传递依赖为 AndroidCoreUtils v1.1.1 → MMKV 1.3.14；Release 还包含 DataStore 的 libdatastore_shared_counter.so。APK 同时包含 armeabi-v7a 和 arm64-v8a。

Release 中两个 arm64 库的 LOAD 对齐均为 16384，APK 的 zipalign -P 16 检查通过；32 位 MMKV 库保持 4096 对齐。arm64 GNU_RELRO 结束地址除以 16384 的余数分别为 MMKV 4096、DataStore 8192。因此不能仅凭 LOAD 和 ZIP 对齐认定全部 16KB 运行兼容性通过，需补 16KB 设备运行及上游库布局核验。

另外核验的官方 1.3.16/1.3.17 AAR 仍存在非 16KB 整齐结束的 RELRO，未据此盲目更换依赖。MMKV 2.x 不再提供 32 位支持，本轮保留产品既有 ABI。参考 [Android 原生库检查要求](https://developer.android.com/guide/practices/page-sizes) 和 [MMKV 官方安装说明](https://github.com/Tencent/MMKV/wiki/android_setup)。

## 5. 尚需实际部署环境验收

- 2026-09-18 已在 OPPO PCAM00（Android 11）完成正式包覆盖升级、R8 后控制接口回归；详见 [真机功能与国际化验收](LOCAL_SIGNAGE_DEVICE_QA_2026_09_18_CN.md)。正式包全新安装仍需在没有历史数据的生产设备补测。
- Android 12–16 及目标电视盒子的全屏、遥控器、后台限制和开机恢复。Android 15+ 普通设备仍按原有策略等待人工打开，不虚构自动恢复能力。
- 双设备配对、网络切换、弱网同步、Gateway 离线、大文件并发、磁盘不足和强制断电。
- 至少 24 小时混合内容播放、硬件解码、真实 WebView 与 16KB 页面设备运行。
- Google Play 真实购买、续费、退款、恢复购买、离线宽限及跨版本权益恢复。
- Git 历史材料处置：停止跟踪不会清除历史。需按仓库传播范围、证书用途和已发布应用状态评估；本轮未重写历史或替换应用签名。
- LAN HTTP 与 CSP 内联脚本例外仍为已知部署边界；独立前端、TLS、排期及复杂分屏属于后续独立工作。
