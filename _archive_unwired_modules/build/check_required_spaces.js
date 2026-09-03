'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: §BOM-REQUIRED-SPACES (prompts/ROOM_TYPE_TEMPLATE_CLASSIFIER.md "Bigger reframing",
// 2026-07-11 follow-up). Feeds build/room_type_classifier.js's PRIMARY-tier classified room
// counts into config/profiles/malaysian_residential.yaml's `required_spaces` recipe — a dormant
// BOM shape (one profile/parent, N typed children, each with a min_count quantity, per CLAUDE.md's
// BOM PRINCIPLE) that had no real room data to check against until today's classifier existed.
//
// REUSED PATTERN, not invented fresh: DAGCompiler/src/main/java/com/bim/compiler/validation/
// building/ProtocolValidatorRegistry.java's BaseProtocolValidator.validate() already does exactly
// this shape (count spaces by canonical type -> compare to req.minCount() -> report a shortfall
// warning if count < minCount) against a DIFFERENT data source (BuildingSpec/RoomSpec, the DAG
// compiler's own author-provided intermediate representation, via ProtocolValidator.getProtocol()
// -- a separate profile registry from config/profiles/*.yaml's ProfileRegistry.java, itself a
// pre-existing, undisturbed duplication, not something this task fixes). This module mirrors that
// SAME shape (count-vs-minCount-shortfall) in JS, applied to real spatial_structure room data via
// the classifier this project just built -- not a parallel validation system, the same recipe
// concept applied to a data source (a probabilistic room-TYPE classifier's output) that did not
// exist when the Java validator was written.
//
// Read the log after every run -- exit code alone is not evidence.
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const Classifier = require('./room_type_classifier.js');

const TEMPLATES_PATH = path.join(__dirname, '..', 'config', 'room_templates.yaml');
const PROFILE_PATH = path.join(__dirname, '..', 'config', 'profiles', 'malaysian_residential.yaml');
const LIVEWIRE = '/tmp/wt-fable-livewire/modeller';

// --- habitability filter, same literal port as build/witness_room_type_classifier.js (kept in
// sync deliberately -- see that file's header for the bim-ootb common/room_habitability.js
// citation this was ported from) ---
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

// §DWELLING-UNIT: Duplex's real room names are literally "A101".."B205" -- a real naming
// convention already present in the source IFC (twin semi-D unit prefix), not invented. A Duplex
// IS two separate dwelling units sharing one structure; checking required_spaces against the
// WHOLE building would trivially "pass" (2 units x everything) and could hide a real per-unit
// shortfall. If every real-labeled room name in a building matches ^[A-Z]\d, split by that leading
// letter into separate dwelling units; otherwise the whole building is one dwelling (SampleHouse's
// real room names -- "1 - Living room" etc -- don't match, so it stays a single dwelling).
function dwellingUnitOf(roomName) {
  const m = /^([A-Z])\d/.exec(roomName || '');
  return m ? m[1] : 'DEFAULT';
}

// Classify every habitable room in a building DB, return [{roomName, unit, doorCount, result}].
function classifyBuildingRooms(db, config) {
  const env = envelopeFromTransforms(db);
  const spaceRows = rows(db, "SELECT s.guid, s.name, s.object_type, s.predefined_type, s.center_x, " +
    "s.center_y, s.center_z, s.size_x, s.size_y, s.size_z, p.name as storey_name FROM " +
    "spatial_structure s LEFT JOIN spatial_structure p ON p.guid=s.parent_guid " +
    "WHERE s.type='IfcSpace' AND s.center_x IS NOT NULL AND s.size_x IS NOT NULL");
  const doorRows = rows(db, "SELECT m.storey, m.element_name, t.center_x, t.center_y, t.bbox_x, " +
    "t.bbox_y FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid " +
    "WHERE m.ifc_class LIKE 'IfcDoor%' AND m.discipline='ARC' AND t.center_x IS NOT NULL");

  const hasRoomGuid = spaceRows.length && ('room_guid' in spaceRows[0]);
  const groups = {};
  for (const r of spaceRows) {
    const key = (hasRoomGuid && r.room_guid) ? r.room_guid : r.guid;
    (groups[key] = groups[key] || { guid: key, rows: [] }).rows.push(r);
  }

  const out = [];
  for (const key of Object.keys(groups)) {
    const g = groups[key];
    const first = g.rows[0];
    const rawLabel = (first.object_type && first.object_type.trim() && first.object_type !== 'SPACE')
      ? first.object_type : first.name;
    const label = [first.object_type, first.predefined_type, first.name].filter(Boolean).join(' ');
    const z1 = Math.max(...g.rows.map(r => (r.center_z || 0) + (r.size_z || 0) / 2));
    if (!spaceHabitable({ label, z1 }, env).ok) continue;

    const feats = Classifier.featuresFromRects(g.rows);
    if (!feats) continue;
    const storeyDoors = doorRows.filter(d => (d.storey || '') === (first.storey_name || ''));
    const doorCount = Classifier.countAdjacentDoors(g.rows, storeyDoors);
    const result = Classifier.classifyRoom({ area: feats.area, aspect: feats.aspect, doorCount }, config);
    out.push({ roomName: first.name, rawLabel, unit: dwellingUnitOf(first.name), doorCount, result });
  }
  return out;
}

