#!/usr/bin/env node
/*
 * witness_modeller_geo_summary.js — W-GEO-SUMMARY. Falsifiable proof of the modeller drop's RUNTIME self-proof:
 * the §GEO_SUMMARY line the JS twin emits (LAST_MILE_PROBLEM.md §7 "Next step for coder" — the JS twin of the
 * Java compiler's `[GEO] SUMMARY 58 elements, 1653 pairs, worst=0.002mm, DRIFT=0`).
 *
 * ⚠ DO NOT REMOVE. SCOPE: Library.geoSummary (the SHIPPED bonsai_library.js, loaded in a vm sandbox like
 * witness_modeller_drop.js) must, over the PROVEN catalog, report DRIFT=0 / worst=0mm for EVERY droppable root
 * (level-agnostic: SET / ROOM / FLOOR / BUILDING) — and it must FIRE (DRIFT>0) on the exact PR#478 scatter class
 * (MIRROR mis-handled → leaf flung to building-absolute coords). A self-proof that can only ever say PASS is not
 * a proof. Read the §-log; exit 1 on any failure. NON-INVENT: claims computed from the catalog + the Java
 * invariant (IntraBOMRelativeTest), no fabricated numbers.
 *
 * THE CLAIMS:
 *   G1  geoSummary loads from the shipped host module and returns a verdict for a known root.
 *   G2  every droppable assembly root reports DRIFT=0, worst=0mm (the catalog is W-MODELLER-DROP clean).
 *   G3  the §GEO_SUMMARY line format matches the Java [GEO] SUMMARY twin (leaves, pairs, worst=mm, DRIFT=n).
 *   G4  FALSIFIABILITY — a deliberately scattered expand (the PR#478 bug: MIRROR not reflected + sub-BOM origin
 *       and ½-extent dropped) trips DRIFT>0 with a multi-metre worst. (Run against an in-test broken oracle so
 *       the GATE logic itself is exercised, not the now-fixed host.)
 */
'use strict';
const fs = require('fs');
const path = require('path');

const CAT = process.argv[2] || path.join(__dirname, '..', 'deploy/dev/dagevu_catalog.json');
const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const fails = [];
const log = (m) => console.log(m);
const fail = (m) => { fails.push(m); };

// ── load the REAL shipped Library (same sandbox approach as witness_modeller_drop.js) ───────────────────────────
function loadHostLibrary() {
  let lib = null;
  for (const cand of [path.join(path.dirname(CAT), 'bonsai_library.js'),
                      '/home/red1/bim-compiler/deploy/dev/bonsai_library.js']) {
    if (fs.existsSync(cand)) { lib = cand; break; }
  }
  if (!lib) return Promise.resolve(null);
  const src = fs.readFileSync(lib, 'utf8');
  const vm = require('vm');
  const win = { Bonsai: {} };
  const fakeFetch = (u) => Promise.resolve({ json: () => Promise.resolve(String(u).includes('dagevu_catalog') ? cat : {}) });
  const sandbox = {
    window: win, console, Math, JSON, Object, Array, Float32Array, Uint32Array, Uint8Array, Number, isFinite,
    Infinity, Promise, atob: (b) => Buffer.from(b, 'base64').toString('binary'), URL,
    fetch: fakeFetch, document: undefined, location: { href: 'file://' + lib }
  };
  vm.createContext(sandbox);
  vm.runInContext(src, sandbox, { filename: lib });
  const L = win.Bonsai.library;
  if (!L) return Promise.resolve(null);
  return L.ready().then(() => L);
}

