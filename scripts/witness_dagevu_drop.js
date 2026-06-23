#!/usr/bin/env node
/*
 * witness_dagevu_drop.js — W-DAGEVU-DROP. Drop on the DAGeVu canvas + record the ORIENTATION BASELINE.
 *
 * "Drop on the canvas" = expand a catalog id through the SAME engine the DAGeVu viewer canvas uses
 * (deploy/dev/bonsai_library.js dropLeaves). No Playwright needed — the §-log IS the proof (§-log-first doctrine).
 *
 * WHY ORIENTATION (user 2026-06-23): position alone is not enough — a chair/bed/wall at the right spot but wrong
 * facing is still wrong. We log rotation (yaw, degrees about Z) per element as a BASELINE so any future facing
 * regression (e.g. everything collapsing to rot=0, the old "all chairs face one way" bug) is caught — GIGO guard.
 *
 * NON-INVENT / anti-GIGO: we log ONLY what the drop actually emits. Families the building does NOT contain are
 * flagged §ABSENT (never fabricated) — e.g. SH has no cabinet/sink/fridge; NO building has a fridge. The per-anchor
 * rotation multiset is written to logs/PROOF_dagevu_drop.json as the durable baseline to diff against.
 *
 * PASS = every structural + orientation check holds (each fails on a real past regression, not a tautology).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');

const ROOT = path.join(__dirname, '..');
const CAT = path.join(ROOT, 'deploy/dev/dagevu_catalog.json');
const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const PROD = {}; cat.products.forEach(p => PROD[p.id] = p);
const sha12 = (f) => { try { return crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex').slice(0, 12); } catch { return 'MISSING'; } };
const bucket = (c) => !c ? '?' : /Furni/.test(c) ? 'FURN' : /Wall/.test(c) ? 'WALL' : /Door/.test(c) ? 'DOOR' : /Window/.test(c) ? 'WINDOW' : c.replace('Ifc', '');

// Anchor families whose ORIENTATION is the GIGO baseline. Display/iteration order:
const ANCHOR_KEYS = ['ROOF', 'WALL', 'DOOR', 'WINDOW', 'BED', 'FRIDGE', 'SINK', 'COUNTER', 'CABINET', 'DESK', 'COUCH', 'PIANO', 'TABLE', 'CHAIR', 'FFE-OTHER'];
// Classify ONE leaf. Structural = by IFC CLASS (so a "...Single Door" CABINET is not mistaken for a door, and a
// "w-Chairs" TABLE is not a chair). FFE = by authoritative product NAME, specific-before-generic. Anything
// furniture-class that matches nothing → FFE-OTHER (logged, never silently dropped → truly revealing).
function anchorOf(l) {
  const p = PROD[l.hash] || {}; const cls = p.ifc_class || ''; const name = p.name || l.hash || '';
  if (/roof/i.test(name) || /IfcRoof/.test(cls)) return 'ROOF';        // DX roof is an IfcSlab named "Basic Roof"
  if (/IfcWall/.test(cls)) return 'WALL';
  if (/IfcDoor/.test(cls)) return 'DOOR';
  if (/IfcWindow/.test(cls)) return 'WINDOW';
  const ffe = /IfcFurnitur|IfcFurnishing/i.test(cls) || /cabinet|counter|sink|chair|table|bed|desk|couch|sofa|piano|fridge|refriger|vanity|wardrobe/i.test(name);
  if (!ffe) return null;                                               // structural (slab/beam/covering/…) — not an FFE anchor
  if (/fridge|refriger/i.test(name)) return 'FRIDGE';
  if (/bed/i.test(name)) return 'BED';
  if (/sink|basin/i.test(name)) return 'SINK';                          // incl. "Counter Top w Sink Hole", "Vanity … Sink Unit"
  if (/counter/i.test(name)) return 'COUNTER';
  if (/cabinet|wardrobe/i.test(name)) return 'CABINET';
  if (/desk/i.test(name)) return 'DESK';
  if (/couch|sofa/i.test(name)) return 'COUCH';
  if (/piano/i.test(name)) return 'PIANO';
  if (/table/i.test(name)) return 'TABLE';                              // "Table_Dining_w-Chairs" → TABLE (table before chair)
  if (/chair/i.test(name)) return 'CHAIR';
  return 'FFE-OTHER';
}

const checks = [];
const check = (name, cond, detail) => { checks.push({ name, pass: !!cond }); console.log(`     ${cond ? '✓' : '✗ FAIL'} ${name} — ${detail}`); };
const finitePos = (v) => Number.isFinite(v) && v > 0;
const uniq = (a) => [...new Set(a)].sort((x, y) => x - y);

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

// Emit the orientation baseline for a building; return { present:{KEY:{n,rots,samples}}, absent:[KEY...] }.
function orientationBaseline(L, id, wantAbsentNote) {
  console.log(`\n── §ANCHOR-BASELINE ${id} (rotation = yaw° about Z) ──`);
  const leaves = L.dropLeaves(id, 0, 0, 0, 0);
  const byA = {};
  leaves.forEach(l => { const k = anchorOf(l); if (!k) return; (byA[k] = byA[k] || []).push(l); });
  const present = {};
  ANCHOR_KEYS.forEach(key => {
    const g = byA[key]; if (!g) return;
    const rots = uniq(g.map(l => l.rot || 0));
    present[key] = { n: g.length, rots, samples: g.slice(0, 3).map(l => ({ hash: l.hash, name: (PROD[l.hash] || {}).name, rot: l.rot || 0 })) };
    console.log(`     ${key.padEnd(9)} n=${String(g.length).padStart(3)}  rot={${rots.join(',')}}`);
    g.slice(0, 4).forEach(l => console.log(`        §ORIENT ${l.hash}  rot=${l.rot || 0}°  @(${l.x.toFixed(3)},${l.y.toFixed(3)},${l.z.toFixed(3)})  "${(PROD[l.hash] || {}).name || ''}"`));
  });
  const absent = ANCHOR_KEYS.filter(k => !present[k]);
  (wantAbsentNote || []).forEach(k => { if (absent.includes(k)) console.log(`     §ABSENT ${k} — NOT in ${id} (${wantAbsentNote.note || ''}); not fabricated.`); });
  return { present, absent };
}

// Independent host-rotation proof: INDEPENDENTLY re-derive (geometrically) which wall each opening is embedded in,
// then assert the dropped opening's rotation EQUALS that host wall's rotation (EN BLOC inheritance). Independent =
// it does NOT read the leaf's hostRef the library wrote, so this is a real oracle, not a tautology. Fails on the
// original bug (openings at rot=0 while host walls rotate → mismatched>0). Unresolved openings (no embedding wall)
// keep rot=0 honestly. NON-INVENT: host wall + its rotation are read from the drop, never fabricated.
function hostRotProof(L, id) {
  const lv = L.dropLeaves(id, 0, 0, 0, 0);
  const cls = h => (PROD[h] || {}).ifc_class || '';
  const norm = r => (((r || 0) % 360) + 360) % 360;
  const walls = lv.filter(l => /IfcWall/.test(cls(l.hash)));
  const opens = lv.filter(l => /IfcDoor|IfcWindow|IfcOpening/.test(cls(l.hash)));
  const runAx = rot => { const d = Math.abs((rot || 0) % 180); return Math.abs(d - 90) < 45 ? 'NS' : 'EW'; };
  let resolved = 0, unresolved = 0, mismatched = 0; const dwRots = new Set();
  opens.forEach(op => {
    if (/IfcDoor|IfcWindow/.test(cls(op.hash))) dwRots.add(norm(op.rot));
    let best = null, bd = Infinity;
    for (const w of walls) {
      const wp = PROD[w.hash] || {}; const ax = runAx(w.rot);
      const wlen = ax === 'NS' ? (wp.d || 0) : (wp.w || 0), wth = ax === 'NS' ? (wp.w || 0) : (wp.d || 0);
      const perp = ax === 'NS' ? Math.abs(op.x - w.x) : Math.abs(op.y - w.y);
      const along = ax === 'NS' ? Math.abs(op.y - w.y) : Math.abs(op.x - w.x);
      if (perp < wth / 2 + 0.15 && along < wlen / 2 + 0.25 && perp < bd) { bd = perp; best = w; }
    }
    if (best) { resolved++; if (norm(op.rot) !== norm(best.rot)) mismatched++; } else unresolved++;
  });
  console.log(`     §HOST-PROOF ${id}: openings=${opens.length} resolved=${resolved} unresolved=${unresolved} mismatched=${mismatched} door/window-rots={${[...dwRots].sort((a, b) => a - b).join(',')}}`);
  return { openings: opens.length, resolved, unresolved, mismatched, dwRots: [...dwRots].sort((a, b) => a - b) };
}

(async function main() {
  const L = await loadHost();
  console.log('═══ W-DAGEVU-DROP — drop on the DAGeVu canvas + ORIENTATION BASELINE (§-log truthful) ═══');

  // ── 1) DINING SET (with orientation) ──
  const DSET = 'SH_DINING_SET';
  console.log(`\n── drop set: ${DSET} (table + chairs, with rotation) ──`);
  const set = L.dropLeaves(DSET, 0, 0, 0, 0);
  set.forEach(l => { const p = PROD[l.hash] || {}; console.log(`     §LEAF ${l.hash}  @(${l.x.toFixed(3)},${l.y.toFixed(3)},${l.z.toFixed(3)})  rot=${l.rot || 0}°  dims=${(p.w || 0).toFixed(3)}x${(p.d || 0).toFixed(3)}x${(p.h || 0).toFixed(3)}`); });
  const tables = set.filter(l => /TABLE/i.test(l.hash)), chairs = set.filter(l => /CHAIR/i.test(l.hash));
  const chairH = chairs.map(l => (PROD[l.hash] || {}).h || 0);
  const distinct = new Set(chairs.map(l => `${l.x.toFixed(3)},${l.y.toFixed(3)}`)).size;
  const setRots = uniq(set.map(l => l.rot || 0));
  console.log(`     §SET-ORIENT ${DSET}: rotations present = {${setRots.join(',')}}° (standalone set is the canonical template; the BUILDING applies real per-chair facing — see SH baseline below)`);
  check('set has 1 table + 6 chairs', tables.length === 1 && chairs.length === 6, `tables=${tables.length} chairs=${chairs.length}`);
  check('chairs at distinct positions', distinct === chairs.length, `${distinct}/${chairs.length} distinct (x,y)`);
  check('chairs are REAL height (≥1.0m, not 0.45m stub cube)', chairH.length && Math.min(...chairH) >= 1.0, `chair h=${uniq(chairH).map(h => h.toFixed(3)).join('/')}m`);
  check('every set leaf finite-positive dims', set.every(l => { const p = PROD[l.hash] || {}; return finitePos(p.w) && finitePos(p.d) && finitePos(p.h); }), `${set.length} leaves`);
  check('every set leaf has a finite rotation', set.every(l => Number.isFinite(l.rot || 0)), `rotations all numeric`);

  // ── 2) SH BUILDING (structure + orientation baseline) ──
  const BLD = 'BUILDING_SH_STD';
  const sh = L.dropLeaves(BLD, 0, 0, 0, 0);
  const byc = {}; let mnx = 1e9, mny = 1e9, mnz = 1e9, mxx = -1e9, mxy = -1e9, mxz = -1e9;
  sh.forEach(l => { const p = PROD[l.hash] || {}; byc[bucket(p.ifc_class)] = (byc[bucket(p.ifc_class)] || 0) + 1;
    mnx = Math.min(mnx, l.x - (p.w || 0) / 2); mxx = Math.max(mxx, l.x + (p.w || 0) / 2);
    mny = Math.min(mny, l.y - (p.d || 0) / 2); mxy = Math.max(mxy, l.y + (p.d || 0) / 2);
    mnz = Math.min(mnz, l.z); mxz = Math.max(mxz, l.z + (p.h || 0)); });
  const ext = [mxx - mnx, mxy - mny, mxz - mnz];
  console.log(`\n── drop building: ${BLD} ──\n     §DROP ${sh.length} leaves  extent ${ext.map(e => e.toFixed(2)).join(' x ')}m  classes=${JSON.stringify(byc)}`);
  const shB = orientationBaseline(L, BLD, Object.assign(['CABINET', 'SINK', 'FRIDGE'], { note: 'cabinet/sink → DX kitchens; fridge → no building has one' }));
  const shWallRots = (shB.present.WALL || {}).rots || [];
  check('SH drops 65 placeable leaves', sh.length === 65, `leaves=${sh.length}`);
  check('SH real extent (≈16.87 x 8.67 x 3.95m)', Math.abs(ext[0] - 16.87) < 0.1 && Math.abs(ext[1] - 8.67) < 0.1 && Math.abs(ext[2] - 3.95) < 0.1, `extent=${ext.map(e => e.toFixed(2)).join('x')}m`);
  check('SH anchors present: ROOF+WALL+DOOR+BED', ['ROOF', 'WALL', 'DOOR', 'BED'].every(k => shB.present[k]), `present=${['ROOF', 'WALL', 'DOOR', 'BED'].filter(k => shB.present[k]).join(',')}`);
  check('SH NOT all collapsed to rot=0 (facing preserved)', shWallRots.length >= 2 || (shB.present.BED || {}).rots?.some(r => r !== 0), `wall rots={${shWallRots.join(',')}} bed rots={${(shB.present.BED || {}).rots || ''}}`);
  check('SH cabinet/sink/fridge correctly ABSENT (not fabricated)', ['CABINET', 'SINK', 'FRIDGE'].every(k => shB.absent.includes(k)), `absent=${['CABINET', 'SINK', 'FRIDGE'].filter(k => shB.absent.includes(k)).join(',')}`);
  // ── HOST-ROTATION INHERITANCE (the fix: openings rotate EN BLOC with their host wall, not flat at rot=0) ──
  const shHost = hostRotProof(L, BLD);
  check('SH every opening embedded in a wall (host resolved)', shHost.resolved === shHost.openings && shHost.unresolved === 0, `resolved=${shHost.resolved}/${shHost.openings} unresolved=${shHost.unresolved}`);
  check('SH openings INHERIT host-wall rotation EN BLOC (rot==host, no mismatch)', shHost.mismatched === 0, `mismatched=${shHost.mismatched} (each opening rot must equal its host wall rot)`);
  check('SH doors/windows NOT collapsed to rot=0 (the old facing bug)', shHost.dwRots.some(r => r !== 0), `door/window rots={${shHost.dwRots.join(',')}}`);

  // ── 3) DX BUILDING (where cabinets + sink live — their orientation baseline) ──
  const DXB = orientationBaseline(L, 'BUILDING_DX_STD', Object.assign(['FRIDGE'], { note: 'no fridge in the DX catalog either' }));
  check('DX anchors present: CABINET + SINK', !!DXB.present.CABINET && !!DXB.present.SINK, `cabinet n=${(DXB.present.CABINET || {}).n || 0} sink n=${(DXB.present.SINK || {}).n || 0}`);
  check('DX vanity sink is oriented (rot≠0 present)', ((DXB.present.SINK || {}).rots || []).some(r => r !== 0), `sink rots={${(DXB.present.SINK || {}).rots || ''}}`);
  check('FRIDGE absent in BOTH buildings (honest GIGO flag)', shB.absent.includes('FRIDGE') && DXB.absent.includes('FRIDGE'), `no fridge in SH or DX catalog`);
  // ── DX host-rotation inheritance (more walls, mirror units; some interior openings have no embedding wall → honest 0) ──
  const dxHost = hostRotProof(L, 'BUILDING_DX_STD');
  check('DX resolved openings INHERIT host-wall rotation EN BLOC (no mismatch)', dxHost.mismatched === 0, `mismatched=${dxHost.mismatched} of resolved=${dxHost.resolved}`);
  check('DX majority of openings host-resolved (rest honest rot=0)', dxHost.resolved >= dxHost.openings * 0.7, `resolved=${dxHost.resolved}/${dxHost.openings} unresolved=${dxHost.unresolved}`);
  check('DX doors/windows span multiple cardinal facings (inherited, not flat)', dxHost.dwRots.filter(r => r !== 0).length >= 2, `door/window rots={${dxHost.dwRots.join(',')}}`);

  // ── verdict + baseline sidecar ──
  const passed = checks.filter(c => c.pass).length, total = checks.length, ok = passed === total;
  console.log(`\n§W-DAGEVU-DROP ${ok ? 'GREEN' : 'RED'} — ${passed}/${total} checks (drop + ORIENTATION baseline, SH + DX + dining set).`);
  const baselineOf = (b) => Object.fromEntries(Object.entries(b.present).map(([k, v]) => [k, { n: v.n, rots: v.rots }]));
  const PROOF = { witness: 'W-DAGEVU-DROP', stamp: new Date().toISOString(), verdict: ok ? 'GREEN' : 'RED',
    checks_passed: passed, checks_total: total,
    note: 'Orientation = yaw° about Z. Baseline to diff against (avoid GIGO). Absences are TRUTH, not gaps.',
    dining_set: { id: DSET, leaves: set.length, chairs: chairs.length, chair_h_m: +Math.min(...chairH).toFixed(3), rots: setRots },
    sh: { id: BLD, leaves: sh.length, extent_m: ext.map(e => +e.toFixed(2)), anchors: baselineOf(shB), absent: shB.absent, host_rot: shHost },
    dx: { id: 'BUILDING_DX_STD', anchors: baselineOf(DXB), absent: DXB.absent, host_rot: dxHost },
    inputs: { catalog: { path: path.relative(ROOT, CAT), sha: sha12(CAT) } },
    checks: checks.map(c => ({ name: c.name, pass: c.pass })) };
  fs.writeFileSync(path.join(ROOT, 'logs', 'PROOF_dagevu_drop.json'), JSON.stringify(PROOF, null, 2) + '\n');
  console.log(`§PROOF-SIDECAR logs/PROOF_dagevu_drop.json — catalog=${PROOF.inputs.catalog.sha} verdict=${PROOF.verdict} (per-anchor rotation baseline recorded)`);
  process.exit(ok ? 0 : 1);
})();
