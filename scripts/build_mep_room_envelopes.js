#!/usr/bin/env node
/**
 * build_mep_room_envelopes.js — Populate building_room in mep_rw.db
 *
 * WHY: the op-log-native modeller has NO building DB, and the catalog's FLOOR rooms are a SYNTHESISED row
 *   (LIVING/DINING/MASTER/BATHROOM all at dy=-0.982) — NOT a building's real layout. So fixture placement must
 *   read a building's REAL rooms from a sidecar, exactly as the ARC clash gate reads arc_envelope/building_origin
 *   (build_mep_arc_envelope.js). This bakes each building's real rooms from its OWN extracted BOM.
 *
 * SOURCE (zero invention): deploy/buildings/<B>_extracted.db
 *   • room LAYOUT = m_bom_line of the FLOOR-grain ROOM bom (group_by='ROOM', bom_type='FLOOR') — role + dx/dy
 *     (corner offset from the building origin, VerbFactorizer convention).
 *   • room FOOTPRINT = the matching ROOM SET's real aabb_width/depth (matched by role keyword).
 *   • floor z = real ground-floor slab TOP; ceiling z = real lowest interior partition-wall TOP (clear height).
 *   Rooms are stored in EXTRACTION-WORLD metres (same frame as ad_mep_anchor x_m/y_m/z_m and arc_envelope), so
 *   the modeller applies the SAME rigid transform it applies to the routed pipes: world = bmin + (pos − origin).
 *
 * Schema added:
 *   building_room (source_building, role, cx, cy, cz, bx, by, bz) — room center + extents in metres.
 *
 * NOTE: only SampleHouse currently carries a ROOM-grain BOM (Duplex extraction has none) — SH-first by doctrine.
 * Run: node scripts/build_mep_room_envelopes.js   (re-run after build_mep_arc_envelope.js)
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
// target mep_rw.db: default the bim-compiler dev sidecar; override with arg 1 to bake the live viewer copy.
var MEP_DB = process.argv[2] ? path.resolve(process.argv[2]) : path.join(ROOT, 'deploy/dev/mep_rw.db');
var BUILDINGS = [
  { name: 'SampleHouse', db: path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db'), floorBom: 'SH Ground Floor' },
];

// role → keywords that identify its ROOM SET name (general, extraction-derived; first match wins)
var ROLE_SET_KEYWORDS = {
  LIVING:   ['living'],
  DINING:   ['dining'],
  BEDROOM:  ['bedroom', 'bed'],
  MASTER:   ['master', 'bedroom', 'bed'],
  CORRIDOR: ['entrance', 'hall', 'corridor', 'lobby'],
  KITCHEN:  ['kitchen'],
  BATHROOM: ['bath', 'toilet', 'wc', 'washroom']
};

function rows(db, sql) {
  var r = db.exec(sql);
  if (!r.length) return [];
  var cols = r[0].columns;
  return r[0].values.map(function (v) { var o = {}; cols.forEach(function (c, i) { o[c] = v[i]; }); return o; });
}

(async function () {
  var SQL = await initSqlJs();
  var mep = new SQL.Database(new Uint8Array(fs.readFileSync(MEP_DB)));
  mep.run('CREATE TABLE IF NOT EXISTS building_room (' +
    'source_building TEXT NOT NULL, role TEXT, cx REAL, cy REAL, cz REAL, bx REAL, by_ REAL, bz REAL)');

  for (var b of BUILDINGS) {
    if (!fs.existsSync(b.db)) { console.log('  SKIP ' + b.name + ' — DB not found'); continue; }
    var bDb = new SQL.Database(new Uint8Array(fs.readFileSync(b.db)));

    // building origin (already baked by build_mep_arc_envelope.js)
    var org = rows(mep, "SELECT min_x,min_y,min_z FROM building_origin WHERE source_building='" + b.name + "'")[0];
    if (!org) { console.log('  SKIP ' + b.name + ' — no building_origin (run build_mep_arc_envelope.js first)'); bDb.close(); continue; }

    // real floor/ceiling z from the extraction
    var slab = rows(bDb, "SELECT t.center_z cz, t.bbox_z bz FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid " +
      "WHERE m.ifc_class='IfcSlab' AND m.storey='Ground Floor' ORDER BY t.bbox_x*t.bbox_y DESC LIMIT 1")[0];
    var floorZ = slab ? (slab.cz + slab.bz / 2) : 0;
    var partTop = rows(bDb, "SELECT MIN(t.center_z + t.bbox_z/2) ztop FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid " +
      "WHERE m.ifc_class='IfcWallStandardCase' AND m.storey='Ground Floor'")[0];
    var ceilZ = (partTop && partTop.ztop != null) ? partTop.ztop : floorZ + 2.7;

    // the ROOM SETs (real footprints) available in this building
    var sets = rows(bDb, "SELECT bom_name, aabb_width_mm w, aabb_depth_mm d FROM m_bom WHERE group_by='ROOM' AND aabb_width_mm>0");
    function findSet(role) {
      var kws = ROLE_SET_KEYWORDS[String(role).toUpperCase()] || [String(role).toLowerCase()];
      for (var k = 0; k < kws.length; k++) {
        var hit = sets.find(function (s) { return String(s.bom_name).toLowerCase().indexOf(kws[k]) >= 0; });
        if (hit) return hit;
      }
      return null;
    }

    // FLOOR-grain room layout lines (role + dx/dy corner offset)
    var layout = rows(bDb, "SELECT l.role, l.dx, l.dy FROM m_bom_line l JOIN m_bom b2 ON l.M_BOM_ID=b2.M_BOM_ID " +
      "WHERE b2.group_by='ROOM' AND b2.bom_name='" + b.floorBom.replace(/'/g, "''") + "' ORDER BY l.sequence");

    mep.run('DELETE FROM building_room WHERE source_building = ?', [b.name]);
    var n = 0;
    layout.forEach(function (rl) {
      var set = findSet(rl.role);
      if (!set) { console.log('  §ROOM_SKIP ' + b.name + ' role=' + rl.role + ' — no matching ROOM SET footprint'); return; }
      var bx = (set.w || 3000) / 1000, by = (set.d || 3000) / 1000, bz = ceilZ - floorZ;
      var cx = org.min_x + rl.dx + bx / 2, cy = org.min_y + rl.dy + by / 2, cz = floorZ + bz / 2;
      mep.run('INSERT INTO building_room (source_building, role, cx, cy, cz, bx, by_, bz) VALUES (?,?,?,?,?,?,?,?)',
        [b.name, rl.role, cx, cy, cz, bx, by, bz]);
      console.log('  §ROOM ' + b.name + ' ' + rl.role + ' set="' + set.bom_name + '" c=(' + cx.toFixed(2) + ',' + cy.toFixed(2) +
        ') ' + bx.toFixed(2) + 'x' + by.toFixed(2) + 'x' + bz.toFixed(2));
      n++;
    });
    console.log('§ROOM_ENV building=' + b.name + ' rooms=' + n + ' floorZ=' + floorZ.toFixed(3) + ' ceilZ=' + ceilZ.toFixed(3));
    bDb.close();
  }

  var outBuf = Buffer.from(mep.export());
  fs.writeFileSync(MEP_DB, outBuf);
  mep.close();
  console.log('§ROOM_ENV_DONE wrote ' + MEP_DB + ' (' + outBuf.length + ' bytes)');
})().catch(function (e) { console.error('FAIL ' + (e && e.stack || e)); process.exit(1); });
