#!/usr/bin/env node
/*
 * witness_drop_vs_compiler.js — W-DROP-VS-COMPILER. The REAL-oracle drop fidelity proof.
 *
 * The independent oracle is the COMPILER's output.db (DAGCompiler/lib/output/samplehouse.db) — the proven
 * IFC->BOM->compile->reconstruct result (Rosetta G1-G6, canvas 55/55 @0.000mm). NOT the catalog (catalog-vs-
 * itself is the tautology that hid the ~1.12m gap in witness_drop_vs_java.js's transcribed oracle).
 *
 * For each candidate droppable building it reports, against the compiler:
 *   - overall world EXTENT (W x D x H span) — translation-invariant; catches "small/scaled" sets.
 *   - per-ifc_class element COUNT + median leaf dims — catches placeholder cubes (chair h=0.45 vs real 1.227).
 *   - all-pairs RELATIVE-offset worst (geo_verify-style, frame-invariant to translation), per matched class.
 * Verdict: which candidate reproduces the proven building. NON-INVENT: oracle read from the compiler output.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const Database = require('better-sqlite3');

const ROOT = path.join(__dirname, '..');
const CAT = path.join(ROOT, 'deploy/dev/dagevu_catalog.json');
const OUTPUT_DB = process.argv[3] || path.join(ROOT, 'DAGCompiler/lib/output/samplehouse.db');
const CANDIDATES = (process.argv[2] || 'BUILDING_SH_STD,SH::BUILDING_SH_STD').split(',');

const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const PROD = {}; cat.products.forEach(p => PROD[p.id] = p);
const ASM = {}; (cat.assemblies || []).forEach(a => ASM[a.id] = a);
const POS_TOL = 0.001;                       // 1mm — the compiler's own tack tolerance
let posPass = false;                         // set true once a candidate reproduces the compiler to POS_TOL

function loadHost() {
  const src = fs.readFileSync(path.join(ROOT, 'deploy/dev/bonsai_library.js'), 'utf8');
  const win = { Bonsai: {} };
  const ff = (u) => Promise.resolve({ json: () => Promise.resolve(String(u).includes('dagevu_catalog') ? cat : {}) });
  const sb = { window: win, console, Math, JSON, Object, Array, Float32Array, Uint32Array, Uint8Array, Number,
    isFinite, Infinity, Promise, atob: (b) => Buffer.from(b, 'base64').toString('binary'), URL, fetch: ff,
    document: undefined, location: { href: 'file://x' } };
  vm.createContext(sb); vm.runInContext(src, sb, { filename: 'bl.js' });
  return win.Bonsai.library.ready().then(() => win.Bonsai.library);
}

const bucket = (c) => !c ? 'UNKNOWN' : /Wall/.test(c) ? 'WALL' : /Furni/.test(c) ? 'FURN'
  : /Door/.test(c) ? 'DOOR' : /Window/.test(c) ? 'WINDOW' : c.replace('Ifc', '');
const med = (a) => { if (!a.length) return 0; const s = [...a].sort((x, y) => x - y); return s[s.length >> 1]; };

function summarize(label, leaves, zMode) {
  // leaves: [{x,y,z,w,d,h,cls}]  — x/y are box CENTRES; z depends on zMode.
  //   zMode='center' (compiler output.db: z = (minZ+maxZ)/2) → box spans z ± h/2.
  //   zMode='base'   (drop: place() seats the box BASE at z, bbox[4]=0)   → box spans z .. z+h.
  // Modelling the drop's base-seated z as a centre falsely inflated H by ~h/2 of the tallest element
  // (the 1.26 'over-spread' was this harness artifact, NOT a drop defect — real drop Z span == compiler's).
  const base = zMode === 'base';
  let mnx = 1e9, mny = 1e9, mnz = 1e9, mxx = -1e9, mxy = -1e9, mxz = -1e9;
  const byc = {};
  leaves.forEach(l => {
    mnx = Math.min(mnx, l.x - l.w / 2); mxx = Math.max(mxx, l.x + l.w / 2);
    mny = Math.min(mny, l.y - l.d / 2); mxy = Math.max(mxy, l.y + l.d / 2);
    mnz = Math.min(mnz, base ? l.z : l.z - l.h / 2); mxz = Math.max(mxz, base ? l.z + l.h : l.z + l.h / 2);
    (byc[l.cls] = byc[l.cls] || []).push(l);
  });
  console.log(`\n  ${label}: ${leaves.length} leaves   extent W×D×H = ${(mxx - mnx).toFixed(2)} × ${(mxy - mny).toFixed(2)} × ${(mxz - mnz).toFixed(2)} m`);
  Object.keys(byc).sort().forEach(c => {
    const g = byc[c];
    console.log(`     ${c.padEnd(8)} n=${String(g.length).padStart(2)}  med dims= ${med(g.map(x => x.w)).toFixed(3)}×${med(g.map(x => x.d)).toFixed(3)}×${med(g.map(x => x.h)).toFixed(3)}`);
  });
  return { n: leaves.length, ext: [mxx - mnx, mxy - mny, mxz - mnz], byc };
}

(async function main() {
  const L = await loadHost();

  // ── Oracle: compiler output.db leaf AABB centres + dims + class ──
  const odb = new Database(OUTPUT_DB, { readonly: true });
  const orows = odb.prepare(
    'SELECT em.ifc_class AS c, (r.minX+r.maxX)/2 AS x,(r.minY+r.maxY)/2 AS y,(r.minZ+r.maxZ)/2 AS z, ' +
    '(r.maxX-r.minX) AS w,(r.maxY-r.minY) AS d,(r.maxZ-r.minZ) AS h ' +
    'FROM elements_meta em JOIN elements_rtree r ON em.id=r.id').all();
  odb.close();
  // Compiler places geometry the drop does NOT (generated coverings, MEP terminals, openings) — restrict the
  // class comparison to the placeable BOM classes the drop emits, but show the full compiler extent too.
  const oracle = orows.map(r => ({ x: r.x, y: r.y, z: r.z, w: r.w, d: r.d, h: r.h, cls: bucket(r.c) }));
  console.log('═══ ORACLE = compiler output.db (proven) ═══');
  const O = summarize('COMPILER', oracle, 'center');

  // ── Each candidate drop ──
  for (const id of CANDIDATES) {
    let leaves;
    try { leaves = L.dropLeaves(id, 0, 0, 0, 0); }
    catch (e) { console.log(`\n  ${id}: dropLeaves threw — ${e.message}`); continue; }
    if (!leaves || !leaves.length) { console.log(`\n  ${id}: 0 leaves (not in catalog or empty)`); continue; }
    const mapped = leaves.map(l => {
      const p = PROD[l.hash] || {};
      return { x: l.x, y: l.y, z: l.z, w: p.w || 0, d: p.d || 0, h: p.h || 0, cls: bucket(p.ifc_class) };
    });
    console.log(`\n═══ CANDIDATE ${id} ═══`);
    const C = summarize(id, mapped, 'base');
    // headline extent ratio vs compiler placeable footprint
    const er = O.ext.map((o, i) => (C.ext[i] / o));
    console.log(`     extent ratio drop/compiler = ${er.map(x => x.toFixed(2)).join(' , ')}  (1.00 = same size)`);

    // ── PER-ELEMENT POSITION CONGRUENCE (the real white-box gate). Expand the building CANONICALLY (rot=0, the
    // Java-faithful path) and bring each leaf into the compiler's ORIGIN-NORMALISED frame by subtracting the
    // building's own m_bom.origin (a.ox/oy/oz) that the drop folds but the compiler output does not carry. Then
    // nearest-match each drop leaf to a compiler leaf OF THE SAME CLASS and report the worst pair. This proves the
    // drop reproduces the compiler's exact placements (incl. verb-expanded qty>1 instances) — NOT just the extent.
    // Compiler-only generated classes (FlowTerminal etc.) are absent from a BOM drop by design → not gated.
    const a = ASM[id] || {}, aox = a.ox || 0, aoy = a.oy || 0, aoz = a.oz || 0;
    const canon = L.expandAssembly(id, { x: 0, y: 0, z: 0, rot: 0 });
    const dByc = {};
    canon.forEach(l => { const p = PROD[l.hash] || {}; const c = bucket(p.ifc_class);
      (dByc[c] = dByc[c] || []).push({ x: l.x - aox, y: l.y - aoy, z: (l.z + (p.h || 0) / 2) - aoz }); });   // → compiler centre frame
    let worstAll = 0, gated = 0;
    Object.keys(dByc).sort().forEach(c => {
      const os = (O.byc[c] || []).slice();        // compiler leaves of this class (centres)
      if (!os.length) return;                     // class the compiler doesn't place (e.g. honest box w/o oracle)
      const used = new Array(os.length).fill(false);
      let worst = 0;
      dByc[c].forEach(d => { let best = 1e9, bi = -1;
        os.forEach((o, i) => { if (used[i]) return; const dd = Math.max(Math.abs(d.x - o.x), Math.abs(d.y - o.y), Math.abs(d.z - o.z)); if (dd < best) { best = dd; bi = i; } });
        if (bi >= 0) used[bi] = true; worst = Math.max(worst, best); });
      worstAll = Math.max(worstAll, worst); gated++;
      console.log(`     pos ${c.padEnd(8)} drop=${String(dByc[c].length).padStart(2)} compiler=${String(os.length).padStart(2)}  worst=${(worst * 1000).toFixed(2)}mm`);
    });
    const ok = gated > 0 && worstAll <= POS_TOL;
    console.log(`     §POS-CONGRUENCE ${id}: worst=${(worstAll * 1000).toFixed(2)}mm over ${gated} classes — ${ok ? 'PASS (≤1mm, reproduces compiler)' : 'over 1mm'}`);
    if (ok) posPass = true;
  }
  console.log(`\n§W-DROP-VS-COMPILER ${posPass ? 'GREEN — a candidate reproduces the compiler output.db to ≤1mm (BOM round-trips, no extracted.db)' : 'RED — no candidate matched the compiler within 1mm'}`);
  process.exit(posPass ? 0 : 1);
})();
