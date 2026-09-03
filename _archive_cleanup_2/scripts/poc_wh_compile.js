#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_wh_compile.js — Witness W-WH-COMPILE for docs/SPATIAL_PICKING_SPEC.md §S-1.
 *
 * Issues this witness proves/disproves:
 *  T1 §WH        — bins in the compiled db == m_locator rows mapped (11/11 bijection,
 *                  bin guid == m_locator_id: the BIMtoERP linkage key reversed).
 *  T2 §WH_BUFFER — BOMBasedCompilation §4.2 space contract holds: per non-leaf BOM,
 *                  parent dim == SUM(children allocated) along the tack axis (BUFFER
 *                  phantom absorbs the gap) AND geometric containment bin⊂rack⊂aisle⊂site.
 *  T3 §WH_FALSIFIER — a yaml bin naming a locator ABSENT from m_locator must FAIL the
 *                  compile (no invented bins). A compile that passes is the failure.
 *  T4 §WH_RENDERGATE — no-cubes rule: every element_instances hash resolves to a real
 *                  vertex/face blob, blobs are distinct (not one placeholder cube),
 *                  floats finite, face indices in range — before the db is ever served.
 *
 * Run: bash build/erp/run_witness.sh scripts/poc_wh_compile.js
 * Read the log (build/erp/poc_wh_compile.log) after every run.
 */
'use strict';
var fs = require('fs');
var os = require('os');
var path = require('path');
var cp = require('child_process');
var Database = require('better-sqlite3');

var ROOT = path.join(__dirname, '..');
var YAML = path.join(ROOT, 'config', 'warehouse_gardenworld.yaml');
var OUT = path.join(ROOT, 'build', 'erp', 'warehouse_gardenworld.db');
var SEED = path.join(ROOT, 'build', 'erp', 'ad_seed_fullwidth.db');
var COMPILER = path.join(ROOT, 'scripts', 'compile_warehouse.js');

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

// ── T0: real compile must succeed ───────────────────────────────────────────
console.log('— T0: compile —');
var r = cp.spawnSync('node', [COMPILER, YAML, OUT, SEED], { encoding: 'utf8' });
process.stdout.write(r.stdout || '');
process.stdout.write(r.stderr || '');
verdict(r.status === 0, 'compile exit 0', 'exit=' + r.status);
if (r.status !== 0) { console.log('§WH-COMPILE FAIL'); process.exit(1); }

var db = new Database(OUT, { readonly: true });
var seed = new Database(SEED, { readonly: true });

// ── T1: bins == m_locator rows mapped, guid == m_locator_id ────────────────
console.log('— T1: bin↔locator bijection —');
var locs = seed.prepare('SELECT l.m_locator_id AS id, l.value AS value FROM m_locator l').all();
var bins = db.prepare("SELECT guid, element_name FROM elements_meta WHERE ifc_class='IfcBuildingElementProxy'").all();
var binGuids = bins.map(function(b) { return b.guid; }).sort();
var locIds = locs.map(function(l) { return String(l.id); }).sort();
var bijection = JSON.stringify(binGuids) === JSON.stringify(locIds);
verdict(bins.length === locs.length, 'bin count == m_locator count', bins.length + '/' + locs.length);
verdict(bijection, 'bin guids == m_locator ids (set equality)', binGuids.join(','));
// every bin element_name carries the REAL locator Value (extract, not invent)
var nameOk = bins.every(function(b) {
  var l = locs.find(function(x) { return String(x.id) === b.guid; });
  return l && b.element_name.indexOf(l.value) === 0;
});
verdict(nameOk, 'bin names carry m_locator.Value');
console.log('§WH bins=' + bins.length + ' == m_locator rows mapped (' + bins.length + '/' + locs.length + ') guid==m_locator_id ' + (bijection ? 'OK' : 'FAIL'));

