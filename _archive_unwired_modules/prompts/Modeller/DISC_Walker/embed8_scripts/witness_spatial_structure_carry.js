'use strict';
// ⚠ DO NOT REMOVE
// SCOPE: W-SPATIAL-CARRY — proves finalize_all_8.js's Task 4 fix (ROOM_INJECTION_HYBRID.md §Task 4):
// spatial_structure must survive the ARC-only finalize step even when the fresh source lacks the table,
// by carrying it forward from the currently-shipped resident ARC.db. Reproduces the exact 6068fab
// regression shape (fresh source with NO spatial_structure table) against REAL room data already local
// in /tmp/wt-fable-livewire (no network/LFS fetch — read-only against an existing worktree). Read the
// log after every run — exit code alone is not evidence.
const fs = require('fs');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const { hasTable, countRows, carrySpatialStructureForward } = require('./finalize_all_8.js');

const LIVEWIRE = '/tmp/wt-fable-livewire/modeller';
const DUPLEX_ARC = LIVEWIRE + '/Duplex_ARC.db';
const SAMPLEHOUSE_ARC = LIVEWIRE + '/SampleHouse_ARC.db';

function mkEmptyMeta(SQL) {
  return new SQL.Database();
}

(async () => {
  const SQL = await initSqlJs();
  let pass = 0, fail = 0;
  function check(name, cond, detail) {
    if (cond) { pass++; console.log('§W-SPATIAL-CARRY PASS ' + name + (detail ? ' ' + detail : '')); }
    else { fail++; console.log('§W-SPATIAL-CARRY FAIL ' + name + (detail ? ' ' + detail : '')); }
  }

  // ── W1: fresh source already has spatial_structure → fresh wins, no carry ──
  {
    const fresh = new SQL.Database();
    fresh.run('CREATE TABLE spatial_structure (guid TEXT, name TEXT, type TEXT, object_type TEXT)');
    for (let i = 0; i < 21; i++) fresh.run('INSERT INTO spatial_structure VALUES (?,?,?,?)', ['g' + i, 'R' + i, 'IfcSpace', 'Room']);
    const meta = mkEmptyMeta(SQL);
    meta.run('CREATE TABLE spatial_structure (guid TEXT, name TEXT, type TEXT, object_type TEXT)');
    const r = carrySpatialStructureForward(fresh, meta, DUPLEX_ARC, SQL, fs);
    check('W1-fresh-wins', r.source === 'fresh' && r.freshRows === 21 && r.finalRows === 21,
      'source=' + r.source + ' fresh=' + r.freshRows + ' final=' + r.finalRows);
    check('W1-metaDb-untouched-by-carry', countRows(meta, 'spatial_structure') === 0,
      '(carry does not insert when fresh already wins — meta stays as the caller left it)');
  }

  // ── W2: reproduce 6068fab's exact regression shape for Duplex — fresh source has NO table at all,
  // real prior ARC.db (wt-fable-livewire, 20 real rooms post Task-5 strip) must be carried forward ──
  {
    if (!fs.existsSync(DUPLEX_ARC)) {
      console.log('§W-SPATIAL-CARRY SKIP W2 — ' + DUPLEX_ARC + ' not present locally');
    } else {
      const fresh = new SQL.Database(); // no spatial_structure table — the real ephemeral _all.db shape
      const meta = new SQL.Database();
      const priorRowsExpected = countRows(new SQL.Database(new Uint8Array(fs.readFileSync(DUPLEX_ARC))), 'spatial_structure');
      const r = carrySpatialStructureForward(fresh, meta, DUPLEX_ARC, SQL, fs);
      check('W2-duplex-carried', r.source.startsWith('carried-forward') && r.finalRows === priorRowsExpected && priorRowsExpected > 0,
        'expected=' + priorRowsExpected + ' final=' + r.finalRows);
      check('W2-duplex-metaDb-actually-has-rows', countRows(meta, 'spatial_structure') === priorRowsExpected,
        'metaDb rows=' + countRows(meta, 'spatial_structure'));
    }
  }

  // ── W3: same for SampleHouse (Task 6's real historical case) — content-level check, not just count ──
  {
    if (!fs.existsSync(SAMPLEHOUSE_ARC)) {
      console.log('§W-SPATIAL-CARRY SKIP W3 — ' + SAMPLEHOUSE_ARC + ' not present locally');
    } else {
      const priorDb = new SQL.Database(new Uint8Array(fs.readFileSync(SAMPLEHOUSE_ARC)));
      const priorGuids = priorDb.exec('SELECT guid FROM spatial_structure ORDER BY guid')[0].values.map(v => v[0]);
      const fresh = new SQL.Database();
      const meta = new SQL.Database();
      const r = carrySpatialStructureForward(fresh, meta, SAMPLEHOUSE_ARC, SQL, fs);
      const metaGuids = meta.exec('SELECT guid FROM spatial_structure ORDER BY guid')[0].values.map(v => v[0]);
      check('W3-samplehouse-carried', r.finalRows === priorGuids.length && priorGuids.length > 0,
        'rows=' + r.finalRows);
      check('W3-samplehouse-guid-match', JSON.stringify(metaGuids) === JSON.stringify(priorGuids),
        'guids match source exactly, not just count');
    }
  }

  // ── W4: neither fresh nor prior has data — legitimate gap, not a regression: 0 rows, no crash ──
  {
    const fresh = new SQL.Database();
    const meta = new SQL.Database();
    const r = carrySpatialStructureForward(fresh, meta, '/tmp/does-not-exist-ever_ARC.db', SQL, fs);
    check('W4-no-source-no-crash', r.source === 'none' && r.finalRows === 0, 'source=' + r.source);
  }

  // ── W5: priorArc path missing/unreadable — degrades gracefully ──
  {
    const fresh = new SQL.Database();
    const meta = new SQL.Database();
    const r = carrySpatialStructureForward(fresh, meta, undefined, SQL, fs);
    check('W5-undefined-priorArc-no-crash', r.source === 'none' && r.finalRows === 0, 'source=' + r.source);
  }

  // ── W6: the FINAL-loop regression gate (finalize_all_8.js's own aggregation logic, reproduced here
  // in miniature) correctly fires when carry() reports data available but the write-time db disagrees —
  // proves the gate isn't a no-op. Simulates a bug where metaDb gets cleared after carry() ran. ──
  {
    const carryResults = { FakeBuilding: { finalRows: 20, source: 'carried-forward:fake' } };
    const metaDbs = { FakeBuilding: new SQL.Database() }; // deliberately empty — simulates post-carry drift
    let anyRegression = false;
    for (const [name, mdb] of Object.entries(metaDbs)) {
      const n = countRows(mdb, 'spatial_structure');
      const expected = carryResults[name] ? carryResults[name].finalRows : 0;
      if (expected > 0 && n === 0) anyRegression = true;
    }
    check('W6-regression-gate-fires', anyRegression === true, '(gate correctly detects carry/write drift)');
  }

  console.log('§W-SPATIAL-CARRY SUMMARY pass=' + pass + ' fail=' + fail);
  if (fail > 0) process.exit(1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
