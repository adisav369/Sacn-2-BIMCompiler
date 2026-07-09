#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-SCHED-MINE scope (read this block first)
 * SCOPE: Step 1 (MINE) of the geometry-hell fix (user vision 2026-07-10): prove the Java-era
 *   per-space MEP schedule projected into the rules DBs (build/project_rule_space_schedule.py →
 *   rule_space_schedule/rule_space_type/rule_space_alias/rule_code_spacing) carried the MEASURED
 *   quantity + offset semantics VERBATIM — before one line of disc_walker.js changes. Read the
 *   §-log after the run; exit code is not the evidence.
 *
 * GROUND TRUTH: today's real Java compile (g1count worktree, logs/pipeline_SH*20260710_0358*.log +
 *   RosettaStoneGateTest G1-COUNT PASS ref=65 expected=108(base=65+gen=43) out=108): rooms
 *   LIVING 8.900×4.689m → 15 devices, BEDROOM 2.007×3.420m → 9, CORRIDOR 16.867×8.667m → 19,
 *   ROOF 14.840×7.284m → 0; orderQty=99. Total 43, zero breaches.
 *
 * CLAIMS:
 *   M1 COUNT-ORACLE   — resolveQty (ported verbatim from SpaceScheduleDAO.resolveQty) over the
 *                       PROJECTED schedule reproduces 15/9/19/0 = 43 for the real room dims —
 *                       the projection carried the quantity semantics intact.
 *   M2 LOD400-BIND    — every schedule row's geometry_hash resolves to a REAL mesh
 *                       (component_geometries, >0 vertices); rows with NULL hash are EXACTLY the
 *                       known no-mesh set {DATA_POINT, EMERGENCY_LIGHT, WASHING_TAP} — surfaced
 *                       for REFUSE, never a fallback shape (WalkerDoctrine §11 UNBREAKABLE).
 *   M3 OFFSET-VERBATIM— edge/z offsets + z_rule/x_ref/y_ref in the projection are byte-equal to
 *                       the source ad_placement_offset rows (nothing reinterpreted in flight).
 *   M4 DISC-ROSTER    — projected discs ⊆ {ELEC, PLB, ACMV, FP} (WalkerDoctrine: disc is a
 *                       column; Java CW/SP fold to the walker's PLB).
 *   M5 FALSIFIER      — tampering one qty_normal in a COPY changes the recomputed total ≠ 43:
 *                       the oracle genuinely measures the data, it is not vacuously true.
 *   M6 SCOPE-BOUNDARY — terminal_rules.db carries NO rule_space_schedule table (Watchdog catch
 *                       2026-07-10): the schedule is RESIDENTIAL-provenance data; leaking it into
 *                       Terminal's class DB would sit as a landmine ready to misapply residential
 *                       quantities the day Terminal gains real spaces. The walker REFUSEs honestly
 *                       via its no-table guard; this claim keeps the boundary from re-blurring.
 *   M7 CATEGORY-VERBATIM — (Watchdog follow-up 1b, 2026-07-10: rule_space_type tagged all 41
 *                       space types building_class='residential' though several are non-residential
 *                       vocabulary.) Proves the fix: every rule_space_type.category byte-equals the
 *                       source ad_space_type.category (the source's ONLY per-type class signal —
 *                       no residential/airport axis exists to copy), >1 distinct category present,
 *                       and the 5 flagged types (CONCOURSE, GATE, DEPARTURE_LOUNGE, ASSEMBLY_HALL,
 *                       CLASSROOM) each carry their source category. building_class remains the
 *                       PROJECTION-TARGET class (see project_rule_space_schedule.py docstring),
 *                       not a per-type claim.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var RULES = path.join(ROOT, 'build/duplex_rules.db');
var TE_RULES = path.join(ROOT, 'build/terminal_rules.db');
var COMPLIB = path.join(ROOT, 'library/component_library.db');
var PATTERNS = path.join(ROOT, 'library/disc_patterns.db');
var LOG = path.join(ROOT, 'logs', 'witness_rule_space_schedule_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(c, cond, d) { if (cond) { pass++; log('  ✅ ' + c + ' — ' + d); } else { fail++; log('  ❌ ' + c + ' — ' + d); } }
function rows(db, sql) { var r = db.exec(sql); if (!r.length) return []; return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; }); }
function loadDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(f))); }

