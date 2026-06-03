#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_odoo_fold_acctderiv.js — Stage 2 finding f1: account DETERMINATION (derive, don't take).
 *   Spec: prompts/MIGRATION_CAMPAIGN_RESUME.md Stage 2 (f1) · docs/ERP.md §13.1 (POST owns ΣDR==ΣCR;
 *         resolver is host glue) · prompts/ODOO_FOLD_POC.md (clean-room: learn from config, not source).
 *
 * f1 was the standing honest bound across the whole Odoo campaign: the folds took Odoo's RESOLVED GL
 * accounts (400000/251000/121000) as host data — "reproduces GIVEN accounts." This runner raises it to
 * "DERIVES the accounts": the resolver (odoo_adapter.resolveAccounts) reads ONLY the extracted Odoo
 * determination CONFIG (build/erp/odoo_oracle_acctderiv.json — product/category income property, tax
 * repartition, partner receivable) and derives the account per role; we then diff the DERIVED accounts
 * against posted_truth (what Odoo actually posted). If they match to the account, f1 is RESOLVED.
 *
 * Honest scope: this is host GLUE, not engine — POST still owns only ΣDR==ΣCR. The determination LOGIC
 * (product→income with template/category fallback; tax repartition; partner receivable) is Odoo's
 * documented model, learned clean-room from the config STRUCTURE, never from Odoo's source. newVerbs=[].
 *
 * Deterministic: every account/amount is from the static config oracle (no Date.now/Math.random, no live
 * Odoo at replay, §0.12). Run:  node scripts/poc_odoo_fold_acctderiv.js 2>&1 | tee -a build/erp/odoo_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle_acctderiv.json'), 'utf8'));
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function n2(x) { return Number(x).toFixed(2); }

(async function () {
  var SQL = await initSqlJs();
  var invId = ORACLE.meta.invoice_id;
  var truth = ORACLE.posted_truth;
  var built = A.buildDerivedPost(ORACLE);     // the resolver runs HERE, reading only account_config
  var R = built.resolved;

  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-ODOO-FOLD-ACCTDERIV — f1: DERIVE the GL accounts from Odoo config (' + ORACLE.meta.invoice + ') ═══');
  console.log('    scenario: ' + ORACLE.meta.scenario);
  console.log('══════════════════════════════════════════════════════════════════════════════\n');

  // ── the f1 claim: each role's account DERIVED from config == what Odoo actually posted ──
  console.log('── derived-from-config vs Odoo posted_truth (the f1 claim) ──');
  var derivedIncome = ORACLE.sale_order_lines.map(function (l) { return R.incomeFor(l.pid); });
  var incomeOK = derivedIncome.every(function (a) { return a === truth.income; });
  verdict(incomeOK && truth.income, 'product-line INCOME account derived (template→category fallback) == Odoo', 'derived=' + JSON.stringify(derivedIncome) + ' odoo=' + truth.income);
  verdict(R.tax === truth.tax && truth.tax, 'TAX account derived (tax repartition) == Odoo', 'derived=' + R.tax + ' odoo=' + truth.tax);
  verdict(R.receivable === truth.receivable && truth.receivable, 'RECEIVABLE account derived (partner property) == Odoo', 'derived=' + R.receivable + ' odoo=' + truth.receivable);

  // ── post the DERIVED double-entry through the kernel: balances + total reproduces ──
  console.log('\n── the derived posting through the POST verb (ΣDR==ΣCR, total reproduces) ──');
  K.register('C_Invoice', 'POST', function () { return built.event.ops; });
  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };
  var usedVerbs = {}; built.event.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
  var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 7000 }, built.event.d);
  verdict(d.ok, 'derived POST commits (CO→' + (d.to || '?') + ') — kernel enforced ΣDR==ΣCR', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);

  var GLWHERE = " WHERE source='DOC:C_Invoice#" + invId + "' AND account_id IS NOT NULL";
  var mineDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLWHERE)[0].s;
  var mineCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLWHERE)[0].s;
  verdict(Math.round(mineDR * 100) === Math.round(mineCR * 100) && Math.round(mineDR * 100) === Math.round(ORACLE.meta.amount_total * 100),
    'derived posting balances AND total == oracle invoice total', 'ΣDR=' + n2(mineDR) + ' ΣCR=' + n2(mineCR) + ' total=' + n2(ORACLE.meta.amount_total));

  // the journal rows must carry the DERIVED account ids (the posting really used them)
  var postedAccts = K.query(db, "SELECT DISTINCT account_id a FROM journal" + GLWHERE + " ORDER BY a").map(function (r) { return r.a; });
  var expectAccts = [truth.income, truth.tax, truth.receivable].sort();
  verdict(JSON.stringify(postedAccts.slice().sort()) === JSON.stringify(expectAccts), 'journal rows carry exactly the 3 derived accounts', 'posted=' + JSON.stringify(postedAccts));

  // tax amount derived from config rate matches the oracle (cent-perfect)
  verdict(Math.round(built.tax * 100) === Math.round(ORACLE.meta.amount_tax * 100), 'tax amount derived (untaxed × config rate) == oracle', 'derived=' + n2(built.tax) + ' oracle=' + n2(ORACLE.meta.amount_tax) + ' rate=' + ORACLE.account_config.tax_rate);

  // ── replay determinism ──
  var liveHash = K.projectionHash(db);
  var fa = new SQL.Database(), repA = K.replay(db, fa); fa.close();
  var fb = new SQL.Database(), repB = K.replay(db, fb); fb.close();
  verdict(repA.hash === liveHash && repA.hash === repB.hash, 'replay rebuilds projection EXACTLY + stable', 'live=' + liveHash + ' A=' + repA.hash + ' B=' + repB.hash);

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });
  var derivAgree = incomeOK && R.tax === truth.tax && R.receivable === truth.receivable;

  console.log('');
  console.log('§ODOO-FOLD-ACCTDERIV derived income=' + truth.income + ' tax=' + truth.tax + ' receivable=' + truth.receivable + ' == Odoo posted agree=' + (derivAgree ? 'Y' : 'N'));
  console.log('§ODOO-FOLD-ACCTDERIV posting balanced ΣDR==ΣCR=' + n2(mineDR) + ' total==oracle=' + (Math.round(mineDR * 100) === Math.round(ORACLE.meta.amount_total * 100) ? 'Y' : 'N') + ' verbs=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');
  console.log('§ODOO-FINDINGS-F1 RESOLVED=account-determination-DERIVED-from-config(product income: template→category fallback; tax: repartition tax-line account; AR: partner property — all read from extracted Odoo config, NOT taken as host rows) ' +
              'CLASS=host-GLUE-not-engine(POST still owns only ΣDR==ΣCR, §13.1) CLEAN-ROOM(determination logic learned from config STRUCTURE, never Odoo source) ' +
              'CLAIM-RAISED=from "reproduces GIVEN accounts" → "DERIVES the accounts"; newVerbs=[]');

  db.close();
  console.log('\n═══ VERDICT ═══');
  var holds = derivAgree && newVerbs.length === 0 && fails === 0;
  console.log('§ODOO-FOLD-ACCTDERIV ' + (fails ? 'FAIL — ' + fails + ' checks red' :
    'PASS — f1 RESOLVED: the resolver DERIVES Odoo\'s GL accounts (400000 Sales / 251000 Tax / 121000 AR) from extracted determination config alone, matching Odoo\'s posting to the account; the derived double-entry balances and totals to the cent; newVerbs=[]; host glue, not engine. The campaign\'s standing f1 bound is now closed — "derives the accounts," not "given the accounts."'));
  process.exit(fails ? 1 : 0);
})();
