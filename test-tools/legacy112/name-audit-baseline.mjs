import {canonical, hash} from './name-audit-core.mjs';

const digest = value => hash(canonical(value));
const sha = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const owns = (value, key) => Object.hasOwn(value, key);
const require = (condition, reason) => { if (!condition) throw new Error(reason); };
const environmentFields = ['game', 'loader', 'java', 'dependencies', 'mods', 'embeddedJars',
  'transformConfigurationAndOrder', 'effectiveMethods', 'observer', 'scripts', 'configuration',
  'generatedInputs', 'registry', 'world', 'resourceProvidersInOrder', 'language',
  'selectiveReload', 'fontSettings'];
const caseFields = ['typedStack', 'tooltipFlag', 'modifiers', 'callOrdinal', 'history',
  'generation', 'ui', 'query', 'determinism'];

function jsonValue(value, ancestors = new Set()) {
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return;
  if (typeof value === 'number') { require(Number.isFinite(value), 'non-json-number'); return; }
  require(typeof value === 'object' && !ancestors.has(value), 'non-json-value');
  require(Array.isArray(value) || Object.getPrototypeOf(value) === Object.prototype
    || Object.getPrototypeOf(value) === null, 'non-json-object');
  require(Reflect.ownKeys(value).every(key => typeof key === 'string'), 'non-json-key');
  require(Reflect.ownKeys(value).length === Object.keys(value).length + Number(Array.isArray(value)), 'non-json-property');
  if (Array.isArray(value)) require(Object.keys(value).length === value.length
    && Object.keys(value).every((key, index) => key === String(index)), 'non-json-array');
  for (const key of Object.keys(value)) require(owns(Object.getOwnPropertyDescriptor(value, key), 'value'), 'non-json-accessor');
  ancestors.add(value);
  for (const entry of Object.values(value)) jsonValue(entry, ancestors);
  ancestors.delete(value);
}

export function environmentKey(environment) {
  require(object(environment), 'environment-missing');
  jsonValue(environment);
  require(Object.keys(environment).length === environmentFields.length, 'environment-fields');
  for (const field of environmentFields) require(sha(environment[field]), 'environment-' + field);
  return digest(environment);
}

export function observationKey(specimen) {
  require(object(specimen), 'case-missing');
  jsonValue(specimen);
  require(Object.keys(specimen).length === caseFields.length, 'case-fields');
  for (const field of caseFields) require(owns(specimen, field) && specimen[field] !== undefined, 'case-' + field);
  require(object(specimen.typedStack), 'case-typed-stack');
  require(['NORMAL', 'ADVANCED'].includes(specimen.tooltipFlag), 'case-flag');
  require(object(specimen.modifiers) && typeof specimen.modifiers.shift === 'boolean'
    && typeof specimen.modifiers.control === 'boolean', 'case-modifiers');
  require(Number.isSafeInteger(specimen.callOrdinal) && specimen.callOrdinal >= 1, 'case-ordinal');
  require(Array.isArray(specimen.history) && specimen.history.length > 0, 'case-history');
  require(sha(specimen.generation), 'case-generation');
  require(object(specimen.ui) && object(specimen.query), 'case-ui-query');
  const deterministic = specimen.determinism;
  require(object(deterministic) && deterministic.kind === 'source-closed'
    && sha(deterministic.sourceSha256) && sha(deterministic.externalStateSha256), 'unproven-determinism');
  return digest(specimen);
}

export function comparisonKey({nativeEnvironment, cases, productArtifact, productConfiguration,
  productTransforms, comparator}) {
  for (const value of [productArtifact, productConfiguration, productTransforms, comparator])
    require(sha(value), 'comparison-fingerprint');
  require(Array.isArray(cases) && cases.length > 0, 'comparison-cases');
  return digest({nativeEnvironment: environmentKey(nativeEnvironment), cases: cases.map(observationKey),
    productArtifact, productConfiguration, productTransforms, comparator});
}

const at = (value, field) => field.split('.').reduce((entry, key) => object(entry) && owns(entry, key) ? entry[key] : undefined, value);

function validateRow(row, specimen, requiredFields) {
  require(object(row) && row.observationAttempted !== false && !row.error && row.captureFailed === false, 'observation-incomplete');
  require(Array.isArray(row.tooltip) && row.tooltip.length > 0 && row.tooltip.every(line => typeof line === 'string'), 'raw-tooltip-missing');
  require(Array.isArray(row.created) && row.created.length === 0, 'native-contaminated');
  require(canonical(row.before) === canonical(specimen.typedStack) && object(row.after), 'typed-state-mismatch');
  require(row.tooltipFlag === specimen.tooltipFlag && canonical(row.modifiers) === canonical(specimen.modifiers), 'observation-input-mismatch');
  for (const field of requiredFields) require(at(row, field) !== undefined, 'required-observation-field:' + field);
}

