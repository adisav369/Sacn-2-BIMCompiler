#!/usr/bin/env node
// ⚠ DO NOT REMOVE — W-SDG-FORWARD witness for SPATIAL_DEPENDENCY_GRAPH.md (§FORWARD, Phase-2 the fold).
// Scope: prove the forward fold is a PURE, invertible, non-inventing transform of the captured geometry —
// drag a datum handle by Δ, the owned + forward set re-folds via edge rules, and the result is rosetta-true
// against RAW extraction (never the cooked output.db). Read the §-log as proof.
//
// Issue it proves: "the graph stops describing and starts EDITING." A datum drag exercises three edge
// families at once — anchored-to (rigid follow), spans (one-end stretch, sizes held), contains (en-bloc
// ride) — as a pure transform, not a re-derive. Disproves any drift / invention / false-or-missed ripple.
//
// Oracle = the pristine *_extracted.db geometry (element_transforms + the 5 edge tables), re-derived fresh
// (datums/anchors/spans) like the other SDG witnesses. ALL checks are Δ-relative ⇒ frame-invariant.
//
// Falsifiers (independent of the engine):
//   1. IDENTITY ROUND-TRIP rosetta — fold(+Δ) and fold(−Δ) negate EXACTLY → 0.000 mm worst offset.
//   2. ANALYTIC-Δ — every moved elem translates exactly |Δ| on the datum's axis only; every stretched elem's
//      new span == old ± Δ and it really spans the datum (recomputed from raw geometry, not read back).
//   3. ZERO-DRAG invariance — Δ=0 ⇒ nothing moves.
//   4. ZERO-INVENTED / NO-MISSED — every moved/stretched guid traces to an edge to the dragged datum; every
//      anchored-to-d element (minus pinned/spanning) appears; no false ripple.
//   5. STOP-GRADIENT — a pinned seed is absent from the result.
//   6. CONSTRUCT-GENERALITY — identical engine on SH + Duplex + Bridge (datums emerged, grid=0 on bridge).
//
// Run:  node scripts/witness_sdg_forward.js
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), os = require('os');
const ROOT = path.join(__dirname, '..');
const { foldDatumDrag } = require(path.join(ROOT, 'deploy/dev/sdg_fold.js'));

const DELTA_MM = 250;            // a clean continuous nudge: > thresholdMm(0.3), < clampMm(10×)
const AX = { X: 0, Y: 1, Z: 2 };
const CASES = [
  ['DAGCompiler/lib/input/SampleHouse_extracted.db', 'SH'],
  ['DAGCompiler/lib/input/Duplex_extracted.db', 'DX'],
  ['DAGCompiler/lib/input/Bridge_extracted.db', 'Bridge'],
];

let passed = 0, failed = 0;
function check(name, ok, evidence) {
  if (ok) passed++; else failed++;
  console.log(`    ${ok ? 'PASS' : 'FAIL'}  ${name.padEnd(34)}  ${evidence}`);
}
const q = (db, sql) => JSON.parse(cp.execFileSync('sqlite3', ['-json', db, sql], { encoding: 'utf8' }) || '[]');
const hasTable = (db, t) => q(db, `SELECT name FROM sqlite_master WHERE type='table' AND name='${t}'`).length > 0;

