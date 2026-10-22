"""Pre-install regression matrix: one candidate jar through every test clone, no-bridge play launch then bridged check.

Each pack is a PrismLauncher clone of a user pack (PT-Matrix-*), never the user's own instance. Per pack, in order and
never in parallel: play-check.py --keep (no bridge; fails on a non-accepted polyglottooltip WARN), then
nova-table-check.py with the pack's controller (bridge on port 19594, throwaway pt112-* world). SF4 first gets a launch
with MixinBooter moved out that must reach Forge's missing-mods error for mixinbooter. Meatball gets a second
bridged launch that must load the table the first one stored; each pack starts with the clone's stored table contexts
cleared, so the play and bridged launches build. Play WARN lines a baseline lists under knownPlayWarnings (same
category, identical counts) do not fail the pack. A failing pack never stops the others; the exit code
is 1 when any pack failed. Results: <report>/matrix-<attempt>.json and matrix-<attempt>.md, compared with baselines.json.

Usage: matrix-check.py <jar> <attempt> --bridge-dir DIR [--packs nova,extended,dj2,expert,meatball,sf4] [--skip-meatball]
                       [--restore] [--gap SECONDS] [--write-baselines]
  --bridge-dir       folder with the bridge jars built from a commit (production, name-audit-production): the bridge
                     patches ItemStack, so bridged results compare only under the same bridge, and the shared
                     build/libs is rebuilt by other sessions. The sub-tools run from a private snapshot of the tool tree
                     (run-matrix-<attempt>/root) whose bridge/build/libs holds these jars; run folders move back afterwards.
  --restore          put back each clone's previous jar afterwards (default: the candidate stays in the clones)
  --write-baselines  replace the baselines of the packs that passed with this run's values
Rules kept here: the target clone must not be running, the previous launch must have exited, launches are --gap
seconds apart (two Prism launches in one second both fail), port 19594 must be free (one bridged check at a time).
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
ROOT = Path(__file__).resolve().parents[3]
TOOLS = Path(__file__).resolve().parent
INSTANCES = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances')
REPORT_PATH = 'docs/research/legacy112/name-table-ic2-injection-2026-10-17'
REPORT = ROOT / REPORT_PATH
BRIDGE_JARS = ['polyglot-devbridge-1.12.2-1.0.0-production.jar', 'polyglot-devbridge-1.12.2-1.0.0-name-audit-production.jar']
BASELINES = TOOLS / 'baselines.json'
JAR_NAME = 'polyglottooltip-1.12.2-forge-2.0.jar'
PORT = 19594
# Without it the bridge injects its name trace into ItemStack#getDisplayName and the product refuses that method
# (report §4.3): every tagged stack then loses its rows. Every bridged check in the report ran with it.
BRIDGED_PROPERTIES = 'clientdevbridge.nameTrace=false'
PACKS = {
    'nova': dict(instance='PT-Matrix-Nova', source='Nova Engineering - World', controller='nova-de-check.mjs', env={}),
    'extended': dict(instance='PT-Matrix-Extended', source='Enigmatica 2- Expert - Extended', controller='e2ee-check.mjs', env={}),
    'dj2': dict(instance='PT-Matrix-DJ2', source='Divine Journey 2', controller='dj2-check.mjs', env={}),
    'expert': dict(instance='PT-Matrix-Expert', source='Enigmatica 2- Expert - E2E', controller='pack-check.mjs', env={'MATRIX_CASES': 'expert'}),
    'meatball': dict(instance='PT-Matrix-Meatball', source='MeatballCraft, Dimensional Ascension', controller='pack-check.mjs',
                     env={'MATRIX_CASES': 'meatball'}, reuse=True, playTimeout=1800),
    # Plain Forge without MixinBooter in the pack (the clone adds -mixinbooter-10.7.jar; Surge and MalisisCore bring Mixin 0.7.11).
    'sf4': dict(instance='PT-Matrix-SF4', source='SkyFactory 4 TEST', controller='pack-check.mjs', env={'MATRIX_CASES': 'sf4'},
                absent=dict(hide='-mixinbooter-10.7.jar', mod='mixinbooter'), playTimeout=1200),
}
# The user's own instances: only read (their jar digests before and after), never launched or changed.
USER_INSTANCES = {key: pack['source'] for key, pack in PACKS.items()}
spec = importlib.util.spec_from_file_location('play_check', TOOLS / 'play-check.py')
play_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(play_check)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            digest.update(part)
    return digest.hexdigest()


def read(path):
    try: return json.loads(path.read_text(encoding='utf-8-sig'))
    except (OSError, ValueError): return None


def running(instance):
    """PIDs of java processes whose command line names this instance folder (clone names are not substrings of each other)."""
    query = ("@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and "
             "$_.CommandLine.Replace('/', '\\').Contains('instances\\" + instance.replace("'", "''") + "\\') } | Select-Object -ExpandProperty ProcessId)"
             " | ConvertTo-Json -Compress")
    text = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True).strip()
    if not text: return []
    value = json.loads(text)
    return value if isinstance(value, list) else [value]


def port_free():
    with socket.socket() as connection:
        return connection.connect_ex(('127.0.0.1', PORT)) != 0


def product_jars(game):
    found = []
    for path in sorted((game / 'mods').glob('*.jar')):
        try:
            with zipfile.ZipFile(path) as archive:
                if play_check.PRODUCT_CLASS in archive.namelist(): found.append(path)
        except (OSError, ValueError):
            continue
    return found


def residue(game):
    """Leftovers a bridged check must not leave behind."""
    return dict(bridgeJars=[path.name for path in (game / 'mods').glob('polyglot-devbridge*.jar')],
                properties=(game / 'polyglot-devbridge.properties').exists(),
                worlds=sorted(path.name for path in (game / 'saves').glob('pt112-*')) if (game / 'saves').is_dir() else [])


def step(log, command, env, timeout):
    started = time.time()
    with log.open('a', encoding='utf-8') as output:
        output.write('\n$ ' + ' '.join(command) + '\n')
        output.flush()
        try:
            code = subprocess.run(command, cwd=ROOT, env={**os.environ, **env}, stdout=output, stderr=subprocess.STDOUT,
                                  timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW).returncode
        except subprocess.TimeoutExpired:
            code = 'timeout'
    return code, round(time.time() - started)


def settle(instance, gap):
    """Wait for the instance to exit and keep launches apart."""
    for _ in range(100):
        if not running(instance): break
        time.sleep(3)
    time.sleep(gap)
    return not running(instance)


def force_close(pack):
    """A game that outlives its check, or a check that died in its cleanup, leaves the clone running with the bridge jar
    in mods (nova-table-check.py cannot unlink a jar the game holds). The clones exist for these checks, so the
    matrix ends that game and removes what the check left; every action is recorded."""
    game, actions = INSTANCES / pack['instance'] / 'minecraft', []
    for pid in running(pack['instance']):
        subprocess.run(['taskkill', '/PID', str(pid), '/T', '/F'], creationflags=subprocess.CREATE_NO_WINDOW,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        actions.append(f'killed {pid}')
    for _ in range(20):
        if not running(pack['instance']): break
        time.sleep(3)
    left = residue(game)
    for name in left['bridgeJars']:
        (game / 'mods' / name).unlink()
        actions.append('removed ' + name)
    if left['properties']:
        (game / 'polyglot-devbridge.properties').unlink()
        actions.append('removed polyglot-devbridge.properties')
    for name in left['worlds']:
        shutil.rmtree(game / 'saves' / name)
        actions.append('removed world ' + name)
    return actions


def play_result(run):
    summary = read(run / 'summary.json')
    if not summary: return dict(run=run.name, missing=True)
    found = summary['playLaunchWarnings']
    return dict(run=run.name, verdict=summary['verdict'], parseMatches=summary['parseMatches'], crash=summary['crash'],
                closed=summary['closed'], elapsedS=summary['elapsedS'], installedBefore=summary['installedBefore'],
                warnings=dict(count=found['count'], blocking=found['blocking'], byCategory=found['byCategory'],
                              lines=[dict(category=line['category'], accepted=line['accepted'], message=line['message'][:600]) for line in found['lines']]))


def counts_key(message):
    """A WARN line up to the end of its first {...} block: the counts, without the digests and handler names after them."""
    match = re.match(r'^[^{]*\{[^}]*\}', message)
    return match.group(0) if match else message


def known_play_warnings(play, known):
    """Marks each blocking play line that a baseline lists as known (same category, identical counts); returns the rest."""
    unknown = []
    for line in ((play or {}).get('warnings') or {}).get('lines', []):
        if line['accepted']: continue
        line['known'] = any(line['category'] == entry['category'] and counts_key(line['message']) == entry['message'] for entry in known)
        if not line['known']: unknown.append(line)
    return unknown


def bridged_result(run):
    summary, state = read(run / 'summary.json'), read(run / 'reuse-state-after.json')
    result = dict(run=run.name, bridge=(read(run / 'config.json') or {}).get('bridge'), restoration=read(run / 'restoration.json'),
                  crash=read(run / 'crash.json'), failure=read(run / 'failure.json'))
    if not summary: return dict(result, missing=True)
    table = (state or {}).get('table', {})
    rejected = (table.get('lastCollection') or {}).get('rejected') or {}
    cases = {case['id']: [bool(case.get('namesInFirstTooltip')), bool(case.get('namesInObservedTooltip'))] for case in summary['cases']}
    log = run / 'latest.log'
    warnings = play_check.summarize(play_check.warnings(log.read_text(encoding='utf-8', errors='replace'))) if log.is_file() else None
    return dict(result, failed=summary['failed'], tableState=summary['tableState'], aliasReady=summary['aliasReady'],
                rows=table.get('entries'), rejected=sum(rejected.values()) if rejected else None, rejectedBy=rejected,
                builds=table.get('builds'), loads=table.get('loads'), cases=cases,
                named=sum(1 for value in cases.values() if value[0]), observed=sum(1 for value in cases.values() if value[1]),
                synthetic=sum(1 for case in summary['cases'] if case.get('synthetic')),
                enchantments={case['id']: bool(case['pass']) for case in summary['enchantments']['cases']},
                bridgedWarnings=dict(count=warnings['count'], byCategory=warnings['byCategory']) if warnings else None)


def compare(key, result, baseline):
    """Differences from the pack's baseline; regressions fail the pack, the rest are reported."""
    if not baseline: return dict(baseline=None, regressions=[], notes=['no baseline for this pack'])
    bridged, regressions, notes = result.get('bridged') or {}, [], []
    rows, base_rows = bridged.get('rows'), baseline.get('rows')
    if rows is not None and base_rows:
        if rows < base_rows * 0.99: regressions.append(f'table rows {rows:,} < baseline {base_rows:,} - 1%')
        elif rows != base_rows: notes.append(f'table rows {rows:,} (baseline {base_rows:,}, {rows - base_rows:+,})')
    if bridged.get('rejected') is not None and baseline.get('rejected') is not None and bridged['rejected'] != baseline['rejected']:
        notes.append(f"rejected {bridged['rejected']:,} (baseline {baseline['rejected']:,}, {bridged['rejected'] - baseline['rejected']:+,})")
    for case, (first, observed) in (baseline.get('cases') or {}).items():
        now_case = (bridged.get('cases') or {}).get(case)
        if now_case is None: regressions.append(f'case {case} missing'); continue
        if first and not now_case[0]: regressions.append(f'case {case} lost its secondary rows in the first tooltip')
        if observed and not now_case[1]: regressions.append(f'case {case} lost its secondary rows in the observed tooltip')
        if (now_case[0] and not first) or (now_case[1] and not observed): notes.append(f'case {case} gained secondary rows')
    for case in set(bridged.get('cases') or {}) - set(baseline.get('cases') or {}): notes.append(f'case {case} is new')
    for case, passed in (baseline.get('enchantments') or {}).items():
        if passed and not (bridged.get('enchantments') or {}).get(case): regressions.append(f'enchantment {case} not annotated')
    play = (result.get('play') or {}).get('warnings') or {}
    base_play = baseline.get('playWarnings') or {}
    if play and base_play and play.get('count') != base_play.get('count'):
        notes.append(f"play WARN lines {play.get('count')} (baseline {base_play.get('count')})")
    return dict(baseline=baseline.get('source'), regressions=regressions, notes=notes)


