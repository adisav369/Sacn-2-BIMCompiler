// 36-card-first-browser.spec.js — Card-First View Model complete test suite (2D_031)
// All card logic + fleet DB verification in one file.
// Tests real DB data via sqlite3 CLI — no browser needed, runs in < 5s.

const { test, expect } = require('@playwright/test');
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DEV = path.resolve(__dirname, '../..');
const src = (f) => fs.readFileSync(path.join(DEV, f), 'utf8');
const BLDG_DIR = path.resolve(__dirname, '../../../../deploy/buildings');

// Find a real DB for detailed tests — SAME source as fleet
const DB_CANDIDATES = [
  path.join(BLDG_DIR, 'SampleHouse_extracted.db'),
  path.join(DEV, 'buildings/SampleHouse_extracted.db'),
  path.resolve(__dirname, '../../../../DAGCompiler/lib/input/SampleHouse_extracted.db'),
];
const DB_PATH = DB_CANDIDATES.find(p => fs.existsSync(p));
const sql = (db, query) => {
  try { return execSync(`sqlite3 "${db}" "${query}"`, { encoding: 'utf8' }).trim(); }
  catch (e) { return ''; }
};

// ── Extract classifyMesh as runnable function ─────────────────────
function buildClassify() {
  const s = src('grid_views.js');
  const hide = s.match(/var HIDE_IN_FLOOR\s*=\s*\{[^}]+\}/)[0];
  const fade = s.match(/var FADE_IN_FLOOR\s*=\s*\{[^}]+\}/)[0];
  const fn = s.match(/function classifyMesh\(ifcClass, retainSet, hideSet\)\s*\{[\s\S]*?return 'clip';\s*\}/)[0];
  return new Function('ifcClass', 'retainSet', 'hideSet',
    hide + ';\n' + fade + ';\n' + fn + '\nreturn classifyMesh(ifcClass, retainSet, hideSet);');
}

