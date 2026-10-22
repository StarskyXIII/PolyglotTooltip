import struct, sys, json
def main(path):
    f = open(path, 'rb'); data = f.read(1 << 20)
    pos = [0]
    def i32():
        v = struct.unpack_from('>i', data, pos[0])[0]; pos[0] += 4; return v
    def i64():
        v = struct.unpack_from('>q', data, pos[0])[0]; pos[0] += 8; return v
    def text():
        n = i32(); s = data[pos[0]:pos[0] + 2 * n].decode('utf-16-be'); pos[0] += 2 * n; return s
    magic = data[:8].decode('ascii'); pos[0] = 8
    out = dict(magic=magic, schema=i32(), protocol=text(), fingerprint=text()[:16], identity=text()[:16], context=text()[:16], generation=text(), createdAt=i64())
    out['languages'] = [text() for _ in range(i32())]
    out['enumerated'] = i32(); out['completeCatalog'] = bool(data[pos[0]]); pos[0] += 1
    out['rejected'] = {text(): i32() for _ in range(i32())}
    out['rows'] = i32()
    print(json.dumps(out, ensure_ascii=False, indent=1))
main(sys.argv[1])
