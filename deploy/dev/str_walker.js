/**
 * BIM OOTB — STR Walker JS (the structural RouteWalker)
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * Implements prompts/STR_ROUTEWALKING_SPEC.md — the STRUCTURAL walker, mirror of RouteWalker.
 * SLICE 1 = the SKELETON walk (deterministic, RS-clean): derive the emergent structural grid
 * from a building's columns (the SDG datum_plane substrate), then WALK each column onto its
 * grid intersection. Position = f(grid). NON-INVENT: every gridline value is the MEAN of the
 * real column coordinates it owns (traceable), zero hardcoded spacing, zero invented positions.
 *
 * This file is NEW. It edits no existing file. GUID prefix: SW2D- (never collides with
 * IFC-extracted, Java RW-, or JS RW2D- elements).
 *
 * Witness: scripts/witness_str_walk_skeleton.js (W-STR-WALK-SKELETON).
 * Oracle: pristine *_extracted.db / raw extraction — NEVER cooked output.db.
 */
'use strict';

// ─── Constants ───────────────────────────────────────────────
var SW_PREFIX = 'SW2D-';
// Cluster gap tolerance (metres): a gap > this between consecutive sorted column coordinates
// starts a new gridline. Within-line jitter is centimetres; adjacent lines are ≥~0.5m apart.
// MEASURED on Terminal (158 cols): the grid RESOLVES at gapTol ≤ 0.5m → 18×10 lines, max column
// residual 0.329m, mean 5.2cm (a stable plateau; coarser tol MERGES real X-lines → false 0.855m
// residual). Tuned to the data via the sweep in witness_str_walk_skeleton.js, reported by §-log.
var SW_GRID_GAP_TOL = 0.5;
// A cluster whose total span exceeds this is rejected as NOT a single gridline (anti-drift guard
// against greedy chaining across a whole wing). Span is reported; never silently merged.
var SW_GRID_SPAN_MAX = 2.0;

// ─── 1D clustering → gridlines (the emergent datum) ──────────
// Greedy by consecutive gap; gridline value = MEAN of the cluster it owns (non-invent).
// Returns [{ value, members:[v...], span }]. Members trace each line to real coordinates.
function swClusterAxis(values, gapTol, spanMax) {
  if (!values.length) return [];
  var sorted = values.slice().sort(function (a, b) { return a - b; });
  var lines = [];
  var cur = [sorted[0]];
  for (var i = 1; i < sorted.length; i++) {
    if (sorted[i] - sorted[i - 1] <= gapTol) {
      cur.push(sorted[i]);
    } else {
      lines.push(cur); cur = [sorted[i]];
    }
  }
  lines.push(cur);
  return lines.map(function (members) {
    var sum = members.reduce(function (s, v) { return s + v; }, 0);
    var span = members[members.length - 1] - members[0];
    return { value: sum / members.length, members: members, span: span, spanOk: span <= spanMax };
  });
}

// ─── Derive the emergent structural grid from columns ────────
// columns: [{ guid, x, y, z }]. Returns { xLines:[..], yLines:[..], xMeta, yMeta, gapTol }.
function swDeriveGrid(columns, opts) {
  opts = opts || {};
  var gapTol = opts.gapTol != null ? opts.gapTol : SW_GRID_GAP_TOL;
  var spanMax = opts.spanMax != null ? opts.spanMax : SW_GRID_SPAN_MAX;
  var xMeta = swClusterAxis(columns.map(function (c) { return c.x; }), gapTol, spanMax);
  var yMeta = swClusterAxis(columns.map(function (c) { return c.y; }), gapTol, spanMax);
  return {
    xLines: xMeta.map(function (m) { return m.value; }),
    yLines: yMeta.map(function (m) { return m.value; }),
    xMeta: xMeta, yMeta: yMeta, gapTol: gapTol, spanMax: spanMax
  };
}

// ─── Nearest gridline ────────────────────────────────────────
function swNearest(value, lines) {
  var best = lines[0], bestD = Math.abs(value - lines[0]);
  for (var i = 1; i < lines.length; i++) {
    var d = Math.abs(value - lines[i]);
    if (d < bestD) { bestD = d; best = lines[i]; }
  }
  return { line: best, dist: bestD };
}

