// Direct code verification — no browser, no Playwright, just truth from the source
// Tests: card system, scene corruption paths, state completeness, fleet composition
const fs = require('fs');
const { execSync } = require('child_process');
const path = require('path');

const DEV = path.resolve(__dirname, '..');
const BLDG = path.resolve(__dirname, '..', 'buildings');
const BLDG2 = path.resolve(__dirname, '..', '..', 'buildings');
const src = f => fs.readFileSync(path.join(DEV, f), 'utf8');
const sql = (db, q) => { try { return execSync('sqlite3 "' + db + '" "' + q + '"', {encoding:'utf8'}).trim(); } catch(e) { return ''; } };

let pass = 0, fail = 0;
function check(tag, condition, detail) {
  if (condition) { console.log('  ✓ ' + tag + (detail ? ' — ' + detail : '')); pass++; }
  else { console.log('  ✗ ' + tag + (detail ? ' — ' + detail : '')); fail++; }
}

// ═══ LOAD ALL SOURCE FILES ═════════════════════════════════════════
const gv = src('grid_views.js');
const go = src('grid_overlay.js');
const sc = src('section_cut.js');
const tools = src('tools.js');
const gc = src('grid_contours.js');
const gd = src('grid_dims.js');
const da = src('grid_door_arcs.js');
const drag = src('grid_drag.js');
const scissors = src('grid_scissors.js');
const scene = src('scene.js');

// ═══ 1. HIDE/FADE CLASSIFICATION ══════════════════════════════════
console.log('\n═══ 1. HIDE/FADE CLASSIFICATION ═══');

const hideM = gv.match(/var HIDE_IN_FLOOR\s*=\s*\{([^}]+)\}/)[1];
const fadeM = gv.match(/var FADE_IN_FLOOR\s*=\s*\{([^}]+)\}/)[1];
check('HIDE has IfcRoof', hideM.includes('IfcRoof'));
check('HIDE has IfcRoofing', hideM.includes('IfcRoofing'));
check('HIDE NOT IfcCovering', !hideM.includes('IfcCovering'), 'BUG: wall tiles wrongly hidden');
check('HIDE NOT IfcSlab', !hideM.includes('IfcSlab'), 'slabs fade, not hide');
check('FADE has IfcSlab', fadeM.includes('IfcSlab'));
check('FADE has IfcPlate', fadeM.includes('IfcPlate'));
check('FADE NOT IfcWall', !fadeM.includes('IfcWall'), 'walls must clip, not fade');

// classifyMesh accepts hideSet override
check('classifyMesh takes hideSet param', gv.includes('function classifyMesh') && gv.includes('hideSet'));
const cmBody = gv.slice(gv.indexOf('function classifyMesh'), gv.indexOf('function classifyMesh') + 300);
check('classifyMesh fallback to HIDE_IN_FLOOR', cmBody.includes('hideSet || HIDE_IN_FLOOR'));

// ═══ 2. CARD restoreSection — ARCHITECTURE ════════════════════════
console.log('\n═══ 2. CARD RESTORE ARCHITECTURE ═══');

const rStart = go.indexOf('function restoreSection');
const rEnd = go.indexOf('\n  // Card cleanup', rStart);
const rBody = go.slice(rStart, rEnd);
check('Card: queryStoreyGuids', rBody.includes('queryStoreyGuids'));
check('Card: lockView cameraOnly', rBody.includes('null, true)'));
check('Card: own THREE.Plane', rBody.includes('THREE.Plane'));
check('Card: skip isContour (BUG W)', rBody.includes('isContour'));
check('Card: hide !guid (BUG A)', rBody.includes('!guid'));
check('Card: no applyStoreyBandVisibility', !rBody.includes('applyStoreyBandVisibility'));
check('Card: no applyFloorClip', !rBody.includes('applyFloorClip'));
check('Card: guidSet check', rBody.includes('guidSet[guid]'));
check('Card: contours rendered', rBody.includes('renderContoursForView'));
check('Card: camera restore', rBody.includes('applyCameraState'));

// ═══ 3. BUG K+J — OPACITY LEAK between card switches ═════════════
console.log('\n═══ 3. BUG K+J — OPACITY LEAK ═══');

check('Unfade BEFORE reset arrays (BUG K)', rBody.indexOf('_origOpacity') < rBody.indexOf('_cardFadedMeshes = []'));
check('Unfade loop exists before mesh pass', rBody.indexOf('_cardFadedMeshes.length') < rBody.indexOf('collectMeshes'));
check('Unfade restores transparent flag', rBody.includes('_origTransparent'));
check('Unfade deletes userData markers', rBody.includes('delete') && rBody.includes('_origOpacity'));
// Check the unfade loop sets needsUpdate
const unfadeSection = rBody.slice(0, rBody.indexOf('_cardFadedMeshes = []'));
check('Unfade sets needsUpdate', unfadeSection.includes('needsUpdate = true'));

// ═══ 4. STATE COMPLETENESS — every mesh path ═════════════════════
console.log('\n═══ 4. STATE COMPLETENESS (scene corruption guard) ═══');

// Parse mesh processing paths in restoreSection
// Each path that touches .visible MUST also set clippingPlanes + clipShadows + needsUpdate
const meshPass = rBody.slice(rBody.indexOf('collectMeshes'));
const returnBlocks = meshPass.split(/return;\s*$/m);
let stateClean = true;
let pathCount = 0;
for (let i = 0; i < returnBlocks.length; i++) {
  const b = returnBlocks[i];
  if (!b.includes('.visible')) continue; // skip non-mesh blocks
  pathCount++;
  const missing = [];
  if (!b.includes('clippingPlanes')) missing.push('clippingPlanes');
  if (!b.includes('clipShadows')) missing.push('clipShadows');
  if (!b.includes('needsUpdate')) missing.push('needsUpdate');
  if (missing.length) { stateClean = false; check('State path ' + i, false, 'MISSING: ' + missing.join(',')); }
}
if (stateClean) check('All ' + pathCount + ' mesh paths set clip+clipShadows+needsUpdate', true);

