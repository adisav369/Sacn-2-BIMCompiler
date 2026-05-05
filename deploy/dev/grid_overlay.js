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

  // ── Elevation View State ──────────────────────────────────────────
  var savedCamera = null;      // { pos, target, up, fov } before first lock
  var orthoCamera = null;      // shared OrthographicCamera for locked views
  var origCamera = null;       // reference to the original PerspectiveCamera
  var activeView = null;       // 'front'|'rear'|'left'|'right'|'roof'|'floor'|'floor1'|null
  var floorClipPlane = null;   // THREE.Plane for floor plan section cut
  var savedClipState = [];     // backup of material clippingPlanes before floor lock

  // ── Constants ─────────────────────────────────────────────────────
  var COLOR_HIGHLIGHT = 0xff6600;     // bright orange on selection
  var LINE_OVERSHOOT_RATIO = 0.15;   // extend 15% of building dim past envelope
  var LINE_OVERSHOOT_MIN = 2.0;      // at least 2m overshoot
  var PANEL_ID = 'grid-overlay-panel';

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

  // ── Elevation View Presets ─────────────────────────────────────────

  var VIEW_BTN_STYLE = 'background:#444;color:#ccc;border:1px solid #666;border-radius:4px;padding:3px 8px;font-size:11px;cursor:pointer';
  var VIEW_BTN_ACTIVE = 'background:#4fc3f7;color:#000;border:1px solid #666;border-radius:4px;padding:3px 8px;font-size:11px;cursor:pointer';

  /** Compute building centre in Three.js coords from envCache */
  function buildingCentre3(env) {
    var cx = (env.xMin + env.xMax) / 2;
    var cy = (env.yMin + env.yMax) / 2;
    var cz = (env.zMin + env.zMax) / 2;
    var t = A.ifc2three(cx, cy, cz);
    return new THREE.Vector3(t.x, t.y, t.z);
  }

  /** Save the original perspective camera state (once) */
  function saveCameraState() {
    if (savedCamera) return; // already saved
    origCamera = A.camera;
    savedCamera = {
      pos: A.camera.position.clone(),
      target: A.controls.target.clone(),
      up: A.camera.up.clone(),
      fov: A.camera.fov
    };
  }

  /** Create or update the shared orthographic camera */
  function getOrthoCamera(halfW, halfH, near, far) {
    if (!orthoCamera) {
      orthoCamera = new THREE.OrthographicCamera(-halfW, halfW, halfH, -halfH, near, far);
    } else {
      orthoCamera.left = -halfW;
      orthoCamera.right = halfW;
      orthoCamera.top = halfH;
      orthoCamera.bottom = -halfH;
      orthoCamera.near = near;
      orthoCamera.far = far;
    }
    orthoCamera.updateProjectionMatrix();
    return orthoCamera;
  }

  /** Switch the renderer to use a given camera and rebind controls */
  function swapCamera(cam) {
    A.camera = cam;
    A.controls.object = cam;
    cam.updateProjectionMatrix();
    A.controls.update();
    // Re-bind resize handler for ortho vs perspective
    if (A._gridViewResize) {
      window.removeEventListener('resize', A._gridViewResize);
    }
    if (cam.isOrthographicCamera) {
      A._gridViewResize = function() {
        var aspect = window.innerWidth / window.innerHeight;
        var halfH = (cam.top);
        var halfW = halfH * aspect;
        cam.left = -halfW;
        cam.right = halfW;
        cam.updateProjectionMatrix();
        A.renderer.setSize(window.innerWidth, window.innerHeight);
      };
      window.addEventListener('resize', A._gridViewResize);
    }
    A.markDirty();
  }

  /** Lock camera to an elevation/roof view */
  function lockView(mode) {
    if (!envCache) return;
    saveCameraState();

    var env = envCache;
    var centre = buildingCentre3(env);
    // Building dimensions in IFC metres
    var bldW = env.xMax - env.xMin;   // IFC X → Three X width
    var bldD = env.yMax - env.yMin;   // IFC Y → Three Z depth
    var bldH = env.zMax - env.zMin;   // IFC Z → Three Y height
    var margin = 1.2; // 20% margin around building
    var dist = Math.max(bldW, bldD, bldH) * 2;

    var camPos, halfW, halfH, upVec;
    upVec = new THREE.Vector3(0, 1, 0); // default up

    if (mode === 'front') {
      // Front: look from +Z (Three.js) = IFC -Y direction
      camPos = new THREE.Vector3(centre.x, centre.y, centre.z + dist);
      halfW = (bldW / 2) * margin;
      halfH = (bldH / 2) * margin;
    } else if (mode === 'rear') {
      // Rear: look from -Z (Three.js) = IFC +Y direction
      camPos = new THREE.Vector3(centre.x, centre.y, centre.z - dist);
      halfW = (bldW / 2) * margin;
      halfH = (bldH / 2) * margin;
    } else if (mode === 'left') {
      // Left: look from -X
      camPos = new THREE.Vector3(centre.x - dist, centre.y, centre.z);
      halfW = (bldD / 2) * margin;
      halfH = (bldH / 2) * margin;
    } else if (mode === 'right') {
      // Right/Side: look from +X
      camPos = new THREE.Vector3(centre.x + dist, centre.y, centre.z);
      halfW = (bldD / 2) * margin;
      halfH = (bldH / 2) * margin;
    } else if (mode === 'roof') {
      // Roof: top-down, look from +Y (Three.js up)
      camPos = new THREE.Vector3(centre.x, centre.y + dist, centre.z);
      halfW = (bldW / 2) * margin;
      halfH = (bldD / 2) * margin;
      upVec = new THREE.Vector3(0, 0, -1); // north faces up
    } else if (mode === 'floor' || mode === 'floor1') {
      // Floor Plan: top-down + horizontal section cut ~1m above slab
      camPos = new THREE.Vector3(centre.x, centre.y + dist, centre.z);
      halfW = (bldW / 2) * margin;
      halfH = (bldD / 2) * margin;
      upVec = new THREE.Vector3(0, 0, -1);
    } else {
      return;
    }

    // Adjust for aspect ratio
    var aspect = window.innerWidth / window.innerHeight;
    if (aspect > 1) {
      // Wide screen: expand width to match
      if (halfW / halfH < aspect) halfW = halfH * aspect;
    } else {
      // Tall screen: expand height to match
      if (halfH / halfW < (1 / aspect)) halfH = halfW / aspect;
    }

    var cam = getOrthoCamera(halfW, halfH, 0.1, dist * 4);
    cam.position.copy(camPos);
    cam.up.copy(upVec);
    cam.lookAt(centre);

    A.controls.target.copy(centre);
    swapCamera(cam);

    A.controls.enableRotate = false;
    A.controls.enablePan = true;
    A.controls.enableZoom = true;
    A.controls.update();

    // Floor plan: apply horizontal section cut ~1m above slab level
    clearFloorClip(); // clear any previous
    if (mode === 'floor' || mode === 'floor1') {
      // Determine cut height: floor=ground+1m, floor1=upper storey+1m
      var cutZ;
      if (mode === 'floor1') {
        // Upper storey: cut at midpoint between ground and roof
        cutZ = env.zMin + bldH * 0.55;
      } else {
        cutZ = env.zMin + 1.0; // 1m above ground slab
      }
      var cutY = (cutZ - A.modelOffset.z); // IFC Z → Three Y
      // Clip plane: normal pointing DOWN (-Y), cuts everything above cutY
      floorClipPlane = new THREE.Plane(new THREE.Vector3(0, -1, 0), cutY);
      A.renderer.localClippingEnabled = true;
      // Save existing clip state and apply floor clip
      savedClipState = [];
      A.collectMeshes(function(o) { return o.isMesh; }).forEach(function(obj) {
        savedClipState.push({ mesh: obj, planes: obj.material.clippingPlanes || [] });
        obj.material.clippingPlanes = [floorClipPlane];
        obj.material.clipShadows = true;
        obj.material.needsUpdate = true;
      });
      log('§GRID_VIEW floor_clip cutZ_ifc=' + cutZ.toFixed(2) + ' cutY_three=' + cutY.toFixed(2));
    }

    // High contrast for all locked views: white bg, dark lines
    if (!A.lightTheme) {
      A.toggleTheme();
    }

    activeView = mode;
    updateViewButtons();
    A.markDirty();
    log('§GRID_VIEW mode=' + mode + ' centre=(' + centre.x.toFixed(1) + ',' + centre.y.toFixed(1) + ',' + centre.z.toFixed(1) + ') dist=' + dist.toFixed(1));
  }

  /** Clear floor plan section clipping */
  function clearFloorClip() {
    if (!floorClipPlane) return;
    for (var i = 0; i < savedClipState.length; i++) {
      var entry = savedClipState[i];
      if (entry.mesh && entry.mesh.material) {
        entry.mesh.material.clippingPlanes = entry.planes;
        entry.mesh.material.needsUpdate = true;
      }
    }
    savedClipState = [];
    floorClipPlane = null;
    log('§GRID_VIEW floor_clip cleared');
  }

  /** Unlock: restore the original perspective camera */
  function unlockView() {
    if (!savedCamera || !origCamera) {
      activeView = null;
      updateViewButtons();
      log('§GRID_VIEW mode=unlock (no saved state)');
      return;
    }

    // Remove ortho resize handler
    if (A._gridViewResize) {
      window.removeEventListener('resize', A._gridViewResize);
      A._gridViewResize = null;
    }

    origCamera.position.copy(savedCamera.pos);
    origCamera.up.copy(savedCamera.up);
    origCamera.fov = savedCamera.fov;
    origCamera.aspect = window.innerWidth / window.innerHeight;
    origCamera.updateProjectionMatrix();

    A.camera = origCamera;
    A.controls.object = origCamera;
    A.controls.target.copy(savedCamera.target);
    A.controls.enableRotate = true;
    A.controls.enablePan = true;
    A.controls.update();

    // Re-bind the original resize handler
    if (A._onResize) {
      A._onResize();
    }

    // Restore floor clip if active
    clearFloorClip();

    // Restore dark theme if we forced light for print contrast
    if (A.lightTheme) {
      A.toggleTheme();
    }

    savedCamera = null;
    activeView = null;
    updateViewButtons();
    A.markDirty();
    log('§GRID_VIEW mode=unlock');
  }

  /** Update active state on all view buttons */
  function updateViewButtons() {
    var btns = document.querySelectorAll('.grid-view-btn');
    for (var i = 0; i < btns.length; i++) {
      var v = btns[i].getAttribute('data-view');
      btns[i].style.cssText = (v === activeView) ? VIEW_BTN_ACTIVE : VIEW_BTN_STYLE;
    }
  }

  /** Handle view button click */
  function onViewBtnClick(e) {
    var mode = e.currentTarget.getAttribute('data-view');
    if (!mode) return;
    if (mode === 'unlock') {
      unlockView();
    } else {
      lockView(mode);
    }
  }

  // ── Measurements Panel ────────────────────────────────────────────
  // ── On-Scene Dimension Chains ─────────────────────────────────────

  /** Create a text sprite showing a dimension value in mm */
  function createDimLabel(text, position) {
    var canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 32;
    var ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, 128, 32);
    ctx.font = '20px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    if (isLight()) {
      ctx.fillStyle = '#000000';
      ctx.fillText(text, 64, 16);
    } else {
      ctx.strokeStyle = '#000000';
      ctx.lineWidth = 3;
      ctx.strokeText(text, 64, 16);
      ctx.fillStyle = '#ffffff';
      ctx.fillText(text, 64, 16);
    }
    var texture = new THREE.CanvasTexture(canvas);
    var mat = new THREE.SpriteMaterial({ map: texture, depthTest: false, transparent: true });
    var sprite = new THREE.Sprite(mat);
    sprite.position.copy(position);
    sprite.scale.set(bubbleScale * 1.6, bubbleScale * 0.4, 1);
    sprite.renderOrder = 1001;
    sprite.userData.isDimChain = true;
    return sprite;
  }

  /** Draw one dimension segment: line + ticks + label */
  function addDimSegment(p0, p1, tickDir, label, group) {
    var dimMat = new THREE.LineBasicMaterial({
      color: isLight() ? 0x555555 : 0x999999,
      transparent: true,
      opacity: 0.7
    });

    // Main dimension line
    var lineGeom = new THREE.BufferGeometry().setFromPoints([p0, p1]);
    var dimLine = new THREE.Line(lineGeom, dimMat);
    dimLine.renderOrder = 999;
    dimLine.userData.isDimChain = true;
    group.add(dimLine);

    // Tick (witness) lines at each end
    var tickLen = bubbleScale * 0.3;
    var t0a = p0.clone().add(tickDir.clone().multiplyScalar(tickLen));
    var t0b = p0.clone().add(tickDir.clone().multiplyScalar(-tickLen));
    var tick0Geom = new THREE.BufferGeometry().setFromPoints([t0a, t0b]);
    var tick0 = new THREE.Line(tick0Geom, dimMat);
    tick0.renderOrder = 999;
    tick0.userData.isDimChain = true;
    group.add(tick0);

    var t1a = p1.clone().add(tickDir.clone().multiplyScalar(tickLen));
    var t1b = p1.clone().add(tickDir.clone().multiplyScalar(-tickLen));
    var tick1Geom = new THREE.BufferGeometry().setFromPoints([t1a, t1b]);
    var tick1 = new THREE.Line(tick1Geom, dimMat);
    tick1.renderOrder = 999;
    tick1.userData.isDimChain = true;
    group.add(tick1);

    // Label at midpoint
    var mid = new THREE.Vector3().addVectors(p0, p1).multiplyScalar(0.5);
    group.add(createDimLabel(label, mid));
  }

  /** Build all dimension chain objects and add to gridGroup */
  function buildDimChains(grids, env) {
    if (!gridGroup) return;

    var bldW = env.xMax - env.xMin;
    var bldD = env.yMax - env.yMin;
    var maxDim = Math.max(bldW, bldD);
    var dimGap = Math.max(1.0, maxDim * 0.03);

    var groundY = (env.zMin - A.modelOffset.z) - 0.05;
    var xLines = grids.xLines || [];
    var yLines = grids.yLines || [];

    // X-axis grids (vertical lines varying in IFC X, run along IFC Y)
    // Dims go along Z-min side (below building in Three.js = +Z after ifc2three)
    if (xLines.length > 1) {
      var refZ = A.ifc2three(0, env.yMin, env.zMin);
      var baseZ = refZ.z;

      // Tier 1: bay-by-bay, closest to building
      var tier1Offset = baseZ + bubbleScale + dimGap;
      var tickDirX = new THREE.Vector3(0, 0, 1);

      for (var i = 0; i < xLines.length - 1; i++) {
        var pa = A.ifc2three(xLines[i].position, env.yMin, env.zMin);
        var pb = A.ifc2three(xLines[i + 1].position, env.yMin, env.zMin);
        var p0 = new THREE.Vector3(pa.x, groundY, tier1Offset);
        var p1 = new THREE.Vector3(pb.x, groundY, tier1Offset);
        var distX = Math.abs(xLines[i + 1].position - xLines[i].position);
        addDimSegment(p0, p1, tickDirX, (distX * 1000).toFixed(0), gridGroup);
      }

      // Tier 3: overall, outermost
      var tier3Offset = baseZ + bubbleScale + dimGap * 3;
      var pFirst = A.ifc2three(xLines[0].position, env.yMin, env.zMin);
      var pLast = A.ifc2three(xLines[xLines.length - 1].position, env.yMin, env.zMin);
      var q0 = new THREE.Vector3(pFirst.x, groundY, tier3Offset);
      var q1 = new THREE.Vector3(pLast.x, groundY, tier3Offset);
      var totalX = Math.abs(xLines[xLines.length - 1].position - xLines[0].position);
      addDimSegment(q0, q1, tickDirX, (totalX * 1000).toFixed(0), gridGroup);
    }

    // Y-axis grids (horizontal lines varying in IFC Y, run along IFC X)
    // Dims go along X-min side (left of building in Three.js = -X after ifc2three)
    if (yLines.length > 1) {
      var refX = A.ifc2three(env.xMin, 0, env.zMin);
      var baseX = refX.x;

      // Tier 1: bay-by-bay
      var tier1OffsetY = baseX - bubbleScale - dimGap;
      var tickDirY = new THREE.Vector3(1, 0, 0);

      for (var j = 0; j < yLines.length - 1; j++) {
        var ra = A.ifc2three(env.xMin, yLines[j].position, env.zMin);
        var rb = A.ifc2three(env.xMin, yLines[j + 1].position, env.zMin);
        var r0 = new THREE.Vector3(tier1OffsetY, groundY, ra.z);
        var r1 = new THREE.Vector3(tier1OffsetY, groundY, rb.z);
        var distY = Math.abs(yLines[j + 1].position - yLines[j].position);
        addDimSegment(r0, r1, tickDirY, (distY * 1000).toFixed(0), gridGroup);
      }

      // Tier 3: overall
      var tier3OffsetY = baseX - bubbleScale - dimGap * 3;
      var sFirst = A.ifc2three(env.xMin, yLines[0].position, env.zMin);
      var sLast = A.ifc2three(env.xMin, yLines[yLines.length - 1].position, env.zMin);
      var s0 = new THREE.Vector3(tier3OffsetY, groundY, sFirst.z);
      var s1 = new THREE.Vector3(tier3OffsetY, groundY, sLast.z);
      var totalY = Math.abs(yLines[yLines.length - 1].position - yLines[0].position);
      addDimSegment(s0, s1, tickDirY, (totalY * 1000).toFixed(0), gridGroup);
    }

    log('§GRID_MODE dimension chains built');
  }

  /** Remove all dimension chain objects from gridGroup */
  function removeDimChains() {
    if (!gridGroup) return;
    var toRemove = [];
    gridGroup.traverse(function(obj) {
      if (obj.userData.isDimChain) toRemove.push(obj);
    });
    for (var i = 0; i < toRemove.length; i++) {
      if (toRemove[i].geometry) toRemove[i].geometry.dispose();
      if (toRemove[i].material) {
        if (toRemove[i].material.map) toRemove[i].material.map.dispose();
        toRemove[i].material.dispose();
      }
      gridGroup.remove(toRemove[i]);
    }
  }


  function buildPanel(grids) {
    if (gridPanel) gridPanel.remove();

    gridPanel = document.createElement('div');
    gridPanel.id = PANEL_ID;
    gridPanel.style.cssText = 'position:fixed;top:56px;left:16px;z-index:10;background:' + panelBg() + ';border-radius:8px;padding:0;border:1px solid ' + panelBorder() + ';backdrop-filter:blur(8px);min-width:180px;max-width:260px';
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
      var vStyle = (views[vi].key === activeView) ? VIEW_BTN_ACTIVE : VIEW_BTN_STYLE;
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
        html += '<div class="grid-row" data-label="' + xLines[i].label + '" style="padding:3px 4px;cursor:pointer;border-radius:3px;font-size:12px;display:flex;justify-content:space-between">' +
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
        html += '<div class="grid-row" data-label="' + yLines[j].label + '" style="padding:3px 4px;cursor:pointer;border-radius:3px;font-size:12px;display:flex;justify-content:space-between">' +
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
    if (!label) return;
    highlightGrid(label);
    zoomToGrid(label);
  }

  function highlightGrid(label) {
    // Reset all lines to theme-aware default
    var defColor = lineColor();
    for (var key in lineMeshes) {
      if (lineMeshes[key].line) {
        lineMeshes[key].line.material.color.setHex(defColor);
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

    selectedLabel = label;

    // Highlight the selected line — bright orange
    if (lineMeshes[label] && lineMeshes[label].line) {
      lineMeshes[label].line.material.color.setHex(COLOR_HIGHLIGHT);
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
      clearFloorClip();
      if (activeView) unlockView();
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

  log('§GRID_INIT ready');
}
