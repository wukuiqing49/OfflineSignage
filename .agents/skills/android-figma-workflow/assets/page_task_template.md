# Figma 页面任务卡

## 基本信息

- 页面名称：
- Figma 文件：
- Figma 链接：
- node id：
- Frame 宽度：
- Frame 高度：
- Figma 逻辑屏幕宽度：
- Figma 逻辑屏幕高度：
- 是否高清放大稿：
- 高清放大倍率：
- Android 基准宽度：
- 缩放比例：
- 页面类型：
- 所属模块：
- 公共资源模块：
- 页面路由：
- Launcher Activity：
- Host Activity / Fragment：
- 目标 Feature 页面：
- 创建 / 路由入口：
- 是否替换旧实现：
- 旧实现替换范围：
- 负责人：
- 日期：

## 设计资源

- 截图：
- JSON：
- 图层报告：
- 规范化报告：
- 图片目录：
- 提取报告：
- 资源清单：
- 页面级资源清单：

## 页面分析

- 页面结构：
- 关键图层：
- 可复用组件：
- 列表 / Adapter：
- ViewModel / UIState：
- 空态 / 错误态：
- 交互说明：

## 项目基类 / 组件审计

- 已扫描模块：
- Gradle / AAR / 源码依赖审计：
- Activity 基类：
- Fragment 基类：
- ViewModel 基类：
- Adapter 基类：
- FragmentStateAdapter / ViewPager 封装：
- Insets / Edge-to-edge 工具：
- 图片加载 / 图标 / 资源工具：
- 最终采用结论：
- 未采用项目基类的原因：

## 页面入口链路合同

| 层级 | Android 落点 | 职责 | 是否新增/替换 | 说明 |
| --- | --- | --- | --- | --- |
| Launcher |  |  |  |  |
| Splash |  |  |  |  |
| Host |  |  |  |  |
| Feature 页面 |  |  |  |  |
| 子 Fragment / Tab |  |  |  |  |
| 路由 / Entry |  |  |  |  |

## 屏幕适配方案

- Figma Frame：
- Figma 逻辑屏幕：
- Android 基准宽度：
- 换算公式：
- 换算比例：
- 宽度基准与高度填充：
- 状态栏 / 顶部安全区：
- 底部虚拟导航栏 / 手势条：
- 沉浸式 / Edge-to-edge：
- Figma 系统栏图层处理：
- 根布局自适应策略：
- 列表列宽 / 间距策略：
- 图片比例策略：
- 固定尺寸例外：
- 视觉高度拆分：
- Insets owner：
- 小屏 / 字体放大风险：

## 宽度基准与高度填充合同

| 项 | 合同 |
| --- | --- |
| 宽度基准 |  |
| 固定视觉块 |  |
| 弹性内容区 |  |
| Figma 高度用途 |  |
| Insets 对高度的影响 |  |

- 根布局填充策略：
- 主内容填充策略：
- 列表 / ViewPager2 填充策略：
- 禁止写死的高度：
- 三键导航 / 手势导航差异：

## 关键图层 Bounds 表

| 用途 | Figma 图层 | Node ID | x | y | width | height | Android 归属 |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| 页面根 |  |  |  |  |  |  | root |
| 状态栏/安全区 |  |  |  |  |  |  | Insets |
| 顶部 Header/Toolbar/Search |  |  |  |  |  |  | XML |
| 主内容列表 |  |  |  |  |  |  | RecyclerView |
| 底部导航/底部操作区 |  |  |  |  |  |  | Insets + XML |

## 关键尺寸换算表

| 用途 | Figma px | 逻辑 px | Android dp/sp | dimen/style 名 | 固定尺寸例外 | 说明 |
| --- | ---: | ---: | ---: | --- | --- | --- |
| 页面左右边距 |  |  |  |  | 否 |  |
| 顶部栏/搜索框高度 |  |  |  |  | 否 |  |
| 状态栏 Insets |  |  |  |  | 是 | 来自系统 Insets |
| 底部虚拟导航栏 Insets |  |  |  |  | 是 | 来自系统 Insets |
| BottomNav 高度 |  |  |  |  | 是 |  |
| Tab 图标 |  |  |  |  | 是 |  |
| 中间按钮 |  |  |  |  | 是 |  |
| 列表列间距 |  |  |  |  | 否 |  |
| 卡片比例 |  |  |  |  | 否 | 使用 ratio |
| 主要字号 |  |  |  |  | 否 | 使用 sp/style |

