#!/usr/bin/env node
// s220_test.js — S220 IFC Import Pipeline Test Harness
// Usage: node deploy/dev/s220_test.js
// Output: deploy/dev/s220_test.log
// Issue: Validates import DB schema and data quality against Java-extracted reference DBs

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const LOG_FILE = path.join(__dirname, 's220_test.log');
const log = [];
let pass = 0, fail = 0;

function emit(msg) { log.push(msg); console.log(msg); }
function ok(test, detail) { pass++; emit(`  PASS §${test} — ${detail}`); }
function ng(test, detail) { fail++; emit(`  FAIL §${test} — ${detail}`); }

// Test buildings — Java-extracted reference DBs
const BUILDINGS_DIR = path.resolve(__dirname, '..', 'buildings');
const TESTS = [
  { name: 'SampleHouse', ext: path.join(BUILDINGS_DIR, 'SampleHouse_extracted.db'), lib: path.join(BUILDINGS_DIR, 'SampleHouse_library.db') },
  { name: 'FZKHaus', ext: path.join(BUILDINGS_DIR, 'FZKHaus_extracted.db'), lib: path.join(BUILDINGS_DIR, 'FZKHaus_library.db') },
];

// Required tables and columns (matches actual Java-extracted schema)
const EXT_SCHEMA = {
  elements_meta: ['guid', 'ifc_class', 'element_name', 'storey', 'discipline', 'material_name', 'material_rgba', 'building'],
  element_transforms: ['guid', 'center_x', 'center_y', 'center_z', 'rotation_x', 'rotation_y', 'rotation_z'],
  element_instances: ['guid', 'geometry_hash'],
};
const LIB_SCHEMA = {
  component_geometries: ['geometry_hash', 'vertices', 'faces'],
};

emit(`§S220_DB_TEST — ${new Date().toISOString()}`);

