const {createRequire} = require('node:module');
const fs = require('node:fs/promises');
const path = require('node:path');
const {pathToFileURL, fileURLToPath} = require('node:url');
const {createHash} = require('node:crypto');
const assert = require('node:assert/strict');
const modules = process.env.CODEX_REPORT_NODE_MODULES;
assert.ok(modules, 'Set CODEX_REPORT_NODE_MODULES to the installed browser runtime packages');
const {chromium} = createRequire(path.join(modules, '../check-report.cjs'))('playwright');

(async () => {
  const report = path.resolve(__dirname, '../../../docs/research/legacy112/name-table-reuse-2026-10-14');
  const stem = process.argv[2] || 'core-v1-report';
  assert.ok(['core-v1-report', 'shared-scope-report'].includes(stem));
  const outputPrefix = stem === 'core-v1-report' ? 'report' : stem;
  const browser = await chromium.launch({executablePath: process.env.CODEX_REPORT_BROWSER, headless: true});
  const errors = [], results = [];
  try {
    const page = await browser.newPage();
    page.on('pageerror', error => errors.push(String(error)));
    for (const [width, colorScheme] of [[1280, 'light'], [360, 'light'], [1280, 'dark']]) {
      await page.setViewportSize({width, height: 900});
      await page.emulateMedia({colorScheme});
      await page.goto(pathToFileURL(path.join(report, stem + '.html')).href);
      await page.screenshot({path: path.join(report, `${outputPrefix}-${width}-${colorScheme}.png`), fullPage: true});
      await page.screenshot({path: path.join(report, `${outputPrefix}-${width}-${colorScheme}-top.png`)});
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
      assert.equal(overflow, false, `No page overflow at ${width}px`);
      results.push({width, colorScheme, overflow});
    }
    const details = page.locator('details').filter({has: page.locator('input.filter')}).first();
    const total = await details.count() ? await details.locator('tbody tr').count() : 0;
    if (total) {
      await details.locator('summary').click();
      await details.locator('input').fill('this-item-is-not-in-the-report');
      assert.equal(await details.locator('tbody tr:not([hidden])').count(), 0);
      await details.locator('input').fill('');
      assert.equal(await details.locator('tbody tr:not([hidden])').count(), total);
    }
    assert.deepEqual(errors, []);
    const links = await page.locator('a').evaluateAll(values => values.map(value => value.href));
    for (const href of links) {
      if (href.startsWith('file:')) await fs.access(fileURLToPath(href));
      else assert.equal(href, 'https://github.com/GTNewHorizons/NotEnoughItems/blob/master/src/main/java/codechicken/nei/util/ItemUntranslator.java',
        'Only the inspected GTNH primary source is external');
    }
    const sha256 = createHash('sha256').update(await fs.readFile(path.join(report, stem + '.html'))).digest('hex');
    const result = {at: new Date().toISOString(), file: stem + '.html', sha256, results, filterRows: total, linksChecked: links.length, errors};
    await fs.writeFile(path.join(report, outputPrefix + '-visual-check.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
