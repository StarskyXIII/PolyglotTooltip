from pathlib import Path
import argparse
import datetime
import hashlib
import html
import json

parser = argparse.ArgumentParser()
parser.add_argument('--candidate', required=True)
parser.add_argument('--e2e-build', required=True)
parser.add_argument('--e2e-reuse', required=True)
parser.add_argument('--meatball-build', required=True)
parser.add_argument('--meatball-reuse', required=True)
parser.add_argument('--shared-scope', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-reuse-2026-10-14'
esc = lambda value: html.escape(str(value))
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig')) if file.is_file() else None
link = lambda relative, label: f'<a href="{esc(relative)}">{esc(label)}</a>'
fmt = lambda value: f'{value:,}' if isinstance(value, int) else esc(value if value is not None else '—')
seconds = lambda ms: f'{ms / 1000:,.1f} 秒' if ms is not None else '—'

def formatted_check(record):
    originals = [row for row in record['queries'] if '§' in row.get('nativeName', '')
                 and any(value.get('source') == 'table' for value in (row.get('languages') or {}).get('values', []))]
    if not originals:
        return None
    actual = (record['formatted-after-search'] or {}).get('rows', [])
    return len(actual) == len(originals) and all(
        before['nativeName'] == after.get('nativeName') and not after.get('error')
        and all(value in (after.get('languages') or {}).get('values', [])
                for value in before['languages']['values'] if value.get('source') == 'table')
        for before, after in zip(originals, actual))

candidate = report / args.candidate
assert candidate.parent == report and candidate.is_dir()
artifacts = read(candidate / 'artifacts.json')
tests = read(candidate / 'tests.json')
expected = {row['file']: row['sha256'] for row in artifacts['files']}
for file, digest in expected.items():
    assert hashlib.sha256((candidate / file).read_bytes()).hexdigest() == digest

packs = []
for pack in ['E2E', 'Meatball']:
    prefix = pack.lower()
    records = {}
    for mode in ['build', 'reuse']:
        attempt = getattr(args, prefix + '_' + mode)
        directory = report / f'run-{prefix}-{attempt}'
        assert directory.parent == report
        records[mode] = {key: read(directory / (key + '.json')) for key in [
            'complete', 'failure', 'restoration', 'launch', 'query-summary', 'raw-catalog-controls', 'persisted-table',
            'search-iron-ingot', 'search-observations', 'formatted-after-search', 'emi-search-iron-ingot', 'ae-search-iron-ingot', 'it-search-iron-ingot',
            'config', 'menu', 'before-quit', 'search-consumers', 'shared-transition', 'shared-autosave-observations', 'shared-autosave-result', 'external-config-event-summary']}
        records[mode]['directory'] = directory.name
        records[mode]['queries'] = [row for file in sorted(directory.glob('queries-*.json')) for row in read(file)['rows']]
    build, reuse = records['build'], records['reuse']
    build_snapshot = (build['complete'] or {}).get('final') or (build['shared-transition'] or {}).get('after', {})
    bt = build_snapshot.get('table', {})
    rt = (reuse['complete'] or {}).get('final', {}).get('table', {})
    complete = bool(build['complete'] and reuse['complete'])
    build_file = build['persisted-table'] or (build['shared-transition'] or {}).get('tableAfter')
    build['table-file-evidence'] = build_file
    checks = {
        '建立完成並曾可查詢': bool(bt.get('ready') and bt.get('builds', 0) >= 1),
        '兩次完整流程均未中止': bool(complete and not build['failure'] and not reuse['failure']),
        '另啟 JVM 載入相同 generation': bool(bt.get('tableGeneration') and bt.get('tableGeneration') == rt.get('tableGeneration')),
        '重用不重新建表／切換語言': bool(rt.get('ready') and rt.get('loads', 0) >= 1 and rt.get('builds') == 0 and rt.get('switches') == 0),
        '兩次原始語言與設定均還原': all(bool(value['before-quit'] and value['restoration'] and value['restoration'].get('optionsRestored') and value['restoration'].get('clientExitCode') == 0
            and value['before-quit']['state'].get('language') == value['config']['initialLanguage']
            and all(value['before-quit']['state'].get(key) == value['menu'].get(key) for key in ['selectiveResourceReload', 'forceUnicode'])) for value in records.values()),
        '兩次實際 Tooltip 表命中': all(bool(value['query-summary'] and value['query-summary']['tooltipHits'] > 0) for value in records.values()),
        '兩次 JEI 英文搜尋找到鐵錠': all(bool(value['search-iron-ingot'] and any(row.get('id') == 'minecraft:iron_ingot' for row in value['search-iron-ingot'].get('rows', []))) for value in records.values()),
        '兩次搜尋索引完成且名稱表可用': all(bool(value['search-observations'] and value['search-observations'][-1]['performance'].get('aliasReady') and value['search-observations'][-1]['table'].get('ready')) for value in records.values()),
        '查詢樣本沒有執行錯誤': all(bool(value['query-summary'] and not value['query-summary']['errors']) for value in records.values()),
        '重用前後表檔內容與修改時間不變': bool(build_file and reuse['persisted-table'] and all(str(build_file[key]) == str(reuse['persisted-table'][key]) for key in ['generation', 'sha256', 'modifiedNs'])),
        '查詢採用的原生名稱前後一致': all(bool(value['queries']) and all(row.get('nativeName') == row.get('repeatNativeName') for row in value['queries']) for value in records.values()),
        '固定產品與橋接版本': all(bool(value['launch'] and value['launch']['productSha256'] == expected['polyglottooltip-1.12.2-forge-2.0.jar'] and value['launch']['bridgeSha256'] == expected['polyglot-devbridge-1.12.2-1.0.0-name-audit-production.jar']) for value in records.values()),
    }
    if any(mod.get('id') == 'emi' for value in records.values() for mod in (value['complete'] or {}).get('state', {}).get('mods', [])):
        checks['兩次 EMI 英文搜尋找到鐵錠且排除石頭'] = all(bool(value['emi-search-iron-ingot'] and value['emi-search-iron-ingot'].get('text') == 'iron ingot' and any(key.startswith('minecraft:iron_ingot:') for key in value['emi-search-iron-ingot'].get('matches', [])) and not any(key.startswith('minecraft:stone:') for key in value['emi-search-iron-ingot'].get('matches', []))) for value in records.values())
    if any(formatted_check(value) is not None for value in records.values()):
        checks['色碼樣本搜尋後仍取得相同表值'] = all(formatted_check(value) is True for value in records.values())
    if args.shared_scope:
        transition = build['shared-transition'] or {}
        before, after = transition.get('before', {}), transition.get('after', {})
        rebound = transition.get('rebound', {})
        checks['同程序 A → 新世界 B 共用相同表'] = bool(transition.get('passed') and transition.get('newWorld')
                and before.get('scope') and before.get('scope') == after.get('scope')
                and before.get('identity') == after.get('identity')
                and transition.get('tableBefore') == transition.get('tableAfter')
                and before.get('table', {}).get('builds') == after.get('table', {}).get('builds')
                and before.get('table', {}).get('switches') == after.get('table', {}).get('switches'))
        checks['舊觀察失效、新 context 可查同表'] = bool(rebound and not rebound.get('heldBindingCurrent', True)
                and not rebound.get('heldObservationCurrent', True) and not rebound.get('heldMayRebind', True)
                and not rebound.get('sameRuntimeContext', True) and rebound.get('freshObservationCurrent')
                and rebound.get('freshEnglishName') == 'Iron Ingot')
        checks['世界 B 再次啟動直接重用'] = bool(transition.get('worldB')
                and transition['worldB'] == (reuse['config'] or {}).get('worldName')
                and after.get('scope') == (reuse['complete'] or {}).get('final', {}).get('scope')
                and after.get('identity') == (reuse['complete'] or {}).get('final', {}).get('identity'))
        if pack == 'Meatball':
            checks['兩次搜尋完成後跨檔案變動區間持續可用'] = all(bool(value['shared-autosave-observations']
                and len(value['shared-autosave-observations']) >= 2
                and value['shared-autosave-observations'][-1]['revision'] > value['shared-autosave-observations'][0]['revision']
                and all(sample['table']['ready'] and sample['performance']['aliasReady'] and sample['iron']
                        for sample in value['shared-autosave-observations'])) for value in records.values())
    packs.append(dict(pack=pack, records=records, build=bt, reuse=rt, checks=checks, passed=all(checks.values())))

passed = all(pack['passed'] for pack in packs)
sections = []
for pack in packs:
    bt, rt = pack['build'], pack['reuse']
    collection = bt.get('lastCollection', {})
    unique = collection.get('eligible', 0) - collection.get('duplicates', 0)
    checks = ''.join(f'<li><span class="{"ok" if value else "pending"}">{"通過" if value else "未通過／未完成"}</span> {esc(name)}</li>' for name, value in pack['checks'].items())
    metrics = [
        ('建表耗時', seconds(collection.get('elapsedMs'))),
        ('最長單次採集操作', f'{collection["worstOperationNanos"] / 1000000:,.1f} 毫秒' if 'worstOperationNanos' in collection else '—'),
        ('列舉候選', fmt(collection.get('enumerated'))),
        ('可讀取 raw state 的候選', fmt(collection.get('eligible'))),
        ('去重後 raw states', fmt(unique) if collection else '—'),
        ('成功還原副本', fmt(collection.get('reconstructed'))),
        ('最終表格列數', fmt(bt.get('entries'))),
        ('來源列舉完整標記', '完整走訪' if bt.get('completeCatalog') else '部分收錄，詳見未收錄原因' if bt else '—'),
        ('表檔大小', f'{int(pack["records"]["build"]["table-file-evidence"]["bytes"]) / 1048576:,.1f} MiB' if pack['records']['build']['table-file-evidence'] else '—'),
        ('重用載入／建表／切換次數', f'{rt.get("loads", "—")} / {rt.get("builds", "—")} / {rt.get("switches", "—")}'),
        ('primary 不符計數（首次流程／重啟）', f'{bt.get("primaryMismatches", "—")} / {rt.get("primaryMismatches", "—")}'),
    ]
    metric_rows = ''.join(f'<tr><th scope="row">{esc(label)}</th><td>{value}</td></tr>' for label, value in metrics)
    acquired = ''.join(f'<tr><th scope="row">{esc(language)}</th><td>{fmt(count)}</td><td>{seconds(collection.get("switchMs", {}).get(language))}</td></tr>' for language, count in collection.get('acquired', {}).items())
    rejected = ''.join(f'<tr><th scope="row"><code>{esc(reason)}</code></th><td>{fmt(count)}</td></tr>' for reason, count in collection.get('rejected', {}).items())
    sources = '、'.join(f'{esc(name)} {fmt(count)}' for name, count in collection.get('sources', {}).items()) or '尚無完整紀錄'
    runs = []
    for mode, value in pack['records'].items():
        summary = value['query-summary'] or {}
        directory = value['directory']
        search_links = [link(directory + '/search-iron-ingot.json', 'JEI 搜尋')] if value['search-iron-ingot'] else []
        if value['search-observations']:
            search_links.append(link(directory + '/search-observations.json', '索引進度與重新驗證'))
        if value['formatted-after-search']:
            search_links.append(link(directory + '/formatted-after-search.json', '搜尋後色碼名稱複查'))
        if value['emi-search-iron-ingot']:
            search_links.append(link(directory + '/emi-search-iron-ingot.json', 'EMI 原生搜尋框'))
        if value['search-consumers']:
            search_links.append(link(directory + '/search-consumers.json', '搜尋前後狀態'))
        if value['shared-autosave-observations']:
            search_links.append(link(directory + '/shared-autosave-observations.json', '搜尋完成後存檔區間'))
        if value['shared-autosave-result']:
            search_links.append(link(directory + '/shared-autosave-result.json', '持續可用性獨立判定'))
        if value['external-config-event-summary']:
            search_links.append(link(directory + '/external-config-event-summary.json', '本次重啟的附加檔案事件'))
        search_samples = value['search-observations'] or []
        search_details = ''
        if value['failure']:
            search_details += f'<p class="pending">本場中止：{esc(value["failure"]["error"])}。後續未執行項目不視為通過。</p>'
        availability = value['shared-autosave-observations'] or []
        if availability:
            unavailable = sum(not (sample['table']['ready'] and sample['performance']['aliasReady'] and sample['iron']) for sample in availability)
            search_details += f'<p class="{"pending" if unavailable else "ok"}">持續可用性：{len(availability)} 次抽樣中，{unavailable} 次名稱表或英文搜尋不可用。此結果獨立於跨世界／跨啟動是否重建。</p>'
        if search_samples:
            duration = (datetime.datetime.fromisoformat(search_samples[-1]['at'].replace('Z', '+00:00'))
                        - datetime.datetime.fromisoformat(search_samples[0]['at'].replace('Z', '+00:00'))).total_seconds()
            last = search_samples[-1]['performance']
            search_details += f'<p>搜尋觀察區間 {duration:,.1f} 秒；最終索引進度 {fmt(last.get("aliasCursor"))} / {fmt(last.get("aliasSize"))}。此區間從設定查詢後開始，並非完整遊戲啟動時間。</p>'
            if not search_samples[0]['performance'].get('ready'):
                search_details += f'<p class="muted">搜尋觀測開始時備援解析器尚未就緒；結束時為{"已就緒" if last.get("ready") else "尚未就緒"}。上列樣本在搜尋觀測前查詢，none 包含當時尚無可用備援的缺項，不代表後續解析器仍不可用。</p>'
        rows = ''.join('<tr>' + ''.join(f'<td>{content}</td>' for content in [
            esc(row.get('item', '')), esc(row.get('nativeName', row.get('error', '未取得'))),
            f'{row.get("tooltipTableHits", 0)} / {row.get("repeatTooltipTableHits", 0)}',
            '<br>'.join(f'{esc(v.get("language", ""))}: {esc(v.get("name") or "未解出")} <small>({esc(v.get("source", ""))})</small>' for v in (row.get('languages') or {}).get('values', [])),
        ]) + '</tr>' for row in value['queries'])
        runs.append(f'''<div class="run"><h3>{'首次建表' if mode == 'build' else '重新啟動'}</h3>
<p>樣本 {fmt(summary.get('samples'))} · Tooltip 命中樣本 {fmt(summary.get('tooltipHits'))} · 每語表值 {fmt(summary.get('tableValues'))}</p>
<p>第一次 Tooltip 命中 {fmt(summary.get('firstTooltipHits'))}；同物品第二次命中 {fmt(summary.get('repeatTooltipHits'))}。兩次皆為自然 Tooltip 呼叫。</p>
<p>來源計數：{esc(json.dumps(summary.get('sources', {}), ensure_ascii=False))}</p>
{search_details}
<p>{link(directory + '/complete.json', '流程完成紀錄（各項驗收另列）') if value['complete'] else link(directory + '/failure.json', '中止紀錄') if value['failure'] else '尚無結束紀錄'} · {link(directory + '/restoration.json', '退出還原') if value['restoration'] else '尚未退出'} · {' · '.join(search_links)}</p>
<details><summary>檢視 {len(value['queries'])} 個查詢樣本</summary><label>篩選物品 <input class="filter" type="search" placeholder="例如 minecraft:iron_ingot"></label><div class="scroll"><table class="samples"><thead><tr><th>物品</th><th>原生名稱</th><th>首次／第二次 HIT</th><th>各語言來源</th></tr></thead><tbody>{rows}</tbody></table></div></details></div>''')
    shared_details = ''
    if args.shared_scope and pack['records']['build']['shared-transition']:
        transition = pack['records']['build']['shared-transition']
        directory = pack['records']['build']['directory']
        shared_details = f'''<p class="note">同一次啟動：{esc(transition['worldA'])} → {esc(transition['worldB'])}（新世界）；第二次啟動直接進入 B。切換前後表 generation、SHA-256 與修改時間一致，世界物件與 binding 已換新。{link(directory + '/shared-transition.json', '跨世界核對紀錄')} · {link(directory + '/shared-transition-observations.json', '切換過程')}</p>'''
    run_finished = all(value['restoration'] and (value['complete'] or value['failure']) for value in pack['records'].values())
    sections.append(f'''<section><div class="section-title"><h2>{esc(pack['pack'])}</h2><span class="{'ok' if pack['passed'] else 'pending'}">{'通過' if pack['passed'] else '未通過' if run_finished else '執行中／未通過'}</span></div>{shared_details}
<ul class="checks">{checks}</ul><div class="columns"><table><tbody>{metric_rows}</tbody></table><div><h3>按語言採集</h3><table><thead><tr><th>語言</th><th>成功保留名稱</th><th>切換呼叫耗時</th></tr></thead><tbody>{acquired}</tbody></table><p class="muted">切換耗時只量測切換呼叫；建表總耗時另包含等待、採集、還原及封存。</p></div></div>
<p>列舉來源：{sources}</p><p class="muted">每 tick 採用 2 毫秒軟預算，單一模組回呼無法強制中斷；返回後讓出。最長單次操作不包含整個語言切換。</p><p class="generation">Generation <code>{esc(bt.get('tableGeneration', '尚未產生'))}</code></p>
<details><summary>未收錄原因與計數</summary><table><tbody>{rejected}</tbody></table><p class="muted">列舉候選可重複；拒絕原因發生於不同階段，不能直接相加當成唯一物品數。每語名稱數是保留時的計數，最終表另經 primary 重複核對。</p></details><div class="columns">{''.join(runs)}</div></section>''')

test_rows = ''.join(f'<tr><th>{esc(role)}</th>' + ''.join(f'<td>{fmt(value["totals"][key])}</td>' for key in ['tests', 'failures', 'errors', 'skipped']) + '</tr>' for role, value in tests.items())
hashes = ''.join(f'<li>{link(args.candidate + "/" + row["file"], row["file"])}<code>{esc(row["sha256"])}</code></li>' for row in artifacts['files'])
document = '''<!doctype html>
<html lang="zh-Hant"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>原生完整名稱表 · 第一版測試報告</title>
<style>
:root{color-scheme:light dark;--bg:#f7f8fa;--paper:#fff;--text:#202937;--muted:#566477;--line:#dce2ea;--accent:#2255ae;--ok:#14663f;--warn:#925600}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.65 system-ui,"Microsoft JhengHei",sans-serif}main{max-width:1180px;margin:auto;padding:42px 28px 70px}header{padding-bottom:22px;border-bottom:2px solid var(--accent)}h1{font-size:32px;line-height:1.3;margin:8px 0}h2{font-size:24px;margin:0}h3{font-size:17px;margin:12px 0}p{margin:12px 0}.eyebrow,.muted,small{color:var(--muted)}section{margin:26px 0;padding:26px;background:var(--paper);border:1px solid var(--line);border-radius:12px}.section-title{display:flex;align-items:center;justify-content:space-between;gap:20px}.ok{color:var(--ok);font-weight:650}.pending{color:var(--warn);font-weight:650}.checks{display:grid;grid-template-columns:1fr 1fr;padding-left:22px;gap:5px 24px}.columns{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:26px;align-items:start}table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;vertical-align:top;padding:9px 10px;border-bottom:1px solid var(--line);overflow-wrap:anywhere}th{font-weight:600}a{color:var(--accent)}code{font:13px/1.5 ui-monospace,monospace;overflow-wrap:anywhere}details{margin:16px 0}summary{cursor:pointer;font-weight:600;padding:10px 0}label{display:block;font-size:14px}input{display:block;width:100%;padding:9px;margin:6px 0 14px;border:1px solid var(--line);border-radius:5px;background:var(--bg);color:var(--text);font:inherit}.scroll{overflow-x:auto}.samples{min-width:500px}.samples[hidden],tr[hidden]{display:none}.hashes li{margin:12px 0}.hashes code{display:block}.generation{overflow-wrap:anywhere}.note{border-left:3px solid var(--accent);padding-left:16px}@media(max-width:740px){main{padding:24px 14px}h1{font-size:26px}section{padding:18px}.columns,.checks{display:block}.checks li{margin:7px 0}.section-title{align-items:start}.run{margin-top:24px}}@media(prefers-color-scheme:dark){:root{--bg:#111720;--paper:#19212c;--text:#e1e6ed;--muted:#a7b5c8;--line:#344154;--accent:#92b6ff;--ok:#88d9aa;--warn:#efc36f}}@media print{body{background:#fff;color:#000}main{max-width:none;padding:0}section{break-inside:avoid;border-radius:0}.filter,label{display:none}a{color:inherit}}
</style><main>'''
document += f'''<header><div class="eyebrow">POLYGLOT TOOLTIP / Minecraft 1.12.2 Forge / {datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime('%Y-%m-%d %H:%M %Z')}</div><h1>原生完整名稱表：第一版測試報告</h1><p class="{'ok' if passed else 'pending'}">{'兩個整合包完成建表與跨啟動重用驗收' if passed else '驗收尚未全部完成；未通過項目不視為成功'}</p><p>優先重用已儲存的完整名稱，各語言缺項再使用既有解析器。範圍依據 {link('core-v1-scope.md', '已確認的精簡第一版')}；原始觀測與測試記錄可由各項連結查閱。</p><p class="muted">持久化的是名稱表；搜尋別名索引仍於各次啟動準備。重用不必重新採集或切換語言，但不代表消除整合包本身的啟動與搜尋準備時間。</p></header>{''.join(sections)}
<section><h2>測試與獨立審查</h2><table><thead><tr><th>測試組</th><th>總數</th><th>失敗</th><th>錯誤</th><th>略過</th></tr></thead><tbody>{test_rows}</tbody></table><p>{link(args.candidate + '/tests.json', '測試摘要與原始 XML 索引')} · {link('review-design-architecture.md', '實作前架構設計審查')} · {link('review-design-evidence.md', '實作前驗證設計審查')} · {link('review-core-lifecycle.md', '核心流程審查')} · {link('review-core-consumers.md', 'Tooltip／搜尋審查')} · {link('search-primary-evidence.md', '搜尋呼叫證據')} · {link('review-search-revalidation.md', '搜尋續建與原生色碼審查')}</p><p>產品測試已對本次候選重新執行；bridge 未變更，48 項為該相同版本先前執行的保存結果。搜尋續建的獨立控制另有 31 個案例、92 項斷言及 14 項等待條件斷言，全部通過。</p><p>隔離測試涵蓋每語 fallback、表格可用而解析器未就緒、基本失效、取消還原與跨 JVM 儲存。這些是受控驗證，不代替整合包實測。</p></section>
<section><h2>解讀與限制</h2><ul><li>本次正式配對均由繁體中文（zh_tw）啟動，採集英文（en_us）與簡體中文（zh_cn）。樣本取自先前封存的整合包樣本集；HIT 分母是實際查詢樣本，不是全部模組或全部物品。「完整」指各收錄狀態的原生完整名稱，不代表涵蓋任意物品狀態。</li><li>創造模式分頁清單超過 256 時，僅保留 SEARCH 分頁與預設物品，記錄 creative-tabs-limit 並持久化部分收錄標記；不因該上限反覆重建。真正的來源例外仍會中止，沒有被改記成完整覆蓋。</li><li>HEI 定期存檔會觸發輸入重新確認。確認期間停用別名索引；同一名稱表、世界、原生語言、備援、設定與相同物品清單才續建，真正變更仍重新整理。修正前的四次進度重設見 {link('meatball-search-diagnostic.md', '搜尋飢餓診斷')}。</li><li>已驗證 HEI 4.31.2／4.35 的名稱呼叫保留 raw 色碼，再產生原生搜尋顯示文字。未知 helper、不同 wrapper 與 stock JEI 的快取 getter 保留既有解析器路徑，不把去格式名稱誤作 raw 名稱。</li><li>正式配對測試使用 JEI／HEI 搜尋；兩個整合包均未安裝 EMI，因此不列為 EMI 實機通過。搜尋結果證明英文別名可用，沒有把它當作逐筆表命中證據。AE2 只有先前診斷場次的補充觀測，Integrated Terminals 與其他未操作介面不標示為實機通過。</li><li>新還原的測試物品可能在首次 Tooltip 初始化能力，造成當次 raw state 變化而拒絕表觀察；首次與再次命中分開列示，不把再次命中改記成首次。詳見 {link('first-tooltip-diagnostic.md', '首次 Tooltip 診斷')}。</li><li>使用 Forge selective resource reload 暫時切換語言，完成或取消後還原使用者設定；沒有移植 Language Reload。取消與故障路徑的受控測試不等同最終候選版的完整實機故障矩陣。</li><li>未知 capability、未列舉 NBT、事件改寫標題與非物品 ingredient 不承諾由本表涵蓋；依現有路徑處理。</li><li>完整多人矩陣、六種初始語言冷啟動格與完整故障矩陣已依第一版範圍延後。</li></ul><p class="note">先前主動取消或失敗的場次均保留原始紀錄，不列入本次成功配對。E2E core-build-4 因測試把全域 mismatch 計數誤當成單筆搜尋計數而中止，AE2 測試設定亦觸發了輸入失效。Meatball core-build-2 因分頁上限誤分類為來源不可用而取消；core-build-3 雖完成建表，仍因搜尋索引反覆重設而未通過。core-diagnostic-1 正常載入同表，但只用來定位搜尋問題，不列入成功配對。本次候選修正上述原因。這些場次沒有完成紀錄，只保留診斷證據。</p></section>
<section><h2>固定候選版本</h2><p>{link(args.candidate + '/artifacts.json', 'JAR 檔案與 SHA-256')}</p><ul class="hashes">{hashes}</ul><p>資料儲存在遊戲目錄的 <code>polyglottooltip/name-tables/contexts/&lt;context hash&gt;/</code>。有效輸入、覆蓋語言或世界／玩家 context 改變時，系統重新判斷可用表格與必要重建。</p></section></main>
<script>document.querySelectorAll('.filter').forEach(input=>input.addEventListener('input',()=>{{const query=input.value.toLowerCase();input.closest('details').querySelectorAll('tbody tr').forEach(row=>{{row.hidden=!row.textContent.toLowerCase().includes(query)}})}}));</script></html>'''
output_name = 'shared-scope-report' if args.shared_scope else 'core-v1-report'
if args.shared_scope:
    document = document.replace('第一版測試報告', '整合包共用測試報告')
    if all(value['restoration'] and (value['complete'] or value['failure']) for pack in packs for value in pack['records'].values()):
        document = document.replace('驗收尚未全部完成；未通過項目不視為成功', '本輪測試已結束；仍有未通過與因中止未執行的項目')
    document = document.replace('兩個整合包完成建表與跨啟動重用驗收', '兩個整合包完成跨世界與重啟重用驗收')
    document = document.replace('範圍依據 ', '同一安裝內跨世界／伺服器／角色／維度共用持久化名稱表，每次仍核對當前 raw item 與原生完整 primary 名稱。範圍依據 ')
    shared_restart_ready = all(value['checks'].get('世界 B 再次啟動直接重用') for value in packs)
    availability_summary = ('兩包均已證實跨世界與重啟可重用同表；' if shared_restart_ready else 'E2E 已通過共用與重啟；Meatball 已通過 A → 新 B，共用表重啟驗證另列；')
    document = document.replace('<p class="muted">持久化的是名稱表；',
        f'<p class="pending">{availability_summary}Meatball 的檔案重驗期間出現搜尋中斷，本候選未全部驗收通過。下列新引擎與 UI 是設計成果，尚未實作。</p><p class="muted">持久化的是名稱表；', 1)
    document = document.replace('產品測試已對本次候選重新執行；bridge 未變更，48 項為該相同版本先前執行的保存結果。',
        '產品與 bridge 測試均已對本次候選重新執行。')
    document = document.replace('</header>', f'''</header><section><h2>1.7.10 調查與新的自動建表方向</h2><p>1.7.10 預載目標字典，在主執行緒的每批工作內暫時交換全域翻譯表，批次結束即還原；因此不必反覆 resource reload。它還混用解析器／特例，且主要 key 不含完整 NBT，不能直接把較低工作量的速度當成目前完整 raw-state 原生採集的等價證據。</p><p>建議先驗證 1.12.2 的局部語言查詢視圖：保留真正原生 getter，僅在每次已核准的採集呼叫期間導向目標語言，scope 外與其他執行緒仍看玩家原語言。事前准入須涵蓋列舉、建構、capability、primary/repeat 與 getter 全部額外回呼。未知或有未隔離快取的路徑使用已有表／解析器回退，不能在遊玩中偷偷觸發全域切換。</p><p>使用者指定的左上角 toast：顯示 Polyglot、目前階段／真實百分比及實際可用的停止按鍵；清單未定時顯示處理數，停止先等待目前回呼及必要還原，保留既有表與手動重建。</p><p class="note">此為調查及已覆核的原型設計，尚未實作進本候選。需先證明名稱等價、整條流程隔離、實際覆蓋及首次準備／遊玩 frame time，才能採為新預設；尚不能承諾所有模組無感或固定秒數。</p><p>{link('legacy1710-language-study.md', '1.7.10 實作與來源證據')} · {link('seamless-112-alternatives.md', '1.12.2 方案比較')} · {link('scoped-native-followup-design.md', '新版原型設計')} · {link('name-progress-toast-design.md', '左上進度及停止契約')} · {link('review-scoped-native-design-architecture.md', '架構覆核')} · {link('review-scoped-native-design-evidence.md', '來源及驗收覆核')}</p></section>''', 1)
    document = document.replace('<section><h2>測試與獨立審查</h2>', f'''<section><h2>共用政策與獨立覆核</h2><p>{link('shared-scope-design.md', '共用範圍設計')} · {link('review-shared-scope-architecture.md', '架構與 58 項受控斷言')} · {link('review-shared-scope-evidence.md', 'HEI 來源與具體實作覆核')} · {link('core-v1-report.html', '先前分區版本基線')}</p><p>新 scope 與 input-policy 版本首次建立一次共用表，舊分區表不改標為新表。已核對兩版 HEI 的八個 UI 欄位；只有精確路徑、完整合法語法、0.1.0 版本與合法 world&lt;int&gt; 類別才排除整檔。未知內容仍保留 identity，ItemFavorites 保持 raw。原始檔案監測仍會使查詢暫停並重新確認。</p><p>世界／玩家／UUID／維度／伺服器物件与位址的 currentness、stale async 結果、quarantine 與 deferred/manual reset 使用 58 項受控斷言；本次實機範圍是兩包各 A → 新 B → 重啟 B。未實測多人、換帳號、維度往返或 B → A。</p><p>原生 primary 不一致會對該 context 的列回退，不能檢出 primary 相同而只有目標語言受隱藏環境影響的情況。這是使用者選擇較少等待的共用政策之限制。</p></section><section><h2>測試與獨立審查</h2>''')
    document = document.replace('有效輸入、覆蓋語言或世界／玩家 context 改變時，系統重新判斷可用表格與必要重建。',
        'scope 為同一安裝的位置與政策版本；世界／玩家／維度切換只使 runtime context 換新。有效名稱輸入或語言覆蓋不足才重新判斷必要採集，查詢仍逐筆核對原生名稱。')
    document = document.replace('本次候選修正上述原因。', '先前分區版本已修正上述原因；本次候選進一步實作整合包共用政策。')
    document = document.replace('<section><h2>固定候選版本</h2>', f'''<section><h2>仍存在的日常啟動限制</h2><p>同一候選的 Meatball core-shared-build-1 在建表前偵測到 <code>config/AppliedEnergistics2/VersionChecker.cfg</code> 變更，進入 restart-required，該場未通過。遊戲正常退出、設定已還原；後續配對使用相同候選重新啟動，沒有透過修改該設定或放寬產品規則取得通過。</p><p>成功的跨世界配對不能抹去這個啟動限制。版本檢查快取等日常寫入仍需來源證據與精確 input policy，屬於後續改善開箱即用的工作。{link('run-meatball-core-shared-build-1/failure.json', '未通過原始紀錄')} · {link('run-meatball-core-shared-build-1/restoration.json', '退出與還原')}</p></section><section><h2>固定候選版本</h2>''')
    document = document.replace('<section><h2>解讀與限制</h2>', '''<section><h2>首次建表為什麼耗時</h2><p>GTNH NEI 的 <a href="https://github.com/GTNewHorizons/NotEnoughItems/blob/master/src/main/java/codechicken/nei/util/ItemUntranslator.java">ItemUntranslator 原始碼</a>同樣會呼叫物品的完整 display name；產生流程以連續迴圈採集當前語言，也可直接讀取已有的 untranslator.cfg。使用者觀察的「1 秒」尚未核對確切版本與計時範圍，不能直接當作同工作量基準。</p><p>本版繁中／英文／簡中三輪採集後，又複查繁中一次；每輪重建物品副本並核對 raw state。列舉、採集與封存每 tick 各受最多 256 項、約 2 毫秒软預算限制。在正常 20 tick／秒下，21.4 萬個狀態的四輪採集即使單項成本極低，僅項數限制也至少需要約 168 秒；尚未包含列舉、封存與語言切換。</p><p>這是本版排程與驗證方式的成本，並非完整名稱表必然需要數分鐘。此次改動解決跨世界重複採集，沒有調整首次採集預算或省略狀態檢查。搜尋索引的準備時間也另外列示，不能混算成讀取持久化名稱表的時間。</p></section><section><h2>解讀與限制</h2>''')
    document = document.replace('<section><h2>仍存在的日常啟動限制</h2>', f'''<section><h2>本輪未通過：重新驗證期間的搜尋中斷</h2><p>Meatball core-shared-build-2 已建立 214,362 列，A → 新 B 仍使用同 generation、檔案內容與修改時間；但搜尋完成後的 65 秒觀察中，檔案變動令狀態進入 checking-inputs，英文鐵錠暫時消失。第一段由 04:38:44 至 04:38:57 UTC 的抽樣觀測可見約 13 秒不可用；第二段在測試結束時仍在重驗，因此原場次中止。</p><p>磁碟共用成功與持續搜尋可用是不同驗收。本輪保留原失敗，另以相同候選、既有世界 B 驗證重啟；沒有重跑建表來掩蓋失敗。後續測試將可用性失敗獨立記錄，繼續取得格式、重啟與退出還原證據，並以每次抽樣都可用判定，沒有只挑最後一筆通過。</p><p>{link('run-meatball-core-shared-build-2/failure.json', '原始中止紀錄')} · {link('run-meatball-core-shared-build-2/shared-autosave-observations.json', '完整可用性觀察')} · {link('run-meatball-core-shared-build-2/restoration.json', '正常退出及設定還原')}</p><p class="pending">此問題尚未修正，本候選不能標為全部驗收通過，也不能稱建表與查詢已無感。</p></section><section><h2>仍存在的日常啟動限制</h2>''')
    document = document.replace('此問題尚未修正，本候選不能標為全部驗收通過，也不能稱建表與查詢已無感。</p>',
        f'''此問題尚未修正，本候選不能標為全部驗收通過，也不能稱建表與查詢已無感。</p><p>補充原型方向是依完整事件批次重新確認變動檔案，保留已驗證 baseline；未知／overflow／競態仍完整掃描並維持失效。新 snapshot 需要新的 current binding，舊觀察不復活；相同有效 identity 才續用相容的搜尋工作。這是縮短重驗成本的假設，尚不承諾零空窗。{link('input-revalidation-followup-design.md', '輸入重驗補充設計')} · {link('review-input-revalidation-architecture.md', '架構覆核')} · {link('review-input-revalidation-evidence.md', '來源覆核')}</p>''')
    document = document.replace('<ul><li>本次正式配對', '<ul><li>primary 不符計數是整個執行程序累計的列隔離數，包含背景搜尋；不等於樣本錯誤數，也不能只由此計數判定哪些模組或世界狀態造成差異。隔離列逐語回退，並未觸發全表重建。</li><li>本次正式配對')
(report / (output_name + '.html')).write_text(document, encoding='utf-8')
summary = dict(generatedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(), candidate=args.candidate, passed=passed,
               packs=[dict(pack=value['pack'], passed=value['passed'], checks=value['checks'], runs={mode: data['directory'] for mode, data in value['records'].items()}) for value in packs])
if args.shared_scope:
    summary['knownLimitations'] = [dict(run='run-meatball-core-shared-build-1', status='failed-before-acquisition',
        reason='restart-required: config/AppliedEnergistics2/VersionChecker.cfg', fixed=False),
        dict(run='run-meatball-core-shared-build-2', status='failed-after-shared-transition',
             reason='input revalidation temporarily disables native table and English search', fixed=False)]
    summary['scopedNativeProposal'] = dict(status='reviewed-prototype-design-only', implementedInCandidate=False,
        design='scoped-native-followup-design.md', toast='name-progress-toast-design.md')
(report / (output_name + '.json')).write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary, ensure_ascii=True))
