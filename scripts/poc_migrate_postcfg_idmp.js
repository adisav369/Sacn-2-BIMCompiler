// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * poc_migrate_postcfg_idmp.js — W-MIGRATE-POSTCFG (prompts/MIGRATE_POSTING_CONFIG.md §WITNESS, iDempiere half).
 *
 * ⚠ DO NOT REMOVE — Scope guard. READ THE LOG after every run — exit code is not evidence.
 * ISSUE this witness proves/disproves: a migrated/regenerated tenant db rendered every GL view
 * `coverage:absent` because the extract pipeline did not carry the post_resolver read-set
 * (c_bp_customer_acct / m_product_category_acct / c_tax_acct / c_acctschema_default /
 * c_validcombination / c_elementvalue) nor the posted fact_acct oracle (TrialBalance honest-empty).
 * After the MIGRATE_POSTING_CONFIG regen (export_ad.sh postcfg block + gen_ad_idmp.sh fact_acct_id
 * band), BOTH regenerated dbs must resolve a real C_Order to coverage:complete + balanced, and the
 * derived journal must equal the db's OWN fact_acct to the cent.
 *
 *   §1  DEFAULT SEED (build/erp/ad_seed_postcfg.db, client 11): derivePostings on a real C_Order →
 *       §MIGRATE-POSTCFG tokens_resolved=N/N coverage=complete balanced=Y; diff vs the seed's OWN
 *       fact_acct(318) → maxDiff=0c; fact_acct TB ΣDR==ΣCR (the TrialBalance residual lit).
 *   §2  TENANT (build/erp/13-idempiere-postcfg.db, client 13, re-banded): same verb, same bar —
 *       the shard standalone carries its whole read-set + its own fact_acct oracle.
 *   §3  FALSIFIER: drop ONE emitted acct-config row (the order BP's c_bp_customer_acct rows in a
 *       throwaway copy) → {BPartner.Receivable} goes absent → coverage drops. Config is load-bearing.
 *
 * FROZEN consumers only: scripts/doc_poster.js + scripts/post_resolver.js (never forked — the token
 * count is taken by WRAPPING R.resolve, not re-deriving). Integer cents. NON-INVENT: every account
 * read from a real iDempiere column; throwaway copies only, staging artifacts never mutated.
 * Run: bash build/erp/run_witness.sh scripts/poc_migrate_postcfg_idmp.js
 */
var fs = require('fs'), path = require('path'), os = require('os');
var Database = require('better-sqlite3');
var DocPoster = require('./doc_poster');
var R = require('./post_resolver');

var SEED = process.env.SEED_DB || path.join(__dirname, '..', 'build', 'erp', 'ad_seed_postcfg.db');
var SHARD = process.env.TENANT_SHARD || path.join(__dirname, '..', 'build', 'erp', '13-idempiere-postcfg.db');
var SCHEMA = 101; // GardenWorld US acctschema — mirrors poc_idmp_reband.js; NOT re-banded (shared config)
var pass = true;
function L(m) { console.log(m); }
function fail(m) { pass = false; L('  ✗ ' + m); }
function cents(n) { return Math.round(Number(n || 0) * 100); }

// counting seam — wraps the FROZEN resolver so tokens_resolved=N/N is observed, never re-derived.
function countingR(base) {
  var n = { calls: 0, ok: 0 };
  return {
    n: n,
    resolve: function (db, token, id, schema) {
      n.calls++;
      var r = base.resolve(db, token, id, schema);
      if (r.acct != null && r.element) n.ok++;
      return r;
    },
    elementOf: base.elementOf,
    TOKENS: base.TOKENS
  };
}

// the first order whose generated invoice has posted fact_acct(318) rows — dynamic, EXTRACTED pick.
function pickOrder(db, client, schema) {
  var orders = db.prepare('SELECT c_order_id AS id FROM c_order WHERE ad_client_id=? ORDER BY c_order_id').all(client);
  for (var i = 0; i < orders.length; i++) {
    var invId = DocPoster.invoiceForOrder(db, orders[i].id);
    if (invId == null) continue;
    var facts = db.prepare('SELECT count(*) AS c FROM fact_acct WHERE ad_table_id=318 AND record_id=? AND c_acctschema_id=?').get(invId, schema).c;
    if (facts > 0) return { orderId: orders[i].id, invId: invId };
  }
  return null;
}

