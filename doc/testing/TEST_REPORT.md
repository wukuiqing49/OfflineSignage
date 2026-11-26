## 提交前证据复核补充（2026-11-26）

复核home-restore-1.5.2的自动冷启动截图发现仍有“System UI is not responding”系统弹窗。素材/播放状态与底层视频变化的断言成立，但不能据此认定模拟器开机后的系统界面稳定、无遮挡或无人值守整机验收通过。保留原始截图证据，并在该轮摘要明确标记系统UI响应性未通过。实体广告机断电与长期稳定性仍待验收。

# 1.5.3 完整正式AAB打包（2026-11-26）

当前全部已实现功能打入Release AAB，版本1.5.3 / 1005003；Pro入口和功能保留既有授权规则，DevHub正式版开关开启。bundleRelease成功，bundletool API导出实际包Manifest确认包名/版本/非debuggable及SignageHome入口；jarsigner验证通过，ZIP完整性、网页资源存在、仅ARM32/ARM64 ABI、两个ARM64库全部LOAD段至少16KB对齐通过。大小约21.2MiB。产物[LocalSignage-1.5.3-1005003.aab](../../app/build/outputs/bundle/release/LocalSignage-1.5.3-1005003.aab)，[SHA256与检查结果](evidence/aab-1.5.3/result.json)。

本轮仅打包及产物检查；完整功能、实际冷启动恢复与真机测试沿用下方1.5.2已记录结果，不冒充1.5.3重新全测。没有上架、提交或push。Manifest辅助检查脚本先前调用错误已修正，实际bundleRelease构建和最终产物验证均成功。

---

# 可选展示启动器与自动恢复优化（2026-11-26）

已在现有启动路径增加用户主动选择的默认主屏幕入口，并在帮助文章提供说明、系统角色选择和后台设置。使用Activity Result调用系统角色选择；已为主屏幕时进入系统Home设置，可切回原桌面。新增4条文案覆盖默认及8种语言，保留蓝色主题。去掉Main每次权益刷新时重复加载相同升级背景的调用，背景仍来自XML。未降低targetSdk、未新增悬浮窗权限、未从开机广播违规启动媒体服务，不自动更改用户主屏幕。

本次通过：模拟器真实系统角色选择、说明取消不改桌面、Home键进入播放器；隔离QEMU强制终止、无快照冷启动后系统自动进入应用并恢复原视频，进度与画面变化，无手动am start；从同一入口打开Home设置并在系统UI选回Pixel Launcher。模拟器素材/设置与原桌面已还原。Pixel 8正式包覆盖安装，说明及取消通过，PackageManager安装收敛后原主屏幕组件与安装前一致，未将Pixel设为本应用Home。正式包22项配对/上传/播放/轮播/清理回归通过；129项App/feature单测0失败，Debug/Release APK/AAB、签名与16KB ZIP对齐、UI/构建/严格8语言门禁通过。既有门禁警告保留，不等于所有native及设备兼容通过。

前期模拟器启动记录有一次应用ANR，主线程在重复背景资源解析；删去冗余调用后冷启动自动恢复与画面测试通过，不保证所有ANR已解决。测试滑动曾误触系统Home手势区；现已修正。系统Role请求曾因普通startActivity无调用者返回关系立即取消，已修正并用真实系统选择界面重测通过。安装后短暂ResolverActivity解析不作为最终桌面改变，已等系统收敛后复核原组件。

[实际说明弹窗](evidence/home-restore-1.5.2/pixel-options.png) · [冷启动及系统选择证据](evidence/home-restore-1.5.2/summary.json) · [启用与退出说明](../../docs/LOCAL_SIGNAGE_STARTUP_RECOVERY_CN.md)。最终1.5.2正式包已安装Pixel 8，保留数据和统计配置，未提交/push。

适用条件：用户在专用设备选择默认Home、解锁后存储可访问、开启启动恢复且上次处于播放中、系统允许Home选择。实体广告机真正断电、密码锁无人值守、其他厂商及受管理设备仍未验证；未启用此模式的普通Android15+应用仍需前台入口，不能宣称无条件开机自启。

---

# 1.5.2 双端、视频直播、定时与恢复补测（2026-11-26）

本轮是用户对上一轮未验证项目的补测。最终18项断言通过、0项最终失败，另1项系统开机行为观察；不是全部平台/协议/无人值守条件均已通过。[结果、环境及限制](evidence/extended-1.5.2/summary.json)。未修改生产业务逻辑。

| 项目 | 本次结果 | 实际证据与边界 |
|---|---|---|
| 视频 | 通过 | Pixel 8正式包上传本地H.264 MP4，播放进度推进、间隔截图像素变化；导入恢复的视频再次验证 |
| 直播 | RTSP通过 | 本机持续实时发布H.264，经RTSP/TCP给Pixel 8，播放进度与实际画面变化。HTTP源按现有协议限制被拒绝；未降低限制，HLS/DASH未执行 |
| 定时任务 | 通过 | 创建下一分钟开始的播放列表任务，不修改系统时间，等待实际分钟边界后自动切换到目标列表/文字素材 |
| 导出/导入 | 通过 | 导出检查，删除仅本轮素材/场景/测试列表，再导入恢复2个素材、2个场景及导出的2个列表（含空Default）；视频真实播放，原有Default保留 |
| 双设备同步 | Pixel 8→模拟器通过 | 两个真实Android实例，经电脑私网TCP桥接及应用UDP发现，真实配对、视频字节传输/哈希一致、重复同步无重复素材、列表/场景/资源映射及远端视频画面变化通过。不是双实体机或逐帧同步证明 |
| 进程恢复 | 通过 | Pixel 8强制停止应用后手动打开，恢复同一素材和播放状态 |
| 异常掉电恢复 | 模拟器有条件通过 | 强制终止仅本轮隔离QEMU进程，禁用快照冷启动；素材及播放记录保留，打开App后视频恢复且画面变化。未给Pixel 8断电 |
| 开机自动播放 | 观察：未自动播放 | Android36开机后未自动播放；符合现有Android15+延迟到用户打开App的实现，不能当作断电后无人值守自启通过 |

全部临时素材、场景、导入列表、定时任务及配对已清理，设置还原，未清库/卸载，未访问PCAM00。临时直播工具仅监听本机，双端桥接仅绑定私网接口且测试后关闭；凭据仅在内存，原始可能含配对信息的日志/截图留build/extended-1.5.2，公开图片仅截取画面中央测试图。

测试脚本前期对HTTP流、Default列表数及模拟器首次引导的假设修正后重跑通过，不把脚本中断记成应用缺陷。隔离模拟器System UI ANR明确记录并关闭系统弹窗；没有应用崩溃证据。素材、定时、导入和直播使用原正式APK；模拟器额外x86_64仅通过本地Gradle测试覆盖配置生成，不修改公开构建配置。

尚未验证：实体机真正断电/开机无人值守恢复、双实体设备、HLS/DASH和音频输出、长时间稳定性及直播断网重连。真实支付及DevHub后台收数仍未执行。

---

# 1.5.2 全功能回归（2026-11-26）

范围为当前未提交工作区，版本1.5.2 / 1005002。结果与产物SHA见[本轮摘要](evidence/full-1.5.2/summary.json)。测试只针对Pixel 8/API36及本机Chromium，不代表全部功能、平台和发布条件均已通过。

本次通过：129项App/feature JVM测试（core_google 5项复用Gradle有效缓存，不计本次执行）；9项Node发布流程测试；R8审计工具5项及国际化工具12项测试。Debug APK/测试APK/Release APK/AAB构建通过，正式APK签名与zipalign 16KB打包对齐检查通过，不将ZIP对齐等同所有native运行兼容。App Lint 0错误/47警告；构建/UI/8语言/HTML一致性检查通过，保留现有提示。

本次通过：原生中英横屏68状态、套餐完整/部分/不可用12状态、欢迎及竖屏大字体32状态，共112状态，无文字省略或垂直裁切。字体、夜间、方向和息屏超时恢复。系统锁竖屏时启动/连接/帮助/支付/返回/网络弹窗7次观察仍横屏。热点真实启动、凭据显示、关闭通过。一次方向测试读取与其他设备操作争用后中断，串行重跑通过。

本次通过：网页5页×中英×明暗×3尺寸60布局无横向溢出；模拟发布部分失败、只重试失败设备、避免重复创建、目标丢失不回退本机。Debug真实配对/拒绝无授权读取/13个只读接口/导出ZIP/图片上传/文字与HTML播放/暂停恢复/三素材轮播/清理22项通过；Release同组API检查22项通过。仅清理本轮新增素材，原有用户数据未清除。

正式包已保留数据覆盖安装Pixel 8；冷启动、完成欢迎引导、进入支付、说明展开、返回连接通过。首次导航脚本错误假定直接到Main而停在Welcome，调整测试前置步骤后通过，无应用启动崩溃结论。设备最后状态以本轮API清理后的应用为准。

未执行：真实收费/续费/退款、Play生产价格与DevHub后台收数、真实双设备同步、视频/直播解码、定时写入触发、备份导入恢复、断电恢复、其他设备系统；以上不能算通过。没有自动修改业务代码、提交或push。

---

# HTML深色背景与可读性修正（2026-11-26）

用户截图标出的发送目标白色容器、本地/网络来源浅色底、上传区浅色底来自遗漏的硬编码颜色。实际console.css统一改用surface、surface-subtle、sidebar等明暗主题变量；选中来源增加蓝色底与下划线，输入提示改用可读的次级文字色。同步修正资源页Tab、记录表头、文件摘要、场景布局选项及计划状态的相同背景问题，不更改素材画面自身的颜色。

