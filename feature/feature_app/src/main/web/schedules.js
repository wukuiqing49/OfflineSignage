(() => {
  Object.assign(messages, {
    playbackSchedules: ['播放排期', 'Playback schedules'],
    playbackSchedulesHint: ['在设备本地按周自动切换播放列表。新建和删除排期需要 Pro。', 'Switch playlists locally on this device by week. Creating and deleting schedules requires Pro.'],
    addSchedule: ['新增排期', 'Add schedule'],
    noSchedules: ['尚未设置自动播放排期。', 'No automatic playback schedules yet.'],
    schedulePlaylist: ['播放列表', 'Playlist'],
    scheduleWeekdays: ['生效日期', 'Active days'],
    scheduleStart: ['开始时间', 'Start time'],
    scheduleEnd: ['结束时间', 'End time'],
    schedulePriority: ['优先级', 'Priority'],
    scheduleEnabled: ['启用此排期', 'Enable this schedule'],
    scheduleSave: ['保存排期', 'Save schedule'],
    scheduleSaved: ['播放排期已保存，设备会在 30 秒内检查并自动切换。', 'Schedule saved. The device checks and switches within 30 seconds.'],
    scheduleDeleted: ['播放排期已删除。', 'Playback schedule deleted.'],
    scheduleNow: ['当前生效', 'Active now'],
    scheduleNoMatch: ['当前没有匹配的自动排期，设备保持手动选择的播放列表。', 'No schedule matches now. The device keeps its manually selected playlist.'],
    scheduleNext: ['下次变化', 'Next change'],
    scheduleDaysRequired: ['请至少选择一个生效日期。', 'Choose at least one active day.'],
    schedulePlaylistRequired: ['请先选择一个播放列表。', 'Choose a playlist first.'],
    scheduleNoPlaylist: ['请先创建一个包含内容的播放列表。', 'Create a playlist with content first.'],
    syncSchedulesToGroup: ['同步到当前设备组', 'Sync to current device group'],
    chooseGroupForSchedules: ['请先在顶部选择一个设备组。', 'Choose a device group from the top selector first.'],
    schedulesSyncedToGroup: ['排期与所需播放内容已同步到 {count} 台设备；目标设备将离线自主执行。', 'Schedules and required playlist content were synced to {count} devices. Target devices now run offline independently.'],
    sunday: ['周日', 'Sun'], monday: ['周一', 'Mon'], tuesday: ['周二', 'Tue'], wednesday: ['周三', 'Wed'], thursday: ['周四', 'Thu'], friday: ['周五', 'Fri'], saturday: ['周六', 'Sat']
  });

  const style = document.createElement('style');
  style.textContent = '.schedule-status{padding:12px;border:1px solid var(--line);background:var(--surface-subtle);border-radius:5px;font-size:13px;line-height:1.5}.schedule-status strong{color:var(--brand-dark)}.schedule-days{display:flex;flex-wrap:wrap;gap:7px}.schedule-days label{display:flex;align-items:center;gap:5px;border:1px solid var(--line);border-radius:5px;padding:7px 8px;font-size:12px;font-weight:650}.schedule-days input{width:16px;min-height:16px}.schedule-list{display:grid}.schedule-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px;align-items:center;padding:14px 0;border-bottom:1px solid var(--line)}.schedule-row:last-child{border-bottom:0}.schedule-row strong{display:block;overflow-wrap:anywhere}.schedule-row span{display:block;color:var(--muted);font-size:12px;margin-top:5px}.schedule-row button{min-height:36px;padding:6px 11px;font-size:12px}@media(max-width:560px){.schedule-row{grid-template-columns:1fr}.schedule-row button{width:100%}}';
  document.head.appendChild(style);

  const button = document.createElement('button');
  button.id = 'openSchedules'; button.type = 'button'; button.textContent = tr('playbackSchedules');
  document.querySelector('#contentView .page-actions').appendChild(button);

  const dialog = document.createElement('dialog');
  dialog.id = 'scheduleDialog';
  dialog.innerHTML = '<div class="dialog-head"><h2 data-i18n="playbackSchedules">播放排期</h2><button class="icon ghost" type="button" aria-label="Close">×</button></div><div class="dialog-body"><p class="help" data-i18n="playbackSchedulesHint"></p><div id="scheduleStatus" class="schedule-status"></div><div id="scheduleRows" class="schedule-list"></div><div class="dialog-section"><h3 data-i18n="addSchedule">新增排期</h3><div id="scheduleForm" class="form-stack"><label><span data-i18n="schedulePlaylist">播放列表</span><select id="schedulePlaylist"></select></label><label><span data-i18n="scheduleWeekdays">生效日期</span><span id="scheduleWeekdays" class="schedule-days"></span></label><div class="form-grid"><label><span data-i18n="scheduleStart">开始时间</span><input id="scheduleStart" type="time" value="09:00"></label><label><span data-i18n="scheduleEnd">结束时间</span><input id="scheduleEnd" type="time" value="18:00"></label></div><div class="form-grid"><label><span data-i18n="schedulePriority">优先级</span><input id="schedulePriority" type="number" value="0" step="1"></label><label class="check"><input id="scheduleEnabled" type="checkbox" checked><span data-i18n="scheduleEnabled">启用此排期</span></label></div></div></div></div><div class="dialog-actions"><button type="button" data-close-schedule data-i18n="cancel">取消</button><button id="syncSchedules" type="button" data-write data-i18n="syncSchedulesToGroup">同步到当前设备组</button><button id="saveSchedule" class="primary" type="button" data-write data-i18n="scheduleSave">保存排期</button></div>';
  document.body.appendChild(dialog);

  const days = [[1, 'sunday'], [2, 'monday'], [3, 'tuesday'], [4, 'wednesday'], [5, 'thursday'], [6, 'friday'], [7, 'saturday']];
  const dayContainer = document.getElementById('scheduleWeekdays');
  days.forEach(([value, key]) => {
    const label = document.createElement('label');
    const input = document.createElement('input'); input.type = 'checkbox'; input.value = value; input.checked = value >= 2 && value <= 6;
    const name = document.createElement('span'); name.dataset.i18n = key; name.textContent = tr(key);
    label.append(input, name); dayContainer.appendChild(label);
  });

  const minuteToTime = minute => String(Math.floor(minute / 60)).padStart(2, '0') + ':' + String(minute % 60).padStart(2, '0');
  const timeToMinute = value => { const parts = String(value || '').split(':').map(Number); return parts.length === 2 && parts.every(Number.isFinite) ? Math.max(0, Math.min(1439, parts[0] * 60 + parts[1])) : 0; };
  const scheduleDayNames = values => values.slice().sort((a, b) => a - b).map(value => tr(days.find(day => day[0] === value)?.[1] || '')).join(' · ');
  let schedules = [];

  const renderSchedules = status => {
    const playlistSelect = document.getElementById('schedulePlaylist');
    const previous = playlistSelect.value;
    playlistSelect.replaceChildren();
    state.playlists.forEach(playlist => { const option = document.createElement('option'); option.value = playlist.id; option.textContent = playlist.name; playlistSelect.appendChild(option); });
    if (state.playlists.some(playlist => playlist.id === previous)) playlistSelect.value = previous;
    const current = status?.active;
    const statusNode = document.getElementById('scheduleStatus'); statusNode.replaceChildren();
    if (current) {
      const playlist = state.playlists.find(item => item.id === current.playlistId);
      const title = document.createElement('strong'); title.textContent = tr('scheduleNow') + '：' + (playlist?.name || current.playlistId);
      const detail = document.createElement('div'); detail.textContent = tr('scheduleNext') + '：' + (status.nextSwitchAt ? formatDateTime(status.nextSwitchAt, { hour: '2-digit', minute: '2-digit', weekday: 'short' }) : '-');
      statusNode.append(title, detail);
    } else statusNode.textContent = tr('scheduleNoMatch');
    const rows = document.getElementById('scheduleRows'); rows.replaceChildren();
    if (!schedules.length) { const empty = document.createElement('div'); empty.className = 'section-empty'; empty.textContent = tr('noSchedules'); rows.appendChild(empty); return; }
    schedules.slice().sort((a, b) => b.priority - a.priority || a.startMinute - b.startMinute).forEach(schedule => {
      const row = document.createElement('div'); row.className = 'schedule-row';
      const main = document.createElement('div'); const playlist = state.playlists.find(item => item.id === schedule.playlistId);
      const title = document.createElement('strong'); title.textContent = playlist?.name || schedule.playlistId;
      const detail = document.createElement('span'); detail.textContent = scheduleDayNames(schedule.weekdays) + ' · ' + minuteToTime(schedule.startMinute) + ' - ' + minuteToTime(schedule.endMinute) + ' · ' + tr('schedulePriority') + ' ' + schedule.priority + (schedule.enabled ? '' : ' · ' + tr('paused'));
      main.append(title, detail); const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger'; remove.dataset.write = 'true'; remove.textContent = tr('delete');
      remove.addEventListener('click', async () => { if (!state.owner) return notify(tr('takeControl'), true); try { await request('/api/schedules/' + encodeURIComponent(schedule.id), { method: 'DELETE' }); notify(tr('scheduleDeleted')); await loadSchedules(); } catch (error) { notify(describeError(error), true); } });
      row.append(main, remove); rows.appendChild(row);
    });
    setOwner(state.owner, document.getElementById('controlBadge').textContent);
  };

  const loadSchedules = async () => {
    try { const result = await Promise.all([request('/api/schedules'), request('/api/schedules/status')]); schedules = result[0] || []; renderSchedules(result[1]); }
    catch (error) { document.getElementById('scheduleStatus').textContent = describeError(error); notify(describeError(error), true); }
  };
  button.addEventListener('click', async () => { if (!state.playlists.length) await loadContent(); dialog.showModal(); await loadSchedules(); });
  dialog.querySelectorAll('[data-close-schedule],.dialog-head button').forEach(node => node.addEventListener('click', () => dialog.close()));
  document.getElementById('saveSchedule').addEventListener('click', async () => {
    if (!state.owner) return notify(tr('takeControl'), true);
    if (!state.playlists.length) return notify(tr('scheduleNoPlaylist'), true);
    const weekdays = [...dayContainer.querySelectorAll('input:checked')].map(node => Number(node.value));
    if (!weekdays.length) return notify(tr('scheduleDaysRequired'), true);
    const playlistId = document.getElementById('schedulePlaylist').value; if (!playlistId) return notify(tr('schedulePlaylistRequired'), true);
    const save = document.getElementById('saveSchedule'); save.disabled = true;
    try {
      await request('/api/schedules', { method: 'POST', body: { playlistId, weekdays, startMinute: timeToMinute(document.getElementById('scheduleStart').value), endMinute: timeToMinute(document.getElementById('scheduleEnd').value), priority: Number(document.getElementById('schedulePriority').value) || 0, enabled: document.getElementById('scheduleEnabled').checked } });
      notify(tr('scheduleSaved')); await loadSchedules();
    } catch (error) { notify(describeError(error), true); }
    finally { save.disabled = !state.owner; }
  });
  document.getElementById('syncSchedules').addEventListener('click', async () => {
    if (!state.owner) return notify(tr('takeControl'), true);
    const target = state.activeTarget || 'LOCAL';
    if (!target.startsWith('GROUP_')) return notify(tr('chooseGroupForSchedules'), true);
    const groupName = decodeURIComponent(target.slice(6));
    const deviceIds = state.paired.filter(device => device.groupName === groupName).map(device => device.deviceId);
    if (!deviceIds.length) return notify(tr('chooseGroupForSchedules'), true);
    const sync = document.getElementById('syncSchedules'); sync.disabled = true;
    try {
      const results = await request('/api/devices/sync-schedules', { method: 'POST', body: { deviceIds } });
      const synced = (results || []).filter(item => item.success).length;
      if (!synced) throw new Error((results || [])[0]?.code || tr('requestFailed'));
      notify(tr('schedulesSyncedToGroup', { count: synced }));
    } catch (error) { notify(describeError(error), true); }
    finally { sync.disabled = !state.owner; }
  });
  localize();
})();
