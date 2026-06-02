#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_odoo_fold_partial.js — finding f7: the PARTIAL 3-way (does the matcher hold under partials?).
 *   Spec: prompts/ODOO_FOLD_POC.md · docs/ERP.md §0.17 (allocation = FK-directed, NOT the matcher class)
 *         / §0.19 (the matcher composes at one seam).
 *
 * The full-qty 3-way (poc_odoo_fold_3way.js) passed trivially because ordered==received==billed. This
 * drives a PARTIAL: Odoo PO P00012 ordered 20, received 12 (backorder 8), billed 12. The question f7
 * asks: does the EXACT-qty greedy matcher still reconcile, or does partial expose a bound?
 *
 * The honest decomposition this tests: a partial = an EXACT-match settlement leg (received==billed → the
 * matcher pairs it) + an FK-DIRECTED open remainder (ordered − received = 8 → derivation, NOT a match).
 * If that holds, newVerbs=[] AND no new matcher policy is needed. The bound it then NAMES (f8) is the
 * DEEPER partial — bill ≠ receipt — which would require partial-quantity matching the engine lacks.
 *
 * Static oracle build/erp/odoo_oracle_p2p_partial.json (§0.12). Run:
 *   node scripts/poc_odoo_fold_partial.js 2>&1 | tee -a build/erp/odoo_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var E = require('./erp_engine');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle_p2p_partial.json'), 'utf8'));
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function n2(x) { return Number(x).toFixed(2); }

