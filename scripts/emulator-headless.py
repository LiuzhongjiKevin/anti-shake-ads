#!/usr/bin/env python3
"""Cloud integration test without the emulator's unreliable UI dumper.

This configures only our own debug app on an ephemeral Android emulator.
Human installation must enable the AccessibilityService in Settings.
"""
import json
import re
import subprocess
import tempfile
import time
from pathlib import Path
from xml.etree import ElementTree as ET

OUT=Path('reports/emulator')
OUT.mkdir(parents=True,exist_ok=True)
GUARD='cn.returnguard'
SOURCE='cn.returnguard.fixture.source'
TARGET='cn.returnguard.fixture.target'

def adb(*args,timeout=35,binary=False):
    p=subprocess.run(['adb',*args],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
    return p.stdout if binary else p.stdout.decode('utf8','replace')

def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))

def events():
    try: raw=adb('shell','run-as',GUARD,'cat','shared_prefs/guard_events.xml')
    except subprocess.CalledProcessError as error:
        if b'No such file' in error.stderr: return []
        raise
    root=ET.fromstring(raw)
    value=next((v.text for v in root.iter('string') if v.get('name')=='events'),'[]')
    return json.loads(value)

def activity_record():
    raw=adb('shell','dumpsys','activity','activities')
    match=re.search(r'ActivityRecord\\{([a-z0-9]+) u0 '+re.escape(SOURCE)+r'/\\.MainActivity',raw)
    if not match:
        (OUT/'last-activity.txt').write_text(raw,encoding='utf8')
        raise AssertionError('Could not identify source ActivityRecord')
    return match.group(1)

def foreground():
    raw=adb('shell','dumpsys','window')
    match=re.search(r'mCurrentFocus=Window\\{[^\\n]+?\\s([\\w.]+)/',raw)
    return match.group(1) if match else None

def setup():
    for file in ['anti-shake-ads-0.1.0-test.apk','fixture-source.apk','fixture-target.apk']:
        adb('install','-r',str(Path('downloads')/file),timeout=120)
    # Create the main app's private prefs before the service is bound.
    xml=f'<map><boolean name="enabled" value="true"/><int name="seconds" value="30"/><set name="sources"><string>{SOURCE}</string></set></map>'
    with tempfile.NamedTemporaryFile(mode='w',encoding='utf8',delete=False) as f:
        f.write(xml)
        temp=f.name
    try:
        adb('push',temp,'/data/local/tmp/guard-settings.xml')
        adb('shell','chmod','644','/data/local/tmp/guard-settings.xml')
        adb('shell','run-as',GUARD,'mkdir','-p','shared_prefs')
        adb('shell','run-as',GUARD,'cp','/data/local/tmp/guard-settings.xml','shared_prefs/guard_settings.xml')
    finally:
        Path(temp).unlink(missing_ok=True)
    adb('shell','settings','put','secure','enabled_accessibility_services',GUARD+'/.GuardService')
    adb('shell','settings','put','secure','accessibility_enabled','1')
    for _ in range(30):
        state=adb('shell','dumpsys','accessibility')
        if 'Bound services:' in state and 'GuardService' in state.split('Bound services:',1)[1].split('Enabled services:',1)[0]:
            print('AccessibilityService bound',flush=True)
            screenshot('service-ready')
            return
        time.sleep(1)
    (OUT/'accessibility.txt').write_text(state,encoding='utf8')
    raise AssertionError('AccessibilityService did not bind')

def case(label,y):
    adb('shell','input','keyevent','KEYCODE_HOME')
    adb('shell','am','start','-n',SOURCE+'/.MainActivity')
    end=time.monotonic()+12
    while foreground()!=SOURCE:
        if time.monotonic()>end: raise AssertionError('Source did not open')
        time.sleep(.2)
    before=activity_record()
    old=len(events())
    screenshot(label+'-before')
    adb('shell','input','tap','160',str(y))
    end=time.monotonic()+20
    while time.monotonic()<end:
        now=events()
        if len(now)>old and foreground()==SOURCE: break
        time.sleep(.35)
    screenshot(label+'-after')
    rows=events()
    (OUT/(label+'-result.json')).write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf8')
    assert len(rows)>old,f'No return event for {label}; foreground={foreground()}'
    assert foreground()==SOURCE, f'Wrong foreground after {label}: {foreground()}'
    row=rows[0]
    assert row['source']==SOURCE and row['target']==TARGET,row
    assert row['result'].startswith('已回到原应用'),row
    after=activity_record()
    assert before==after,f'ActivityRecord changed: {before} -> {after}'
    print(json.dumps({'case':label,'result':row,'same_activity_record':True},ensure_ascii=False),flush=True)

def main():
    setup()
    # Coordinates on the 320x640 emulator, from the fixture's own XML layout.
    case('clicked',365)
    case('delayed',413)
    print('Cloud cross-app return integration passed.',flush=True)

if __name__=='__main__':
    try: main()
    except Exception:
        try: screenshot('failure')
        except Exception: pass
        try:
            (OUT/'failure-accessibility.txt').write_text(adb('shell','dumpsys','accessibility'),encoding='utf8')
        except Exception: pass
        raise
