(() => {
  Object.assign(messages, {
    exportProject: ['导出完整项目', 'Export project'],
    exportingProject: ['正在导出项目备份…', 'Exporting project backup...'],
    projectExported: ['项目备份已开始下载', 'Project backup download started']
  });
  const exportProjectButton = document.createElement('button');
  exportProjectButton.id = 'exportProject';
  exportProjectButton.dataset.write = 'true';
  exportProjectButton.dataset.i18n = 'exportProject';
  exportProjectButton.textContent = tr('exportProject');
  document.querySelector('#contentView .page-actions').appendChild(exportProjectButton);
  exportProjectButton.addEventListener('click', async () => {
    try {
      exportProjectButton.disabled = true;
      notify(tr('exportingProject'));
      const response = await fetch('/api/project-backup/export', { headers: apiHeaders(), cache: 'no-store' });
      if (!response.ok) {
        const data = await response.json().catch(() => null);
        const error = new Error(data?.error?.message || tr('requestFailed'));
        error.code = data?.error?.code;
        throw error;
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'local-signage-project.zip';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      notify(tr('projectExported'));
    } catch (error) { notify(describeError(error), true); }
    finally { exportProjectButton.disabled = !state.owner; }
  });
  localize();
})();
