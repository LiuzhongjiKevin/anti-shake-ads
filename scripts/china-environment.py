#!/usr/bin/env python3
"""Record emulator region configuration; never certify mainland ad coverage by locale alone."""
import json
import subprocess
import time
import urllib.request
from pathlib import Path


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30).decode().strip()


def main():
    out = Path('reports/china-environment')
    out.mkdir(parents=True, exist_ok=True)
    report = {'ad_test_performed': False, 'device_egress_verified': False}
    try:
        if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
            raise RuntimeError('This setup is for disposable Android emulators only')
        adb('root')
        adb('wait-for-device')
        adb('shell', 'setprop', 'persist.sys.locale', 'zh-CN')
        adb('shell', 'stop')
        time.sleep(5)
        adb('shell', 'setprop', 'sys.boot_completed', '0')
        adb('shell', 'start')
        deadline = time.monotonic() + 90
        while adb('shell', 'getprop', 'sys.boot_completed') != '1':
            if time.monotonic() > deadline:
                raise RuntimeError('Android framework did not restart in time')
            time.sleep(2)
        report['gps_command'] = adb('emu', 'geo', 'fix', '116.4074', '39.9042')
        report['locale'] = adb('shell', 'getprop', 'persist.sys.locale')
        report['timezone'] = adb('shell', 'getprop', 'persist.sys.timezone')
        report['fingerprint'] = adb('shell', 'getprop', 'ro.build.fingerprint')
        report['device_time'] = adb('shell', 'date')
        with urllib.request.urlopen('https://ipinfo.io/json', timeout=20) as response:
            network = json.load(response)
        report['runner_egress'] = {key: network.get(key) for key in ('ip', 'country', 'region', 'city', 'org')}
        report['regional_settings_ok'] = report['locale'] == 'zh-CN' and report['timezone'] == 'Asia/Shanghai'
        report['runner_country_cn'] = network.get('country') == 'CN'
        report['status'] = 'BLOCKED'
        report['reason'] = ('Runner network exit is outside mainland China' if not report['runner_country_cn']
                            else 'Device-side network exit still requires verification before ad testing')
        if not report['regional_settings_ok']:
            report['reason'] = 'Android locale/timezone verification failed'
        # A host IP is insufficient when device VPN/proxy routing differs. No bypass flag.
    except Exception as error:
        report.update(status='BLOCKED', reason=str(error))
    finally:
        (out / 'environment.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(1)


if __name__ == '__main__':
    main()
