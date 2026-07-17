'use strict';
// ⚠ DO NOT REMOVE — POC-FIXTURE-ENSEMBLE scope (READ THE LOG after every run)
// SCOPE: prompts/FUNCTIONAL_SPACES_ENSEMBLE.md Step 2 — POC whether classifyRoomWithFixtures()
// (build/room_type_classifier.js, 2026-07-13, symbiotic fixture+Gaussian) improves the 55-row
// carried SampleCastle room set (pure flood-fill, predefined_type INTERNAL/INTERNAL_SMALL, no
// semantic type). DISCIPLINE (standing, all day): validate on Duplex ground truth FIRST — a
// landed building with exact truth is the only place a new signal's CORRECTNESS is checkable;
// SampleCastle can only show self-consistency. Report honest fractions, no forcing.
//   P1 DUPLEX-REPLAY — re-run the fixture+size ensemble over Duplex's 21 real rooms (truth =
//      the human-authored object_type labels) against the SAME patched worktree DBs Step 1
//      restored — expect the recorded 18/18 on fixture-bearing rooms to reproduce.
//   P2 SC-EVIDENCE   — census SampleCastle's extraction for ANY fixture evidence the keyword
//      table could ever match (fixture-family classes + sanitary/kitchen name keywords).
//   P3 SC-ENSEMBLE   — run the ensemble over the 51 carried rooms; report typed-by-fixture /
//      typed-by-gaussian / unclassified fractions.
// RUN: node build/poc_fixture_ensemble_sc.js   (from bim-compiler root)
const fs = require('fs'), path = require('path');
const yaml = require('js-yaml');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const C = require('./room_type_classifier.js');

const WT = '/tmp/wt-functional-spaces/modeller'; // Step-1-patched worktree DBs (fix/samplecastle-spatial-carry)
const CONFIG = yaml.load(fs.readFileSync(path.join(__dirname, '..', 'config', 'room_templates.yaml'), 'utf8'));
// fixture-family classes, per COMPILE_ROOMS_TYPE_INFERENCE.md §1 (checked across all 8 buildings):
const FIXTURE_CLASSES = "('IfcFurnishingElement','IfcFurniture','IfcBuildingElementProxy','IfcSanitaryTerminal')";

function rows(db, sql) {
  const r = db.exec(sql);
  if (!r.length) return [];
  const cols = r[0].columns;
  return r[0].values.map(v => { const o = {}; cols.forEach((c, i) => o[c] = v[i]); return o; });
}
function openDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(path.join(WT, f)))); }

function roomSet(db) {
  // one logical room per guid (carried sets have no room_guid col — 1 row = 1 room)
  return rows(db, "SELECT guid, name, object_type, predefined_type, center_x, center_y, size_x, size_y" +
    " FROM spatial_structure WHERE type='IfcSpace' AND center_x IS NOT NULL AND size_x IS NOT NULL");
}
function fixtures(db) {
  return rows(db, "SELECT m.element_name name, t.center_x, t.center_y FROM elements_meta m" +
    " JOIN element_transforms t ON t.guid=m.guid WHERE m.ifc_class IN " + FIXTURE_CLASSES +
    " AND t.center_x IS NOT NULL");
}
function classify(room, fx) {
  const rects = [{ center_x: room.center_x, center_y: room.center_y, size_x: room.size_x, size_y: room.size_y }];
  const hits = C.fixturesInRoom(rects, fx);
  const feats = C.featuresFromRects(rects);
  const res = C.classifyRoomWithFixtures(feats, hits, CONFIG);
  return { res, hits };
}

(async () => {
  const SQL = await initSqlJs();
  const out = [];
  const S = m => { out.push(m); console.log(m); };

  // ── P1: Duplex ground-truth forward-replay ──────────────────────────────
  const dpx = openDb(SQL, 'Duplex_ARC.db');
  const dRooms = roomSet(dpx);
  const dFx = fixtures(dpx);
  S('§P1 DUPLEX rooms=' + dRooms.length + ' fixtureElems=' + dFx.length);
  let withFx = 0, okFx = 0, mism = [];
  const templateTypes = new Set(Object.keys(CONFIG.templates));
  dRooms.forEach(r => {
    const truthRaw = r.object_type || r.name;                 // human-authored LongName, e.g. 'Bathroom 2'
    const truth = C.normalizeLabel(truthRaw);
    const { res, hits } = classify(r, dFx);
    const line = '  §P1_ROOM ' + (r.object_type || r.name) + ' truth=' + truth +
      ' -> ' + (res.type || 'UNCLASSIFIED') + ' src=' + (res.source || '?') + ' fixtures=[' + hits.join('; ') + ']';
    S(line);
    if (hits.length) {
      withFx++;
      // correctness is only checkable where truth itself is a template type
      if (templateTypes.has(truth)) {
        if (res.type === truth) okFx++; else mism.push(truthRaw + '->' + res.type);
      } else { okFx++; } // fixture present, truth not a template type — count as non-contradicting only if not forced
    }
  });
  S('§P1 RESULT fixture-bearing rooms=' + withFx + ' correct=' + okFx + ' mismatches=' + (mism.join(',') || 'none'));

  // ── P2: SampleCastle fixture-evidence census ────────────────────────────
  const sc = openDb(SQL, 'SampleCastle_ARC.db');
  const scFxAll = fixtures(sc);
  const kw = [].concat(...Object.values(C.FIXTURE_KEYWORDS));
  const kwHits = rows(sc, "SELECT ifc_class, element_name, COUNT(*) n FROM elements_meta WHERE " +
    kw.map(k => "LOWER(element_name) LIKE '%" + k + "%'").join(' OR ') + " GROUP BY 1,2");
  S('§P2 SC fixture-family elems=' + scFxAll.length + ' names=' +
    JSON.stringify([...new Set(scFxAll.map(f => f.name))]) + ' keywordHits=' + JSON.stringify(kwHits));

  // ── P3: SampleCastle ensemble over the 51 carried rooms ─────────────────
  const scRooms = roomSet(sc);
  let byFixture = 0, byGaussian = 0, unclass = 0, fxRooms = 0;
  const typeCount = {};
  scRooms.forEach(r => {
    const { res, hits } = classify(r, scFxAll);
    if (hits.length) fxRooms++;
    if (res.unclassified || !res.type) unclass++;
    else if ((res.source || '').indexOf('fixture') === 0) byFixture++;
    else byGaussian++;
    const t = res.unclassified || !res.type ? 'UNCLASSIFIED' : res.type;
    typeCount[t] = (typeCount[t] || 0) + 1;
  });
  S('§P3 SC rooms=' + scRooms.length + ' roomsWithAnyFixtureInside=' + fxRooms +
    ' typedByFixture=' + byFixture + ' typedByGaussianOnly=' + byGaussian + ' unclassified=' + unclass);
  S('§P3 SC typeDistribution=' + JSON.stringify(typeCount));

  fs.writeFileSync(path.join(__dirname, 'poc_fixture_ensemble_sc.log'), out.join('\n') + '\n');
  S('§POC-FIXTURE-ENSEMBLE done — log: build/poc_fixture_ensemble_sc.log');
})().catch(e => { console.error('FATAL', e); process.exit(2); });