// §BOM-REQUIRED-SPACES: the check itself. classifiedRooms = classifyBuildingRooms() output for
// ONE dwelling unit. Mirrors ProtocolValidatorRegistry.BaseProtocolValidator.validate()'s shape.
function checkRequiredSpaces(classifiedRooms, requiredSpaces, config) {
  const counts = {}; // canonical_type -> count, PRIMARY tier only
  const excludedSupplementary = [];
  for (const r of classifiedRooms) {
    if (r.result.unclassified) continue;
    const tmpl = config.templates[r.result.type];
    if (!tmpl) continue;
    if (tmpl.tier !== 'primary') { excludedSupplementary.push(r.result.type); continue; } // §PRIMARY-VS-SUPPLEMENTARY
    const canon = tmpl.canonical_type;
    if (!canon) continue; // e.g. UTILITY -- primary but no required_spaces vocabulary entry
    counts[canon] = (counts[canon] || 0) + 1;
  }
  const shortfalls = [];
  for (const req of requiredSpaces) {
    const count = counts[req.type] || 0;
    if (count < req.min_count) {
      shortfalls.push({ type: req.type, required: req.min_count, found: count });
    }
  }
  return { counts, shortfalls, satisfied: shortfalls.length === 0, excludedSupplementaryCount: excludedSupplementary.length };
}

const API = { dwellingUnitOf, classifyBuildingRooms, checkRequiredSpaces };
if (typeof module !== 'undefined' && module.exports) module.exports = API;

// --- standalone witness: real Duplex + SampleHouse data, per dwelling UNIT ---
if (require.main === module) {
  (async () => {
    const SQL = await initSqlJs();
    const config = Classifier.loadTemplateConfig(yaml.load(fs.readFileSync(TEMPLATES_PATH, 'utf8')));
    const profile = yaml.load(fs.readFileSync(PROFILE_PATH, 'utf8'));
    const requiredSpaces = profile.required_spaces;
    console.log(`§W-BOM-SPACES profile=${profile.name} required_spaces=${JSON.stringify(requiredSpaces)}`);

    let pass = 0, fail = 0;
    const ok = (cond, msg) => { console.log(`§W-BOM-SPACES ${cond ? 'PASS' : 'FAIL'} ${msg}`); if (cond) pass++; else fail++; };

    for (const b of ['Duplex', 'SampleHouse']) {
      const file = `${LIVEWIRE}/${b}_ARC.db`;
      if (!fs.existsSync(file)) { console.log(`§W-BOM-SPACES SKIP ${b} (no ${file})`); continue; }
      const db = new SQL.Database(new Uint8Array(fs.readFileSync(file)));
      const classified = classifyBuildingRooms(db, config);

      const units = {};
      classified.forEach(r => (units[r.unit] = units[r.unit] || []).push(r));

      for (const unit of Object.keys(units).sort()) {
        const rooms = units[unit];
        rooms.forEach(r => console.log(`§W-BOM-SPACES   ${b}:${unit} ${r.roomName}(${r.rawLabel}) doors=${r.doorCount} -> ` +
          `${r.result.unclassified ? '(unclassified)' : r.result.type + '@' + (r.result.confidence * 100).toFixed(1) + '%'}`));

        const check = checkRequiredSpaces(rooms, requiredSpaces, config);
        const unitLabel = unit === 'DEFAULT' ? b : `${b} unit ${unit}`;
        console.log(`§W-BOM-SPACES ${unitLabel}: primary-tier canonical counts=${JSON.stringify(check.counts)} ` +
          `(${check.excludedSupplementaryCount} supplementary-tier rooms excluded)`);
        if (check.satisfied) {
          console.log(`§W-BOM-SPACES ${unitLabel}: SATISFIES ${profile.name} required_spaces`);
        } else {
          check.shortfalls.forEach(s => console.log(`§W-BOM-SPACES ${unitLabel}: SHORTFALL — requires at least ${s.required} ${s.type}, found ${s.found}`));
          console.log(`§W-BOM-SPACES ${unitLabel}: does NOT satisfy ${profile.name} required_spaces (${check.shortfalls.length} shortfall(s))`);
        }
      }
    }

    // F1: mechanism check -- a synthetic dwelling missing BEDROOM entirely must report exactly
    // that shortfall (proves checkRequiredSpaces() actually gates on count, not always-pass).
    const fakeConfig = config;
    const fakeRooms = [
      { result: { type: 'LIVING_ROOM', unclassified: false, confidence: 1 } },
      { result: { type: 'KITCHEN', unclassified: false, confidence: 1 } },
      { result: { type: 'BATHROOM', unclassified: false, confidence: 1 } },
      { result: { type: 'HALLWAY', unclassified: false, confidence: 1 } }, // supplementary, must NOT count toward anything
    ];
    const fakeCheck = checkRequiredSpaces(fakeRooms, requiredSpaces, fakeConfig);
    ok(!fakeCheck.satisfied && fakeCheck.shortfalls.length === 1 && fakeCheck.shortfalls[0].type === 'BEDROOM' && fakeCheck.shortfalls[0].found === 0,
      `F1 mechanism: synthetic dwelling (LIVING+KITCHEN+BATHROOM+HALLWAY, no BEDROOM) correctly reports exactly 1 shortfall (BEDROOM, found=0) — proves the gate actually gates`);
    ok(fakeCheck.excludedSupplementaryCount === 1,
      `F2 mechanism: the synthetic HALLWAY room was excluded from counts as supplementary-tier (excludedSupplementaryCount=${fakeCheck.excludedSupplementaryCount}) — proves tier filtering actually runs`);

    console.log(`\n§W-BOM-SPACES SUMMARY: ${pass}/${pass + fail} checks passed`);
    process.exit(fail === 0 ? 0 : 1);
  })();
}
