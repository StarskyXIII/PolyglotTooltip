"""Launch the owned Meatball fixture for one phase-1 measurement and restore its settings after exit."""
from pathlib import Path
import datetime
import importlib.util
import shutil
import sys

source = Path(__file__).resolve().parents[1] / 'name-table/launch_fixture.py'
spec = importlib.util.spec_from_file_location('owned_fixture', source)
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)
launcher.REPORT = launcher.REPO / 'docs/research/legacy112/name-table-phase1-measure-2026-10-15'
pack, attempt, world_name = sys.argv[1:]
assert pack == 'Meatball' and attempt.startswith('phase1-')
run = launcher.REPORT / ('run-' + pack.lower() + '-' + attempt)
game = Path(launcher.FIXTURES[pack]['game'])
# The baseline ran with FPS Reducer neutralised so an unfocused client is not throttled.
fps = game / 'config/fpsreducer/fpsreducer.cfg'
restore = {relative: (game / relative).read_bytes() for relative in ['options.txt', 'config/polyglottooltip.cfg', 'config/fpsreducer/fpsreducer.cfg']}
text = restore['config/fpsreducer/fpsreducer.cfg'].decode('utf-8')
assert text.count('B:enableFpsReducer=true') == 1
fps.write_text(text.replace('B:enableFpsReducer=true', 'B:enableFpsReducer=false'), encoding='utf-8')
code = None
try:
    code = launcher.main(pack, attempt, 'zh_tw', world_name)
finally:
    if code is not None or (run / 'exit.json').is_file():
        after = run / 'after-exit'
        after.mkdir(exist_ok=True)
        files = []
        for relative, content in restore.items():
            target = game / relative
            shutil.copy2(target, after / Path(relative).name)
            target.write_bytes(content)
            files.append(dict(path=relative, restored=target.read_bytes() == content))
        launcher.write(run / 'restoration.json', dict(at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                                      files=files, clientExitCode=code))
    else:
        fps.write_bytes(restore['config/fpsreducer/fpsreducer.cfg'])
sys.exit(code or 0)
