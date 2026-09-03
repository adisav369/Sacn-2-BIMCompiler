#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-ROUTEWALK-FIXTURES-GEO scope (read this block first)
 * SCOPE: SampleHouse "all systems go" MEP benchmark — the FOOLPROOF GEOMETRIC test (RESUME_MODELLER_FOCUS.md
 *   §NEXT-SESSION BUILD step A). It SUPERSEDES the geometry of W-ROUTEWALK-FIXTURES, whose honest gap the user
 *   caught: that witness used the FURNITURE-SET footprint as the room bbox + a NOMINAL 2.7m height = a PROXY,
 *   and positioned rooms from the catalog's SYNTHESISED FLOOR row (all children at dy=-0.982). So "inside the
 *   room" only meant "near the furniture cluster" and heights were nominal, not real.
 *
 * FOOLPROOF FIX — anchor EVERYTHING to SampleHouse's OWN reconstructed geometry, zero invention:
 *   • ROOMS come from SH's authoritative extracted BOM (deploy/buildings/SampleHouse_extracted.db, m_bom rows
 *     group_by='ROOM', the FLOOR-grain "SH Ground Floor" → LIVING / BEDROOM / CORRIDOR), at their REAL corner
 *     offsets (dx/dy), mapped to the world by the SAME building_origin transform the pipes use (mep_rw.db
 *     building_origin, the StructuralBomBuilder allMinXYZ). Footprint = the room SET's REAL aabb. NOT the
 *     catalog synthesised row. (SH's real rooms are Living/Bedroom/Corridor — it has NO wet room, so FP/PLB
 *     honestly do NOT fire. The prior 39-fixture/sprinkler count was an artifact of the catalog proxy rooms.)
 *   • HEIGHT is real: floor z = the real ground-floor slab TOP (z=0), ceiling z = the real interior partition
 *     wall TOP from the extraction — NOT a nominal 2.7m.
 *   • The CLASH GATE uses SH's REAL wall solids (mep_rw.db arc_envelope, source_building='SampleHouse',
 *     filtered to the thin-and-tall vertical walls), so a fixture EMBEDDED in a wall is caught — the same
 *     gate the routed pipes pass through.
 *   Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS (each names the issue it proves):
 *   G1 REAL_ROOMS  — rooms resolve from SH's OWN extracted BOM (LIVING/BEDROOM/CORRIDOR), positioned by the
 *                    real building_origin transform + real SET footprint. No catalog synthesised row, no proxy.
 *   G2 IN_BUILDING — EVERY placed fixture lands inside the REAL ground-floor slab footprint (the building
 *                    envelope from the extraction). The proxy never checked this; it is the core foolproof gate.
 *   G3 REAL_HEIGHT — every fixture sits at the right height for its offset z_rule measured against the REAL
 *                    floor/ceiling: CEILING fixtures within [ceiling-0.15, ceiling]; FLOOR fixtures within
 *                    [floor, floor+0.10]; wall switch (WALL_ENTRY) ≈ 1.2m. Not a nominal 2.7m room.
 *   G4 NO_WALL_CLASH — no OPEN-SPACE fixture (ceiling/floor host) is embedded in a REAL wall solid (SH
 *                    arc_envelope walls), via the same _rwClashesWithArc gate the pipes use. WALL-host fixtures
 *                    (switch/outlet/wall-aircon) are MEANT to mount on a wall, so they are reported, not gated.
 *   G5 IN_ROOM     — every fixture lies within its OWN real room's footprint (+0.5m wall-mount tolerance),
 *                    and traces to a real ad_space_type_mep_bom recipe row (zero invented products).
 *   G6 SYSTEMS     — over SH's REAL rooms the generated MEP fires ELEC (lights/outlets/switches) + ACMV
 *                    (fans/diffusers/aircon). Honestly reports FP/PLB as ABSENT (SH has no wet room) — faithful,
 *                    not fabricated. (To get a sprinkler, the building needs a real bathroom in its BOM.)
 *   G7 DETERM      — re-running yields byte-identical placements (deterministic fold, no Math.random).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var RW_SRC = path.join(ROOT, 'deploy/dev/routewalker.js');
var MEP_DB = path.join(ROOT, 'deploy/dev/mep_rw.db');
var SH_DB = path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db');
var BLDG = 'SampleHouse';

// SH's real ROOM (from m_bom group_by='ROOM') → the SET whose real aabb is its footprint + the recipe space type.
var ROOM_SET = {
  LIVING:   '1 - Living room SET',
  BEDROOM:  '2 - Bedroom SET',
  CORRIDOR: '3 - Entrance hall SET'
};

