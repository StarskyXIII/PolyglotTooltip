"""Stage 4 matrix of the minimal-core product on the five PT-Matrix clones (legacy/1.12.2/DESIGN.md §9).

For one clone: back up the clone's config directory and options.txt, rename the clone's own polyglottooltip-*.jar to
*.core-hidden, install the candidate jar and the instrumented devbridge (name tracing off, so only the HUD capture and
a few mod-specific observers are transformed), enable the bridge through polyglot-devbridge.properties, set the recipe
viewer's tooltip search to REQUIRE_PREFIX and AE2's SEARCH_TOOLTIPS to NO (otherwise the layer 1 tooltip line makes
secondary-language queries hit natively), start the clone through the PrismLauncher helper and let core-check.mjs run
the matrix scenario in a throwaway pt112-core-* world. Then a baseline launch of the same clone without any product
jar (same bridge and overrides) reaches the main menu only, for the IngredientFilter build time. Afterwards every
config file is restored byte for byte (files the game created are deleted), the original jar name is restored, the
candidate, bridge and properties are removed, the world is deleted and the screenshots the run took are moved into the
report. Reports go to docs/research/legacy112/core-matrix-2026-10-23/run-<pack>-<attempt>[-baseline]/ and
summary-<attempt>.md.

Usage: core-matrix.py <pack|all> <attempt> [--jar PATH] [--gap 30] [--no-baseline] [--baseline-only]
                                           [--set integrations.ae2=false]
       core-matrix.py summary <attempt>

Rules kept from core-check.py: the clone must not be running, port 19594 must be free (one bridged launch at a time),
launches are at least --gap seconds apart, and only PT-Matrix-* clones are touched.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import uuid

sys.stdout.reconfigure(encoding='utf-8')
ROOT = Path(__file__).resolve().parents[3]
INSTANCES = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances')
PRISM = Path.home() / '.local' / 'bin' / 'prism-agent-launch.exe'
JAR = ROOT / 'legacy/1.12.2/build/libs/polyglottooltip-1.12.2-forge-2.0.jar'
BRIDGE = ROOT / 'test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-production.jar'
CONTROLLER = ROOT / 'test-tools/legacy112/name-table-reuse/core-check.mjs'
REPORT = ROOT / 'docs/research/legacy112/core-matrix-2026-10-23'
BACKUPS = Path('D:/Coding/pt-tmp/core-matrix-backup')
PORT = 19594
HIDDEN = '.core-hidden'
CANDIDATE = 'polyglottooltip-core-check.jar'
PACKS = {
    'nova': 'PT-Matrix-Nova',
    'extended': 'PT-Matrix-Extended',
    'dj2': 'PT-Matrix-DJ2',
    'expert': 'PT-Matrix-Expert',
    'meatball': 'PT-Matrix-Meatball',
}
FILTER_TIME = re.compile(r'^\[(\d\d):(\d\d):(\d\d)\].*Building ingredient filter(?: and search trees)? took ([\d.]+) (ms|s)\b')
OWN_LINE = re.compile(r'/(INFO|WARN|ERROR|FATAL)\] \[polyglottooltip\]')
WARN_LINE = re.compile(r'/(WARN|ERROR|FATAL)\]')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            digest.update(part)
    return digest.hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def read(path):
    return json.loads(path.read_text(encoding='utf-8')) if path.is_file() else None


def processes(instance):
    query = ("@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and "
             "$_.CommandLine -like '*" + instance + "*' } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    text = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True).strip()
    if not text: return []
    value = json.loads(text)
    return value if isinstance(value, list) else [value]


def port_free():
    with socket.socket() as probe:
        try:
            probe.bind(('127.0.0.1', PORT))
            return True
        except OSError:
            return False


def listing(folder):
    return set(os.listdir(folder)) if folder.is_dir() else set()


def tree(folder):
    return {str(path.relative_to(folder)).replace('\\', '/'): sha(path) for path in folder.rglob('*') if path.is_file()}


def languages_of(game):
    cfg = game / 'config' / 'polyglottooltip.cfg'
    if not cfg.is_file(): return ['en_us']
    lines = cfg.read_text(encoding='utf-8', errors='replace').splitlines()
    for index, line in enumerate(lines):
        if re.match(r'\s*S:displayLanguage\s*<\s*$', line):
            values = []
            for entry in lines[index + 1:]:
                if entry.strip() == '>': break
                if entry.strip(): values.append(entry.strip())
            return values or ['en_us']
    return ['en_us']


def replace_option(path, key, value):
    """Rewrite an existing S:<key>=... line of a Forge config; returns False when the file or the line is missing."""
    if not path.is_file(): return False
    text = path.read_text(encoding='utf-8')
    changed, count = re.subn(r'(?m)^(\s*S:' + re.escape(key) + r'=).*$', lambda match: match.group(1) + value, text)
    if count: path.write_text(changed, encoding='utf-8')
    return bool(count)


def set_forge_option(path, category, key, kind, value):
    lines = path.read_text(encoding='utf-8').splitlines() if path.is_file() else ['# Configuration file', '']
    start = next((i for i, line in enumerate(lines) if re.match(r'\s*' + re.escape(category) + r'\s*\{\s*$', line)), None)
    entry = '    %s:%s=%s' % (kind, key, value)
    if start is None:
        lines += ['', category + ' {', entry, '}', '']
    else:
        end = next(i for i in range(start + 1, len(lines)) if lines[i].strip() == '}')
        found = next((i for i in range(start + 1, end) if re.match(r'\s*%s:%s=' % (kind, re.escape(key)), lines[i])), None)
        if found is None: lines.insert(end, entry)
        else: lines[found] = entry
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def filter_times(log):
    times = []
    for line in log.read_text(encoding='utf-8', errors='replace').splitlines():
        match = FILTER_TIME.match(line)
        if match:
            value = float(match.group(4)) * (1000 if match.group(5) == 's' else 1)
            times.append(dict(at=':'.join(match.group(1, 2, 3)), ms=round(value, 1), line=line[:200]))
    return times


def product_lines(log):
    """INFO and above from the product's own logger, and WARN or above from any logger that names the product."""
    found = []
    for line in log.read_text(encoding='utf-8', errors='replace').splitlines():
        if OWN_LINE.search(line) or (WARN_LINE.search(line) and 'polyglottooltip' in line.lower()
                                     and 'testbridge' not in line and 'devbridge' not in line.lower()):
            found.append(line[:400])
    return found


