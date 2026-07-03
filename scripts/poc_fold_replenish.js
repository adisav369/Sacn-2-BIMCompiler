// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-REPLENISH is ✅ DONE in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-REPLENISH — prove build/erp/report_replenish.foldReplenish reproduces the PLANNING core of
//   iDempiere's ReplenishReport (prepareTable + fillTable → the T_Replenish scratch table): the QtyToOrder
//   min/max planning + QtyOnHand/Reserved/Ordered, against an INDEPENDENT oracle. SPEC: docs/GapClosureSpec §5c.
// ORACLE (non-tautological, §3 option b): T_Replenish holds 0 captured rows → oracle = a HAND-WRITTEN SQL
//   re-derivation (CTEs) over the SAME base tables, replicating the prepareTable corrections + two-phase insert
//   + the QtyToOrder formula + min/pack + delete-<1, run in Postgres. The engine does the same in JS — two
//   independent paths over the same rows. Labelled "grounded on the base tables", NOT "== T_Replenish".
// NON-INVENT: every value READ from a real row. EXACT BigDecimal (compareTo, scale-insensitive). No Date.now/
//   random. Source-warehouse + custom-class branches verified no-op in this seed (asserted in-witness).
// §-log first — READ build/erp/poc_fold_replenish.log before concluding (exit code ≠ evidence).
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_replenish.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var R = require(path.join(ERP, 'report_replenish.js'));
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

