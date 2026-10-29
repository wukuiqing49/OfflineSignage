// 发布任务保留已创建内容及每台设备的阶段，重试不重新上传或打扰成功设备。
const PublishWorkflow = (() => {
  function targets(selection, local, paired) {
    if (selection === 'LOCAL') return [{ ...local, local: true }];
    if (selection === 'ALL') return [{ ...local, local: true }, ...paired];
    if (selection.startsWith('DEVICE_')) return paired.filter(d => d.deviceId === selection.slice(7));
    if (selection.startsWith('GROUP_')) {
      const group = decodeURIComponent(selection.slice(6));
      return paired.filter(d => d.groupName === group);
    }
    return [];
  }

  function create(content, devices) {
    if (!content.resourceId && !content.playlistId) throw new Error('PUBLISH_CONTENT_MISSING');
    if (!devices.length) throw new Error('PUBLISH_TARGET_MISSING');
    return {
      content: { ...content },
      rows: [...new Map(devices.map(device => [device.deviceId, device])).values()]
        .map(device => ({ ...device, stage: device.local ? 'SYNCED' : 'PENDING', error: '' }))
    };
  }

  async function run(job, transport, changed = () => {}) {
    const content = job.content;
    const body = content.playlistId ? { playlistId: content.playlistId } : { resourceId: content.resourceId };
    const syncPath = content.playlistId ? '/api/devices/sync-playlist' : '/api/devices/sync';
    const playPath = content.playlistId ? '/api/devices/play-playlist' : '/api/devices/play';

    async function phase(rows, path, successStage) {
      if (!rows.length) return;
      rows.forEach(row => { row.error = ''; row.stage = successStage === 'SYNCED' ? 'SYNCING' : 'STARTING'; });
      changed(job);
      try {
        const results = await transport(path, { ...body, deviceIds: rows.map(row => row.deviceId) });
        rows.forEach(row => {
          const result = Array.isArray(results) && results.find(item => item.deviceId === row.deviceId);
          row.stage = result?.success === true ? successStage : successStage === 'SYNCED' ? 'SYNC_FAILED' : 'PLAY_FAILED';
          row.error = result?.success === true ? '' : (result?.reason || result?.code || 'PUBLISH_RESULT_MISSING');
        });
      } catch (error) {
        rows.forEach(row => {
          row.stage = successStage === 'SYNCED' ? 'SYNC_FAILED' : 'PLAY_FAILED';
          row.error = error.code || error.message || 'PUBLISH_REQUEST_FAILED';
        });
      }
      changed(job);
    }

    await phase(job.rows.filter(row => !row.local && ['PENDING', 'SYNC_FAILED'].includes(row.stage)), syncPath, 'SYNCED');
    // 同步失败不会进入播放；已接受的设备不再收到新命令。
    await phase(job.rows.filter(row => !row.local && ['SYNCED', 'PLAY_FAILED'].includes(row.stage)), playPath, 'ACCEPTED');
    for (const row of job.rows.filter(row => row.local && row.stage !== 'ACCEPTED')) {
      row.stage = 'STARTING';
      row.error = '';
      changed(job);
      try {
        await transport('/api/control', { action: content.playlistId ? 'PLAY_PLAYLIST' : 'PLAY', ...body });
        row.stage = 'ACCEPTED';
      } catch (error) {
        row.stage = 'PLAY_FAILED';
        row.error = error.code || error.message || 'PUBLISH_REQUEST_FAILED';
      }
      changed(job);
    }
    return job.rows.every(row => row.stage === 'ACCEPTED');
  }
  return { targets, create, run };
})();
