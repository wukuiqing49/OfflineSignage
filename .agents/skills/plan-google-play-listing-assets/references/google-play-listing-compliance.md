# Google Play Listing 素材合规

## 目录

- 官方来源
- 每次执行的核验流程
- Shared 检查
- Feature Graphic 检查
- Screenshots 检查
- Preview Video 检查
- Badge 检查
- 无法联网时

## 官方来源

- Preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Google Play brand assets: https://play.google.com/intl/en_us/badges/

优先读取 Google 官方当前页面，不以本文件替代最新规则。在 Brief 中记录 URL、核验日期和访问状态。

## 每次执行的核验流程

1. 查询 Feature Graphic、Screenshots 和 Preview Video 当前规格。
2. 查询设备类型、方向、格式、透明通道和数量要求。
3. 查询 YouTube、广告、年龄限制、字幕和封面要求。
4. 仅在 External Video 需要下载引导时查询 Badge 最新素材和语言版本。
5. 把规则分为 `OFFICIAL_REQUIRED`、`OFFICIAL_RECOMMENDED`、`INTERNAL_RECOMMENDATION`。

## Shared 检查

- 只宣传 VERIFIED 且 advertisable 的能力。
- 不使用 Fake UI、虚假数据、排名、下载量、价格、折扣和未经验证声明。
- 使用真实 App Icon，不重绘或改色。
- 三类素材与 Listing Asset Positioning 一致。
- 不包含账号、Token、用户数据或隐私截图。
- CONCEPT 模式必须明确不是 Play Console 提交就绪素材；不使用设备框、假 UI、虚构操作或未经验证的市场声明。

## Feature Graphic 检查

- 当前基线为 1024x500，执行时重新核验。
- 检查格式、透明通道、安全区域、文字可读性和视觉遮挡。
- 检查与 Preview Video 播放封面的协同。
- 不把内部建议表述成 Google 强制要求。

## Screenshots 检查

- 按目标设备类型查询当前数量、尺寸和方向要求。
- 只使用真实 UI，保持截图比例和内容。
- 检查顺序、文字、裁剪、变形、遮挡和小尺寸可读性。
- 多语言版本使用对应 Locale 的真实 UI。

## Preview Video 检查

- 使用单个 YouTube 视频 URL，不使用频道、播放列表或附加 timecode。
- 视频设为 Public 或 Unlisted，不设为 Private。
- 关闭 monetization/ads；存在版权 monetization claim 时换用无认领内容。
- 不设置 Age Restriction。
- 横屏和竖屏均可，方向匹配真实 App 体验。
- 竖屏视频不添加左右黑边。
- 前 30 秒可能静音自动播放，因此开头必须独立成立。
- 规划字幕并检查 Feature Graphic 封面。

Preview 中不用强安装 CTA 和 Badge 是本 Skill 的保守内部策略，不冒充 Google 明文禁令。

## Badge 检查

- 只使用 Google 当前官方素材，不由 AI 重绘。
- 不改颜色、比例、字体、图标、文字或内部布局。
- 使用与目标营销语言一致的官方版本。
- Preview 默认不用 Badge；External 下载引导时使用。

## 无法联网时

生成 DRAFT Brief，在合规报告中标记 `UNVERIFIED_CURRENT_POLICY`，列出未核验规则、上次已知来源和人工检查项。不得声称已经通过 Google Play Compliance。
