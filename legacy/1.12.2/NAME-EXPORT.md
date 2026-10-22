# Name export (1.12.2)

Open a world and wait for the configured secondary languages to load. `/polyglotdump` is a client command and does not require server permissions.

```text
/polyglotdump start held
/polyglotdump start defaults minecraft:stone 64
/polyglotdump start creative appliedenergistics2 10000
/polyglotdump start jei appliedenergistics2 10000
/polyglotdump start creative appliedenergistics2 10000 batch
/polyglotdump start jei appliedenergistics2 batch
/polyglotdump stack minecraft:potion 0 1 {Potion:"minecraft:healing"}
/polyglotdump status
/polyglotdump pause
/polyglotdump resume
/polyglotdump cancel
```

Catalog selectors accept an exact item ID, a mod ID, or an explicit `*`. The optional row limit defaults to 10,000 and accepts 1–100,000. `defaults` takes one default stack per selected registered item; `creative` asks selected items for their search and declared creative-tab variants; `jei` takes registered ItemStack ingredients from JEI/HEI. Creative callbacks that fail are recorded as source errors. Duplicate stacks are removed using the complete typed stack NBT, actual damage and count, rather than just item ID and metadata.

The exporter observes one natural display-name call for each accepted stack and uses the guarded name plans/providers to render the configured languages. It does not call the full tooltip or repeatedly switch the game's language. The source stack is serialized and reconstructed before capture. Custom names remain literal. The export also works when the primary language is in the configured language list and the normal extra tooltip lines are consequently hidden.

The default `interactive` mode has a soft 4 ms budget and at most eight source steps per tick. Append `batch` when running a catalog inventory: it allows up to 512 source steps and a soft 20 ms budget per tick. The mode may follow the optional limit or replace it when the default limit is sufficient. `start held batch` is also supported. Omitting the mode on a new job always selects `interactive`, even after a batch job.

Batch mode spends more client-thread time on the export and may reduce frame smoothness. Duplicate and skipped source entries use the larger step allowance too. It preserves the same source order, stack identity, name capture, deduplication, row limits, bounded writer queue and lifecycle checks. An individual mod callback cannot be interrupted and can exceed either soft budget, especially on first use. Batch mode does not promise a hard frame-time limit or make a limited export complete. The manifest and status record the selected mode and budgets.

Pause/resume continues the current job in the same session. Cancelled jobs cannot resume. Changing the world, player, language configuration or relevant resource generation invalidates an active or paused job. A new job requires the previous writer to close.

## Output and interpretation

Each job creates a unique directory under the game's `exports/polyglot-names/` directory:

| File | Contents |
| --- | --- |
| `manifest.json` | Source, selector, limit, languages, loaded mod versions, selected resource packs and generation context. |
| `names.jsonl` | One item or source-error record per line, including stack identity, typed NBT, serialized NBT, native name and per-language results. |
| `names.tsv` | One row per item/language, with escaped tabs/newlines; source errors have blank language/name cells and a `source-error` status. |
| `summary.json` | Final state, processed/written counts, duplicates and whether the source was exhausted. |
| `failure.json` | Writer failure marker, when it can be written. |

Running, paused, cancelled, invalidated and limited exports use `names.partial.jsonl` / `names.partial.tsv`. Only a cleanly exhausted source receives the final filenames. Files alone do not prove completion: require a valid summary, matching row counts and no writer/source errors. A process exit can leave partial files without a summary. Files are flushed per row; no crash-durability guarantee is made.

Per-language statuses are `translated`, `same-as-native`, `literal`, `unresolved`, and `error`. `translated` means the resolved name differs from the primary native name; it is not an independent correctness verdict. `same-as-native` can be legitimate or a language fallback. An unresolved/error result is a candidate for investigation, not a confirmed translation defect. Post-capture NBT changes are recorded as `stateChanged` and `afterStack`.

Generate a per-mod diagnostic report from a finished or partial export:

```text
node test-tools/legacy112/name-export-report.mjs EXPORT_DIRECTORY NEW_REPORT_JSON
```

The report retains candidate stack identities, counts each language/status separately, lists source errors, and identifies loaded mods with zero sampled items. It rejects missing summaries, mismatched counts and malformed/truncated output. `selectedSourceComplete` refers only to the selected finite source. No finite catalog enumerates arbitrary NBT, scripts, world/player state, tooltip-only title rewrites or enchantment body lines. Use an independent native-language observation to confirm candidate name defects before changing a provider.

## Focused verification

The regression controller `test-tools/legacy112/name-export-verify.mjs OWNED_RUN_DIRECTORY` requires a prepared, identity-bound test fixture. It supplies twelve named samples and exact `minecraft:stone` catalog requests, compares exported names with independent native observations, and checks pause/resume, deduplication, cancellation, limits and language invalidation. Its bridge restricts samples to sixteen and catalog requests to one exact item ID with a limit of at most sixty-four. It does not enumerate the full pack.

`test-tools/legacy112/name-export-batch-verify.mjs OWNED_RUN_DIRECTORY PREVIOUS_CHUNKED_INVENTORY_RUN` compares interactive and batch modes in an owned Meatball fixture with retained inventory files. It caps each Microblocks catalog export at 64 accepted rows and reuses 128 frozen states from the previous inventory's third chunk for duplicate-heavy replay. Each scenario runs in interactive/batch/batch/interactive order. It compares all JSON row fields except timing, requires byte-identical TSV output, checks default-mode reset and command syntax, and exercises batch pause/resume, cancellation and language invalidation. Startup and language-resource loading are outside the reported export timings. This is a focused mode comparison, not a new full-pack inventory or a universal speedup guarantee.
