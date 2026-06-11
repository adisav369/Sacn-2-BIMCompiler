#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * compile_warehouse.js — GardenWorld warehouse model compiler.
 *
 * Implementing docs/SPATIAL_PICKING_SPEC.md §S-1 — Witness: W-WH-COMPILE
 * (+ docs/BOMBasedCompilation.md §2.1 recipe→placement, §4 tack convention,
 *  §4.2 BUFFER invariant, §6 verbs TILE/ROUTE).
 *
 * EXTRACT: config/warehouse_gardenworld.yaml names REAL m_locator rows
 *   (Value + m_locator_id) from build/erp/ad_seed_fullwidth.db. Any recipe bin
 *   naming a locator absent from m_locator FAILS the compile (§FALSIFIER —
 *   no invented bins); any m_locator row missing from the recipe also fails
 *   (bins == m_locator rows mapped, a bijection).
 * COMPILE: positions come from the EXISTING BOM recursion path —
 *   deploy/dev/bom_walker.js (walk m_bom/m_bom_line, the same code the
 *   building→floor→room→furniture path runs) + deploy/dev/verb_expand.js
 *   (TILE bins along racks, ROUTE racks along the walk order). M_Locator.X/Y/Z
 *   are TEXT labels in the ERP — never read as coordinates, never invented.
 * OUTPUT: build/erp/warehouse_gardenworld.db — viewer-streamable schema
 *   (project_metadata / elements_meta / element_transforms / element_instances /
 *    component_geometries) + the recipe itself (m_bom / m_bom_line) so §S-2
 *   can derive the route from the tree. Each BIN mesh guid = its m_locator_id
 *   (BIMtoERP linkage key, reversed).
 *
 * Run: node scripts/compile_warehouse.js [yamlPath] [outDbPath] [seedDbPath]
 * Read the log after every run.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var yaml = require('js-yaml');
var Database = require('better-sqlite3');

// ── Load the EXISTING recursion + verb code (browser IIFEs → window shim) ──
global.window = global.window || global;
require('../deploy/dev/bom_walker.js');   // window.BOMWalker
require('../deploy/dev/verb_expand.js');  // window.VerbExpand
var BOMWalker = global.window.BOMWalker;
var VerbExpand = global.window.VerbExpand;

var ROOT = path.join(__dirname, '..');
var YAML_PATH = process.argv[2] || path.join(ROOT, 'config', 'warehouse_gardenworld.yaml');
var OUT_PATH = process.argv[3] || path.join(ROOT, 'build', 'erp', 'warehouse_gardenworld.db');
var SEED_PATH = process.argv[4] || path.join(ROOT, 'build', 'erp', 'ad_seed_fullwidth.db');

function fail(msg) { console.error('§WH COMPILE FAIL ' + msg); process.exit(1); }

// ── Schematic dimensions (metres) — compiled layout constants, spec §S-1:
//    "topology + walk order matter, millimetres don't". All COMPILED, none read.
var BIN = { w: 1.2, d: 1.2, h: 1.1 };
var RACK = { w: 2.4, d: 1.2, h: 2.4 };
var RACK_PITCH_Y = 3.0;     // ROUTE step along the aisle walk
var AISLE_W = 6.0;          // rack row + walking strip
var AISLE_PITCH_X = 8.0;    // TILE step between aisles
var FLOOR_T = 0.1, STRIP_T = 0.12;

// ── 1. EXTRACT: yaml topology + m_locator truth ─────────────────────────────
var recipe = yaml.load(fs.readFileSync(YAML_PATH, 'utf8'));
var seed = new Database(SEED_PATH, { readonly: true });
var locRows = seed.prepare(
  // seed columns are declared-case (M_Locator_ID, Value) — alias to stable lowercase keys
  'SELECT l.m_locator_id AS m_locator_id, l.value AS value, w.value AS wh_value, w.name AS wh_name ' +
  'FROM m_locator l JOIN m_warehouse w ON w.m_warehouse_id = l.m_warehouse_id'
).all();
seed.close();
var locById = {};
locRows.forEach(function(r) { locById[r.m_locator_id] = r; });

var yamlBins = [];
recipe.aisles.forEach(function(aisle) {
  aisle.racks.forEach(function(rack) {
    rack.bins.forEach(function(bin) { yamlBins.push({ aisle: aisle, rack: rack, bin: bin }); });
  });
});

