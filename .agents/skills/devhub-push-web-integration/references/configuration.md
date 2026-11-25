# 配置、发送与诊断

本文历史核对日期为 2026-11-14，但未附可复核的版本快照，不能据此视为当前已验证。执行前先核对目标 DevHub `docs/push-integration.md`、存在时的 `docs/push-center.md`、服务端 push/users 服务与 SDK 源码；旧项目推送页和新版全局推送中心不能混用操作路径。官方行为参考 [FCM 接收消息](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)、[HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api)、[通知创建](https://developer.android.com/develop/ui/views/notifications/build-notification)。版本变更需重新核对。

## 配置顺序

1. 分清 DevHub App ID、Firebase Project ID、Android 包名、Google Web Client ID，不能互换。API/CMS/宿主按自己的环境配置，不复制历史真实地址或账户。
2. 查询远程迁移记录，依目标版本只执行待执行项。历史推送涉及 0026～0029，新版全局中心还需 0030；不得按本文编号盲目执行。数据库 → API → CMS，确认部署授权后实施。
3. `GOOGLE_PROFILE_ENCRYPTION_KEY` 是32字节随机密钥的 Base64，用于资料、Token 与新版发送账号加密。已有密文或邮箱索引时恢复旧密钥，不为消除错误随机覆盖。只有确认首次配置且无既有密文/相关索引时才生成，通过 Secret 机制保存并安全备份，不回显值。
4. `GOOGLE_SIGN_IN_CLIENT_ID` 与宿主的 Google Web Client ID 对齐。Google 登录需服务端校验与当前安装身份关联，Token 取得与关联成功分别检查。
5. 新版 CMS「推送设置 → 添加程序」选择目标 App，上传对应 Firebase 项目的服务账号 JSON。保存只检查格式，再用「验证发送权限」执行 OAuth + FCM validate_only，不真实发送。账号加密保存，不回显私钥。
6. 旧版或保留 Secret 时，`FCM_SERVICE_ACCOUNTS` 为 **DevHub App ID → 服务账号对象** 映射；对象包含 project_id、client_email、PKCS#8 private_key。更新映射保留其他 App。新版数据库保存的账号优先于 Secret，先确认实际生效来源，避免修改无效配置。

仅结构示例，不能直接上线：

```json
{
  "DEVHUB_APP_ID": {
    "project_id": "firebase-project-id",
    "client_email": "sender@firebase-project-id.iam.gserviceaccount.com",
    "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
  }
}
```

服务账号有目标 Firebase 项目的 FCM 发送权限；Token 必须属于该项目。服务端私钥不能进入 App/CMS 前端，上传界面须沿用管理员鉴权，后端再次校验 App 范围。

## 登记和范围

SDK `updatePushRegistration` 复用安装登记。启动主动获取当前 Token；enabled=true 需要有效 Token。enabled=false 可传 null 保留旧 Token 并停用，permission 报告系统真实状态，关闭运营偏好不等于 DENIED。遵守现有数据收集同意和备份排除，日志只写脱敏错误码。

指定用户/设备依赖当前 identity 关联、enabled=true、系统权限 granted 和有效 Token。登录/换号用 identifyGoogle，退出 clearIdentity；离线解绑有同步窗口，正文及 URL 不携带个人敏感信息，页面再验证当前账号权限。

公共 Topic 从 CMS 当前 App 复制，形如 devhub_app_ 加去掉连字符的 App ID，用户同意且系统授权才订阅；停用登记不代替退订，退订也非即时。Topic 只发公开信息，不是鉴权手段；一个 Topic 目标不代表一个接收设备。

## CMS 与发送

新版全局推送中心先选择程序，再选指定个人/设备或明确确认群发；填写标题、正文、HTTPS URL，预览应用/受众/内容后发送。发布活动页不自动发送通知；推送也不自动发布页面。修改内容后重新预览。

兼容发送接口为 POST `/v1/admin/apps/{appId}/push`，字段和限制按当前后端类型核对；requestId 幂等，不能将结果不明的任务作为全新任务重发。GET 同路径查看任务；新版 GET `/v1/admin/push/settings` 取配置摘要，POST 同路径 save/check，POST `/v1/admin/apps/{appId}/push/process` 仅处理到期任务，不是失败任务强制重发。

notification + data 中 data 全为字符串，版本1的点击字段：

```json
{"devhubVersion":"1","devhubMessageId":"job-id","url":"https://trusted.example/page"}
```

前台通常经过 onMessageReceived 手动展示；后台 notification + data 通常由 FCM 展示、点击 Intent 交宿主，不能只改前台路径。

## 错误判断

| 观察 | 下一步 |
| --- | --- |
| profile_not_configured | 检查既有32字节 Base64 密钥绑定；先看密文存在情况，不盲目换 key |
| push_not_configured | 检查当前 App 账号配置来源与 App ID 映射 |
| push_configuration_invalid | 查字段/PKCS#8/加密读取，报告不含配置内容 |
| google_login_not_configured | 核对服务端 Google Web Client ID；不跳过 ID Token 校验 |
| 无可用设备 | 看当前身份、enabled、权限、Token 登记，不从历史安装猜收件人 |
| validate_only 通过 | 只证明凭据/权限校验路径，再做指定设备真实接收 |
| accepted | FCM 已接受；继续查手机显示、点击和内容，不宣称送达率 |
| unknown/网络结果不明 | 先核对原任务与设备，禁止盲目重发 |

保存配置、配置格式正确、发送权限有效、手机实际收到是四个不同结果。CMS 不显示密钥/Token，服务端错误脱敏；本地测试使用 mock 时必须说明。

## 版本追溯记录

接入或复测时在目标项目现有报告中追加下表；读取真实源码/部署信息后填写，不用日期代替版本证据。无法取得时标记未知及受影响项目，不宣称接口已验证。

| 对象 | 应记录的证据 |
| --- | --- |
| Android 宿主 | 仓库标识、commit、相关未提交差异、构建变体与 applicationId |
| DevHub SDK | 实际解析依赖版本/不可变 commit、updatePushRegistration 签名、权限枚举来源 |
| API Worker | 源码 commit、部署版本/环境、push/users 路径和协议定义 |
| CMS Pages | 源码 commit、实际部署版本、页面清洗与 CSP 配置来源 |
| 数据库 | 当前迁移状态及待执行项；文中的历史编号仅供定位 |
| Web 桥 | 宿主及 H5 客户端版本、动作契约、精确 origin 白名单 |
| 验证记录 | 本轮日期、脱敏目标设备、requestId、各阶段证据及未验证项 |

本地源码与线上版本不一致时，先标明差异并按实际部署版本核对；不根据本地接口自动修改线上配置。记录配置名称和来源，不保存私钥、Token、用户身份或真实凭据。
