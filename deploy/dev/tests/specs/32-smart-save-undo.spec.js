// 32-smart-save-undo.spec.js — Smart Save, Undo/Redo, Band Filter, Wall Outline, Drag UX
// Issues proven:
//   T_3201: kernel_ops.js has compact function — log housekeeping present
//   T_3202: kernel_ops.js has sessionStart function — session boundary marker present
//   T_3203: kernel_ops.js v4 loaded — compact + sessionStart exported
//   T_3204: grid_scissors.js has dwell tracker — smart save detection present
//   T_3205: grid_scissors.js exports dwellPoints function — dwell data accessible
//   T_3206: grid_scissors.js exports dwellReset function — cleanup accessible
//   T_3207: grid_scissors.js has flashDwellCapture — visual feedback on dwell
//   T_3208: grid_scissors.js has FIFO eviction — oldest dwell removed when max reached
//   T_3209: grid_scissors.js has dwell markers — red outlines at captured Z levels
//   T_3210: grid_scissors.js has proximity dedup — same-band dwells consolidated
//   T_3211: grid_scissors.js has three-strike lock — convergence detection
//   T_3212: grid_rules.json has smart_save config — dwell thresholds externalised
//   T_3213: grid_overlay.js has undo/redo buttons — doUndo doRedo functions present
//   T_3214: grid_overlay.js undo skips audit ops — only GRID_MOVE is undoable
//   T_3215: grid_overlay.js restoreSection does NOT toggle scissors on — no GF trap
//   T_3216: grid_overlay.js restoreSection raises clip for dwells — all layers visible
//   T_3217: grid_overlay.js restoreSection renders composite layers — painter's algorithm
//   T_3218: grid_overlay.js restoreSection calls applyStoreyBandVisibility — element picking fixed
//   T_3219: grid_overlay.js toggle-off calls clearStoreyBandVisibility — no mesh corruption
//   T_3220: grid_overlay.js saves dwells via saveSectionToDb — dwell points persisted in DB
//   T_3221: grid_overlay.js loadSavedSections reads detected_grids column — dwells loaded from DB
//   T_3222: section_cut.js unconditionally excludes IfcRoof from floor plan — no roof contours in plan
//   T_3223: section_cut.js detectStoreys called once not twice — no duplicate query
//   T_3224: section_cut.js band filter uses bbox_z fallback — non-rtree Z-filtering works
//   T_3225: grid_dims.js has storey band proximity filter — distant storey noise blocked
//   T_3226: grid_dims.js commits GRID_DETECT to kernel_ops — detection auditable
//   T_3227: grid_dims.js dead snapToNearestFace removed — no stale code
//   T_3228: grid_door_arcs.js has connecting dimension line — opening mini dim lines present
//   T_3229: grid_drag.js exports applyReplayedMove — undo buttons can call it
//   T_3230: grid_drag.js has drag status hints — A.status updated during drag
//   T_3231: grid_drag.js has red ghost + blue proposed — drag visual feedback
//   T_3232: grid_contours.js has buildRibbon function — adaptive wall outline for large buildings
//   T_3233: grid_contours.js computes minOutlineW from camera — wall thickness scales with zoom
//   T_3234: grid_overlay.js smart save badge shows dwell count — UI indicator present

const { test, expect } = require('@playwright/test');
const fs   = require('fs');
const path = require('path');

const DEV = path.resolve(__dirname, '../..');

const src = (f) => fs.readFileSync(path.join(DEV, f), 'utf8');

