# Android UI 规则

## 组件与页面结构

- 优先复用项目已有 Base、组件、样式、颜色、字号、间距、drawable、Adapter 和弹窗封装。
- Activity/Fragment 负责 UI 绑定、导航和状态观察；复杂状态与业务调度放 ViewModel。
- Adapter 不承担复杂业务逻辑。
- Tab 页面默认使用 `MagicIndicator + ViewPager2 + FragmentStateAdapter + 子 Fragment`；已有同类封装时优先复用。
- 父 Fragment 负责 Tab、页面切换和公共状态；子 Fragment 负责各自列表、加载和空态。
- Dialog、Popup、BottomSheet、Drawer、图片预览和全屏弹窗优先检查项目封装与 XPopup。

## 布局与滚动

- 布局优先减少嵌套；ConstraintLayout 中避免无必要的固定高度。
- `RecyclerView`、`ViewPager2`、`ScrollView`、`NestedScrollView`、`HorizontalScrollView` 默认隐藏滚动条。
- 列表下方存在导航或操作区时使用 `clipToPadding=false`，确保最后一项能完整滚出遮挡区。
- 设计稿以项目基准宽度换算固定视觉尺寸，高度只用于区域比例参考。
- 根布局填满真实屏幕；ViewPager2、RecyclerView 和滚动内容使用约束填充剩余空间。

## Edge-to-edge 与 Insets

- 普通页面默认 Edge-to-edge + WindowInsets，不默认隐藏状态栏或导航栏。
- 顶部 Toolbar、Search、Header 明确 top inset owner；BottomNav、底部按钮、输入框明确 bottom inset owner。
- 同一个 top/bottom system inset 只能由一个 owner 消费，禁止父子 Fragment 重复叠加。
- BottomNav 的业务视觉高度与 navigation bar inset 分离。
- `fitsSystemWindows=true` 只用于经过说明的局部兼容，不作为整页兜底。
- 全屏隐藏系统栏只用于视频、图片预览、游戏、相机和全屏编辑器等场景，并提供退出与恢复策略。
- 全屏或贴边 Dialog/Popup/BottomSheet/Drawer 必须处理自身 Window 的系统栏、键盘和 display cutout。

## 文字与资源

- 标题、正文、简介、辅助说明、按钮、Tab 和列表文字使用项目已有 style、textAppearance 或 dimen。
- 文字必须明确单行省略、多行最大行数或自然换行。
- 检查长英文、中文、翻译文案、小屏幕和系统字体放大。
- 用户可见文案进入 string 资源；颜色、尺寸和 drawable 进入对应资源文件。
- 不随意重命名 XML id、资源名、style 名或 drawable 名。
- `setBackgroundColor()` 可能覆盖 shape drawable，修改背景前先检查现有实现。
