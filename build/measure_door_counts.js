'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: measure per-room-type door-count stats from REAL ground-truth labeled rooms (Duplex +
// SampleHouse — the same 2-building anchor prompts/ROOM_TYPE_TEMPLATE_CLASSIFIER.md fit
// area/aspect from), for config/room_templates.yaml's `door_count` band. prompts/
// ROOM_TYPE_DOOR_ACCESS_SIGNAL.md §2: "typical ranges must be MEASURED from Duplex/SampleHouse's
// real labeled rooms... not guessed or imported". This script IS that measurement — run it, read
// the log, transcribe the printed mean/std/n into config/room_templates.yaml by hand (same
// auditable convention as the area/aspect numbers already there — no auto-rewrite of the yaml).
//
// Door count per room = build/room_type_classifier.js's countAdjacentDoors(), which reuses
// scripts/compile_rooms.py's own door-adjacency buffer test (_door_adjacent), extended from a
// boolean to a count. Doors are pre-filtered to the room's OWN STOREY before testing (mirrors
// compile_rooms.py's storey_doors() grouping) — an earlier un-storey-filtered attempt was measured
// to badly over-count (e.g. Duplex's A101 Foyer picking up Level-2 doors that only coincide in XY,
// not Z) — this is not a threshold tuned to make the numbers look nice, it is fixing an actual bug
// in the query, kept here as a comment so it is not reintroduced.
// Read the log after every run — exit code alone is not evidence.
const fs = require('fs');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const Classifier = require('./room_type_classifier.js');

const LIVEWIRE = '/tmp/wt-fable-livewire/modeller'; // only location with Duplex+SampleHouse's real
                                                      // spatial_structure + elements_meta together
                                                      // (see witness_room_type_classifier.js note)
const GROUND_TRUTH = ['Duplex', 'SampleHouse'];

function rows(db, sql) {
  const r = db.exec(sql);
  if (!r.length) return [];
  const cols = r[0].columns;
  return r[0].values.map(v => { const o = {}; cols.forEach((c, i) => o[c] = v[i]); return o; });
}

function mean(a) { return a.reduce((s, x) => s + x, 0) / a.length; }
function std(a) {
  const m = mean(a);
  return Math.sqrt(a.reduce((s, x) => s + (x - m) * (x - m), 0) / a.length);
}

(async () => {
  const SQL = await initSqlJs();
  const byType = {}; // normalizedLabel -> [{building, roomName, doorCount}]

  for (const b of GROUND_TRUTH) {
    const file = `${LIVEWIRE}/${b}_ARC.db`;
    if (!fs.existsSync(file)) { console.log(`§DOOR-MEASURE SKIP ${b} (no ${file})`); continue; }
    const db = new SQL.Database(new Uint8Array(fs.readFileSync(file)));
    const spaceRows = rows(db, "SELECT s.guid,s.name,s.object_type,s.center_x,s.center_y,s.size_x," +
      "s.size_y,p.name as storey_name FROM spatial_structure s LEFT JOIN spatial_structure p " +
      "ON p.guid=s.parent_guid WHERE s.type='IfcSpace' AND s.center_x IS NOT NULL");
    const doorRows = rows(db, "SELECT m.storey, m.element_name, t.center_x, t.center_y, t.bbox_x, " +
      "t.bbox_y FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid " +
      "WHERE m.ifc_class LIKE 'IfcDoor%' AND m.discipline='ARC' AND t.center_x IS NOT NULL");
    console.log(`§DOOR-MEASURE ${b}: ${spaceRows.length} real rooms, ${doorRows.length} real doors`);

    for (const r of spaceRows) {
      const rawLabel = (r.object_type && r.object_type.trim() && r.object_type !== 'SPACE') ? r.object_type : r.name;
      if (!rawLabel) continue;
      const norm = Classifier.normalizeLabel(rawLabel);
      const rect = [{ center_x: r.center_x, center_y: r.center_y, size_x: r.size_x, size_y: r.size_y }];
      const storeyDoors = doorRows.filter(d => (d.storey || '') === (r.storey_name || ''));
      const count = Classifier.countAdjacentDoors(rect, storeyDoors);
      (byType[norm] = byType[norm] || []).push({ building: b, roomName: `${r.name}(${rawLabel})`, doorCount: count });
      console.log(`§DOOR-MEASURE   ${b}:${(r.name || '').padEnd(6)} label=${rawLabel.padEnd(18)} norm=${norm.padEnd(14)} storey=${(r.storey_name || '?').padEnd(10)} door_count=${count}`);
    }
  }

  console.log('\n§DOOR-MEASURE --- per-type door_count stats (promotion bar n>=2, same as area/aspect) ---');
  const promoted = ['BEDROOM', 'BATHROOM', 'KITCHEN', 'LIVING_ROOM', 'FOYER', 'HALLWAY', 'UTILITY'];
  for (const type of promoted) {
    const list = byType[type] || [];
    const counts = list.map(x => x.doorCount);
    if (list.length < 2) {
      console.log(`§DOOR-MEASURE ${type.padEnd(14)} n=${list.length} — BELOW promotion bar, no door_count band added`);
      continue;
    }
    console.log(`§DOOR-MEASURE ${type.padEnd(14)} n=${counts.length} counts=[${counts.join(',')}] mean=${mean(counts).toFixed(3)} std=${std(counts).toFixed(3)} min=${Math.min(...counts)} max=${Math.max(...counts)}`);
    list.forEach(x => console.log(`§DOOR-MEASURE     ${x.building}:${x.roomName} door_count=${x.doorCount}`));
  }
  console.log('\n§DOOR-MEASURE --- exceptions (n=1, not promoted, for audit trail only) ---');
  Object.keys(byType).filter(k => !promoted.includes(k)).forEach(k => {
    byType[k].forEach(x => console.log(`§DOOR-MEASURE   ${k}: ${x.building}:${x.roomName} door_count=${x.doorCount}`));
  });
})();
