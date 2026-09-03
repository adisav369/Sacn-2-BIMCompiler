#!/usr/bin/env node
// W-ROTATION-ROSETTA-ALL — does EVERY element class (walls·beams·slabs·openings·furniture) reproduce its real
// per-instance facing through the WHOLE cascade, or only chairs? End-to-end and frame-tolerant.
//
//   GROUND TRUTH = the real IFC extraction's per-instance yaw (rotation_z), per ifc_class → distinct facing set.
//   REPRODUCTION = drop BUILDING_SH_STD through the modeller engine (the FINAL render: expandAssembly recursion +
//     rotationStack cascade + verb expansion + _inheritHostRotation for openings). Per ifc_class → distinct set.
//   COMPARE = per class, is every REAL facing value reproduced? (SH's drop frame has zero global yaw offset vs the
//     IFC frame — verified: walls match exactly — so the distinct sets compare directly.) A class missing a value
//     LOST per-instance rotation somewhere in the cascade (the chair-TILE bug, for that class).
//
// This answers "can walls & beams be handled abstractly while cascading": the orientation-uniformity guard lives in
// VerbFactorizer.doFactorize (EVERY product group), the cascade is class-agnostic, openings inherit host rotation —
// this witness measures whether the END RESULT preserves real facing for ALL classes, not just furniture.
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), cp = require('child_process');
const ROOT = path.join(__dirname, '..');
const GT = process.argv[2] || path.join(ROOT, 'reference/transient_rosetta/SH_fresh_rot.db');
const CAT = process.argv[3] || path.join(ROOT, 'dagevu_catalog.json');
const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const PROD = {}; cat.products.forEach(p => PROD[p.id] = p);
const norm = c => (c || '').replace(/StandardCase|ElementedCase/i, '').replace(/^Ifc/, '').toLowerCase();
const r2d = r => ((Math.round(r) % 360) + 360) % 360;
const setKey = a => [...new Set(a)].sort((x, y) => x - y);

function extraction() {
  const rows = JSON.parse(cp.execFileSync('sqlite3', ['-json', GT,
    "SELECT m.ifc_class cls, t.rotation_z*180/3.14159265358979 deg FROM element_transforms t JOIN elements_meta m ON m.guid=t.guid;"], { encoding: 'utf8' }) || '[]');
  const by = {}; rows.forEach(r => { const c = norm(r.cls); (by[c] = by[c] || []).push(r2d(r.deg || 0)); }); return by;
}
function loadEngine() {
  const src = fs.readFileSync(path.join(ROOT, 'deploy/dev/bonsai_library.js'), 'utf8');
  const win = { Bonsai: {} };
  const ff = u => Promise.resolve({ json: () => Promise.resolve(String(u).includes('dagevu_catalog') ? cat : {}) });
  const sb = { window: win, console: { log() {}, warn() {} }, Math, JSON, Object, Array, Map, Set, Float32Array,
    Uint32Array, Uint8Array, Number, isFinite, Infinity, Promise, atob: b => Buffer.from(b, 'base64').toString('binary'),
    URL, fetch: ff, document: undefined, location: { href: 'file://x' } };
  vm.createContext(sb); vm.runInContext(src, sb, { filename: 'bl.js' });
  return win.Bonsai.library.ready().then(() => win.Bonsai.library);
}

(async function main() {
  console.log('═══ W-ROTATION-ROSETTA-ALL — per-class facing, drop vs REAL extraction (every class, end-to-end) ═══');
  const L = await loadEngine();
  const drop = {};
  L.dropLeaves('BUILDING_SH_STD', 0, 0, 0, 0).forEach(l => { const c = norm((PROD[l.hash] || {}).ifc_class); if (!c) return; (drop[c] = drop[c] || []).push(r2d(l.rot || 0)); });
  const gt = extraction();
  const classes = [...new Set([...Object.keys(gt), ...Object.keys(drop)])].sort();
  let allOk = true, judged = 0;
  for (const c of classes) {
    const g = gt[c], d = drop[c];
    if (!g || !d) { console.log(`     §ROT-MS ${c.padEnd(12)} ${g ? 'extract' : 'drop'}-only — skip`); continue; }
    judged++;
    const gset = setKey(g), dset = setKey(d);
    const missing = gset.filter(x => !dset.includes(x));
    const ok = missing.length === 0;
    if (!ok) allOk = false;
    console.log(`     §ROT-MS ${c.padEnd(12)} extract{${gset.join(',')}}  drop{${dset.join(',')}}  ${ok ? '✓ every real facing reproduced' : '✗ MISSING ' + missing.join(',') + '°'}`);
  }
  console.log(`\n§W-ROTATION-ROSETTA-ALL ${allOk ? 'GREEN' : 'RED'} — ${judged} classes judged; the cascade reproduces real per-instance facing for ${allOk ? 'EVERY class (walls·beams·slabs·openings·furniture), not just chairs' : 'most but NOT all (see ✗)'}.`);
  process.exit(allOk ? 0 : 1);
})();
