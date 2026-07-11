#!/usr/bin/env node
'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: prompts/DISC_WALK_ROOM_TYPE_AWARE.md — MEASURE (not assume) whether real discipline
// element density correlates with classified room type, on real MEP-bearing buildings. Read the
// §-log after every run; exit code alone is not evidence.
//
// SUBSTRATE HONESTY (checked first, reported plainly): Terminal (deploy/buildings/Terminal_extracted.db)
// has 0 real IfcSpace rows and NO spatial_structure table at all (confirmed live query) — it cannot
// contribute a room-type x discipline measurement; WalkerDoctrine's own §NOSPACES comment says the
// same ("shipped ARC residents mostly carry NO spatial_structure table"). Duplex is the ONLY building
// in this repo with BOTH (a) real per-room labels+geometry (deploy/buildings/Duplex_extracted.db's
// spatial_structure, object_type = real Revit LongName) AND (b) real placed MEP fixtures
// (build/Duplex_mep_extracted.db). This script measures on Duplex ONLY and says so — it does not
// force a second building into the table.
//
// PIPELINE (every step traces to a real source, nothing invented):
//   1. Real rooms: deploy/buildings/Duplex_extracted.db spatial_structure (guid, object_type=real
//      label, center/size). Habitability filter ported verbatim from build/witness_room_type_classifier.js
//      (itself ported from bim-ootb common/room_habitability.js).
//   2. Room-type classification: build/room_type_classifier.js + config/room_templates.yaml
//      (area+aspect only — no door signal here, Duplex_mep_extracted.db carries no IfcDoor rows).
//   3. Real MEP elements: build/Duplex_mep_extracted.db elements_meta WHERE discipline='MEP', joined
//      to element_transforms for position. elements_meta.discipline is the COARSE extractor bucket
//      (DAGCompiler DISCIPLINE_MAP, ifc_class-only) — every real Revit MEP element in this DB is
//      generically 'MEP', not pre-split into PLB/ELEC/FP/ACMV.
//   4. Fine discipline: library/disc_patterns.db's ad_element_mep_alias (element_name LIKE patterns,
//      priority 4, source='DX_MINED' = MEASURED off this exact Duplex model, migration/DV003) joined
//      to ad_element_mep.discipline (ELEC/SP/FP/HVAC). SP->PLB and HVAC->ACMV per the same fold
//      build/project_rule_space_schedule.py applies when it builds duplex_rules.db's disc column
//      (confirmed by source read, not invented here). Unmatched element_names are counted and
//      reported as UNCLASSIFIED_MEP, never guessed into a bucket.
//   5. Spatial join (element -> room): point-in-bbox, element center inside room bbox padded by
//      ROOM_PAD (wall-mounted fixtures sit slightly outside the room's floor-slab bbox). Ties broken
//      by nearest room centroid. No match -> unassigned, counted and reported (expected: most of the
//      904 MEP rows are routing fittings/segments between rooms, not room-hosted terminals).
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const initSqlJs = require('sql.js');
const Classifier = require('./room_type_classifier.js');

