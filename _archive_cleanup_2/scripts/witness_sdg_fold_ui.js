#!/usr/bin/env node
// ⚠ DO NOT REMOVE — W-SDG-FOLD-UI witness for SPATIAL_DEPENDENCY_GRAPH.md (§FORWARD, the UI slice).
// Scope: prove the viewer wiring is faithful — the graph loads from the DB unchanged, the FoldResult turns
// into the CORRECT three.js scene deltas (IFC→three map + span scale), and applying then resetting is EXACT.
// The fold MATH is already proven by W-SDG-FORWARD; this guards the bridge from engine → pixels. §-log proof.
//
// Issue it proves: "wiring the rosetta-GREEN engine to the scene introduces zero drift." Disproves a wrong
// coordinate map, a dropped/duplicated mesh, a non-restoring reset, or a graph mis-read from the DB.
//
// Oracle = the same fresh-derived *_extracted.db (element_transforms + 5 edge tables) as W-SDG-FORWARD.
// Falsifiers (independent of the module): graph row-counts == DB row-counts; moved delta == IFC(dx,dy,dz)→
// three(x,z,−y) with scale 1; stretched scale == new_span/old_span on the mapped axis; translate round-trips
// to 0.000 mm; applyFold shifts mock meshes by exactly the scene delta; reset restores to 0.000 mm.
//
// Run:  node scripts/witness_sdg_fold_ui.js
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), os = require('os');
const ROOT = path.join(__dirname, '..');
const { foldDatumDrag } = require(path.join(ROOT, 'deploy/dev/sdg_fold.js'));
const UI = require(path.join(ROOT, 'deploy/dev/sdg_fold_ui.js'));

const DELTA_MM = 250, AX = { X: 0, Y: 1, Z: 2 };
const CASES = [
  ['DAGCompiler/lib/input/SampleHouse_extracted.db', 'SH'],
  ['DAGCompiler/lib/input/Duplex_extracted.db', 'DX'],
  ['DAGCompiler/lib/input/Bridge_extracted.db', 'Bridge'],
];
let passed = 0, failed = 0;
function check(name, ok, ev) { if (ok) passed++; else failed++; console.log(`    ${ok ? 'PASS' : 'FAIL'}  ${name.padEnd(34)}  ${ev}`); }