def baseline_of(result, source, previous=None):
    bridged = result['bridged']
    known = (previous or {}).get('knownPlayWarnings')
    return dict(**({'knownPlayWarnings': known} if known else {}), source=source, candidate=result['candidate'], rows=bridged['rows'], rejected=bridged['rejected'], cases=bridged['cases'],
                named=bridged['named'], enchantments=bridged['enchantments'],
                playWarnings=dict(count=result['play']['warnings']['count'], blocking=result['play']['warnings']['blocking']))


def final_state(key, pack, candidate, before, restore):
    game = INSTANCES / pack['instance'] / 'minecraft'
    left = residue(game)
    jars = product_jars(game)
    digests = [sha(path)[:8] for path in jars]
    expected = (before or candidate)[:8] if restore else candidate[:8]
    problems = ([f'running {running(pack["instance"])}'] if running(pack['instance']) else []) \
        + (['bridge jar ' + ', '.join(left['bridgeJars'])] if left['bridgeJars'] else []) \
        + (['polyglot-devbridge.properties present'] if left['properties'] else []) \
        + (['worlds ' + ', '.join(left['worlds'])] if left['worlds'] else []) \
        + ([] if digests == [expected] else [f'product jars {[path.name for path in jars]} {digests}, expected [{expected}]'])
    return dict(instance=pack['instance'], productJars=dict(zip([path.name for path in jars], digests)), expected=expected,
                bakFiles=sorted(path.name for path in (game / 'mods').glob('*.bak')), ok=not problems, problems=problems)


