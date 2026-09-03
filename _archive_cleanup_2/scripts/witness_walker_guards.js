#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-GUARD-* / W-PRIORITY-ORDER / W-GUARD-ABSTRACT scope (read this first)
 * SCOPE: Witnesses for the universal guard pass (prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md §2/§5).
 *   Each guard is exercised by a SYNTHETIC PERTURBATION on REAL Terminal geometry — a clean
 *   candidate (from a real element) PASSES; the SAME candidate, perturbed to violate one guard,
 *   is caught (REFUSE/flag). Every test NAMES the failure it proves the guard catches.
 *   Oracle/source of real geometry = pristine Terminal_extracted.db, NEVER output.db.
 *   Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS:
 *   W-GUARD-CONTAINMENT — a candidate pushed past the envelope → REFUSE(gap), not placed.
 *   W-GUARD-SURFACE     — a candidate floated off its host plane → HARD(refuse); a small drift → SOFT snap+flag.
 *   W-GUARD-ORIENT      — a candidate forced to face +Z (off a horizontal host normal) → HARD (measured, no class).
 *   W-GUARD-CLASH       — a 2nd candidate overlapping a placed one → REFUSED/flagged (clash bus works).
 *   W-GUARD-SOURCE      — a candidate requiring a source with an empty path → REFUSED (mid-air).
 *   W-GUARD-REFUSE      — when a HARD guard fails, a signed WALKER_GAP op is emitted; ZERO fabricated placement.
 *   W-PRIORITY-ORDER    — a low-priority candidate places first; a higher-priority one reads the bus and is refused.
 *   W-GUARD-ABSTRACT    — the guard LOGIC contains ZERO IFC-class / discipline / role tokens (door doctrine).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var WG = require(path.join(ROOT, 'deploy/dev/walker_guards.js'));
var GUARDS_SRC = path.join(ROOT, 'deploy/dev/walker_guards.js');
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_walker_guards_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

function aabbOf(r) {
  // r: { cx,cy,cz, bx,by,bz } — full extents; AABB = centre ± half-extent (MEASURED, not invented).
  return { minX: r.cx - r.bx / 2, maxX: r.cx + r.bx / 2,
           minY: r.cy - r.by / 2, maxY: r.cy + r.by / 2,
           minZ: r.cz - r.bz / 2, maxZ: r.cz + r.bz / 2 };
}

