'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: W-ROOM-WELLFORMED — falsifiers for the two smells the user saw visually on HHS
// (ROOM_INJECTION_HYBRID.md §7), proven on ALL synthetic-room buildings, not just HHS:
//   W1 tag integrity: every compiled room row is one of the 5 known predefined_types and its
//      name mark matches ('⚠ ' iff SUSPECT_*, '≈ ' otherwise).
//   W2 containment purity: rel_contained_in_space never references a SUSPECT_* room.
//   W3 (smell B falsifier — "floor crosses through a wall"): re-rasterize each storey's walls
//      INDEPENDENTLY (own grid code, not the walker's) and assert ZERO raw wall cells inside any
//      non-SUSPECT room rect. This is exactly the defect the user saw; it cannot re-form silently.
//   W4 (smell A falsifier — "corridor as room" / HHS door-partition collapse): HHS compiles via
//      flood-fill on every level (zero INTERNAL_DOORPART rows), >0 rooms on each of Level 1/2/3.
// Read the log after every run — exit code alone is not evidence.
const fs = require('fs');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const RoomWalker = require('./room_walker.js');

const LIVEWIRE = '/tmp/wt-fable-livewire/modeller';
const SCRATCH = '/tmp/w_room_wellformed';
const BUILDINGS = ['SampleCastle', 'HHS', 'Clinic', 'Garage', 'Hospital', 'Terminal'];
const KNOWN_TYPES = ['INTERNAL', 'INTERNAL_SMALL', 'INTERNAL_DOORPART', 'SUSPECT_NO_DOOR', 'SUSPECT_OPEN'];
const RES = RoomWalker.RES;

function rows(db, sql) {
  const r = db.exec(sql);
  if (!r.length) return [];
  const cols = r[0].columns;
  return r[0].values.map(v => { const o = {}; cols.forEach((c, i) => o[c] = v[i]); return o; });
}

