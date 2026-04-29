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
  var GRID_EXTEND = 1.0; // meters — how far grid lines extend beyond plan bbox
  var LABEL_RADIUS = 10; // screen px — circle radius for grid bubbles (renderer must NOT multiply by viewScale)
  var LABEL_TEXT_H = 10; // screen px — bubble label font size (renderer must NOT multiply by viewScale)

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
   */
  function snapGrids(lines) {
    if (lines.length < 2) return lines;

    var snapped = [lines[0]];
    for (var i = 1; i < lines.length; i++) {
      var rawBayMm = (lines[i].position - lines[i - 1].position) * 1000;
      var snappedBayMm = Math.round(rawBayMm / SNAP_MODULE) * SNAP_MODULE;
      if (snappedBayMm < SNAP_MODULE) snappedBayMm = SNAP_MODULE;
      var newPos = snapped[snapped.length - 1].position + snappedBayMm / 1000;
      snapped.push({ label: lines[i].label, position: newPos, guids: lines[i].guids });
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

    // Query columns
    var sql =
      "SELECT m.guid, t.center_x, t.center_y " +
      "FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class IN ('IfcColumn')";

    var result;
    try {
      result = db.exec(sql);
    } catch (e) {
      log('§GD_COLUMNS query error: ' + e.message);
      return empty;
    }

    if (!result || !result.length || !result[0].values.length) {
      log('§GD_COLUMNS found=0');
      console.warn('[GridDims] No columns found — returning empty grids');
      return empty;
    }

    var rows = result[0].values;
    log('§GD_COLUMNS found=' + rows.length);

    if (rows.length < 3) {
      console.warn('[GridDims] Fewer than 3 columns (' + rows.length + ') — returning empty grids');
      return empty;
    }

    // Build entries per axis
    var xEntries = [];
    var yEntries = [];
    for (var i = 0; i < rows.length; i++) {
      var guid = rows[i][0];
      var cx = rows[i][1];
      var cy = rows[i][2];
      xEntries.push({ pos: cx, guid: guid });
      yEntries.push({ pos: cy, guid: guid });
    }

    // Cluster
    var xClusters = clusterEntries(xEntries, tolerance);
    var yClusters = clusterEntries(yEntries, tolerance);

    log('§GD_CLUSTER axis=X clusters=' + xClusters.length);
    log('§GD_CLUSTER axis=Y clusters=' + yClusters.length);

    // Label: X-axis gets numeric (1,2,3...) left-to-right
    var xLines = xClusters.map(function (c, idx) {
      return { label: String(idx + 1), position: c.position, guids: c.guids };
    });

    // Label: Y-axis gets letters (A,B,C...) bottom-to-top, skip I
    var letterSeq = 'A,B,C,D,E,F,G,H,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z'.split(',');
    var yLines = yClusters.map(function (c, idx) {
      var lbl = idx < letterSeq.length ? letterSeq[idx] : String.fromCharCode(65 + idx);
      return { label: lbl, position: c.position, guids: c.guids };
    });

    // Snap to nearest SNAP_MODULE
    xLines = snapGrids(xLines);
    yLines = snapGrids(yLines);

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
    if (xLines.length > 2) {
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
    if (yLines.length > 2) {
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
   * @returns {Array} draw commands
   */
  function renderGridEntities(gridResult, dims, bbox) {
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
    for (var di = 0; di < dims.length; di++) {
      var d = dims[di];
      var offset = d.tier === 2 ? DIM_OFFSET_2 : DIM_OFFSET_1;

      if (d.axis === 'x') {
        var dimY = bMaxY + offset;
        cmds.push({ type: 'line', x1: d.startPos, y1: dimY, x2: d.endPos, y2: dimY, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: d.startPos, y1: dimY - 0.15, x2: d.startPos, y2: dimY + 0.15, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: d.endPos, y1: dimY - 0.15, x2: d.endPos, y2: dimY + 0.15, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'text', x: (d.startPos + d.endPos) / 2, y: dimY + 0.3, text: d.label, color: DIM_COLOR, fontSize: DIM_TEXT_H, screenH: true, align: 'center' });
      } else {
        var dimX = bMinX - offset;
        cmds.push({ type: 'line', x1: dimX, y1: d.startPos, x2: dimX, y2: d.endPos, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: dimX - 0.15, y1: d.startPos, x2: dimX + 0.15, y2: d.startPos, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'line', x1: dimX - 0.15, y1: d.endPos, x2: dimX + 0.15, y2: d.endPos, dash: null, color: DIM_COLOR, lineWidth: 0.3 });
        cmds.push({ type: 'text', x: dimX - 0.3, y: (d.startPos + d.endPos) / 2, text: d.label, color: DIM_COLOR, fontSize: DIM_TEXT_H, screenH: true, align: 'center' });
      }
    }

    log('§GD_RENDER entities=' + cmds.length);
    return cmds;
  }

  // ── Attach to window ────────────────────────────────────────────

  if (typeof window !== 'undefined') {
    window.GridDims = {
      detectGrids: detectGrids,
      generateDimensions: generateDimensions,
      renderGridEntities: renderGridEntities
    };
  }

})();
