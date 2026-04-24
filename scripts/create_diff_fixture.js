#!/usr/bin/env node
// create_diff_fixture.js — S225 Create a realistic diff test fixture
// Produces a single combined DB (metadata + geometry) for v0 and v1
// v1 = revised SampleHouse: 2 removed, 3 changed, 2 added
// Usage: node scripts/create_diff_fixture.js
// Output: deploy/dev/test_sh_v0.db, deploy/dev/test_sh_v1.db

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const EXT = path.resolve(__dirname, '..', 'deploy/buildings/SampleHouse_extracted.db');
const LIB = path.resolve(__dirname, '..', 'deploy/buildings/SampleHouse_library.db');
const OUT_V0 = path.resolve(__dirname, '..', 'deploy/dev/test_sh_v0.db');
const OUT_V1 = path.resolve(__dirname, '..', 'deploy/dev/test_sh_v1.db');

if (!fs.existsSync(EXT) || !fs.existsSync(LIB)) {
  console.error('§FIXTURE_ERROR SampleHouse DBs not found');
  process.exit(1);
}

function createCombinedDb(outPath) {
  if (fs.existsSync(outPath)) fs.unlinkSync(outPath);
  const out = new Database(outPath);
  const ext = new Database(EXT, { readonly: true });
  const lib = new Database(LIB, { readonly: true });

  // Copy extracted tables
  for (const t of ext.prepare("SELECT name, sql FROM sqlite_master WHERE type='table'").all()) {
    const ddl = t.sql.replace(/,\s*FOREIGN KEY\s*\([^)]*\)\s*REFERENCES\s*[^)]*\)/gi, '');
    out.exec(ddl);
    const rows = ext.prepare(`SELECT * FROM ${t.name}`).all();
    if (rows.length > 0) {
      const cols = Object.keys(rows[0]);
      const ph = cols.map(() => '?').join(',');
      const ins = out.prepare(`INSERT INTO ${t.name} (${cols.join(',')}) VALUES (${ph})`);
      for (const r of rows) ins.run(...cols.map(c => r[c]));
    }
  }

  // Copy library tables (component_geometries, surface_styles)
  for (const t of lib.prepare("SELECT name, sql FROM sqlite_master WHERE type='table'").all()) {
    try { out.exec(t.sql); } catch(e) { /* table may exist */ }
    const rows = lib.prepare(`SELECT * FROM ${t.name}`).all();
    if (rows.length > 0) {
      const cols = Object.keys(rows[0]);
      const ph = cols.map(() => '?').join(',');
      const ins = out.prepare(`INSERT OR IGNORE INTO ${t.name} (${cols.join(',')}) VALUES (${ph})`);
      for (const r of rows) ins.run(...cols.map(c => r[c]));
    }
  }

  ext.close();
  lib.close();
  return out;
}

// ── V0: Combined base (unchanged SampleHouse) ──
console.log('§FIXTURE creating v0 (base)...');
const v0 = createCombinedDb(OUT_V0);
const v0Count = v0.prepare('SELECT COUNT(*) as n FROM elements_meta').get().n;
const v0Geo = v0.prepare('SELECT COUNT(*) as n FROM component_geometries').get().n;
console.log(`§FIXTURE_V0 elements=${v0Count} geometries=${v0Geo}`);
v0.close();

// ── V1: Modified revision ──
console.log('§FIXTURE creating v1 (revision)...');
const v1 = createCombinedDb(OUT_V1);

// Get all GUIDs
const allGuids = v1.prepare('SELECT guid, ifc_class, element_name FROM elements_meta').all();

// 1. REMOVE 2 furniture elements (simulating demolition)
const furniture = allGuids.filter(r => r.ifc_class === 'IfcFurniture');
const toRemove = furniture.slice(0, 2);
for (const r of toRemove) {
  v1.prepare('DELETE FROM elements_meta WHERE guid = ?').run(r.guid);
  v1.prepare('DELETE FROM element_transforms WHERE guid = ?').run(r.guid);
  v1.prepare('DELETE FROM element_instances WHERE guid = ?').run(r.guid);
  console.log(`  §REMOVE ${r.guid.substring(0, 30)} (${r.ifc_class} — ${r.element_name})`);
}

