import hashlib
import json
from pathlib import Path
import struct
import zipfile

root = Path(__file__).resolve().parents[2]
artifact = root / 'legacy/1.12.2/build/libs/polyglottooltip-1.12.2-forge-2.0.jar'
with zipfile.ZipFile(artifact) as jar:
    entries = jar.namelist()
    classes = {name: struct.unpack('>H', jar.read(name)[6:8])[0]
               for name in entries if name.endswith('.class')}
    assert classes and max(classes.values()) <= 52, 'Unsupported Java bytecode'
    forbidden = ['org/spongepowered/', 'zone/rong/', 'com/github/houbb/',
                 'com/starskyxiii/polyglottooltip/testbridge/']
    assert not any(name.startswith(tuple(forbidden)) for name in entries), 'Unexpected bundled classes'
    assert 'polyglottooltip.legacy.refmap.json' in entries, 'Missing production refmap'
    for name in ['LICENSE-PolyglotTooltip.txt', 'LICENSE-Apache-2.0.txt', 'THIRD-PARTY-NOTICES.txt']:
        assert 'META-INF/' + name in entries, 'Missing license ' + name
    manifest = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '')
    assert 'FMLCorePlugin: com.starskyxiii.polyglottooltip.legacy.bootstrap.CorePlugin' in manifest
    assert 'TweakClass:' not in manifest and 'MixinConfigs:' not in manifest
    refmap = json.loads(jar.read('polyglottooltip.legacy.refmap.json'))
    print(json.dumps({'artifact': str(artifact), 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
                      'classes': len(classes), 'maxClassVersion': max(classes.values()),
                      'refmap': refmap, 'passed': True}, indent=2))
