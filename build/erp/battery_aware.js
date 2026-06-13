// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * battery_aware.js — CONSTRAINT_MITIGATION_LANE item 6 (P2): battery-aware folding + persistence + resume cache.
 *   Spec: docs/FoldEngineConstraints.md §4 P2 / §6 (battery_pct monitor).
 *
 * Three small policies, no verb fork:
 *   1. THROTTLE — when the battery is <20% AND discharging, defer NON-URGENT folds (background re-fold,
 *      compaction, prefetch). URGENT folds (a user commit / the bootstrap that paints the screen) ALWAYS run
 *      — correctness and the user's own action are never sacrificed for battery.
 *   2. PERSIST — request navigator.storage.persist() so the checkpoint/op-log resists eviction.
 *   3. RESUME CACHE — the asset set a service worker should precache (WASM + latest checkpoint) for instant
 *      cold resume. (The SW wiring itself is deploy-side, bim-ootb/erp/sw.js — out of this lane; this module
 *      supplies the policy + manifest the SW consumes.)
 *
 *   ERP.Battery.shouldThrottle(state, urgency) -> bool
 *     state   = { level: 0..1, charging: bool }   urgency = 'urgent' | 'background'
 *   ERP.Battery.requestPersist(storage?) -> Promise<{ persisted, supported }>
 *   ERP.Battery.resumeAssets(opts?) -> [urls]     // what the SW should cache for instant resume
 *
 * §FALSIFIER: an URGENT fold throttled at any battery level = a correctness/UX regression → MUST be false.
 */
'use strict';

(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else { root.ERP = root.ERP || {}; root.ERP.Battery = factory(); }
}(typeof self !== 'undefined' ? self : this, function () {

  var LOW = 0.20;   // 20% threshold (§4 P2)

  // Throttle ONLY non-urgent folds, and ONLY when low + discharging.
  function shouldThrottle(state, urgency) {
    if (urgency === 'urgent') return false;                 // never throttle a user commit / paint bootstrap
    if (!state) return false;
    var low = typeof state.level === 'number' && state.level < LOW;
    var discharging = state.charging === false;
    var throttle = !!(low && discharging);
    console.log('§BATTERY throttle=' + (throttle ? 'Y' : 'N') + ' urgency=' + urgency +
      ' level=' + (state.level != null ? Math.round(state.level * 100) + '%' : '?') +
      ' charging=' + (state.charging ? 'Y' : 'N'));
    return throttle;
  }

  // Read the battery once (browser). Injectable getter for the witness.
  async function readBattery(getter) {
    var g = getter || ((typeof navigator !== 'undefined' && navigator.getBattery) ? navigator.getBattery.bind(navigator) : null);
    if (!g) return { level: 1, charging: true, supported: false };
    try { var b = await g(); return { level: b.level, charging: b.charging, supported: true }; }
    catch (e) { return { level: 1, charging: true, supported: false }; }
  }

  async function requestPersist(storage) {
    var s = storage || ((typeof navigator !== 'undefined') ? navigator.storage : null);
    if (!s || typeof s.persist !== 'function') {
      console.log('§BATTERY persist supported=N (no navigator.storage.persist)');
      return { persisted: false, supported: false };
    }
    var ok = await s.persist();
    console.log('§BATTERY persist supported=Y persisted=' + (ok ? 'Y' : 'N'));
    return { persisted: !!ok, supported: true };
  }

  // Assets the SW should precache for an instant cold resume (no genesis, no re-download).
  function resumeAssets(opts) {
    opts = opts || {};
    var base = opts.base || 'erp/';
    return [
      base + (opts.wasm || 'lib/sql-wasm-fts5.wasm'),
      base + (opts.checkpoint || 'checkpoint.json'),    // latest signed checkpoint (per-device)
      base + (opts.seed || 'ad_seed.db')                // T0 dictionary (already precached)
    ];
  }

  return { shouldThrottle: shouldThrottle, readBattery: readBattery, requestPersist: requestPersist, resumeAssets: resumeAssets, LOW: LOW };
}));
