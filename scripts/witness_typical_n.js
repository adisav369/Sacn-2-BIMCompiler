#!/usr/bin/env node
// W-TYPICAL-N — the instanced-by-n RosettaStone (MAIN MISSION: reconstruct EXACTLY, LOSSLESSLY).
//
// THE PERCEPT: an element is "instanced-by n along Z" iff a copy (same class, same X/Y) recurs at a measured pitch p
// (CONSTRUCTION_GRID_BOM_DUAL_MODEL §SHELL-N-ZSPAN; the last SPATIAL_DEPENDENCY_GRAPH edge). It is the editable BATCH
// "typical floor × n" — edit the one stored rep → all n re-fold. Detected by `factorizeInstancedZ` in
// deploy/dev/bom_extract.js (modeller track), grep-clean of IFC class names (class = opaque equality key only).
//
// THE PROOF (no cheating): decompose the building into reps (stored) ∪ instances (GENERATED = rep+k·p) ∪ residuals
// (stored), then RECONSTRUCT and land EXACTLY on the pristine extracted.db — 0.000 mm, every element, none missing,
// none invented. n is claimed ONLY where a real copy lands within eps of rep+k·p; anything else is a residual
// (carried, never invented). coveredFraction = instances/total = the honest, MEASURED typicality (we report it; the
// pass criterion is exact lossless landing, NOT a high coverage number — measure, don't assume).
//   - ORACLE = raw extraction (extracted.db element_transforms), read INDEPENDENTLY. NEVER the cooked output.db.
//   - SH is the single-storey CONTROL: must yield ZERO instances (honest n=1), all residual.
'use strict';
const cp = require('child_process'), fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..');
const { factorizeInstancedZ } = require(path.join(ROOT, 'deploy/dev/bom_extract.js'));

const SC = process.argv[2] || path.join(ROOT, 'reference/transient_rosetta/SC_fresh.db');
const SH = process.argv[3] || path.join(ROOT, 'deploy/dev/buildings/SampleHouse_extracted.db');
const EPS_MM = 1e-3;                 // "0.000 mm" landing tolerance = 0.001 mm

function loadElems(db) {
  const rows = JSON.parse(cp.execFileSync('sqlite3', ['-json', db,
    'SELECT m.guid guid, m.ifc_class cls, t.center_x x, t.center_y y, t.center_z z ' +
    'FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid'],
    { encoding: 'utf8', maxBuffer: 1 << 28 }) || '[]');
  return rows.map(r => ({ guid: r.guid, cls: r.cls, x: +r.x, y: +r.y, z: +r.z }));
}

let pass = 0, fail = 0;
const ok = (c, m) => { (c ? pass++ : fail++); console.log(`  ${c ? '✓' : '✗ FAIL'} ${m}`); };

