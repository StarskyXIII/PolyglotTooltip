"""List IResourcePack implementations in a mods folder by reading class headers only (no class loading, no launch).

Usage: python -I resource-pack-scan.py <mods folder>; prints jar, class, superclass and interfaces per pack class.
"""
import sys, zipfile, struct, glob, os
PACK = {'net/minecraft/client/resources/IResourcePack','net/minecraft/client/resources/AbstractResourcePack',
        'net/minecraft/client/resources/FileResourcePack','net/minecraft/client/resources/FolderResourcePack',
        'net/minecraft/client/resources/DefaultResourcePack','net/minecraftforge/fml/client/FMLFileResourcePack',
        'net/minecraftforge/fml/client/FMLFolderResourcePack'}
def header(data):
    if data[:4] != b'\xca\xfe\xba\xbe': return None
    count = struct.unpack('>H', data[8:10])[0]; pos = 10; utf = {}; cls = {}; i = 1
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack('>H', data[pos+1:pos+3])[0]; utf[i] = data[pos+3:pos+3+n].decode('utf-8', 'replace'); pos += 3+n
        elif tag in (7, 8, 16, 19, 20): 
            if tag == 7: cls[i] = struct.unpack('>H', data[pos+1:pos+3])[0]
            pos += 3
        elif tag == 15: pos += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): pos += 5
        elif tag in (5, 6): pos += 9; i += 1
        else: return None
        i += 1
    name = lambda k: utf.get(cls.get(k))
    this, sup, n = struct.unpack('>HHH', data[pos+2:pos+8])
    ifs = [name(struct.unpack('>H', data[pos+8+2*k:pos+10+2*k])[0]) for k in range(n)]
    return name(this), name(sup), ifs
classes = {}
for jar in sorted(glob.glob(os.path.join(sys.argv[1], '*.jar'))):
    try: z = zipfile.ZipFile(jar)
    except Exception: continue
    for entry in z.namelist():
        if not entry.endswith('.class'): continue
        data = z.read(entry)
        if b'client/resources/' not in data and b'ResourcePack' not in data: continue
        h = header(data)
        if h: classes[h[0]] = (h[1], h[2], os.path.basename(jar))
def is_pack(name, seen=()):
    if name in PACK: return True
    if name not in classes or name in seen: return False
    sup, ifs, _ = classes[name]
    return any(is_pack(x, seen + (name,)) for x in [sup] + ifs if x)
for name in sorted(classes):
    if name not in PACK and is_pack(name):
        sup, ifs, jar = classes[name]
        print(f'{jar}\t{name}\textends {sup}\t{",".join(i for i in ifs if i)}')
