// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * genesis_worker.js — off-main-thread genesis fold (CONSTRAINT_MITIGATION_LANE item 1, the ~40-LOC shim).
 *   Spec: docs/FoldEngineConstraints.md §4 P0 ("run in a Web Worker, never block the main thread").
 *   NON-INVENT / no verb fork: the worker folds with the SAME erp_period_close.foldBalances the main
 *   thread uses; it only moves the CPU off the main thread.
 *
 * Message contract (identical for browser Web Worker + node worker_threads):
 *   in : { ops: [...], opening: {...} }
 *   out: { bal: {...}, count: N }
 *
 * Browser: ship this as a Worker entry (replace the worker_threads guard with onmessage/postMessage).
 * Node (the POC witness): runs under worker_threads.
 */
'use strict';

var foldBalances;
try { foldBalances = require('./erp_period_close.js').foldBalances; }
catch (e) { /* browser: erp_period_close loaded on the global ERP seam */ }

function fold(ops, opening) {
  if (foldBalances) return foldBalances(ops || [], opening || {}).bal;
  // browser fallback: pull the verb off the global seam (never re-implemented here)
  return (self.ERP.PeriodClose.foldBalances(ops || [], opening || {})).bal;
}

// ── node worker_threads entry (the witness path) ─────────────────────────────
try {
  var wt = require('worker_threads');
  if (!wt.isMainThread && wt.parentPort) {
    wt.parentPort.on('message', function (msg) {
      var bal = fold(msg.ops, msg.opening);
      wt.parentPort.postMessage({ bal: bal, count: (msg.ops || []).length });
    });
  }
} catch (e) { /* not in node worker context (browser) */ }

// ── browser Web Worker entry (same contract) ─────────────────────────────────
if (typeof self !== 'undefined' && typeof self.onmessage !== 'undefined' && typeof require === 'undefined') {
  self.onmessage = function (e) {
    var bal = fold(e.data.ops, e.data.opening);
    self.postMessage({ bal: bal, count: (e.data.ops || []).length });
  };
}

if (typeof module === 'object' && module.exports) module.exports = { fold: fold };
