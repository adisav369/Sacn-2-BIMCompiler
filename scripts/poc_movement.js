#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_movement.js — W-FOLD-MOVEMENT (FOLD_MODEL_LOGIC.md REMAINING-TAIL — the inter-org M_Movement posting).
//
// SPEC (Doc_Movement.java): a material movement between two locators posts at the product's CURRENT COST. When the
//   from-locator's org ≠ the to-locator's org (an INTER-ORG transfer), each org books its own side and the two are
//   bridged by the Intercompany Due-To/From accounts:
//     amt = round(movementqty × cost, 2)                                          (cost per the schema costing method)
//     DR {Product.Asset}            amt   — the TO-org's inventory increases
//     CR {Product.Asset}            amt   — the FROM-org's inventory decreases   (same natural acct → nets to 0)
//     DR {Schema.IntercompanyDueFrom} amt — the receiving org owes the shipping org
//     CR {Schema.IntercompanyDueTo}   amt
//   COST SELECTION (non-invent): c_acctschema.costingmethod ('A'=Average) → the m_costelement whose costingmethod
//     matches → m_cost.currentcostprice for (product, schema, costtype, costelement). Schema 101: 'A' → element 103
//     (Average PO) → product 123 = 51.45 → 4 × 51.45 = 205.80.
//
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents == real fact_acct(323) schema 101, maxDiff=0c.
//
// HONEST SCOPE: schema 101 (base) only — matching the W-FOLD-ALLOC / W-POST-HARDEN keystone-schema discipline.
//   Schema 200000 is the SAME fold with the schema-200000 cost (43.7325) — named-residual, not claimed here. The
//   seed has exactly ONE movement (inter-org); the same-org branch (no intercompany leg) is absent in seed → named.
//
// NON-INVENT: movement lines, cost, and account config are real GardenWorld rows (glassbowl_data.db, client 11);
//   {Product.Asset} RESOLVED via post_resolver; intercompany accounts READ from c_acctschema_gl; integer cents; no
//   Date.now/Math.random. READ build/erp/poc_movement.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md REMAINING-TAIL (M_Movement) — Witness: W-FOLD-MOVEMENT
// Run: node scripts/poc_movement.js 2>&1 | tee build/erp/poc_movement.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var AD_TABLE_M_MOVEMENT = 323;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

