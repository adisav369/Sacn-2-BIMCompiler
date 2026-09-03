#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * build_all_shards.js — D2 driver: emit one T2 module shard per tree-10 top-level menu group,
 *   then the SET-level §SHARD-SET aggregate (the witness ERP_SHARD_GENERATOR.md §6/§7-D2 names).
 *
 * PRIME RULE: EXTRACT, do not invent. The group set is DISCOVERED from build/erp/ad_full.db
 *   (tree 10, parent_id=0|NULL). Groups with zero active W-windows are REPORTED (§SHARD-SKIP),
 *   never synthesized. Each shard is produced by scripts/build_erp_shard.js --module (the engine);
 *   this driver only iterates + aggregates.
 *
 * READ THE LOG AFTER EVERY RUN. Exit code is not evidence.
 * Run:  node scripts/build_all_shards.js 2>&1 | tee build/erp/logs/build_all_shards.log
 * Env:  ERP_SRC=ad_full.db  ERP_T0=ad_seed_gen.db  ERP_SHARD_BUDGET_MB=13  ERP_DEMO_CLIENT=11
 */
'use strict';

var fs = require('fs');
var path = require('path');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');

var ROOT = path.join(__dirname, '..');
var SRC = process.env.ERP_SRC || path.join(ROOT, 'build', 'erp', 'ad_full.db');
var SHARDDIR = process.env.ERP_SHARD_DIR || path.join(ROOT, 'build', 'erp', 'shards');
// T0 size reference for the §SHARD-SET tiers line (the closed seed the shards stream into).
var T0 = process.env.ERP_T0 || path.join(ROOT, 'build', 'erp', 'ad_seed_gen.db');
// "none should approach the 43 MB blob" — the T0 instant budget (13 MB) is the oversized ceiling.
var BUDGET_MB = process.env.ERP_SHARD_BUDGET_MB ? +process.env.ERP_SHARD_BUDGET_MB : 13;
var ENGINE = path.join(__dirname, 'build_erp_shard.js');
var COVERAGE = path.join(__dirname, 'erp_shard_coverage.js');
var MANIFEST = path.join(__dirname, 'build_shard_manifest.js');
var MAXBUF = 512 * 1024 * 1024;

function slug(name) {
  return String(name).toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 28);
}

if (!fs.existsSync(SRC)) { console.error('FATAL: no source: ' + SRC); process.exit(2); }
var src = new Database(SRC, { readonly: true });
// top-level tree-10 menu groups (summary nodes + any leaf-window roots) — DISCOVERED, non-invent.
var groups = src.prepare(
  "SELECT tn.node_id id, m.name name, m.issummary summ FROM ad_treenodemm tn" +
  " JOIN ad_menu m ON tn.node_id=m.ad_menu_id" +
  " WHERE tn.ad_tree_id=10 AND (tn.parent_id=0 OR tn.parent_id IS NULL) ORDER BY tn.seqno").all();
// recursive active-W-window count for each group (mirrors build_erp_shard.js buildModule walk).
var wq = src.prepare(
  "WITH RECURSIVE dn(node) AS (SELECT ?" +
  "  UNION SELECT tn.node_id FROM ad_treenodemm tn JOIN dn ON tn.parent_id=dn.node WHERE tn.ad_tree_id=10)" +
  " SELECT count(DISTINCT m.ad_window_id) c FROM dn JOIN ad_menu m ON dn.node=m.ad_menu_id" +
  " WHERE m.action='W' AND m.isactive='Y' AND m.ad_window_id IS NOT NULL");
groups.forEach(function (g) { g.win = wq.get(g.id).c; g.slug = slug(g.name); });
src.close();

console.log('§SHARD-DRIVER groups=' + groups.length + ' (top-level tree-10, parent=0|NULL) src=' + path.basename(SRC) +
  ' budget<=' + BUDGET_MB + 'MB');
fs.mkdirSync(SHARDDIR, { recursive: true });

var built = [], empty = [], maxMB = 0;
groups.forEach(function (g) {
  if (!g.win) {
    empty.push(g);
    console.log('§SHARD-SKIP group=' + g.id + ':' + g.name.trim() + ' windows=0 (no data shard — reported, not synthesized)');
    return;
  }
  var outName = g.id + '-' + g.slug + '.db';
  var outPath = path.join(SHARDDIR, outName);
  var env = Object.assign({}, process.env, { ERP_SRC: SRC, ERP_OUT: outPath });
  var o = execFileSync('node', [ENGINE, '--module', String(g.id)], { encoding: 'utf8', env: env, maxBuffer: MAXBUF });
  process.stdout.write(o);
  var mb = fs.statSync(outPath).size / 1048576;
  if (mb > maxMB) maxMB = mb;
  // per-shard COVERAGE gate (offline self-sufficiency over T0 ATTACH shard). execFileSync throws on a
  // non-zero exit (true dangle); capture either way and surface the §SHARD-COVERAGE line.
  var dangle = 0;
  try {
    var c = execFileSync('node', [COVERAGE, T0, outPath, String(g.id), SRC], { encoding: 'utf8', env: env, maxBuffer: MAXBUF });
    process.stdout.write(c);
  } catch (e) {
    process.stdout.write((e.stdout || '') + (e.stderr || ''));
    dangle = 1;
  }
  built.push({ id: g.id, name: g.name.trim(), slug: g.slug, file: outName, mb: +mb.toFixed(2), win: g.win, dangle: dangle });
});

var totalMB = built.reduce(function (s, b) { return s + b.mb; }, 0);
var t0MB = fs.existsSync(T0) ? fs.statSync(T0).size / 1048576 : 0;
var oversized = built.filter(function (b) { return b.mb > BUDGET_MB; });
var dangled = built.filter(function (b) { return b.dangle; });
console.log('');
console.log('§SHARD-SET tiers=[T0:' + t0MB.toFixed(1) + 'MB, T2:' + built.length + ' shards axis=menu-group] ' +
  'none-oversized=' + (oversized.length ? 'N' : 'Y') + ' total=' + totalMB.toFixed(2) + 'MB ' +
  'maxShard=' + maxMB.toFixed(2) + 'MB empty=' + empty.length);
console.log('§SHARD-COVERAGE-SET shards=' + built.length + ' all-dangling=0=' + (dangled.length ? 'N' : 'Y') +
  ' withDangle=' + dangled.length);
if (oversized.length) {
  console.log('§SHARD-SET WARN oversized=' + oversized.map(function (b) { return b.file + ':' + b.mb + 'MB'; }).join(','));
}
if (dangled.length) {
  console.log('§SHARD-COVERAGE-SET WARN dangled=' + dangled.map(function (b) { return b.file; }).join(','));
}

// ── manifest (the seam's manifest() payload — what to load and from where) ────
try {
  var mo = execFileSync('node', [MANIFEST, T0, SHARDDIR], { encoding: 'utf8', env: process.env, maxBuffer: MAXBUF });
  process.stdout.write(mo);
} catch (e) {
  process.stdout.write((e.stdout || '') + (e.stderr || ''));
  console.log('§SHARD-MANIFEST WARN generation failed');
  process.exit(1);
}

if (oversized.length || dangled.length) process.exit(1);