// ── T2: W-BUFFER space contract ─────────────────────────────────────────────
console.log('— T2: BUFFER invariant + containment —');
// (a) §4.2 sum invariant per non-leaf BOM along its tack axis (X for site+rack, Y for aisle)
var boms = db.prepare('SELECT bom_id, bom_type, aabb_width_mm w, aabb_depth_mm d FROM m_bom').all();
var sumBad = 0;
boms.forEach(function(b) {
  var axis = (b.bom_type === 'AISLE') ? 'allocated_depth_mm' : 'allocated_width_mm';
  var parentDim = (b.bom_type === 'AISLE') ? b.d : b.w;
  var s = db.prepare('SELECT SUM(' + axis + ') s, COUNT(*) n FROM m_bom_line WHERE bom_id=?').get(b.bom_id);
  if (!s.n) return; // leaf-only bom rows have no lines (racks DO have lines)
  if (s.s !== parentDim) { sumBad++; console.log('   sum-invariant BROKEN ' + b.bom_id + ': parent=' + parentDim + ' sum(children)=' + s.s); }
});
verdict(sumBad === 0, '§4.2 parent == SUM(children allocated) for every non-leaf BOM', boms.length + ' BOMs');
// (b) geometric containment from element_transforms: bins fit racks fit aisles fit site
function aabb(guid) {
  var t = db.prepare('SELECT center_x cx, center_y cy, center_z cz, bbox_x bx, bbox_y by, bbox_z bz FROM element_transforms WHERE guid=?').get(guid);
  return t && { x0: t.cx - t.bx / 2, x1: t.cx + t.bx / 2, y0: t.cy - t.by / 2, y1: t.cy + t.by / 2, z0: t.cz - t.bz / 2, z1: t.cz + t.bz / 2 };
}
function insideXY(a, b) { var e = 1e-6; return a.x0 >= b.x0 - e && a.x1 <= b.x1 + e && a.y0 >= b.y0 - e && a.y1 <= b.y1 + e; }
var tree = db.prepare("SELECT l.bom_id parent, l.child_product_id child, l.role role FROM m_bom_line l WHERE l.component_type IS NULL").all();
var containBad = 0, containChecked = 0;
// rack ⊂ aisle, aisle ⊂ site (their own meshes share the bom ids as guids)
tree.forEach(function(t) {
  if (t.role === 'RACK') { containChecked++; if (!insideXY(aabb(t.child), aabb(t.parent))) { containBad++; console.log('   rack ' + t.child + ' outside aisle ' + t.parent); } }
  if (t.role === 'AISLE') { containChecked++; if (!insideXY(aabb(t.child), aabb('GW_SITE_FLOOR'))) { containBad++; console.log('   aisle ' + t.child + ' outside site'); } }
});
// bin ⊂ its rack: bins were TILEd from the rack BOM — recover rack from element_ref
var rackBinLines = db.prepare("SELECT bom_id rack, element_ref bins FROM m_bom_line WHERE child_product_id='WH_BIN'").all();
rackBinLines.forEach(function(l) {
  JSON.parse(l.bins).forEach(function(locId) {
    containChecked++;
    if (!insideXY(aabb(String(locId)), aabb(l.rack))) { containBad++; console.log('   bin ' + locId + ' outside rack ' + l.rack); }
  });
});
verdict(containBad === 0, 'containment bin⊂rack⊂aisle⊂site', containChecked + ' pairs checked');
console.log('§WH_BUFFER sum-invariant=' + (sumBad === 0 ? 'OK' : 'FAIL') + ' containment=' + (containBad === 0 ? 'OK' : 'FAIL') + ' pairs=' + containChecked);

// ── T3: falsifier — ghost locator must FAIL the compile ─────────────────────
console.log('— T3: falsifier —');
var ghostYaml = fs.readFileSync(YAML, 'utf8') + '\n      - id: RACK_GHOST\n        warehouse: HQ\n        bins:\n          - { locator_value: "Ghost Bin", m_locator_id: 99999 }\n';
var tmpY = path.join(os.tmpdir(), 'wh_ghost.yaml');
var tmpDb = path.join(os.tmpdir(), 'wh_ghost.db');
fs.writeFileSync(tmpY, ghostYaml);
var rg = cp.spawnSync('node', [COMPILER, tmpY, tmpDb, SEED], { encoding: 'utf8' });
var refusedRight = rg.status !== 0 && /NOT in m_locator/.test(rg.stderr + rg.stdout);
verdict(refusedRight, 'ghost locator 99999 REFUSED by compiler', 'exit=' + rg.status);
console.log('§WH_FALSIFIER ghost m_locator_id=99999 → compile ' + (refusedRight ? 'FAILED as required (no invented bins)' : 'WRONGLY PASSED'));

// ── T4: render gate (no-cubes) ──────────────────────────────────────────────
console.log('— T4: render gate —');
var miss = db.prepare('SELECT COUNT(*) c FROM element_instances i LEFT JOIN component_geometries g ON g.geometry_hash=i.geometry_hash WHERE g.geometry_hash IS NULL').get().c;
var geos = db.prepare('SELECT geometry_hash h, vertices v, faces f FROM component_geometries').all();
var distinct = new Set(geos.map(function(g) { return g.v.toString('hex'); })).size;
var geomBad = 0;
geos.forEach(function(g) {
  var v = new Float32Array(g.v.buffer, g.v.byteOffset, g.v.byteLength / 4);
  var f = new Uint32Array(g.f.buffer, g.f.byteOffset, g.f.byteLength / 4);
  var nv = v.length / 3;
  var finite = true, inRange = true;
  for (var i = 0; i < v.length; i++) if (!isFinite(v[i])) { finite = false; break; }
  for (var j = 0; j < f.length; j++) if (f[j] >= nv) { inRange = false; break; }
  if (!(nv >= 8 && f.length >= 36 && finite && inRange)) { geomBad++; console.log('   bad geometry ' + g.h); }
});
var gateOk = miss === 0 && distinct === geos.length && distinct >= 2 && geomBad === 0;
verdict(miss === 0, 'no §BBOX_KEEP risk: 0 instances without geometry', 'miss=' + miss);
verdict(distinct === geos.length && distinct >= 2, 'distinct vertex blobs (not one placeholder cube)', distinct + '/' + geos.length);
verdict(geomBad === 0, 'all blobs valid Float32/Uint32, indices in range', geos.length + ' hashes');
console.log('§WH_RENDERGATE hashes=' + geos.length + ' distinct_vertex_blobs=' + distinct + ' blob_miss=' + miss + (gateOk ? ' PASS (no cubes)' : ' FAIL'));

var bytes = fs.statSync(OUT).size;
console.log('§WH_DB ' + OUT + ' bytes=' + bytes);
console.log(fails === 0 ? '§W-WH-COMPILE PASS — all verdicts green' : '§W-WH-COMPILE FAIL fails=' + fails);
process.exit(fails === 0 ? 0 : 1);
