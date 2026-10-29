(() => {
  Object.assign(messages, {
    showAttentionDevices: ['仅看需关注设备', 'Show devices needing attention'],
    showAllDevices: ['显示全部设备', 'Show all devices'],
    noAttentionDevices: ['当前没有需要处理的设备。', 'No devices currently need attention.']
  });
  state.showOnlyAttentionDevices = false;
  const deviceHead = document.querySelector('#devicesView .surface-head');
  if (!deviceHead) return;
  const filterButton = document.createElement('button');
  filterButton.type = 'button';
  const label = () => filterButton.textContent = tr(state.showOnlyAttentionDevices ? 'showAllDevices' : 'showAttentionDevices');
  label();
  const baseRenderDevices = renderDevices;
  renderDevices = function () {
    baseRenderDevices();
    const rows = [...document.querySelectorAll('#deviceList .device-row')];
    const localIssue = false;
    const issues = [localIssue, ...state.paired.map(device => {
      const status = state.fleet.find(value => value.deviceId === device.deviceId);
      const assignment = state.assignments.find(value => value.deviceId === device.deviceId);
      return status?.state !== 'ONLINE' || assignment?.state === 'FAILED' || !!status?.error;
    })];
    rows.forEach((row, index) => row.hidden = state.showOnlyAttentionDevices && !issues[index]);
    if (state.showOnlyAttentionDevices && !issues.some(Boolean)) {
      const empty = document.createElement('div');
      empty.className = 'empty'; empty.textContent = tr('noAttentionDevices');
      document.getElementById('deviceList').appendChild(empty);
    }
  };
  filterButton.addEventListener('click', () => { state.showOnlyAttentionDevices = !state.showOnlyAttentionDevices; label(); renderDevices(); });
  deviceHead.appendChild(filterButton);
  localize();
})();
