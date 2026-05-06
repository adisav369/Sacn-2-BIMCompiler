/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_door_arcs.js — Door Swing Arc Rendering
 *
 * Implementing BBC.md §2D_023 — Witness: W-DOOR-ARCS
 *
 * Generates quarter-circle door swing arcs from section cut contours.
 * The door contour from SectionCut.sectionCut() gives us the door panel edge
 * as a line segment. The arc is computed from:
 *   - Hinge point: the endpoint of the door contour closest to a wall contour
 *   - Swing radius: length of the door contour segment (= door width)
 *   - Arc direction: determined by which side the door opens to
 *
 * Extracted, not invented: the hinge point and radius come from mesh geometry.
 *
 * API:
 *   DoorArcs.generateArcs(doorElements, wallElements) → [{hinge, radius, startAngle, endAngle, points}]
 *   DoorArcs.createArcLine(arc, ifc2threeFn, cutZ, style) → THREE.Line
 *
 * Log tags:
 *   §DOOR_ARC_DETECT  — hinge/radius extraction
 *   §DOOR_ARC_RENDER  — Three.js line creation
 */
var DoorArcs = (function() {
  'use strict';

  var ARC_SEGMENTS = 16;  // polyline segments per quarter-circle

  function log(msg) { console.log('[DoorArcs] ' + msg); }

  /**
   * Find the hinge point of a door from its section contour and nearby wall contours.
   * The hinge is the door contour endpoint closest to any wall contour endpoint.
   *
   * @param {Array} doorContour  - [[x,y], ...] polyline from SectionCut
   * @param {Array} wallContours - array of [[x,y], ...] wall contour polylines
   * @returns {{ hinge: [x,y], free: [x,y], radius: number }} or null
   */
  function findHinge(doorContour, wallContours) {
    if (!doorContour || doorContour.length < 2) return null;

    var p0 = doorContour[0];
    var p1 = doorContour[doorContour.length - 1];
    var radius = Math.sqrt(
      (p1[0] - p0[0]) * (p1[0] - p0[0]) +
      (p1[1] - p0[1]) * (p1[1] - p0[1])
    );
    if (radius < 0.05) return null; // too small to be a real door

    // Find which endpoint is closest to any wall contour point — that's the hinge
    var bestDist0 = Infinity, bestDist1 = Infinity;

    for (var w = 0; w < wallContours.length; w++) {
      var wc = wallContours[w];
      for (var wi = 0; wi < wc.length; wi++) {
        var wp = wc[wi];
        var d0 = Math.sqrt((wp[0] - p0[0]) * (wp[0] - p0[0]) + (wp[1] - p0[1]) * (wp[1] - p0[1]));
        var d1 = Math.sqrt((wp[0] - p1[0]) * (wp[0] - p1[0]) + (wp[1] - p1[1]) * (wp[1] - p1[1]));
        if (d0 < bestDist0) bestDist0 = d0;
        if (d1 < bestDist1) bestDist1 = d1;
      }
    }

    // The endpoint closer to a wall is the hinge
    if (bestDist0 <= bestDist1) {
      return { hinge: p0, free: p1, radius: radius };
    } else {
      return { hinge: p1, free: p0, radius: radius };
    }
  }

  /**
   * Generate quarter-circle arc points from hinge → free endpoint.
   * Arc sweeps 90 degrees from the free point direction.
   *
   * @param {{ hinge: [x,y], free: [x,y], radius: number }} arc
   * @returns {Array} [[x,y], ...] polyline points for the arc
   */
  function computeArcPoints(arc) {
    var hx = arc.hinge[0], hy = arc.hinge[1];
    var fx = arc.free[0], fy = arc.free[1];
    var r = arc.radius;

    // Start angle: direction from hinge to free point
    var startAngle = Math.atan2(fy - hy, fx - hx);
    var sweep = Math.PI / 2; // quarter circle

    var points = [];
    for (var i = 0; i <= ARC_SEGMENTS; i++) {
      var t = i / ARC_SEGMENTS;
      var angle = startAngle + t * sweep;
      points.push([
        hx + r * Math.cos(angle),
        hy + r * Math.sin(angle)
      ]);
    }
    return points;
  }

  /**
   * Generate arcs for all door elements given section cut results.
   *
   * @param {Array} doorElements - section cut results filtered to IfcDoor
   * @param {Array} wallElements - section cut results filtered to IfcWall/IfcWallStandardCase
   * @returns {Array} [{ guid, hinge, free, radius, points }]
   */
  function generateArcs(doorElements, wallElements) {
    // Collect all wall contour polylines
    var wallContours = [];
    for (var w = 0; w < wallElements.length; w++) {
      var wContours = wallElements[w].contours || [];
      for (var wc = 0; wc < wContours.length; wc++) {
        wallContours.push(wContours[wc]);
      }
    }

    var arcs = [];
    for (var d = 0; d < doorElements.length; d++) {
      var door = doorElements[d];
      var dContours = door.contours || [];
      for (var dc = 0; dc < dContours.length; dc++) {
        var hingeResult = findHinge(dContours[dc], wallContours);
        if (!hingeResult) continue;

        var arcPoints = computeArcPoints(hingeResult);
        arcs.push({
          guid: door.guid,
          hinge: hingeResult.hinge,
          free: hingeResult.free,
          radius: hingeResult.radius,
          points: arcPoints
        });
        log('§DOOR_ARC_DETECT guid=' + door.guid + ' radius=' + hingeResult.radius.toFixed(3) +
            ' hinge=(' + hingeResult.hinge[0].toFixed(2) + ',' + hingeResult.hinge[1].toFixed(2) + ')');
      }
    }
    return arcs;
  }

  /**
   * Create a Three.js Line for a door arc.
   *
   * @param {Object} arc       - from generateArcs()
   * @param {Function} ifc2three - coordinate transform function
   * @param {number} cutZ      - IFC Z of the section cut
   * @param {Object} style     - { color, weight } from GridConfig
   * @returns {THREE.Line}
   */
  function createArcLine(arc, ifc2three, cutZ, style) {
    var color = (style && style.color) ? style.color : '#333333';
    var weight = (style && style.weight) ? style.weight : 1.0;

    var threePoints = [];
    for (var i = 0; i < arc.points.length; i++) {
      var p = arc.points[i];
      var t = ifc2three(p[0], p[1], cutZ);
      threePoints.push(new THREE.Vector3(t.x, t.y, t.z));
    }

    var geom = new THREE.BufferGeometry().setFromPoints(threePoints);
    var mat = new THREE.LineBasicMaterial({ color: color, linewidth: weight });
    var line = new THREE.Line(geom, mat);
    line.renderOrder = 1000;
    line.userData = { isDoorArc: true, guid: arc.guid };
    log('§DOOR_ARC_RENDER guid=' + arc.guid + ' segments=' + threePoints.length);
    return line;
  }

  return {
    generateArcs:  generateArcs,
    createArcLine: createArcLine,
    findHinge:     findHinge,
    computeArcPoints: computeArcPoints
  };
})();
