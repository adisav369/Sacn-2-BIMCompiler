// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-DISTRUN is recorded in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-DISTRUN — prove build/erp/report_distribution_run.foldDistributionRun reproduces the PLANNING core
//   of iDempiere's DistributionRun (→ the T_DistributionRunDetail scratch table): the ratio split and the
//   integer-allocation loop. SPEC: docs/GapClosureSpec §5e.
// TWO claims, deliberately distinct (see GapClosureSpec §5e + §3 gap taxonomy):
//   §A rawSplit (ll.Ratio/l.RatioTotal*rl.TotalQty) is ORACLE-EQUIVALENT — diffed against iDempiere's OWN
//      insertDetails INSERT…SELECT formula, run as a SELECT on the live idempiere_test → maxDiff=0.
//   §B the allocation loop (round to UOM precision → force ΣActualAllocation==TotalQty under MinQty floors) is
//      RULE-CONSISTENT only: it is a procedural loop with NO SQL/function oracle in the live DB (a true row
//      oracle would require RUNNING the process via the app server, unavailable here). So §B proves the loop's
//      INVARIANTS (sum-exact, MinQty respected, deterministic) — NOT a row diff. Labelled honestly, per the
//      Prime Directive "NEVER hand-author an expected output."
// NON-INVENT: every value READ from a real row. EXACT BigDecimal. No Date.now/random.
// §-log first — READ build/erp/poc_fold_distribution_run.log before concluding (exit code ≠ evidence).
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_distribution_run.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var R = require(path.join(ERP, 'report_distribution_run.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };
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

console.log('=== §DISTRUN witness (W-DISTRUN) — T_DistributionRunDetail ratio-split + allocation fold ===');
try { psql('SELECT 1'); } catch (e) { console.log('§DISTRUN-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§DISTRUN-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere)\n');

// pick the run under test (the one with active run lines)
var RUN = +psql("SELECT m_distributionrun_id FROM m_distributionrunline WHERE isactive='Y' GROUP BY 1 ORDER BY count(*) DESC LIMIT 1").trim();

// ── ENGINE inputs: raw base-table dumps ────────────────────────────────────────────────────────────────────
var src = {
  runLines: rows("SELECT m_distributionrunline_id, m_distributionrun_id, m_distributionlist_id, m_product_id, totalqty, minqty, isactive FROM m_distributionrunline",
    ['m_distributionrunline_id', 'm_distributionrun_id', 'm_distributionlist_id', 'm_product_id', 'totalqty', 'minqty', 'isactive']),
  listLines: rows("SELECT m_distributionlistline_id, m_distributionlist_id, c_bpartner_id, c_bpartner_location_id, ratio, minqty, isactive FROM m_distributionlistline",
    ['m_distributionlistline_id', 'm_distributionlist_id', 'c_bpartner_id', 'c_bpartner_location_id', 'ratio', 'minqty', 'isactive']),
  productUOM: rows("SELECT p.m_product_id, u.stdprecision uomprecision FROM m_product p JOIN c_uom u ON p.c_uom_id=u.c_uom_id", ['m_product_id', 'uomprecision'])
};
var folded = R.foldDistributionRun(src, RUN);
var L = folded[0];
console.log('§DISTRUN-FOLD run=' + RUN + ' runLines=' + folded.length + ' product=' + L.product + ' totalQty=' + L.totalQty.toString() +
  ' uomPrec=' + L.precision + ' details=' + L.details.length + '\n');

// ── §A. RAW SPLIT — ORACLE-EQUIVALENT vs iDempiere's own insertDetails formula ──────────────────────────────
var ORACLE_RAW =
  "SELECT ll.c_bpartner_id, ll.m_distributionlistline_id, (ll.ratio/l.ratiototal*rl.totalqty) rawqty " +
  "FROM m_distributionrunline rl JOIN m_distributionlist l ON rl.m_distributionlist_id=l.m_distributionlist_id " +
  "JOIN m_distributionlistline ll ON rl.m_distributionlist_id=ll.m_distributionlist_id " +
  "WHERE rl.m_distributionrun_id=" + RUN + " AND l.ratiototal<>0 AND rl.isactive='Y' AND ll.isactive='Y' ORDER BY ll.c_bpartner_id";
var oracleRaw = rows(ORACLE_RAW, ['c_bpartner_id', 'm_distributionlistline_id', 'rawqty']);
var oByLL = {}; oracleRaw.forEach(function (o) { oByLL[o.m_distributionlistline_id] = o; });
console.log('§DISTRUN-A oracle rawSplit rows=' + oracleRaw.length + ' (iDempiere insertDetails SQL formula on live DB)');
ok(oracleRaw.length === L.details.length, 'rawSplit row count matches', 'oracle=' + oracleRaw.length + ' engine=' + L.details.length);
var rawMism = 0;
L.details.forEach(function (d) {
  var o = oByLL[d.listLine];
  if (!o) { rawMism++; console.log('   ⚠ no oracle row for listLine ' + d.listLine); return; }
  if (d.rawQty.compareTo(BD.of(o.rawqty)) !== 0) { rawMism++; console.log('   ⚠ FINDING rawQty bp=' + d.bp + ' engine=' + d.rawQty.toString() + ' oracle=' + o.rawqty); }
});
ok(rawMism === 0, 'rawSplit == iDempiere insertDetails formula, EXACT (ORACLE-EQUIVALENT)', 'mismatches=' + rawMism);

// ── §B. ALLOCATION LOOP — RULE-CONSISTENT (invariants, not a row oracle) ────────────────────────────────────
console.log('\n── §B allocation loop (RULE-CONSISTENT — no live oracle; proving invariants) ──');
ok(!L.loopDetected && !L.cannotAdjust && !L.minSumGtTotal, 'loop converged without error (no >10 loops / cannot-adjust / Min>Total)',
  'loopDetected=' + !!L.loopDetected + ' cannotAdjust=' + !!L.cannotAdjust + ' minSumGtTotal=' + !!L.minSumGtTotal);
ok(L.allocTotal.compareTo(L.totalQty) === 0, 'Σ ActualAllocation == TotalQty EXACTLY (the loop\'s core invariant)',
  'sum=' + L.allocTotal.toString() + ' totalQty=' + L.totalQty.toString());
var minRespected = L.details.every(function (d) { return d.actualAllocation.compareTo(d.minQty) >= 0; });
ok(minRespected, 'every ActualAllocation respects its MinQty floor', L.details.map(function (d) { return 'bp' + d.bp + ':' + d.actualAllocation.toString() + '>=' + d.minQty.toString(); }).join(' '));
// determinism: a second fold yields identical allocations
var folded2 = R.foldDistributionRun(src, RUN);
var det2 = folded2[0].details.map(function (d) { return d.actualAllocation.toString(); }).join(',');
var det1 = L.details.map(function (d) { return d.actualAllocation.toString(); }).join(',');
ok(det1 === det2, 'allocation is deterministic across runs (replay-stable)', det1);
console.log('§DISTRUN-B allocations: ' + L.details.map(function (d) { return 'bp' + d.bp + '(ratio' + d.ratio.toString() + ',min' + d.minQty.toString() + ')=' + d.actualAllocation.toString(); }).join('  '));
console.log('§DISTRUN-B NOTE final allocation is rule-consistent (sum-exact under MinQty floors); a row-level oracle requires a live process run (T_DistributionRunDetail is 0-row until DistributionRun executes).');
// the MinQty floor must actually bite here (else the distribute branch is untested) — bp117 raw 187.5 < min 200
var floored = L.details.filter(function (d) { return d.qty.compareTo(d.minQty) <= 0; }).length;
ok(floored > 0, 'a MinQty floor is binding → the distribute-by-ratio branch is exercised (not just adjust-biggest)', 'flooredRows=' + floored);

// ── §FALSIFIER — bend one list line's Ratio → its rawSplit must diverge from the live iDempiere formula ──────
console.log('\n── §FALSIFIER — bending a list line Ratio must break the rawSplit oracle diff ──');
(function () {
  var probe = L.details[0];
  var bent = R.foldDistributionRun(src, RUN, { bendRatioListLine: { listLine: probe.listLine, delta: '25' } });
  var br = bent[0].details.filter(function (d) { return d.listLine === probe.listLine; })[0];
  var o = oByLL[probe.listLine];
  var diverged = br && br.rawQty.compareTo(BD.of(o.rawqty)) !== 0;
  console.log('§DISTRUN-FALSIFIER bent listLine=' + probe.listLine + ' Ratio+25 → rawQty ' + o.rawqty + ' → ' + (br ? br.rawQty.toString() : '(gone)'));
  ok(diverged, 'perturbing a Ratio breaks the rawSplit diff (the oracle-backed metric is load-bearing)');
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-DISTRUN NOT proven' :
  '✅ ALL CHECKS PASS — rawSplit ORACLE-EQUIVALENT (iDempiere insertDetails formula) + allocation loop RULE-CONSISTENT — W-DISTRUN'));
process.exit(fails ? 1 : 0);
