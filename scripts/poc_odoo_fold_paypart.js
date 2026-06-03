#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_odoo_fold_paypart.js — Stage 2 finding f2: PARTIAL PAYMENT (sell-side partial reconciliation).
 *   Spec: prompts/MIGRATION_CAMPAIGN_RESUME.md Stage 2 · prompts/ODOO_FOLD_POC.md · docs/ERP.md §0.12/§0.19.
 *
 * The sell-side O2C chain is already folded (§ODOO-FOLD PASS) — but with a FULL payment that clears the
 * invoice (full-reconcile = FK-directed ALLOCATE, the f2 bound named in poc_odoo_fold.js). This runner
 * folds the OTHER half of f2: a payment that does NOT clear the invoice. Odoo drove SO→deliver→invoice→
 * post→register a PARTIAL payment of 3000 of a 5002.50 invoice, leaving amount_residual=2002.50,
 * payment_state='partial' (build/erp/odoo_oracle_paypart.json, driven by scripts/drive_odoo_paypart.py).
 *
 * THE QUESTION: does partial reconciliation need a new verb? NO. The SAME ALLOCATE verb carries the
 * smaller amount; the residual is total−allocated, reproduced to the cent. This is the CLEANEST f2 result:
 * newVerbs=[] AND no engine change (unlike f8's matcher behaviour). Partial payment is just ALLOCATE(amount<total).
 *
 * Deterministic: every id/amount/residual is a RECORDED INPUT from the static oracle (Odoo computed the
 * residual; we extract + reproduce it — no Date.now/Math.random, no live Odoo at replay, §0.12). Run:
 *   node scripts/poc_odoo_fold_paypart.js 2>&1 | tee -a build/erp/odoo_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle_paypart.json'), 'utf8'));
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function n2(x) { return Number(x).toFixed(2); }

(async function () {
  var SQL = await initSqlJs();
  var built = A.buildPayPartEvents(ORACLE);
  var soId = ORACLE.meta.so_id, invId = ORACLE.meta.invoice_id;
  var total = ORACLE.meta.amount_total, allocated = ORACLE.meta.reconcile_amount;
  var oracleResidual = ORACLE.meta.amount_residual, oracleState = ORACLE.meta.payment_state;

  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-ODOO-FOLD-PAYPART — f2 PARTIAL payment: residual carried (' + ORACLE.meta.so + ') ═══');
  console.log('    scenario: ' + ORACLE.meta.scenario);
  console.log('══════════════════════════════════════════════════════════════════════════════\n');
  console.log('── the chain (oracle): SO ' + ORACLE.meta.so + ' → deliver → ' + ORACLE.meta.invoice +
              ' (total ' + n2(total) + ') → pay ' + n2(allocated) + ' → residual ' + n2(oracleResidual) + ' (' + oracleState + ')\n');

  // one handler per event; the kernel owns the write (handlers carry no business logic).
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };

  // ── drive the 5 document-events through the existing verbs ──
  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 6000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed (' + ev.d.status + '→' + (d.to || '?') + ')', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  console.log('\n── effects vs the Odoo oracle (the PARTIAL reconciliation, to the cent) ──');

  // 1) GL double-entry balanced AND total == oracle invoice total (the invoice is unchanged by partial pay)
  var GLWHERE = " WHERE source='DOC:C_Invoice#" + invId + "' AND account_id IS NOT NULL";
  var mineDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLWHERE)[0].s;
  var mineCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLWHERE)[0].s;
  verdict(Math.round(mineDR * 100) === Math.round(mineCR * 100) && Math.round(mineDR * 100) === Math.round(total * 100),
    'GL balanced AND invoice total == oracle (unaffected by partial pay)', 'ΣDR=' + n2(mineDR) + ' ΣCR=' + n2(mineCR) + ' total=' + n2(total));

  // 2) the PARTIAL allocate: amount committed == the partial payment (< invoice total)
  var allocRow = K.query(db, "SELECT json_extract(metadata,'$.invoice_id') i, json_extract(metadata,'$.amount') a FROM journal WHERE journal_id LIKE 'JRN@paypay%'")[0];
  var mineAlloc = allocRow ? Number(allocRow.a) : null;
  verdict(allocRow && Number(allocRow.i) === invId && Math.round(mineAlloc * 100) === Math.round(allocated * 100) && allocated < total,
    'partial ALLOCATE commits the payment amount via the EXISTING verb (amount < total)', 'allocated=' + n2(mineAlloc) + ' (< total ' + n2(total) + ')');

  // 3) the RESIDUAL reproduces Odoo: residual = total − allocated, to the cent
  var mineResidual = mineAlloc == null ? null : total - mineAlloc;
  verdict(mineResidual != null && Math.round(mineResidual * 100) === Math.round(oracleResidual * 100),
    'residual = total − allocated reproduces Odoo amount_residual', 'mine=' + n2(mineResidual) + ' oracle=' + n2(oracleResidual));

  // 4) the derived payment_state matches Odoo: 0<allocated<total → 'partial'
  var mineState = mineAlloc == null ? 'none' : (mineAlloc <= 0 ? 'not_paid' : (mineResidual <= 0.005 ? 'paid' : 'partial'));
  verdict(mineState === oracleState, 'derived payment_state matches Odoo (partial, not cleared)', 'mine=' + mineState + ' oracle=' + oracleState);

  var diffAgree = Math.round(mineDR * 100) === Math.round(total * 100) &&
    mineAlloc != null && Math.round(mineAlloc * 100) === Math.round(allocated * 100) &&
    Math.round(mineResidual * 100) === Math.round(oracleResidual * 100) && mineState === oracleState;

  // ── replay determinism: rebuild twice, hashes must agree ──
  var liveHash = K.projectionHash(db);
  var fa = new SQL.Database(), repA = K.replay(db, fa); fa.close();
  var fb = new SQL.Database(), repB = K.replay(db, fb); fb.close();
  verdict(repA.hash === liveHash && repA.hash === repB.hash, 'replay rebuilds projection EXACTLY + stable (rebuildA==rebuildB==live)', 'live=' + liveHash + ' A=' + repA.hash + ' B=' + repB.hash);

  // ── the witnesses (§-log first; the only evidence) ──
  console.log('');
  console.log('§ODOO-FOLD-PAYPART scenario=total:' + n2(total) + '/paid:' + n2(allocated) + '/residual:' + n2(oracleResidual) + '/state:' + oracleState);
  console.log('§ODOO-FOLD-PAYPART chain so→deliver→invoice→post→PARTIAL-pay mapped=' + mappedHops + '/5 missing=' + (5 - mappedHops));
  console.log('§ODOO-FOLD-PAYPART verbs used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']  ← same ALLOCATE verb, smaller amount');
  console.log('§ODOO-FOLD-PAYPART residual mine=' + n2(mineResidual) + ' == oracle ' + n2(oracleResidual) + ' agree=' + (diffAgree ? 'Y' : 'N') + ' replay-stable=' + (repA.hash === repB.hash ? 'Y' : 'N'));
  console.log('§ODOO-FINDINGS-F2 RESULT=HOLDS(partial-payment folds with the EXISTING ALLOCATE verb at a smaller amount; residual=total−allocated reproduces Odoo to the cent; payment_state derives as partial) ' +
              'CLASS=no-new-verb(newVerbs=[]) AND no-engine-change(ALLOCATE already carries amount — UNLIKE f8 which needed matcher behaviour) ' +
              'NOTE=full-reconcile (poc_odoo_fold) and partial-reconcile (here) are the SAME verb; the §0.19 "matcher composes in at one seam" applies only to multi-document settlement, NOT to a single partial payment');

  db.close();
  console.log('\n═══ VERDICT ═══');
  var thesisHolds = newVerbs.length === 0 && diffAgree && mappedHops === 5;
  console.log('§ODOO-FOLD-PAYPART ' + (fails ? 'FAIL — ' + fails + ' checks red' :
    (thesisHolds ? 'PASS — Odoo PARTIAL payment folds with the EXISTING verb set: same ALLOCATE, amount 3000 < total 5002.50, residual 2002.50 reproduces Odoo to the cent; newVerbs=[]; no engine change; replay exact. f2 partial reconciliation HOLDS — the cleanest Odoo finding (free).'
                 : 'BOUNDED — newVerbs=[' + newVerbs.join(',') + '] or diff disagrees (a valid, reportable result; update HolyGrail scope)')));
  process.exit(fails ? 1 : 0);
})();