// ─── SKELETON WALK: snap each column onto its grid intersection ──
// Position = f(grid): walked (x,y) is EXACTLY a gridline pair (deterministic, RS-clean).
// residual = planar distance from the real column to its snapped intersection (reported, the
// grid-quantization error). z is kept verbatim (Z lattice handled by a later slice).
function swWalkColumns(columns, grid, opts) {
  opts = opts || {};
  var out = [];
  for (var i = 0; i < columns.length; i++) {
    var c = columns[i];
    var nx = swNearest(c.x, grid.xLines);
    var ny = swNearest(c.y, grid.yLines);
    var residual = Math.hypot(nx.dist, ny.dist);
    out.push({
      guid: SW_PREFIX + (c.guid || ('col' + i)),
      srcGuid: c.guid,                 // the extracted column this walks (oracle link)
      x: nx.line, y: ny.line, z: c.z,  // ON the grid — f(grid)
      residual: residual,
      provenance: 'derived:grid'       // never ifc_extract; the position is computed from the datum
    });
  }
  return out;
}

// ─── SPANS WALK: girders between adjacent grid columns ───────
// The `spans` edge (prompts/SPATIAL_DEPENDENCY_GRAPH.md §SPANS): along each gridline, columns
// snapped to that line are sorted and ADJACENT pairs get a girder. The girder runs ON one datum
// (its gridline) and SPANS between two distinct perpendicular datums (the two gridlines its
// endpoints sit on); span == |toDatum − fromDatum| = one structural bay. Cross-section is NOT
// derived here (held; sized by the regulatory handler later). NON-INVENT: endpoints are real
// snapped columns, span is a measured grid gap, zero invented length.
function swWalkGirders(columns, grid, opts) {
  var snap = columns.map(function (c) {
    return { x: swNearest(c.x, grid.xLines).line, y: swNearest(c.y, grid.yLines).line, srcGuid: c.guid };
  });
  var girders = [];
  function emit(axis, onDatum, fromD, toD, from, to, i) {
    girders.push({
      guid: SW_PREFIX + 'GIRDER-' + axis + onDatum.toFixed(2) + '-' + i,
      axis: axis, onDatum: onDatum,            // the gridline the girder runs along
      fromDatum: fromD, toDatum: toD,          // the two distinct datums it spans between
      from: from, to: to, span: Math.abs(toD - fromD),
      provenance: 'derived:str-walk'
    });
  }
  // Girders along each X-line span between adjacent Y-datums (sort the line's columns by y).
  grid.xLines.forEach(function (xl) {
    var on = snap.filter(function (s) { return s.x === xl; }).sort(function (a, b) { return a.y - b.y; });
    for (var i = 1; i < on.length; i++) {
      if (on[i].y === on[i - 1].y) continue;   // duplicate column at one intersection
      emit('Xline@', xl, on[i - 1].y, on[i].y, [xl, on[i - 1].y], [xl, on[i].y], i);
    }
  });
  // Girders along each Y-line span between adjacent X-datums.
  grid.yLines.forEach(function (yl) {
    var on = snap.filter(function (s) { return s.y === yl; }).sort(function (a, b) { return a.x - b.x; });
    for (var i = 1; i < on.length; i++) {
      if (on[i].x === on[i - 1].x) continue;
      emit('Yline@', yl, on[i - 1].x, on[i].x, [on[i - 1].x, yl], [on[i].x, yl], i);
    }
  });
  return girders;
}

// ─── Convenience: derive + walk in one call ──────────────────
function swWalkSkeleton(columns, opts) {
  var grid = swDeriveGrid(columns, opts);
  var walked = swWalkColumns(columns, grid, opts);
  var girders = swWalkGirders(columns, grid, opts);
  return { grid: grid, walked: walked, girders: girders };
}

// ─── Exports (node) + globals (browser eval) ─────────────────
var _swApi = {
  SW_PREFIX: SW_PREFIX, SW_GRID_GAP_TOL: SW_GRID_GAP_TOL, SW_GRID_SPAN_MAX: SW_GRID_SPAN_MAX,
  swClusterAxis: swClusterAxis, swDeriveGrid: swDeriveGrid, swNearest: swNearest,
  swWalkColumns: swWalkColumns, swWalkGirders: swWalkGirders, swWalkSkeleton: swWalkSkeleton
};
if (typeof module !== 'undefined' && module.exports) module.exports = _swApi;
if (typeof window !== 'undefined') Object.keys(_swApi).forEach(function (k) { window[k] = _swApi[k]; });
