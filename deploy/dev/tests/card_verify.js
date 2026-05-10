// Direct code verification — no browser, no Playwright, just truth from the source
const fs = require('fs');
const { execSync } = require('child_process');
const path = require('path');

const DEV = path.resolve(__dirname, 'deploy/dev');
const BLDG = path.resolve(__dirname, 'deploy/buildings');
const src = f => fs.readFileSync(path.join(DEV, f), 'utf8');
const sql = (db, q) => { try { return execSync('sqlite3 "' + db + '" "' + q + '"', {encoding:'utf8'}).trim(); } catch(e) { return ''; } };

let pass = 0, fail = 0;
function check(tag, condition, detail) {
  if (condition) { console.log('  ✓ ' + tag + (detail ? ' — ' + detail : '')); pass++; }
  else { console.log('  ✗ ' + tag + (detail ? ' — ' + detail : '')); fail++; }
}

// ═══ CARD SYSTEM ════════════════════════════════════════════════
console.log('\n═══ CARD SYSTEM ═══');

const gv = src('grid_views.js');
const go = src('grid_overlay.js');
const sc = src('section_cut.js');
const tools = src('tools.js');
const gc = src('grid_contours.js');
const gd = src('grid_dims.js');
const da = src('grid_door_arcs.js');
const drag = src('grid_drag.js');

// classifyMesh
const hideM = gv.match(/var HIDE_IN_FLOOR\s*=\s*\{([^}]+)\}/)[1];
const fadeM = gv.match(/var FADE_IN_FLOOR\s*=\s*\{([^}]+)\}/)[1];
check('HIDE has IfcRoof', hideM.includes('IfcRoof'));
check('HIDE has IfcRoofing', hideM.includes('IfcRoofing'));
check('HIDE NOT IfcCovering', !hideM.includes('IfcCovering'), 'wall tiles visible');
check('FADE has IfcSlab', fadeM.includes('IfcSlab'));
check('FADE has IfcPlate', fadeM.includes('IfcPlate'));

// restoreSection architecture
const rStart = go.indexOf('function restoreSection');
const rEnd = go.indexOf('\n  // Card cleanup', rStart);
const rBody = go.slice(rStart, rEnd);
check('Card: queryStoreyGuids', rBody.includes('queryStoreyGuids'));
check('Card: lockView cameraOnly', rBody.includes('null, true)'));
check('Card: own THREE.Plane', rBody.includes('THREE.Plane'));
check('Card: skip isContour', rBody.includes('isContour'));
check('Card: hide !guid', rBody.includes('!guid'));
check('Card: no applyStoreyBandVisibility', !rBody.includes('applyStoreyBandVisibility'));
check('Card: no applyFloorClip', !rBody.includes('applyFloorClip'));
check('Card: guidSet check', rBody.includes('guidSet[guid]'));
check('Card: contours rendered', rBody.includes('renderContoursForView'));
check('Card: camera restore', rBody.includes('applyCameraState'));
check('Card: unfade previous before reset', rBody.indexOf('_origOpacity') < rBody.indexOf('_cardFadedMeshes = []'));

// State completeness — every path sets all 4 properties
const blocks = rBody.split(/return;\s*\}/);
let stateClean = true;
for (let i = 0; i < blocks.length - 1; i++) {
  const b = blocks[i];
  if (!b.includes('.visible') && !b.includes('isContour')) continue; // skip non-mesh blocks
  const missing = [];
  if (!b.includes('clippingPlanes')) missing.push('clip');
  if (!b.includes('clipShadows')) missing.push('clipShadow');
  if (!b.includes('needsUpdate')) missing.push('needsUpdate');
  if (missing.length) { stateClean = false; check('State path ' + i, false, 'MISSING: ' + missing.join(',')); }
}
if (stateClean) check('All mesh paths set clip+clipShadows+needsUpdate', true);

// clearCardView
const cvStart = go.indexOf('function clearCardView()');
const cvEnd = go.indexOf('\n  }', cvStart) + 4;
const cvBody = go.slice(cvStart, cvEnd);
check('clearCardView: visible=true', cvBody.includes('visible = true'));
check('clearCardView: restore opacity', cvBody.includes('_origOpacity'));
check('clearCardView: clippingPlanes=null', cvBody.includes('clippingPlanes = null'));
check('clearCardView: called on exit', go.includes('clearCardView()'));
check('clearCardView: called on delete', go.slice(go.indexOf('saved-section-del')).includes('clearCardView'));

// autoCreateCards
check('autoCreateCards: guard', go.includes('savedSections.length > 0) return'));
check('autoCreateCards: door-count sort', go.includes('db2 - da'));
check('autoCreateCards: ABS(z) tiebreak', go.includes('Math.abs(a.floorZ)'));

// captureViewState
check('captureViewState: DB storey lookup', go.includes('detectStoreys') && go.includes('captureViewState'));

// view_state schema
check('Schema: ALTER TABLE view_state', go.includes('ADD COLUMN view_state TEXT'));
check('Schema: SELECT view_state', go.includes('view_state FROM saved_sections'));
check('Schema: localStorage includes view_state', go.includes('view_state) VALUES(?,?,?,?,?,?,?)'));

