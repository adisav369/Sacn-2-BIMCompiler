// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * poc_idmp_frame_fit.js — WITNESS for MIGRATE_FULL_MODEL_FRAME.md (iDempiere Client 13).
 *
 * iDempiere is the SAME AD schema as the engine, so "migration" = EXTRACT GardenWorld via the existing
 * install routine (scripts/migrate_pg_to_sqlite.js, run against the `idempiere_test` PG) + RE-KEY client 11→13.
 * The migrated tenant (build/erp/13-idempiere.db) must then fit the frame: the FROZEN engine
 * (doc_poster→post_resolver, untouched) resolves its accounts == iDempiere's OWN posted fact_acct to the cent.
 *
 *   §FRAME-FIT  client=13 — derivePostings(C_Order behind a posted invoice) == fact_acct (the tenant's own GL),
 *               coverage:complete, balanced, maxDiff=0c → ORACLE-EQUIVALENT.
 *   §FALSIFIER  — drop the receivable config row → {BPartner.Receivable} goes absent → coverage drops
 *               (the migrated config is load-bearing). On a THROWAWAY copy — never mutates the artifact.
 *
 * NON-INVENT: every row a recorded iDempiere row; the oracle is iDempiere's real fact_acct in the same db.
 * Run:  node scripts/poc_idmp_frame_fit.js 2>&1 | tee build/erp/poc_idmp_frame_fit.log
 */
var fs = require('fs'), path = require('path'), os = require('os');
var Database = require('better-sqlite3');
var DocPoster = require('./doc_poster');
var R = require('./post_resolver');

var DB = process.env.TENANT_DB || path.join(__dirname, '..', 'build', 'erp', '13-idempiere.db');
var SCHEMA = 101;                          // GardenWorld acctschema = the frozen consumer default
// Tenant ids are RE-BANDED into the client band since IDMP_FULLWIDTH_SEED §4 (gen_ad_idmp.sh 2b,
// the Odoo CL*100000 pattern) — invoice 100 is now 100 + CL*100000 (default 1300100).
var CL = Number(process.env.CLIENT_ID || 13);
var INVOICE_ID = 100 + CL * 100000;        // a posted sales invoice with fact_acct (idempiere_test oracle)
var log = []; function L(m) { console.log(m); log.push(m); }
function cents(n) { return Math.round(Number(n || 0) * 100); }

(function () {
  L('\n══ POC-IDMP-FRAME-FIT — frozen engine on the MIGRATED iDempiere tenant (' + path.basename(DB) + ', Client 13) ══\n');
  if (!fs.existsSync(DB)) { L('§FRAME-FIT FAIL — tenant db missing'); process.exit(1); }
  var db = new Database(DB, { readonly: true });

  // the order behind the posted invoice → derive via the ORACLE path (C_Order → its generated invoice).
  var orderId = db.prepare('SELECT c_order_id FROM c_invoice WHERE c_invoice_id=?').get(INVOICE_ID).c_order_id;
  var res = DocPoster.derivePostings(db, { table: 'C_Order', id: orderId }, SCHEMA, R);
  var coverage = res.absent.length ? 'partial' : 'complete';
  L('   migrated tenant: AD_Client=' + db.prepare('SELECT value FROM ad_client WHERE ad_client_id=' + CL).get().value +
    '(' + CL + ')  doc=C_Order id=' + orderId + ' → invoice ' + INVOICE_ID + ' (basis=' + res.basis + ')');
  L('   derived journal:');
  res.lines.forEach(function (l) { L('     acct ' + l.account_id + ' ' + (l.value || '') + ' ' + (l.name || '') + '  DR ' + l.amtacctdr.toFixed(2) + '  CR ' + l.amtacctcr.toFixed(2)); });

  // ── ORACLE: iDempiere's OWN posted fact_acct for this invoice (ad_table_id=318), aggregated by account. ──
  var facts = db.prepare('SELECT account_id, round(amtacctdr,2) AS dr, round(amtacctcr,2) AS cr FROM fact_acct WHERE ad_table_id=318 AND record_id=? AND c_acctschema_id=?').all(INVOICE_ID, SCHEMA);
  var oracle = {}; facts.forEach(function (f) { if (!oracle[f.account_id]) oracle[f.account_id] = { dr: 0, cr: 0 }; oracle[f.account_id].dr += cents(f.dr); oracle[f.account_id].cr += cents(f.cr); });
  var derived = {}; res.lines.forEach(function (l) { derived[l.account_id] = { dr: cents(l.amtacctdr), cr: cents(l.amtacctcr) }; });
  var maxDiff = 0, keys = {};
  Object.keys(oracle).forEach(function (k) { keys[k] = 1; }); Object.keys(derived).forEach(function (k) { keys[k] = 1; });
  Object.keys(keys).forEach(function (k) {
    var d = derived[k] || { dr: 0, cr: 0 }, o = oracle[k] || { dr: 0, cr: 0 };
    maxDiff = Math.max(maxDiff, Math.abs(d.dr - o.dr), Math.abs(d.cr - o.cr));
  });
  var fit = (coverage === 'complete' && res.balanced && maxDiff === 0 && facts.length > 0);
  L('§FRAME-FIT client=13 doc=C_Order postingDoc=true coverage=' + coverage + ' balanced=' + (res.balanced ? 'Y' : 'N') +
    ' oracle=fact_acct(' + facts.length + ') maxDiff=' + maxDiff + 'c verdict=' + (fit ? 'ORACLE-EQUIVALENT' : 'DRIFT'));

  // ── §FALSIFIER on a throwaway copy (never mutate the artifact). ──
  var tmp = path.join(os.tmpdir(), 'idmp_frame_falsify.db'); fs.copyFileSync(DB, tmp);
  var fdb = new Database(tmp, { readonly: false });
  var bp = fdb.prepare('SELECT c_bpartner_id FROM c_invoice WHERE c_invoice_id=?').get(INVOICE_ID).c_bpartner_id;
  fdb.prepare('DELETE FROM c_bp_customer_acct WHERE c_bpartner_id=? AND c_acctschema_id=?').run(bp, SCHEMA);
  var res2 = DocPoster.derivePostings(fdb, { table: 'C_Order', id: orderId }, SCHEMA, R);
  fdb.close(); fs.unlinkSync(tmp);
  var falsified = res2.absent.indexOf('{BPartner.Receivable}') >= 0 && !res2.balanced;
  L('§FALSIFIER dropped c_bp_customer_acct(bp=' + bp + ') → absent=[' + res2.absent.join(',') + '] balanced=' + (res2.balanced ? 'Y' : 'N') + ' loadBearing=' + (falsified ? 'Y' : 'N'));

  var pass = fit && falsified;
  L('\n§IDMP-FRAME-FIT ' + (pass ? 'PASS' : 'FAIL') + '\n');
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_idmp_frame_fit.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})();
