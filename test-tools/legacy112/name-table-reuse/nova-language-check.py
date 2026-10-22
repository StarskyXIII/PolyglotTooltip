"""Install a candidate jar into the user's Nova Engineering instance, launch it through PrismLauncher, wait at the
main menu for the secondary-language preparation verdict in latest.log, then close the game window.

Usage: nova-language-check.py <jar> [timeout-seconds]
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
INSTANCE = os.environ.get('POLYGLOT_INSTANCE', 'Nova Engineering - World')  # any PrismLauncher instance name
GAME = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances') / INSTANCE / 'minecraft'
PRISM = Path(r'C:/Users/StarskyXIII/AppData/Local/Programs/PrismLauncher/prismlauncher.exe')
VERDICT = re.compile(r'\[polyglottooltip\]: (Secondary language parse matches .*|Secondary languages are unavailable .*)')
# A launch that never prepares is a verdict too: the product names the gate after a minute at the menu.
VERDICT = re.compile(VERDICT.pattern + r'|\[polyglottooltip\]: Secondary languages are still not prepared[^\n]*')
jar = Path(sys.argv[1]).resolve()
timeout = int(sys.argv[2]) if len(sys.argv) > 2 else 900

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            digest.update(part)
    return digest.hexdigest()

def nova_processes():
    query = ("@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and "
             "$_.CommandLine -like '*" + INSTANCE + "*' } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    text = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True).strip()
    if not text: return []
    value = json.loads(text)
    return value if isinstance(value, list) else [value]

assert PRISM.is_file() and GAME.is_dir() and jar.is_file()
assert not nova_processes(), 'Nova is already running'
target = GAME / 'mods' / 'polyglottooltip-1.12.2-forge-2.0.jar'
assert len(list((GAME / 'mods').glob('polyglottooltip-*.jar'))) == 1 and target.is_file()
before = sha(target)
if before != sha(jar):
    backup = GAME / 'mods' / ('polyglottooltip-1.12.2-forge-2.0.jar.' + before[:8] + '.bak')
    if not backup.exists(): shutil.copy2(target, backup)
    shutil.copy2(jar, target)
assert sha(target) == sha(jar)
log = GAME / 'logs' / 'latest.log'
previous = log.stat().st_mtime if log.is_file() else 0
started = time.time()
subprocess.Popen([str(PRISM), '--launch', INSTANCE], creationflags=subprocess.CREATE_NO_WINDOW, stdin=subprocess.DEVNULL,
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
verdict, extra = None, []
deadline = time.time() + timeout
while time.time() < deadline:
    time.sleep(3)
    if not log.is_file() or log.stat().st_mtime <= previous: continue
    text = log.read_text(encoding='utf-8', errors='replace')
    if not text.startswith('[') or 'Initialized Polyglot Tooltip' not in text:
        # Still the previous launch's file, or ours not initialised yet.
        if '[polyglottooltip]' not in text: continue
    hit = VERDICT.search(text)
    if hit:
        verdict = hit.group(0)
        extra = [line for line in text.splitlines() if '[polyglottooltip]' in line and ('language' in line.lower() or 'Secondary' in line or 'identity' in line or 'not prepared' in line)]
        break
    if 'Minecraft Crash Report' in text or '---- Minecraft Crash Report ----' in text:
        verdict = 'CRASH'; break
pids = nova_processes()
for pid in pids:
    subprocess.run(['taskkill', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(40):
    if not nova_processes(): break
    time.sleep(3)
else:
    for pid in nova_processes():
        subprocess.run(['taskkill', '/F', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print(json.dumps(dict(jar=sha(jar)[:16], launchedPids=pids, elapsedS=round(time.time() - started), verdict=verdict,
                      lines=[line[:600] for line in extra], closed=not nova_processes()), ensure_ascii=False, indent=1))
sys.exit(0 if verdict and verdict.startswith('[polyglottooltip]: Secondary language parse matches') else 2)
