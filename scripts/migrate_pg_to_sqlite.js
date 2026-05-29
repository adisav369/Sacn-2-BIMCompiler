#!/usr/bin/env node
/**
 * migrate_pg_to_sqlite.js — ERP Step 0: raw PG→SQLite migration.
 *   Spec: docs/ERP.md §0.10 + prompts/ERP_RAW_MIGRATION.md (TASK Step 0).
 *
 * PRIME RULE: EXTRACT, do not invent. Migrate the FULL iDempiere PG dictionary
 * RAW — no column-strip — into a separate SQLite cluster (build/erp/ad_full.db).
 * This is the SAME pipeline as the §1 ad_seed.db export, run COMPLETE.
 *
 * Source : docker `postgres` container, db idempiere, user adempiere, schema adempiere.
 * Extract: `psql -c "COPY (...) TO STDOUT"` — TEXT format escapes embedded
 *   newlines/tabs/backslashes (\n \t \\) and NULL as \N, so each PG row is exactly
 *   ONE output line and rule-script bodies round-trip BYTE-IDENTICAL after unescape.
 * Build  : better-sqlite3, loose affinity, bytea -> BLOB (hex-decoded).
 *
 * Flagged subset (handled explicitly, logged LOUDLY — never silently dropped):
 *   - sequences : DROPPED (§5 forbids MAX+1).
 *   - functions/triggers : SKIPPED (logic lives in Java/rules, not data).
 *   - views (RV_*, _v) : DEFERRED — definitions SNAPSHOT into _pg_views (translate later).
 * Java is NOT migrated — it stays the §18.10 oracle.
 *
 * READ THE LOG AFTER EVERY RUN. Exit code is not evidence.
 * Run:  node scripts/migrate_pg_to_sqlite.js [2>&1 | tee build/erp/migrate.log]
 * Env:  ERP_TABLES="ad_rule,c_doctype"  -> migrate only those (smoke subset)
 *       ERP_OUT=/path/ad_full.db        -> override output db
 */
'use strict';

var fs = require('fs');
var path = require('path');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');

// ── Config ──────────────────────────────────────────────────────────────────
var CONTAINER = process.env.ERP_PG_CONTAINER || 'postgres';
var DB = process.env.ERP_PG_DB || 'idempiere';
var PGUSER = process.env.ERP_PG_USER || 'adempiere';
var SCHEMA = process.env.ERP_PG_SCHEMA || 'adempiere';
var OUT = process.env.ERP_OUT ||
  path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var ONLY = (process.env.ERP_TABLES || '').split(',').map(function (s) {
  return s.trim().toLowerCase();
}).filter(Boolean);
var MAXBUF = 512 * 1024 * 1024; // 512MB — largest table is ~11MB, ample headroom.

// ── PG helpers ──────────────────────────────────────────────────────────────
// Metadata query: tuples-only, unaligned, tab field separator.
function pgMeta(sql) {
  var out = execFileSync('docker',
    ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', DB,
      '-t', '-A', '-F', '\t', '-c', sql],
    { maxBuffer: MAXBUF, encoding: 'utf8' });
  return out.split('\n').filter(function (l) { return l.length > 0; })
    .map(function (l) { return l.split('\t'); });
}
// COPY a table out in TEXT format. Returns the raw payload (one row per line).
function pgCopy(sql) {
  return execFileSync('docker',
    ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', DB, '-c', sql],
    { maxBuffer: MAXBUF, encoding: 'utf8' });
}

// ── COPY TEXT unescape (PostgreSQL copy.c rules) ─────────────────────────────
// A bare field "\N" is NULL. Else decode C-escapes in a single left-to-right
// pass so a literal backslash (emitted as "\\") is never mis-read.
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

