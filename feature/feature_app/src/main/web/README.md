# 离线控制台源码

`index.html` 按显式顺序包含 JS/CSS；不依赖 npm、CDN 或在线构建服务。APK 仍使用 `res/raw/web_console.html`，不要直接修改生成文件。

```powershell
python tools/build_web_console.py
python tools/build_web_console.py --check
node --test tools/test_publish_workflow.cjs
```

Gradle `verifyOfflineConsole` 随 `preBuild` 执行，拒绝源码与资源不一致的构建。脚本保留普通 script 的执行顺序与共享作用域，避免此次迁移同时改变加载模型。

- `publish-workflow.js`：无 DOM 的发布阶段、目标快照及失败重试。
- `publish-feedback.js`：逐设备结果、重试入口、预览与部署说明。
- `console.js`：基础状态、请求和既有页面行为。
- `publishing-ui.js`：发布方式与 H5 编辑。
- `device-groups.js`、`device-filters.js`：分组及设备筛选。
- `scene-editor.js`、`schedules.js`：场景编辑预览及排期。
- `project-export.js`、`project-import.js`：离线项目导出和导入。
- `content-filters.js`、`resource-search.js`、`design-examples.js`：素材筛选、搜索与模板示例。

新的发布任务与草稿只保留在当前页面内存中，不把配对凭据或用户内容写入额外持久存储。刷新/关闭页面时有未完成编辑或发布则触发浏览器离开提示；浏览器可能要求先有用户交互才展示提示。

控制台当前中英文完整，其他语言按既有机制回退英文。命令成功仅表示设备接受命令，不能替代实际画面或硬件解码验收。
