import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import net from 'node:net';
import {verify} from './setup.mjs';
import {canonical} from '../lib/compare.mjs';

export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
export async function poll(action, description, timeout = 60000) {
  const deadline = Date.now() + timeout;
  do {
    const result = await action();
    if (result) return result;
    await sleep(500);
  } while (Date.now() < deadline);
  throw new Error(`Timed out: ${description}`);
}
export const alive = pid => { try { process.kill(pid, 0); return true; } catch (error) { if (error.code === 'ESRCH') return false; throw error; } };
export const occupied = port => new Promise(resolve => {
  const socket = net.connect({host: '127.0.0.1', port});
  socket.setTimeout(1000);
  socket.once('connect', () => {socket.destroy(); resolve(true);});
  socket.once('error', error => {socket.destroy(); resolve(error.code !== 'ECONNREFUSED');});
  socket.once('timeout', () => {socket.destroy(); resolve(true);});
});
const normalizedPath = value => path.resolve(value).toLowerCase();
export function validateIdentity(identity, profile) {
  assert.equal(identity.session, profile.session, 'Foreign Bridge session');
  assert.equal(normalizedPath(identity.gameDir), normalizedPath(profile.gameDir), 'Foreign game directory');
  assert(Number.isSafeInteger(identity.pid) && identity.pid > 0, 'Missing JVM PID');
  assert.equal(identity.language, profile.language, 'Wrong game language');
  assert.equal(identity.mods.minecraft?.version, '1.21.1', 'Wrong Minecraft version');
  assert.equal(identity.mods.neoforge?.version, '21.1.249', 'Wrong NeoForge version');
  for (const artifact of profile.artifacts) {
    const mod = identity.mods[artifact.id];
    assert(mod, `Missing loaded mod ${artifact.id}`);
    assert.equal(mod.version, artifact.version, `Wrong version of ${artifact.id}`);
    assert.equal(normalizedPath(mod.path), normalizedPath(path.join(profile.gameDir, 'mods', artifact.file)), `Wrong origin of ${artifact.id}`);
  }
  if (profile.name === 'polyglot-zh') {
    assert.equal(canonical(identity.settings), canonical({DISPLAY_LANGUAGE: ['en_us'], ALWAYS_SHOW: false}), 'Wrong product settings');
  } else assert(!identity.mods.polyglottooltip, 'Product present in native baseline');
}
export async function verifyProfile(profile) {
  const folder = path.join(profile.gameDir, 'mods');
  assert.deepEqual((await fs.readdir(folder)).sort(), profile.artifacts.map(a => a.file).sort(), 'Unexpected mod file set');
  for (const artifact of profile.artifacts) await verify(path.join(folder, artifact.file), artifact.sha256);
  assert.deepEqual(await fs.readdir(path.join(profile.gameDir, 'resourcepacks')), [], 'Unexpected resource packs');
}
