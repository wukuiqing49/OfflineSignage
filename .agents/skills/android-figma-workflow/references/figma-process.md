# Figma Process 主执行模板

## 目录

- [目标](#目标)
- [前置条件](#前置条件)
- [执行命令](#执行命令)
- [必须输出](#必须输出)
- [施工图要求](#施工图要求)
- [区域尺寸预算](#区域尺寸预算)
- [宽度基准与高度填充合同](#宽度基准与高度填充合同)
- [尺寸合同](#尺寸合同)
- [Insets 合同](#insets-合同)
- [输出结束条件](#输出结束条件)

## 目标

只分析当前 Figma Frame，不生成代码。输出结果必须像施工图，能直接指导 `figma-code` 阶段重建页面。

## 前置条件

先完成 `.agents/workflows/figma-process.md` 的加载步骤，再按当前 Frame 读取 `pull-figma.md`、`normalize-figma.md`、`android-mapping.md` 和结构化图层、截图、资源清单。

## 执行命令

通过 Figma MCP 拉取当前选中 Frame：

1. 获取结构化图层。
2. 获取截图。
3. 获取资源/图片清单。
4. 写入 `.ai-work/figma/output/`。
5. 生成或更新 `figma_layer_report.md`、`asset_manifest_<page>.md`、`figma_normalize_report.md`、`page_task_<page>.md`。

如果没有结构化图层、本地位图截图、`figma_asset_index.json` 或资源清单，停止，不要生成代码。聊天中的内联截图不算可复现验收输入。

## 必须输出

按 `assets/page_task_template.md` 输出页面任务卡，并至少包含：

1. 页面结构分析
2. Activity / Fragment / ViewModel / Adapter 拆分
3. 项目基类 / 组件审计结果：必须说明已扫描哪些模块、发现哪些 BaseActivity / BaseFragment / BaseVMFragment / BaseViewModel / Adapter / FragmentStateAdapter 封装，以及最终采用或不采用原因
4. 页面入口链路合同：必须声明 Launcher Activity、Host Activity/Fragment、目标 Feature Fragment、路由/创建方法、是否替换旧页面
5. Tab / BottomNav / ViewPager2 / FragmentStateAdapter 判断
6. RecyclerView / item / 列宽 / 间距方案
7. Figma 图层到 Android 文件、XML id、dimen、drawable、string 的映射
8. 图片资源清单和缺失资源
9. colors.xml / strings.xml / dimens.xml 规划
10. Figma 逻辑屏幕宽度、Android 基准宽度、换算公式
11. 关键尺寸换算表
12. 宽度基准与高度填充合同
13. 视觉高度拆分表
14. Insets owner 合同
15. 区域尺寸预算
16. 屏幕适配、沉浸式、Dialog/Popup 适配方案
17. 运行时视觉风险清单：必须覆盖顶部 status inset、底部 navigation inset、可滚动区域、FAB 与 BottomNav、真机高度小于 Figma Frame 高度时的降级策略
18. 生成前阻塞项

## 施工图要求

图层映射必须具体到 Android 落点，不要只写泛泛描述。

示例：

```text
搜索框 Groups 181:1555
-> fragment_home.xml#search_container
-> height: home_search_height = 30dp
-> radius: home_search_radius = 15dp
-> icon: ic_home_search
-> text: home_search_hint
```

## 区域尺寸预算

必须把首屏拆成可验证的区域预算：

```text
状态栏：系统 inset，不绘制
Header/Search：topInset + 39dp
榜单区域：69dp
分类区域：约 52dp
直播列表起点：约 x dp
BottomNav 视觉区：xx dp
底部系统栏：spacer/inset，不属于页面视觉高度
```

没有区域预算时，不允许进入代码生成。

## 宽度基准与高度填充合同

必须把 Figma 屏幕归一化写成以下模型：

```text
Figma 逻辑宽度 -> Android 360dp/项目基准宽度 -> 固定视觉尺寸换算
Figma Frame 高度 -> 只作为区域比例参考，不作为 Android 根布局或首屏高度
Android 真实屏幕 -> match_parent + 约束填充
```

要求：

- 根布局、主页容器、ViewPager2 宿主必须 `match_parent` 或 `0dp + 约束` 填充父容器。
- Header、Toolbar、BottomNav、按钮、图标、角标等固定视觉块按 360dp/项目基准宽度换算。
- RecyclerView、ViewPager2、ScrollView、内容容器必须作为弹性区吃掉剩余高度。
- 禁止把 Figma Frame 高度、逻辑屏幕高度、首屏内容总高等比写成 Android `layout_height`。
- Insets 只改变安全区 padding/spacer，不反向压缩或放大业务视觉尺寸。
- 如果设备是三键导航，`navigationBars.bottom` 仍是运行时安全区，不属于 Figma 业务视觉高度。

没有该合同，或合同没有说明固定区/弹性区/Insets 关系时，不允许进入代码生成。

## 尺寸合同

- 关键尺寸表中的 `dimen/style 名` 是生成合同。
- 每个关键 `dimen` 必须有目标 dp/sp、允许误差和例外原因。
- 禁止为了触控面积、经验值或安全区静默放大视觉尺寸。
- 触控热区只能通过父容器、padding、foreground、hitRect 或 spacer 处理，不得改大图标、按钮图、搜索框、BottomNav 视觉尺寸。

## Insets 合同

- 同一个 `statusBars.top` / `navigationBars.bottom` / `systemBars.bottom` 只能有一个 owner。
- BottomNav + ViewPager2 页面必须拆分 BottomNav 视觉区和 bottom inset。
- 如果 BottomNav 或 spacer 消费 `navigationBars.bottom`，子 Fragment 的 RecyclerView 不再重复消费 bottom system inset。
- 如果 Figma Header 总高度已经包含状态栏预览，实现时必须拆成 `statusBars.top + Header 业务视觉高度`，禁止再用 Figma 总高度叠加 `statusBars.top`。
- 如果 Figma BottomNav 总高度已经包含手势条或三键导航预览，实现时必须拆成 `BottomNav 业务视觉高度 + navigationBars.bottom`，禁止把预览安全区折算进业务 `dimen`。

## 架构与入口合同

分析阶段必须先完成项目基类和入口链路审计，再输出任务卡：

```text
已扫描模块：app、feature/feature_app、core/core_base、相关依赖/AAR
页面基类：BaseActivity / BaseFragment / BaseVMFragment / BaseViewModel / Adapter 基类
采用结果：
Launcher：
Host：
目标 Fragment：
创建/路由入口：
旧页面替换范围：
```

要求：

- 如果 `core:core_base` 或其他共享模块暴露了可用基类，任务卡必须写明类名和泛型签名，代码阶段必须优先采用。
- 如果基类来自 AAR 或外部依赖，分析阶段也必须通过 Gradle 依赖、缓存 AAR、源码 jar 或反编译签名确认，不得因为源码目录没有类就判定“不存在”。
- 如果不用项目基类，必须写明阻塞原因，例如泛型不匹配、final、生命周期冲突或用户明确要求。
- 启动页、新入口、主页面容器和 feature 页面必须拆清职责，不允许用含糊的“MainActivity 展示页面”代替明确链路。

## 输出结束条件

只输出分析、Android 文件映射和门禁结论。等待用户确认后，才能进入 `figma-code`。
