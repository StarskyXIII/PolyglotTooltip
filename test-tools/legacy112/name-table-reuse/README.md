# 1.12.2 matrix tools

This directory holds the game-test runners and controllers for the 1.12.2 mod. Only the files in the first table work with the current mod. The others were written for the earlier implementation (background name tables, bytecode observation, `/polyglotnames`), which is kept at tag `archive/legacy112-admission-2026-10-22`; they are kept unchanged as a record of how its results were produced and only make sense at that tag.

## Current mod

| File | Purpose |
| --- | --- |
| `core-matrix.py` | Runs the full matrix on one or all of the five `PT-Matrix-*` clones, with an optional product-free baseline launch for the JEI/HEI `IngredientFilter` build time, and restores each clone afterwards. `core-matrix.py summary <attempt>` rebuilds the summary. |
| `core-check.py` | Runs one scenario on one clone or instance: `ae2` (terminal search) or `export` (the [name export](../../../legacy/1.12.2/NAME-EXPORT.md)). Restores the clone afterwards. |
| `core-check.mjs` | Bridge controller for `core-matrix.py` (scenarios `matrix` and `baseline`) and `core-check.py` (`ae2`, `export`), and for the `rs` scenario on a `launch.py` fixture. |
| `consistency-run.py` | Measures how many ingredients pass the name consistency check on the clones, independently of the mod. |
| `consistency-check.mjs` | Bridge controller for `consistency-run.py` (`probe.consistency`). |
| `consistency-summary.py` | Summarizes the `consistency-run.py` reports. |

Usage is in each runner's docstring. The controllers share `../name-table/runtime-common.mjs` and `../../name-audit/runner/bridge.mjs`; `../call.mjs` calls a single bridge method, and `../launch.py` builds the Forge and Cleanroom fixtures. All of them need the devbridge jars from `..\..\gradlew.bat :devbridge:build` (run in `legacy/1.12.2`). Only one bridged game may run at a time, on port 19594.

## Earlier implementation

Every other file here belongs to the earlier implementation, including `matrix-check.py`, `pack-check.mjs`, `nova-*-check.*`, `dj2-check.mjs`, `ic2-*`, `meatball-hud-check.py`, `play-*`, `startup-smoke.*`, the `phase*` and `run-*` runners, `core-runtime.*`, `diagnostic-runtime.mjs`, `scoped-runtime.mjs`, `measure.*` and the analysis scripts. Many of them call devbridge methods that have been removed (`audit.*`, `probe.nameTable`, `probe.nameReuse`, `lifecycle.preparation` and others) or read the earlier mod's classes and files. Run them only from a checkout of the archive tag. The same applies to the other controllers in `test-tools/legacy112` that call those methods.