def stage(work, bridge_dir):
    """The private tool root: the sub-tools resolve the bridge, controllers and report folder from their own location."""
    root = work / 'root'
    skip = shutil.ignore_patterns('build', 'logs', '__pycache__', 'src', '.gradle')
    shutil.copytree(ROOT / 'test-tools/legacy112', root / 'test-tools/legacy112', ignore=skip)
    shutil.copytree(ROOT / 'test-tools/name-audit/runner', root / 'test-tools/name-audit/runner')
    libs = root / 'test-tools/legacy112/bridge/build/libs'
    libs.mkdir(parents=True)
    for name in BRIDGE_JARS: shutil.copy2(bridge_dir / name, libs / name)
    (root / REPORT_PATH).mkdir(parents=True)
    return root, {name: sha(libs / name) for name in BRIDGE_JARS}


def run_pack(key, pack, jar, candidate, attempt, args, log, root, known=()):
    tools, report = root / 'test-tools/legacy112/name-table-reuse', root / REPORT_PATH
    result = dict(pack=key, instance=pack['instance'], candidate=candidate, started=now(), failures=[])
    game = INSTANCES / pack['instance'] / 'minecraft'
    begun = time.time()
    if not game.is_dir():
        result['failures'].append('instance-missing')
        return result
    if running(pack['instance']):
        result['failures'].append('instance-running')
        return result
    left = residue(game)
    if left['bridgeJars'] or left['properties']:
        result['failures'].append('residue-before: ' + json.dumps(left))
        return result
    jars = product_jars(game)
    if len(jars) != 1 or jars[0].name != JAR_NAME:
        result['failures'].append('product-jars: ' + str([path.name for path in jars]))
        return result
    result['installedBefore'] = sha(jars[0])
    # Tables a previous matrix run stored would be loaded instead of built (no rejection counts, no build exercised).
    # Only Meatball's reuse launch should load: the one its bridged launch stores in this run.
    contexts = game / 'polyglottooltip/name-tables/contexts'
    if contexts.is_dir():
        result['clearedTables'] = sorted(path.name for path in contexts.iterdir())
        shutil.rmtree(contexts)
    env = {'POLYGLOT_INSTANCE': pack['instance'], 'NOVA_EXTRA_PROPERTIES': BRIDGED_PROPERTIES, **pack['env']}
    absent = pack.get('absent')
    if absent:
        # The pack type without MixinBooter: the coremod must load, stay inactive and let Forge name the missing mod.
        absent_run = report / ('run-play-' + key + '-' + attempt + '-absent')
        code, elapsed = step(log, [sys.executable, str(tools / 'play-check.py'), str(jar), key + '-' + attempt + '-absent', '--keep',
                                   '--hide=' + absent['hide'], '--expect-missing', absent['mod'], '--timeout', '600'], env, 1200)
        result['absent'] = dict(play_result(absent_run), exit=code, stepS=elapsed,
                                hiddenRestored=(read(absent_run / 'summary.json') or {}).get('hiddenRestored'))
        if code != 0: result['failures'].append(f'absent-check exit {code}')
        if not (game / 'mods' / absent['hide']).is_file():
            result['failures'].append('absent-check left ' + absent['hide'] + ' out of mods')
            return result
        if not settle(pack['instance'], args.gap):
            result['failures'].append('absent launch did not exit')
            result['forcedCleanup'] = force_close(pack)
            return result
    play_run = report / ('run-play-' + key + '-' + attempt)
    code, elapsed = step(log, [sys.executable, str(tools / 'play-check.py'), str(jar), key + '-' + attempt, '--keep',
                               '--timeout', str(pack.get('playTimeout', 900))], env, pack.get('playTimeout', 900) + 600)
    result['play'] = dict(play_result(play_run), exit=code, stepS=elapsed)
    # Exit 4 is a parse match with blocking WARN lines; lines the baseline lists as known (identical counts) do not fail.
    known_only = code == 4 and not known_play_warnings(result['play'], known)
    if known_only: result['play']['knownOnly'] = True
    elif code != 0: result['failures'].append(f'play-check exit {code}')
    if not settle(pack['instance'], args.gap):
        result['failures'].append('play launch did not exit')
        result['forcedCleanup'] = force_close(pack)
        return result
    # A crash before the main menu (verdict None) is a startup failure of the pack: a bridged launch would only repeat it.
    if result['play'].get('crash') and not result['play'].get('verdict'):
        result['failures'].append('bridged skipped: the play launch crashed before the language verdict')
        result['elapsedS'] = round(time.time() - begun)
        return result
    launches = [('bridged', key + '-' + attempt)] + ([('reuse', key + '-' + attempt + '-reuse')] if pack.get('reuse') else [])
    for name, label in launches:
        deadline = time.time() + 600
        while not port_free() and time.time() < deadline: time.sleep(10)
        if not port_free():
            result['failures'].append(f'{name}: port {PORT} busy')
            break
        code, elapsed = step(log, [sys.executable, str(tools / 'nova-table-check.py'), str(jar), label, pack['controller'], 'production'],
                             env, 5400)
        # PrismLauncher 11.0.3 sometimes dies of heap corruption (0xc0000374) before it spawns the game; the check then
        # ends at 'Nova did not start' with no process.json. That launch never ran, so it is repeated once.
        if code == 1 and not (report / ('run-nova-' + label) / 'process.json').exists() and settle(pack['instance'], args.gap):
            result[name + 'NotStarted'] = 'run-nova-' + label
            label += '-r'
            code, elapsed = step(log, [sys.executable, str(tools / 'nova-table-check.py'), str(jar), label, pack['controller'], 'production'],
                                 env, 5400)
        result[name] = dict(bridged_result(report / ('run-nova-' + label)), exit=code, stepS=elapsed)
        current = result[name]
        # The controller fails play-launch-warnings for the same play lines; exit 2 only reports that failed list.
        failed = [value for value in current.get('failed') or [] if not (known_only and value == 'play-launch-warnings')]
        if code != 0 and not (code == 2 and current.get('failed') and not failed): result['failures'].append(f'{name} exit {code}')
        if failed: result['failures'].append(f'{name} failed {failed}')
        if current.get('tableState') != 'ready': result['failures'].append(f'{name} table {current.get("tableState")}')
        restoration = current.get('restoration') or {}
        if not (restoration.get('settingsRemoved') and restoration.get('bridgeRemoved') and not restoration.get('worldLeft')):
            result['failures'].append(f'{name} restoration incomplete')
        exited = settle(pack['instance'], args.gap)
        if not exited or any(residue(game).values()):
            if not exited: result['failures'].append(f'{name} launch did not exit')
            result.setdefault('forcedCleanup', []).extend(force_close(pack))
            if not exited: break
    if pack.get('reuse') and result.get('reuse') and not result['reuse'].get('missing'):
        reuse, build = result['reuse'], result.get('bridged') or {}
        result['reuseCheck'] = dict(builds=reuse.get('builds'), loads=reuse.get('loads'), rows=reuse.get('rows'), buildRows=build.get('rows'),
                                    ok=reuse.get('builds') == 0 and (reuse.get('loads') or 0) >= 1 and reuse.get('rows') == build.get('rows'))
        if not result['reuseCheck']['ok']: result['failures'].append('reuse launch did not load the stored table')
    if args.restore and result['installedBefore'] != candidate and not running(pack['instance']):
        shutil.copy2(play_run / ('installed-before-' + result['installedBefore'][:8] + '.jar'), game / 'mods' / JAR_NAME)
    result['elapsedS'] = round(time.time() - begun)
    return result