for (const t of TESTS) {
  emit(`\n── ${t.name} ──`);

  if (!fs.existsSync(t.ext)) { ng('FILE', `${t.name}_extracted.db not found at ${t.ext}`); continue; }
  if (!fs.existsSync(t.lib)) { ng('FILE', `${t.name}_library.db not found at ${t.lib}`); continue; }

  const extDb = new Database(t.ext, { readonly: true });
  const libDb = new Database(t.lib, { readonly: true });

  // 1. Schema validation — extracted DB
  for (const [table, cols] of Object.entries(EXT_SCHEMA)) {
    try {
      const info = extDb.pragma(`table_info(${table})`);
      const colNames = info.map(c => c.name);
      if (info.length === 0) { ng('SCHEMA', `${table} missing`); continue; }
      for (const col of cols) {
        if (colNames.includes(col)) ok('SCHEMA', `${table}.${col} exists`);
        else ng('SCHEMA', `${table}.${col} MISSING`);
      }
    } catch (e) { ng('SCHEMA', `${table}: ${e.message}`); }
  }

  // Schema validation — library DB
  for (const [table, cols] of Object.entries(LIB_SCHEMA)) {
    try {
      const info = libDb.pragma(`table_info(${table})`);
      const colNames = info.map(c => c.name);
      if (info.length === 0) { ng('LIB_SCHEMA', `${table} missing`); continue; }
      for (const col of cols) {
        if (colNames.includes(col)) ok('LIB_SCHEMA', `${table}.${col} exists`);
        else ng('LIB_SCHEMA', `${table}.${col} MISSING`);
      }
    } catch (e) { ng('LIB_SCHEMA', `${table}: ${e.message}`); }
  }

  // 2. Row counts
  const elCount = extDb.prepare('SELECT COUNT(*) as n FROM elements_meta').get().n;
  const trCount = extDb.prepare('SELECT COUNT(*) as n FROM element_transforms').get().n;
  const instCount = extDb.prepare('SELECT COUNT(*) as n FROM element_instances').get().n;
  const geoCount = libDb.prepare('SELECT COUNT(*) as n FROM component_geometries').get().n;
  emit(`  COUNTS elements=${elCount} transforms=${trCount} instances=${instCount} geometries=${geoCount}`);

  if (elCount > 0) ok('DATA', `${elCount} elements`);
  else ng('DATA', 'zero elements');

  // 3. No NULL transforms
  const nullTr = extDb.prepare(
    'SELECT COUNT(*) as n FROM element_transforms WHERE center_x IS NULL OR center_y IS NULL OR center_z IS NULL'
  ).get().n;
  if (nullTr === 0) ok('TRANSFORMS', 'no NULL center_x/y/z');
  else ng('TRANSFORMS', `${nullTr} rows with NULL coordinates`);

  // 4. No 0-byte BLOBs in library
  const zeroBlobCount = libDb.prepare(
    'SELECT COUNT(*) as n FROM component_geometries WHERE length(vertices) = 0 OR vertices IS NULL'
  ).get().n;
  if (zeroBlobCount === 0) ok('BLOBS', 'no zero-byte vertex BLOBs');
  else ng('BLOBS', `${zeroBlobCount} zero-byte vertex BLOBs`);

  // 5. Coordinate sanity — envelope within 0.5m-2000m per axis
  const bbox = extDb.prepare(`
    SELECT MIN(center_x) as min_x, MAX(center_x) as max_x,
           MIN(center_y) as min_y, MAX(center_y) as max_y,
           MIN(center_z) as min_z, MAX(center_z) as max_z
    FROM element_transforms
  `).get();
  const ranges = {
    x: bbox.max_x - bbox.min_x,
    y: bbox.max_y - bbox.min_y,
    z: bbox.max_z - bbox.min_z,
  };
  emit(`  ENVELOPE x=${ranges.x.toFixed(1)}m y=${ranges.y.toFixed(1)}m z=${ranges.z.toFixed(1)}m`);
  for (const [axis, range] of Object.entries(ranges)) {
    if (range >= 0.5 && range <= 2000) ok('ENVELOPE', `${axis}=${range.toFixed(1)}m in range`);
    else ng('ENVELOPE', `${axis}=${range.toFixed(1)}m out of 0.5-2000m range — possible unit/scaling bug`);
  }

  // 6. Cross-table integrity: every element should have a transform
  const orphanTr = extDb.prepare(`
    SELECT COUNT(*) as n FROM elements_meta m
    LEFT JOIN element_transforms t ON t.guid = m.guid
    WHERE t.guid IS NULL
  `).get().n;
  if (orphanTr === 0) ok('INTEGRITY', 'all elements have transforms');
  else ng('INTEGRITY', `${orphanTr} elements without transforms`);

  // 7. Every element should have an instance record
  const orphanInst = extDb.prepare(`
    SELECT COUNT(*) as n FROM elements_meta m
    LEFT JOIN element_instances i ON i.guid = m.guid
    WHERE i.guid IS NULL
  `).get().n;
  if (orphanInst === 0) ok('INTEGRITY', 'all elements have instances');
  else ng('INTEGRITY', `${orphanInst} elements without instances`);

  // 8. Every instance geometry_hash should exist in library
  const orphanGeo = extDb.prepare(`SELECT DISTINCT geometry_hash FROM element_instances`).all();
  let missingGeo = 0;
  for (const row of orphanGeo) {
    const found = libDb.prepare('SELECT 1 FROM component_geometries WHERE geometry_hash = ?').get(row.geometry_hash);
    if (!found) missingGeo++;
  }
  if (missingGeo === 0) ok('INTEGRITY', 'all geometry hashes found in library');
  else ng('INTEGRITY', `${missingGeo} geometry hashes missing from library`);

  // 9. Class distribution
  const classes = extDb.prepare('SELECT ifc_class, COUNT(*) as n FROM elements_meta GROUP BY ifc_class ORDER BY n DESC LIMIT 10').all();
  emit(`  CLASSES ${classes.map(c => c.ifc_class + ':' + c.n).join(' ')}`);

  extDb.close();
  libDb.close();
}

// Summary
emit(`\n── SUMMARY ──`);
emit(`§RESULT PASS=${pass} FAIL=${fail} TOTAL=${pass + fail}`);
if (fail > 0) emit('§STATUS SOME TESTS FAILED');
else emit('§STATUS ALL TESTS PASSED');

// Write log
fs.writeFileSync(LOG_FILE, log.join('\n') + '\n');
emit(`\nLog saved to ${LOG_FILE}`);