真实打包HTML与电脑示例预览已重新生成。5页面×2主题×3窗口共30布局检查无横向溢出；用户标出区域及占位提示共7项计算对比度6.17–14.58:1；本地/网络切换通过，实际深色截图复核通过。Debug构建及HTML一致性通过。本轮仅Web视觉修正，未重复Android业务测试或安装Pixel 8，不宣称真机/真实上传/全部弹窗已覆盖。见[修正画面](evidence/console-dark-20261126/send-dark.png)及[结果](evidence/console-dark-20261126/summary.json)。

---

# 支付与其他页面紧凑化（2026-11-26）

本轮将真实支付页重排为皇冠PRO与当前授权、首屏权益、对齐的月/年/永久三档套餐、集中工具操作区。年度保留推荐态，缺货显示次级提示且禁止购买；真实价格仍只来自Google Play。帮助目录去掉重复说明和层层卡片，10个入口在普通字体横屏一屏展示；法律中心改为文档/统计偏好双栏，窄屏自动堆叠；文章阅读限制720dp行宽。欢迎页收紧边距、步骤和标题。保留所有原有入口、返回和隐私开关行为。

HTML五主页面与弹窗统一紧凑间距，手机横屏增加96px侧边导航与播放双栏；触控目标保留48px，主色保持蓝色。试用/免费版有皇冠升级入口，Pro不重复促销；支持暗色与长文案自然换行。

- Pixel 8最终原生中文1.0深色/英文1.3浅色：28页面状态，无文字省略或高度裁切。放大字体允许堆叠滚动，不强制一屏压缩。套餐完整/部分/不可用的测试注入状态12个通过，购买按钮可用性正确，无真实购买。冷启动、连接页进入支付、展开说明、返回连接再进支付通过。
- 改造中中文全文章/法律34状态通过；英文全巡检曾因另一个应用抢占前台中断，最终定向28状态重新通过。未将中断结果计为通过。
- 浏览器5页×3尺寸×中/英/德共45布局检查无横向溢出；试用/免费/Pro入口显示及弹窗通过；发布部分失败后仅重试失败设备，内容只创建一次，失效目标不回退本机。使用虚拟API，不代表真实双设备链路。
- 本轮129项Android单测、9项Node测试通过；最后的支付视觉微调后补跑构建、设备巡检与状态检查。feature_app Lint 0错误/16警告；UI、严格8语言与HTML生成一致性检查通过，保留现有ViewPager2/空app资源提示。

[Pixel 8支付页](evidence/all-pages-20261126/payment-px8.png) · [帮助目录](evidence/all-pages-20261126/help-px8.png) · [法律中心](evidence/all-pages-20261126/legal-px8.png) · [本轮摘要与产物指纹](evidence/all-pages-20261126/summary.json)。原始含配对信息的截图和日志仅存build/all-pages-20261126。最终Debug保留数据覆盖安装，停在支付页；字体、夜间和息屏超时均恢复。

未验证：真实购买与生产价格（当前设备Google Play目录不可用）、本轮Release、其他厂商/系统及欢迎页本轮实机画面。没有清库、变更统计同意或发起支付。

---

# Pro入口收窄与品牌标识（2026-11-26）

按最新反馈将底部全宽横幅改为居中紧凑入口，最大420dp。Pixel 8实测宽1102px，占屏宽45.9%，保留16dp底部边距和真实授权状态；新增金色皇冠矢量图标与不翻译的PRO品牌字样。白色按钮仍直达真实授权页，点击及返回通过，无购买操作。

Debug与测试APK构建、App Lint（0错误）、严格8语言校验和git diff --check通过。中文1.0深色/英文1.3浅色横屏共4个连接页状态通过，无文字裁切；原字体/夜间设置已恢复。当前版本已安装Pixel 8并停在连接页。只是视觉调整，未重复业务单测或Release验证。

[实机截图（配对信息已遮挡）](evidence/reference-hotspot-20261126/connection-pro-compact-redacted.png) · [尺寸、皇冠及跳转断言](evidence/reference-hotspot-20261126/pro-compact-result.json) · [字体检查](evidence/reference-hotspot-20261126/pro-entry-font-audit.json)。本节取代下方历史“全宽升级栏”的设计结论。

---

# 连接首页按参考图落地、热点与边距修正（2026-11-26）

按用户提供的图片重构实际Android连接首页，保留品牌蓝色。截图外的黑色区域不属于App，最终页面为全屏深蓝，无模拟窗口边框。单一16dp页面内边距；页头靠上、升级栏靠下；刘海仅由实际相交的行避让，避免对称安全区、居中容器margin和padding累加。Pixel 8最终页头bounds为[42,42,2358,168]，底部升级栏为[42,848,2358,1038]，屏幕2400×1080。见[最终实机图（配对信息遮挡）](evidence/reference-hotspot-20261126/connection-final-redacted.png)与[摘要](evidence/reference-hotspot-20261126/summary.json)。

