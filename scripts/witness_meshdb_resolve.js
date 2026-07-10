#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-MESHDB-RESOLVE scope (READ THE LOG after every run)
 * SCOPE: item 5 of prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md — its stated
 * witness bar VERBATIM: "every geometry_hash carried by rule_space_schedule + rule_mesh_binding
 * resolves to >0-vertex mesh in the SHIPPED mesh.db, REFUSE-list devices excepted."
 * ISSUE THIS WITNESS EXPOSES: the embed-8 consolidation re-keyed every geometry hash, so rule-mined
 * hashes resolved 0/26 in the shipped mesh.db — schedule-generated fixtures could never render a real
 * mesh (the LOD400 render seam was dead). M-FAKE is the falsifier: a fabricated hash must NOT resolve,
 * proving the check can fail.
 * Usage: node scripts/witness_meshdb_resolve.js [path/to/mesh.db]   (default: ~/bim-ootb/modeller/mesh.db)
 */
'use strict';
const path = require('path');
const Database = require('better-sqlite3');
const MESH = process.argv[2] || path.join(process.env.HOME, 'bim-ootb', 'modeller', 'mesh.db');
const HERE = path.join(__dirname, '..');

let pass = 0, fail = 0;
const chk = (n, c, x) => { if (c) { pass++; console.log('  ✅ ' + n + (x ? '  ' + x : '')); } else { fail++; console.log('  ❌ ' + n + (x ? '  ' + x : '')); } };

const dx = new Database(path.join(HERE, 'build', 'duplex_rules.db'), { readonly: true });
const te = new Database(path.join(HERE, 'build', 'terminal_rules.db'), { readonly: true });
const mesh = new Database(MESH, { readonly: true });
const q = mesh.prepare('SELECT length(vertices) v, length(faces) f FROM component_geometries WHERE geometry_hash=?');

console.log(`═══ W-MESHDB-RESOLVE (target: ${MESH}) ═══`);

// M1 — every rule_space_schedule hash resolves >0-vertex
const dxH = dx.prepare(`SELECT geometry_hash h, GROUP_CONCAT(DISTINCT device_id) lbl FROM rule_space_schedule
  WHERE geometry_hash IS NOT NULL AND geometry_hash!='' GROUP BY geometry_hash`).all();
const dxBad = dxH.filter(r => { const m = q.get(r.h); return !m || !m.v || !m.f; });
chk('M1 rule_space_schedule hashes resolve', dxH.length > 0 && dxBad.length === 0,
  `${dxH.length - dxBad.length}/${dxH.length} >0-vertex` + (dxBad.length ? ' MISSING: ' + dxBad.map(r => r.lbl).join(',') : ''));

// M2 — every rule_mesh_binding hash resolves >0-vertex
const teH = te.prepare(`SELECT geometry_hash h, disc||'/'||ifc_class lbl FROM rule_mesh_binding`).all();
const teBad = teH.filter(r => { const m = q.get(r.h); return !m || !m.v || !m.f; });
chk('M2 rule_mesh_binding hashes resolve', teH.length > 0 && teBad.length === 0,
  `${teH.length - teBad.length}/${teH.length} >0-vertex` + (teBad.length ? ' MISSING: ' + teBad.map(r => r.lbl).join(',') : ''));

// M3 — REFUSE-list devices (hash NULL by design) are excepted, still hash-less, and NOT in mesh.db under any guise
const refuse = dx.prepare(`SELECT DISTINCT device_id FROM rule_space_schedule
  WHERE geometry_hash IS NULL OR geometry_hash=''`).all().map(r => r.device_id);
chk('M3 REFUSE-list stays hash-less (no fake mesh smuggled in)',
  refuse.length === 3 && ['EMERGENCY_LIGHT', 'DATA_POINT', 'WASHING_TAP'].every(d => refuse.includes(d)),
  '[' + refuse.join(', ') + ']');

// M-FAKE falsifier — a fabricated hash must NOT resolve (proves M1/M2 can fail)
chk('M-FAKE falsifier: fabricated hash does not resolve', q.get('deadbeef00000000') === undefined);

// M4 — projected rows carry a provenance building tag (not masquerading as a real building's extraction)
const tags = mesh.prepare(`SELECT DISTINCT building FROM component_geometries WHERE geometry_hash IN
  (${dxH.concat(teH).map(r => `'${r.h}'`).join(',')})`).all().map(r => r.building).sort();
chk('M4 provenance building tags', tags.join('|') === 'DeviceLibrary_residential|TerminalMerged_rules', tags.join('|'));

console.log(`\nW-MESHDB-RESOLVE: ${pass}/${pass + fail} PASS${fail ? ' — ' + fail + ' FAIL' : ''}`);
process.exit(fail ? 1 : 0);