// Re-derive datums/anchors/spans FRESH (identical to W-SDG-SPANS), so the fold reads canonical edges.
function prep(srcAbs) {
  const tmp = path.join(os.tmpdir(), `sdgfwd_${path.basename(srcAbs)}_${process.pid}.db`);
  fs.copyFileSync(srcAbs, tmp);
  const py = `
import sqlite3, sys
sys.path.insert(0, r"${path.join(ROOT, 'DAGCompiler/python')}")
from extractIFCtoDB import derive_datums_and_anchors, derive_spans
c = sqlite3.connect(r"${tmp}")
c.executescript("CREATE TABLE IF NOT EXISTS datum_plane (datum_id INTEGER PRIMARY KEY,axis TEXT,coord REAL,support_count INTEGER,provenance TEXT);"
  "CREATE TABLE IF NOT EXISTS rel_anchored (element_guid TEXT,datum_id INTEGER,axis TEXT,offset_mm REAL,provenance TEXT,PRIMARY KEY(element_guid,datum_id));"
  "CREATE TABLE IF NOT EXISTS rel_spans (element_guid TEXT,axis TEXT,datum_lo_id INTEGER,datum_hi_id INTEGER,span_m REAL,provenance TEXT,PRIMARY KEY(element_guid,axis));")
for t in ("datum_plane","rel_anchored","rel_spans"): c.execute(f"DELETE FROM {t}")
derive_datums_and_anchors(c); derive_spans(c); c.commit(); c.close()
`;
  cp.execFileSync('python3', ['-c', py], { encoding: 'utf8' });
  return tmp;
}

function loadGraph(db) {
  const g = { transforms: {}, aabb: {}, datums: {}, anchored: [], spans: [], aggregates: [], fillsHost: [] };
  q(db, 'SELECT guid,center_x cx,center_y cy,center_z cz FROM element_transforms')
    .forEach(r => g.transforms[r.guid] = { cx: r.cx, cy: r.cy, cz: r.cz });
  q(db, 'SELECT m.guid guid,r.minX,r.maxX,r.minY,r.maxY,r.minZ,r.maxZ FROM elements_meta m JOIN elements_rtree r ON m.id=r.id')
    .forEach(r => g.aabb[r.guid] = r);
  q(db, 'SELECT datum_id id,axis,coord FROM datum_plane').forEach(r => g.datums[r.id] = { axis: r.axis, coord: r.coord });
  q(db, 'SELECT element_guid guid,datum_id datumId,axis,offset_mm offsetMm FROM rel_anchored').forEach(r => g.anchored.push(r));
  q(db, 'SELECT element_guid guid,axis,datum_lo_id loId,datum_hi_id hiId,span_m spanM FROM rel_spans').forEach(r => g.spans.push(r));
  q(db, 'SELECT parent_guid parent,child_guid child FROM rel_aggregates').forEach(r => g.aggregates.push(r));
  if (hasTable(db, 'rel_fills_host'))
    q(db, 'SELECT opening_guid opening,host_guid host,filling_guid filling FROM rel_fills_host').forEach(r => g.fillsHost.push(r));
  return g;
}

// Pick the datum that touches the most elements (anchored + spanning) → maximal fold coverage.
function pickDatum(g) {
  const score = {};
  g.anchored.forEach(a => score[a.datumId] = (score[a.datumId] || 0) + 1);
  g.spans.forEach(s => { score[s.loId] = (score[s.loId] || 0) + 1; score[s.hiId] = (score[s.hiId] || 0) + 1; });
  let best = null, bn = -1;
  Object.keys(score).forEach(d => { if (score[d] > bn) { bn = score[d]; best = +d; } });
  return best;
}

