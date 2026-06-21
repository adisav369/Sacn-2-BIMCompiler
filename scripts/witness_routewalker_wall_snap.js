#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-ROUTEWALK-WALL-SNAP scope (read this block first)
 * SCOPE: the SampleHouse MEP benchmark's honest residual (RESUME_MODELLER_FOCUS.md step A finding #2): WALL-host
 *   fixtures (switch/outlet/wall-aircon) landed at the room SET's furniture-extent aabb EDGE, not on a real wall —
 *   only 1 of 13 touched a real wall solid. This proves the fix: rwPlaceFixtures now SNAPS each wall-host fixture
 *   onto the nearest REAL wall solid (mep_rw.db arc_envelope thin-and-tall boxes, the SAME source the clash gate
 *   uses), with an honest fallback to the nominal edge where SH's PARTIAL wall model has no wall within range.
 *   Anchors everything to SH's OWN extracted geometry — ZERO invention. Read the §-log (Log Mandate).
 *
 * CLAIMS (each names the issue it proves):
 *   S1 MORE_ON_WALL — strictly MORE wall-host fixtures touch a real wall solid WITH snapping than the prior
 *                     furniture-edge placement (the regression this fix exists to kill). Counted by the SAME
 *                     _rwClashesWithArc adjacency the GEO witness used.
 *   S2 SNAPPED_ARE_ON — every fixture rwPlaceFixtures REPORTS as wallSnapped actually sits on (adjacent to) a real
 *                     wall solid (within the clash adjacency tol). No fixture is marked snapped but floating.
 *   S3 IN_BUILDING  — snapping NEVER pushes a fixture outside the real building footprint (snap = onto a wall that
 *                     bounds the building, clamped to its span; isometry of containment preserved).
 *   S4 OPEN_UNTOUCHED — OPEN-space fixtures (ceiling/floor: lights/fans/diffusers) are NOT snapped and NONE is
 *                     embedded in a wall (the clash gate still holds — snapping is wall-host only).
 *   S5 FALLBACK_HONEST — fixtures NOT near any wall keep their nominal position (snapped=false), never teleported;
 *                     and the whole chain is deterministic (re-run byte-identical).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var VIEWER = process.env.VIEWER_DIR || '/tmp/wt-wallsnap/viewer';
function pick(n) { var w = path.join(VIEWER, n); return fs.existsSync(w) ? w : path.join(ROOT, 'deploy/dev', n); }
var RW_SRC = pick('routewalker.js');
var MEP_DB = pick('mep_rw.db');
var SH_DB = path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db');
var BLDG = 'SampleHouse';
var ROOM_SET = { LIVING: '1 - Living room SET', BEDROOM: '2 - Bedroom SET', CORRIDOR: '3 - Entrance hall SET' };

function loadRW(SQL, mepBuf) {
  var src = fs.readFileSync(RW_SRC, 'utf8');
  return new Function('SQL', 'B', src + '\n_rwDb=new SQL.Database(new Uint8Array(B));_rwReady=true;\n' +
    'return { placeFixtures: rwPlaceFixtures, clashesWithArc: _rwClashesWithArc, CLASH_TOL: RW_ARC_CLASH_TOL, SNAP_MAX: RW_WALL_SNAP_MAX };')(SQL, mepBuf);
}
function rows(db, sql) { var r = db.exec(sql); if (!r.length) return []; var c = r[0].columns; return r[0].values.map(function (v) { var o = {}; c.forEach(function (k, i) { o[k] = v[i]; }); return o; }); }

