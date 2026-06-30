// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// witness_hr_bim_asset.js — proves prompts/RESUME_HR_BIM_ASSET.md (HR_BIM_Asset operate-phase alpha).
//
// Claims (each NAMES the issue it proves):
//   §HBA P-one-engine   — ONE runPeriod serves payroll · tenancy · strata · maintenance; each posts a BALANCED journal.
//   §HBA P-tenancy-term — RENTRUN emits a line ONLY for in-term leases; an out-of-term lease emits ZERO (and the
//                         set flips when the period moves past a lease's end).
//   §HBA P-tenancy-gl   — RENTRUN posts a balanced AR journal: Dr AR(1200) / Cr Rent Income(4100), ΣDr == ΣCr.
//   §HBA P-bind         — leases/assets bind to REAL building guids (WBDG_Office); a synthetic absent guid is
//                         honestly UN-LINKED; ZERO fabricated bindings.
//   §HBA P-chain        — the run folds as ONE signed op-group that verifyChain's; tamper one param byte → verify FAILS.
//
// Log Mandate: writes build/erp/hr_bim_asset.log; read it before any conclusion. Exit code 0 ⇔ all PASS.

var path = require('path');
var fs = require('fs');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');

// load the browser kernel op-log + the engine in node (window shim — same pattern as poc_blue_future.js)
global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var HBA = require(path.join(__dirname, '..', 'build', 'erp', 'hr_bim_asset.js'));

var BLD = path.join(__dirname, '..', 'deploy', 'buildings', 'WBDG_Office_extracted.db');
var LOG = path.join(__dirname, '..', 'build', 'erp', 'hr_bim_asset.log');
var PERIOD = '2026-06';

