const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../feature/feature_app/src/main/web/publish-workflow.js'), 'utf8');
const workflow = vm.runInNewContext(source + '\nPublishWorkflow');
const devices = [{deviceId:'a',deviceName:'A',groupName:'shop'}, {deviceId:'b',deviceName:'B',groupName:'shop'}];
const ok = ids => ids.map(deviceId => ({deviceId,success:true}));

test('group targets exclude gateway; empty group never falls back to all', () => {
  assert.deepEqual(Array.from(workflow.targets('GROUP_shop',{deviceId:'local'},devices), d=>d.deviceId), ['a','b']);
  assert.equal(workflow.targets('GROUP_missing',{deviceId:'local'},devices).length,0);
  assert.equal(workflow.targets('ALL',{deviceId:'local'},devices).length,3);
});
test('sync failures never receive play; retry reuses content and skips successes', async () => {
  const job = workflow.create({resourceId:'r'},devices);
  const calls=[];
  const first = await workflow.run(job, async (url, body) => {
    calls.push({url, ids:Array.from(body.deviceIds), resourceId:body.resourceId});
    return url.endsWith('/sync') ? [{deviceId:'a',success:true},{deviceId:'b',success:false,code:'DISK_FULL'}] : ok(body.deviceIds);
  });
  assert.equal(first,false);
  assert.deepEqual(calls[1].ids,['a']);
  calls.length=0;
  assert.equal(await workflow.run(job,async (url,body)=>{calls.push({url,ids:Array.from(body.deviceIds),resourceId:body.resourceId});return ok(body.deviceIds)}),true);
  assert.deepEqual(calls.map(c=>c.ids),[['b'],['b']]);
  assert.ok(calls.every(c=>c.resourceId==='r'));
});
test('missing or malformed result is failure, never silent success', async () => {
  for(const response of [null,{},[],[{deviceId:'other',success:true}]]) {
    const job=workflow.create({resourceId:'r'},[devices[0]]);
    let calls=0;
    assert.equal(await workflow.run(job,async()=>{calls++;return response}),false);
    assert.equal(calls,1);
    assert.equal(job.rows[0].error,'PUBLISH_RESULT_MISSING');
  }
});
test('network failure retains pending devices for retry', async () => {
  const job=workflow.create({resourceId:'r'},devices);
  assert.equal(await workflow.run(job,async()=>{throw new Error('offline')}),false);
  assert.ok(job.rows.every(r=>r.stage==='SYNC_FAILED'));
  assert.equal(await workflow.run(job,async(_,b)=>ok(b.deviceIds)),true);
});
test('play failure retries play only, without resync or reupload',async()=>{
  const job=workflow.create({playlistId:'p'},[devices[0]]);
  await workflow.run(job,async(url,b)=>url.endsWith('sync-playlist')?ok(b.deviceIds):[]);
  const paths=[];
  await workflow.run(job,async(url,b)=>{paths.push(url);assert.equal(b.playlistId,'p');return ok(b.deviceIds)});
  assert.deepEqual(paths,['/api/devices/play-playlist']);
});
test('local publication uses explicit playlist command and propagates rejection',async()=>{
  const job=workflow.create({playlistId:'p'},[{deviceId:'local',local:true}]);
  assert.equal(await workflow.run(job,async(url,b)=>{assert.equal(url,'/api/control');assert.equal(b.action,'PLAY_PLAYLIST');throw new Error('COMMAND_REJECTED')}),false);
  assert.equal(job.rows[0].stage,'PLAY_FAILED');
  assert.equal(await workflow.run(job,async()=>({})),true);
});
test('invalid content or empty target rejected before any commands',()=>{
  assert.throws(()=>workflow.create({},devices),/CONTENT_MISSING/);
  assert.throws(()=>workflow.create({resourceId:'r'},[]),/TARGET_MISSING/);
});
test('targets are snapshotted and duplicate devices execute once',async()=>{
  const job=workflow.create({resourceId:'r'},[devices[0],devices[0]]);
  assert.equal(job.rows.length,1);
  job.rows[0].deviceName='changed';
  assert.equal(devices[0].deviceName,'A');
  await workflow.run(job,async(_,b)=>ok(b.deviceIds));
  let calls=0;
  await workflow.run(job,async()=>{calls++});
  assert.equal(calls,0);
});

test('fleet control reports partial HTTP 200 failures and missing device results',async()=>{
  const consoleSource=fs.readFileSync(path.join(__dirname,'../feature/feature_app/src/main/web/console.js'),'utf8');
  const fn=consoleSource.match(/async function fleetAction\([\s\S]*?\n\}/)[0];
  for(const results of [[{deviceId:'a',success:true},{deviceId:'b',success:false,code:'OFFLINE'}],[{deviceId:'a',success:true}]]) {
    const action=vm.runInNewContext(fn+'\nfleetAction',{request:async()=>results,state:{paired:devices},tr:key=>key});
    await assert.rejects(action('pause',['a','b']),/B:/);
  }
});
