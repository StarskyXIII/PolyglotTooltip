from pathlib import Path
import datetime
import importlib.util
import shutil
import sys

source = Path(__file__).resolve().parents[1] / 'name-table/launch_fixture.py'
spec = importlib.util.spec_from_file_location('owned_fixture', source)
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)
launcher.REPORT = launcher.REPO / 'docs/research/legacy112/name-table-reuse-2026-10-14'
pack, attempt, *choice = sys.argv[1:]
language = choice[0] if choice else 'zh_tw'
world_name = choice[1] if len(choice) > 1 else None
assert attempt.startswith('core-')
run = launcher.REPORT / ('run-' + pack.lower() + '-' + attempt)
code = launcher.main(pack, attempt, language, world_name)
assert (run / 'exit.json').is_file()
game = Path(launcher.FIXTURES[pack]['game'])
saved = run / 'before/options.txt'
shutil.copy2(saved, game / 'options.txt')
launcher.write(run / 'restoration.json', dict(
    at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    optionsRestored=launcher.sha(saved) == launcher.sha(game / 'options.txt'),
    clientExitCode=code))
sys.exit(code)
