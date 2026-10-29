(() => {
  Object.assign(messages, {
    inspectProject: ['检查项目备份', 'Inspect project backup'],
    importProject: ['恢复项目备份', 'Restore project backup'],
    chooseProjectBackup: ['选择项目 ZIP 备份', 'Choose project ZIP backup'],
    inspectingProject: ['正在检查备份包…', 'Inspecting backup...'],
    importingProject: ['正在恢复项目备份…', 'Restoring project backup...'],
    backupValid: ['备份包有效：{resources} 个资源、{scenes} 个场景、{playlists} 个播放列表。', 'Backup is valid: {resources} resources, {scenes} scenes, {playlists} playlists.'],
    backupImported: ['项目已恢复：{resources} 个资源、{scenes} 个场景、{playlists} 个播放列表已追加到当前项目。', 'Project restored: {resources} resources, {scenes} scenes, and {playlists} playlists were added to the current project.'],
    confirmProjectImport: ['恢复会将内容追加到当前项目，不会覆盖已有内容。继续吗？', 'Restore will add content to the current project without replacing existing content. Continue?']
  });
  const backupInput = document.createElement('input');
  backupInput.id = 'projectBackupFile';
  backupInput.type = 'file';
  backupInput.accept = '.zip,application/zip';
  backupInput.className = 'hidden';
  const inspectButton = document.createElement('button');
  inspectButton.id = 'inspectProject';
  inspectButton.dataset.write = 'true';
  inspectButton.dataset.i18n = 'inspectProject';
  inspectButton.textContent = tr('inspectProject');
  const importButton = document.createElement('button');
  importButton.id = 'importProject';
  importButton.dataset.write = 'true';
  importButton.dataset.i18n = 'importProject';
  importButton.textContent = tr('importProject');
  document.querySelector('#contentView .page-actions').append(backupInput, inspectButton, importButton);
  let backupAction = 'inspect';
  inspectButton.addEventListener('click', () => { backupAction = 'inspect'; backupInput.click(); });
  importButton.addEventListener('click', () => { backupAction = 'import'; backupInput.click(); });
  backupInput.addEventListener('change', async () => {
    const file = backupInput.files[0];
    if (!file) return;
    try {
      inspectButton.disabled = true;
      importButton.disabled = true;
      notify(tr('inspectingProject'));
      const form = new FormData();
      form.append('file', file, file.name);
      const response = await fetch('/api/project-backup/inspect', { method: 'POST', headers: apiHeaders(), body: form, cache: 'no-store' });
      const data = await response.json().catch(() => null);
      if (!response.ok || !data?.valid) {
        const error = new Error(data?.error?.message || tr('requestFailed'));
        error.code = data?.error?.code;
        throw error;
      }
      notify(tr('backupValid', data));
      if (backupAction !== 'import') return;
      if (!confirm(tr('confirmProjectImport'))) return;
      notify(tr('importingProject'));
      const restoreForm = new FormData();
      restoreForm.append('file', file, file.name);
      const restored = await fetch('/api/project-backup/import', { method: 'POST', headers: apiHeaders(), body: restoreForm, cache: 'no-store' });
      const restoreData = await restored.json().catch(() => null);
      if (!restored.ok || !restoreData?.imported) {
        const error = new Error(restoreData?.error?.message || tr('requestFailed'));
        error.code = restoreData?.error?.code;
        throw error;
      }
      await refresh();
      notify(tr('backupImported', restoreData));
    } catch (error) { notify(describeError(error), true); }
    finally { inspectButton.disabled = !state.owner; importButton.disabled = !state.owner; backupInput.value = ''; }
  });
  localize();
})();
