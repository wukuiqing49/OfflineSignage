# Figma 资源清单模板

| 节点名称 | Node ID | 类型 | Android 文件名 | 目标目录 | 使用位置 | 状态 | 是否必需 | 是否已落地 | 实际路径 | 处理建议 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 示例图标 | `1:2` | Vector/Icon | `figma_ic_example.xml` | `drawable` | Toolbar | normal | 是 | 否 |  | 从 Figma 导出，禁止静默替代 |

## 必填检查

- 底部导航图标是否包含 normal / selected。
- 顶部 Tab 图标是否包含 normal / selected。
- 工具栏和操作按钮图标是否完整。
- 图片是否区分普通资源、`drawable-nodpi` 或远程 URL。
- 缺失图标是否标注“MD 图标替代”并经过确认。
- `是否必需=是` 且 `是否已落地=是` 的资源必须能在 Android res 目录找到实际文件。
- 如果目标目录写 `drawable-nodpi 或 shape`，必须在实际路径列写清最终选择。
