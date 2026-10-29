(() => {
  Object.assign(messages, {
    deviceGroup: ['设备分组', 'Device group'],
    deviceGroupHint: ['例如：一楼、咖啡区或会议室', 'For example: Floor 1, Cafe, or Meeting rooms'],
    saveGroup: ['保存分组', 'Save group'],
    groupSaved: ['设备分组已保存', 'Device group saved'],
    deviceGroups: ['设备分组', 'Device groups'],
    groupScreens: ['{name}（{count} 台）', '{name} ({count} screens)'],
    groupControl: ['控制设备组', 'Control device group']
  });

  const groupSection = document.createElement('section');
  groupSection.className = 'dialog-section';
  groupSection.innerHTML = '<h3 data-i18n="deviceGroup">设备分组</h3><label><span data-i18n="deviceGroup">设备分组</span><input id="remoteDeviceGroup" maxlength="60" data-write><small data-i18n="deviceGroupHint">例如：一楼、咖啡区或会议室</small></label><button id="saveRemoteDeviceGroup" data-write data-i18n="saveGroup">保存分组</button>';
  const remoteDialogBody = document.querySelector('#remoteDeviceDialog .dialog-body');
  remoteDialogBody.insertBefore(groupSection, remoteDialogBody.children[1]);

  const baseOpenRemoteDevice = openRemoteDevice;
  openRemoteDevice = function (deviceId) {
    baseOpenRemoteDevice(deviceId);
    document.getElementById('remoteDeviceGroup').value = state.paired.find(device => device.deviceId === deviceId)?.groupName || '';
  };
  document.getElementById('saveRemoteDeviceGroup').addEventListener('click', async () => {
    if (isFreeLimited()) { showProDialog(); return; }
    const deviceId = state.selectedDeviceId;
    if (!deviceId) return;
    try {
      const saved = await request('/api/devices/paired/' + encodeURIComponent(deviceId) + '/group', {
        method: 'PUT', body: { groupName: document.getElementById('remoteDeviceGroup').value.trim() || null }
      });
      state.paired = state.paired.map(device => device.deviceId === saved.deviceId ? saved : device);
      updateGlobalTargetOptions();
      renderDevices();
      notify(tr('groupSaved'));
    } catch (error) { notify(describeError(error), true); }
  });

  const baseTargetOptions = updateGlobalTargetOptions;
  updateGlobalTargetOptions = function () {
    const wanted = state.activeTarget || 'LOCAL';
    baseTargetOptions();
    const groups = [...new Set(state.paired.map(device => device.groupName).filter(Boolean))].sort((a, b) => a.localeCompare(b));
    const addGroups = select => {
      if (!select) return;
      groups.forEach(groupName => {
        const count = state.paired.filter(device => device.groupName === groupName).length;
        const option = document.createElement('option');
        option.value = 'GROUP_' + encodeURIComponent(groupName);
        option.textContent = '▣ ' + tr('groupScreens', { name: groupName, count });
        select.appendChild(option);
      });
    };
    addGroups(document.getElementById('globalTargetSelect'));
    addGroups(document.getElementById('sendTargetSelect'));
    if ([...document.getElementById('globalTargetSelect').options].some(option => option.value === wanted)) {
      state.activeTarget = wanted;
      document.getElementById('globalTargetSelect').value = wanted;
      const sendTarget = document.getElementById('sendTargetSelect');
      if (sendTarget) sendTarget.value = wanted;
      updateActiveTargetUI();
    }
  };

  const baseUpdateTarget = updateActiveTargetUI;
  updateActiveTargetUI = function () {
    const target = state.activeTarget || 'LOCAL';
    if (!target.startsWith('GROUP_')) return baseUpdateTarget();
    const groupName = decodeURIComponent(target.slice(6));
    const members = state.paired.filter(device => device.groupName === groupName);
    const online = members.filter(device => state.fleet.find(status => status.deviceId === device.deviceId)?.state === 'ONLINE').length;
    document.getElementById('playDeviceName').textContent = '▣ ' + groupName;
    document.getElementById('playAddress').textContent = tr('groupScreens', { name: groupName, count: members.length });
    document.getElementById('playState').textContent = online === members.length ? tr('online') : tr('attention');
    document.getElementById('playDot').className = 'dot ' + (online === members.length ? 'online' : 'warning');
    document.getElementById('currentName').textContent = groupName;
    document.getElementById('currentKind').textContent = tr('deviceGroups');
    document.getElementById('currentDetail').textContent = tr('broadcastStatus', { online, total: members.length, playing: 0 });
    currentPreviewPlaceholder('▣', groupName, tr('groupScreens', { name: groupName, count: members.length }));
  };

  const baseExecuteActiveCommand = executeActiveCommand;
  executeActiveCommand = async function (action, value = null) {
    const target = state.activeTarget || 'LOCAL';
    if (!target.startsWith('GROUP_')) return baseExecuteActiveCommand(action, value);
    if (!state.owner) return notify(tr('takeControl'), true);
    const groupName = decodeURIComponent(target.slice(6));
    const deviceIds = state.paired.filter(device => device.groupName === groupName).map(device => device.deviceId);
    if (!deviceIds.length) return notify(tr('selectDevice'), true);
    try {
      await fleetAction(action, deviceIds, value);
      notify(tr('groupControl'));
      await loadDevices();
      updateActiveTargetUI();
    } catch (error) { notify(describeError(error), true); }
  };
  localize();
})();