(async function main() {
  log('═══ W-GUARD-* — universal guard pass on REAL Terminal perturbations ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));

  // Real geometry: a sample of columns + the building envelope (both MEASURED from extracted.db).
  var rows = db.exec(
    "SELECT m.guid, t.center_x, t.center_y, t.center_z, t.bbox_x, t.bbox_y, t.bbox_z " +
    "FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid WHERE m.ifc_class='IfcColumn'")[0]
    .values.map(function (v) { return { guid: v[0], cx: v[1], cy: v[2], cz: v[3], bx: v[4], by: v[5], bz: v[6] }; });
  var envRow = db.exec(
    "SELECT min(center_x-bbox_x/2),max(center_x+bbox_x/2),min(center_y-bbox_y/2),max(center_y+bbox_y/2)," +
    "min(center_z-bbox_z/2),max(center_z+bbox_z/2) FROM element_transforms")[0].values[0];
  var envelope = { minX: envRow[0], maxX: envRow[1], minY: envRow[2], maxY: envRow[3], minZ: envRow[4], maxZ: envRow[5] };
  db.close();
  log('§GUARD envelope: x[' + envelope.minX.toFixed(0) + ',' + envelope.maxX.toFixed(0) + '] y[' +
      envelope.minY.toFixed(0) + ',' + envelope.maxY.toFixed(0) + '] z[' + envelope.minZ.toFixed(0) + ',' +
      envelope.maxZ.toFixed(0) + '], ' + rows.length + ' real columns');

  var base = rows[0], ctx = { envelope: envelope };
  function cand(extra) {
    var c = { id: base.guid, aabb: aabbOf(base), pos: [base.cx, base.cy, base.cz], priority: 1 };
    return Object.assign(c, extra || {});
  }

  // ── W-GUARD-CONTAINMENT ──
  var cleanC = WG.wgContainment(cand(), ctx);
  var outside = cand(); outside.aabb = aabbOf({ cx: envelope.maxX + 50, cy: base.cy, cz: base.cz, bx: base.bx, by: base.by, bz: base.bz });
  var outC = WG.wgContainment(outside, ctx);
  assert('W-GUARD-CONTAINMENT',
    cleanC.status === 'pass' && outC.status === 'hard',
    'real column in-envelope=' + cleanC.status + '; pushed +50m past maxX → ' + outC.status + ' (overhang ' + outC.measure.toFixed(1) + 'm)');

  // ── W-GUARD-SURFACE ──  host plane = horizontal at the column base z (n=[0,0,1], d=base z).
  var baseZ = base.cz - base.bz / 2;
  var plane = { normal: [0, 0, 1], d: baseZ };
  var adhered = cand({ hostPlane: plane, seat: [base.cx, base.cy, baseZ] });
  var drifted = cand({ hostPlane: plane, seat: [base.cx, base.cy, baseZ + 0.15] });   // 0.15m < snap 0.30
  var floating = cand({ hostPlane: plane, seat: [base.cx, base.cy, baseZ + 1.0] });    // 1m → detached
  var sA = WG.wgSurface(adhered, ctx), sD = WG.wgSurface(drifted, ctx), sF = WG.wgSurface(floating, ctx);
  assert('W-GUARD-SURFACE',
    sA.status === 'pass' && sD.status === 'soft' && !!sD.snap && sF.status === 'hard',
    'adhered=' + sA.status + ', drift 0.15m=' + sD.status + ' (snap z→' + (sD.snap ? sD.snap[2].toFixed(2) : '—') + '), float 1m=' + sF.status);

  // ── W-GUARD-ORIENT ──  host is a vertical surface (normal horizontal); a facing along it = pass,
  //    a facing flipped to +Z = impossible. MEASURED angle; no class branch.
  var hostN = [1, 0, 0];                       // a vertical wall facing +X
  var aligned = cand({ normal: [1, 0, 0], hostNormal: hostN });
  var faceUp = cand({ normal: [0, 0, 1], hostNormal: hostN });
  var oA = WG.wgOrientation(aligned, ctx), oU = WG.wgOrientation(faceUp, ctx);
  assert('W-GUARD-ORIENT',
    oA.status === 'pass' && oU.status === 'hard',
    'facing along host=' + oA.status + ' (' + (oA.measure * 180 / Math.PI).toFixed(0) + '°); forced +Z=' + oU.status + ' (' + (oU.measure * 180 / Math.PI).toFixed(0) + '°)');

  // ── W-GUARD-CLASH ──  place a real column, then a 2nd candidate ON it.
  var placedBus = [{ id: base.guid, aabb: aabbOf(base) }];
  var clear = cand(); clear.aabb = aabbOf({ cx: base.cx + 20, cy: base.cy, cz: base.cz, bx: base.bx, by: base.by, bz: base.bz });
  var onTop = cand({ id: 'SW2D-overlap' });    // same aabb as the placed column → full penetration
  var cClear = WG.wgClash(clear, { placed: placedBus }), cHit = WG.wgClash(onTop, { placed: placedBus });
  assert('W-GUARD-CLASH',
    cClear.status === 'pass' && cHit.status === 'hard',
    'clear@+20m=' + cClear.status + '; overlapping placed=' + cHit.status + ' (pen ' + cHit.measure.toFixed(2) + 'm vs ' + cHit.withId + ')');

  // ── W-GUARD-SOURCE ──  a routed/loaded candidate requiring a path; empty = mid-air.
  var sourced = cand({ requiresPath: true, path: ['main', 'riser', 'branch'] });
  var midair = cand({ requiresPath: true, path: [] });
  var srcOk = WG.wgSource(sourced, ctx), srcBad = WG.wgSource(midair, ctx);
  assert('W-GUARD-SOURCE',
    srcOk.status === 'pass' && srcBad.status === 'hard',
    'path of 3 hops=' + srcOk.status + '; empty path=' + srcBad.status + ' (mid-air)');

  // ── W-GUARD-REFUSE ──  a candidate failing a HARD guard → a signed WALKER_GAP op, no placement.
  var doomed = cand({ id: 'SW2D-doomed' });
  doomed.aabb = aabbOf({ cx: envelope.maxX + 80, cy: base.cy, cz: base.cz, bx: base.bx, by: base.by, bz: base.bz });
  var ev = WG.wgEvaluate(doomed, ctx);
  assert('W-GUARD-REFUSE',
    ev.outcome === 'REFUSE' && ev.signal === 'RED' && ev.op.opType === 'WALKER_GAP' &&
    ev.confidence === 0 && ev.op.params.reasons.length > 0,
    'outcome=' + ev.outcome + ', op=' + ev.op.opType + ', conf=' + ev.confidence + ', reasons=' + ev.op.params.reasons.length + ' (zero fabricated placement)');

  // ── W-PRIORITY-ORDER ──  two candidates at the SAME spot, different priority. Lower places first
  //    into the bus; the higher reads the bus and is REFUSED on clash. Proves order + shared state.
  var first = { id: 'SW2D-pri-lo', aabb: aabbOf(base), pos: [base.cx, base.cy, base.cz], priority: 1 };
  var second = { id: 'SW2D-pri-hi', aabb: aabbOf(base), pos: [base.cx, base.cy, base.cz], priority: 2 };
  var batch = WG.wgRunPass([second, first], { envelope: envelope });   // deliberately out of order
  var firstResult = batch.results.find(function (r) { return r.id === 'SW2D-pri-lo'; });
  var secondResult = batch.results.find(function (r) { return r.id === 'SW2D-pri-hi'; });
  var firstIdx = batch.results.indexOf(firstResult), secondIdx = batch.results.indexOf(secondResult);
  assert('W-PRIORITY-ORDER',
    firstIdx < secondIdx && firstResult.outcome !== 'REFUSE' && secondResult.outcome === 'REFUSE',
    'lower-priority evaluated first (idx ' + firstIdx + '<' + secondIdx + '), placed=' + firstResult.outcome +
    '; higher read the bus → ' + secondResult.outcome + ' (clash); placed=' + batch.placed + ' refused=' + batch.refused);

  // ── W-GUARD-ABSTRACT ──  the guard LOGIC has ZERO class/discipline/role tokens. Strip comments
  //    (block + line) first — the human-facing doctrine may be CITED by name in comments; what must
  //    be class-agnostic is the executable code (no class-keyed branch — the door doctrine).
  var src = fs.readFileSync(GUARDS_SRC, 'utf8');
  var code = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  // NOTE 'window' is excluded as a bare role token (it is the JS browser global, used legitimately in
  // the export shim); the IFC class form IfcWindow is still caught by the /Ifc[A-Z]/ pattern.
  var forbidden = [/Ifc[A-Z]/, /\b(STR|MEP|ARC|ELEC|ACMV|FP|CW|SP)\b/,
    /\b(door|wall|column|beam|pipe|duct|slab|girder|joist|truss|riser|fitting)\b/i];
  var hits = [];
  forbidden.forEach(function (re) { var m = code.match(re); if (m) hits.push(m[0]); });
  // ('main' in W-GUARD-SOURCE is test data, not in the module; 'riser' check above guards the module.)
  assert('W-GUARD-ABSTRACT',
    hits.length === 0,
    'guard logic (comments stripped) class/discipline tokens = ' + (hits.length ? hits.join(',') : 'ZERO') + ' (door doctrine held)');

  log('───────────────────────────────────────────────');
  log('W-GUARD-* : ' + pass + ' PASS / ' + fail + ' FAIL');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
