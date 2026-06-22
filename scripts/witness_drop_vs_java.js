#!/usr/bin/env node
/*
 * witness_drop_vs_java.js — W-DROP-VS-JAVA. STRICT whitebox: does the modeller DROP reproduce the EXACT spatial
 * relationships the Java compiler attested (LAST_MILE_PROBLEM.md §7 / geo_verify.py — SH 58 elements, 1653 pairs,
 * worst 0.002mm; DX 1099 elements, 0.004mm)? Not "lands on cursor" — all-pairs RELATIVE OFFSETS to the micron.
 *
 * ⚠ DO NOT REMOVE. STATUS (2026-06-22): currently RED on purpose — it is the falsifiable target for the next
 * session (the user: "be strict with the whitebox; it must land EXACT spatial relationship/offsets"). The log
 * shows TWO real gaps, neither hidden:
 *   GAP-1 PRECISION: the drop quantizes positions to 0.1mm via `.toFixed(4)` in expandAssembly/dropLeaves; the
 *         Java holds 0.002mm. Relative offsets are structurally exact (rigid-invariant, == the Java-faithful
 *         oracle to the rounding floor) but 50× coarser than the attested bar.
 *   GAP-2 ELEMENT COUNT: BUILDING_SH_STD expands to 55 leaves; the Java geo_verify attested 58 (1653 pairs). DX
 *         matches (1099==1099). The 3-leaf SH gap is a catalog-bake vs Java-element-set reconciliation, to be
 *         resolved against the REAL extraction (deploy/buildings/SampleHouse_extracted.db, by IFC GUID — the
 *         geo_verify join), NOT by inventing 3 elements.
 *
 * CLAIMS (read the §-log; exit 1 while RED):
 *   S1  the drop's all-pairs relative offsets == the Java-faithful oracle to <0.01mm (STRICT — drives GAP-1).
 *   S2  BUILDING_SH_STD element count == 58, the Java-attested SH count (drives GAP-2).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const CAT = process.argv[2] || path.join(__dirname, '..', 'deploy/dev/dagevu_catalog.json');
const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const STRICT = 0.00001;                              // 0.01mm — strict spatial bar (Java held 0.002mm)
const fails = [];
const log = (m) => console.log(m);
const fail = (m) => { fails.push(m); };

const ASM = {}; cat.assemblies.forEach(a => ASM[a.id] = a);
const PROD = {}; cat.products.forEach(p => PROD[p.id] = p);

function loadHost() {
  const lib = path.join(path.dirname(CAT), 'bonsai_library.js');
  const src = fs.readFileSync(fs.existsSync(lib) ? lib : '/home/red1/bim-compiler/deploy/dev/bonsai_library.js', 'utf8');
  const win = { Bonsai: {} };
  const ff = (u) => Promise.resolve({ json: () => Promise.resolve(String(u).includes('dagevu_catalog') ? cat : {}) });
  const sb = { window: win, console, Math, JSON, Object, Array, Float32Array, Uint32Array, Uint8Array, Number,
    isFinite, Infinity, Promise, atob: (b) => Buffer.from(b, 'base64').toString('binary'), URL, fetch: ff,
    document: undefined, location: { href: 'file://x' } };
  vm.createContext(sb); vm.runInContext(src, sb, { filename: 'bl.js' });
  return win.Bonsai.library.ready().then(() => win.Bonsai.library);
}

// Java-faithful canonical leaf CENTRES (transcribed PlacementCollectorVisitor; == witness_modeller_drop oracle)
function oracle(id, pl, depth, seen) {
  const a = ASM[id]; if (!a) return []; depth = depth || 0; seen = seen || {};
  if (depth > 12 || seen[id]) return []; seen = Object.assign({}, seen); seen[id] = true;
  const px = pl.x || 0, py = pl.y || 0, pz = pl.z || 0, pr = pl.rot || 0, pm = pl.mirror || '';
  const rad = pr * Math.PI / 180, cs = Math.cos(rad), sn = Math.sin(rad);
  const ox = a.ox || 0, oy = a.oy || 0, oz = a.oz || 0; const out = [];
  (a.children || []).forEach(ch => {
    let dx = ch.dx, dy = ch.dy;
    if (pm) { dx = -dx; dy = -dy; } else if (pr) { const x2 = cs * dx - sn * dy, y2 = sn * dx + cs * dy; dx = x2; dy = y2; }
    const cm = ch.mirror || pm;
    if (ch.isBom) { const wx = px + dx + ox, wy = py + dy + oy, wz = pz + (ch.dz || 0) + oz;
      const wr = ((pr + (ch.rotDeg || 0)) % 360 + 360) % 360;
      oracle(ch.ref, { x: wx, y: wy, z: wz, rot: wr, mirror: cm }, depth + 1, seen).forEach(s => out.push(s));
    } else { const p = PROD[ch.ref]; if (!p) return; const hw = (p.w || 0) / 2, hd = (p.d || 0) / 2, sg = pm ? -1 : 1;
      const wx = px + dx + ox, wy = py + dy + oy, wz = pz + oz + (ch.dz || 0);
      out.push({ x: ch.lbd ? wx + sg * hw : wx, y: ch.lbd ? wy + sg * hd : wy, z: wz }); }
  });
  return out;
}

(async function main() {
  const L = await loadHost();

  // S1 — STRICT all-pairs relative offset: the DROP path vs the Java-faithful oracle, in the canonical frame.
  const strictOne = (id, yaw) => {
    const drop = L.dropLeaves(id, 5, -3, yaw, 0);
    const orc = oracle(id, { x: 0, y: 0, z: 0, rot: 0 });
    const n = Math.min(drop.length, orc.length);
    const r = -yaw * Math.PI / 180, cs = Math.cos(r), sn = Math.sin(r);
    let worst = 0, pairs = 0;
    for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) {
      const ddx0 = drop[i].x - drop[j].x, ddy0 = drop[i].y - drop[j].y;
      const ddx = cs * ddx0 - sn * ddy0, ddy = sn * ddx0 + cs * ddy0;          // un-rotate drop offset to canonical
      const ddz = drop[i].z - drop[j].z;
      const odx = orc[i].x - orc[j].x, ody = orc[i].y - orc[j].y, odz = orc[i].z - orc[j].z;
      const d = Math.max(Math.abs(ddx - odx), Math.abs(ddy - ody), Math.abs(ddz - odz));
      if (d > worst) worst = d; pairs++;
    }
    return { n, pairs, worst };
  };
  let s1worst = 0;
  for (const id of ['BUILDING_SH_STD', 'BUILDING_DX_STD', 'DX::BUILDING_DX_STD']) for (const yaw of [0, 90]) {
    const r = strictOne(id, yaw);
    log('§W-DROP-VS-JAVA S1 ' + id + '@yaw' + yaw + ': ' + r.n + ' elem, ' + r.pairs + ' pairs, worst=' + (r.worst * 1000).toFixed(4) + 'mm');
    if (r.worst > s1worst) s1worst = r.worst;
  }
  if (s1worst > STRICT) fail('S1 GAP-1 PRECISION: drop all-pairs worst=' + (s1worst * 1000).toFixed(4) + 'mm > strict ' + (STRICT * 1000) + 'mm (Java held 0.002mm) — `.toFixed(4)` quantizes to 0.1mm');

  // S2 — element count vs the Java-attested SH count (58).
  const shN = L.dropLeaves('BUILDING_SH_STD', 0, 0, 0, 0).length;
  log('§W-DROP-VS-JAVA S2 BUILDING_SH_STD leaves=' + shN + ' (Java geo_verify attested 58)');
  if (shN !== 58) fail('S2 GAP-2 ELEMENT COUNT: SH drop=' + shN + ' != Java 58 — reconcile vs SampleHouse_extracted.db by IFC GUID');

  if (fails.length) {
    log('\n§W-DROP-VS-JAVA RED (' + fails.length + ' gap(s) — target for next session):');
    fails.forEach(f => log('  - ' + f));
    process.exit(1);
  }
  log('\n§W-DROP-VS-JAVA GREEN — the modeller drop reproduces the Java-attested spatial relationships exactly ' +
      '(all-pairs <0.01mm, SH element count 58).');
  process.exit(0);
})();