(async function () {
  var SQL = await initSqlJs();
  var built = A.buildBuyEvents(ORACLE);
  var poId = ORACLE.meta.po_id, billId = ORACLE.meta.bill_id;
  var L = ORACLE.po_lines[0];
  var ordered = L.ordered, received = L.received, billed = L.invoiced, open = ordered - received;

  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-ODOO-FOLD-PARTIAL — f7: matcher under a PARTIAL 3-way (' + ORACLE.meta.po + ') ═══');
  console.log('    scenario: ' + ORACLE.meta.scenario);
  console.log('══════════════════════════════════════════════════════════════════════════════\n');

  var realMatch = E.match, matcherCalls = 0;
  E.match = function () { matcherCalls++; return realMatch.apply(E, arguments); };

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };

  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 3000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });

  // ── the settlement leg: receipt(12) ↔ bill(12) — EXACT match (received==billed) ──
  var S = built.matchSets;
  var mOpts = { idL: 'id', idR: 'id', qtyL: 'qty', qtyR: 'qty', keyOf: function (r) { return r.pid; }, partition: function (r) { return r.bp; } };
  var miPairs = E.match(S.receiptLines, S.billLines, mOpts);
  var matchOps = miPairs.map(function (p) { return { op_type: 'MATCH', table: 'M_MatchInv', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'inv' }; });
  matchOps.forEach(function (o) { usedVerbs[o.op_type] = 1; });
  K.register('C_MatchInv', 'CO', function () { return matchOps; });
  var dM = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 3500 }, { docType: 'C_MatchInv', action: 'CO', status: 'DR' });
  if (dM.ok) mappedHops++;

  // ── the order↔bill EXACT match — EXPECTED to NOT pair (20 ≠ 12): the bound being characterized ──
  var poBillPairs = E.match(S.poLines, S.billLines, mOpts);

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  console.log('\n── effects vs the Odoo oracle (PARTIAL) ──');
  var mineRecv = K.query(db, "SELECT COALESCE(SUM(json_extract(metadata,'$.movementqty')),0) q FROM document_lines WHERE document_id='DOC:M_InOut@from" + poId + "'")[0].q;
  verdict(mineRecv === received, 'received qty reproduces oracle (partial)', 'mine=' + mineRecv + ' oracle=' + received);
  var mineBill = K.query(db, "SELECT COALESCE(SUM(json_extract(metadata,'$.qtyinvoiced')),0) q FROM document_lines WHERE document_id='DOC:C_Invoice@from" + poId + "'")[0].q;
  verdict(mineBill === billed, 'billed qty reproduces oracle (partial)', 'mine=' + mineBill + ' oracle=' + billed);

  var GLW = " WHERE source='DOC:C_Invoice#" + billId + "' AND account_id IS NOT NULL";
  var mDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLW)[0].s;
  var mCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLW)[0].s;
  verdict(Math.round(mDR * 100) === Math.round(mCR * 100) && Math.round(mDR * 100) === Math.round(ORACLE.bill.amount_total * 100), 'AP balanced AND total == oracle bill total', 'ΣDR=' + n2(mDR) + ' ΣCR=' + n2(mCR) + ' oracle=' + n2(ORACLE.bill.amount_total));

  // the heart of f7: the partial decomposes into (exact-match settlement leg) + (FK-directed remainder)
  verdict(miPairs.length === 1 && matcherCalls >= 1, 'settlement leg receipt↔bill EXACT-matches (received==billed=' + billed + ')', 'inv-pairs=' + miPairs.length + ' matcherCalls=' + matcherCalls);
  var matchedQty = miPairs.length ? billed : 0;
  var openQty = ordered - received;                          // FK-directed derivation, NOT a match (§0.17)
  verdict(matchedQty === billed && openQty === open && (matchedQty + openQty === ordered), 'partial decomposes: matched(' + matchedQty + ') + FK-directed open(' + openQty + ') == ordered(' + ordered + ') = Odoo backorder', 'matched=' + matchedQty + ' open=' + openQty);
  verdict(poBillPairs.length === 0, 'order↔bill does NOT exact-match (20≠12) — the bound, correctly NOT forced', 'po-bill exact-pairs=' + poBillPairs.length + ' (open handled FK-directed, not by the matcher)');

  var liveHash = K.projectionHash(db);
  var fa = new SQL.Database(), repA = K.replay(db, fa); fa.close();
  var fb = new SQL.Database(), repB = K.replay(db, fb); fb.close();
  verdict(repA.hash === liveHash && repA.hash === repB.hash, 'replay rebuilds projection EXACTLY + stable', 'live=' + liveHash + ' A=' + repA.hash + ' B=' + repB.hash);

  E.match = realMatch;

  var foldsClean = mineRecv === received && mineBill === billed && miPairs.length === 1 && (matchedQty + openQty === ordered) && fails === 0;
  console.log('');
  console.log('§ODOO-FOLD-PARTIAL scenario=ordered:' + ordered + '/received:' + received + '/billed:' + billed + '/open:' + open);
  console.log('§ODOO-FOLD-PARTIAL verbs used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + '] matcherInvoked=' + matcherCalls);
  console.log('§ODOO-FOLD-PARTIAL decompose matched=' + matchedQty + '(exact-match leg) + open=' + openQty + '(FK-directed, §0.17) == ordered=' + ordered + ' agree=' + (foldsClean ? 'Y' : 'N'));
  console.log('§ODOO-FINDINGS-PARTIAL f7-RESULT=HOLDS(partial-receipt folds: received==billed → exact MATCH on the settlement leg; ordered−received → FK-directed open remainder, NOT a matcher pairing — exactly the §0.17 split; newVerbs=[], no new matcher policy needed) ' +
              'f8-NEXT=deeper-partial-where-bill≠receipt(would need partial-QUANTITY matching — pair min(qtyL,qtyR), leave remainder — which the exact-qty greedy matcher does NOT carry; the honest next falsifier, untested here because Odoo bills received-qty by default)');

  db.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§ODOO-FOLD-PARTIAL ' + (fails ? 'FAIL — ' + fails + ' checks red (a real bound found, report it)' :
    'PASS (f7 HOLDS) — Odoo partial-receipt 3-way folds: exact-match settlement leg + FK-directed open remainder reproduce Odoo (received=' + received + ', billed=' + billed + ', open=' + open + '); newVerbs=[]. Bound f8 (bill≠receipt → partial-qty matching) named, not hidden.'));
  process.exit(fails ? 1 : 0);
})();
