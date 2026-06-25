/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// sdg_fold.js — Spatial Dependency Graph: the FORWARD FOLD (continuous-Δ engine).
// Implementing SPATIAL_DEPENDENCY_GRAPH.md §FORWARD — Witness: W-SDG-FORWARD
//
// Grab one handle (a datum plane), drag it by a measured Δ, and the owned + forward set re-folds by applying
// each edge's fold rule. PURE TRANSFORM of the captured element_transforms — never a re-derive of the building.
// No DB writes, no verbs, no proximity, no IFC class names (the engine reads edge tables + transforms only).
//
// Fold rules (each is one row of the contract table in the spine — nothing invented):
//   anchored-to(d) → element rigid-follows the datum (offset held), translate Δ on d.axis.
//   spans(lo|hi = d) → the end AT the dragged datum moves Δ, the other end held: center += Δ/2, extent ± Δ.
//   contains(parent moved) → children ride rigid en-bloc (rel_aggregates descendants get the parent's Δ).
//   hosted-by(host moved) → the opening + filling translate with the host ("openings can't divorce their wall").
//
// Defences (ML lessons #3 + stop-gradient): pinnedGuids HALT propagation (stop-gradient); a Δ below thresholdMm
// prunes the ripple (vanish); an element moved more than clampMm is surfaced under clamped[] not applied (explode).

(function (global) {
  'use strict';

  var AXIS_IDX = { X: 0, Y: 1, Z: 2 };

  // graph = {
  //   transforms: { guid: {cx,cy,cz} }            centers, metres
  //   aabb:       { guid: {minX,maxX,minY,maxY,minZ,maxZ} }  metres
  //   datums:     { id:  {axis:'X'|'Y'|'Z', coord} }         metres
  //   anchored:   [ {guid, datumId, axis, offsetMm} ]
  //   spans:      [ {guid, axis, loId, hiId, spanM} ]
  //   aggregates: [ {parent, child} ]
  //   fillsHost:  [ {opening, host, filling} ]
  // }
  //
  // foldDatumDrag(graph, datumId, deltaMm, opts) → FoldResult:
  //   { moved:[{guid,dx,dy,dz}], stretched:[{guid,axis,d_lo_mm,d_hi_mm,new_span_m}],
  //     pinned:[guid], clamped:[{guid,reqMm}], unchanged_count }
  function foldDatumDrag(graph, datumId, deltaMm, opts) {
    opts = opts || {};
    var pinned = new Set(opts.pinnedGuids || []);
    var thresholdMm = opts.thresholdMm != null ? opts.thresholdMm : 0.3;
    var clampMm = opts.clampMm != null ? opts.clampMm : Math.abs(deltaMm) * 10 + 1e-9;

    var datum = graph.datums[datumId];
    var result = { moved: [], stretched: [], pinned: [], clamped: [], unchanged_count: 0 };
    if (!datum) return result;                                   // unknown handle → no-op
    if (Math.abs(deltaMm) < thresholdMm) {                       // vanish-prune: ripple below noise floor
      result.unchanged_count = Object.keys(graph.transforms || {}).length;
      return result;
    }

    var axis = datum.axis;
    var k = AXIS_IDX[axis];
    var deltaM = deltaMm / 1000;

    // --- spans touching the dragged datum take precedence on this axis (a spanning element stretches, it
    //     does NOT rigid-translate). Collect them first so we can exclude them from the anchored rigid-follow.
    var spanGuids = new Set();
    (graph.spans || []).forEach(function (s) {
      if (s.axis !== axis) return;
      if (s.loId !== datumId && s.hiId !== datumId) return;
      if (pinned.has(s.guid)) { result.pinned.push(s.guid); return; }
      var dLo = s.loId === datumId ? deltaMm : 0;                // lo face moves with the datum (shrinks span)
      var dHi = s.hiId === datumId ? deltaMm : 0;                // hi face moves with the datum (grows span)
      var newSpanM = s.spanM + (dHi - dLo) / 1000;
      spanGuids.add(s.guid);
      result.stretched.push({ guid: s.guid, axis: axis, d_lo_mm: dLo, d_hi_mm: dHi, new_span_m: newSpanM });
    });

    // --- rigid-translate seeds: anchored-to the datum (offset held) AND not a spanning element on this axis.
    var moved = new Map();                                       // guid -> [dx,dy,dz] metres
    function addMove(guid, vec) {
      if (pinned.has(guid)) { if (result.pinned.indexOf(guid) < 0) result.pinned.push(guid); return false; }
      moved.set(guid, vec);
      return true;
    }
    var seedVec = [0, 0, 0]; seedVec[k] = deltaM;
    (graph.anchored || []).forEach(function (a) {
      if (a.datumId !== datumId) return;
      if (spanGuids.has(a.guid)) return;                         // spans authoritative on this axis
      addMove(a.guid, seedVec.slice());
    });

    // --- contains cascade: a rigidly-moved parent carries every rel_aggregates descendant en-bloc.
    //     Stop-gradient: a pinned node halts its whole subtree.
    var childrenOf = {};
    (graph.aggregates || []).forEach(function (e) {
      (childrenOf[e.parent] = childrenOf[e.parent] || []).push(e.child);
    });
    function cascade(guid, vec) {
      var kids = childrenOf[guid]; if (!kids) return;
      kids.forEach(function (c) {
        if (pinned.has(c)) { if (result.pinned.indexOf(c) < 0) result.pinned.push(c); return; }
        if (moved.has(c)) return;                                // already seeded/visited
        moved.set(c, vec.slice());
        cascade(c, vec);
      });
    }
    Array.from(moved.keys()).forEach(function (g) { cascade(g, moved.get(g)); });

    // --- hosted-by ride: a moved host carries its opening + filling (openings can't divorce their wall).
    (graph.fillsHost || []).forEach(function (h) {
      if (!moved.has(h.host)) return;
      var vec = moved.get(h.host);
      if (h.opening) addMove(h.opening, vec.slice());
      if (h.filling) addMove(h.filling, vec.slice());
    });

    // --- clamp (explode-guard): surface any element pushed past clampMm instead of applying it.
    moved.forEach(function (vec, guid) {
      var mag = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2]) * 1000;
      if (mag > clampMm) { result.clamped.push({ guid: guid, reqMm: mag }); return; }
      result.moved.push({ guid: guid, dx: vec[0], dy: vec[1], dz: vec[2] });
    });

    var touched = new Set(result.moved.map(function (m) { return m.guid; }));
    result.stretched.forEach(function (s) { touched.add(s.guid); });
    var total = Object.keys(graph.transforms || {}).length;
    result.unchanged_count = Math.max(0, total - touched.size);
    return result;
  }

  var api = { foldDatumDrag: foldDatumDrag, AXIS_IDX: AXIS_IDX };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;                                        // Node (the W-SDG-FORWARD witness)
  } else {
    global.SDGFold = api;                                        // browser (the live viewer fold)
  }
})(typeof window !== 'undefined' ? window : globalThis);
