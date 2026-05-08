/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_dims.js — Grid Detection and Dimension Generation from BIM Database
 *
 * API: window.GridDims = { detectGrids, generateDimensions, renderGridEntities }
 *
 * Log tags (§GD_ prefix):
 *   §GD_COLUMNS   — column count after query
 *   §GD_CLUSTER   — cluster count per axis
 *   §GD_GRIDS     — final grid line counts
 *   §GD_DIMS      — dimension annotation count
 *   §GD_RENDER    — draw command count
 *
 * Pattern: plain script tag, no ES modules. Attaches to window.GridDims.
 * No DOM access in core logic.
 */
(function () {
  'use strict';

  var SNAP_MODULE = 300; // mm — snap bay widths to nearest 300mm if within 150mm
  var DEFAULT_TOLERANCE = 0.3; // meters — columns within 30cm are same grid line
  var GRID_EXTEND = 1.0;    // meters — how far grid lines extend beyond plan bbox
  var LABEL_RADIUS = 10;   // screen px — circle radius for grid bubbles (renderer must NOT multiply by viewScale)
  var LABEL_TEXT_H = 10;   // screen px — bubble label font size
  var MIN_BAY_DISPLAY = 1.0; // meters — drop grid lines closer than this to any neighbour (sub-metre column pairs)

  // Crowding thresholds — kick in for large buildings (Terminal, Hospital, etc.)
  // MAX_GRID_LINES: if either axis has more than this many lines after filterStructural,
  //   stride-thin the interior ones so only ~MAX_GRID_LINES labels remain (first + last always kept).
  // MIN_DIM_SCREEN_PX: minimum screen-space gap between dim lines before dim text is suppressed
  //   and the bay is listed in a panel instead of inline.  Evaluated at render time via viewScale.
  var MAX_GRID_LINES = 12;    // per axis — beyond this, thin interior grids
  var MIN_DIM_SCREEN_PX = 28; // px — minimum gap for inline dim text (≈ 2 char widths × 14px)

  // ── Helpers ──────────────────────────────────────────────────────

  function log(msg) {
    console.log('[GridDims] ' + msg);
  }

  /**
   * Cluster sorted (position, guid) entries within tolerance.
   * Returns [{position (mean), guids:[]}] sorted by position.
   */
  function clusterEntries(entries, tolerance) {
    if (!entries.length) return [];
    entries.sort(function (a, b) { return a.pos - b.pos; });

    var clusters = [{ pos: entries[0].pos, guids: [entries[0].guid], sum: entries[0].pos, count: 1 }];
    for (var i = 1; i < entries.length; i++) {
      var last = clusters[clusters.length - 1];
      var mean = last.sum / last.count;
      if (Math.abs(entries[i].pos - mean) < tolerance) {
        last.sum += entries[i].pos;
        last.count++;
        last.guids.push(entries[i].guid);
      } else {
        clusters.push({ pos: entries[i].pos, guids: [entries[i].guid], sum: entries[i].pos, count: 1 });
      }
    }

    return clusters.map(function (c) {
      return { position: c.sum / c.count, guids: c.guids };
    });
  }

  /**
   * Snap grid positions so bay widths round to nearest SNAP_MODULE mm.
   * Anchor at first grid on each axis. Never collapse a bay to zero.
   * Implementing 2D_027 §1.2 — Witness: W-2D27
   * rawPosition is preserved (actual IFC position); position is snapped (display only).
   */
  function snapGrids(lines) {
    if (lines.length < 2) return lines;

    // Preserve rawPosition from input (set if already present, else use position)
    var firstRaw = lines[0].rawPosition !== undefined ? lines[0].rawPosition : lines[0].position;
    var snapped = [{ label: lines[0].label, position: lines[0].position, rawPosition: firstRaw, guids: lines[0].guids }];
    for (var i = 1; i < lines.length; i++) {
      var rawBayMm = (lines[i].position - lines[i - 1].position) * 1000;
      var snappedBayMm = Math.round(rawBayMm / SNAP_MODULE) * SNAP_MODULE;
      if (snappedBayMm < SNAP_MODULE) snappedBayMm = SNAP_MODULE;
      var newPos = snapped[snapped.length - 1].position + snappedBayMm / 1000;
      var rawPos = lines[i].rawPosition !== undefined ? lines[i].rawPosition : lines[i].position;
      var delta = Math.abs(newPos - rawPos);
      if (delta > 0.001) {
        // Only log when snap introduces > 1mm drift
        console.log('[GridDims] §GD_SNAP_DELTA idx=' + i + ' raw=' + (rawPos * 1000).toFixed(0) + ' snapped=' + (newPos * 1000).toFixed(0) + ' delta=' + (delta * 1000).toFixed(0));
      }
      snapped.push({ label: lines[i].label, position: newPos, rawPosition: rawPos, guids: lines[i].guids });
    }
    return snapped;
  }

  /**
   * Format distance in meters to mm string, 0 decimal places.
   */
  function formatDim(meters) {
    var mm = meters * 1000;
    return String(Math.round(mm));
  }

  /**
   * Drop sub-structural lines: keep first/last, drop any interior line where
   * both adjacent bays are < MIN_BAY_DISPLAY. Repeat until stable.
   */
  function filterStructural(lines) {
    var result = lines.slice();
    var changed = true;
    while (changed && result.length > 2) {
      changed = false;
      var next = [result[0]];
      for (var i = 1; i < result.length - 1; i++) {
        var prev = result[i].position - result[i - 1].position;
        var nxt  = result[i + 1].position - result[i].position;
        if (prev >= MIN_BAY_DISPLAY && nxt >= MIN_BAY_DISPLAY) {
          next.push(result[i]);
        } else {
          changed = true;
        }
      }
      next.push(result[result.length - 1]);
      result = next;
    }
    return result;
  }

  /**
   * Stride-thin crowded grids: keep first + last + every Nth interior line
   * so the total never exceeds MAX_GRID_LINES.
   */
  function thinGrids(lines) {
    if (lines.length <= MAX_GRID_LINES) return lines;
    var interior = lines.slice(1, lines.length - 1);
    var stride = Math.ceil(interior.length / (MAX_GRID_LINES - 2));
    var kept = [lines[0]];
    for (var ti = 0; ti < interior.length; ti++) {
      if (ti % stride === 0) kept.push(interior[ti]);
    }
    kept.push(lines[lines.length - 1]);
    return kept;
  }

  // ── detectGrids ─────────────────────────────────────────────────

  /**
   * Detect grid lines from column positions in the database.
   * @param {object} db - sql.js database instance
   * @param {number} [tolerance=0.3] - clustering tolerance in meters
   * @returns {{xLines: Array, yLines: Array}}
   */
  function detectGrids(db, tolerance) {
    if (tolerance === undefined || tolerance === null) tolerance = DEFAULT_TOLERANCE;

    var empty = { xLines: [], yLines: [] };

    // Query columns first; fall back to walls if no columns found
    var sql =
      "SELECT m.guid, t.center_x, t.center_y " +
      "FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class IN ('IfcColumn')";

    var result;
    var source = 'column';
    try {
      result = db.exec(sql);
    } catch (e) {
      log('§GD_COLUMNS query error: ' + e.message);
    }

    // Fallback: if no columns, use wall face positions for grid alignment
    // Implementing 2D_027 §1.2 — Witness: W-2D27
    // Wall centroid is NOT a grid face — use bbox edges instead
    var wallFaceRows = null;
    if (!result || !result.length || !result[0].values.length || result[0].values.length < 2) {
      log('§GD_COLUMNS found=' + (result && result.length && result[0].values ? result[0].values.length : 0) + ' — falling back to wall faces');
      source = 'wall';
      var wallSql =
        "SELECT m.guid, t.center_x - t.bbox_x*0.5 AS x_lo, t.center_x + t.bbox_x*0.5 AS x_hi, " +
        "       t.center_y - t.bbox_y*0.5 AS y_lo, t.center_y + t.bbox_y*0.5 AS y_hi " +
        "FROM elements_meta m " +
        "JOIN element_transforms t ON m.guid = t.guid " +
        "WHERE m.ifc_class IN ('IfcWall', 'IfcWallStandardCase')";
      try {
        var wallResult = db.exec(wallSql);
        if (wallResult && wallResult.length && wallResult[0].values.length) {
          wallFaceRows = wallResult[0].values;
        }
      } catch (e2) {
        log('§GD_WALLS query error: ' + e2.message);
        return empty;
      }
      if (!wallFaceRows || wallFaceRows.length < 2) {
        log('§GD_ELEMENTS found=0 source=wall');
        console.warn('[GridDims] No columns or walls found — returning empty grids');
        return empty;
      }
    }

    // Build entries per axis
    var xEntries = [];
    var yEntries = [];

    if (wallFaceRows) {
      // Wall face positions: cluster both lo and hi edges for each axis
      log('§GD_ELEMENTS found=' + wallFaceRows.length + ' source=wall-faces');
      for (var i = 0; i < wallFaceRows.length; i++) {
        var wguid = wallFaceRows[i][0];
        var xLo = wallFaceRows[i][1];
        var xHi = wallFaceRows[i][2];
        var yLo = wallFaceRows[i][3];
        var yHi = wallFaceRows[i][4];
        xEntries.push({ pos: xLo, guid: wguid + '_lo' });
        xEntries.push({ pos: xHi, guid: wguid + '_hi' });
        yEntries.push({ pos: yLo, guid: wguid + '_lo' });
        yEntries.push({ pos: yHi, guid: wguid + '_hi' });
      }
    } else {
      var rows = result[0].values;
      log('§GD_ELEMENTS found=' + rows.length + ' source=' + source);
      if (rows.length < 2) {
        console.warn('[GridDims] Fewer than 2 elements (' + rows.length + ') — returning empty grids');
        return empty;
      }
      for (var i = 0; i < rows.length; i++) {
        var guid = rows[i][0];
        var cx = rows[i][1];
        var cy = rows[i][2];
        xEntries.push({ pos: cx, guid: guid });
        yEntries.push({ pos: cy, guid: guid });
      }
    }

    // Cluster
    var xClusters = clusterEntries(xEntries, tolerance);
    var yClusters = clusterEntries(yEntries, tolerance);

    log('§GD_CLUSTER axis=X clusters=' + xClusters.length);
    log('§GD_CLUSTER axis=Y clusters=' + yClusters.length);

    // Label: X-axis gets numeric (1,2,3...) left-to-right
    // rawPosition = actual IFC cluster position; position = snapped display position
    var xLines = xClusters.map(function (c, idx) {
      return { label: String(idx + 1), position: c.position, rawPosition: c.position, guids: c.guids };
    });

    // Label: Y-axis gets letters (A,B,C...) bottom-to-top, skip I
    var letterSeq = 'A,B,C,D,E,F,G,H,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z'.split(',');
    var yLines = yClusters.map(function (c, idx) {
      var lbl = idx < letterSeq.length ? letterSeq[idx] : String.fromCharCode(65 + idx);
      return { label: lbl, position: c.position, rawPosition: c.position, guids: c.guids };
    });

    // Snap to nearest SNAP_MODULE (display positions only; rawPosition preserved)
    xLines = snapGrids(xLines);
    yLines = snapGrids(yLines);

    var xFiltered = filterStructural(xLines);
    var yFiltered = filterStructural(yLines);
    if (xFiltered.length !== xLines.length || yFiltered.length !== yLines.length) {
      log('§GD_FILTER x:' + xLines.length + '→' + xFiltered.length +
          ' y:' + yLines.length + '→' + yFiltered.length + ' (MIN_BAY=' + MIN_BAY_DISPLAY + 'm)');
    }
    xLines = xFiltered;
    yLines = yFiltered;

    var xThin = thinGrids(xLines);
    var yThin = thinGrids(yLines);
    if (xThin.length !== xLines.length || yThin.length !== yLines.length) {
      log('§GD_THIN x:' + xLines.length + '→' + xThin.length +
          ' y:' + yLines.length + '→' + yThin.length + ' (MAX_GRID_LINES=' + MAX_GRID_LINES + ')');
    }
    xLines = xThin;
    yLines = yThin;

    // Relabel sequentially after filtering — survivors get clean 1,2,3 / A,B,C
    for (var ri = 0; ri < xLines.length; ri++) xLines[ri].label = String(ri + 1);
    for (var rj = 0; rj < yLines.length; rj++) yLines[rj].label = rj < letterSeq.length ? letterSeq[rj] : String.fromCharCode(65 + rj);

    log('§GD_GRIDS xLines=' + xLines.length + ' yLines=' + yLines.length);

    return { xLines: xLines, yLines: yLines };
  }

  // ── generateDimensions ──────────────────────────────────────────

  /**
   * Generate dimension annotations between grid lines.
   * Bay dimensions for consecutive pairs + overall dimension per axis.
   * @param {{xLines: Array, yLines: Array}} gridResult
   * @returns {Array} [{startPos, endPos, axis, distance, label}]
   */
  function generateDimensions(gridResult) {
    var dims = [];
    var xLines = gridResult.xLines || [];
    var yLines = gridResult.yLines || [];

    // X-axis bay dimensions (between adjacent grids)
    if (xLines.length > 1) {
      for (var i = 0; i < xLines.length - 1; i++) {
        var dist = xLines[i + 1].position - xLines[i].position;
        dims.push({
          startPos: xLines[i].position,
          endPos: xLines[i + 1].position,
          axis: 'x',
          distance: dist,
          label: formatDim(dist),
          tier: 1,
          fromLabel: xLines[i].label,
          toLabel: xLines[i + 1].label
        });
      }
    }

    // X-axis overall dimension
    if (xLines.length >= 2) {
      var distX = xLines[xLines.length - 1].position - xLines[0].position;
      dims.push({
        startPos: xLines[0].position,
        endPos: xLines[xLines.length - 1].position,
        axis: 'x',
        distance: distX,
        label: formatDim(distX),
        tier: 2,
        fromLabel: xLines[0].label,
        toLabel: xLines[xLines.length - 1].label
      });
    }

    // Y-axis bay dimensions
    if (yLines.length > 1) {
      for (var j = 0; j < yLines.length - 1; j++) {
        var distY = yLines[j + 1].position - yLines[j].position;
        dims.push({
          startPos: yLines[j].position,
          endPos: yLines[j + 1].position,
          axis: 'y',
          distance: distY,
          label: formatDim(distY),
          tier: 1,
          fromLabel: yLines[j].label,
          toLabel: yLines[j + 1].label
        });
      }
    }

    // Y-axis overall dimension
    if (yLines.length >= 2) {
      var distYAll = yLines[yLines.length - 1].position - yLines[0].position;
      dims.push({
        startPos: yLines[0].position,
        endPos: yLines[yLines.length - 1].position,
        axis: 'y',
        distance: distYAll,
        label: formatDim(distYAll),
        tier: 2,
        fromLabel: yLines[0].label,
        toLabel: yLines[yLines.length - 1].label
      });
    }

    log('§GD_DIMS count=' + dims.length);
    return dims;
  }

  // ── renderGridEntities ──────────────────────────────────────────

  /**
   * Generate abstract draw commands for grids and dimensions.
   * @param {{xLines: Array, yLines: Array}} gridResult
   * @param {Array} dims - from generateDimensions()
   * @param {{minX: number, minY: number, maxX: number, maxY: number}} bbox - plan extents
   * @param {{viewScale: number}} [opts] - optional rendering options
   * @returns {Array} draw commands
   */
  function renderGridEntities(gridResult, dims, bbox, opts) {
    var viewScale = (opts && opts.viewScale) || 50; // px/m fallback — conservative
    var cmds = [];
    var xLines = gridResult.xLines || [];
    var yLines = gridResult.yLines || [];

    var ext = GRID_EXTEND;
    var bMinX = bbox.minX - ext;
    var bMaxX = bbox.maxX + ext;
    var bMinY = bbox.minY - ext;
    var bMaxY = bbox.maxY + ext;

    // Dim offset from bbox edge (meters) — tier 1 closer, tier 2 further
    var DIM_OFFSET_1 = 1.5;
    var DIM_OFFSET_2 = 2.5;

    // ── X-axis grid lines (vertical lines at each X position) ──
    for (var xi = 0; xi < xLines.length; xi++) {
      var xPos = xLines[xi].position;

      // Dashed vertical line
      cmds.push({ type: 'line', x1: xPos, y1: bMinY, x2: xPos, y2: bMaxY, dash: [0.3, 0.15], color: '#666677', lineWidth: 0.5 });

      // Label circle + text at bottom — screenR/screenH: renderer uses fixed px, no viewScale multiply
      cmds.push({ type: 'circle', cx: xPos, cy: bMinY - 0.5, r: LABEL_RADIUS, screenR: true, color: '#aaaaaa', fill: false });
      cmds.push({ type: 'text', x: xPos, y: bMinY - 0.5, text: xLines[xi].label, color: '#aaaaaa', fontSize: LABEL_TEXT_H, screenH: true, align: 'center' });
      // Label circle + text at top
      cmds.push({ type: 'circle', cx: xPos, cy: bMaxY + 0.5, r: LABEL_RADIUS, screenR: true, color: '#aaaaaa', fill: false });
      cmds.push({ type: 'text', x: xPos, y: bMaxY + 0.5, text: xLines[xi].label, color: '#aaaaaa', fontSize: LABEL_TEXT_H, screenH: true, align: 'center' });
    }

    // ── Y-axis grid lines (horizontal lines at each Y position) ──
    for (var yi = 0; yi < yLines.length; yi++) {
      var yPos = yLines[yi].position;

      // Dashed horizontal line
      cmds.push({ type: 'line', x1: bMinX, y1: yPos, x2: bMaxX, y2: yPos, dash: [0.3, 0.15], color: '#666677', lineWidth: 0.5 });

      // Label circle + text at left
      cmds.push({ type: 'circle', cx: bMinX - 0.5, cy: yPos, r: LABEL_RADIUS, screenR: true, color: '#aaaaaa', fill: false });
      cmds.push({ type: 'text', x: bMinX - 0.5, y: yPos, text: yLines[yi].label, color: '#aaaaaa', fontSize: LABEL_TEXT_H, screenH: true, align: 'center' });
      // Label circle + text at right
      cmds.push({ type: 'circle', cx: bMaxX + 0.5, cy: yPos, r: LABEL_RADIUS, screenR: true, color: '#aaaaaa', fill: false });
      cmds.push({ type: 'text', x: bMaxX + 0.5, y: yPos, text: yLines[yi].label, color: '#aaaaaa', fontSize: LABEL_TEXT_H, screenH: true, align: 'center' });
    }

    // ── Dimension annotations ─────────────────────────────────────
    var DIM_COLOR = '#99aacc';  // visible on dark bg
    var DIM_TEXT_H = 9;         // screen px

    // Crowding detection: if a tier-1 bay is narrower than MIN_DIM_SCREEN_PX on screen,
    // its inline label is suppressed (tick marks kept) and the bay is collected for a panel.
    var suppressedX = [], suppressedY = [];

    for (var di = 0; di < dims.length; di++) {
      var d = dims[di];
      var offset = d.tier === 2 ? DIM_OFFSET_2 : DIM_OFFSET_1;
      var bayScreenPx = d.distance * viewScale;
      var crowded = d.tier === 1 && bayScreenPx < MIN_DIM_SCREEN_PX;

      if (d.axis === 'x') {
        var dimY = bMaxY + offset;
        cmds.push({ type: 'line', x1: d.startPos, y1: dimY, x2: d.endPos, y2: dimY, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: d.startPos, y1: dimY - 0.15, x2: d.startPos, y2: dimY + 0.15, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: d.endPos, y1: dimY - 0.15, x2: d.endPos, y2: dimY + 0.15, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        if (!crowded) {
          cmds.push({ type: 'text', x: (d.startPos + d.endPos) / 2, y: dimY + 0.3, text: d.label, color: DIM_COLOR, fontSize: DIM_TEXT_H, screenH: true, align: 'center' });
        } else {
          suppressedX.push(d.fromLabel + '–' + d.toLabel + ':' + d.label);
        }
      } else {
        var dimX = bMinX - offset;
        cmds.push({ type: 'line', x1: dimX, y1: d.startPos, x2: dimX, y2: d.endPos, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: dimX - 0.15, y1: d.startPos, x2: dimX + 0.15, y2: d.startPos, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: dimX - 0.15, y1: d.endPos, x2: dimX + 0.15, y2: d.endPos, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        if (!crowded) {
          cmds.push({ type: 'text', x: dimX - 0.3, y: (d.startPos + d.endPos) / 2, text: d.label, color: DIM_COLOR, fontSize: DIM_TEXT_H, screenH: true, align: 'center' });
        } else {
          suppressedY.push(d.fromLabel + '–' + d.toLabel + ':' + d.label);
        }
      }
    }

    // If dims were suppressed, emit a compact panel text below the X overall dim
    // (no crowded text on canvas — summary in one place only)
    if (suppressedX.length > 0 || suppressedY.length > 0) {
      var panelX = (bbox.minX + bbox.maxX) / 2;
      var panelY = bMaxY + DIM_OFFSET_2 + 0.8;
      var panelLines = [];
      if (suppressedX.length) panelLines.push('X: ' + suppressedX.join('  '));
      if (suppressedY.length) panelLines.push('Y: ' + suppressedY.join('  '));
      for (var pi = 0; pi < panelLines.length; pi++) {
        cmds.push({ type: 'text', x: panelX, y: panelY + pi * 0.6, text: panelLines[pi],
                    color: '#778899', fontSize: 8, screenH: true, align: 'center' });
      }
      log('§GD_CROWD suppressedX=' + suppressedX.length + ' suppressedY=' + suppressedY.length +
          ' viewScale=' + viewScale.toFixed(1) + ' minPx=' + MIN_DIM_SCREEN_PX);
    }

    log('§GD_RENDER entities=' + cmds.length);
    return cmds;
  }

  // ── detectGridsAtPlane ───────────────────────────────────────────

  /**
   * Detect grid lines from structural elements crossing a horizontal cut plane.
   * Same clustering as detectGrids() but filtered to elements whose vertical
   * extent includes cutZ (IFC metres).
   * @param {object} db - sql.js database instance
   * @param {number} cutZ - IFC Z height of the cut plane
   * @param {number} [tolerance=0.3] - clustering tolerance in meters
   * @returns {{xLines: Array, yLines: Array}}
   */
  function detectGridsAtPlane(db, cutZ, tolerance) {
    if (tolerance === undefined || tolerance === null) tolerance = DEFAULT_TOLERANCE;
    var empty = { xLines: [], yLines: [] };

    var sql =
      "SELECT m.guid, t.center_x, t.center_y " +
      "FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class IN ('IfcColumn','IfcWall','IfcWallStandardCase','IfcBeam','IfcMember') " +
      "  AND (t.center_z - COALESCE(t.bbox_z,3.0)/2) <= " + Number(cutZ) +
      "  AND (t.center_z + COALESCE(t.bbox_z,3.0)/2) >= " + Number(cutZ);

    var result;
    try {
      result = db.exec(sql);
    } catch (e) {
      log('§GD_PLANE query error: ' + e.message);
      return empty;
    }

    if (!result || !result.length || !result[0].values.length) {
      log('§GD_PLANE cutZ=' + cutZ.toFixed(2) + ' elements=0');
      return empty;
    }

    var rows = result[0].values;
    log('§GD_PLANE cutZ=' + cutZ.toFixed(2) + ' elements=' + rows.length);

    var xEntries = [], yEntries = [];
    for (var i = 0; i < rows.length; i++) {
      xEntries.push({ pos: rows[i][1], guid: rows[i][0] });
      yEntries.push({ pos: rows[i][2], guid: rows[i][0] });
    }

    var xClusters = clusterEntries(xEntries, tolerance);
    var yClusters = clusterEntries(yEntries, tolerance);

    var xLines = xClusters.map(function (c, idx) {
      return { label: String(idx + 1), position: c.position, rawPosition: c.position, guids: c.guids };
    });
    var letterSeq = 'A,B,C,D,E,F,G,H,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z'.split(',');
    var yLines = yClusters.map(function (c, idx) {
      var lbl = idx < letterSeq.length ? letterSeq[idx] : String.fromCharCode(65 + idx);
      return { label: lbl, position: c.position, rawPosition: c.position, guids: c.guids };
    });

    xLines = snapGrids(xLines);
    yLines = snapGrids(yLines);
    xLines = filterStructural(xLines);
    yLines = filterStructural(yLines);
    xLines = thinGrids(xLines);
    yLines = thinGrids(yLines);

    // Relabel sequentially after filtering — survivors keep position but get clean A,B,C / 1,2,3
    for (var ri = 0; ri < xLines.length; ri++) xLines[ri].label = String(ri + 1);
    for (var rj = 0; rj < yLines.length; rj++) yLines[rj].label = rj < letterSeq.length ? letterSeq[rj] : String.fromCharCode(65 + rj);

    log('§GD_PLANE_GRIDS cutZ=' + cutZ.toFixed(2) + ' xLines=' + xLines.length + ' yLines=' + yLines.length);
    return { xLines: xLines, yLines: yLines };
  }

  // ── Attach to window ────────────────────────────────────────────

  if (typeof window !== 'undefined') {
    window.GridDims = {
      detectGrids: detectGrids,
      detectGridsAtPlane: detectGridsAtPlane,
      generateDimensions: generateDimensions,
      renderGridEntities: renderGridEntities
    };
  }

})();
