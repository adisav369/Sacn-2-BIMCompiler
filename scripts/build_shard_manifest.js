#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * build_shard_manifest.js — emit the shard MANIFEST the seam serves (ENGINE_CONTRACT §1 manifest(ctx) /
 *   §3 sharding policy). Data-lane hook ONLY — produces build/erp/shards/manifest.json; touches NO renderer.
 *
 * The manifest is the engine's answer to "what is worth loading and from where". For every table the
 * DataSource may need it records {table, menuGroup, resident, contentHash}:
 *   - resident=true  → T0 (the precached seed) carries the table WITH DATA → DataSource reads it locally,
 *                      no fetch (IDEMPIERE_DATA_STREAMING_SPEC §3 selection: "T0 if the table is in seed").
 *   - resident=false → streamed: the named menuGroup's T2 shard carries it; fetch + ATTACH on window-open.
 *   - contentHash    → sha256 over the table's rows (verify-not-trust; ENGINE_CONTRACT §3 content-hash).
 * A table carried by several module shards yields one entry per shard (the DataSource picks the shard of
 * the module being opened). A `shards` index maps menuGroup → shard file + whole-file hash.
 *
 * NON-INVENT: every table/row read from real artifacts (T0 seed + build/erp/shards/*.db). Mutates nothing.
 * Run:  node scripts/build_shard_manifest.js [<T0.db>] [<shardsDir>] [<out.json>]
 * READ the §SHARD-MANIFEST line before any conclusion.
 */
'use strict';

var fs = require('fs');
var path = require('path');
var crypto = require('crypto');
var Database = require('better-sqlite3');

var ROOT = path.join(__dirname, '..');
var T0 = process.argv[2] || process.env.ERP_T0 || path.join(ROOT, 'build', 'erp', 'ad_seed_gen.db');
var SHARDDIR = process.argv[3] || process.env.ERP_SHARD_DIR || path.join(ROOT, 'build', 'erp', 'shards');
var OUT = process.argv[4] || path.join(SHARDDIR, 'manifest.json');

function q(id) { return '"' + String(id).replace(/"/g, '""') + '"'; }

// sha256 over a table's rows in natural (build) order — deterministic: each shard/seed is one
// INSERT…SELECT, so scan order is stable. Returns {hash, rows}.
function hashTable(db, name) {
  var h = crypto.createHash('sha256');
  var n = 0;
  var it = db.prepare('SELECT * FROM ' + q(name) + ' ').iterate();
  for (var row of it) { h.update(JSON.stringify(Object.values(row))); h.update('\n'); n++; }
  return { hash: h.digest('hex'), rows: n };
}
function fileHash(p) {
  return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
}
function tablesOf(db) {
  return db.prepare("SELECT lower(name) n FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
    .all().map(function (r) { return r.n; });
}

if (!fs.existsSync(T0)) { console.error('FATAL: no T0 seed: ' + T0); process.exit(2); }

// ── T0 resident set (tables the seed carries WITH DATA) ──────────────────────
var t0 = new Database(T0, { readonly: true });
var tables = [];                                  // the flat [{table, menuGroup, resident, contentHash}]
var t0Resident = {};                              // table -> true (carries rows)
var groupName = {};                               // group menu id -> name (the dictionary lives in T0)
t0.prepare("SELECT ad_menu_id id, name FROM ad_menu").all().forEach(function (r) { groupName[r.id] = r.name; });
var t0Tables = tablesOf(t0);
t0Tables.forEach(function (t) {
  var hr = hashTable(t0, t);
  if (hr.rows > 0) {
    t0Resident[t] = true;
    tables.push({ table: t, menuGroup: 'T0', resident: true, contentHash: hr.hash });
  }
  // empty T0 tables (schema-only business tables in --seed-login) are NOT resident — they stream via a shard.
});
t0.close();
var residentCount = tables.length;

// ── module shards ────────────────────────────────────────────────────────────
var shardFiles = fs.existsSync(SHARDDIR)
  ? fs.readdirSync(SHARDDIR).filter(function (f) { return /^\d+-.*\.db$/.test(f); }).sort()
  : [];
var shards = [];
shardFiles.forEach(function (f) {
  var gid = f.match(/^(\d+)-/)[1];
  var p = path.join(SHARDDIR, f);
  var db = new Database(p, { readonly: true });
  var gname = groupName[gid];                      // from T0 (shards no longer carry the dictionary)
  var sts = tablesOf(db);
  var nonEmpty = 0;
  sts.forEach(function (t) {
    var hr = hashTable(db, t);
    if (hr.rows === 0) return;                     // empty table in the shard → nothing to stream, skip entry
    nonEmpty++;
    tables.push({ table: t, menuGroup: +gid, resident: !!t0Resident[t], contentHash: hr.hash });
  });
  db.close();
  shards.push({ menuGroup: +gid, name: gname ? String(gname).trim() : null, file: f,
    bytes: fs.statSync(p).size, tables: nonEmpty, contentHash: fileHash(p) });
});

// ── emit ──────────────────────────────────────────────────────────────────────
var manifest = {
  version: 1,
  t0: { file: path.basename(T0), bytes: fs.statSync(T0).size, residentTables: residentCount },
  axis: 'menu-group',
  shards: shards,
  tables: tables
};
var json = JSON.stringify(manifest, null, 2);
fs.writeFileSync(OUT, json);
var manifestHash = crypto.createHash('sha256').update(json).digest('hex');

console.log('§SHARD-MANIFEST tables=' + tables.length + ' residentT0=' + residentCount +
  ' streamed=' + (tables.length - residentCount) + ' shards=' + shards.length +
  ' hash=' + manifestHash.slice(0, 16) + ' out=' + path.basename(OUT));
