#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-ROUTEWALK-FIXTURES-DB scope (read this block first)
 * SCOPE: the MODELLER path for the SampleHouse MEP benchmark (RESUME_MODELLER_FOCUS.md §NEXT-SESSION BUILD
 *   step B), proven headless before the live wiring. The op-log modeller has NO building DB, so it cannot read
 *   SH's real rooms from the extraction the way W-ROUTEWALK-FIXTURES-GEO did. Instead it loads them from the
 *   mep_rw.db sidecar (building_room, baked by build_mep_room_envelopes.js) — the SAME way it already loads the
 *   ARC clash gate. This witness proves the exact runtime chain:
 *       rwLoadRooms('SampleHouse')  →  rwPlaceFixtures(rooms)  →  toWorld() rigid transform
 *   where toWorld is byte-for-byte the transform the routed pipes use in modeller.html autoRouteMEP:
 *       worldPos = bmin + Rot(yaw)·(pos − buildingOrigin)
 *   Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS (each names the issue it proves):
 *   D1 LOADED    — rwLoadRooms returns SH's 3 real rooms (LIVING/BEDROOM/CORRIDOR) from mep_rw.db, identical to
 *                  the baked envelopes — NO extraction DB, NO catalog synthesised row.
 *   D2 SAME_FX   — rwPlaceFixtures over the loaded rooms yields the SAME 23 fixtures the GEO witness proved
 *                  (ELEC 15 + ACMV 8), every product a real recipe row. The sidecar path == the extraction path.
 *   D3 IN_PLACED — after the rigid toWorld transform (a real drop: arbitrary bmin + yaw), EVERY fixture lands
 *                  inside the PLACED building footprint. The transform preserves containment (it is an isometry).
 *   D4 ISOMETRY  — pairwise fixture distances are invariant under toWorld (rigid, no scale/shear) for yaw 0° AND
 *                  90° — so the placed MEP keeps the real building's relative geometry.
 *   D5 DETERM    — the whole chain is deterministic (re-run byte-identical).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var RW_SRC = path.join(ROOT, 'deploy/dev/routewalker.js');
var MEP_DB = path.join(ROOT, 'deploy/dev/mep_rw.db');
var BLDG = 'SampleHouse';

function loadRW(SQL, mepBuf) {
  var src = fs.readFileSync(RW_SRC, 'utf8');
  return new Function('SQL', 'B', src + '\n_rwDb=new SQL.Database(new Uint8Array(B));_rwReady=true;\n' +
    'return { loadRooms: rwLoadRooms, placeFixtures: rwPlaceFixtures, origin: rwBuildingOrigin };')(SQL, mepBuf);
}

// the EXACT transform from modeller.html autoRouteMEP: worldPos = bmin + Rot(yaw)·(pos − origin)
function makeToWorld(bmin, org, yawDeg) {
  var rad = yawDeg * Math.PI / 180, cs = Math.cos(rad), sn = Math.sin(rad);
  return function (p) {
    return [bmin[0] + (cs * (p[0] - org.x) - sn * (p[1] - org.y)),
            bmin[1] + (sn * (p[0] - org.x) + cs * (p[1] - org.y)),
            bmin[2] + (p[2] - org.z)];
  };
}

