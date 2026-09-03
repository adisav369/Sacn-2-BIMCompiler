// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * poc_idmp_reband.js — W-IDMP-REBAND (prompts/IDMP_FULLWIDTH_SEED.md §4 / NEW_CLIENT_MGMT #5-install).
 *
 * ⚠ DO NOT REMOVE — Scope guard. READ THE LOG after every run — exit code is not evidence.
 * ISSUE this witness proves/disproves: gen_ad_idmp.sh re-keyed only AD_Client_ID 11→13 and left
 * GardenWorld's PRIMARY KEYS unchanged, so installing the shard over ad_seed.db (which IS GardenWorld)
 * collided every tenant PK → INSERT OR IGNORE silently DROPPED the tenant rows (client-13 orders
 * landed = 0) while PK-less tax junctions DUPLICATED on re-install → GL doubled. The §4 fix re-bands
 * every tenant-owned id family (+ identity families ad_org/ad_role/ad_user, + fact_acct dims) into
 * the CL*100000 client band, and the FULL-WIDTH seed now carries REAL PG primary keys.
 *
 *   §A  install over the full-width seed → 0 PK collisions: client-13 landed counts == shard counts
 *       (fact_acct named-skipped: the base carries no fact_acct table — installShard skips by design).
 *   §B  re-install → tax junctions NOT doubled (substrate dedup: real composite PKs, no guard needed).
 *   §C  books: merged-db client-13 invoice folds coverage:complete + balanced == the shard's OWN
 *       fact_acct to the cent; GardenWorld invoice 100 still folds in the same merged db (no
 *       cross-contamination); shard fact_acct TB balances (ΣDR==ΣCR) — the tenant's books are whole.
 *   §D  referential: every offset FK family resolves in the merged db (0 dangles).
 *   §E  login-able: client 13 reachable via an active user with an active role (the identity-family
 *       offset is what makes this true — un-banded roles/users were silently dropped).
 *   §F  FALSIFIER: the UN-banded shard (REBAND=0 fixture) reproduces the original bug — client-13
 *       orders land 0 over the same seed.
 *
 * Install mirror = idempiere.html installShard: column-INTERSECT (case-insensitive), INSERT OR IGNORE,
 * skip tables the base doesn't carry. NON-INVENT: throwaway copies only, artifacts never mutated.
 * Run: bash build/erp/run_witness.sh scripts/poc_idmp_reband.js
 */
var fs = require('fs'), path = require('path'), os = require('os');
var Database = require('better-sqlite3');
var DocPoster = require('./doc_poster');
var R = require('./post_resolver');

var SEED = process.env.SEED_DB || path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db');
var SHARD = process.env.TENANT_SHARD || path.join(__dirname, '..', 'build', 'erp', '13-idempiere.db');
var UNBANDED = path.join(__dirname, '..', 'build', 'erp', '13-idempiere-unbanded.db');
var CL = Number(process.env.CLIENT_ID || 13), OFF = CL * 100000, SCHEMA = 101;
var pass = true;
function L(m) { console.log(m); }
function fail(m) { pass = false; L('  ✗ ' + m); }
function cents(n) { return Math.round(Number(n || 0) * 100); }

// ── the installShard mirror (idempiere.html:449-506, node-side) ─────────────────────────────
function installShard(base, shardFile) {
  var sdb = new Database(shardFile, { readonly: true });
  var tbls = sdb.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\\_%' ESCAPE '\\'").all();
  var mTbl = 0, mRow = 0, skipped = [];
  tbls.forEach(function (tv) {
    var t = tv.name;
    var rows = sdb.prepare('SELECT * FROM "' + t + '"').all();
    if (!rows.length) return;
    var bi; try { bi = base.prepare('PRAGMA table_info("' + t + '")').all(); } catch (e) { bi = []; }
    if (!bi.length) { skipped.push(t); return; }                       // base doesn't carry it
    var baseCols = {}; bi.forEach(function (c) { baseCols[String(c.name).toLowerCase()] = true; });
    var cols = Object.keys(rows[0]).filter(function (c) { return baseCols[c.toLowerCase()]; });
    if (!cols.length) return;
    var ins = base.prepare('INSERT OR IGNORE INTO "' + t + '" (' + cols.map(function (c) { return '"' + c + '"'; }).join(',') +
      ') VALUES (' + cols.map(function () { return '?'; }).join(',') + ')');
    rows.forEach(function (r) { mRow += ins.run(cols.map(function (c) { return r[c]; })).changes; });
    mTbl++;
  });
  sdb.close();
  return { mTbl: mTbl, mRow: mRow, skipped: skipped };
}
function clientCount(db, t, cl) {
  try { return db.prepare('SELECT count(*) c FROM "' + t + '" WHERE ad_client_id=?').get(cl).c; } catch (e) { return null; }
}

