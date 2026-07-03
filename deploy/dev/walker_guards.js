/**
 * BIM OOTB — Walker Guards (the universal guard pass)
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * Implements prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md §2 — the ONE discipline-agnostic guard
 * pass every walker (STR str_walker.js; MEP routewalker.js; future ELEC/FP/ACMV) flows through
 * before a candidate becomes a signed op. It LIFTS the already-built handlers (STR fit/clash +
 * RED/ORANGE/GREEN; MEP ARC-clash + anchor connectivity) into one layer. Reuse, don't rebuild.
 *
 * THE DOOR DOCTRINE, ENFORCED: every guard is a MEASURED predicate over geometry the walker hands
 * in — NEVER keyed off an IFC class or discipline name. (W-GUARD-ABSTRACT greps this file for any
 * class/role/discipline token and must find ZERO.) A discipline differs ONLY by the numeric
 * `priority` it stamps on its candidates (orchestration), never by a branch in here.
 *
 * THE CONTRACT (§2C): each candidate → PLACE(GREEN) | PLACE-BUT-FLAG(ORANGE) | REFUSE(RED/GAP).
 * REFUSE emits a signed WALKER_GAP op (an HONEST non-placement), never a fabricated element.
 * Confidence here is the RAW guard-margin number; CALIBRATION against the oracle is a later layer
 * (walker_confidence.js) — never display this raw number un-calibrated.
 *
 * Witness: scripts/witness_walker_guards.js (W-GUARD-* + W-PRIORITY-ORDER + W-GUARD-ABSTRACT).
 * This file is NEW; it edits no existing file.
 */
'use strict';

// ── Default tolerances (metres / radians). MEASURED-scale, not magic: containment ~ construction
//    tol; surface snap ~ a slab thickness; clashTouch ~ a fabrication gap; orientHard ~ a face that
//    has clearly rotated off its host. A caller may override any via ctx.tol. ──
var WG_TOL = {
  containment: 0.50,   // m — AABB may sit this far past the envelope before it is "outside" (HARD)
  surface: 0.05,       // m — seat within this of the host plane = adhered (PASS)
  surfaceSnap: 0.30,   // m — within this, snap onto the plane + flag (SOFT); beyond = floating (HARD)
  clashTouch: 0.02,    // m — AABB overlap up to this = touching (SOFT); beyond = penetration (HARD)
  orient: 0.262,       // rad (15°) — facing within this of the host normal = aligned (PASS)
  orientHard: 1.047,   // rad (60°) — beyond this off the host normal = impossible orientation (HARD)
  fit: 0.30,           // m — walker's snap displacement (quantization error) within this = fits (PASS)
  fitHard: 0.50        // m — beyond this the walk cannot place it on the derived datum → REFUSE (HARD)
};

// ── small vector helpers ──
function _dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function _norm(a) { var m = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / m, a[1] / m, a[2] / m]; }
function _clamp01(x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }

// ═══ §2A.1 CONTAINMENT — candidate AABB ⊂ building envelope (HARD if outside) ═══
// MEASURED: the max distance any AABB face pokes past the envelope. Catches the smeared/rotated-grid
// failure (a mis-derived datum that throws an element outside the building is REFUSED).
function wgContainment(cand, ctx) {
  var e = ctx.envelope, b = cand.aabb, tol = (ctx.tol && ctx.tol.containment) || WG_TOL.containment;
  if (!e || !b) return { guard: 'containment', status: 'pass', margin: 1, measure: null, reason: 'no envelope/aabb — not constrained' };
  var over = Math.max(0,
    e.minX - b.minX, b.maxX - e.maxX,
    e.minY - b.minY, b.maxY - e.maxY,
    e.minZ - b.minZ, b.maxZ - e.maxZ);
  var status = over <= tol ? 'pass' : 'hard';
  return { guard: 'containment', status: status, margin: _clamp01(1 - over / tol),
    measure: over, reason: 'max overhang ' + over.toFixed(3) + 'm vs tol ' + tol + 'm' };
}

// ═══ §2A.2 SURFACE ADHERENCE — seat sits ON the host plane (HARD if floating; SOFT if drifted) ═══
// host = { normal:[..] (unit), d } plane (n·x = d). seat = the candidate's support point.
function wgSurface(cand, ctx) {
  var tolP = (ctx.tol && ctx.tol.surface) || WG_TOL.surface;
  var tolS = (ctx.tol && ctx.tol.surfaceSnap) || WG_TOL.surfaceSnap;
  if (!cand.hostPlane || !cand.seat) return { guard: 'surface', status: 'pass', margin: 1, measure: null, reason: 'no host plane — not constrained (non-invent)' };
  var n = _norm(cand.hostPlane.normal);
  var dist = Math.abs(_dot(n, cand.seat) - cand.hostPlane.d);
  var status = dist <= tolP ? 'pass' : (dist <= tolS ? 'soft' : 'hard');
  // SOFT → snap the seat onto the plane (report the adjustment; the walker applies it)
  var snap = null;
  if (status === 'soft') {
    var signed = _dot(n, cand.seat) - cand.hostPlane.d;
    snap = [cand.seat[0] - n[0] * signed, cand.seat[1] - n[1] * signed, cand.seat[2] - n[2] * signed];
  }
  return { guard: 'surface', status: status, margin: _clamp01(1 - dist / tolS),
    measure: dist, snap: snap, reason: 'seat ' + dist.toFixed(3) + 'm off host plane (pass≤' + tolP + ', snap≤' + tolS + ')' };
}

