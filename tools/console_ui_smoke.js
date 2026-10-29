// Playwright browser_run_code_unsafe 的可复跑函数；仅使用本机 HTTP 与虚拟 API。
// 先运行 python -m http.server 18765 --bind 127.0.0.1 --directory feature/feature_app/src/main/res/raw
async (page) => {
  await page.unrouteAll();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    window.__CONTROL_TOKEN__ = 'fixture';
    window.__PAIRING_TOKEN__ = '';
    localStorage.setItem('localSignageLanguage', 'zh-CN');
    window.WebSocket = class { static OPEN = 1; constructor() { this.readyState = 0; } close() {} send() {} };
  });
  const paired = [{deviceId:'a',deviceName:'前台屏',groupName:'门店'}, {deviceId:'b',deviceName:'菜单屏',groupName:'门店'}];
  const calls = [];
  let fail = true;
  await page.route('**/api/**', async route => {
    const req = route.request(), pathname = new URL(req.url()).pathname, body = req.postDataJSON();
    calls.push({path:pathname,body});
    let data = [];
    if (pathname === '/api/status') data = {deviceId:'local',deviceName:'网关',playing:false,volume:50,commandRevision:0,commercialAccess:{mode:'PRO',advancedEnabled:true,multiDeviceEnabled:true}};
    else if (pathname === '/api/control/session/acquire') data = {sessionId:'fixture'};
    else if (pathname === '/api/devices/paired') data = paired;
    else if (pathname === '/api/devices/status') data = paired.map(d=>({...d,state:'ONLINE',playing:false,volume:50}));
    else if (pathname === '/api/resources/virtual') data = {id:'resource-fixture'};
    else if (pathname === '/api/devices/sync') data = body.deviceIds.map(id=>({deviceId:id,success:!(id==='b'&&fail),code:id==='b'&&fail?'DISK_FULL':'OK'}));
    else if (pathname === '/api/devices/play') { data=body.deviceIds.map(deviceId=>({deviceId,success:true})); fail=false; }
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
  });
  await page.goto('http://127.0.0.1:18765/web_console.html');
  await page.waitForFunction(() => state.owner && state.paired.length === 2);
  await page.evaluate(() => { state.activeTarget='GROUP_'+encodeURIComponent('门店');updateGlobalTargetOptions();showView('send');selectSendType('TEXT'); });
  await page.locator('#textContent').fill('今日营业');
  await page.locator('#sendContent').click();
  await page.waitForFunction(() => !state.sendInProgress && publicationJob?.rows.some(row=>row.stage==='SYNC_FAILED'));
  const first = await page.locator('#publicationResults').innerText();
  if (!first.includes('1/2')) throw new Error('Missing partial publication result');
  await page.locator('#retryPublication').click();
  await page.waitForFunction(() => !state.sendInProgress && publicationJob.rows.every(row=>row.stage==='ACCEPTED'));
  const publishCalls = calls.filter(c=>['/api/resources/virtual','/api/devices/sync','/api/devices/play','/api/control'].includes(c.path));
  if (publishCalls.filter(c=>c.path==='/api/resources/virtual').length!==1) throw new Error('Duplicate resource creation');
  if (publishCalls.some(c=>c.path==='/api/control')) throw new Error('Group changed gateway playback');
  const playTargets=publishCalls.filter(c=>c.path==='/api/devices/play').map(c=>c.body.deviceIds.join(','));
  if (playTargets.join('|')!=='a|b') throw new Error('Incorrect retry targets');
  const layouts=[];
  for (const width of [1440,390,320]) {
    await page.setViewportSize({width,height:900});
    await page.locator('#publicationResults').scrollIntoViewIfNeeded();
    const layout = await page.evaluate(() => ({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,resultWidth:document.getElementById('publicationResults').getBoundingClientRect().width}));
    if (layout.scrollWidth>layout.width+1) throw new Error('Horizontal overflow: '+JSON.stringify(layout));
    layouts.push(layout);
    await page.screenshot({path:'C:/work/AI/OfflineSignage/doc/testing/evidence/optimization-20261029/publish-'+width+'.png',fullPage:true});
  }
  await page.locator('#language').selectOption('en');
  if (!(await page.locator('#publicationResults').innerText()).includes('2/2 displays')) throw new Error('Result language not refreshed');
  const lostTarget = await page.evaluate(() => { state.activeTarget='DEVICE_missing';updateGlobalTargetOptions();return {target:state.activeTarget,value:document.getElementById('globalTargetSelect').value}; });
  if(lostTarget.target!=='DEVICE_missing'||lostTarget.value!=='DEVICE_missing')throw new Error('Missing target silently became local');
  await page.evaluate(() => {state.activeTarget='LOCAL';updateGlobalTargetOptions();showView('playback');});
  await page.screenshot({path:'C:/work/AI/OfflineSignage/doc/testing/evidence/optimization-20261029/playback-320.png',fullPage:true});
  if(errors.length)throw new Error(errors.join('\n'));
  return {errors,first,final:await page.locator('#publicationResults').innerText(),publishCalls,layouts,lostTarget};
}