export async function assessNativeReuse(candidate, request, readBytes) {
  try {
    require(object(candidate) && candidate.schema === 1 && candidate.complete === true, 'baseline-incomplete');
    require(candidate.productPresent === false && candidate.independentProcess === true, 'not-independent-native');
    require(object(candidate.provenance) && typeof candidate.provenance.pid === 'string'
      && candidate.provenance.pid.length > 0 && typeof candidate.provenance.nonce === 'string'
      && candidate.provenance.nonce.length > 0 && sha(candidate.provenance.sessionSha256), 'provenance-missing');
    require(typeof request.observationSchema === 'string' && request.observationSchema.length > 0
      && candidate.observationSchema === request.observationSchema, 'observation-schema');
    require(environmentKey(candidate.environment) === environmentKey(request.environment), 'environment-changed');
    require(Array.isArray(request.requiredFields) && request.requiredFields.length > 0
      && request.requiredFields.every(field => typeof field === 'string' && field.length > 0), 'required-fields-missing');
    require(Array.isArray(request.cases) && request.cases.length > 0, 'requested-cases-missing');
    const requested = new Map();
    for (const specimen of request.cases) {
      const key = observationKey(specimen);
      require(!requested.has(key), 'duplicate-request');
      requested.set(key, specimen);
    }
    require(Array.isArray(candidate.files) && candidate.files.length > 0, 'baseline-files-missing');
    require(Array.isArray(candidate.evidence) && candidate.evidence.length > 0, 'evidence-files-missing');
    const allPaths = new Set(), found = new Map(), evidenceHashes = new Set(), environmentEvidence = new Set();
    let sessionBound = false;
    for (const entry of [...candidate.evidence, ...candidate.files]) {
      require(object(entry) && typeof entry.path === 'string' && entry.path.length > 0
        && sha(entry.sha256) && !allPaths.has(entry.path), 'artifact-descriptor');
      allPaths.add(entry.path);
      const bytes = await readBytes(entry.path);
      require(hash(bytes) === entry.sha256, 'artifact-changed:' + entry.path);
      if (candidate.evidence.includes(entry)) {
        evidenceHashes.add(entry.sha256);
        if (typeof entry.role === 'string' && entry.role.startsWith('environment.')) {
          const field = entry.role.slice('environment.'.length);
          require(environmentFields.includes(field) && !environmentEvidence.has(field)
            && candidate.environment[field] === entry.sha256, 'environment-evidence');
          environmentEvidence.add(field);
        }
        if (entry.role === 'session') {
          require(!sessionBound && entry.sha256 === candidate.provenance.sessionSha256, 'session-evidence');
          const session = JSON.parse(bytes.toString('utf8'));
          require(session.pid === candidate.provenance.pid && session.config?.nonce === candidate.provenance.nonce
            && session.config?.product === false && session.identity?.product === false, 'session-not-native');
          sessionBound = true;
        }
        continue;
      }
      const artifact = JSON.parse(bytes.toString('utf8'));
      require(artifact.complete === true && artifact.observationSchema === request.observationSchema
        && artifact.sessionSha256 === candidate.provenance.sessionSha256, 'artifact-incomplete');
      require(Array.isArray(artifact.rows) && Number.isSafeInteger(entry.rows) && entry.rows > 0
        && artifact.rows.length === entry.rows, 'artifact-count');
      for (const record of artifact.rows) {
        const key = observationKey(record.case);
        require([record.case.determinism.sourceSha256, record.case.determinism.externalStateSha256,
          record.case.generation].every(value => evidenceHashes.has(value)), 'case-evidence-missing');
        require(!found.has(key), 'duplicate-observation');
        validateRow(record.observation, record.case, request.requiredFields);
        found.set(key, {path: entry.path, key});
      }
    }
    require(sessionBound, 'session-evidence-missing');
    require(environmentEvidence.size === environmentFields.length, 'environment-evidence-missing');
    require(Number.isSafeInteger(candidate.rows) && candidate.rows === found.size, 'baseline-count');
    require([...requested.keys()].every(key => found.has(key)), 'case-coverage-missing');
    return {reusable: true, environmentKey: environmentKey(candidate.environment),
      observations: [...requested.keys()].map(key => found.get(key))};
  } catch (error) {
    return {reusable: false, reason: error.message};
  }
}

export function selectContractCases(cases, requiredContracts) {
  require(Array.isArray(requiredContracts) && requiredContracts.length > 0
    && requiredContracts.every(key => typeof key === 'string' && key.length > 0), 'contracts-missing');
  require(new Set(requiredContracts).size === requiredContracts.length, 'duplicate-contract');
  const required = new Set(requiredContracts), covered = new Set(), selected = [], seen = new Set();
  for (const item of cases) {
    jsonValue(item);
    require(typeof item.id === 'string' && item.id.length > 0 && !seen.has(item.id), 'duplicate-case-id');
    seen.add(item.id);
    require(Array.isArray(item.contracts) && item.contracts.length > 0, 'case-contracts-missing');
    require(object(item.specimen), 'case-specimen-missing');
    for (const field of ['typedStack', 'tooltipFlag', 'modifiers', 'callOrdinal', 'history', 'generation', 'ui', 'query'])
      require(owns(item.specimen, field) && item.specimen[field] !== undefined, 'case-' + field);
    if (!item.contracts.some(key => required.has(key))) continue;
    selected.push(item);
    for (const key of item.contracts) if (required.has(key)) covered.add(key);
  }
  const missing = requiredContracts.filter(key => !covered.has(key));
  return {complete: missing.length === 0, selected, missing, sourceSha256: digest(cases),
    requirementsSha256: digest(requiredContracts)};
}