// §FALSIFIER (spec §S-1): recipe bin naming a locator absent from m_locator → FAIL.
yamlBins.forEach(function(e) {
  var real = locById[e.bin.m_locator_id];
  if (!real) fail('recipe bin "' + e.bin.locator_value + '" names m_locator_id=' + e.bin.m_locator_id + ' — NOT in m_locator (no invented bins)');
  if (real.value !== e.bin.locator_value) fail('m_locator_id=' + e.bin.m_locator_id + ' Value mismatch: yaml "' + e.bin.locator_value + '" vs m_locator "' + real.value + '"');
  if (real.wh_value !== e.rack.warehouse) fail('m_locator_id=' + e.bin.m_locator_id + ' warehouse mismatch: yaml "' + e.rack.warehouse + '" vs m_warehouse "' + real.wh_value + '"');
});
// Bijection: every m_locator row must be mapped exactly once.
var seen = {};
yamlBins.forEach(function(e) {
  if (seen[e.bin.m_locator_id]) fail('m_locator_id=' + e.bin.m_locator_id + ' mapped twice in recipe');
  seen[e.bin.m_locator_id] = true;
});
locRows.forEach(function(r) {
  if (!seen[r.m_locator_id]) fail('m_locator_id=' + r.m_locator_id + ' ("' + r.value + '") in m_locator but missing from recipe');
});
console.log('§WH_EXTRACT yaml bins=' + yamlBins.length + ' m_locator rows=' + locRows.length + ' bijection OK');

// ── 2. Output db: viewer schema + recipe tables (SampleHouse_extracted.db shape) ──
if (fs.existsSync(OUT_PATH)) fs.unlinkSync(OUT_PATH);
var out = new Database(OUT_PATH);
out.exec(
  'CREATE TABLE project_metadata (key TEXT PRIMARY KEY, value TEXT);' +
  'CREATE TABLE elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT, storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, building TEXT);' +
  'CREATE TABLE element_transforms (guid TEXT PRIMARY KEY, center_x REAL, center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL, bbox_x REAL, bbox_y REAL, bbox_z REAL);' +
  'CREATE TABLE element_instances (guid TEXT PRIMARY KEY, geometry_hash TEXT);' +
  'CREATE TABLE component_geometries (geometry_hash TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT);' +
  'CREATE TABLE m_bom (M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT, bom_id TEXT NOT NULL UNIQUE, Value TEXT, Name TEXT, bom_name TEXT NOT NULL, description TEXT, target_ifc_class TEXT, group_by TEXT NOT NULL, is_active INTEGER DEFAULT 1, bom_level TEXT, bom_type TEXT NOT NULL, origin_x REAL DEFAULT 0, origin_y REAL DEFAULT 0, origin_z REAL DEFAULT 0, entity_type TEXT DEFAULT \'U\', aabb_width_mm INTEGER DEFAULT 0, aabb_depth_mm INTEGER DEFAULT 0, aabb_height_mm INTEGER DEFAULT 0);' +
  'CREATE TABLE m_bom_line (M_BOM_Line_ID INTEGER PRIMARY KEY AUTOINCREMENT, bom_id TEXT NOT NULL, child_product_id TEXT, role TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 1, sequence INTEGER DEFAULT 100, is_active INTEGER DEFAULT 1, dx REAL DEFAULT 0, dy REAL DEFAULT 0, dz REAL DEFAULT 0, allocated_width_mm INTEGER DEFAULT 0, allocated_depth_mm INTEGER DEFAULT 0, allocated_height_mm INTEGER DEFAULT 0, component_type TEXT DEFAULT NULL, storey TEXT, element_ref TEXT, ordinal INTEGER DEFAULT 0, material_rgba TEXT, entity_type TEXT DEFAULT \'U\', verb_ref TEXT DEFAULT NULL);'
);

// ── 3. Write the recipe (m_bom / m_bom_line) — verbs compute the offsets ────
// BOMBasedCompilation §2.1: the recipe is FACTORED (type lines + verb formulas);
// dx/dy/dz per line follow the §4 tack convention (parent-LBD-relative).
var nAisles = recipe.aisles.length;
var maxRacks = Math.max.apply(null, recipe.aisles.map(function(a) { return a.racks.length; }));
var SITE = { w: nAisles * AISLE_PITCH_X, d: maxRacks * RACK_PITCH_Y, h: RACK.h + 0.6 };

