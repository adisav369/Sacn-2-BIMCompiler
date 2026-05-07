/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_contours.js — 2D Line Renderer (Single Responsibility: engine output → Three.js lines)
 *
 * Does NOT call engines. Receives pre-computed geometry and GridConfig styles,
 * produces Three.js line objects in a managed THREE.Group.
 *
 * API:
 *   GridContours.renderContours(APP, contourData, viewMode, cutZ)  → THREE.Group
 *   GridContours.renderEdges(APP, edgeData, viewMode, env)         → THREE.Group
 *   GridContours.renderLevelMarkers(APP, storeys, viewMode, env)   → THREE.Group
 *   GridContours.addDoorArcs(APP, doorArcs, viewMode, cutZ)
 *   GridContours.clear(APP)
 *   GridContours.activeGroup()
 *
 * Log tags: §CONTOUR_RENDER, §EDGE_RENDER, §LEVEL_MARKER, §CONTOUR_CLEAR
 */
var GridContours = (function() {
  'use strict';

  var _group = null;       // THREE.Group holding all contour/edge lines

  function log(msg) { console.log('[GridContours] ' + msg); }

  /** Ensure the contour group exists and is in the scene */
  function ensureGroup(APP) {
    if (!_group) {
      _group = new THREE.Group();
      _group.name = 'contourOverlay';
      _group.renderOrder = 1100;
    }
    if (!_group.parent) {
      APP.scene.add(_group);
    }
    return _group;
  }

  /** Dispose a Three.js object's geometry and material */
  function disposeObj(obj) {
    if (obj.geometry) obj.geometry.dispose();
    if (obj.material) {
      if (obj.material.map) obj.material.map.dispose();
      obj.material.dispose();
    }
  }

  // ── Section Contour Renderer ──────────────────────────────────────

  /**
   * Render section cut contours as Three.js Lines.
   * @param {Object} APP — viewer app
   * @param {Array} contourData — from SectionCut.sectionCut(): [{ifcClass, contours: [{points}]}]
   * @param {string} viewMode — 'floor' or 'floor1'
   * @param {number} cutZ — IFC Z of section cut
   * @returns {THREE.Group}
   */
  function renderContours(APP, contourData, viewMode, cutZ) {
    var group = ensureGroup(APP);
    var lineCount = 0;

    // Structural classes: render as filled polygons (solid black = wall thickness visible)
    // IfcSlab excluded — slab contour covers entire floor area, obscures walls
    var FILL_CLASSES = { 'IfcWall': 1, 'IfcWallStandardCase': 1, 'IfcColumn': 1 };

    for (var i = 0; i < contourData.length; i++) {
      var el = contourData[i];
      var style = GridConfig.styleFor(viewMode, el.ifcClass);
      var contours = el.contours || [];
      var doFill = !!FILL_CLASSES[el.ifcClass];

      for (var c = 0; c < contours.length; c++) {
        var pts = contours[c].points || contours[c];
        if (!pts || pts.length < 2) continue;

        var threePoints = [];
        for (var p = 0; p < pts.length; p++) {
          var t = APP.ifc2three(pts[p][0], pts[p][1], cutZ);
          threePoints.push(new THREE.Vector3(t.x, t.y, t.z));
        }

        // Filled polygon for structural elements (shows wall thickness)
        if (doFill && threePoints.length >= 3) {
          var shape = new THREE.Shape();
          shape.moveTo(threePoints[0].x, threePoints[0].z); // XZ plane (Y is up)
          for (var sp = 1; sp < threePoints.length; sp++) {
            shape.lineTo(threePoints[sp].x, threePoints[sp].z);
          }
          shape.closePath();
          var shapeGeom = new THREE.ShapeGeometry(shape);
          // ShapeGeometry creates in XY — rotate to XZ (floor plan)
          shapeGeom.rotateX(-Math.PI / 2);
          var fillMat = new THREE.MeshBasicMaterial({
            color: style.color, side: THREE.DoubleSide, depthTest: false
          });
          var fillMesh = new THREE.Mesh(shapeGeom, fillMat);
          fillMesh.position.y = threePoints[0].y;
          fillMesh.renderOrder = 1099;
          fillMesh.userData = { isContour: true, guid: el.guid, ifcClass: el.ifcClass };
          group.add(fillMesh);
        }

        // Outline stroke (all elements)
        var geom = new THREE.BufferGeometry().setFromPoints(threePoints);
        var mat = new THREE.LineBasicMaterial({ color: style.color, linewidth: style.weight || 1 });
        var line = new THREE.Line(geom, mat);
        line.renderOrder = 1100;
        line.userData = { isContour: true, guid: el.guid, ifcClass: el.ifcClass };
        group.add(line);
        lineCount++;
      }
    }

    APP.markDirty();
    log('§CONTOUR_RENDER mode=' + viewMode + ' elements=' + contourData.length + ' lines=' + lineCount);
    return group;
  }

  // ── Elevation Edge Renderer ───────────────────────────────────────

  /** Map elevation (h,v) back to IFC coords for a given face */
  function elevHVtoIFC(h, v, face, env) {
    var cy = (env.yMin + env.yMax) / 2;
    var cx = (env.xMin + env.xMax) / 2;
    switch (face) {
      case 'front': return { ix: h,  iy: cy, iz: v };
      case 'rear':  return { ix: -h, iy: cy, iz: v };
      case 'left':  return { ix: cx, iy: h,  iz: v };
      case 'right': return { ix: cx, iy: -h, iz: v };
      default:      return { ix: h,  iy: cy, iz: v };
    }
  }

  /**
   * Render elevation edges as Three.js LineSegments.
   * @param {Object} APP
   * @param {Array} edgeData — from Elevation.generateElevation(): [{ifcClass, edges: [[h0,v0,h1,v1]], depth}]
   * @param {string} viewMode — 'front','rear','left','right'
   * @param {Object} env — building envelope {xMin,xMax,yMin,yMax,zMin,zMax}
   * @returns {THREE.Group}
   */
  function renderEdges(APP, edgeData, viewMode, env) {
    var group = ensureGroup(APP);
    var segCount = 0;

    // Sort by depth (back to front) for proper overdraw
    edgeData.sort(function(a, b) { return b.depth - a.depth; });

    for (var i = 0; i < edgeData.length; i++) {
      var el = edgeData[i];
      var style = GridConfig.styleFor(viewMode, el.ifcClass);
      var edges = el.edges || [];
      if (edges.length === 0) continue;

      // Batch all edges of same element into one LineSegments
      var positions = [];
      for (var e = 0; e < edges.length; e++) {
        var edge = edges[e];
        var ifc0 = elevHVtoIFC(edge[0], edge[1], viewMode, env);
        var ifc1 = elevHVtoIFC(edge[2], edge[3], viewMode, env);
        var t0 = APP.ifc2three(ifc0.ix, ifc0.iy, ifc0.iz);
        var t1 = APP.ifc2three(ifc1.ix, ifc1.iy, ifc1.iz);
        positions.push(t0.x, t0.y, t0.z, t1.x, t1.y, t1.z);
      }

      var geom = new THREE.BufferGeometry();
      geom.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
      var mat = new THREE.LineBasicMaterial({ color: style.color, linewidth: style.weight || 1 });
      var segs = new THREE.LineSegments(geom, mat);
      segs.renderOrder = 1100;
      segs.userData = { isContour: true, guid: el.guid, ifcClass: el.ifcClass };
      group.add(segs);
      segCount += edges.length;
    }

    // 3D meshes stay visible — elevation edges overlay on top.
    // User can toggle mesh visibility independently if desired.

    APP.markDirty();
    log('§EDGE_RENDER mode=' + viewMode + ' elements=' + edgeData.length + ' segments=' + segCount);
    return group;
  }

  // ── Level Markers ─────────────────────────────────────────────────

  /**
   * Render horizontal level markers on elevation views.
   * @param {Object} APP
   * @param {Array} storeys — from SectionCut.detectStoreys(): [{name, floorZ}]
   * @param {string} viewMode
   * @param {Object} env — building envelope
   * @returns {THREE.Group}
   */
  function renderLevelMarkers(APP, storeys, viewMode, env) {
    var markerCfg = GridConfig.levelMarkersFor(viewMode);
    if (!markerCfg || !markerCfg.enabled) return null;

    var group = ensureGroup(APP);
    var style = markerCfg.style || { color: '#666666', weight: 0.5 };
    var dash = style.dash || [0.3, 0.15];

    // Building width in the elevation's horizontal axis
    var hMin, hMax;
    if (viewMode === 'front' || viewMode === 'rear') {
      hMin = env.xMin; hMax = env.xMax;
    } else {
      hMin = env.yMin; hMax = env.yMax;
    }
    var margin = (hMax - hMin) * 0.15;

    for (var i = 0; i < storeys.length; i++) {
      var storey = storeys[i];
      var z = storey.floorZ;

      // Two endpoints of the level line
      var ifc0 = elevHVtoIFC(hMin - margin, z, viewMode, env);
      var ifc1 = elevHVtoIFC(hMax + margin, z, viewMode, env);
      var t0 = APP.ifc2three(ifc0.ix, ifc0.iy, ifc0.iz);
      var t1 = APP.ifc2three(ifc1.ix, ifc1.iy, ifc1.iz);
      var p0 = new THREE.Vector3(t0.x, t0.y, t0.z);
      var p1 = new THREE.Vector3(t1.x, t1.y, t1.z);

      var geom = new THREE.BufferGeometry().setFromPoints([p0, p1]);
      var mat = new THREE.LineDashedMaterial({
        color: style.color,
        linewidth: style.weight || 0.5,
        dashSize: dash[0],
        gapSize: dash[1]
      });
      var line = new THREE.Line(geom, mat);
      line.computeLineDistances();
      line.renderOrder = 1050;
      line.userData = { isContour: true, isLevelMarker: true, storey: storey.name };
      group.add(line);

      log('§LEVEL_MARKER storey=' + storey.name + ' z=' + z.toFixed(2));
    }

    APP.markDirty();
    return group;
  }

  // ── Door Arcs ─────────────────────────────────────────────────────

  /**
   * Add door arcs to the contour group.
   * @param {Object} APP
   * @param {Array} arcs — from DoorArcs.generateArcs()
   * @param {string} viewMode
   * @param {number} cutZ
   */
  function addDoorArcs(APP, arcs, viewMode, cutZ) {
    if (!arcs || arcs.length === 0) return;
    var group = ensureGroup(APP);
    var style = GridConfig.styleFor(viewMode, 'IfcDoor');

    for (var i = 0; i < arcs.length; i++) {
      var arcLine = DoorArcs.createArcLine(arcs[i], APP.ifc2three, cutZ, style);
      arcLine.userData.isContour = true;
      group.add(arcLine);
    }

    APP.markDirty();
    log('§CONTOUR_RENDER door_arcs=' + arcs.length);
  }

  // ── Cleanup ───────────────────────────────────────────────────────

  function clear(APP) {
    if (_group) {
      _group.traverse(function(obj) {
        if (obj !== _group) disposeObj(obj);
      });
      if (_group.parent) _group.parent.remove(_group);
      _group = null;
    }
    if (APP.markDirty) APP.markDirty();
    log('§CONTOUR_CLEAR done');
  }

  // ── Public API ────────────────────────────────────────────────────

  return {
    renderContours:     renderContours,
    renderEdges:        renderEdges,
    renderLevelMarkers: renderLevelMarkers,
    addDoorArcs:        addDoorArcs,
    clear:              clear,
    activeGroup:        function() { return _group; }
  };
})();
