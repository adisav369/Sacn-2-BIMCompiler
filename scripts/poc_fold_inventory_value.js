// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-INVVALUE is ✅ DONE in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-INVVALUE — prove build/erp/report_inventory_value.foldInventoryValue reproduces the COST-VALUATION
//   core of iDempiere's InventoryValue report (the T_InventoryValue scratch table): CostStandard, QtyOnHand,
//   CostStandardAmt, EXACT, against an INDEPENDENT oracle. SPEC: docs/GapClosureSpec.md §5b.
// ORACLE (non-tautological, GapClosureSpec §3 option (b)): T_InventoryValue holds 0 captured rows, so the
//   oracle is a HAND-WRITTEN SQL re-derivation over the SAME base tables (M_Cost ⋈ M_Warehouse ⋈ AD_ClientInfo
//   ⋈ C_AcctSchema ⋈ M_CostElement, QtyOnHand = SUM(M_StorageOnHand⋈M_Locator), CostStandardAmt = qty*cost),
//   run in Postgres. The engine does the SAME joins/aggregation/multiply in JS — two independent paths over the
//   same rows (the W-FOLD-QTYONHAND pattern). Labelled "grounded on the base tables", NOT "== T_InventoryValue".
// NON-INVENT: every value READ from a real row. EXACT BigDecimal product (compareTo, scale-insensitive). No
//   Date.now/random. DateValue = today ⇒ 0 future transactions over this historical seed (verified in-witness).
// §-log first — READ build/erp/poc_fold_inventory_value.log before concluding (exit code ≠ evidence).
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_inventory_value.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var IV = require(path.join(ERP, 'report_inventory_value.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var US = '|';
var fails = 0;
function ok(cond, msg, detail) { if (!cond) fails++; console.log('  ' + (cond ? '✓' : '✗ FAIL:') + ' ' + msg + (detail ? ' — ' + detail : '')); }
function psql(sql) {
  return cp.execFileSync('docker', ['exec', '-e', 'PGOPTIONS=-c search_path=adempiere', PG.container,
    'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
}
function rows(sql, cols) {
  return psql(sql).split('\n').filter(function (s) { return s.length; }).map(function (l) {
    var f = l.split(US), o = {}; cols.forEach(function (c, i) { o[c] = f[i]; }); return o;
  });
}

console.log('=== §INVVALUE witness (W-INVVALUE) — T_InventoryValue cost-valuation fold vs independent SQL over base tables ===');
try { psql('SELECT 1'); } catch (e) { console.log('§INVVALUE-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§INVVALUE-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere)\n');

// ── 0. valuation-date guard: confirm 0 future transactions (so QtyOnHand needs no adjustment) ─────────────
var nFuture = +psql("SELECT count(*) FROM m_transaction WHERE movementdate > now()").trim();
console.log('§INVVALUE-DATE DateValue=today; future-dated M_Transaction rows=' + nFuture + ' → adjustment ' + (nFuture === 0 ? 'is 0 (verified)' : 'APPLIES'));

// ── 1. ENGINE: pull RAW base tables (plain dumps), fold the joins in JS ───────────────────────────────────
var src = {
  warehouses: rows("SELECT m_warehouse_id, ad_client_id, ad_org_id, isactive FROM m_warehouse", ['m_warehouse_id', 'ad_client_id', 'ad_org_id', 'isactive']),
  clientInfo: rows("SELECT ad_client_id, c_acctschema1_id FROM ad_clientinfo", ['ad_client_id', 'c_acctschema1_id']),
  acctSchemas: rows("SELECT c_acctschema_id, m_costtype_id FROM c_acctschema", ['c_acctschema_id', 'm_costtype_id']),
  costs: rows("SELECT c_acctschema_id, m_costtype_id, ad_org_id, m_costelement_id, m_product_id, m_attributesetinstance_id, currentcostprice FROM m_cost",
    ['c_acctschema_id', 'm_costtype_id', 'ad_org_id', 'm_costelement_id', 'm_product_id', 'm_attributesetinstance_id', 'currentcostprice']),
  costElements: rows("SELECT m_costelement_id, costingmethod, costelementtype FROM m_costelement", ['m_costelement_id', 'costingmethod', 'costelementtype']),
  storage: rows("SELECT m_locator_id, m_product_id, m_attributesetinstance_id, qtyonhand FROM m_storageonhand", ['m_locator_id', 'm_product_id', 'm_attributesetinstance_id', 'qtyonhand']),
  locators: rows("SELECT m_locator_id, m_warehouse_id FROM m_locator", ['m_locator_id', 'm_warehouse_id']),
  futureTxn: []   // DateValue=today → none (guarded above); fold the empty adjustment honestly
};
// convert numeric id columns to numbers for sort/compare stability (values stay strings → BigDecimal)
['warehouses', 'clientInfo', 'acctSchemas', 'costs', 'costElements', 'storage', 'locators'].forEach(function (t) {
  src[t].forEach(function (r) { Object.keys(r).forEach(function (k) { if (/_id$/.test(k)) r[k] = +r[k]; }); });
});
var folded = IV.foldInventoryValue(src, {});
console.log('§INVVALUE-FOLD base{warehouses=' + src.warehouses.length + ',costs=' + src.costs.length + ',storage=' + src.storage.length + '} → surviving rows=' + folded.length);

// ── 2. ORACLE: independent SQL re-derivation over the same base tables ────────────────────────────────────
var ORACLE = "SELECT w.m_warehouse_id, c.m_product_id, c.m_attributesetinstance_id, c.currentcostprice, " +
  "qoh.q, (qoh.q * c.currentcostprice) amt " +
  "FROM m_warehouse w " +
  "JOIN ad_clientinfo ci ON w.ad_client_id=ci.ad_client_id " +
  "JOIN c_acctschema acs ON ci.c_acctschema1_id=acs.c_acctschema_id " +
  "JOIN m_cost c ON acs.c_acctschema_id=c.c_acctschema_id AND acs.m_costtype_id=c.m_costtype_id AND c.ad_org_id IN (0,w.ad_org_id) " +
  "JOIN m_costelement ce ON c.m_costelement_id=ce.m_costelement_id AND ce.costingmethod='S' AND ce.costelementtype='M' " +
  "JOIN LATERAL (SELECT SUM(s.qtyonhand) q FROM m_storageonhand s JOIN m_locator l ON l.m_locator_id=s.m_locator_id " +
    "WHERE s.m_product_id=c.m_product_id AND l.m_warehouse_id=w.m_warehouse_id) qoh ON TRUE " +
  "WHERE w.isactive='Y' AND qoh.q IS NOT NULL AND qoh.q<>0 " +
  "ORDER BY w.m_warehouse_id, c.m_product_id, c.m_attributesetinstance_id";
var oracle = rows(ORACLE, ['m_warehouse_id', 'm_product_id', 'm_attributesetinstance_id', 'currentcostprice', 'q', 'amt']);
console.log('§INVVALUE-ORACLE-SQL independent SQL re-derivation rows=' + oracle.length + ' (hand-coded joins, distinct path from the JS fold)\n');
ok(oracle.length === folded.length, 'row count matches engine vs SQL oracle', 'engine=' + folded.length + ' sql=' + oracle.length);

// ── 3. DIFF — CostStandard, QtyOnHand, CostStandardAmt, EXACT (BigDecimal compareTo) ──────────────────────
var oByKey = {};
oracle.forEach(function (o) { oByKey[o.m_warehouse_id + '|' + o.m_product_id + '|' + o.m_attributesetinstance_id] = o; });
var mismatches = 0, checked = 0, nonZeroAmt = 0;
folded.forEach(function (r) {
  var key = r.M_Warehouse_ID + '|' + r.M_Product_ID + '|' + r.M_AttributeSetInstance_ID;
  var o = oByKey[key];
  if (!o) { mismatches++; console.log('   ⚠ FINDING no oracle row for ' + key); return; }
  checked++;
  if (r.CostStandard.compareTo(BD.of(o.currentcostprice)) !== 0) { mismatches++; console.log('   ⚠ FINDING CostStandard ' + key + ' engine=' + r.CostStandard.toString() + ' oracle=' + o.currentcostprice); }
  if (r.QtyOnHand.compareTo(BD.of(o.q)) !== 0) { mismatches++; console.log('   ⚠ FINDING QtyOnHand ' + key + ' engine=' + r.QtyOnHand.toString() + ' oracle=' + o.q); }
  if (r.CostStandardAmt.compareTo(BD.of(o.amt)) !== 0) { mismatches++; console.log('   ⚠ FINDING CostStandardAmt ' + key + ' engine=' + r.CostStandardAmt.toString() + ' oracle=' + o.amt); }
  if (!r.CostStandardAmt.eq(BD.ZERO)) nonZeroAmt++;
});
console.log('§INVVALUE surface=report_inventory_value rows=' + checked + ' cols=3(CostStandard,QtyOnHand,CostStandardAmt) mismatches=' + mismatches + ' oracle=base-table-SQL');
ok(mismatches === 0, 'foldInventoryValue == independent SQL re-derivation, EXACT (cost, qty, value)', 'rows=' + checked + ' mismatches=' + mismatches);
ok(nonZeroAmt > 0, 'at least one row carries non-zero inventory value (diff is over real money, not all-zero)', 'nonZeroAmt=' + nonZeroAmt);

// ── 4. §FALSIFIER — perturb ONE product's CostStandard → CostStandardAmt diff MUST go non-zero ────────────
console.log('\n── §FALSIFIER — bending one product\'s cost must break the value diff ──');
(function () {
  // pick a real surviving row with non-zero qty so qty*delta != 0
  var probe = folded.filter(function (r) { return !r.QtyOnHand.eq(BD.ZERO); })[0];
  if (!probe) { ok(false, 'no non-zero-qty row to perturb'); return; }
  var bent = IV.foldInventoryValue(src, { bendCostProduct: { product: probe.M_Product_ID, delta: '1' } });
  var br = bent.filter(function (r) { return r.M_Warehouse_ID === probe.M_Warehouse_ID && r.M_Product_ID === probe.M_Product_ID; })[0];
  var o = oByKey[probe.M_Warehouse_ID + '|' + probe.M_Product_ID + '|' + probe.M_AttributeSetInstance_ID];
  var diffNow = br.CostStandardAmt.compareTo(BD.of(o.amt)) !== 0;
  var deltaExpected = probe.QtyOnHand.toString();   // qty * (+1) added to the value
  console.log('§INVVALUE-FALSIFIER bent product=' + probe.M_Product_ID + ' wh=' + probe.M_Warehouse_ID + ' cost+1 → amt ' + o.amt + ' → ' + br.CostStandardAmt.toString() + ' (expected delta=qty=' + deltaExpected + ')');
  ok(diffNow, 'perturbing CostStandard breaks the value diff (the metric is load-bearing, not vacuous)');
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-INVVALUE NOT proven' : '✅ ALL CHECKS PASS — foldInventoryValue cost-valuation core ORACLE-EQUIVALENT (grounded on base tables) — W-INVVALUE'));
process.exit(fails ? 1 : 0);
