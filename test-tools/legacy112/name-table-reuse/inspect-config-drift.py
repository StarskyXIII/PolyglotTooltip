"""Describe preserved config snapshots. Candidate comparisons are not cache admission."""
from pathlib import Path
import difflib
import hashlib
import io
import json
import struct
import sys


def nbt(data):
    stream = io.BytesIO(data)
    nodes = [0]
    def take(size):
        if size < 0 or size > 16777216:
            raise ValueError('NBT size')
        value = stream.read(size)
        if len(value) != size:
            raise ValueError('Truncated NBT')
        return value
    def integer():
        return struct.unpack('>i', take(4))[0]
    def string():
        return take(struct.unpack('>H', take(2))[0]).hex()
    def value(kind, depth):
        nodes[0] += 1
        if nodes[0] > 1000000 or depth > 64:
            raise ValueError('NBT complexity')
        if kind in (1, 2, 3, 4, 5, 6):
            return [kind, take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind]).hex()]
        if kind == 8:
            return [kind, string()]
        if kind in (7, 11, 12):
            return [kind, take(integer() * {7: 1, 11: 4, 12: 8}[kind]).hex()]
        if kind == 9:
            child = take(1)[0]
            length = integer()
            if length < 0 or length > 1000000:
                raise ValueError('NBT list size')
            return [kind, child, [value(child, depth + 1) for _ in range(length)]]
        if kind == 10:
            result = {}
            while True:
                child = take(1)[0]
                if child == 0:
                    return [kind, result]
                key = string()
                if key in result:
                    raise ValueError('Duplicate compound key')
                result[key] = value(child, depth + 1)
        raise ValueError('Unknown NBT type')
    kind = take(1)[0]
    name = string()
    result = [name, value(kind, 0)]
    if stream.read(1):
        raise ValueError('Trailing NBT data')
    return result


def candidate_text(data):
    text = data.decode('utf-8-sig')
    if '\x00' in text:
        raise ValueError('Binary text')
    return '\n'.join(line.strip() for line in text.splitlines()
                     if line.strip() and not line.lstrip().startswith(('#', ';', '!')))


def main(before, after, output):
    before, after, output = Path(before), Path(after), Path(output)
    old = {p.relative_to(before).as_posix(): p for p in before.rglob('*') if p.is_file()}
    new = {p.relative_to(after).as_posix(): p for p in after.rglob('*') if p.is_file()}
    rows = []
    for relative in sorted(old.keys() | new.keys()):
        left = old.get(relative).read_bytes() if relative in old else None
        right = new.get(relative).read_bytes() if relative in new else None
        if left == right:
            continue
        row = dict(path=relative, change='added' if left is None else 'removed' if right is None else 'changed')
        if left is not None and right is not None:
            row.update(beforeBytes=len(left), afterBytes=len(right), beforeSha256=hashlib.sha256(left).hexdigest(),
                       afterSha256=hashlib.sha256(right).hexdigest())
            try:
                a, b = candidate_text(left), candidate_text(right)
                row['candidateNonCommentLinesEqual'] = a == b
                row['candidateLineMultisetsEqual'] = sorted(a.splitlines()) == sorted(b.splitlines())
                row['candidateDiff'] = list(difflib.unified_diff(a.splitlines(), b.splitlines(), n=1))[:160]
            except (ValueError, UnicodeError):
                row['textCandidate'] = 'unsupported'
            if relative.lower().endswith('.nbt'):
                try:
                    a, b = nbt(left), nbt(right)
                    row['exactTypedNbtEqualIgnoringCompoundOrder'] = a == b
                    if a != b:
                        old_tree = json.dumps(a, sort_keys=True, indent=2)
                        new_tree = json.dumps(b, sort_keys=True, indent=2)
                        row['typedNbtDiff'] = list(difflib.unified_diff(old_tree.splitlines(), new_tree.splitlines(), n=2))[:300]
                except ValueError as error:
                    row['typedNbtError'] = str(error)
        rows.append(row)
    with output.open('x', encoding='utf-8') as stream:
        json.dump(dict(before=str(before), after=str(after), purpose='Diagnostic candidates only; not admission policy', changes=rows), stream, indent=2, ensure_ascii=False)
    print(json.dumps([dict(path=r['path'], change=r['change'], nonCommentEqual=r.get('candidateNonCommentLinesEqual'),
                           reorderedLinesEqual=r.get('candidateLineMultisetsEqual'), typedNbtEqual=r.get('exactTypedNbtEqualIgnoringCompoundOrder')) for r in rows], indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main(*sys.argv[1:])
