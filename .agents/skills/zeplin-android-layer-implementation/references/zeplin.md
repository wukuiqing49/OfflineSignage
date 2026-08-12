# Zeplin 图层映射

## 读取顺序

先读取根画板，再读取直属子层、组件实例和所需子树。每个有意义的图层都记录以下字段：

- `type`、`name`、`componentName`、`sourceId`、`exportable`
- `rect` 和约束
- 文本 `content` 和 `textStyles`
- 填充、边框、透明度、圆角、阴影和混合模式
- 注释和 `designTokens`

忽略设计工具导出的设备外壳，仅将其作为系统栏参考。

## Android 映射

| Zeplin 图层 | Android 实现 |
| --- | --- |
| 有语义子层的 Group | 按布局行为选择 `ConstraintLayout`、`LinearLayout` 或可复用自定义 View |
| `componentName` 实例 | 新建前优先复用或适配仓库内对应组件 |
| 文本 | `TextView` + 字符串资源 + 文本样式 |
| 纯色、边框、圆角 | Shape drawable 或现有设计 Token |
| 渐变填充 | Gradient drawable，保留方向和色标 |
| 矢量图标/可导出 Shape | 导出 SVG；仅兼容 Android VectorDrawable 时转换，否则使用提供的位图 |
| 位图/Image 图层 | 下载后放入仓库规定的资源密度/位置，保持宽高比例 |
| 重复项 Group | 只有数据动态或集合可变时才使用 item 布局和 `RecyclerView` |
| 浮层/角标/装饰堆叠 | 约束至父容器；仅在受限的浮层内部使用绝对定位 |

仅将无法稳定地用原生文本、Shape 或布局表达的内容作为导出资源使用。禁止手绘复杂品牌图形。

## 导出资产流程

1. 在目标工程或系统临时目录下创建本次任务专用的 staging 目录，解析绝对路径并确认它位于预期目录内。`download_layer_asset` 只写入该目录，不直接覆盖 `res/` 文件。
2. 根据图层语义生成小写 snake_case 文件名并使用正确前缀，例如 `ic_certification_badge`、`img_identity_example`。去除路径分隔符、空格和非资源字符。
3. 搜索所有相关资源目录。内容相同则复用；同名但内容或来源不同则禁止静默覆盖，应使用稳定的 source ID/短内容哈希后缀，或在确认替换含义后明确覆盖并报告。
4. SVG 仅在路径、填充、描边、透明度等能力可由 Android VectorDrawable 正确表达，且转换后与 Zeplin 预览一致时，才转换为 VectorDrawable。含不兼容的滤镜、蒙版、复杂渐变、文字或效果时，改用 Zeplin 提供的 PNG/JPG 导出，不自行猜测或简化图形。
5. Zeplin 提供多密度位图时，根据导出倍率放入对应的 `drawable-mdpi`、`drawable-xhdpi` 等目录。只有素材明确要求保持原始像素、与设备密度无关且由布局负责缩放时才放入 `drawable-nodpi`；不得把未知倍率位图随意放入普通 `drawable`。
6. 在布局中通过约束、`adjustViewBounds`、`scaleType` 或 `dimensionRatio` 保持原始宽高比，不得拉伸 Logo、照片或导出图稿。
7. 资产成功集成并验证后清理 staging；下载、转换或集成失败时同样清理，并报告未落地资产及失败原因。

## 设计缺口

以下内容必须作为待确认问题或实现说明，禁止自行假设：

- 没有注释或导航目标的点击行为
- Zeplin 未提供的禁用、加载、错误、选中或按压状态
- 后端渠道标识、API 参数与 Token 校验流程
- 设计稿或仓库未提供的字体文件
- 图标按钮的无障碍标签和 `contentDescription`
