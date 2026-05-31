// glassbowl_layout.js — the ONE source of truth for bubble positioning across BOTH glassbowl pages.
//   Spec: prompts/GLASSBOWL_LAYOUT.md (W-LAYOUT / W-NUDGE / W-DOCKLENS).
//
// A `Layout` factory of PURE, DETERMINISTIC functions over plain node objects. NO DOM, NO globals,
// NO Math.random — same input always yields the same output (replay/refresh/offline stable).
//
// IMPORTANT — this file is INLINED VERBATIM into both served pages so each stays self-contained,
// file://-safe and offline-SW-cacheable (no new network dependency):
//   • glassbowl.html        — system_explorer.js reads this file and folds it into VIEWER_JS.
//   • glassbowl_gravity.html — a hand-authored static page; the SAME snippet is pasted in by hand
//     between the two sentinel marker lines below so the two copies never drift. If you edit the body,
//     re-paste it into glassbowl_gravity.html (the sentinels bound exactly what to copy) and re-run
//     system_explorer.js so glassbowl.html picks it up too. The sentinels are unique tokens that
//     appear ONLY on the two marker lines (so the generator's slice is unambiguous).
//
// Node accessor model: the two pages name a bubble's live geometry differently — glassbowl uses
// {x,y} with a radius FUNCTION, gravity uses {tx,ty,tr} targets. So every function takes an optional
// `acc` adapter (getX/getY/getR/setX/setY); defaults read x/y and a numeric r. This is what lets ONE
// module own positions on BOTH pages without merging their node shapes.

