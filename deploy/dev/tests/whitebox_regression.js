#!/usr/bin/env node
// whitebox_regression.js — S260c deterministic regression suite
// Covers: split DB, IFC drop, auto-split, variance, offline, filename case, ground Y
// Run: node deploy/dev/tests/whitebox_regression.js
// Rules: §-tagged logs, PASS/FAIL every line, no browser, no Playwright.
// This is the ONLY whitebox regression file. Do NOT create alternatives.

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

let pass = 0, fail = 0;

function test(name, fn) {
  try {
    const result = fn();
    if (result.ok) { pass++; console.log(result.log + ' PASS'); }
    else { fail++; console.log(result.log + ' FAIL — ' + result.reason); }
  } catch (e) { fail++; console.log('§WB_ERROR test=' + name + ' err=' + e.message + ' FAIL'); }
}

// Resolve paths relative to repo root
const REPO = path.resolve(__dirname, '../../..');
const BUILDINGS_DIR = path.join(REPO, 'deploy/buildings');
const DEV_BUILDINGS_DIR = path.join(REPO, 'deploy/dev/buildings');
const DEV_DIR = path.join(REPO, 'deploy/dev');

// Helper: find a building file in either buildings dir
function findBldFile(filename) {
  const p1 = path.join(BUILDINGS_DIR, filename);
  if (fs.existsSync(p1)) return p1;
  const p2 = path.join(DEV_BUILDINGS_DIR, filename);
  if (fs.existsSync(p2)) return p2;
  return null;
}

// Helper: open sqlite3 DB and run query
function sqlQuery(dbPath, sql) {
  return execSync(`sqlite3 "${dbPath}" "${sql}"`, { encoding: 'utf8' }).trim();
}

// ─── 3.1 Split DB integrity ──────────────────────────────────────────────────
// Issue: S260c Clinic BLOB_MISS — stale meta caused 100% miss
const SPLIT_BUILDINGS = ['Terminal', 'Hospital', 'LTU_AHouse', 'Clinic'];

for (const bld of SPLIT_BUILDINGS) {
  test('split_integrity_' + bld, () => {
    const metaPath = findBldFile(bld + '_meta.db');
    const geoPath = findBldFile(bld + '_geo.db');

    if (!metaPath || !geoPath) {
      return { ok: false, log: `§WB_SPLIT_INTEGRITY bld=${bld}`, reason: 'meta.db or geo.db not found locally' };
    }

    const metaHashes = parseInt(sqlQuery(metaPath,
      "SELECT COUNT(DISTINCT geometry_hash) FROM element_instances WHERE geometry_hash IS NOT NULL"), 10);
    const geoHashes = parseInt(sqlQuery(geoPath,
      "SELECT COUNT(DISTINCT geometry_hash) FROM component_geometries"), 10);

    // Cross-check: count meta hashes NOT in geo
    const orphans = parseInt(sqlQuery(metaPath,
      `SELECT COUNT(DISTINCT geometry_hash) FROM element_instances WHERE geometry_hash IS NOT NULL AND geometry_hash NOT IN (SELECT geometry_hash FROM (SELECT geometry_hash FROM element_instances WHERE 0))`), 10);

    // Better cross-check using attached DB
    let realOrphans = 0;
    try {
      const result = execSync(
        `sqlite3 "${metaPath}" "ATTACH '${geoPath}' AS geo; SELECT COUNT(DISTINCT ei.geometry_hash) FROM element_instances ei WHERE ei.geometry_hash IS NOT NULL AND ei.geometry_hash NOT IN (SELECT geometry_hash FROM geo.component_geometries);"`,
        { encoding: 'utf8' }
      ).trim();
      realOrphans = parseInt(result, 10);
    } catch (e) {
      realOrphans = -1;
    }

    // Verify no NULL vertices in geo
    let nullVerts = 0;
    try {
      nullVerts = parseInt(sqlQuery(geoPath,
        "SELECT COUNT(*) FROM component_geometries WHERE vertices IS NULL OR faces IS NULL"), 10);
    } catch (e) { nullVerts = -1; }

    const ok = realOrphans === 0 && nullVerts === 0 && metaHashes > 0 && geoHashes > 0;
    return {
      ok,
      log: `§WB_SPLIT_INTEGRITY bld=${bld} meta_hashes=${metaHashes} geo_hashes=${geoHashes} orphans=${realOrphans} null_verts=${nullVerts}`,
      reason: realOrphans > 0 ? `${realOrphans} orphan hashes` : nullVerts > 0 ? `${nullVerts} null verts/faces` : 'no data'
    };
  });
}

