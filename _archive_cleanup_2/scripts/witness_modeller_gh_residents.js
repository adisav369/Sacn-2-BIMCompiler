/**
 * W-MODELLER-GH-RESIDENTS — the Modeller's 4 residents open from its OWN ISOLATED GH playground.
 *
 * Issue proven: the modeller was still fetching residents from the VIEWER's OCI bucket (buildings/<X>_meta.db)
 * even after #541 committed the building DBs to the repo-root modeller/ dir → split brain, isolation hole
 * (§SESSION 2026-06-26b: "it was drifting bad last session, so dont drift"). This witness proves the loader
 * now reads ONLY from ../modeller/ (GH Pages, zero OCI), the resident list is SH/DX/SC/Terminal (SampleCastle
 * NOT Schependomlaan), each DB physically present opens + WALKS to its MEASURED system/grid, and the 250MB
 * Terminal_geo mesh DB is Git-LFS-tracked (the user's "do LFS for Terminal to GH" — full GH self-containment).
 *
 *   C1 shipped loader lists all 4 residents + uses ../modeller/ (NO _ociBase, NO 'buildings/')
 *   C2 each resident DB physically present in modeller/ at the expected size
 *   C3 each opens + WALKS via the shipped swbInit → MEASURED system/grid/cols/girders (non-invent)
 *   C4 Terminal_geo.db (250MB meshes) is Git-LFS-tracked in .gitattributes (GH 100MB limit → LFS)
 *   C5 Terminal_meta = the pristine bbox WALK substrate (0 cooked tables); SH/DX/SC = committed extracted.db
 *
 * Non-invent: C3 expectations are MEASURED from the actual committed modeller/ DBs (see logs). LOCAL witness
 * (reads the worktree files) — no network; the GH-serving check is a separate live curl after merge.
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const WT = process.argv[2] || '/tmp/wt-modeller-gh';
const MOD = path.join(WT, 'modeller');
const OL = path.join(WT, 'viewer/str_walker_outliner.js');

// the curated residents (mirror of str_walker_outliner.js RESIDENTS) + MEASURED expected walk + size band
// SH/DX = re-extracted with the current extractor (REAL IfcSpace rooms + space AABB + native bbox + SDG
// edges baked) → clean reference DBs, 0 cooked m_bom tables. SC still the legacy synthetic extract (no
// source IFC on disk to re-extract — flagged). Terminal_meta = pristine bbox substrate.
const RESIDENTS = [
  { key: 'SampleHouse',  db: 'SampleHouse_extracted.db',  system: 'wall-bearing',  grid: '2x3',   cols: 0,   girders: 0,   mb: [0.3, 1.5], cooked: 0 },
  { key: 'Duplex',       db: 'Duplex_extracted.db',       system: 'wall-bearing',  grid: '9x6',   cols: 0,   girders: 0,   mb: [0.5, 2],   cooked: 0 },
  { key: 'SampleCastle', db: 'SampleCastle_extracted.db', system: 'column-framed', grid: '7x9',   cols: 23,  girders: 14,  mb: [5, 15],    cooked: 0 },
  { key: 'Terminal',     db: 'Terminal_meta.db',          system: 'column-framed', grid: '18x10', cols: 158, girders: 108, mb: [12, 25],   cooked: 0 }
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

  // C1 — shipped loader lists all 4 + reads from ../modeller/, never OCI
  const txt = fs.readFileSync(OL, 'utf8');
  const missing = RESIDENTS.filter(r => txt.indexOf(r.db) < 0).map(r => r.db);
  ok(missing.length === 0, 'C1a LIST — loader references all 4 residents' + (missing.length ? ' MISSING ' + missing.join(',') : ''));
  const usesMod = /_modellerBase\s*\(\)\s*\{\s*return\s*'\.\.\/modeller\/'/.test(txt) && txt.indexOf("_modellerBase() + res.db") >= 0;
  ok(usesMod, 'C1b ISOLATED — openResident fetches ../modeller/<db> (GH playground)');
  const noOci = txt.indexOf('_ociBase') < 0 && txt.indexOf("'buildings/'") < 0 && txt.indexOf('objectstorage') < 0;
  ok(noOci, 'C1c NO-OCI — zero _ociBase / buildings/ / objectstorage references (drift sealed)');
  ok(txt.indexOf('Schependomlaan') < 0, 'C1d SC-NOT-SCHEP — Schependomlaan replaced by SampleCastle');

  for (const r of RESIDENTS) {
    const fp = path.join(MOD, r.db);
    if (!fs.existsSync(fp)) { ok(false, 'C2 ' + r.key + ' — MISSING ' + r.db + ' in modeller/'); continue; }
    const buf = fs.readFileSync(fp);
    const mb = buf.length / 1024 / 1024;
    ok(mb >= r.mb[0] && mb <= r.mb[1], 'C2 ' + r.key + ' — ' + r.db + ' present ' + mb.toFixed(1) + 'MB (band ' + r.mb[0] + '–' + r.mb[1] + ')');

    const db = new SQL.Database(new Uint8Array(buf));
    try {
      const st = global.window.swbInit(db, {});
      const tab = global.window.swbTabData();
      const grid = st ? (st.base.grid.xLines.length + 'x' + st.base.grid.yLines.length) : '-';
      const gird = st && st.base.girders ? st.base.girders.length : 0;
      const good = st && st.system === r.system && tab && tab.system === r.system &&
                   grid === r.grid && st.columnCount === r.cols && gird === r.girders;
      ok(good, 'C3 ' + r.key + ' — walk system=' + (st && st.system) + ' grid=' + grid + ' cols=' + (st && st.columnCount) + ' gird=' + gird +
        ' (expected ' + r.system + ' ' + r.grid + '/' + r.cols + '/' + r.girders + ')');
    } catch (e) { ok(false, 'C3 ' + r.key + ' — swbInit threw ' + e.message); }

    try {
      const cooked = db.exec("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('m_bom','m_bom_line','component_geometries')");
      const n = cooked.length ? cooked[0].values.length : 0;
      ok(n === r.cooked, 'C5 ' + r.key + ' — ' + (r.cooked === 0 ? 'PRISTINE meta substrate (0 cooked)' : 'committed extracted.db (' + n + ' cooked tables, expected ' + r.cooked + ')'));
    } catch (e) { ok(false, 'C5 ' + r.key + ' — cooked check threw ' + e.message); }
    db.close();
  }

  // C4 — Terminal_geo.db is Git-LFS-tracked (the 250MB mesh DB; GH 100MB → LFS)
  const ga = fs.existsSync(path.join(WT, '.gitattributes')) ? fs.readFileSync(path.join(WT, '.gitattributes'), 'utf8') : '';
  ok(/modeller\/\*_geo\.db\s+filter=lfs/.test(ga), 'C4a LFS-TRACK — .gitattributes tracks modeller/*_geo.db via lfs');
  ok(fs.existsSync(path.join(MOD, 'Terminal_geo.db')), 'C4b GEO-PRESENT — modeller/Terminal_geo.db staged (250MB meshes → LFS)');

  console.log('─'.repeat(52));
  console.log('W-MODELLER-GH-RESIDENTS: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  try {
    fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true });
    const stamp = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '');
    fs.writeFileSync(path.join(ROOT, 'logs', 'witness_modeller_gh_residents_' + stamp + '.log'), 'W-MODELLER-GH-RESIDENTS ' + PASS + '/' + (PASS + FAIL) + '\n');
  } catch (e) {}
  process.exit(FAIL ? 1 : 0);
})();
