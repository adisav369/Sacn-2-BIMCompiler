#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_movement_fx.js — W-FOLD-MOVEMENT-FX (FOLD_MODEL_LOGIC.md handoff-2026-06-10 NEXT#3 — the inter-org M_Movement
//   posting in GardenWorld's SECOND acctschema, 200000 = EUR, ccy 102).
//
// SPEC (Doc_Movement.java): identical to W-FOLD-MOVEMENT (poc_movement.js) — DR/CR {Product.Asset} (TO-org in /
//   FROM-org out) + the Intercompany Due-From/Due-To bridge — but at the SCHEMA-200000 cost. Unlike the allocation
//   FX fold (poc_alloc_fx.js), a movement does NOT convert a source amount: m_cost stores a SEPARATE per-schema cost
//   (schema 200000 = EUR 43.7325 for product 123, vs schema 101's USD 51.45), so the posting is round(qty × that
//   schema's cost) with NO currency-conversion step. The cost is carried at FULL 4-decimal precision and the LINE
//   amount rounded (4 × 43.7325 = 174.93, not round(43.73) × 4 = 174.92 — the W-FOLD-MATCHINV rounding rule; schema
//   101's 2dp cost masked this, schema 200000's 4dp cost exposes it).
//
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents == real fact_acct(323) schema 200000, maxDiff=0c.
//
// NON-INVENT: movement lines, the schema-200000 cost, and account config are real GardenWorld rows (glassbowl_data.db,
//   client 11); {Product.Asset} RESOLVED via post_resolver at schema 200000; intercompany accounts READ from
//   c_acctschema_gl; integer cents; no Date.now/Math.random. READ build/erp/poc_movement_fx.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md handoff NEXT#3 (M_Movement schema-200000) — Witness: W-FOLD-MOVEMENT-FX
// Run: node scripts/poc_movement_fx.js 2>&1 | tee build/erp/poc_movement_fx.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 200000;              // the SECOND acctschema (EUR) — same fold at this schema's per-schema cost
var AD_TABLE_M_MOVEMENT = 323;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