function prep(srcAbs) {
  const tmp = path.join(os.tmpdir(), `sdgui_${path.basename(srcAbs)}_${process.pid}.db`);
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
// Mimic the browser A.dbQuery contract: row arrays in SELECT order; missing table → [] (no throw).
function makeQuery(db) {
  return function (sql) {
    try {
      const j = JSON.parse(cp.execFileSync('sqlite3', ['-json', db, sql], { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }) || '[]');
      return j.map(o => Object.values(o));
    } catch (e) { return []; }
  };
}
const scalar = (db, sql) => { try { return +cp.execFileSync('sqlite3', [db, sql], { encoding: 'utf8' }).trim(); } catch (e) { return 0; } };

// minimal three-like vector for the mock mesh (only the ops applyFold/reset use)
function V(x, y, z) { return { x: x, y: y, z: z, set(a, b, c) { this.x = a; this.y = b; this.z = c; return this; }, clone() { return V(this.x, this.y, this.z); }, copy(o) { this.x = o.x; this.y = o.y; this.z = o.z; return this; } }; }

function pickDatum(g) {
  const s = {}; g.anchored.forEach(a => s[a.datumId] = (s[a.datumId] || 0) + 1);
  g.spans.forEach(x => { s[x.loId] = (s[x.loId] || 0) + 1; s[x.hiId] = (s[x.hiId] || 0) + 1; });
  let best = null, bn = -1; Object.keys(s).forEach(d => { if (s[d] > bn) { bn = s[d]; best = +d; } }); return best;
}

function runCase(dbRel, label) {
  console.log(`\n  §FOLD-UI-WITNESS ${label}  ${dbRel}`);
  const srcAbs = path.join(ROOT, dbRel);
  if (!fs.existsSync(srcAbs)) { check(`${label}:db-exists`, false, `missing ${dbRel}`); return; }
  const tmp = prep(srcAbs);
  try {
    const query = makeQuery(tmp);
    const g = UI.buildGraph(query);

    // 1) GRAPH FIDELITY — every table loaded, counts == DB.
    const dbN = {
      transforms: scalar(tmp, 'SELECT count(*) FROM element_transforms'),
      datums: scalar(tmp, 'SELECT count(*) FROM datum_plane'),
      anchored: scalar(tmp, 'SELECT count(*) FROM rel_anchored'),
      spans: scalar(tmp, 'SELECT count(*) FROM rel_spans'),
      aggregates: scalar(tmp, 'SELECT count(*) FROM rel_aggregates'),
    };
    check(`${label}:graph-transforms`, Object.keys(g.transforms).length === dbN.transforms, `${Object.keys(g.transforms).length}==${dbN.transforms}`);
    check(`${label}:graph-datums`, Object.keys(g.datums).length === dbN.datums, `${Object.keys(g.datums).length}==${dbN.datums}`);
    check(`${label}:graph-anchored`, g.anchored.length === dbN.anchored, `${g.anchored.length}==${dbN.anchored}`);
    check(`${label}:graph-spans`, g.spans.length === dbN.spans, `${g.spans.length}==${dbN.spans}`);
    check(`${label}:graph-aggregates`, g.aggregates.length === dbN.aggregates, `${g.aggregates.length}==${dbN.aggregates}`);

    const datumId = pickDatum(g);
    const fwd = foldDatumDrag(g, datumId, DELTA_MM);
    const rev = foldDatumDrag(g, datumId, -DELTA_MM);
    const dF = UI.sceneDelta(fwd, g), dR = UI.sceneDelta(rev, g);
    console.log(`      §LOG datum=${datumId} moved=${fwd.moved.length} stretched=${fwd.stretched.length} sceneDeltas=${Object.keys(dF).length}`);

    // 2) MOVED → IFC(dx,dy,dz) → three(x,z,−y), scale 1.
    let badMap = 0;
    fwd.moved.forEach(m => {
      const d = dF[m.guid];
      if (!d || Math.abs(d.tx - m.dx) > 1e-12 || Math.abs(d.ty - m.dz) > 1e-12 || Math.abs(d.tz - (-m.dy)) > 1e-12
        || d.sx !== 1 || d.sy !== 1 || d.sz !== 1) badMap++;
    });
    check(`${label}:moved→three-map`, badMap === 0, `${badMap}/${fwd.moved.length} wrong IFC→three translate/scale`);

    // 3) STRETCHED → scale == new_span/old_span on the mapped three axis; translate == center-shift mapped.
    const AX3 = { X: 'sx', Y: 'sz', Z: 'sy' };           // IFC axis → three scale field (X→x, Y→z, Z→y)
    const srcBy = {}; g.spans.forEach(s => srcBy[s.guid + '|' + s.axis] = s);
    let badStretch = 0;
    fwd.stretched.forEach(s => {
      const d = dF[s.guid], src = srcBy[s.guid + '|' + s.axis];
      const f = src.spanM ? s.new_span_m / src.spanM : 1;
      const shiftM = (s.d_lo_mm + s.d_hi_mm) / 2 / 1000;
      const exp = { tx: 0, ty: 0, tz: 0 }; if (s.axis === 'X') exp.tx = shiftM; else if (s.axis === 'Y') exp.tz = -shiftM; else exp.ty = shiftM;
      const okScale = d && Math.abs(d[AX3[s.axis]] - f) < 1e-12;
      const okShift = d && Math.abs(d.tx - exp.tx) < 1e-9 && Math.abs(d.ty - exp.ty) < 1e-9 && Math.abs(d.tz - exp.tz) < 1e-9;
      if (!(okScale && okShift)) badStretch++;
    });
    check(`${label}:stretched→scale+shift`, badStretch === 0, `${badStretch}/${fwd.stretched.length} wrong scale/center-shift`);

    // 4) TRANSLATE ROUND-TRIP — sceneDelta(+Δ) translate negates sceneDelta(−Δ) → 0.000 mm.
    let worst = 0;
    Object.keys(dF).forEach(guid => {
      const a = dF[guid], b = dR[guid] || { tx: 0, ty: 0, tz: 0 };
      worst = Math.max(worst, Math.abs(a.tx + b.tx), Math.abs(a.ty + b.ty), Math.abs(a.tz + b.tz));
    });
    check(`${label}:scene-translate-round-trip`, worst * 1000 === 0, `worst |+Δ+−Δ| = ${(worst * 1000).toFixed(6)} mm`);

    // 5) APPLY → RESET is EXACT on mock individual meshes (the headless-testable apply path).
    const idx = {}, orig = {};
    Object.keys(dF).forEach(guid => { const p = V(Math.random ? 0 : 0, 0, 0); /* fixed origin 0 */ orig[guid] = { x: 0, y: 0, z: 0 }; idx[guid] = { kind: 'mesh', obj: { position: V(0, 0, 0), scale: V(1, 1, 1) } }; });
    const A = { _sdgIndex: idx };
    UI.applyFold(A, fwd, g);
    let badApply = 0;
    Object.keys(dF).forEach(guid => {
      const o = idx[guid].obj, d = dF[guid];
      if (Math.abs(o.position.x - d.tx) > 1e-12 || Math.abs(o.position.y - d.ty) > 1e-12 || Math.abs(o.position.z - d.tz) > 1e-12
        || Math.abs(o.scale.x - d.sx) > 1e-12 || Math.abs(o.scale.y - d.sy) > 1e-12 || Math.abs(o.scale.z - d.sz) > 1e-12) badApply++;
    });
    check(`${label}:applyFold-moves-meshes`, badApply === 0, `${badApply}/${Object.keys(dF).length} meshes not at scene delta`);
    UI.reset(A);
    let badReset = 0;
    Object.keys(dF).forEach(guid => {
      const o = idx[guid].obj;
      if (Math.abs(o.position.x) > 1e-12 || Math.abs(o.position.y) > 1e-12 || Math.abs(o.position.z) > 1e-12
        || Math.abs(o.scale.x - 1) > 1e-12 || Math.abs(o.scale.y - 1) > 1e-12 || Math.abs(o.scale.z - 1) > 1e-12) badReset++;
    });
    check(`${label}:reset-restores-exact`, badReset === 0, `${badReset}/${Object.keys(dF).length} meshes not restored to 0.000`);
  } finally { try { fs.unlinkSync(tmp); } catch (e) {} }
}

console.log('═══ W-SDG-FOLD-UI — graph→scene wiring: faithful load, correct three.js deltas, exact apply/reset ═══');
CASES.forEach(([db, label]) => runCase(db, label));
console.log(`\n  §FOLD-UI-RESULT  PASS=${passed}  FAIL=${failed}`);
if (failed) { console.log('  ✗ W-SDG-FOLD-UI RED'); process.exit(1); }
console.log(`  ✓ W-SDG-FOLD-UI GREEN ${passed}/${passed}`);
