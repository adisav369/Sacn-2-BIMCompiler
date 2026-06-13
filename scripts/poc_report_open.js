// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SCOPE: R-T1 (prompts/REPORTING_UI_DEFAULT_FALLTHROUGH.md) — prove a user can OPEN a real report from the
//   Glassbowl surface with a GESTURE (no JS console). The report engine is proven; this witness proves
//   REACHABILITY: the affordance is REGISTERED in the ring, it is PURE-READ always-enabled (no owner/docstatus
//   gate), it dispatches the existing key-addressed intent bus (overlay:report / overlay:statement), the report
//   overlay LISTENS on that bus, and the dispatch path RESOLVES to a fold that renders REAL cells from the bundle.
//     ISSUE-1  the ring registers a 'report' verb (▤) that is always-enabled and dispatches overlay:report.
//     ISSUE-2  report_overlay.js subscribes to overlay:report (Receipt) AND overlay:statement (financial menu).
//     ISSUE-3  the resolved fold renders REAL cells: a Receipt (foldReceipt for the lit doc) has line cells > 0,
//              and a Statement (foldStatement, default scope) has folded cells > 0 — the exact code show()/statement() run.
// NON-INVENT: the affordance/listener facts are READ from the real source files; the cell counts are folds over real
//   glassbowl_data.db rows. No Date.now/Math.random. §-log first — READ build/erp/poc_report_open.log before concluding.
// Run:  bash build/erp/run_witness.sh scripts/poc_report_open.js
'use strict';
var path = require('path'), fs = require('fs');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var Database = require('better-sqlite3');
var GB = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function has(t, c) { return GB.prepare('PRAGMA table_info(' + t + ')').all().some(function (r) { return String(r.name).toLowerCase() === c; }); }

console.log('=== §REPORT-OPEN witness (R-T1) — ' + new Date().toISOString() + ' ===');
console.log('surface = Glassbowl; affordance = the ring ▤ Report fab + the ▤ Reports menu pill; bus = overlay:report/overlay:statement\n');

var crudSrc = fs.readFileSync(path.join(ERP, 'crud_overlay.js'), 'utf8');
var rptSrc = fs.readFileSync(path.join(ERP, 'report_overlay.js'), 'utf8');