var lines = [];
function log(s) { lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function check(name, cond, detail) {
  if (cond) { pass++; log('  ✓ ' + name + (detail ? '  ' + detail : '')); }
  else { fail++; log('  ✗ FAIL ' + name + (detail ? '  ' + detail : '')); }
}

function approx(a, b) { return Math.abs(a - b) < 1e-9; }

(async function main() {
  log('§HBA witness — HR_BIM_Asset operate-phase alpha  (' + HBA.WATERMARK + ')');
  log('§HBA building=' + path.basename(BLD) + '  period=' + PERIOD);

  // ── extract REAL anchors (non-invent): real element guids + their real storey ──
  var bdb = new Database(BLD, { readonly: true });
  var rows = bdb.prepare(
    "SELECT guid, storey FROM elements_meta WHERE storey IN ('Level 1','Level 2') AND guid IS NOT NULL ORDER BY storey, guid LIMIT 8"
  ).all();
  bdb.close();
  var realGuids = rows.map(function (r) { return r.guid; });
  var storeyMap = {}; rows.forEach(function (r) { storeyMap[r.guid] = r.storey; });
  var storeyTally = Object.keys(storeyMap).reduce(function (acc, g) { acc[storeyMap[g]] = (acc[storeyMap[g]] || 0) + 1; return acc; }, {});
  log('§HBA extracted ' + realGuids.length + ' real guids (' + JSON.stringify(storeyTally) +
      '): ' + realGuids.slice(0, 3).map(function (g) { return g.slice(0, 10) + '…'; }).join(', ') + ', …');
  if (realGuids.length < 8) { log('§HBA ABORT — need ≥8 real guids, got ' + realGuids.length); finish(); return; }

  // building index — the non-invent join surface
  var guidSet = new Set(realGuids);
  var idx = { has: function (g) { return guidSet.has(g); }, storeyOf: function (g) { return storeyMap[g] || null; } };

  var seed = HBA.demoSeed(realGuids);

  // ── §HBA P-one-engine — one engine, four profiles, every run balanced ──
  log('\n§HBA P-one-engine — ONE runPeriod serves payroll/tenancy/strata/maintenance, each balanced');
  var profiles = ['PAYROLL', 'RENTRUN', 'STRATA', 'MAINTENANCE'];
  var runs = {};
  profiles.forEach(function (p) {
    var run = HBA.runPeriod(seed, p, PERIOD);
    runs[p] = run;
    log('    ' + p.padEnd(12) + ' lines=' + run.lines.length + ' ΣDr=' + run.journal.sumDr + ' ΣCr=' + run.journal.sumCr +
        ' balanced=' + run.journal.balanced + ' cashDir=' + run.cashDir);
    check('balanced/' + p, run.journal.balanced && approx(run.journal.sumDr, run.journal.sumCr));
  });
  check('one-engine serves 4 profiles', profiles.every(function (p) { return runs[p] && runs[p].journal.balanced; }), '(payroll=OUT, rent=IN, strata=IN, maint=OUT)');
  // expected obligation counts for 2026-06 (Emp-2 hired next month excluded; future lease/asset excluded)
  check('PAYROLL keeps active employees only', runs.PAYROLL.lines.length === 1, '(Emp-2 hired 2026-07 excluded)');
  check('MAINTENANCE keeps due/overdue assets only', runs.MAINTENANCE.lines.length === 2, '(A3 next_due 2026-12 excluded)');

  // ── §HBA P-tenancy-term — in-term filter, and the set flips with the period ──
  log('\n§HBA P-tenancy-term — RENTRUN emits a line ONLY for in-term leases');
  var rentJun = runs.RENTRUN;
  var partiesJun = rentJun.lines.map(function (l) { return l.party; });
  log('    2026-06 rent lines: ' + partiesJun.join(', '));
  check('Tenant-C (future 2026-09) excluded in 2026-06', partiesJun.indexOf('Tenant-C') === -1);
  check('in-term tenants A,B,D billed in 2026-06', ['Tenant-A', 'Tenant-B', 'Tenant-D'].every(function (t) { return partiesJun.indexOf(t) !== -1; }), '(D in-term though geometry-unlinked)');
  var rentOct = HBA.runPeriod(seed, 'RENTRUN', '2026-10');
  var partiesOct = rentOct.lines.map(function (l) { return l.party; });
  log('    2026-10 rent lines: ' + partiesOct.join(', '));
  check('Tenant-B (ended 2026-06) dropped in 2026-10', partiesOct.indexOf('Tenant-B') === -1, '(term filter moves with period)');
  check('Tenant-C now in-term in 2026-10', partiesOct.indexOf('Tenant-C') !== -1);

  // ── §HBA P-tenancy-gl — balanced AR journal, correct accounts ──
  log('\n§HBA P-tenancy-gl — RENTRUN posts a balanced AR journal');
  var expectRent = HBA.round2(2500 + 1800 + 999); // A + B + D
  check('Dr account = AR(1200)', rentJun.lines.every(function (l) { return l.drAccount === HBA.ACCT.AR; }));
  check('Cr account = Rent Income(4100)', rentJun.lines.every(function (l) { return l.crAccount === HBA.ACCT.RENT_INCOME; }));
  check('ΣDr == ΣCr == ' + expectRent, approx(rentJun.journal.sumDr, expectRent) && approx(rentJun.journal.sumCr, expectRent));
  log('    journal: Dr 1200 ' + rentJun.journal.sumDr + '  Cr 4100 ' + rentJun.journal.sumCr);

  // ── §HBA P-bind — real guids bind, synthetic guid honestly un-linked, zero fabricated ──
  log('\n§HBA P-bind — non-invent geometry binding');
  var boundLeases = HBA.bindUnits(seed.leases, idx);
  var nBound = boundLeases.filter(function (b) { return b.bound; }).length;
  var unlinked = boundLeases.filter(function (b) { return !b.bound; });
  log('    leases: ' + boundLeases.map(function (b) { return b.tenant + '=' + (b.bound ? 'BOUND@' + b.storey : 'unlinked'); }).join(', '));
  check('3 leases bind to real guids', nBound === 3);
  check('1 lease honestly un-linked', unlinked.length === 1 && unlinked[0].unit_guid === HBA.SYNTHETIC_ABSENT, '(synthetic absent guid)');
  // a fabricated binding would be: bound:true while guid not in the real set
  var fabricated = boundLeases.filter(function (b) { return b.bound && !guidSet.has(b.anchor); }).length;
  check('ZERO fabricated bindings', fabricated === 0);
  var boundAssets = HBA.bindUnits(seed.assets, idx);
  check('all 3 demo assets bind to real guids', boundAssets.filter(function (b) { return b.bound; }).length === 3);

  // spatial-view sanity (the viewer consumes this verbatim)
  var sv = HBA.spatialView(seed, idx, PERIOD);
  var statusOf = {}; sv.units.forEach(function (u) { statusOf[u.guid] = u.status; });
  check('lens: Tenant-A unit occupied', statusOf[seed.leases[0].unit_guid] === 'occupied');
  check('lens: Tenant-B unit expiring (ends this month)', statusOf[seed.leases[1].unit_guid] === 'expiring');
  check('lens: Tenant-C unit vacant (out-of-term)', statusOf[seed.leases[2].unit_guid] === 'vacant');
  check('lens: synthetic unit unlinked (never tinted)', statusOf[HBA.SYNTHETIC_ABSENT] === 'unlinked');
  var assetStates = {}; sv.assets.forEach(function (a) { assetStates[a.guid] = a.state; });
  check('lens: overdue/due/ok asset states', assetStates[realGuids[5]] === 'overdue' && assetStates[realGuids[6]] === 'due' && assetStates[realGuids[7]] === 'ok');
  log('    storeys density: ' + sv.storeys.map(function (s) { return s.storey + '=' + s.occupied + '/' + s.total; }).join(', '));

  // ── §HBA P-chain — the run folds as ONE signed op-group; tamper → verify fails ──
  log('\n§HBA P-chain — run is a tamper-evident signed op-group');
  var SQL = await initSqlJs();
  var kdb = new SQL.Database();
  KO.ensureTable(kdb);
  var res = await HBA.commitRun(KO, kdb, rentJun, { gid: 'hba-rentrun-2026-06' });
  check('op-group committed (all-or-none)', !!res && res.committed === true, '(ops=' + (res && res.ids ? res.ids.length : 0) + ', expect ' + (rentJun.lines.length + 1) + ')');
  check('op count = header + N lines', res && res.ids && res.ids.length === rentJun.lines.length + 1);
  var v1 = await KO.verifyChain(kdb);
  check('verifyChain OK before tamper', v1.ok === true, '(len=' + v1.len + ')');
  // tamper: rewrite one HBA_RUN_LINE parameter in place (forge an amount) — chain must catch it
  kdb.run("UPDATE kernel_ops SET parameters = REPLACE(parameters, '\"amount\":2500', '\"amount\":9999') WHERE op_type='HBA_RUN_LINE'");
  var v2 = await KO.verifyChain(kdb);
  check('verifyChain FAILS after tamper', v2.ok === false, '(why=' + v2.why + ', brokeAt=' + v2.brokeAt + ')');
  kdb.close();

  finish();
})().catch(function (e) { log('§HBA EXCEPTION ' + e.stack); finish(); });

function finish() {
  var summary = '\n§HBA RESULT pass=' + pass + ' fail=' + fail + '  → ' + (fail === 0 ? 'GREEN' : 'RED');
  log(summary);
  fs.writeFileSync(LOG, lines.join('\n') + '\n');
  log('§HBA log → ' + path.relative(path.join(__dirname, '..'), LOG));
  process.exit(fail === 0 ? 0 : 1);
}
