# Bridge item-name audit

Compare Polyglot's English secondary names with the full native English tooltip titles from released Reliquary and Malum mods. The audit also checks name placement, duplicate names, and preservation of the native Chinese tooltip content.

Follow the steps below to configure the tool, build it, run an audit, and review the findings. This is a separate test tool: it does not change the product or automatically accept compatibility differences.

## 1. Configure your local environment

You need:

- Node.js 22 or later, with npm.
- JDK 21 for both Gradle and the Minecraft clients.
- Git available on your command path.
- Prism Launcher with an account that can launch Minecraft.
- Three consecutive free local ports, starting at the `basePort` you configure below.

Start in the repository root and open the audit directory:

```powershell
Set-Location test-tools/name-audit
```

For the first run, create your local configuration:

```powershell
Copy-Item local.example.json local.json
```

If you already have `local.json`, keep it and update its values instead. Edit the file using [local.example.json](local.example.json) as a reference:

| Setting | What to enter |
| --- | --- |
| `prismExecutable` | Path to `prismlauncher.exe`. |
| `prismData` | Prism's data directory, which contains its `instances` folder. |
| `javaExecutable` | Path to the JDK 21 `javaw.exe` used to launch the test clients. |
| `productJar` | Path to the local Polyglot JAR you want to test. The example points to the output of step 3. |
| `basePort` | First of three consecutive free ports; the default is `25971`. |

Relative paths are resolved from `test-tools/name-audit`. Use forward slashes in JSON paths, as shown in the example. `local.json` is ignored by Git.

The `javaExecutable` setting controls the test clients. Your shell must also use JDK 21 when running Gradle in step 3.

## 2. Download the pinned dependencies and check the runner

Still in `test-tools/name-audit`, run:

```powershell
npm run fetch
npm test
```

`fetch` downloads the release JARs listed in [dependencies.json](dependencies.json), verifies their SHA-256 hashes, and copies the Bridge JAR into the companion's compile-time library directory. Run it before building the companion.

`npm test` checks the comparison rules and runner helpers without launching Minecraft. Continue after both commands succeed.

## 3. Build Polyglot and the test companion

Return to the repository root and build with JDK 21:

```powershell
Set-Location ../..
.\gradlew.bat :1.21.1-neoforge:build
.\gradlew.bat -p test-tools/name-audit/companion build
```

The two Gradle commands build the product under test and the separate audit companion, respectively. The companion build also runs its Java tests. The companion is installed only in the dedicated test instances.

After both builds succeed, return to the audit directory:

```powershell
Set-Location test-tools/name-audit
```

