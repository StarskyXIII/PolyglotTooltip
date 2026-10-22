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
pack, attempt = sys.argv[1:]
assert attempt.startswith('startup-inputs-')
run = launcher.REPORT / ('run-' + pack.lower() + '-' + attempt)
code = launcher.main(pack, attempt, 'zh_tw')
assert (run / 'exit.json').is_file()
game = Path(launcher.FIXTURES[pack]['game'])
saved = run / 'before/options.txt'
assert saved.is_file()
shutil.copy2(saved, game / 'options.txt')
launcher.write(run / 'restoration.json', dict(
    at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    optionsRestored=launcher.sha(saved) == launcher.sha(game / 'options.txt'),
    clientExitCode=code))
sys.exit(code)
