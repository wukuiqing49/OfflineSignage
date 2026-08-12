# Android 通用规则

## 修改边界

- 优先最小修改，不做无关重构，不删除现有功能。
- 未经明确要求，不修改 public API、包名、模块名、资源名、XML id 或外部调用方式。
- 新增能力前先检查项目已有模块、基础封装、公共组件和 WKQ 能力。
- 项目技术栈以 `.agents/config/project-profile.yml` 为概览，实际构建文件为最终事实来源。

## 生命周期与性能

- 页面销毁后不得继续回调 UI；ViewBinding 按生命周期释放。
- ViewModel 不持有 View、Activity 或需要页面生命周期的 Context。
- 避免主线程耗时、重复 inflate、重复图片加载、频繁对象分配和无界缓存。
- Bitmap、Surface、Player、Texture、MediaCodec、FFmpeg、OpenGL 等资源必须明确释放。

## 安全

- 不输出或提交真实密钥、证书、签名密码、token 和用户隐私数据。
- WebView JSBridge 只暴露必要方法，`addJavascriptInterface` 方法必须使用 `@JavascriptInterface`。
- WebView 销毁顺序必须覆盖停止加载、移除引用和释放资源。
- 反射、native、`/proc/self/maps` 和动态加载相关修改必须说明风险与验证范围。
- Shader 修改必须说明输入输出纹理类型，例如 `sampler2D` 或 `samplerExternalOES`。

## 兼容性

- 新功能先确认 `minSdk` 和系统版本差异，不通过降低 targetSdk 规避兼容问题。
- 错误必须有统一兜底，不允许静默失败。
- 涉及系统权限、存储、通知、后台任务或外部 Intent 时检查高版本行为变化。
