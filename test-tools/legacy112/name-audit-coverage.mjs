import {equal} from './name-audit-core.mjs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const artifactHashes = {tconstruct: '80ea6b4fe648234477d74481ade97467b68634a288c256a3dea4a624562a0c0c',
  actuallyadditions: '21311e401f36a58222a50b6f0d66ac29614d7ab039c961dcc307fe499da030b6'};
const partClasses = ['slimeknights.tconstruct.library.tools.ToolPart', 'slimeknights.tconstruct.library.tools.Shard',
  'slimeknights.tconstruct.tools.common.item.SharpeningKit'];
const profiles = Object.fromEntries([
  ['tools.Pickaxe', 3, [1]], ['tools.Shovel', 3, [1]], ['tools.Hatchet', 3, [1]], ['tools.Kama', 3, [1]],
  ['tools.Mattock', 3, [1, 2]], ['tools.Excavator', 4, [1, 2]], ['tools.LumberAxe', 4, [1, 2]], ['tools.Scythe', 4, [1, 2]],
  ['tools.Hammer', 4, [1, 2, 3]], ['melee.item.BattleAxe', 4, [1, 2]], ['melee.item.BattleSign', 2, [1]],
  ['melee.item.BroadSword', 3, [1]], ['melee.item.Cleaver', 4, [1, 2]], ['melee.item.FryPan', 2, [1]],
  ['melee.item.LongSword', 3, [1]], ['melee.item.Rapier', 3, [1]], ['ranged.item.Arrow', 3, [1]],
  ['ranged.item.Bolt', 3, [1]], ['ranged.item.CrossBow', 4, [1]], ['ranged.item.LongBow', 4, [0, 1]],
  ['ranged.item.ShortBow', 3, [0, 1]], ['ranged.item.Shuriken', 4, [0, 1, 2, 3]]]
  .map(([name, components, repair]) => ['slimeknights.tconstruct.tools.' + name, {components, repair}]));
const normalizedPath = value => path.resolve(value).toLowerCase();

