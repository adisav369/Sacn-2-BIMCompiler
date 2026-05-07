/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_views.js — Orthographic View Presets (elevation, roof, floor plan)
 *
 * Implementing BBC.md §2D_022/§2D_023 — Witness: W-GRID-VIEWS
 *
 * Separated concern: camera positioning, section clipping, theme management.
 * No grid lines, no panels, no DOM — pure Three.js camera + material ops.
 *
 * API: GridViews.lockView(APP, mode, env)
 *      GridViews.unlockView(APP)
 *      GridViews.clearFloorClip(APP)
 *      GridViews.activeView()
 *      GridViews.VIEW_DEFS
 *
 * Log tags:
 *   §VIEW_LOCK    — view locked to preset
 *   §VIEW_UNLOCK  — view restored to perspective
 *   §VIEW_CLIP    — floor plan clip applied/cleared
 */
var GridViews = (function() {
  'use strict';

  // ── View Definitions ──────────────────────────────────────────────
  // Pure data: camera offset direction, frustum axes, up vector.
  // clip: 'gf'|'l1' means this view applies a horizontal section cut.
  var VIEW_DEFS = {
    front:  { dx: 0, dy: 0, dz:+1, fw:'W', fh:'H', up:[0,1,0] },
    rear:   { dx: 0, dy: 0, dz:-1, fw:'W', fh:'H', up:[0,1,0] },
    left:   { dx:-1, dy: 0, dz: 0, fw:'D', fh:'H', up:[0,1,0] },
    right:  { dx:+1, dy: 0, dz: 0, fw:'D', fh:'H', up:[0,1,0] },
    roof:   { dx: 0, dy:+1, dz: 0, fw:'W', fh:'D', up:[0,0,-1] },
    floor:  { dx: 0, dy:+1, dz: 0, fw:'W', fh:'D', up:[0,0,-1], clip: true },
    floor1: { dx: 0, dy:+1, dz: 0, fw:'W', fh:'D', up:[0,0,-1], clip: true }
  };

  // ── State (per-session) ───────────────────────────────────────────
  var _savedCamera = null;      // { pos, target, up, fov } before first lock
  var _orthoCamera = null;      // shared OrthographicCamera for locked views
  var _origCamera = null;       // reference to the original PerspectiveCamera
  var _activeView = null;       // current mode key or null
  var _floorClipPlane = null;   // THREE.Plane for floor plan section cut
  // No forced theme state — user controls theme
  var _resizeHandler = null;    // resize event handler ref

  function log(msg) { console.log('[GridViews] ' + msg); }

  // ── Camera ────────────────────────────────────────────────────────

  /** Compute building centre in Three.js coords */
  function buildingCentre3(A, env) {
    var cx = (env.xMin + env.xMax) / 2;
    var cy = (env.yMin + env.yMax) / 2;
    var cz = (env.zMin + env.zMax) / 2;
    var t = A.ifc2three(cx, cy, cz);
    return new THREE.Vector3(t.x, t.y, t.z);
  }

  /** Save the original perspective camera state (once) */
  function saveCameraState(A) {
    if (_savedCamera) return;
    _origCamera = A.camera;
    _savedCamera = {
      pos: A.camera.position.clone(),
      target: A.controls.target.clone(),
      up: A.camera.up.clone(),
      fov: A.camera.fov
    };
  }

  /** Create or update the shared orthographic camera */
  function getOrthoCamera(halfW, halfH, near, far) {
    if (!_orthoCamera) {
      _orthoCamera = new THREE.OrthographicCamera(-halfW, halfW, halfH, -halfH, near, far);
    } else {
      _orthoCamera.left = -halfW;
      _orthoCamera.right = halfW;
      _orthoCamera.top = halfH;
      _orthoCamera.bottom = -halfH;
      _orthoCamera.near = near;
      _orthoCamera.far = far;
    }
    _orthoCamera.updateProjectionMatrix();
    return _orthoCamera;
  }

  /** Switch the renderer to use a given camera and rebind controls */
  function swapCamera(A, cam) {
    A.camera = cam;
    A.controls.object = cam;
    cam.updateProjectionMatrix();
    A.controls.update();

    if (_resizeHandler) {
      window.removeEventListener('resize', _resizeHandler);
      _resizeHandler = null;
    }
    if (cam.isOrthographicCamera) {
      _resizeHandler = function() {
        A.renderer.setSize(window.innerWidth, window.innerHeight);
        // §7 — Recompute ortho frustum with new viewport aspect ratio
        var newAspect = window.innerWidth / window.innerHeight;
        var curHalfH = cam.top; // current half-height (positive)
        var curHalfW = curHalfH * newAspect;
        cam.left = -curHalfW;
        cam.right = curHalfW;
        cam.updateProjectionMatrix();
        console.log('§GRID_VIEW resize aspect=' + newAspect.toFixed(3) +
            ' halfW=' + curHalfW.toFixed(2) + ' halfH=' + curHalfH.toFixed(2));
      };
      window.addEventListener('resize', _resizeHandler);
    }
    A.markDirty();
  }

  /** Position the ortho camera — pure geometry, no side effects */
  function positionOrthoCamera(A, mode, env, centre) {
    var def = VIEW_DEFS[mode];
    if (!def) return null;

    var bldW = env.xMax - env.xMin;
    var bldD = env.yMax - env.yMin;
    var bldH = env.zMax - env.zMin;
    var margin = 1.2; // envCache is center points, not mesh extents
    var dist = Math.max(bldW, bldD, bldH) * 2;

    var dims = { W: bldW, D: bldD, H: bldH };
    var halfW = (dims[def.fw] / 2) * margin;
    var halfH = (dims[def.fh] / 2) * margin;

    // §7 — Viewport aspect ratio correction to prevent bubble skewing
    var viewportAspect = window.innerWidth / window.innerHeight;
    var buildingAspect = halfW / halfH;
    if (viewportAspect > buildingAspect) {
      halfW = halfH * viewportAspect;
    } else {
      halfH = halfW / viewportAspect;
    }

    // §7 — Pre-render guard: abort if frustum aspect still mismatches viewport
    var frustumAspect = halfW / halfH;
    var vpAspect = window.innerWidth / window.innerHeight;
    if (Math.abs(frustumAspect - vpAspect) > 0.01) {
      console.log('§GRID_VIEW ABORT — frustum aspect ' + frustumAspect.toFixed(3) +
          ' ≠ viewport ' + vpAspect.toFixed(3) + ' — would skew');
      return null;
    }

    var camPos = new THREE.Vector3(
      centre.x + def.dx * dist,
      centre.y + def.dy * dist,
      centre.z + def.dz * dist
    );
    var upVec = new THREE.Vector3(def.up[0], def.up[1], def.up[2]);

    var cam = getOrthoCamera(halfW, halfH, 0.1, dist * 4);
    cam.position.copy(camPos);
    cam.up.copy(upVec);
    cam.lookAt(centre);
    return cam;
  }

  // ── Clipping (floor plan only) ────────────────────────────────────

  /** Apply horizontal section cut — reads clip + retain config from GridConfig */
  function applyFloorClip(A, env, viewMode) {
    var clipCfg = (typeof GridConfig !== 'undefined') ? GridConfig.clipFor(viewMode) : null;
    var bldH = env.zMax - env.zMin;
    var cutZ;
    if (clipCfg && clipCfg.offset_ratio) {
      cutZ = env.zMin + bldH * clipCfg.offset_ratio;
    } else {
      cutZ = env.zMin + ((clipCfg && clipCfg.offset_m) || 1.0);
    }
    var cutY = cutZ - A.modelOffset.z;

    _floorClipPlane = new THREE.Plane(new THREE.Vector3(0, -1, 0), cutY);
    A.renderer.localClippingEnabled = true;

    // Retain set from GridConfig — classes to skip clipping
    var retainSet = (typeof GridConfig !== 'undefined')
      ? GridConfig.retainSet(viewMode)
      : { 'IfcFurnishingElement': 1, 'IfcFurniture': 1 }; // fallback
    var clipped = 0, skipped = 0;
    A.collectMeshes(function(o) { return o.isMesh; }).forEach(function(obj) {
      var cls = (obj.userData && obj.userData.ifcClass) || '';
      if (retainSet[cls]) { skipped++; return; }
      obj.material.clippingPlanes = [_floorClipPlane];
      obj.material.clipShadows = true;
      obj.material.needsUpdate = true;
      clipped++;
    });
    log('§VIEW_CLIP apply cutZ_ifc=' + cutZ.toFixed(2) + ' cutY_three=' + cutY.toFixed(2) +
        ' clipped=' + clipped + ' furniture_skipped=' + skipped);
  }

  /** Clear floor plan section clipping — wipe ALL meshes */
  function clearFloorClip(A) {
    if (!_floorClipPlane) return;
    A.collectMeshes(function(o) { return o.isMesh; }).forEach(function(obj) {
      obj.material.clippingPlanes = null;
      obj.material.clipShadows = false;
      obj.material.needsUpdate = true;
    });
    _floorClipPlane = null;
    A.renderer.localClippingEnabled = false;
    log('§VIEW_CLIP cleared');
  }

  // ── Theme ──────────────────────────────────────────────────────────
  // No forced theme toggle. User controls theme via sunglasses button.
  // Removed: applyFloorTheme / restoreFloorTheme — was inverting background
  // without user request (invention, not extraction).

  // ── Public API ────────────────────────────────────────────────────

  /** Lock camera to a named view preset */
  function lockView(A, mode, env) {
    if (!env || !VIEW_DEFS[mode]) return;
    saveCameraState(A);

    var centre = buildingCentre3(A, env);

    // Always clear previous clip first — non-negotiable
    clearFloorClip(A);

    // Concern 1: Camera — §7: null means aspect mismatch, do not enter 2D mode
    var cam = positionOrthoCamera(A, mode, env, centre);
    if (!cam) {
      console.log('§GRID_VIEW lockView aborted — setupCamera returned null for mode=' + mode);
      return;
    }
    A.controls.target.copy(centre);
    swapCamera(A, cam);
    A.controls.enableRotate = false;
    A.controls.enablePan = true;
    A.controls.enableZoom = true;
    A.controls.update();

    // Concern 2: Clip + theme — only if view definition says so
    var def = VIEW_DEFS[mode];
    if (def.clip) {
      applyFloorClip(A, env, mode);
    }

    _activeView = mode;
    A.markDirty();
    log('§VIEW_LOCK mode=' + mode + ' centre=(' + centre.x.toFixed(1) + ',' +
        centre.y.toFixed(1) + ',' + centre.z.toFixed(1) + ')');
  }

  /** Unlock: restore the original perspective camera */
  function unlockView(A) {
    if (!_savedCamera || !_origCamera) {
      _activeView = null;
      log('§VIEW_UNLOCK (no saved state)');
      return;
    }

    if (_resizeHandler) {
      window.removeEventListener('resize', _resizeHandler);
      _resizeHandler = null;
    }

    _origCamera.position.copy(_savedCamera.pos);
    _origCamera.up.copy(_savedCamera.up);
    _origCamera.fov = _savedCamera.fov;
    _origCamera.aspect = window.innerWidth / window.innerHeight;
    _origCamera.updateProjectionMatrix();

    A.camera = _origCamera;
    A.controls.object = _origCamera;
    A.controls.target.copy(_savedCamera.target);
    A.controls.enableRotate = true;
    A.controls.enablePan = true;
    A.controls.update();

    if (A._onResize) A._onResize();

    var wasFloor = (_activeView === 'floor' || _activeView === 'floor1');
    clearFloorClip(A);
    // No theme restore needed — user controls theme

    _savedCamera = null;
    _activeView = null;
    A.markDirty();
    log('§VIEW_UNLOCK restored');
  }

  return {
    VIEW_DEFS:      VIEW_DEFS,
    lockView:       lockView,
    unlockView:     unlockView,
    clearFloorClip: clearFloorClip,
    activeView:     function() { return _activeView; }
  };
})();
