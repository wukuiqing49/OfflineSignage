# Android 代码规则

- 固定视觉关系的根布局默认使用 `ConstraintLayout`；使用 `0dp` 的 match constraints，避免固定父容器尺寸计算。
- 页面专属资源放入所属模块；确认仓库现有资源归属后，才将真正共享资源放入共享资源模块。
- 文本使用 `sp`；重复字体使用 style 或 font resource。只打包仓库已有，或由 Zeplin/用户提供且使用权清晰的字体文件；指定字体缺失时报告差异，不得自行下载、伪造字体或静默选择近似字体。
- 通过约束或 `dimensionRatio` 保持图片比例；禁止拉伸 Logo 或导出的图稿。
- 只有现有流程、设计注释或明确需求要求时，才建模加载、错误和启用状态。禁止添加空 ViewModel 或占位网络调用。
- 设计期的 Group 没有语义或布局作用时，不应保留在运行时代码中。
- 有意义的图标操作使用 `contentDescription`；纯装饰图片标为不参与无障碍。
- 优先使用原生 selector、Shape drawable 和颜色资源，避免运行时构造 Drawable。