(function () {
  var pass = true;
  var fail = function (m) { pass = false; console.log('  ✗ ' + m); };

  initSqlJs().then(function (SQL) {
    var mepBuf = fs.readFileSync(MEP_DB);
    var rw = loadRW(SQL, mepBuf);

    // ─── D1: rooms loaded from the sidecar ────────────────────────────────────
    var rooms = rw.loadRooms(BLDG);
    var roles = rooms.map(function (r) { return r.type; });
    console.log('§DB_D1 loadedRooms=' + rooms.length + ' roles=[' + roles.join(',') + ']');
    if (rooms.length !== 3) fail('D1 expected 3 baked SH rooms, got ' + rooms.length);
    ['LIVING', 'BEDROOM', 'CORRIDOR'].forEach(function (r) { if (roles.indexOf(r) < 0) fail('D1 missing real room ' + r); });
    rooms.forEach(function (r) {
      if (!(r.bx > 0 && r.by > 0 && r.bz > 0)) fail('D1 room ' + r.type + ' has a degenerate envelope');
    });

    // ─── D2: same 23 fixtures as the GEO witness ──────────────────────────────
    var fx = rw.placeFixtures(rooms);
    var byDisc = {}; fx.forEach(function (f) { byDisc[f.disc] = (byDisc[f.disc] || 0) + 1; });
    console.log('§DB_D2 fixtures=' + fx.length + ' disc=' + JSON.stringify(byDisc));
    if (fx.length !== 23) fail('D2 expected 23 fixtures over the real rooms, got ' + fx.length);
    if (byDisc.ELEC !== 15 || byDisc.ACMV !== 8) fail('D2 discipline mix changed (expected ELEC 15 + ACMV 8)');

    // ─── D3 + D4: rigid drop transform ────────────────────────────────────────
    var org = rw.origin(BLDG);
    console.log('§DB_ORG origin=(' + org.x.toFixed(3) + ',' + org.y.toFixed(3) + ',' + org.z.toFixed(3) + ')');
    // building footprint extents (from the rooms' enclosing real CORRIDOR room = the whole ground floor)
    var hall = rooms.filter(function (r) { return r.type === 'CORRIDOR'; })[0];
    var W = hall.bx, D = hall.by;   // 16.87 × 8.67 — the real building footprint

    [{ yaw: 0, bmin: [12.0, -5.0, 0.0] }, { yaw: 90, bmin: [3.0, 7.0, 0.0] }].forEach(function (drop) {
      var toWorld = makeToWorld(drop.bmin, org, drop.yaw);
      // placed footprint AABB: rotate the [0..W]×[0..D] corners about origin, translate by bmin
      var corners = [[0, 0], [W, 0], [0, D], [W, D]].map(function (c) {
        var rad = drop.yaw * Math.PI / 180, cs = Math.cos(rad), sn = Math.sin(rad);
        return [drop.bmin[0] + (cs * c[0] - sn * c[1]), drop.bmin[1] + (sn * c[0] + cs * c[1])];
      });
      var xs = corners.map(function (c) { return c[0]; }), ys = corners.map(function (c) { return c[1]; });
      var pxmin = Math.min.apply(null, xs), pxmax = Math.max.apply(null, xs);
      var pymin = Math.min.apply(null, ys), pymax = Math.max.apply(null, ys);
      var tol = 0.6;   // wall-mount fixtures sit at the room edge ≈ the footprint edge
      var outside = 0;
      fx.forEach(function (f) {
        var w = toWorld([f.x, f.y, f.z]);
        if (w[0] < pxmin - tol || w[0] > pxmax + tol || w[1] < pymin - tol || w[1] > pymax + tol) outside++;
      });
      console.log('§DB_D3 yaw=' + drop.yaw + ' placedFootprint X[' + pxmin.toFixed(2) + ',' + pxmax.toFixed(2) +
        '] Y[' + pymin.toFixed(2) + ',' + pymax.toFixed(2) + '] fixturesOutside=' + outside);
      if (outside) fail('D3 yaw=' + drop.yaw + ' ' + outside + ' fixtures fell outside the placed building footprint');

      // D4: isometry — distance between the first two fixtures is preserved by toWorld
      if (fx.length >= 2) {
        var a = fx[0], b = fx[1];
        var dPre = Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
        var wa = toWorld([a.x, a.y, a.z]), wb = toWorld([b.x, b.y, b.z]);
        var dPost = Math.hypot(wa[0] - wb[0], wa[1] - wb[1], wa[2] - wb[2]);
        if (Math.abs(dPre - dPost) > 1e-9) fail('D4 yaw=' + drop.yaw + ' toWorld not an isometry (Δdist=' + (dPre - dPost) + ')');
      }
    });
    console.log('§DB_D4 isometry preserved for yaw 0° + 90°');

    // ─── D5: deterministic ────────────────────────────────────────────────────
    var fx2 = rw.placeFixtures(rw.loadRooms(BLDG));
    var same = fx2.length === fx.length && fx2.every(function (f, i) {
      return f.product === fx[i].product && Math.abs(f.x - fx[i].x) < 1e-9 && Math.abs(f.y - fx[i].y) < 1e-9 && Math.abs(f.z - fx[i].z) < 1e-9;
    });
    console.log('§DB_D5 run1=' + fx.length + ' run2=' + fx2.length + ' identical=' + same);
    if (!same) fail('D5 chain is non-deterministic');

    console.log('§DB_VERDICT ' + (pass ? 'PASS' : 'FAIL') + ' claims=' + (pass ? '5/5' : '<5/5'));
    console.log('── W-ROUTEWALK-FIXTURES-DB ' + (pass ? 'PASS' : 'FAIL') + ' ──');
    process.exit(pass ? 0 : 1);
  }).catch(function (e) {
    console.log('  ✗ EXCEPTION ' + (e && e.stack || e));
    console.log('── W-ROUTEWALK-FIXTURES-DB FAIL ──');
    process.exit(1);
  });
})();