// derived journal vs the db's OWN fact_acct(318) for the invoice — to the cent, per account.
function diffOracle(db, res, invId, schema) {
  var facts = db.prepare('SELECT account_id AS account_id, round(amtacctdr,2) AS dr, round(amtacctcr,2) AS cr FROM fact_acct WHERE ad_table_id=318 AND record_id=? AND c_acctschema_id=?').all(invId, schema);
  var oracle = {}; facts.forEach(function (f) { if (!oracle[f.account_id]) oracle[f.account_id] = { dr: 0, cr: 0 }; oracle[f.account_id].dr += cents(f.dr); oracle[f.account_id].cr += cents(f.cr); });
  var derived = {}; res.lines.forEach(function (l) { derived[l.account_id] = { dr: cents(l.amtacctdr), cr: cents(l.amtacctcr) }; });
  var keys = {}, maxDiff = 0;
  Object.keys(oracle).concat(Object.keys(derived)).forEach(function (k) { keys[k] = 1; });
  Object.keys(keys).forEach(function (k) {
    var d = derived[k] || { dr: 0, cr: 0 }, o = oracle[k] || { dr: 0, cr: 0 };
    maxDiff = Math.max(maxDiff, Math.abs(d.dr - o.dr), Math.abs(d.cr - o.cr));
  });
  return { maxDiff: maxDiff, factRows: facts.length };
}

function arm(label, dbFile, client) {
  L('── ' + label + ' (' + path.basename(dbFile) + ', client ' + client + ') ──');
  if (!fs.existsSync(dbFile)) { fail(label + ' db missing: ' + dbFile); return null; }
  var db = new Database(dbFile, { readonly: true });

  var pick = pickOrder(db, client, SCHEMA);
  if (!pick) { fail(label + ' no order with a posted invoice oracle found'); db.close(); return null; }

  var CR = countingR(R);
  var res = DocPoster.derivePostings(db, { table: 'C_Order', id: pick.orderId }, SCHEMA, CR);
  var cov = res.absent.length ? 'partial(' + res.absent.join(',') + ')' : 'complete';
  L('§MIGRATE-POSTCFG client=' + client + ' doc=C_Order id=' + pick.orderId +
    ' tokens_resolved=' + CR.n.ok + '/' + CR.n.calls + ' coverage=' + cov +
    ' balanced=' + (res.balanced ? 'Y' : 'N') + ' basis=' + res.basis);
  if (!(cov === 'complete' && res.balanced && CR.n.ok === CR.n.calls && CR.n.calls > 0)) fail(label + ' posting config did not fully resolve');

  var d = diffOracle(db, res, pick.invId, SCHEMA);
  L('§MIGRATE-POSTCFG client=' + client + ' oracle=fact_acct(318) invoice=' + pick.invId +
    ' rows=' + d.factRows + ' maxDiff=' + d.maxDiff + 'c');
  if (!(d.maxDiff === 0 && d.factRows > 0)) fail(label + ' derived journal drifts from the db\'s own fact_acct');

  var tb = db.prepare('SELECT count(*) AS n, round(sum(amtacctdr),2) AS dr, round(sum(amtacctcr),2) AS cr FROM fact_acct WHERE ad_client_id=?').get(client);
  L('§MIGRATE-POSTCFG client=' + client + ' fact_acct rows=' + tb.n + ' ΣDR=' + tb.dr + ' ΣCR=' + tb.cr +
    ' TB-balanced=' + (cents(tb.dr) === cents(tb.cr) ? 'Y' : 'N') + ' (TrialBalance honest-empty residual lit)');
  if (!(tb.n > 0 && cents(tb.dr) === cents(tb.cr))) fail(label + ' fact_acct empty or unbalanced');

  db.close();
  return pick;
}

(function () {
  L('\n══ W-MIGRATE-POSTCFG — regenerated seed + Client-13 tenant carry RESOLVABLE posting config ══\n');

  // §1 — DEFAULT SEED arm
  var seedPick = arm('§1 SEED', SEED, 11);

  // §2 — TENANT arm (re-banded Client 13 shard, standalone)
  arm('§2 TENANT', SHARD, 13);

  // §3 — FALSIFIER: one dropped acct-config row collapses coverage (config is load-bearing).
  if (seedPick) {
    var tmp = path.join(os.tmpdir(), 'postcfg_falsify.db');
    fs.copyFileSync(SEED, tmp);
    var fdb = new Database(tmp);
    var bp = fdb.prepare('SELECT c_bpartner_id AS bp FROM c_order WHERE c_order_id=?').get(seedPick.orderId).bp;
    var del = fdb.prepare('DELETE FROM c_bp_customer_acct WHERE c_bpartner_id=?').run(bp).changes;
    var fres = DocPoster.derivePostings(fdb, { table: 'C_Order', id: seedPick.orderId }, SCHEMA, R);
    var fcov = fres.absent.length ? 'partial(' + fres.absent.join(',') + ')' : 'complete';
    L('§FALSIFIER dropped c_bp_customer_acct rows=' + del + ' (bp=' + bp + ') → coverage=' + fcov);
    if (!(del > 0 && fres.absent.indexOf('{BPartner.Receivable}') >= 0)) fail('§FALSIFIER coverage did NOT drop — config not load-bearing?');
    fdb.close(); fs.unlinkSync(tmp);
  } else fail('§FALSIFIER skipped — §1 arm produced no pick');

  L('\n§W-MIGRATE-POSTCFG ' + (pass ? 'PASS' : 'FAIL') + '\n');
  process.exit(pass ? 0 : 1);
})();
