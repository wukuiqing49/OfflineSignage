# Feature Graphic / 封面策划

## 角色

Feature Graphic 用一个主要信息说明 App 是什么，并与截图和视频保持统一。常规 Preview Video 在 Google Play 中使用 Feature Graphic 作为播放封面，因此同时检查静态封面和视频入口效果。

## 当前制作基线

- 画布：1024x500 px
- 格式：按 Google 当前要求使用 JPEG 或无透明通道 PNG
- 内容：以品牌和产品价值为主，避免复杂功能清单

每次执行重新查询 Google 官方页面。把以上基线与最新规则不一致的部分标记并采用最新规则。

## 构图决策

根据真实品牌和产品决定：

- Primary Message
- Supporting Message
- 是否需要真实 App UI
- 是否需要设备框
- Visual Focus
- Background/Color Direction
- Typography Direction
- Icon/Logo Treatment
- Safe Area
- Text Placement

如果使用 App UI，只使用已提供的真实截图，保持比例和像素内容，不翻译、不重绘、不改数据。

CONCEPT 模式没有真实截图时，不使用设备框或屏幕区域。使用真实 App Icon、品牌色、项目内授权示例素材和 VERIFIED 核心任务建立产品类别画面；不得生成看起来像 App 页面、按钮或导航的元素。

## 避免

- 多张截图拼贴
- 长段文字或功能清单
- 与 App 无关的 Stock Photo
- 假 UI、伪文字或无法验证的数据
- 排名、下载量、价格、折扣或促销
- 直接把 App Icon 放大当作唯一创意
- 与 Preview Video 第一帧完全无关的封面

## 输出

使用 `assets/FEATURE_GRAPHIC_BRIEF.template.md`。Final Image Prompt 必须引用真实 Asset ID，禁止生成或重绘 App UI，并说明最终位图仍需人工检查文字、变形、遮挡和授权。