const ROOT = path.join(__dirname, '..');
const CONFIG_PATH = path.join(ROOT, 'config', 'room_templates.yaml');
const ARC_DB = path.join(ROOT, 'deploy', 'buildings', 'Duplex_extracted.db');
const MEP_DB = path.join(ROOT, 'build', 'Duplex_mep_extracted.db');
const PAT_DB = path.join(ROOT, 'library', 'disc_patterns.db');
const TERMINAL_DB = path.join(ROOT, 'deploy', 'buildings', 'Terminal_extracted.db');
const LOG = path.join(ROOT, 'logs', 'measure_disc_room_type_density_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

const ROOM_PAD = 0.30; // m — wall-mounted fixture standoff from the room bbox edge; not invented,
                        // same order of magnitude as disc_walker.js's own SNAP_REACH/standoff constants

const NONHAB_TYPES = ['ROOF', 'SHAFT', 'VOID', 'PLANT', 'PLANT_ROOM', 'EXTERNAL', 'PODIUM',
  'SILL', 'PARAPET', 'BALCONY']; // ported verbatim, see header
function spaceHabitable(label) {
  const norm = String(label || '').toUpperCase().replace(/\s+/g, '_').replace(/[_\s]*\d+$/, '').trim();
  const toks = norm.split('_');
  for (const t of NONHAB_TYPES) if (norm === t || toks.indexOf(t) >= 0) return false;
  return true;
}

// SP->PLB, HVAC->ACMV fold — same as build/project_rule_space_schedule.py's disc reconciliation
// (confirmed by source read: SP/CW->PLB, HVAC->ACMV), applied here to the disc_patterns.db raw
// discipline codes so this script's output uses the SAME disc vocabulary as duplex_rules.db/disc_walker.js.
const DISC_FOLD = { SP: 'PLB', CW: 'PLB', HVAC: 'ACMV', ELEC: 'ELEC', FP: 'FP' };

const _lines = [];
function log(s) { _lines.push(s); console.log(s); }

function rows(db, sql) {
  const r = db.exec(sql);
  if (!r.length) return [];
  const cols = r[0].columns;
  return r[0].values.map(v => { const o = {}; cols.forEach((c, i) => o[c] = v[i]); return o; });
}
function loadDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(f))); }

function likeToRegex(pat) {
  // SQL LIKE '%text%' -> case-insensitive substring regex (only % wildcards appear in DV003)
  const esc = pat.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/%/g, '.*');
  return new RegExp('^' + esc + '$', 'i');
}

