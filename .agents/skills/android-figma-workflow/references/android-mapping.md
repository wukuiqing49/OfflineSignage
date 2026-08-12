# 05 Figma 到 Android 架构映射规则

本文件用于解决 Figma 生成代码时常见的静态还原问题。实现页面前必须先判断页面类型，再选择目标项目已有基类和组件；不要假设每个项目都有同名基础模块或基类。通用约束来自 `.agents/rules/architecture.md` 和 `.agents/rules/ui.md`，本文件只补充 Figma 图层到 Android 架构的映射。

## 目录

- [基类选择原则](#基类选择原则)
- [页面结构映射](#页面结构映射)
- [TextView 布局硬规则](#textview-布局硬规则)
- [列表实现硬规则](#列表实现硬规则)
- [生成前必须判断](#生成前必须判断)

## 基类选择原则

实现前先在目标项目中查找：

- 已有 Activity / Fragment / ViewModel 基类。
- 已有列表页、Adapter、多类型列表、ViewPager2 Adapter 封装。
- 已有 Tab、Dialog、Popup、图片加载、路由、资源命名规范。

如果存在项目基类，优先复用；如果不存在，不要虚构目标项目没有的 `Base*` 页面、列表或 Adapter 类型，改用 AndroidX / RecyclerView 标准实现并在方案中说明。

### 基类审计硬规则

查找范围不能只限于当前 feature 模块源码，必须覆盖：

- `app`、目标 feature 模块、相关 `core/*` 模块。
- `settings.gradle`、各模块 `build.gradle`、`gradle/libs.versions.toml` 中声明的基础库依赖。
- 通过 `api` / `implementation` 暴露给目标模块的 AAR、jar 或源码依赖。
- 项目已有相似页面、Adapter、ViewPager2、Insets 工具的实际用法。

输出到页面任务卡时必须写明：

```text
已扫描模块：
Activity 基类：
Fragment 基类：
ViewModel 基类：
Adapter 基类：
FragmentStateAdapter / ViewPager 封装：
最终采用结论：
未采用原因：
```

规则：

- 如果基础能力来自 AAR 或缓存依赖，必须通过可用方式确认类名、泛型和构造签名；不要因为源码目录没有类就降级。
- 如果项目基类存在但不能继承，例如 final、构造参数不匹配或生命周期模型冲突，必须写明证据。
- 使用 AndroidX 标准 Fragment、ViewModel、RecyclerView.Adapter 前，必须能在任务卡中看到“不存在可用项目基类”或“不可用原因”。
- 主页 Tab / BottomNav 的 Adapter 如果项目封装不可继承但可组合使用，优先组合使用，不能因为 final 就整体降级。

## 页面结构映射

### 主页容器 / 底部主导航

适用：首页、关注、消息、我的、发布入口等同级主页面切换。

必须优先使用：

- 容器：`Activity` 或主页 `Fragment` 只持有底部导航、`ViewPager2` 和公共入口。
- 内容：`ViewPager2`。
- Adapter：按 `android-ui-workflow` 使用 `FragmentStateAdapter`。
- 子页面：每个底部 Tab 对应一个 Fragment；中间发布/直播按钮如果不是页面 Tab，应作为独立点击入口或弹窗入口。
- 子页面列表：各自使用项目已有列表页封装或标准 RecyclerView。

禁止：

- 只画静态底部导航但不接入页面切换。
- 用一个 Activity + 一个 RecyclerView 根据 tabType 切换多个主页面。
- 把所有主页面内容写在一个 XML 或一个 Fragment 中。

例外：用户明确要求只还原当前 Frame 的静态视觉稿，或当前 Frame 已被明确裁剪为“单个子页面内容 Fragment”。例外必须写入门禁记录。

### 普通展示页

适用：个人资料、关于我们、说明页、简单结果页。

- Activity / Fragment：优先使用项目已有基类；没有时使用 AndroidX 标准类。
- XML：`ConstraintLayout` 为主，必要时使用 `NestedScrollView`

### 表单页

适用：登录、认证、编辑资料、提现申请。

- Fragment：优先使用项目已有带 ViewModel 的页面基类；没有时使用标准 Fragment + ViewModel。
- ViewModel：管理输入状态、按钮状态、提交结果
- XML：避免固定高度，输入项使用约束布局和 `wrap_content`

### 单列表页

适用：消息列表、关注列表、收益记录、搜索结果。

- Fragment：优先使用项目已有列表页基类或列表封装；没有时使用标准 Fragment。
- Adapter：优先使用项目已有 Adapter 基类；没有时使用标准 `ListAdapter` / `RecyclerView.Adapter`。
- XML：优先复用项目已有列表容器；没有时使用 `RecyclerView`。

### 顶部 Header + 下方列表

适用：顶部统计、筛选、说明，下方是单个列表。

- 容器：优先使用项目已有带 ViewModel 的页面基类；没有时使用标准 Fragment + ViewModel。
- Header：写在容器 XML 或单独 include
- 列表：优先拆成子 Fragment，并使用项目已有列表页封装或标准 RecyclerView。
- 禁止在一个 XML 中静态写多份 item 来模拟列表

### 顶部 Tab + 下方列表

适用：榜单、订单、记录、消息分类、搜索分类、关注分类。

必须优先使用：

- 容器：优先使用项目已有页面基类；没有时使用 AndroidX 标准类。
- Tab：项目已有 Tab 组件、MagicIndicator、TabLayout 或现有封装
- 内容：`ViewPager2`
- 子页面：每个 Tab 对应一个 Fragment
- 子列表：使用项目已有列表页封装或标准 RecyclerView。
- Adapter：使用项目已有 Adapter 基类或标准 Adapter。

禁止把多个 Tab 的内容全部写进一个静态 XML。

如果 Figma 中的 Tab 同时包含图标、角标、selected/normal 状态，资源清单必须列出每个状态；缺少状态资源时阻塞完整实现，除非用户确认使用临时降级。

### 多 Tab 且列表 UI 相同

- 共用一个 ListFragment。
- 通过 `arguments` 传入 `tabType`、`categoryId` 或 `rankType`。
- Adapter 复用同一个项目 Adapter 基类或标准 Adapter。

### 多 Tab 但列表 UI 差异大

- 拆多个 Fragment。
- 不要在一个 Adapter 中堆大量 `when` 分支。
- 公共逻辑抽到 ViewModel、Mapper 或 UI model。

### 弹窗 / 底部面板

适用：确认弹窗、更多操作、充值面板、观众列表。

- 优先复用项目已有 Dialog / Popup 基类或封装。
- 列表型弹窗仍优先使用 `RecyclerView + 项目已有 Adapter 封装`；没有封装时使用标准 Adapter。
- 注意生命周期和 dismiss 后回调安全。

## TextView 布局硬规则

Figma 的 Text 图层包含固定 `width` / `height`，但 Android `TextView` 不能直接照搬。

### 默认规则

- 普通 `TextView` 默认使用 `android:layout_width="wrap_content"`。
- 普通 `TextView` 默认使用 `android:layout_height="wrap_content"`。
- 在 ConstraintLayout 中需要占满剩余宽度时，使用 `layout_width="0dp"` 加左右约束。
- 不允许把 Figma Text 图层宽高直接转成 `TextView` 固定 dp 宽高。

### 允许固定宽高的例外

只有以下控件可以考虑固定宽高：

- 按钮
- 角标
- 徽章
- 倒计时数字格
- 固定尺寸图标
- 头像
- Tab 指示器
- 设计明确要求固定视觉尺寸的计数器

例外必须在实现说明中说明原因。

### 多语言和字体缩放

- 文案必须考虑中文、英文、土耳其语长度差异。
- 长文本使用 `maxLines`、`ellipsize` 或自适应换行。
- 禁止用固定高度模拟文字行高。
- 行高优先通过 `textSize`、`includeFontPadding`、`lineSpacingExtra`、`minHeight` 调整。

## 列表实现硬规则

- Figma 中重复出现 2 个及以上同结构 item 时，优先识别为列表。
- 列表必须使用 RecyclerView。
- 列表 Fragment 必须优先复用项目已有列表页封装；没有封装时使用标准 Fragment + RecyclerView。
- Adapter 必须优先复用项目已有 Adapter 封装；没有封装时使用标准 Adapter。
- 禁止在主页面 XML 里复制多个 item 布局模拟列表。
- 禁止 Adapter 直接发起网络请求或处理复杂业务。

## 生成前必须判断

生成代码前先回答：

1. 当前页面是普通页、表单页、单列表页、Header + 列表页，还是 Tab + ViewPager2 页？
2. 是否存在重复 item，可以抽成 RecyclerView？
3. 是否存在项目列表页基类或列表封装可复用？
4. 是否需要 `ViewPager2 + Fragment`？
5. 如果需要 ViewPager2，是否已生成或规划 `FragmentStateAdapter` 和每个子 Fragment？
6. 是否存在 TextView 固定宽高风险？
7. 是否可以复用项目已有 Adapter / Fragment / ViewModel？
8. 是否已输出屏幕适配方案：设计基准、换算比例、Insets、列表列宽、图片 ratio 和固定尺寸例外？
9. 是否已扫描 `core:core_base` / AAR / Gradle 依赖并确认项目基类可用性？
10. 是否已明确 Launcher / Host / Feature 页面入口链路？
11. 是否已明确 Figma 系统栏预览和 Android 运行时 Insets 的拆分策略？
