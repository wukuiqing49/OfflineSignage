import http.client
import argparse, json, subprocess, time, urllib.request, urllib.error, xml.etree.ElementTree as ET, re, struct, zlib, uuid
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--device',required=True);p.add_argument('--port',type=int,default=18084);p.add_argument('--video',required=True);p.add_argument('--seconds',type=int,default=300);args=p.parse_args()
assert args.device.startswith('emulator-'), 'Virtual device required'
assert args.seconds >= 60, 'At least 60 seconds required'
out=Path('build/functional-test/mixed-media');out.mkdir(parents=True,exist_ok=True)
results=[];token='';session='';base=f'http://127.0.0.1:{args.port}'
def adb(*cmd,binary=False):
 r=subprocess.run(['adb','-s',args.device,*cmd],capture_output=True,timeout=30,check=True)
 return r.stdout if binary else r.stdout.decode('utf-8',errors='replace')
def mark(name):
 results.append({'case':name,'status':'PASS'});print('PASS',name,flush=True)
 (out/'report.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
def request(path,method='GET',body=None,content_type='application/json',retry=True):
 global session
 headers={'Content-Type':content_type}
 if token:headers['X-Local-Signage-Token']=token
 if session:headers['X-Control-Session']=session
 data=json.dumps(body).encode() if isinstance(body,(dict,list)) else body
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=data,headers=headers,method=method),timeout=20) as r:
   raw=r.read();return json.loads(raw) if raw else None
 except urllib.error.HTTPError as e:
  if e.code==409 and retry and token and '/session/' not in path:
   session=request('/api/control/session/acquire','POST',{'clientName':'Virtual smoke','takeover':True},retry=False)['sessionId']
   return request(path,method,body,content_type,False)
  raise RuntimeError(f'HTTP {e.code} for {path}') from None
def tree():
 adb('shell','uiautomator','dump','/sdcard/signage-smoke.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/signage-smoke.xml'))
def node(name):
 return next((x for x in tree().iter('node') if x.get('resource-id','').endswith('/'+name)),None)
def click(n):
 assert n is not None
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def shot(name):
 (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def wait_state(predicate,timeout=15):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  try:
   s=request('/api/status')
   if predicate(s):return s
  except (RuntimeError,urllib.error.URLError,http.client.RemoteDisconnected,ConnectionError):pass
  time.sleep(.4)
 raise AssertionError('Playback state did not converge')
def png(rgb):
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 raw=(b'\x00'+bytes(rgb)*320)*180
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',320,180,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')

def upload(name,content,mime):
 boundary='SignageTest'+uuid.uuid4().hex
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n').encode()+content+f'\r\n--{boundary}--\r\n'.encode()
 return request('/api/resources/upload?play=false','POST',body,'multipart/form-data; boundary='+boundary)['ids'][0]