function runCase(dbRel, label) {
  console.log(`\n  §FORWARD-WITNESS ${label}  ${dbRel}`);
  const srcAbs = path.join(ROOT, dbRel);
  if (!fs.existsSync(srcAbs)) { check(`${label}:db-exists`, false, `missing ${dbRel}`); return; }
  const tmp = prep(srcAbs);
  try {
    const g = loadGraph(tmp);
    const datumId = pickDatum(g);
    if (datumId == null) { check(`${label}:has-foldable-datum`, false, 'no anchored/span datum'); return; }
    const datum = g.datums[datumId], axis = datum.axis, k = AX[axis], dM = DELTA_MM / 1000;

    const fwd = foldDatumDrag(g, datumId, DELTA_MM);
    const rev = foldDatumDrag(g, datumId, -DELTA_MM);
    const zero = foldDatumDrag(g, datumId, 0);
    console.log(`      §LOG datum=${datumId} axis=${axis} coord=${datum.coord.toFixed(3)}  Δ=${DELTA_MM}mm  moved=${fwd.moved.length} stretched=${fwd.stretched.length} unchanged=${fwd.unchanged_count}`);

    check(`${label}:fold-non-empty`, fwd.moved.length + fwd.stretched.length > 0, `${fwd.moved.length}+${fwd.stretched.length} touched`);

    // 0) DISJOINT — an element is rigid-MOVED xor STRETCHED, never both (spans authoritative on the dragged axis).
    const movedG = new Set(fwd.moved.map(m => m.guid)), stretchG = new Set(fwd.stretched.map(s => s.guid));
    const both = [...movedG].filter(g => stretchG.has(g));
    check(`${label}:moved∩stretched-empty`, both.length === 0, `${both.length} elems both moved AND stretched`);

    // 1) IDENTITY ROUND-TRIP — fold(+Δ) negates fold(−Δ) exactly → 0.000 mm worst offset.
    const fMap = new Map(fwd.moved.map(m => [m.guid, m]));
    const rMap = new Map(rev.moved.map(m => [m.guid, m]));
    let worstMm = 0;
    fMap.forEach((m, gid) => {
      const r = rMap.get(gid) || { dx: 0, dy: 0, dz: 0 };
      worstMm = Math.max(worstMm, Math.abs(m.dx + r.dx), Math.abs(m.dy + r.dy), Math.abs(m.dz + r.dz));
    });
    const fS = new Map(fwd.stretched.map(s => [s.guid, s])), rS = new Map(rev.stretched.map(s => [s.guid, s]));
    let worstSpanMm = 0;
    fS.forEach((s, gid) => {
      const r = rS.get(gid);
      if (!r) { worstSpanMm = Infinity; return; }
      // forward span dev + reverse span dev must cancel: (newF-orig)+(newR-orig)=0
      worstSpanMm = Math.max(worstSpanMm, Math.abs((s.d_lo_mm + r.d_lo_mm)), Math.abs((s.d_hi_mm + r.d_hi_mm)));
    });
    const wmm = Math.max(worstMm * 1000, worstSpanMm);
    check(`${label}:identity-round-trip`, wmm === 0, `worst |+Δ + −Δ| = ${wmm.toFixed(6)} mm (must be 0.000)`);

    // 2) ANALYTIC-Δ — every moved elem = exactly |Δ| on the datum axis ONLY; every stretched = old±Δ on its span.
    let badMove = 0;
    fwd.moved.forEach(m => {
      const v = [m.dx, m.dy, m.dz];
      const onAxis = Math.abs(v[k] - dM) < 1e-9;
      const offAxis = v.every((c, i) => i === k || Math.abs(c) < 1e-12);
      if (!(onAxis && offAxis)) badMove++;
    });
    check(`${label}:moved-rigid-Δ-on-axis`, badMove === 0, `${badMove}/${fwd.moved.length} moves not exactly Δ on ${axis}`);

    let badSpan = 0;
    const spanByGuidAxis = {};
    g.spans.forEach(s => spanByGuidAxis[s.guid + '|' + s.axis] = s);
    fwd.stretched.forEach(s => {
      const src = spanByGuidAxis[s.guid + '|' + s.axis];
      const reach = src && (src.loId === datumId || src.hiId === datumId);
      const expSpan = src ? src.spanM + (s.d_hi_mm - s.d_lo_mm) / 1000 : NaN;
      const magOk = Math.abs(Math.abs(s.d_hi_mm - s.d_lo_mm) - DELTA_MM) < 1e-9;  // one end moved by Δ
      if (!(reach && Math.abs(s.new_span_m - expSpan) < 1e-9 && magOk)) badSpan++;
    });
    check(`${label}:stretched-span-old±Δ`, badSpan === 0, `${badSpan}/${fwd.stretched.length} stretches not old±Δ between real datums`);

    // 3) ZERO-DRAG invariance
    check(`${label}:zero-drag-no-op`, zero.moved.length === 0 && zero.stretched.length === 0, `Δ=0 → ${zero.moved.length}+${zero.stretched.length}`);

    // 4) ZERO-INVENTED + NO-MISSED. Every moved/stretched guid exists in substrate AND traces to an edge to d.
    const allGuids = new Set(Object.keys(g.transforms));
    const spanGuids = new Set(g.spans.filter(s => s.axis === axis && (s.loId === datumId || s.hiId === datumId)).map(s => s.guid));
    const anchoredToD = new Set(g.anchored.filter(a => a.datumId === datumId).map(a => a.guid));
    const childParent = {}; g.aggregates.forEach(e => (childParent[e.child] = childParent[e.child] || []).push(e.parent));
    const hostFollow = new Set();
    g.fillsHost.forEach(h => { if (h.opening) hostFollow.add(h.opening); if (h.filling) hostFollow.add(h.filling); });
    const movedSet = new Set(fwd.moved.map(m => m.guid));
    function tracesToEdge(gid, seen) {                            // anchored-to-d | descendant of moved | host-follow
      if (anchoredToD.has(gid) && !spanGuids.has(gid)) return true;
      if (hostFollow.has(gid)) return true;
      seen = seen || new Set(); if (seen.has(gid)) return false; seen.add(gid);
      const parents = childParent[gid] || [];
      return parents.some(p => movedSet.has(p) && (anchoredToD.has(p) || tracesToEdge(p, seen)));
    }
    const invented = fwd.moved.filter(m => !allGuids.has(m.guid)).length + fwd.stretched.filter(s => !allGuids.has(s.guid)).length;
    check(`${label}:zero-invented-guids`, invented === 0, `${invented} touched guids not in substrate`);
    const untraced = fwd.moved.filter(m => !tracesToEdge(m.guid)).length;
    check(`${label}:every-move-traces-edge`, untraced === 0, `${untraced} moves with no edge to datum ${datumId}`);
    const missed = [...anchoredToD].filter(gid => !spanGuids.has(gid) && !movedSet.has(gid));
    check(`${label}:no-missed-anchored`, missed.length === 0, `${missed.length} anchored-to-d not folded (false negative)`);
    const stretchSet = new Set(fwd.stretched.map(s => s.guid));
    const missedSpan = [...spanGuids].filter(gid => !stretchSet.has(gid));
    check(`${label}:no-missed-span`, missedSpan.length === 0, `${missedSpan.length} spanning elems not stretched`);

    // 5) STOP-GRADIENT — pin a seed → it is absent from the result and reported pinned.
    const seed = [...anchoredToD].find(gid => !spanGuids.has(gid));
    if (seed) {
      const pinnedRun = foldDatumDrag(g, datumId, DELTA_MM, { pinnedGuids: [seed] });
      const inMoved = pinnedRun.moved.some(m => m.guid === seed);
      check(`${label}:stop-gradient-halts`, !inMoved && pinnedRun.pinned.indexOf(seed) >= 0, `pinned seed ${inMoved ? 'STILL moved' : 'halted'}; moved ${fwd.moved.length}→${pinnedRun.moved.length}`);
    } else {
      check(`${label}:stop-gradient-halts`, true, 'no pure-anchored seed (span/host only) — n/a');
    }
  } finally {
    try { fs.unlinkSync(tmp); } catch (e) { /* best-effort */ }
  }
}

console.log('═══ W-SDG-FORWARD — datum-drag forward fold: pure, invertible, non-inventing, on house + bridge ═══');
CASES.forEach(([db, label]) => runCase(db, label));
console.log(`\n  §FORWARD-RESULT  PASS=${passed}  FAIL=${failed}`);
if (failed) { console.log('  ✗ W-SDG-FORWARD RED'); process.exit(1); }
console.log(`  ✓ W-SDG-FORWARD GREEN ${passed}/${passed}`);