var sch = db.prepare('SELECT costingmethod,m_costtype_id,c_currency_id FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA);
var gl = db.prepare('SELECT intercompanydueto_acct,intercompanyduefrom_acct FROM c_acctschema_gl WHERE c_acctschema_id=?').get(SCHEMA);
var DUE_TO = (function () { var r = R.elementOf(db, gl.intercompanydueto_acct); return r ? r.id : null; })();
var DUE_FROM = (function () { var r = R.elementOf(db, gl.intercompanyduefrom_acct); return r ? r.id : null; })();

// cost selection: schema costing method → cost element (matching costingmethod) → m_cost.currentcostprice (schema-scoped).
//   Carry at FULL precision as milli-cents (cost × 10000); round the LINE amount, never the per-unit cost.
function costElementId() { var e = db.prepare('SELECT m_costelement_id FROM m_costelement WHERE costingmethod=?').get(sch.costingmethod); return e ? e.m_costelement_id : null; }
function costMilliOf(productId, costElem) {
  var c = db.prepare('SELECT currentcostprice FROM m_cost WHERE m_product_id=? AND c_acctschema_id=? AND m_costtype_id=? AND m_costelement_id=?')
    .get(sid(productId), SCHEMA, sch.m_costtype_id, costElem);
  return c ? Math.round(Number(c.currentcostprice) * 10000) : null;
}
function orgOfLocator(locId) {
  var r = db.prepare('SELECT w.ad_org_id AS org FROM m_locator l JOIN m_warehouse w ON w.m_warehouse_id=l.m_warehouse_id WHERE l.m_locator_id=?').get(sid(locId));
  return r ? r.org : null;
}

// ── DERIVE: the EUR-schema movement posting for one document (sum over its lines) ──
function deriveMovement(movId, opt) {
  opt = opt || {};
  var lines = db.prepare('SELECT * FROM m_movementline WHERE m_movement_id=? ORDER BY m_movementline_id').all(sid(movId));
  var agg = {}, absent = [], interOrgLines = 0;
  function add(side, acct, cnt) { if (cnt === 0 || acct == null) return; var k = key(acct, side); agg[k] = (agg[k] || 0) + cnt; }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  var ce = opt.costElem != null ? opt.costElem : costElementId();

  lines.forEach(function (ln) {
    var costMilli = costMilliOf(ln.m_product_id, ce);
    if (costMilli == null) { absent.push('m_cost product=' + ln.m_product_id + ' elem=' + ce); return; }
    var amt = Math.round(Number(ln.movementqty) * costMilli / 100);  // round(qty × cost) in cents, full precision
    var asset = nat(R.resolve(db, '{Product.Asset}', sid(ln.m_product_id), SCHEMA));
    var fromOrg = orgOfLocator(ln.m_locator_id), toOrg = orgOfLocator(ln.m_locatorto_id);
    add('DR', asset, amt);
    add('CR', asset, amt);
    if (fromOrg !== toOrg) {
      interOrgLines++;
      var dueFrom = opt.swapIntercompany ? DUE_TO : DUE_FROM;
      var dueTo = opt.swapIntercompany ? DUE_FROM : DUE_TO;
      add('DR', dueFrom, amt);
      add('CR', dueTo, amt);
    }
  });
  return { agg: agg, absent: absent, lineCount: lines.length, interOrgLines: interOrgLines };
}

function oracleMovement(movId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_M_MOVEMENT, sid(movId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = (agg[key(r.account_id, 'DR')] || 0) + cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = (agg[key(r.account_id, 'CR')] || 0) + cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-MOVEMENT-FX — inter-org M_Movement GL in the EUR schema (200000) == iDempiere oracle (cents) ═══');
console.log('    derive = {Product.Asset} + Intercompany Due-To/From at the schema-200000 cost (NO ccy conversion — m_cost is per-schema) · oracle = real fact_acct(323) · schema=' + SCHEMA);
console.log('    schema ccy=' + sch.c_currency_id + ' · costingmethod=' + sch.costingmethod + ' → costElem=' + costElementId() + ' · dueTo=' + DUE_TO + ' dueFrom=' + DUE_FROM + '\n');

var docs = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_M_MOVEMENT, SCHEMA).map(function (r) { return r.record_id; });
var equiv = 0;
docs.forEach(function (id) {
  var d = deriveMovement(id), o = oracleMovement(id);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var ok = md === 0 && d.absent.length === 0 && accts > 0;
  if (ok) equiv++;
  verdict(ok, 'FX movement ' + id + ' → oracle-equivalent',
    'lines=' + d.lineCount + ' interOrg=' + d.interOrgLines + ' postings=' + accts + ' maxDiff=' + md + 'c' + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=M_Movement schema=' + SCHEMA + ' id=' + id + ' postings=' + accts + ' interOrg=' + d.interOrgLines + ' oracle=iDempiere maxDiff=' + md + 'c');
});

verdict(equiv === docs.length && docs.length > 0, equiv + '/' + docs.length + ' M_Movement EUR-schema postings ORACLE-EQUIVALENT to the cent (inter-org cost transfer at the schema-200000 cost)', 'equiv=' + equiv);

// ── §FALSIFIER-A: swap the Intercompany Due-To/From accounts → the (account,side) set diverges from the oracle ──
(function () {
  var id = docs[0]; var o = oracleMovement(id);
  var md = maxDiff(deriveMovement(id, { swapIntercompany: true }).agg, o);
  verdict(md > 0, '§FALSIFIER-A swap Intercompany Due-To/From on movement ' + id + ' → maxDiff≠0 (the intercompany bridge is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-A doc=' + id + ' mutation=swap-intercompany maxDiff=' + md + 'c (must be >0)');
})();

// ── §FALSIFIER-B: round the per-unit cost to cents BEFORE multiplying (43.73 × 4 = 174.92) → off by the 1c the
//    full-precision rule recovers. This is the exact bug schema 200000 exposed; proves the rounding point is load-bearing.
(function () {
  var id = docs[0]; var o = oracleMovement(id);
  var ce = costElementId();
  var lines = db.prepare('SELECT * FROM m_movementline WHERE m_movement_id=? ORDER BY m_movementline_id').all(sid(id));
  var agg = {}; function add(side, acct, cnt) { if (cnt === 0 || acct == null) return; var k = key(acct, side); agg[k] = (agg[k] || 0) + cnt; }
  lines.forEach(function (ln) {
    var costMilli = costMilliOf(ln.m_product_id, ce);
    var amtRoundFirst = Number(ln.movementqty) * Math.round(costMilli / 100);   // cents-first (wrong)
    var asset = (function () { var r = R.resolve(db, '{Product.Asset}', sid(ln.m_product_id), SCHEMA); return r.element ? r.element.id : null; })();
    add('DR', asset, amtRoundFirst); add('CR', asset, amtRoundFirst);
    var fromOrg = orgOfLocator(ln.m_locator_id), toOrg = orgOfLocator(ln.m_locatorto_id);
    if (fromOrg !== toOrg) { add('DR', DUE_FROM, amtRoundFirst); add('CR', DUE_TO, amtRoundFirst); }
  });
  var md = maxDiff(agg, o);
  verdict(md > 0, '§FALSIFIER-B round per-unit cost to cents BEFORE ×qty (43.73×4=174.92) → maxDiff≠0 (full-precision line rounding is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-B doc=' + id + ' mutation=round-cost-first maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n§MOVE_FX_NOTE the schema-200000 EUR cost (43.7325) comes from m_cost\'s per-schema row — a movement posts ' +
  'at the schema cost directly, NOT a currency conversion of the schema-101 amount (contrast Doc_AllocationHdr, ' +
  'poc_alloc_fx.js, which converts a source amount). The full-precision line rounding (matchinv rule) is what makes ' +
  'the 4dp EUR cost fold to the cent — round-cost-first loses 1c (see §FALSIFIER-B).');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-MOVEMENT-FX PASS' : '🔴 W-FOLD-MOVEMENT-FX FAIL (' + fails + ')') +
  ' — EUR-schema inter-org M_Movement posting (per-schema cost transfer + intercompany bridge) oracle-equivalent to the cent.');
db.close();
process.exit(fails === 0 ? 0 : 1);