// ═══ §2A.3 ORIENTATION — facing follows the host normal (HARD on impossible orientation) ═══
// The en-bloc/door doctrine GENERALISED: a hosted element's face normal should align with its host's
// outward normal (within tol). MEASURED angle between the two — no class whitelist. A face pointing
// +Z while its host is a vertical surface ⇒ ~90° ⇒ HARD. |dot| so an opposite normal (180°) is fine.
function wgOrientation(cand, ctx) {
  var tolA = (ctx.tol && ctx.tol.orient) || WG_TOL.orient;
  var tolH = (ctx.tol && ctx.tol.orientHard) || WG_TOL.orientHard;
  if (!cand.normal || !cand.hostNormal) return { guard: 'orientation', status: 'pass', margin: 1, measure: null, reason: 'no measured normal/host normal — not constrained (non-invent)' };
  var align = Math.abs(_dot(_norm(cand.normal), _norm(cand.hostNormal)));
  var dev = Math.acos(_clamp01(align));    // radians off the host normal
  var status = dev <= tolA ? 'pass' : (dev <= tolH ? 'soft' : 'hard');
  return { guard: 'orientation', status: status, margin: _clamp01(1 - dev / tolH),
    measure: dev, reason: 'facing ' + (dev * 180 / Math.PI).toFixed(1) + '° off host normal (pass≤' +
      (tolA * 180 / Math.PI).toFixed(0) + '°, hard>' + (tolH * 180 / Math.PI).toFixed(0) + '°)' };
}

// ═══ §2A.4 MUTUAL CLASH — AABB does not penetrate an already-placed element (shared CLASH BUS) ═══
// MEASURED penetration depth = min axis-overlap when AABBs intersect. HARD on real penetration,
// SOFT on a touch within tol. ctx.placed is the accumulated bus (ARC→STR→MEP…).
function wgClash(cand, ctx) {
  var tol = (ctx.tol && ctx.tol.clashTouch) || WG_TOL.clashTouch;
  var placed = ctx.placed || [], b = cand.aabb;
  if (!b || !placed.length) return { guard: 'clash', status: 'pass', margin: 1, measure: 0, reason: 'no prior placements / no aabb' };
  var maxPen = 0, withId = null;
  for (var i = 0; i < placed.length; i++) {
    var p = placed[i].aabb; if (!p) continue;
    var ox = Math.min(b.maxX, p.maxX) - Math.max(b.minX, p.minX);
    var oy = Math.min(b.maxY, p.maxY) - Math.max(b.minY, p.minY);
    var oz = Math.min(b.maxZ, p.maxZ) - Math.max(b.minZ, p.minZ);
    if (ox > 0 && oy > 0 && oz > 0) {
      var pen = Math.min(ox, oy, oz);
      if (pen > maxPen) { maxPen = pen; withId = placed[i].id; }
    }
  }
  var status = maxPen <= 0 ? 'pass' : (maxPen <= tol ? 'soft' : 'hard');
  return { guard: 'clash', status: status, margin: _clamp01(1 - maxPen / (tol * 5)),
    measure: maxPen, withId: withId, reason: 'max penetration ' + maxPen.toFixed(3) + 'm (touch≤' + tol + ', else HARD) vs ' + (withId || 'none') };
}

// ═══ §2B.5 SOURCE CONNECTIVITY — a routed/loaded element traces back to a main/ground (HARD if none) ═══
// MEASURED: the walker hands in cand.path (the node chain to the main/riser/foundation it computed).
// HARD if a candidate that REQUIRES a source has an empty/broken path (starts in mid-air).
function wgSource(cand, ctx) {
  if (!cand.requiresPath) return { guard: 'source', status: 'pass', margin: 1, measure: null, reason: 'no source requirement — not constrained' };
  var ok = Array.isArray(cand.path) && cand.path.length > 0;
  return { guard: 'source', status: ok ? 'pass' : 'hard', margin: ok ? 1 : 0,
    measure: ok ? cand.path.length : 0, reason: ok ? ('path of ' + cand.path.length + ' hops to source') : 'NO path to a main/ground — starts in mid-air' };
}