def run_pack(key, instance, attempt, args, baseline):
    game = INSTANCES / instance / 'minecraft'
    assert instance.startswith('PT-Matrix-'), 'only PT-Matrix-* clones are touched'
    assert game.is_dir(), 'No such instance: ' + instance
    assert not processes(instance), instance + ' is already running'
    assert port_free(), 'port %d is in use' % PORT
    mods = game / 'mods'
    products = sorted(path for path in mods.glob('polyglottooltip-*.jar') if path.suffix == '.jar')
    assert len(products) <= 1, 'more than one product jar in ' + str(mods)
    assert not (mods / CANDIDATE).exists(), 'a candidate jar is already installed'
    assert not list(mods.glob('polyglot-devbridge-*.jar')), 'a bridge jar is already installed'
    assert not list(mods.glob('*' + HIDDEN)), 'a hidden product jar is already present; restore it first'
    settings = game / 'polyglot-devbridge.properties'
    assert not settings.exists(), 'polyglot-devbridge.properties already exists'
    assert not [name for name in listing(game / 'saves') if name.startswith('pt112-')], 'a pt112 world is already present'
    jar = Path(args.jar)
    assert baseline or jar.is_file(), 'candidate jar missing: ' + str(jar)
    assert BRIDGE.is_file(), 'bridge jar missing: ' + str(BRIDGE)
    name = 'run-%s-%s%s' % (key, attempt, '-baseline' if baseline else '')
    run = REPORT / name
    run.mkdir(parents=True)
    backup = BACKUPS / name
    assert not backup.exists(), 'a backup is already present: ' + str(backup)
    candidate = mods / CANDIDATE
    bridge_target = mods / BRIDGE.name
    nonce = str(uuid.uuid4())
    world = 'pt112-core-' + attempt
    languages = languages_of(game)
    started = time.time()
    crashes_before = listing(game / 'crash-reports')
    shots_before = listing(game / 'screenshots')
    hidden = None
    code = None
    crash = None
    before = tree(game / 'config')
    options = game / 'options.txt'
    options_sha = sha(options) if options.is_file() else None
    try:
        shutil.copytree(game / 'config', backup / 'config')
        if options.is_file(): shutil.copy2(options, backup / 'options.txt')
        assert tree(backup / 'config') == before, 'config backup differs from the clone'
        if products:
            hidden = products[0].with_name(products[0].name + HIDDEN)
            products[0].rename(hidden)
        if not baseline: shutil.copy2(jar, candidate)
        shutil.copy2(BRIDGE, bridge_target)
        overrides = dict(jeiTooltipSearch=replace_option(game / 'config/jei/jei.cfg', 'tooltipSearchMode', 'REQUIRE_PREFIX'),
                         aeSearchTooltips=replace_option(game / 'config/AppliedEnergistics2/AppliedEnergistics2.cfg', 'SEARCH_TOOLTIPS', 'NO'))
        if not baseline:
            for override in args.set:
                option, value = override.split('=', 1)
                category, option = option.split('.', 1)
                set_forge_option(game / 'config/polyglottooltip.cfg', category, option, 'B' if value in ('true', 'false') else 'S', value)
        settings.write_text('clientdevbridge.enabled=true\nclientdevbridge.port=%d\nclientdevbridge.timeoutSeconds=2400\n'
                            'clientdevbridge.lifecycleTimeoutSeconds=1200\nclientdevbridge.nameTrace=false\n'
                            'clientdevbridge.fixtureNonce=%s\n' % (PORT, nonce), encoding='utf-8')
        write(run / 'config.json', dict(pack=key, instance=instance, fixture=str(game), port=PORT, nonce=nonce, worldName=world,
                                        attempt=attempt, scenario='baseline' if baseline else 'matrix', languages=languages,
                                        overrides=[] if baseline else args.set, configOverrides=overrides,
                                        candidate=None if baseline else str(jar), candidateSha=None if baseline else sha(jar),
                                        bridge=BRIDGE.name, bridgeSha=sha(BRIDGE),
                                        hiddenProduct=(hidden.name if hidden else None), hiddenProductSha=(sha(hidden) if hidden else None)))
        subprocess.Popen([str(PRISM), '--launch', instance], creationflags=subprocess.CREATE_NO_WINDOW, stdin=subprocess.DEVNULL,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        pids = []
        deadline = time.time() + 180
        while not pids and time.time() < deadline:
            time.sleep(2)
            pids = processes(instance)
        assert pids, instance + ' did not start'
        write(run / 'process.json', dict(pid=pids[0], nonce=nonce, gameDirectory=str(game)))
        controller = subprocess.Popen(['node', str(CONTROLLER), str(run)], cwd=ROOT, creationflags=subprocess.CREATE_NO_WINDOW,
                                      stdout=(run / 'controller.log').open('w', encoding='utf-8'), stderr=subprocess.STDOUT)
        seen = False
        launched_at = time.time()
        while controller.poll() is None:
            time.sleep(3)
            fresh = sorted(listing(game / 'crash-reports') - crashes_before)
            if fresh:
                report = game / 'crash-reports' / fresh[-1]
                crash = dict(report=str(report), head=report.read_text(encoding='utf-8', errors='replace').splitlines()[:60])
                write(run / 'crash.json', crash)
                controller.terminate()
                break
            running = bool(processes(instance))
            seen = seen or running
            if not running and (seen or time.time() - launched_at > 120) and not (run / 'before-quit.json').is_file():
                crash = dict(report=None, head=[instance + ' exited before the controller finished'])
                write(run / 'crash.json', crash)
                controller.terminate()
                break
        try: code = controller.wait(timeout=60)
        except subprocess.TimeoutExpired: controller.kill(); code = controller.wait()
        for _ in range(80):
            if not processes(instance): break
            time.sleep(3)
        for pid in processes(instance):
            subprocess.run(['taskkill', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(40):
            if not processes(instance): break
            time.sleep(3)
    finally:
        alive = bool(processes(instance))
        if settings.exists() and not alive: settings.unlink()
        if bridge_target.is_file() and not alive: bridge_target.unlink()
        if candidate.is_file() and not alive: candidate.unlink()
        restored = None
        if hidden is not None and hidden.is_file() and not alive:
            original = hidden.with_name(hidden.name[:-len(HIDDEN)])
            if not original.exists(): hidden.rename(original)
            restored = original.is_file() and not hidden.exists() and sha(original) == read(run / 'config.json')['hiddenProductSha']
        configs = None
        if (backup / 'config').is_dir() and not alive:
            after = tree(game / 'config')
            changed = sorted(path for path, digest in before.items() if after.get(path) != digest)
            created = sorted(path for path in after if path not in before)
            for path in changed:
                target = game / 'config' / path
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(backup / 'config' / path, target)
            for path in created:
                (game / 'config' / path).unlink()
            for folder in sorted((path for path in (game / 'config').rglob('*') if path.is_dir()), key=lambda path: -len(path.parts)):
                relative = str(folder.relative_to(game / 'config')).replace('\\', '/')
                if not any(path.startswith(relative + '/') for path in before) and not any(folder.iterdir()): folder.rmdir()
            if (backup / 'options.txt').is_file() and sha(options) != options_sha: shutil.copy2(backup / 'options.txt', options)
            same = tree(game / 'config') == before and (options_sha is None or sha(options) == options_sha)
            configs = dict(changed=changed, created=created, restored=same)
            if same: shutil.rmtree(backup, ignore_errors=True)
        if not alive and (game / 'logs' / 'latest.log').is_file():
            shutil.copy2(game / 'logs' / 'latest.log', run / 'latest.log')
        shots = sorted(listing(game / 'screenshots') - shots_before)
        if shots and not alive:
            (run / 'screenshots').mkdir(exist_ok=True)
            for shot in shots: shutil.move(str(game / 'screenshots' / shot), str(run / 'screenshots' / shot))
        save = game / 'saves' / world
        removed_world = None
        if save.is_dir() and not alive:
            shutil.rmtree(save, ignore_errors=True)
            removed_world = not save.exists()
        left = dict(java=processes(instance), bridge=[path.name for path in mods.glob('polyglot-devbridge*.jar')],
                    candidate=candidate.exists(), hidden=[path.name for path in mods.glob('*' + HIDDEN)],
                    properties=settings.exists(), worlds=[name for name in listing(game / 'saves') if name.startswith('pt112-')],
                    products=[path.name for path in mods.glob('polyglottooltip-*.jar')])
        clean = (not left['java'] and not left['bridge'] and not left['candidate'] and not left['hidden'] and not left['properties']
                 and not left['worlds'] and (configs or {}).get('restored') and (hidden is None or restored))
        write(run / 'restoration.json', dict(at=now(), productRestored=restored, configs=configs, worldRemoved=removed_world,
                                             screenshots=shots, left=left, clean=bool(clean), controllerExit=code))
    log = run / 'latest.log'
    lines = product_lines(log) if log.is_file() else None
    times = filter_times(log) if log.is_file() else []
    write(run / 'log-check.json', dict(productLines=lines, ingredientFilter=times))
    summary = read(run / 'summary.json')
    result = dict(run=run.name, pack=key, instance=instance, baseline=baseline, elapsedS=round(time.time() - started), controller=code,
                  crash=crash, productLines=None if lines is None else len(lines), ingredientFilterMs=[entry['ms'] for entry in times],
                  clean=bool(clean), pass_=bool(summary and summary.get('pass')) and code == 0 and not crash and lines == [] and bool(clean),
                  failures=(summary or {}).get('failures'))
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


def pct(value):
    return '—' if value is None else '%.1f%%' % (100 * value)


def summarize(attempt):
    rows, details = [], []
    for key, instance in PACKS.items():
        run = REPORT / ('run-%s-%s' % (key, attempt))
        base = REPORT / ('run-%s-%s-baseline' % (key, attempt))
        if not run.is_dir() and not base.is_dir(): continue
        summary = read(run / 'summary.json') or {}
        logs = read(run / 'log-check.json') or {}
        base_logs = read(base / 'log-check.json') or {}
        restoration = read(run / 'restoration.json') or {}
        base_restoration = read(base / 'restoration.json') or {}
        coverage = summary.get('coverage') or {}
        layer1 = summary.get('layer1') or {}
        product_ms = [entry['ms'] for entry in logs.get('ingredientFilter') or []]
        base_ms = [entry['ms'] for entry in base_logs.get('ingredientFilter') or []]
        delta = '—'
        if product_ms and base_ms:
            delta = '%.0f ms vs %.0f ms (%+.0f%%)' % (product_ms[0], base_ms[0], 100 * (product_ms[0] - base_ms[0]) / base_ms[0])
        hud = summary.get('hud') or {}
        hud_text = '—' if not hud.get('kinds') else ('pass' if hud.get('pass') else 'fail') + ' (' + ', '.join(hud['kinds']) + \
            (', limitation' if hud.get('limitation') or any(row.get('limitation') for row in hud.get('checks', [])) else '') + ')'
        ae2 = summary.get('ae2')
        disabled = sorted(set((summary.get('whyStart') or {}).get('disabled', []) + (summary.get('whyEnd') or {}).get('disabled', [])))
        rows.append('| %s | %s | %s/%s, mods %s/%s | %s | %s (%s/%s) | %s / %s | %s | %s | %s | %s | %s | %s | %s |' % (
            key, 'pass' if summary.get('pass') else 'fail: ' + ', '.join(summary.get('failures') or ['no summary']),
            layer1.get('vanillaPass', '—'), layer1.get('vanilla', '—'), layer1.get('modPass', '—'), layer1.get('modRepresentative', '—'),
            pct(coverage.get('consistentShare')), pct(coverage.get('share')), coverage.get('withSecondary', '—'), coverage.get('stacks', '—'),
            coverage.get('complete', '—'), coverage.get('partial', '—'),
            'pass' if summary.get('searchPass') else 'fail', hud_text, '—' if ae2 is None else 'pass' if ae2.get('pass') else 'fail',
            ', '.join(disabled) or 'none', len(logs.get('productLines') or []) if logs else '—', delta,
            'yes' if restoration.get('clean') and base_restoration.get('clean') else 'no'))
        if coverage:
            details.append('### %s\n\nby source: %s\n\nmisses: %s\n\nerrors: %s\n' % (key, json.dumps(coverage.get('bySource'), ensure_ascii=False),
                                                                                       json.dumps(coverage.get('misses'), ensure_ascii=False),
                                                                                       json.dumps(coverage.get('errors'), ensure_ascii=False)))
            full = read(run / 'coverage.json') or {}
            details.append('| class | mod | stacks | misses | by miss | sample |\n|---|---|---|---|---|---|')
            for entry in (full.get('topMissClasses') or [])[:30]:
                sample = (entry.get('missSamples') or [{}])[0]
                details.append('| %s | %s | %s | %s | %s | %s |' % (entry['name'], entry['mod'], entry['stacks'], entry['misses'],
                                                                    json.dumps(entry['byMiss'], ensure_ascii=False),
                                                                    (sample.get('name') or '').replace('|', '/')[:60]))
            details.append('')
            if full.get('topPartialClasses'):
                details.append('partially translated, most frequent parts: %s\n' % ', '.join(
                    '%s (%s)' % (entry['text'], entry['count']) for entry in (full.get('topUntranslated') or [])[:15]))
                details.append('| class | mod | stacks | partial | by source | sample | untranslated |\n|---|---|---|---|---|---|---|')
                for entry in full['topPartialClasses'][:20]:
                    sample = (entry.get('partialSamples') or [{}])[0]
                    details.append('| %s | %s | %s | %s | %s | %s → %s | %s |' % (
                        entry['name'], entry['mod'], entry['stacks'], entry['partial'], json.dumps(entry['bySource'], ensure_ascii=False),
                        (sample.get('name') or '').replace('|', '/')[:60], ' / '.join(sample.get('secondary') or []).replace('|', '/')[:60],
                        json.dumps(sample.get('untranslated') or {}, ensure_ascii=False).replace('|', '/')))
                details.append('')
        for line in (logs.get('productLines') or [])[:20]: details.append('    ' + line)
    text = ['# Core matrix %s' % attempt, '',
            '| pack | result | layer 1 vanilla, mods | standard-path consistent | with secondary | complete / partial | search | HUD | AE2 | disabled integrations | product log lines | IngredientFilter product vs baseline | restored |',
            '|---|---|---|---|---|---|---|---|---|---|---|---|---|'] + rows + [''] + details
    (REPORT / ('summary-%s.md' % attempt)).write_text('\n'.join(text) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('target', help='pack key (%s), all, or summary' % ', '.join(PACKS))
    parser.add_argument('attempt')
    parser.add_argument('--jar', default=str(JAR))
    parser.add_argument('--gap', type=int, default=30, help='seconds between launches, at least 30')
    parser.add_argument('--no-baseline', action='store_true')
    parser.add_argument('--baseline-only', action='store_true')
    parser.add_argument('--set', action='append', default=[], help='polyglottooltip.cfg override, category.option=value')
    args = parser.parse_args()
    assert re.fullmatch(r'[a-z0-9-]{1,40}', args.attempt), 'attempt must be [a-z0-9-]{1,40}'
    assert args.gap >= 30, 'launches must be at least 30 seconds apart'
    for override in args.set:
        assert re.fullmatch(r'[a-z]+\.[a-zA-Z0-9_-]+=[^\s=]+', override), 'invalid override ' + override
    REPORT.mkdir(parents=True, exist_ok=True)
    if args.target == 'summary':
        summarize(args.attempt)
        return
    assert PRISM.is_file(), 'Prism helper missing: ' + str(PRISM)
    targets = list(PACKS.items()) if args.target == 'all' else [(args.target, PACKS[args.target])]
    launches = []
    for key, instance in targets:
        if not args.baseline_only: launches.append((key, instance, False))
        if not args.no_baseline: launches.append((key, instance, True))
    results = []
    for index, (key, instance, baseline) in enumerate(launches):
        if index: time.sleep(args.gap)
        try:
            results.append(run_pack(key, instance, args.attempt, args, baseline))
        except Exception as error:  # one failing clone must not stop the others
            results.append(dict(pack=key, instance=instance, baseline=baseline, error=repr(error)[:400]))
            print(json.dumps(results[-1], ensure_ascii=False), flush=True)
    previous = read(REPORT / ('matrix-%s.json' % args.attempt)) or dict(results=[])
    write(REPORT / ('matrix-%s.json' % args.attempt), dict(attempt=args.attempt, finished=now(), results=previous['results'] + results))
    summarize(args.attempt)


if __name__ == '__main__':
    main()
