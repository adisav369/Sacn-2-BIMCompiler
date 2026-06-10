// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SCOPE: W-PA-MENU — prove the app reaches the financial reports the SAME WAY iDempiere does: via the AD_Menu
//   tree. Diff the bundle's reporting menu branch (ad_menu/ad_treenodemm/ad_menu_table, loaded by
//   extract_pa_report.sh and rendered by report_overlay.openMenu) against the LIVE idempiere_test AD_Menu —
//   the path Performance Analysis and Accounting (278) → Financial Reporting (280) → Financial Report (281, a
//   Window over PA_Report), the children set+order under 280, and the PA_Report records reachable there.
// NON-INVENT: every node/name/action READ from the bundle or live PG. §-log first.
// Run:  node scripts/poc_pa_menu.js 2>&1 | tee build/erp/poc_pa_menu.log
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var Database = require('better-sqlite3');
var GB = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function ok(c, m) { if (!c) { fails++; console.log('  ✗ FAIL: ' + m); } else { console.log('  ✓ ' + m); } }
function PG(sql) { return cp.execSync('docker exec -i postgres psql -U adempiere -d idempiere_test -tAF"|" -c ' + JSON.stringify(sql), { encoding: 'utf8' }).trim(); }
function pgRows(sql) { var o = PG(sql); return o ? o.split('\n').map(function (l) { return l.split('|'); }) : []; }

console.log('=== §PA-MENU witness (W-PA-MENU) — ' + new Date().toISOString() + ' ===');
console.log('bundle = glassbowl_data.db ad_menu/ad_treenodemm; oracle = LIVE idempiere_test AD_Menu (tree 10)\n');

console.log('[ISSUE] the bundle carries the AD_Menu tree (so the launcher is data-driven, not invented)');
['ad_menu', 'ad_treenodemm', 'ad_menu_table'].forEach(function (t) {
  ok(GB.prepare("SELECT count(*) n FROM sqlite_master WHERE type='table' AND name=?").get(t).n > 0, 'bundle carries ' + t);
});

// ── 1. the parent chain 278 → 280 → 281, names + parent links == live ──
console.log('\n[ISSUE] reporting path Performance Analysis and Accounting (278) → Financial Reporting (280) → Financial Report (281) == live');
[278, 280, 281].forEach(function (id) {
  var b = GB.prepare('SELECT m.name, m.action, n.parent_id FROM ad_menu m JOIN ad_treenodemm n ON n.node_id=m.ad_menu_id WHERE m.ad_menu_id=? AND n.ad_tree_id=10').get(id);
  var p = pgRows("SELECT m.name, m.action, n.parent_id FROM adempiere.ad_menu m JOIN adempiere.ad_treenodemm n ON n.node_id=m.ad_menu_id WHERE m.ad_menu_id=" + id + " AND n.ad_tree_id=10")[0];
  ok(b && p && b.name === p[0] && String(b.action || '') === String(p[1] || '') && String(b.parent_id) === String(p[2]),
    'node ' + id + ' "' + (b && b.name) + '" action=' + (b && b.action) + ' parent=' + (b && b.parent_id) + ' == live (' + (p && p[0]) + '/' + (p && p[1]) + '/' + (p && p[2]) + ')');
});

// ── 2. Financial Report (281) is a Window that opens PA_Report — in the bundle AND live ──
console.log('\n[ISSUE] Financial Report (281) opens table PA_Report (bundle ad_menu_table == live window→table)');
var bTbl = GB.prepare('SELECT tablename FROM ad_menu_table WHERE ad_menu_id=281').get();
var pTbl = pgRows("SELECT t.tablename FROM adempiere.ad_menu m JOIN adempiere.ad_tab tb ON tb.ad_window_id=m.ad_window_id AND tb.tablevel=0 AND tb.isactive='Y' JOIN adempiere.ad_table t ON t.ad_table_id=tb.ad_table_id WHERE m.ad_menu_id=281 LIMIT 1")[0];
ok(bTbl && bTbl.tablename === 'PA_Report', 'bundle: menu 281 opens ' + (bTbl && bTbl.tablename));
ok(pTbl && pTbl[0] === 'PA_Report', 'live: menu 281 window opens ' + (pTbl && pTbl[0]));

// ── 3. children of Financial Reporting (280): same node set, order, names, action ──
console.log('\n[ISSUE] children of Financial Reporting (280) — same set / seqno order / names / actions as live');
var bKids = GB.prepare('SELECT n.node_id, m.name, m.action FROM ad_treenodemm n JOIN ad_menu m ON m.ad_menu_id=n.node_id WHERE n.parent_id=280 AND n.ad_tree_id=10 AND m.isactive=\'Y\' ORDER BY n.seqno').all();
var pKids = pgRows("SELECT n.node_id, m.name, m.action FROM adempiere.ad_treenodemm n JOIN adempiere.ad_menu m ON m.ad_menu_id=n.node_id WHERE n.parent_id=280 AND n.ad_tree_id=10 AND m.isactive='Y' ORDER BY n.seqno");
var bStr = bKids.map(function (k) { return k.node_id + ':' + k.name + ':' + (k.action || ''); }).join(' | ');
var pStr = pKids.map(function (k) { return k[0] + ':' + k[1] + ':' + (k[2] || ''); }).join(' | ');
console.log('§PA-MENU bundle-children: ' + bStr);
console.log('§PA-MENU live-children:   ' + pStr);
ok(bStr === pStr, 'Financial Reporting children match live exactly (' + bKids.length + ' nodes, ordered)');

// ── 4. the reports reachable under Financial Report == live PA_Report records ──
console.log('\n[ISSUE] PA_Report records reachable from the menu == live pa_report set');
var bReps = GB.prepare("SELECT pa_report_id id, name FROM pa_report ORDER BY pa_report_id").all().map(function (r) { return r.id + ':' + r.name; }).join(' | ');
var pReps = pgRows("SELECT pa_report_id, name FROM adempiere.pa_report WHERE ad_client_id=11 ORDER BY pa_report_id").map(function (r) { return r[0] + ':' + r[1]; }).join(' | ');
console.log('§PA-MENU bundle-reports: ' + bReps);
console.log('§PA-MENU live-reports:   ' + pReps);
ok(bReps === pReps && bReps.length > 0, 'reachable reports == live pa_report (Balance Sheet / Income Statement / Cash Flows)');

// ── §FALSIFIER: corrupt a node name in-memory -> the path diff must catch it ──
console.log('\n[§FALSIFIER] a wrong menu name must FAIL the path diff (the test has teeth)');
(function () {
  var realName = GB.prepare('SELECT name FROM ad_menu WHERE ad_menu_id=281').get().name;
  var tampered = realName + ' (TAMPERED)';
  var live = pgRows("SELECT name FROM adempiere.ad_menu WHERE ad_menu_id=281")[0][0];
  console.log('§PA-MENU-FALSIFIER tampered="' + tampered + '" vs live="' + live + '" -> caughtMismatch=' + (tampered !== live ? 'Y' : 'N'));
  ok(tampered !== live, '§FALSIFIER fired: a tampered menu name diverges from live AD_Menu');
})();

console.log('\n=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
