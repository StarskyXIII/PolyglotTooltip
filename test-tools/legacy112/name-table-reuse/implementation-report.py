from pathlib import Path
import datetime
import html
import json
import re
import shutil

root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-reuse-2026-10-14'
target = report / 'shared-scope-report.html'
historical = report / 'shared-scope-report-candidate1.html'
if not historical.exists():
    shutil.copy2(target, historical)
old = historical.read_text(encoding='utf-8')
style = re.search(r'<style>(.*?)</style>', old, re.S).group(1)
read = lambda name: json.loads((report / name).read_text(encoding='utf-8-sig')) if (report / name).is_file() else None
esc = lambda value: html.escape(str(value))
link = lambda path, label: f'<a href="{esc(path)}">{esc(label)}</a>'
stamp = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime('%Y-%m-%d %H:%M UTC+08:00')
pair = read('shared-pair-meatball-core-incremental-build-2.json')
assert pair and pair['passed']
candidate = read('candidate-shared-4/artifacts.json')
tests = read('candidate-shared-4/tests.json')
tests = {key: value['totals'] for key, value in tests.items()}
availability = pair['availability']
native = pair['transition']['before']['table']['lastCollection']
panels = []
for pack, attempt in [('E2E', 'core-scoped-1'), ('Meatball', 'core-scoped-1')]:
    folder = f'run-{pack.lower()}-{attempt}'
    if not (report / folder).exists():
        panels.append(f'<tr><th>{pack}</th><td>尚未啟動此配對測試</td><td>—</td></tr>')
        continue
    scope = read(folder + '/scope-result.json')
    failure = read(folder + '/failure.json')
    progress = read(folder + '/scope-progress.json')
    state = '驗證進行中；不列為通過'
    detail = link(folder + '/process.json', '執行歸屬')
    if scope:
        result = scope['final']
        state = f"{result['state']}；完成 {result.get('completedRows', 0):,} / {result['cohort']:,} raw keys，差異 {result['mismatchCount']}"
        detail = link(folder + '/scope-result.json', '有限配對結果') + ' · ' + link(folder + '/scope-performance-windows.json', '幀間隔原始量測')
    if failure:
        state += '；控制器未通過'
        detail += ' · ' + link(folder + '/failure.json', '保留失敗原因')
    panels.append(f'<tr><th>{pack}</th><td>{esc(state)}</td><td>{detail}</td></tr>')
