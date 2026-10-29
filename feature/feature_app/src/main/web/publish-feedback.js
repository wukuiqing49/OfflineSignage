Object.assign(messages, {
  publishResults: ['本次发布结果', 'Publication results'],
  publishSummary: ['{accepted}/{total} 台已接受播放命令', '{accepted}/{total} displays accepted playback commands'],
  publishPending: ['等待同步', 'Waiting to sync'],
  publishSyncing: ['正在同步', 'Syncing'],
  publishSynced: ['内容已同步', 'Content synced'],
  publishStarting: ['正在发送播放命令', 'Sending playback command'],
  publishAccepted: ['播放命令已接受', 'Playback command accepted'],
  publishSyncFailed: ['同步失败', 'Sync failed'],
  publishPlayFailed: ['播放命令未确认', 'Playback command unconfirmed'],
  retryFailedDevices: ['仅重试未成功设备', 'Retry unsuccessful displays'],
  previewDisclaimer: ['效果预览，非屏幕实况。请结合设备状态或现场画面确认播放。', 'Content preview, not a live screen capture. Check device status or the display to confirm playback.'],
  publishTargetMissing: ['目标已不可用或分组为空，请重新选择屏幕。', 'The target is unavailable or the group is empty. Select displays again.'],
  publishResultMissing: ['未收到该设备的完整结果，可重试。', 'No complete result received for this display. Retry to check.'],
  publishRefreshFailed: ['发布结果已保留，状态刷新失败；可稍后刷新。', 'Publication results are saved here. Status refresh failed; refresh again later.'],
  changePublishMethod: ['更换发布方式', 'Change content type'],
  recoveryBoundary: ['启动后恢复不等于开机自启。Android 15 及以上普通设备重启后需手动打开应用；专用设备须完成自启动配置及断电验收。其他系统也受厂商策略影响。', 'Resume after launch is not guaranteed boot auto-start. On ordinary Android 15+ devices, open the app after restarting. Dedicated devices require auto-start configuration and power-loss acceptance testing. Manufacturer policies also affect other versions.'],
  entitlementBoundary: ['本地内容播放无需互联网。购买和恢复购买需要连接 Google Play；到期后已有内容继续播放，新建或修改高级内容受当前权益限制。', 'Local content playback works offline. Purchases and restoring purchases require Google Play connectivity. Existing content keeps playing after expiry; creating or changing advanced content depends on current access.']
});

let publicationJob = null;
let publishDraftDirty = false;

function renderPublicationResults() {
  let panel = $('publicationResults');
  if (!panel) {
    panel = document.createElement('section');
    panel.id = 'publicationResults';
    panel.className = 'surface publication-results';
    panel.setAttribute('aria-live', 'polite');
    $('sendView').appendChild(panel);
  }
  panel.replaceChildren();
  panel.hidden = !publicationJob;
  if (!publicationJob) return;
  const title = document.createElement('h2');
  title.textContent = tr('publishResults');
  const summary = document.createElement('p');
  const accepted = publicationJob.rows.filter(row => row.stage === 'ACCEPTED').length;
  summary.textContent = tr('publishSummary', { accepted, total: publicationJob.rows.length });
  panel.append(title, summary);
  const stages = { PENDING: 'publishPending', SYNCING: 'publishSyncing', SYNCED: 'publishSynced', STARTING: 'publishStarting', ACCEPTED: 'publishAccepted', SYNC_FAILED: 'publishSyncFailed', PLAY_FAILED: 'publishPlayFailed' };
  publicationJob.rows.forEach(row => {
    const line = document.createElement('div');
    line.className = 'publication-result';
    const name = document.createElement('strong');
    name.textContent = row.deviceName;
    const result = document.createElement('span');
    result.textContent = tr(stages[row.stage]) + (row.error ? ' · ' + (row.error === 'PUBLISH_RESULT_MISSING' ? tr('publishResultMissing') : row.error) : '');
    line.append(name, result);
    panel.appendChild(line);
  });
  const note = document.createElement('p');
  note.className = 'help';
  note.textContent = tr('previewDisclaimer');
  const retry = document.createElement('button');
  retry.id = 'retryPublication';
  retry.textContent = tr('retryFailedDevices');
  retry.disabled = state.sendInProgress || accepted === publicationJob.rows.length;
  retry.hidden = accepted === publicationJob.rows.length;
  retry.addEventListener('click', retryPublication);
  panel.append(note, retry);
}

