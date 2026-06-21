#!/usr/bin/env node
/**
 * build_mep_arc_envelope.js — Populate arc_envelope + building_origin in mep_rw.db
 *
 * Reads ARC AABB boxes from each per-building extracted DB and writes them into
 * mep_rw.db so rwRouteSegments() can load the ARC clash envelope without needing
 * a separate building DB (the op-log modeller has none).
 *
 * SOURCE: elements_meta + element_transforms from *_extracted.db (same space as
 *         ad_mep_anchor x_m/y_m/z_m — both in extraction-space metres).
 *
 * Schema added:
 *   arc_envelope  (source_building, cx, cy, cz, w, d, h) — ARC element bboxes in metres
 *   building_origin (source_building, min_x, min_y, min_z) — structural AABB min
 *     (= allMinXYZ used by StructuralBomBuilder; the origin of the BOM coordinate frame)
 *
 * Run: node scripts/build_mep_arc_envelope.js
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');
var BetterSqlite3 = require('better-sqlite3');   // native (rtree-capable) — for reading building DBs

// adapter: run sql on a better-sqlite3 db, return sql.js-shaped [{values:[[...],...]}] so downstream v[0..5] works
function bquery(db, sql) {
  var rows = db.prepare(sql).raw().all();
  return rows.length ? [{ values: rows }] : [];
}

var ROOT = path.join(__dirname, '..');
var MEP_DB = path.join(ROOT, 'deploy/dev/mep_rw.db');
var BUILDINGS = [
  { name: 'Duplex',      db: path.join(ROOT, 'deploy/buildings/Duplex_extracted.db') },
  { name: 'SampleHouse', db: path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db') },
  // Terminal: the GEO-MATCHED extraction = library/archive (its elements_rtree centres equal the 340
  // ad_mep_anchor x_m/y_m/z_m exactly; site_normalization already applied). The deploy/buildings &
  // deploy/dev copies are DIFFERENT extractions in other frames (guid 0 / coord-frame mismatch) — do NOT use.
  { name: 'Terminal',    db: path.join(ROOT, 'library/archive/Terminal_extracted.db') },
];

// ARC classes used for the clash envelope (same filter as _rwLoadArcEnvelope in routewalker.js)
var ARC_CLASSES = "('IfcWall','IfcWallStandardCase','IfcSlab','IfcRoof','IfcCovering')";

(async function () {
  var SQL = await initSqlJs();
  var mepBuf = fs.readFileSync(MEP_DB);
  var mep = new SQL.Database(new Uint8Array(mepBuf));

  // Create tables (idempotent)
  mep.run('CREATE TABLE IF NOT EXISTS arc_envelope (' +
    'source_building TEXT NOT NULL, cx REAL, cy REAL, cz REAL, w REAL, d REAL, h REAL)');
  mep.run('CREATE TABLE IF NOT EXISTS building_origin (' +
    'source_building TEXT PRIMARY KEY, min_x REAL, min_y REAL, min_z REAL)');

  for (var b of BUILDINGS) {
    if (!fs.existsSync(b.db)) {
      console.log('  SKIP ' + b.name + ' — DB not found: ' + b.db);
      continue;
    }
    var bDb = new BetterSqlite3(b.db, { readonly: true });

    // Format detect: served DBs carry bbox in element_transforms; extraction DBs carry AABB in elements_rtree.
    function hasTable(n){ return bquery(bDb, "SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + n + "'").length>0; }
    function hasCol(t,c){ var r = bquery(bDb, "PRAGMA table_info(" + t + ")"); return r.length && r[0].values.some(function(v){return v[1]===c;}); }
    var EXTRACTION_FMT = hasTable('elements_rtree') && !(hasTable('element_transforms') && hasCol('element_transforms','bbox_x'));

    // 1. ARC envelope: walls/slabs/roofs/coverings with non-zero bbox.
    //    served fmt → element_transforms center/bbox ; extraction fmt → elements_rtree AABB (same metres frame)
    var arcRows = EXTRACTION_FMT ? bquery(bDb,
        'SELECT (r.minX+r.maxX)/2, (r.minY+r.maxY)/2, (r.minZ+r.maxZ)/2, (r.maxX-r.minX), (r.maxY-r.minY), (r.maxZ-r.minZ) ' +
        'FROM elements_meta m JOIN elements_rtree r ON m.id = r.id ' +
        'WHERE m.discipline = \'ARC\' AND m.ifc_class IN ' + ARC_CLASSES +
        ' AND r.maxX > r.minX AND r.maxY > r.minY AND r.maxZ > r.minZ'
      ) : bquery(bDb,
        'SELECT t.center_x, t.center_y, t.center_z, t.bbox_x, t.bbox_y, t.bbox_z ' +
        'FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid ' +
        'WHERE m.discipline = \'ARC\' AND m.ifc_class IN ' + ARC_CLASSES +
        ' AND t.bbox_x > 0 AND t.bbox_y > 0 AND t.bbox_z > 0'
      );
    mep.run('DELETE FROM arc_envelope WHERE source_building = ?', [b.name]);
    var arcBoxCount = 0;
    if (arcRows.length && arcRows[0].values.length) {
      for (var v of arcRows[0].values) {
        mep.run('INSERT INTO arc_envelope (source_building, cx, cy, cz, w, d, h) VALUES (?,?,?,?,?,?,?)',
          [b.name, v[0], v[1], v[2], v[3], v[4], v[5]]);
        arcBoxCount++;
      }
    }

    // 2. Building origin: ARC+STR AABB min (= StructuralBomBuilder's allMinXYZ)
    var orgRows = EXTRACTION_FMT ? bquery(bDb,
        'SELECT MIN(r.minX), MIN(r.minY), MIN(r.minZ) ' +
        'FROM elements_meta m JOIN elements_rtree r ON m.id = r.id ' +
        'WHERE m.discipline IN (\'ARC\',\'STR\') AND r.maxX > r.minX AND r.maxY > r.minY AND r.maxZ > r.minZ'
      ) : bquery(bDb,
        'SELECT MIN(t.center_x - t.bbox_x/2), MIN(t.center_y - t.bbox_y/2), MIN(t.center_z - t.bbox_z/2) ' +
        'FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid ' +
        'WHERE m.discipline IN (\'ARC\',\'STR\') AND t.bbox_x > 0 AND t.bbox_y > 0 AND t.bbox_z > 0'
      );
    var orgX = null, orgY = null, orgZ = null;
    if (orgRows.length && orgRows[0].values.length) {
      var ov = orgRows[0].values[0];
      orgX = ov[0]; orgY = ov[1]; orgZ = ov[2];
    }
    mep.run('INSERT OR REPLACE INTO building_origin (source_building, min_x, min_y, min_z) VALUES (?,?,?,?)',
      [b.name, orgX, orgY, orgZ]);

    console.log('§ARC_ENV building=' + b.name + ' arc_boxes=' + arcBoxCount +
      ' origin=[' + [orgX, orgY, orgZ].map(function(v) { return v != null ? v.toFixed(4) : 'null'; }).join(',') + ']');
    bDb.close();
  }

  // Persist
  var outBuf = Buffer.from(mep.export());
  fs.writeFileSync(MEP_DB, outBuf);
  mep.close();
  console.log('§ARC_ENV_DONE wrote ' + MEP_DB + ' (' + outBuf.length + ' bytes)');
})().catch(function(e) {
  console.error('FAIL ' + (e && e.stack || e));
  process.exit(1);
});