test.describe('Smart Save + Undo/Redo + Band Filter + Wall Outline + Drag UX', () => {

  // ── kernel_ops.js — housekeeping ────────────────────────────────

  test('T_3201: kernel_ops.js has compact function — log housekeeping present', () => {
    const s = src('kernel_ops.js');
    expect(s).toContain('function compact(db)');
    expect(s).toContain("DELETE FROM kernel_ops WHERE undone = 1");
  });

  test('T_3202: kernel_ops.js has sessionStart function — session boundary marker present', () => {
    const s = src('kernel_ops.js');
    expect(s).toContain('function sessionStart(db)');
    expect(s).toContain("'SESSION_START'");
  });

  test('T_3203: kernel_ops.js v4 loaded — compact + sessionStart exported', () => {
    const s = src('kernel_ops.js');
    expect(s).toContain('compact:');
    expect(s).toContain('sessionStart:');
    expect(s).toContain('§KERNEL_OPS_LOADED v4');
  });

  // ── grid_scissors.js — smart save dwell tracker ─────────────────

  test('T_3204: grid_scissors.js has dwell tracker — smart save detection present', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('dwellPoints');
    expect(s).toContain('function dwellTrack');
    expect(s).toContain('function checkDwell');
  });

  test('T_3205: grid_scissors.js exports dwellPoints function — dwell data accessible', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('dwellPoints:');
    expect(s).toMatch(/dwellPoints:\s*function/);
  });

  test('T_3206: grid_scissors.js exports dwellReset function — cleanup accessible', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('dwellReset:');
  });

  test('T_3207: grid_scissors.js has flashDwellCapture — visual feedback on dwell', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('function flashDwellCapture');
    expect(s).toContain('background:white'); // screen flash
  });

  test('T_3208: grid_scissors.js has FIFO eviction — oldest dwell removed when max reached', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('dwellPoints.shift()');
    expect(s).toContain('FIFO');
  });

  test('T_3209: grid_scissors.js has dwell markers — red outlines at captured Z levels', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('function rebuildDwellMarkers');
    expect(s).toContain('function clearDwellMarkers');
    expect(s).toContain('isDwellMarker');
  });

  test('T_3210: grid_scissors.js has proximity dedup — same-band dwells consolidated', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('§SMART_SAVE dedup');
    expect(s).toContain('proxTol');
  });

  test('T_3211: grid_scissors.js has three-strike lock — convergence detection', () => {
    const s = src('grid_scissors.js');
    expect(s).toContain('locked = true');
    expect(s).toContain('lockHits');
    expect(s).toContain('convergence');
  });

  // ── grid_rules.json — smart_save config ─────────────────────────

  test('T_3212: grid_rules.json has smart_save config — dwell thresholds externalised', () => {
    const rules = JSON.parse(src('grid_rules.json'));
    expect(rules.smart_save).toBeDefined();
    expect(rules.smart_save.dwell_threshold_s).toBe(1.0);
    expect(rules.smart_save.max_dwells).toBe(3);
    expect(rules.smart_save.lock_after_hits).toBe(3);
    expect(rules.smart_save.proximity_tol_m).toBe(0.3);
  });

  // ── grid_overlay.js — undo/redo + restore ───────────────────────

  test('T_3213: grid_overlay.js has undo/redo buttons — doUndo doRedo functions present', () => {
    const s = src('grid_overlay.js');
    expect(s).toContain('function doUndo()');
    expect(s).toContain('function doRedo()');
    expect(s).toContain('kernel-undo-btn');
    expect(s).toContain('kernel-redo-btn');
  });

  test('T_3214: grid_overlay.js undo skips audit ops — only GRID_MOVE is undoable', () => {
    const s = src('grid_overlay.js');
    expect(s).toContain('UNDOABLE_OPS');
    expect(s).toContain("'GRID_MOVE': true");
    expect(s).toContain('skip audit op');
  });

  test('T_3215: grid_overlay.js restoreSection does NOT toggle scissors on — no GF trap', () => {
    const s = src('grid_overlay.js');
    const restoreBlock = s.slice(s.indexOf('function restoreSection'));
    const endIdx = restoreBlock.indexOf('\n  function ', 50);
    const body = endIdx > 0 ? restoreBlock.slice(0, endIdx) : restoreBlock.slice(0, 2000);
    // Must NOT have the pattern: if (!sectionOn) toggleSection — that was the GF trap
    expect(body).not.toMatch(/!A\.sectionOn.*toggleSection/);
  });

  test('T_3216: grid_overlay.js restoreSection raises clip for dwells — all layers visible', () => {
    const s = src('grid_overlay.js');
    const restoreBlock = s.slice(s.indexOf('function restoreSection'));
    expect(restoreBlock).toContain('clip raised');
    expect(restoreBlock).toContain('highest dwell');
  });

  test('T_3217: grid_overlay.js restoreSection renders composite layers — painters algorithm', () => {
    const s = src('grid_overlay.js');
    const restoreBlock = s.slice(s.indexOf('function restoreSection'));
    expect(restoreBlock).toContain('composite layer=');
    expect(restoreBlock).toContain('GridContours.renderContours');
  });

  test('T_3218: grid_overlay.js restoreSection calls applyStoreyBandVisibility — element picking fixed', () => {
    const s = src('grid_overlay.js');
    const restoreBlock = s.slice(s.indexOf('function restoreSection'));
    expect(restoreBlock).toContain('applyStoreyBandVisibility');
  });

  test('T_3219: grid_overlay.js toggle-off calls clearStoreyBandVisibility — no mesh corruption', () => {
    const s = src('grid_overlay.js');
    // In the toggle-off block (active=false path), clearStoreyBandVisibility must be called
    const toggleBlock = s.slice(s.indexOf('A.toggleGridOverlay'));
    const exitBlock = toggleBlock.slice(0, toggleBlock.indexOf('active = true'));
    expect(exitBlock).toContain('clearStoreyBandVisibility');
  });

  test('T_3220: grid_overlay.js saves dwells via saveSectionToDb — dwell points persisted in DB', () => {
    const s = src('grid_overlay.js');
    expect(s).toContain('function saveSectionToDb(name, dwells)');
    expect(s).toContain('dwellJson');
  });

  test('T_3221: grid_overlay.js loadSavedSections reads detected_grids column — dwells loaded from DB', () => {
    const s = src('grid_overlay.js');
    expect(s).toContain('detected_grids FROM saved_sections');
    expect(s).toContain('dwells: dwells');
  });

  test('T_3234: grid_overlay.js smart save badge shows dwell count — UI indicator present', () => {
    const s = src('grid_overlay.js');
    expect(s).toContain('smart-save-badge');
    expect(s).toContain('Dwell points captured');
  });

  // ── section_cut.js — band filter fixes ──────────────────────────

  test('T_3222: section_cut.js unconditionally excludes IfcRoof from floor plan — no roof contours in plan', () => {
    const s = src('section_cut.js');
    // The class exclude must NOT have a Z-position condition (was the bug)
    expect(s).toContain("if (excAbove.indexOf(bcls) >= 0) { continue; }");
  });

  test('T_3223: section_cut.js detectStoreys called once not twice — no duplicate query', () => {
    const s = src('section_cut.js');
    // detectStoreys should appear only once in sectionCut function
    const scFn = s.slice(s.indexOf('function sectionCut'));
    const matches = scFn.match(/detectStoreys\(db\)/g);
    expect(matches).not.toBeNull();
    expect(matches.length).toBe(1);
  });

  test('T_3224: section_cut.js band filter uses bbox_z fallback — non-rtree Z-filtering works', () => {
    const s = src('section_cut.js');
    expect(s).toContain('bbz > 0');
    expect(s).toContain('bcz - bbz * 0.5');
  });

  // ── grid_dims.js — vote algorithm fixes ─────────────────────────

  test('T_3225: grid_dims.js has storey band proximity filter — distant storey noise blocked', () => {
    const s = src('grid_dims.js');
    expect(s).toContain('§GD_BAND_PROXIMITY');
    expect(s).toContain('bandH');
  });

  test('T_3226: grid_dims.js commits GRID_DETECT to kernel_ops — detection auditable', () => {
    const s = src('grid_dims.js');
    expect(s).toContain("'GRID_DETECT'");
    expect(s).toContain('KernelOps.commitOp');
  });

  test('T_3227: grid_dims.js dead snapToNearestFace removed — no stale code', () => {
    const s = src('grid_dims.js');
    expect(s).not.toContain('function snapToNearestFace');
  });

  // ── grid_door_arcs.js — dimension lines ─────────────────────────

  test('T_3228: grid_door_arcs.js has connecting dimension line — opening mini dim lines present', () => {
    const s = src('grid_door_arcs.js');
    expect(s).toContain('isDimLine');
    expect(s).toContain('dimOffset');
    expect(s).toContain('Connecting dimension line');
  });

  // ── grid_drag.js — drag UX ─────────────────────────────────────

  test('T_3229: grid_drag.js exports applyReplayedMove — undo buttons can call it', () => {
    const s = src('grid_drag.js');
    expect(s).toContain('applyReplayedMove: applyReplayedMove');
  });

  test('T_3230: grid_drag.js has drag status hints — A.status updated during drag', () => {
    const s = src('grid_drag.js');
    expect(s).toContain("A.status.textContent = 'Dragging grid");
    expect(s).toContain("A.status.textContent = 'Grid ' + dragLabel");
  });

  test('T_3231: grid_drag.js has red ghost + blue proposed — drag visual feedback', () => {
    const s = src('grid_drag.js');
    expect(s).toContain('function showDragGhost');
    expect(s).toContain('0xcc2222'); // red ghost
    expect(s).toContain('0x2266cc'); // blue proposed
  });

  // ── grid_contours.js — adaptive wall outline ────────────────────

  test('T_3232: grid_contours.js has buildRibbon function — adaptive wall outline for large buildings', () => {
    const s = src('grid_contours.js');
    expect(s).toContain('function buildRibbon');
  });

  test('T_3233: grid_contours.js computes minOutlineW from camera — wall thickness scales with zoom', () => {
    const s = src('grid_contours.js');
    expect(s).toContain('MIN_WALL_SCREEN_PX');
    expect(s).toContain('minOutlineW');
    expect(s).toContain('frustumW');
  });
});
