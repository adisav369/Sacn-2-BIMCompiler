'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: W-ROOM-TYPE — witness for build/room_type_classifier.js against
// config/room_templates.yaml, run over all 8 shipped buildings' real compiled
// spatial_structure rows (post habitability-filter — same filter as bim-ootb
// common/room_habitability.js, ported inline below since this is a bim-compiler-only
// witness with no cross-repo require at run time; see NONHAB_TYPES/spaceHabitable below
// for the citation). Report the confidence distribution HONESTLY — Duplex/SampleHouse are
// the templates' own training source (expect a strong self-fit, NOT independent validation);
// the other 6 buildings are genuine held-out inference over synthetic COMPILED rooms with NO
// ground truth to check against — report what confidence the classifier assigns, nothing more.
// Read the log after every run — exit code alone is not evidence.
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const Classifier = require('./room_type_classifier.js');

const CONFIG_PATH = path.join(__dirname, '..', 'config', 'room_templates.yaml');
const LIVEWIRE = '/tmp/wt-fable-livewire/modeller'; // only location with all 8 *_ARC.db populated
                                                      // (see research: main/deploy DBs only have
                                                      // Duplex+Terminal's spatial_structure table)
const BUILDINGS = ['Duplex', 'SampleHouse', 'SampleCastle', 'HHS', 'Clinic', 'Garage', 'Hospital', 'Terminal'];
const TRAINING_BUILDINGS = new Set(['Duplex', 'SampleHouse']); // the templates' own source data

// --- habitability filter, ported from bim-ootb common/room_habitability.js (merged main @
// f60bfb7, PR #732) — same NONHAB_TYPES list + z-band check, not re-derived. Cross-repo require
// isn't available at bim-compiler build/ script run time, so this is a literal port (documented,
// not invented) — same pattern already established for build/room_walker.js being a JS port of
// scripts/compile_rooms.py.
const NONHAB_TYPES = ['ROOF', 'SHAFT', 'VOID', 'PLANT', 'PLANT_ROOM', 'EXTERNAL', 'PODIUM',
  'SILL', 'PARAPET', 'BALCONY'];
function spaceHabitable(space, env) {
  const norm = String(space.label || '').toUpperCase().replace(/\s+/g, '_')
    .replace(/[_\s]*\d+$/, '').trim();
  const toks = norm.split('_');
  for (const t of NONHAB_TYPES) if (norm === t || toks.indexOf(t) >= 0) return { ok: false, why: 'label:' + t };
  if (env && env.z1 != null && space.z1 > env.z1 + 0.25)
    return { ok: false, why: 'zband:' + space.z1.toFixed(2) + '>' + env.z1.toFixed(2) };
  return { ok: true };
}
function envelopeFromTransforms(db) {
  try {
    const r = rows(db, "SELECT MIN(center_x-bbox_x/2) x0, MAX(center_x+bbox_x/2) x1, " +
      "MIN(center_y-bbox_y/2) y0, MAX(center_y+bbox_y/2) y1, MIN(center_z-bbox_z/2) z0, " +
      "MAX(center_z+bbox_z/2) z1 FROM element_transforms");
    if (!r.length || r[0].z1 == null) return null;
    return r[0];
  } catch (e) { return null; }
}

function rows(db, sql) {
  const r = db.exec(sql);
  if (!r.length) return [];
  const cols = r[0].columns;
  return r[0].values.map(v => { const o = {}; cols.forEach((c, i) => o[c] = v[i]); return o; });
}

function fmtPct(n, d) { return d > 0 ? (100 * n / d).toFixed(1) + '%' : 'n/a'; }

