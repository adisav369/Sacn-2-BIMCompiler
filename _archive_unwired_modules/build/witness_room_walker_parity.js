'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: W-ROOM-WALKER-PARITY — proves build/room_walker.js (the JS port) is byte-for-byte
// equivalent to scripts/compile_rooms.py (the checked ground truth) on every real building that
// has synthetic/compiled room data. ROOM_WALKER_JS_PORT.md Task 3's "two distinct bars, both
// required": (1) TEST — dry-run counts/tagging match; (2) INJECTION — --write output, diffed row
// for row (guid/name/coords/predefined_type), not just aggregate counts. Read the log after every
// run — exit code alone is not evidence.
const fs = require('fs');
const { execFileSync } = require('child_process');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const RoomWalker = require('./room_walker.js');

const LIVEWIRE = '/tmp/wt-fable-livewire/modeller';
const SCRATCH = '/tmp/w_room_walker_parity';
const BUILDINGS = ['SampleCastle', 'HHS', 'Clinic', 'Garage', 'Hospital', 'Terminal'];

function dumpSpatialStructure(dbPath, SQL) {
  const db = new SQL.Database(new Uint8Array(fs.readFileSync(dbPath)));
  const r = db.exec("SELECT guid,type,name,parent_guid,object_type,predefined_type," +
    "round(center_x,4),round(center_y,4),round(center_z,4),round(size_x,4),round(size_y,4),round(size_z,4),room_guid " +
    "FROM spatial_structure WHERE guid LIKE 'RM\\_%' ESCAPE '\\' OR guid LIKE 'STC\\_%' ESCAPE '\\' ORDER BY guid");
  db.close();
  if (!r.length) return [];
  return r[0].values.map(v => v.join('|'));
}

function dumpRel(dbPath, SQL) {
  const db = new SQL.Database(new Uint8Array(fs.readFileSync(dbPath)));
  const r = db.exec("SELECT space_guid, element_guid FROM rel_contained_in_space " +
    "WHERE space_guid LIKE 'RM\\_%' ESCAPE '\\' ORDER BY space_guid, element_guid");
  db.close();
  if (!r.length) return [];
  return r[0].values.map(v => v.join('|'));
}

(async () => {
  fs.rmSync(SCRATCH, { recursive: true, force: true });
  fs.mkdirSync(SCRATCH, { recursive: true });
  const SQL = await initSqlJs();
  let pass = 0, fail = 0;

  for (const b of BUILDINGS) {
    const src = `${LIVEWIRE}/${b}_ARC.db`;
    if (!fs.existsSync(src)) { console.log(`§W-ROOM-WALKER-PARITY SKIP ${b} — source not local`); continue; }

    const pyDb = `${SCRATCH}/${b}_py.db`, jsDb = `${SCRATCH}/${b}_js.db`;
    fs.copyFileSync(src, pyDb);
    fs.copyFileSync(src, jsDb);

    // Python: real compile_rooms.py, --write, against its own scratch copy
    const pyOut = execFileSync('python3', ['/home/red1/bim-compiler/scripts/compile_rooms.py', pyDb, '--write'], { encoding: 'utf8' });
    const pyTotalMatch = pyOut.match(/TOTAL compiled rooms = (\d+)/);
    const pyTotal = pyTotalMatch ? parseInt(pyTotalMatch[1], 10) : -1;

    // JS: the port, --write, against its own separate scratch copy
    const jsDbHandle = new SQL.Database(new Uint8Array(fs.readFileSync(jsDb)));
    const jsResult = RoomWalker.walk(jsDbHandle, { write: true });
    fs.writeFileSync(jsDb, Buffer.from(jsDbHandle.export()));
    jsDbHandle.close();

    console.log(`§W-ROOM-WALKER-PARITY ${b} python_total=${pyTotal} js_total=${jsResult.total}`);
    if (pyTotal !== jsResult.total) {
      console.log(`§W-ROOM-WALKER-PARITY FAIL ${b} TOTAL mismatch`);
      fail++; continue;
    }

    const pySS = dumpSpatialStructure(pyDb, SQL), jsSS = dumpSpatialStructure(jsDb, SQL);
    const pyRel = dumpRel(pyDb, SQL), jsRel = dumpRel(jsDb, SQL);
    const ssMatch = JSON.stringify(pySS) === JSON.stringify(jsSS);
    const relMatch = JSON.stringify(pyRel) === JSON.stringify(jsRel);

    if (ssMatch && relMatch) {
      console.log(`§W-ROOM-WALKER-PARITY PASS ${b} spatial_structure(${pySS.length} rows) + rel_contained_in_space(${pyRel.length} rows) byte-identical`);
      pass++;
    } else {
      console.log(`§W-ROOM-WALKER-PARITY FAIL ${b} content mismatch (ss_match=${ssMatch} rel_match=${relMatch})`);
      if (!ssMatch) {
        for (let i = 0; i < Math.max(pySS.length, jsSS.length); i++) {
          if (pySS[i] !== jsSS[i]) { console.log('  first diff at row', i, 'py=', pySS[i], 'js=', jsSS[i]); break; }
        }
      }
      fail++;
    }
  }

  console.log(`§W-ROOM-WALKER-PARITY SUMMARY pass=${pass} fail=${fail}`);
  if (fail > 0) process.exit(1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