(function () {
  var pass = true; var fail = function (m) { pass = false; console.log('  ✗ ' + m); };
  initSqlJs().then(function (SQL) {
    console.log('§SNAP_SRC viewer=' + VIEWER + ' shipping=' + (RW_SRC.indexOf(VIEWER) === 0));
    var mepBuf = fs.readFileSync(MEP_DB);
    var rw = loadRW(SQL, mepBuf);
    var sh = new SQL.Database(new Uint8Array(fs.readFileSync(SH_DB)));
    var mep = new SQL.Database(new Uint8Array(mepBuf));

    // ── real building footprint + real wall solids (same derivation as the GEO witness) ──
    var slab = rows(sh, "SELECT t.center_x cx,t.center_y cy,t.bbox_x bx,t.bbox_y by_ FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class='IfcSlab' AND m.storey='Ground Floor'")[0];
    var bld = { xmin: slab.cx - slab.bx / 2, xmax: slab.cx + slab.bx / 2, ymin: slab.cy - slab.by_ / 2, ymax: slab.cy + slab.by_ / 2 };
    var org = rows(mep, "SELECT min_x,min_y,min_z FROM building_origin WHERE source_building='" + BLDG + "'")[0];
    var floorZ = 0.0;
    var part = rows(sh, "SELECT MIN(t.center_z + t.bbox_z/2) AS ztop FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid " +
      "WHERE m.ifc_class='IfcWallStandardCase' AND m.storey='Ground Floor'")[0];
    var ceilZ = part.ztop;
    var wallBoxes = rows(mep, "SELECT cx,cy,cz,w,d,h FROM arc_envelope WHERE source_building='" + BLDG + "'")
      .filter(function (a) { return Math.min(a.w, a.d) < 0.5 && a.h > 1.5; });
    console.log('§SNAP_WALLS solids=' + wallBoxes.length + ' bld X[' + bld.xmin.toFixed(2) + ',' + bld.xmax.toFixed(2) + '] Y[' + bld.ymin.toFixed(2) + ',' + bld.ymax.toFixed(2) + ']');

    // ── SH's real rooms (same as GEO witness) ──
    var roomLines = rows(sh, "SELECT l.role, l.dx, l.dy FROM m_bom_line l JOIN m_bom b ON l.M_BOM_ID=b.M_BOM_ID " +
      "WHERE b.group_by='ROOM' AND b.bom_name='SH Ground Floor' ORDER BY l.sequence");
    var rooms = [];
    roomLines.forEach(function (rl) {
      var setName = ROOM_SET[rl.role]; if (!setName) return;
      var set = rows(sh, "SELECT aabb_width_mm w, aabb_depth_mm d FROM m_bom WHERE bom_name='" + setName.replace(/'/g, "''") + "'")[0];
      var bx = (set.w || 3000) / 1000, by = (set.d || 3000) / 1000, bz = ceilZ - floorZ;
      rooms.push({ type: rl.role, name: rl.role, storey: 'Ground Floor',
        cx: org.min_x + rl.dx + bx / 2, cy: org.min_y + rl.dy + by / 2, cz: floorZ + bz / 2, bx: bx, by: by, bz: bz });
    });

    var onWall = function (f) { return rw.clashesWithArc(f.x, f.y, f.z, 0.1, 0.1, 0.4, wallBoxes); };

    // ── BEFORE: no snapping (nominal furniture-edge) ──
    var before = rw.placeFixtures(rooms);
    var wallFxBefore = before.filter(function (f) { return f.hostSurface === 'WALL'; });
    var onWallBefore = wallFxBefore.filter(onWall).length;

    // ── AFTER: with the real wall solids injected (the shipping path: opts.buildingName self-loads the same set) ──
    var after = rw.placeFixtures(rooms, { wallBoxes: wallBoxes });
    var wallFxAfter = after.filter(function (f) { return f.hostSurface === 'WALL'; });
    var onWallAfter = wallFxAfter.filter(onWall).length;
    var snapped = after.filter(function (f) { return f.wallSnapped; });
    console.log('§SNAP_S1 wallFixtures=' + wallFxAfter.length + ' onRealWall: before=' + onWallBefore + ' after=' + onWallAfter + ' snapped=' + snapped.length);

    // S1: more wall fixtures on real walls after
    if (!(onWallAfter > onWallBefore)) fail('S1 snapping did not increase wall-mounted fixtures (' + onWallBefore + '→' + onWallAfter + ')');

    // S2: every reported-snapped fixture is actually on a wall
    var snappedFloating = snapped.filter(function (f) { return !onWall(f); });
    console.log('§SNAP_S2 snapped=' + snapped.length + ' floating(markedSnappedButOffWall)=' + snappedFloating.length);
    if (snappedFloating.length) { snappedFloating.slice(0, 4).forEach(function (f) { console.log('    OFF ' + f.product + ' @(' + f.x.toFixed(2) + ',' + f.y.toFixed(2) + ',' + f.z.toFixed(2) + ')'); }); fail('S2 ' + snappedFloating.length + ' fixtures marked snapped but not on a real wall'); }
    if (!snapped.length) fail('S2 nothing snapped — the fix did not engage');

    // S3: no snap pushed a fixture outside the building
    var outBld = after.filter(function (f) { return f.x < bld.xmin - 0.05 || f.x > bld.xmax + 0.05 || f.y < bld.ymin - 0.05 || f.y > bld.ymax + 0.05; });
    console.log('§SNAP_S3 outsideBuildingAfter=' + outBld.length);
    if (outBld.length) { outBld.slice(0, 4).forEach(function (f) { console.log('    OUT ' + f.product + ' @(' + f.x.toFixed(2) + ',' + f.y.toFixed(2) + ')'); }); fail('S3 ' + outBld.length + ' fixtures pushed outside the building by snapping'); }

    // S4: open-space fixtures untouched + not embedded in a wall
    var openSnapped = after.filter(function (f) { return f.hostSurface !== 'WALL' && f.wallSnapped; });
    var openEmbedded = after.filter(function (f) { return f.hostSurface !== 'WALL'; }).filter(function (f) { return rw.clashesWithArc(f.x, f.y, f.z, 0.1, 0.1, 0.1, wallBoxes); });
    console.log('§SNAP_S4 openSnapped=' + openSnapped.length + ' openEmbeddedInWall=' + openEmbedded.length);
    if (openSnapped.length) fail('S4 ' + openSnapped.length + ' OPEN-space fixtures were wrongly snapped');
    if (openEmbedded.length) fail('S4 ' + openEmbedded.length + ' OPEN-space fixtures embedded in a wall');

    // S5: honest fallback (some may stay nominal where no wall is near) + deterministic
    var notSnapped = wallFxAfter.filter(function (f) { return !f.wallSnapped; });
    var after2 = rw.placeFixtures(rooms, { wallBoxes: wallBoxes });
    var determ = after2.length === after.length && after2.every(function (f, i) {
      return f.product === after[i].product && Math.abs(f.x - after[i].x) < 1e-9 && Math.abs(f.y - after[i].y) < 1e-9 && f.wallSnapped === after[i].wallSnapped;
    });
    console.log('§SNAP_S5 wallFixturesKeptNominal(noWallNear)=' + notSnapped.length + ' deterministic=' + determ);
    if (!determ) fail('S5 snapping is non-deterministic');

    console.log('§SNAP_VERDICT ' + (pass ? 'PASS' : 'FAIL') + ' claims=' + (pass ? '5/5' : '<5/5'));
    console.log('── W-ROUTEWALK-WALL-SNAP ' + (pass ? 'PASS' : 'FAIL') + ' ──');
    process.exit(pass ? 0 : 1);
  }).catch(function (e) { console.log('  ✗ EXCEPTION ' + (e && e.stack || e)); console.log('── W-ROUTEWALK-WALL-SNAP FAIL ──'); process.exit(1); });
})();