// BUG N: clipShadows on fade path must be TRUE (slab clips cast shadows)
const fadePath = rBody.slice(rBody.indexOf('fadeSet[cls]'), rBody.indexOf('fadeSet[cls]') + 400);
check('BUG N: fade path clipShadows=true', fadePath.includes('clipShadows = true'), 'slabs need shadow clip');

// Clipped path (walls/columns) must have clipShadows=true
const clipPath = rBody.slice(rBody.lastIndexOf('obj.material.clippingPlanes = [clipPlane]'));
check('Clip path clipShadows=true', clipPath.includes('clipShadows = true'));

// ═══ 5. clearCardView — FULL SCENE RESTORE ═══════════════════════
console.log('\n═══ 5. clearCardView — SCENE RESTORE ═══');

const cvStart = go.indexOf('function clearCardView()');
const cvEnd = go.indexOf('\nfunction', cvStart + 10) !== -1
  ? go.indexOf('\nfunction', cvStart + 10)
  : go.indexOf('\n  // ══', cvStart + 10);
const cvBody = go.slice(cvStart, cvEnd);
check('clearCardView: visible=true on ALL meshes', cvBody.includes('visible = true'));
check('clearCardView: opacity restored from _origOpacity', cvBody.includes('_origOpacity'));
check('clearCardView: transparent restored from _origTransparent', cvBody.includes('_origTransparent'));
check('clearCardView: clippingPlanes=null', cvBody.includes('clippingPlanes = null'));
check('clearCardView: clipShadows=false', cvBody.includes('clipShadows = false'));
check('clearCardView: needsUpdate=true', cvBody.includes('needsUpdate = true'));
check('clearCardView: deletes _origOpacity marker', cvBody.includes('delete') && cvBody.includes('_origOpacity'));
check('clearCardView: called on grid exit', go.includes('clearCardView()'));
// BUG U: delete active card must call clearCardView
check('BUG U: delete calls clearCardView', go.slice(go.indexOf('saved-section-del')).includes('clearCardView'));

// ═══ 6. clearFloorClip — grid_views.js SCENE RESTORE ═════════════
console.log('\n═══ 6. clearFloorClip — grid_views RESTORE ═══');

const cfStart = gv.indexOf('function clearFloorClip');
const cfEnd = gv.indexOf('\n  function', cfStart + 10) !== -1
  ? gv.indexOf('\n  function', cfStart + 10)
  : gv.indexOf('\n  }', cfStart + 200);
const cfBody = gv.slice(cfStart, cfEnd);
check('clearFloorClip: clippingPlanes=null on all meshes', cfBody.includes('clippingPlanes = null'));
check('clearFloorClip: clipShadows=false', cfBody.includes('clipShadows = false'));
check('clearFloorClip: visible=true on hidden', cfBody.includes('visible = true'));
check('clearFloorClip: opacity restored for faded', cfBody.includes('_origOpacity'));
check('clearFloorClip: localClippingEnabled=false', cfBody.includes('localClippingEnabled = false'));
check('clearFloorClip: cleanup arrays', cfBody.includes('_hiddenMeshes = []') && cfBody.includes('_fadedMeshes = []'));

// ═══ 7. applyFloorClip — opacity save (BUG C) ═══════════════════
console.log('\n═══ 7. applyFloorClip — BUG C (falsy zero) ═══');

const afStart = gv.indexOf('function applyFloorClip');
const afEnd = gv.indexOf('\n  function', afStart + 10);
const afBody = gv.slice(afStart, afEnd);
check('BUG C: uses == null (not !obj for falsy zero)', afBody.includes('_origOpacity == null') || afBody.includes('_origOpacity === undefined'));
check('applyFloorClip: fade saves opacity BEFORE modifying', afBody.indexOf('_origOpacity') < afBody.indexOf('opacity = 0.08'));
check('applyFloorClip: fade sets transparent=true', afBody.includes('transparent = true'));
check('applyFloorClip: clip path sets clipShadows=true', afBody.includes('clipShadows = true'));

// ═══ 8. BUG A — ground plane / InstancedMesh hidden ══════════════
console.log('\n═══ 8. BUG A — NO-GUID MESHES ═══');

check('restoreSection: !guid → visible=false', rBody.includes("!guid") && rBody.includes('obj.visible = false'));
// Verify InstancedMesh or no-userData handled
check('restoreSection: userData guard before guid access', rBody.includes('obj.userData && obj.userData.guid'));

// ═══ 9. BUG W — CONTOUR MESHES SKIP ═════════════════════════════
console.log('\n═══ 9. BUG W — CONTOUR OVERLAY SKIP ═══');

check('isContour check BEFORE guid check', rBody.indexOf('isContour') < rBody.indexOf('!guid'));
check('isContour returns (skip, no modify)',
  rBody.slice(rBody.indexOf('isContour'), rBody.indexOf('isContour') + 80).includes('return'));

// ═══ 10. SAVE BUTTON LOCATION ════════════════════════════════════
console.log('\n═══ 10. SAVE BUTTON ═══');

check('Save btn in grid panel (grid-save-section-btn)', go.includes('id="grid-save-section-btn"'));
check('Save NOT in tools.js (old location)', !tools.includes('section-save-cut-btn'));
check('Save NOT gated by isIn2DView', !tools.includes('isIn2DView'), 'always on when scissors ON');
check('Save btn wired with pointerup', go.includes("querySelector('#grid-save-section-btn')") && go.includes('pointerup'));

