"""Compile and run the frozen-data engine tests on actual Java 8 and oldest supported Gson."""
from pathlib import Path
import json
import os
import subprocess

root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-prototype-2026-10-14'
runtime = json.loads((report / 'java8-runtime.json').read_text(encoding='utf-8'))
classes = report / 'java8-classes-v2'
classes.mkdir(exist_ok=True)
sources = root / 'test-tools/legacy112/bridge/src'
files = list((sources / 'main/java/com/starskyxiii/polyglottooltip/testbridge/nametable').glob('*.java'))
files += list((sources / 'test/java/com/starskyxiii/polyglottooltip/testbridge/nametable').glob('*.java'))
classpath = os.pathsep.join(runtime[name] for name in ['gson', 'junit', 'hamcrest'])
with (report / 'java8-gson280-tests-v2.log').open('w', encoding='utf-8') as log:
    subprocess.run([runtime['javac'], '-J-Xmx256m', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', classpath,
                    '-d', str(classes), *map(str, files)], check=True, stdout=log, stderr=subprocess.STDOUT)
    subprocess.run([runtime['java'], '-Xmx384m', '-cp', str(classes) + os.pathsep + classpath, 'org.junit.runner.JUnitCore',
                    'com.starskyxiii.polyglottooltip.testbridge.nametable.NativeNameTableTest'], check=True, stdout=log, stderr=subprocess.STDOUT)
runtime['classes'] = str(classes)
(report / 'java8-runtime-v2.json').write_text(json.dumps(runtime, indent=2) + '\n', encoding='utf-8')
print((report / 'java8-gson280-tests-v2.log').read_text(encoding='utf-8'))