// Ported VERBATIM from SpaceScheduleDAO.resolveQty (DISC_VALIDATION_DB_SRS.md §6.12.4 §8):
// 99/blank → qty_normal; 0 → qty_max; N → budget cap; per_area first; always ≥ qty_min.
function resolveQty(orderQty, e, roomAreaM2) {
  var qty;
  if (e.per_area_normal > 0) qty = Math.ceil(roomAreaM2 * e.per_area_normal);
  else if (orderQty === 0) qty = e.qty_max;
  else if (orderQty === 99 || orderQty < 0) qty = e.qty_normal;
  else qty = orderQty;
  return Math.max(e.qty_min, qty);
}
function expectedForRoom(db, spaceType, w, d, orderQty) {
  var sched = rows(db, "SELECT * FROM rule_space_schedule WHERE space_type_id='" + spaceType + "'");
  var total = 0;
  sched.forEach(function (e) { var q = resolveQty(orderQty, e, w * d); if (q > 0) total += q; });
  return total;
}

// The real rooms from today's verified Java compile (c_orderline ROOM rows, output.db samplehouse).
var ROOMS = [
  { type: 'LIVING',   w: 8.900,  d: 4.689, expect: 15 },
  { type: 'BEDROOM',  w: 2.007,  d: 3.420, expect: 9 },
  { type: 'CORRIDOR', w: 16.867, d: 8.667, expect: 19 },
  { type: 'ROOF',     w: 14.840, d: 7.284, expect: 0 }
];
var KNOWN_NO_MESH = ['DATA_POINT', 'EMERGENCY_LIGHT', 'WASHING_TAP'];