/* @@LAYOUT_INLINE_START@@ — verbatim-shared body (keep glassbowl_gravity.html's copy identical) */
var Layout = (function () {
  'use strict';
  var GOLDEN = 2.39996322972865332;   // golden angle (radians) — the deterministic spiral step.

  // default accessor: a node carries {x,y} and a numeric .r (render/layout radius).
  function defAcc(acc) {
    acc = acc || {};
    return {
      gx: acc.getX || function (n) { return n.x; },
      gy: acc.getY || function (n) { return n.y; },
      gr: acc.getR || function (n) { return n.r || 0; },
      sx: acc.setX || function (n, v) { n.x = v; },
      sy: acc.setY || function (n, v) { n.y = v; }
    };
  }

  // ── seed: deterministic initial positions on a circle, by index (the existing glassbowl seed).
  //    No Math.random — index→angle. cx/cy default to the frame centre; spread is the ring radius.
  function seed(nodes, opt) {
    opt = opt || {};
    var W = opt.W || 0, H = opt.H || 0;
    var cx = opt.cx != null ? opt.cx : W / 2;
    var cy = opt.cy != null ? opt.cy : H / 2;
    var spread = opt.spread != null ? opt.spread : Math.min(W, H) * 0.32;
    var A = defAcc(opt.acc), n = nodes.length;
    for (var i = 0; i < n; i++) {
      var a = i / n * 6.283185307179586;
      A.sx(nodes[i], cx + Math.cos(a) * spread);
      A.sy(nodes[i], cy + Math.sin(a) * spread);
    }
    return nodes;
  }

  // ── countOverlaps: pairs whose centre-distance < r_a + r_b + pad (the bunching metric).
  function countOverlaps(nodes, opt) {
    opt = opt || {};
    var pad = opt.pad != null ? opt.pad : 0, A = defAcc(opt.acc), c = 0;
    for (var i = 0; i < nodes.length; i++) {
      for (var j = i + 1; j < nodes.length; j++) {
        var dx = A.gx(nodes[j]) - A.gx(nodes[i]), dy = A.gy(nodes[j]) - A.gy(nodes[i]);
        var d = Math.sqrt(dx * dx + dy * dy);
        if (d < A.gr(nodes[i]) + A.gr(nodes[j]) + pad) c++;
      }
    }
    return c;
  }

  // ── nudge: gentle anti-overlap relaxation. Any pair closer than r_a+r_b+pad is pushed apart a
  //    SMALL fraction of the overlap; EVERY per-node displacement is clamped to maxMove ("not too
  //    much"); `iters` small. Deterministic (fixed iteration order, no random) so a second run on
  //    the same input is identical → preserves the at-rest invariant (Reset re-homes to this).
  //    Returns {overlapsBefore, overlapsAfter, maxMove, maxMoveApplied}.
  function nudge(nodes, opt) {
    opt = opt || {};
    var pad = opt.pad != null ? opt.pad : 6;
    var maxMove = opt.maxMove != null ? opt.maxMove : 8;
    var iters = opt.iters != null ? opt.iters : 6;
    var frac = opt.frac != null ? opt.frac : 0.5;   // fraction of the overlap each side takes
    var A = defAcc(opt.acc);
    var before = countOverlaps(nodes, { pad: pad, acc: opt.acc });
    var maxApplied = 0, n = nodes.length;
    for (var it = 0; it < iters; it++) {
      var dx = new Array(n), dy = new Array(n);
      for (var a = 0; a < n; a++) { dx[a] = 0; dy[a] = 0; }
      for (var i = 0; i < n; i++) {
        for (var j = i + 1; j < n; j++) {
          var ddx = A.gx(nodes[j]) - A.gx(nodes[i]), ddy = A.gy(nodes[j]) - A.gy(nodes[i]);
          var dist = Math.sqrt(ddx * ddx + ddy * ddy);
          var want = A.gr(nodes[i]) + A.gr(nodes[j]) + pad;
          if (dist < want) {
            // direction i→j; if exactly coincident, use a deterministic axis from the index pair.
            var ux, uy;
            if (dist > 1e-6) { ux = ddx / dist; uy = ddy / dist; }
            else { var th = (i * 7 + j * 13) * 0.5; ux = Math.cos(th); uy = Math.sin(th); }
            var push = (want - dist) * frac * 0.5;   // each side moves half the corrected overlap
            dx[i] -= ux * push; dy[i] -= uy * push;
            dx[j] += ux * push; dy[j] += uy * push;
          }
        }
      }
      for (var k = 0; k < n; k++) {
        var mvx = clamp(dx[k], -maxMove, maxMove), mvy = clamp(dy[k], -maxMove, maxMove);
        var mag = Math.sqrt(mvx * mvx + mvy * mvy);
        if (mag > maxApplied) maxApplied = mag;
        if (mvx) A.sx(nodes[k], A.gx(nodes[k]) + mvx);
        if (mvy) A.sy(nodes[k], A.gy(nodes[k]) + mvy);
      }
    }
    var after = countOverlaps(nodes, { pad: pad, acc: opt.acc });
    return { overlapsBefore: before, overlapsAfter: after, maxMove: maxMove, maxMoveApplied: maxApplied };
  }

  // ── dockLens: macOS-dock hover MAGNIFY. Returns a per-node RENDER radius — restR grown toward
  //    maxR by closeness to the cursor (within `reach`), cosine dock falloff. VISUAL ONLY — never
  //    mutates a node's x/y or data. cursor null/undefined → every node sits at its rest radius.
  //    restR/maxR may be numbers OR functions of the node (so a per-bubble rest size can drive it).
  function dockLens(nodes, cursor, opt) {
    opt = opt || {};
    var reach = opt.reach != null ? opt.reach : 130;
    var A = defAcc(opt.acc);
    var restFn = asFn(opt.restR != null ? opt.restR : A.gr);
    var maxFn = asFn(opt.maxR != null ? opt.maxR : function (n) { return restFn(n) * 1.8; });
    var out = new Array(nodes.length);
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i], rest = restFn(n), peak = maxFn(n);
      if (!cursor) { out[i] = rest; continue; }
      var dx = A.gx(n) - cursor.x, dy = A.gy(n) - cursor.y;
      var d = Math.sqrt(dx * dx + dy * dy);
      if (d >= reach) { out[i] = rest; continue; }
      // dock falloff: 1 at the cursor, 0 at `reach`, smooth cosine in between.
      var t = 0.5 * (1 + Math.cos(Math.PI * (d / reach)));   // 1→0
      out[i] = rest + (peak - rest) * t;
    }
    return out;
  }

  // ── STRATEGIES (deterministic target builders) ────────────────────────────────────────────
  // orbitPlanes: classification → z depth plane (spine=0, settlement=+ZD shell, reference=-ZD shell)
  //   with a deterministic per-index jitter so each plane has THICKNESS. Mirrors the generator math;
  //   z stays 0-contributing to the head-on (yaw=pitch=0) projection, so the at-rest layout is flat.
  function orbitPlanes(nodes, opt) {
    opt = opt || {};
    var ZD = opt.ZD != null ? opt.ZD : 220;
    var si = 0, ri = 0, planes = { spine: 0, settlement: 0, reference: 0 };
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (n.settlement) { n.z = ZD + ((si++ % 5) - 2) * 26; planes.settlement++; }
      else if (n.kind === 'master') { n.z = -ZD + ((ri++ % 9) - 4) * 34; planes.reference++; }
      else { n.z = 0; planes.spine++; }
    }
    return planes;
  }

  // diagonal: spread bubbles evenly by index along the screen diagonal axis (1,1)/√2, centred on the
  //   centroid, with a tiny deterministic perpendicular zig so they don't sit on one infinitely-thin
  //   line. Returns per-node {x,y} TARGETS (caller eases to them); does not mutate.
  function diagonal(nodes, opt) {
    opt = opt || {};
    var A = defAcc(opt.acc), n = nodes.length, inv = 1 / Math.sqrt(2);
    var cx = 0, cy = 0;
    for (var a = 0; a < n; a++) { cx += A.gx(nodes[a]); cy += A.gy(nodes[a]); }
    cx /= (n || 1); cy /= (n || 1);
    var L = opt.length != null ? opt.length : 800;        // total span along the diagonal
    var perp = opt.perp != null ? opt.perp : 26;          // alternating perpendicular offset
    var out = new Array(n);
    for (var i = 0; i < n; i++) {
      var t = n > 1 ? (i / (n - 1) - 0.5) : 0;            // -0.5 … +0.5 by index
      var along = t * L, side = (i % 2 ? 1 : -1) * perp * (i % 3) / 2;
      out[i] = { x: cx + along * inv - side * inv, y: cy + along * inv + side * inv };
    }
    return out;
  }

  // gravitySpiral: heaviest → biggest & nearest centre (golden-angle spiral). Mass-driven radius +
  //   spiral placement. Writes tr/tx/ty onto each item (gravity page's target fields) and returns it.
  function gravitySpiral(items, opt) {
    opt = opt || {};
    var maxMass = opt.maxMass || 1, cx0 = opt.cx || 0, cy0 = opt.cy || 0, spread = opt.spread || 100;
    var rMin = opt.rMin != null ? opt.rMin : 16, rRange = opt.rRange != null ? opt.rRange : 48;
    for (var i = 0; i < items.length; i++) {
      var it = items[i], mn = maxMass ? it.mass / maxMass : 0;
      it.tr = rMin + rRange * Math.sqrt(mn);
      if (i === 0 && items.length > 1 && it._center) { it.tx = cx0; it.ty = cy0; continue; }
      var rad = spread * Math.sqrt(i + (it._center ? 0 : 0.6)), ang = i * GOLDEN;
      it.tx = cx0 + rad * Math.cos(ang);
      it.ty = cy0 + rad * Math.sin(ang);
    }
    return items;
  }

  // dictionaryRow: lay N cells out in ONE horizontal row spanning the usable width (the AD spine).
  //   This is the crowded view the dock-lens declutters: cells rest small (capped cr), evenly gapped.
  //   Writes tr/tx/ty and returns the items; cy is the row's y.
  function dictionaryRow(items, opt) {
    opt = opt || {};
    var cx0 = opt.cx || 0, cy0 = opt.cy || 0, halfW = opt.halfW || 200, n = items.length || 1;
    var cr = clamp(halfW * 2 / (n * 1.7), opt.rMin != null ? opt.rMin : 20, opt.rMax != null ? opt.rMax : 38);
    var gap = Math.min((halfW * 2 - 2 * cr) / Math.max(1, n - 1), opt.gapMax != null ? opt.gapMax : 170);
    for (var i = 0; i < items.length; i++) {
      items[i].tr = cr;
      items[i].tx = cx0 + (i - (n - 1) / 2) * gap;
      items[i].ty = cy0;
    }
    return items;
  }

  // helpers
  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }
  function asFn(v) { return typeof v === 'function' ? v : function () { return v; }; }

  return {
    GOLDEN: GOLDEN,
    seed: seed,
    nudge: nudge,
    dockLens: dockLens,
    countOverlaps: countOverlaps,
    orbitPlanes: orbitPlanes,
    diagonal: diagonal,
    gravitySpiral: gravitySpiral,
    dictionaryRow: dictionaryRow,
    clamp: clamp
  };
})();
/* @@LAYOUT_INLINE_END@@ */

// Node-only export (stripped when inlined into the browser pages — the marker block above is the
// browser-shared part; this footer is not copied).
if (typeof module !== 'undefined' && module.exports) module.exports = Layout;