export function nativeCoverage(corpus, nativeRows, diagnostics, sourceEvidence = [], {matchingNativeMutations = []} = {}) {
  const rows = nativeRows.map(lane => new Map(lane.map(row => [row.id, row])));
  const mutationIds = new Set(matchingNativeMutations);
  const nativeInitialization = sample => {
    if (!mutationIds.has(sample.id) || rows.length !== 2 || sample.stack.type !== 10 || sample.stack.value.ForgeCaps !== undefined) return false;
    const after = rows[0].get(sample.id)?.after;
    return after?.type === 10 && after.value.ForgeCaps?.type === 10 && equal(after, rows[1].get(sample.id)?.after)
      && equal(Object.keys(after.value).sort(), [...Object.keys(sample.stack.value), 'ForgeCaps'].sort())
      && Object.entries(sample.stack.value).every(([key, value]) => equal(value, after.value[key]));
  };
  const details = diagnostics.map(lane => new Map(lane.filter(row => !row.diagnosticError).map(row => [row.id, row.diagnostics])));
  const occurrences = new Map();
  for (const sample of corpus.samples) for (const source of sample.sources) occurrences.set(source.index, (occurrences.get(source.index) ?? 0) + 1);
  const subitemsWitness = sample => sample.sources.map(origin => ({...corpus.sources[origin.index], index: origin.index}))
    .find(source => source.kind?.startsWith('subitems:') && source.owner === sample.item && !source.error
      && source.id === source.index && Number.isInteger(source.returned) && source.returned > 0
      && source.returned >= source.matched && source.matched === source.serialized && source.serialized === occurrences.get(source.index));
  const pinnedSource = sample => sourceEvidence.length === 2 && details.length === 2 && details.every((lane, index) => {
    const diagnostic = lane.get(sample.id), namespace = sample.item.split(':')[0];
    if (!diagnostic || diagnostic.item !== sample.item || diagnostic.metadata !== sample.metadata) return false;
    const mods = sourceEvidence[index].filter(row => row.id === namespace);
    if (mods.length !== 1 || mods[0].file?.sha256 !== artifactHashes[namespace]) return false;
    try {
      let source = diagnostic.classSource;
      if (source.startsWith('jar:')) {
        const delimiter = source.indexOf('!/');
        if (delimiter < 0 || source.slice(delimiter + 2) !== diagnostic.class.replaceAll('.', '/') + '.class') return false;
        source = source.slice(4, delimiter);
      }
      return normalizedPath(fileURLToPath(source)) === normalizedPath(mods[0].file.path);
    }
    catch { return false; }
  });
  const valid = sample => sample.sources.length > 0 && sample.sources.every(source => source.sourceUnchanged === true)
    && sample.sources.some(source => {
      const origin = corpus.sources[source.index];
      return origin && !origin.error && /^(ingredient-registry$|subitems:)/.test(origin.kind);
    }) && pinnedSource(sample) && rows.length === 2
    && rows.every(lane => {
      const row = lane.get(sample.id);
      return row && !row.error && row.captureFailed === false && row.literalCustomName === false && row.tooltip?.length > 0
        && row.modifiers?.shift === false && row.modifiers?.control === false && row.created?.length === 0
        && equal(row.before, sample.stack) && (equal(row.after, sample.stack) || nativeInitialization(sample));
    }) && !['Name', 'LocName'].some(key => sample.stack.value.tag?.value.display?.value[key]?.type === 8);
  const classes = (sample, expected) => details.every(lane => lane.get(sample.id)?.class === expected);
  const owners = (sample, expected) => details.every(lane => lane.get(sample.id)?.nameOwner === expected);
  const drillStates = [], crystalStates = [], materialParts = [], materialTools = [];
  for (const sample of corpus.samples.filter(valid)) {
    const tag = sample.stack.value.tag;
    if (sample.item === 'actuallyadditions:item_drill' && classes(sample, 'de.ellpeck.actuallyadditions.mod.items.ItemDrill')) {
      const energy = tag?.type === 10 ? tag.value.Energy : null;
      if (energy?.type === 3 && ['0', '250000'].includes(energy.value) && sample.metadata >= 0 && sample.metadata < 16) {
        drillStates.push({id: sample.id, metadata: sample.metadata, energy: energy.value, path: 'tag.Energy', type: energy.type});
      }
    }
    if (['actuallyadditions:item_crystal', 'actuallyadditions:item_crystal_empowered'].includes(sample.item)
        && classes(sample, 'de.ellpeck.actuallyadditions.mod.items.ItemCrystal') && sample.metadata >= 0 && sample.metadata < 6) {
      const suffix = ['red', 'blue', 'light_blue', 'black', 'green', 'white'][sample.metadata];
      const expectedKey = 'item.actuallyadditions.' + sample.item.split(':')[1] + '_' + suffix + '.name';
      if (details.every(lane => lane.get(sample.id)?.key === expectedKey)) {
        crystalStates.push({id: sample.id, item: sample.item, metadata: sample.metadata, key: expectedKey});
      }
    }
    if (!sample.item.startsWith('tconstruct:') || tag?.type !== 10) continue;
    const diagnostic = details[0].get(sample.id), witness = subitemsWitness(sample);
    if (!witness) continue;
    if (owners(sample, 'slimeknights.tconstruct.library.tools.ToolPart') && partClasses.includes(diagnostic.class)
        && classes(sample, diagnostic.class)) {
      const material = tag.value.Material;
      if (material?.type === 8 && material.value && material.value !== 'unknown') {
        materialParts.push({id: sample.id, item: sample.item, path: 'tag.Material', material: material.value,
          nameOwner: diagnostic.nameOwner, class: diagnostic.class, witness});
      }
    }
    const profile = profiles[diagnostic.class];
    if (owners(sample, 'slimeknights.tconstruct.library.tools.ToolCore') && profile && classes(sample, diagnostic.class)) {
      const data = tag.value.TinkerData, materials = data?.type === 10 ? data.value.Materials : null;
      if (materials?.type === 9 && materials.elementType === 8 && materials.value.length === profile.components
          && materials.value.every(value => value.type === 8 && value.value && value.value !== 'unknown')) {
        materialTools.push({id: sample.id, item: sample.item, path: 'tag.TinkerData.Materials', materials: materials.value.map(value => value.value),
          nameOwner: diagnostic.nameOwner, class: diagnostic.class, profile, witness});
      }
    }
  }
  const drillMissing = [];
  for (let metadata = 0; metadata < 16; metadata++) for (const energy of ['0', '250000']) {
    if (!drillStates.some(row => row.metadata === metadata && row.energy === energy)) drillMissing.push({metadata, energy});
  }
  const witnessedIds = new Set(materialParts.map(row => row.material));
  const admittedTools = materialTools.filter(row => row.materials.every(value => witnessedIds.has(value)))
    .map(row => ({...row, selectedNameMaterials: [...new Set(row.profile.repair.map(index => row.materials[index]))]}));
  const nameBearingIds = [...new Set([...witnessedIds, ...admittedTools.flatMap(row => row.selectedNameMaterials)])].sort();
  const minimum = {materialPart: materialParts.length > 0, repairNameTool: admittedTools.length > 0, distinctNameMaterials: nameBearingIds.length >= 2};
  return {drillStates, drillMissing, crystalStates, materialParts, materialTools: admittedTools,
    unprovenMaterialTools: materialTools.filter(row => !admittedTools.some(tool => tool.id === row.id)),
    materialIdsObservedInExactNativePaths: nameBearingIds, minimum, minimumMaterialCoverage: Object.values(minimum).every(Boolean),
    ordinaryPartCovered: materialParts.some(row => row.class === partClasses[0]),
    sharpeningKitCovered: materialParts.some(row => row.class === partClasses[2]),
    multipleNameMaterialsInOneTool: admittedTools.filter(row => row.selectedNameMaterials.length >= 2).map(row => row.id),
    limits: ['Registry-backed native provider constructions are inferred from pinned source and same-item subitems receipts; final transformed provider bodies and live registry membership at later timestamps are not attested.',
      'Coverage is bounded to observed exact native classes and does not prove whole-registry enumeration or arbitrary addon subclass support.']};
}