console.log('=== §REPLENISH witness (W-REPLENISH) — T_Replenish planning fold vs independent SQL over base tables ===');
try { psql('SELECT 1'); } catch (e) { console.log('§REPLENISH-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§REPLENISH-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere)\n');

// ── 0. pick the warehouse under test + confirm seed branches are no-ops ───────────────────────────────────
var WH = +psql("SELECT m_warehouse_id FROM m_replenish WHERE isactive='Y' AND replenishtype<>'0' GROUP BY m_warehouse_id ORDER BY count(*) DESC LIMIT 1").trim();
var whMeta = rows("SELECT COALESCE(m_warehousesource_id,0) src, COALESCE(replenishmentclass,'') cls FROM m_warehouse WHERE m_warehouse_id=" + WH, ['src', 'cls'])[0];
console.log('§REPLENISH-WH warehouse=' + WH + ' source=' + whMeta.src + ' replenishClass="' + whMeta.cls + '"');
ok(whMeta.src === '0' && whMeta.cls === '', 'source-warehouse + custom-class branches are no-ops in this seed (fold targets the base planning path)');

// ── 1. ENGINE: pull RAW base tables, fold in JS ───────────────────────────────────────────────────────────
var src = {
  replenish: rows("SELECT m_warehouse_id, m_product_id, ad_client_id, ad_org_id, replenishtype, level_min, level_max, isactive FROM m_replenish",
    ['m_warehouse_id', 'm_product_id', 'ad_client_id', 'ad_org_id', 'replenishtype', 'level_min', 'level_max', 'isactive']),
  productPO: rows("SELECT m_product_id, c_bpartner_id, order_min, order_pack, iscurrentvendor, isactive FROM m_product_po",
    ['m_product_id', 'c_bpartner_id', 'order_min', 'order_pack', 'iscurrentvendor', 'isactive']),
  storage: rows("SELECT m_locator_id, m_product_id, qtyonhand FROM m_storageonhand", ['m_locator_id', 'm_product_id', 'qtyonhand']),
  locators: rows("SELECT m_locator_id, m_warehouse_id FROM m_locator", ['m_locator_id', 'm_warehouse_id']),
  reservation: rows("SELECT m_product_id, m_warehouse_id, qty, issotrx FROM m_storagereservation", ['m_product_id', 'm_warehouse_id', 'qty', 'issotrx']),
  products: rows("SELECT m_product_id, isactive FROM m_product", ['m_product_id', 'isactive'])
};
// fold the FULL pre-delete candidate set (keepZero) so the type-1 branch — whose QtyToOrder is legitimately 0
// when stock is sufficient — is itself oracle-diffed (not silently filtered away before comparison).
var folded = R.foldReplenish(src, WH, { keepZero: true });
var survivors = folded.filter(function (r) { return r.QtyToOrder.compareTo(BD.ONE) >= 0; });
console.log('§REPLENISH-FOLD base{replenish=' + src.replenish.length + ',productPO=' + src.productPO.length + ',storage=' + src.storage.length +
  '} → candidates=' + folded.length + ', survivors(QtyToOrder>=1)=' + survivors.length);

// ── 2. ORACLE: independent SQL re-derivation (CTEs) over the same base tables ─────────────────────────────
var ORACLE =
  "WITH po AS (SELECT m_product_id, c_bpartner_id, iscurrentvendor, " +
  "  CASE WHEN order_min IS NULL OR order_min<1 THEN 1 ELSE order_min END om, " +
  "  CASE WHEN order_pack IS NULL OR order_pack<1 THEN 1 ELSE order_pack END op, " +
  "  count(*) OVER (PARTITION BY m_product_id) ncnt, " +
  "  sum(CASE WHEN iscurrentvendor='Y' THEN 1 ELSE 0 END) OVER (PARTITION BY m_product_id) ncurr " +
  "  FROM m_product_po WHERE isactive='Y'), " +
  "poeff AS (SELECT *, CASE WHEN iscurrentvendor<>'Y' AND ncnt=1 THEN 'Y' " +
  "  WHEN iscurrentvendor='Y' AND ncurr>1 THEN 'N' ELSE iscurrentvendor END eff FROM po), " +
  "cand AS (" +
  "  SELECT r.m_warehouse_id, r.m_product_id, p.c_bpartner_id, p.om order_min, p.op order_pack, " +
  "    r.replenishtype, r.level_min, GREATEST(r.level_max, r.level_min) level_max " +
  "  FROM m_replenish r JOIN poeff p ON r.m_product_id=p.m_product_id AND p.eff='Y' " +
  "  WHERE r.isactive='Y' AND r.replenishtype<>'0' AND r.m_warehouse_id=" + WH +
  "  UNION ALL " +
  "  SELECT r.m_warehouse_id, r.m_product_id, 0, 1, 1, r.replenishtype, r.level_min, GREATEST(r.level_max,r.level_min) " +
  "  FROM m_replenish r WHERE r.isactive='Y' AND r.replenishtype<>'0' AND r.m_warehouse_id=" + WH +
  "    AND NOT EXISTS (SELECT 1 FROM poeff p WHERE p.m_product_id=r.m_product_id AND p.eff='Y')), " +
  "q AS (SELECT c.*, " +
  "  COALESCE((SELECT SUM(s.qtyonhand) FROM m_storageonhand s JOIN m_locator l ON l.m_locator_id=s.m_locator_id " +
  "    WHERE s.m_product_id=c.m_product_id AND l.m_warehouse_id=c.m_warehouse_id),0) qtyonhand, " +
  "  COALESCE((SELECT SUM(sr.qty) FROM m_storagereservation sr WHERE sr.m_product_id=c.m_product_id " +
  "    AND sr.m_warehouse_id=c.m_warehouse_id AND sr.issotrx='Y'),0) qtyreserved, " +
  "  COALESCE((SELECT SUM(sr.qty) FROM m_storagereservation sr WHERE sr.m_product_id=c.m_product_id " +
  "    AND sr.m_warehouse_id=c.m_warehouse_id AND sr.issotrx='N'),0) qtyordered " +
  "  FROM cand c WHERE NOT EXISTS (SELECT 1 FROM m_product mp WHERE mp.m_product_id=c.m_product_id AND mp.isactive='N') " +
  "    AND NOT EXISTS (SELECT 1 FROM m_replenish rr WHERE rr.m_product_id=c.m_product_id AND rr.isactive='N' AND rr.m_warehouse_id=" + WH + ")), " +
  "q1 AS (SELECT *, CASE replenishtype " +
  "    WHEN '1' THEN CASE WHEN qtyonhand-qtyreserved+qtyordered <= level_min THEN level_max-qtyonhand+qtyreserved-qtyordered ELSE 0 END " +
  "    WHEN '2' THEN level_max-qtyonhand+qtyreserved-qtyordered ELSE 0 END qraw FROM q), " +
  "q2 AS (SELECT *, CASE WHEN qraw>0 AND qraw<order_min THEN order_min ELSE qraw END qmin FROM q1), " +
  "q3 AS (SELECT *, CASE WHEN qmin>0 AND MOD(qmin,order_pack)<>0 THEN qmin-MOD(qmin,order_pack)+order_pack ELSE qmin END qto FROM q2) " +
  // full pre-delete candidate set (no qto>=1 filter) — the survivor filter is applied identically on both sides
  "SELECT m_product_id, c_bpartner_id, replenishtype, level_min, level_max, order_min, order_pack, " +
  "  qtyonhand, qtyreserved, qtyordered, qto FROM q3 ORDER BY m_product_id";
var oracle = rows(ORACLE, ['m_product_id', 'c_bpartner_id', 'replenishtype', 'level_min', 'level_max', 'order_min', 'order_pack', 'qtyonhand', 'qtyreserved', 'qtyordered', 'qto']);
console.log('§REPLENISH-ORACLE-SQL independent SQL re-derivation rows=' + oracle.length + ' (CTE port, distinct path from the JS fold)\n');
ok(oracle.length === folded.length, 'row count matches engine vs SQL oracle', 'engine=' + folded.length + ' sql=' + oracle.length);

// ── 3. DIFF — every planning field, EXACT (BigDecimal compareTo for numbers) ──────────────────────────────
var oByPid = {}; oracle.forEach(function (o) { oByPid[o.m_product_id] = o; });
var NUM = { Level_Min: 'level_min', Level_Max: 'level_max', Order_Min: 'order_min', Order_Pack: 'order_pack', QtyOnHand: 'qtyonhand', QtyReserved: 'qtyreserved', QtyOrdered: 'qtyordered', QtyToOrder: 'qto' };
var mismatches = 0, checked = 0, qtoNonZero = 0;
folded.forEach(function (r) {
  var o = oByPid[r.M_Product_ID];
  if (!o) { mismatches++; console.log('   ⚠ FINDING no oracle row for product ' + r.M_Product_ID); return; }
  checked++;
  if (+r.C_BPartner_ID !== +o.c_bpartner_id) { mismatches++; console.log('   ⚠ FINDING C_BPartner product=' + r.M_Product_ID + ' engine=' + r.C_BPartner_ID + ' oracle=' + o.c_bpartner_id); }
  if (r.ReplenishType !== o.replenishtype) { mismatches++; console.log('   ⚠ FINDING ReplenishType product=' + r.M_Product_ID + ' engine=' + r.ReplenishType + ' oracle=' + o.replenishtype); }
  Object.keys(NUM).forEach(function (k) {
    if (r[k].compareTo(BD.of(o[NUM[k]])) !== 0) { mismatches++; console.log('   ⚠ FINDING ' + k + ' product=' + r.M_Product_ID + ' engine=' + r[k].toString() + ' oracle=' + o[NUM[k]]); }
  });
  if (r.QtyToOrder.gt(BD.ZERO)) qtoNonZero++;
});
console.log('§REPLENISH surface=report_replenish candidates=' + checked + ' fields=10 mismatches=' + mismatches + ' oracle=base-table-SQL');
ok(mismatches === 0, 'foldReplenish == independent SQL re-derivation, EXACT (incl. QtyToOrder min/max+pack)', 'candidates=' + checked + ' mismatches=' + mismatches);
ok(qtoNonZero > 0, 'planning produced real QtyToOrder values (diff is over real numbers)', 'qtoNonZero=' + qtoNonZero);
// the survivor (delete <1) filter must agree too — this is the actual T_Replenish row set
var oSurv = oracle.filter(function (o) { return BD.of(o.qto).compareTo(BD.ONE) >= 0; }).length;
ok(survivors.length === oSurv, 'post-delete survivor set (the real T_Replenish) matches the oracle', 'engine=' + survivors.length + ' oracle=' + oSurv);
// both planning branches must be exercised in the diffed (pre-delete) set, else one branch is untested
var types = {}; folded.forEach(function (r) { types[r.ReplenishType] = (types[r.ReplenishType] || 0) + 1; });
console.log('§REPLENISH replenish-types diffed (pre-delete): ' + JSON.stringify(types) + ' — type-1 rows compute to QtyToOrder=0 (stock sufficient) and are oracle-confirmed before the delete');
ok((types['1'] || 0) > 0 && (types['2'] || 0) > 0, 'both ReorderBelowMin(1) and MaintainMax(2) branches are exercised & diffed', JSON.stringify(types));

// ── 4. §FALSIFIER — bend ONE product's Level_Max → its QtyToOrder must diverge from the oracle ────────────
console.log('\n── §FALSIFIER — bending one product\'s Level_Max must break the QtyToOrder diff ──');
(function () {
  var probe = survivors[0];   // a row with non-zero QtyToOrder, so bending Level_Max provably moves it
  if (!probe) { ok(false, 'no surviving row to perturb'); return; }
  var bent = R.foldReplenish(src, WH, { keepZero: true, bendLevelMaxProduct: { product: probe.M_Product_ID, delta: '1000' } });
  var br = bent.filter(function (r) { return r.M_Product_ID === probe.M_Product_ID; })[0];
  var o = oByPid[probe.M_Product_ID];
  var diffNow = br && br.QtyToOrder.compareTo(BD.of(o.qto)) !== 0;
  console.log('§REPLENISH-FALSIFIER bent product=' + probe.M_Product_ID + ' Level_Max+1000 → QtyToOrder ' + o.qto + ' → ' + (br ? br.QtyToOrder.toString() : '(deleted)'));
  ok(diffNow, 'perturbing Level_Max breaks the QtyToOrder diff (the metric is load-bearing, not vacuous)');
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-REPLENISH NOT proven' : '✅ ALL CHECKS PASS — foldReplenish planning core ORACLE-EQUIVALENT (grounded on base tables) — W-REPLENISH'));
process.exit(fails ? 1 : 0);
