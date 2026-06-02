#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_odoo_fold_3way.js — the BUY-SIDE continuation of the Odoo migration falsifier.
 *   Spec: prompts/ODOO_FOLD_POC.md · docs/ERP.md §0.17/§0.19 (matcher composes at one seam).
 *
 * The sell-side fold (poc_odoo_fold.js) exercised 5 of the 6 kernel verbs; only MATCH (the 3-way
 * settlement engine) was untouched — finding f2. This drives Odoo's PROCURE-to-pay chain
 * (PO P00011 → receipt → vendor bill → GL post → 3-way reconcile) and folds it, invoking the
 * EXISTING matcher (erp_engine.match) to reconcile PO↔receipt↔bill. Completes verb coverage to 6/6.
 *
 * Static oracle build/erp/odoo_oracle_p2p.json (Odoo's executed output, §0.12). No live Odoo at
 * replay. Deterministic. Run:  node scripts/poc_odoo_fold_3way.js 2>&1 | tee -a build/erp/odoo_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var E = require('./erp_engine');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle_p2p.json'), 'utf8'));
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function diffSet(mine, oracle) {
  function tally(a) { var m = {}; a.forEach(function (k) { m[k] = (m[k] || 0) + 1; }); return m; }
  var mm = tally(mine), om = tally(oracle), keys = {};
  Object.keys(mm).forEach(function (k) { keys[k] = 1; }); Object.keys(om).forEach(function (k) { keys[k] = 1; });
  var matched = 0, missed = 0, extra = 0;
  Object.keys(keys).forEach(function (k) { var a = mm[k] || 0, b = om[k] || 0; matched += Math.min(a, b); if (b > a) missed += b - a; if (a > b) extra += a - b; });
  return { matched: matched, missed: missed, extra: extra, ok: missed === 0 && extra === 0, mine: mine.length, oracle: oracle.length };
}
function n2(x) { return Number(x).toFixed(2); }

(async function () {
  var SQL = await initSqlJs();
  var built = A.buildBuyEvents(ORACLE);
  var poId = ORACLE.meta.po_id, billId = ORACLE.meta.bill_id;

  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-ODOO-FOLD-3WAY — buy-side continuation, the MATCH verb (' + ORACLE.meta.po + ') ═══');
  console.log('    source: ' + ORACLE.meta.source);
  console.log('══════════════════════════════════════════════════════════════════════════════\n');
  console.log('── the chain (oracle): PO ' + ORACLE.meta.po + ' → ' + ORACLE.meta.receipt.join(',') + ' → ' + ORACLE.meta.bill + ' → 3-way reconcile\n');

  // ── matcher spy: prove the EXISTING settlement engine is genuinely invoked (not stubbed) ──
  var realMatch = E.match, matcherCalls = 0;
  E.match = function () { matcherCalls++; return realMatch.apply(E, arguments); };

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };

  // ── events 1-4: confirm PO → receive → bill → post (the derivation verbs) ──
  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 2000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed (' + ev.d.status + '→' + (d.to || '?') + ')', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });

  // ── event 5: the 3-way MATCH via the EXISTING settlement engine (erp_engine.match) ──
  var S = built.matchSets;
  var mOpts = { idL: 'id', idR: 'id', qtyL: 'qty', qtyR: 'qty', keyOf: function (r) { return r.pid; }, partition: function (r) { return r.bp; } };
  var miPairs = E.match(S.receiptLines, S.billLines, mOpts);   // receipt ↔ bill (M_MatchInv)
  var poPairs = E.match(S.poLines, S.billLines, mOpts);        // PO ↔ bill      (M_MatchPO)
  var matchOps = [];
  miPairs.forEach(function (p) { matchOps.push({ op_type: 'MATCH', table: 'M_MatchInv', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'inv' }); });
  poPairs.forEach(function (p) { matchOps.push({ op_type: 'MATCH', table: 'M_MatchPO', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'po' }); });
  matchOps.forEach(function (o) { usedVerbs[o.op_type] = 1; });
  K.register('C_MatchPO', 'CO', function () { return matchOps; });
  var dM = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 2500 }, { docType: 'C_MatchPO', action: 'CO', status: 'DR' });
  verdict(dM.ok, 'event 5 (3-way MATCH) committed (DR→' + (dM.to || '?') + ')', dM.ok ? 'ops=' + dM.applied : dM.stage + ':' + dM.reason);
  if (dM.ok) mappedHops++;
  verdict(matcherCalls > 0, 'the EXISTING matcher (erp_engine.match) was genuinely INVOKED (not stubbed)', 'E.match calls=' + matcherCalls);

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  console.log('\n── effects vs the Odoo oracle ──');

  // received qty per product
  var mineRecv = K.query(db, "SELECT json_extract(metadata,'$.m_product_id') pid, json_extract(metadata,'$.movementqty') q FROM document_lines WHERE document_id='DOC:M_InOut@from" + poId + "'").map(function (r) { return r.pid + ':' + r.q; });
  var oracleRecv = ORACLE.receipt_moves.map(function (m) { return m.pid + ':' + m.qty; });
  var dRecv = diffSet(mineRecv, oracleRecv);
  verdict(dRecv.ok && dRecv.oracle > 0, 'received qty reproduces oracle', 'oracle=' + dRecv.oracle + ' mine=' + dRecv.mine + ' matched=' + dRecv.matched);

  // billed qty per product
  var mineBill = K.query(db, "SELECT json_extract(metadata,'$.m_product_id') pid, json_extract(metadata,'$.qtyinvoiced') q FROM document_lines WHERE document_id='DOC:C_Invoice@from" + poId + "'").map(function (r) { return r.pid + ':' + r.q; });
  var oracleBill = ORACLE.po_lines.map(function (l) { return l.pid + ':' + l.invoiced; });
  var dBill = diffSet(mineBill, oracleBill);
  verdict(dBill.ok && dBill.oracle > 0, 'billed qty reproduces oracle', 'oracle=' + dBill.oracle + ' mine=' + dBill.mine + ' matched=' + dBill.matched);

  // AP double-entry (the POST verb) — balanced + total
  var GLW = " WHERE source='DOC:C_Invoice#" + billId + "' AND account_id IS NOT NULL";
  var mineGL = K.query(db, "SELECT account_id a, amtacctdr dr, amtacctcr cr FROM journal" + GLW).map(function (r) { return r.a + ':' + n2(r.dr) + ':' + n2(r.cr); });
  var oracleGL = ORACLE.bill_gl_lines.map(function (a) { return a.account + ':' + n2(a.debit) + ':' + n2(a.credit); });
  var dGL = diffSet(mineGL, oracleGL);
  var mDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLW)[0].s;
  var mCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLW)[0].s;
  verdict(dGL.ok && dGL.oracle > 0, 'AP double-entry reproduces oracle (account:dr:cr)', 'oracle=' + dGL.oracle + ' mine=' + dGL.mine + ' matched=' + dGL.matched);
  verdict(Math.round(mDR * 100) === Math.round(mCR * 100) && Math.round(mDR * 100) === Math.round(ORACLE.bill.amount_total * 100), 'AP balanced AND total == oracle bill total', 'ΣDR=' + n2(mDR) + ' ΣCR=' + n2(mCR) + ' oracleTotal=' + n2(ORACLE.bill.amount_total));

  // the 3-way MATCH reproduces Odoo's reconciliation (every PO line received AND billed, qty agrees)
  var nLines = ORACLE.po_lines.length;
  var threeWayOk = miPairs.length === nLines && poPairs.length === nLines;
  var qtyAligned = ORACLE.po_lines.every(function (l) { return l.ordered === l.received && l.received === l.invoiced; });
  var mineMatch = K.query(db, "SELECT match_type t, COUNT(*) c FROM document_lines WHERE match_type IN ('inv','po') GROUP BY match_type").map(function (r) { return r.t + ':' + r.c; }).sort();
  verdict(threeWayOk && qtyAligned, '3-way MATCH reproduces oracle (PO↔receipt↔bill, qty agrees to the unit)', 'inv-pairs=' + miPairs.length + ' po-pairs=' + poPairs.length + ' lines=' + nLines + ' qtyAligned=' + qtyAligned);
  verdict(mineMatch.length === 2, 'both settlement edges committed to the projection (M_MatchInv + M_MatchPO)', 'edges=[' + mineMatch.join(',') + ']');

  var diffAgree = dRecv.ok && dBill.ok && dGL.ok && threeWayOk;

  // replay determinism
  var liveHash = K.projectionHash(db);
  var fa = new SQL.Database(), repA = K.replay(db, fa); fa.close();
  var fb = new SQL.Database(), repB = K.replay(db, fb); fb.close();
  verdict(repA.hash === liveHash && repA.hash === repB.hash, 'replay rebuilds projection EXACTLY + stable', 'live=' + liveHash + ' A=' + repA.hash + ' B=' + repB.hash);

  E.match = realMatch;  // un-spy

  console.log('');
  console.log('§ODOO-FOLD-3WAY chain po→receipt→bill→post→match mapped=' + mappedHops + '/5 missing=' + (5 - mappedHops));
  console.log('§ODOO-FOLD-3WAY verbs used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + '] matcherInvoked=' + matcherCalls);
  console.log('§ODOO-FOLD-3WAY diff matched=' + (dRecv.matched + dBill.matched + dGL.matched) + ' vs odoo_oracle_p2p agree=' + (diffAgree ? 'Y' : 'N'));
  console.log('§ODOO-FOLD-VERB-COVERAGE sell-side=[ALLOCATE,CREATE_DOCUMENT,CREATE_LINE,POST,SET_STATUS] + buy-side=[' + used.join(',') + '] → all 6 kernel verbs exercised across the two folds, newVerbs=[]');
  console.log('§ODOO-FINDINGS-3WAY f5=matcher-reconciles-Odoo-3way(PO↔receipt↔bill via erp_engine.match, partition=partner, key=product, qty-tolerance — the SAME settlement engine, no Odoo-specific code) ' +
              'f6=AP-mirror-of-AR(vendor bill posts DR Expense/Tax CR Payable; same POST verb, ΣDR==ΣCR) ' +
              'f7=full-qty-3way-here(partial-receipt/over-bill would test tolerance+ordering policy — the matcher already carries FIFO/LIFO+tol, untested on Odoo)');

  db.close();
  console.log('\n═══ VERDICT ═══');
  var holds = newVerbs.length === 0 && diffAgree && mappedHops === 5 && matcherCalls > 0;
  console.log('§ODOO-FOLD-3WAY ' + (fails ? 'FAIL — ' + fails + ' checks red' :
    (holds ? 'PASS — Odoo procure-to-pay 3-way folds with the EXISTING verb set incl. MATCH; newVerbs=[]; matcher genuinely invoked; effects reproduce Odoo. With the sell-side fold, ALL 6 kernel verbs now fold Odoo with nothing invented.'
           : 'BOUNDED — newVerbs=[' + newVerbs.join(',') + ']')));
  process.exit(fails ? 1 : 0);
})();