var insBom = out.prepare('INSERT INTO m_bom (bom_id, Value, Name, bom_name, group_by, bom_level, bom_type, aabb_width_mm, aabb_depth_mm, aabb_height_mm) VALUES (?,?,?,?,?,?,?,?,?,?)');
var insLine = out.prepare('INSERT INTO m_bom_line (bom_id, child_product_id, role, qty, sequence, dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm, component_type, storey, element_ref, ordinal, verb_ref) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)');
function mm(m) { return Math.round(m * 1000); }

insBom.run('WH_SITE_GW', 'WH_SITE_GW', recipe.site + ' Warehouse Site', recipe.site + ' Warehouse Site', 'site', 'UNIT', 'SITE', mm(SITE.w), mm(SITE.d), mm(SITE.h));

// Site → aisles: TILE verb computes the aisle origins (§6 TILE).
var aisleOrigins = VerbExpand.expandTile('TILE:' + nAisles + ':1:' + AISLE_PITCH_X + ':0', 0, 0, 0);
var walkSeq = 0;
recipe.aisles.forEach(function(aisle, ai) {
  var aisleDepth = aisle.racks.length * RACK_PITCH_Y;
  insBom.run(aisle.id, aisle.id, aisle.name, aisle.name, 'aisle', 'FLOOR', 'AISLE', mm(AISLE_W), mm(aisleDepth), mm(RACK.h));
  var o = aisleOrigins[ai];
  insLine.run('WH_SITE_GW', aisle.id, 'AISLE', 1, (ai + 1) * 10, o[0], o[1], o[2], mm(AISLE_W), mm(aisleDepth), mm(RACK.h), null, aisle.id, null, ai + 1, null);

  // Aisle → racks: ROUTE verb = the walk order along the aisle (§S-2's order).
  var rackOrigins = VerbExpand.expandRoute('ROUTE:Y:' + RACK_PITCH_Y + ':' + aisle.racks.length, 0, 0, 0);
  aisle.racks.forEach(function(rack, ri) {
    walkSeq++;
    insBom.run(rack.id, rack.id, rack.warehouse + ' rack', rack.warehouse + ' rack', 'rack', 'ROOM', 'RACK', mm(RACK.w), mm(RACK.d), mm(RACK.h));
    var ro = rackOrigins[ri];
    insLine.run(aisle.id, rack.id, 'RACK', 1, (ri + 1) * 10, ro[0], ro[1], ro[2], mm(RACK.w), mm(RACK.d), mm(RACK.h), null, aisle.id, rack.warehouse, walkSeq, null);

    // Rack → bins: ONE type line, TILE expands bins along the rack (§S-1 verbatim).
    insLine.run(rack.id, 'WH_BIN', 'BIN', rack.bins.length, 10, 0, 0, 0, mm(BIN.w * rack.bins.length), mm(BIN.d), mm(BIN.h), null, aisle.id, JSON.stringify(rack.bins.map(function(b) { return b.m_locator_id; })), walkSeq, 'TILE:' + rack.bins.length + ':1:' + BIN.w + ':0');
    // §4.2 BUFFER phantom — parent dim == SUM(children allocated) along tack-X.
    var bufW = mm(RACK.w) - mm(BIN.w) * rack.bins.length;
    insLine.run(rack.id, 'BUFFER', 'BUFFER', 1, 99, 0, 0, 0, bufW, mm(RACK.d), mm(RACK.h), 'PHANTOM', aisle.id, null, 0, null);
  });
  // Aisle BUFFER along tack-Y: aisle depth == SUM(rack allocated depths) + buffer.
  var bufD = mm(aisleDepth) - mm(RACK.d) * aisle.racks.length;
  insLine.run(aisle.id, 'BUFFER', 'BUFFER', 1, 99, 0, 0, 0, mm(AISLE_W), bufD, mm(RACK.h), 'PHANTOM', aisle.id, null, 0, null);
});
// Site BUFFER along tack-X.
insLine.run('WH_SITE_GW', 'BUFFER', 'BUFFER', 1, 99, 0, 0, 0, mm(SITE.w) - mm(AISLE_W) * nAisles, mm(SITE.d), mm(SITE.h), 'PHANTOM', 'SITE', null, 0, null);

// ── 4. Walk the recipe with the EXISTING walker → placements ────────────────
// bom_walker.js wants a sql.js db — feed it the recipe we just wrote.
var initSqlJs = require('sql.js');
var binsByRack = {}, rackWhById = {};
recipe.aisles.forEach(function(a) { a.racks.forEach(function(r) { binsByRack[r.id] = r.bins; rackWhById[r.id] = r.warehouse; }); });