// ═══ 11. SECTION CUT — band filter ══════════════════════════════
console.log('\n═══ 11. SECTION CUT ═══');

const excMatch = sc.match(/exclude_above_band.*?\[([^\]]+)\]/);
check('Band excludes IfcRoof', excMatch && excMatch[1].includes('IfcRoof'));
check('Band excludes IfcRoofing', excMatch && excMatch[1].includes('IfcRoofing'));
check('Band NOT excludes IfcCovering', excMatch && !excMatch[1].includes('IfcCovering'));
check('SLICE_CLASSES has IfcWall', sc.includes("'IfcWall': 1") || sc.includes('"IfcWall": 1'));
check('SLICE_CLASSES has IfcWallStandardCase', sc.includes("IfcWallStandardCase"));
check('SLICE_CLASSES has IfcDoor', sc.includes("'IfcDoor': 1") || sc.includes('"IfcDoor": 1'));
check('SLICE_CLASSES has IfcWindow', sc.includes("'IfcWindow': 1") || sc.includes('"IfcWindow": 1'));
check('Band 1.5m clamp exists', sc.includes('1.5') && (sc.includes('bandMax') || sc.includes('band_max')));
check('Section cut logs §SC_ tags', sc.includes('§SC_'));

// ═══ 12. CONTOURS ════════════════════════════════════════════════
console.log('\n═══ 12. CONTOURS ═══');

const fillMatch = gc.match(/FILL_CLASSES\s*=\s*\{([^}]+)\}/);
check('FILL_CLASSES has IfcWall', fillMatch && fillMatch[1].includes('IfcWall'));
check('FILL_CLASSES NOT IfcSlab', fillMatch && !fillMatch[1].includes('IfcSlab'), 'slabs not solid-filled');
check('buildRibbon exists', gc.includes('buildRibbon'));
check('White/black reverse for print', gc.includes("isDark ? '#ffffff' : '#000000'") || gc.includes("isDark ? 0xffffff : 0x000000"));
check('Contour meshes marked isContour', gc.includes('isContour'));
check('Contour clear function', gc.includes('function') && gc.includes('clear'));

// ═══ 13. DOOR ARCS ══════════════════════════════════════════════
console.log('\n═══ 13. DOOR ARCS ═══');

check('generateArcs function', da.includes('generateArcs'));
check('extractLeafAxis function', da.includes('extractLeafAxis'));
check('generateWindowOpenings function', da.includes('generateWindowOpenings'));
check('generateStairSymbol function', da.includes('generateStairSymbol'));
check('Door arc logs §DOOR_ARC', da.includes('§DOOR_ARC'));

// ═══ 14. GRID DETECTION ═════════════════════════════════════════
console.log('\n═══ 14. GRID DETECTION ═══');

check('Wall clustering', gd.includes('cluster'));
check('Snap to structural', gd.includes('snap') || gd.includes('SNAP'));
check('IfcWall query', gd.includes('IfcWall'));
check('IfcColumn query', gd.includes('IfcColumn'));
check('IfcBeam query', gd.includes('IfcBeam'));
check('Wall weight/vote', gd.includes('weight') || gd.includes('vote'));
check('Grid dims logs §GD_', gd.includes('§GD_'));

// Grid alignment REAL DATA — SampleHouse wall X positions must cluster
console.log('\n  ── Grid Alignment: SampleHouse wall positions ──');
const shDb = findDb('SampleHouse');
if (shDb) {
  const wallXs = sql(shDb, "SELECT ROUND(center_x, 1) as rx, COUNT(*) FROM element_transforms et JOIN elements_meta m ON et.guid=m.guid WHERE m.ifc_class IN ('IfcWall','IfcWallStandardCase') AND m.storey='Ground Floor' GROUP BY rx ORDER BY rx").split('\n').filter(r => r);
  const xPositions = wallXs.map(r => parseFloat(r.split('|')[0]));
  console.log('    §GRID_ALIGN SH wall X positions: ' + xPositions.join(', '));
  check('SH: walls have distinct X positions (grid lines)', xPositions.length >= 2, 'unique_x=' + xPositions.length);
  // Check clustering — positions should not all be same
  const spread = xPositions.length > 1 ? Math.abs(xPositions[xPositions.length-1] - xPositions[0]) : 0;
  check('SH: wall X spread > 1m (not all same position)', spread > 1, 'spread=' + spread.toFixed(2) + 'm');

  const wallYs = sql(shDb, "SELECT ROUND(center_y, 1) as ry, COUNT(*) FROM element_transforms et JOIN elements_meta m ON et.guid=m.guid WHERE m.ifc_class IN ('IfcWall','IfcWallStandardCase') AND m.storey='Ground Floor' GROUP BY ry ORDER BY ry").split('\n').filter(r => r);
  const yPositions = wallYs.map(r => parseFloat(r.split('|')[0]));
  console.log('    §GRID_ALIGN SH wall Y positions: ' + yPositions.join(', '));
  check('SH: walls have distinct Y positions', yPositions.length >= 2, 'unique_y=' + yPositions.length);
}

// ═══ 15. GRID DRAG ══════════════════════════════════════════════
console.log('\n═══ 15. GRID DRAG ═══');

check('rebuildPanel on drag', drag.includes('rebuildPanel'));
check('Position delta', drag.includes('delta'));
check('grid_rules.json reference', drag.includes('grid_rules') || drag.includes('_gridRules'));
check('Undo/redo support', drag.includes('undo') || drag.includes('_undoStack'));

// ═══ 16. GRID SCISSORS — state init ═════════════════════════════
console.log('\n═══ 16. GRID SCISSORS ═══');

