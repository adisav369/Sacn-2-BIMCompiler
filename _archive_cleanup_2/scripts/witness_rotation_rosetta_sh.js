#!/usr/bin/env node
// W-ROTATION-ROSETTA-SH — the rotation analogue of the position RosettaStone (rosetta_canvas_sh.js).
//
// THE PROOF (no invented constant): a real building's per-element FACING survives the round-trip.
//   - GROUND TRUTH = the real IFC extraction's per-instance yaw. element_transforms.rotation_z, captured by
//     DAGCompiler/python/extractIFCtoDB.py from the IfcLocalPlacement chain (Euler decomposition). Re-extract:
//       python3 DAGCompiler/python/extractIFCtoDB.py --ifc reference/residential/Ifc4_SampleHouse.ifc -o <db>
//   - REPRODUCTION = drop BUILDING_SH_STD through the SAME engine the modeller canvas uses
//     (deploy/dev/bonsai_library.js expandAssembly — NO _faceRingChairs, NO invented offset).
//   - COMPARE = per matched element (same ifc_class + same table-relative position, frame-invariant),
//     Δ = rot_drop − rot_extract. A faithful reproduction has Δ CONSTANT across all elements (≤ a single
//     global frame offset); a lossy one (e.g. a TILE verb that flattened {0,π,0,π}→0) spreads Δ. Worst metric
//     = circular spread of Δ. PASS = spread ≈ 0.
//
// This is a COMPARISON to the real sample — not a synthetic invariant and not a hardcoded angle. If the BOM
// factorization drops a per-instance yaw, THIS goes RED on its own.
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), cp = require('child_process');
const ROOT = path.join(__dirname, '..');
const EXTRACT_DB = process.argv[2] || path.join(ROOT, 'reference/transient_rosetta/SH_fresh_rot.db');
const CAT = process.argv[3] || path.join(ROOT, 'dagevu_catalog.json');
const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const PROD = {}; cat.products.forEach(p => PROD[p.id] = p);
const DEG = 180 / Math.PI, wrap = a => ((a % 360) + 360) % 360;
function circRange(d) { if (d.length < 2) return 0; const s = d.map(wrap).sort((a, b) => a - b);
  let g = (360 - s[s.length - 1]) + s[0]; for (let i = 1; i < s.length; i++) g = Math.max(g, s[i] - s[i - 1]); return +(360 - g).toFixed(2); }

// ── GROUND TRUTH: furniture rows from the real extraction (centers + captured yaw) ──
function extractionFurniture() {
  const q = "SELECT m.element_name nm, m.ifc_class cls, round(t.center_x,4) x, round(t.center_y,4) y, " +
            "round(t.rotation_z*180/3.14159265358979,2) rotz FROM element_transforms t " +
            "JOIN elements_meta m ON m.guid=t.guid WHERE m.ifc_class LIKE '%Furnitur%';";
  const out = cp.execFileSync('sqlite3', ['-json', EXTRACT_DB, q], { encoding: 'utf8' });
  return JSON.parse(out || '[]');
}

// ── REPRODUCTION: load the modeller engine, drop BUILDING_SH_STD, return furniture leaves ──
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

// align two clouds by their dining table → table-relative offsets (translation, frame-invariant for the match)
function tableRel(items, tx, ty) { return items.map(o => ({ ...o, rx: o.x - tx, ry: o.y - ty })); }
function nearest(p, pool) { let b = null, bd = Infinity; for (const q of pool) { const d = (p.rx - q.rx) ** 2 + (p.ry - q.ry) ** 2; if (d < bd) { bd = d; b = q; } } return { b, d: Math.sqrt(bd) }; }

(async function main() {
  console.log('═══ W-ROTATION-ROSETTA-SH — per-element facing vs the REAL extraction (no invented angle) ═══');
  if (!fs.existsSync(EXTRACT_DB)) { console.log('✗ missing extraction ' + EXTRACT_DB + ' — re-extract first (see header).'); process.exit(2); }
  const L = await loadEngine();
  const drop = L.dropLeaves('BUILDING_SH_STD', 0, 0, 0, 0)
    .map(l => ({ ...l, nm: (PROD[l.hash] || {}).name || '', cls: (PROD[l.hash] || {}).ifc_class || '' }))
    .filter(l => /IfcFurnitur/i.test(l.cls));
  const gt = extractionFurniture();
  console.log(`  reproduction furniture leaves=${drop.length}   ground-truth furniture rows=${gt.length}`);

  // table-relative frames (dining table = the anchor present in both)
  const dtDrop = drop.find(l => /Table_Dining/i.test(l.nm));
  const dtGt = gt.find(l => /Table_Dining/i.test(l.nm) || /Table_Dining/i.test(l.nm));
  if (!dtDrop || !dtGt) { console.log('✗ no dining table anchor in one side'); process.exit(2); }
  const D = tableRel(drop, dtDrop.x, dtDrop.y);
  const G = tableRel(gt, dtGt.x, dtGt.y);

  // match every reproduced furniture leaf to the nearest ground-truth element (table-relative). Report Δ-yaw.
  const deltas = [], rows = [];
  for (const p of D) {
    const { b, d } = nearest(p, G);
    if (!b || d > 0.25) continue;                          // unmatched (>250mm) — skip, not fabricated
    const delta = wrap((p.rot || 0) - (b.rotz || 0));
    deltas.push(delta); rows.push({ nm: p.nm.slice(0, 22), dropRot: +(p.rot || 0).toFixed(1), gtRot: b.rotz, d: +d.toFixed(3), delta: +delta.toFixed(1) });
  }
  rows.sort((a, b) => a.nm.localeCompare(b.nm)).forEach(r =>
    console.log(`     §ROT ${r.nm.padEnd(22)} drop=${String(r.dropRot).padStart(6)}°  extract=${String(r.gtRot).padStart(6)}°  Δ=${String(r.delta).padStart(6)}°  (match ${r.d}m)`));
  const spread = circRange(deltas);
  console.log(`     §ROT-ROSETTA matched=${deltas.length}/${drop.length}  Δ-spread=${spread}°  (faithful ⇔ Δ constant ⇔ spread≈0)`);

  const ok = deltas.length >= 6 && spread < 2.0;            // 6 dining chairs minimum; <2° = same up to a frame offset
  console.log(`\n§W-ROTATION-ROSETTA-SH ${ok ? 'GREEN' : 'RED'} — reproduced facing ${ok ? 'MATCHES' : 'DRIFTS from'} the real extraction (Δ-spread=${spread}°, matched=${deltas.length}).`);
  process.exit(ok ? 0 : 1);
})();