// Box geometry: 24 verts (4/face, crisp normals), 12 tris, CENTRED at origin,
// IFC coords (z-up, metres). Distinct dims → distinct vertex content (no-cubes gate).
function boxBlobs(w, d, h) {
  var x = w / 2, y = d / 2, z = h / 2;
  var faces = [ // [4 corners], outward CCW
    [[-x,-y,-z],[ x,-y,-z],[ x,-y, z],[-x,-y, z]],   // -Y front
    [[ x, y,-z],[-x, y,-z],[-x, y, z],[ x, y, z]],   // +Y back
    [[-x, y,-z],[-x,-y,-z],[-x,-y, z],[-x, y, z]],   // -X left
    [[ x,-y,-z],[ x, y,-z],[ x, y, z],[ x,-y, z]],   // +X right
    [[-x,-y, z],[ x,-y, z],[ x, y, z],[-x, y, z]],   // +Z top
    [[-x, y,-z],[ x, y,-z],[ x,-y,-z],[-x,-y,-z]]    // -Z bottom
  ];
  var verts = new Float32Array(24 * 3), idx = new Uint32Array(12 * 3), vi = 0, ii = 0;
  faces.forEach(function(f, fi) {
    f.forEach(function(c) { verts[vi++] = c[0]; verts[vi++] = c[1]; verts[vi++] = c[2]; });
    var b = fi * 4;
    idx[ii++] = b; idx[ii++] = b + 1; idx[ii++] = b + 2;
    idx[ii++] = b; idx[ii++] = b + 2; idx[ii++] = b + 3;
  });
  return { vertices: Buffer.from(verts.buffer), faces: Buffer.from(idx.buffer) };
}

var insMeta = out.prepare('INSERT INTO elements_meta VALUES (?,?,?,?,?,?,?,?)');
var insTr = out.prepare('INSERT INTO element_transforms VALUES (?,?,?,?,0,0,0,?,?,?)');
var insInst = out.prepare('INSERT INTO element_instances VALUES (?,?)');
var insGeo = out.prepare('INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)');
var BUILDING = recipe.site;
var geoHashes = {};
function ensureGeo(kind, w, d, h) {
  var hash = 'WHBOX_' + kind + '_' + mm(w) + 'x' + mm(d) + 'x' + mm(h);
  if (!geoHashes[hash]) { var b = boxBlobs(w, d, h); insGeo.run(hash, b.vertices, b.faces, BUILDING); geoHashes[hash] = true; }
  return hash;
}
// placeElement: LBD (§4.1) + dims → center transform + instance row.
function placeElement(guid, ifcClass, name, storey, disc, matName, rgba, kind, lbd, dims) {
  insMeta.run(guid, ifcClass, name, storey, disc, matName, rgba, BUILDING);
  insTr.run(guid, lbd[0] + dims.w / 2, lbd[1] + dims.d / 2, lbd[2] + dims.h / 2, dims.w, dims.d, dims.h);
  insInst.run(guid, ensureGeo(kind, dims.w, dims.d, dims.h));
}

// Site floor slab (root's own mesh).
placeElement('GW_SITE_FLOOR', 'IfcSlab', recipe.site + ' warehouse floor', 'SITE', 'STR', 'Concrete', '0.74,0.74,0.78,1.000', 'FLOOR', [0, 0, -FLOOR_T], { w: SITE.w, d: SITE.d, h: FLOOR_T });