(async function main() {
  const L = await loadHostLibrary();
  if (!L || typeof L.geoSummary !== 'function') {
    fail('G1 host bonsai_library.js has no geoSummary() — not implemented in the shipped module');
    log('§W-GEO-SUMMARY FAIL: ' + fails.join('; ')); process.exit(1);
  }

  // G1 — a verdict for the proven SH building.
  const sh = L.geoSummary('BUILDING_SH_STD');
  if (!sh) { fail('G1 geoSummary(BUILDING_SH_STD) returned null'); }
  else log('§W-GEO-SUMMARY G1 host verdict BUILDING_SH_STD leaves=' + sh.leaves + ' pairs=' + sh.pairs +
           ' worst=' + sh.worstMm + 'mm DRIFT=' + sh.drift);

  // G2 — every droppable root is DRIFT=0 / worst=0mm.
  let roots = 0, dirty = 0, maxLeaves = 0; const byLevel = {};
  for (const a of cat.assemblies) {
    const r = L.geoSummary(a.id); if (!r) continue;
    roots++; maxLeaves = Math.max(maxLeaves, r.leaves);
    byLevel[r.level] = (byLevel[r.level] || 0) + 1;
    if (r.drift > 0 || r.worstMm > 0) { dirty++; fail('G2 DRIFT root ' + a.id + ' worst=' + r.worstMm + 'mm DRIFT=' + r.drift + ' :: ' + (r.detail || []).slice(0, 2).join(' | ')); }
  }
  log('§W-GEO-SUMMARY G2 roots=' + roots + ' (' + Object.keys(byLevel).sort().map(k => k + ':' + byLevel[k]).join(' ') +
      ') maxLeaves=' + maxLeaves + ' withDRIFT=' + dirty);

  // G3 — line format twin of the Java [GEO] SUMMARY (capture stdout of one geoSummary call).
  const orig = console.log; let captured = '';
  console.log = (m) => { captured += m + '\n'; };
  L.geoSummary('DX::DX_A102_SET');
  console.log = orig;
  const line = captured.split('\n').find(l => l.indexOf('§GEO_SUMMARY') === 0) || '';
  const fmtOK = /^§GEO_SUMMARY \S+ \[\w+\] \d+ leaves, \d+ pairs, worst=\d+mm, DRIFT=\d+$/.test(line);
  log('§W-GEO-SUMMARY G3 line="' + line.trim() + '" fmtOK=' + fmtOK);
  if (!fmtOK) fail('G3 §GEO_SUMMARY line does not match the Java [GEO] SUMMARY twin format');

  // G4 — FALSIFIABILITY. Re-run the GATE (transcribed identically) against a BROKEN expand that reproduces the
  // PR#478 scatter: MIRROR ignored (treated as rotation), sub-BOM origin + LBD ½-extent dropped. The DX building
  // carries MIRROR sub-BOMs → a leaf is flung to building-absolute coords → R3 must fire.
  const ASM = {}; cat.assemblies.forEach(a => ASM[a.id] = a);
  const PROD = {}; cat.products.forEach(p => PROD[p.id] = p);
  function expBroken(id, pl, depth, seen) {
    const a = ASM[id]; if (!a) return []; depth = depth || 0; seen = seen || {};
    if (depth > 12 || seen[id]) return []; seen = Object.assign({}, seen); seen[id] = true;
    const px = pl.x || 0, py = pl.y || 0, pz = pl.z || 0, pr = pl.rot || 0;
    const rad = pr * Math.PI / 180, cs = Math.cos(rad), sn = Math.sin(rad); const out = [];
    (a.children || []).forEach(ch => {
      const dx = cs * ch.dx - sn * ch.dy, dy = sn * ch.dx + cs * ch.dy;     // MIRROR ignored (the bug)
      if (ch.isBom) {
        const wr = ((pr + (ch.rotDeg || 0)) % 360 + 360) % 360;
        expBroken(ch.ref, { x: px + dx, y: py + dy, z: pz + (ch.dz || 0), rot: wr }, depth + 1, seen).forEach(s => out.push(s));
      } else { if (!PROD[ch.ref]) return; out.push({ x: px + dx, y: py + dy, z: pz + (ch.dz || 0) }); }  // no ½-extent
    });
    return out;
  }
  function gateWith(rootId, expFn) {                                        // the SAME invariant geoSummary applies
    const root = ASM[rootId]; if (!root) return null;
    const R1 = 10, R2 = 4.5, EF = 10, ET = 3; let viol = 0, worst = 0; const seen = {};
    const span = (v) => { let mn = Infinity, mx = -Infinity; for (const x of v) { if (x < mn) mn = x; if (x > mx) mx = x; } return mx - mn; };
    const walk = (id) => {
      const a = ASM[id]; if (!a || seen[id]) return; seen[id] = true;
      const scoped = !(a.level === 'FLOOR' || a.level === 'BUILDING');
      (a.children || []).forEach(ch => {
        if (ch.isBom) { walk(ch.ref); return; }
        if (!scoped) return;
        const e1 = Math.max(Math.abs(ch.dx) - R1, Math.abs(ch.dy) - R1); if (e1 > 0) { viol++; worst = Math.max(worst, e1); }
        const e2 = Math.abs(ch.dz || 0) - R2; if (e2 > 0) { viol++; worst = Math.max(worst, e2); }
      });
      if (scoped) {
        const ex = expFn(id, { x: 0, y: 0, z: 0, rot: 0 });
        if (ex.length > 1) {
          const sX = span(ex.map(l => l.x)), sY = span(ex.map(l => l.y));
          const e3 = Math.max(sX - (Math.max(a.w || 0, EF) + ET), sY - (Math.max(a.d || 0, EF) + ET));
          if (e3 > 0) { viol++; worst = Math.max(worst, e3); }
        }
      }
    };
    walk(rootId);
    return { worstMm: Math.round(worst * 1000), drift: viol };
  }
  const broke = gateWith('DX::BUILDING_DX_STD', expBroken);
  log('§W-GEO-SUMMARY G4 broken-expand DX::BUILDING_DX_STD worst=' + broke.worstMm + 'mm DRIFT=' + broke.drift + ' (must be >0)');
  if (!(broke.drift > 0 && broke.worstMm > 1000))
    fail('G4 the gate did NOT fire on the PR#478 scatter (worst=' + broke.worstMm + 'mm DRIFT=' + broke.drift + ') — it cannot prove anything');

  if (fails.length) {
    log('\n§W-GEO-SUMMARY FAIL (' + fails.length + '):'); fails.slice(0, 12).forEach(f => log('  - ' + f));
    process.exit(1);
  }
  log('\n§W-GEO-SUMMARY PASS — the modeller drop emits its own §GEO_SUMMARY runtime verdict (the JS twin of the ' +
      'Java [GEO] SUMMARY); every droppable root is DRIFT=0/worst=0mm on the proven catalog, the line format ' +
      'matches the Java twin, and the gate FIRES (DRIFT>0, worst>1m) on the PR#478 scatter class.');
  process.exit(0);
})();
