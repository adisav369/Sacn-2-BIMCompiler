#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * build_erp_shard.js — ERP closure + shard generator (Layer 1).
 *   Spec: docs/ERP_SHARD_GENERATOR.md  ·  Policy: docs/ERP_DATA_COMPLETENESS_POLICY.md §3
 *   Gate: scripts/erp_shard_integrity.sh → must print §SHARD-CLOSURE … dangling=0.
 *
 * PRIME RULE: EXTRACT, do not invent. Every row a real copy from build/erp/ad_full.db
 *   (the closed superset) or a real EXTRACT from the running PG (the 3 view-backed targets).
 *
 * Mode --seed-login (this deliverable, D1): emit ad_seed = login ∪ dictionary closure.
 *   - Dictionary-class closure is WHOLE-TABLE (justified: the 146 FK-display targets total
 *     6,889 rows — all small — so whole-table stays inside the instant budget; §SHARD-GENERATOR §3).
 *   - Table set = renderer dictionary read-set (mirrors ad_parser/ad_data/idmp_session.js)
 *     ∪ displayed-FK-display targets (resolveFK convention: ColumnName minus _ID; §2)
 *     ∪ 3 view-backed targets MATERIALIZED from PG (real rows; back the All-Users/Roles/Tenants windows).
 *   - Output local only (build/erp/ad_seed_gen.db). DEPLOY is HOLD-for-GO (do NOT overwrite the
 *     renderer's bim-ootb/erp/ad_seed.db here).
 *
 * READ THE LOG AFTER EVERY RUN. Exit code is not evidence.
 * Run:  node scripts/build_erp_shard.js --seed-login [2>&1 | tee build/erp/logs/build_seed.log]
 * Env:  ERP_SRC=/path/ad_full.db  ERP_OUT=/path/out.db  ERP_PG_DB=idempiere
 */
'use strict';

var fs = require('fs');
var path = require('path');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');

// ── Config ──────────────────────────────────────────────────────────────────
var SRC = process.env.ERP_SRC || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var OUT = process.env.ERP_OUT || path.join(__dirname, '..', 'build', 'erp', 'ad_seed_gen.db');
// T0 reference (the precached seed). --module subtracts T0's dictionary class so a T2 shard never
// duplicates the scaffold (ERP_SHARD_GENERATOR §3; DataSource selection IDEMPIERE_DATA_STREAMING_SPEC §3).
var T0 = process.env.ERP_T0 || path.join(__dirname, '..', 'build', 'erp', 'ad_seed_gen.db');
var CONTAINER = process.env.ERP_PG_CONTAINER || 'postgres';
var PGDB = process.env.ERP_PG_DB || 'idempiere';
var PGUSER = process.env.ERP_PG_USER || 'adempiere';
var SCHEMA = process.env.ERP_PG_SCHEMA || 'adempiere';
var MAXBUF = 512 * 1024 * 1024;
var argv = process.argv.slice(2);
// --seed-login : dictionary+login closure, business tables SCHEMA-ONLY (data streams via T1/T2).
// --seed-demo  : same closure, but business tables carry client 0/DEMO_CLIENT rows (GardenWorld) so the
//                seed renders WITH data until streaming exists (the deployable demo seed). Both CLOSED.
// --module <group_menu_id> : a T2 data shard for one menu-group (IDEMPIERE_DATA_STREAMING_SPEC §5).
//   Carries the group's windows' tab tables (header+child) + _trl + FK-master targets, client 0/DEMO_CLIENT
//   rows — the DATA the dictionary (T0) streams in. ATTACHed beside ad_seed.db. Dictionary NOT duplicated.
var _mi = argv.indexOf('--module');
var MODULE = _mi >= 0 ? argv[_mi + 1] : null;
// --rekey-client <src> <dst> : clone client <src>'s access subgraph as <dst> with a deterministic PK
//   offset so BOTH coexist (D3; ERP_SHARD_GENERATOR §4.3). The FK-rewrite migrate_pg_to_sqlite.js:169 skips.
var _rk = argv.indexOf('--rekey-client');
var REKEY = _rk >= 0 ? { src: +argv[_rk + 1], dst: +argv[_rk + 2] } : null;
var REKEY_OFFSET = process.env.ERP_REKEY_OFFSET ? +process.env.ERP_REKEY_OFFSET : 10000000;
var MODE = REKEY != null ? 'rekey'
  : (MODULE != null ? 'module'
    : (argv.indexOf('--seed-demo') >= 0 ? 'seed-demo'
      : (argv.indexOf('--seed-login') >= 0 ? 'seed-login' : null)));
var DEMO_CLIENT = process.env.ERP_DEMO_CLIENT ? +process.env.ERP_DEMO_CLIENT : 11;

// Renderer dictionary read-set — NON-INVENT: grep of bim-ootb/erp/{ad_parser,ad_data,idmp_session}.js
// (2026-06-02). Uses ad_treenodemm (not ad_treenode) + ad_element; does NOT read ad_ref_table
// (resolveFK is convention-only, ad_data.js:332-339).
var DICT_CORE = ['ad_user', 'ad_user_roles', 'ad_role', 'ad_client', 'ad_role_orgaccess', 'ad_org',
  'ad_window_access', 'ad_menu', 'ad_treenodemm', 'ad_window', 'ad_tab', 'ad_field', 'ad_column',
  'ad_table', 'ad_element', 'ad_reference', 'ad_ref_list'];
// View-backed FK targets to materialize from PG (real rows; 12 total). User decision 2026-06-02.
var VIEWS = ['ad_allusers_v', 'ad_allroles_v', 'ad_allclients_v'];

// Column projection for the THREE wide dictionary tables (the 75% of seed bytes). NON-INVENT: the kept
// columns are EXACTLY those the renderer reads (ad_parser.js getFields/getTabs, ad_data.js keyColumn is
// convention-only). The DROPPED columns (help, description, columnsql, *logic, po_* translations, _uu,
// audit) are mostly DEFAULT/rarely-read → the LAZIEST tier (user 2026-06-02): not in instant T0; served
// on-demand via T1 range over ad_full (which keeps every column) when help/translations/logic are needed.
var PROJECT = {
  ad_field: ['ad_field_id', 'name', 'description', 'seqno', 'isdisplayed', 'displaylogic',
    'ismandatory', 'isreadonly', 'defaultvalue', 'ad_column_id', 'ad_tab_id', 'isactive'],
  ad_column: ['ad_column_id', 'columnname', 'ad_reference_id', 'fieldlength', 'ismandatory',
    'iskey', 'isidentifier', 'defaultvalue', 'isactive'],
  ad_element: ['ad_element_id', 'columnname', 'name', 'isactive'] // read by nothing in render; keep minimal
};

function log(m) { console.log(m); }
function q(id) { return '"' + String(id).replace(/"/g, '""') + '"'; }

// ── PG helpers (mirror migrate_pg_to_sqlite.js) ──────────────────────────────
function pgMeta(sql) {
  var o = execFileSync('docker', ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', PGDB,
    '-t', '-A', '-F', '\t', '-c', sql], { maxBuffer: MAXBUF, encoding: 'utf8' });
  return o.split('\n').filter(function (l) { return l.length > 0; })
    .map(function (l) { return l.split('\t'); });
}
function pgCopy(sql) {
  return execFileSync('docker', ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', PGDB, '-c', sql],
    { maxBuffer: MAXBUF, encoding: 'utf8' });
}
function unescape(s) {
  if (s === '\\N') return null;
  if (s.indexOf('\\') === -1) return s;
  var out = '';
  for (var i = 0; i < s.length; i++) {
    var c = s[i];
    if (c !== '\\') { out += c; continue; }
    var n = s[++i];
    switch (n) {
      case 'n': out += '\n'; break; case 't': out += '\t'; break;
      case 'r': out += '\r'; break; case 'b': out += '\b'; break;
      case 'f': out += '\f'; break; case 'v': out += '\v'; break;
      case '\\': out += '\\'; break;
      default: out += (n === undefined ? '\\' : n); break;
    }
  }
  return out;
}
function affinity(t) {
  if (t === 'bytea') return 'BLOB';
  if (t === 'smallint' || t === 'integer' || t === 'bigint') return 'INTEGER';
  if (t === 'numeric' || t === 'decimal' || t === 'real' || t === 'double precision') return 'NUMERIC';
  return 'TEXT';
}

// Displayed FK-display targets (resolveFK convention) that are real AD_Tables in src.
function fkTargets(src) {
  var rows = src.prepare(
    "WITH wl AS (SELECT DISTINCT ad_window_id wid FROM ad_menu WHERE action='W' AND isactive='Y' AND ad_window_id IS NOT NULL)," +
    " fkcol AS (SELECT DISTINCT c.columnname cn FROM ad_field f" +
    "   JOIN ad_tab tt ON f.ad_tab_id=tt.ad_tab_id JOIN wl ON tt.ad_window_id=wl.wid" +
    "   JOIN ad_column c ON f.ad_column_id=c.ad_column_id" +
    "   WHERE f.isdisplayed='Y' AND f.isactive='Y' AND tt.isactive='Y'" +
    "     AND c.ad_reference_id IN (18,19,30) AND upper(c.columnname) LIKE '%\\_ID' ESCAPE '\\')," +
    " tgt AS (SELECT DISTINCT lower(substr(cn,1,length(cn)-3)) tn FROM fkcol)" +
    " SELECT tn FROM tgt WHERE tn IN (SELECT lower(tablename) FROM ad_table)").all();
  return rows.map(function (r) { return r.tn; });
}

function materializeView(out, v) {
  var cols = pgMeta("SELECT column_name,data_type FROM information_schema.columns WHERE table_schema='" +
    SCHEMA + "' AND table_name='" + v + "' ORDER BY ordinal_position");
  if (!cols.length) { log('§SHARD-GEN WARN view absent in PG: ' + v); return; }
  var colDefs = cols.map(function (c) { return q(c[0]) + ' ' + affinity(c[1]); }).join(', ');
  out.exec('DROP TABLE IF EXISTS ' + q(v));
  out.exec('CREATE TABLE ' + q(v) + ' (' + colDefs + ')');
  var payload = pgCopy('COPY (SELECT * FROM ' + SCHEMA + '.' + q(v) + ') TO STDOUT');
  var lines = payload.split('\n').filter(function (l) { return l.length > 0; });
  var ins = out.prepare('INSERT INTO ' + q(v) + ' VALUES (' + cols.map(function () { return '?'; }).join(',') + ')');
  var n = 0;
  out.exec('BEGIN');
  lines.forEach(function (line) { ins.run(line.split('\t').map(unescape)); n++; });
  out.exec('COMMIT');
  log('§SHARD-GEN view-materialized=' + v + ' cols=' + cols.length + ' rows=' + n);
}

// T0-resident DICTIONARY class (ad_*/_v tables the precached seed carries). A T2 module shard subtracts
// this set so it never re-ships the scaffold (the DataSource serves those from T0; §3). Returns {} (no
// exclusion) if the seed is absent — but warns loudly, since the shard will then bloat.
function t0DictSet() {
  if (!fs.existsSync(T0)) {
    log('§SHARD-GEN WARN no T0 seed at ' + T0 + ' — dictionary NOT excluded (shard will bloat; run --seed-login first)');
    return {};
  }
  var t0 = new Database(T0, { readonly: true });
  var rows = t0.prepare("SELECT lower(name) n FROM sqlite_master WHERE type IN ('table','view')" +
    " AND (lower(name) LIKE 'ad\\_%' ESCAPE '\\' OR lower(name) LIKE '%\\_v' ESCAPE '\\')").all();
  t0.close();
  var set = {}; rows.forEach(function (r) { set[r.n] = 1; });
  return set;
}

// ── T2 module shard (one menu-group's DATA) ──────────────────────────────────
function buildModule(group) {
  if (!fs.existsSync(SRC)) { console.error('FATAL: no source: ' + SRC); process.exit(2); }
  var src = new Database(SRC, { readonly: true });
  // 1) descendant W-windows of the group node (tree 10 recursive)
  var wins = src.prepare(
    "WITH RECURSIVE dn(node) AS (" +
    "  SELECT node_id FROM ad_treenodemm WHERE ad_tree_id=10 AND node_id=?" +
    "  UNION SELECT tn.node_id FROM ad_treenodemm tn JOIN dn ON tn.parent_id=dn.node WHERE tn.ad_tree_id=10)" +
    " SELECT DISTINCT m.ad_window_id wid FROM dn JOIN ad_menu m ON dn.node=m.ad_menu_id" +
    " WHERE m.action='W' AND m.isactive='Y' AND m.ad_window_id IS NOT NULL").all(group);
  var winIds = wins.map(function (r) { return r.wid; });
  var gname = (src.prepare("SELECT name FROM ad_menu WHERE ad_menu_id=?").get(group) || {}).name || ('group' + group);
  if (!winIds.length) { console.error('FATAL: no W-windows under group ' + group + ' (' + gname + ')'); process.exit(2); }
  var inWin = winIds.join(',');
  // 2) tab tables (header+child) of those windows
  var tabTables = src.prepare(
    "SELECT DISTINCT lower(tbl.tablename) tn FROM ad_tab tt JOIN ad_table tbl ON tt.ad_table_id=tbl.ad_table_id" +
    " WHERE tt.isactive='Y' AND tt.ad_window_id IN (" + inWin + ") AND tbl.tablename IS NOT NULL").all()
    .map(function (r) { return r.tn; });
  // 3) displayed-FK target master tables of those windows (resolveFK convention)
  var fkt = src.prepare(
    "WITH fkcol AS (SELECT DISTINCT c.columnname cn FROM ad_field f JOIN ad_tab tt ON f.ad_tab_id=tt.ad_tab_id" +
    "   JOIN ad_column c ON f.ad_column_id=c.ad_column_id WHERE tt.ad_window_id IN (" + inWin + ")" +
    "   AND f.isdisplayed='Y' AND f.isactive='Y' AND tt.isactive='Y' AND c.ad_reference_id IN (18,19,30)" +
    "   AND upper(c.columnname) LIKE '%\\_ID' ESCAPE '\\')" +
    " SELECT DISTINCT lower(substr(cn,1,length(cn)-3)) tn FROM fkcol WHERE tn IN (SELECT lower(tablename) FROM ad_table)").all()
    .map(function (r) { return r.tn; });
  // union + _trl variants that exist physically; only real tables. EXCLUDE the dictionary class already
  // resident in T0: per ERP_SHARD_GENERATOR §3 ("Dictionary NOT duplicated") + the DataSource selection
  // rule (T0 if the table is in ad_seed.db; IDEMPIERE_DATA_STREAMING_SPEC §3), a T2 shard that re-ships
  // the ad_column/ad_field/ad_element scaffold (53k+ rows) is dead weight — the renderer reads it from T0.
  var srcTables = {};
  src.prepare("SELECT lower(name) n FROM sqlite_master WHERE type='table'").all().forEach(function (r) { srcTables[r.n] = 1; });
  var t0Dict = t0DictSet();
  var set = {}, hasClient = {}, excluded = 0, trlDeferred = 0;
  tabTables.concat(fkt).forEach(function (t) {
    if (!srcTables[t]) return;
    if (/_trl$/.test(t)) { trlDeferred++; return; }          // translations → laziest T1 tier (online range), not T2
    if (t0Dict[t]) { excluded++; return; }                   // dictionary class — served from T0, not duplicated
    set[t] = 1;
  });
  var shardTables = Object.keys(set).sort();
  shardTables.forEach(function (t) {
    if (src.prepare("SELECT 1 FROM pragma_table_info(?) WHERE lower(name)='ad_client_id'").get(t)) hasClient[t] = 1;
  });
  src.close();
  log('§SHARD-GEN module group=' + group + ':' + gname + ' windows=' + winIds.length +
    ' tabTables=' + tabTables.length + ' fkTargets=' + fkt.length +
    ' t0DictExcluded=' + excluded + ' trlDeferred(T1)=' + trlDeferred + ' shardTables=' + shardTables.length);
  // 4) build shard with client 0/DEMO_CLIENT data
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  if (fs.existsSync(OUT)) fs.unlinkSync(OUT);
  var out = new Database(OUT); out.pragma('journal_mode = OFF'); out.pragma('synchronous = OFF');
  out.exec("ATTACH '" + SRC.replace(/'/g, "''") + "' AS src");
  var rows = 0;
  out.exec('BEGIN');
  shardTables.forEach(function (t) {
    var where = hasClient[t] ? ' WHERE ad_client_id IN (0,' + DEMO_CLIENT + ')' : '';
    out.exec('CREATE TABLE ' + q(t) + ' AS SELECT * FROM src.' + q(t) + where);
    rows += out.prepare('SELECT count(*) c FROM ' + q(t)).get().c;
  });
  out.exec('COMMIT'); out.exec('DETACH src'); out.close();
  var bytes = fs.statSync(OUT).size;
  // Per-module line (§SHARD-MODULE). The SET-level §SHARD-SET aggregate over all module shards is owned
  // by the driver scripts/build_all_shards.js (avoids a per-call/set witness-name collision).
  log('§SHARD-MODULE group=' + group + ':' + gname.trim() + ' tables=' + shardTables.length + ' rows=' + rows +
    ' MB=' + (bytes / 1048576).toFixed(2) + ' client=0/' + DEMO_CLIENT + ' out=' + path.basename(OUT));
}

// ── Main ─────────────────────────────────────────────────────────────────────
function buildSeed() {
  if (!fs.existsSync(SRC)) { console.error('FATAL: no source: ' + SRC); process.exit(2); }

  var src = new Database(SRC, { readonly: true });
  var srcTables = {};
  src.prepare("SELECT lower(name) n FROM sqlite_master WHERE type='table'").all()
    .forEach(function (r) { srcTables[r.n] = 1; });

  var viewSet = {}; VIEWS.forEach(function (v) { viewSet[v] = 1; });
  var targets = fkTargets(src);
  var set = {};
  DICT_CORE.concat(targets).forEach(function (t) {
    if (viewSet[t]) return;                                  // view-backed → materialized from PG, not copied
    if (srcTables[t]) set[t] = 1;
    else log('§SHARD-GEN WARN absent-in-src skipped: ' + t);
  });
  // AD_* dictionary/login tables carry ROWS (the scaffold); business FK-target tables (c_*, m_*, …) are
  // SCHEMA-ONLY in --seed-login (data streams via T1/T2). In --seed-demo they carry client 0/DEMO_CLIENT
  // rows so the seed renders WITH GardenWorld data until streaming exists. Detect ad_client_id presence now.
  var withRows = [], schemaOnly = [], hasClient = {};
  Object.keys(set).sort().forEach(function (t) {
    if (t.indexOf('ad_') === 0) { withRows.push(t); return; }
    schemaOnly.push(t);
    var cols = src.prepare("SELECT 1 FROM pragma_table_info(?) WHERE lower(name)='ad_client_id'").get(t);
    if (cols) hasClient[t] = 1;
  });
  src.close();
  log('§SHARD-GEN ' + MODE + ' core=' + DICT_CORE.length + ' fkTargets=' + targets.length +
    ' withRows=' + withRows.length + ' business=' + schemaOnly.length + ' views=' + VIEWS.length +
    ' src=' + path.basename(SRC) + (MODE === 'seed-demo' ? ' demoClient=' + DEMO_CLIENT : ''));

  if (fs.existsSync(OUT)) fs.unlinkSync(OUT);
  var out = new Database(OUT);
  out.pragma('journal_mode = OFF');
  out.pragma('synchronous = OFF');
  out.exec("ATTACH '" + SRC.replace(/'/g, "''") + "' AS src");
  var rowsum = 0, projected = [];
  out.exec('BEGIN');
  withRows.forEach(function (t) {
    var cols = PROJECT[t] ? PROJECT[t].map(q).join(', ') : '*';
    if (PROJECT[t]) projected.push(t + ':' + PROJECT[t].length + 'cols');
    out.exec('CREATE TABLE ' + q(t) + ' AS SELECT ' + cols + ' FROM src.' + q(t));
    rowsum += out.prepare('SELECT count(*) c FROM ' + q(t)).get().c;
  });
  if (projected.length) log('§SHARD-GEN projected ' + projected.join(' ') +
    ' (detail cols → laziest T1 tier)');
  var bizRows = 0;
  schemaOnly.forEach(function (t) {
    var where;
    if (MODE === 'seed-demo') {
      // GardenWorld demo data — real client 0/DEMO_CLIENT rows (non-invent). Tables without ad_client_id
      // (reference data) copied whole; the rest filtered to the demo tenant + System.
      where = hasClient[t] ? ' WHERE ad_client_id IN (0,' + DEMO_CLIENT + ')' : '';
    } else {
      where = ' WHERE 0';                                    // --seed-login: present but empty (closure, no bloat)
    }
    out.exec('CREATE TABLE ' + q(t) + ' AS SELECT * FROM src.' + q(t) + where);
    bizRows += out.prepare('SELECT count(*) c FROM ' + q(t)).get().c;
  });
  out.exec('COMMIT');
  out.exec('DETACH src');
  log('§SHARD-GEN copied dict-withRows=' + withRows.length + ' rows=' + rowsum +
    ' business=' + schemaOnly.length + ' bizRows=' + bizRows +
    (MODE === 'seed-demo' ? ' (client 0/' + DEMO_CLIENT + ' data)' : ' (schema-only, stream via T1/T2)'));

  VIEWS.forEach(function (v) { materializeView(out, v); });

  // login witness — System login needs role 0 (System Administrator) + its window-access rows.
  var role0 = out.prepare('SELECT count(*) c FROM ad_role WHERE ad_role_id=0').get().c;
  var wa0 = out.prepare('SELECT count(*) c FROM ad_window_access WHERE ad_role_id=0').get().c;
  out.close();
  var bytes = fs.statSync(OUT).size;
  log('§IDEMPIERE-LOGIN system hasRoles=' + (role0 > 0 && wa0 > 0 ? 'Y' : 'N') +
    ' role0=' + role0 + ' windowAccess(role0)=' + wa0);
  log('§SHARD-GEN out=' + OUT + ' bytes=' + bytes + ' MB=' + (bytes / 1048576).toFixed(1) +
    ' budget<=13MB=' + (bytes <= 13 * 1048576 ? 'Y' : 'N'));
}

// ── D3: re-key a client (clone access subgraph src→dst, coexisting) ──────────
// Root = client <src>'s LOGIN/ACCESS rows (already present whole in the seed — DICT_CORE copies them).
// Each cloned row: ad_client_id → dst; ad_org_id/ad_role_id/ad_user_id → +REKEY_OFFSET (via maps built
// from the real src ids — NON-INVENT, a deterministic surrogate-key offset, no synthesis); ad_window_id
// and audit cols (createdby/updatedby) are KEPT — they resolve to the RETAINED src rows. So src + dst
// coexist and the login graph stays closed. Business-data clone (c_*/m_* rows for dst) is a heavier
// follow-up, deliberately NOT bundled — the §CLIENT-SWITCH witness is roles+windows (the access surface).
var ACCESS = ['ad_client', 'ad_org', 'ad_role', 'ad_user', 'ad_user_roles', 'ad_role_orgaccess', 'ad_window_access'];
function rekeyClient(src, dst) {
  if (!fs.existsSync(SRC)) { console.error('FATAL: no source: ' + SRC); process.exit(2); }
  if (!process.env.ERP_OUT && /ad_seed_gen\.db$/.test(OUT)) {     // don't clobber the T0 seed by default
    OUT = path.join(__dirname, '..', 'build', 'erp', 'ad_seed_rekey.db');
  }
  // 1) base seed (client 0/src business + whole dictionary incl every client's access rows).
  MODE = 'seed-demo'; DEMO_CLIENT = src;
  log('§REKEY base: building seed-demo (client 0/' + src + ') into ' + path.basename(OUT));
  buildSeed();

  // 2) clone the access subgraph src→dst with the PK offset.
  var db = new Database(OUT);
  db.pragma('journal_mode = OFF'); db.pragma('synchronous = OFF');
  function mapOf(table, idcol) {
    var m = {};
    db.prepare('SELECT ' + idcol + ' v FROM ' + q(table) + ' WHERE ad_client_id=?').all(src)
      .forEach(function (r) { if (r.v != null) m[r.v] = r.v + REKEY_OFFSET; });
    return m;
  }
  var orgMap = mapOf('ad_org', 'ad_org_id');
  var roleMap = mapOf('ad_role', 'ad_role_id');
  var userMap = mapOf('ad_user', 'ad_user_id');
  function assertFree(table, idcol, map) {
    var ids = Object.keys(map).map(function (k) { return map[k]; });
    if (!ids.length) return;
    var hit = db.prepare('SELECT count(*) c FROM ' + q(table) + ' WHERE ' + idcol + ' IN (' + ids.join(',') + ')').get().c;
    if (hit) { console.error('FATAL: rekey offset collision in ' + table + ' (' + hit + ' target ids already exist) — raise ERP_REKEY_OFFSET'); process.exit(2); }
  }
  assertFree('ad_org', 'ad_org_id', orgMap);
  assertFree('ad_role', 'ad_role_id', roleMap);
  assertFree('ad_user', 'ad_user_id', userMap);
  function remap(col, v) {
    if (v == null) return v;
    var c = col.toLowerCase();
    if (c === 'ad_client_id') return dst;
    if (c === 'ad_org_id') return (orgMap[v] != null ? orgMap[v] : v);   // org 0=* (not in map) → kept
    if (c === 'ad_role_id') return (roleMap[v] != null ? roleMap[v] : v);
    if (c === 'ad_user_id') return (userMap[v] != null ? userMap[v] : v);
    return v;                                                            // ad_window_id, createdby/updatedby → kept
  }
  var counts = {};
  db.exec('BEGIN');
  ACCESS.forEach(function (t) {
    var cols = db.prepare("SELECT name FROM pragma_table_info(?)").all(t).map(function (r) { return r.name; });
    var rows = db.prepare('SELECT * FROM ' + q(t) + ' WHERE ad_client_id=?').all(src);
    counts[t] = 0;
    if (!rows.length) return;
    var ins = db.prepare('INSERT INTO ' + q(t) + ' (' + cols.map(q).join(',') + ') VALUES (' +
      cols.map(function () { return '?'; }).join(',') + ')');
    rows.forEach(function (row) { ins.run(cols.map(function (col) { return remap(col, row[col]); })); counts[t]++; });
  });
  db.exec('COMMIT');

  var roles = db.prepare('SELECT count(*) c FROM ad_role WHERE ad_client_id=?').get(dst).c;
  var windows = db.prepare('SELECT count(DISTINCT ad_window_id) c FROM ad_window_access WHERE ad_client_id=?').get(dst).c;
  var clientName = (db.prepare('SELECT name FROM ad_client WHERE ad_client_id=?').get(dst) || {}).name;
  var bytes = fs.statSync(OUT).size;
  db.close();
  log('§REKEY cloned ' + ACCESS.map(function (t) { return t + '=' + counts[t]; }).join(' ') + ' offset=' + REKEY_OFFSET);
  log('§CLIENT-SWITCH client=' + dst + ':' + (clientName ? String(clientName).trim() : '?') +
    ' roles=' + roles + ' windows=' + windows + ' src=' + src + ' out=' + path.basename(OUT) +
    ' MB=' + (bytes / 1048576).toFixed(1));
  log('§REKEY gate: run scripts/erp_shard_integrity.sh ' + OUT + ' — must print dangling=0');
}

// ── Dispatch ─────────────────────────────────────────────────────────────────
if (MODE === 'module') buildModule(MODULE);
else if (MODE === 'rekey') rekeyClient(REKEY.src, REKEY.dst);
else if (MODE === 'seed-login' || MODE === 'seed-demo') buildSeed();
else { console.error('usage: build_erp_shard.js --seed-login | --seed-demo | --module <group_menu_id> | --rekey-client <src> <dst>'); process.exit(2); }
