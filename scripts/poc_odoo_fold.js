#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_odoo_fold.js — the migration-solvent FALSIFIER on Odoo (prompts/ODOO_FOLD_POC.md Step 2).
 *   Drives Odoo's executed O2C chain (static build/erp/odoo_oracle.json, SO S00023) through the
 *   EXISTING kernel verbs via a PURE adapter (scripts/odoo_adapter.js). Diffs the canonical effects
 *   against the Odoo oracle, the same effect-set multiset diff as diff_oracle.js / poc_longtail.js.
 *
 *   THE QUESTION: does Odoo's sale→deliver→invoice→post→reconcile fold with the 6 kernel verbs and a
 *   pure adapter, reproducing Odoo's output to the cent? newVerbs=[] = thesis holds; non-empty = bounded.
 *
 *   Deterministic: every id/qty/amount is a RECORDED INPUT from the static oracle (no Date.now /
 *   Math.random). No live Odoo at replay (§0.12). Run:
 *     node scripts/poc_odoo_fold.js 2>&1 | tee -a build/erp/odoo_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle.json'), 'utf8'));
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// canonical effect-set diff (identical to diff_oracle.js): MATCH iff same multiset.
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
  var built = A.buildEvents(ORACLE);
  var soId = ORACLE.meta.so_id, invId = ORACLE.meta.invoice_id;

  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-ODOO-FOLD — migration-solvent falsifier on Odoo O2C (' + ORACLE.meta.so + ') ═══');
  console.log('    source: ' + ORACLE.meta.source);
  console.log('══════════════════════════════════════════════════════════════════════════════\n');
  console.log('── the chain (oracle): SO ' + ORACLE.meta.so + ' → ' + ORACLE.meta.picking.join(',') +
              ' → ' + ORACLE.meta.invoice + ' → pay → reconcile ' + n2(ORACLE.meta.reconcile_amount) + ' (' + ORACLE.meta.payment_state + ')\n');

  // ── register one handler per event; the kernel owns the write (no business logic in handlers) ──
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };

  // ── drive the 5 document-events through the existing verbs ──
  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 1000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed (' + ev.d.status + '→' + (d.to || '?') + ')', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  console.log('\n── effects vs the Odoo oracle (effect-set multiset diff, to the cent) ──');

  // 1) delivered qty per product
  var mineShip = K.query(db, "SELECT json_extract(metadata,'$.m_product_id') pid, json_extract(metadata,'$.movementqty') q FROM document_lines WHERE document_id='DOC:M_InOut@from" + soId + "'").map(function (r) { return r.pid + ':' + r.q; });
  var deliv = {}; ORACLE.delivery_moves.forEach(function (m) { deliv[m.pid] = (deliv[m.pid] || 0) + m.qty; });
  var oracleShip = Object.keys(deliv).map(function (pid) { return pid + ':' + deliv[pid]; });
  var dShip = diffSet(mineShip, oracleShip);
  verdict(dShip.ok && dShip.oracle > 0, 'delivered qty reproduces oracle', 'oracle=' + dShip.oracle + ' mine=' + dShip.mine + ' matched=' + dShip.matched + ' missed=' + dShip.missed + ' extra=' + dShip.extra);

  // 2) invoiced qty per product
  var mineInv = K.query(db, "SELECT json_extract(metadata,'$.m_product_id') pid, json_extract(metadata,'$.qtyinvoiced') q FROM document_lines WHERE document_id='DOC:C_Invoice@from" + soId + "'").map(function (r) { return r.pid + ':' + r.q; });
  var oracleInv = ORACLE.sale_order_lines.map(function (l) { return l.pid + ':' + l.qty_invoiced; });
  var dInv = diffSet(mineInv, oracleInv);
  verdict(dInv.ok && dInv.oracle > 0, 'invoiced qty reproduces oracle', 'oracle=' + dInv.oracle + ' mine=' + dInv.mine + ' matched=' + dInv.matched + ' missed=' + dInv.missed + ' extra=' + dInv.extra);

  // 3) GL double-entry (the POST verb) — account:dr:cr multiset + balance. Filter to POST rows
  //    (account_id NOT NULL): the ALLOCATE settlement edge shares the journal table by design but
  //    carries a null account, so it is NOT part of the invoice's double-entry.
  var GLWHERE = " WHERE source='DOC:C_Invoice#" + invId + "' AND account_id IS NOT NULL";
  var mineGL = K.query(db, "SELECT account_id a, amtacctdr dr, amtacctcr cr FROM journal" + GLWHERE).map(function (r) { return r.a + ':' + n2(r.dr) + ':' + n2(r.cr); });
  var oracleGL = ORACLE.invoice_gl_lines.map(function (a) { return a.account + ':' + n2(a.debit) + ':' + n2(a.credit); });
  var dGL = diffSet(mineGL, oracleGL);
  var mineDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLWHERE)[0].s;
  var mineCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLWHERE)[0].s;
  verdict(dGL.ok && dGL.oracle > 0, 'GL double-entry reproduces oracle (account:dr:cr)', 'oracle=' + dGL.oracle + ' mine=' + dGL.mine + ' matched=' + dGL.matched + ' missed=' + dGL.missed + ' extra=' + dGL.extra);
  verdict(Math.round(mineDR * 100) === Math.round(mineCR * 100) && Math.round(mineDR * 100) === Math.round(ORACLE.invoice.amount_total * 100), 'GL balanced AND total == oracle invoice total', 'ΣDR=' + n2(mineDR) + ' ΣCR=' + n2(mineCR) + ' oracleTotal=' + n2(ORACLE.invoice.amount_total));

  // 4) allocation / reconciliation amount
  var mineAlloc = K.query(db, "SELECT json_extract(metadata,'$.invoice_id') i, json_extract(metadata,'$.amount') a FROM journal WHERE journal_id LIKE 'JRN@paypay%'").map(function (r) { return 'inv' + r.i + ':' + n2(r.a); });
  var oracleAlloc = ['inv' + invId + ':' + n2(ORACLE.meta.reconcile_amount)];
  var dAlloc = diffSet(mineAlloc, oracleAlloc);
  verdict(dAlloc.ok && dAlloc.oracle > 0, 'reconciliation edge reproduces oracle (FK-directed, full payment)', 'oracle=' + dAlloc.oracle + ' mine=' + dAlloc.mine + ' matched=' + dAlloc.matched);

  var diffAgree = dShip.ok && dInv.ok && dGL.ok && dAlloc.ok;

  // ── replay determinism: rebuild twice, hashes must agree (no live system, holder-irrelevant) ──
  var liveHash = K.projectionHash(db);
  var fa = new SQL.Database(), repA = K.replay(db, fa); fa.close();
  var fb = new SQL.Database(), repB = K.replay(db, fb); fb.close();
  verdict(repA.hash === liveHash && repA.hash === repB.hash, 'replay rebuilds projection EXACTLY + stable (rebuildA==rebuildB==live)', 'live=' + liveHash + ' A=' + repA.hash + ' B=' + repB.hash);

  // ── the witnesses (§-log first; the only evidence) ──
  console.log('');
  console.log('§ODOO-FOLD chain so→picking→invoice→payment→reconcile mapped=' + mappedHops + '/5 missing=' + (5 - mappedHops));
  console.log('§ODOO-FOLD verbs used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');
  console.log('§ODOO-FOLD diff matched=' + (dShip.matched + dInv.matched + dGL.matched + dAlloc.matched) + ' missed=' + (dShip.missed + dInv.missed + dGL.missed + dAlloc.missed) + ' extra=' + (dShip.extra + dInv.extra + dGL.extra + dAlloc.extra) + ' vs odoo_oracle agree=' + (diffAgree ? 'Y' : 'N'));
  console.log('§ODOO-FOLD replay-hash stable rebuildA==rebuildB agree=' + (repA.hash === repB.hash ? 'Y' : 'N') + ' edgeMintOnReplay=' + K._stats.edgeMintCalls);
  // honest named findings (the boundary the fold reveals — refines §0.17/§0.19/§13.1, hides nothing)
  console.log('§ODOO-FINDINGS f1=account-determination-is-host-data(Odoo resolves 400000/251000/121000; POST verb owns only ΣDR==ΣCR, §13.1) ' +
              'f2=full-reconcile=FK-directed-ALLOCATE(matcher NOT invoked; partial/multi-reconcile would compose the matcher at one seam, §0.19) ' +
              'f3=delivery-move-split-aggregated-by-product(Odoo partial reservation → 2 move_lines; canonical qty reproduces) ' +
              'f4=invoice_policy=delivery-gates-invoicing(mirrors iDempiere DOCPOLICY decision-table)');

  db.close();
  console.log('\n═══ VERDICT ═══');
  var thesisHolds = newVerbs.length === 0 && diffAgree && mappedHops === 5;
  console.log('§ODOO-FOLD ' + (fails ? 'FAIL — ' + fails + ' checks red' :
    (thesisHolds ? 'PASS — Odoo O2C folds with the EXISTING verb set + a pure adapter; newVerbs=[]; effects reproduce Odoo to the cent; replay exact. Migration-solvent thesis HOLDS for Odoo.'
                 : 'BOUNDED — newVerbs=[' + newVerbs.join(',') + '] (a valid, reportable result; update HolyGrail scope)')));
  process.exit(fails ? 1 : 0);
})();
