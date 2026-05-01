/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// import_db_builder.js — Shared DB builder for IFC import
// Both landing2.html and import.js call buildImportDBs(SQL, data)
// Returns ONE database with all 4 tables (metadata + geometry).
// Geometry is instanced via geometry_hash — dedup preserved, one file to manage.
//
// Enterprise setup: For centralised library across projects, see
//   https://red1oon.github.io/BIMCompiler/BIM_Designer_Browser/
//   or contact the creator for consultation on shared component library setup.

function buildImportDBs(SQL, data) {
  var db = new SQL.Database();

  // S224: Use filename (without .ifc) as building name — IFC project name is often generic ("Project")
  var buildingName = (data.meta.filename || data.meta.name || 'Import').replace(/\.(ifc|dae|obj|glb|gltf|3ds|fbx|stl)$/i, '');

  // Project metadata
  db.run('CREATE TABLE IF NOT EXISTS project_metadata (key TEXT PRIMARY KEY, value TEXT)');
  db.run('INSERT INTO project_metadata VALUES (?,?),(?,?),(?,?)',
    ['project_name', data.meta.name, 'import_date', new Date().toISOString(), 'building_name', buildingName]);

  // Elements
  db.run('CREATE TABLE IF NOT EXISTS elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT, storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, building TEXT)');
  db.run('CREATE TABLE IF NOT EXISTS element_transforms (guid TEXT PRIMARY KEY, center_x REAL, center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL)');
  db.run('CREATE TABLE IF NOT EXISTS element_instances (guid TEXT PRIMARY KEY, geometry_hash TEXT)');

  var stmtEl = db.prepare('INSERT OR IGNORE INTO elements_meta VALUES (?,?,?,?,?,?,?,?)');
  for (var i = 0; i < data.elements.length; i++) {
    var el = data.elements[i];
    stmtEl.run([el.guid, el.ifcClass, el.name, el.storey, el.discipline, null, el.material, buildingName]);
  }
  stmtEl.free();

  var stmtTr = db.prepare('INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?,?,?)');
  for (var i = 0; i < data.transforms.length; i++) {
    var t = data.transforms[i];
    stmtTr.run([t.guid, t.cx, t.cy, t.cz, t.rx, t.ry, t.rz]);
  }
  stmtTr.free();

  var stmtInst = db.prepare('INSERT OR IGNORE INTO element_instances VALUES (?,?)');
  for (var i = 0; i < data.geometries.length; i++) {
    stmtInst.run([data.geometries[i].guid, data.geometries[i].geomHash]);
  }
  stmtInst.free();

  // Geometry BLOBs — same DB, keyed by geometry_hash (instanced, deduped)
  db.run('CREATE TABLE IF NOT EXISTS component_geometries (geometry_hash TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT)');
  var stmtGeo = db.prepare('INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)');
  for (var i = 0; i < data.geometries.length; i++) {
    var g = data.geometries[i];
    stmtGeo.run([g.geomHash, new Uint8Array(g.vertices), new Uint8Array(g.indices), buildingName]);
  }
  stmtGeo.free();

  console.log('[S220] §DB_BUILD single_db: elements=' + data.elements.length + ' transforms=' + data.transforms.length + ' instances=' + data.geometries.length + ' geometries=' + data.geometries.length);

  var buf = db.export().buffer;
  db.close();

  return { extractedDb: buf, libraryDb: buf };
}
