import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--asm', type=Path, action='append', required=True)
    parser.add_argument('--cases', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    build = root / 'legacy/1.12.2/build/shape-audit'
    build.mkdir(parents=True, exist_ok=True)
    sources = root / 'legacy/1.12.2/src/main/java/com/starskyxiii/polyglottooltip/legacy/bootstrap'
    separator = '.exe' if os.name == 'nt' else ''
    libraries = os.pathsep.join(str(path.resolve()) for path in args.asm)
    subprocess.run([str(args.java_home / 'bin' / ('javac' + separator)), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                    '-cp', libraries, '-d', str(build), str(sources / 'ClassShape.java'), str(sources / 'Ae2Compatibility.java'),
                    str(sources / 'RecipeViewerCompatibility.java'), str(sources / 'AeAddonClassShape.java'), str(sources / 'AeAddonCompatibility.java'),
                    str(Path(__file__).with_name('ShapeAudit.java')), str(Path(__file__).with_name('AddonShapeAudit.java'))], check=True)
    cases = json.loads(args.cases.read_text(encoding='utf-8-sig'))
    results = []
    for case in cases:
        artifact = Path(case['file']).resolve()
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        if digest != case['sha256']:
            raise ValueError('Artifact hash mismatch: ' + str(artifact))
        extra = []
        for dependency in case.get('dependencies', []):
            path = Path(dependency['file']).resolve()
            if hashlib.sha256(path.read_bytes()).hexdigest() != dependency['sha256']:
                raise ValueError('Dependency hash mismatch: ' + str(path))
            extra.append(str(path))
        addon = case.get('suite') == 'addon'
        run = subprocess.run([str(args.java_home / 'bin' / ('java' + separator)), '-cp',
                              str(build) + os.pathsep + libraries, 'AddonShapeAudit' if addon else 'ShapeAudit', case['profile'], str(artifact)]
                             + (extra if addon else [str(case.get('dualLabels', False)).lower()]),
                             capture_output=True, text=True)
        results.append({**case, 'success': run.returncode == 0, 'stdout': run.stdout, 'stderr': run.stderr})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, indent=2), encoding='utf-8')
    failed = [case for case in results if not case['success']]
    print(json.dumps({'passed': len(results) - len(failed), 'failed': failed}, indent=2))
    if failed:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