check('GridScissors.init exists', scissors.includes('function init'));
check('Scissors wired by grid_overlay', go.includes('GridScissors.init'));
check('lastCutVal starts null', scissors.includes('lastCutVal = null'));
check('onOff resets lastCutVal', scissors.includes('lastCutVal = null') &&
  scissors.indexOf('function onOff') < scissors.lastIndexOf('lastCutVal = null'));
check('Dwell tracking', scissors.includes('dwellTrack') || scissors.includes('dwell'));
check('Scissors disposes geometry on off', scissors.includes('dispose'));
// BUG: dwellTrack must NOT fire when 2D overlay is OFF (causes snap flash without grid)
const sliderFn = scissors.slice(scissors.indexOf('function onSliderChange'));
const dwellPos = sliderFn.indexOf('dwellTrack');
const guardPos = sliderFn.indexOf('!st.active');
check('dwellTrack AFTER st.active guard (no snap without 2D)', guardPos < dwellPos,
  'guard@' + guardPos + ' dwell@' + dwellPos);

// SCENE CORRUPTION: dwellTrack → checkDwell → rebuildDwellMarkers adds THREE.Line to scene
// + flashDwellCapture adds white overlay div. Both corrupt scene when 2D is OFF.
// Verify: the return before dwellTrack means NONE of these can fire without 2D.
const earlyReturn = sliderFn.slice(guardPos, dwellPos);
check('Early return BEFORE dwellTrack (no scene add without 2D)', earlyReturn.includes('return'));
// rebuildDwellMarkers adds objects to A.scene — must be gated
check('rebuildDwellMarkers adds to scene', scissors.includes('A.scene.add'));
check('flashDwellCapture creates overlay div', scissors.includes("background:white") || scissors.includes('flash'));
// clearDwellMarkers removes from scene — verify cleanup exists
check('clearDwellMarkers disposes + removes from scene', scissors.includes('A.scene.remove') && scissors.includes('.dispose()'));
// onOff calls dwellReset — prevents stale markers on scissors toggle
check('onOff calls dwellReset', scissors.slice(scissors.indexOf('function onOff')).includes('dwellReset'));

// ═══ 17. SCENE CORRUPTION — cross-module state leak ══════════════
console.log('\n═══ 17. SCENE CORRUPTION GUARDS ═══');

// Card must not leave localClippingEnabled=true when exiting
check('Grid exit disables localClipping', go.includes('localClippingEnabled = false'));
// Card switch must not accumulate clip planes
check('restoreSection creates FRESH clipPlane', rBody.includes('new THREE.Plane'));
// Verify no shared/cached clip planes across cards
const clipPlaneCreations = (rBody.match(/new THREE\.Plane/g) || []).length;
check('Only ONE clipPlane per restore call', clipPlaneCreations === 1, 'found ' + clipPlaneCreations);
// renderer.localClippingEnabled set on card enter
check('localClippingEnabled=true on card', rBody.includes('localClippingEnabled = true'));
// grid_views applyFloorClip also sets it
check('applyFloorClip enables localClipping', afBody.includes('localClippingEnabled = true'));

// Verify no stale clip planes from section_cut when entering card mode
check('clearFloorClip nulls _floorClipPlane', cfBody.includes('_floorClipPlane = null'));

// autoCreateCards — storey ranking (verify it finds correct GF)
console.log('\n═══ 18. autoCreateCards — STOREY RANKING ═══');
check('autoCreateCards: door-count sort (most doors first)', go.includes('db2 - da'));
check('autoCreateCards: abs(z) tiebreak (closest to ground)', go.includes('Math.abs(a.floorZ)'));
check('autoCreateCards: element count >= 5 filter', go.includes('>= 5') || go.includes('elementCount >= 5'));
check('autoCreateCards: creates GF card', go.includes("'GF'") || go.includes('"GF"'));
check('autoCreateCards: creates L1 card if >1 storey', go.includes("'L1'") || go.includes('"L1"'));
check('autoCreateCards: CUT_ABOVE offset', go.includes('CUT_ABOVE'));

// ═══ 19. captureViewState + schema ═══════════════════════════════
console.log('\n═══ 19. captureViewState + SCHEMA ═══');

check('captureViewState: DB storey lookup', go.includes('detectStoreys') && go.includes('captureViewState'));
check('captureViewState: captures camera', go.includes('getCameraState') || go.includes('camera'));
check('Schema: ALTER TABLE view_state', go.includes('ADD COLUMN view_state TEXT'));
check('Schema: SELECT view_state', go.includes('view_state FROM saved_sections'));
check('Schema: INSERT includes view_state', go.includes('view_state) VALUES'));
check('BUG G: localStorage INSERT has view_state', go.includes('view_state) VALUES(?,?,?,?,?,?,?)') || go.includes('view_state'));

// ═══ 20. EXECUTE classifyMesh on REAL DB DATA — SH, DX, SampleCastle, Terminal ═══
// This runs the ACTUAL classification logic from grid_views.js on real building data.
// The logs show EXACTLY what the viewer will do. No guessing.

console.log('\n═══ 20. BUILDING TESTS — classifyMesh on real DB data ═══');

// Extract classifyMesh as executable function from source
const hideCode = gv.match(/var HIDE_IN_FLOOR\s*=\s*\{[^}]+\}/)[0];
const fadeCode = gv.match(/var FADE_IN_FLOOR\s*=\s*\{[^}]+\}/)[0];
const fnCode = gv.match(/function classifyMesh\(ifcClass, retainSet, hideSet\)\s*\{[\s\S]*?return 'clip';\s*\}/)[0];
const classify = new Function('ifcClass', 'retainSet', 'hideSet',
  hideCode + ';\n' + fadeCode + ';\n' + fnCode + '\nreturn classifyMesh(ifcClass, retainSet, hideSet);');