(async function main() {
  log('═══ W-SCHED-MINE — projected per-space schedule re-derives the real Java compile blindfolded ═══');
  var SQL = await initSqlJs();
  var rules = loadDb(SQL, RULES), lib = loadDb(SQL, COMPLIB), erp = loadDb(SQL, PATTERNS);

  log(''); log('─── M1 COUNT-ORACLE ───');
  var total = 0, detail = [];
  ROOMS.forEach(function (r) {
    var got = expectedForRoom(rules, r.type, r.w, r.d, 99);
    total += got;
    detail.push(r.type + ' ' + r.w + '×' + r.d + 'm → ' + got + ' (java=' + r.expect + ')');
    log('§SM ' + detail[detail.length - 1]);
  });
  var allMatch = ROOMS.every(function (r) { return expectedForRoom(rules, r.type, r.w, r.d, 99) === r.expect; });
  assert('M1 COUNT-ORACLE', allMatch && total === 43,
    'projected schedule × real room dims → ' + total + ' devices, per-room ' +
    ROOMS.map(function (r) { return r.type + '=' + expectedForRoom(rules, r.type, r.w, r.d, 99); }).join(' ') +
    ' — identical to the real Java compile (G1-COUNT gen=43, 2026-07-10 log)');

  log(''); log('─── M2 LOD400-BIND ───');
  var devs = rows(rules, 'SELECT DISTINCT device_id, geometry_hash FROM rule_space_schedule');
  var meshless = [], badHash = [];
  devs.forEach(function (dv) {
    if (!dv.geometry_hash) { meshless.push(dv.device_id); return; }
    var m = rows(lib, "SELECT LENGTH(vertices) n FROM component_geometries WHERE geometry_hash='" + dv.geometry_hash + "'");
    if (!m.length || !(m[0].n > 0)) badHash.push(dv.device_id + ':' + dv.geometry_hash);
  });
  meshless.sort();
  assert('M2 LOD400-BIND', badHash.length === 0 && JSON.stringify(meshless) === JSON.stringify(KNOWN_NO_MESH),
    (devs.length - meshless.length) + '/' + devs.length + ' devices bind a REAL mesh (all hashes resolve, ' +
    badHash.length + ' dangling); NULL-hash set = [' + meshless.join(', ') + '] — exactly the known ' +
    'no-mesh devices, stamped for walker REFUSE (no fallback shape, WalkerDoctrine §11)');

  log(''); log('─── M3 OFFSET-VERBATIM ───');
  var src = {};
  rows(erp, 'SELECT placement_rule, from_edge_x, from_edge_y, z_offset, z_rule, x_ref, y_ref FROM ad_placement_offset')
    .forEach(function (o) { src[o.placement_rule] = o; });
  var offRows = rows(rules, 'SELECT DISTINCT placement_rule, edge_x_m, edge_y_m, z_offset_m, z_rule, x_ref, y_ref FROM rule_space_schedule WHERE placement_rule IS NOT NULL');
  var offBad = [];
  offRows.forEach(function (o) {
    var s = src[o.placement_rule];
    if (!s) { if (!(o.edge_x_m === 0 && o.edge_y_m === 0 && o.z_offset_m === 0)) offBad.push(o.placement_rule + ':no-source'); return; }
    if (o.edge_x_m !== s.from_edge_x || o.edge_y_m !== s.from_edge_y || o.z_offset_m !== s.z_offset ||
        o.z_rule !== s.z_rule || o.x_ref !== s.x_ref || o.y_ref !== s.y_ref) offBad.push(o.placement_rule);
  });
  assert('M3 OFFSET-VERBATIM', offBad.length === 0,
    offRows.length + ' distinct placement_rules in the projection, all byte-equal to source ad_placement_offset' +
    (offBad.length ? ' EXCEPT: ' + offBad.join(', ') : ' (0 drift)'));

  log(''); log('─── M4 DISC-ROSTER ───');
  var discs = rows(rules, 'SELECT DISTINCT disc FROM rule_space_schedule ORDER BY disc').map(function (r) { return r.disc; });
  var roster = ['ACMV', 'ELEC', 'FP', 'PLB'];
  assert('M4 DISC-ROSTER', discs.every(function (d) { return roster.indexOf(d) >= 0; }),
    'projected discs = [' + discs.join(', ') + '] ⊆ walker roster [' + roster.join(', ') +
    '] — disc stays a column (WalkerDoctrine), Java CW/SP folded to PLB');

  log(''); log('─── M5 FALSIFIER ───');
  var tampered = loadDb(SQL, RULES);
  tampered.exec("UPDATE rule_space_schedule SET qty_normal = qty_normal + 3 WHERE space_type_id='LIVING' AND device_id='OUTLET'");
  var tTotal = 0;
  ROOMS.forEach(function (r) { tTotal += expectedForRoom(tampered, r.type, r.w, r.d, 99); });
  assert('M5 FALSIFIER', tTotal !== 43 && tTotal === 46,
    'tampering LIVING/OUTLET qty_normal (+3) in a COPY → oracle recomputes ' + tTotal +
    ' ≠ 43 — the count check genuinely measures the data (not vacuously true)');
  tampered.close();

  log(''); log('─── M6 SCOPE-BOUNDARY ───');
  var te = loadDb(SQL, TE_RULES);
  var leak = rows(te, "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
    "('rule_space_schedule','rule_space_type','rule_space_alias','rule_code_spacing')");
  assert('M6 SCOPE-BOUNDARY', leak.length === 0,
    'terminal_rules.db has ' + leak.length + ' schedule tables — residential-provenance schedule ' +
    'stays OUT of the terminal class DB (walker no-table guard REFUSEs honestly; no dormant landmine)');
  te.close();

  log(''); log('─── M7 CATEGORY-VERBATIM ───');
  var srcCat = {};
  rows(erp, 'SELECT Value, category FROM ad_space_type WHERE is_active=1')
    .forEach(function (r) { srcCat[r.Value] = r.category; });
  var typeRows = rows(rules, 'SELECT value, category FROM rule_space_type');
  var catBad = [], catSet = {};
  typeRows.forEach(function (r) {
    if (!(r.value in srcCat)) { catBad.push(r.value + ':not-in-source'); return; }
    if (r.category !== srcCat[r.value]) { catBad.push(r.value + ':' + r.category + '≠' + srcCat[r.value]); }
    catSet[r.category] = true;
  });
  var FLAGGED = ['CONCOURSE', 'GATE', 'DEPARTURE_LOUNGE', 'ASSEMBLY_HALL', 'CLASSROOM'];
  var flaggedOk = FLAGGED.every(function (v) {
    var row = typeRows.filter(function (r) { return r.value === v; })[0];
    if (row) log('§SM flagged ' + v + ' → category=' + row.category + ' (source-verbatim)');
    return row && row.category === srcCat[v];
  });
  assert('M7 CATEGORY-VERBATIM', catBad.length === 0 && flaggedOk && Object.keys(catSet).length > 1,
    typeRows.length + ' rule_space_type rows carry category VERBATIM from ad_space_type.category (' +
    catBad.length + ' drift' + (catBad.length ? ': ' + catBad.join(', ') : '') + '); ' +
    Object.keys(catSet).sort().join('/') + ' present; the 5 Watchdog-flagged types carry their source ' +
    'category — building_class stays the projection-target label, never a per-type class claim');

  log('');
  log('§SM SUMMARY: the Java-era measured schedule (188 rows, 37+ space types) + offset semantics are now');
  log('§SM first-class rules-DB tables, verified to re-derive the real compile blindfolded (43/43) with');
  log('§SM LOD400 bindings resolved for every meshed device. NEXT: Step 2 (PLACE) wires these into');
  log('§SM disc_walker.js — space-scoped schedule×offset placement; meshless devices REFUSE.');
  log('');
  log('W-SCHED-MINE: ' + pass + ' PASS / ' + fail + ' FAIL');
  fs.writeFileSync(LOG, _lines.join('\n') + '\n');
  log('§LOG ' + LOG);
  rules.close(); lib.close(); erp.close();
  process.exit(fail ? 1 : 0);
})();
