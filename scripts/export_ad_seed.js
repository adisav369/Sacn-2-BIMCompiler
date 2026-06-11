#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * export_ad_seed.js — FULL-WIDTH browser seed builder (prompts/IDMP_FULLWIDTH_SEED.md §1).
 *
 * ⚠ DO NOT REMOVE — Scope guard
 * Scope: replace export_ad.sh's hand-picked COLUMN SLICE with a full-width (SELECT *)
 *   extract of the SAME table set, driven by scripts/ad_seed_manifest.json (the per-table
 *   contract observed from the deployed seed: name-case, WHERE, IsActive rule) plus the
 *   card's two additions (ad_process, ad_process_para). EXTRACT, DON'T INVENT — every
 *   row/column from the Docker PG (`postgres`/`idempiere`, GardenWorld client 11).
 *   READ THE LOG after every run — exit code is not evidence.
 *
 * Mechanics (reuses migrate_pg_to_sqlite.js's proven machinery — COPY TEXT escaping,
 * loose affinity — plus what the browser seed additionally needs):
 *   - DDL columns in PG ordinal order; for `case:"canonical"` tables the column names
 *     take iDempiere's canonical case from AD_Column.ColumnName (sql.js returns
 *     STORED-case keys — the live UI reads e.g. row.AD_Window_ID); `case:"lower"`
 *     tables keep PG lowercase (idmp_session/engines read lowercase keys).
 *   - PRIMARY KEY clause from the PG catalog (shard-install INSERT OR IGNORE dedups
 *     by PK — the PK-less c_invoicetax/c_ordertax doubling class dies here).
 *   - No 2>/dev/null anywhere: a bad WHERE or missing column FAILS LOUDLY.
 *
 * Run : node scripts/export_ad_seed.js   (out: build/erp/ad_seed_fullwidth.db)
 * Env : ERP_OUT=/path/x.db  ERP_PG_CONTAINER/ERP_PG_DB/ERP_PG_USER/ERP_PG_SCHEMA
 */
'use strict';

var fs = require('fs');
var path = require('path');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');

var CONTAINER = process.env.ERP_PG_CONTAINER || 'postgres';
var DB = process.env.ERP_PG_DB || 'idempiere';
var PGUSER = process.env.ERP_PG_USER || 'adempiere';
var SCHEMA = process.env.ERP_PG_SCHEMA || 'adempiere';
var OUT = process.env.ERP_OUT ||
  path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db');
var MANIFEST = path.join(__dirname, 'ad_seed_manifest.json');
var MAXBUF = 512 * 1024 * 1024;

