'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: W-ROOM008-TERMINAL — proves ROOM008_Terminal_correction_43_to_53.sql applies cleanly to a
// real main Terminal_ARC.db and reproduces EXACTLY what build/room_walker.js computes fresh from
// the same source geometry right now (not a frozen /tmp snapshot — the algorithm itself is the
// ground truth here, since this migration IS a re-run of it). Read the log after every run.
const fs = require('fs');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const RoomWalker = require('/home/red1/bim-compiler/build/room_walker.js');

const MAIN_TERMINAL = '/home/red1/bim-ootb/modeller/Terminal_ARC.db';
const SCRIPT = __dirname + '/ROOM008_Terminal_correction_43_to_53.sql';
const SCRATCH = '/tmp/w_room008_terminal';

function dump(db, table, order) {
  const r = db.exec(`SELECT * FROM ${table} ORDER BY ${order}`);
  return r.length ? r[0].values.map(v => v.join('|')) : [];
}

(async () => {
  if (!fs.existsSync(MAIN_TERMINAL)) { console.log('§W-ROOM008-TERMINAL SKIP — ' + MAIN_TERMINAL + ' not present locally'); return; }
  fs.rmSync(SCRATCH, { recursive: true, force: true });
  fs.mkdirSync(SCRATCH, { recursive: true });
  const SQL = await initSqlJs();
  let pass = 0, fail = 0;
  function check(name, cond, detail) {
    if (cond) { pass++; console.log('§W-ROOM008-TERMINAL PASS ' + name + (detail ? ' ' + detail : '')); }
    else { fail++; console.log('§W-ROOM008-TERMINAL FAIL ' + name + (detail ? ' ' + detail : '')); }
  }

  // A: apply the migration script to a fresh copy of the real main Terminal_ARC.db
  const appliedPath = SCRATCH + '/applied.db';
  fs.copyFileSync(MAIN_TERMINAL, appliedPath);
  const { execFileSync } = require('child_process');
  execFileSync('sqlite3', [appliedPath], { input: fs.readFileSync(SCRIPT) });
  const appliedDb = new SQL.Database(new Uint8Array(fs.readFileSync(appliedPath)));

  // B: run RoomWalker.walk({write:true}) fresh against a SEPARATE copy of the same real geometry
  const freshPath = SCRATCH + '/fresh.db';
  fs.copyFileSync(MAIN_TERMINAL, freshPath);
  const freshDb = new SQL.Database(new Uint8Array(fs.readFileSync(freshPath)));
  const result = RoomWalker.walk(freshDb, { write: true });

  check('counts-match', result.total === 53 && result.doorRescuedTotal === 10,
    'total=' + result.total + ' door_rescued=' + result.doorRescuedTotal);

  const appliedSS = dump(appliedDb, 'spatial_structure', 'guid');
  const freshSS = dump(freshDb, 'spatial_structure', 'guid');
  check('spatial_structure-byte-identical', JSON.stringify(appliedSS) === JSON.stringify(freshSS),
    'applied=' + appliedSS.length + ' fresh=' + freshSS.length);

  const appliedRel = dump(appliedDb, 'rel_contained_in_space', 'space_guid,element_guid');
  const freshRel = dump(freshDb, 'rel_contained_in_space', 'space_guid,element_guid');
  check('rel_contained_in_space-byte-identical', JSON.stringify(appliedRel) === JSON.stringify(freshRel),
    'applied=' + appliedRel.length + ' fresh=' + freshRel.length);

  // Sanity: the migration didn't touch anything outside spatial_structure/rel_contained_in_space
  // (e.g. elements_meta row count, the source geometry the algorithm re-derives everything from).
  const emCountApplied = appliedDb.exec('SELECT COUNT(*) FROM elements_meta')[0].values[0][0];
  const emCountOriginal = new SQL.Database(new Uint8Array(fs.readFileSync(MAIN_TERMINAL))).exec('SELECT COUNT(*) FROM elements_meta')[0].values[0][0];
  check('elements_meta-untouched', emCountApplied === emCountOriginal, 'applied=' + emCountApplied + ' original=' + emCountOriginal);

  console.log('§W-ROOM008-TERMINAL SUMMARY pass=' + pass + ' fail=' + fail);
  if (fail > 0) process.exit(1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
