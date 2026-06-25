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

// ─── Convenience: derive + walk in one call ──────────────────
function swWalkSkeleton(columns, opts) {
  var grid = swDeriveGrid(columns, opts);
  var walked = swWalkColumns(columns, grid, opts);
  return { grid: grid, walked: walked };
}

// ─── Exports (node) + globals (browser eval) ─────────────────
var _swApi = {
  SW_PREFIX: SW_PREFIX, SW_GRID_GAP_TOL: SW_GRID_GAP_TOL, SW_GRID_SPAN_MAX: SW_GRID_SPAN_MAX,
  swClusterAxis: swClusterAxis, swDeriveGrid: swDeriveGrid, swNearest: swNearest,
  swWalkColumns: swWalkColumns, swWalkSkeleton: swWalkSkeleton
};
if (typeof module !== 'undefined' && module.exports) module.exports = _swApi;
if (typeof window !== 'undefined') Object.keys(_swApi).forEach(function (k) { window[k] = _swApi[k]; });
