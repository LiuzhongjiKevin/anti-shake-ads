#!/usr/bin/env python3
import subprocess,time,threading,tarfile,io,json,os
from pathlib import Path
OUT=Path('reports/baozi');OUT.mkdir(parents=True,exist_ok=True)
def adb(*args,timeout=45,binary=False):
 p=subprocess.run(['adb',*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout,check=True)
 return p.stdout if binary else p.stdout.decode('utf-8','replace')
pkg=os.environ['BAOZI_PACKAGE']
guard='cn.returnguard'
for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','downloads/baozi.apk']:
 print(adb('install','-r',apk,timeout=180),flush=True)
setup=adb('shell','am','instrument','-w','-e','mode','setup','-e','package',pkg,'cn.returnguard.test/cn.returnguard.BaoziCapture',timeout=60)
print(setup,flush=True)
assert 'CAPTURE_SETUP_OK' in setup
adb('shell','settings','put','secure','enabled_accessibility_services','cn.returnguard/.GuardService')
adb('shell','settings','put','secure','accessibility_enabled','1')
adb('shell','am','start','-W','-n','cn.returnguard/.MainActivity')
time.sleep(3)
(OUT/'guard-before.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
(OUT/'accessibility-before.txt').write_text(adb('shell','dumpsys','accessibility'))
shake=[]
def shake_loop():
 start=time.monotonic()
 for target in [20,35,50]:
  time.sleep(max(0,start+target-time.monotonic()))
  for x in [18,-18,18,-18,0]:
   response=adb('emu','sensor','set','acceleration',f'{x}:0:9.81').strip()
   shake.append({'elapsed_s':round(time.monotonic()-start,3),'x':x,'response':response})
   time.sleep(.12)
thread=threading.Thread(target=shake_loop);thread.start()
try:
 output=adb('shell','am','instrument','-w','-e','package',pkg,'cn.returnguard.test/cn.returnguard.BaoziCapture',timeout=130)
 print(output,flush=True);(OUT/'instrumentation.txt').write_text(output)
finally:
 thread.join()
 data=adb('exec-out','run-as',guard,'tar','-cf','-','files/baozi-capture',binary=True)
 with tarfile.open(fileobj=io.BytesIO(data)) as t:
  for item in t:
   if item.isfile():
    name=Path(item.name).name
    (OUT/name).write_bytes(t.extractfile(item).read())
 (OUT/'shakes.json').write_text(json.dumps(shake,indent=2))
 (OUT/'accessibility-after.txt').write_text(adb('shell','dumpsys','accessibility'))
 (OUT/'activities-after.txt').write_text(adb('shell','dumpsys','activity','activities'))
 (OUT/'logcat.txt').write_text(adb('logcat','-d','-t','3000'))
 (OUT/'guard-after.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
assert 'CAPTURE_OBSERVATION_OK' in output
frames=json.loads((OUT/'frames.json').read_text())
print('CAPTURE_SUMMARY',json.dumps({'frames':len(frames),'guard_connected_all':all(f['guard_connected'] for f in frames),'foreground_service_all':all(f['foreground_service'] for f in frames),'elapsed_ms':[f['actual_ms'] for f in frames]}),flush=True)

assert all(f["guard_connected"] and f["foreground_service"] and f["guard_enabled"] for f in frames),"Guard was not continuously active; inspect frame flags"