def cell(value):
    return '—' if value is None else f'{value:,}' if isinstance(value, int) else str(value)


def markdown(matrix):
    lines = [f"# Regression matrix {matrix['attempt']}", '',
             f"Candidate `{matrix['candidate'][:8]}` (HEAD `{matrix['head'][:7]}`), {matrix['started'][:16]} → {matrix['finished'][:16]} UTC, "
             f"{matrix['elapsedS'] // 60} min. Result: **{'PASS' if matrix['pass'] else 'FAIL'}**.", '',
             'Bridge (private copy, ' + matrix['bridgeDir'] + '): ' + ', '.join(f'`{name}` `{value[:8]}`' for name, value in matrix['bridge'].items()) + '.', '',
             '| Pack | Result | Product | Bridge | Language verdict | Table rows | Rejected | Named cases (first / observed) | Enchantments | Play WARN (blocking) | Minutes |',
             '|---|---|---|---|---|---|---|---|---|---|---|']
    for key, result in matrix['packs'].items():
        play, bridged = result.get('play') or {}, result.get('bridged') or {}
        verdict = (play.get('verdict') or '—').replace('[polyglottooltip]: ', '')
        verdict = re.sub(r'^Secondary language parse matches', 'parse matches', verdict)[:90]
        ench = bridged.get('enchantments') or {}
        warnings = play.get('warnings') or {}
        cases = f"{bridged.get('named', '—')} / {bridged.get('observed', '—')} of {len(bridged.get('cases') or {})}" if bridged.get('cases') else '—'
        lines.append(f"| {key} | {'pass' if result['pass'] else 'FAIL'} | `{result['candidate'][:8]}` | `{(bridged.get('bridge') or '—')[:8]}` | {verdict} | {cell(bridged.get('rows'))} | {cell(bridged.get('rejected'))} | "
                     f"{cases} | {sum(ench.values())}/{len(ench)} | {warnings.get('count', '—')} ({warnings.get('blocking', '—')}) | "
                     f"{round(result.get('elapsedS', 0) / 60)} |")
    for key, result in matrix['packs'].items():
        lines += ['', f"## {key} — {result['instance']}", '']
        for failure in result['failures']: lines.append(f'- **failure:** {failure}')
        diff = result.get('baseline') or {}
        lines.append(f"- baseline: {diff.get('baseline') or 'none'}")
        for regression in diff.get('regressions', []): lines.append(f'- **regression:** {regression}')
        for note in diff.get('notes', []): lines.append(f'- difference: {note}')
        absent = result.get('absent') or {}
        if absent: lines.append(f"- launch without MixinBooter `{absent.get('run')}`: exit {absent.get('exit')}, crash {absent.get('crash')}, "
                                f"verdict {absent.get('verdict')}, jar restored {absent.get('hiddenRestored')}, {absent.get('stepS')} s")
        play = result.get('play') or {}
        if play: lines.append(f"- play launch `{play.get('run')}`: exit {play.get('exit')}, crash {play.get('crash')}, {play.get('stepS')} s")
        for line in (play.get('warnings') or {}).get('lines', []):
            lines.append(f"  - WARN [{line['category']}{', accepted' if line['accepted'] else ', known' if line.get('known') else ''}] {line['message'][:200]}")
        for name in ('bridged', 'reuse'):
            bridged = result.get(name)
            if not bridged: continue
            if result.get(name + 'NotStarted'): lines.append(f"- {name}: Prism started no game for `{result[name + 'NotStarted']}`; launched again")
            lines.append(f"- {name} `{bridged.get('run')}`: exit {bridged.get('exit')}, failed {bridged.get('failed')}, table {bridged.get('tableState')}, "
                         f"builds {bridged.get('builds')} loads {bridged.get('loads')}, rejected {bridged.get('rejectedBy')}, "
                         f"bridged-log WARN {(bridged.get('bridgedWarnings') or {}).get('byCategory')}, {bridged.get('stepS')} s")
        if result.get('reuseCheck'): lines.append(f"- reuse check: {result['reuseCheck']}")
        state = result.get('finalState') or {}
        if result.get('clearedTables'): lines.append(f"- cleared stored table contexts before the launches: {len(result['clearedTables'])}")
        if result.get('forcedCleanup'): lines.append(f"- forced cleanup by the matrix: {result['forcedCleanup']}")
        lines.append(f"- final state: {'ok' if state.get('ok') else 'PROBLEMS ' + str(state.get('problems'))}; jars {state.get('productJars')}; "
                     f"backups {state.get('bakFiles')}")
    lines += ['', '## User instances (read only)', '']
    for key, value in matrix['userInstances'].items():
        lines.append(f"- {value['instance']}: {'unchanged' if value['unchanged'] else 'CHANGED'} {value['after']}")
    return '\n'.join(lines) + '\n'


