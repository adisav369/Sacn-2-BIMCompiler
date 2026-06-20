#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-ROUTEWALKER-MEP scope (read this block first)
 * SCOPE: Increment-1 witness for the RouteWalker MEP modeller lane
 *   (prompts/RESUME_MODELLER_FOCUS.md §NEXT). Proves the REAL routewalker.js port
 *   (deploy/dev/routewalker.js — ported from RouteWalker.java) routes Duplex + SampleHouse
 *   MEP from the REAL recipe data (deploy/dev/mep_rw.db + deploy/buildings/*_extracted.db),
 *   BEFORE the GEOM_SWEEP op-log emit seam is wired into the modeller.
 *   Non-invent: every route comes from real anchors / real room BOM recipes; no synthetic
 *   positions. Read the §-log after the run; exit code is not the evidence.
 *
 * METHOD: eval the unmodified routewalker.js into a scope with sql.js injected, set its
 *   _rwDb to the real mep_rw.db, and run rwWalk() against the real building extracted DBs.
 *   Capture the RW2D- rows it inserts and assert each claim against the real geometry.
 *
 * CLAIMS (each names the issue it proves):
 *   C1 RW_INIT      — mep_rw.db carries the real recipe (Duplex 2100 anchors, 9 patterns).
 *   C2 PATH_A_ROUTE — Path A pattern-walk pairs the REAL Duplex anchors and emits pipe
 *                     segments, each a real run (non-degenerate bbox, <= 50 m, between two
 *                     real anchor points). Proves: routing is data-driven, not invented.
 *   C3 CLASH_AWARE  — the ARC envelope (199 boxes) is loaded and the clash gate runs
 *                     (some pipes skipped, or all pass — reported, never silent).
 *   C4 CROSS_POP    — SampleHouse has NO anchors → Path B places fixtures from the room BOM
 *                     recipes + connects them. Proves DX-less buildings still get MEP.
 *   C5 IDEMPOTENT   — re-running rwWalk yields the SAME RW2D- count (DELETE-then-insert),
 *                     so the modeller can re-route without accreting duplicates.
 *   C6 SEGMENTS     — the op-log emit seam rwRouteSegments() returns explicit endpoint
 *                     polylines (from→to) for the SAME runs the proven insert path emits
 *                     (count == Path A pipes), each connecting two REAL anchor positions.
 *                     Proves the GEOM_SWEEP bridge is faithful to the routed MEP.
 *   C7 SWEEP_OPS    — modeller-ready path: rwRouteSegments(null, name, {arcEnvelope}) works
 *                     WITHOUT a building DB, and rwSweepOps() maps each segment to a
 *                     well-formed GEOM_SWEEP commit payload (2-point path, square profile,
 *                     drop-transform applied, limit honoured). Proves the bridge is ready to
 *                     feed window.Bonsai.oplog.commit() in the op-log-native modeller.
 *   C8 ALIGN        — auto-emit-on-drop transform: rwAlignSegments() maps the anchor-space
 *                     routes onto a WORLD-placed building's bbox (1:1 m + yaw). EVERY aligned
 *                     endpoint lands inside the placed bbox, and lengths are preserved.
 *                     Proves the routed MEP registers to the dropped building's footprint.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var RW_SRC = path.join(ROOT, 'deploy/dev/routewalker.js');
var MEP_DB = path.join(ROOT, 'deploy/dev/mep_rw.db');
var DUPLEX_DB = path.join(ROOT, 'deploy/buildings/Duplex_extracted.db');
var SH_DB = path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db');

var RW_MAX_PAIR_DIST = 50.0; // mirror routewalker.js sanity guard

function loadRouteWalker(SQL, mepBuf) {
  // eval the UNMODIFIED port into a fresh scope, inject the real mep_rw.db as _rwDb.
  var src = fs.readFileSync(RW_SRC, 'utf8');
  var factory = new Function('SQL', 'MEP_BUF', src + '\n' +
    '_rwDb = new SQL.Database(new Uint8Array(MEP_BUF));\n' +
    '_rwReady = true;\n' +
    'return { rwWalk: rwWalk, loadArc: _rwLoadArcEnvelope, findAnchor: _rwFindAnchorKey, ' +
    'routeSegments: rwRouteSegments, sweepOps: rwSweepOps, centroid: rwSegmentCentroid, ' +
    'align: rwAlignSegments };');
  return factory(SQL, mepBuf);
}

// Read back the RW2D- elements the walker inserted, joined to their transforms.
function readRouted(db) {
  var rows = db.exec(
    "SELECT m.guid, m.ifc_class, m.discipline, t.center_x, t.center_y, t.center_z, " +
    "t.bbox_x, t.bbox_y, t.bbox_z " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid " +
    "WHERE m.guid LIKE 'RW2D-%'");
  if (!rows.length) return [];
  return rows[0].values.map(function (v) {
    return { guid: v[0], cls: v[1], disc: v[2], cx: v[3], cy: v[4], cz: v[5],
             bx: v[6], by: v[7], bz: v[8] };
  });
}

(async function () {
  var pass = true;
  var fail = function (msg) { pass = false; console.log('  ✗ ' + msg); };

  var SQL = await initSqlJs();
  var mepBuf = fs.readFileSync(MEP_DB);
  var rw = loadRouteWalker(SQL, mepBuf);

  // ─── C1: real recipe present ──────────────────────────────────────────────
  var mep = new SQL.Database(new Uint8Array(mepBuf));
  var ancN = mep.exec("SELECT COUNT(*) FROM ad_mep_anchor WHERE source_building='Duplex'")[0].values[0][0];
  var patN = mep.exec("SELECT COUNT(DISTINCT pattern_id) FROM ad_mep_pattern")[0].values[0][0];
  var stepN = mep.exec("SELECT COUNT(*) FROM ad_mep_pattern")[0].values[0][0];
  console.log('§RW_C1 duplexAnchors=' + ancN + ' patterns=' + patN + ' steps=' + stepN);
  if (ancN < 2000) fail('C1 expected ~2100 Duplex anchors, got ' + ancN);
  if (patN < 2) fail('C1 expected >=2 patterns (CW+SP), got ' + patN);

  // ─── C2 + C3: Path A routes Duplex from real anchors, clash gate runs ──────
  var dup = new SQL.Database(new Uint8Array(fs.readFileSync(DUPLEX_DB)));
  var arcN = rw.loadArc(dup).length;
  var dupRes = rw.rwWalk(dup, 'Duplex');
  if (!dupRes) fail('C2 rwWalk(Duplex) returned null');
  var routed = readRouted(dup);
  var pipes = routed.filter(function (r) { return r.cls === 'IfcPipeSegment'; });
  // every emitted pipe must be a real run: non-degenerate bbox AND within the pair-dist guard
  var degenerate = 0, overLong = 0;
  pipes.forEach(function (p) {
    var span = Math.sqrt(p.bx * p.bx + p.by * p.by + p.bz * p.bz);
    if (span <= 1e-6) degenerate++;
    if (span > RW_MAX_PAIR_DIST * 1.74) overLong++; // diagonal of a 50m cube ~ 86.6
  });
  console.log('§RW_C2 building=Duplex pipes=' + pipes.length + ' fixtures=' +
    (routed.length - pipes.length) + ' degenerate=' + degenerate + ' overLong=' + overLong +
    ' clashSkipped=' + dupRes.clashSkipped);
  if (pipes.length === 0) fail('C2 Path A emitted 0 pipe runs from 2100 real anchors');
  if (degenerate > 0) fail('C2 ' + degenerate + ' pipe runs are degenerate (zero span)');
  if (overLong > 0) fail('C2 ' + overLong + ' pipe runs exceed the 50m pair-distance guard');

  console.log('§RW_C3 arcEnvelopeBoxes=' + arcN + ' clashGate=' + (arcN > 0 ? 'ACTIVE' : 'EMPTY') +
    ' clashSkipped=' + dupRes.clashSkipped);
  if (arcN === 0) fail('C3 ARC envelope empty — clash gate inert');

  // ─── C4: SampleHouse has no anchors → Path B cross-populates from room BOM ──
  var shAnchors = mep.exec("SELECT COUNT(*) FROM ad_mep_anchor WHERE source_building='SampleHouse'");
  var shAncN = shAnchors.length ? shAnchors[0].values[0][0] : 0;
  var sh = new SQL.Database(new Uint8Array(fs.readFileSync(SH_DB)));
  var shRes = rw.rwWalk(sh, 'SampleHouse');
  var shRouted = readRouted(sh);
  var shFix = shRouted.filter(function (r) { return r.cls !== 'IfcPipeSegment'; });
  console.log('§RW_C4 sampleHouseAnchors=' + shAncN + ' pathB_fixtures=' + shFix.length +
    ' pathB_pipes=' + (shRouted.length - shFix.length) + ' total=' + (shRes ? shRes.total : 0));
  if (shAncN !== 0) console.log('  (note: SampleHouse has anchors — Path A may also run)');
  if (shFix.length === 0) fail('C4 Path B placed 0 fixtures from room BOM recipes (cross-pop failed)');

  // ─── C5: idempotent re-run ─────────────────────────────────────────────────
  var before = readRouted(dup).length;
  rw.rwWalk(dup, 'Duplex');
  var after = readRouted(dup).length;
  console.log('§RW_C5 firstRun=' + before + ' secondRun=' + after +
    ' delta=' + (after - before));
  if (after !== before) fail('C5 re-run changed RW2D count ' + before + ' -> ' + after +
    ' (not idempotent)');

  // ─── C6: op-log emit seam returns explicit endpoint polylines ──────────────
  // Build a set of real Duplex anchor positions (rounded) to verify endpoints are real.
  var ancRows = mep.exec("SELECT x_m, y_m, z_m FROM ad_mep_anchor WHERE source_building='Duplex'")[0].values;
  var ancXYZ = {}, ancXY = {};
  var k3 = function (a, b, c) { return a.toFixed(3) + ',' + b.toFixed(3) + ',' + c.toFixed(3); };
  var k2 = function (a, b) { return a.toFixed(3) + ',' + b.toFixed(3); };
  ancRows.forEach(function (v) { ancXYZ[k3(v[0], v[1], v[2])] = 1; ancXY[k2(v[0], v[1])] = 1; });

  var dup2 = new SQL.Database(new Uint8Array(fs.readFileSync(DUPLEX_DB)));
  var segs = rw.routeSegments(dup2, 'Duplex');
  var pathAPipes = dupRes.pipes; // Path B added 0 fixtures → all pipes are Path A
  var fromReal = 0, toReal = 0, lenBad = 0;
  segs.forEach(function (s) {
    if (ancXYZ[k3(s.from[0], s.from[1], s.from[2])]) fromReal++; // from is always a real anchor
    if (ancXY[k2(s.to[0], s.to[1])]) toReal++;                   // to.xy real (z may be gradient-adjusted)
    var d = Math.sqrt(Math.pow(s.to[0] - s.from[0], 2) + Math.pow(s.to[1] - s.from[1], 2) +
      Math.pow(s.to[2] - s.from[2], 2));
    if (Math.abs(d - s.len) > 1e-6 || d <= 1e-6 || d > RW_MAX_PAIR_DIST * 1.74) lenBad++;
  });
  console.log('§RW_C6 segments=' + segs.length + ' pathAPipes=' + pathAPipes +
    ' fromReal=' + fromReal + ' toReal=' + toReal + ' lenBad=' + lenBad);
  if (segs.length !== pathAPipes) fail('C6 segment count ' + segs.length +
    ' != proven Path A pipe count ' + pathAPipes);
  if (segs.length === 0) fail('C6 emit seam returned 0 segments');
  if (fromReal !== segs.length) fail('C6 ' + (segs.length - fromReal) +
    ' segment FROM endpoints are not real anchors');
  if (toReal !== segs.length) fail('C6 ' + (segs.length - toReal) +
    ' segment TO endpoints are not real anchors (xy)');
  if (lenBad > 0) fail('C6 ' + lenBad + ' segments have inconsistent/degenerate length');

  // ─── C7: modeller-ready — null-DB segments + GEOM_SWEEP payload mapping ────
  // No building DB (op-log modeller), pass the ARC envelope explicitly.
  var arcBoxes = rw.loadArc(new SQL.Database(new Uint8Array(fs.readFileSync(DUPLEX_DB))));
  var segNoDb = rw.routeSegments(null, 'Duplex', { arcEnvelope: arcBoxes });
  var centroid = rw.centroid(segNoDb);
  var LIMIT = 6;
  var translate = [-centroid[0], -centroid[1], -centroid[2]]; // centre routed run at modeller origin
  var ops = rw.sweepOps(segNoDb, { translate: translate, limit: LIMIT, profileM: 0.05 });
  var malformed = 0, offCentre = 0;
  ops.forEach(function (o, i) {
    var p = o.parameters;
    if (o.op_type !== 'GEOM_SWEEP' || !p || !p.path || p.path.length !== 2 ||
        !p.profile || p.profile.w !== 0.05 || p.profile.h !== 0.05) { malformed++; return; }
    // each endpoint = original ± translate; verify the translate landed (midpoint near origin-ish band)
    var s = segNoDb[i];
    if (Math.abs(p.path[0][0] - (s.from[0] + translate[0])) > 1e-9) offCentre++;
  });
  console.log('§RW_C7 nullDbSegments=' + segNoDb.length + ' centroid=[' +
    centroid.map(function (c) { return c.toFixed(2); }).join(',') + '] ops=' + ops.length +
    ' limit=' + LIMIT + ' malformed=' + malformed + ' offCentre=' + offCentre);
  if (segNoDb.length !== segs.length) fail('C7 null-DB segments ' + segNoDb.length +
    ' != with-DB segments ' + segs.length + ' (DB-independence broken)');
  if (ops.length !== LIMIT) fail('C7 sweepOps limit not honoured: ' + ops.length + ' != ' + LIMIT);
  if (malformed > 0) fail('C7 ' + malformed + ' GEOM_SWEEP payloads malformed');
  if (offCentre > 0) fail('C7 ' + offCentre + ' payloads did not apply the drop transform');

  // ─── C8: auto-emit-on-drop alignment — routes register to the placed bbox ──
  // The placed building's world bbox = the anchor span (1:1 m) translated to a drop point [10,5,0].
  var aMin = [Infinity, Infinity, Infinity], aMax = [-Infinity, -Infinity, -Infinity];
  segNoDb.forEach(function (s) {
    [s.from, s.to].forEach(function (p) {
      for (var i = 0; i < 3; i++) { if (p[i] < aMin[i]) aMin[i] = p[i]; if (p[i] > aMax[i]) aMax[i] = p[i]; }
    });
  });
  var drop = [10, 5, 0];
  var placedBBox = { min: drop, max: [drop[0] + (aMax[0] - aMin[0]), drop[1] + (aMax[1] - aMin[1]), drop[2] + (aMax[2] - aMin[2])] };
  var aligned = rw.align(segNoDb, placedBBox, { rotDeg: 0 });
  var EPS = 1e-6;
  var outside = 0, lenDrift = 0;
  aligned.forEach(function (s, i) {
    [s.from, s.to].forEach(function (p) {
      if (p[0] < placedBBox.min[0] - EPS || p[0] > placedBBox.max[0] + EPS ||
          p[1] < placedBBox.min[1] - EPS || p[1] > placedBBox.max[1] + EPS ||
          p[2] < placedBBox.min[2] - EPS || p[2] > placedBBox.max[2] + EPS) outside++;
    });
    var d = Math.sqrt(Math.pow(s.to[0] - s.from[0], 2) + Math.pow(s.to[1] - s.from[1], 2) +
      Math.pow(s.to[2] - s.from[2], 2));
    if (Math.abs(d - segNoDb[i].len) > 1e-6) lenDrift++;
  });
  console.log('§RW_C8 aligned=' + aligned.length + ' placedBBox=[' + placedBBox.min.join(',') + '..' +
    placedBBox.max.join(',') + '] endpointsOutside=' + outside + ' lenDrift=' + lenDrift);
  if (aligned.length !== segNoDb.length) fail('C8 aligned count != segments');
  // anchor bbox spans ~9×17×7 ≈ placed 9×17×7, so all endpoints must fall inside the placed bbox
  if (outside > 0) fail('C8 ' + outside + ' aligned endpoints fell OUTSIDE the placed building bbox');
  if (lenDrift > 0) fail('C8 ' + lenDrift + ' aligned runs changed length (transform not rigid)');

  console.log('§RW_VERDICT ' + (pass ? 'PASS' : 'FAIL') + ' claims=' + (pass ? '8/8' : '<8/8'));
  console.log('── W-ROUTEWALKER-MEP ' + (pass ? 'PASS' : 'FAIL') + ' ──');
  process.exit(pass ? 0 : 1);
})().catch(function (e) {
  console.log('  ✗ EXCEPTION ' + (e && e.stack || e));
  console.log('── W-ROUTEWALKER-MEP FAIL ──');
  process.exit(1);
});
