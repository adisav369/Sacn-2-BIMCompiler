#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * verify_migration.js — ERP Step 0 acceptance gate (prompts/ERP_RAW_MIGRATION.md).
 *
 * Proves, from the BUILT cluster (not from PG trust):
 *   1. §MIGRATE verify counts — per-table SQLite rowcount == PG count(*); 0 mismatches
 *      (proves nothing was silently dropped).
 *   2. Round-trip — every AD_Rule.Script md5 matches the PG md5 (byte-identical).
 *   3. sql.js read — open ad_full.db in sql.js (the PWA's engine) and run one query
 *      per major table (proves the browser can read the cluster).
 *   4. Blob fidelity — a bytea row reads back as a SQLite BLOB of the PG byte length.
 *
 * READ THE LOG. Run: node scripts/verify_migration.js 2>&1 | tee build/erp/verify.log
 */
'use strict';
var fs = require('fs');
var path = require('path');
var crypto = require('crypto');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');

var CONTAINER = process.env.ERP_PG_CONTAINER || 'postgres';
var PGDB = process.env.ERP_PG_DB || 'idempiere';
var PGUSER = process.env.ERP_PG_USER || 'adempiere';
var SCHEMA = process.env.ERP_PG_SCHEMA || 'adempiere';
var OUT = process.env.ERP_OUT || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var MAXBUF = 64 * 1024 * 1024;

function pg(sql) {
  return execFileSync('docker',
    ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', PGDB, '-t', '-A', '-F', '\t', '-c', sql],
    { maxBuffer: MAXBUF, encoding: 'utf8' })
    .split('\n').filter(function (l) { return l.length; }).map(function (l) { return l.split('\t'); });
}

(async function () {
  var fail = 0;
  var db = new Database(OUT, { readonly: true });

  // 1) Per-table count compare vs PG.
  var pgCounts = {};
  pg("SELECT relname, n_live_tup FROM pg_stat_user_tables").forEach(function (r) {
    pgCounts[r[0].toLowerCase()] = +r[1];
  });
  // n_live_tup is an estimate; use exact count(*) for the compare via a single UNION.
  var tables = db.prepare(
    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\\_%' ESCAPE '\\' ORDER BY name"
  ).all().map(function (r) { return r.name; });

  var unions = tables.map(function (t) {
    return "SELECT '" + t + "' t, count(*) c FROM " + SCHEMA + '."' + t + '"';
  }).join(' UNION ALL ');
  var pgExact = {};
  pg(unions).forEach(function (r) { pgExact[r[0].toLowerCase()] = +r[1]; });

  var mism = [], totalSqlite = 0, totalPg = 0;
  tables.forEach(function (t) {
    var s = db.prepare('SELECT count(*) c FROM "' + t + '"').get().c;
    var p = pgExact[t.toLowerCase()];
    totalSqlite += s; totalPg += (p || 0);
    if (s !== p) mism.push(t + ' sqlite=' + s + ' pg=' + p);
  });
  if (mism.length) {
    fail++;
    console.log('§MIGRATE verify counts MISMATCH=' + mism.length);
    mism.slice(0, 20).forEach(function (m) { console.log('   ! ' + m); });
  }
  console.log('§MIGRATE verify counts tables=' + tables.length +
    ' sqliteRows=' + totalSqlite + ' pgRows=' + totalPg +
    ' mismatches=' + mism.length + ' => ' + (mism.length ? 'FAIL' : 'PASS'));

  // 2) AD_Rule round-trip vs PG md5.
  var pgMd5 = {};
  pg('SELECT ad_rule_id, md5(script) FROM ad_rule WHERE script IS NOT NULL')
    .forEach(function (r) { pgMd5[r[0]] = r[1]; });
  var rrFail = 0, rrN = 0;
  db.prepare('SELECT ad_rule_id, script FROM ad_rule WHERE script IS NOT NULL').all()
    .forEach(function (r) {
      rrN++;
      var m = crypto.createHash('md5').update(r.script, 'utf8').digest('hex');
      if (m !== pgMd5[String(r.ad_rule_id)]) { rrFail++; console.log('   ! AD_Rule ' + r.ad_rule_id + ' md5 ' + m + ' != ' + pgMd5[String(r.ad_rule_id)]); }
    });
  if (rrFail) fail++;
  console.log('§MIGRATE verify roundtrip AD_Rule checked=' + rrN + ' byteIdentical=' +
    (rrN - rrFail) + '/' + rrN + ' => ' + (rrFail ? 'FAIL' : 'PASS'));

  // 4) Blob fidelity — ad_attachment.binarydata.
  var battRows = +pg('SELECT count(*) FROM ad_attachment')[0][0];
  var blobRow = db.prepare("SELECT typeof(binarydata) t, length(binarydata) len FROM ad_attachment WHERE binarydata IS NOT NULL LIMIT 1").get();
  var pgBlobLen = battRows ? +pg('SELECT length(binarydata) FROM ad_attachment WHERE binarydata IS NOT NULL LIMIT 1')[0][0] : 0;
  if (blobRow) {
    var ok = blobRow.t === 'blob' && blobRow.len === pgBlobLen;
    if (!ok) fail++;
    console.log('§MIGRATE verify blob ad_attachment type=' + blobRow.t +
      ' sqliteLen=' + blobRow.len + ' pgLen=' + pgBlobLen + ' => ' + (ok ? 'PASS' : 'FAIL'));
  } else {
    console.log('§MIGRATE verify blob ad_attachment rows=' + battRows + ' (no non-null binarydata to check)');
  }
  db.close();

  // 3) sql.js read — the PWA engine opens the cluster + one query per major table.
  var SQL = await initSqlJs();
  var sdb = new SQL.Database(fs.readFileSync(OUT));
  var majors = ['ad_window', 'ad_tab', 'ad_field', 'ad_column', 'ad_table',
    'ad_rule', 'ad_val_rule', 'c_doctype', 'ad_printformat', 'ad_ref_list',
    'c_order', 'c_invoice', 'm_product'];
  var sjFail = 0;
  majors.forEach(function (t) {
    try {
      var res = sdb.exec('SELECT count(*) FROM ' + t);
      var c = res[0].values[0][0];
      console.log('§MIGRATE verify sql.js ' + t + ' rows=' + c);
    } catch (e) { sjFail++; console.log('   ! sql.js FAILED ' + t + ': ' + e.message); }
  });
  if (sjFail) fail++;
  // rules accounting (echo the migration line from the built db, via sql.js).
  function sc(sql) { try { return sdb.exec(sql)[0].values[0][0]; } catch (e) { return 'NA'; } }
  console.log('§MIGRATE rules AD_Rule=' + sc('SELECT count(*) FROM ad_rule') +
    ' AD_Val_Rule=' + sc('SELECT count(*) FROM ad_val_rule') +
    ' callouts=' + sc("SELECT count(*) FROM ad_column WHERE callout IS NOT NULL AND callout<>''") +
    ' docTypes=' + sc('SELECT count(*) FROM c_doctype'));
  sdb.close();

  console.log('§MIGRATE VERIFY ' + (fail ? 'FAIL (' + fail + ' checks failed)' : 'PASS — all checks green'));
  process.exit(fail ? 1 : 0);
})();
