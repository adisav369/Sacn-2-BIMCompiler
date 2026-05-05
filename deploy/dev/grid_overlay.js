/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_overlay.js — 3D Grid Overlay Mode (2D_022 spec)
 *
 * Implementing BBC.md §2D_022 — Witness: W-GRID-OVERLAY
 *
 * Shows architectural grid lines as Three.js scene objects overlaid on the 3D model.
 * Reuses GridDims.detectGrids() from grid_dims.js for column-based detection.
 * Self-contained: if this file fails to load or throws, the viewer is unaffected.
 *
 * API: setupGridOverlay(APP) — attaches APP.toggleGridOverlay
 *
 * Log tags:
 *   §GRID_MODE      — mode enter/exit
 *   §GRID_DETECT    — detection results
 *   §GRID_BBOX      — building envelope from DB
 *   §GRID_LINE      — each grid line created
 *   §GRID_ZOOM      — zoom-to-grid action
 *   §GRID_INIT      — setup/failure
 */
function setupGridOverlay(APP) {
  'use strict';
  var A = APP;

  // ── State ─────────────────────────────────────────────────────────
  var gridGroup = null;        // THREE.Group holding all grid lines + bubbles
  var gridPanel = null;        // DOM panel element
  var gridData = null;         // { xLines, yLines } from GridDims
  var dimsData = null;         // dimensions from GridDims.generateDimensions
  var active = false;
  var selectedLabel = null;    // currently highlighted grid label
  var lineMeshes = {};         // label -> THREE.Line object
  var bubbleScale = 1.0;       // computed from building size

  // ── Constants ─────────────────────────────────────────────────────
  var COLOR_DEFAULT = 0x000000;       // black grid lines
  var COLOR_HIGHLIGHT = 0xff8c00;     // orange on selection
  var LINE_OVERSHOOT_RATIO = 0.15;   // extend 15% of building dim past envelope
  var LINE_OVERSHOOT_MIN = 2.0;      // at least 2m overshoot
  var PANEL_ID = 'grid-overlay-panel';

  // ── Helpers ───────────────────────────────────────────────────────

  function log(msg) { console.log('[GridOverlay] ' + msg); }

  /**
   * Get building envelope from DB in IFC coordinates.
   * Returns { xMin, xMax, yMin, yMax, zMin, zMax } in IFC metres.
   */
  function getBuildingEnvelopeIFC() {
    var fallback = { xMin: -10, xMax: 10, yMin: -10, yMax: 10, zMin: 0, zMax: 6 };
    if (!A.db) return fallback;
    try {
      var r = A.db.exec(
        'SELECT MIN(center_x), MAX(center_x), MIN(center_y), MAX(center_y), MIN(center_z), MAX(center_z) FROM element_transforms'
      );
      if (!r || !r.length || !r[0].values.length || r[0].values[0][0] == null) return fallback;
      var v = r[0].values[0];
      var env = { xMin: v[0], xMax: v[1], yMin: v[2], yMax: v[3], zMin: v[4], zMax: v[5] };
      log('§GRID_BBOX ifc x=[' + env.xMin.toFixed(1) + ',' + env.xMax.toFixed(1) +
          '] y=[' + env.yMin.toFixed(1) + ',' + env.yMax.toFixed(1) +
          '] z=[' + env.zMin.toFixed(1) + ',' + env.zMax.toFixed(1) + ']');
      return env;
    } catch (e) {
      log('§GRID_BBOX query error: ' + e.message);
      return fallback;
    }
  }

  /** Create a circle sprite (billboard) for grid bubble */
  function createBubble(label, position, highlighted) {
    var canvas = document.createElement('canvas');
    canvas.width = 64; canvas.height = 64;
    var ctx = canvas.getContext('2d');
    ctx.beginPath();
    ctx.arc(32, 32, 28, 0, Math.PI * 2);
    ctx.fillStyle = highlighted ? '#fff3e0' : '#ffffff';
    ctx.fill();
    ctx.lineWidth = 3;
    ctx.strokeStyle = highlighted ? '#ff8c00' : '#000000';
    ctx.stroke();
    ctx.fillStyle = highlighted ? '#ff8c00' : '#000000';
    ctx.font = 'bold 28px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, 32, 33);

    var texture = new THREE.CanvasTexture(canvas);
    var mat = new THREE.SpriteMaterial({ map: texture, depthTest: false });
    var sprite = new THREE.Sprite(mat);
    sprite.position.copy(position);
    sprite.scale.set(bubbleScale, bubbleScale, 1);
    sprite.userData.gridLabel = label;
    return sprite;
  }

  // ── Build Grid Scene Objects ──────────────────────────────────────

  function buildGridScene(grids, env) {
    if (gridGroup) {
      A.scene.remove(gridGroup);
      gridGroup.traverse(function(obj) {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) {
          if (obj.material.map) obj.material.map.dispose();
          obj.material.dispose();
        }
      });
    }
    gridGroup = new THREE.Group();
    gridGroup.name = 'gridOverlay';
    lineMeshes = {};

    // Building dimensions in IFC coords
    var bldW = env.xMax - env.xMin;  // IFC X width
    var bldD = env.yMax - env.yMin;  // IFC Y depth
    var bldH = env.zMax - env.zMin;  // IFC Z height
    var maxDim = Math.max(bldW, bldD);

    // Overshoot: 15% of building dimension, min 2m
    var overshootX = Math.max(LINE_OVERSHOOT_MIN, bldD * LINE_OVERSHOOT_RATIO);
    var overshootY = Math.max(LINE_OVERSHOOT_MIN, bldW * LINE_OVERSHOOT_RATIO);

    // Bubble size: ~3% of max building dimension (visible but not huge)
    bubbleScale = Math.max(1.0, maxDim * 0.03);
    log('§GRID_BBOX bldW=' + bldW.toFixed(1) + ' bldD=' + bldD.toFixed(1) +
        ' bldH=' + bldH.toFixed(1) + ' bubbleScale=' + bubbleScale.toFixed(2));

    // Ground floor Y in Three.js (IFC Z=zMin → Three Y)
    var groundY = (env.zMin - A.modelOffset.z) - 0.05;

    // X-axis grids: constant IFC X, run along IFC Y direction
    // Line endpoints in IFC: (xPos, yMin - overshoot, zMin) to (xPos, yMax + overshoot, zMin)
    var xLines = grids.xLines || [];
    for (var i = 0; i < xLines.length; i++) {
      var xPos = xLines[i].position;
      var p0_ifc = { x: xPos, y: env.yMin - overshootX, z: env.zMin };
      var p1_ifc = { x: xPos, y: env.yMax + overshootX, z: env.zMin };
      var p0 = A.ifc2three(p0_ifc.x, p0_ifc.y, p0_ifc.z);
      var p1 = A.ifc2three(p1_ifc.x, p1_ifc.y, p1_ifc.z);
      var v0 = new THREE.Vector3(p0.x, groundY, p0.z);
      var v1 = new THREE.Vector3(p1.x, groundY, p1.z);

      addGridLine(xLines[i].label, 'X', xPos, v0, v1);
    }

    // Y-axis grids: constant IFC Y, run along IFC X direction
    var yLines = grids.yLines || [];
    for (var j = 0; j < yLines.length; j++) {
      var yPos = yLines[j].position;
      var q0_ifc = { x: env.xMin - overshootY, y: yPos, z: env.zMin };
      var q1_ifc = { x: env.xMax + overshootY, y: yPos, z: env.zMin };
      var q0 = A.ifc2three(q0_ifc.x, q0_ifc.y, q0_ifc.z);
      var q1 = A.ifc2three(q1_ifc.x, q1_ifc.y, q1_ifc.z);
      var w0 = new THREE.Vector3(q0.x, groundY, q0.z);
      var w1 = new THREE.Vector3(q1.x, groundY, q1.z);

      addGridLine(yLines[j].label, 'Y', yPos, w0, w1);
    }

    A.scene.add(gridGroup);
    A.markDirty();
    log('§GRID_MODE lines=' + Object.keys(lineMeshes).length + ' added to scene');
  }

  function addGridLine(label, axis, ifcPos, v0, v1) {
    var geom = new THREE.BufferGeometry().setFromPoints([v0, v1]);
    var mat = new THREE.LineBasicMaterial({ color: COLOR_DEFAULT, depthTest: false });
    var line = new THREE.Line(geom, mat);
    line.renderOrder = 999;
    line.userData = { gridLabel: label, gridAxis: axis, gridPos: ifcPos };
    gridGroup.add(line);
    lineMeshes[label] = line;

    // Bubbles at both ends
    gridGroup.add(createBubble(label, v0, false));
    gridGroup.add(createBubble(label, v1, false));

    log('§GRID_LINE axis=' + axis + ' ifc=' + ifcPos.toFixed(3) + ' label=' + label +
        ' three=[(' + v0.x.toFixed(1) + ',' + v0.z.toFixed(1) + ')→(' + v1.x.toFixed(1) + ',' + v1.z.toFixed(1) + ')]');
  }

  // ── Measurements Panel ────────────────────────────────────────────

  function buildPanel(grids) {
    if (gridPanel) gridPanel.remove();

    gridPanel = document.createElement('div');
    gridPanel.id = PANEL_ID;
    gridPanel.style.cssText = 'position:fixed;top:56px;left:16px;z-index:10;background:rgba(20,40,80,0.55);border-radius:8px;padding:0;border:1px solid rgba(255,255,255,0.15);backdrop-filter:blur(8px);min-width:180px;max-width:260px';
    gridPanel.innerHTML = '<b style="display:block;padding:6px 10px;color:#4fc3f7;font-size:12px;cursor:grab" onclick="togglePanel(\'grid-panel-body\')">Grid Dimensions</b>' +
      '<div id="grid-panel-body" class="panel-body" style="max-height:300px;overflow-y:auto;padding:4px 10px"></div>';
    document.body.appendChild(gridPanel);
    if (A._makeDraggable) A._makeDraggable(gridPanel);

    var body = document.getElementById('grid-panel-body');
    if (!body) return;

    var html = '';

    // X-axis (numeric labels)
    var xLines = grids.xLines || [];
    if (xLines.length > 1) {
      html += '<div style="color:#aaa;font-size:10px;margin:4px 0 2px;border-bottom:1px solid #333">X-Axis (1,2,3…)</div>';
      for (var i = 0; i < xLines.length - 1; i++) {
        var dist = Math.abs(xLines[i + 1].position - xLines[i].position);
        var lbl = xLines[i].label + '–' + xLines[i + 1].label;
        html += '<div class="grid-row" data-label="' + xLines[i].label + '" style="padding:3px 4px;cursor:pointer;border-radius:3px;font-size:12px;display:flex;justify-content:space-between">' +
          '<span>' + lbl + '</span><span style="color:#4fc3f7">' + (dist * 1000).toFixed(0) + ' mm</span></div>';
      }
      var totalX = Math.abs(xLines[xLines.length - 1].position - xLines[0].position);
      html += '<div style="padding:3px 4px;font-size:11px;color:#888;display:flex;justify-content:space-between"><span>' +
        xLines[0].label + '–' + xLines[xLines.length - 1].label + ' total</span><span>' + (totalX * 1000).toFixed(0) + ' mm</span></div>';
    }

    // Y-axis (letter labels)
    var yLines = grids.yLines || [];
    if (yLines.length > 1) {
      html += '<div style="color:#aaa;font-size:10px;margin:8px 0 2px;border-bottom:1px solid #333">Y-Axis (A,B,C…)</div>';
      for (var j = 0; j < yLines.length - 1; j++) {
        var dist2 = Math.abs(yLines[j + 1].position - yLines[j].position);
        var lbl2 = yLines[j].label + '–' + yLines[j + 1].label;
        html += '<div class="grid-row" data-label="' + yLines[j].label + '" style="padding:3px 4px;cursor:pointer;border-radius:3px;font-size:12px;display:flex;justify-content:space-between">' +
          '<span>' + lbl2 + '</span><span style="color:#4fc3f7">' + (dist2 * 1000).toFixed(0) + ' mm</span></div>';
      }
      var totalY = Math.abs(yLines[yLines.length - 1].position - yLines[0].position);
      html += '<div style="padding:3px 4px;font-size:11px;color:#888;display:flex;justify-content:space-between"><span>' +
        yLines[0].label + '–' + yLines[yLines.length - 1].label + ' total</span><span>' + (totalY * 1000).toFixed(0) + ' mm</span></div>';
    }

    if (!html) html = '<div style="color:#888;font-size:11px;padding:8px">No grids detected — need ≥3 columns</div>';

    body.innerHTML = html;

    // Attach click handlers
    var rows = body.querySelectorAll('.grid-row');
    for (var r = 0; r < rows.length; r++) {
      rows[r].addEventListener('pointerup', onPanelRowClick);
    }
  }

  // ── Click-to-Zoom + Orange Highlight ──────────────────────────────

  function onPanelRowClick(e) {
    var label = e.currentTarget.getAttribute('data-label');
    if (!label) return;
    highlightGrid(label);
    zoomToGrid(label);
  }

  function highlightGrid(label) {
    // Reset previous line
    if (selectedLabel && lineMeshes[selectedLabel]) {
      lineMeshes[selectedLabel].material.color.setHex(COLOR_DEFAULT);
    }

    // Reset panel row highlights
    if (gridPanel) {
      var rows = gridPanel.querySelectorAll('.grid-row');
      for (var i = 0; i < rows.length; i++) {
        rows[i].style.background = '';
        rows[i].style.color = '';
      }
    }

    selectedLabel = label;

    // Highlight the line orange
    if (lineMeshes[label]) {
      lineMeshes[label].material.color.setHex(COLOR_HIGHLIGHT);
    }

    // Highlight matching panel rows
    if (gridPanel) {
      var matching = gridPanel.querySelectorAll('.grid-row[data-label="' + label + '"]');
      for (var j = 0; j < matching.length; j++) {
        matching[j].style.background = 'rgba(255,140,0,0.2)';
        matching[j].style.color = '#ff8c00';
      }
    }

    // Rebuild bubbles with highlight state
    if (gridGroup) {
      gridGroup.traverse(function(obj) {
        if (obj.isSprite && obj.userData.gridLabel) {
          if (obj.material.map) obj.material.map.dispose();
          obj.material.dispose();
          var isHL = (obj.userData.gridLabel === label);
          var fresh = createBubble(obj.userData.gridLabel, obj.position, isHL);
          obj.material = fresh.material;
        }
      });
    }

    A.markDirty();
    log('§GRID_ZOOM highlight=' + label);
  }

  function zoomToGrid(label) {
    var line = lineMeshes[label];
    if (!line) { log('§GRID_ZOOM FAIL label=' + label + ' not found'); return; }

    var positions = line.geometry.attributes.position;
    var mid = new THREE.Vector3(
      (positions.getX(0) + positions.getX(1)) / 2,
      (positions.getY(0) + positions.getY(1)) / 2,
      (positions.getZ(0) + positions.getZ(1)) / 2
    );

    // Camera offset: look from above-and-to-the-side at the grid midpoint
    // Use current camera distance as reference, but don't fly too far
    var curDist = A.camera.position.distanceTo(A.controls.target);
    var dist = Math.max(curDist * 0.5, 15);

    var targetPos = new THREE.Vector3(
      mid.x + dist * 0.4,
      mid.y + dist * 0.8,
      mid.z + dist * 0.4
    );

    log('§GRID_ZOOM label=' + label +
        ' mid=(' + mid.x.toFixed(1) + ',' + mid.y.toFixed(1) + ',' + mid.z.toFixed(1) + ')' +
        ' camTo=(' + targetPos.x.toFixed(1) + ',' + targetPos.y.toFixed(1) + ',' + targetPos.z.toFixed(1) + ')' +
        ' dist=' + dist.toFixed(1));

    // Animate (16 frames ≈ 270ms)
    var startPos = A.camera.position.clone();
    var startTarget = A.controls.target.clone();
    var frame = 0;
    var totalFrames = 16;

    function step() {
      frame++;
      var t = frame / totalFrames;
      t = t * (2 - t); // ease-out
      A.camera.position.lerpVectors(startPos, targetPos, t);
      A.controls.target.lerpVectors(startTarget, mid, t);
      A.controls.update();
      A.markDirty();
      if (frame < totalFrames) requestAnimationFrame(step);
    }
    step();
  }

  // ── Toggle ────────────────────────────────────────────────────────

  A.toggleGridOverlay = function() {
    if (active) {
      active = false;
      if (gridGroup) { gridGroup.visible = false; }
      if (gridPanel) { gridPanel.style.display = 'none'; }
      A.markDirty();
      log('§GRID_MODE state=exit');
      return;
    }

    active = true;
    log('§GRID_MODE state=enter');

    // If already built, just show
    if (gridGroup && gridData) {
      gridGroup.visible = true;
      if (gridPanel) gridPanel.style.display = '';
      A.markDirty();
      return;
    }

    // Preflight
    if (typeof GridDims === 'undefined' || !GridDims.detectGrids) {
      log('§GRID_DETECT ERROR: GridDims not available');
      A.status.textContent = 'Grid detection unavailable';
      active = false;
      return;
    }
    if (!A.db) {
      log('§GRID_DETECT ERROR: no database loaded');
      A.status.textContent = 'Load a building first';
      active = false;
      return;
    }

    // Detect grids (column-based only — structural grid, not internal walls)
    gridData = GridDims.detectGrids(A.db);
    log('§GRID_DETECT xLines=' + (gridData.xLines || []).length + ' yLines=' + (gridData.yLines || []).length);

    if ((!gridData.xLines || !gridData.xLines.length) && (!gridData.yLines || !gridData.yLines.length)) {
      A.status.textContent = 'No grid lines detected (need ≥3 columns)';
      active = false;
      return;
    }

    dimsData = GridDims.generateDimensions(gridData);

    // Get building envelope from DB — not from scene (scene has 50km ground plane)
    var env = getBuildingEnvelopeIFC();
    buildGridScene(gridData, env);
    buildPanel(gridData);

    A.status.textContent = 'Grid mode — ' + ((gridData.xLines || []).length + (gridData.yLines || []).length) + ' grid lines';
  };

  window.toggleGridOverlay = A.toggleGridOverlay;
  log('§GRID_INIT ready');
}