reviews = [
 ('review-incremental-input-implementation-architecture.md', '增量重驗架構'),
 ('review-incremental-input-implementation-evidence.md', '增量重驗證據'),
 ('review-startup-watch-discontinuity-evidence.md', '啟動目錄監看'),
 ('review-progress-overlay-implementation-evidence.md', '進度與停止 UI'),
 ('review-shared-work-budget-implementation.md', '共享工作額度'),
 ('review-scoped-lookup-implementation-architecture.md', '局部字典原語'),
 ('review-scoped-stage3-validator-design-architecture.md', '同源配對設計'),
 ('review-scoped-stage3-validator-implementation-architecture.md', '同源配對實作'),
 ('review-ae2-e2e-version-clock-evidence.md', 'AE2 時間欄位政策'),
]
review_links = ' · '.join(link(file, label) for file, label in reviews if (report / file).is_file())
artifact_links = ''.join(f'<li>{link("candidate-shared-4/" + entry["file"], entry["file"])}<code>{entry["sha256"]}</code></li>' for entry in candidate['files'])
body = f'''<header><div class="eyebrow">POLYGLOT TOOLTIP / Minecraft 1.12.2 / {stamp}</div>
<h1>名稱表改善：實作與實機驗證</h1><p class="pending">工作進行中。搜尋重驗已有新實測通過；背景建表尚未完成正式預設驗收。</p>
<p>本頁將已完成的輸入重驗修正、已實作的進度介面與仍在驗證的背景名稱取得分開呈現。原失敗紀錄完整保留。</p></header>
<section><h2>已通過：大型整合包的重验與同表沿用</h2><p class="ok">Meatball candidate-shared-3：兩次流程合計 {sum(x['samples'] for x in availability)} 個自動存檔期間觀測點，表與英文搜尋均可用，未觀測到空窗。</p>
<p>同程序 A → 新世界 B、再啟動直接進入 B，持久化表的 generation、SHA-256 與修改時間相同。第二次啟動載入一次，建表零次、語言切換零次。舊世界觀察失效，新世界能以新的 binding 查詢同表。</p>
<p>{link('shared-pair-meatball-core-incremental-build-2.json', '完整配對結果')} · {link('run-meatball-core-incremental-build-2/shared-autosave-observations.json', '首次 31 個觀測')} · {link('run-meatball-core-incremental-reuse-2/shared-autosave-observations.json', '重啟 31 個觀測')} · {link('candidate-shared-3/artifacts.json', '該次固定版本')}</p>
<p>增量重驗會沿用已驗證內容，只重讀需要確認的檔案；未知事件、監看中斷與競態仍要求完整檢查。正常啟動首次仍完整讀取約 2.11 GB。一次增量 snapshot 記錄 231 ms、重讀 0 bytes；此時間不含最初檔案盤點，不能當作整段重驗牆鐘時間。</p>
<p class="note">62 個觀測點均可用是有限抽樣結果，不代表已證明每一幀永遠零空窗。這次首次表仍由既有全域語言切換流程建立：{native['elapsedMs']/1000:.1f} 秒、214,425 distinct raw keys、214,362 表列。它是背景引擎的比較基準，不是新引擎加速成績。</p></section>
<section><h2>已實作、分項驗證中的內容</h2><table><thead><tr><th>項目</th><th>目前證據</th><th>仍需完成</th></tr></thead><tbody>
<tr><th>左上進度與停止鍵</th><td>已實作真實階段／百分比、未知分母處理數、可重新綁定的鍵鼠停止、衝突提示與終止結果；Subagent 來源覆核通過</td><td>實機顯示、GUI scale、停止及恢復操作</td></tr>
<tr><th>共享每 tick 工作額度</th><td>語言準備、列舉、採集、alias 與保留搜尋更新共用 2 ms／256 工作單位的軟額度；首項前也檢查</td><td>實際幀間隔與吞吐。單次 callback 不能搶占；必要語言還原與人工 export 是明列例外</td></tr>
<tr><th>局部語言取得</th><td>lexical scope 已實作；只在目前呼叫中讀目標字典，其他執行緒與 scope 外仍讀原字典；strict base Item profile 採集前准入</td><td>完整 cold loader 的外部來源與模組副作用隔離，以及正式建表／持久化整合</td></tr>
<tr><th>同源原生名稱比對</th><td>已實作獨立 witness、相同完整原生 reference、所有 retained raw key 分母、unknown／未完成／差異計數及 primary repeat；不發布名稱</td><td>實機配對與實際覆蓋；此測試本身不能證明冷啟動字典等價</td></tr></tbody></table><p>{review_links}</p></section>
<section><h2>背景取得實測</h2><table><thead><tr><th>整合包</th><th>状态</th><th>證據</th></tr></thead><tbody>{''.join(panels)}</tbody></table>
<p>比較同一世界、相同鏡頭與每秒相同診斷輪詢的基準與工作期間。採用 RenderTick START 到下一次 START 的實際幀間隔，另記 tick／render 工作時間；保留完整 raw samples 與量測缺口。不以 render 耗時冒稱 FPS，也不以零採用、零完成或只比 primary 作通過。</p></section>
<section><h2>保留的啟動失敗與新修正</h2><p>candidate-shared-3 的 E2E core-incremental-build-6 因 AE2 VersionChecker.cfg 變動，於建表前安全停止。沒有當時的 AE2 檔案前後副本，不能斷言只有 lastCheck 變動；這一場仍是失敗。</p>
<p>已獨立核對 E2E AE2 的固定 JAR 與全部 class references：lastCheck 僅用於版本查詢排程。candidate-shared-4 政策 v2.4 只在該精確 provider／路徑下忽略合法的 cache/S:lastCheck 時間值；其他欄位、未知內容及失配 provider 仍保留，畸形值回到原始內容核對。來源与測試通過，實機驗收另列。</p>
<p>{link('run-e2e-core-incremental-build-6/failure.json', '原始失敗')} · {link('run-e2e-core-incremental-build-6/restoration.json', '正常退出與還原')} · {link('review-ae2-e2e-version-clock-evidence.md', '來源與實作覆核')}</p></section>
<section><h2>目前固定測試版</h2><p>candidate-shared-4：產品 {tests['product']['tests']} tests，0 failures/errors，{tests['product']['skipped']} skipped；bridge {tests['bridge']['tests']} tests 全通過。這些不是 {tests['product']['tests']} 個 GUI 或整合包實測。</p>
<p>{link('candidate-shared-4/artifacts.json', '版本紀錄')} · {link('candidate-shared-4/tests.json', '測試統計')}</p><ul class="hashes">{artifact_links}</ul></section>
<section><h2>先前候選與設計證據</h2><p>{link('shared-scope-report-candidate1.html', 'candidate-shared-1 完整歷史報告（含原搜尋空窗）')}。歷史報告中的「尚未修正／尚未實作」只描述當時候選；本頁分項狀態為目前進度。</p></section>'''
target.write_text('<!doctype html><html lang="zh-Hant"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>名稱表改善 · 實作驗證</title><style>' + style + '</style><main>' + body + '</main></html>', encoding='utf-8')
print(target)