(function () {
  L('\n══ W-IDMP-REBAND — re-banded iDempiere shard installs beside GardenWorld (' + path.basename(SEED) + ') ══\n');
  if (!fs.existsSync(SEED) || !fs.existsSync(SHARD)) { L('🔴 missing seed/shard'); process.exit(1); }
  var tmp = path.join(os.tmpdir(), 'reband_install.db'); fs.copyFileSync(SEED, tmp);
  var db = new Database(tmp);
  var shard = new Database(SHARD, { readonly: true });

  // §A — every shard row lands (0 PK collisions): per-table client-13 counts, shard vs merged.
  var r1 = installShard(db, SHARD);
  L('§A shard-in tables=' + r1.mTbl + ' rows=' + r1.mRow + ' base-skips=[' + r1.skipped.join(',') + '] (fact_acct skip = by design, base carries no fact table)');
  // SHARED-CONFIG exception (§-named, not hidden): c_acctschema_default keys on c_acctschema_id
  // (101/102) — the acctschema is NOT tenant-banded (c_acctschema itself isn't in the shard; the
  // tenant deliberately reuses GardenWorld's schema 101, exactly like fact_acct.c_acctschema_id).
  // The seed already carries the SAME GardenWorld default rows → the tenant copies are EXPECTED to
  // IGNORE-drop; the engine read-set proven by §C never needs the tenant copies in the merged db.
  var SHARED_CONFIG = ['c_acctschema_default'];
  var stbls = shard.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\\_%' ESCAPE '\\'").all();
  var landedAll = true;
  stbls.forEach(function (tv) {
    var t = tv.name;
    if (r1.skipped.indexOf(t) >= 0) return;
    var want = clientCount(shard, t, CL), got = clientCount(db, t, CL);
    if (want === null || want === 0) return;
    if (SHARED_CONFIG.indexOf(t) >= 0) {
      var cfg = db.prepare('SELECT count(*) c FROM ' + t).get().c;
      L('  §A ' + t + ' SHARED-CONFIG expected-drop landed=' + got + '/' + want + ' seed-config-rows=' + cfg);
      if (cfg < want) fail('§A shared-config ' + t + ' absent from seed too');
      return;
    }
    var ok = got === want; if (!ok) { landedAll = false; fail('§A ' + t + ' landed ' + got + '/' + want); }
    L('  §A ' + t + ' client' + CL + ' landed=' + got + '/' + want + (ok ? ' ✓' : ' ✗'));
  });
  if (landedAll) L('§A PASS — 0 PK collisions, every client-' + CL + ' row landed (shared-config drop §-named)');

  // §B — re-install: tax junctions must NOT double (real composite PKs in the full-width seed dedup).
  var taxBefore = { ot: clientCount(db, 'c_ordertax', CL), it: clientCount(db, 'c_invoicetax', CL) };
  var r2 = installShard(db, SHARD);
  var taxAfter = { ot: clientCount(db, 'c_ordertax', CL), it: clientCount(db, 'c_invoicetax', CL) };
  L('§B re-install rows=' + r2.mRow + ' c_ordertax ' + taxBefore.ot + '→' + taxAfter.ot +
    ' c_invoicetax ' + taxBefore.it + '→' + taxAfter.it);
  if (taxAfter.ot !== taxBefore.ot || taxAfter.it !== taxBefore.it) fail('§B tax junction DOUBLED on re-install');
  else L('§B PASS — substrate dedup holds (PK-carrying seed), re-install is a no-op');

  // §C — books: client-13 invoice in the MERGED db == the shard's OWN fact_acct, to the cent.
  // AS-alias the read: the merged table is the seed's canonical-case DDL and SQLite returns the
  // DECLARED case for a bare column reference (the v642 lesson) — `.c_order_id` on an unaliased
  // SELECT comes back undefined.
  var INV = 100 + OFF;
  var orderId = db.prepare('SELECT c_order_id AS c_order_id FROM c_invoice WHERE c_invoice_id=?').get(INV).c_order_id;
  var res = DocPoster.derivePostings(db, { table: 'C_Order', id: orderId }, SCHEMA, R);
  var facts = shard.prepare('SELECT account_id, round(amtacctdr,2) dr, round(amtacctcr,2) cr FROM fact_acct WHERE ad_table_id=318 AND record_id=? AND c_acctschema_id=?').all(INV, SCHEMA);
  var oracle = {}; facts.forEach(function (f) { if (!oracle[f.account_id]) oracle[f.account_id] = { dr: 0, cr: 0 }; oracle[f.account_id].dr += cents(f.dr); oracle[f.account_id].cr += cents(f.cr); });
  var derived = {}; res.lines.forEach(function (l) { derived[l.account_id] = { dr: cents(l.amtacctdr), cr: cents(l.amtacctcr) }; });
  var maxDiff = 0, keys = {};
  Object.keys(oracle).concat(Object.keys(derived)).forEach(function (k) { keys[k] = 1; });
  Object.keys(keys).forEach(function (k) {
    var d = derived[k] || { dr: 0, cr: 0 }, o = oracle[k] || { dr: 0, cr: 0 };
    maxDiff = Math.max(maxDiff, Math.abs(d.dr - o.dr), Math.abs(d.cr - o.cr));
  });
  var cov13 = res.absent.length ? 'partial(' + res.absent.join(',') + ')' : 'complete';
  L('§C client' + CL + ' invoice ' + INV + ' (merged db) coverage=' + cov13 + ' balanced=' + (res.balanced ? 'Y' : 'N') +
    ' oracle=fact_acct(' + facts.length + ') maxDiff=' + maxDiff + 'c');
  if (!(cov13 === 'complete' && res.balanced && maxDiff === 0 && facts.length > 0)) fail('§C client-' + CL + ' books drift in merged db');
  // GardenWorld arm = order-direct fold (v642's proven shape: order 100 → basis=invoice). The seed's
  // OWN c_invoice rows carry no c_order_id linkage (the `idempiere` PG differs from idempiere_test).
  var gw = DocPoster.derivePostings(db, { table: 'C_Order', id: 100 }, SCHEMA, R);
  L('§C GardenWorld order 100 (same merged db) basis=' + gw.basis + ' coverage=' + (gw.absent.length ? 'partial(' + gw.absent.join(',') + ')' : 'complete') +
    ' balanced=' + (gw.balanced ? 'Y' : 'N') + ' — no cross-contamination check');
  if (!gw.balanced) fail('§C GardenWorld fold broke after install');
  var tb = shard.prepare('SELECT round(sum(amtacctdr),2) dr, round(sum(amtacctcr),2) cr FROM fact_acct WHERE ad_client_id=?').get(CL);
  L('§C shard TB client' + CL + ' ΣDR=' + tb.dr + ' ΣCR=' + tb.cr + ' balanced=' + (cents(tb.dr) === cents(tb.cr) ? 'Y' : 'N'));
  if (cents(tb.dr) !== cents(tb.cr)) fail('§C tenant TB unbalanced');

  // §D — referential: every offset FK family resolves in the merged db.
  [['c_orderline', 'c_order_id', 'c_order', 'c_order_id'],
   ['c_invoiceline', 'c_invoice_id', 'c_invoice', 'c_invoice_id'],
   ['c_invoiceline', 'c_orderline_id', 'c_orderline', 'c_orderline_id'],
   ['c_order', 'c_bpartner_id', 'c_bpartner', 'c_bpartner_id'],
   ['c_orderline', 'm_product_id', 'm_product', 'm_product_id'],
   ['m_product', 'm_product_category_id', 'm_product_category', 'm_product_category_id'],
   ['c_invoicetax', 'c_tax_id', 'c_tax', 'c_tax_id'],
   ['c_validcombination', 'account_id', 'c_elementvalue', 'c_elementvalue_id'],
   ['c_bp_customer_acct', 'c_receivable_acct', 'c_validcombination', 'c_validcombination_id'],
   ['ad_user_roles', 'ad_role_id', 'ad_role', 'ad_role_id'],
   ['ad_user_roles', 'ad_user_id', 'ad_user', 'ad_user_id'],
   ['ad_role_orgaccess', 'ad_org_id', 'ad_org', 'ad_org_id']].forEach(function (s) {
    var dangle = db.prepare('SELECT count(*) c FROM ' + s[0] + ' WHERE ad_client_id=' + CL + ' AND ' + s[1] +
      ' IS NOT NULL AND ' + s[1] + ' NOT IN (SELECT ' + s[3] + ' FROM ' + s[2] + ')').get().c;
    if (dangle) fail('§D ' + s[0] + '.' + s[1] + ' dangles=' + dangle);
  });
  L('§D referential — 12 offset FK families checked, dangles only flagged above (none = silent pass)');

  // §E — login-able: an active client-13 user holds an active client-13 role (idmp_session shape).
  var login = db.prepare("SELECT count(*) c FROM ad_user_roles ur JOIN ad_user u ON u.ad_user_id=ur.ad_user_id " +
    "JOIN ad_role r ON r.ad_role_id=ur.ad_role_id WHERE ur.ad_client_id=? AND u.isactive='Y' AND r.isactive='Y'").get(CL).c;
  L('§E login-able client' + CL + ' active user+role grants=' + login);
  if (!login) fail('§E client ' + CL + ' has no login path (identity families not banded?)');

  // §F — FALSIFIER: the un-banded shard reproduces the original drop (orders land 0).
  if (fs.existsSync(UNBANDED)) {
    var tmp2 = path.join(os.tmpdir(), 'reband_falsify.db'); fs.copyFileSync(SEED, tmp2);
    var fdb = new Database(tmp2);
    installShard(fdb, UNBANDED);
    var fOrders = clientCount(fdb, 'c_order', CL);
    L('§F FALSIFIER un-banded shard → client' + CL + ' orders landed=' + fOrders + ' (the original #5-install bug, reproduced)');
    if (fOrders !== 0) fail('§F un-banded shard unexpectedly landed ' + fOrders + ' orders — falsifier dead');
    fdb.close(); fs.unlinkSync(tmp2);
  } else L('§F SKIP — no un-banded fixture (REBAND=0 run of gen_ad_idmp.sh produces it)');

  db.close(); shard.close(); fs.unlinkSync(tmp);
  L('\n§W-IDMP-REBAND ' + (pass ? 'PASS' : 'FAIL') + '\n');
  process.exit(pass ? 0 : 1);
})();
