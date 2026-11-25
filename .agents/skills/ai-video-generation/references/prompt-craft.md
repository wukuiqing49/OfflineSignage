# 镜头提示词工程指南 (Prompt Craft Guide)

## 目录
- [提示词公式](#提示词公式)
- [景别规范](#景别规范)
- [镜头运动词汇](#镜头运动词汇)
- [动作与物理连贯性](#动作与物理连贯性)
- [光影与质感控制](#光影与质感控制)
- [负向提示词沉淀](#负向提示词沉淀)

---

## 提示词公式

高质量 AI 视频生成的黄金结构：
> **[景别/构图] + [主体身份/外观] + [核心动作/微表情] + [运镜方式/速度] + [环境与光影氛围] + [画质与渲染风格]**

**示例**：
`Medium close-up of an Asian female engineer wearing modern studio headphones, nodding attentively with a slight smile as code compiles on screen. Slow smooth dolly in. Cinematic soft rim light, 35mm lens, photorealistic 4k, shallow depth of field.`

---

## 景别规范

| 景别术语 | 英文关键词 | 适用场景 |
| --- | --- | --- |
| 远景 / 大全景 | `Extreme wide shot`, `Aerial view`, `Establishing shot` | 交代宏大场景、城市天际线、环境背景 |
| 全景 | `Wide shot`, `Full body shot` | 展现人物全身及与所处环境的空间关系 |
| 中景 | `Medium shot`, `Waist-up shot` | 日常交谈、主体交互、半身工作状态 |
| 中近景 | `Medium close-up`, `Bust shot` | 聚焦人物表情、微妙的情绪变化 |
| 特写 | `Close-up shot` | 手部操作手机、屏幕细节、眼神焦点 |
| 大特写 / 微距 | `Extreme close-up`, `Macro shot` | 细微按键反馈、镜头光圈收缩、眼睛瞳孔 |

---

## 镜头运动词汇

严禁使用模糊的“camera moves”，必须使用明确的电影摄影机专业词汇：
- **推进 / 拉远**：`Slow push-in`, `Smooth dolly in`, `Dramatic pull-out`, `Slow zoom out`
- **水平平移**：`Pan left to right`, `Tracking shot following the subject`, `Truck right`
- **垂直升降**：`Tilt up from hands to face`, `Pedestal up`, `Crane shot descending`
- **环绕运转**：`360-degree orbit around the subject`, `Arc shot clockwise`
- **固定机位**：`Static locked-off shot`（适合呈现精致的微动作或界面变动，避免画面晃动眩晕）

---

## 动作与物理连贯性

1. **单一动作原则**：每个 4s ~ 6s 的镜头内只安排**一个明确的主动作**（例如“转头微笑”或“拿起咖啡杯”，不要写“走进房间、坐下、开电脑然后喝水”）。
2. **动词优先**：使用精确的动态动词（`swipes smoothly`, `glances upward`, `reaches for the smartphone`, `taps firmly`），而非抽象修饰词。
3. **避免动作过载**：过于剧烈的打斗或剧烈翻滚极易造成模型肢体溶解，若需复杂动作，可拆分为“起势”、“命中”、“特写反应”三个镜头分别生成。

---

## 光影与质感控制

- **自然光**：`Soft golden hour sunlight`, `Overcast diffused lighting`, `Morning mist through blinds`
- **工作室/科技光**：`Studio key light with cool rim light`, `Neon blue and magenta cyber glow`, `Subtle screen reflection on face`
- **电影级镜头质感**：`Shot on 35mm lens, anamorphic bokeh, shallow depth of field, f/1.8, fine film grain`

---

## 负向提示词沉淀

常用的基础通用负向词集合：
`blurry, distorted anatomy, morphing limbs, extra fingers, floating objects, jitter, flicker, low resolution, bad compression artifacts, watermark, text glitches, sudden jump cut, warped geometry.`
