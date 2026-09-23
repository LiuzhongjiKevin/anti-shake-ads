#!/usr/bin/env python3
"""Exercise committed debug APKs on an ephemeral Android emulator.

This intentionally uses only our synthetic source and target applications. It
never claims that a real third-party advertisement was served or clicked.
"""
import json
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree as ET

OUT = Path('reports/emulator')
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = 'cn.returnguard.fixture.source'
TARGET = 'cn.returnguard.fixture.target'
GUARD = 'cn.returnguard'


def adb(*args, timeout=35, binary=False):
    cmd = ['adb', *args]
    p = subprocess.run(cmd, check=True, stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, timeout=timeout)
    return p.stdout if binary else p.stdout.decode('utf-8', 'replace')


def screen(name):
    (OUT / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/guard-window.xml', timeout=40)
    raw = adb('shell', 'cat', '/sdcard/guard-window.xml')
    (OUT / 'last-window.xml').write_text(raw, encoding='utf-8')
    return ET.fromstring(raw)


def node_with(text):
    for node in hierarchy().iter('node'):
        if text in node.get('text', '') or text in node.get('content-desc', ''):
            return node
    return None


def tap_node(text, *, attempts=4):
    for n in range(attempts):
        node = node_with(text)
        if node is not None:
            match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
            if match:
                x1,y1,x2,y2 = map(int, match.groups())
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
        adb('shell', 'input', 'swipe', '500', '1200', '500', '350', '300')
    raise AssertionError(f'Could not find visible control containing: {text!r}')


def wait_node(text, timeout=40):
    end = time.monotonic()+timeout
    while time.monotonic()<end:
        node=node_with(text)
        if node is not None: return node
        time.sleep(0.5)
    raise AssertionError(f'UI did not show: {text!r}')


def events():
    try:
        xml=adb('shell','run-as',GUARD,'cat','shared_prefs/guard_events.xml')
    except subprocess.CalledProcessError as error:
        if b'No such file or directory' in error.stderr: return []
        raise
    root=ET.fromstring(xml)
    value=next((n.text for n in root.iter('string') if n.get('name')=='events'),'[]')
    return json.loads(value)


def current_pkg():
    text=adb('shell','dumpsys','window')
    match=re.search(r'mCurrentFocus=Window\{[^\n]+?\s([\w.]+)/',text)
    return match.group(1) if match else None


def test_jump(label,button):
    adb('shell','input','keyevent','KEYCODE_HOME')
    adb('shell','am','start','-n',SOURCE+'/.MainActivity')
    wait_node('模拟误点')
    before=hierarchy()
    field=next((n for n in before.iter('node') if '输入文字，返回后检查是否保留' in n.get('text','') or
                '输入文字，返回后检查是否保留' in n.get('content-desc','')),None)
    if field is not None:
        match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',field['bounds'])
        if match:
            x1,y1,x2,y2=map(int,match.groups())
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            adb('shell','input','text','cloudtest123')
            adb('shell','input','keyevent','KEYCODE_BACK')
    start_xml=ET.tostring(hierarchy(),encoding='unicode')
    instance=re.search('页面实例：([0-9a-f]+)',start_xml)
    assert instance, 'Source instance marker not visible'
    screen(label+'-before')
    old_len=len(events())
    tap_node(button)
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        if current_pkg()==SOURCE and len(events())>old_len: break
        time.sleep(.35)
    result=events()
    (OUT/f'{label}-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    screen(label+'-after')
    assert len(result)>old_len, f'No return event for {label}; current={current_pkg()}'
    assert current_pkg()==SOURCE, f'Wrong foreground after {label}: {current_pkg()}'
    assert result[0]['source']==SOURCE and result[0]['target']==TARGET, result[0]
    assert result[0]['result'].startswith('已回到原应用'), result[0]
    end_xml=ET.tostring(hierarchy(),encoding='unicode')
    assert instance.group(1) in end_xml, f'Activity was recreated after {label}'
    if field is not None: assert 'cloudtest123' in end_xml, f'Text not preserved after {label}'
    print(json.dumps({'case':label,'result':result[0], 'same_instance':True},ensure_ascii=False))


def main():
    for app in ['anti-shake-ads-0.1.0-test.apk','fixture-source.apk','fixture-target.apk']:
        adb('install','-r',str(Path('downloads')/app),timeout=120)
    # Test-only emulator configuration; a normal user enables the service in Settings.
    adb('shell','settings','put','secure','enabled_accessibility_services',GUARD+'/.GuardService')
    adb('shell','settings','put','secure','accessibility_enabled','1')
    adb('shell','am','start','-n',GUARD+'/.MainActivity')
    wait_node('开启跳转保护')
    print('Guard UI is visible',flush=True)
    screen('setup-before')
    tap_node('开启跳转保护')
    tap_node('选择保护应用')
    wait_node('搜索应用名称或包名')
    tap_node('搜索应用名称或包名')
    adb('shell','input','text','fixture')
    tap_node(SOURCE)
    tap_node('保存')
    wait_node('已选 1 个应用')
    tap_node('30秒')
    screen('setup-ready')
    test_jump('clicked','模拟误点：打开广告页')
    test_jump('delayed','模拟无点击跳转：1 秒后打开')
    print('Emulator integration smoke passed.')


if __name__=='__main__':
    try: main()
    except Exception:
        for name, command in [('window', ('shell','cat','/sdcard/guard-window.xml')),
                              ('activity', ('shell','dumpsys','activity','activities')),
                              ('logcat', ('logcat','-d','-t','500'))]:
            try:
                data=adb(*command, timeout=30)
                (OUT / f'failure-{name}.txt').write_text(data, encoding='utf-8')
                if name=='window': print('Visible UI:',data[:5000],flush=True)
                if name=='activity': print('Activity state:',data[-3500:],flush=True)
                if name=='logcat':
                    lines=[v for v in data.splitlines() if 'FATAL EXCEPTION' in v or 'cn.returnguard' in v or 'AndroidRuntime' in v]
                    print('Relevant logcat:', '\n'.join(lines[-65:]),flush=True)
            except Exception as error: print('Cannot collect', name, error,flush=True)
        try: screen('failure')
        except Exception: pass
        raise