function pgMeta(sql) {
  var out = execFileSync('docker',
    ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', DB, '-t', '-A', '-F', '\t', '-c', sql],
    { maxBuffer: MAXBUF, encoding: 'utf8' });
  return out.split('\n').filter(function (l) { return l.length > 0; })
    .map(function (l) { return l.split('\t'); });
}
function pgCopy(sql) {
  return execFileSync('docker',
    ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', DB, '-c', sql],
    { maxBuffer: MAXBUF, encoding: 'utf8' });
}
// COPY TEXT unescape (PostgreSQL copy.c rules) — verbatim from migrate_pg_to_sqlite.js.
function unescape(s) {
  if (s === '\\N') return null;
  if (s.indexOf('\\') === -1) return s;
  var out = '';
  for (var i = 0; i < s.length; i++) {
    var c = s[i];
    if (c !== '\\') { out += c; continue; }
    var n = s[++i];
    switch (n) {
      case 'n': out += '\n'; break;
      case 't': out += '\t'; break;
      case 'r': out += '\r'; break;
      case 'b': out += '\b'; break;
      case 'f': out += '\f'; break;
      case 'v': out += '\v'; break;
      case '\\': out += '\\'; break;
      default: out += (n === undefined ? '\\' : n); break;
    }
  }
  return out;
}
function affinity(t) {
  if (t === 'bytea') return 'BLOB';
  if (t === 'smallint' || t === 'integer' || t === 'bigint') return 'INTEGER';
  if (t === 'numeric' || t === 'decimal' || t === 'real' ||
    t === 'double precision') return 'NUMERIC';
  return 'TEXT';
}
function q(id) { return '"' + String(id).replace(/"/g, '""') + '"'; }

function main() {
  var manifest = JSON.parse(fs.readFileSync(MANIFEST, 'utf8'));
  console.log('§SEED-FW start tables=' + manifest.length + ' source=pg:' + DB + '/' +
    SCHEMA + ' out=' + OUT);

  // PG catalog: columns (ordinal + type), primary keys, canonical AD_Column case.
  var colRows = pgMeta(
    "SELECT table_name, column_name, data_type FROM information_schema.columns " +
    "WHERE table_schema='" + SCHEMA + "' ORDER BY table_name, ordinal_position");
  var cols = {};
  colRows.forEach(function (r) {
    (cols[r[0]] = cols[r[0]] || []).push({ name: r[1], type: r[2] });
  });
  var pkRows = pgMeta(
    "SELECT tc.table_name, kcu.column_name FROM information_schema.table_constraints tc " +
    "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name=tc.constraint_name " +
    "AND kcu.table_schema=tc.table_schema WHERE tc.table_schema='" + SCHEMA + "' " +
    "AND tc.constraint_type='PRIMARY KEY' ORDER BY tc.table_name, kcu.ordinal_position");
  var pks = {};
  pkRows.forEach(function (r) { (pks[r[0]] = pks[r[0]] || []).push(r[1]); });
  var caseRows = pgMeta(
    "SELECT lower(t.tablename), lower(c.columnname), c.columnname FROM " + q(SCHEMA) +
    ".ad_column c JOIN " + q(SCHEMA) + ".ad_table t ON c.ad_table_id=t.ad_table_id");
  var canon = {};
  caseRows.forEach(function (r) { canon[r[0] + '.' + r[1]] = r[2]; });

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  if (fs.existsSync(OUT)) fs.unlinkSync(OUT);
  var sqlite = new Database(OUT);
  sqlite.pragma('journal_mode = OFF');
  sqlite.pragma('synchronous = OFF');

  var totalRows = 0, widened = 0, missing = [];
  manifest.forEach(function (e) {
    var tn = e.table.toLowerCase();
    var cs = cols[tn];
    if (!cs || !cs.length) { missing.push(e.table); return; }

    // Column display case: canonical AD_Column.ColumnName for the MixedCase stratum
    // (stored-case keys feed the live UI), PG lowercase for the rest.
    function disp(c) {
      if (e.case !== 'canonical') return c;
      return canon[tn + '.' + c] || c;
    }
    var pk = pks[tn] || [];
    var ddl = 'CREATE TABLE ' + q(e.table) + ' (' +
      cs.map(function (c) { return q(disp(c.name)) + ' ' + affinity(c.type); }).join(', ') +
      (pk.length ? ', PRIMARY KEY (' + pk.map(function (c) { return q(disp(c)); }).join(', ') + ')'
                 : '') + ')';
    sqlite.exec(ddl);

    var where = e.where || '1=1';
    if (e.activeOnly) where += " AND isactive='Y'";
    var collist = cs.map(function (c) { return q(c.name); }).join(',');
    var payload = pgCopy('COPY (SELECT ' + collist + ' FROM ' + q(SCHEMA) + '.' + q(tn) +
      ' WHERE ' + where + ') TO STDOUT');

    var blobIdx = cs.map(function (c, i) { return c.type === 'bytea' ? i : -1; })
      .filter(function (i) { return i >= 0; });
    var ins = sqlite.prepare('INSERT INTO ' + q(e.table) + ' VALUES (' +
      cs.map(function () { return '?'; }).join(',') + ')');
    var lines = payload.length ? payload.split('\n') : [];
    if (lines.length && lines[lines.length - 1] === '') lines.pop();
    var n = 0;
    var tx = sqlite.transaction(function () {
      for (var li = 0; li < lines.length; li++) {
        var fields = lines[li].split('\t');
        var vals = new Array(cs.length);
        for (var fi = 0; fi < cs.length; fi++) {
          var v = unescape(fields[fi]);
          if (v !== null && blobIdx.indexOf(fi) >= 0) {
            v = (v.slice(0, 2) === '\\x')
              ? Buffer.from(v.slice(2), 'hex') : Buffer.from(v, 'binary');
          }
          vals[fi] = v;
        }
        ins.run(vals);
        n++;
      }
    });
    tx();
    totalRows += n;
    if (cs.length > e.prevCols) widened++;
    console.log('§SEED-FW table=' + e.table + ' rows=' + n + ' (was ' + e.prevRows +
      ') cols=' + cs.length + ' (was ' + e.prevCols + ') pk=' + (pk.join('+') || 'none'));
  });

  sqlite.close();
  var bytes = fs.statSync(OUT).size;
  console.log('§SEED-FW done tables=' + (manifest.length - missing.length) + '/' +
    manifest.length + ' rows=' + totalRows + ' widened=' + widened +
    ' bytes=' + bytes + ' (' + (bytes / 1048576).toFixed(1) + 'MB)');
  if (missing.length) {
    console.log('§SEED-FW MISSING-IN-PG [' + missing.join(',') + ']');
    process.exit(1);
  }
}

main();
