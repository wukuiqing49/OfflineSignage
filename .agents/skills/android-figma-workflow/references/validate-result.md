# 04 验证结果

## 目录

- [项目规范检查](#项目规范检查)
- [视觉还原检查](#视觉还原检查)
- [资源检查](#资源检查)
- [代码检查](#代码检查)
- [编译与运行](#编译与运行)
- [工作流脚本校验](#工作流脚本校验)
- [常见反模式搜索](#常见反模式搜索)
- [验收输出](#验收输出)

## 目标

确认页面不仅能编译，还要能稳定运行、符合设计、便于后续维护。

## 项目规范检查

- 是否已读取并遵守根目录 `AGENTS.md`。
- 是否已读取并遵守 `.agents/workflows/figma-code.md` 声明的 Rules 和 Skills。
- 是否已读取并遵守 `android-mapping.md`。
- 是否已按页面任务卡完成项目基类 / 组件审计，并实际采用项目已有 BaseActivity / BaseFragment / BaseVMFragment / BaseViewModel / Adapter / ViewPager 封装。
- 如果未采用项目基类，是否有明确证据说明不存在或不可用。
- 是否已按页面任务卡落地 Launcher / Splash / Host / Feature Fragment / 路由 Entry 链路。
- 是否优先最小修改，没有无关重构。
- 是否没有随意修改 public API、包名、模块名、资源名、XML id。
- 是否没有引入不必要的新依赖。
- 是否没有改变外部调用方式，或已明确说明原因。
- 是否保留了分析阶段确认的 Android 架构，没有在实现中降级成临时结构。
- 是否没有把主页、主导航、底部导航或 Tab 页面合并成单 Activity + 单 RecyclerView 数据切换。

## 视觉还原检查

- 页面结构是否和 Figma 一致。
- 间距、字号、颜色、圆角是否接近设计稿。
- 图片比例是否正确。
- 状态栏、导航栏、安全区域是否适配。
- Edge-to-edge / 沉浸式策略是否与页面类型匹配，普通页是否没有误隐藏系统栏。
- 顶部状态栏、底部虚拟导航栏、手势导航、三键导航是否都说明了处理方式。
- Figma 状态栏、手势条、虚拟导航栏、刘海/安全区是否只作为 Insets 参考，没有被误绘制成页面主体。
- 是否按设计基准输出并落实屏幕适配换算，且没有把 Figma Frame 宽高写死。
- 是否识别 Figma 逻辑屏幕宽度；390/393/402/414 逻辑宽或高清放大稿没有被误当普通 px。
- 是否按“宽度建基准，高度填充屏幕”实现：Figma 宽度用于 360dp/项目基准换算，Figma 高度只用于区域参考。
- 根布局、主页容器、ViewPager2、RecyclerView、ScrollView 是否使用 `match_parent` 或 `0dp + 约束` 填充真实屏幕/剩余高度。
- 是否没有把 Figma Frame 高度、逻辑屏幕高度或首屏内容总高写成主内容固定高度。
- Header/Toolbar/BottomNav 等固定视觉块与 RecyclerView/ViewPager2 等弹性内容区是否分工清楚。
- 是否输出并落实关键尺寸换算表。
- 是否把关键尺寸换算表中的 `dimen` 落到资源文件；实际 dp/sp 与任务卡目标值是否在允许误差内。
- 是否输出并落实 Insets owner 合同；同一个 top/bottom inset 是否只被一个 owner 消费。
- Header/Toolbar 是否没有把 Figma 已含状态栏预览的总高度再次叠加 `statusBars.top`。
- BottomNav、底部按钮或输入框是否拆分了业务视觉高度和运行时 bottom inset，没有把 Figma 手势条/虚拟导航栏预览算进视觉高度。
- BottomNav 是否没有被三键导航或手势导航改变业务视觉高度；navigation inset 只作为安全区。
- 是否已逐项核对运行时视觉风险清单：顶部空白、底部系统栏、可滚动区域、FAB/BottomNav、小屏可见内容。
- 列表列宽、间距、图片比例是否能随屏幕宽度变化。
- 小屏、大屏、横竖屏是否有明显错位。
- 系统字体放大后文字是否重叠或被截断。
- 长文案、多语言是否破坏布局。
- 普通 TextView 是否避免固定 dp 宽高。
- 顶部 Tab / 底部主导航 / 同级页面切换是否使用 Tab/BottomNav + ViewPager2 + FragmentStateAdapter + 子 Fragment。
- 重复 item 是否抽成 RecyclerView，而不是静态复制。
- 底部导航 / 顶部 Tab / 工具栏是否保留了 Figma 图标。
- normal / selected / pressed 状态是否与 Figma 一致或有明确差异说明。
- 是否按 Figma 图层结构实现页面，而不是使用整页截图、区域截图或截图切片作为页面主体。
- Figma 截图是否仅用于视觉参考和验收对比。

## 资源检查

- 颜色是否放入 `colors.xml`。
- 文案是否放入 `strings.xml`。
- 尺寸是否放入 `dimens.xml`。
- shape / selector 命名是否清晰。
- 图片是否放到正确资源模块。
- 普通图片是否优先 WebP，复杂图是否允许 PNG。
- 图标是否优先 VectorDrawable。
- 是否存在 `asset_manifest.md` 或页面任务卡中的资源清单。
- 图片、SVG、图标是否都能在本地资源目录找到。
- 底部导航图标是否包含完整 normal / selected 状态。
- 顶部 Tab 图标是否包含完整 normal / selected 状态。
- 工具栏和操作按钮图标是否完整。
- 是否存在用通用图标、文字按钮或占位图替代 Figma 图标的情况。
- 使用 Material Design 图标替代缺失 Figma 图标时，是否已在资源清单和差异说明中明确标注“MD 图标替代”。
- 是否存在未说明的图标替代、图片替代或截图替代。
- 是否还有无必要的硬编码颜色、文案、尺寸。

## 代码检查

- Activity / Fragment 是否只负责 UI 绑定和交互分发。
- 页面是否优先使用目标项目已有页面基类；如果没有基类，是否使用 AndroidX 标准类并说明。
- 单列表页面是否优先使用目标项目已有列表页封装。
- Adapter 是否优先使用目标项目已有 Adapter 封装。
- ViewPager2 是否优先搭配 Fragment，而不是一个页面里硬切多套布局。
- ViewModel 是否只负责 UI 状态和业务调度。
- Adapter 是否只做视图绑定。
- 是否存在主线程耗时操作。
- 页面销毁后是否可能继续回调 UI。
- 是否存在 Bitmap、Surface、Player、WebView 泄漏风险。
- 错误态、空态、loading 是否完整。
- 是否破坏现有路由和外部调用方式。
- Dialog、Popup、BottomSheet、Drawer、全屏浮层是否处理 Window Insets、软键盘、刘海/挖孔和返回恢复。
- RecyclerView/ScrollView 最后一项是否可能被底部虚拟导航栏、BottomNav 或底部操作按钮遮挡。

## 编译与运行

建议至少执行：

```powershell
./gradlew assembleDebug
```

如果只改某个模块，优先执行对应模块构建：

```powershell
./gradlew :目标模块路径:assembleDebug
```

## 工作流脚本校验

生成后建议先运行 Figma 输出校验脚本。多页面任务优先传页面级资源清单，例如 `asset_manifest_home.md`：

```powershell
python .agents\skills\android-figma-workflow\scripts\validate_figma_output.py --module-src <目标模块源码目录> --module-res <目标模块资源目录> --asset-manifest .ai-work\figma\output\asset_manifest_<page>.md
```

主页容器、底部主导航或顶部 Tab 页面必须启用强校验：

```powershell
python .agents\skills\android-figma-workflow\scripts\validate_figma_output.py `
  --module-src <app源码目录> --module-src <feature源码目录> `
  --module-res <app资源目录> --module-res <feature资源目录> `
  --asset-manifest .ai-work\figma\output\asset_manifest_<page>.md `
  --analysis-report .ai-work\figma\output\page_task_<page>.md `
  --figma-output-dir .ai-work\figma\output `
  --require-screen-adaptation `
  --require-pager-navigation
```

强校验会要求输出目录存在本地 Figma 位图截图、图层报告、规范化报告和 Node 资源索引，并检查任务卡的降级声明是否与资源清单一致。脚本也会读取 `--analysis-report` 判断系统栏策略是否已落地。代码或 XML 中命中 `WindowInsets` / system bar / Edge-to-edge 相关 API 时：

- 页面任务卡已写清 `Edge-to-edge/沉浸式`、顶部状态栏、底部虚拟导航栏、Dialog/Popup、父子 Fragment Insets 分工时，不再输出“需要人工确认策略”的 warning。
- 页面任务卡缺少上述任一类策略时，继续输出 warning，并提示缺失项。

如果目标项目没有统一页面基类或 Adapter 封装，可以显式允许标准实现，避免只报提醒：

```powershell
python .agents\skills\android-figma-workflow\scripts\validate_figma_output.py --module-src <目标模块源码目录> --module-res <目标模块资源目录> --asset-manifest .ai-work\figma\output\asset_manifest.md --allow-standard-adapter --allow-raw-fragment
```

## 常见反模式搜索

可以用以下命令辅助检查生成代码：

```powershell
rg -n 'RecyclerView\.Adapter|ListAdapter<' <目标模块源码目录>
rg -n 'AppCompatActivity|: Fragment\(' <目标模块源码目录>
rg -n '<TextView' <目标模块资源目录>\layout
rg -n 'android:layout_width="[0-9]+dp"|android:layout_height="[0-9]+dp"' <目标模块资源目录>\layout
rg -n 'placeholder|TODO|drawableTop|icon="@null"|MaterialButton' <目标模块源码目录> <目标模块资源目录>
rg -n 'screenshot|screen_shot|snapshot|page_capture|figma_reference' <目标模块源码目录> <目标模块资源目录> <公共资源目录>
rg -n 'fitsSystemWindows="true"|SYSTEM_UI_FLAG|hide\(|systemBars\(|decorFitsSystemWindows|setStatusBarColor|setNavigationBarColor' <目标模块源码目录> <目标模块资源目录>
rg -n 'navigationBars\(\).*bottom|systemBars\(\).*bottom|updatePadding\(bottom|paddingBottom' <目标模块源码目录> <目标模块资源目录>
```

命中不一定都是错误，但必须人工确认是否属于允许例外。

## 验收输出

```text
验证结果：

1. 编译：
2. 页面运行：
3. Figma 对比：
4. 多屏适配：
5. 沉浸式 / Edge-to-edge / Insets：
6. Dialog / Popup / BottomSheet：
7. 国际化：
8. 空态 / 错误态：
9. 架构门禁：
10. 资源门禁：
11. 缺失资源：
12. 项目基类 / 组件审计：
13. 页面入口链路：
14. 运行时视觉风险：
15. 遗留问题：
```