// ════════════════════════════════════════════════════════════════════════
// ISSUE-1: the ring REGISTERS a 'report' verb, always-enabled (pure read), dispatching overlay:report.
// ════════════════════════════════════════════════════════════════════════
console.log('[ISSUE-1] the ring registers a ▤ Report fab — always-enabled (pure read) — dispatching overlay:report');
var hasReportIcon = /verb:\s*'report'[\s\S]{0,80}glyph:\s*'▤'/.test(crudSrc);
var alwaysOn = /ic\.verb\s*===\s*'view'\s*\|\|\s*ic\.verb\s*===\s*'report'/.test(crudSrc);     // read verbs are free in openRing
var dispatchesReport = /verb\s*===\s*'report'[\s\S]{0,200}CustomEvent\('overlay:report'/.test(crudSrc);
ok(hasReportIcon, "ring ICONS include a 'report' verb with the ▤ glyph (the affordance is rendered)");
ok(alwaysOn, "report fab is always-enabled in openRing (pure read — no owner-gate / docstatus precondition)");
ok(dispatchesReport, "tapping report dispatches CustomEvent('overlay:report', {key}) on the intent bus");
console.log('');

// ════════════════════════════════════════════════════════════════════════
// ISSUE-2: report_overlay.js SUBSCRIBES to the bus (Receipt + Statement) and a menu pill reaches statements.
// ════════════════════════════════════════════════════════════════════════
console.log('[ISSUE-2] report_overlay subscribes to overlay:report (Receipt) + overlay:statement (financial menu)');
var listensReport = /addEventListener\('overlay:report'/.test(rptSrc);
var listensStatement = /addEventListener\('overlay:statement'/.test(rptSrc);
var menuPill = /reportMenuPill[\s\S]{0,400}addEventListener\('click'[\s\S]{0,40}openMenu\(\)/.test(rptSrc);
ok(listensReport, "report_overlay listens on overlay:report -> show(key) (the ring hand-off resolves)");
ok(listensStatement, "report_overlay listens on overlay:statement -> statement(reportId) (menu/launcher hand-off)");
ok(menuPill, "a ▤ Reports menu pill opens the iDempiere Financial Reporting menu (statements reachable without a lit doc)");
console.log('');

// ════════════════════════════════════════════════════════════════════════
// ISSUE-3: the dispatch path RESOLVES to a fold that renders REAL cells (the exact code show()/statement() run).
//   Receipt: foldReceipt for the lit document (c_order) -> line cells > 0.
//   Statement: foldStatement on the default scope (Balance Sheet 100) -> folded cells > 0.
// ════════════════════════════════════════════════════════════════════════
console.log('[ISSUE-3] the resolved fold renders REAL cells (Receipt line cells > 0; Statement folded cells > 0)');

// — Receipt path (overlay:report -> show('c_order')) —
var map = CORE.REPORT_MAP.c_order;
var oid = (GB.prepare('SELECT ' + map.pk + ' id FROM ' + map.key + ' ORDER BY ' + map.pk + ' LIMIT 1').get() || {}).id;
var header = GB.prepare('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=?').get(oid);
var lines = GB.prepare('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=? ORDER BY line').all(oid);
// name resolution is hasCol-guarded — EXACTLY as report_overlay.nameOf (the slim bundle's c_bpartner has no name col).
var bp = (header.c_bpartner_id != null && has('c_bpartner', 'name')) ? GB.prepare('SELECT name FROM c_bpartner WHERE c_bpartner_id=?').get(header.c_bpartner_id) : null;
var prodHasName = has('m_product', 'name');
var names = { partner: bp ? bp.name : null, products: {} };
lines.forEach(function (r) { var pid = r[map.fkProduct]; if (pid != null && names.products[pid] === undefined) { var p = prodHasName ? GB.prepare('SELECT name FROM m_product WHERE m_product_id=?').get(pid) : null; names.products[pid] = p ? p.name : null; } });
var rec = CORE.foldReceipt(map, header, lines, names);
var recCells = rec.lines.length;
console.log('§REPORT-OPEN surface=gw via=ring kind=receipt rendered=' + (recCells > 0 ? 'Y' : 'N') + ' cells=' + recCells +
  ' doc=c_order#' + rec.id + ' total=' + rec.total);
ok(recCells > 0, 'Receipt fold renders real line cells (' + recCells + ' > 0) — the ▤ ring verb opens a populated receipt');

// — Statement path (overlay:statement -> statement(100), default scope via the SAME CORE.resolveScope) —
var TREE = 101, CAL = 102;
var signCol = has('c_elementvalue', 'accountsign');
var EV = {};
GB.prepare('SELECT c_elementvalue_id id, accounttype' + (signCol ? ', accountsign' : '') + ', issummary FROM c_elementvalue')
  .all().forEach(function (r) { EV[r.id] = { accounttype: r.accounttype, accountsign: signCol ? r.accountsign : null, issummary: r.issummary }; });
var kids = {};
GB.prepare('SELECT node_id, parent_id FROM ad_treenode WHERE ad_tree_id=?').all(TREE).forEach(function (n) { (kids[n.parent_id] = kids[n.parent_id] || []).push(n.node_id); });
function leaves(id) { var e = EV[id]; if (!e || e.issummary !== 'Y') return [id]; var out = []; (function rec(x) { (kids[x] || []).forEach(function (c) { if (EV[c] && EV[c].issummary === 'Y') rec(c); else out.push(c); }); })(id); return out; }
var accounts = {}; Object.keys(EV).forEach(function (id) { accounts[id] = { accounttype: EV[id].accounttype, accountsign: EV[id].accountsign }; });
var periods = GB.prepare("SELECT p.c_period_id id, p.name, p.startdate sd, p.enddate ed, p.c_year_id yr FROM c_period p JOIN c_year y ON p.c_year_id=y.c_year_id WHERE y.c_calendar_id=? AND p.isactive='Y' AND p.periodtype='S' ORDER BY p.startdate").all(CAL);
var factRows = GB.prepare('SELECT account_id, amtacctdr, amtacctcr, qty, c_period_id, c_acctschema_id, postingtype FROM fact_acct').all();
var SRID = 100;
var rep = GB.prepare('SELECT pa_report_id, name, pa_reportlineset_id, c_acctschema_id FROM pa_report WHERE pa_report_id=?').get(SRID);
var slines = GB.prepare("SELECT pa_reportline_id, seqno, name, linetype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype FROM pa_reportline WHERE pa_reportlineset_id=? AND isactive='Y' ORDER BY seqno").all(rep.pa_reportlineset_id);
var scols = GB.prepare("SELECT pa_reportcolumn_id, columntype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, relativeperiod, postingtype, name FROM pa_reportcolumn WHERE pa_reportcolumnset_id=(SELECT pa_reportcolumnset_id FROM pa_report WHERE pa_report_id=?) AND isactive='Y' ORDER BY seqno").all(SRID);
var sbl = {}; slines.forEach(function (l) { sbl[l.pa_reportline_id] = GB.prepare("SELECT c_elementvalue_id ev FROM pa_reportsource WHERE pa_reportline_id=? AND isactive='Y'").all(l.pa_reportline_id).map(function (s) { return { account_ids: leaves(s.ev) }; }); });
var sc = CORE.resolveScope(periods, factRows, scols);
var folded = CORE.foldStatement(rep, slines, scols, sbl, factRows, accounts, sc.periodWindows);
function nonzero(f) { var n = 0; f.lineOrder.forEach(function (lid) { f.colOrder.forEach(function (cid) { if (Math.round(Number((f.cells[lid] || {})[cid]) * 100) !== 0) n++; }); }); return n; }
var stmtCells = nonzero(folded);
console.log('§REPORT-OPEN surface=gw via=ring kind=statement rendered=' + (stmtCells > 0 ? 'Y' : 'N') + ' cells=' + stmtCells +
  ' report=' + SRID + ' "' + rep.name + '" coverage=' + sc.coverage + ' sourcePeriod=' + (sc.sourcePeriod ? sc.sourcePeriod.id : 'none'));
ok(stmtCells > 0, 'Statement fold renders real non-zero cells (' + stmtCells + ' > 0) — the ▤ Reports menu opens a populated statement');
console.log('');

console.log('=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
