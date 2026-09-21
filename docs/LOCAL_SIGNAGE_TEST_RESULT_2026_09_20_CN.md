# Local Signage 测试结果

测试日期：2026-09-20 至 2026-09-21

## 范围

本轮覆盖离线内容制作、播放排期、局域网设备发现与配对、设备间播放列表下发，以及手机/设备端控制入口。测试环境为同一局域网内的两台 Android 真机：Huawei NAM-AL00 与 OPPO PCAM00。

## 已通过

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| Debug 构建 | 通过 | `:app:assembleDebug --offline` 成功。 |
| 功能模块单元测试 | 通过 | `:feature:feature_app:testDebugUnitTest --offline` 成功。 |
| 架构校验 | 通过 | 校验通过；保留既有 `Uri.fromFile` 外部共享告警。 |
| 局域网发现 | 通过 | 两台真机可相互发现。 |
| 设备配对 | 通过 | 已验证设备对设备的本地配对与凭据保存。 |
| 源端排期播放 | 通过 | 文本素材、场景、播放列表和全天排期可自动进入播放。 |
| 播放页控制入口 | 通过 | 遥控器/键盘等价按键可打开设备中心并返回播放。 |
| 跨真机播放列表同步 | 通过 | OPPO 源端向华为旧构建下发真实文本资源、场景与播放列表，目标端返回 `PLAYLIST_SYNCED`。 |
| 跨真机远程播放 | 通过 | 目标端返回 `COMMAND_ACCEPTED`，状态确认播放中，且华为实际显示同步文本。 |

## 已完成的产品改进

1. 欢迎页在紧凑横屏设备上减少冗余说明，保留首屏的主要配置入口。
2. 排期命中后强制恢复播放，避免冷启动仅切换到播放层却停留在黑屏。
3. 播放列表同步按资源、场景、播放列表、排期阶段返回状态码，便于控制台显示准确错误原因。
4. 手动打开设备中心时轮换二维码和电脑配对码，避免继续展示过期的临时凭据。
5. Web 控制台补充排期、多设备状态、内容模板与离线项目管理相关能力。
6. 资源查重仅在目标端明确返回 `exists=true` 且携带有效资源 ID 时复用；旧目标端的 `exists=false, resourceId=null` 不再被误判为可引用资源。
7. 场景和播放列表同步失败会保留目标端返回的错误码，控制台可直接区分资源、场景和播放列表的失败阶段。
8. 对空设备列表与未配对设备分别返回 `DEVICE_IDS_REQUIRED` 和 `PAIRED_TARGET_NOT_FOUND`，避免笼统的“无设备”提示。

## 未闭环项

跨真机播放列表同步与远程播放已经闭环。华为设备因系统要求输入锁屏密码，未在本轮更新到最新包，因此本轮特意保留其旧构建作为兼容性目标端；排期同步沿用 2026-09-20 的本机与设备间验证结果，尚未以本次新增的文本播放列表再次执行一轮非空排期回归。

## 本次门禁

```text
./gradlew :app:assembleDebug :feature:feature_app:testDebugUnitTest --offline
python .agents/skills/android-project-architecture-workflow/scripts/validate_architecture.py --project-root .
git diff --check
```

以上门禁均通过；架构校验仅报告已有的 `Uri.fromFile` 外部共享告警。
