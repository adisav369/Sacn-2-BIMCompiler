#!/usr/bin/env node
/*
 * witness_drop_vs_java.js — W-DROP-VS-JAVA. STRICT whitebox: does the modeller DROP reproduce the EXACT spatial
 * relationships the Java compiler attested (LAST_MILE_PROBLEM.md §7 / geo_verify.py — SH 58 elements, 1653 pairs,
 * worst 0.002mm; DX 1099 elements, 0.004mm)? Not "lands on cursor" — all-pairs RELATIVE OFFSETS to the micron.
 *
 * ⚠ DO NOT REMOVE. STATUS (2026-06-22): GREEN. Both gaps the prior session left RED are closed:
 *   GAP-1 PRECISION — CLOSED. The drop quantized positions to 0.1mm via `.toFixed(4)` in expandAssembly/dropLeaves;
 *         raised to `.toFixed(7)` (0.1µm). All-pairs relative offsets are now 0.0000mm vs the Java-faithful oracle
 *         (under the Java's own 0.002mm bar) for SH + DX at yaw 0/90.
 *   GAP-2 ELEMENT COUNT — RESOLVED (the 55 is the honest PLACED set; the bake dropped NOTHING). Reconciled by
 *         class against the REAL extraction (deploy/buildings/SampleHouse_extracted.db): 60 elements − 2
 *         IfcCurtainWall CONTAINERS (0 transforms, folded into their members+plates) − 3 IfcCovering ceilings
 *         (COVERING-GENERATED downstream, never BOM-placed — CLAUDE.md doctrine) = 55 placed == the drop. The
 *         Java geo_verify's 58 = these 55 placed + the 3 generated coverings. NON-INVENT: no element fabricated —
 *         the 3-element gap traces to covering generation, and the 5-element extraction surplus to the source DB.
 *
 * CLAIMS (read the §-log; exit 1 if a regression reopens either gap):
 *   S1  the drop's all-pairs relative offsets == the Java-faithful oracle to <0.01mm (STRICT — guards GAP-1).
 *   S2  the drop's PLACED-leaf set == the real extraction's placed set, reconciled by class against
 *       SampleHouse_extracted.db (60 − 2 containers − 3 generated = 55), and the geometric count == Java's 58.
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

  // S2 — STRICT source-traced reconciliation (was a brittle `==58`): the drop's PLACED-leaf set == the REAL
  // extraction's placed set, proven by class against deploy/buildings/SampleHouse_extracted.db (the geo_verify
  // ground truth, NON-INVENT). The Java geo_verify attested 58 GEO-matched elements; the BOM RECIPE legitimately
  // PLACES 55 of them — the 3-element gap is NOT a dropped leaf, it is covering-GENERATED geometry the recipe
  // never places (CLAUDE.md doctrine: ARC(PLACE)+STR(FRAME) placed; COVERING/ROUTE generated). Verified:
  //   60 extraction elements
  //   − 2 IfcCurtainWall CONTAINERS (0 element_transforms — no own geometry; folded into their IfcMember+IfcPlate)
  //   − 3 IfcCovering ceilings (COVERING-GENERATED downstream, never a BOM-placed leaf)
  //   = 55 placed == the modeller drop.   (Java's 58 = these 55 placed + the 3 generated coverings.)
  const Database = require('better-sqlite3');
  const EXT = path.join(__dirname, '..', 'deploy/buildings/SampleHouse_extracted.db');
  const xdb = new Database(EXT, { readonly: true });
  const xrows = xdb.prepare('SELECT em.ifc_class AS c, COUNT(*) AS n, ' +
    'SUM(CASE WHEN et.guid IS NULL THEN 0 ELSE 1 END) AS xf ' +
    'FROM elements_meta em LEFT JOIN element_transforms et ON et.guid = em.guid GROUP BY em.ifc_class').all();
  xdb.close();
  const bucket = (c) => /Wall/.test(c) ? 'WALL' : /Furni/.test(c) ? 'FURN' : c;   // wall/std-case → WALL; *Furni* → FURN
  let extTotal = 0, containers = 0, generated = 0; const extPlaced = {};
  xrows.forEach(r => {
    extTotal += r.n;
    if (r.xf === 0) { containers += r.n; return; }          // 0-transform = container node, no own geometry
    if (r.c === 'IfcCovering') { generated += r.n; return; } // COVERING-generated downstream, not a BOM-placed leaf
    extPlaced[bucket(r.c)] = (extPlaced[bucket(r.c)] || 0) + r.n;
  });
  const extPlacedN = Object.values(extPlaced).reduce((a, b) => a + b, 0);
  const geometric = extTotal - containers;                  // = the Java geo_verify-attested geometric count

  const shLeaves = L.dropLeaves('BUILDING_SH_STD', 0, 0, 0, 0);
  const shN = shLeaves.length;
  const dropPlaced = {};
  shLeaves.forEach(lf => { const p = PROD[lf.hash]; const c = (p && p.ifc_class) ? bucket(p.ifc_class) : 'UNKNOWN';
    dropPlaced[c] = (dropPlaced[c] || 0) + 1; });

  log('§W-DROP-VS-JAVA S2 extraction=' + extTotal + ' − ' + containers + ' container(IfcCurtainWall) − ' +
      generated + ' generated(IfcCovering) = ' + extPlacedN + ' placed | geometric(Java)=' + geometric +
      ' = ' + extPlacedN + ' placed + ' + generated + ' generated');
  log('§W-DROP-VS-JAVA S2 drop=' + shN + ' leaves; per-class drop=' + JSON.stringify(dropPlaced));
  log('§W-DROP-VS-JAVA S2 per-class extraction-placed=' + JSON.stringify(extPlaced));
  if (geometric !== 58) fail('S2 geometric extraction count=' + geometric + ' != Java-attested 58 (geo_verify)');
  if (shN !== extPlacedN) fail('S2 drop placed=' + shN + ' != extraction placed=' + extPlacedN +
    ' — the bake dropped or added a real placed leaf');
  const allBuckets = new Set([...Object.keys(extPlaced), ...Object.keys(dropPlaced)]);
  allBuckets.forEach(b => { if ((extPlaced[b] || 0) !== (dropPlaced[b] || 0))
    fail('S2 per-class mismatch ' + b + ': drop=' + (dropPlaced[b] || 0) + ' != extraction=' + (extPlaced[b] || 0)); });

  if (fails.length) {
    log('\n§W-DROP-VS-JAVA RED (' + fails.length + ' gap(s) — target for next session):');
    fails.forEach(f => log('  - ' + f));
    process.exit(1);
  }
  log('\n§W-DROP-VS-JAVA GREEN — the modeller drop reproduces the Java-attested spatial relationships exactly ' +
      '(all-pairs 0.0000mm < 0.01mm), and its 55 placed leaves reconcile to the real extraction class-for-class ' +
      '(60 − 2 IfcCurtainWall containers − 3 covering-generated = 55; Java geo_verify 58 = 55 placed + 3 generated).');
  process.exit(0);
})();