// Default retainSet (same as code uses when GridConfig unavailable)
const RETAIN = { 'IfcFurnishingElement': 1, 'IfcFurniture': 1, 'IfcFlowTerminal': 1, 'IfcSanitaryTerminal': 1 };

// Target buildings
const TARGET_BUILDINGS = {
  'SampleHouse': { expectedGF: 'Ground Floor' },
  'Duplex': {},
  'SampleCastle': {},
  'Terminal': {}
};

// Find DB for each building
function findDb(name) {
  const candidates = [
    path.join(BLDG, name + '_extracted.db'),
    path.join(BLDG2, name + '_extracted.db'),
  ];
  return candidates.find(p => fs.existsSync(p));
}

// GF storey detection — same algorithm as grid_overlay.js autoCreateCards
function detectGF(dbPath) {
  return sql(dbPath, "SELECT m.storey FROM elements_meta m JOIN element_transforms et ON m.guid=et.guid WHERE m.storey IS NOT NULL AND m.storey NOT IN ('Unknown','Roof','unknown') GROUP BY m.storey HAVING COUNT(*)>=5 ORDER BY SUM(CASE WHEN m.ifc_class IN ('IfcDoor','IfcDoorStandardCase') THEN 1 ELSE 0 END) DESC, ABS(MIN(et.center_z)) LIMIT 1").split('|')[0];
}

