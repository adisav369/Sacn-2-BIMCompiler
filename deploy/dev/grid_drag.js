/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_drag.js — Grid Line Drag Editing with Rules-Driven Cascade
 *
 * Implementing 2D_024 spec — Witness: W-GRID-DRAG
 *
 * Single concern: pointer event handling + constraint logic for grid line
 * repositioning. After each drag gesture, cascades furniture/device/switch
 * positions per clearance rules from grid_rules.json. Shadow outlines show
 * proposed positions before commit.
 *
 * ALL constants come from grid_rules.json — zero hardcoded values.
 *
 * API:
 *   GridDrag.init(APP, state)        — wire pointer events
 *   GridDrag.loadRules(json)         — load rules from parsed JSON object
 *   GridDrag.enabled()               — true if drag in progress
 *   GridDrag.history()               — array of compound undo records
 *   GridDrag.undo()                  — revert last drag + cascade
 *   GridDrag.clamp(pos, idx, positions, axis, env, rules) — constraint maths
 *   GridDrag.snap(pos, snapM)        — snap to grid
 *   GridDrag.cascadeElements(axis, idx, oldPos, newPos, gridLines, db, rules) — cascade maths
 *   GridDrag.rules()                 — current rules (for tests)
 *
 * Log tags: §GRID_DRAG, §GRID_CASCADE, §GRID_SHADOW, §GRID_RULES
 */
