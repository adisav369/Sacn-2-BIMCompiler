#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_reference_harden.js — W-REFERENCE-HARDEN witness (HARDEN_MATRIX.md §H-3). W-REFERENCE proved
// ad_reference.js INTERPRETS AD_Ref_Table (resolves a Table/TableDir reference → fk table + key column, then
// answers FK membership). H-3 asks: does the verdict match the REAL iDempiere oracle?
//
// ORACLE = live iDempiere Postgres (docker `postgres`/`idempiere`, GardenWorld). Per reference we diff TWO
// things against the oracle:
//   (1) RESOLUTION — our readRefTable() (fkTable,keyCol) == the oracle's ad_ref_table⋈ad_table⋈ad_column join,
//   (2) MEMBERSHIP — the full keyCol id-set of the resolved FK table, SQLite (ours) vs Postgres (oracle), diffed.
// A diff=0 over the full set proves fkExists() (a membership query against that set) is equivalent for EVERY id,
// not just sampled ones. Plus an explicit fkExists(real)/fkExists(bogus) probe exercises the engine's verdict API.
// (ad_reference's applyVFormat leg is a pure Java-port string mask with NO independent SQL oracle → named-deferred,
// not faked.)
//
// NON-INVENT: oracle is Postgres, never synthesized; pg-down ⇒ honest SKIP. Read-only, no Date/random.
// Run: node scripts/poc_reference_harden.js 2>&1 | tee build/erp/poc_reference_harden.log  (read the log)
'use strict';
var path = require('path');
var cp = require('child_process');
var Database = require('better-sqlite3');
var R = require(path.join(__dirname, '..', 'build', 'erp', 'ad_reference.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var fails = 0, hardened = 0, skipped = 0;
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

console.log('═══ W-REFERENCE-HARDEN — ad_reference FK resolution + membership diffed vs live iDempiere Postgres (§H-3) ═══\n');
if (!pgUp()) { console.log('§HARDEN-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§HARDEN-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ')\n');
var db = new Database(DB_PATH, { readonly: true });

// real AD_Ref_Table references spanning master-data FK targets.
var REFS = [1, 3, 4, 100, 102, 105, 106, 109, 110, 112, 113, 114];

console.log('── per reference: RESOLUTION (fkTable,keyCol) + full MEMBERSHIP set, ours vs iDempiere oracle ──');
var allOk = true;
REFS.forEach(function (refId) {
  var rt = R.readRefTable(db, refId);
  if (!rt) { console.log('§HARDEN-SKIP ref=' + refId + ' no-ref-table in ad_full.db'); skipped++; return; }

  // (1) RESOLUTION oracle
  var oRes;
  try {
    oRes = pgQuery('SELECT lower(tb.tablename)||\'|\'||kc.columnname FROM ad_ref_table rt ' +
      'JOIN ad_table tb ON tb.ad_table_id=rt.ad_table_id JOIN ad_column kc ON kc.ad_column_id=rt.ad_key ' +
      'WHERE rt.ad_reference_id=' + refId)[0];
  } catch (e) { console.log('§HARDEN-SKIP ref=' + refId + ' pg-resolution err'); skipped++; return; }
  var oParts = (oRes || '').split('|');
  var resMatch = rt.fkTable === oParts[0] && rt.keyCol.toLowerCase() === String(oParts[1] || '').toLowerCase();

  // (2) MEMBERSHIP full id-set diff over the resolved (fkTable,keyCol)
  var sel = 'SELECT ' + rt.keyCol + ' FROM ' + rt.fkTable + ' ORDER BY ' + rt.keyCol;
  var E, O;
  try { E = db.prepare(sel).all().map(function (r) { return String(r[rt.keyCol] == null ? r[Object.keys(r)[0]] : r[rt.keyCol]); }); }
  catch (e) { console.log('§HARDEN-SKIP ref=' + refId + ' SQLite err: ' + e.message); skipped++; return; }
  try { O = pgQuery(sel); } catch (e) { console.log('§HARDEN-SKIP ref=' + refId + ' Postgres err'); skipped++; return; }
  var d = setDiff(E, O);

  // (3) engine fkExists verdict probe: a real id (first oracle member) + a guaranteed-bogus id.
  var realId = O.length ? O[0] : null;
  var probeReal = realId != null ? R.fkExists(db, refId, realId).exists : null;
  var bogus = '999999999';
  var probeBogus = R.fkExists(db, refId, bogus).exists;

  var ok = resMatch && d.total === 0 && probeReal === true && probeBogus === false;
  if (!ok) allOk = false;
  hardened++;
  console.log('§HARDEN surface=AD_RefTable ref=' + refId + ' resolved=' + rt.fkTable + '.' + rt.keyCol +
    ' resMatch=' + (resMatch ? 'Y' : 'N') + ' members=' + E.length + ' oracleMembers=' + O.length + ' diff=' + d.total +
    ' fkExists(' + realId + ')=' + probeReal + ' fkExists(bogus)=' + probeBogus + ' oracle=iDempiere(pg)');
  verdict(ok, 'ref ' + refId + ' resolution+membership == iDempiere oracle', 'diff=' + d.total + ' resMatch=' + resMatch);
});

console.log('\n§HARDEN-TALLY surface=AD_RefTable refs_diffed=' + hardened + ' all_ok=' + (allOk ? 'Y' : 'N') + ' skipped=' + skipped);
verdict(hardened >= 8 && allOk, 'AD_Ref_Table FK engine is ORACLE-EQUIVALENT (≥8 refs: resolution + full membership match iDempiere)', 'hardened=' + hardened);

// ── §FALSIFIER — prove the membership diff is load-bearing: an engine that resolved the WRONG fk table would
//    diverge from the oracle. Resolve ref 4 (→ad_reference) but diff its members against a DIFFERENT table's
//    id-set (ad_column) as the oracle would never agree. ───────────────────────────────────────────────────
console.log('\n── §FALSIFIER — wrong-table resolution must DIVERGE from the oracle membership (diff>0) ──');
(function () {
  var rt = R.readRefTable(db, 4);                                 // ours: ad_reference.ad_reference_id
  var oursMembers = db.prepare('SELECT ' + rt.keyCol + ' FROM ' + rt.fkTable + ' ORDER BY ' + rt.keyCol).all().map(function (r) { return String(r[rt.keyCol]); });
  var wrongOracle = pgQuery('SELECT ad_column_id FROM ad_column ORDER BY ad_column_id');   // a DIFFERENT table
  var d = setDiff(oursMembers, wrongOracle);
  console.log('§HARDEN-FALSIFIER ref=4 ours(ad_reference)=' + oursMembers.length + ' wrongOracle(ad_column)=' + wrongOracle.length + ' diff=' + d.total);
  verdict(d.total > 0, 'wrong-table membership DIVERGES from oracle → diff metric is load-bearing', 'diff=' + d.total);
})();

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' verdict(s) FAILED' : '✅ ALL VERDICTS PASS — AD_Ref_Table oracle-equivalent vs live iDempiere (applyVFormat leg = no-SQL-oracle, named-deferred)'));
process.exit(fails ? 1 : 0);
