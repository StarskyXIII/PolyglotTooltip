"""Byte accounting of one name table generation (.pnt, TABLE_SCHEMA 5 or 6): keys, names, lookup keys.

Read-only. Prints JSON: per-section bytes, unique lookup keys, distinct lookup sets and size
estimates for a per-dictionary-epoch layout (no per-row lookup keys) and an interned-set layout.
"""
from collections import Counter
import hashlib
import json
import struct
import sys

data = open(sys.argv[1], 'rb').read()
pos = 0


def take(n):
    global pos
    value = data[pos:pos + n]
    if len(value) != n:
        raise EOFError(pos)
    pos += n
    return value


def i32():
    return struct.unpack('>i', take(4))[0]


def text():
    n = i32()
    return take(2 * n).decode('utf-16-be')


assert struct.unpack('>q', take(8))
schema = i32()
assert schema in (5, 6), schema
protocol, fingerprint, identity, context, generation = text(), text(), text(), text(), text()
take(8)
languages = [text() for _ in range(i32())]
enumerated = i32(); take(1)
rejected = {}
for _ in range(i32()):
    reason = text(); rejected[reason] = i32()
header = pos
rows = i32()
sections = Counter()
lookup_sets = Counter()
lookup_keys = Counter()
lookup_chars = 0
set_sizes = Counter()
key_sizes = []
for _ in range(rows):
    start = pos
    n = i32(); take(n); sections['nativeKey'] += pos - start; key_sizes.append(n)
    start = pos
    for _ in languages:
        if take(1)[0]:
            text()
    sections['names'] += pos - start
    start = pos
    if take(1)[0]:
        text()
    sections['scopedDependency'] += pos - start
    start = pos
    count = i32() if schema == 5 else 0
    values = []
    for _ in range(max(count, 0)):
        value = text(); values.append(value); lookup_keys[value] += 1; lookup_chars += len(value)
    sections['lookupKeys'] += pos - start
    set_sizes[count] += 1
    lookup_sets[hashlib.sha1('\0'.join(values).encode('utf-8')).digest()] += 1
seal = len(data) - pos
assert seal == 32, seal
unique_key_bytes = sum(4 + 2 * len(key) for key in lookup_keys)
# Interned layout: a table of unique strings, a table of distinct sets (int indices), one int per row.
distinct_set_members = 0
# Recompute distinct set members by a second pass is unnecessary: approximate with mean set size.
mean_set = sum(size * count for size, count in set_sizes.items()) / rows
interned = unique_key_bytes + len(lookup_sets) * (4 + 4 * mean_set) + 4 * rows
result = dict(
    file=sys.argv[1], fileBytes=len(data), schema=schema, protocol=protocol, languages=languages, rows=rows,
    headerBytes=header, sealBytes=seal, sections=dict(sections),
    sectionShare={key: round(value / len(data), 4) for key, value in sections.items()},
    lookup=dict(
        totalReferences=sum(lookup_keys.values()), uniqueKeys=len(lookup_keys), totalChars=lookup_chars,
        uniqueKeyBytesSerialized=unique_key_bytes, distinctSets=len(lookup_sets), meanSetSize=round(mean_set, 2),
        setSizeHistogram={str(size): count for size, count in sorted(set_sizes.items())[:40]},
        largestSets=max(set_sizes), mostSharedKeys=lookup_keys.most_common(12),
        rowsInLargestSharedSets=[count for _, count in lookup_sets.most_common(10)]),
    nativeKeyBytes=dict(mean=round(sum(key_sizes) / rows, 1), max=max(key_sizes)),
    estimates=dict(
        perEpochNoLookupBytes=len(data) - sections['lookupKeys'] + 4 * rows,
        internedSetsBytes=round(len(data) - sections['lookupKeys'] + interned)),
)
print(json.dumps(result, ensure_ascii=False, indent=2))