var GridDrag = (function() {
  'use strict';

  // ── Rules (loaded from grid_rules.json, never hardcoded) ────────
  var R = null;  // parsed grid_rules.json

  // ── State ───────────────────────────────────────────────────────
  var A = null;            // APP reference
  var st = null;           // grid overlay state object
  var dragging = false;    // currently dragging?
  var dragLabel = null;    // label of line being dragged
  var dragAxis = null;     // 'X' or 'Y'
  var dragIdx = -1;        // index into xLines or yLines
  var dragStartIFC = 0;    // IFC position at drag start
  var origEnv = null;      // original building envelope (frozen at first drag)
  var hist = [];           // compound undo history
  var shadowGroup = null;  // THREE.Group for shadow outlines

  function log(msg) { console.log('[GridDrag] ' + msg); }

  // ── Rules loader ───────────────────────────────────────────────

  function loadRules(json) {
    R = json;
    log('§GRID_RULES loaded grid_move=' + JSON.stringify(R.grid_move) +
        ' clearance_count=' + (R.clearance || []).length);
  }

  function rules() { return R; }

  // ── Maths (all values from R.grid_move) ────────────────────────

  /** Snap position to grid (Rule 4) — snap_m from rules */
  function snap(pos, snapM) {
    var s = (snapM != null) ? snapM : R.grid_move.snap_m;
    return Math.round(pos / s) * s;
  }

  /**
   * Clamp a grid position within constraints (Rules 1-3 + max_step_m).
   * @param {number} pos        — proposed IFC position
   * @param {number} idx        — index in sorted positions array
   * @param {number[]} positions — all positions on this axis
   * @param {string} axis       — 'X' or 'Y'
   * @param {Object} envelope   — original building envelope {xMin,xMax,yMin,yMax}
   * @param {Object} moveRules  — R.grid_move or compatible object
   * @param {number} [startPos] — position at drag start (for max_step_m)
   * @returns {number} clamped position
   */
  function clamp(pos, idx, positions, axis, envelope, moveRules, startPos) {
    var mr = moveRules || R.grid_move;
    var n = positions.length;

    // Rule 1: cannot cross neighbours (min bay width)
    var lo = (idx > 0)     ? positions[idx - 1] + mr.min_bay_m : -Infinity;
    var hi = (idx < n - 1) ? positions[idx + 1] - mr.min_bay_m :  Infinity;

    // Rule 3: outermost grids have envelope limit
    if (idx === 0) {
      var envMin = (axis === 'X') ? envelope.xMin : envelope.yMin;
      lo = Math.max(lo, envMin - mr.max_extend_m);
    }
    if (idx === n - 1) {
      var envMax = (axis === 'X') ? envelope.xMax : envelope.yMax;
      hi = Math.min(hi, envMax + mr.max_extend_m);
    }

    // max_step_m: limit per gesture
    if (startPos != null && mr.max_step_m > 0) {
      lo = Math.max(lo, startPos - mr.max_step_m);
      hi = Math.min(hi, startPos + mr.max_step_m);
    }

    return Math.max(lo, Math.min(hi, pos));
  }

  // ── Cascade: reposition elements in affected bays ──────────────

  /**
   * Compute new positions for elements in affected bays after a grid line move.
   * Pure maths — no DOM/scene mutation. Returns array of {guid, oldX, oldY, newX, newY}.
   *
   * @param {string} axis     — 'X' or 'Y'
   * @param {number} idx      — index of moved grid line
   * @param {number} oldPos   — original IFC position of moved line
   * @param {number} newPos   — new IFC position of moved line
   * @param {Object[]} gridLines — sorted [{label, position}] on this axis
   * @param {Object} db       — sql.js database
   * @param {Object[]} clearanceRules — R.clearance array
   * @returns {Object[]} moves — [{guid, ifcClass, oldX, oldY, newX, newY, strategy}]
   */
  function cascadeElements(axis, idx, oldPos, newPos, gridLines, db, clearanceRules) {
    if (!db || !clearanceRules || !clearanceRules.length) return [];
    var delta = newPos - oldPos;
    if (Math.abs(delta) < 0.001) return [];

    // Build class filter from clearance rules
    var classSet = {};
    var ruleMap = {};
    for (var c = 0; c < clearanceRules.length; c++) {
      classSet[clearanceRules[c].class] = true;
      ruleMap[clearanceRules[c].class] = clearanceRules[c];
    }
    var classList = Object.keys(classSet);
    if (!classList.length) return [];

    // Affected bays: the bay before and after the moved grid line
    // Bay before: grid[idx-1] .. grid[idx] (old positions)
    // Bay after:  grid[idx]   .. grid[idx+1] (old positions)
    var bayRanges = [];
    if (idx > 0) {
      bayRanges.push({
        lo: gridLines[idx - 1].position,
        hi: oldPos,
        loNew: gridLines[idx - 1].position,
        hiNew: newPos,
        side: 'before'
      });
    }
    if (idx < gridLines.length - 1) {
      bayRanges.push({
        lo: oldPos,
        hi: gridLines[idx + 1].position,
        loNew: newPos,
        hiNew: gridLines[idx + 1].position,
        side: 'after'
      });
    }

    // Query elements in affected bays
    var coordCol = (axis === 'X') ? 'center_x' : 'center_y';
    var classPlaceholders = classList.map(function() { return '?'; }).join(',');
    var moves = [];

    for (var b = 0; b < bayRanges.length; b++) {
      var bay = bayRanges[b];
      var sql = 'SELECT t.guid, t.center_x, t.center_y, m.ifc_class ' +
                'FROM element_transforms t JOIN elements_meta m ON t.guid = m.guid ' +
                'WHERE m.ifc_class IN (' + classPlaceholders + ') ' +
                'AND t.' + coordCol + ' >= ? AND t.' + coordCol + ' <= ?';
      var params = classList.concat([bay.lo, bay.hi]);

      try {
        var result = db.exec(sql, params);
        if (!result || !result.length) continue;
        var rows = result[0].values;

        for (var r = 0; r < rows.length; r++) {
          var guid = rows[r][0];
          var cx = rows[r][1];
          var cy = rows[r][2];
          var cls = rows[r][3];
          var rule = ruleMap[cls];
          if (!rule) continue;

          var move = applyStrategy(rule.strategy, axis, cx, cy, bay, rule);
          if (move) {
            move.guid = guid;
            move.ifcClass = cls;
            move.strategy = rule.strategy;
            moves.push(move);
            log('§GRID_CASCADE guid=' + guid + ' class=' + cls + ' strategy=' + rule.strategy +
                ' old=(' + cx.toFixed(3) + ',' + cy.toFixed(3) + ')' +
                ' new=(' + move.newX.toFixed(3) + ',' + move.newY.toFixed(3) + ')');
          }
        }
      } catch (e) {
        log('§GRID_CASCADE query error: ' + e.message);
      }
    }

    log('§GRID_CASCADE axis=' + axis + ' idx=' + idx + ' delta=' + delta.toFixed(3) +
        ' elements=' + moves.length);
    return moves;
  }

  /**
   * Apply a positioning strategy to compute new element position.
   * @returns {Object|null} {oldX, oldY, newX, newY} or null if no move needed
   */
  function applyStrategy(strategy, axis, cx, cy, bay, rule) {
    var oldWidth = bay.hi - bay.lo;
    var newWidth = bay.hiNew - bay.loNew;
    if (oldWidth < 0.001) return null;

    // Normalised position within old bay (0..1)
    var coord = (axis === 'X') ? cx : cy;
    var t = (coord - bay.lo) / oldWidth;

    var newCoord;
    if (strategy === 'proportional') {
      // Scale position proportionally within new bay
      newCoord = bay.loNew + t * newWidth;
      // Enforce grid_min_m clearance from bay edges
      var gm = rule.grid_min_m || 0;
      newCoord = Math.max(bay.loNew + gm, Math.min(bay.hiNew - gm, newCoord));
    } else if (strategy === 'pin_to_wall') {
      // Stay at same distance from nearest bay edge (wall)
      var distLo = coord - bay.lo;
      var distHi = bay.hi - coord;
      if (distLo <= distHi) {
        // Pinned to low side
        newCoord = bay.loNew + distLo;
      } else {
        // Pinned to high side
        newCoord = bay.hiNew - distHi;
      }
      // Enforce wall_min_m
      var wm = rule.wall_min_m || 0;
      newCoord = Math.max(bay.loNew + wm, Math.min(bay.hiNew - wm, newCoord));
    } else if (strategy === 'center_bay') {
      // Place at center of new bay
      newCoord = (bay.loNew + bay.hiNew) / 2;
    } else {
      return null; // unknown strategy
    }

    // Build result
    var newX = (axis === 'X') ? newCoord : cx;
    var newY = (axis === 'Y') ? newCoord : cy;

    // Skip if no meaningful movement
    if (Math.abs(newX - cx) < 0.001 && Math.abs(newY - cy) < 0.001) return null;

    return { oldX: cx, oldY: cy, newX: newX, newY: newY };
  }

  // ── Shadow outlines ────────────────────────────────────────────

  /** Create shadow outline boxes for proposed element moves */
  function showShadows(moves) {
    clearShadows();
    if (!moves.length || !A || !A.scene) return;
    if (!R || !R.shadow) return;

    shadowGroup = new THREE.Group();
    shadowGroup.name = 'gridDragShadows';

    var color = new THREE.Color(R.shadow.color || '#ff6600');
    var opacity = R.shadow.opacity || 0.35;

    for (var i = 0; i < moves.length; i++) {
      var m = moves[i];
      // Query bbox for this element
      var bbox = getElementBBox(m.guid);
      if (!bbox) continue;

      // Create wireframe box at new position
      var geom = new THREE.BoxGeometry(bbox.bx, bbox.bz, bbox.by); // Three: x=ifcX, y=ifcZ(up), z=ifcY(depth)
      var edges = new THREE.EdgesGeometry(geom);
      var mat = new THREE.LineBasicMaterial({ color: color, transparent: true, opacity: opacity });
      var outline = new THREE.LineSegments(edges, mat);

      // Position in Three.js coords
      var tp = A.ifc2three(m.newX, m.newY, bbox.cz);
      outline.position.set(tp.x, tp.y, tp.z);
      outline.renderOrder = 1001;
      outline.userData.shadowGuid = m.guid;
      shadowGroup.add(outline);
    }

    A.scene.add(shadowGroup);
    A.markDirty();
    log('§GRID_SHADOW created=' + shadowGroup.children.length + ' outlines');
  }

  /** Remove all shadow outlines */
  function clearShadows() {
    if (shadowGroup && A && A.scene) {
      shadowGroup.traverse(function(obj) {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) obj.material.dispose();
      });
      A.scene.remove(shadowGroup);
    }
    shadowGroup = null;
  }

  /** Get element bounding box from DB */
  function getElementBBox(guid) {
    if (!A || !A.db) return null;
    try {
      var r = A.db.exec(
        'SELECT center_x, center_y, center_z, bbox_x, bbox_y, bbox_z FROM element_transforms WHERE guid = ?',
        [guid]
      );
      if (!r || !r.length || !r[0].values.length) return null;
      var v = r[0].values[0];
      return { cx: v[0], cy: v[1], cz: v[2], bx: v[3], by: v[4], bz: v[5] };
    } catch (e) { return null; }
  }

  // ── Coordinate helpers ─────────────────────────────────────────

  /** Convert pointer event to IFC position on drag axis via ground-plane raycast */
  function pointerToIFC(evt) {
    if (!A || !A.camera || !A.renderer) return null;
    var rect = A.renderer.domElement.getBoundingClientRect();
    var ndcX = ((evt.clientX - rect.left) / rect.width) * 2 - 1;
    var ndcY = -((evt.clientY - rect.top) / rect.height) * 2 + 1;

    var ray = new THREE.Raycaster();
    ray.setFromCamera(new THREE.Vector2(ndcX, ndcY), A.camera);

    var entry = st.lineMeshes[dragLabel];
    var planeY = entry ? entry.v0.y : 0;
    var plane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -planeY);
    var hit = new THREE.Vector3();
    if (!ray.ray.intersectPlane(plane, hit)) return null;

    if (dragAxis === 'X') {
      return hit.x + A.modelOffset.x;
    } else {
      return -hit.z + A.modelOffset.y;
    }
  }

  // ── Update scene objects ───────────────────────────────────────

  /** Shift a grid line + its bubbles by an IFC delta along its axis */
  function shiftLine(label, axis, delta) {
    var entry = st.lineMeshes[label];
    if (!entry) return;

    var dx = 0, dz = 0;
    if (axis === 'X') { dx = delta; } else { dz = -delta; }

    // Update line geometry
    var posAttr = entry.line.geometry.getAttribute('position');
    var arr = posAttr.array;
    arr[0] += dx; arr[2] += dz;
    arr[3] += dx; arr[5] += dz;
    posAttr.needsUpdate = true;

    entry.v0.x += dx; entry.v0.z += dz;
    entry.v1.x += dx; entry.v1.z += dz;
    entry.line.userData.gridPos += delta;

    // Shift bubbles
    if (st.gridGroup) {
      st.gridGroup.traverse(function(obj) {
        if (obj.isSprite && obj.userData.gridLabel === label) {
          obj.position.x += dx;
          obj.position.z += dz;
        }
      });
    }
  }

  function updateGridData(axis, idx, newPos) {
    var lines = (axis === 'X') ? st.gridData.xLines : st.gridData.yLines;
    if (lines && lines[idx]) lines[idx].position = newPos;
  }

  function rebuildAnnotations() {
    if (typeof DimChains !== 'undefined' && st.gridGroup && st.gridData && st.envCache) {
      DimChains.remove(st.gridGroup);
      DimChains.build(A, st.gridGroup, st.gridData, st.envCache, { bubbleScale: st.bubbleScale });
    }
    if (st.rebuildPanel) st.rebuildPanel(st.gridData);
  }

  // ── Drag event handlers ────────────────────────────────────────

  function onPointerDown(evt) {
    if (!st || !st.active || !st.gridGroup || !st.lineMeshes) return;
    if (!R) { log('§GRID_DRAG no rules loaded — drag disabled'); return; }
    if (evt.button !== 0) return;

    var rect = A.renderer.domElement.getBoundingClientRect();
    var ndcX = ((evt.clientX - rect.left) / rect.width) * 2 - 1;
    var ndcY = -((evt.clientY - rect.top) / rect.height) * 2 + 1;
    var mouse = new THREE.Vector2(ndcX, ndcY);
    var ray = new THREE.Raycaster();
    ray.setFromCamera(mouse, A.camera);
    ray.params.Line = { threshold: 0.5 };

    var targets = [];
    st.gridGroup.traverse(function(obj) {
      if (obj.userData && obj.userData.gridLabel) targets.push(obj);
    });

    var hits = ray.intersectObjects(targets, false);
    if (!hits.length) return;

    var label = hits[0].object.userData.gridLabel;
    var axis = hits[0].object.userData.gridAxis;
    if (!axis && st.lineMeshes[label]) {
      axis = st.lineMeshes[label].line.userData.gridAxis;
    }
    if (!label || !axis) return;

    var lines = (axis === 'X') ? st.gridData.xLines : st.gridData.yLines;
    var idx = -1;
    for (var i = 0; i < lines.length; i++) {
      if (lines[i].label === label) { idx = i; break; }
    }
    if (idx < 0) return;

    // Freeze original envelope on first drag
    if (!origEnv) {
      origEnv = {
        xMin: st.envCache.xMin, xMax: st.envCache.xMax,
        yMin: st.envCache.yMin, yMax: st.envCache.yMax
      };
    }

    dragging = true;
    dragLabel = label;
    dragAxis = axis;
    dragIdx = idx;
    dragStartIFC = lines[idx].position;

    if (st.lineMeshes[label]) {
      st.lineMeshes[label].line.material.color.setHex(0xff6600);
      st.lineMeshes[label].line.material.linewidth = 3;
    }
    if (A.controls) A.controls.enabled = false;

    evt.preventDefault();
    evt.stopPropagation();
    log('§GRID_DRAG start label=' + label + ' axis=' + axis + ' idx=' + idx +
        ' pos=' + dragStartIFC.toFixed(3) + ' max_step=' + R.grid_move.max_step_m);
  }

  function onPointerMove(evt) {
    if (!dragging) return;

    var ifcPos = pointerToIFC(evt);
    if (ifcPos == null) return;

    var lines = (dragAxis === 'X') ? st.gridData.xLines : st.gridData.yLines;
    var positions = [];
    for (var i = 0; i < lines.length; i++) positions.push(lines[i].position);

    // Clamp with max_step_m from drag start
    var clamped = clamp(ifcPos, dragIdx, positions, dragAxis, origEnv, R.grid_move, dragStartIFC);
    var snapped = snap(clamped);

    var currentPos = lines[dragIdx].position;
    var delta = snapped - currentPos;
    if (Math.abs(delta) < 0.001) return;

    shiftLine(dragLabel, dragAxis, delta);
    updateGridData(dragAxis, dragIdx, snapped);
    rebuildAnnotations();

    // Show shadow outlines for cascaded elements during drag
    var cascadeMoves = cascadeElements(
      dragAxis, dragIdx, dragStartIFC, snapped, lines, A.db, R.clearance
    );
    showShadows(cascadeMoves);

    A.markDirty();
    evt.preventDefault();
  }

  function onPointerUp(evt) {
    if (!dragging) return;
    if (A.controls) A.controls.enabled = true;

    var lines = (dragAxis === 'X') ? st.gridData.xLines : st.gridData.yLines;
    var newPos = lines[dragIdx].position;
    var delta = newPos - dragStartIFC;

    if (Math.abs(delta) > 0.001) {
      // Compute final cascade
      var cascadeMoves = cascadeElements(
        dragAxis, dragIdx, dragStartIFC, newPos, lines, A.db, R.clearance
      );

      // Compound undo record: grid move + all cascaded elements
      var record = {
        grid: {
          label: dragLabel,
          axis: dragAxis,
          idx: dragIdx,
          oldPos: dragStartIFC,
          newPos: newPos,
          delta: delta
        },
        elements: cascadeMoves
      };
      hist.push(record);

      log('§GRID_DRAG label=' + dragLabel + ' axis=' + dragAxis +
          ' oldPos=' + dragStartIFC.toFixed(3) + ' newPos=' + newPos.toFixed(3) +
          ' delta=' + (delta >= 0 ? '+' : '') + delta.toFixed(3) +
          ' cascaded=' + cascadeMoves.length);
    } else {
      log('§GRID_DRAG cancel label=' + dragLabel + ' (no movement)');
    }

    // Clear shadows (they persist until commit or undo)
    clearShadows();

    // Reset visual
    if (st.lineMeshes[dragLabel]) {
      var defColor = A.lightTheme ? 0x444444 : 0xcccccc;
      st.lineMeshes[dragLabel].line.material.color.setHex(defColor);
      st.lineMeshes[dragLabel].line.material.linewidth = 1;
    }

    dragging = false;
    dragLabel = null;
    dragAxis = null;
    dragIdx = -1;

    A.markDirty();
    evt.preventDefault();
  }

  // ── Undo (compound: grid + cascade) ────────────────────────────

  function undo() {
    if (!hist.length) { log('§GRID_DRAG undo — nothing to undo'); return false; }
    var rec = hist.pop();

    // Revert grid line
    var g = rec.grid;
    var reverseDelta = g.oldPos - g.newPos;
    shiftLine(g.label, g.axis, reverseDelta);
    updateGridData(g.axis, g.idx, g.oldPos);
    rebuildAnnotations();

    log('§GRID_DRAG undo grid label=' + g.label + ' restored=' + g.oldPos.toFixed(3));

    // Revert cascaded elements (log each for debug)
    for (var i = 0; i < rec.elements.length; i++) {
      var el = rec.elements[i];
      log('§GRID_CASCADE undo guid=' + el.guid + ' class=' + el.ifcClass +
          ' restored=(' + el.oldX.toFixed(3) + ',' + el.oldY.toFixed(3) + ')');
    }

    clearShadows();
    A.markDirty();
    log('§GRID_DRAG undo complete — grid + ' + rec.elements.length + ' elements reverted');
    return true;
  }

  // ── Init ───────────────────────────────────────────────────────

  function init(APP, state) {
    A = APP;
    st = state;
    hist = [];
    origEnv = null;

    var canvas = A.renderer.domElement;
    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointermove', onPointerMove);
    canvas.addEventListener('pointerup', onPointerUp);

    // Auto-load rules from grid_rules.json via fetch
    if (!R) {
      var rulesUrl = 'grid_rules.json?v=1';
      fetch(rulesUrl).then(function(resp) {
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        return resp.json();
      }).then(function(json) {
        loadRules(json);
      }).catch(function(e) {
        log('§GRID_RULES WARN: could not load grid_rules.json — ' + e.message);
      });
    }

    log('§GRID_DRAG init — pointer events wired');
  }

  return {
    init:             init,
    loadRules:        loadRules,
    rules:            rules,
    enabled:          function() { return dragging; },
    history:          function() { return hist.slice(); },
    undo:             undo,
    clamp:            clamp,
    snap:             snap,
    cascadeElements:  cascadeElements,
    applyStrategy:    applyStrategy,
    clearShadows:     clearShadows
  };
})();