// PG data_type -> SQLite affinity. Loose by design; only BLOB needs real care.
function affinity(t) {
  if (t === 'bytea') return 'BLOB';
  if (t === 'smallint' || t === 'integer' || t === 'bigint') return 'INTEGER';
  if (t === 'numeric' || t === 'decimal' || t === 'real' ||
    t === 'double precision') return 'NUMERIC';
  return 'TEXT';
}
function q(id) { return '"' + String(id).replace(/"/g, '""') + '"'; }

// ── Main ────────────────────────────────────────────────────────────────────
function main() {
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  if (fs.existsSync(OUT)) fs.unlinkSync(OUT);
  var sqlite = new Database(OUT);
  sqlite.pragma('journal_mode = OFF');
  sqlite.pragma('synchronous = OFF');

  // 1) Base tables (exclude views — handled separately).
  var tables = pgMeta(
    "SELECT table_name FROM information_schema.tables " +
    "WHERE table_schema='" + SCHEMA + "' AND table_type='BASE TABLE' " +
    "ORDER BY table_name").map(function (r) { return r[0]; });
  if (ONLY.length) tables = tables.filter(function (t) { return ONLY.indexOf(t) >= 0; });

  // 2) All columns for those tables, in ordinal order, with data_type.
  var colRows = pgMeta(
    "SELECT table_name, column_name, data_type, ordinal_position " +
    "FROM information_schema.columns WHERE table_schema='" + SCHEMA + "' " +
    "ORDER BY table_name, ordinal_position");
  var cols = {}; // table -> [{name, type}]
  colRows.forEach(function (r) {
    var tn = r[0];
    if (!cols[tn]) cols[tn] = [];
    cols[tn].push({ name: r[1], type: r[2] });
  });

  var totalRows = 0, doneTables = 0, blobTables = 0;
  console.log('§MIGRATE start tables=' + tables.length + ' out=' + OUT);

  tables.forEach(function (tn) {
    var cs = cols[tn];
    if (!cs || !cs.length) {
      console.log('§MIGRATE skip table=' + tn + ' (no columns)');
      return;
    }
    // CREATE TABLE (loose affinity).
    var ddl = 'CREATE TABLE ' + q(tn) + ' (' +
      cs.map(function (c) { return q(c.name) + ' ' + affinity(c.type); }).join(', ') +
      ')';
    sqlite.exec(ddl);

    var blobIdx = cs.map(function (c, i) { return c.type === 'bytea' ? i : -1; })
      .filter(function (i) { return i >= 0; });
    if (blobIdx.length) blobTables++;

    // COPY data out (explicit column list = guaranteed order match).
    var collist = cs.map(function (c) { return q(c.name); }).join(',');
    var payload = pgCopy('COPY (SELECT ' + collist + ' FROM ' + q(SCHEMA) + '.' + q(tn) +
      ') TO STDOUT');

    var ins = sqlite.prepare('INSERT INTO ' + q(tn) + ' VALUES (' +
      cs.map(function () { return '?'; }).join(',') + ')');

    // payload ends with a trailing newline; split and drop the empty tail.
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
            // bytea TEXT output is "\x<hex>"; decode to a real BLOB.
            v = (v.slice(0, 2) === '\\x')
              ? Buffer.from(v.slice(2), 'hex')
              : Buffer.from(v, 'binary');
          }
          vals[fi] = v;
        }
        ins.run(vals);
        n++;
      }
    });
    tx();
    totalRows += n;
    doneTables++;
  });

  // 3) Flagged subset — explicit, loud.
  var seqCount = +pgMeta("SELECT count(*) FROM information_schema.sequences " +
    "WHERE sequence_schema='" + SCHEMA + "'")[0][0];
  var fnCount = +pgMeta("SELECT count(*) FROM information_schema.routines " +
    "WHERE routine_schema='" + SCHEMA + "'")[0][0];
  var trgCount = +pgMeta("SELECT count(DISTINCT trigger_name) FROM information_schema.triggers " +
    "WHERE trigger_schema='" + SCHEMA + "'")[0][0];

  // Views: SNAPSHOT definitions (deferred translation). Only when migrating full set.
  var viewCount = 0, viewSnap = 0;
  sqlite.exec('CREATE TABLE _pg_views (view_name TEXT, definition TEXT)');
  if (!ONLY.length) {
    var views = pgMeta("SELECT table_name FROM information_schema.views " +
      "WHERE table_schema='" + SCHEMA + "' ORDER BY table_name")
      .map(function (r) { return r[0]; });
    viewCount = views.length;
    var vins = sqlite.prepare('INSERT INTO _pg_views VALUES (?,?)');
    var vtx = sqlite.transaction(function () {
      views.forEach(function (vn) {
        var def = pgCopy("COPY (SELECT pg_get_viewdef('" + SCHEMA + '.' + vn +
          "'::regclass, true)) TO STDOUT");
        var lines = def.split('\n'); if (lines[lines.length - 1] === '') lines.pop();
        vins.run(vn, lines.map(unescape).join('\n'));
        viewSnap++;
      });
    });
    vtx();
  }

  // 4) Migration provenance + flagged-subset record.
  sqlite.exec('CREATE TABLE _migration_meta (key TEXT, value TEXT)');
  var meta = sqlite.prepare('INSERT INTO _migration_meta VALUES (?,?)');
  [['source', 'pg:' + DB + '/' + SCHEMA], ['tables', String(doneTables)],
  ['rows', String(totalRows)], ['blob_tables', String(blobTables)],
  ['flagged_sequences', String(seqCount) + ' (dropped)'],
  ['flagged_functions', String(fnCount) + ' (skipped)'],
  ['flagged_triggers', String(trgCount) + ' (skipped)'],
  ['flagged_views', String(viewCount) + ' (snapshot=' + viewSnap + ', deferred)']
  ].forEach(function (kv) { meta.run(kv[0], kv[1]); });

  // 5) Rule/policy corpus accounting — read BACK from the migrated SQLite (proves
  //    the §1-stripped layer survived raw). Guarded: only when the full set ran.
  var ruleLine = '';
  if (!ONLY.length) {
    function cnt(sql) { try { return sqlite.prepare(sql).get().c; } catch (e) { return 'NA'; } }
    var nRule = cnt('SELECT count(*) c FROM ad_rule');
    var nVal = cnt('SELECT count(*) c FROM ad_val_rule');
    var nCallout = cnt("SELECT count(*) c FROM ad_column WHERE callout IS NOT NULL AND callout<>''");
    // docTypeFlags F = how many of the §0.9 policy-flag columns survived on c_doctype.
    // C_DocType policy flags MOrder.completeIt() branches on (DeliveryRule lives on
    // C_Order, not here — §0.9). All four are stripped from ad_seed.db, kept raw here.
    var flagCols = ['isautogenerateinout', 'isautogenerateinvoice', 'docsubtypeso', 'docsubtypeinv'];
    var present = sqlite.prepare("SELECT name FROM pragma_table_info('c_doctype')").all()
      .map(function (r) { return r.name.toLowerCase(); });
    var nFlags = flagCols.filter(function (c) { return present.indexOf(c) >= 0; }).length;
    ruleLine = '§MIGRATE rules AD_Rule=' + nRule + ' AD_Val_Rule=' + nVal +
      ' callouts=' + nCallout + ' docTypeFlags=' + nFlags + '/' + flagCols.length +
      ' docTypes=' + cnt('SELECT count(*) c FROM c_doctype');
  }

  sqlite.close();

  // ── §-log acceptance ────────────────────────────────────────────────────
  console.log('§MIGRATE tables=' + doneTables + ' rows=' + totalRows +
    ' blobTables=' + blobTables);
  if (ruleLine) console.log(ruleLine);
  console.log('§MIGRATE flagged sequences=' + seqCount + ' (dropped) functions=' +
    fnCount + ' (skipped) triggers=' + trgCount + ' (skipped) views=' + viewCount +
    ' (snapshot=' + viewSnap + ', deferred)');
}

main();
