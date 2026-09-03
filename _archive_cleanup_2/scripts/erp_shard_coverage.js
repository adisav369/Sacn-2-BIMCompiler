#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * erp_shard_coverage.js — per-shard COVERAGE gate (the check the thin seed lacked).
 *   Spec: prompts/BACKEND_LANE_SESSION.md task 2 · docs/ERP_SHARD_GENERATOR.md §3.
 *
 * A T2 module shard must let a user OPEN every window in its menu-group OFFLINE without dangling. Because
 * the shard subtracts the T0 dictionary (it is a DELTA over the seed), coverage is asserted over the
 * UNION — T0 ATTACH shard — exactly as the renderer sees it (precached seed + ATTACHed shard). The check:
 *   (a) every ACTIVE-tab base table of the group's windows is PHYSICALLY present in T0 ∪ shard;
 *   (b) every DISPLAYED FK-display target (resolveFK convention, ad_data.js:332-339) of those windows is
 *       present in T0 ∪ shard.
 * Translations (_trl) are the laziest T1 tier (online range) — REPORTED as deferred, never a coverage
 * defect (consistent with the T0 projection that drops translations to T1).
 *
 * NON-INVENT: the dictionary used to enumerate the group's windows/tabs/FKs lives in T0; this only reads
 * physical table presence — it mutates nothing.
 *
 * A missing tab/FK target is classed honestly: ABSENT-IN-SOURCE (no physical object in ad_full.db — no
 * shard can carry what the canonical source lacks; reported, non-invent) vs a true DANGLE (present in the
 * source but the shard omitted it — the only coverage DEFECT). Only dangles fail the gate.
 *
 * Run:  node scripts/erp_shard_coverage.js <T0.db> <shard.db> [<groupMenuId>] [<source.db=ad_full.db>]
 *   groupMenuId defaults to the leading "<id>-" of the shard filename (the driver's naming).
 * READ THE §SHARD-COVERAGE line before any conclusion. Exit 0 iff dangling=0.
 */
'use strict';

var fs = require('fs');
var path = require('path');
var Database = require('better-sqlite3');

var T0 = process.argv[2];
var SHARD = process.argv[3];
var GID = process.argv[4];
var SRC = process.argv[5] || process.env.ERP_SRC || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
if (!T0 || !SHARD) { console.error('usage: erp_shard_coverage.js <T0.db> <shard.db> [<groupMenuId>] [<source.db>]'); process.exit(2); }
if (!fs.existsSync(T0)) { console.error('FATAL: no T0: ' + T0); process.exit(2); }
if (!fs.existsSync(SHARD)) { console.error('FATAL: no shard: ' + SHARD); process.exit(2); }
if (!fs.existsSync(SRC)) { console.error('FATAL: no source: ' + SRC); process.exit(2); }
if (GID == null) {
  var m = path.basename(SHARD).match(/^(\d+)-/);
  if (!m) { console.error('FATAL: cannot derive groupMenuId from ' + path.basename(SHARD) + '; pass it explicitly'); process.exit(2); }
  GID = m[1];
}

// Open T0 (carries the dictionary) and ATTACH the shard. The union table set = T0 tables ∪ shard tables.
var db = new Database(T0, { readonly: true });
db.exec("ATTACH '" + SHARD.replace(/'/g, "''") + "' AS shard");
db.exec("ATTACH '" + SRC.replace(/'/g, "''") + "' AS src");
var union = {}, srcPhysical = {};
db.prepare("SELECT lower(name) n FROM sqlite_master WHERE type IN ('table','view')").all().forEach(function (r) { union[r.n] = 1; });
db.prepare("SELECT lower(name) n FROM shard.sqlite_master WHERE type IN ('table','view')").all().forEach(function (r) { union[r.n] = 1; });
db.prepare("SELECT lower(name) n FROM src.sqlite_master WHERE type IN ('table','view')").all().forEach(function (r) { srcPhysical[r.n] = 1; });
// classify a list of required tables → {present, dangling (in source, omitted), absentInSource (non-invent)}
function classify(list) {
  var present = [], dangling = [], absent = [];
  list.forEach(function (t) {
    if (union[t]) present.push(t);
    else if (srcPhysical[t]) dangling.push(t);   // source HAS it → shard SHOULD have carried it → defect
    else absent.push(t);                         // not in source either → reported, never synthesized
  });
  return { present: present, dangling: dangling, absent: absent };
}

var gname = (db.prepare("SELECT name FROM ad_menu WHERE ad_menu_id=?").get(GID) || {}).name || ('group' + GID);

// (a) active-tab base tables of the group's W-windows (mirrors build_erp_shard.js buildModule walk).
var tabTables = db.prepare(
  "WITH RECURSIVE dn(node) AS (SELECT ?" +
  "  UNION SELECT tn.node_id FROM ad_treenodemm tn JOIN dn ON tn.parent_id=dn.node WHERE tn.ad_tree_id=10)," +
  " wins AS (SELECT DISTINCT m.ad_window_id wid FROM dn JOIN ad_menu m ON dn.node=m.ad_menu_id" +
  "   WHERE m.action='W' AND m.isactive='Y' AND m.ad_window_id IS NOT NULL)" +
  " SELECT DISTINCT lower(tbl.tablename) tn FROM ad_tab tt JOIN wins ON tt.ad_window_id=wins.wid" +
  "   JOIN ad_table tbl ON tt.ad_table_id=tbl.ad_table_id" +
  "   WHERE tt.isactive='Y' AND tbl.tablename IS NOT NULL").all(GID).map(function (r) { return r.tn; });

var tabReal = tabTables.filter(function (t) { return !/_trl$/.test(t); });   // _trl → T1, not asserted
var trlDeferred = tabTables.length - tabReal.length;
var tabC = classify(tabReal);

// (b) displayed FK-display targets (resolveFK convention) of the group's windows must resolve in the union.
var fkTargets = db.prepare(
  "WITH RECURSIVE dn(node) AS (SELECT ?" +
  "  UNION SELECT tn.node_id FROM ad_treenodemm tn JOIN dn ON tn.parent_id=dn.node WHERE tn.ad_tree_id=10)," +
  " wins AS (SELECT DISTINCT m.ad_window_id wid FROM dn JOIN ad_menu m ON dn.node=m.ad_menu_id" +
  "   WHERE m.action='W' AND m.isactive='Y' AND m.ad_window_id IS NOT NULL)," +
  " fkcol AS (SELECT DISTINCT c.columnname cn FROM ad_field f JOIN ad_tab tt ON f.ad_tab_id=tt.ad_tab_id" +
  "   JOIN wins ON tt.ad_window_id=wins.wid JOIN ad_column c ON f.ad_column_id=c.ad_column_id" +
  "   WHERE f.isdisplayed='Y' AND f.isactive='Y' AND tt.isactive='Y' AND c.ad_reference_id IN (18,19,30)" +
  "     AND upper(c.columnname) LIKE '%\\_ID' ESCAPE '\\')" +
  " SELECT DISTINCT lower(substr(cn,1,length(cn)-3)) tn FROM fkcol" +
  "   WHERE tn IN (SELECT lower(tablename) FROM ad_table)").all(GID).map(function (r) { return r.tn; });
var fkC = classify(fkTargets);
db.close();

var dangling = tabC.dangling.length + fkC.dangling.length;
var absent = tabC.absent.length + fkC.absent.length;
console.log('§SHARD-COVERAGE module=' + GID + ':' + String(gname).trim() + ' shard=' + path.basename(SHARD) +
  ' tabTables=' + tabC.present.length + '/' + tabReal.length + ' fkTargets=' + fkC.present.length + '/' + fkTargets.length +
  ' trlDeferred(T1)=' + trlDeferred + ' absentInSource=' + absent + ' dangling=' + dangling + ' over=T0+shard');
if (absent) {
  console.log('  ABSENT-IN-SOURCE (non-invent, no shard can carry) tabTables=[' + tabC.absent.join(',') +
    '] fkTargets=[' + fkC.absent.join(',') + ']');
}
if (dangling) {
  console.log('  DANGLE (in source, shard omitted) tabTables=[' + tabC.dangling.join(',') +
    '] fkTargets=[' + fkC.dangling.join(',') + ']');
  process.exit(1);
}