def user_jars():
    state = {}
    for key, name in USER_INSTANCES.items():
        game = INSTANCES / name / 'minecraft'
        state[key] = {path.name: sha(path)[:8] for path in product_jars(game)} if game.is_dir() else None
    return state


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('jar')
    parser.add_argument('attempt')
    parser.add_argument('--bridge-dir', required=True)
    parser.add_argument('--packs', default=','.join(PACKS))
    parser.add_argument('--skip-meatball', action='store_true')
    parser.add_argument('--restore', action='store_true')
    parser.add_argument('--gap', type=int, default=15)
    parser.add_argument('--write-baselines', action='store_true')
    args = parser.parse_args()
    keys = [key for key in args.packs.split(',') if key and not (args.skip_meatball and key == 'meatball')]
    assert all(key in PACKS for key in keys), 'unknown pack in ' + args.packs
    # Child attempts are <pack>-<attempt>-reuse and their worlds pt112-nova-<that>; both have 40-character limits.
    assert re.fullmatch(r'[a-z0-9-]{1,24}', args.attempt)
    out = REPORT / ('matrix-' + args.attempt + '.json')
    assert not out.exists(), out
    work = REPORT / ('run-matrix-' + args.attempt)
    work.mkdir()
    # A private copy: sessions sharing this checkout rebuild build/libs while the matrix runs.
    jar = work / JAR_NAME
    shutil.copy2(Path(args.jar).resolve(), jar)
    candidate = sha(jar)
    log = work / 'steps.log'
    bridge_dir = Path(args.bridge_dir).resolve()
    root, bridges = stage(work, bridge_dir)
    baselines = read(BASELINES) or {}
    users_before = user_jars()
    started, begun = now(), time.time()
    packs = {}
    for key in keys:
        print(f'[{now()[11:19]}] {key}: start', flush=True)
        try:
            result = run_pack(key, PACKS[key], jar, candidate, args.attempt, args, log, root,
                              (baselines.get(key) or {}).get('knownPlayWarnings') or [])
        except Exception as error:  # one pack's tool failure must not end the matrix
            result = dict(pack=key, instance=PACKS[key]['instance'], candidate=candidate, failures=['matrix error: ' + repr(error)[:400]])
        result['baseline'] = compare(key, result, baselines.get(key))
        result['finalState'] = final_state(key, PACKS[key], candidate, result.get('installedBefore'), args.restore)
        if not result['finalState']['ok']: result['failures'].append('final state: ' + '; '.join(result['finalState']['problems']))
        result['pass'] = not result['failures'] and not result['baseline']['regressions']
        packs[key] = result
        print(f"[{now()[11:19]}] {key}: {'pass' if result['pass'] else 'FAIL ' + str(result['failures'] + result['baseline']['regressions'])}", flush=True)
        (work / 'progress.json').write_text(json.dumps(packs, ensure_ascii=False, indent=1), encoding='utf-8')
    users_after = user_jars()
    # Run folders join the shared report folder, where earlier runs and play-launch.mjs lookups live.
    for run in sorted((root / REPORT_PATH).iterdir()):
        if not (REPORT / run.name).exists(): shutil.move(str(run), str(REPORT / run.name))
    matrix = dict(attempt=args.attempt, candidate=candidate, jar=str(Path(args.jar).resolve()),
                  head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  bridge=bridges, bridgeDir=str(bridge_dir),
                  started=started, finished=now(), elapsedS=round(time.time() - begun), restore=args.restore, packs=packs,
                  userInstances={key: dict(instance=USER_INSTANCES[key], before=users_before[key], after=users_after[key],
                                           unchanged=users_before[key] == users_after[key]) for key in USER_INSTANCES})
    matrix['pass'] = all(result['pass'] for result in packs.values()) and all(value['unchanged'] for value in matrix['userInstances'].values())
    out.write_text(json.dumps(matrix, ensure_ascii=False, indent=2), encoding='utf-8')
    (REPORT / ('matrix-' + args.attempt + '.md')).write_text(markdown(matrix), encoding='utf-8')
    if args.write_baselines:
        for key, result in packs.items():
            if result['pass']:
                baselines[key] = baseline_of(result, 'matrix-' + args.attempt, baselines.get(key))
        BASELINES.write_text(json.dumps(baselines, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    print(markdown(matrix), flush=True)
    sys.exit(0 if matrix['pass'] else 1)


if __name__ == '__main__':
    main()
