import assert from 'node:assert/strict';
import {run} from './integration-runner.mjs';

await run(async ({call, check, observations: o, render, baseline}) => {
  o.initial = await call('status');
  const toggle = async () => {
    const screen = await call('screen.snapshot'), button = screen.buttons.find(button => button.id === 100); assert.ok(button);
    await call('input.click', {x: button.x + 8, y: button.y + 8}); return call('status');
  };
  try {
    o.open = await call('language.open'); o.englishShot = await render('language-english');
    if (!o.initial.forceUnicode) await toggle();
    o.forced = await call('language.set', {language: 'zh_cn'}); o.chinese = await call('screen.snapshot'); o.chineseShot = await render('language-chinese');
    check('native language operation selects actual language code', o.forced.language, 'zh_cn');
    check('native language operation preserves forced Unicode', [o.forced.forceUnicode, o.forced.unicode], [true, true]);
    check('language operation preserves native language screen', o.chinese.class, 'net.minecraft.client.gui.GuiLanguage');
    check('native Done button relabels after language change', o.chinese.buttons.find(button => button.id === 6)?.text, '完成');
    o.englishForced = await call('language.set', {language: 'en_us'});
    check('English locale still respects forced Unicode', [o.englishForced.forceUnicode, o.englishForced.unicode, o.englishForced.bidi], [true, true, false]);
    o.unforced = await toggle(); check('native force-Unicode button toggles actual font state', [o.unforced.forceUnicode, o.unforced.unicode], [false, false]);
    o.arabic = await call('language.set', {language: 'ar_sa'}); o.arabicShot = await render('language-arabic');
    check('native right-to-left language sets bidi font flag', [o.arabic.language, o.arabic.bidi], ['ar_sa', true]);
    o.fullReload = await call('resources.reload');
    check('explicit full reload preserves current language and font flags', [o.fullReload.language, o.fullReload.forceUnicode, o.fullReload.unicode, o.fullReload.bidi], [o.arabic.language, o.arabic.forceUnicode, o.arabic.unicode, o.arabic.bidi]);
    o.restoredEnglish = await call('language.set', {language: 'en_us'});
    check('returning to English clears locale bidi and Unicode', [o.restoredEnglish.bidi, o.restoredEnglish.unicode], [false, false]);
    if (baseline) for (const key of ['forced', 'englishForced', 'unforced', 'arabic', 'fullReload', 'restoredEnglish']) check(`${key} native language flags equal baseline`, [o[key].language,o[key].forceUnicode,o[key].unicode,o[key].bidi], [baseline[key].language,baseline[key].forceUnicode,baseline[key].unicode,baseline[key].bidi]);
  } finally {
    if ((await call('status')).language !== 'en_us') await call('language.set', {language:'en_us'});
    if ((await call('status')).forceUnicode !== o.initial.forceUnicode) {if ((await call('status')).screen !== 'net.minecraft.client.gui.GuiLanguage') await call('language.open');await toggle();}
    await call('screen.close');
  }
});