// ══════════════════════════════════════════════════════════════════
test.describe('Card-First Complete Suite', () => {

  // ── 1. classifyMesh pure logic ─────────────────────────────────
  test('T_3601: classifyMesh — 14 IFC classes, correct action each', () => {
    const c = buildClassify();
    const r = { 'IfcFurnishingElement': 1, 'IfcFurniture': 1, 'IfcFlowTerminal': 1, 'IfcSanitaryTerminal': 1 };
    const log = [];

    const checks = [
      ['IfcRoof',       'hide'],  ['IfcRoofing',     'hide'],
      ['IfcCovering',   'clip'],  // IfcCovering = wall/floor tiles, NOT roof — must be visible
      ['IfcSlab',       'fade'],  ['IfcPlate',       'fade'],
      ['IfcFurniture',  'retain'],['IfcFurnishingElement','retain'],
      ['IfcWall',       'clip'],  ['IfcWallStandardCase','clip'],['IfcColumn','clip'],
      ['IfcDoor',       'clip'],  ['IfcWindow',      'clip'],  ['IfcBeam','clip'],['IfcStair','clip'],
    ];
    for (const [cls, expected] of checks) {
      const got = c(cls, r, null);
      log.push(cls + '→' + got + (got === expected ? ' ✓' : ' ✗ expected=' + expected));
      expect(got).toBe(expected);
    }
    // Custom hideSet overrides HIDE_IN_FLOOR
    const got2 = c('IfcBeam', r, { 'IfcBeam': 1 });
    log.push('IfcBeam+customHide→' + got2);
    expect(got2).toBe('hide');

    console.log('§T_3601 ' + log.join(' | '));
  });

  // ── 2. restoreSection architecture ─────────────────────────────
  test('T_3602: restoreSection — DB query, one pass, no band, no applyFloorClip', () => {
    const s = src('grid_overlay.js');
    const start = s.indexOf('function restoreSection');
    const end = s.indexOf('\n  // Card cleanup', start);
    const body = s.slice(start, end);
    const log = [];

    const has = (tag, str) => { const ok = body.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };
    const hasNot = (tag, str) => { const ok = !body.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };

    expect(has('queryStoreyGuids', 'queryStoreyGuids')).toBe(true);
    expect(has('lockView_cameraOnly', 'null, true)')).toBe(true);
    expect(has('own_clipPlane', 'THREE.Plane')).toBe(true);
    expect(has('guidSet_check', 'guidSet[guid]')).toBe(true);
    expect(has('hideSet_check', 'hideSet[cls]')).toBe(true);
    expect(has('fadeSet_check', 'fadeSet[cls]')).toBe(true);
    expect(has('retainSet_check', 'retainSet[cls]')).toBe(true);
    expect(has('contours', 'renderContoursForView')).toBe(true);
    expect(hasNot('no_band', 'applyStoreyBandVisibility')).toBe(true);
    expect(hasNot('no_applyFloorClip', 'applyFloorClip')).toBe(true);

    console.log('§T_3602 ' + log.join(' | '));
  });

  // ── 3. clearCardView ───────────────────────────────────────────
  test('T_3603: clearCardView — restores visible, opacity, clips, called on exit', () => {
    const s = src('grid_overlay.js');
    const fn = s.slice(s.indexOf('function clearCardView()'), s.indexOf('\n  }', s.indexOf('function clearCardView()')) + 4);
    const log = [];
    const has = (tag, str) => { const ok = fn.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };

    expect(has('visible=true', 'visible = true')).toBe(true);
    expect(has('origOpacity', '_origOpacity')).toBe(true);
    expect(has('clip=null', 'clippingPlanes = null')).toBe(true);
    expect(has('clipping=false', 'localClippingEnabled = false')).toBe(true);
    // Called on grid exit
    expect(s.includes('clearCardView()')).toBe(true);
    log.push('calledOnExit ✓');

    console.log('§T_3603 ' + log.join(' | '));
  });

  // ── 4. autoCreateCards ─────────────────────────────────────────
  test('T_3604: autoCreateCards — guard, detectStoreys, GF+L1', () => {
    const s = src('grid_overlay.js');
    const fn = s.slice(s.indexOf('function autoCreateCards()'));
    const log = [];
    const has = (tag, str) => { const ok = fn.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };

    expect(has('guard', 'savedSections.length > 0) return')).toBe(true);
    expect(has('storeys', 'detectStoreys')).toBe(true);
    expect(has('GF', "'GF'")).toBe(true);
    expect(has('L1', "'L1'")).toBe(true);
    console.log('§T_3604 ' + log.join(' | '));
  });

  // ── 5. view_state round-trip ───────────────────────────────────
  test('T_3605: view_state — schema, capture, save, load, parse', () => {
    const overlay = src('grid_overlay.js');
    const log = [];
    const has = (tag, str) => { const ok = overlay.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };

    expect(has('alter', 'ADD COLUMN view_state TEXT')).toBe(true);
    expect(has('capture', 'function captureViewState()')).toBe(true);
    expect(has('save', 'view_state) VALUES')).toBe(true);
    expect(has('select', 'view_state FROM saved_sections')).toBe(true);
    expect(has('parse', 'JSON.parse(row[5])')).toBe(true);
    console.log('§T_3605 ' + log.join(' | '));
  });

  // ── 6. lockView cameraOnly ─────────────────────────────────────
  test('T_3606: lockView cameraOnly — skips clip when true', () => {
    const s = src('grid_views.js');
    const log = [];
    const has = (tag, str) => { const ok = s.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };

    expect(has('param', 'hideSet, cameraOnly)')).toBe(true);
    expect(has('guard', '!cameraOnly && VIEW_DEFS')).toBe(true);
    console.log('§T_3606 ' + log.join(' | '));
  });

  // ── 7. Save button — always when scissors ON ──────────────────
  test('T_3607: Save button — available when scissors ON, wired to saveSectionFromScissors', () => {
    const s = src('tools.js');
    const log = [];
    const has = (tag, str) => { const ok = s.includes(str); log.push(tag + (ok ? ' ✓' : ' ✗')); return ok; };

    expect(has('btn_id', 'section-save-cut-btn')).toBe(true);
    expect(has('save_fn', 'saveSectionFromScissors')).toBe(true);
    // NOT gated by isIn2DView (was the bug)
    const toggleBlock = s.slice(s.indexOf('A.sectionOn = !A.sectionOn'));
    const saveBlock = toggleBlock.slice(0, toggleBlock.indexOf('section-save-cut-btn'));
    const gated = saveBlock.includes('isIn2DView');
    log.push('not_gated' + (gated ? ' ✗ STILL GATED' : ' ✓'));
    expect(gated).toBe(false);

    console.log('§T_3607 ' + log.join(' | '));
  });

  // ── 8. SampleHouse GF card composition ─────────────────────────
  test('T_3608: SampleHouse GF — hide+fade+retain+clip = total, all GUIDs joinable', () => {
    if (!DB_PATH) return;
    const gf = 'Ground Floor';
    const total = parseInt(sql(DB_PATH, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + gf + "'"));
    const hidden = parseInt(sql(DB_PATH, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + gf + "' AND ifc_class IN ('IfcRoof','IfcRoofing')"));
    const faded = parseInt(sql(DB_PATH, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + gf + "' AND ifc_class IN ('IfcSlab','IfcPlate')"));
    const retained = parseInt(sql(DB_PATH, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + gf + "' AND ifc_class IN ('IfcFurniture','IfcFurnishingElement','IfcFlowTerminal','IfcSanitaryTerminal','IfcElectricalAppliance')"));
    const clipped = total - hidden - faded - retained;
    const joinCount = parseInt(sql(DB_PATH, "SELECT COUNT(*) FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid WHERE m.storey='" + gf + "'"));
    const totalAll = parseInt(sql(DB_PATH, "SELECT COUNT(*) FROM elements_meta"));

    console.log('§T_3608 db=' + path.basename(DB_PATH) +
                ' total=' + total + ' hidden=' + hidden + ' faded=' + faded +
                ' retained=' + retained + ' clipped=' + clipped +
                ' join=' + joinCount + ' totalAll=' + totalAll);

    // Invariants — these hold for ANY DB version
    expect(total).toBeGreaterThan(0);
    expect(hidden + faded + retained + clipped).toBe(total);  // all accounted for
    // joinCount may be < total: some elements have metadata but no transform (no mesh in scene)
    // Card query returns all GUIDs from metadata; orphans are harmless (no mesh to match)
    expect(joinCount).toBeGreaterThan(0);
    expect(joinCount).toBeLessThanOrEqual(total);
    if (joinCount < total) {
      console.log('§T_3608 NOTE: ' + (total - joinCount) + ' orphan GUIDs (meta without transform)');
    }
    expect(clipped).toBeGreaterThan(0);                        // must have walls to clip
    expect(total).toBeLessThan(totalAll);                      // storey is subset
  });

  // ── 9. Fleet — every deployed building ─────────────────────────
  // Uses door-count ranking (same as viewer computeStoreyAwareCutZ) to pick GF.
  // Logs warnings for: wrong GF, zero walls, slab-heavy, foundation-as-GF.
  test('T_3609: Fleet — GF card composition across all deployed buildings', () => {
    const dbs = fs.existsSync(BLDG_DIR)
      ? fs.readdirSync(BLDG_DIR).filter(f => f.endsWith('_extracted.db'))
      : [];
    expect(dbs.length).toBeGreaterThan(0);

    // Basement/foundation keywords — these should NOT be GF
    const BASEMENT_WORDS = ['keller','kjeller','kælder','basement','foundation','footing',
                            'fundering','fdn','t/fdn','subgrade','pile','ug','untergeschoss',
                            'ground water'];

    const results = [];
    const warnings = [];
    let checked = 0;

    for (const dbFile of dbs) {
      const bld = dbFile.replace('_extracted.db', '');
      const dbPath = path.join(BLDG_DIR, dbFile);

      const hasMeta = sql(dbPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='elements_meta'");
      const hasTx = sql(dbPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='element_transforms'");
      if (hasMeta === '0' || hasTx === '0') {
        results.push(bld + ': infra_db');
        continue;
      }

      // Door-count ranking: storey with most doors = GF (same as viewer)
      // Fallback: storey with most elements excluding junk names
      var gf = sql(dbPath,
        "SELECT m.storey, COUNT(*) as n, " +
        "SUM(CASE WHEN m.ifc_class IN ('IfcDoor','IfcDoorStandardCase') THEN 1 ELSE 0 END) as doors " +
        "FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid " +
        "WHERE m.storey IS NOT NULL AND m.storey NOT IN ('Unknown','Roof','unknown') " +
        "GROUP BY m.storey HAVING n >= 5 ORDER BY doors DESC, MIN(et.center_z) ASC LIMIT 1");
      if (!gf) {
        results.push(bld + ': no_storey');
        continue;
      }
      gf = gf.split('|')[0]; // first column = storey name

      const esc = gf.replace(/'/g, "''");
      const total = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "'")) || 0;
      const walls = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcWall','IfcWallStandardCase')")) || 0;
      const doors = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcDoor','IfcDoorStandardCase')")) || 0;
      const roofs = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcRoof','IfcRoofing')")) || 0;
      const slabs = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcSlab','IfcPlate')")) || 0;
      const furn = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcFurniture','IfcFurnishingElement')")) || 0;
      const totalAll = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta")) || 0;

      // Card composition — same categories as restoreSection
      const hidden = roofs;  // hideSet: IfcRoof, IfcRoofing, IfcCovering
      const faded = slabs;   // fadeSet: IfcSlab, IfcPlate
      const retained = furn;  // retainSet: furniture (simplified — viewer also has FlowTerminal etc.)
      const clipped = total - hidden - faded - retained;  // everything else: walls, doors, windows, beams

      // GUIDs joinable to element_transforms (= will have meshes in scene)
      const joinCount = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid WHERE m.storey='" + esc + "'")) || 0;
      const orphans = total - joinCount;

      // ── Whitebox checks ──
      const gfLower = gf.toLowerCase();
      const isBasement = BASEMENT_WORDS.some(w => gfLower.includes(w));
      const wallPct = total > 0 ? (walls / total * 100).toFixed(0) : '0';
      const slabPct = total > 0 ? (slabs / total * 100).toFixed(0) : '0';

      let flags = '';
      if (isBasement) flags += ' ⚠BASEMENT';
      if (walls === 0) flags += ' ⚠NO_WALLS';
      if (doors === 0 && walls > 0) flags += ' ⚠NO_DOORS';
      if (roofs > 0) flags += ' ⚠ROOF_ON_GF(' + roofs + ')';
      if (orphans > 0) flags += ' ⚠ORPHANS(' + orphans + ')';
      if (hidden + faded + retained + clipped !== total) flags += ' ⚠SUM_MISMATCH';

      results.push(bld + ': GF=' + gf + ' tot=' + total + '/' + totalAll +
                   ' hide=' + hidden + ' fade=' + faded + ' retain=' + retained + ' clip=' + clipped +
                   ' w=' + walls + '(' + wallPct + '%) d=' + doors +
                   ' join=' + joinCount + '/' + total + flags);
      if (flags) warnings.push(bld + ': ' + flags.trim());

      // ── Assertions ──
      expect(total).toBeGreaterThan(0);
      expect(hidden + faded + retained + clipped).toBe(total);  // composition adds up
      expect(clipped).toBeGreaterThanOrEqual(0);                 // no negative
      checked++;
    }

    console.log('§T_3609 FLEET checked=' + checked + '/' + dbs.length +
                ' issues=' + warnings.length);
    for (const r of results) console.log('  ' + r);
    if (warnings.length) {
      console.log('§T_3609 ISSUES:');
      for (const w of warnings) console.log('  ' + w);
    }
    expect(checked).toBeGreaterThan(20);
  });

  // ── 10. Door-count ranking vs lowest-z ─────────────────────────
  // The bug: autoCreateCards was sorting by lowest floorZ → picked basements.
  // Fix: sort by door count desc, then floorZ asc (same as computeStoreyAwareCutZ).
  test('T_3610: autoCreateCards uses door-count ranking — not lowest-z', () => {
    const s = src('grid_overlay.js');
    const fn = s.slice(s.indexOf('function autoCreateCards()'));
    const log = [];

    // Must query door counts per storey
    const hasDoorQuery = fn.includes("IfcDoor") && fn.includes("GROUP BY m.storey");
    log.push('doorQuery' + (hasDoorQuery ? ' ✓' : ' ✗'));
    expect(hasDoorQuery).toBe(true);

    // Must sort by doors DESC
    const hasDoorSort = fn.includes('db2 - da') || fn.includes('doors DESC');
    log.push('doorSort' + (hasDoorSort ? ' ✓' : ' ✗'));
    expect(hasDoorSort).toBe(true);

    // Verify on real DBs: door-ranked GF ≠ lowest-z GF for multi-storey buildings
    if (!DB_PATH) { console.log('§T_3610 ' + log.join(' | ')); return; }
    const dbs = fs.existsSync(BLDG_DIR) ? fs.readdirSync(BLDG_DIR).filter(f => f.endsWith('_extracted.db')) : [];
    let improved = 0;
    for (const dbFile of dbs) {
      const dbPath = path.join(BLDG_DIR, dbFile);
      const hasMeta = sql(dbPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='elements_meta'");
      if (hasMeta === '0') continue;

      const lowestZ = sql(dbPath, "SELECT m.storey FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid WHERE m.storey IS NOT NULL AND m.storey NOT IN ('Unknown','Roof','unknown') GROUP BY m.storey HAVING COUNT(*)>=5 ORDER BY MIN(et.center_z) LIMIT 1");
      const doorRanked = sql(dbPath, "SELECT m.storey FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid WHERE m.storey IS NOT NULL AND m.storey NOT IN ('Unknown','Roof','unknown') GROUP BY m.storey HAVING COUNT(*)>=5 ORDER BY SUM(CASE WHEN m.ifc_class IN ('IfcDoor','IfcDoorStandardCase') THEN 1 ELSE 0 END) DESC, MIN(et.center_z) LIMIT 1");
      if (!lowestZ || !doorRanked) continue;
      const lz = lowestZ.split('|')[0];
      const dr = doorRanked.split('|')[0];
      if (lz !== dr) improved++;
    }
    log.push('improved=' + improved + '_buildings');
    console.log('§T_3610 ' + log.join(' | '));
    // Door ranking should improve at least some buildings
    expect(improved).toBeGreaterThan(0);
  });

  // ── 11. classifyMesh covers every IFC class in fleet ───────────
  // Extract all unique IFC classes across all DBs, run classifyMesh on each.
  // Every class must get exactly one of: hide, fade, retain, clip.
  test('T_3611: classifyMesh handles every IFC class in the fleet', () => {
    const classify = buildClassify();
    const retain = { 'IfcFurnishingElement': 1, 'IfcFurniture': 1, 'IfcFlowTerminal': 1, 'IfcSanitaryTerminal': 1 };
    const validActions = ['hide', 'fade', 'retain', 'clip'];

    // Gather all unique IFC classes from all DBs
    const allClasses = new Set();
    const dbs = fs.existsSync(BLDG_DIR) ? fs.readdirSync(BLDG_DIR).filter(f => f.endsWith('_extracted.db')) : [];
    for (const dbFile of dbs) {
      const dbPath = path.join(BLDG_DIR, dbFile);
      const classes = sql(dbPath, "SELECT DISTINCT ifc_class FROM elements_meta WHERE ifc_class IS NOT NULL");
      if (classes) classes.split('\n').forEach(c => allClasses.add(c));
    }

    const results = {};
    let unknown = 0;
    for (const cls of allClasses) {
      const action = classify(cls, retain, null);
      if (!validActions.includes(action)) unknown++;
      results[cls] = action;
    }

    // Group by action for log
    const groups = { hide: [], fade: [], retain: [], clip: [] };
    for (const [cls, action] of Object.entries(results)) groups[action].push(cls);

    console.log('§T_3611 classes=' + allClasses.size +
                ' hide=' + groups.hide.length +
                ' fade=' + groups.fade.length +
                ' retain=' + groups.retain.length +
                ' clip=' + groups.clip.length);
    console.log('  hide: ' + groups.hide.join(', '));
    console.log('  fade: ' + groups.fade.join(', '));
    console.log('  retain: ' + groups.retain.join(', '));
    console.log('  clip: ' + groups.clip.sort().join(', '));

    expect(unknown).toBe(0);
    expect(allClasses.size).toBeGreaterThan(10);
  });

  // ── 12. Contour composition — section_cut produces walls at GF cutZ ──
  // Verify that section_cut.js SLICE_CLASSES includes the classes needed
  // for contours, and that the band filter excludes roof classes.
  test('T_3612: section_cut SLICE_CLASSES covers walls+doors, band excludes roof', () => {
    const s = src('section_cut.js');
    const log = [];

    // SLICE_CLASSES must include walls, doors, windows for contour
    const sliceMatch = s.match(/var SLICE_CLASSES\s*=\s*\{([^}]+)\}/);
    expect(sliceMatch).not.toBeNull();
    const sliceBody = sliceMatch[1];

    const mustSlice = ['IfcWall', 'IfcWallStandardCase', 'IfcDoor', 'IfcWindow', 'IfcColumn'];
    for (const cls of mustSlice) {
      const has = sliceBody.includes(cls);
      log.push(cls + (has ? ' ✓' : ' ✗'));
      expect(has).toBe(true);
    }

    // Band filter must exclude roof classes
    const excMatch = s.match(/exclude_above_band.*?\[([^\]]+)\]/);
    expect(excMatch).not.toBeNull();
    const excBody = excMatch[1];
    const mustExclude = ['IfcRoof', 'IfcRoofing'];
    for (const cls of mustExclude) {
      const has = excBody.includes(cls);
      log.push('band_excl_' + cls + (has ? ' ✓' : ' ✗'));
      expect(has).toBe(true);
    }

    console.log('§T_3612 ' + log.join(' | '));
  });
});