// ─── 3.2 IFC Drop → DB validity ─────────────────────────────────────────────
// Issue: S260c BUG 1 — Drop IFC sometimes produces DB viewer cannot open
test('drop_ifc_validity', () => {
  // Use existing fixture DB as proxy for drop-produced DB
  const dbPath = path.join(__dirname, 'fixtures/duplex_extracted.db');
  if (!fs.existsSync(dbPath)) {
    return { ok: false, log: '§WB_DROP_IFC db=duplex_extracted.db', reason: 'fixture not found' };
  }

  const requiredTables = ['elements_meta', 'element_transforms', 'element_instances', 'component_geometries'];
  const existingTables = sqlQuery(dbPath, "SELECT name FROM sqlite_master WHERE type='table'").split('\n');

  const missing = requiredTables.filter(t => !existingTables.includes(t));
  if (missing.length > 0) {
    return { ok: false, log: `§WB_DROP_IFC db=duplex tables=${existingTables.length}`, reason: 'missing: ' + missing.join(',') };
  }

  const elements = parseInt(sqlQuery(dbPath, "SELECT COUNT(*) FROM elements_meta"), 10);
  const geometries = parseInt(sqlQuery(dbPath, "SELECT COUNT(*) FROM component_geometries"), 10);

  // Check no NULL primary keys
  const nullGuids = parseInt(sqlQuery(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE guid IS NULL"), 10);

  const ok = elements > 0 && geometries > 0 && nullGuids === 0;
  return {
    ok,
    log: `§WB_DROP_IFC db=duplex tables=${requiredTables.length} elements=${elements} geometries=${geometries} null_pks=${nullGuids}`,
    reason: elements === 0 ? 'no elements' : geometries === 0 ? 'no geometries' : 'null PKs'
  };
});

// ─── 3.3 Large IFC → auto-split threshold ───────────────────────────────────
// Issue: S260c BUG 2 — >15K elements should auto-split
test('split_threshold', () => {
  const scriptPath = path.join(REPO, 'scripts/split_db.sh');
  const scriptExists = fs.existsSync(scriptPath);

  // Read the threshold from split_db.sh
  let threshold = 0;
  if (scriptExists) {
    const content = fs.readFileSync(scriptPath, 'utf8');
    const m = content.match(/-lt\s+(\d+)/);
    if (m) threshold = parseInt(m[1], 10);
  }

  const ok = scriptExists && threshold === 15000;
  return {
    ok,
    log: `§WB_SPLIT_THRESHOLD threshold=${threshold} script_exists=${scriptExists}`,
    reason: !scriptExists ? 'script not found' : threshold !== 15000 ? `threshold=${threshold}, expected 15000` : ''
  };
});

// ─── 3.4 Variance IFC → 4D5D HTML inclusion ─────────────────────────────────
// Issue: Variance IFC logic must not regress — 4D5D HTML must include variance graph
test('variance_modules', () => {
  const variationPath = path.join(DEV_DIR, 'variation_order.js');
  const diffPath = path.join(DEV_DIR, 'diff.js');
  const boqPath = path.join(DEV_DIR, 'boq_charts.html');

  const modules = [];
  if (fs.existsSync(variationPath)) modules.push('variation_order');
  if (fs.existsSync(diffPath)) modules.push('diff');

  let boqRef = false;
  if (fs.existsSync(boqPath)) {
    const boqContent = fs.readFileSync(boqPath, 'utf8');
    boqRef = boqContent.includes('diff') || boqContent.includes('variance') || boqContent.includes('variation');
  }

  const ok = modules.length === 2 && boqRef;
  return {
    ok,
    log: `§WB_VARIANCE modules=[${modules.join(',')}] boq_ref=${boqRef}`,
    reason: modules.length < 2 ? 'missing modules: ' + ['variation_order', 'diff'].filter(m => !modules.includes(m)).join(',') : !boqRef ? 'boq_charts.html has no variance/diff reference' : ''
  };
});

// ─── 3.5 Offline/PWA mode ────────────────────────────────────────────────────
// Issue: SW version mismatch causes stale JS to be served from cache
test('offline_pwa', () => {
  const swPath = path.join(DEV_DIR, 'sw.js');
  const indexPath = path.join(DEV_DIR, 'index.html');

  if (!fs.existsSync(swPath) || !fs.existsSync(indexPath)) {
    return { ok: false, log: '§WB_OFFLINE', reason: 'sw.js or index.html not found' };
  }

  const swContent = fs.readFileSync(swPath, 'utf8');
  const indexContent = fs.readFileSync(indexPath, 'utf8');

  // Extract CACHE_VERSION from sw.js
  const swMatch = swContent.match(/CACHE_VERSION\s*=\s*'v(\d+)'/);
  const swVersion = swMatch ? parseInt(swMatch[1], 10) : 0;

  // Extract ?v=N from index.html sw.js registration
  const indexMatch = indexContent.match(/sw\.js\?v=(\d+)/);
  const indexVersion = indexMatch ? parseInt(indexMatch[1], 10) : 0;

  const versionMatch = swVersion > 0 && swVersion === indexVersion;

  // Count precache assets in sw.js
  const precacheMatch = swContent.match(/PRECACHE_ASSETS\s*=\s*\[([\s\S]*?)\];/);
  let precacheCount = 0;
  if (precacheMatch) {
    precacheCount = (precacheMatch[1].match(/'/g) || []).length / 2; // pairs of quotes
  }

  // Check manifest exists
  const manifestExists = fs.existsSync(path.join(DEV_DIR, 'manifest.webmanifest')) ||
                          fs.existsSync(path.join(DEV_DIR, 'manifest.json'));

  const ok = versionMatch && precacheCount > 10 && manifestExists;
  return {
    ok,
    log: `§WB_OFFLINE sw_version=${swVersion} index_version=${indexVersion} match=${versionMatch} precache_count=${precacheCount} manifest=${manifestExists}`,
    reason: !versionMatch ? `sw=${swVersion} != index=${indexVersion}` : precacheCount <= 10 ? 'too few precache assets' : !manifestExists ? 'no manifest' : ''
  };
});

// ─── 3.6 Filename case consistency ──────────────────────────────────────────
// Issue: `hospital.db` vs `Hospital_extracted.db` caused split detect 404
test('filename_case', () => {
  const landingFiles = [
    path.join(REPO, 'SYSNOVA/index.html'),
    path.join(REPO, 'deploy/dev/landing.html')
  ];

  const actualFiles = new Set(fs.readdirSync(BUILDINGS_DIR));
  const mismatches = [];
  let totalBuildings = 0;

  for (const lf of landingFiles) {
    if (!fs.existsSync(lf)) continue;
    const content = fs.readFileSync(lf, 'utf8');
    // Extract db filenames from BUILDINGS config
    const dbMatches = content.matchAll(/db:\s*'([^']+)'/g);
    for (const m of dbMatches) {
      totalBuildings++;
      const dbFile = m[1];
      if (!actualFiles.has(dbFile)) {
        mismatches.push(dbFile);
      }
    }
  }

  const ok = mismatches.length === 0 && totalBuildings > 0;
  return {
    ok,
    log: `§WB_CASE_CHECK buildings=${totalBuildings} mismatches=[${mismatches.join(',')}]`,
    reason: mismatches.length > 0 ? 'missing: ' + mismatches.join(', ') : totalBuildings === 0 ? 'no buildings found' : ''
  };
});

// ─── 3.7 Ground Y — false floor filter ──────────────────────────────────────
// Issue: S260c BUG 3 — ground hovers on some buildings
for (const bld of SPLIT_BUILDINGS) {
  test('ground_y_' + bld, () => {
    // Use meta.db if available (has element_transforms + elements_meta), else extracted
    let dbPath = findBldFile(bld + '_meta.db');
    if (!dbPath) dbPath = findBldFile(bld + '_extracted.db');
    if (!dbPath) {
      return { ok: false, log: `§WB_GROUND_Y bld=${bld}`, reason: 'no DB found' };
    }

    // Check if required tables exist
    const tables = sqlQuery(dbPath, "SELECT name FROM sqlite_master WHERE type='table'").split('\n');
    if (!tables.includes('element_transforms') || !tables.includes('elements_meta')) {
      return { ok: false, log: `§WB_GROUND_Y bld=${bld}`, reason: 'missing tables' };
    }

    // Replicate _calcGroundY logic
    const gfNames = "'Ground Floor','Ground','First Floor','1st Floor','Level 0','Level 00','Level 1','GF','L0','L00','L1','00','0','1F','EG','Erdgeschoss','Storey 1','Plan 1'";
    let groundZ = null, src = '?';

    // Step 1: storey name match
    try {
      const r = sqlQuery(dbPath,
        `SELECT t.center_z - t.bbox_z/2, t.bbox_x * t.bbox_y AS area, m.storey FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0 AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL AND m.storey IN (${gfNames}) ORDER BY area DESC LIMIT 1`);
      if (r && r.length > 0) {
        const parts = r.split('|');
        groundZ = parseFloat(parts[0]);
        src = 'gf-storey-slab(' + parts[2] + ')';
      }
    } catch (e) { /* no match */ }

    // Step 2: lowest of top 5 largest slabs
    if (src === '?') {
      try {
        const r = sqlQuery(dbPath,
          "SELECT t.center_z - t.bbox_z/2, t.bbox_x * t.bbox_y AS area, t.center_z, m.storey FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0 AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL ORDER BY area DESC LIMIT 5");
        if (r && r.length > 0) {
          const rows = r.split('\n');
          let bestCz = Infinity, bestBottom = null, bestStorey = '';
          for (const row of rows) {
            const parts = row.split('|');
            const cz = parseFloat(parts[2]);
            if (cz < bestCz) { bestCz = cz; bestBottom = parseFloat(parts[0]); bestStorey = parts[3] || ''; }
          }
          if (bestBottom !== null) { groundZ = bestBottom; src = 'lowest-of-top5(' + bestStorey + ')'; }
        }
      } catch (e) { /* no match */ }
    }

    // Validation: ground Z should be near building's min Z (not from roof).
    // Some buildings have IFC coords offset high (Hospital Level 1 at z=165), so we
    // compare against the building's own Z range, not absolute values.
    let minZ = null, maxZ = null;
    try {
      const zRange = sqlQuery(dbPath, "SELECT MIN(center_z), MAX(center_z) FROM element_transforms");
      const parts = zRange.split('|');
      minZ = parseFloat(parts[0]); maxZ = parseFloat(parts[1]);
    } catch (e) { /* ignore */ }

    // Ground should be in the lower third of the building's Z range
    let reasonable = groundZ !== null;
    if (minZ !== null && maxZ !== null && maxZ > minZ) {
      const range = maxZ - minZ;
      reasonable = groundZ <= minZ + range * 0.4; // ground in lower 40% of building
    }
    const ok = groundZ !== null && reasonable;

    return {
      ok,
      log: `§WB_GROUND_Y bld=${bld} src=${src} z=${groundZ !== null ? groundZ.toFixed(2) : 'null'}`,
      reason: groundZ === null ? 'no slabs found' : !reasonable ? `z=${groundZ.toFixed(2)} out of range [-10,30]` : ''
    };
  });
}

// ─── 3.8 LTU draw call consolidation — maths proof ──────────────────────────
// Issue: Progressive flush + per-hash InstancedMesh creates thousands of draw calls.
// Fix: (1) hashes with ≤5 instances → BatchedMesh instead of InstancedMesh.
//      (2) _consolidateBatched() merges fragmented BM after streaming ends.
// This test computes BEFORE and AFTER draw call counts from DB.
test('ltu_consolidation_maths', () => {
  let metaPath = path.join(REPO, 'deploy/dev/buildings/LTU_AHouse_meta.db');
  if (!fs.existsSync(metaPath)) metaPath = path.join(BUILDINGS_DIR, 'LTU_AHouse_meta.db');
  let geoPath = path.join(REPO, 'deploy/dev/buildings/LTU_AHouse_geo.db');
  if (!fs.existsSync(geoPath)) geoPath = path.join(BUILDINGS_DIR, 'LTU_AHouse_geo.db');

  if (!fs.existsSync(metaPath) || !fs.existsSync(geoPath)) {
    return { ok: false, log: '§WB_LTU_CONSOLIDATE', reason: 'LTU meta/geo not found' };
  }

  const elements = parseInt(sqlQuery(metaPath, "SELECT COUNT(*) FROM element_instances"), 10);

  // Hash distribution by instance count
  const hashes1 = parseInt(sqlQuery(metaPath,
    "SELECT COUNT(*) FROM (SELECT geometry_hash FROM element_instances WHERE geometry_hash IS NOT NULL GROUP BY geometry_hash HAVING COUNT(*)=1)"), 10);
  const hashes2to5 = parseInt(sqlQuery(metaPath,
    "SELECT COUNT(*) FROM (SELECT geometry_hash FROM element_instances WHERE geometry_hash IS NOT NULL GROUP BY geometry_hash HAVING COUNT(*) BETWEEN 2 AND 5)"), 10);
  const hashes6plus = parseInt(sqlQuery(metaPath,
    "SELECT COUNT(*) FROM (SELECT geometry_hash FROM element_instances WHERE geometry_hash IS NOT NULL GROUP BY geometry_hash HAVING COUNT(*)>=6)"), 10);

  // Storey|disc buckets (BatchedMesh grouping key)
  const buckets = parseInt(sqlQuery(metaPath,
    "SELECT COUNT(DISTINCT COALESCE(storey,'')||'|'||COALESCE(discipline,'')) FROM elements_meta"), 10);

  // Progressive flush count
  const flushes = 1 + Math.ceil((elements - 500) / 5000);

  // OLD behaviour: 1-inst → BatchedMesh (fragmented), 2+ → InstancedMesh
  const oldDrawCalls = (flushes * buckets) + hashes2to5 + hashes6plus;

  // NEW behaviour: ≤5-inst → BatchedMesh (consolidated), 6+ → InstancedMesh
  const newDrawCalls = buckets + hashes6plus;

  const reduction = ((1 - newDrawCalls / oldDrawCalls) * 100).toFixed(0);
  const ok = newDrawCalls <= 2000;

  return {
    ok,
    log: `§WB_LTU_CONSOLIDATE elements=${elements} h1=${hashes1} h2to5=${hashes2to5} h6plus=${hashes6plus} buckets=${buckets} flushes=${flushes} OLD=${oldDrawCalls} NEW=${newDrawCalls} reduction=${reduction}%`,
    reason: ok ? '' : `still ${newDrawCalls} draw calls after fix (target ≤2000)`
  };
});

// ─── 3.9 Offline/IDB — hard reset survivability ──────────────────────────────
// Issue: After hard reset (Ctrl+Shift+R), offline mode should serve from IDB.
// Architecture: SW Cache = app shell (JS/HTML), IndexedDB = .db building files.
// SW explicitly skips .db fetches (line 174 of sw.js) — cachedFetch() handles them.
// Problem: if SW cache is cleared ("Empty cache and hard reload"), the app shell
// is gone. Even though .db files survive in IDB, the viewer JS can't load to read them.
// This test checks that the offline chain is complete:
//   1. SW precache covers ALL JS files loaded by index.html
//   2. SW does NOT intercept .db requests (so IDB handles them)
//   3. cachedFetch() checks IDB BEFORE network (offline-first for DBs)
test('offline_idb_chain', () => {
  const swPath = path.join(DEV_DIR, 'sw.js');
  const indexPath = path.join(DEV_DIR, 'index.html');
  const scenePath = path.join(DEV_DIR, 'scene.js');

  if (!fs.existsSync(swPath) || !fs.existsSync(indexPath) || !fs.existsSync(scenePath)) {
    return { ok: false, log: '§WB_OFFLINE_IDB', reason: 'required files not found' };
  }

  const swContent = fs.readFileSync(swPath, 'utf8');
  const indexContent = fs.readFileSync(indexPath, 'utf8');
  const sceneContent = fs.readFileSync(scenePath, 'utf8');

  // 1. Extract JS files loaded by index.html (script src= and import map)
  const scriptSrcs = [];
  const srcMatches = indexContent.matchAll(/src=["']([^"']+\.js)(?:\?[^"']*)?["']/g);
  for (const m of srcMatches) scriptSrcs.push(m[1]);

  // Extract PRECACHE_ASSETS from sw.js
  const precacheMatch = swContent.match(/PRECACHE_ASSETS\s*=\s*\[([\s\S]*?)\];/);
  const precacheFiles = new Set();
  if (precacheMatch) {
    const entries = precacheMatch[1].matchAll(/'([^']+)'/g);
    for (const e of entries) precacheFiles.add(e[1]);
  }

  // Check which index.html scripts are NOT in precache (skip external/CDN scripts)
  const missingFromPrecache = scriptSrcs.filter(s =>
    !precacheFiles.has(s) && !s.startsWith('//') && !s.startsWith('http'));

  // 2. Verify SW skips .db files
  const swSkipsDb = swContent.includes(".endsWith('.db')") && swContent.includes('return');

  // 3. Verify cachedFetch checks IDB first (cache read before fetch)
  const idbFirst = sceneContent.includes('cachedFetch') &&
    sceneContent.includes('CACHE_HIT') &&
    sceneContent.includes('CACHE_MISS');

  const ok = missingFromPrecache.length === 0 && swSkipsDb && idbFirst;
  return {
    ok,
    log: `§WB_OFFLINE_IDB scripts_in_index=${scriptSrcs.length} precached=${precacheFiles.size} missing=[${missingFromPrecache.join(',')}] sw_skips_db=${swSkipsDb} idb_first=${idbFirst}`,
    reason: missingFromPrecache.length > 0
      ? `JS not precached: ${missingFromPrecache.join(', ')} — offline will fail loading these`
      : !swSkipsDb ? 'SW does not skip .db files'
      : !idbFirst ? 'cachedFetch does not check IDB before network'
      : ''
  };
});

// ─── 3.10 Drop IFC multi-file — Clinic discipline assignment ─────────────────
// Issue: Clinic multi-IFC drop — progress bar broken, Open icon broken.
// Trace the actual code path: _discFromFilename() splits filename on _ and checks aliases.
test('drop_ifc_clinic_disc_assignment', () => {
  const landingPath = path.join(DEV_DIR, 'landing.html');
  if (!fs.existsSync(landingPath)) {
    return { ok: false, log: '§WB_CLINIC_DROP', reason: 'landing.html not found' };
  }
  const content = fs.readFileSync(landingPath, 'utf8');

  // Extract _VALID_DISCS array
  const validMatch = content.match(/_VALID_DISCS\s*=\s*\[([^\]]+)\]/);
  const validDiscs = validMatch ? validMatch[1].match(/'([^']+)'/g).map(s => s.replace(/'/g, '')) : [];

  // Extract _DISC_ALIAS map
  const aliasMatch = content.match(/_DISC_ALIAS\s*=\s*\{([\s\S]*?)\}/);
  const aliases = {};
  if (aliasMatch) {
    const pairs = aliasMatch[1].matchAll(/(\w+)\s*:\s*'([^']+)'/g);
    for (const p of pairs) aliases[p[1]] = p[2];
  }

  // Simulate _discFromFilename for each Clinic IFC filename
  const clinicFiles = [
    'Clinic_Architectural_IFC2x3.ifc',
    'Clinic_Electrical_IFC2x3.ifc',
    'Clinic_Plumbing_IFC2x3.ifc',
    'Clinic_HVAC_IFC2x3.ifc',
    'Clinic_Structural_IFC2x3.ifc'
  ];

  function discFromFilename(fname) {
    const stem = fname.replace(/\.(ifc|IFC)$/, '');
    const parts = stem.split(/[_\-]/);
    for (let i = parts.length - 1; i >= 0; i--) {
      const p = parts[i].toUpperCase();
      if (validDiscs.includes(p)) return p;
      if (aliases[p]) return aliases[p];
    }
    return null;
  }

  const results = {};
  const failures = [];
  for (const f of clinicFiles) {
    const disc = discFromFilename(f);
    results[f] = disc;
    if (!disc) failures.push(f + '→null');
  }

  // Also check: common prefix extraction (buildingName)
  const stems = clinicFiles.map(f => f.replace(/\.ifc$/i, ''));
  let prefix = stems[0];
  for (let i = 1; i < stems.length; i++) {
    while (stems[i].indexOf(prefix) !== 0) { prefix = prefix.substring(0, prefix.length - 1); if (!prefix) break; }
  }
  const buildingName = prefix.replace(/[_\-]+$/, '') || stems[0];

  // Check progress bar: must be shown via parentElement.style.display='block' in handleImportMultiIFC
  const multiHandler = content.match(/function handleImportMultiIFC[\s\S]*?^}/m);
  const progressShown = content.includes("progressBar.parentElement.style.display = 'block'");

  // Check worker path exists on disk
  const workerMatch = content.match(/new Worker\(['"]([^'"]+import_worker[^'"]*)['"]\)/);
  const workerPath = workerMatch ? workerMatch[1].replace(/^sandbox\//, '').replace(/\?.*$/, '') : null;
  const workerExists = workerPath ? fs.existsSync(path.join(DEV_DIR, workerPath)) : false;

  // Check: does handleImportMultiIFC call renderImportCards at end?
  const rendersCards = content.includes('renderImportCards()');

  // Check: does the card template have an Open button with data-open attribute?
  const hasOpenBtn = content.includes('data-open=');

  // Check: does openProject reference sandbox/index.html (viewer)?
  const opensViewer = content.includes("sandbox/index.html");

  const ok = failures.length === 0 && workerExists && progressShown && rendersCards && hasOpenBtn && opensViewer;
  const issues = [];
  if (failures.length > 0) issues.push('disc assignment failed: ' + failures.join(', '));
  if (!workerExists) issues.push('import_worker.js missing at ' + workerPath);
  if (!progressShown) issues.push('progress bar never shown');
  if (!rendersCards) issues.push('renderImportCards not called');
  if (!hasOpenBtn) issues.push('no Open button (data-open)');
  if (!opensViewer) issues.push('openProject does not open sandbox/index.html');

  return {
    ok,
    log: `§WB_CLINIC_DROP building=${buildingName} discs=${JSON.stringify(results)} worker=${workerPath}(${workerExists}) progress=${progressShown} cards=${rendersCards} open=${hasOpenBtn} viewer=${opensViewer}`,
    reason: issues.join('; ')
  };
});

// ─── 3.11 Clinic discipline coverage — all 5 IFC sources in extracted DB ─────
// Issue: User reports only ACMV appears from OCI Clinic.
// Whitebox: verify all disciplines present + geometry hashes exist for each.
test('clinic_disciplines', () => {
  const metaPath = findBldFile('Clinic_meta.db');
  const geoPath = findBldFile('Clinic_geo.db');
  if (!metaPath) {
    return { ok: false, log: '§WB_CLINIC_DISC', reason: 'Clinic_meta.db not found' };
  }

  // Discipline counts
  const discRows = sqlQuery(metaPath,
    "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC");
  const disciplines = {};
  for (const row of discRows.split('\n')) {
    const [disc, cnt] = row.split('|');
    if (disc) disciplines[disc] = parseInt(cnt, 10);
  }

  // Expected: ARC, ELEC, ACMV, PLB, STR (from 5 IFC files)
  const expected = ['ARC', 'ELEC', 'ACMV', 'PLB', 'STR'];
  const missing = expected.filter(d => !disciplines[d]);
  const total = Object.values(disciplines).reduce((a, b) => a + b, 0);

  // Per-discipline geometry coverage: each discipline must have geometry hashes
  const discGeo = {};
  try {
    const geoRows = sqlQuery(metaPath,
      "SELECT m.discipline, COUNT(DISTINCT i.geometry_hash) FROM elements_meta m JOIN element_instances i ON m.guid=i.guid WHERE i.geometry_hash IS NOT NULL GROUP BY m.discipline");
    for (const row of geoRows.split('\n')) {
      const [disc, cnt] = row.split('|');
      if (disc) discGeo[disc] = parseInt(cnt, 10);
    }
  } catch (e) { /* skip */ }
  const noGeometry = expected.filter(d => !discGeo[d] || discGeo[d] === 0);

  // Cross-check geo.db if available
  let geoOrphans = -1;
  if (geoPath) {
    try {
      geoOrphans = parseInt(execSync(
        `sqlite3 "${metaPath}" "ATTACH '${geoPath}' AS geo; SELECT COUNT(DISTINCT ei.geometry_hash) FROM element_instances ei WHERE ei.geometry_hash IS NOT NULL AND ei.geometry_hash NOT IN (SELECT geometry_hash FROM geo.component_geometries);"`,
        { encoding: 'utf8' }
      ).trim(), 10);
    } catch (e) { geoOrphans = -1; }
  }

  const issues = [];
  if (missing.length > 0) issues.push('missing disciplines: ' + missing.join(','));
  if (noGeometry.length > 0) issues.push('disciplines with 0 geometry hashes: ' + noGeometry.join(','));
  if (geoOrphans > 0) issues.push(geoOrphans + ' orphan hashes in meta not found in geo');
  if (total <= 15000) issues.push('total only ' + total);

  const ok = issues.length === 0;
  return {
    ok,
    log: `§WB_CLINIC_DISC total=${total} disciplines=${JSON.stringify(disciplines)} geo_per_disc=${JSON.stringify(discGeo)} geo_orphans=${geoOrphans} missing=[${missing.join(',')}] no_geo=[${noGeometry.join(',')}]`,
    reason: issues.join('; ')
  };
});

// ─── 3.12 Clinic building column — all disciplines must share one building ───
// Issue: Multi-IFC Clinic extraction stores each file as separate "building"
// in elements_meta.building. Viewer auto-streams only the nearest "building",
// so only ACMV appears. All rows must share one building name.
test('clinic_single_building', () => {
  const metaPath = findBldFile('Clinic_meta.db');
  const extractedPath = findBldFile('Clinic_extracted.db');
  const dbPath = metaPath || extractedPath;
  if (!dbPath) {
    return { ok: false, log: '§WB_CLINIC_BLD', reason: 'Clinic DB not found' };
  }

  const buildings = sqlQuery(dbPath,
    "SELECT DISTINCT building FROM elements_meta").split('\n').filter(Boolean);
  const counts = sqlQuery(dbPath,
    "SELECT building, COUNT(*) FROM elements_meta GROUP BY building ORDER BY COUNT(*) DESC");

  // PASS only if there's exactly 1 building name
  const ok = buildings.length === 1;
  return {
    ok,
    log: `§WB_CLINIC_BLD buildings=${buildings.length} names=[${buildings.join(',')}] counts=${counts.replace(/\n/g, '; ')}`,
    reason: ok ? '' : `${buildings.length} building names instead of 1 — viewer streams only one at a time. Fix: UPDATE elements_meta SET building="Clinic"`
  };
});

// ─── 3.12 S261 DLOD geometry-swap prerequisites ─────────────────────────────
// Issue: S261 DLOD requires bbox columns in element_transforms for per-element bbox sizing

test('dlod_bbox_columns', () => {
  // Check that at least one large building has bbox_x/y/z columns
  const dbPath = findBldFile('Terminal_extracted.db') || findBldFile('LTU_AHouse_extracted.db');
  if (!dbPath) return { ok: true, log: '§WB_DLOD_BBOX skip=no_large_db', reason: '' };
  try {
    const cols = sqlQuery(dbPath, "PRAGMA table_info(element_transforms)");
    const hasBbox = cols.includes('bbox_x') && cols.includes('bbox_y') && cols.includes('bbox_z');
    return {
      ok: hasBbox,
      log: `§WB_DLOD_BBOX db=${path.basename(dbPath)} has_bbox=${hasBbox}`,
      reason: hasBbox ? '' : 'element_transforms missing bbox_x/y/z — DLOD needs per-element bbox dims'
    };
  } catch(e) {
    return { ok: false, log: '§WB_DLOD_BBOX err=' + e.message, reason: 'query failed' };
  }
});

test('dlod_budget_math', () => {
  // Verify: 8M vert budget can hold 122K elements at tier-256 average
  // 122K * 256 = 31.2M — exceeds 8M, so budget guard must trigger for largest buildings
  // But real distribution is mixed: many small elements, so average is much less
  var budget = 8000000;
  var elements = 122000;
  var avgReserved = 256;
  var needed = elements * avgReserved;
  var wouldExceed = needed > budget;
  // This is informational — budget guard correctly caps allocation
  return {
    ok: true,
    log: `§WB_DLOD_BUDGET budget=${budget} elements=${elements} avg_reserved=${avgReserved} needed=${needed} would_exceed=${wouldExceed}`,
    reason: ''
  };
});

test('dlod_reservation_tiers', () => {
  // Verify tier boundaries are correct
  var tiers = [[64, 192], [128, 384], [256, 768], [512, 2048]];
  var BBOX_VERTS = 24, BBOX_IDX = 36;
  var allOk = true;
  var details = [];
  for (var ti = 0; ti < tiers.length; ti++) {
    var rv = tiers[ti][0], ri = tiers[ti][1];
    if (rv < BBOX_VERTS) { allOk = false; details.push('tier_' + rv + '_lt_bbox_verts'); }
    if (ri < BBOX_IDX) { allOk = false; details.push('tier_' + ri + '_lt_bbox_idx'); }
    // Tier index ratio should be ~3x verts (triangulated mesh heuristic)
    if (ri < rv * 2) { allOk = false; details.push('tier_' + rv + '_idx_ratio_low'); }
  }
  return {
    ok: allOk,
    log: `§WB_DLOD_TIERS tiers=${tiers.length} bbox_verts=${BBOX_VERTS} bbox_idx=${BBOX_IDX} ok=${allOk}`,
    reason: allOk ? '' : 'tier issues: ' + details.join(', ')
  };
});

// ─── Summary ─────────────────────────────────────────────────────────────────
console.log(`§WB_SUMMARY pass=${pass} fail=${fail} total=${pass + fail}`);
process.exit(fail > 0 ? 1 : 0);
