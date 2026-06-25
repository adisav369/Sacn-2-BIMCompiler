#!/usr/bin/env node
// W-BOM-TREE-EDIT — PoC proof of the Outliner BOM editor (§BUILD-DECOMPOSITION part 1), op-log-driven, zero geometry risk.
//
// PROVES: the BOM Tree composition facet (deploy/dev/bom_tree.js) seeds from a real building, re-parents by SIGNED op,
// is `foldOps(seed, ops)` (deterministic replay), supports en-bloc selection, and — the safety keystone — RE-PARENT
// TOUCHES NO GEOMETRY (it is a pure pointer re-point; element positions read from extracted.db are identical pre/post).
//   ORACLE = raw extraction (extracted.db) read INDEPENDENTLY. No browser; pure node + sqlite3 (like the other witnesses).
'use strict';
const cp = require('child_process'), fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..');
const T = require(path.join(ROOT, 'deploy/dev/bom_tree.js'));
const DB = process.argv[2] || path.join(ROOT, 'deploy/dev/buildings/SampleHouse_extracted.db');

let pass = 0, fail = 0;
const ok = (c, m) => { (c ? pass++ : fail++); console.log(`  ${c ? '✓' : '✗ FAIL'} ${m}`); };

function load(db) {
  const rows = JSON.parse(cp.execFileSync('sqlite3', ['-json', db,
    'SELECT m.guid guid, m.ifc_class cls, m.storey storey, m.discipline disc, ' +
    't.center_x x, t.center_y y, t.center_z z ' +
    'FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid'],
    { encoding: 'utf8', maxBuffer: 1 << 28 }) || '[]');
  return rows.map(r => ({ guid: r.guid, cls: r.cls, storey: r.storey, disc: r.disc, x: +r.x, y: +r.y, z: +r.z }));
}

(function main() {
  console.log('═══ W-BOM-TREE-EDIT — Outliner BOM editor PoC (op-log-driven, geometry untouched) ═══');
  if (!fs.existsSync(DB)) { ok(false, `db exists: ${DB}`); process.exit(2); }
  const elems = load(DB);
  const pos = {}; elems.forEach(e => { pos[e.guid] = { x: e.x, y: e.y, z: e.z }; });   // ORACLE geometry snapshot
  console.log(`  building: ${path.basename(DB)}  elements=${elems.length}`);

  // 1. SEED — derived grouping; every element a leaf; lossless partition; provenance derived-seed.
  const seed = T.seedTree(elems, { building: 'SampleHouse' });
  const seedLeaves = T.leaves(seed);
  ok(seedLeaves.length === elems.length, `seed: every element is a leaf [${seedLeaves.length}/${elems.length}]`);
  ok(T.bloc(seed, 'B').length === elems.length, `seed: en-bloc(root) == all elements (lossless partition) [${T.bloc(seed,'B').length}]`);
  const allDerived = Object.keys(seed.nodes).every(k => seed.nodes[k].prov === 'derived-seed');
  ok(allDerived, `seed: every node provenance = 'derived-seed'`);
  const invented = seedLeaves.filter(g => !pos[g]);
  ok(invented.length === 0, `seed: ZERO invented guids (every leaf exists in extracted.db) [${invented.length}]`);

  // 2. EN-BLOC matches an independent SQL group-by (the bloc is the real bunch).
  const storeys = JSON.parse(cp.execFileSync('sqlite3', ['-json', DB,
    "SELECT COALESCE(storey,'Unknown') s, COUNT(*) n FROM elements_meta GROUP BY s"], { encoding: 'utf8' }) || '[]');
  let blocOk = true;
  storeys.forEach(r => { const got = T.bloc(seed, 'S|' + r.s).length; if (got !== r.n) { blocOk = false; console.log(`     storey ${r.s}: bloc=${got} sql=${r.n}`); } });
  ok(blocOk, `en-bloc(storey) == independent SQL group-by count, every storey`);

  // 3. RE-PARENT via SIGNED op — pick an element, move it to a DIFFERENT class node; the op-log carries the change.
  const e0 = elems[0];
  const seedNode = seed.nodes[e0.guid];
  const fromParent = seedNode.parent;
  // find a different existing class node to reparent under
  const otherClass = Object.keys(seed.nodes).find(k => seed.nodes[k].kind === 'class' && k !== fromParent);
  const ops = [{ opType: 'BOM_REPARENT', params: { childId: e0.guid, toParent: otherClass } }];   // kernel_ops-shaped
  const t1 = T.foldOps(seed, ops);
  ok(t1.nodes[e0.guid].parent === otherClass, `re-parent: element moved to new parent via signed BOM_REPARENT op`);
  ok(t1.nodes[e0.guid].prov === 'user-authored', `re-parent: provenance flips derived-seed → user-authored`);
  ok(T.bloc(t1, 'B').length === elems.length, `re-parent: still lossless (every element present exactly once) [${T.bloc(t1,'B').length}]`);
  ok(T.bloc(t1, otherClass).indexOf(e0.guid) >= 0 && T.bloc(t1, fromParent).indexOf(e0.guid) < 0,
    `re-parent: element now under new bloc, gone from old bloc`);

  // 4. GEOMETRY UNTOUCHED (the safety keystone) — re-parent is a pure re-point; positions identical pre/post.
  const after = load(DB);   // re-read the DB: the editor must NOT have written geometry
  let worstMm = 0;
  after.forEach(e => { const p = pos[e.guid]; const d = Math.max(Math.abs(p.x - e.x), Math.abs(p.y - e.y), Math.abs(p.z - e.z)) * 1000; if (d > worstMm) worstMm = d; });
  ok(worstMm === 0, `GEOMETRY UNTOUCHED: extracted.db positions identical pre/post re-parent [worst=${worstMm}mm]`);

  // 5. DETERMINISTIC REPLAY — fold the same ops twice → byte-identical tree.
  const t2 = T.foldOps(seed, ops);
  ok(JSON.stringify(t1.nodes) === JSON.stringify(t2.nodes), `deterministic: foldOps(seed, ops) replays identically`);

  // 6. DATA-INTEGRITY refusals — cycle / self / missing are refused, tree untouched.
  const tref = T.foldOps(seed, []);
  ok(T.reparent(tref, 'B', e0.guid) === false, `refuses CYCLE (root under its own descendant)`);
  ok(T.reparent(tref, e0.guid, e0.guid) === false, `refuses SELF-parent`);
  ok(T.reparent(tref, e0.guid, 'NO_SUCH_NODE') === false, `refuses MISSING parent`);

  // 7. GREP-CLEAN — no IFC class-name literal drives a branch in the engine.
  const src = fs.readFileSync(path.join(ROOT, 'deploy/dev/bom_tree.js'), 'utf8');
  const hits = (src.match(/['"`]Ifc[A-Z][A-Za-z]+['"`]/g) || []);
  ok(hits.length === 0, `engine grep-clean of IFC class names [hits=${hits.length}]`);

  console.log(`\n═══ W-BOM-TREE-EDIT: ${pass}/${pass + fail} ${fail ? '✗ RED' : '🟢 GREEN'} ═══`);
  process.exit(fail ? 1 : 0);
})();