## 视觉高度拆分表

| 区域 | Figma 总高度 | 业务视觉高度 dp | 系统预览/安全区高度 | Android owner | 是否绘制 | 说明 |
| --- | ---: | ---: | ---: | --- | --- | --- |
| 状态栏 |  |  | 系统 Insets |  | 否 |  |
| BottomNav / 底部操作区 |  |  | navigationBars |  | 只绘制业务视觉区 |  |

## Insets owner 合同

| Inset 类型 | Android owner | 消费方式 | 子层是否可再次消费 | 说明 |
| --- | --- | --- | --- | --- |
| statusBars.top |  | paddingTop / marginTop / 不消费 |  |  |
| navigationBars.bottom |  | paddingBottom / marginBottom / 不消费 |  |  |

## 运行时视觉风险清单

| 风险项 | Figma 依据 | Android 处理 | 验收方法 | 失败时优先回查 |
| --- | --- | --- | --- | --- |
| 顶部 status inset 重复计算 |  |  | 真机截图 / 截图对比 | Header 总高是否含状态栏预览 |
| 底部 navigation inset 重复计算 |  |  | 手势导航 + 三键导航 | BottomNav 视觉高与安全区是否拆分 |
| 可滚动区域被固定高度挤压 |  |  | 小屏 / 大字体 | ViewPager2/RecyclerView 是否 0dp 约束填充 |
| FAB 与 BottomNav 重叠或漂移 |  |  | 真机截图 | FAB 是否约束到底部视觉区上方 |
| 真机高度小于 Figma Frame |  |  | 小屏设备 | 首屏内容是否可滚动，固定区是否过高 |

## 沉浸式 / 弹窗适配

- 顶部状态栏：
- 底部虚拟导航栏：
- 手势导航 / 三键导航：
- 全屏隐藏系统栏：
- Dialog / Popup / BottomSheet：
- 刘海 / 挖孔 / display cutout：
- 父子 Fragment Insets 分工：

## UI 工作流对齐

- 页面结构：
- Tab / ViewPager2：
- RecyclerView / Scroll：
- 文字字号 / 省略：
- Insets / Edge-to-edge：
- Dialog / Popup / BottomSheet：
- 资源样式：
- 验证门禁：

## 图层到 Android 映射

| Figma 图层 | Node ID | Android 文件 | 类型 | 说明 |
| --- | --- | --- | --- | --- |
|  |  |  | XML / item / drawable / string / color / dimen |  |

## 资源清单

- 图片：
- 图标：
- 底部导航图标：
  - normal：
  - selected：
- 顶部 Tab 图标：
  - normal：
  - selected：
- 工具栏图标：
- 操作按钮图标：
- 缺失资源：
- 降级替代说明：
- 颜色：
- 文案：
- 尺寸：
- shape / selector：

## 门禁记录

- 架构门禁是否通过：
- 项目基类 / 组件审计是否通过：
- 是否已扫描 core:core_base / AAR / 依赖基类：
- 是否已明确 Activity / Fragment / ViewModel / Adapter 基类采用结论：
- 页面入口链路合同是否通过：
- Launcher / Host / Feature 职责是否明确：
- 图层报告是否已读取：
- 规范化报告是否已读取：
- 是否已识别 Figma 逻辑屏幕：
- 是否已输出关键尺寸换算表：
- 是否已输出视觉高度拆分表：
- 是否已输出运行时视觉风险清单：
- 是否主页容器 / 主导航页：
- 是否需要 ViewPager2 + Fragment：
- 是否需要 FragmentStateAdapter：
- 是否禁止单 Activity + 单 RecyclerView 数据切换：
- 是否允许降级：
- 资源门禁是否通过：
- 资源清单是否已读取：
- 是否存在缺失导航 / Tab / 工具栏图标：
- 用户确认的降级方案：

## 实现记录

- 新增文件：
- 修改文件：
- 新增资源：
- 依赖变更：
- 混淆变更：
- 路由变更：

## 验证结果

- 编译：
- 运行：
- 视觉对比：
- 多屏适配：
- 沉浸式 / Edge-to-edge / Insets：
- Dialog / Popup / BottomSheet：
- 国际化：
- 遗留问题：