var sqlBuf = out.serialize(); // recipe is in — hand the same bytes to the walker
var binCount = 0, rackCount = 0, aisleCount = 0;
initSqlJs().then(function(SQL) {
  var walkDb = new SQL.Database(new Uint8Array(sqlBuf));
  var originStack = [[0, 0, 0]]; // site LBD at world origin

  var stats = BOMWalker.walk(walkDb, 'WH_SITE_GW', {
    onSubAssembly: function(ctx) {
      var p = originStack[originStack.length - 1];
      var lbd = [p[0] + ctx.line.dx, p[1] + ctx.line.dy, p[2] + ctx.line.dz];
      originStack.push(lbd);
      if (ctx.line.role === 'AISLE') {
        aisleCount++;
        var aDepth = ctx.line.allocDepth / 1000;
        placeElement(ctx.childBom.bomId, 'IfcSlab', ctx.childBom.name + ' (aisle strip)', ctx.childBom.bomId, 'ARC', 'Epoxy strip', '0.52,0.58,0.66,1.000', 'AISLE_' + ctx.childBom.bomId, lbd, { w: AISLE_W, d: aDepth, h: STRIP_T });
      } else if (ctx.line.role === 'RACK') {
        rackCount++;
        placeElement(ctx.childBom.bomId, 'IfcFurnishingElement', ctx.childBom.name + ' [' + rackWhById[ctx.childBom.bomId] + ']', ctx.line.storey, 'ARC', 'Pallet rack', '0.82,0.50,0.22,1.000', 'RACK', [lbd[0], lbd[1], lbd[2] + STRIP_T], { w: RACK.w, d: RACK.d, h: RACK.h });
      }
    },
    onSubAssemblyComplete: function() { originStack.pop(); },
    onLeaf: function(ctx) {
      if (ctx.line.childProductId !== 'WH_BIN') return;
      var p = originStack[originStack.length - 1];
      // §S-1 verbatim: TILE the bins along the rack via the existing verb.
      var offs = VerbExpand.expandVerb(ctx.line.verbRef, ctx.line.qty, p[0], p[1], p[2]);
      var bins = binsByRack[ctx.bom.bomId] || [];
      if (offs.length !== bins.length) fail('TILE expansion ' + offs.length + ' != bins ' + bins.length + ' for ' + ctx.bom.bomId);
      bins.forEach(function(bin, i) {
        binCount++;
        var real = locById[bin.m_locator_id];
        // GUID = m_locator_id — the BIMtoERP linkage key, reversed (spec §S-1).
        placeElement(String(bin.m_locator_id), 'IfcBuildingElementProxy',
          real.value + ' [' + real.wh_name + ']', ctx.line.storey, 'MEP', 'Bin',
          '0.25,0.55,0.92,1.000', 'BIN',
          [offs[i][0], offs[i][1], offs[i][2] + STRIP_T + 0.05], { w: BIN.w, d: BIN.d, h: BIN.h });
      });
    }
  }, 'WAREHOUSE');

  // project_metadata — provenance, all EXTRACTED/COMPILED.
  var insPm = out.prepare('INSERT INTO project_metadata VALUES (?,?)');
  insPm.run('project_name', recipe.site + ' Warehouse');
  insPm.run('building_name', BUILDING);
  insPm.run('source_file', 'config/warehouse_gardenworld.yaml + build/erp/ad_seed_fullwidth.db m_locator (11 rows)');
  insPm.run('import_date', new Date().toISOString());
  insPm.run('spec', 'docs/SPATIAL_PICKING_SPEC.md §S-1');

  // ── 5. Self render-gate (no-cubes): every hash resolves + distinct vertex content ──
  var miss = out.prepare('SELECT COUNT(*) c FROM element_instances i LEFT JOIN component_geometries g ON g.geometry_hash=i.geometry_hash WHERE g.geometry_hash IS NULL').get().c;
  var hashN = out.prepare('SELECT COUNT(*) c FROM component_geometries').get().c;
  var distinctV = out.prepare('SELECT COUNT(DISTINCT vertices) c FROM component_geometries').get().c;
  if (miss > 0) fail('render gate: ' + miss + ' instances with missing geometry (would §BBOX_KEEP as cubes)');
  if (distinctV < 2 || distinctV !== hashN) fail('render gate: vertex blobs not distinct (' + distinctV + '/' + hashN + ') — placeholder-cube smell');

  out.close();
  var bytes = fs.statSync(OUT_PATH).size;
  console.log('§WH_WALK aisles=' + aisleCount + ' racks=' + rackCount + ' bins=' + binCount + ' leaves=' + stats.leafCount + ' phantoms=' + stats.phantomCount);
  console.log('§WH bins=' + binCount + ' == m_locator rows mapped (' + binCount + '/' + locRows.length + ')');
  console.log('§WH_RENDERGATE hashes=' + hashN + ' distinct_vertex_blobs=' + distinctV + ' blob_miss=' + miss + ' PASS (no cubes)');
  console.log('§WH_DB ' + OUT_PATH + ' bytes=' + bytes);
  if (binCount !== locRows.length) fail('bins ' + binCount + ' != m_locator rows ' + locRows.length);
}).catch(function(e) { fail(e.message + '\n' + e.stack); });