(async () => {
  const SQL = await initSqlJs();
  let pass = 0, fail = 0;
  const ok = (cond, msg) => { console.log(`§W-ROOM-TYPE ${cond ? 'PASS' : 'FAIL'} ${msg}`); if (cond) pass++; else fail++; };

  // F0: habitability filter is reachable/correct in isolation. Every real building below reports
  // habitability_excluded=0 — that does NOT by itself prove the exclude path works (it could be
  // dead code); this is a direct unit check on synthetic labels, independent of any building's DB.
  const hab1 = spaceHabitable({ label: 'ROOF', z1: 0 }, null);
  const hab2 = spaceHabitable({ label: 'Roof void 2', z1: 0 }, null);
  const hab3 = spaceHabitable({ label: 'Bedroom 1', z1: 0 }, null);
  const hab4 = spaceHabitable({ label: 'x', z1: 10 }, { z1: 5 }); // z-band: 10 > 5+0.25
  ok(!hab1.ok && !hab2.ok && hab3.ok && !hab4.ok,
    `F0 habitability filter unit check: ROOF excluded=${!hab1.ok}, "Roof void 2" excluded=${!hab2.ok}, "Bedroom 1" kept=${hab3.ok}, z-band(10>5.25) excluded=${!hab4.ok}`);

  const config = Classifier.loadTemplateConfig(yaml.load(fs.readFileSync(CONFIG_PATH, 'utf8')));
  const templateNames = Object.keys(config.templates);
  console.log(`§W-ROOM-TYPE config: templates=[${templateNames.join(', ')}] std_floor_fraction=${config.std_floor_fraction} unclassified_z_threshold=${config.unclassified_z_threshold}`);
  ok(templateNames.length === 7, `config has 7 promoted templates (>=2 real occurrences each) — ${templateNames.length}`);

  const allResults = []; // {building, guid, name, rawLabel, area, aspect, result, isTraining}

  for (const b of BUILDINGS) {
    const file = `${LIVEWIRE}/${b}_ARC.db`;
    if (!fs.existsSync(file)) { console.log(`§W-ROOM-TYPE SKIP ${b} (no ${file})`); continue; }
    const db = new SQL.Database(new Uint8Array(fs.readFileSync(file)));
    const env = envelopeFromTransforms(db);
    const spaceRows = rows(db, "SELECT guid, name, object_type, predefined_type, center_x, center_y, " +
      "center_z, size_x, size_y, size_z FROM spatial_structure WHERE type='IfcSpace' " +
      "AND center_x IS NOT NULL AND size_x IS NOT NULL");

    // group by room_guid if present (§8 MULTI-RECT), else own guid — these livewire copies predate
    // §8, so every group here is a singleton (one row = one room), same code path either way.
    const hasRoomGuid = spaceRows.length && ('room_guid' in spaceRows[0]);
    const groups = {};
    for (const r of spaceRows) {
      const key = (hasRoomGuid && r.room_guid) ? r.room_guid : r.guid;
      (groups[key] = groups[key] || { guid: key, rows: [] }).rows.push(r);
    }

    let total = 0, habExcluded = 0, classified = 0, unclassified = 0;
    const byType = {};

    for (const key of Object.keys(groups)) {
      const g = groups[key];
      total++;
      const first = g.rows[0];
      const rawLabel = (first.object_type && first.object_type.trim() && first.object_type !== 'SPACE')
        ? first.object_type : first.name;
      const label = [first.object_type, first.predefined_type, first.name].filter(Boolean).join(' ');
      const z1 = Math.max(...g.rows.map(r => (r.center_z || 0) + (r.size_z || 0) / 2));
      const hab = spaceHabitable({ label, z1 }, env);
      if (!hab.ok) { habExcluded++; continue; }

      const feats = Classifier.featuresFromRects(g.rows);
      if (!feats) continue;
      const result = Classifier.classifyRoom(feats, config);
      if (result.unclassified) unclassified++; else { classified++; byType[result.type] = (byType[result.type] || 0) + 1; }
      allResults.push({ building: b, guid: key, rawLabel, area: feats.area, aspect: feats.aspect,
        result, isTraining: TRAINING_BUILDINGS.has(b) });
    }

    const kind = TRAINING_BUILDINGS.has(b) ? 'TRAINING-SOURCE (self-fit, not validation)' : 'HELD-OUT inference (no ground truth)';
    console.log(`§W-ROOM-TYPE ${b} [${kind}]: total_rooms=${total} habitability_excluded=${habExcluded} ` +
      `classified=${classified} (${fmtPct(classified, total - habExcluded)}) unclassified=${unclassified} ` +
      `byType=${JSON.stringify(byType)}`);
  }

  // --- honest reporting: per-row detail for the two real-label buildings (training-source, so
  // this SHOULD look good — it's not proof the classifier generalizes) ---
  console.log('\n§W-ROOM-TYPE --- Duplex + SampleHouse per-room detail (training-source) ---');
  allResults.filter(r => r.isTraining).forEach(r => {
    console.log(`§W-ROOM-TYPE   ${r.building} ${r.rawLabel.padEnd(20)} area=${r.area.toFixed(2)} aspect=${r.aspect.toFixed(2)} ` +
      `-> ${r.result.unclassified ? '(unclassified, nearest=' + r.result.nearest + ' z=' + r.result.z.toFixed(2) + ')' : r.result.type} ` +
      `confidence=${(r.result.confidence * 100).toFixed(1)}%`);
  });
  // F1: self-consistency DIAGNOSTIC, deliberately NOT a pass/fail gate — a Gaussian classifier
  // over overlapping distributions is NOT guaranteed to re-classify every training point as its
  // own label even on a perfect fit (this is normal statistics, not a bug signature). Reported so
  // any miss is visible and explained, not gated as if it were a defect.
  let selfConsistBad = 0;
  allResults.filter(r => r.isTraining).forEach(r => {
    const norm = Classifier.normalizeLabel(r.rawLabel);
    if (config.templates[norm] && (!r.result.type || r.result.type !== norm)) {
      selfConsistBad++;
      console.log(`§W-ROOM-TYPE   SELF-CONSIST MISS (diagnostic, not a bug): ${r.building} ${r.rawLabel} ` +
        `normalized=${norm} but classified as ${r.result.type || '(unclassified)'} — see write-up for why`);
    }
  });
  console.log(`§W-ROOM-TYPE self-consistency diagnostic: ${selfConsistBad}/${allResults.filter(r => r.isTraining && config.templates[Classifier.normalizeLabel(r.rawLabel)]).length} training rooms with a promoted-template label did NOT re-classify as their own label`);

  // --- honest reporting: aggregate confidence distribution across the 6 held-out buildings ---
  console.log('\n§W-ROOM-TYPE --- held-out (COMPILED, no ground truth) confidence distribution ---');
  const heldOut = allResults.filter(r => !r.isTraining);
  const buckets = { '0-20%': 0, '20-40%': 0, '40-60%': 0, '60-80%': 0, '80-100%': 0, 'unclassified': 0 };
  heldOut.forEach(r => {
    if (r.result.unclassified) { buckets['unclassified']++; return; }
    const c = r.result.confidence * 100;
    if (c < 20) buckets['0-20%']++; else if (c < 40) buckets['20-40%']++; else if (c < 60) buckets['40-60%']++;
    else if (c < 80) buckets['60-80%']++; else buckets['80-100%']++;
  });
  console.log(`§W-ROOM-TYPE held-out total=${heldOut.length} distribution=${JSON.stringify(buckets)}`);
  console.log(`§W-ROOM-TYPE held-out unclassified rate=${fmtPct(buckets.unclassified, heldOut.length)} — ` +
    `EXPECTED to be high: these are COMPILED (synthetic, algorithmically-sized flood-fill pockets ` +
    `in institutional/industrial buildings), not residential rooms shaped like Duplex's mirrored ` +
    `bedroom/kitchen/bathroom units. A low unclassified rate here would be a red flag (over-eager ` +
    `matching), not a win.`);

  // --- config-editability: verified, not assumed (Scope step 3) ---
  console.log('\n§W-ROOM-TYPE --- config-editability check ---');
  const sample = heldOut.find(r => r.result.unclassified) || heldOut[0];
  if (sample) {
    const before = Classifier.classifyRoom({ area: sample.area, aspect: sample.aspect }, config);
    // widen every template's std by 50x in an in-memory COPY (never touches the yaml file) —
    // proves the classifier actually reads config.templates[*].area_m2/aspect_ratio.std, not a
    // hardcoded band.
    const widened = JSON.parse(JSON.stringify(config));
    Object.keys(widened.templates).forEach(k => {
      widened.templates[k].area_m2.std *= 50;
      widened.templates[k].aspect_ratio.std *= 50;
    });
    widened.unclassified_z_threshold = 100; // also relax the refuse-to-classify gate
    const after = Classifier.classifyRoom({ area: sample.area, aspect: sample.aspect }, widened);
    const changed = before.unclassified !== after.unclassified || before.type !== after.type ||
      Math.abs(before.confidence - after.confidence) > 1e-6;
    console.log(`§W-ROOM-TYPE editability sample=${sample.building}:${sample.guid} area=${sample.area.toFixed(2)} aspect=${sample.aspect.toFixed(2)} ` +
      `BEFORE(narrow config)=${before.unclassified ? '(unclassified)' : before.type + '@' + (before.confidence * 100).toFixed(1) + '%'} ` +
      `AFTER(50x-widened std, threshold=100)=${after.unclassified ? '(unclassified)' : after.type + '@' + (after.confidence * 100).toFixed(1) + '%'}`);
    ok(changed, 'config-editability: widening template std + relaxing threshold in an in-memory config copy changes the classification (proves the classifier reads config, not a hardcoded band) — original config/room_templates.yaml file untouched');
  } else {
    ok(false, 'config-editability: no held-out sample room available to test');
  }

  console.log(`\n§W-ROOM-TYPE SUMMARY: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