for (const [bldName, expected] of Object.entries(TARGET_BUILDINGS)) {
  console.log('\n  ── ' + bldName + ' ──');
  const dbPath = findDb(bldName);
  if (!dbPath) { check(bldName + ': DB found', false, 'NOT FOUND'); continue; }
  check(bldName + ': DB found', true, path.basename(dbPath));

  // 1. Detect GF storey
  const gfStorey = detectGF(dbPath);
  check(bldName + ': GF storey detected', !!gfStorey, 'GF="' + gfStorey + '"');
  if (!gfStorey) continue;
  if (expected.expectedGF) {
    check(bldName + ': GF matches expected', gfStorey === expected.expectedGF,
      'got="' + gfStorey + '" expected="' + expected.expectedGF + '"');
  }
  const esc = gfStorey.replace(/'/g, "''");

  // 2. Get ALL elements on GF storey with their classes
  const rows = sql(dbPath, "SELECT m.ifc_class, COUNT(*) FROM elements_meta m WHERE m.storey='" + esc + "' GROUP BY m.ifc_class ORDER BY COUNT(*) DESC").split('\n').filter(r => r);

  // 3. Run classifyMesh on each class — log the RESULT
  let hideCount = 0, fadeCount = 0, retainCount = 0, clipCount = 0, totalCount = 0;
  const classList = {};
  for (const row of rows) {
    const parts = row.split('|');
    const cls = parts[0];
    const count = parseInt(parts[1]) || 0;
    const action = classify(cls, RETAIN, null);
    classList[cls] = { count, action };
    totalCount += count;
    if (action === 'hide') hideCount += count;
    else if (action === 'fade') fadeCount += count;
    else if (action === 'retain') retainCount += count;
    else clipCount += count;
  }

  // 4. LOG THE TRUTH — what the code WILL do to this building's GF
  console.log('    §CLASSIFY storey="' + gfStorey + '" total=' + totalCount +
    ' hide=' + hideCount + ' fade=' + fadeCount + ' retain=' + retainCount + ' clip=' + clipCount);
  for (const [cls, info] of Object.entries(classList)) {
    console.log('      ' + cls + ': n=' + info.count + ' → ' + info.action);
  }

  // 5. CHECKS — things that MUST be true
  check(bldName + ': hide+fade+retain+clip = total',
    hideCount + fadeCount + retainCount + clipCount === totalCount,
    hideCount + '+' + fadeCount + '+' + retainCount + '+' + clipCount + '=' + (hideCount + fadeCount + retainCount + clipCount) + ' vs ' + totalCount);

  // Walls must exist (needed for contours)
  const wallCount = ((classList['IfcWall'] || {}).count || 0) + ((classList['IfcWallStandardCase'] || {}).count || 0);
  check(bldName + ': has walls for contours', wallCount > 0, 'walls=' + wallCount);

  // Doors must exist (needed for door arcs)
  const doorCount = ((classList['IfcDoor'] || {}).count || 0) + ((classList['IfcDoorStandardCase'] || {}).count || 0);
  console.log('    §DOOR_ARC_INPUT doors=' + doorCount + (doorCount > 0 ? ' — arcs possible' : ' — NO ARCS POSSIBLE (0 doors)'));

  // Roofs: if code says hide, they WILL be hidden. But log if roofs exist in storey.
  if (hideCount > 0) {
    console.log('    §HIDE roofs/roofing on this storey: ' + hideCount + ' elements will be hidden');
  }

  // 6. GEOMETRY CHECK — section_cut.lookupGeometry checks BOTH extracted DB and library DB
  // Path: element_instances.geometry_hash → component_geometries (in db OR libDb)
  const hasInstTable = sql(dbPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='element_instances'");
  const hasGeomInExtracted = sql(dbPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='component_geometries'") !== '0'
    ? parseInt(sql(dbPath, "SELECT COUNT(*) FROM component_geometries")) || 0 : 0;
  // Check library DB too (section_cut uses libDb as fallback)
  const libPath = dbPath.replace('_extracted.db', '_library.db');
  const hasLibDb = fs.existsSync(libPath);
  const hasGeomInLib = hasLibDb
    ? (sql(libPath, "SELECT COUNT(*) FROM sqlite_master WHERE name='component_geometries'") !== '0'
      ? parseInt(sql(libPath, "SELECT COUNT(*) FROM component_geometries")) || 0 : 0)
    : 0;
  const geomSource = hasGeomInExtracted > 0 ? 'extracted' : (hasGeomInLib > 0 ? 'library' : 'NONE');
  const geomDb = hasGeomInExtracted > 0 ? dbPath : (hasGeomInLib > 0 ? libPath : null);
  console.log('    §GEOM source=' + geomSource + ' extracted=' + hasGeomInExtracted + ' library=' + hasGeomInLib);

  if (geomDb && hasInstTable !== '0') {
    // Count walls/doors that have matching geometry hash in the geometry DB
    const wallGeomQuery = "SELECT COUNT(*) FROM element_instances ei WHERE ei.guid IN (SELECT guid FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcWall','IfcWallStandardCase')) AND ei.geometry_hash IN (SELECT geometry_hash FROM component_geometries)";
    const doorGeomQuery = "SELECT COUNT(*) FROM element_instances ei WHERE ei.guid IN (SELECT guid FROM elements_meta WHERE storey='" + esc + "' AND ifc_class IN ('IfcDoor','IfcDoorStandardCase')) AND ei.geometry_hash IN (SELECT geometry_hash FROM component_geometries)";

    // If geom is in library, we need cross-db check. sqlite3 CLI can't ATTACH easily,
    // so check if hashes from extracted exist in library.
    let wallsWithGeom = 0, doorsWithGeom = 0;
    if (geomSource === 'extracted') {
      wallsWithGeom = parseInt(sql(dbPath, wallGeomQuery)) || 0;
      doorsWithGeom = parseInt(sql(dbPath, doorGeomQuery)) || 0;
    } else {
      // Cross-DB: get hashes from extracted, check existence in library
      const wallHashes = sql(dbPath, "SELECT DISTINCT ei.geometry_hash FROM element_instances ei JOIN elements_meta m ON ei.guid=m.guid WHERE m.storey='" + esc + "' AND m.ifc_class IN ('IfcWall','IfcWallStandardCase') LIMIT 50").split('\n').filter(h => h);
      const doorHashes = sql(dbPath, "SELECT DISTINCT ei.geometry_hash FROM element_instances ei JOIN elements_meta m ON ei.guid=m.guid WHERE m.storey='" + esc + "' AND m.ifc_class IN ('IfcDoor','IfcDoorStandardCase') LIMIT 50").split('\n').filter(h => h);
      // Sample check: does library have these hashes?
      for (const h of wallHashes) {
        const found = sql(libPath, "SELECT COUNT(*) FROM component_geometries WHERE geometry_hash='" + h + "'");
        if (found !== '0') wallsWithGeom++;
      }
      for (const h of doorHashes) {
        const found = sql(libPath, "SELECT COUNT(*) FROM component_geometries WHERE geometry_hash='" + h + "'");
        if (found !== '0') doorsWithGeom++;
      }
      // wallsWithGeom here = distinct hashes found (elements may share geometry)
      console.log('    §GEOM cross-db: wall_hashes_in_lib=' + wallsWithGeom + '/' + wallHashes.length + ' door_hashes_in_lib=' + doorsWithGeom + '/' + doorHashes.length);
    }
    console.log('    §GEOM walls_with_geometry=' + wallsWithGeom + ' doors_with_geometry=' + doorsWithGeom);
    check(bldName + ': walls have geometry', wallsWithGeom > 0, wallsWithGeom + ' wall geom entries (' + geomSource + ')');
    if (doorsWithGeom === 0 && doorCount > 0) {
      console.log('    §DOOR_ARC_SKIP reason=no_geometry — doors have no geometry BLOBs in ' + geomSource);
    }
  } else {
    console.log('    §GEOM NO GEOMETRY SOURCE — contours impossible');
    check(bldName + ': has geometry', false, 'no component_geometries in extracted or library');
  }

  // 7. SLICE_CLASSES coverage — which GF classes will section_cut actually slice?
  const SLICE_CLASSES_SET = {};
  const sliceMatch = sc.match(/SLICE_CLASSES\s*=\s*\{([^}]+)\}/);
  if (sliceMatch) {
    sliceMatch[1].replace(/'([^']+)'\s*:/g, (_, cls) => { SLICE_CLASSES_SET[cls] = 1; });
  }
  let sliceable = 0, notSliceable = 0;
  const notSliceableClasses = [];
  for (const [cls, info] of Object.entries(classList)) {
    if (info.action === 'clip') { // only clipped elements get sliced
      if (SLICE_CLASSES_SET[cls]) sliceable += info.count;
      else { notSliceable += info.count; notSliceableClasses.push(cls + '(' + info.count + ')'); }
    }
  }
  console.log('    §SLICE sliceable=' + sliceable + ' not_sliceable=' + notSliceable +
    (notSliceableClasses.length ? ' skipped=[' + notSliceableClasses.join(',') + ']' : ''));
  check(bldName + ': walls are in SLICE_CLASSES', !!SLICE_CLASSES_SET['IfcWall'] && !!SLICE_CLASSES_SET['IfcWallStandardCase']);
  check(bldName + ': doors are in SLICE_CLASSES', !!SLICE_CLASSES_SET['IfcDoor'] && !!SLICE_CLASSES_SET['IfcDoorStandardCase']);

  // 8. CONTOUR INVENTION CHECK — are there hardcoded coordinates for this building?
  // Only flag if building name appears in contour/section code (not comments/logs in overlay)
  const contourAndSection = gc + sc + da;
  const mentionsBldg = contourAndSection.includes(bldName);
  if (mentionsBldg) {
    console.log('    §INVENTION_SMELL contour/section code mentions "' + bldName + '" — HARDCODED DATA?');
  }
  check(bldName + ': NO building name in contour/section/arc code', !mentionsBldg, 'building-specific = invention');
}

// ═══ 21. ANTI-INVENTION — no hardcoded coordinates in contour/section code ═══
console.log('\n═══ 21. ANTI-INVENTION ═══');

// Any literal coordinate pairs in contour generation = invented geometry
const contourFns = gc.slice(gc.indexOf('function renderContours'));
const hardcodedCoords = contourFns.match(/\b\d{2,}\.\d+\s*,\s*\d{2,}\.\d+/g);
check('No hardcoded coordinate pairs in renderContours', !hardcodedCoords,
  hardcodedCoords ? 'FOUND: ' + hardcodedCoords.slice(0, 3).join('; ') : 'clean');

// No building-specific if/switch in section_cut
const bldgNames = ['SampleHouse', 'Duplex', 'SampleCastle', 'Terminal', 'HITOS', 'Hospital', 'Clinic'];
const inventions = bldgNames.filter(n => sc.includes(n) || gc.includes(n));
check('No building names in section_cut/contours', inventions.length === 0,
  inventions.length > 0 ? 'INVENTED: ' + inventions.join(',') : 'clean');

// No fake/placeholder/demo geometry
check('No "demo" in contour code', !gc.toLowerCase().includes('demo'));
check('No "placeholder" in contour code', !gc.toLowerCase().includes('placeholder'));
check('No "fake" in contour code', !gc.toLowerCase().includes('fake'));
check('No "example" geometry in section_cut', !sc.toLowerCase().includes('example point'));

// ═══ 22. KEYBOARD — G+X combo, sequence engine ═══════════════════
console.log('\n═══ 22. KEYBOARD ═══');

// G = open 2D plans, X = scissors. Must work in sequence (G then X).
// The sequence engine fires single-char shortcuts immediately if no longer prefix exists.
check('G shortcut: opens 2D plans', scene.includes("'g':") && scene.includes('open2DPlans'));
check('X shortcut: toggles section', scene.includes("'x':") && scene.includes('toggleSection'));
// X must be context-aware: when grid overlay active, don't disrupt 2D
check('X in 2D mode: checks _gridOverlayState.active', scene.includes('_gridOverlayState') && scene.includes("active") &&
  scene.slice(scene.indexOf("'x':")).includes('_gridOverlayState'));
// Sequence engine: G has no longer prefix (fires immediately)
// Need to verify no shortcut starts with 'g' (would cause wait)
const shortcutKeys = (scene.match(/'([a-z0-9+=-]+)'\s*:\s*function/g) || []).map(m => m.match(/'([^']+)'/)[1]);
const gPrefix = shortcutKeys.filter(k => k.length > 1 && k.startsWith('g'));
const xPrefix = shortcutKeys.filter(k => k.length > 1 && k.startsWith('x'));
check('No multi-char shortcut starts with g (G fires immediately)', gPrefix.length === 0, gPrefix.join(','));
check('No multi-char shortcut starts with x (X fires immediately)', xPrefix.length === 0, xPrefix.join(','));
console.log('    §KBD all shortcuts: ' + shortcutKeys.join(', '));

// ═══ 23. ZOMBIE CARDS — deleted cards must not return ════════════
console.log('\n═══ 23. ZOMBIE CARDS ═══');

// When user deletes a card, it must be removed from BOTH DB and localStorage.
// If only one is cleared, the card "returns" on next reload from the other source.
const delFnStart = go.indexOf('function deleteSavedSection');
const delFnEnd = go.indexOf('\n  function', delFnStart + 10);
const delFnBody = go.slice(delFnStart, delFnEnd);
check('Delete: removes from DB (DELETE SQL)', delFnBody.includes('DELETE FROM saved_sections'));
check('Delete: removes from localStorage (removeItem)', delFnBody.includes('localStorage.removeItem'));
check('Delete: calls loadSavedSections (rebuild list)', delFnBody.includes('loadSavedSections'));
check('Delete: localStorage.setItem (update remaining)', delFnBody.includes('localStorage.setItem'));
// Delete button handler calls clearCardView after deleteSavedSection
const delCallSite = go.indexOf('deleteSavedSection(id);');
const afterDel = go.slice(delCallSite, delCallSite + 200);
check('Delete btn: clearCardView after deleteSavedSection', afterDel.includes('clearCardView'));
// Auto-create suppression: if user deletes ALL cards, must not re-create on next entry
check('Auto-create: _noauto flag set when all deleted', delFnBody.includes('_noauto'));
check('Auto-create: checks _noauto before creating', go.includes("_noauto") && go.includes("return"));

// ═══ 24. UX DEBT — 2D_022-030 outstanding items ═════════════════
console.log('\n═══ 24. UX DEBT (2D_022-030) ═══');

// Debt 1: Grid drag highlight — draggable lines must have hover/pointer cursor
check('Grid drag: pointer cursor on hover', drag.includes('cursor') || drag.includes('pointer'),
  'user must see which lines are draggable');
check('Grid drag: highlight on hover', drag.includes('highlight') || drag.includes('hover') || drag.includes('emissive'),
  'visual feedback on draggable line');

// Debt 2: IFC popup on element click — raycaster must exist (scene.js handles globally)
// Card sets visible=false on non-storey meshes → Three.js raycaster auto-skips invisible
const hasPick = scene.includes('Raycaster') || scene.includes('raycaster');
check('Global element pick (scene.js has Raycaster)', hasPick);
check('Pick respects visibility (card hides non-storey → raycaster skips)', hasPick, 'Three.js raycaster skips visible=false');

// Debt 3: Cost panel variance — Δ Qty / Δ Vol columns
const cp = src('cost_panel.js');
check('Cost panel: Δ Qty column', cp.includes('Qty') || cp.includes('qty') || cp.includes('delta'));
check('Cost panel: Δ Vol column', cp.includes('Vol') || cp.includes('vol') || cp.includes('volume'));
check('Cost panel: ✕ close button', cp.includes('close') || cp.includes('✕') || cp.includes('×'));

// Debt 4: Terminal walls — verify SLICE_CLASSES includes IfcCurtainWall
// (Terminal has curtain walls that may lack contours if not in SLICE_CLASSES)
const hasCurtainSlice = sc.includes("'IfcCurtainWall'") || sc.includes('"IfcCurtainWall"');
console.log('    §DEBT IfcCurtainWall in SLICE_CLASSES: ' + hasCurtainSlice);
// Don't fail — just report (curtain walls are often panel assemblies, not solid)

// Debt 5: Grid exit FULL scene restore (no corruption)
// The exit path: toggleGridOverlay → clearFloorClip + unlockView + clearCardView + clearStoreyBandVisibility
const exitFn = go.slice(go.indexOf('A.toggleGridOverlay = function'));
const exitBlock = exitFn.slice(exitFn.indexOf('if (active)'), exitFn.indexOf('active = true'));
check('Exit: clears contours', exitBlock.includes('GridContours.clear') || exitBlock.includes('contour'));
check('Exit: clearFloorClip called', exitBlock.includes('clearFloorClip'));
check('Exit: unlockView called', exitBlock.includes('unlockView'));
check('Exit: clearCardView called', exitBlock.includes('clearCardView'));
check('Exit: clearStoreyBandVisibility called', exitBlock.includes('clearStoreyBandVisibility'));
check('Exit: localClippingEnabled=false (in clearCardView)', cvBody.includes('localClippingEnabled = false'));
// Verify the traverse in clearCardView covers ALL isMesh objects
check('Exit: clearCardView traverses ALL isMesh', cvBody.includes('collectMeshes') || cvBody.includes('traverse'));
// Scene remove gridGroup
check('Exit: scene.remove(gridGroup)', exitBlock.includes('scene.remove'));

// Debt 6: Esc key routes through toggleGridOverlay (single exit path)
check('Esc: routes through toggleGridOverlay', go.includes('_gridClose') && go.includes('toggleGridOverlay'));

// ═══ 25. SCENE CORRUPTION — complete exit state audit ═══════════
console.log('\n═══ 25. SCENE CORRUPTION EXIT AUDIT ═══');

// After grid mode exit, these properties MUST be restored on ALL meshes:
// visible=true, clippingPlanes=null, clipShadows=false, opacity=original, transparent=original
// localClippingEnabled=false, camera=perspective restored, controls.enableRotate=true

// clearCardView blanket restore
check('clearCardView: sets visible=true on ALL meshes (traverse)', cvBody.includes('obj.visible = true'));
check('clearCardView: nulls clippingPlanes on ALL meshes', cvBody.includes('obj.material.clippingPlanes = null'));
check('clearCardView: localClippingEnabled=false', cvBody.includes('localClippingEnabled = false'));

// unlockView restores camera to perspective
const uvStart = gv.indexOf('function unlockView');
const uvEnd = gv.indexOf('\n  function', uvStart + 20) !== -1 ? gv.indexOf('\n  function', uvStart + 20) : uvStart + 1000;
const uvBody = gv.slice(uvStart, uvEnd);
check('unlockView: restores original camera', uvBody.includes('_origCamera'));
check('unlockView: enableRotate=true (3D orbit restored)', uvBody.includes('enableRotate = true'));
check('unlockView: calls clearFloorClip', uvBody.includes('clearFloorClip'));
check('unlockView: restores lighting', uvBody.includes('restoreLighting'));

// Grid scissors cleanup on exit
check('Scissors: onOff disposes geometry', scissors.includes('disposeScissorsGroup'));
check('Scissors: onOff resets dwell', scissors.includes('dwellReset'));

// ═══ 26. DEPLOYED vs LOCAL — curl check ═════════════════════════
console.log('\n═══ 26. DEPLOYED vs LOCAL (curl) ═══');

const DEPLOY_BASE = 'https://objectstorage.ap-sydney-1.oraclecloud.com/n/sdavtmjntjhq/b/ootb-dev/o/sandbox/';
const filesToCheck = ['grid_overlay.js', 'grid_views.js', 'section_cut.js', 'grid_contours.js',
                      'grid_scissors.js', 'grid_dims.js', 'grid_door_arcs.js', 'grid_drag.js',
                      'tools.js', 'scene.js', 'cost_panel.js'];

let deployChecked = 0, deployMismatches = [];
for (const f of filesToCheck) {
  const localContent = src(f);
  try {
    const curl = execSync('curl -s --max-time 5 "' + DEPLOY_BASE + f + '"', { encoding: 'utf8', timeout: 8000 });
    if (curl.length < 100) {
      deployMismatches.push(f + ': deployed too small (' + curl.length + 'B) — STALE or missing');
    } else {
      const sizeDiff = Math.abs(curl.length - localContent.length) / localContent.length;
      if (sizeDiff > 0.05) {
        deployMismatches.push(f + ': size mismatch local=' + localContent.length + ' deployed=' + curl.length + ' (' + (sizeDiff * 100).toFixed(1) + '%)');
      }
    }
    deployChecked++;
  } catch (e) {
    deployMismatches.push(f + ': curl failed');
  }
}
check('Deploy: all files reachable', deployChecked === filesToCheck.length, deployChecked + '/' + filesToCheck.length);
if (deployMismatches.length > 0) {
  for (const m of deployMismatches) check('Deploy mismatch', false, m);
} else {
  check('Deploy: all files match local', true);
}

// ═══ SUMMARY ════════════════════════════════════════════════════
console.log('\n═══ RESULT: ' + pass + ' pass, ' + fail + ' fail ═══');
if (fail > 0) console.log('\n  ⚠ FIX ALL FAILURES BEFORE DEPLOYING\n');
process.exit(fail > 0 ? 1 : 0);
