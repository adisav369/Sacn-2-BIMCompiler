/**
 * W-RESIDENT-OPEN — the Modeller's 4 permanent residents open end-to-end from their real hosting.
 *
 * ⚠ CORRECTED 2026-07-10 (RESUME_MODELLER_WALK_SUBSTRATE.md "RESUME HERE" item 1): the ORIGINAL
 * version of this witness tested an OBSOLETE architecture — OCI `buildings/<X>_meta.db` fetch,
 * including a resident named "Schependomlaan". That was the task-2.5 (2026-06-26) design, superseded
 * THE SAME DAY by the "SESSION 2026-06-26b" isolation decision: the Modeller is its OWN GH-Pages
 * playground (`bim-ootb/modeller/<X>.db`, same-dir `fetch`, ZERO OCI — §101 Drift Law), and
 * "Schependomlaan" was renamed/replaced by "SampleCastle" (same source building, better re-extraction
 * — see memory feedback_modeller_gh_vs_viewer_oci_data.md 2026-07-08 pass). The stale witness kept
 * hitting OCI for objects that legitimately stopped being the live path, so when those 3 OCI objects
 * later went missing (unrelated bucket drift) it read as "3/4 residents dead in prod" — a FALSE ALARM:
 * verified live (curl, 2026-07-10) that all 4 real GH-Pages URLs return 200 and `str_walker_outliner.js`
 * `openResident()` fetches `_modellerBase() + res.db` (relative, same-dir), never OCI_BASE. Rewritten to
 * test the CURRENT real path instead of the retired one.
 *
 * Proves (against the REAL hosted GH-Pages files, not a local copy):
 *   C1 the curated resident set = SH/DX/SC/Terminal, and the shipped outliner references each db name
 *   C2 GH_BASE + <db> = the exact prod URL (bim-ootb/modeller/, GH Pages same-dir as modeller.html); each returns 200
 *   C3 each hosted DB OPENS + WALKS via the shipped swbInit, AUTO-PICKING the right system + a real grid
 *   C4 each hosted DB carries real substrate (elements_meta non-empty) — NOT a pristine-only check: these
 *      residents are the isolation-era `_extracted.db` bundles, which legitimately carry cooked
 *      tables (m_bom/component_geometries) by design (self-contained, no separate mesh fetch needed
 *      for small buildings) — asserting "0 cooked tables" here would itself be testing the wrong thing.
 *
 * Non-invent: expectations are the MEASURED SH/DX/SC walks (W-STR-BRIDGE-ARCONLY / W-STR-GENERAL-SC);
 * Terminal asserted column-framed + nonzero only. NETWORK witness — needs GH Pages reachable.
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const GH_BASE = 'https://red1oon.github.io/bim-ootb/modeller/';

// the curated residents (mirror of bim-ootb/modeller/str_walker_outliner.js RESIDENTS, core 4 only —
// excludes the SampleCastle-ARC diagnostic + Ifc4_Revit reference entries) + their MEASURED expected walk.
// ⚠ ALL FOUR are 'wall-bearing' as of bim-ootb PR #712 (2026-07-08, "strip all 4 residents to ARC-only"):
// SampleCastle/Terminal used to carry real STR/MEP rows (column-framed) BEFORE that commit deliberately
// cascade-deleted every non-ARC discipline row from the Modeller's own copies (dev/user split doctrine —
// users walk ARC-only with NO oracle; STR/MEP get DERIVED live by the walker, not served pre-baked). A
// prior version of this witness still expected 'column-framed' for a 23-column building here (based on
// the RETIRED "Schependomlaan" resident, and, separately, on SampleCastle's PRE-#712 content) — grid
// values below are what the CURRENT (post-#712) ARC-only substrate actually derives, confirmed by
// direct query (SampleCastle_extracted.db discipline breakdown = 100% ARC, 0 STR/MEP rows).
const RESIDENTS = [
  { key: 'SampleHouse',   db: 'SampleHouse_extracted.db',   system: 'wall-bearing',  grid: '2x3' },
  { key: 'Duplex',        db: 'Duplex_extracted.db',        system: 'wall-bearing',  grid: '9x6' },
  { key: 'SampleCastle',  db: 'SampleCastle_extracted.db',  system: 'wall-bearing',  grid: '13x12' },
  { key: 'Terminal',      db: 'Terminal_meta.db',           system: 'wall-bearing',  grid: '38x32' }
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
                  path.join(process.env.HOME || '', 'bim-ootb/modeller/str_walker_outliner.js'),
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
    const url = GH_BASE + r.db;
    let buf;
    try {
      const resp = await fetch(url);
      if (!resp.ok) { ok(false, 'C2 ' + r.key + ' — fetch ' + resp.status + ' ' + url); continue; }
      buf = Buffer.from(await resp.arrayBuffer());   // fetch auto-inflates gzip (Terminal)
      ok(true, 'C2 ' + r.key + ' — fetched modeller/' + r.db + ' (' + (buf.length / 1024).toFixed(0) + 'KB raw sqlite)');
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
    // C4 — real substrate present (NOT a pristine/cooked-table check — see header comment: these
    // isolation-era _extracted.db residents legitimately carry m_bom/component_geometries by design)
    try {
      const cnt = db.exec("SELECT count(*) FROM elements_meta");
      const n = cnt.length ? cnt[0].values[0][0] : 0;
      ok(n > 0, 'C4 ' + r.key + ' — real substrate: elements_meta=' + n);
    } catch (e) { ok(false, 'C4 ' + r.key + ' — substrate check threw ' + e.message); }
    db.close();
  }

  console.log('─'.repeat(48));
  console.log('W-RESIDENT-OPEN: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  const logf = path.join(ROOT, 'logs', 'witness_resident_open_' + new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '').replace(/(\d{8})(\d{4})/, '$1T$2') + '.log');
  try { fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true }); fs.writeFileSync(logf, 'W-RESIDENT-OPEN ' + PASS + '/' + (PASS + FAIL) + '\n'); console.log('§LOG ' + logf); } catch (e) {}
  process.exit(FAIL ? 1 : 0);
})();
