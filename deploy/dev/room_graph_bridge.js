// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// room_graph_bridge.js — §R5-A (prompts/Viewer/FLY_TOUR_CORRIDOR_GRAPH.md §R5-A)
//
// deploy/dev's tour.js is now bim-ootb's v19, whose graph route needs exactly TWO externals that
// this fork does not have: `A.getRoomGraph` and `A.ensureRooms`. In bim-ootb both live inside
// viewer/navigate_find.js (5252 lines — the Find panel, HBA overlay and lens stack, none of it on
// the path to "does this building show a highlight", and it additionally needs five APIs this fork
// lacks: activeGuidFilter, filterByGuids, buildingName, _hbaTenancySpec, _applyPendingPatch).
// So the two functions are ported HERE instead of dragging that file across. Measured scope call,
// recorded in the spec — not a silent narrowing.
//
// §R5-A IS CODE-ONLY BY DESIGN. `ensureRooms` here REPORTS what the db already has; it does NOT
// self-heal. The patch loader (`_applyPendingPatch`) + raster SQL patches are §R5-B, deliberately
// sequenced after A is verified in dev. A building whose db carries no rooms therefore degrades to
// the LEGACY tour exactly as it did before this port — that is the gate, not a shortfall.
function setupRoomGraphBridge(A) {
  console.log('[R5A] §R5A_BRIDGE v1 (getRoomGraph + ensureRooms; self-heal is R5-B)');

  // ── A.getRoomGraph — body ported verbatim from navigate_find.js `_roomGraphFor()`.
  // One graph per building, never two: the cache key is `activeBuilding`, so switching buildings
  // rebuilds and a repeat press reuses. Returns null when the module never loaded (a §LOAD_FAIL on
  // room_graph.js, or an older cached index.html) — tour.js treats null as "no route" and falls
  // through to the legacy centroid tour, which is the whole graceful-degradation contract.
  var _graphCache = null, _graphBld = null;
  A.getRoomGraph = function() {
    var RG = (typeof window !== 'undefined') && window.RoomGraph;
    if (!RG || !A.dbQuery) return null;
    if (_graphCache && _graphBld === A.activeBuilding) return _graphCache;
    var g = RG.buildGraph(A.dbQuery, { log: function(m) { console.log('[RP-PATH] ' + m); } });
    _graphCache = g; _graphBld = A.activeBuilding;
    return g;
  };
  // Invalidate when the db underneath changes (building switch handles itself via the key; this is
  // for an in-place db swap, e.g. a reload of the same building).
  A._roomGraphInvalidate = function() { _graphCache = null; _graphBld = null; };

  // ── A.ensureRooms — the A-scope report. Same RESULT SHAPE tour.js already consumes
  // ({status, source, rooms}) so no tour.js edit is needed, and the same room-state classification
  // bim-ootb uses: zero rooms / all-compiled (RM_ prefix) / real extraction present.
  //   status 'present' → the tour can build a graph route
  //   status 'zero'    → no rooms at all; tour.js will log §FLY_ROUTE_REJECT and fly the legacy tour
  // `source` is always 'none' here BY DESIGN — nothing was injected, because injecting is R5-B.
  A.ensureRooms = function(opts) {
    opts = opts || {};
    return Promise.resolve().then(function() {
      if (!A.db) return { status: 'error', message: 'no db', source: 'none', rooms: 0 };
      var total = 0, compiled = 0;
      try {
        var q = A.db.exec("SELECT COUNT(*), COUNT(CASE WHEN guid LIKE 'RM\\_%' ESCAPE '\\' THEN 1 END)" +
          " FROM spatial_structure WHERE type='IfcSpace'");
        if (q.length) { total = q[0].values[0][0] || 0; compiled = q[0].values[0][1] || 0; }
      } catch (e) { total = 0; compiled = 0; }   // table missing on this building — same as zero
      var real = total > 0 && total !== compiled;
      // §R5A_ENSURE_ROOMS is the honest §-line: it says what exists AND says plainly that no
      // self-heal ran, so a log reader never mistakes "rooms=0" here for "the walker failed".
      console.log('[R5A] §R5A_ENSURE_ROOMS bld=' + (A.activeBuilding || '') + ' rooms=' + total +
        ' compiled=' + compiled + ' real=' + real + ' selfHeal=none(R5-B)' +
        (opts.force ? ' forceIgnored=true' : ''));
      return { status: total > 0 ? 'present' : 'zero', real: real, rooms: total, source: 'none' };
    });
  };
}