- 连接：二维码左置、标题和说明右置、地址与配对码并列、网络状态旁提供网络设置。底部整条蓝色权益栏配白色专业版入口；当前授权状态仍来自真实权益。企业支持入口移到帮助页，功能保留。
- 热点：IDLE/STARTING/ACTIVE/FAILED状态、防重复请求、迟到回调失效和reservation释放。回调不持有Activity；按生命周期订阅状态。启动、名称/密码、失败原因均在本页显示。权限拒绝/失败不再自动跳系统设置；系统设置、定位或应用权限只在用户主动选择后打开。使用[Android本地热点API](https://developer.android.com/develop/connectivity/wifi/localonlyhotspot)，不共享公网。
- 方向：启动窗口预设横屏，后续页继承前页；手机显式横/竖方向改为固定方向，保留AUTO与大屏/多窗口系统策略。Pixel 8当前LANDSCAPE设置下，系统锁竖屏时的启动窗口、连接、帮助、授权、返回与网络弹窗7次观察均2400×1080。
- 本次通过：Pixel 8真实热点启动、显示凭据、关闭；临时模拟永久权限拒绝时原位反馈且不跳设置；恢复授权后重试启动/关闭成功。权限及临时标记均还原，最终热点关闭。未使用第二台设备连接热点，不声称双机链路已验证。
- 本次通过：重排阶段中文标准字体/深色与英文1.3字体/浅色共28页面状态；最终边距修正后定向复测连接页4状态，0 ellipsis/verticalOverflow。早期英文配对码标签裁切已通过列宽随字体调整修正；一次系统面板遮挡重跑后通过。
- 本次通过：本轮app3项及feature_app126项单测、严格8语言资源校验、Debug/测试APK与受影响Lint、UI门禁。最终边距仅补测设备页面和构建/Lint，不重复无关业务单测。保留方向锁定与滚动容器等Lint提示，无error/fatal。本轮未重新验证Release、真实购买、旧Android热点与其它厂商设备。

原始日志/敏感截图留在忽略目录build/px8-direction-fix。最终Debug已安装Pixel 8并停在连接首页；字体1.0、夜间yes、系统方向/息屏超时、附近设备权限均恢复。未清库、上传用户素材或发起支付。没有提交或push。

---

# Pixel 8 安装与冷启动复核（2026-11-26）

本次通过：当前Debug应用完整重编译后，以保留数据的完整APK覆盖安装Pixel 8，冷启动由Splash进入MainActivity，连续3次确认进程和前台主页面正常。证据见[启动记录](evidence/px8-launch-20261126/result.json)。没有修改业务源码、清库或购买。

第一次复用增量产物时实际启动失败：SplashActivity仍调用FrameLayout返回类型的ActivitySplashBinding.getRoot，而新布局根节点已经是LinearLayout，触发NoSuchMethodError。清理app模块生成缓存并完整重编译后解决，采用非增量APK安装。此前页面巡检直接进入目标页面，没有覆盖这个Debug启动入口；不能把这些巡检结果当作Debug冷启动通过。上轮恢复Debug后的前台退出也需结合此问题理解，不能仅归因于桌面切换。

复核命令：gradlew.bat :app:clean :app:assembleDebug :app:assembleDebugAndroidTest；adb -s <Pixel8> install --no-incremental -r <Debug APK>；force-stop后am start -W启动Splash，再检查进程与ResumedActivity。app:clean清除了旧app构建产物，本次重建Debug与测试APK，未重新打包Release。

---

# Lint 与 R8 优化结果（2026-11-26）

本轮将三模块 Lint 从 **113 条降到44条（减少69条，约61%）**，0 error/fatal；没有新增生产代码Lint屏蔽、关闭检查或生成告警baseline。当前工作区包含此前页面改动，本轮未提交或push。版本仍为1.5.1 / 1005001，源码指纹与产物见[本轮摘要](evidence/warnings-r8-20261126/summary.json)。

| 模块 | 优化前 | 优化后 |
| --- | ---: | ---: |
| app | 43 | 36 |
| feature_app | 43 | 5 |
| core_google | 27 | 3 |
| 合计 | 113 | 44 |

已实施：KTX等价替换保留SharedPreferences异步编辑边界；复数按中日韩实际数量类别精简；删除已无调用的旧试用文案；修正西葡文拼写；二维码512px采样量改整数资源；兼容层设置无障碍标题；矢量默认缓存尺寸缩小且显示比例保持；图片列表使用同步DiffUtil差分通知；连接头与授权入口移除嵌套权重；合并启动/授权冗余容器；普通窗口取消底层重复背景；圆形图标接入已有圆形资源；移除minSdk已覆盖的旧版本分支，明确共享模块RTL与默认英文资源。

## 剩余44条的保留原因

- **32条SDK/依赖升级提示**：OldTargetApi 1、AndroidGradlePluginVersion 3、GradleDependency 19、NewerVersionAvailable 9。本轮保留compile/target 36及现有依赖，未为清告警执行整套SDK/框架迁移。
- **Overdraw 8条**：页面与共享Fragment保留自身背景。普通应用主题已取消窗口底层背景；库Lint仍按推断主题报告。Fragment还需适用于其它宿主，不能直接删除背景；未屏蔽这些提示。
- **StaticFieldLeak 2条**：播放视图由attach/detach管理，MainActivity.onDestroy调用detach并清空视图/监听器，相关detach幂等测试通过；DevHub初始化只传Application Context。保留静态审查提示，不据此声称完成长期内存压力测试。
- **CustomSplashScreen 1条**：保留用户已有自定义启动页及存储初始化失败重试流程；本轮合并容器与重复背景，不改写启动产品流程。
- **UnusedResources 1条**：Welcome布局由基础框架泛型ViewBinding加载；正式包欢迎页实测正常，未删除资源。

逐条位置见[剩余告警](evidence/warnings-r8-20261126/lint-remaining.json)。构建门禁既有4条网络/系统栏提示与UI门禁1条非Tab ViewPager提示单独保留，不计入上述Lint总数。

## R8配置及检查能力

- 删除GoogleKitConfig、GoogleReward的整类/字段硬保留：源码为普通直接调用，无对应反射/序列化入口。
- 两个Fragment参数配置仍使用Java Serializable，保留类名与成员以维持默认serialVersionUID，增加allowshrinking让未使用类可整体裁剪。保留Gson登录声明模型的注解字段与默认构造函数保护。
- 保持Release minifyEnabled、shrinkResources和完整混淆，按[Android官方AGP8.12/8.13说明](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)开启android.r8.optimizedResourceShrinking。AGP8.13构建仍将此开关标记为experimental；该提示未隐藏。
- 本仓库R8 Skill增加正式合并configuration.txt审计、精确类全成员硬保留提示、部分allow修饰符识别；避免误报注解/继承/特定成员限定及完全软保留规则。5项脚本回归通过。
- 源码规则审计CRITICAL/HIGH/INFO均为0；**最终合并配置为CRITICAL 0、HIGH 3、INFO 9**。HIGH来自AndroidCoreUtils的MMKV整包规则，以及Billing SDK的两个AIDL包规则；INFO主要为上游基础库门面和三方精确类保护。保留其JNI/AIDL兼容性，未修改依赖缓存或假装可由宿主软规则抵消。后续需要上游按真实入口发布更窄consumer rules。

| 正式产物 | 本轮优化前 | 优化后 | 减少 |
| --- | ---: | ---: | ---: |
| APK | 16,716,172 B | 16,281,502 B | 434,670 B（约425 KiB） |
| AAB | 22,868,666 B | 22,218,813 B | 649,853 B（约635 KiB） |
| 未压缩DEX | 10,153,920 B | 9,831,808 B | 322,112 B（约315 KiB，3.17%） |

比较包含本轮代码、资源和规则的合并效果，不把差异单独归因于某一条keep规则，也不等同于Play Console三项优化百分比。mapping.txt生成，AAB签名验证通过（自签名链/时间戳提示保留），APK ZIP 16KB对齐检查通过。两个arm64库ELF LOAD均16KB对齐；armv7 MMKV仍为4KB，本轮未改native依赖，未做16KB系统运行测试。

## 本次验证

- **通过**：全模块现有134项JVM测试（app3/core_google5/feature_app126，0失败/错误/跳过）。最终资源/布局变更后app及feature测试再次执行；core用例同轮已执行，后续为UP-TO-DATE，不重复计数。
- **通过**：R8审计器5项测试；严格国际化覆盖app/feature_app/core_google/feature_res及8种语言；Debug、测试APK、Release APK/AAB与三模块Lint；构建/UI门禁及git diff --check。
- **通过**：Pixel 8 / Android36，Debug中文1.0深色横屏14状态、英文1.3浅色竖屏14状态，无ellipsis/verticalOverflow；人工复核授权页截图。
- **通过**：同一签名的R8 Release覆盖安装保留数据，22项真实接口与媒体检查，包括配对、权限拒绝、备份ZIP、图片/文字/HTML播放、暂停恢复、三素材轮播与清理。
- **通过**：独立平台API测试入口ReleaseUiSmoke在Release包检查欢迎、连接、授权、帮助、法律中心5页和套餐说明展开/收起，INSTRUMENTATION_CODE=-1。测试APK通过signageUiAuditRunner属性选择入口，不静态链接已被R8改名的业务类型；主应用不包含该测试类。
- **受阻**：原生触控滚动脚本已进入授权页，随后可见窗口变为桌面，未验证连续触控滚动到说明/法律入口；不把此结果作为产品崩溃。程序点击展开逻辑的通过不替代触控可达性。
- **未执行**：真实收费/恢复交易、生产统计后台收数、双真机同步、视频硬件解码、24小时连续播放及平台优化百分比。商品仍显示不可用。没有运行这些场景的结论。

## 执行记录与恢复

第一次构建因等价URI替换漏导入类型而失败，修正后通过；布局Lint抓到漏补的箭头约束，修复后0错误。第一轮设备巡检失焦后重跑通过。新Release巡检的法律中心根ID误写导致测试断言使主线程退出，已修正ID并把断言回传测试线程以确保teardown；最终5页通过。这是测试脚本问题，不是已证明的Release业务崩溃。

正式包媒体测试确认空资源库后才创建数据，均已清理。结束后恢复Debug与默认DeviceUiAudit测试APK、字体1.0、夜间模式yes、已完成引导标记；一次测试断言中断后曾未恢复引导标记，最终已核实恢复。未清库、卸载、重启设备或发起购买。含配对信息的截图和原始日志只保留在忽略目录build/warning-optimization，分享证据只有脱敏摘要。

主构建日志gradle-final-retry.log为BUILD SUCCESSFUL（3m6s）；追加final-check.log确认新测试入口配置不改变正式产物，所有Release产物任务UP-TO-DATE。运行入口：gradlew.bat :app:assembleDebugAndroidTest '-PsignageUiAuditRunner=com.wkq.localsignage.ReleaseUiSmoke'，安装同签名Release及测试APK后执行am instrument -w -e welcome true com.wkq.localsignage.test/com.wkq.localsignage.ReleaseUiSmoke；welcome参数仅适用于先确认空素材库的设备。详细机器摘要见本节链接，历史全功能回归保留如下。

---

# 全功能回归结果（2026-11-26）

版本1.5.1 / 1005001，当前工作区含未提交UI改动。对应commit与源码SHA-256指纹见 [本轮摘要](evidence/full-20261126/summary.json)。本轮有检查失败与未验证项，不能判为全部功能验收通过。

| 测试组 | 结果 | 本轮证据与范围 |
| --- | --- | --- |
| 全模块现有Debug JVM测试 | 本次通过 | 强制Test任务重跑，app 3、core_google 5、feature_app 126，共134项，0 failures/errors/skipped。涵盖存储与迁移、HTTP契约、配对安全、播放状态、场景/列表/排期、授权策略、价格及统计开关。见 unit-results.json |
| 发布流程Node测试 | 本次通过 | 9项，部分失败、网络错误、目标保持及失败设备重试 |
| Python门禁自身测试 | 本次通过 | 12项 |
| Pixel 8页面巡检 | 本次通过 | Android36。中文标准字体深色横屏34状态、英文1.3字体浅色竖屏34状态、中文欢迎路径16状态、英文套餐注入6状态，共90状态。布局JSON未见ellipsis/verticalOverflow |
| Pixel 8真实API与媒体 | 本次通过 | 22项。配对、无授权拒绝、状态/资源/场景/列表/排期/配置/诊断等读取、备份ZIP、图片/文字/HTML创建和播放状态、暂停恢复、三素材列表轮播、测试数据清理。状态断言不代表视频硬件解码或全天候运行 |
| 浏览器发布闭环 | 本次通过 | 虚拟API部分失败后只重试失败设备、只创建一次资源、目标消失不转本机、中英文切换，无pageerror |
| 浏览器布局及弹窗 | 本次通过 | 亮暗×4语言选择×3窗口×5页面=120组，320竖屏/844低高横屏/1440桌面无横向溢出；8个现有弹窗可打开及Esc关闭。de/ja-JP仍按产品现状英文回退 |
| 严格国际化资源 | 本次通过 | en、zh-CN、de、es、fr、ja、ko、pt。app壳无自身文案资源的提示保留 |
| 构建/UI/架构静态检查 | 本次通过 | 保留HTTP、系统栏主题、非Tab ViewPager、静态模块图不完整等警告 |
| 技能结构校验 | 失败 | 13项。Figma默认提示与校验器规则冲突；两个旧技能frontmatter格式不符；缺失ui-baseline、ui-style、library-organization与architecture测试脚本等引用。测试任务只记录，不修改业务与技能 |
| Debug、Release APK与AAB | 本次通过 | 首次组合进程提前退出，日志无明确原因；独立重跑Release/Lint后BUILD SUCCESSFUL。正式包开启R8；三个产物的HTML与当前源码一致，AAB签名校验通过（自签名链警告） |
| Lint | 本次通过（有警告） | app43、feature_app43、core_google27，共113 warnings，0 error/fatal。含依赖升级、KTX、复数候选、绘制、资源、静态字段等提示 |
| 原生单击授权/展开收起 | 受阻 | 第一次已进入套餐页，随后前台被桌面切换；重试时连接控件未出现在当前窗口。未取得完整连续操作证据，不作为产品缺陷或通过 |
| 真实购买、恢复交易与统计收数 | 未执行 | Play商品不可用，无真实扣款和生产统计后台验证 |
| 真双机同步、视频/native解码、24小时播放、Release实机运行 | 未执行 | 本轮无相应隔离现场环境或测试素材。现有相关单测及模拟多设备不替代这些结果 |

## 设备及恢复

Pixel 8中途离线，用户重新连接后完成真实API与媒体冒烟。字体已恢复为原值1.0，系统夜间模式恢复yes；页面巡检恢复应用语言与方向设置。媒体测试只在空资源库创建测试数据，全部清理完成。手动验证欢迎流程时点击了开始设置。配对截图、Token及原始日志仅保留在忽略的build目录；提交证据仅包含统计和产物摘要。未进行清数据、设备重启、支付或其他设备操作。

## 执行与可复核入口

- 本机完整日志、设备原始截图与脚本在 build/functional-test/full-20261126/。gradle.log为首次组合任务，gradle-retry.log为完成的独立重跑。
- Gradle执行 testDebugUnitTest（Test任务禁用up-to-date）、assembleDebug、assembleDebugAndroidTest、assembleRelease、bundleRelease、app/feature_app/core_google lintDebug。未通过调整构建配置消除错误。
- 设备入口为DeviceUiAudit，分别使用全量默认模式、welcome true与billing模拟模式。真实接口使用ADB loopback转发，结束后释放控制会话与端口。
- 基线只更新本轮明确执行通过且可关联源码指纹的测试组；未执行、技能失败与受阻动作不推进。

---

## 全页面简洁布局与蓝色视觉统一（2026-11-26）

当前工作区Debug构建成功，app 3项与feature_app 126项单元测试通过。严格国际化8语言检查通过。控制台9项发布测试通过，Playwright发布部分失败及重试、目标保持通过，深色下5主页面×4语言选择×3窗口尺寸共60组无横向溢出。非中英文选项使用已有英文回退。设计总览14页横屏布局检查通过。

修改涵盖实际原生连接页开放布局、蓝色授权入口、触控与电视焦点行为、帮助分组，以及控制台深色表面/表单/弹窗和设计总览。品牌蓝色保留#2563EB，Android夜间强调色为#91B8FF。

Pixel 8本轮设备巡检受阻。首次由NotificationShade遮挡，收起通知层并重跑后被另一应用com.wkq.nas切到前台遮挡，两次均返回code=0，不能计为通过。最新实机画面、大字与遥控焦点仍未完成验收。之前的设备结果不替代本轮结果。未发起真实购买，未推进基线。

## Pixel 8 设计落地差异修正（2026-11-26）

收紧授权状态区域，将错误、重试与账号说明移到套餐后，真实价格与价格不可用提示分别使用价格/正文字号。帮助页减少顶部留白、降低双栏阈值至640dp（仍按字体缩放计算）。Debug构建成功，重新安装Pixel 8后compact中文1.0横屏14状态通过，未发现ellipsis/verticalOverflow。原始截图位于build/functional-test/px8-20261126/zh-CN-1.0-px8-corrected-final。脱敏页面授权/帮助截图复制到设计总览，配对信息未复制。商品不可用，真实购买未验证。此前大字体结果不替代修正后的大字体验收。

## Pixel 8 页面实机复测（2026-11-26）

- 本次通过。当前工作区 Debug 与 AndroidTest APK 安装成功。Pixel 8 启动正常。
- 两轮 DeviceUiAudit compact 均返回 Audited 14 page states / code=-1。中文字体1.0横屏、英文字体1.3横屏，共28状态，report.json未出现ellipsis或verticalOverflow。覆盖连接、帮助、两篇文章、授权、法律中心及隐私正文和相应滚动状态。复核授权与帮助截图。
- 系统字体恢复为原值1.0。应用方向和语言由巡检恢复。设备未清数据，未发起购买。
- 授权页显示Google Play商品不可用，真实价格、交易与恢复交易未验证。欢迎页和纯播放画面未纳入本轮compact巡检，不能以本次结果声称全页面视觉验收完成。
- 原始截图含配对信息，只保留在忽略路径 build/functional-test/px8-20261126，不加入分享证据或Git。未更新设备测试基线。

## 全局蓝色 UI / 字体与横竖屏调整（2026-11-26）

- 本次通过：Android Debug assemble、app 与 feature_app 现有 Debug 单元测试；UI 静态检查。
- 本次通过：资源严格国际化检查覆盖 en、zh-CN、de、es、fr、ja、ko、pt；默认 app 资源不含本地文案的提示保留。Windows HTML 脚本检查遇命令长度限制，改为 stdin 传入后通过；门禁自身12项测试通过。
- 本次通过：控制台虚拟 API 发布失败重试及目标保持；5主页面 × 4语言选择 × 3窗口尺寸共60组无横向溢出。四个语言选项为 en、zh-CN、de、ja-JP；后两者按既有策略显示英文回退，不声称完整翻译。窗口为390×844、844×390、1280×800。截图在 evidence/redesign-20261126。
- 行为：手机保留既有默认横屏及显式竖屏/AUTO设置；大屏与多窗口按现有策略尊重系统。连接布局宽屏说明在左/二维码在右，窄屏二维码优先；普通正文字号统一，按钮允许长翻译换行，网页低高度横屏导航回归文档流。
- 未执行：Android 设备安装/实际画面、TV遥控、最大字体、Release构建与运行、真实计费、多设备现场。物理设备未操作；当前无可用AVD，不推进设备测试基线。

# 功能回归测试报告（最新一轮）

最新结果见本文件顶部的本轮完整回归；下方历史区块保留原始测试范围。




## 发布确定性与职责拆分（2026-10-29）

- 范围：分组发布不包含网关、同步后再播放、逐设备结果、失败设备独立重试、目标消失不回落本机、批量控制结果检查、移动端发布方式折叠、预览/恢复/授权说明、Web 离线源码拆分、数据库 schema/JSON/Web 内容访问职责分离。
- 功能事实：数据库版本保持 15，不更改 SQL、协议字段、权限或 SDK。自动恢复帮助统一到已有各语言部署边界文案；购买说明修正为月度、年度、永久。控制台新文案中英文，其他语言按已有策略 fallback 英文。
- OPT-PUBLISH：**本次通过**。`node --test tools/test_publish_workflow.cjs`，9 tests，0 failures。覆盖分组、空组、缺失结果、网络错误、同步失败不播放、只重试失败目标、播放失败不重复同步、本地播放拒绝与批量控制 HTTP 200 部分失败。日志 `build/optimization-20261029-web-tests.log`。
- OPT-CONSOLE：**本次通过**。Playwright 执行 `tools/console_ui_smoke.js`，使用本机静态页与虚拟 API；第一次 1/2 成功，重试后 2/2；仅创建资源一次、组内不发送本机命令、播放目标依次为 a 和 b。1440/390/320px 无横向溢出、中英文结果切换、失效目标保留均通过；另实测手机选内容后折叠及重新展开。无 pageerror。截图与摘要见 [浏览器证据](evidence/optimization-20261029/browser-results.json)。fixture 结果不代表真机播放。
- OPT-CONSOLE-PACK：**本次通过**。Python `--check`、全部源 JS 的 `node --check` 与 Gradle `verifyOfflineConsole` 通过。商业门禁加入源码一致性及发布流程回归。
- OPT-GATES：**本次通过**。架构、UI、i18n、构建扫描及 `git diff --check` 通过；Python 门禁自身回归 12 项通过。原有警告包括内部 Uri.fromFile、多 Insets owner 启发式提示、非 Tab ViewPager2、app 无本地 strings、局域网 HTTP/未声明 networkSecurityConfig、系统栏 theme 项。静态通过不表示这些风险已真机验收。
- OPT-CORE：**本次通过（首轮）**。`:feature:feature_app:testDebugUnitTest :app:testDebugUnitTest :core:core_google:testDebugUnitTest :app:assembleDebug --console=plain --max-workers=1 --no-parallel`，8m11s，exit 0。feature_app 126 + app 3 tests 无失败；core_google 5 项为 UP-TO-DATE，标记历史通过未复测。初次编译发现抽取 Web 内容策略时遗漏仍被 Overlay 路径使用的 WebSettings import，补回后通过。日志 `build/optimization-20261029-gradle.log`。
- OPT-FINAL：**本次通过**。最终 app/feature_app Lint 均 0 errors，分别 40/41 warnings；Debug/Release 构建成功，Release 含 R8/资源压缩（19m48s，`build/optimization-20261029-final.log`）。发现 Debug 中间包早于最后文案修正后重新打包；两个最终 APK 的控制台均与当前源码逐字节一致，签名和 16KB ZIP 对齐检查通过。APK 路径与 SHA-256 见 [产物清单](evidence/optimization-20261029/artifacts.json)。未进行 native ELF 单独审核或 16KB 真机执行。
- 时序问题与最终复测：最后刷新 Debug 时，已有 DevHubConsentTest 一次超过原 5 秒回调等待，失败点是回调未完成（授权撤回步骤），不是隐私状态断言；独立复测通过。将此异步集成测试的等待上限改为 30 秒并保留全部状态断言/有界失败，随后完整 feature_app **126 项再次通过**，0 failures/errors/skipped，1m25s。日志 `build/optimization-20261029-artifact-refresh.log`（失败）、`build/optimization-20261029-consent-recheck.log`（独立复测及最终 Debug）、`build/optimization-20261029-final-unit.log`（最终全量）。不以延长测试等待承诺 SDK 的实时性能。
- 汇总：6 组验证最终通过，最终未解决失败 0；保留上述一次测试时序失败记录。实际执行 Android 单测 126+3，JS 9，Python 工具 12；core_google 5 项历史通过未复测。两份 APK 均已生成但未安装到物理设备。源码指纹与测试摘要见 [源码清单](evidence/optimization-20261029/source-manifest.json)及[验证摘要](evidence/optimization-20261029/verification-summary.json)。
- 现场范围：**未执行**。检测到两台物理设备，未选择、安装、清数据或改变播放。Android 日夜/大字体/遥控器、双真机、磁盘满、24 小时混播、断电恢复、Release 安装升级与真实 Play 购买均未执行；详见 [现场矩阵](../../docs/LOCAL_SIGNAGE_DEVICE_ACCEPTANCE_MATRIX_CN.md)。本轮没有真实扣款或生产统计上报。


## 开放三档购买套餐（2026-10-21）

- 用户明确授权恢复月度、年度和永久版新购入口。移除隐藏面板和仅年付筛选；三个按钮各自绑定精确商品与offer，发起购买时统一防重复点击。保留Play新鲜商品校验，不硬编码生产价格。
- SUBS/INAPP独立查询并合并可用结果，缺少年付不再让月付/永久版一起消失。三张卡片始终展示，无有效价格或offer的卡片显示价格不可用且不可购买；支持pro-month及历史pro-mouth。宽屏三卡横排，窄屏/大字体纵排；帮助9个语言资源目录及中英购买说明同步。
- BILLING-PLANS-UNIT：**本次通过**。feature_app共126项，0 failures/errors/skipped，其中新增BillingPlansTest 7项，覆盖三档选取、月付别名、仅永久、无效价格/offer、单侧加载失败、双侧失败。日志 `build/billing-plans-build.log`（构建及测试2m47s）。完整app单测未重复执行，不将历史3项计入本轮。
- BILLING-PLANS-BUILD：**本次通过**。Debug与测试APK构建成功；最后布局改动后再次assembleDebug/assembleDebugAndroidTest，48s成功，`build/billing-plans-final-build.log`。没有修改依赖、Manifest或SDK。APK及源码指纹见 [artifact.json](evidence/billing-plans-20261021/artifact.json)。
- BILLING-PLANS-GATES：**本次通过**。UI/i18n/架构及git diff --check通过；既有非Tab ViewPager、内部Uri.fromFile提示保留。日志 `build/billing-{ui,i18n,architecture}-gate.log`。
- BILLING-PLANS-UI：**本次通过**。专用emulator-5564 / Android36：英文浅色宽屏1920×1200/160dpi/字体1.0；中文深色竖屏1080×2400/420dpi/字体1.3。`DeviceUiAudit -e mode billing`在测试APK内注入全部可用、仅永久可用、全部不可用三种页面状态，每种含顶部/底部，共 **12状态**，均code=-1，面板可见及按钮启用断言通过，JSON无ellipsis/verticalOverflow。人工检查宽屏三卡和竖屏底部按钮可达。
- **上述价格与offer均是UI测试夹具，不是Google Play真实商品响应；未点击购买、未真实扣款，也没有将夹具放入生产APK。** 原始数据在 `build/functional-test/billing-plans/`；[共享截图与JSON](evidence/billing-plans-20261021)已添加测试价格标识。日志 `build/billing-ui-wide.log`、`build/billing-ui-portrait.log`。
- 虚拟机启动时出现SystemUI ANR，按控件树识别后关闭系统弹窗；应用巡检最终正常完成。仅使用专用AVD，完成后恢复测试前显示参数并关闭，不操作物理设备。
- 汇总：4组通过，最终失败0，受阻0；真实Play商品发布/地区可用性、购买/退款/恢复交易、Release未执行。Play未返回的月度或永久商品仍显示价格不可用，需后台商品实际可用后才能购买。
- 本次建立完整app/core/feature/gradle及公开构建配置文件SHA-256清单，包含未跟踪源码；更新TEST_BASELINE中本次两条BILLING-PLANS用例，其余基线不动。[源码清单](evidence/billing-plans-20261021/source-manifest.json)。未提交或推送。

## 视频、HTML 混播与系统重启（2026-10-21）

- 本轮继续验证上一轮缺口，仍使用独立 Android36 AVD `localsignage_ui_20261021`。没有改动生产 Kotlin/XML，没有重复声称执行上一轮122项单测/48页面矩阵。新增可复跑测试脚本、执行说明、用例映射和脱敏证据；修正剩余工作清单中将已有双区模板/排期列为未实现的过时表述。
- 最终结果：**脚本8项通过 + 旋转1项通过，失败0，受阻0**。24小时、实体设备硬解、流媒体及API23–35设备属于未执行范围。实际运行命令 `python -X utf8 doc/testing/scripts/run_virtual_mixed_media.py --device emulator-5564 --video build/functional-test/mixed-media/motion.mp4 --seconds 300`，exit0；日志 `build/mixed-media-test.log`。脚本语法检查与 `git diff --check` 通过。
- MIXED-VIDEO：生成12秒640×360/24fps H.264测试图，经真实HTTP上传。播放器位置推进；两张间隔截图像素变化，人工确认运动测试图渲染。只证明此编码在虚拟机解码路径可用，不推断实体硬件解码。
- MIXED-HTML：本地HTML蓝色背景、正文和JS计数器可见；控件树包含正文，间隔截图计数器变化。初次控件树读取过早失败，增加渲染等待后通过；没有修改生产WebView来规避测试。
- MIXED-OFFLINE：关闭虚拟机Wi-Fi/移动数据，用ADB转发读取本地HTTP状态，视频、PNG、HTML三种资源均轮播且error为空。恢复网络后继续运行。
- MIXED-BOOT：执行真实 `adb reboot`；开机广播日志确认“Android 15+ defers media playback restore until the user opens the app.”。显式打开Splash后恢复原混播列表。结论是**系统重启后手动打开可恢复**，不表述为Android36无人值守开机自启。
- MIXED-SOAK：实际 **302秒 / 147次采样 / 50次素材切换**，3类素材均覆盖，持续playing且error为空，前后应用PID相同。主进程PSS从174363KB到192328KB，中间采样197181KB；结束时1个Activity、1个WebView。开始时尚有2个Activity，冷热阶段与素材类型不同，这组数据不能证明无内存泄漏，更不能代替24小时验收。
- MIXED-ROTATE：持续混播中实际旋转为1200×1920，再恢复1920×1200。截图确认竖屏视频按FIT保留上下黑边；混播断言持续通过。第一次仅改user_rotation时自动旋转仍开启，截图维持横屏，不计通过；随后保存原设置、临时锁定旋转并在finally恢复，实际方向验证通过。未对“同一视频位置无缝衔接”做逐帧断言。
- MIXED-CLEANUP：删除本轮新增资源、场景、列表，恢复初始设置/列表并恢复网络；仅清理专用AVD生成的测试数据。测试结束后关闭该AVD，未操作物理设备或其他模拟器。
- 环境与重试：虚拟机SystemUI曾ANR，按控件树确认后关闭系统弹窗并记录环境事件；不是应用自身崩溃。首次重启只等待5秒导致开机广播未到，且ADB转发已重置，使清理失败；改为等待实际开机广播、重建转发后重跑完成。只对已确认无原有内容的专用AVD重置本轮测试数据。最终日志无失败项。
- 证据：[完整结果](evidence/mixed-media-20261021/report.json)、[持续运行统计](evidence/mixed-media-20261021/soak-summary.json)、[视频/HTML预览](evidence/mixed-media-20261021/mixed-playback-preview.png)、[旋转记录](evidence/mixed-media-20261021/rotation.json)、[APK指纹](evidence/mixed-media-20261021/artifact.json)。证据不包含Token或连接二维码。
- 复跑说明：[scripts/README.md](scripts/README.md)。脚本使用内存Token，当前不做8小时Token续期，因此不能直接用86400参数宣称支持24小时方案；长期测试还需续期、周期采样与失败留证。
- TEST_BASELINE.json不推进：沿用上一轮构建的Debug APK（已记录SHA-256），未建立本轮与APK严格对应的源码工作区指纹。源码未提交/推送。

## UI 样式优化与独立虚拟机回归（2026-10-21）

- 增量验证，main / HEAD de3edf6 + 未提交工作区。完成欢迎页标题层级、连接页主次操作、帮助/授权响应双栏、统一日夜色板/按钮、授权重新加载、方向契约与设置局部更新。样式分析见 [优化记录](../../docs/LOCAL_SIGNAGE_UI_REFINEMENT_CN.md)。本节覆盖上一轮 UI 受阻项，不回写历史结果。
- UI-UNIT：通过。feature_app 119 + app 3 = **122 tests**，0 failures/errors/skipped；包含方向策略6项与真实 Ktor HTTP 设置更新回归。命令 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :feature:feature_app:testDebugUnitTest :app:testDebugUnitTest --console=plain --max-workers=1 --no-parallel -Pkotlin.compiler.execution.strategy=in-process`。日志 `build/ui-refinement-build.log`（6m4s）；最终资源构建 `build/ui-refinement-final-build.log`（27s）；巡检 runner 修正构建 `build/ui-audit-runner-build.log`（38s），均成功。
- UI-GATES：UI、i18n、架构、构建检查通过，git diff --check 通过；最终 app/feature UI 和 i18n 分别扫描。既有非 Tab ViewPager、局域网明文服务、系统栏主题提示仍保留。构建通过不替代视觉验证。
- UI-DEVICE：独立新建 AVD `localsignage_ui_20261021` / emulator-5564，Android36 google_apis x86_64。未操作物理设备或其他 AVD。三组巡检均 `Audited 16 page states / code=-1`，共 **48 个页面状态**；包括欢迎、连接、帮助、两篇文章、授权、法律中心和隐私正文及各自滚动底部。JSON 无 ellipsis/verticalOverflow，人工检查欢迎、帮助、连接、授权的截图层级与可达性。

| 配置 | 分辨率/密度 | 主题/语言/字体 | 方向 |
| --- | --- | --- | --- |
| 手机横屏 | 1080x2400 / 420dpi | 浅色 / en-US / 1.0 | LANDSCAPE，实际2 |
| 手机竖屏 | 1080x2400 / 420dpi | 深色 / zh-CN / 1.3 | PORTRAIT，实际1 |
| 平板横屏 | 1920x1200 / 160dpi | 深色 / zh-CN / 1.3 | 系统横屏，实际2 |

- 可复跑命令：`adb -s emulator-5564 shell am instrument -w -e mode compact -e locale en-US -e fontScale 1.0 -e orientation phone-landscape -e preference LANDSCAPE -e expectedOrientation 2 -e welcome true com.wkq.localsignage.test/com.wkq.localsignage.DeviceUiAudit`；其余配置替换 locale/fontScale/orientation/preference/expectedOrientation。日志 `build/ui-audit-phone-landscape.log`、`build/ui-audit-phone-portrait.log`、`build/ui-audit-tablet-landscape.log`。原始截图与报告在 `build/functional-test/ui-refinement/`，连接凭据仅存忽略目录。
- UI-PLAYBACK：通过 **9 项**：首次引导与配对、局部设置保留方向、PNG 上传并播放、暂停、恢复、双素材列表切换、关闭虚拟机 Wi-Fi/移动数据后本地轮播、force-stop 后重新启动恢复原列表、测试数据清理。通过 ADB 转发访问真实 HTTP 服务。三张播放截图中心 RGB 与上传素材一致，确认实际渲染；重启恢复截图有按比例适配的上下黑边。日志 `build/ui-playback-smoke.log`，结果 [playback-results.json](evidence/ui-refinement-20261021/playback-results.json)。不将进程重启等同于系统重启。
- 过程问题：独立虚拟机初启曾出现 SystemUI ANR；UI runner 的 API33+语言切换及中文脚本标签断言修正后通过。首次播放测试在重启服务的暂时断连时提前退出，补齐测试脚本启动等待的断连重试后9项全通过；只重置新建 AVD 内本轮生成的应用测试数据。未修改生产异常处理来规避失败。
- [可共享截图](evidence/ui-refinement-20261021)：三组欢迎、帮助、授权及播放画面；不含配对码、Token、二维码。测试后清理生成素材/场景/列表并恢复网络。
- 未执行：真实购买/商品价格卡、API23–35设备 UI、实际分屏拖动、视频硬件解码与旋转连续性、系统开机恢复、24小时播放、实体电视遥控、Release/AAB 发布。本轮仅 Debug 和虚拟机验证。API35方向由单测覆盖，不代表该版本设备验收。
- 不推进 TEST_BASELINE.json：未建立完整未提交源码指纹；保留历史基线。源码未提交、未推送。

## 横屏与页面适配（2026-10-21）

- 增量模式，Asia/Singapore，main / HEAD de3edf6，源码未提交。修改共享页面方向策略、新设备方向默认值、连接页响应布局/滚动位置、手动连接面板状态恢复、欢迎页文字与横屏间距。未改 SDK、Manifest、签名或依赖。
- ORIENT-UNIT：本次通过。`:feature:feature_app:testDebugUnitTest :app:testDebugUnitTest`：feature_app 118 + app 3 = 121 tests，0 failures/errors/skipped。共享方向用例 5 项，包含默认横屏、显式方向、AUTO 跟随设备、大屏和多窗口。旧 app 专属策略及测试迁移至 feature_app。大屏 >=600dp/多窗口继续遵循系统窗口方向，不强制锁定；已有 AUTO/PORTRAIT 配置保留。
- ORIENT-BUILD：本次通过。`gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :feature:feature_app:testDebugUnitTest :app:testDebugUnitTest --console=plain --max-workers=1 --no-parallel -Pkotlin.compiler.execution.strategy=in-process`，exit 0 / 2m2s；最后资源调整后补跑两项 assemble，exit 0 / 8s。日志 `build/orientation-verification.log`、`build/orientation-final-build.log`；Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`。首次构建出现 Runtime 对 storageAttempt 的全限定引用解析错误，改为显式 import 后通过。
- ORIENT-GATES：本次通过。UI、架构、i18n、build 四门禁及 git diff --check 均 exit 0。既有警告包括内部 Uri.fromFile、Insets 多页面消费者、非 Tab ViewPager、明文局域网服务及主题项提示，不当作视觉验证。
- ORIENT-UI：受阻（部分页面已观察）。专用目标 emulator-5558，Android36，1080x2400 / density420，浅色、en-US、字体1.0；保留数据覆盖安装 Debug 和测试 APK 成功。`adb -s emulator-5558 shell am instrument -w -e mode compact -e locale en-US -e fontScale 1.0 -e orientation phone com.wkq.localsignage.test/com.wkq.localsignage.DeviceUiAudit`。首轮被系统首次全屏提示遮挡；通过控件树定位 Got it 并关闭后重跑。生成连接、帮助、两篇文章、授权、法律中心共12张页面/滚动截图，但法律正文阶段失去窗口焦点而以 failure/code=0 退出，不能判定完整巡检通过；同期系统日志显示其他应用由 shell 启动并接管前台，不继续改动共享模拟器配置。
- 实际检查连接页、帮助、授权及授权滚动底部截图：横屏展示，二维码与信息左右排列，帮助标题避开系统栏，恢复购买和法律入口能完整滚出。只代表这些截图状态，不代表所有页面/字体/主题均通过。商品不可用，未验证真实价格或交易。
- 原始截图（含临时配对信息）只留 `build/functional-test/orientation-final/`，不提交；非敏感帮助、授权、法律中心截图见 [evidence/orientation-20261021](evidence/orientation-20261021)。巡检未正常结束，没有完整 JSON 报告；不宣称已完成所有控件溢出断言。
- ORIENT-REMAINING：未执行。竖屏/分屏、大字体与深色主题本轮设备验证、首次欢迎页、手动连接面板旋转恢复、视频旋转连续性、实体电视遥控、Release 和真实购买。未操作两台物理设备，未清库/卸载，未改变系统字体或分辨率。代码中的对应适配不等于上述设备验收通过。
- 汇总：通过3组（单测、构建、静态门禁），最终失败0组，受阻1组，未执行1组。固定基线不推进：本轮未建立可靠工作区指纹，保留原有基线。

## 全面屏与大屏适配（2026-10-20）

- 增量模式，main / HEAD d648970，本轮修改未提交。开始前 pull --ff-only 返回已是最新。版本仍为 1.4.0 / 1004000，compile/target 36，未降低 SDK。通过 5 组，最终失败 0、受阻 0，未执行 1 组（EDGE-REMAINING）。
- 改动：统一 WindowCompat.enableEdgeToEdge，覆盖 BaseActivity 默认重复 Insets；移除应用自身旧系统栏颜色/shortEdges 配置；页面避让系统栏与刘海，滚动区支持 IME。播放保留全屏背景，控件在安全区。Manifest 允许 resize，启动/欢迎不锁横屏，大屏和多窗口播放不申请固定方向；欢迎/套餐布局在尺寸变化后重排。
- EDGE-UNIT：本次通过。app 7 + feature_app 113 = 120 tests，0 failures/errors/skipped；新增方向策略 4 项、Insets 2 项（Robolectric API35）。命令 `gradlew.bat :app:testDebugUnitTest :feature:feature_app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --console=plain --max-workers=1 --no-parallel`，临时 init script Test 堆 128–512m、forkEvery=8，Kotlin in-process。JUnit XML 为两个模块 build/test-results/testDebugUnitTest/TEST-*.xml；日志 build/edge-to-edge-retry.log。此组合命令测试通过后 Release 内存不足，构建单独重跑见下一项。
- EDGE-BUILD：本次通过。最终 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --console=plain --max-workers=1 --no-parallel -Pkotlin.compiler.execution.strategy=in-process`，exit 0 / 4m53s；使用项目原有 Gradle 2GB 堆配置。证据 build/edge-packaging.log；APK app/build/outputs/apk/{debug,release}/app-{debug,release}.apk。架构/UI/build/i18n 四门禁 exit0（build/edge-*-gate.log），git diff --check 通过。既有 Insets、多消费者、滚动及非 Tab ViewPager 等扫描警告保留，不等于视觉错误。
- EDGE-PHONE、EDGE-TABLET：本次通过。专用 Android36 x86_64 模拟器 emulator-5558，未操作物理设备。命令 `adb -s emulator-5558 shell am instrument -w -e mode compact -e locale en-US -e fontScale <1.0|1.3> -e orientation <system-portrait|tablet-landscape> com.wkq.localsignage.test/com.wkq.localsignage.DeviceUiAudit`。两次均 result=Audited 14 page states / code=-1；实际方向分别为 portrait=1 / landscape=2。页面包括配对、帮助、两篇文章、计费、法律、隐私，均含滚动后状态；JSON 未发现 ellipsis 或 verticalOverflow，检查帮助/计费/法律截图无系统栏遮挡。计费为无 Play 商品可用状态，未验证真实价格卡片或交易。
- 手机配置 1080x2400 / density420 / 浅色 / 字体1.0 / 手势；平板配置 2560x1600 / density240 / 深色 / 字体1.3 / 三键任务栏。报告及原始截图在 build/functional-test/edge-20261020/；含配对凭据的原始截图仅留忽略目录，不复制到 Git。可跨电脑复核的非敏感截图见 [evidence/edge-20261020](evidence/edge-20261020)。
- EDGE-RELEASE：本次通过。保留数据安装同签名 Release 成功；Splash 进入 Welcome，检查平板横屏深色与手机竖屏浅色布局，按钮完整可见，点击 Start setup 后 MainActivity resumed。截图 release-welcome-*.png 在上述 evidence 目录。曾启用 tall cutout 模拟 overlay，但截图未确认实际刘海轮廓，实体刘海效果不记为通过；Insets 刘海值由单测覆盖。
- 测试过程失败记录：初次低堆测试出现 Robolectric OOM，改为上述 512m 后120项通过；低 Gradle Metaspace 上限导致 R8 daemon 退出，恢复项目配置后构建通过。首次模拟器进程启动 ANR，后续运行完成；UI 巡检最初被系统全屏提示遮挡，确认提示后重跑；`en` 与实际 `en-US` 严格断言不匹配导致测试 runner 抛出异常，改用实际 locale 后两轮通过。这些测试环境失败不计为最终生产崩溃修复证据。
- 已恢复模拟器原始分辨率/密度、字体1.0、浅色、自动旋转1/user_rotation0、手势导航与 cutout overlay；恢复原空 immersive_mode_confirmations。未清空数据库、卸载应用或开启统计采集。
- EDGE-REMAINING：未执行。实体设备、实际分屏拖拽/折叠屏、API23–35 页面、真实商品卡片、长期播放与 Play Console 上传后复扫。旧1.3.0条目不会因本地代码改变消失，不能保证依赖库的弃用 API 警告全部清除。未打包 AAB、未上传或发布。
- 不推进 TEST_BASELINE.json：本轮未建立包含未提交/未跟踪源码的可靠指纹，历史基线保持。

## 崩溃风险加固（2026-10-20）

- 模式：增量；本轮源码未提交，保留此前前台服务修复。本次通过 5 组，失败 0，受阻 0，未执行 1 组设备场景。
- 范围：页面解绑、主网页及 HTML 叠加层的 renderer 退出处理、诊断写入失败、初始化重试、设备任务同步异常边界。未改动依赖、SDK、Manifest、计费权益、统计授权设置；未删除数据库或用户内容。
- CRASH-LIFECYCLE：本次通过，WebViewRecoveryTest 3 tests / 0 failures / 0 errors。验证失效 WebView 替换后布局位置/id/尺寸/可见性保持、旧实例不重复替换、页面释放后不重建、detach 清理 owner 且可重复调用。属于 Robolectric 和内部状态测试，不等于系统真实 renderer 退出验收。
- CRASH-STORAGE：本次通过，StorageFailureBoundaryTest 4 tests / 0 failures / 0 errors。隔离测试数据库 `PRAGMA query_only=ON` 注入写失败，验证内存错误保留、错误记录写失败后不二次抛出、恢复写入、原 Store 初始化失败后重试成功；验证非存储 IllegalStateException 原样传播。
- CRASH-RETRY：本次通过，RetryAttemptTest 2 tests / 0 failures / 0 errors。验证失败后下一轮仍可成功、CancellationException 原样传播。生产循环失败时 60 秒起退避，最大 300 秒；成功轮次回到 30 秒。
- CRASH-REGRESSION：本次通过。`gradlew.bat :feature:feature_app:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain --max-workers=1 --no-parallel`，最终 exit 0，3m 50s；feature_app 111 + app 3 = 114 tests，0 failures / 0 errors / 0 skipped，含新增 9 项。证据 `build/crash-hardening-verification.log` 及两个模块 `build/test-results/testDebugUnitTest/TEST-*.xml`；APK `app/build/outputs/apk/debug/app-debug.apk`。
- 验证进程临时使用 Gradle `-Xmx512m -XX:MaxMetaspaceSize=384m`、Kotlin in-process、Test 堆 64–128m（临时 init script），未修改仓库构建配置。初次测试编译因 Robolectric getApplication 不接受泛型参数失败；修正后整套命令通过。
- 静态门禁：本次通过，架构/UI/i18n 脚本均 exit 0，`git diff --check` 通过。证据 `build/crash-hardening-architecture.log`、`build/crash-hardening-ui.log`、`build/crash-hardening-i18n.log`。既有警告包含内部 Uri.fromFile、Insets/滚动/非 Tab ViewPager2 提示、app 无本地 strings；未作为本轮整改项。
- CRASH-DEVICE：未执行。ADB 检测到两台物理设备，未选择目标或安装覆盖。未验证真实 renderer 退出、多 WebView 共用 renderer 的系统回调顺序、真机磁盘满/损坏、长时间播放、后台服务恢复、错误提示在小屏/大字体/日夜模式下的视觉效果；Release 未构建。本次只降低已识别风险，不代表所有异常均已消除。
- 不推进 TEST_BASELINE.json：未建立本轮源码工作区可靠指纹。原有无关测试基线保留。

## 前台服务启动拒绝修复（2026-10-20）

- 模式：增量；修改 MainActivity 启动时机、SignageService 提升前台失败的生命周期处理和任务移除恢复方式。未降低 SDK、未变更 Manifest 或开机恢复策略。
- 本次通过 1，失败 0，受阻 0，未执行 2；本次通过仅指构建/既有单测，不代表拒绝路径已在设备复现。
- FGS-BUILD：`:app:testDebugUnitTest :app:assembleDebug --console=plain --max-workers=1 --no-parallel` 最终 exit 0，34s；现有 BootRestorePolicyTest 3 tests / 0 failures / 0 errors。产物 `app/build/outputs/apk/debug/app-debug.apk`；证据 `build/fgs-fix-retry.log` 和 `app/build/test-results/testDebugUnitTest/TEST-com.wkq.localsignage.BootRestorePolicyTest.xml`。
- 首次执行因 Kotlin daemon 断连和测试 JVM 内存提交不足退出 1；重跑临时使用 Gradle `-Xmx384m -XX:MaxMetaspaceSize=256m`、Kotlin in-process，临时 init script 将 Test 堆设为 64–128m。未修改仓库构建内存配置。期间一次命令因 PowerShell 未引用带点的 `-P` 参数失败，修正引用后通过。
- FGS-REJECT、FGS-LIFECYCLE：未执行。ADB 检测到两台物理设备，未选择设备或安装覆盖；未验证实际拒绝、重进页面、任务移除、系统回收和播放/局域网控制闭环；Release 未构建。
- 静态检查：`git diff --check` 通过。对代码检查确认：仅处理 API 31+ 的 ForegroundServiceStartNotAllowedException，其他异常继续抛出；拒绝后停止服务且返回 START_NOT_STICKY，不初始化运行时资源，也不释放页面播放器；正常启动保留 START_STICKY 和 stopWithTask=false。
- 本轮不推进 TEST_BASELINE.json；未生成可靠工作区指纹，原始堆栈缺少异常消息、系统版本及触发场景，不能断言唯一触发入口。

## DevHub 接入增量回归（2026-10-09）

- 模式：增量；时区 Asia/Singapore；HEAD `a8b0c0a`，本轮源码未提交。
- 范围：DevHub 1.2.0 核心统计、app/core_google 初始化与依赖、服务日活触发、法律页面和多语言说明；保留 Firebase、购买权益与本地控制。
- 新增用例结果：本次通过 3，失败 0，受阻 0，未执行 2。既有及新增单测合计 110 tests / 0 failures / 0 errors / 0 skipped。
- 环境：JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.3.0；设备为 Android 36 `sdk_gphone64_x86_64` 模拟器，ADB 目标 `emulator-5556`；未操作物理设备。
- 测试前该模拟器未安装本应用。安装 Debug 后以保留数据方式覆盖为同签名 Release，未清除数据。应用版本 `1.3.0` / `com.wkq.localsignage`。
- 临时分辨率 `800x1280`、字体 `1.3`、深色模式已恢复为原始 `2560x1600`、字体 `1.0`、浅色模式。
- 本轮未建立包含新源码内容的可靠工作区指纹，不推进 `TEST_BASELINE.json`；后续须重新选择这些新用例，不能将本报告当成全量发布保证。

| 用例 / 检查 | 状态 | 命令或步骤 | 实际结果与证据 |
| --- | --- | --- | --- |
| DEVHUB-CONSENT | 本次通过 | `gradlew.bat :feature:feature_app:testDebugUnitTest :core:core_google:testDebugUnitTest :app:testDebugUnitTest --console=plain --max-workers=1 --no-parallel` | exit 0，42s；feature 102 / core_google 5 / app 3 单测通过。新增 `DevHubConsentTest` 验证未授权不生成安装身份、不入活跃队列，授权持久化，撤回清理队列，关闭后重复触发不再入队。仅使用测试 Key 与 `.invalid` 保留域名，未向生产上报。证据：`feature/feature_app/build/test-results/testDebugUnitTest/TEST-com.wkq.localsignage.analytics.DevHubConsentTest.xml` 及各模块 JUnit 报告 |
| DEVHUB-BUILD | 本次通过 | `gradlew.bat :app:assembleDebug :app:assembleRelease :feature:feature_app:testDebugUnitTest :core:core_google:testDebugUnitTest --console=plain --max-workers=2`；修复测试编译后单独 `gradlew.bat :app:assembleRelease --console=plain --max-workers=1 --no-parallel` | Debug assemble 完成；最终 Release 命令 exit 0，8s；R8、资源压缩、签名与打包通过；私有 DevHub 依赖成功解析。APK 路径见下方 |
| 架构 / 构建 / UI / i18n 门禁 | 本次通过 | `python -X utf8 .agents/skills/android-project-architecture-workflow/scripts/validate_architecture.py --project-root .`；`android-build-workflow/scripts/validate_build_output.py --project-root .`；UI 与 i18n 脚本按 Skill 声明扫描 app、feature_app、feature_res | 全部 exit 0。既有警告：内部 `Uri.fromFile`、局域网明文/缺少 networkSecurityConfig、既有 Insets/滚动/Pager 扫描提示、app 无本地 strings；未以本轮统计接入扩大其整改范围 |
| DEVHUB-LEGAL-UI | 本次通过 | `adb -s emulator-5556 install --no-incremental -r <apk>`；启动 Splash，通过计费页进入法律与隐私；UIAutomator 检查开关属性并截图；临时小屏竖屏、深色、1.3 字体后滚动至末尾 | Debug：checked=false / enabled=false；Release：checked=false / enabled=true，说明完整。800x1280 深色、1.3 字体下开关和说明自然换行，底部可完整滚出，无重叠。UI 树及截图在 `build/functional-test/devhub-20261009/`；关键脱敏截图见 `doc/testing/evidence/devhub-20261009/`。设备上未开启采集 |
| DEVHUB-DAILY-SERVICE | 未执行 | 服务每小时 `reportActive()`，销毁时取消回调；SDK `start()` 按 UTC 日期去重 | 代码路径与关闭状态单测已检查，但未执行真实跨 UTC 日期的常驻服务/后台计数验收 |
| DEVHUB-PRODUCTION | 未执行 | Play 测试轨授权后核对 CMS 安装/日活、断网恢复、撤回和 Integrity | 本轮未向生产创建测试安装记录，未验证 CMS 收数；未配置或验证 Play Integrity 项目编号。开关更新成功不等于后台已收到数据 |

初次并发构建和增量安装期间出现启动 ANR，系统日志同时显示 CPU / 内存压力。随后改用非增量安装、分步构建，Debug 与 Release 均可启动并打开目标页面；未将此观察当成长期性能验收。初次 app 缺少 core_google 依赖和测试 `getApplication` 泛型写法导致的编译错误已修复，最终通过结果如上。

配置迁移补充验证：按用户要求将客户端 App Key 放入已跟踪的 `app-config.properties`。核对时发现用户级 Gradle 同名属性会覆盖项目 Key，已改为只接受环境变量、显式 `-P` 参数、本机覆盖和公开项目配置，忽略用户级同名 DevHub 属性。未改动本机其他项目配置。Debug / Release 生成值均与项目公开配置一致；重新执行 `:app:generateDebugBuildConfig :app:generateReleaseBuildConfig :app:assembleDebug :app:assembleRelease --console=plain --max-workers=1 --no-parallel`，exit 0，1m 55s。旧配置的设备 UI 测试未开启采集，因此未向错误的应用创建测试统计记录；下列产物为修正配置后的版本。

产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `DC68E0799493D0EFEDF275E3EC74DF325965E9073B7F470FDD617FBB834ABCAD`。
- Release：`app/build/outputs/apk/release/app-release.apk`，SHA-256 `A078D1550F1B84E433813F3F0421AB13724D06B6519564314B7039A670E27345`。

未验证范围：其他 Android/TV/厂商设备、所有语言的设备视觉遍历、真实购买、真实跨日、生产断网补报、Play Integrity 与生产 CMS 收数；未重新认证整包 native/16KB 兼容性。后续按用户要求将客户端 App Key 移入公开的 `app-config.properties`；JitPack 授权、签名密码等秘密仍只存在未跟踪配置或环境变量。报告与模板不重复记录真实 Key/Token。

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

## 商业化计费与文案一致性回归

- 时间/时区：2026-09-24，Asia/Singapore。
- 分支/HEAD：`main` / `a7d72f0`；本轮未提交。
- 工作区指纹：`0247f0493c6047316880c258f33b2df8267306f1`。由排除本报告/基线后的 tracked diff 与新增的 `BillingPriceFormatter.kt`、`BillingPriceFormatterTest.kt` blob 哈希组合生成。
- 环境：Windows 10 x64、JDK 17.0.12、Gradle 8.13、compile/target SDK 36、Robolectric。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；SHA-256 `2DDE86F92BE2AE1152D2B6EB536BE7B0838E848B3449EBCBFB4E4E6B7585E544`。

| 用例 | 状态 | 命令/步骤 | 实际结果 |
| --- | --- | --- | --- |
| Feature JVM 回归（含 `BILLING-PRICE`、`BILLING-VERIFY`、`BILLING-ACCESS`） | 本次通过 | `gradlew.bat :feature:feature_app:testDebugUnitTest --no-parallel` | 99 tests，0 failures，0 errors；包含 Play 格式化价格、空价格不回退伪价格、API 23 验签兼容与购买签名拒绝路径 |
| Feature lint | 本次通过 | `gradlew.bat :feature:feature_app:lintDebug --no-parallel` | exit 0；此前报告的 `java.util.Base64` / minSdk 23 lint errors 已消除 |
| Debug APK | 本次通过 | `gradlew.bat :app:assembleDebug --no-parallel` | exit 0；APK SHA-256 见上 |
| 多语言资源校验 | 本次通过 | `python -X utf8 .agents/skills/android-i18n-workflow/scripts/validate_i18n_resources.py --res-dir app/src/main/res --res-dir feature/feature_app/src/main/res --res-dir feature/feature_res/src/main/res` | 校验通过；提示 `app` 没有 string/plurals 资源，为现有结构警告 |
| 差异格式 | 本次通过 | `git diff --check` | exit 0；Git 提示工作区 LF/CRLF 转换，不影响检查结果 |
| Google Play 真实购买、取消、续订、退款、恢复及目标电视设备 | 未执行 | 需 Play 内部测试轨道、License Tester 和目标硬件 | 不能据此声称订阅交易、商品目录、设备兼容或上架验收已通过 |

本轮汇总：本地自动门禁 4 项通过、0 项失败；Play 与硬件交易验证未执行。商店素材已标记为草案，法律服务条款已标注待产品及法律审核，不应直接视作已生效或可上架内容。仅记录本轮通过的本地门禁，见 [`TEST_BASELINE.json`](TEST_BASELINE.json)。

## 计费页模拟器冒烟与错误状态修正

- 时间/时区：2026-09-24，Asia/Singapore。
- 分支/HEAD：`main` / `a7d72f0`；本轮变更尚未提交。
- 初测环境：新建隔离 AVD `codex_localsignage_billing_api36`，Android 16/API 36，Google Play x86_64 系统镜像，序列号 `emulator-5560`；未操作 PCAM00 实体机或另一台 RoofQuote 模拟器。
- 初测 APK：Debug，`com.wkq.localsignage` 1.3.0；SHA-256 `2DDE86F92BE2AE1152D2B6EB536BE7B0838E848B3449EBCBFB4E4E6B7585E544`。

| 用例 | 初测结果 | 实际观察 |
| --- | --- | --- |
| 新装启动与试用态 | 通过 | App 启动至主界面，显示本地 Trial 状态；没有 AndroidRuntime 崩溃 |
| 打开计费页 | 通过 | 主界面的计费入口可打开 `BillingActivity` |
| Play Billing 不可用时的页面状态 | 失败，随后修正代码 | 侧载 Debug 包在该 AVD 收到 Billing response code 3（`Billing service unavailable on device.`）；计费页一直显示加载圈，没有呈现不可用提示。该环境无法验证商品目录或真实交易，但暴露了终态错误仍被当作加载中的 UI 状态问题 |
| 计费 UI 状态回归 | 本次通过 | 新增 `MonetizationUiStateTest` 两项断言：未完成且无错误时保持 pending；终态错误时结束 pending。Feature 全套 101 tests，0 failures/errors |
| Feature lint 与 Debug APK | 本次通过 | `:feature:feature_app:lintDebug` 及 `:app:assembleDebug --no-parallel` 成功；新 APK SHA-256 `9BB63E34D20911B9A1577DE6B93FD2E9D1D730B4FA3EAC61502422C556B564AC` |

代码修正让 `MonetizationUiState` 在错误终态结束目录等待，从而让页面显示不可用状态和重试入口；没有改购买校验、真实权益或发起购买的逻辑。模拟器在新 APK 覆盖安装前已退出。按用户要求停止测试，修正后的计费页尚未在 AVD 复核；Google Play 真实购买/恢复也未执行。修复前计费页截图与层级证据位于 Git 忽略目录 `build/functional-test/billing-vm-20260924/`；同目录另有主界面临时截图/层级文件，含一次性配对信息，不会纳入提交或分享。

## Pixel 8 紧凑套餐首屏（2026-11-26）

三档套餐描述及专业版权益改为展开区域，八种语言提供展开/收起标签，状态区进一步收紧。Debug构建、八语言严格资源检查通过。安装Pixel 8后compact中文标准字体横屏14状态巡检通过。首屏可见月度、年度、永久三个购买按钮，布局报告坐标均在屏幕范围内。原始截图在build/functional-test/px8-20261126/zh-CN-1.0-px8-compact-plans。Google Play商品不可用，真实购买未验证；大字体修正后未复测。