For a Windows Gradle socket-path error, see [Troubleshooting](#troubleshooting).

## 4. Validate the setup and run the audit

From `test-tools/name-audit`, run:

```powershell
npm run prepare
npm run audit
```

`prepare` checks the configured paths, release JAR hashes, and local build outputs. It does not launch Minecraft. Resolve any reported error before running the audit.

Each audit creates three new, dedicated Prism instances and starts them in order:

| Run | Game language | Polyglot | Purpose |
| --- | --- | --- | --- |
| `native-en` | English (`en_us`) | Absent | Collect samples and capture the expected English titles. |
| `native-zh` | Simplified Chinese (`zh_cn`) | Absent | Replay the same samples and capture the expected primary titles and native content. |
| `polyglot-zh` | Simplified Chinese (`zh_cn`) | Installed | Replay the samples with English secondary names and `alwaysShow=false`. |

The runner verifies each client's identity and mod files before collecting evidence. It waits for the current client's JVM to exit and its port to close before starting the next instance. Existing worlds are neither copied nor deleted.

For a run that returns a nonzero exit code when it finds name mismatches or inconclusive results, use this **instead of** `npm run audit`:

```powershell
npm run audit:strict
```

Normal and strict modes perform the same checks. Their exit behavior differs.

## 5. Review the results

The runner prints the output directory: `evidence/<UTC timestamp>/`.

Start with **`report.md`** for the findings and control-case results. Then use **`result.json`** to check whether the run completed correctly, including client shutdown.

| Field in `result.json` | Meaning |
| --- | --- |
| `infrastructure` | Whether setup, collection, validation, rendering checks, and shutdown completed successfully. |
| `compatibility` | Whether the completed comparison passed or produced findings. |
| `counts` | Numbers of passing, mismatched, and inconclusive samples. |

**A successful scan can still find compatibility problems.** Read both status fields, or use strict mode when the exit code must reflect those findings.

| Outcome | Normal mode | Strict mode |
| --- | ---: | ---: |
| Scan completed; all samples passed | `0` | `0` |
| Scan completed; mismatches or inconclusive results found | `0` | `2` |
| Setup, observation, validation, rendering, or shutdown failed | `1` | `1` |

An incomplete run may have only partial evidence and no comparison report.

For a closer investigation, use these files in the same output directory:

| File | Contents |
| --- | --- |
| `collection.json` | Raw sample collection and sources. |
| `samples.json` | Frozen full ItemStack data replayed across all three runs. |
| `coverage.json` | Collected variant counts and sources for each item ID. |
| `<profile>/observations.json` | Full tooltips, Components, hover names, observed secondary lines, and stack data before and after each observation. |
| `comparison.json` | Expected and actual names, findings, and additional body lines for every sample. |
| `<profile>/*.png` | Rendered examples for stone, a charm fragment, a charm, and a Geas. |

To test another product build, rebuild it, confirm `productJar` points to the intended JAR, and repeat steps 4–5. Every audit captures fresh native baselines.

## Troubleshooting

### A previous run left `.run.lock`

The lock prevents overlapping runs from this tool directory. If the runner cannot verify a client's identity or confirm its shutdown, it preserves the lock and instance for recovery.

1. Read the latest `result.json` and its recovery information. If needed, `.run.lock` identifies the evidence directory.
2. Identify the dedicated test instance named in the report.
3. Close that instance if it is still running. Confirm that its JVM has exited and its port is closed.
4. Remove `.run.lock` only after those checks, then retry.

Do not close an unidentified game process. Completed test instances remain in Prism for inspection or manual removal; the runner does not delete them automatically.

### Gradle reports a Windows Unix-domain socket path error

Use your existing local Gradle workaround. In the tested environment, setting the `jdk.net.unixdomain.tmpdir` Java property through `JAVA_TOOL_OPTIONS` to a short, nonexistent path allowed Gradle to start. Apply this only when that specific error occurs. The tool does not change system Java settings or Prism's global settings.

## Scope and comparison rules

### Fixed test environment

The current suite uses Minecraft **1.21.1**, NeoForge **21.1.249**, Reliquary **2.0.80.1570**, Malum **1.8.2**, Lodestone **1.8.2**, Curios **9.5.1+1.21.1**, and ClientDevBridge **1.0.0-171**. Release identifiers, download URLs, and SHA-256 hashes are recorded in [dependencies.json](dependencies.json). Reliquary reports the internal mod version `2.0.80`; its filename retains the full release version.

English and Simplified Chinese are used because the pinned JARs provide those languages. Results do not cover external Traditional Chinese resource packs, other Minecraft versions, or other loaders.

### Samples and expected names

- Samples include creative-tab and search-tab ItemStack variants, plus registry defaults. Duplicate collection sources are merged; duplicate identities in the frozen manifest are an error.
- Required coverage includes zombie and skeleton charms and fragments, plus at least two distinct, populated Geas effects. Coverage is limited to the variants actually collected.
- The expected English name is the **final native tooltip's first line with Polyglot absent**, not the hover name alone or a value produced by Polyglot's resolver. The Chinese baseline supplies the expected primary title and native content.
- Literal custom names should not receive a secondary name. Translatable custom names follow the normal language rules. Unknown missing translation keys are inconclusive; a deliberate missing-key control checks fallback behavior separately.

### Text, placement, and observation

- Text comparison removes Minecraft formatting codes only. Case, punctuation, whitespace, and variant names remain significant.
- The expected secondary-name position is index `1`, directly below the primary title. The original tooltip lines must remain in order after removing the tracked secondary line. Additional details are recorded as `additionalBodyLines`; extra copies of either title still fail.
- A test-only optional Mixin observes Components created by Polyglot and checks their identity and position in the final tooltip. Its scope is limited to the current thread and tooltip call, so background indexing cannot contaminate the sample.
- Native runs must produce no product-observer records. The stone control must confirm that the observer and product settings work. A native tooltip that already contains English does not count as a successful product insertion.
- A created line that is removed or copied and loses its observable identity is inconclusive. Nested tooltip calls on the same thread may also be inconclusive. Native body lines that are replaced, removed, or reordered remain `native-tooltip-changed`, even when another feature might explain the change.

### Full stack identity

Samples retain all serialized components with a count of `1`. Object keys are sorted for comparison; ordinary array order is preserved. The runner checks that decoding and observing a sample do not change its data.

The one set-order exception is `neoforge:cures` within `minecraft:potion_contents.custom_effects[*]` and its recursive `hidden_effect` entries. NeoForge 21.1.249 serializes these values with `setOf(EffectCure.CODEC)`, so their output order can differ between JVMs. Only those string members are sorted; no values are removed or deduplicated. Raw JSON is retained, and `result.json` records the canonicalization version. Changes to effect order, duration, cure membership, or other components still fail validation.

### Rendering coverage

The full sample set is checked through tooltip data. Representative screenshots use a dedicated screen calling the native ItemStack tooltip renderer, with fresh-frame title checks. They do not establish coverage of JEI/EMI overlays, every item's visual layout, or pixel equality across GPUs.
