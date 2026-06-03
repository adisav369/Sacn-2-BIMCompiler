#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_odoo_fold_f8.js — finding f8: the DEEPER partial (bill ≠ receipt) — a BOUNDED result.
 *   Spec: prompts/ODOO_FOLD_POC.md (a needed-but-absent capability is a NAMED finding, code-or-data
 *         classified) · docs/ERP.md §0.17/§0.19.
 *
 * f7 held because received==billed (the exact-qty matcher's happy case). f8 breaks that on purpose:
 * Odoo PO P00013 received 12 but the vendor billed only 8 (4 received-not-billed). This is the case
 * f7 NAMED as untested. It is a deliberate FALSIFICATION attempt — and it finds a real bound:
 *
 *   - The SHIPPED matcher (erp_engine.match) pairs L↔R only when |qtyL−qtyR| ≤ tol. receipt(12) vs
 *     bill(8) → NO pair. The exact-qty greedy matcher CANNOT reconcile bill≠receipt. ← the bound.
 *   - The FIX is small and NAMED: partial-QUANTITY matching (pair min(qtyL,qtyR), carry the remainder).
 *     Characterised here in the runner to show exactly what's needed — it reconciles (matched 8, the
 *     receipt's 4 stay open-to-bill). Crucially it still emits the SAME MATCH verb: newVerbs=[].
 *
 * HONEST classification: the bound is NEW MATCHER BEHAVIOUR (code in erp_engine.match), NOT a new verb
 * and NOT adapter-shaped (data). The migration-solvent thesis holds at the VERB level; the matcher's
 * IMPLEMENTATION is incomplete for bill≠receipt. Report exactly that — do not hide it, do not overclaim.
 *
 * Static oracle build/erp/odoo_oracle_p2p_f8.json (§0.12). RESOLVED (Stage 1, 2026-06-03): the fix
 * landed in erp_engine.match as opt-in partial-quantity matching (opts.partial=true). This runner now
 * EXERCISES the engine's partial mode (the local characterisation is gone) — flipping BOUNDED→PASS while
 * the SHIPPED exact-qty path still returns 0 pairs (the bound, kept as the contrast witness). Run:
 *   node scripts/poc_odoo_fold_f8.js 2>&1 | tee -a build/erp/odoo_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var E = require('./erp_engine');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle_p2p_f8.json'), 'utf8'));
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function n2(x) { return Number(x).toFixed(2); }

(async function () {
  var SQL = await initSqlJs();
  var built = A.buildBuyEvents(ORACLE);
  var poId = ORACLE.meta.po_id, billId = ORACLE.meta.bill_id;
  var L = ORACLE.po_lines[0];
  var received = L.received, billed = L.invoiced;

  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-ODOO-FOLD-F8 — bill ≠ receipt: a BOUNDED finding (' + ORACLE.meta.po + ') ═══');
  console.log('    scenario: ' + ORACLE.meta.scenario);
  console.log('══════════════════════════════════════════════════════════════════════════════\n');

  var realMatch = E.match, matcherCalls = 0;
  E.match = function () { matcherCalls++; return realMatch.apply(E, arguments); };

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };
  var usedVerbs = {};
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 4000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
  });

  console.log('\n── effects vs the Odoo oracle ──');
  var mineRecv = K.query(db, "SELECT COALESCE(SUM(json_extract(metadata,'$.movementqty')),0) q FROM document_lines WHERE document_id='DOC:M_InOut@from" + poId + "'")[0].q;
  var mineBill = K.query(db, "SELECT COALESCE(SUM(json_extract(metadata,'$.qtyinvoiced')),0) q FROM document_lines WHERE document_id='DOC:C_Invoice@from" + poId + "'")[0].q;
  verdict(mineRecv === received && mineBill === billed, 'received & billed reproduce oracle (bill≠receipt)', 'received=' + mineRecv + ' billed=' + mineBill + ' (Δ=' + (received - billed) + ')');
  var GLW = " WHERE source='DOC:C_Invoice#" + billId + "' AND account_id IS NOT NULL";
  var mDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLW)[0].s;
  var mCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLW)[0].s;
  verdict(Math.round(mDR * 100) === Math.round(mCR * 100) && Math.round(mDR * 100) === Math.round(ORACLE.bill.amount_total * 100), 'AP balanced AND total == oracle bill total', 'ΣDR=' + n2(mDR) + ' total=' + n2(ORACLE.bill.amount_total));

  var S = built.matchSets;
  var mOpts = { idL: 'id', idR: 'id', qtyL: 'qty', qtyR: 'qty', keyOf: function (r) { return r.pid; }, partition: function (r) { return r.bp; } };

  console.log('\n── the bound was real, the fix has LANDED: exact-qty path vs the engine partial mode ──');
  // (1) the exact-qty path STILL cannot reconcile bill≠receipt — the bound, kept as contrast
  var exact = E.match(S.receiptLines, S.billLines, mOpts);
  verdict(exact.length === 0, 'exact-qty erp_engine.match (default) CANNOT reconcile receipt(' + received + ')↔bill(' + billed + ') — the original BOUND, unchanged', 'exact-pairs=' + exact.length + ' (|12−8|>tol → no pair)');
  // (2) the LANDED engine fix DOES reconcile — opts.partial=true, min-qty + remainder carried
  var part = E.match(S.receiptLines, S.billLines, Object.assign({}, mOpts, { partial: true }));
  var matchedQty = part.reduce(function (s, p) { return s + p.qty; }, 0);
  var openToBill = received - billed;
  verdict(part.length === 1 && matchedQty === billed && (matchedQty + openToBill === received), 'NEEDED partial-qty matcher reconciles: matched ' + matchedQty + ' + open-to-bill ' + openToBill + ' == received ' + received, 'pairs=' + part.length + ' matchedQty=' + matchedQty);
  // (3) the fix still emits the SAME MATCH verb — the bound is matcher BEHAVIOUR, not a new verb
  var matchOps = part.map(function (p) { return { op_type: 'MATCH', table: 'M_MatchInv', source_line_id: p.l, counterpart_line_id: p.r, match_type: 'inv', qty: p.qty }; });
  matchOps.forEach(function (o) { usedVerbs[o.op_type] = 1; });
  K.register('C_MatchInv', 'CO', function () { return matchOps; });
  var dM = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 4500 }, { docType: 'C_MatchInv', action: 'CO', status: 'DR' });
  var committedQty = K.query(db, "SELECT json_extract(metadata,'$.qty') q FROM document_lines WHERE match_type='inv'")[0];
  verdict(dM.ok && committedQty && committedQty.q === billed, 'partial reconciliation commits via the EXISTING MATCH verb (qty carried in the op)', 'matchOp.qty=' + (committedQty && committedQty.q));

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  E.match = realMatch;

  console.log('');
  console.log('§MATCH-PARTIAL pairs=' + part.length + ' matchedQty=' + matchedQty + ' remainder(open-to-bill)=' + openToBill + ' (received=' + received + '=matched+remainder) via erp_engine.match opts.partial=true');
  console.log('§ODOO-FOLD-F8 scenario=received:' + received + '/billed:' + billed + '/open-to-bill:' + openToBill);
  console.log('§ODOO-FOLD-F8 exact-qty-pairs=' + exact.length + ' (CANNOT, default path) → partial-qty-pairs=' + part.length + ' matchedQty=' + matchedQty + ' (RECONCILES, opts.partial=true)');
  console.log('§ODOO-FOLD-F8 verbs used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']  ← thesis holds at the VERB level');
  console.log('§ODOO-FINDINGS-F8 RESOLVED=partial-QUANTITY-matching-landed(erp_engine.match opts.partial=true: pair-min(qty)+carry-remainder; exact-qty fast path preserved) ' +
              'CLASS=new-matcher-BEHAVIOUR(code in erp_engine.match) NOT-a-new-verb(MATCH carries the partial qty) NOT-adapter-shaped(not data/config) ' +
              'SCOPE=common-real-case(over/under-billing, disputed/damaged goods) — the one genuine Odoo engine finding, now closed');

  db.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§ODOO-FOLD-F8 ' + (fails ? 'FAIL — ' + fails + ' checks red' :
    'PASS (was BOUNDED, fix landed) — bill≠receipt now folds: erp_engine.match opts.partial=true pairs min(qty) and carries the remainder, reconciling to the unit (matched ' + matchedQty + ' + open-to-bill ' + openToBill + ' == received ' + received + '). Still the SAME MATCH verb — newVerbs=[]. The exact-qty default path is unchanged (the original bound kept as contrast). Migration-solvent thesis holds for Odoo bill≠receipt.'));
  process.exit(fails ? 1 : 0);
})();
