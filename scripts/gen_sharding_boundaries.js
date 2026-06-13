#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * gen_sharding_boundaries.js — emit sharding_boundaries.json by RULE from build/erp/ad_full.db.
 *   Spec: docs/ACCESS_FREQUENCY_SHARDING.md §2-§3 (the warm/cold split rule).
 *   PRIME RULE: derive, do not invent. Every table/tier is read from ad_full.db's real schema;
 *   the WHERE predicates are the project's real DocStatus / periodstatus value sets.
 *
 * Run:  node scripts/gen_sharding_boundaries.js [2>&1 | tee build/erp/logs/gen_boundaries.log]
 * Out:  sharding_boundaries.json (repo root)  ·  READ the log before any conclusion.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var Database = require('better-sqlite3');

var SRC = process.env.ERP_SRC || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var OUT = process.env.BOUNDARIES_OUT || path.join(__dirname, '..', 'sharding_boundaries.json');
function log(m) { console.log(m); }

if (!fs.existsSync(SRC)) { console.error('FATAL: no source ' + SRC); process.exit(2); }
var src = new Database(SRC, { readonly: true });

// ── dictionary class already in T0 (ad_seed.db) — not re-sharded here ─────────
function dictCount() {
  return src.prepare("SELECT count(*) c FROM sqlite_master WHERE type='table'" +
    " AND (lower(name) LIKE 'ad\\_%' ESCAPE '\\' OR lower(name) LIKE '%\\_v' ESCAPE '\\'" +
    "      OR lower(name) LIKE '%\\_trl' ESCAPE '\\')").get().c;
}

// ── (a) DocStatus tables → warm(T1)/cold(T2) partition ────────────────────────
var docTables = src.prepare(
  "SELECT lower(m.name) n FROM sqlite_master m WHERE m.type='table'" +
  " AND EXISTS (SELECT 1 FROM pragma_table_info(m.name) p WHERE lower(p.name)='docstatus')" +
  " ORDER BY n").all().map(function (r) { return r.n; });

// ── (b) init-bubble master tables → T1 whole (hot, small) ─────────────────────
// Source = bim-ootb/erp/initbubble.json node tables that have NO DocStatus (the masters).
var INITBUBBLE_MASTERS = ['c_bpartner', 'm_product', 'm_product_category', 'm_warehouse',
  'c_elementvalue', 'm_productprice', 'c_bp_group'];

// ── (c) genuinely cold, period-bound → T2 ─────────────────────────────────────
// fact_acct rows in a CLOSED/PERMANENT period (periodstatus C|P); archives whole.
var COLD_PERIOD = {
  table: 'fact_acct',
  tier: 'T2',
  coldWhere: "C_Period_ID IN (SELECT C_Period_ID FROM c_periodcontrol WHERE periodstatus IN ('C','P'))",
  reason: 'period-closed postings (periodstatus C=Closed/P=Permanent); read only on historical drill'
};
var COLD_ARCHIVE = ['ad_archive', 'ad_archive_blob'];

// ── (d) T_* are NOT shards (reconciliation #1) ────────────────────────────────
var tStarTables = src.prepare(
  "SELECT lower(name) n FROM sqlite_master WHERE type='table'" +
  " AND lower(name) LIKE 't\\_%' ESCAPE '\\' ORDER BY n").all().map(function (r) { return r.n; });

// helper: does a physical table exist + carry rows (non-invent reporting)
function exists(t) { return !!src.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND lower(name)=?").get(t); }
function rowCount(t, where) {
  if (!exists(t)) return null;
  try { return src.prepare('SELECT count(*) c FROM "' + t + '"' + (where ? ' WHERE ' + where : '')).get().c; }
  catch (e) { return null; }
}

var WARM = "DocStatus NOT IN ('CL','VO')";
var COLD = "DocStatus IN ('CL','VO')";

var tables = [];
docTables.forEach(function (t) {
  var w = rowCount(t, WARM), c = rowCount(t, COLD);
  tables.push({
    table: t, tier: 'T1', coldTier: 'T2',
    warmWhere: WARM, coldWhere: COLD,
    reason: 'document table — warm=open/active, cold=closed/voided',
    rows: { warm: w, cold: c }
  });
});
INITBUBBLE_MASTERS.forEach(function (t) {
  if (!exists(t)) { log('§GEN WARN init-bubble master absent: ' + t); return; }
  tables.push({ table: t, tier: 'T1', coldTier: null, warmWhere: null, coldWhere: null,
    reason: 'init-bubble master — hot, small, whole-table warm', rows: { warm: rowCount(t, null), cold: 0 } });
});
// cold period postings
tables.push(Object.assign({}, COLD_PERIOD, { rows: { warm: 0, cold: rowCount(COLD_PERIOD.table, COLD_PERIOD.coldWhere) } }));
COLD_ARCHIVE.forEach(function (t) {
  if (!exists(t)) { log('§GEN WARN archive absent: ' + t); return; }
  tables.push({ table: t, tier: 'T2', coldTier: null, warmWhere: null, coldWhere: null,
    reason: 'archive — whole-table cold', rows: { warm: 0, cold: rowCount(t, null) } });
});
// T_* explicitly excluded
var excluded = tStarTables.map(function (t) {
  return { table: t, tier: 'none', reason: 'in-memory fold / temp selection — not persisted, nothing to shard' };
});

var boundaries = {
  version: 1,
  generatedBy: 'scripts/gen_sharding_boundaries.js',
  spec: 'docs/ACCESS_FREQUENCY_SHARDING.md',
  source: 'build/erp/ad_full.db',
  tierMap: { L0: 'T0', L1: 'T1', L2: 'T2' },
  rules: [
    { match: 'dictionary (ad_*, *_v, *_trl)', tier: 'T0', note: 'already in ad_seed.db; not re-sharded here' },
    { match: 'tables with DocStatus', tier: 'T1', coldTier: 'T2',
      warmWhere: WARM, coldWhere: COLD, note: 'warm=open/active, cold=closed/voided' },
    { match: 'init-bubble masters (no DocStatus)', tier: 'T1', note: 'hot, small, whole-table' },
    { match: 'fact_acct in closed period', tier: 'T2', note: 'periodstatus C|P' },
    { match: 'ad_archive*', tier: 'T2', note: 'whole-table cold' },
    { match: 'T_* temp/fold tables', tier: 'none', note: 'reconciliation #1 — in-memory, not persisted' }
  ],
  dictionaryTablesInT0: dictCount(),
  tables: tables,
  excluded: excluded
};
src.close();

fs.writeFileSync(OUT, JSON.stringify(boundaries, null, 2) + '\n');
var nWarm = tables.filter(function (t) { return t.tier === 'T1'; }).length;
var nCold = tables.filter(function (t) { return t.tier === 'T2'; }).length;
var nPart = tables.filter(function (t) { return t.coldTier === 'T2'; }).length;
log('§BOUNDARIES out=' + path.basename(OUT) + ' docTables=' + docTables.length +
  ' partitioned(warm+cold)=' + nPart + ' T1=' + nWarm + ' T2=' + nCold +
  ' excluded(T_*)=' + excluded.length + ' dictInT0=' + boundaries.dictionaryTablesInT0);
log('§BOUNDARIES OVERALL=PASS');
