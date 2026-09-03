#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_access_harden.js — W-ACCESS-HARDEN witness (HARDEN_MATRIX.md §H-3). W-ACCESS proved ad_access.js
// INTERPRETS the real grant rows (gates windows/processes/forms/records). H-3 asks: does the gate's verdict
// match the REAL iDempiere oracle?
//
// ORACLE = live iDempiere Postgres (docker `postgres`/`idempiere`). Two diffs:
//   (1) GRANT MAPS — for every role, our buildRole() window/process/form access map (id→readWrite) is diffed
//       against the oracle SQL MRole runs (SELECT id,IsReadWrite FROM AD_*_Access WHERE AD_Role_ID=? AND
//       IsActive='Y'). This is the MRole.getWindowAccess/getProcessAccess/getFormAccess contract over the real
//       1303+1309+145 grant rows, per role — diff=0 = the engine reproduces iDempiere's per-role access map.
//   (2) canView SCO logic — ad_access.canView (a port of MRole.canView's switch) is cross-checked against the
//       INDEPENDENT bitmask-intersection definition of AccessLevel ({S=4,C=2,O=1}; table sees user iff bits
//       overlap) across ALL 8 table-levels × a userLevel matrix. Two distinct encodings of the same rule agree
//       ⇒ the port is faithful (no live JVM oracle for pure Java logic — this is the honest substitute).
//
// NON-INVENT: grant oracle is Postgres, never synthesized; pg-down ⇒ honest SKIP. Read-only, no Date/random.
// Run: node scripts/poc_access_harden.js 2>&1 | tee build/erp/poc_access_harden.log  (read the log)
'use strict';
var path = require('path');
var cp = require('child_process');
var Database = require('better-sqlite3');
var A = require(path.join(__dirname, '..', 'build', 'erp', 'ad_access.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function pgQuery(sql) {
  return cp.execFileSync('docker', ['exec', PG.container, 'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
    .split('\n').map(function (s) { return s.trim(); }).filter(function (s) { return s.length; });
}
function pgUp() { try { pgQuery('SELECT 1'); return true; } catch (e) { return false; } }
function setDiff(a, b) {
  var sa = new Set(a), sb = new Set(b), miss = 0, extra = 0;
  a.forEach(function (x) { if (!sb.has(x)) extra++; });
  b.forEach(function (x) { if (!sa.has(x)) miss++; });
  return { miss: miss, extra: extra, total: miss + extra };
}
// our access Map<id,bool> -> sorted ["id:Y"/"id:N"] ; oracle rows "id|Y"/"id|N" -> same shape.
function ourMapSet(map) { var o = []; map.forEach(function (rw, id) { o.push(id + ':' + (rw ? 'Y' : 'N')); }); return o; }
function oracleSet(rows) { return rows.map(function (r) { var p = r.split('|'); return p[0] + ':' + p[1]; }); }

console.log('═══ W-ACCESS-HARDEN — ad_access role gate diffed vs live iDempiere Postgres (MRole oracle, §H-3) ═══\n');
if (!pgUp()) { console.log('§HARDEN-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§HARDEN-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', MRole grant tables)\n');
var db = new Database(DB_PATH, { readonly: true });

// ── (1) GRANT MAPS — per role, our access map == iDempiere's getXxxAccess SQL ──────────────────────────
console.log('── per role: window/process/form access map, ours (buildRole) vs iDempiere oracle SQL ──');
var roleIds = db.prepare('SELECT ad_role_id FROM ad_role ORDER BY ad_role_id').all().map(function (r) { return r.ad_role_id; });
var grantSurfaces = [
  { key: 'win',  table: 'ad_window_access',  idcol: 'ad_window_id',  label: 'window' },
  { key: 'proc', table: 'ad_process_access', idcol: 'ad_process_id', label: 'process' },
  { key: 'form', table: 'ad_form_access',    idcol: 'ad_form_id',    label: 'form' }
];
var mapsDiffed = 0, allMapsZero = true;
roleIds.forEach(function (rid) {
  var role = A.buildRole(db, rid);
  grantSurfaces.forEach(function (gs) {
    var ours = ourMapSet(role[gs.key]);
    var oracle = oracleSet(pgQuery("SELECT " + gs.idcol + "||'|'||isreadwrite FROM " + gs.table + " WHERE ad_role_id=" + rid + " AND isactive='Y'"));
    var d = setDiff(ours, oracle);
    if (d.total !== 0) allMapsZero = false;
    mapsDiffed++;
    console.log('§HARDEN surface=AD_Access role=' + rid + '("' + role.name + '") ' + gs.label + '-access ours=' + ours.length + ' oracle=' + oracle.length + ' diff=' + d.total + ' oracle=iDempiere(pg)');
    verdict(d.total === 0, 'role ' + rid + ' ' + gs.label + '-access map == MRole oracle', 'n=' + ours.length + ' diff=' + d.total);
  });
});
console.log('§HARDEN-TALLY surface=AD_Access roles=' + roleIds.length + ' maps_diffed=' + mapsDiffed + ' all_diff0=' + (allMapsZero ? 'Y' : 'N'));
verdict(mapsDiffed >= 12 && allMapsZero, 'AD role/access engine is ORACLE-EQUIVALENT (every per-role grant map matches iDempiere)', 'maps=' + mapsDiffed);

// ── (2) canView SCO logic — engine switch == independent bitmask-intersection over all levels × userLevels ─
console.log('\n── canView: engine switch vs INDEPENDENT bitmask-intersection (no live-JVM oracle for pure logic) ──');
// independent oracle: userLevel SCO -> bits {S=4,C=2,O=1}; canView iff (userBits & tableBits) != 0.
function bitsOfUserLevel(u) { var b = 0; if (u.charAt(0) === 'S') b |= 4; if (u.charAt(1) === 'C') b |= 2; if (u.charAt(2) === 'O') b |= 1; return b; }
function canViewBitmask(userLevel, tableLevel) { return (bitsOfUserLevel(String(userLevel)) & (parseInt(tableLevel, 10) || 0)) !== 0; }
var TABLE_LEVELS = ['1', '2', '3', '4', '6', '7'];          // the real AccessLevel values in ad_table (n/a: 0,5)
// the 7 VALID userLevels (≥1 SCO bit — every iDempiere role has at least one; the seed's real 3 are
// "  O"/" CO"/"S  ", a subset). The empty "   " is out-of-domain (no login-able role) and is the only input
// where canView's faithful `case '7' → always true` diverges from a naive bitmask — so it is excluded.
var USER_LEVELS = ['S  ', ' C ', '  O', 'SC ', 'S O', ' CO', 'SCO'];
var combos = 0, mismatches = 0;
TABLE_LEVELS.forEach(function (tl) {
  USER_LEVELS.forEach(function (ul) {
    combos++;
    if (A.canView(ul, tl) !== canViewBitmask(ul, tl)) { mismatches++; console.log('   ⚠ mismatch userLevel="' + ul + '" tableLevel=' + tl + ' engine=' + A.canView(ul, tl) + ' bitmask=' + canViewBitmask(ul, tl)); }
  });
});
console.log('§HARDEN-CANVIEW combos=' + combos + ' (6 levels × 7 valid userLevels) mismatches=' + mismatches);
verdict(mismatches === 0, 'canView switch == bitmask-intersection over every (tableLevel,userLevel)', 'combos=' + combos + ' mismatch=' + mismatches);

// ── §FALSIFIER — the grant-map diff is load-bearing: shift a role's window map by one bogus grant → diff>0. ─
console.log('\n── §FALSIFIER — a tampered access map must DIVERGE from the oracle (diff>0) ──');
(function () {
  var rid = roleIds[0], role = A.buildRole(db, rid);
  var tampered = ourMapSet(role.win).concat(['999999:Y']);     // inject a grant the oracle never had
  var oracle = oracleSet(pgQuery("SELECT ad_window_id||'|'||isreadwrite FROM ad_window_access WHERE ad_role_id=" + rid + " AND isactive='Y'"));
  var d = setDiff(tampered, oracle);
  console.log('§HARDEN-FALSIFIER role=' + rid + ' tampered(+1 bogus grant)=' + tampered.length + ' oracle=' + oracle.length + ' diff=' + d.total);
  verdict(d.total > 0, 'tampered grant map DIVERGES from oracle → diff metric is load-bearing', 'diff=' + d.total);
})();

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' verdict(s) FAILED' : '✅ ALL VERDICTS PASS — AD role/access oracle-equivalent vs live iDempiere (canView cross-checked, no JVM oracle)'));
process.exit(fails ? 1 : 0);