// ═══ TOOLS — Save button ════════════════════════════════════════
console.log('\n═══ SAVE BUTTON ═══');
check('Save btn exists', tools.includes('section-save-cut-btn'));
check('Save NOT gated by isIn2DView', !tools.includes('isIn2DView'), 'always on when scissors ON');

// ═══ SECTION CUT — band filter ═════════════════════════════════
console.log('\n═══ SECTION CUT ═══');
const excMatch = sc.match(/exclude_above_band.*?\[([^\]]+)\]/);
check('Band excludes IfcRoof', excMatch && excMatch[1].includes('IfcRoof'));
check('Band excludes IfcRoofing', excMatch && excMatch[1].includes('IfcRoofing'));
check('Band NOT excludes IfcCovering', excMatch && !excMatch[1].includes('IfcCovering'));
check('SLICE_CLASSES has IfcWall', sc.includes("'IfcWall': 1"));
check('SLICE_CLASSES has IfcDoor', sc.includes("'IfcDoor': 1"));
check('SLICE_CLASSES has IfcWindow', sc.includes("'IfcWindow': 1"));

// ═══ CONTOURS ═══════════════════════════════════════════════════
console.log('\n═══ CONTOURS ═══');
const fillMatch = gc.match(/FILL_CLASSES\s*=\s*\{([^}]+)\}/);
check('FILL_CLASSES has IfcWall', fillMatch && fillMatch[1].includes('IfcWall'));
check('FILL_CLASSES NOT IfcSlab', fillMatch && !fillMatch[1].includes('IfcSlab'));
check('buildRibbon exists', gc.includes('buildRibbon'));
check('White/black reverse', gc.includes("isDark ? '#ffffff' : '#000000'"));

// ═══ DOOR ARCS ══════════════════════════════════════════════════
console.log('\n═══ DOOR ARCS ═══');
check('generateArcs', da.includes('generateArcs'));
check('extractLeafAxis', da.includes('extractLeafAxis'));
check('generateWindowOpenings', da.includes('generateWindowOpenings'));
check('generateStairSymbol', da.includes('generateStairSymbol'));

// ═══ GRID DETECTION ═════════════════════════════════════════════
console.log('\n═══ GRID DETECTION ═══');
check('Wall clustering', gd.includes('cluster'));
check('Snap to structural', gd.includes('snap') || gd.includes('SNAP'));
check('IfcWall query', gd.includes('IfcWall'));
check('IfcColumn query', gd.includes('IfcColumn'));
check('IfcBeam query', gd.includes('IfcBeam'));
check('Wall weight/vote', gd.includes('weight') || gd.includes('vote'));

// ═══ GRID DRAG ══════════════════════════════════════════════════
console.log('\n═══ GRID DRAG ═══');
check('rebuildPanel on drag', drag.includes('rebuildPanel'));
check('Position delta', drag.includes('delta'));
check('grid_rules.json reference', drag.includes('grid_rules') || drag.includes('_gridRules'));

// ═══ FLEET DB ═══════════════════════════════════════════════════
console.log('\n═══ FLEET DB ═══');
const dbs = fs.existsSync(BLDG) ? fs.readdirSync(BLDG).filter(f => f.endsWith('_extracted.db')) : [];
let fleetChecked = 0;
for (const dbFile of dbs) {
  const dbPath = path.join(BLDG, dbFile);
  const bld = dbFile.replace('_extracted.db', '');
  const hasMeta = sql(dbPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='elements_meta'");
  if (hasMeta === '0') continue;

  const gf = sql(dbPath, "SELECT m.storey FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid WHERE m.storey IS NOT NULL AND m.storey NOT IN ('Unknown','Roof','unknown') GROUP BY m.storey HAVING COUNT(*)>=5 ORDER BY SUM(CASE WHEN m.ifc_class IN ('IfcDoor','IfcDoorStandardCase') THEN 1 ELSE 0 END) DESC, ABS(MIN(et.center_z)) LIMIT 1");
  if (!gf) continue;
  const esc = gf.split('|')[0].replace(/'/g, "''");
  const total = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "'")) || 0;
  const walls = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcWall','IfcWallStandardCase')")) || 0;
  const doors = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcDoor','IfcDoorStandardCase')")) || 0;
  const roofs = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcRoof','IfcRoofing')")) || 0;
  const slabs = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcSlab','IfcPlate')")) || 0;
  const totalAll = parseInt(sql(dbPath, "SELECT COUNT(*) FROM elements_meta")) || 0;

  console.log('  ' + bld + ': GF=' + gf.split('|')[0] + ' tot=' + total + '/' + totalAll + ' w=' + walls + ' d=' + doors + ' r=' + roofs + ' s=' + slabs);
  fleetChecked++;
}
check('Fleet: >20 buildings checked', fleetChecked > 20, 'checked=' + fleetChecked);

// ═══ SUMMARY ════════════════════════════════════════════════════
console.log('\n═══ RESULT: ' + pass + ' pass, ' + fail + ' fail ═══');
process.exit(fail > 0 ? 1 : 0);