function runBuilding(name, db, expectInstances) {
  console.log(`\n─── ${name} (${path.basename(db)}) ───`);
  if (!fs.existsSync(db)) { ok(false, `db exists: ${db}`); return; }
  const elems = loadElems(db);
  const real = {};                                   // ORACLE: real positions, read independently
  elems.forEach(e => { real[e.guid] = e; });
  const F = factorizeInstancedZ(elems, { epsMm: EPS_MM });
  const cov = (100 * F.coveredFraction).toFixed(1);
  const nDist = {}; Object.values(F.n_by_rep).forEach(n => nDist[n] = (nDist[n] || 0) + 1);
  console.log(`     pitch=${F.pitch == null ? 'none' : F.pitch + 'm'} support=${F.pitchSupport}` +
    ` reps=${F.reps.length} instances=${F.instances.length} residual=${F.residualGuids.length}` +
    ` covered=${cov}% n-dist=${JSON.stringify(nDist)}`);

  // A. LOSSLESS PARTITION — reps ∪ instances ∪ residuals == all elements, DISJOINT, no miss / no invented.
  const seen = {}, dup = [];
  const mark = g => { if (seen[g]) dup.push(g); seen[g] = 1; };
  F.reps.forEach(mark); F.instances.forEach(i => mark(i.guid)); F.residualGuids.forEach(mark);
  ok(dup.length === 0, `partition is DISJOINT (no element in two classes) [dups=${dup.length}]`);
  ok(Object.keys(seen).length === elems.length, `partition COVERS every element [${Object.keys(seen).length}/${elems.length}]`);
  const invented = Object.keys(seen).filter(g => !real[g]);
  ok(invented.length === 0, `ZERO invented guids (every partitioned guid exists in extracted.db) [${invented.length}]`);

  // B. EXACT LANDING — every instance regenerates from its rep (+k·p on Z) onto the REAL element, ≤ EPS_MM.
  let worstMm = 0, badLand = 0;
  F.instances.forEach(inst => {
    const rep = real[inst.repGuid], got = real[inst.guid];
    if (!rep || !got) { badLand++; return; }
    const rx = rep.x, ry = rep.y, rz = rep.z + inst.k * F.pitch;     // GENERATED geometry (rep translated on Z)
    const dMm = Math.max(Math.abs(rx - got.x), Math.abs(ry - got.y), Math.abs(rz - got.z)) * 1000;
    if (dMm > worstMm) worstMm = dMm;
    if (dMm > EPS_MM) badLand++;
  });
  ok(badLand === 0, `every instance lands on the REAL element ≤ ${EPS_MM}mm [worst=${worstMm.toExponential(2)}mm, bad=${badLand}]`);
  ok(F.instances.every(i => i.k >= 1), `every instance offset k≥1 (a copy ABOVE its rep, never self)`);

  // C. FULL RECONSTRUCTION — reps(as-is) + regenerated instances + residuals(as-is) == extracted.db, 0.000 mm.
  const recon = {};
  F.reps.forEach(g => { const e = real[g]; recon[g] = { x: e.x, y: e.y, z: e.z }; });        // stored
  F.residualGuids.forEach(g => { const e = real[g]; recon[g] = { x: e.x, y: e.y, z: e.z }; }); // stored
  F.instances.forEach(i => { const r = real[i.repGuid]; recon[i.guid] = { x: r.x, y: r.y, z: r.z + i.k * F.pitch }; }); // generated
  let worstReconMm = 0, missed = 0;
  elems.forEach(e => {
    const r = recon[e.guid];
    if (!r) { missed++; return; }
    const dMm = Math.max(Math.abs(r.x - e.x), Math.abs(r.y - e.y), Math.abs(r.z - e.z)) * 1000;
    if (dMm > worstReconMm) worstReconMm = dMm;
  });
  ok(missed === 0, `reconstruction includes EVERY real element [missed=${missed}]`);
  ok(Object.keys(recon).length === elems.length, `reconstruction count == real count [${Object.keys(recon).length}/${elems.length}]`);
  ok(worstReconMm <= EPS_MM, `RECONSTRUCTION lands on extracted.db ≤ ${EPS_MM}mm (the RosettaStone) [worst=${worstReconMm.toExponential(2)}mm]`);

  // D. n HONEST — every claimed rep has n≥2; coveredFraction matches instance share.
  ok(Object.values(F.n_by_rep).every(n => n >= 2), `every instanced rep has n≥2 (no n=1 batch claimed)`);
  ok(Math.abs(F.coveredFraction - F.instances.length / elems.length) < 1e-12, `coveredFraction == instances/total (honest measure)`);

  // E. EXPECTATION — SC has real repetition (instances>0); SH control has NONE.
  if (expectInstances) ok(F.instances.length > 0, `genuine repetition detected (instances>0) on the apartment block`);
  else { ok(F.instances.length === 0 && F.reps.length === 0, `single-storey CONTROL: ZERO instances/reps (honest n=1, all residual)`);
         ok(F.residualGuids.length === elems.length, `control: every element is a residual [${F.residualGuids.length}/${elems.length}]`); }

  // F. DETERMINISM — re-run identical.
  const F2 = factorizeInstancedZ(elems, { epsMm: EPS_MM });
  ok(JSON.stringify(F2.instances) === JSON.stringify(F.instances) && JSON.stringify(F2.reps) === JSON.stringify(F.reps),
    `deterministic (re-run identical)`);
}

(function main() {
  console.log('═══ W-TYPICAL-N — instanced-by-n RosettaStone (lossless: typical + residuals == extracted.db, 0.000mm) ═══');

  runBuilding('SampleCastle (10-apartment block)', SC, true);
  runBuilding('SampleHouse (single-storey control)', SH, false);

  // G. GREP-CLEAN — the engine's instanced-Z code must contain ZERO IFC class-name literals (measure, not whitelist).
  console.log(`\n─── doctrine ───`);
  const src = fs.readFileSync(path.join(ROOT, 'deploy/dev/bom_extract.js'), 'utf8');
  const seg = src.slice(src.indexOf('§SHELL-N-ZSPAN: instanced-by n as MEASURED'), src.indexOf('// ── Public API'));
  const hits = (seg.match(/Ifc[A-Z][A-Za-z]+|\bSTAIR\b|\bLIFT\b|\bRISER\b|\bWALL\b|\bCOLUMN\b|\bROOF\b/g) || []);
  ok(hits.length === 0, `instanced-Z engine is GREP-CLEAN of IFC class names [hits=${hits.length}${hits.length ? ': ' + [...new Set(hits)].join(',') : ''}]`);

  console.log(`\n═══ W-TYPICAL-N: ${pass}/${pass + fail} ${fail ? '✗ RED' : '🟢 GREEN'} ═══`);
  process.exit(fail ? 1 : 0);
})();
