# Google Play 商店截图策划

## 角色

截图组通过真实 App UI 证明功能存在。把截图当作有顺序的产品叙事，不按源码页面数量机械生成。

## 顺序

默认从以下结构中选择：

1. 产品类别和核心价值
2. 核心任务入口
3. 最重要功能
4. 第二重要功能
5. VERIFIED 差异化
6. 真实结果

根据 Google 当前设备类型要求、产品复杂度和真实页面动态确定数量。前几张必须独立解释产品，不依赖用户滑到最后。

## 每张截图

填写：

- Screenshot Number
- Purpose
- Device Type
- Locale
- Orientation
- Real App Screen
- Starting State
- Demo Data
- Headline
- Supporting Text
- Text Position
- Visual Focus
- Crop
- Device Frame
- Background Direction
- Product Feature Evidence
- Keyword Relationship
- Source Screenshot ID
- Required Assets

Headline 简短、准确，一次只表达一个意思。外部标题不得遮挡关键按钮、数据和结果。

## 真实 UI 门禁

- 只使用独立提供的真实 App Screenshot。
- 不把设计参考、竞品截图或图片 AI 生成页面放入设备框。
- 不修改截图内部文字、按钮、图标、颜色、布局和数据。
- 需要其他 Locale 时使用真实 App 对应 Locale 的截图，不翻译图片像素。
- 允许等比裁剪低价值系统区域和克制高亮，不允许拉伸或变形。

CONCEPT 模式不要求真实截图，但必须改用无设备框的功能营销画面：以 VERIFIED 功能标题、真实项目示例输入/输出素材、准确结果类型和品牌视觉表达能力。`Real App Screen`、`Starting State`、`Source Screenshot ID` 填写 `N/A`，`Device Frame` 填写 `none`；不得生成任何仿 App 页面、按钮、列表、导航栏或虚构屏幕数据。该模式产物是创意执行稿，不声明已经展示真实 UI。

## 输出

使用 `assets/SCREENSHOT_BRIEF.template.md`。每张截图生成独立 Final Image Prompt，并映射真实 Source Screenshot ID。缺失真实截图时保留计划，但标记 `BLOCKED_BY_MISSING_ASSETS`。