// ═══ §2A.5 FIT — the walker's snap displacement from its measured anchor (REFUSE-to-place valve) ═══
// MEASURED: cand.snapResidual = how far the walk had to move the element to land it on the derived datum
// (the grid-quantization error). On a clean grid this is centimetres; on a rotated/smeared plan where
// the axis-aligned datum derivation degrades, it blows up → the walk cannot honestly place the element
// → HARD → a WALKER_GAP, not a wrong placement. Generalises STR's null-on-no-plates. No class branch.
function wgFit(cand, ctx) {
  if (cand.snapResidual == null) return { guard: 'fit', status: 'pass', margin: 1, measure: null, reason: 'no snap residual reported — not constrained' };
  var tolP = (ctx.tol && ctx.tol.fit) || WG_TOL.fit;
  var tolH = (ctx.tol && ctx.tol.fitHard) || WG_TOL.fitHard;
  var r = cand.snapResidual;
  var status = r <= tolP ? 'pass' : (r <= tolH ? 'soft' : 'hard');
  return { guard: 'fit', status: status, margin: _clamp01(1 - r / tolH),
    measure: r, reason: 'snap residual ' + r.toFixed(3) + 'm (fits≤' + tolP + ', refuse>' + tolH + ')' };
}

var WG_GUARDS = [wgContainment, wgSurface, wgOrientation, wgClash, wgSource, wgFit];

// ═══ §2C THE GUARD-PASS CONTRACT — evaluate all guards on one candidate → one signed outcome ═══
// Returns { id, outcome:'PLACE'|'PLACE_FLAG'|'REFUSE', signal:'GREEN'|'ORANGE'|'RED', confidence,
//           guards:[...], op:{opType, params, provenance} }. Confidence = RAW guard margin (min =
//           weakest link); ORANGE is capped (a flagged placement can never read as high-confidence);
//           RED = 0. CALIBRATION is a later layer — never show this number un-calibrated.
function wgEvaluate(cand, ctx) {
  ctx = ctx || {};
  var results = WG_GUARDS.map(function (g) { return g(cand, ctx); });
  var hard = results.filter(function (r) { return r.status === 'hard'; });
  var soft = results.filter(function (r) { return r.status === 'soft'; });
  // weakest-link margin over the guards that actually applied (measure !== null)
  var applied = results.filter(function (r) { return r.measure !== null; });
  var minMargin = applied.length ? Math.min.apply(null, applied.map(function (r) { return r.margin; })) : 1;

  var outcome, signal, confidence, op;
  if (hard.length) {
    outcome = 'REFUSE'; signal = 'RED'; confidence = 0;
    op = { opType: 'WALKER_GAP', params: { id: cand.id, pos: cand.pos || null,
      reasons: hard.map(function (r) { return r.guard + ': ' + r.reason; }) }, provenance: 'derived:guard-refuse' };
  } else if (soft.length) {
    outcome = 'PLACE_FLAG'; signal = 'ORANGE'; confidence = Math.min(0.6, minMargin);
    // apply any snap the surface guard proposed
    var snapR = soft.filter(function (r) { return r.snap; })[0];
    var placedSeat = snapR ? snapR.snap : (cand.seat || null);
    op = { opType: 'WALKER_PLACE', params: { id: cand.id, pos: cand.pos || null, seat: placedSeat,
      flags: soft.map(function (r) { return r.guard + ': ' + r.reason; }) }, provenance: 'derived:guard-place-flag' };
  } else {
    outcome = 'PLACE'; signal = 'GREEN'; confidence = minMargin;
    op = { opType: 'WALKER_PLACE', params: { id: cand.id, pos: cand.pos || null, seat: cand.seat || null }, provenance: 'derived:guard-place' };
  }
  return { id: cand.id, outcome: outcome, signal: signal, confidence: confidence,
    guards: results, op: op };
}

// ═══ §2B.6 PRIORITY PLACEMENT ORDER — the orchestration (ARC→STR→MEP→…) ═══
// Run a batch of candidates in ascending `priority` so later walks read the accumulated clash bus and
// respect earlier placements. The bus is the ONLY shared state; every outcome is a signed op so the
// whole walk is deterministic-replay. (Priority is a NUMBER the discipline stamps — no name in here.)
function wgRunPass(candidates, ctx) {
  ctx = ctx || {};
  var bus = (ctx.placed || []).slice();
  var ordered = candidates.slice().sort(function (a, b) {
    return (a.priority || 0) - (b.priority || 0);
  });
  var results = [], ops = [], placedCount = 0, refusedCount = 0;
  for (var i = 0; i < ordered.length; i++) {
    var cand = ordered[i];
    var r = wgEvaluate(cand, { envelope: ctx.envelope, placed: bus, tol: ctx.tol });
    results.push(r); ops.push(r.op);
    if (r.outcome === 'REFUSE') { refusedCount++; }
    else { bus.push({ id: cand.id, aabb: cand.aabb }); placedCount++; }   // accumulate the clash bus
  }
  return { results: results, ops: ops, placed: placedCount, refused: refusedCount, bus: bus };
}

// ─── Exports (node) + globals (browser eval) ─────────────────
var _wgApi = {
  WG_TOL: WG_TOL,
  wgContainment: wgContainment, wgSurface: wgSurface, wgOrientation: wgOrientation,
  wgClash: wgClash, wgSource: wgSource, wgFit: wgFit, wgEvaluate: wgEvaluate, wgRunPass: wgRunPass
};
if (typeof module !== 'undefined' && module.exports) module.exports = _wgApi;
if (typeof window !== 'undefined') Object.keys(_wgApi).forEach(function (k) { window[k] = _wgApi[k]; });