(async () => {
  log('═══ W-DW-TYPE-DENSITY — measured discipline x room-type element density (prompts/DISC_WALK_ROOM_TYPE_AWARE.md) ═══');
  const SQL = await initSqlJs();

  // ── §SUBSTRATE-CHECK: Terminal has no real per-room data, reported honestly, not silently skipped ──
  log('');
  log('─── §SUBSTRATE-CHECK ───');
  if (fs.existsSync(TERMINAL_DB)) {
    const te = loadDb(SQL, TERMINAL_DB);
    const teTables = rows(te, "SELECT name FROM sqlite_master WHERE type='table'").map(r => r.name);
    const teSpaceRows = teTables.includes('spatial_structure')
      ? rows(te, "SELECT COUNT(*) n FROM spatial_structure WHERE type='IfcSpace'")[0].n : 0;
    const teIfcSpace = rows(te, "SELECT COUNT(*) n FROM elements_meta WHERE ifc_class='IfcSpace'")[0].n;
    log(`§SUBSTRATE-CHECK Terminal: spatial_structure table present=${teTables.includes('spatial_structure')} ` +
      `IfcSpace(spatial_structure)=${teSpaceRows} IfcSpace(elements_meta)=${teIfcSpace} — ` +
      (teSpaceRows === 0 && teIfcSpace === 0
        ? 'NO real per-room space data. Terminal is REFUSED as a room-type x discipline measurement substrate (honest, not silently skipped).'
        : 'has real space data — should be added to this measurement, flag for follow-up.'));
    te.close();
  } else {
    log('§SUBSTRATE-CHECK Terminal_extracted.db not found at ' + TERMINAL_DB + ' — skipped, cannot verify substrate claim');
  }
  log('§SUBSTRATE-CHECK Duplex: deploy/buildings/Duplex_extracted.db (real object_type labels+geometry) + ' +
    'build/Duplex_mep_extracted.db (904 real placed MEP elements) — the ONLY real substrate with both signals in this repo.');

  // ── load rooms (label+geometry) ──
  const arcDb = loadDb(SQL, ARC_DB);
  const roomRows = rows(arcDb, "SELECT guid, name, object_type, center_x, center_y, size_x, size_y " +
    "FROM spatial_structure WHERE type='IfcSpace' AND center_x IS NOT NULL AND size_x IS NOT NULL");
  arcDb.close();

  const config = Classifier.loadTemplateConfig(yaml.load(fs.readFileSync(CONFIG_PATH, 'utf8')));
  log('');
  log('§ROOMS Duplex: ' + roomRows.length + ' real IfcSpace rows (spatial_structure), templates=[' +
    Object.keys(config.templates).join(', ') + ']');

  const rooms = [];
  let habExcluded = 0;
  roomRows.forEach(r => {
    const label = (r.object_type && r.object_type.trim() && r.object_type !== 'SPACE') ? r.object_type : r.name;
    if (!spaceHabitable(label)) { habExcluded++; log(`§ROOMS EXCLUDE ${r.name} (${label}) — non-habitable`); return; }
    const feats = Classifier.featuresFromRects([r]);
    if (!feats) return;
    const result = Classifier.classifyRoom(feats, config);
    const x0 = r.center_x - r.size_x / 2, x1 = r.center_x + r.size_x / 2;
    const y0 = r.center_y - r.size_y / 2, y1 = r.center_y + r.size_y / 2;
    rooms.push({
      guid: r.guid, name: r.name, rawLabel: label, area: feats.area, aspect: feats.aspect,
      x0, x1, y0, y1, cx: r.center_x, cy: r.center_y,
      classType: result.unclassified ? '(unclassified)' : result.type, confidence: result.confidence,
      z: result.z
    });
  });
  log('§ROOMS classified: ' + rooms.length + ' habitable rooms (' + habExcluded + ' excluded as non-habitable)');
  rooms.forEach(r => log(`§ROOMS   ${r.name.padEnd(6)} label="${r.rawLabel}" area=${r.area.toFixed(2)}m2 aspect=${r.aspect.toFixed(2)} ` +
    `-> classified=${r.classType}${r.classType === '(unclassified)' ? ' z=' + r.z.toFixed(2) : ' conf=' + (r.confidence * 100).toFixed(1) + '%'}`));

  // ── fine discipline classification for real MEP elements ──
  const patDb = loadDb(SQL, PAT_DB);
  const aliasRows = rows(patDb, "SELECT canonical_type, match_value FROM ad_element_mep_alias " +
    "WHERE match_field='element_name' AND is_active=1 ORDER BY priority ASC");
  const discByCanon = {};
  rows(patDb, "SELECT Value, discipline FROM ad_element_mep").forEach(r => { discByCanon[r.Value] = r.discipline; });
  patDb.close();
  const patterns = aliasRows.map(a => ({ canon: a.canonical_type, re: likeToRegex(a.match_value) }));

  function classifyElement(name) {
    for (const p of patterns) if (p.re.test(name)) {
      const rawDisc = discByCanon[p.canon];
      return { canon: p.canon, disc: DISC_FOLD[rawDisc] || rawDisc };
    }
    return null;
  }

  const mepDb = loadDb(SQL, MEP_DB);
  const mepRows = rows(mepDb, "SELECT m.guid, m.element_name, t.center_x cx, t.center_y cy, t.center_z cz " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.discipline='MEP'");
  mepDb.close();

  log('');
  log('§MEP-CLASSIFY ' + mepRows.length + ' real MEP elements (discipline=\'MEP\' coarse bucket) in Duplex_mep_extracted.db');
  const discCount = {}, unmatchedCount = {};
  const elements = [];
  mepRows.forEach(m => {
    const c = classifyElement(m.element_name);
    if (c) {
      discCount[c.disc] = (discCount[c.disc] || 0) + 1;
      elements.push(Object.assign({}, m, c));
    } else {
      const key = m.element_name.split(':')[0]; // Revit family name prefix, for a readable tally
      unmatchedCount[key] = (unmatchedCount[key] || 0) + 1;
    }
  });
  log('§MEP-CLASSIFY matched by discipline: ' + JSON.stringify(discCount));
  const unmatchedTotal = Object.values(unmatchedCount).reduce((a, b) => a + b, 0);
  log('§MEP-CLASSIFY UNMATCHED (no DV003 alias hit, honestly excluded, not guessed): ' + unmatchedTotal + ' — ' +
    JSON.stringify(unmatchedCount));

  // ── spatial join: element -> room (point-in-padded-bbox, nearest centroid tiebreak) ──
  let unassigned = 0;
  elements.forEach(e => {
    let best = null, bestD = Infinity;
    rooms.forEach(r => {
      const inside = e.cx >= r.x0 - ROOM_PAD && e.cx <= r.x1 + ROOM_PAD &&
                     e.cy >= r.y0 - ROOM_PAD && e.cy <= r.y1 + ROOM_PAD;
      if (!inside) return;
      const d = Math.hypot(e.cx - r.cx, e.cy - r.cy);
      if (d < bestD) { bestD = d; best = r; }
    });
    e.room = best;
    if (!best) unassigned++;
  });
  log('§SPATIAL-JOIN ' + (elements.length - unassigned) + '/' + elements.length +
    ' classified MEP elements assigned to a real room bbox (pad=' + ROOM_PAD + 'm); ' +
    unassigned + ' unassigned (routing fittings/segments between rooms — expected, honestly excluded from the density table)');

  // ── DENSITY TABLE: discipline x classified room-type ──
  log('');
  log('─── §DENSITY-TABLE (discipline x CLASSIFIED room-type, real Duplex data) ───');
  const byTypeArea = {}; // classType -> {area, rooms:[names]}
  rooms.forEach(r => { (byTypeArea[r.classType] = byTypeArea[r.classType] || { rooms: [] }).rooms.push(r); });

  const DISCS = ['PLB', 'ELEC', 'FP', 'ACMV'];
  const table = {}; // disc -> classType -> {count, roomCount, perRoomCounts:[]}
  DISCS.forEach(d => { table[d] = {}; });
  Object.keys(byTypeArea).forEach(t => {
    DISCS.forEach(d => { table[d][t] = { count: 0, roomCount: byTypeArea[t].rooms.length, perRoom: [] }; });
    byTypeArea[t].rooms.forEach(r => {
      DISCS.forEach(d => {
        const n = elements.filter(e => e.room === r && e.disc === d).length;
        table[d][t].count += n;
        table[d][t].perRoom.push({ name: r.name, n, area: r.area, density: r.area > 0 ? n / r.area : 0 });
      });
    });
  });

  DISCS.forEach(d => {
    log('');
    log('§DENSITY disc=' + d + ':');
    Object.keys(table[d]).sort().forEach(t => {
      const cell = table[d][t];
      const densities = cell.perRoom.map(p => p.density);
      const mean = densities.length ? densities.reduce((a, b) => a + b, 0) / densities.length : 0;
      const min = densities.length ? Math.min(...densities) : 0, max = densities.length ? Math.max(...densities) : 0;
      log(`§DENSITY   ${t.padEnd(16)} n_rooms=${cell.roomCount} total_count=${cell.count} ` +
        `mean_density=${mean.toFixed(3)}/m2 range=[${min.toFixed(3)},${max.toFixed(3)}] ` +
        `per-room=[${cell.perRoom.map(p => p.name + ':' + p.n).join(' ')}]`);
    });
  });

  // ── §CORRIDOR-CHECK: explicit HALLWAY/FOYER (supplementary tier) vs FP/ELEC density ──
  log('');
  log('─── §CORRIDOR-CHECK (supplementary tier: HALLWAY/FOYER vs FP/ELEC — explicit, not assumed) ───');
  ['FP', 'ELEC'].forEach(d => {
    const supp = ['HALLWAY', 'FOYER'].map(t => table[d][t]).filter(Boolean);
    const primary = Object.keys(table[d]).filter(t => t !== 'HALLWAY' && t !== 'FOYER' && t !== '(unclassified)')
      .map(t => table[d][t]);
    const suppMean = supp.length ? supp.reduce((a, c) => a + c.count, 0) / supp.reduce((a, c) => a + c.roomCount, 0) : null;
    const primMean = primary.length ? primary.reduce((a, c) => a + c.count, 0) / primary.reduce((a, c) => a + c.roomCount, 0) : null;
    log(`§CORRIDOR-CHECK ${d}: supplementary(HALLWAY+FOYER) mean=${suppMean === null ? 'n/a' : suppMean.toFixed(3) + '/room'} ` +
      `vs primary(BEDROOM/BATHROOM/KITCHEN/LIVING_ROOM/UTILITY) mean=${primMean === null ? 'n/a' : primMean.toFixed(3) + '/room'}`);
  });

  log('');
  log('§LOG done. Raw §-lines above are the full measured evidence — no summary claim below should be trusted over them.');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) { console.error(e); }
})();
