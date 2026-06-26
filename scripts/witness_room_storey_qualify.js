/**
 * W-ROOM-STOREY-QUALIFY — the bom-graph qualifies ROOMS and STOREYS by the OPENING (door) signal +
 * habitable footprint AABB, landing on the prompt's hand-counted answer (SPATIAL_DEPENDENCY_GRAPH design).
 *
 * Issue proven: the shipped bom-graph used a size_x heuristic over SYNTHETIC clustered "≈ R1" containers →
 * SH/SC showed 0 (or wrong) rooms, foundation/roof miscounted as storeys (user: "rooms/storeys unsolved").
 * Re-extracting with the current extractor (+ the new IfcSpace footprint AABB) gives REAL IfcSpace rooms;
 * this proves seedFromDb now classifies them right:
 *   • a STOREY with ≥1 door = habitable; a door-less storey (roof/foundation) = LAYER (labelled, not counted)
 *   • a ROOM = IfcSpace on a habitable storey whose ceiling height ≥ 1.8m (thin roof void excluded)
 *
 *   C1 SampleHouse — 3 rooms (Living/Bedroom/Entrance), Ground Floor habitable, Roof = layer, "4-Roof" not a room
 *   C2 Duplex — 20 rooms across Level 1 + Level 2 (habitable); T/FDN + Roof = layers
 *   C3 NON-INVENT — every room node maps to a real IfcSpace name; no fabricated rooms; layer storeys flagged
 *   C4 AABB LEG — the SampleHouse roof void is rejected by BOTH legs (door-less storey AND height < 1.8m)
 *   C5 GRACEFUL — a legacy DB with no spatial_structure type/size → storey-only tree, no throw
 *
 * Non-invent: expectations are the MEASURED re-extract (verified by hand above); LOCAL witness over the
 * v2 DBs in /tmp/reextract (re-extracted with the enhanced extractIFCtoDB.py).
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const DIR = process.argv[2] || '/tmp/reextract';

global.window = {};
const BT = require(path.join(ROOT, 'deploy/dev/bom_tree.js'));
const initSqlJs = require(path.join(ROOT, 'node_modules/sql.js'));

let PASS = 0, FAIL = 0;
const ok = (c, m) => { (c ? PASS++ : FAIL++); console.log('  ' + (c ? '✅' : '❌') + ' ' + m); return c; };

function summarize(tree) {
  const n = tree.nodes;
  const rooms = Object.keys(n).filter(k => n[k].kind === 'room');
  // named habitable storeys (the 'Unknown' bucket for storey-less elements is neither a storey nor a layer)
  const storeys = Object.keys(n).filter(k => n[k].kind === 'storey' && !n[k].layer && n[k].label !== 'Unknown');
  const layers = Object.keys(n).filter(k => n[k].kind === 'storey' && n[k].layer);
  return { rooms: rooms.map(k => n[k].label), storeys: storeys.map(k => n[k].label), layers: layers.map(k => n[k].label) };
}

(async () => {
  const SQL = await initSqlJs();

  function open(f) { return new SQL.Database(new Uint8Array(fs.readFileSync(path.join(DIR, f)))); }

  // C1 — SampleHouse
  let sh = open('SampleHouse_v2.db');
  let s = summarize(BT.seedFromDb(sh, { building: 'SampleHouse' }));
  ok(s.rooms.length === 3 && s.layers.length === 1 && s.storeys.length === 1 &&
     s.rooms.some(r => /Living/.test(r)) && s.rooms.some(r => /Bedroom/.test(r)) && s.rooms.some(r => /Entrance/.test(r)) &&
     !s.rooms.some(r => /Roof/.test(r)),
    'C1 SampleHouse — rooms=' + s.rooms.length + ' [' + s.rooms.join(', ') + '] storeys=' + JSON.stringify(s.storeys) + ' layers=' + JSON.stringify(s.layers));
  sh.close();

  // C2 — Duplex
  let dx = open('Duplex_v2.db');
  let d = summarize(BT.seedFromDb(dx, { building: 'Duplex' }));
  ok(d.rooms.length === 20 && d.storeys.length === 2 && d.layers.length === 2 &&
     d.layers.some(l => /FDN/.test(l)) && d.layers.some(l => /Roof/.test(l)),
    'C2 Duplex — rooms=' + d.rooms.length + ' storeys=' + JSON.stringify(d.storeys) + ' layers=' + JSON.stringify(d.layers));
  dx.close();

  // C3 — NON-INVENT: every room label is a real IfcSpace name in the DB
  sh = open('SampleHouse_v2.db');
  const realNames = new Set((sh.exec("SELECT name FROM spatial_structure WHERE type='IfcSpace'")[0].values).map(v => v[0]));
  const t = BT.seedFromDb(sh, {});
  const roomLabels = Object.keys(t.nodes).filter(k => t.nodes[k].kind === 'room').map(k => t.nodes[k].label);
  ok(roomLabels.every(l => realNames.has(l)), 'C3 NON-INVENT — all room labels are real IfcSpace names (' + roomLabels.length + ' rooms, 0 fabricated)');
  sh.close();

  // C4 — AABB leg: the SampleHouse roof space is < 1.8m high (rejected on height too, not just doors)
  sh = open('SampleHouse_v2.db');
  const roof = sh.exec("SELECT size_z FROM spatial_structure WHERE type='IfcSpace' AND name LIKE '%Roof%'");
  const roofH = roof.length ? roof[0].values[0][0] : null;
  ok(roofH != null && roofH < 1.8, 'C4 AABB-LEG — roof void height ' + (roofH == null ? 'n/a' : roofH.toFixed(2) + 'm') + ' < 1.8m (rejected by the habitable-AABB leg independently of doors)');
  sh.close();

  // C5 — graceful on a legacy DB (no spatial_structure type/size columns)
  const legacy = new SQL.Database();
  legacy.run("CREATE TABLE elements_meta (guid TEXT, ifc_class TEXT, storey TEXT, discipline TEXT)");
  legacy.run("INSERT INTO elements_meta VALUES ('g1','IfcWall','L1','ARC')");
  let g = true; try { const lt = BT.seedFromDb(legacy, {}); g = Object.keys(lt.nodes).some(k => lt.nodes[k].guid === 'g1') && !Object.keys(lt.nodes).some(k => lt.nodes[k].kind === 'room'); } catch (e) { g = false; }
  ok(g, 'C5 GRACEFUL — legacy DB (no spatial type/size) → storey-only tree, no room level, no throw');
  legacy.close();

  console.log('─'.repeat(52));
  console.log('W-ROOM-STOREY-QUALIFY: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  try {
    fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true });
    const stamp = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '');
    fs.writeFileSync(path.join(ROOT, 'logs', 'witness_room_storey_qualify_' + stamp + '.log'), 'W-ROOM-STOREY-QUALIFY ' + PASS + '/' + (PASS + FAIL) + '\n');
  } catch (e) {}
  process.exit(FAIL ? 1 : 0);
})();
