/**
 * W-CROSS-EDGES-ABUTS — the modeller derives the typed `abuts` cross-edge in JS, matching the witnessed
 * Python (extractIFCtoDB.py _face_touch / derive_adjacency, W-SDG-ABUTS 16/16) EDGE-FOR-EDGE.
 *
 * Issue proven: the bom-graph shipped the containment TREE (PR #539); the GRAPH half = the typed lateral
 * edges. This proves the FIRST one (`abuts`) is derived ON-THE-FLY from the pristine bbox substrate
 * (element_transforms) — NOT baked into the resident DB — and is a FAITHFUL port of the Python oracle,
 * not a re-invention. If JS ≠ Python on any real building, the port drifted → FAIL.
 *
 *   C1 SampleHouse — JS abuts set == Python oracle set (every edge: same pair, axis, gap_mm, contact_m2)
 *   C2 Schependomlaan — same, at 3284 elements / 13344 edges (mid building, exercises the sweep prune)
 *   C3 Duplex — same, 1119 elements / 1987 edges
 *   C4 NON-INVENT — every JS edge carries provenance 'derived:face-touch' + a REAL measured contact (>0)
 *   C5 GRACEFUL — a DB with no element_transforms bbox → 0 edges, no throw (residents w/o geometry)
 *
 * Oracle = /tmp/adjacency_oracle.json, produced by /tmp/gen_adjacency_oracle.py importing the EXACT
 * witnessed _face_touch over the same element_transforms bboxes. Non-invent: expectations are MEASURED.
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const SCRATCH = process.argv[2] || '/tmp/claude-1000/-home-red1-bim-compiler/608cc7ab-e605-4a5a-8906-c68b4ab7656a/scratchpad';
const ORACLE = process.argv[3] || '/tmp/adjacency_oracle.json';

global.window = {};
const CE = require(path.join(ROOT, 'deploy/dev/cross_edges.js'));
const initSqlJs = require(path.join(ROOT, 'node_modules/sql.js'));

let PASS = 0, FAIL = 0;
const ok = (c, m) => { (c ? PASS++ : FAIL++); console.log('  ' + (c ? '✅' : '❌') + ' ' + m); return c; };

// compare JS edges to the oracle map { "a|b": [axis, gap_mm, contact_m2] }
function diff(jsEdges, oracle) {
  const js = {};
  jsEdges.forEach(e => { js[e.a + '|' + e.b] = [e.axis, e.gap_mm, e.contact_m2]; });
  const okeys = Object.keys(oracle), jkeys = Object.keys(js);
  const missing = okeys.filter(k => !(k in js));         // oracle edge the JS failed to find
  const extra = jkeys.filter(k => !(k in oracle));       // JS edge the oracle does not have
  let mism = 0, ex = null;
  for (const k of okeys) {
    if (!(k in js)) continue;
    const o = oracle[k], j = js[k];
    // axis must match exactly; numeric fields within float epsilon (rounding parity)
    if (o[0] !== j[0] || Math.abs(o[1] - j[1]) > 0.01 || Math.abs(o[2] - j[2]) > 1e-4) { mism++; if (!ex) ex = { k, o, j }; }
  }
  return { missing, extra, mism, ex, nJs: jkeys.length, nOr: okeys.length };
}

(async () => {
  const SQL = await initSqlJs();
  const oracle = JSON.parse(fs.readFileSync(ORACLE, 'utf8'));
  const CASES = [
    { c: 'C1', label: 'SampleHouse',    db: 'SampleHouse_ext.db' },
    { c: 'C2', label: 'Schependomlaan', db: 'Schependomlaan_ext.db' },
    { c: 'C3', label: 'Duplex',         db: 'Duplex_ext.db' }
  ];
  let allEdges = [];
  for (const t of CASES) {
    const buf = fs.readFileSync(path.join(SCRATCH, t.db));
    const db = new SQL.Database(new Uint8Array(buf));
    const edges = CE.deriveAdjacency(db);
    allEdges = allEdges.concat(edges);
    const d = diff(edges, oracle[t.label]);
    ok(d.missing.length === 0 && d.extra.length === 0 && d.mism === 0,
      t.c + ' ' + t.label + ' — JS abuts ' + d.nJs + ' vs oracle ' + d.nOr +
      ' | missing=' + d.missing.length + ' extra=' + d.extra.length + ' mismatch=' + d.mism +
      (d.ex ? ' e.g. ' + d.ex.k + ' oracle=' + JSON.stringify(d.ex.o) + ' js=' + JSON.stringify(d.ex.j) : ''));
    db.close();
  }

  // C4 — non-invent: every edge is a real measured face contact, stamped provenance
  const bad = allEdges.filter(e => e.provenance !== 'derived:face-touch' || !(e.contact_m2 > 0) || !'XYZ'.includes(e.axis));
  ok(bad.length === 0, 'C4 NON-INVENT — all ' + allEdges.length + ' edges provenance=derived:face-touch + contact>0 + real axis' +
    (bad.length ? ' (' + bad.length + ' bad)' : ''));

  // C5 — graceful on a geometry-less DB (a resident with no element_transforms bbox)
  const empty = new SQL.Database();
  empty.run("CREATE TABLE elements_meta (guid TEXT)");
  let g = true; try { const e = CE.deriveAdjacency(empty); g = Array.isArray(e) && e.length === 0; } catch (_) { g = false; }
  ok(g, 'C5 GRACEFUL — no element_transforms → 0 edges, no throw');
  empty.close();

  console.log('─'.repeat(52));
  console.log('W-CROSS-EDGES-ABUTS: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  try {
    fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true });
    const stamp = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '');
    fs.writeFileSync(path.join(ROOT, 'logs', 'witness_cross_edges_abuts_' + stamp + '.log'), 'W-CROSS-EDGES-ABUTS ' + PASS + '/' + (PASS + FAIL) + '\n');
  } catch (e) {}
  process.exit(FAIL ? 1 : 0);
})();
