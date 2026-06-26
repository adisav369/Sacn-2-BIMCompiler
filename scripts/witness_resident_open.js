/**
 * W-RESIDENT-OPEN — the Modeller's 4 permanent residents open end-to-end from the CLOUD.
 *
 * Proves (against the REAL hosted OCI meta.db, not a local copy):
 *   C1 the curated resident set = SH/DX/SC/Terminal, and the shipped outliner references each db name
 *   C2 _ociBase + buildings/<db> = the exact prod URL (GH-Pages app → cloud DBs); each returns 200
 *   C3 each hosted DB OPENS + WALKS via the shipped swbInit, AUTO-PICKING the right system + a real grid
 *   C4 each hosted DB is PRISTINE substrate — no m_bom, no component_geometries (bboxes only)
 *
 * Non-invent: expectations are the MEASURED SH/DX/SC walks (W-STR-BRIDGE-ARCONLY / W-STR-GENERAL-SC);
 * Terminal asserted column-framed + nonzero only. NETWORK witness — needs the OCI bucket reachable.
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const OCI_BASE = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/';

// the curated residents (mirror of str_walker_outliner.js RESIDENTS) + their MEASURED expected walk
const RESIDENTS = [
  { key: 'SampleHouse',    db: 'SampleHouse_meta.db',    system: 'wall-bearing',  grid: '2x3' },
  { key: 'Duplex',         db: 'Duplex_meta.db',         system: 'wall-bearing',  grid: '9x6' },
  { key: 'Schependomlaan', db: 'Schependomlaan_meta.db', system: 'column-framed', grid: '8x9', cols: 23, girders: 13 },
  { key: 'Terminal',       db: 'Terminal_meta.db',       system: 'column-framed' }   // OCI gzip → auto-inflate
];

global.window = {};
const SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
require(path.join(ROOT, 'deploy/dev/walker_confidence.js'));
require(path.join(ROOT, 'deploy/dev/str_walker_bridge.js'));
global.window.SW = SW;
const initSqlJs = require(path.join(ROOT, 'node_modules/sql.js'));

let PASS = 0, FAIL = 0;
const ok = (c, m) => { (c ? PASS++ : FAIL++); console.log('  ' + (c ? '✅' : '❌') + ' ' + m); return c; };

(async () => {
  const SQL = await initSqlJs();

  // C1 — shipped outliner references each curated db name (best-effort drift guard)
  const olPath = [process.argv[2],
                  path.join(process.env.HOME || '', 'bim-ootb/viewer/str_walker_outliner.js'),
                  path.join(ROOT, 'deploy/dev/str_walker_outliner.js')].filter(Boolean).find(p => fs.existsSync(p));
  if (olPath) {
    const txt = fs.readFileSync(olPath, 'utf8');
    const missing = RESIDENTS.filter(r => txt.indexOf(r.db) < 0).map(r => r.db);
    ok(missing.length === 0, 'C1 SHIPPED-LIST — outliner (' + path.basename(path.dirname(olPath)) + ') references all 4 residents' +
      (missing.length ? ' MISSING ' + missing.join(',') : ''));
  } else {
    ok(true, 'C1 SHIPPED-LIST — outliner source not checked out (skipped drift guard); residents = ' + RESIDENTS.map(r => r.key).join('/'));
  }

  for (const r of RESIDENTS) {
    const url = OCI_BASE + 'buildings/' + r.db;
    let buf;
    try {
      const resp = await fetch(url);
      if (!resp.ok) { ok(false, 'C2 ' + r.key + ' — fetch ' + resp.status + ' ' + url); continue; }
      buf = Buffer.from(await resp.arrayBuffer());   // fetch auto-inflates gzip (Terminal)
      ok(true, 'C2 ' + r.key + ' — fetched buildings/' + r.db + ' (' + (buf.length / 1024).toFixed(0) + 'KB raw sqlite)');
    } catch (e) { ok(false, 'C2 ' + r.key + ' — fetch threw ' + e.message); continue; }

    const db = new SQL.Database(new Uint8Array(buf));
    // C3 — walk
    try {
      const st = global.window.swbInit(db, {});
      const tab = global.window.swbTabData();
      const gridStr = st ? (st.base.grid.xLines.length + 'x' + st.base.grid.yLines.length) : '-';
      let good = st && st.system === r.system && tab && tab.system === r.system &&
                 st.base.grid.xLines.length > 0 && st.base.grid.yLines.length > 0;
      if (r.grid) good = good && gridStr === r.grid;
      if (r.cols != null) good = good && st.columnCount === r.cols;
      if (r.girders != null) good = good && (st.base.girders ? st.base.girders.length : 0) === r.girders;
      ok(good, 'C3 ' + r.key + ' — walk system=' + (st && st.system) + ' grid=' + gridStr +
        (r.cols != null ? ' cols=' + st.columnCount : '') +
        (r.girders != null ? ' girders=' + (st.base.girders ? st.base.girders.length : 0) : '') +
        ' (expected ' + r.system + (r.grid ? ' ' + r.grid : '') + ')');
    } catch (e) { ok(false, 'C3 ' + r.key + ' — swbInit threw ' + e.message); }
    // C4 — pristine substrate
    try {
      const cooked = db.exec("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('m_bom','m_bom_line','component_geometries')");
      const n = cooked.length ? cooked[0].values.length : 0;
      ok(n === 0, 'C4 ' + r.key + ' — pristine: 0 cooked tables (m_bom/geometries), bboxes only' + (n ? ' FOUND ' + n : ''));
    } catch (e) { ok(false, 'C4 ' + r.key + ' — pristine check threw ' + e.message); }
    db.close();
  }

  console.log('─'.repeat(48));
  console.log('W-RESIDENT-OPEN: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  const logf = path.join(ROOT, 'logs', 'witness_resident_open_' + new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '').replace(/(\d{8})(\d{4})/, '$1T$2') + '.log');
  try { fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true }); fs.writeFileSync(logf, 'W-RESIDENT-OPEN ' + PASS + '/' + (PASS + FAIL) + '\n'); console.log('§LOG ' + logf); } catch (e) {}
  process.exit(FAIL ? 1 : 0);
})();