def snapshot(name):
 shot(name)
 from PIL import Image
 im=Image.open(out/(name+'.png')).convert('RGB')
 crop=im.crop((im.width//4,im.height//4,im.width*3//4,im.height*3//4))
 return crop

def dismiss_system_ui_anr():
 nodes=list(tree().iter('node'))
 if any(n.get('text')=="System UI isn't responding" for n in nodes):
  close=next(n for n in nodes if n.get('resource-id')=='android:id/aerr_close')
  click(close)
  with (out/'environment-events.log').open('a',encoding='utf-8') as f:f.write('Dismissed emulator System UI ANR; not an application crash\n')
  time.sleep(3)

created=[];playlist=None;initial=None;initial_playlists=[]
try:
 avd=adb('emu','avd','name').splitlines()[0].strip()
 assert avd=='localsignage_ui_20261021', 'Use the dedicated disposable signage AVD only'
 adb('forward',f'tcp:{args.port}','tcp:8080')
 dismiss_system_ui_anr()
 adb('shell','am','start','-n','com.wkq.localsignage/.SplashActivity')
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  start=node('start_button')
  if start is not None:click(start)
  code=node('pairing_access_code')
  if code is not None:break
  time.sleep(2)
 assert code is not None, 'Open connection screen before running'
 code=re.sub(r'\s','',code.get('text',''));assert re.fullmatch(r'\d{6}',code)
 token=request('/api/access/exchange','POST',{'pairingCode':code})['accessToken']
 session=request('/api/control/session/acquire','POST',{'clientName':'Mixed media regression','takeover':True})['sessionId']
 assert not request('/api/resources'), 'Dedicated AVD must contain no preexisting content'
 initial=request('/api/settings');initial_playlists=request('/api/playlists')
 request('/api/settings','POST',{'autoResume':True})
 video=upload('motion.mp4',Path(args.video).read_bytes(),'video/mp4');created.append(video)
 picture=upload('green.png',png((24,118,82)),'image/png');created.append(picture)
 html=request('/api/resources/virtual','POST',{'name':'Offline HTML fixture','kind':'WEB','play':False,'content':'<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><body style="margin:0;background:#224477;color:white;display:grid;place-items:center;height:100vh;font:48px sans-serif"><main>LOCAL HTML <span id="tick">0</span></main><script>setInterval(()=>document.getElementById("tick").textContent=Number(document.getElementById("tick").textContent)+1,500)</script></body></html>'})['id'];created.append(html)
 mark('create-video-image-html')
 request('/api/control','POST',{'action':'PLAY','resourceId':video})
 wait_state(lambda s:s['currentResourceId']==video and s['playing'] and s['positionMs']>500,30)
 a=snapshot('video-frame-a');time.sleep(2);b=snapshot('video-frame-b')
 from PIL import ImageChops,ImageStat
 assert sum(ImageStat.Stat(ImageChops.difference(a,b)).mean)>3,'Video pixels did not advance'
 mark('video-decoded-moving-frames')
 request('/api/control','POST',{'action':'PLAY','resourceId':html})
 wait_state(lambda s:s['currentResourceId']==html and s['playing']);time.sleep(3)
 shot('html-observed')
 for attempt in range(4):
  adb('shell','uiautomator','dump','/sdcard/signage-html.xml')
  html_tree=adb('shell','cat','/sdcard/signage-html.xml')
  if 'LOCAL HTML' in html_tree:break
  time.sleep(2)
 (out/'html-tree.xml').write_text(html_tree,encoding='utf-8')
 assert 'LOCAL HTML' in html_tree, 'HTML text not available after render wait'
 a=snapshot('html-frame-a');time.sleep(2);b=snapshot('html-frame-b')
 assert sum(ImageStat.Stat(ImageChops.difference(a,b)).mean)>.01,'HTML counter did not advance'
 mark('html-rendered-javascript-running')
 scenes=[s for s in request('/api/scenes') if s['resourceId'] in created]
 assert len(scenes)==3
 playlist=request('/api/playlists','POST',{'name':'Mixed media regression','loop':True,'items':[{'sceneId':s['id'],'durationMs':6000} for s in scenes]})
 request('/api/control','POST',{'action':'PLAY','playlistId':playlist['id']})
 adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
 seen=set();deadline=time.monotonic()+45
 while time.monotonic()<deadline and len(seen)<3:
  s=request('/api/status');assert s['playing'] and not s.get('error'), 'Mixed playback error'
  seen.add(s['currentResourceId']);time.sleep(1)
 assert seen==set(created), 'Not all media types played offline'
 mark('offline-three-media-cycle')
 adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
 # 真正重启系统；Android15+需用户重新打开，不能用启动 Activity 冒充开机自启。
 adb('reboot');time.sleep(5)
 deadline=time.monotonic()+180
 while time.monotonic()<deadline:
  try:
   if adb('shell','getprop','sys.boot_completed').strip()=='1':break
  except subprocess.CalledProcessError:pass
  time.sleep(3)
 else:raise AssertionError('Emulator reboot timeout')
 adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard');time.sleep(5)
 dismiss_system_ui_anr()
 deadline=time.monotonic()+120
 while time.monotonic()<deadline:
  logs=adb('logcat','-d','-s','SignageBootReceiver:I','AndroidRuntime:E','*:S')
  if 'defers media playback restore' in logs:break
  time.sleep(3)
 (out/'boot-policy.log').write_text(logs,encoding='utf-8')
 assert 'defers media playback restore' in logs,'Expected Android 15+ boot deferral log'
 mark('system-reboot-defers-playback-until-launch')
 adb('forward',f'tcp:{args.port}','tcp:8080')
 adb('shell','am','start','-n','com.wkq.localsignage/.SplashActivity')
 wait_state(lambda s:s['playing'] and s['currentPlaylistId']==playlist['id'],45)
 mark('post-reboot-launch-restores-playlist')
 start=time.monotonic();seen=set();transitions=0;previous=None;samples=0
 pid=adb('shell','pidof','com.wkq.localsignage').strip()
 (out/'memory-start.txt').write_text(adb('shell','dumpsys','meminfo','com.wkq.localsignage'),encoding='utf-8')
 while time.monotonic()-start<args.seconds:
  s=request('/api/status');assert s['playing'] and not s.get('error') and s['currentPlaylistId']==playlist['id'],'Soak playback state failed'
  current=s['currentResourceId'];seen.add(current)
  if current!=previous:transitions+=1;previous=current
  samples+=1;time.sleep(2)
 assert seen==set(created) and transitions>=args.seconds//20,'Playback stopped cycling'
 assert adb('shell','pidof','com.wkq.localsignage').strip()==pid,'App process restarted during soak'
 (out/'memory-end.txt').write_text(adb('shell','dumpsys','meminfo','com.wkq.localsignage'),encoding='utf-8')
 (out/'soak-summary.json').write_text(json.dumps({'seconds':round(time.monotonic()-start),'samples':samples,'transitions':transitions,'mediaTypes':3,'sameProcess':True},indent=2),encoding='utf-8')
 mark('bounded-mixed-media-soak')
except Exception as exc:
 results.append({'case':'execution','status':'FAIL','error':str(exc)});print('FAIL',str(exc),flush=True)
 (out/'report.json').write_text(json.dumps(results,indent=2),encoding='utf-8');raise
finally:
 adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
 if initial is not None:
  try:
   request('/api/control','POST',{'action':'STOP'})
   if playlist:request('/api/playlists/'+playlist['id'],'DELETE')
   for old in initial_playlists:request('/api/playlists','POST',old)
   for scene in request('/api/scenes'):
    if scene['resourceId'] in created:request('/api/scenes/'+scene['id'],'DELETE')
   for resource in created:request('/api/resources/'+resource,'DELETE')
   request('/api/settings','POST',initial);mark('test-data-cleanup')
  except Exception as exc:
   results.append({'case':'test-data-cleanup','status':'FAIL','error':type(exc).__name__})
   (out/'report.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
   print('CLEANUP FAILED',type(exc).__name__,flush=True)
   raise
 subprocess.run(['adb','-s',args.device,'forward','--remove',f'tcp:{args.port}'],capture_output=True)
