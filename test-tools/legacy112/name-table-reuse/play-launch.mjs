// The no-bridge play check (play-check.py) for the jar a bridged check is measuring. The research bridge patches
// ItemStack too, so a bridged pass does not show what the player gets: Nova's tool enchantment rows passed every bridged
// check while real play refused their source. A bridged summary therefore carries the play launch's WARN lines and fails
// when there was none for this jar and instance, or when it had blocking lines.
import fs from 'node:fs/promises';
import path from 'node:path';

const same = (left, right) => path.resolve(String(left)).toLowerCase() === path.resolve(String(right)).toLowerCase();

/** The latest run-play-* summary next to `run` for config.candidate on config.fixture's game directory. */
export async function playLaunchWarnings(run, config) {
  const folder = path.dirname(path.resolve(run));
  let latest = null;
  for (const name of await fs.readdir(folder)) {
    if (!name.startsWith('run-play-')) continue;
    let summary;
    try { summary = JSON.parse(await fs.readFile(path.join(folder, name, 'summary.json'), 'utf8')); } catch { continue; }
    if (summary.candidate !== config.candidate || summary.bridge !== false || !same(summary.game, config.fixture)) continue;
    if (!latest || summary.at > latest.summary.at) latest = {name, summary};
  }
  if (!latest) return {checked: false, failed: 'play-launch-unchecked'};
  const {summary} = latest, found = summary.playLaunchWarnings;
  const failed = summary.crash || !summary.verdict ? 'play-launch-failed' : !summary.parseMatches ? 'play-launch-language'
    : found.blocking > 0 ? 'play-launch-warnings' : null;
  return {checked: true, failed, run: latest.name, at: summary.at, verdict: summary.verdict, count: found.count, blocking: found.blocking,
    byCategory: found.byCategory, blockingLines: found.lines.filter(line => !line.accepted).map(line => line.message)};
}
