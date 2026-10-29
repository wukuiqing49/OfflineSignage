(() => {
  const sendView = document.getElementById('sendView');
  const sendNav = document.querySelector('[data-view="send"]');
  const sendHeading = sendView.querySelector('.page-head h1');
  const sendIntro = sendView.querySelector('.page-head p');
  sendNav.querySelector('span:last-child').dataset.i18n = 'publish';
  sendHeading.dataset.i18n = 'publish';
  sendIntro.dataset.i18n = 'publishIntro';

  const tabs = sendView.querySelector('.type-tabs');
  tabs.classList.add('hidden');
  const publishStyle = document.createElement('style');
  publishStyle.textContent = '.publish-chooser{display:grid;gap:12px;margin:0 0 18px}.publish-chooser h2{margin:0;font-size:15px}.publish-methods{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.publish-method{display:grid;min-height:118px;align-content:start;gap:6px;padding:15px;border:1px solid var(--line);border-radius:6px;background:#fff;text-align:left}.publish-method:hover{border-color:#7aa990;background:#f5fbf8}.publish-method.active{border:2px solid var(--brand);background:var(--brand-soft)}.publish-method b{font-size:14px}.publish-method span{font-size:12px;line-height:1.45;color:var(--muted);font-weight:400}.method-tag,.pro-tag{justify-self:start;padding:3px 6px;border-radius:4px;font-size:10px;font-weight:750;line-height:1.2}.method-tag{background:#e4f5ec;color:var(--brand-dark)}.pro-tag{background:#fff0d7;color:#935f12}.commercial-note{display:flex;align-items:center;gap:8px;padding:10px 12px;border:1px solid #e8d1a8;border-radius:6px;background:#fffaf0;color:#79521b;font-size:12px}.commercial-note.ok{border-color:#b9ddca;background:#f2fbf6;color:var(--brand-dark)}.pro-dialog{display:grid;gap:14px}.pro-dialog h2,.pro-dialog p{margin:0}.pro-dialog p{color:var(--muted);line-height:1.6}.pro-dialog .dialog-actions{margin:4px -20px -20px}@media(max-width:900px){.publish-methods{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:560px){.publish-methods{grid-template-columns:repeat(2,minmax(0,1fr))}.publish-method{min-height:108px;padding:13px}.publish-method:last-child{grid-column:span 2}}';
  publishStyle.textContent += '.global-commercial-banner{margin:18px 34px 0;padding:13px 16px;border:1px solid #e2bd78;border-radius:6px;background:#fff8e8;color:#694713;display:flex;align-items:center;justify-content:space-between;gap:16px}.global-commercial-banner[hidden]{display:none}.global-commercial-copy{display:grid;gap:3px;min-width:0}.global-commercial-copy strong{font-size:14px}.global-commercial-copy span{font-size:12px;line-height:1.45}.global-commercial-banner button{flex:none;background:#8a5a0a;border-color:#8a5a0a;color:#fff}@media(max-width:900px){.global-commercial-banner{margin:14px 16px 0}}@media(max-width:560px){.global-commercial-banner{align-items:stretch;flex-direction:column;gap:10px}.global-commercial-banner button{width:100%}}';
  document.head.appendChild(publishStyle);
  const chooser = document.createElement('section');
  chooser.className = 'publish-chooser';
  chooser.innerHTML = '<h2 data-i18n="choosePublishMethod">选择发布方式</h2><div class="publish-methods">' +
    '<button type="button" class="publish-method" data-publish-type="IMAGE"><b data-i18n="image">图片</b><span data-i18n="publishImageHint">上传一张或多张图片，可设置轮播与切换效果。</span></button>' +
    '<button type="button" class="publish-method" data-publish-type="VIDEO"><b data-i18n="video">视频</b><span data-i18n="publishVideoHint">上传本地视频并直接发布播放。</span></button>' +
    '<button type="button" class="publish-method" data-publish-type="DESIGN"><b data-i18n="templateDesign">模板设计</b><small class="method-tag" data-i18n="offlineTemplate">离线模板</small><small class="pro-tag" data-i18n="proFeature">Pro 专属</small><span data-i18n="publishDesignHint">用离线模板快速制作餐牌、欢迎屏、促销或会议日程。</span></button>' +
    '<button type="button" class="publish-method" data-publish-type="HTML"><b data-i18n="h5Project">H5 项目</b><small class="method-tag" data-i18n="offlinePackage">离线项目包</small><small class="pro-tag" data-i18n="proFeature">Pro 专属</small><span data-i18n="h5PackagePreview">项目包将在发布后由设备离线加载。</span></button>' +
    '<button type="button" class="publish-method" data-publish-type="WEB"><b data-i18n="web">网页</b><small class="pro-tag" data-i18n="proFeature">Pro 专属</small><span data-i18n="publishWebHint">播放一个局域网或受支持的网址。</span></button>' +
    '<button type="button" class="publish-method" data-publish-type="TEXT"><b data-i18n="text">文字</b><small class="pro-tag" data-i18n="proFeature">Pro 专属</small><span data-i18n="publishTextHint">发布公告、通知或滚动字幕画面。</span></button>' +
    '<button type="button" class="publish-method" data-publish-type="STREAM"><b data-i18n="live">直播</b><small class="pro-tag" data-i18n="proFeature">Pro 专属</small><span data-i18n="publishLiveHint">接入 HLS、DASH 或 RTSP 直播源。</span></button>' +
    '</div>';
  sendView.querySelector('.target-bar').after(chooser);
  const commercialNote = document.createElement('div');
  commercialNote.className = 'commercial-note';
  chooser.before(commercialNote);
  const proDialog = document.createElement('dialog');
  proDialog.innerHTML = '<div class="dialog-body pro-dialog"><h2 data-i18n="proRequiredTitle">此功能需要 Pro</h2><p data-i18n="proRequiredBody">免费版可保留并播放现有内容，支持最多 10 个图片或视频资源与 1 个基础图片/视频轮播。文字、网页、直播、H5、模板设计和多屏管理需要 Pro。</p><p data-i18n="proUpgradeDevice">请在广告机 App 中打开“版本与授权”，选择月度、年度或永久 Pro；可用套餐与当地价格以 Google Play 实时显示为准。</p><div class="dialog-actions"><button type="button" class="primary" data-close-pro data-i18n="proClose">知道了</button></div></div>';
  document.body.appendChild(proDialog);
  proDialog.querySelector('[data-close-pro]').addEventListener('click', () => proDialog.close());
  const globalCommercialBanner = document.createElement('section');
  globalCommercialBanner.className = 'global-commercial-banner';
  globalCommercialBanner.hidden = true;
  globalCommercialBanner.innerHTML = '<div class="global-commercial-copy"><strong data-i18n="freeBannerTitle">当前为免费版，已有画面会继续播放</strong><span data-i18n="freeBannerBody">可使用 10 个图片/视频资源和 1 个基础轮播；高级内容与多屏管理需要 Pro。</span></div><button type="button" data-view-upgrade data-i18n="viewUpgrade">查看升级方式</button>';
  document.querySelector('.topbar').after(globalCommercialBanner);
  globalCommercialBanner.querySelector('[data-view-upgrade]').addEventListener('click', () => showProDialog());
  const proTypes = new Set(['DESIGN', 'HTML', 'WEB', 'TEXT', 'STREAM']);
  function isFreeLimited() { return state.current?.commercialAccess?.mode === 'FREE_LIMITED'; }
  function renderCommercialAccess() {
    const access = state.current?.commercialAccess;
    const mode = access?.mode || 'FREE_LIMITED';
    globalCommercialBanner.hidden = mode !== 'FREE_LIMITED';
    commercialNote.classList.toggle('ok', mode !== 'FREE_LIMITED');
    commercialNote.textContent = mode === 'PRO' ? tr('proUsage') : mode === 'TRIAL' ? tr('trialUsage') : tr('freeUsage', {resources: access?.resourceCount || 0, playlists: access?.playlistCount || 0});
    chooser.querySelectorAll('[data-publish-type]').forEach(node => node.classList.toggle('pro-locked', isFreeLimited() && proTypes.has(node.dataset.publishType)));
  }
  const baseRenderStatus = renderStatus;
  renderStatus = function (value, forcePreview) { baseRenderStatus(value, forcePreview); renderCommercialAccess(); };
  function showProDialog() { if (!proDialog.open) proDialog.showModal(); }
  const baseSelectSendType = selectSendType;
  selectSendType = function (type) {
    if (isFreeLimited() && proTypes.has(type)) { showProDialog(); return; }
    baseSelectSendType(type);
    chooser.querySelectorAll('[data-publish-type]').forEach(node => node.classList.toggle('active', node.dataset.publishType === type));
    const titleKey = {TEXT:'sendText',IMAGE:'sendImage',VIDEO:'sendVideo',STREAM:'sendLive',WEB:'sendWeb',DESIGN:'templateDesign',HTML:'h5Project'}[type];
    const editorTitle = document.getElementById('editorTitle');
    editorTitle.dataset.i18n = titleKey;
    editorTitle.textContent = tr(titleKey);
    if (type === 'HTML') document.getElementById('editorTypeBadge').textContent = 'H5';
  };
  chooser.querySelectorAll('[data-publish-type]').forEach(node => node.addEventListener('click', () => selectSendType(node.dataset.publishType)));
  document.getElementById('language').addEventListener('change', renderCommercialAccess);
  selectSendType('IMAGE');

  const htmlEditor = document.getElementById('htmlEditor');
  htmlEditor.innerHTML = `
    <div class="h5-mode-tabs" role="tablist">
      <button class="active" type="button" data-h5-source="PACKAGE" data-i18n="h5PackageMode">本地 H5 项目包</button>
      <button type="button" data-h5-source="CODE" data-i18n="h5CodeMode">单页 HTML 代码</button>
    </div>
    <div id="h5PackageSource" class="h5-source">
      <input id="webPackage" class="hidden" type="file" accept=".zip,application/zip">
      <div id="h5DropZone" class="drop-zone" tabindex="0">
        <div><strong data-i18n="chooseH5Package">选择 H5 项目 ZIP</strong><span data-i18n="chooseH5PackageHint">选择包含 index.html、CSS、JS 和图片的 ZIP 项目包。全部资源将在设备本地离线播放。</span></div>
      </div>
      <div id="h5PackageSummary" class="file-summary hidden"></div>
      <p class="format-hint" data-i18n="webPackageHint">ZIP 根目录必须包含 index.html，最多 256 个文件、50 MB。</p>
      <small class="help" data-i18n="freeLicense">免费版 · 高级功能需 Pro</small>
    </div>
    <div id="h5CodeSource" class="h5-source hidden">
      <label><span data-i18n="htmlCode">HTML / CSS 代码</span><textarea id="htmlContent" class="html-code" maxlength="100000" spellcheck="false" autocapitalize="off"></textarea><small data-i18n="h5CodeHint">适合单个静态 HTML 画面。复杂项目请使用本地 H5 项目包。</small></label>
    </div>`;

  state.h5Source = 'PACKAGE';
  function selectH5Source(source) {
    state.h5Source = source;
    document.querySelectorAll('[data-h5-source]').forEach(node => node.classList.toggle('active', node.dataset.h5Source === source));
    document.getElementById('h5PackageSource').classList.toggle('hidden', source !== 'PACKAGE');
    document.getElementById('h5CodeSource').classList.toggle('hidden', source !== 'CODE');
    updatePreview();
  }
  document.querySelectorAll('[data-h5-source]').forEach(node => node.addEventListener('click', () => selectH5Source(node.dataset.h5Source)));
  const packageInput = document.getElementById('webPackage');
  const packageSummary = document.getElementById('h5PackageSummary');
  function updatePackageSelection() {
    const file = packageInput.files[0];
    packageSummary.classList.toggle('hidden', !file);
    packageSummary.textContent = file ? file.name + ' · ' + formatBytes(file.size) : '';
    updatePreview();
  }
  document.getElementById('h5DropZone').addEventListener('click', () => packageInput.click());
  document.getElementById('h5DropZone').addEventListener('keydown', event => {
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); packageInput.click(); }
  });
  packageInput.addEventListener('change', updatePackageSelection);
  document.getElementById('htmlContent').addEventListener('input', updatePreview);

  const priorUpdatePreview = updatePreview;
  updatePreview = function () {
    if (state.selectedType === 'HTML' && state.h5Source === 'PACKAGE') {
      const preview = document.getElementById('contentPreview');
      const file = packageInput.files[0];
      preview.innerHTML = '';
      preview.classList.remove('html-preview-wrap');
      const node = document.createElement('div');
      node.className = 'h5-package-preview';
      node.innerHTML = '<strong>' + (file ? escapeDesign(file.name) : tr('h5PackageReady')) + '</strong><span>' + tr(file ? 'h5PackagePreview' : 'chooseH5PackageHint') + '</span>';
      preview.appendChild(node);
      return;
    }
    priorUpdatePreview();
  };

  const designDefaults = {
    MENU: { title: '晨光咖啡', subtitle: '新鲜现制 · 每日供应', items: '招牌拿铁 | ¥28\n澳白 | ¥26\n冷萃 | ¥24', hint: '品名 | 价格' },
    WELCOME: { title: '欢迎莅临', subtitle: '期待与您相见', items: '', hint: '可留空，画面将突出欢迎语' },
    PROMOTION: { title: '限时大促', subtitle: '到店即享 · 数量有限', items: '全场低至 | 5 折\n活动日期 | 9 月 20 日 - 9 月 30 日', hint: '优惠标题 | 优惠说明' },
    SCHEDULE: { title: '今日会议安排', subtitle: '请按时前往对应会场', items: '09:00 - 10:30 | 季度经营复盘 · 云庭厅 A\n14:00 - 16:00 | 合作伙伴交流会 · 宴会厅', hint: '时间 | 会议主题 · 地点' }
  };
  const designTitle = document.getElementById('designTitle');
  const designSubtitle = document.getElementById('designSubtitle');
  const designItems = document.getElementById('designItems');
  [designTitle, designSubtitle, designItems].forEach(node => node.addEventListener('input', () => { node.dataset.edited = 'true'; }));
  function updateDesignGuide() {
    const guide = designDefaults[document.getElementById('designType').value];
    if (!designTitle.dataset.edited) designTitle.value = guide.title;
    if (!designSubtitle.dataset.edited) designSubtitle.value = guide.subtitle;
    if (!designItems.dataset.edited) designItems.value = guide.items;
    designItems.placeholder = guide.hint;
    updatePreview();
  }
  document.getElementById('designType').addEventListener('change', updateDesignGuide);
  updateDesignGuide();
  localize();
})();
