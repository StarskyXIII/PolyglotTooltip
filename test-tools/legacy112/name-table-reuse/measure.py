"""Existing-bridge design measurement; does not build or change product code."""
from pathlib import Path
import datetime
import hashlib
import importlib.util
import json
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[3]
REPORT = ROOT / 'docs/research/legacy112/name-table-reuse-2026-10-14'
spec = importlib.util.spec_from_file_location('previous_launcher', ROOT / 'test-tools/legacy112/name-table/launch_fixture.py')
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)
launcher.REPORT = REPORT


def snapshot(source, target):
    target.mkdir(parents=True, exist_ok=False)
    manifest = []
    for file in sorted(source.rglob('*')):
        assert not file.is_symlink(), 'Symlink in fixture config snapshot'
        if not file.is_file():
            continue
        relative = file.relative_to(source)
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, destination)
        manifest.append(dict(path=relative.as_posix(), bytes=destination.stat().st_size,
                             sha256=hashlib.sha256(destination.read_bytes()).hexdigest()))
    (target.parent / (target.name + '-manifest.json')).write_text(json.dumps(manifest, indent=2), encoding='utf-8')


def main(pack, attempt='selective-measure2'):
    assert pack in launcher.FIXTURES
    assert re.fullmatch(r'selective-measure[0-9]+', attempt)
    game = Path(launcher.FIXTURES[pack]['game']).resolve()
    evidence = REPORT / ('measurement-' + pack.lower() + '-' + attempt)
    assert not evidence.exists(), 'Keep all attempts immutable'
    evidence.mkdir(parents=True)
    forge = game / 'config/forge.cfg'
    options = game / 'options.txt'
    originals = {forge: forge.read_bytes(), options: options.read_bytes()}
    for file, content in originals.items():
        (evidence / ('original-' + file.name)).write_bytes(content)
    snapshot(game / 'config', evidence / 'config-before-launch')
    text = originals[forge].decode('utf-8')
    assert len(re.findall(r'(?m)^\s*B:selectiveResourceReloadEnabled=false\s*$', text)) == 1
    forge.write_text(text.replace('B:selectiveResourceReloadEnabled=false', 'B:selectiveResourceReloadEnabled=true'), encoding='utf-8')
    exited = False
    try:
        code = launcher.main(pack, attempt, 'zh_tw')
        exited = True
        return code
    finally:
        # Only restore files after the launched game has exited. A hard launcher failure
        # retains backups and requires process ownership inspection before restoration.
        if exited:
            snapshot(game / 'config', evidence / 'config-after-exit')
            for file, content in originals.items():
                file.write_bytes(content)
            record = dict(restored=True, at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                          files=[dict(path=str(file), sha256=hashlib.sha256(file.read_bytes()).hexdigest(),
                                      matchesOriginal=file.read_bytes() == content) for file, content in originals.items()])
            (evidence / 'restoration.json').write_text(json.dumps(record, indent=2), encoding='utf-8')


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:]))
