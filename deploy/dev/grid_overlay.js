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
  var lineMeshes = {};         // label -> { line, v0, v1 }
  var bubbleScale = 1.0;       // computed from building size
  var envCache = null;         // cached building envelope
  var zoomAnim = null;         // current zoom animation ID (for cancellation)

  // View state is managed by GridViews (grid_views.js)

  // ── Constants ─────────────────────────────────────────────────────
  var COLOR_HIGHLIGHT = 0xff6600;     // bright orange on selection
  var LINE_OVERSHOOT_RATIO = 0.15;   // extend 15% of building dim past envelope
  var LINE_OVERSHOOT_MIN = 2.0;      // at least 2m overshoot
  var PANEL_ID = 'grid-overlay-panel';
  var BUBBLE_MAX_SCREEN_FRAC = 0.02; // max 2% of visible width in ortho

  // ── Helpers ───────────────────────────────────────────────────────

  function log(msg) { console.log('[GridOverlay] ' + msg); }

  /** Theme-aware colors — flips with sunglasses */
  function isLight() { return !!A.lightTheme; }
  function lineColor() { return isLight() ? 0x444444 : 0xcccccc; }
  function bubbleStroke() { return isLight() ? '#333333' : '#666666'; }
  function bubbleText() { return isLight() ? '#222222' : '#444444'; }
  function panelBg() { return isLight() ? 'rgba(255,255,255,0.9)' : 'rgba(20,40,80,0.55)'; }
  function panelBorder() { return isLight() ? 'rgba(0,0,0,0.3)' : 'rgba(255,255,255,0.15)'; }
  function panelText() { return isLight() ? '#000000' : '#4fc3f7'; }
  function panelDimText() { return isLight() ? '#000000' : '#4fc3f7'; }
  function panelSubText() { return isLight() ? '#444444' : '#aaaaaa'; }
  function panelTotalText() { return isLight() ? '#333333' : '#888888'; }
  function panelDivider() { return isLight() ? '#999999' : '#333333'; }

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
    ctx.lineWidth = highlighted ? 5 : 3;
    ctx.strokeStyle = highlighted ? '#ff6600' : bubbleStroke();
    ctx.stroke();
    ctx.fillStyle = highlighted ? '#ff6600' : bubbleText();
    ctx.font = 'bold 26px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, 32, 33);

    var texture = new THREE.CanvasTexture(canvas);
    var mat = new THREE.SpriteMaterial({ map: texture, depthTest: false });
    var sprite = new THREE.Sprite(mat);
    sprite.position.copy(position);
    sprite.scale.set(bubbleScale, bubbleScale, 1);
    sprite.renderOrder = 1000;
    sprite.userData.gridLabel = label;
    return sprite;
  }

  /** Clamp bubble + dim sprites so they never exceed BUBBLE_MAX_SCREEN_FRAC of ortho view.
   *  When dense, hides dim labels that would overlap; zooming in reveals them progressively. */
  function clampBubbleScales() {
    if (!active || !gridGroup) return;
    var cam = A.camera;
    if (!cam.isOrthographicCamera) return;
    // OrbitControls zooms ortho via camera.zoom — actual visible width = (right-left)/zoom
    var visW = (cam.right - cam.left) / (cam.zoom || 1);
    var maxS = visW * BUBBLE_MAX_SCREEN_FRAC;
    var s = Math.min(bubbleScale, maxS);
    gridGroup.traverse(function(obj) {
      if (obj.isSprite && obj.userData.gridLabel) {
        obj.scale.set(s, s, 1);
      }
    });
    // Clamp dim chain labels + density filter
    if (typeof DimChains !== 'undefined' && DimChains.clampScales) {
      DimChains.clampScales(gridGroup, bubbleScale, s, visW);
    }
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

    // Bubble size: ~4% of max building dimension
    bubbleScale = Math.max(1.2, maxDim * 0.04);
    log('§GRID_BBOX bldW=' + bldW.toFixed(1) + ' bldD=' + bldD.toFixed(1) +
        ' bldH=' + bldH.toFixed(1) + ' bubbleScale=' + bubbleScale.toFixed(2));

    // Ground floor Y in Three.js (IFC Z=zMin → Three Y)
    var groundY = (env.zMin - A.modelOffset.z) - 0.05;

    // X-axis grids: constant IFC X, run along IFC Y direction
    var xLines = grids.xLines || [];
    for (var i = 0; i < xLines.length; i++) {
      var xPos = xLines[i].position;
      var p0 = A.ifc2three(xPos, env.yMin - overshootX, env.zMin);
      var p1 = A.ifc2three(xPos, env.yMax + overshootX, env.zMin);
      var v0 = new THREE.Vector3(p0.x, groundY, p0.z);
      var v1 = new THREE.Vector3(p1.x, groundY, p1.z);
      addGridLine(xLines[i].label, 'X', xPos, v0, v1);
    }

    // Y-axis grids: constant IFC Y, run along IFC X direction
    var yLines = grids.yLines || [];
    for (var j = 0; j < yLines.length; j++) {
      var yPos = yLines[j].position;
      var q0 = A.ifc2three(env.xMin - overshootY, yPos, env.zMin);
      var q1 = A.ifc2three(env.xMax + overshootY, yPos, env.zMin);
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
    var mat = new THREE.LineBasicMaterial({ color: lineColor() });
    var line = new THREE.Line(geom, mat);
    line.renderOrder = 999;
    line.userData = { gridLabel: label, gridAxis: axis, gridPos: ifcPos };
    gridGroup.add(line);
    lineMeshes[label] = { line: line, v0: v0.clone(), v1: v1.clone() };

    // Bubbles at both ends
    gridGroup.add(createBubble(label, v0, false));
    gridGroup.add(createBubble(label, v1, false));

    log('§GRID_LINE axis=' + axis + ' ifc=' + ifcPos.toFixed(3) + ' label=' + label +
        ' three=[(' + v0.x.toFixed(1) + ',' + v0.z.toFixed(1) + ')→(' + v1.x.toFixed(1) + ',' + v1.z.toFixed(1) + ')]');
  }

  // ── View Presets (delegated to GridViews) ───────────────────────────

  var VIEW_BTN_STYLE = 'background:#444;color:#ccc;border:1px solid #666;border-radius:4px;padding:3px 8px;font-size:11px;cursor:pointer';
  var VIEW_BTN_ACTIVE = 'background:#4fc3f7;color:#000;border:1px solid #666;border-radius:4px;padding:3px 8px;font-size:11px;cursor:pointer';

  /** Update active state on all view buttons */
  function updateViewButtons() {
    var av = GridViews.activeView();
    var btns = document.querySelectorAll('.grid-view-btn');
    for (var i = 0; i < btns.length; i++) {
      var v = btns[i].getAttribute('data-view');
      btns[i].style.cssText = (v === av) ? VIEW_BTN_ACTIVE : VIEW_BTN_STYLE;
    }
  }

  /** Handle view button click — orchestrates engines → renderer */
  function onViewBtnClick(e) {
    var mode = e.currentTarget.getAttribute('data-view');
    if (!mode) return;

    // Clear previous contours
    if (typeof GridContours !== 'undefined') GridContours.clear(A);

    if (mode === 'unlock') {
      GridViews.unlockView(A);
    } else {
      GridViews.lockView(A, mode, envCache);
      renderContoursForView(mode);
      clampBubbleScales();
    }
    updateViewButtons();
  }

  /** Orchestrate: read contourMode from config, call engine, pass to renderer */
  function renderContoursForView(mode) {
    if (typeof GridConfig === 'undefined') return;
    var contourMode = GridConfig.contourModeFor(mode);
    if (!contourMode) return; // null = no contours (e.g. roof)

    if (contourMode === 'section' && typeof SectionCut !== 'undefined' && typeof GridContours !== 'undefined') {
      // Floor plan: section cut → contours + door arcs
      var clipCfg = GridConfig.clipFor(mode);
      var bldH = envCache.zMax - envCache.zMin;
      var cutZ = (clipCfg && clipCfg.offset_ratio)
        ? envCache.zMin + bldH * clipCfg.offset_ratio
        : envCache.zMin + ((clipCfg && clipCfg.offset_m) || 1.0);

      var results = SectionCut.sectionCut(A.db, A.libDb, cutZ, null);
      GridContours.renderContours(A, results, mode, cutZ);

      // Door arcs
      if (typeof DoorArcs !== 'undefined') {
        var doors = results.filter(function(r) { return r.ifcClass === 'IfcDoor'; });
        var walls = results.filter(function(r) { return r.ifcClass === 'IfcWall' || r.ifcClass === 'IfcWallStandardCase'; });
        var arcs = DoorArcs.generateArcs(doors, walls);
        GridContours.addDoorArcs(A, arcs, mode, cutZ);
      }
      log('§GRID_VIEW contours=section mode=' + mode + ' cutZ=' + cutZ.toFixed(2));

    } else if (contourMode === 'elevation' && typeof Elevation !== 'undefined' && typeof GridContours !== 'undefined') {
      // Elevation: projected edges + level markers
      var face = mode; // front/rear/left/right map directly to Elevation face names
      var edgeData = Elevation.generateElevation(A.db, A.libDb, face);
      GridContours.renderEdges(A, edgeData, mode, envCache);

      // Level markers
      if (typeof SectionCut !== 'undefined') {
        var storeys = SectionCut.detectStoreys(A.db);
        GridContours.renderLevelMarkers(A, storeys, mode, envCache);
      }
      log('§GRID_VIEW contours=elevation mode=' + mode + ' edges=' + edgeData.length);
    }
  }

  // ── Dimension Chains (delegated to DimChains module) ───────────────

  function buildDimChains(grids, env) {
    if (typeof DimChains !== 'undefined') {
      DimChains.build(A, gridGroup, grids, env, { bubbleScale: bubbleScale });
    }
  }

  function removeDimChains() {
    if (typeof DimChains !== 'undefined') {
      DimChains.remove(gridGroup);
    }
  }


  function buildPanel(grids) {
    if (gridPanel) gridPanel.remove();

    gridPanel = document.createElement('div');
    gridPanel.id = PANEL_ID;
    gridPanel.style.cssText = 'position:fixed;top:56px;left:16px;z-index:25;background:' + panelBg() + ';border-radius:8px;padding:0;border:1px solid ' + panelBorder() + ';backdrop-filter:blur(8px);min-width:180px;max-width:260px';
    gridPanel.innerHTML = '<b style="display:block;padding:6px 10px;color:' + panelText() + ';font-size:12px;font-weight:bold;cursor:grab" onclick="togglePanel(\'grid-panel-body\')">Grid Dimensions</b>' +
      '<div id="grid-panel-body" class="panel-body" style="max-height:300px;overflow-y:auto;padding:4px 10px"></div>';
    document.body.appendChild(gridPanel);
    if (A._makeDraggable) A._makeDraggable(gridPanel);

    var body = document.getElementById('grid-panel-body');
    if (!body) return;

    // View preset buttons at the top of the panel
    var viewHtml = '<div style="display:flex;gap:3px;margin:4px 0 6px;flex-wrap:wrap">';
    var views = [
      { key: 'floor', label: 'GF' },
      { key: 'floor1', label: 'L1' },
      { key: 'front', label: 'F' },
      { key: 'rear', label: 'R' },
      { key: 'left', label: 'L' },
      { key: 'right', label: 'S' },
      { key: 'roof', label: 'Roof' },
      { key: 'unlock', label: '\uD83D\uDD13' }
    ];
    for (var vi = 0; vi < views.length; vi++) {
      var vStyle = (views[vi].key === GridViews.activeView()) ? VIEW_BTN_ACTIVE : VIEW_BTN_STYLE;
      viewHtml += '<button class="grid-view-btn" data-view="' + views[vi].key + '" style="' + vStyle + '">' + views[vi].label + '</button>';
    }
    viewHtml += '</div>';

    var html = viewHtml;

    var xLines = grids.xLines || [];
    if (xLines.length > 1) {
      html += '<div style="color:' + panelSubText() + ';font-size:10px;margin:4px 0 2px;border-bottom:1px solid ' + panelDivider() + '">X-Axis (1,2,3…)</div>';
      for (var i = 0; i < xLines.length - 1; i++) {
        var dist = Math.abs(xLines[i + 1].position - xLines[i].position);
        var lbl = xLines[i].label + '–' + xLines[i + 1].label;
        html += '<div class="grid-row" data-label="' + xLines[i].label + '" data-label-end="' + xLines[i + 1].label + '" style="padding:3px 4px;cursor:pointer;border-radius:3px;font-size:12px;display:flex;justify-content:space-between">' +
          '<span>' + lbl + '</span><span style="color:' + panelDimText() + '">' + (dist * 1000).toFixed(0) + ' mm</span></div>';
      }
      var totalX = Math.abs(xLines[xLines.length - 1].position - xLines[0].position);
      html += '<div style="padding:3px 4px;font-size:11px;color:' + panelTotalText() + ';display:flex;justify-content:space-between"><span>' +
        xLines[0].label + '–' + xLines[xLines.length - 1].label + ' total</span><span>' + (totalX * 1000).toFixed(0) + ' mm</span></div>';
    }

    var yLines = grids.yLines || [];
    if (yLines.length > 1) {
      html += '<div style="color:' + panelSubText() + ';font-size:10px;margin:8px 0 2px;border-bottom:1px solid ' + panelDivider() + '">Y-Axis (A,B,C…)</div>';
      for (var j = 0; j < yLines.length - 1; j++) {
        var dist2 = Math.abs(yLines[j + 1].position - yLines[j].position);
        var lbl2 = yLines[j].label + '–' + yLines[j + 1].label;
        html += '<div class="grid-row" data-label="' + yLines[j].label + '" data-label-end="' + yLines[j + 1].label + '" style="padding:3px 4px;cursor:pointer;border-radius:3px;font-size:12px;display:flex;justify-content:space-between">' +
          '<span>' + lbl2 + '</span><span style="color:' + panelDimText() + '">' + (dist2 * 1000).toFixed(0) + ' mm</span></div>';
      }
      var totalY = Math.abs(yLines[yLines.length - 1].position - yLines[0].position);
      html += '<div style="padding:3px 4px;font-size:11px;color:' + panelTotalText() + ';display:flex;justify-content:space-between"><span>' +
        yLines[0].label + '–' + yLines[yLines.length - 1].label + ' total</span><span>' + (totalY * 1000).toFixed(0) + ' mm</span></div>';
    }

    if (!html) html = '<div style="color:#888;font-size:11px;padding:8px">No grids detected</div>';

    body.innerHTML = html;

    var rows = body.querySelectorAll('.grid-row');
    for (var r = 0; r < rows.length; r++) {
      rows[r].addEventListener('pointerup', onPanelRowClick);
    }

    // View preset button listeners
    var vBtns = body.querySelectorAll('.grid-view-btn');
    for (var vb = 0; vb < vBtns.length; vb++) {
      vBtns[vb].addEventListener('pointerup', onViewBtnClick);
    }
  }

  // ── Click-to-Zoom + Orange Highlight ──────────────────────────────

  function onPanelRowClick(e) {
    var label = e.currentTarget.getAttribute('data-label');
    var labelEnd = e.currentTarget.getAttribute('data-label-end');
    if (!label) return;
    highlightGrid(label, labelEnd);
    zoomToGrid(label);
  }

  function highlightGrid(label, labelEnd) {
    // Reset all lines to theme-aware default
    var defColor = lineColor();
    for (var key in lineMeshes) {
      if (lineMeshes[key].line) {
        lineMeshes[key].line.material.color.setHex(defColor);
        lineMeshes[key].line.material.linewidth = 1;
      }
    }

    // Reset panel row highlights
    if (gridPanel) {
      var rows = gridPanel.querySelectorAll('.grid-row');
      for (var i = 0; i < rows.length; i++) {
        rows[i].style.background = '';
        rows[i].style.color = '';
      }
    }

    // Remove previous slab
    if (gridGroup) {
      var toRemove = [];
      gridGroup.traverse(function(obj) {
        if (obj.userData && obj.userData.isHighlightSlab) toRemove.push(obj);
      });
      for (var r = 0; r < toRemove.length; r++) {
        if (toRemove[r].geometry) toRemove[r].geometry.dispose();
        if (toRemove[r].material) toRemove[r].material.dispose();
        gridGroup.remove(toRemove[r]);
      }
    }

    selectedLabel = label;
    var highlightSet = {};
    highlightSet[label] = true;
    if (labelEnd) highlightSet[labelEnd] = true;

    // Highlight BOTH grid lines — bright orange, thicker
    for (var hl in highlightSet) {
      if (lineMeshes[hl] && lineMeshes[hl].line) {
        lineMeshes[hl].line.material.color.setHex(COLOR_HIGHLIGHT);
        lineMeshes[hl].line.material.linewidth = 3;
      }
    }

    // Orange transparent slab between the two grid lines
    if (labelEnd && lineMeshes[label] && lineMeshes[labelEnd] && gridGroup) {
      var a = lineMeshes[label];
      var b = lineMeshes[labelEnd];
      // Build quad from the 4 corners: a.v0, a.v1, b.v1, b.v0
      var slabGeo = new THREE.BufferGeometry();
      var positions = new Float32Array([
        a.v0.x, a.v0.y, a.v0.z,
        a.v1.x, a.v1.y, a.v1.z,
        b.v1.x, b.v1.y, b.v1.z,
        a.v0.x, a.v0.y, a.v0.z,
        b.v1.x, b.v1.y, b.v1.z,
        b.v0.x, b.v0.y, b.v0.z
      ]);
      slabGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
      var slabMat = new THREE.MeshBasicMaterial({
        color: 0xff6600,
        transparent: true,
        opacity: 0.12,
        side: THREE.DoubleSide,
        depthTest: false
      });
      var slab = new THREE.Mesh(slabGeo, slabMat);
      slab.renderOrder = 998;
      slab.userData.isHighlightSlab = true;
      gridGroup.add(slab);
    }

    // Highlight matching panel rows
    if (gridPanel) {
      var matching = gridPanel.querySelectorAll('.grid-row[data-label="' + label + '"]');
      for (var j = 0; j < matching.length; j++) {
        matching[j].style.background = 'rgba(255,102,0,0.25)';
        matching[j].style.color = '#ff6600';
      }
    }

    // Rebuild bubbles with highlight state
    if (gridGroup) {
      gridGroup.traverse(function(obj) {
        if (obj.isSprite && obj.userData.gridLabel) {
          if (obj.material.map) obj.material.map.dispose();
          obj.material.dispose();
          var isHL = !!highlightSet[obj.userData.gridLabel];
          var fresh = createBubble(obj.userData.gridLabel, obj.position, isHL);
          obj.material = fresh.material;
        }
      });
    }

    A.markDirty();
    log('§GRID_ZOOM highlight=' + label + (labelEnd ? '+' + labelEnd : '') + ' slab=' + !!labelEnd);
  }

  function zoomToGrid(label) {
    var entry = lineMeshes[label];
    if (!entry) { log('§GRID_ZOOM FAIL label=' + label + ' not found'); return; }

    // Cancel any running zoom animation
    if (zoomAnim) { cancelAnimationFrame(zoomAnim); zoomAnim = null; }

    // Line midpoint (where we look at)
    var mid = new THREE.Vector3().addVectors(entry.v0, entry.v1).multiplyScalar(0.5);

    // Line length determines how far the camera should be
    var lineLen = entry.v0.distanceTo(entry.v1);

    // Camera: position slightly above and to the side, at a distance that frames the line
    // Keep roughly current viewing angle but re-target to grid midpoint
    var camDir = A.camera.position.clone().sub(A.controls.target).normalize();
    var dist = lineLen * 1.2;
    // Ensure minimum distance so we don't clip into the model
    dist = Math.max(dist, 10);
    var targetCamPos = mid.clone().add(camDir.multiplyScalar(dist));

    log('§GRID_ZOOM label=' + label +
        ' mid=(' + mid.x.toFixed(1) + ',' + mid.y.toFixed(1) + ',' + mid.z.toFixed(1) + ')' +
        ' lineLen=' + lineLen.toFixed(1) + ' camDist=' + dist.toFixed(1));

    // Animate (20 frames ≈ 330ms)
    var startPos = A.camera.position.clone();
    var startTarget = A.controls.target.clone();
    var frame = 0;
    var totalFrames = 20;

    function step() {
      frame++;
      var t = frame / totalFrames;
      t = t * (2 - t); // ease-out quadratic
      A.camera.position.lerpVectors(startPos, targetCamPos, t);
      A.controls.target.lerpVectors(startTarget, mid, t);
      A.controls.update();
      A.markDirty();
      if (frame < totalFrames) {
        zoomAnim = requestAnimationFrame(step);
      } else {
        zoomAnim = null;
      }
    }
    zoomAnim = requestAnimationFrame(step);
  }

  // ── Toggle ────────────────────────────────────────────────────────

  A.toggleGridOverlay = function() {
    if (active) {
      active = false;
      if (zoomAnim) { cancelAnimationFrame(zoomAnim); zoomAnim = null; }
      if (typeof GridContours !== 'undefined') GridContours.clear(A);
      GridViews.clearFloorClip(A);
      if (GridViews.activeView()) GridViews.unlockView(A);
      // S250 §6: Dispose all canvas textures (bubbles + dim labels) to free GPU memory
      if (gridGroup) {
        var texCount = 0;
        gridGroup.traverse(function(obj) {
          if (obj.material && obj.material.map) {
            obj.material.map.dispose();
            texCount++;
          }
          if (obj.material) obj.material.dispose();
          if (obj.geometry) obj.geometry.dispose();
        });
        A.scene.remove(gridGroup);
        console.log('§GRID_TEARDOWN disposing ' + texCount + ' textures');
        gridGroup = null;
        gridData = null;
        lineMeshes = {};
      }
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

    // Detect grids (column-based — structural grid, not internal walls)
    gridData = GridDims.detectGrids(A.db);
    log('§GRID_DETECT xLines=' + (gridData.xLines || []).length + ' yLines=' + (gridData.yLines || []).length);

    if ((!gridData.xLines || !gridData.xLines.length) && (!gridData.yLines || !gridData.yLines.length)) {
      A.status.textContent = 'No grid lines detected (need ≥2 columns)';
      active = false;
      return;
    }

    dimsData = GridDims.generateDimensions(gridData);

    // Get building envelope from DB — not from scene (scene has 50km ground plane)
    envCache = getBuildingEnvelopeIFC();
    buildGridScene(gridData, envCache);
    buildPanel(gridData);
    buildDimChains(gridData, envCache);

    A.status.textContent = 'Grid mode — ' + ((gridData.xLines || []).length + (gridData.yLines || []).length) + ' grid lines';
  };

  window.toggleGridOverlay = A.toggleGridOverlay;

  // React to theme changes (sunglasses toggle) — update line/bubble/panel colors
  var _origToggleTheme = A.toggleTheme;
  A.toggleTheme = function() {
    _origToggleTheme.call(A);
    if (!active || !gridGroup) return;
    var def = lineColor();
    for (var key in lineMeshes) {
      if (lineMeshes[key].line) {
        lineMeshes[key].line.material.color.setHex(key === selectedLabel ? COLOR_HIGHLIGHT : def);
      }
    }
    // Rebuild bubbles with new theme colors
    gridGroup.traverse(function(obj) {
      if (obj.isSprite && obj.userData.gridLabel) {
        if (obj.material.map) obj.material.map.dispose();
        obj.material.dispose();
        var isHL = (obj.userData.gridLabel === selectedLabel);
        var fresh = createBubble(obj.userData.gridLabel, obj.position, isHL);
        obj.material = fresh.material;
      }
    });
    // Update panel colors — rebuild to pick up new theme text colors
    if (gridPanel && gridData) {
      gridPanel.style.background = panelBg();
      gridPanel.style.borderColor = panelBorder();
      buildPanel(gridData);
    }
    // Rebuild dimension text sprites with new theme colors
    if (gridGroup && gridData && envCache) {
      removeDimChains();
      buildDimChains(gridData, envCache);
    }
    A.markDirty();
  };

  // ── State accessor for GridDrag ──────────────────────────────────
  // Exposes closure variables as a live-read object so grid_drag.js
  // can access scene state without coupling to internals.
  A._gridOverlayState = {
    get active()      { return active; },
    get gridGroup()   { return gridGroup; },
    get gridData()    { return gridData; },
    get envCache()    { return envCache; },
    get lineMeshes()  { return lineMeshes; },
    get bubbleScale() { return bubbleScale; },
    rebuildPanel:     function(grids) { buildPanel(grids); }
  };

  // Wire GridDrag if available
  if (typeof GridDrag !== 'undefined' && GridDrag.init) {
    GridDrag.init(A, A._gridOverlayState);
    log('§GRID_INIT GridDrag wired');
  }

  // Clamp bubble sizes on every camera change (zoom/pan in ortho views)
  A.controls.addEventListener('change', clampBubbleScales);

  log('§GRID_INIT ready');
}