function loadRouteWalker(SQL, mepBuf) {
  var src = fs.readFileSync(RW_SRC, 'utf8');
  var factory = new Function('SQL', 'MEP_BUF', src + '\n' +
    '_rwDb = new SQL.Database(new Uint8Array(MEP_BUF));\n' +
    '_rwReady = true;\n' +
    // expose the internals the witness needs (clash gate + tolerance) so we test the SHIPPED code, not a copy
    'return { placeFixtures: rwPlaceFixtures, clashesWithArc: _rwClashesWithArc, CLASH_TOL: RW_ARC_CLASH_TOL };');
  return factory(SQL, mepBuf);
}

function rows(db, sql) {
  var r = db.exec(sql);
  if (!r.length) return [];
  var cols = r[0].columns;
  return r[0].values.map(function (v) { var o = {}; cols.forEach(function (c, i) { o[c] = v[i]; }); return o; });
}

(function () {
  var pass = true;
  var fail = function (m) { pass = false; console.log('  ✗ ' + m); };

  initSqlJs().then(function (SQL) {
    var mepBuf = fs.readFileSync(MEP_DB);
    var rw = loadRouteWalker(SQL, mepBuf);
    var sh = new SQL.Database(new Uint8Array(fs.readFileSync(SH_DB)));
    var mep = new SQL.Database(new Uint8Array(mepBuf));   // single read handle for mep_rw.db lookups

    // ─── REAL building geometry from the extraction ───────────────────────────
    // building envelope = the ground-floor slab AABB
    var slab = rows(sh, "SELECT t.center_x cx,t.center_y cy,t.center_z cz,t.bbox_x bx,t.bbox_y by_,t.bbox_z bz " +
      "FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid " +
      "WHERE m.ifc_class='IfcSlab' AND m.storey='Ground Floor'")[0];
    var bld = {
      xmin: slab.cx - slab.bx / 2, xmax: slab.cx + slab.bx / 2,
      ymin: slab.cy - slab.by_ / 2, ymax: slab.cy + slab.by_ / 2
    };
    var floorZ = slab.cz + slab.bz / 2;   // real slab TOP = floor level
    // real ceiling = the LOWEST interior partition-wall top (the real clear room height, not a nominal 2.7m)
    var part = rows(sh, "SELECT MIN(t.center_z + t.bbox_z/2) AS ztop FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class='IfcWallStandardCase' AND m.storey='Ground Floor'")[0];
    var ceilZ = part.ztop;
    console.log('§GEO_BLD slab X[' + bld.xmin.toFixed(2) + ',' + bld.xmax.toFixed(2) + '] Y[' +
      bld.ymin.toFixed(2) + ',' + bld.ymax.toFixed(2) + '] floorZ=' + floorZ.toFixed(3) + ' ceilZ=' + ceilZ.toFixed(3));

    // real wall solids for the clash gate (SH arc_envelope, thin-and-tall = vertical walls only, not slab/roof)
    var arcAll = rows(mep, "SELECT cx,cy,cz,w,d,h FROM arc_envelope WHERE source_building='" + BLDG + "'");
    var wallBoxes = arcAll.filter(function (a) { return Math.min(a.w, a.d) < 0.5 && a.h > 1.5; });
    console.log('§GEO_WALLS arcBoxes=' + arcAll.length + ' wallSolids=' + wallBoxes.length + ' (thin&tall)');
    if (wallBoxes.length < 5) fail('expected >=5 SH wall solids for the clash gate, got ' + wallBoxes.length);

    // building_origin = the StructuralBomBuilder allMinXYZ (the SAME transform the routed pipes use)
    var org = rows(mep, "SELECT min_x,min_y,min_z FROM building_origin WHERE source_building='" + BLDG + "'")[0];
    console.log('§GEO_ORIGIN min=(' + org.min_x.toFixed(3) + ',' + org.min_y.toFixed(3) + ',' + org.min_z.toFixed(3) + ')');

    // ─── G1: SH's REAL rooms from its own extracted BOM ───────────────────────
    var roomLines = rows(sh, "SELECT l.role, l.dx, l.dy FROM m_bom_line l JOIN m_bom b ON l.M_BOM_ID=b.M_BOM_ID " +
      "WHERE b.group_by='ROOM' AND b.bom_name='SH Ground Floor' ORDER BY l.sequence");
    var rooms = [];
    roomLines.forEach(function (rl) {
      var setName = ROOM_SET[rl.role];
      if (!setName) { console.log('§GEO_SKIP role ' + rl.role + ' has no SET footprint'); return; }
      var set = rows(sh, "SELECT aabb_width_mm w, aabb_depth_mm d FROM m_bom WHERE bom_name='" + setName.replace(/'/g, "''") + "'")[0];
      var bx = (set.w || 3000) / 1000, by = (set.d || 3000) / 1000, bz = ceilZ - floorZ;
      // corner = building_origin + dx/dy (VerbFactorizer corner convention); center = corner + half-extent
      var cx = org.min_x + rl.dx + bx / 2, cy = org.min_y + rl.dy + by / 2;
      rooms.push({ type: rl.role, name: rl.role, storey: 'Ground Floor',
        cx: cx, cy: cy, cz: floorZ + bz / 2, bx: bx, by: by, bz: bz });
    });
    var roles = rooms.map(function (r) { return r.type; });
    console.log('§GEO_G1 realRooms=' + rooms.length + ' roles=[' + roles.join(',') + ']');
    rooms.forEach(function (r) {
      console.log('  room ' + r.type + ' c=(' + r.cx.toFixed(2) + ',' + r.cy.toFixed(2) + ') ' +
        r.bx.toFixed(2) + 'x' + r.by.toFixed(2) + 'x' + r.bz.toFixed(2));
    });
    if (rooms.length < 3) fail('G1 expected SH 3 real rooms (LIVING/BEDROOM/CORRIDOR), got ' + rooms.length);

    // ─── place the real recipe over the REAL rooms ────────────────────────────
    var fx = rw.placeFixtures(rooms);
    var byDisc = {}, byProd = {};
    fx.forEach(function (f) { byDisc[f.disc] = (byDisc[f.disc] || 0) + 1; byProd[f.product] = (byProd[f.product] || 0) + 1; });

    // ─── G2: every fixture inside the REAL building footprint ─────────────────
    var outBld = fx.filter(function (f) { return f.x < bld.xmin || f.x > bld.xmax || f.y < bld.ymin || f.y > bld.ymax; });
    console.log('§GEO_G2 fixtures=' + fx.length + ' outsideBuilding=' + outBld.length);
    if (outBld.length) {
      outBld.slice(0, 5).forEach(function (f) { console.log('    OUT ' + f.product + ' @(' + f.x.toFixed(2) + ',' + f.y.toFixed(2) + ') room=' + f.room); });
      fail('G2 ' + outBld.length + ' fixtures fell OUTSIDE the real building footprint');
    }

    // ─── G3: real height per offset z_rule ────────────────────────────────────
    var offByRule = {};
    rows(mep, "SELECT placement_rule,z_rule,z_offset FROM ad_placement_offset")
      .forEach(function (o) { offByRule[o.placement_rule] = o; });
    var badH = 0, switchOK = 0;
    fx.forEach(function (f) {
      var off = offByRule[f.placementRule] || offByRule['AUTO'];
      var zr = off.z_rule, zo = off.z_offset || 0, ok = true;
      if (zr === 'CEILING') ok = (f.z >= ceilZ - zo - 0.05 && f.z <= ceilZ + 0.01);
      else if (zr === 'FLOOR') ok = (f.z >= floorZ - 0.01 && f.z <= floorZ + zo + 0.05);
      else ok = (f.z >= floorZ && f.z <= ceilZ); // MID
      if (f.placementRule === 'WALL_ENTRY') { if (Math.abs(f.z - (floorZ + 1.2)) < 0.05) switchOK++; else ok = false; }
      if (!ok) { badH++; if (badH <= 5) console.log('    BADH ' + f.product + ' rule=' + f.placementRule + ' z=' + f.z.toFixed(3) + ' (' + zr + ' off ' + zo + ')'); }
    });
    console.log('§GEO_G3 ceilZ=' + ceilZ.toFixed(2) + ' floorZ=' + floorZ.toFixed(2) + ' badHeight=' + badH + ' wallSwitches@1.2m=' + switchOK);
    if (badH) fail('G3 ' + badH + ' fixtures at the wrong real height for their z_rule');

    // ─── G4: no OPEN-SPACE fixture embedded in a real wall solid ──────────────
    // The clash gate is the pipes' gate: it keeps things that belong in CLEAR SPACE out of wall solids.
    // CEILING + FLOOR fixtures (lights, fans, diffusers, sprinklers, floor traps) are open-space and MUST NOT
    // sit inside a wall — that is the gate. WALL-host fixtures (switch, outlet, wall aircon) are the OPPOSITE:
    // they are MEANT to mount on a wall, so touching a wall solid is correct, not a clash — they are reported,
    // not gated (and SH's wall model is a partial sample, so full wall-adjacency cannot be asserted here).
    var open = fx.filter(function (f) { return f.hostSurface !== 'WALL'; });
    var wallFx = fx.filter(function (f) { return f.hostSurface === 'WALL'; });
    var clashed = open.filter(function (f) { return rw.clashesWithArc(f.x, f.y, f.z, 0.1, 0.1, 0.1, wallBoxes); });
    var wallMounted = wallFx.filter(function (f) { return rw.clashesWithArc(f.x, f.y, f.z, 0.1, 0.1, 0.4, wallBoxes); });
    console.log('§GEO_G4 openFixtures=' + open.length + ' embeddedInWall=' + clashed.length +
      ' | wallFixtures=' + wallFx.length + ' touchingRealWall=' + wallMounted.length + ' (tol=' + rw.CLASH_TOL + 'm)');
    if (clashed.length) {
      clashed.slice(0, 5).forEach(function (f) { console.log('    CLASH ' + f.product + ' @(' + f.x.toFixed(2) + ',' + f.y.toFixed(2) + ',' + f.z.toFixed(2) + ') room=' + f.room); });
      fail('G4 ' + clashed.length + ' OPEN-SPACE fixtures embedded in a real wall solid');
    }

    // ─── G5: in its own real room + traces to a real recipe row ───────────────
    var roomByName = {}; rooms.forEach(function (r) { roomByName[r.name] = r; });
    var invented = 0, outRoom = 0;
    fx.forEach(function (f) {
      var hit = mep.exec("SELECT 1 FROM ad_space_type_mep_bom WHERE mep_product_id='" + f.product.replace(/'/g, "''") + "' LIMIT 1");
      if (!hit.length) invented++;
      var rm = roomByName[f.room];
      if (rm) {
        var hx = rm.bx / 2 + 0.5, hy = rm.by / 2 + 0.5;
        if (f.x < rm.cx - hx || f.x > rm.cx + hx || f.y < rm.cy - hy || f.y > rm.cy + hy) outRoom++;
      }
    });
    console.log('§GEO_G5 invented=' + invented + ' outsideOwnRoom=' + outRoom);
    if (invented) fail('G5 ' + invented + ' fixtures are not real recipe products (invented)');
    if (outRoom) fail('G5 ' + outRoom + ' fixtures fell outside their OWN real room footprint');

    // ─── G6: which systems fire (honest) ──────────────────────────────────────
    console.log('§GEO_G6 disc=' + JSON.stringify(byDisc) + ' products=' + JSON.stringify(byProd));
    ['ELEC', 'ACMV'].forEach(function (d) { if (!byDisc[d]) fail('G6 system ' + d + ' did not fire (0 fixtures) over real rooms'); });
    if (byDisc['FP'] || byDisc['PLB']) console.log('  note: FP/PLB fired — a real wet room is present in SH after all');
    else console.log('  HONEST: FP/PLB absent — SampleHouse has no wet room (LIVING/BEDROOM/CORRIDOR only); a sprinkler needs a real bathroom in the BOM');

    // ─── G7: deterministic re-run ─────────────────────────────────────────────
    var fx2 = rw.placeFixtures(rooms);
    var same = fx2.length === fx.length && fx2.every(function (f, i) {
      return f.product === fx[i].product && Math.abs(f.x - fx[i].x) < 1e-9 &&
        Math.abs(f.y - fx[i].y) < 1e-9 && Math.abs(f.z - fx[i].z) < 1e-9;
    });
    console.log('§GEO_G7 run1=' + fx.length + ' run2=' + fx2.length + ' identical=' + same);
    if (!same) fail('G7 re-run produced different placements (non-deterministic)');

    console.log('§GEO_VERDICT ' + (pass ? 'PASS' : 'FAIL') + ' claims=' + (pass ? '7/7' : '<7/7'));
    console.log('── W-ROUTEWALK-FIXTURES-GEO ' + (pass ? 'PASS' : 'FAIL') + ' ──');
    process.exit(pass ? 0 : 1);
  }).catch(function (e) {
    console.log('  ✗ EXCEPTION ' + (e && e.stack || e));
    console.log('── W-ROUTEWALK-FIXTURES-GEO FAIL ──');
    process.exit(1);
  });
})();
