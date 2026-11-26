(() => {
  Object.assign(messages, {
    previewScene: ['预览场景', 'Preview scene'],
    scenePreview: ['场景预览', 'Scene preview'],
    scenePreviewUnavailable: ['此资源无法在控制台中预览，将按设备播放设置发布。', 'This resource cannot be previewed in the console and will use the device playback settings.'],
    sceneLayout: ['画面布局', 'Layout'],
    layoutFullscreen: ['全屏', 'Full screen'],
    layoutMainSidebar: ['主画面 + 侧栏', 'Main + sidebar'],
    sidebarContent: ['侧栏内容', 'Sidebar content'],
    chooseSidebarContent: ['选择图片或文字', 'Choose an image or text'],
    sidebarRequired: ['请选择侧栏图片或文字。', 'Choose an image or text for the sidebar.']
  });
  const sourceDialog = document.getElementById('sceneDialog');
  const templateStyle = document.createElement('style');
  templateStyle.textContent = '.scene-layout-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.scene-layout-option{display:grid;gap:8px;justify-items:start;text-align:left;padding:10px;border-color:var(--line);background:var(--surface)}.scene-layout-option[aria-checked=true]{border:2px solid var(--brand);padding:9px;background:var(--brand-soft);color:var(--brand-dark)}.scene-layout-thumb{display:grid;grid-template-columns:2fr 1fr;gap:3px;width:100%;height:44px}.scene-layout-thumb span{background:#dce6e1;border-radius:3px}.scene-layout-option[data-layout=FULLSCREEN] .scene-layout-thumb{grid-template-columns:1fr}.scene-layout-option[data-layout=FULLSCREEN] .scene-layout-thumb span:last-child{display:none}#scenePreviewStage.split{display:grid;grid-template-columns:minmax(0,2.125fr) minmax(76px,1fr);gap:3px}#scenePreviewStage.split{grid-template-rows:minmax(0,1fr);place-items:stretch}.scene-preview-region{position:relative;min-width:0;min-height:0;overflow:hidden;background:#101612}.scene-preview-region img,.scene-preview-region video,.scene-preview-region iframe{width:100%;height:100%;position:absolute;inset:0;border:0;object-fit:contain}.scene-preview-region .current-text-preview{font-size:clamp(10px,3vw,36px);padding:8px}.scene-preview-region.sidebar .current-text-preview{font-size:clamp(9px,2vw,22px);padding:8px}.scene-preview-region .preview-empty{position:absolute;inset:0;display:grid;place-items:center;font-size:11px;padding:8px}';
  document.head.appendChild(templateStyle);
  templateStyle.textContent += '.scene-preview-region.scene-preview-sidebar .current-text-preview{font-size:clamp(9px,2vw,22px);padding:8px}';
  const resourceLabel = document.getElementById('sceneResource').closest('label');
  const layoutLabel = document.createElement('div');
  layoutLabel.className = 'form-stack';
  layoutLabel.innerHTML = '<span id="sceneLayoutHeading" style="font-size:13px;font-weight:650" data-i18n="sceneLayout"></span><div class="scene-layout-options" role="radiogroup" aria-labelledby="sceneLayoutHeading"><button type="button" class="scene-layout-option" data-layout="FULLSCREEN" role="radio"><span class="scene-layout-thumb"><span></span><span></span></span><span data-i18n="layoutFullscreen"></span></button><button type="button" class="scene-layout-option" data-layout="MAIN_WITH_SIDEBAR" role="radio"><span class="scene-layout-thumb"><span></span><span></span></span><span data-i18n="layoutMainSidebar"></span></button></div>';
  const sidebarLabel = document.createElement('label');
  sidebarLabel.className = 'hidden';
  sidebarLabel.innerHTML = '<span data-i18n="sidebarContent"></span><select id="sceneSidebarResource"></select>';
  resourceLabel.after(layoutLabel, sidebarLabel);
  let draftLayout = 'FULLSCREEN';
  const supportedSidebarResources = () => state.resources.filter(item => item.kind === 'TEXT' || (item.mimeType || '').startsWith('image/'));
  const updateLayoutChoice = layout => {
    draftLayout = layout;
    window.sceneLayoutDraft = layout;
    layoutLabel.querySelectorAll('[data-layout]').forEach(button => { const selected = button.dataset.layout === layout; button.setAttribute('aria-checked', String(selected)); button.tabIndex = selected ? 0 : -1; });
    sidebarLabel.classList.toggle('hidden', layout !== 'MAIN_WITH_SIDEBAR');
  };
  const fillSidebarResources = selected => {
    const select = document.getElementById('sceneSidebarResource');
    select.innerHTML = '';
    const placeholder = document.createElement('option'); placeholder.value = ''; placeholder.textContent = tr('chooseSidebarContent'); select.appendChild(placeholder);
    supportedSidebarResources().forEach(item => {
      const option = document.createElement('option'); option.value = item.id; option.textContent = item.name + ' · ' + resourceKind(item.id); option.selected = item.id === selected; select.appendChild(option);
    });
  };
  layoutLabel.querySelectorAll('[data-layout]').forEach(button => button.addEventListener('click', () => { updateLayoutChoice(button.dataset.layout); if (previewDialog.open) renderDraft(); }));
  document.getElementById('sceneSidebarResource').addEventListener('change', () => { if (previewDialog.open) renderDraft(); });
  const previewDialog = document.createElement('dialog');
  previewDialog.id = 'scenePreviewDialog';
  previewDialog.innerHTML = '<div class="dialog-head"><h2 data-i18n="scenePreview">场景预览</h2><button class="icon ghost" type="button" aria-label="Close">×</button></div><div class="dialog-body"><div id="scenePreviewStage" class="preview" style="aspect-ratio:16/9;min-height:0"></div></div><div class="dialog-actions"><button type="button" data-i18n="cancel">关闭</button></div>';
  document.body.appendChild(previewDialog);
  let previewRequest = 0;
  const previewUrls = new Set();
  const clearPreviewMedia = () => {
    previewRequest += 1;
    previewUrls.forEach(url => URL.revokeObjectURL(url));
    previewUrls.clear();
  };
  const closePreview = () => previewDialog.close();
  previewDialog.querySelectorAll('button').forEach(button => button.addEventListener('click', closePreview));
  previewDialog.addEventListener('close', () => {
    clearPreviewMedia();
    document.getElementById('scenePreviewStage').replaceChildren();
  });
  const previewButton = document.createElement('button');
  previewButton.type = 'button';
  previewButton.dataset.i18n = 'previewScene';
  previewButton.textContent = tr('previewScene');
  sourceDialog.querySelector('.dialog-actions').insertBefore(previewButton, document.getElementById('saveScene'));
  const renderResource = async (resource, region, fitMode = 'FIT', gravity = 'CENTER', requestId) => {
    if (!resource) { const empty = document.createElement('div'); empty.className = 'preview-empty'; empty.textContent = tr('chooseSidebarContent'); region.appendChild(empty); return; }
    let node;
    if (resource.kind === 'TEXT') {
      node = document.createElement('div'); node.className = 'current-text-preview'; node.textContent = resource.content || '';
      node.style.color = resource.textColor || '#fff'; node.style.background = resource.textBackgroundColor || '#000';
      node.style.fontSize = Math.min(64, Math.max(12, Number(resource.textSizeSp) || 36)) + 'px';
    } else if (resource.kind === 'WEB' && resource.content) {
      node = document.createElement('iframe'); node.className = 'html-frame'; node.sandbox = ''; node.srcdoc = resource.content;
    } else if (resource.url || resource.sourceUri) {
      const isVideo = (resource.mimeType || '').startsWith('video/');
      node = document.createElement(isVideo ? 'video' : 'img');
      node.style.width = '100%'; node.style.height = '100%';
      if (isVideo) { node.muted = true; node.controls = true; }
      node.style.objectFit = ({FIT:'contain',FILL:'cover',CROP:'cover',STRETCH:'fill',CENTER:'none'})[fitMode] || 'contain';
      node.style.objectPosition = ({CENTER:'center',TOP:'top',BOTTOM:'bottom',LEFT:'left',RIGHT:'right'})[gravity] || 'center';
      if (resource.kind === 'LOCAL_FILE' && resource.url) {
        if (isVideo && Number(resource.sizeBytes) > 25 * 1024 * 1024) {
          const unavailable = document.createElement('div'); unavailable.className = 'preview-empty'; unavailable.textContent = tr('previewTooLarge'); region.appendChild(unavailable); return;
        }
        const loading = document.createElement('div'); loading.className = 'preview-empty'; loading.textContent = tr('previewLoading'); region.appendChild(loading);
        try {
          const response = await fetch(resource.url, {headers:apiHeaders(false),cache:'no-store'});
          if (!response.ok) throw new Error();
          const blob = await response.blob();
          if (requestId !== previewRequest || !region.isConnected) return;
          const url = URL.createObjectURL(blob); previewUrls.add(url); node.src = url;
          loading.remove();
          node.addEventListener('error', () => {
            if (requestId !== previewRequest || !region.isConnected) return;
            const failed = document.createElement('div'); failed.className = 'preview-empty'; failed.textContent = tr('previewFailed'); region.replaceChildren(failed);
          }, {once:true});
        } catch (_) {
          if (requestId === previewRequest && region.isConnected) { loading.textContent = tr('previewFailed'); }
          return;
        }
      } else {
        node.src = resource.sourceUri || resource.url;
      }
    } else {
      node = document.createElement('div'); node.className = 'preview-empty'; node.textContent = tr('scenePreviewUnavailable');
    }
    region.appendChild(node);
  };
  const renderDraft = () => {
    clearPreviewMedia();
    const requestId = previewRequest;
    const stage = document.getElementById('scenePreviewStage');
    const resource = state.resources.find(item => item.id === document.getElementById('sceneResource').value);
    stage.innerHTML = '';
    stage.classList.toggle('split', draftLayout === 'MAIN_WITH_SIDEBAR');
    stage.style.background = document.getElementById('sceneBackground').value === 'BLUR' ? '#34433c' : '#101612';
    if (!resource) return;
    const mainRegion = document.createElement('div'); mainRegion.className = 'scene-preview-region'; stage.appendChild(mainRegion);
    renderResource(resource, mainRegion, document.getElementById('sceneFitMode').value, document.getElementById('sceneCropGravity').value, requestId);
    if (draftLayout === 'MAIN_WITH_SIDEBAR') {
      const sidebarRegion = document.createElement('div'); sidebarRegion.className = 'scene-preview-region scene-preview-sidebar'; stage.appendChild(sidebarRegion);
      renderResource(state.resources.find(item => item.id === document.getElementById('sceneSidebarResource').value), sidebarRegion, 'FIT', 'CENTER', requestId);
    }
    if (document.getElementById('tickerEnabled').checked && document.getElementById('tickerText').value.trim()) {
      const ticker = document.createElement('div'); ticker.textContent = document.getElementById('tickerText').value.trim();
      ticker.style.cssText = 'position:absolute;left:3%;right:3%;padding:8px 12px;color:' + document.getElementById('tickerColor').value + ';background:' + document.getElementById('tickerBackground').value + ';font-size:' + Math.min(36, Math.max(12, Number(document.getElementById('tickerSize').value) || 28)) + 'px;line-height:1.25;overflow:hidden;white-space:nowrap;';
      ticker.style[document.getElementById('tickerPosition').value === 'TOP' ? 'top' : document.getElementById('tickerPosition').value === 'CENTER' ? 'top' : 'bottom'] = document.getElementById('tickerPosition').value === 'CENTER' ? '50%' : '3%';
      if (document.getElementById('tickerPosition').value === 'CENTER') ticker.style.transform = 'translateY(-50%)';
      mainRegion.appendChild(ticker);
    }
  };
  previewButton.addEventListener('click', () => { renderDraft(); previewDialog.showModal(); });
  ['sceneResource','sceneSidebarResource','sceneFitMode','sceneBackground','sceneCropGravity','tickerEnabled','tickerText','tickerSize','tickerPosition','tickerColor','tickerBackground'].forEach(id => {
    const node = document.getElementById(id);
    ['input', 'change'].forEach(event => node.addEventListener(event, () => { if (previewDialog.open) renderDraft(); }));
  });
  const openSceneDialogBase = openSceneDialog;
  window.openSceneDialog = function(id='') {
    openSceneDialogBase(id);
    if (!$('sceneDialog').open) return;
    const item = state.scenes.find(value => value.id === id);
    updateLayoutChoice(item?.layoutTemplate || 'FULLSCREEN');
    fillSidebarResources(item?.sidebarResourceId || '');
  };
  localize();
})();
