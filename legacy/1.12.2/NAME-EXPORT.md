# Name export (1.12.2)

The mod itself has no export command. For translation diagnostics, the 1.12.2 devbridge (`test-tools/legacy112/bridge`) provides `probe.nameExport`. It writes each ingredient's primary name and the secondary name the mod resolves for every configured language to a TSV file, together with the layer or resolver that produced the name and the reason when a language has none. It reads the mod's own resolvers, so the file shows what tooltips and searches use in that game session.

## Run it on a PT-Matrix clone

Build the mod and the bridge first:

```powershell
Set-Location legacy/1.12.2
..\..\gradlew.bat build :devbridge:build --no-daemon --console=plain --offline
```

Then run the `export` scenario of `core-check.py` from the repository root:

```text
python test-tools/legacy112/name-table-reuse/core-check.py <pack|instance-name> <attempt> --scenario export --report <report> [--export-source auto|jei|creative]
```

`core-check.py` installs the jar and the passive bridge in the clone, opens a throwaway world, calls `probe.nameExport` and restores the clone afterwards. The TSV is moved to `docs/research/legacy112/<report>/run-<attempt>/names-<attempt>.tsv`; `export.json` in the same directory records the summary. The same rules apply as for the other `core-check.py` scenarios: the clone must not be running, port 19594 must be free, and only `PT-Matrix-*` clones are used unless an instance name is given.

## Call it directly

In any game with the bridge enabled (for example a `test-tools/legacy112/launch.py` fixture), call the method from Git Bash while a world is open:

```text
node test-tools/legacy112/call.mjs 19594 probe.nameExport '{"source":"auto","file":"names-nova.tsv"}'
```

| Parameter | Default | Meaning |
| --- | --- | --- |
| `source` | `auto` | `jei` reads every ItemStack ingredient from JEI/HEI; `creative` reads every registered item's creative-tab variants (or its default stack); `auto` uses JEI/HEI when it is loaded and falls back to `creative`. |
| `file` | `names-<yyyyMMdd-HHmmss>.tsv` | File name in `polyglottooltip-export/` under the game directory, `[A-Za-z0-9_-]{1,80}.tsv`. An existing file is never overwritten. |
| `limit` | all stacks | Stop after this many stacks. |

While writing, the file is named `<file>.partial`; it gets its final name only when every row is written. The result reports the path, the source used (and the error when JEI/HEI could not be read), the current game language, the secondary languages, the number of stacks and rows, and counts by `language:status` and by source.

## File format

UTF-8 without a byte order mark, one header row, then one row per stack and secondary language. A tab, newline, carriage return or backslash inside a cell is written as `\t`, `\n`, `\r` or `\\`.

| Column | Contents |
| --- | --- |
| `index` | Position of the stack in the source, starting at 0; the rows of one stack share it. |
| `id`, `metadata`, `nbt`, `class` | Registry name, metadata, NBT in Minecraft's text form (empty without NBT), item class. |
| `key` | Translation key derived from the stack; empty when none could be derived. |
| `primary` | Displayed name without formatting codes. |
| `standard` | The key translated in the current language; empty when no key was derived. |
| `consistent` | `true` when `standard` equals `primary`, so the secondary name comes from the key itself. |
| `source` | `standard` for a consistent stack, otherwise the class of the resolver that produced the names (`CompositeNameResolver`, `FormatNameResolver`, `PotionNameResolver`, …); empty when no resolver produced a name. |
| `language` | Secondary language code. |
| `secondary` | Resolved secondary name, with formatting codes; empty when there is none. |
| `shown` | `true` when the tooltip shows this name. It is `false` for a name equal to the primary name, and for the current game language unless `alwaysShow` is on. |
| `status` | See below. |
| `detail` | Exception class for `error` rows. |

| Status | Meaning |
| --- | --- |
| `translated` | A secondary name that differs from the primary name. Not an independent check that the translation is right. |
| `same` | The secondary name equals the primary name; the tooltip leaves it out. Can be correct, or a mod that falls back to the primary language. |
| `missingKey` | Consistent stack whose key is in no `.lang` file of that language. |
| `noKey` | No translation key could be derived and no resolver named the stack in this language. |
| `unresolved` | Inconsistent stack that no resolver could name. |
| `resolverMissing` | A resolver named the stack in another language but not in this one. |
| `custom` | The stack has a custom name; the mod leaves it unchanged. |
| `error` | Reading the stack or resolving its names threw; see `detail`. |
| `noLanguage` | No secondary language is configured. |

The source is a finite catalog: it does not cover arbitrary NBT, world or player state, or names that only appear in tooltips. Treat `unresolved`, `missingKey` and `same` rows as candidates to investigate and use `/polyglot why` on the item in game before changing a resolver.
