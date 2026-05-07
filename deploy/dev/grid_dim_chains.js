/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_dim_chains.js — On-Scene Dimension Chain Rendering
 *
 * Single concern: renders measurement annotations (lines + ticks + labels)
 * as Three.js sprites/lines in a given THREE.Group.
 *
 * Does NOT detect grids, does NOT manage state — pure rendering from grid data.
 *
 * API:
 *   DimChains.build(APP, gridGroup, grids, env, opts)  → adds dim objects to gridGroup
 *   DimChains.remove(gridGroup)                        → removes dim objects from gridGroup
 *
 * Log tags: §DIM_CHAIN
 */
var DimChains = (function() {
  'use strict';

  function log(msg) { console.log('[DimChains] ' + msg); }

  /** Theme-aware: light or dark text */
  function isLight(APP) { return !!APP.lightTheme; }

  /** Create a text sprite showing a dimension value */
  function createDimLabel(APP, text, position, bubbleScale) {
    var canvas = document.createElement('canvas');
    canvas.width = 192;
    canvas.height = 48;
    var ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, 192, 48);
    ctx.font = 'bold 28px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    if (isLight(APP)) {
      ctx.fillStyle = '#000000';
      ctx.fillText(text, 96, 24);
    } else {
      ctx.strokeStyle = '#000000';
      ctx.lineWidth = 4;
      ctx.strokeText(text, 96, 24);
      ctx.fillStyle = '#ffffff';
      ctx.fillText(text, 96, 24);
    }
    var texture = new THREE.CanvasTexture(canvas);
    var mat = new THREE.SpriteMaterial({ map: texture, depthTest: false, transparent: true });
    var sprite = new THREE.Sprite(mat);
    sprite.position.copy(position);
    sprite.scale.set(bubbleScale * 2.0, bubbleScale * 0.5, 1);
    sprite.renderOrder = 1001;
    sprite.userData.isDimChain = true;
    return sprite;
  }

  /** Draw one dimension segment: line + ticks + label */
  function addDimSegment(APP, p0, p1, tickDir, label, group, bubbleScale) {
    var dimMat = new THREE.LineBasicMaterial({
      color: isLight(APP) ? 0x555555 : 0x999999,
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
    group.add(createDimLabel(APP, label, mid, bubbleScale));
  }

  /**
   * Build all dimension chain objects and add to gridGroup.
   * @param {Object} APP — viewer app
   * @param {THREE.Group} gridGroup — parent group to attach to
   * @param {Object} grids — { xLines: [{label, position}], yLines: [...] }
   * @param {Object} env — building envelope { xMin, xMax, yMin, yMax, zMin, zMax }
   * @param {Object} opts — { bubbleScale: number }
   */
  function build(APP, gridGroup, grids, env, opts) {
    if (!gridGroup) return;

    var bubbleScale = (opts && opts.bubbleScale) || 1.0;
    var bldW = env.xMax - env.xMin;
    var bldD = env.yMax - env.yMin;
    var maxDim = Math.max(bldW, bldD);
    var dimGap = Math.max(1.0, maxDim * 0.03);

    var groundY = (env.zMin - APP.modelOffset.z) - 0.05;
    var xLines = grids.xLines || [];
    var yLines = grids.yLines || [];

    // X-axis dimension chains (below building in Three.js = +Z)
    if (xLines.length > 1) {
      var refZ = APP.ifc2three(0, env.yMin, env.zMin);
      var baseZ = refZ.z;
      var tier1Offset = baseZ + bubbleScale + dimGap;
      var tickDirX = new THREE.Vector3(0, 0, 1);

      for (var i = 0; i < xLines.length - 1; i++) {
        var pa = APP.ifc2three(xLines[i].position, env.yMin, env.zMin);
        var pb = APP.ifc2three(xLines[i + 1].position, env.yMin, env.zMin);
        var p0 = new THREE.Vector3(pa.x, groundY, tier1Offset);
        var p1 = new THREE.Vector3(pb.x, groundY, tier1Offset);
        var distX = Math.abs(xLines[i + 1].position - xLines[i].position);
        addDimSegment(APP, p0, p1, tickDirX, (distX * 1000).toFixed(0), gridGroup, bubbleScale);
      }

      // Overall
      var tier3Offset = baseZ + bubbleScale + dimGap * 3;
      var pFirst = APP.ifc2three(xLines[0].position, env.yMin, env.zMin);
      var pLast = APP.ifc2three(xLines[xLines.length - 1].position, env.yMin, env.zMin);
      var q0 = new THREE.Vector3(pFirst.x, groundY, tier3Offset);
      var q1 = new THREE.Vector3(pLast.x, groundY, tier3Offset);
      var totalX = Math.abs(xLines[xLines.length - 1].position - xLines[0].position);
      addDimSegment(APP, q0, q1, tickDirX, (totalX * 1000).toFixed(0), gridGroup, bubbleScale);
    }

    // Y-axis dimension chains (left of building in Three.js = -X)
    if (yLines.length > 1) {
      var refX = APP.ifc2three(env.xMin, 0, env.zMin);
      var baseX = refX.x;
      var tier1OffsetY = baseX - bubbleScale - dimGap;
      var tickDirY = new THREE.Vector3(1, 0, 0);

      for (var j = 0; j < yLines.length - 1; j++) {
        var ra = APP.ifc2three(env.xMin, yLines[j].position, env.zMin);
        var rb = APP.ifc2three(env.xMin, yLines[j + 1].position, env.zMin);
        var r0 = new THREE.Vector3(tier1OffsetY, groundY, ra.z);
        var r1 = new THREE.Vector3(tier1OffsetY, groundY, rb.z);
        var distY = Math.abs(yLines[j + 1].position - yLines[j].position);
        addDimSegment(APP, r0, r1, tickDirY, (distY * 1000).toFixed(0), gridGroup, bubbleScale);
      }

      // Overall
      var tier3OffsetY = baseX - bubbleScale - dimGap * 3;
      var sFirst = APP.ifc2three(env.xMin, yLines[0].position, env.zMin);
      var sLast = APP.ifc2three(env.xMin, yLines[yLines.length - 1].position, env.zMin);
      var s0 = new THREE.Vector3(tier3OffsetY, groundY, sFirst.z);
      var s1 = new THREE.Vector3(tier3OffsetY, groundY, sLast.z);
      var totalY = Math.abs(yLines[yLines.length - 1].position - yLines[0].position);
      addDimSegment(APP, s0, s1, tickDirY, (totalY * 1000).toFixed(0), gridGroup, bubbleScale);
    }

    log('§DIM_CHAIN built');
  }

  /** Remove all dimension chain objects from a group */
  function remove(gridGroup) {
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

  /** Clamp dim label sprites + density filter: hide labels that would overlap at current zoom.
   *  visW = visible world width — labels whose text sprite width > gap between them get hidden.
   *  Zooming in shrinks visW → reveals progressively more labels. */
  function clampScales(gridGroup, origBubbleScale, clampedScale, visW) {
    if (!gridGroup) return;
    // Collect dim sprites with positions for density check
    var dimSprites = [];
    gridGroup.traverse(function(obj) {
      if (obj.isSprite && obj.userData.isDimChain) {
        obj.scale.set(clampedScale * 2.0, clampedScale * 0.5, 1);
        dimSprites.push(obj);
      }
    });
    // Density filter: hide labels whose screen-space width would overlap neighbours
    if (!visW || dimSprites.length < 2) return;
    var labelScreenW = clampedScale * 2.0; // world-unit width of label sprite
    // Min gap between labels to stay readable (2× label width)
    var minGap = labelScreenW * 2.0;
    // Sort by X then Z for proximity checks (works for both horizontal and vertical chains)
    dimSprites.sort(function(a, b) {
      var dx = a.position.x - b.position.x;
      return dx !== 0 ? dx : a.position.z - b.position.z;
    });
    // Show first, then only show next if far enough from last shown
    var lastShown = dimSprites[0];
    lastShown.visible = true;
    for (var i = 1; i < dimSprites.length; i++) {
      var dist = dimSprites[i].position.distanceTo(lastShown.position);
      if (dist < minGap) {
        dimSprites[i].visible = false;
      } else {
        dimSprites[i].visible = true;
        lastShown = dimSprites[i];
      }
    }
  }

  return {
    build:       build,
    remove:      remove,
    clampScales: clampScales
  };
})();
