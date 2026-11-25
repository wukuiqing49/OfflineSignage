# CaptionMeta 宿主桥参考

仅用于理解现有实现，不能作为其他宿主的默认协议。协议细节须先按目标版本源码核实，参见 [版本追溯](configuration.md#版本追溯记录)。

## CaptionMeta 已实现的示例协议

以下为 **CaptionMeta 宿主协议，不是 DevHub SDK API**。其他 App 先明确桥名、能力与原生实现，不能复制名称就宣称支持登录/会员/兑换。实际示例源码：宿主 CampaignBridgeRequest、CampaignNativeActions、CampaignWeb Activity；DevHub CMS 的 `cms/public/captionmeta-bridge.js` 与 `captionmeta-bridge-test.html`。

请求 `window.CaptionMeta.postMessage(JSON.stringify({id,action,params}))`，响应由 CaptionMeta.onmessage 的 event.data 解析 JSON。请求 ID 为1～64位字母数字下划线连字符，总长≤8192；同文档最多256个唯一 ID，重复/超量拒绝，不能再执行动作。

```json
{"id":"request_1","action":"setImmersive","params":{"enabled":true}}
```

```json
{"id":"request_1","action":"setImmersive","success":true,"error":null,"immersive":true}
```

| action | 参数与成功语义 |
| --- | --- |
| getCapabilities | 返回 protocolVersion=1、actions 和 immersive，先探测再调用 |
| setImmersive | enabled 必须为布尔值，结果含实际 immersive |
| close | 关闭页面，不能保证已销毁文档继续展示回调 |
| openMembership | 仅导航成功，不表示付款成功 |
| login | 原生 Google 登录与关联，不向网页回传 ID Token 或邮箱 |
| openFeedback | 可选 content 字符串≤4096，原生输入和确认后提交 |
| redeem | 可选 code 字符串≤64，原生确认后按服务端 redeemed 判断；不等同 Play 兑换或自动解锁 |

失败返回 success=false 和 error：invalid_request、action_busy、canceled、duplicate_or_excessive_request 等。能识别的非法请求保留合法 id/action，使 H5 结束等待。原生回调还校验文档代次/当前来源；导航销毁旧动作处理器和未提交弹窗，迟到登录结果不得绑定新页账号。

Promise 客户端以 id/action/success 类型关联响应，默认45秒、最多16个 pending、最长120秒；pagehide reject 并清理等待，页面恢复按实现重新允许调用。超时不自动重试写入。使用已存在客户端，不重复覆盖 onmessage：

```html
<button id="fullscreen">进入全屏</button>
<p id="result" role="status"></p>
<script src="/captionmeta-bridge.js"></script>
<script src="/page-actions.js"></script>
```

page-actions.js 完整最小调用示例：

```javascript
document.querySelector('#fullscreen').addEventListener('click', async () => {
  const output = document.querySelector('#result');
  if (!window.CaptionMetaClient?.isAvailable()) {
    output.textContent = '请在支持此交互的 App 内打开。';
    return;
  }
  try {
    const capabilities = await CaptionMetaClient.request('getCapabilities');
    if (!capabilities.actions.includes('setImmersive')) throw new Error('unsupported_action');
    const result = await CaptionMetaClient.request('setImmersive', {enabled: true});
    output.textContent = result.immersive ? '已进入全屏' : '已退出全屏';
  } catch (error) {
    output.textContent = error.code || error.message;
  }
});
```

只绑定用户显式点击，不自动登录/反馈/兑换。使用 textContent 不渲染响应 HTML；无桥时提示或提供普通网页行为。
