import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {report} from './runtime-common.mjs';
const require=createRequire('C:/Users/StarskyXIII/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/package.json');
const {chromium}=require('playwright');
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
const errors=[],results={};
const reportUrl=pathToFileURL(path.join(report,'index.html')).href;
try {
  const page=await browser.newPage({viewport:{width:1440,height:1050}});page.on('pageerror',error=>errors.push(String(error)));
  await page.goto(reportUrl);
  const links=await page.locator('a[href]').evaluateAll(nodes=>nodes.map(node=>node.getAttribute('href')));
  const missing=[];
  for(const href of new Set(links.filter(href=>href&&!href.startsWith('#')))) {
    const url=new URL(href,reportUrl);if(url.protocol!=='file:') continue;
    try{await fs.stat(fileURLToPath(url));}catch(error){missing.push({href,error:error.code});}
  }
  assert.deepEqual(missing,[],'Every local evidence link must exist');results.localEvidenceLinks=links.filter(href=>href&&!href.startsWith('#')).length;results.offlineFileProtocol=true;
  await page.locator('#mod-count').waitFor();assert.equal(await page.locator('#mod-table tbody tr').count(),25);
  await page.screenshot({path:path.join(report,'report-desktop.png')});
  for(const section of ['fresh','cost']) {
    await page.locator('#'+section).evaluate(node=>window.scrollTo({top:node.getBoundingClientRect().top+window.scrollY-75}));
    await page.screenshot({path:path.join(report,'report-'+section+'.png')});
  }
  for(const [name,title] of [['controls','獨立補充：同一有效環境的生命週期控制'],['tooltips','既有 tooltip 的完整文字比較']]) {
    await page.getByRole('heading',{name:title,exact:true}).evaluate(node=>window.scrollTo({top:node.getBoundingClientRect().top+window.scrollY-75}));
    await page.screenshot({path:path.join(report,'report-'+name+'.png')});
  }
  await page.locator('#mod-search').fill('microblock');assert.ok(await page.locator('#mod-count').innerText());
  const searched=await page.locator('#mod-table tbody tr').allTextContents();assert.ok(searched.length>=1 && searched.every(row=>row.toLowerCase().includes('microblock')));results.search=searched;
  await page.locator('#pack-filter').selectOption('Meatball');assert.equal(await page.locator('#mod-table tbody tr').count(),1);
  const download=page.waitForEvent('download');await page.locator('#download-csv').click();const file=await download;await file.saveAs(path.join(report,'report-filtered-csv-check.csv'));
  assert.ok((await fs.readFile(path.join(report,'report-filtered-csv-check.csv'),'utf8')).includes('microblockcbe'));
  await page.locator('#mod-search').fill('');await page.locator('#pack-filter').selectOption('');await page.locator('#next-page').click();assert.match(await page.locator('#page-count').innerText(),/第 2/);
  await page.locator('#scope-filter').selectOption('unsampled');assert.ok(!(await page.locator('#mod-table tbody').innerText()).includes('有限樣本有可比輸出'));
  for(const kind of ['disagreements','rejected','historicalChanges','unchanged']) {await page.locator('#example-kind').selectOption(kind);results[kind]=await page.locator('#example-count').innerText();}
  await page.locator('#mod-search').fill('no-such-mod-112');assert.match(await page.locator('#mod-table').innerText(),/沒有符合/);
  await page.setViewportSize({width:390,height:844});await page.goto(reportUrl);
  results.mobileOverflow=await page.evaluate(()=>({client:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));assert.equal(results.mobileOverflow.client,results.mobileOverflow.scroll);
  await page.screenshot({path:path.join(report,'report-mobile.png')});await page.locator('#mod-search').fill('tconstruct');
  await page.locator('#mod-table').evaluate(node=>window.scrollTo({top:node.getBoundingClientRect().top+window.scrollY-70}));
  await page.screenshot({path:path.join(report,'report-mobile-table.png')});assert.ok((await page.locator('#mod-table tbody').innerText()).includes('tconstruct'));
  assert.deepEqual(errors,[]);results.scriptErrors=errors;results.passed=true;
}finally{await browser.close();await fs.writeFile(path.join(report,'report-qa.json'),JSON.stringify(results,null,2)+'\n');}
