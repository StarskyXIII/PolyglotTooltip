"""No-bridge play check: launch a user's PrismLauncher instance exactly as it is played, with only the candidate jar
swapped in, wait at the main menu for the secondary-language verdict, then read every polyglottooltip WARN/ERROR in
latest.log and fail on any of them.

Bridged checks (nova-table-check.py + nova-de-check.mjs / e2ee-check.mjs) install the research bridge, which also
patches ItemStack: a method shape measured there is not the shape the player gets. Every jar that goes into a user's
instance therefore needs one run of this script first; the bridged checks read its result (play-launch.mjs) and list
'play-launch-unchecked' or 'play-launch-warnings' under summary.failed.

Usage: play-check.py <jar> <attempt> [--keep] [--settle SECONDS] [--timeout SECONDS] [--hide=JAR --expect-missing MODID]
  POLYGLOT_INSTANCE  PrismLauncher instance name (default: Nova Engineering - World)
  --keep             leave the candidate installed (the previous jar stays as mods/<name>.<sha8>.bak); default restores it
  --hide=JAR         move mods/JAR into the run folder for this launch and put it back afterwards (write it with '=':
                     MixinBooter's file name starts with '-')
  --expect-missing   the verdict is Forge's missing-mods error naming MODID for the product instead of the language
                     line: the launch must reach that screen without a crash report (pack without MixinBooter)
Exit: 0 parse matches (or the expected missing-mods error) and no blocking WARN, 2 no parse match, 3 crash or no launch,
4 blocking WARN lines.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
INSTANCE = os.environ.get('POLYGLOT_INSTANCE', 'Nova Engineering - World')
ROOT = Path(__file__).resolve().parents[3]
GAME = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances') / INSTANCE / 'minecraft'
PRISM = Path.home() / '.local' / 'bin' / 'prism-agent-launch.exe'
REPORT = ROOT / 'docs/research/legacy112/name-table-ic2-injection-2026-10-17'
PRODUCT_CLASS = 'com/starskyxiii/polyglottooltip/legacy/PolyglotTooltip.class'
MISSING = 'MissingModsException: Mod polyglottooltip (Polyglot Tooltip) requires [{}]'
VERDICT = re.compile(r'\[polyglottooltip\]: (Secondary language parse matches .*|Secondary languages are unavailable .*'
                     r'|Secondary languages are still not prepared[^\n]*)')
# A cold PrismLauncher start (no Prism running) took 116 s before it spawned Java on MeatballCraft; a game that has not
# appeared by then has not crashed yet.
START_GRACE = 300
LINE = re.compile(r'^\[[0-9:]+\] \[([^\]]*)/([A-Z]+)\] \[([^\]]*)\]: (.*)$')
# Every WARN/ERROR of the product is put in one category; all but the accepted ones fail the check.
CATEGORIES = [
    ('enchantment-tooltip-source', r'^Enchantment rows on item tooltips get no secondary languages'),
    ('enchanted-book', r'^Enchanted book rows get no secondary languages'),
    ('enchantment-formatter', r'^Enchantment names get no secondary languages'),
    ('enchantment-verify', r'^(Enchantment rows will get no secondary names|Could not verify the enchantment name capture)'),
    ('enchcontrol', r'^EnchControl enchantment'),
    ('not-prepared', r'^(Secondary languages are still not prepared|Secondary names have been withheld|Secondary languages are unavailable'
                     r'|Secondary language loading is unavailable|Could not prepare secondary)'),
    ('item-name-observation', r'^(Item name observation is unavailable|Unsupported (effective|native) tooltip observation shape)'),
    ('native-name-acquisition', r'^Could not acquire a native name'),
    ('native-name-table', r'^Native name table built'),
    ('search-integration', r'^Unsupported search integration shape'),
    ('mixinbooter-absent', r'^MixinBooter is not installed'),
]
# Known lines that do not take a secondary name from a tooltip row; reported, never fatal. Each needs a reason.
ACCEPTED = {
    'mixinbooter-absent': 'the coremod staying inactive without MixinBooter; only expected under --expect-missing',
    'native-name-table': 'per-build breakdown of deliberately rejected catalogue states (withheld keys, excluded composites)',
    'search-integration': 'a GUI class whose search box has no known shape; tooltip names are unaffected',
}


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            digest.update(part)
    return digest.hexdigest()


def game_processes():
    query = ("@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and "
             "$_.CommandLine -like '*" + INSTANCE + "*' } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    text = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True).strip()
    if not text: return []
    value = json.loads(text)
    return value if isinstance(value, list) else [value]


def product_jars():
    found = []
    for path in sorted((GAME / 'mods').glob('*.jar')):
        try:
            with zipfile.ZipFile(path) as archive:
                if PRODUCT_CLASS in archive.namelist(): found.append(path)
        except (zipfile.BadZipFile, OSError):
            continue
    return found


def warnings(text):
    """polyglottooltip WARN/ERROR entries with their continuation lines (pin lines follow without a log prefix)."""
    entries, current = [], None
    for line in text.splitlines():
        match = LINE.match(line)
        if match:
            current = None
            if match.group(3) == 'polyglottooltip' and match.group(2) in ('WARN', 'ERROR'):
                current = dict(level=match.group(2), message=match.group(4), detail=[])
                entries.append(current)
        elif current is not None and line.strip():
            current['detail'].append(line.strip())
    for entry in entries:
        entry['category'] = next((name for name, pattern in CATEGORIES if re.search(pattern, entry['message'])), 'other')
        entry['accepted'] = entry['category'] in ACCEPTED
    return entries


def summarize(entries):
    blocking = [entry for entry in entries if not entry['accepted']]
    by_category = {}
    for entry in entries: by_category[entry['category']] = by_category.get(entry['category'], 0) + 1
    return dict(count=len(entries), blocking=len(blocking), byCategory=by_category,
                accepted={name: ACCEPTED[name] for name in by_category if name in ACCEPTED},
                lines=[dict(category=entry['category'], level=entry['level'], accepted=entry['accepted'], message=entry['message'][:600],
                            detail=[line[:400] for line in entry['detail'][:4]]) for entry in entries])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('jar')
    parser.add_argument('attempt')
    parser.add_argument('--keep', action='store_true')
    parser.add_argument('--settle', type=int, default=45, help='seconds to keep reading the log after the verdict')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--log', help='only classify an existing latest.log (no launch)')
    parser.add_argument('--hide', help='mods/<file> to move out for this launch')
    parser.add_argument('--expect-missing', help='mod id Forge must report missing instead of a language verdict')
    args = parser.parse_args()
    if args.log:
        result = summarize(warnings(Path(args.log).read_text(encoding='utf-8', errors='replace')))
        print(json.dumps(result, ensure_ascii=False, indent=1))
        sys.exit(4 if result['blocking'] else 0)
    jar = Path(args.jar).resolve()
    assert re.fullmatch(r'[a-z0-9-]{1,40}', args.attempt)
    assert PRISM.is_file() and GAME.is_dir() and jar.is_file()
    assert not game_processes(), INSTANCE + ' is already running'
    # A play check is only a play check without the research bridge.
    assert not list((GAME / 'mods').glob('polyglot-devbridge*.jar')), 'the research bridge jar is installed'
    assert not (GAME / 'polyglot-devbridge.properties').exists(), 'polyglot-devbridge.properties is present'
    installed = product_jars()
    assert len(installed) == 1, 'expected exactly one PolyglotTooltip jar in mods, found ' + str([path.name for path in installed])
    target = installed[0]
    run = REPORT / ('run-play-' + args.attempt)
    run.mkdir(parents=True)
    before, candidate = sha(target), sha(jar)
    original = run / ('installed-before-' + before[:8] + '.jar')
    shutil.copy2(target, original)
    if before != candidate: shutil.copy2(jar, target)
    assert sha(target) == candidate
    hidden = None
    if args.hide:
        assert Path(args.hide).name == args.hide and (GAME / 'mods' / args.hide).is_file(), 'nothing to hide: ' + args.hide
        hidden = run / ('hidden-' + args.hide)
        shutil.move(str(GAME / 'mods' / args.hide), str(hidden))
    expected = MISSING.format(args.expect_missing) if args.expect_missing else None
    log = GAME / 'logs' / 'latest.log'
    previous = log.stat().st_mtime if log.is_file() else 0
    started = time.time()
    verdict, crash, text, pids = None, None, '', []
    try:
        subprocess.Popen([str(PRISM), '--launch', INSTANCE], creationflags=subprocess.CREATE_NO_WINDOW, stdin=subprocess.DEVNULL,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deadline, settle_until, seen = time.time() + args.timeout, None, False
        while time.time() < deadline:
            time.sleep(3)
            running = bool(game_processes())
            seen = seen or running
            if not log.is_file() or log.stat().st_mtime <= previous:
                if not running and (seen or time.time() - started > START_GRACE): crash = 'game exited before writing a log'; break
                continue
            text = log.read_text(encoding='utf-8', errors='replace')
            if '---- Minecraft Crash Report ----' in text or 'Minecraft Crash Report' in text: crash = 'crash report in latest.log'; break
            if verdict is None and expected:
                if expected in text:
                    verdict = expected
                    settle_until = time.time() + 10
            elif verdict is None:
                hit = VERDICT.search(text)
                if hit:
                    verdict = hit.group(0)
                    settle_until = time.time() + args.settle
            if settle_until is not None and time.time() >= settle_until: break
            if not running and seen: crash = 'game exited during the check'; break
        pids = game_processes()
        for pid in pids:
            subprocess.run(['taskkill', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(40):
            if not game_processes(): break
            time.sleep(3)
        else:
            for pid in game_processes():
                subprocess.run(['taskkill', '/F', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(5)
    finally:
        if log.is_file() and log.stat().st_mtime > previous:
            shutil.copy2(log, run / 'latest.log')
            text = log.read_text(encoding='utf-8', errors='replace')
        if hidden and not game_processes() and not (GAME / 'mods' / args.hide).exists():
            shutil.move(str(hidden), str(GAME / 'mods' / args.hide))
        if not game_processes():
            if args.keep:
                backup = target.with_name(target.name + '.' + before[:8] + '.bak')
                if before != candidate and not backup.exists(): shutil.copy2(original, backup)
            else:
                shutil.copy2(original, target)
    found = summarize(warnings(text))
    parse = bool(verdict and verdict.startswith('[polyglottooltip]: Secondary language parse matches'))
    if expected:
        # Without the hidden jar the coremod's own WARN is the expected line; every other product WARN still fails.
        absent = [line for line in found['lines'] if line['category'] == 'mixinbooter-absent']
        parse = verdict == expected and len(absent) == 1
    summary = dict(at=datetime.datetime.now(datetime.timezone.utc).isoformat(), instance=INSTANCE, game=str(GAME), candidate=candidate,
                   installedBefore=before, installedAfter=sha(target) if target.is_file() else None, kept=args.keep, bridge=False,
                   elapsedS=round(time.time() - started), launchedPids=pids, closed=not game_processes(), crash=crash, verdict=verdict,
                   parseMatches=parse, hidden=args.hide, hiddenRestored=(GAME / 'mods' / args.hide).is_file() if args.hide else None,
                   expectedMissing=args.expect_missing, playLaunchWarnings=found)
    (run / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(dict(run=run.name, **{key: value for key, value in summary.items() if key != 'playLaunchWarnings'},
                          playLaunchWarnings={key: value for key, value in found.items() if key != 'lines'},
                          blockingLines=[line['message'][:300] for line in found['lines'] if not line['accepted']]), ensure_ascii=False, indent=1))
    restored = not args.hide or summary['hiddenRestored']
    sys.exit(3 if crash or not verdict or not restored else 2 if not parse else 4 if found['blocking'] else 0)


if __name__ == '__main__':
    main()