async function runPublication() {
  const complete = await PublishWorkflow.run(publicationJob, async (path, body) => {
    if (path === '/api/control') {
      body.revision = ++state.revision;
      body.commandId = newId();
    }
    return request(path, { method: 'POST', body });
  }, renderPublicationResults);
  state.sendFailed = false;
  setSendStatus(tr('publishSummary', {
    accepted: publicationJob.rows.filter(row => row.stage === 'ACCEPTED').length,
    total: publicationJob.rows.length
  }), null, !complete);
  if (complete) publishDraftDirty = false;
  try { await refreshBase(); } catch (_) { notify(tr('publishRefreshFailed'), true); }
  return complete;
}

async function retryPublication() {
  if (!publicationJob || state.sendInProgress) return;
  setSendBusy(true);
  try {
    if (!state.owner) await acquire(true);
    if (!state.owner) return notify(tr('takeControl'), true);
    await runPublication();
  } catch (error) { notify(describeError(error), true); }
  finally { setSendBusy(false); renderPublicationResults(); }
}

document.addEventListener('input', event => {
  if (event.target.closest('#sendView')) publishDraftDirty = true;
});
document.addEventListener('change', event => {
  if (event.target.closest('#sendView')) publishDraftDirty = true;
  if (event.target.id === 'language') renderPublicationResults();
});
window.addEventListener('beforeunload', event => {
  if (publishDraftDirty || state.sendInProgress) { event.preventDefault(); event.returnValue = ''; }
});

// 各入口共用明确的预览和部署说明；使用 textContent 避免内容插入成为 HTML。
function installPublicationGuidance() {
  for (const id of ['currentPreview', 'contentPreview', 'scenePreviewStage']) {
    const preview = $(id);
    if (!preview || $(id + 'Disclaimer')) continue;
    const note = document.createElement('p');
    note.id = id + 'Disclaimer';
    note.dataset.i18n = 'previewDisclaimer';
    note.className = 'help preview-disclaimer';
    note.textContent = tr('previewDisclaimer');
    if (id === 'currentPreview') {
      const stage = preview.closest('.now-playing');
      const column = document.createElement('div');
      column.className = 'play-preview-column';
      stage.before(column);
      column.append(stage, note);
    } else preview.after(note);
  }
  const settings = $('autoResume')?.closest('label');
  if (settings && !$('recoveryBoundary')) {
    const note = document.createElement('p');
    note.id = 'recoveryBoundary';
    note.dataset.i18n = 'recoveryBoundary';
    note.className = 'help';
    note.textContent = tr('recoveryBoundary');
    settings.after(note);
  }
  const pro = document.querySelector('.pro-dialog');
  if (pro && !$('entitlementBoundary')) {
    const note = document.createElement('p');
    note.id = 'entitlementBoundary';
    note.dataset.i18n = 'entitlementBoundary';
    note.textContent = tr('entitlementBoundary');
    pro.querySelector('.dialog-actions').before(note);
  }
  $('sendProgress').setAttribute('role', 'status');
  renderPublicationResults();
}
installPublicationGuidance();

// 手机上选好内容类型后收起七张说明卡，把空间留给编辑与发布结果。
const publishChooser = document.querySelector('.publish-chooser');
if (publishChooser) {
  const toggle = document.createElement('button');
  toggle.type = 'button';
  toggle.className = 'publish-method-toggle';
  toggle.dataset.i18n = 'changePublishMethod';
  toggle.textContent = tr('changePublishMethod');
  toggle.setAttribute('aria-expanded', 'true');
  const methods = publishChooser.querySelector('.publish-methods');
  methods.id = 'publishMethods';
  toggle.setAttribute('aria-controls', methods.id);
  publishChooser.querySelector('h2').after(toggle);
  toggle.addEventListener('click', () => {
    const collapsed = publishChooser.classList.toggle('compact-choice');
    toggle.setAttribute('aria-expanded', String(!collapsed));
  });
  methods.addEventListener('click', event => {
    const choice = event.target.closest('[data-publish-type]');
    if (!choice || state.selectedType !== choice.dataset.publishType || !matchMedia('(max-width:560px)').matches) return;
    publishChooser.classList.add('compact-choice');
    toggle.setAttribute('aria-expanded', 'false');
    toggle.focus();
  });
}