// 2. CHANGE 3 elements — different material (simulating spec change)
const walls = allGuids.filter(r => r.ifc_class === 'IfcWall' || r.ifc_class === 'IfcWallStandardCase');
const toChange = walls.slice(0, 3);
for (const r of toChange) {
  v1.prepare("UPDATE elements_meta SET material_rgba = '0.8,0.2,0.2,1.0', material_name = 'Revised_BrickRed' WHERE guid = ?").run(r.guid);
  console.log(`  §CHANGE ${r.guid.substring(0, 30)} material → BrickRed`);
}

// 3. ADD 2 new elements — a partition wall + a door (simulating addition)
// Reuse existing geometry hashes for realism
const wallHash = v1.prepare("SELECT geometry_hash FROM element_instances WHERE guid = ?").get(walls[0].guid);
const doorGuids = allGuids.filter(r => r.ifc_class === 'IfcDoor');
const doorHash = doorGuids.length > 0
  ? v1.prepare("SELECT geometry_hash FROM element_instances WHERE guid = ?").get(doorGuids[0].guid)
  : wallHash;

// Get a reference transform for positioning (near existing walls)
const refPos = v1.prepare("SELECT center_x, center_y, center_z FROM element_transforms WHERE guid = ?").get(walls[0].guid);

const addedElements = [
  {
    guid: 'S225_ADDED_PARTITION_WALL_001',
    ifc_class: 'IfcWall',
    element_name: 'Partition Wall:Interior_100mm:S225_NEW',
    storey: 'Ground Floor',
    discipline: 'ARC',
    material_name: 'Plasterboard',
    material_rgba: '0.9,0.9,0.85,1.0',
    hash: wallHash.geometry_hash,
    cx: refPos.center_x + 2.0,
    cy: refPos.center_y + 1.5,
    cz: refPos.center_z,
  },
  {
    guid: 'S225_ADDED_DOOR_002',
    ifc_class: 'IfcDoor',
    element_name: 'Door:Single_900mm:S225_NEW',
    storey: 'Ground Floor',
    discipline: 'ARC',
    material_name: 'Timber_Oak',
    material_rgba: '0.6,0.4,0.2,1.0',
    hash: doorHash.geometry_hash,
    cx: refPos.center_x + 2.0,
    cy: refPos.center_y + 1.5,
    cz: refPos.center_z + 0.1,
  }
];

for (const el of addedElements) {
  v1.prepare(
    "INSERT INTO elements_meta (guid, ifc_class, element_name, storey, discipline, material_name, material_rgba, building) VALUES (?, ?, ?, ?, ?, ?, ?, 'Ifc4_SampleHouse')"
  ).run(el.guid, el.ifc_class, el.element_name, el.storey, el.discipline, el.material_name, el.material_rgba);

  v1.prepare(
    "INSERT INTO element_transforms (guid, center_x, center_y, center_z, rotation_x, rotation_y, rotation_z) VALUES (?, ?, ?, ?, 0, 0, 0)"
  ).run(el.guid, el.cx, el.cy, el.cz);

  v1.prepare(
    "INSERT INTO element_instances (guid, geometry_hash) VALUES (?, ?)"
  ).run(el.guid, el.hash);

  console.log(`  §ADD ${el.guid} (${el.ifc_class} — ${el.element_name})`);
}

const v1Count = v1.prepare('SELECT COUNT(*) as n FROM elements_meta').get().n;
console.log(`§FIXTURE_V1 elements=${v1Count} (was ${v0Count}: -${toRemove.length} removed, +${addedElements.length} added, ${toChange.length} changed)`);

// Verify diff
const g0 = new Set(new Database(OUT_V0, { readonly: true }).prepare('SELECT guid FROM elements_meta').all().map(r => r.guid));
const g1 = new Set(v1.prepare('SELECT guid FROM elements_meta').all().map(r => r.guid));
const added = [...g1].filter(g => !g0.has(g));
const removed = [...g0].filter(g => !g1.has(g));
const overlap = [...g1].filter(g => g0.has(g)).length / Math.max(g0.size, g1.size);
console.log(`§FIXTURE_DIFF added=${added.length} removed=${removed.length} overlap=${(overlap * 100).toFixed(1)}% mode=${overlap >= 0.1 ? 'REVISION' : 'MERGE'}`);

v1.close();
console.log(`\n§FIXTURE_DONE v0=${OUT_V0} v1=${OUT_V1}`);
