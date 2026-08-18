# VIDEO_BRIEF 结构

## 输出原则

使用 `assets/VIDEO_BRIEF.template.md`。保留全部一级章节；不适用内容填写 `N/A` 并说明原因，不删除章节。

## 定位信息

记录 App Name、Category、Platform、Audience、Problem、Value Proposition、Primary Message、Differentiator、Supporting Features、Video Type、Locale、Orientation、Resolution 和 Duration。

定位必须引用最终关键词分类和 VERIFIED Product Fact，不以高搜索量替代产品相关性。

## Storyboard

每个 Scene 标题使用：

```text
### Scene 01 | 00:00-00:03
```

每个 Scene 填写：

- Purpose
- Real App Screen
- Starting State
- User Action
- Visible Result
- Demo Data
- Text Overlay
- Text Position
- Visual Focus
- Camera / Crop
- Transition
- Positioning Relationship
- Product Feature Evidence
- Recording Clip ID

时间轴必须从 00:00 开始、连续、无重叠，并覆盖声明总时长。不要机械套用固定 Scene 数量。

## 录屏和素材

每个 Clip 记录 Screen、Start State、Action、End State、Duration、Orientation、Demo Data、Overlay 和关联 Scene。使用可复现、无个人信息、无版权风险的数据。

真实素材使用稳定 ID，例如 `ICON-01`、`CLIP-01`、`SHOT-01`、`BADGE-en-US-01`。缺失素材标记 `MISSING`，不得让视频 AI 自动补画 UI。

CONCEPT 模式不要求录屏。Storyboard 使用真实项目 Icon、品牌资源和示例输入/输出素材制作功能动效，`Real App Screen`、`Starting State`、`User Action`、`Recording Clip ID` 填写 `N/A`，并明确不展示点击、导航或 App 页面。可以动画化裁剪、压缩、格式转换等 VERIFIED 任务的输入到输出关系，但不得伪装成真实操作录屏。此模式视频是概念宣传稿，不声明为已经验证的真实产品操作演示。

## Final Execution Prompt

Prompt 必须自包含并包含：

- 产品与视频定位
- 精确尺寸、方向、总时长和 Scene 时间
- Clip/Asset 映射
- Overlay、Demo Data、转场、视觉和音频方向
- App Icon、Badge 和 Ending 规则
- 禁止虚构功能、UI、数据、排名和促销声明
- 缺失真实录屏时的阻塞状态

要求视频工具以 compositing 方式使用真实录屏，锁定 UI 像素，不生成、重绘、翻译或改写屏幕内容。

## 合规结果

每项检查填写 `PASS`、`FAIL` 或 `UNVERIFIED`，同时填写证据或待办。只要存在影响真实性或官方提交的 FAIL，Executive Summary 就不得写“可直接发布”。