var sch = db.prepare('SELECT costingmethod,m_costtype_id FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA);
var gl = db.prepare('SELECT intercompanydueto_acct,intercompanyduefrom_acct FROM c_acctschema_gl WHERE c_acctschema_id=?').get(SCHEMA);
var DUE_TO = (function () { var r = R.elementOf(db, gl.intercompanydueto_acct); return r ? r.id : null; })();
var DUE_FROM = (function () { var r = R.elementOf(db, gl.intercompanyduefrom_acct); return r ? r.id : null; })();

// cost selection: schema costing method → cost element (matching costingmethod) → m_cost.currentcostprice (cents).
function costElementId() { var e = db.prepare('SELECT m_costelement_id FROM m_costelement WHERE costingmethod=?').get(sch.costingmethod); return e ? e.m_costelement_id : null; }
function costCentsOf(productId, costElem) {
  var c = db.prepare('SELECT currentcostprice FROM m_cost WHERE m_product_id=? AND c_acctschema_id=? AND m_costtype_id=? AND m_costelement_id=?')
    .get(sid(productId), SCHEMA, sch.m_costtype_id, costElem);
  return c ? cents(c.currentcostprice) : null;
}
function orgOfLocator(locId) {
  var r = db.prepare('SELECT w.ad_org_id AS org FROM m_locator l JOIN m_warehouse w ON w.m_warehouse_id=l.m_warehouse_id WHERE l.m_locator_id=?').get(sid(locId));
  return r ? r.org : null;
}

// ── DERIVE: the movement posting for one document (sum over its lines) ──
function deriveMovement(movId, opt) {
  opt = opt || {};
  var lines = db.prepare('SELECT * FROM m_movementline WHERE m_movement_id=? ORDER BY m_movementline_id').all(sid(movId));
  var agg = {}, absent = [], interOrgLines = 0;
  function add(side, acct, cnt) { if (cnt === 0 || acct == null) return; var k = key(acct, side); agg[k] = (agg[k] || 0) + cnt; }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  var ce = opt.costElem != null ? opt.costElem : costElementId();

  lines.forEach(function (ln) {
    var costCents = costCentsOf(ln.m_product_id, ce);
    if (costCents == null) { absent.push('m_cost product=' + ln.m_product_id + ' elem=' + ce); return; }
    var amt = Math.round(Number(ln.movementqty) * costCents);     // round(qty × cost) in cents
    var asset = nat(R.resolve(db, '{Product.Asset}', sid(ln.m_product_id), SCHEMA));
    var fromOrg = orgOfLocator(ln.m_locator_id), toOrg = orgOfLocator(ln.m_locatorto_id);
    // TO-org inventory in / FROM-org inventory out (same natural asset acct → nets, but both legs posted)
    add('DR', asset, amt);
    add('CR', asset, amt);
    if (fromOrg !== toOrg) {
      interOrgLines++;
      var dueFrom = opt.swapIntercompany ? DUE_TO : DUE_FROM;
      var dueTo = opt.swapIntercompany ? DUE_FROM : DUE_TO;
      add('DR', dueFrom, amt);   // receiving org owes
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

console.log('═══ W-FOLD-MOVEMENT — inter-org M_Movement GL derivation == iDempiere oracle (per-account, cents) ═══');
console.log('    derive = {Product.Asset} + Intercompany Due-To/From at current cost · oracle = real fact_acct(323) · schema=' + SCHEMA);
console.log('    costingmethod=' + sch.costingmethod + ' → costElem=' + costElementId() + ' · dueTo=' + DUE_TO + ' dueFrom=' + DUE_FROM + '\n');

var docs = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_M_MOVEMENT, SCHEMA).map(function (r) { return r.record_id; });
var equiv = 0;
docs.forEach(function (id) {
  var d = deriveMovement(id), o = oracleMovement(id);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var ok = md === 0 && d.absent.length === 0 && accts > 0;
  if (ok) equiv++;
  verdict(ok, 'movement ' + id + ' → oracle-equivalent',
    'lines=' + d.lineCount + ' interOrg=' + d.interOrgLines + ' postings=' + accts + ' maxDiff=' + md + 'c' + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=M_Movement id=' + id + ' postings=' + accts + ' interOrg=' + d.interOrgLines + ' oracle=iDempiere maxDiff=' + md + 'c');
});

verdict(equiv === docs.length && docs.length > 0, equiv + '/' + docs.length + ' M_Movement postings ORACLE-EQUIVALENT to the cent (inter-org cost transfer)', 'equiv=' + equiv);

// ── §FALSIFIER-A: swap the Intercompany Due-To/From accounts → the (account,side) set diverges from the oracle ──
(function () {
  var id = docs[0]; var o = oracleMovement(id);
  var md = maxDiff(deriveMovement(id, { swapIntercompany: true }).agg, o);
  verdict(md > 0, '§FALSIFIER-A swap Intercompany Due-To/From on movement ' + id + ' → maxDiff≠0 (the intercompany bridge is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-A doc=' + id + ' mutation=swap-intercompany maxDiff=' + md + 'c (must be >0)');
})();

// ── §FALSIFIER-B: pick the wrong cost element (Material 100 = 24.00, not Average 103 = 51.45) → amounts diverge ──
(function () {
  var id = docs[0]; var o = oracleMovement(id);
  var wrongElem = db.prepare("SELECT m_costelement_id FROM m_costelement WHERE costingmethod='S' AND costelementtype='M'").get();
  var md = wrongElem ? maxDiff(deriveMovement(id, { costElem: wrongElem.m_costelement_id }).agg, o) : 0;
  verdict(md > 0, '§FALSIFIER-B use the Material (Standard) cost element not the schema Average → maxDiff≠0 (cost selection is load-bearing)', 'maxDiff=' + md + 'c wrongElem=' + (wrongElem ? wrongElem.m_costelement_id : 'n/a'));
  console.log('§FALSIFIER-B doc=' + id + ' mutation=wrong-cost-element maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n§MOVE_NOTE schema 200000 = same fold, schema-200000 cost (43.7325) — named-residual. Same-org movement ' +
  '(no intercompany leg) absent in seed. The cost-selection rule (schema costing method → cost element → m_cost) is ' +
  'the SAME rule M_MatchInv (fact_acct 472) needs for its IPV/cost-adjustment split.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-MOVEMENT PASS' : '🔴 W-FOLD-MOVEMENT FAIL (' + fails + ')') +
  ' — inter-org M_Movement posting (cost transfer + intercompany bridge) oracle-equivalent to the cent.');
db.close();
process.exit(fails === 0 ? 0 : 1);