(async () => {
  fs.rmSync(SCRATCH, { recursive: true, force: true });
  fs.mkdirSync(SCRATCH, { recursive: true });
  const SQL = await initSqlJs();
  let pass = 0, fail = 0;
  const ok = (cond, msg) => {
    console.log(`§W-ROOM-WELLFORMED ${cond ? 'PASS' : 'FAIL'} ${msg}`);
    if (cond) pass++; else fail++;
  };

  for (const b of BUILDINGS) {
    const src = `${LIVEWIRE}/${b}_ARC.db`;
    if (!fs.existsSync(src)) { console.log(`§W-ROOM-WELLFORMED SKIP ${b} — source not local`); continue; }
    const dbPath = `${SCRATCH}/${b}.db`;
    fs.copyFileSync(src, dbPath);
    const db = new SQL.Database(new Uint8Array(fs.readFileSync(dbPath)));
    const result = RoomWalker.walk(db, { write: true });

    const roomRows = rows(db, "SELECT guid, name, parent_guid, predefined_type pt, " +
      "center_x cx, center_y cy, size_x sx, size_y sy FROM spatial_structure " +
      "WHERE type='IfcSpace' AND guid LIKE 'RM\\_%' ESCAPE '\\'");
    const storeyName = {};
    rows(db, "SELECT guid, name FROM spatial_structure WHERE type='IfcBuildingStorey'")
      .forEach(r => { storeyName[r.guid] = r.name; });

    // W1 — tag integrity
    const badType = roomRows.filter(r => KNOWN_TYPES.indexOf(r.pt) < 0);
    const badMark = roomRows.filter(r => {
      const isSuspect = r.pt.indexOf('SUSPECT_') === 0;
      return isSuspect ? r.name.indexOf('⚠ ') !== 0 : r.name.indexOf('≈ ') !== 0;
    });
    const nSuspect = roomRows.filter(r => r.pt.indexOf('SUSPECT_') === 0).length;
    ok(badType.length === 0 && badMark.length === 0,
      `${b} W1 tags: ${roomRows.length} rooms (${nSuspect} suspect), badType=${badType.length} badMark=${badMark.length}`);

    // W2 — containment purity: no rel row points at a SUSPECT room
    const suspectGuids = new Set(roomRows.filter(r => r.pt.indexOf('SUSPECT_') === 0).map(r => r.guid));
    const relRows = rows(db, "SELECT DISTINCT space_guid g FROM rel_contained_in_space WHERE space_guid LIKE 'RM\\_%' ESCAPE '\\'");
    const relSuspect = relRows.filter(r => suspectGuids.has(r.g));
    ok(relSuspect.length === 0, `${b} W2 containment: ${relRows.length} spaces referenced, suspectRefs=${relSuspect.length}`);

    // W3 — smell B falsifier: independent raster, zero raw wall cells inside any non-SUSPECT rect
    const ds = RoomWalker.doorStats(db);
    const vertMin = ds.h > 0 ? RoomWalker.VERT_FACTOR * ds.h : 0.0;
    const anchors = RoomWalker.storeyZAnchors(db);
    const wallsBy = RoomWalker.storeyWalls(db, vertMin, anchors);
    let checked = 0, crossings = 0;
    const byStorey = {};
    roomRows.forEach(r => {
      if (r.pt.indexOf('SUSPECT_') === 0) return; // suspects are review candidates, not trusted rects
      const st = storeyName[r.parent_guid];
      if (st === undefined) return;
      (byStorey[st] = byStorey[st] || []).push(r);
    });
    Object.keys(byStorey).forEach(st => {
      const ws = wallsBy[st];
      if (!ws || ws.length < 3) return;
      // independent grid (same extent formula, own rasterization loop — the oracle)
      let xs0 = Infinity, xs1 = -Infinity, ys0 = Infinity, ys1 = -Infinity;
      ws.forEach(w => {
        xs0 = Math.min(xs0, w[0] - w[3] / 2); xs1 = Math.max(xs1, w[0] + w[3] / 2);
        ys0 = Math.min(ys0, w[1] - w[4] / 2); ys1 = Math.max(ys1, w[1] + w[4] / 2);
      });
      const pad = RES * 2; xs0 -= pad; ys0 -= pad; xs1 += pad; ys1 += pad;
      const nx = Math.max(4, Math.ceil((xs1 - xs0) / RES));
      const ny = Math.max(4, Math.ceil((ys1 - ys0) / RES));
      const clampI = v => Math.min(nx - 1, Math.max(0, v));
      const clampJ = v => Math.min(ny - 1, Math.max(0, v));
      const raw = new Uint8Array(nx * ny);
      ws.forEach(w => {
        const i0 = clampI(Math.floor((w[0] - w[3] / 2 - xs0) / RES)), i1 = clampI(Math.floor((w[0] + w[3] / 2 - xs0) / RES));
        const j0 = clampJ(Math.floor((w[1] - w[4] / 2 - ys0) / RES)), j1 = clampJ(Math.floor((w[1] + w[4] / 2 - ys0) / RES));
        for (let i = i0; i <= i1; i++) for (let j = j0; j <= j1; j++) raw[i * ny + j] = 1;
      });
      byStorey[st].forEach(r => {
        const i0 = Math.round((r.cx - r.sx / 2 - xs0) / RES), i1 = Math.round((r.cx + r.sx / 2 - xs0) / RES) - 1;
        const j0 = Math.round((r.cy - r.sy / 2 - ys0) / RES), j1 = Math.round((r.cy + r.sy / 2 - ys0) / RES) - 1;
        let hit = 0;
        for (let i = Math.max(0, i0); i <= Math.min(nx - 1, i1); i++)
          for (let j = Math.max(0, j0); j <= Math.min(ny - 1, j1); j++)
            if (raw[i * ny + j]) hit++;
        checked++;
        if (hit > 0) {
          crossings++;
          console.log(`  wall-crossing: ${r.guid} rect ${r.sx.toFixed(1)}x${r.sy.toFixed(1)} @(${r.cx.toFixed(1)},${r.cy.toFixed(1)}) contains ${hit} raw wall cells`);
        }
      });
    });
    ok(crossings === 0, `${b} W3 wall-crossing: ${checked} non-suspect rects checked, crossings=${crossings}`);

    // W4 — HHS-specific corridor falsifier
    if (b === 'HHS') {
      const dpRows = roomRows.filter(r => r.pt === 'INTERNAL_DOORPART').length;
      const perLevel = {};
      roomRows.forEach(r => {
        const st = storeyName[r.parent_guid];
        perLevel[st] = (perLevel[st] || 0) + 1;
      });
      const levelsOk = ['Level 1', 'Level 2', 'Level 3'].every(l => (perLevel[l] || 0) > 0);
      ok(dpRows === 0 && levelsOk,
        `HHS W4 corridor-collapse: doorPartitionRows=${dpRows} perLevel=${JSON.stringify(perLevel)}`);
    }
    db.close();
  }

  console.log(`§W-ROOM-WELLFORMED SUMMARY pass=${pass} fail=${fail}`);
  if (fail > 0) process.exit(1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
