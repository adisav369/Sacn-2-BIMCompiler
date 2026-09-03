// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * bootstrap_path.js — CONSTRAINT_MITIGATION_LANE item 1: checkpoint-first bootstrap + bootstrap_path metric.
 *   Spec: docs/FoldEngineConstraints.md §4 (P0 genesis-fold) + §7.1  ·  Lane card: prompts/CONSTRAINT_MITIGATION_LANE.md
 *   Consumes the engine through the seam — NEVER forks a verb: checkpoint fold = erp_period_close.bootstrapFromCheckpoint;
 *   genesis fold = the SAME erp_period_close.foldBalances, run in a Worker. kernel_ops for replay/verify.
 *
 * The cliff (FoldEngineConstraints §2 #2): a 100M-op GENESIS re-fold = 25 s on mobile and BLOCKS the main
 * thread → 25 s white screen. The checkpoint bootstrap is ~9 ms FLAT regardless of N (folds open-period only).
 * Mitigation = make checkpoint-first the ENFORCED default + emit a bootstrap_path metric + route any genesis
 * to a Worker (never the main thread on mobile).
 *
 *   ERP.Bootstrap.run(db, {
 *     kernel,            // build/erp/kernel_ops.js  (replayOps)
 *     periodClose,       // build/erp/erp_period_close.js  (bootstrapFromCheckpoint, foldBalances, CKPT_TYPE)
 *     mobile,            // boolean — mobile-emulation gate (UA/throttle decided by caller)
 *     recover,           // boolean — explicit genesis recovery (operator action); else checkpoint-first
 *     runInWorker,       // async (ops, opening) => foldedState  — REQUIRED for genesis on mobile
 *     now                // () => ms  (injectable clock for deterministic witness)
 *   }) -> Promise<{ path, ms, postOps, totalOps, offMainThread, ok, refused, state }>
 *
 * §FALSIFIER: mobile + missing checkpoint + no worker → MUST refuse (offMainThread=N, ok=false, refused=true) —
 *   it must NEVER run genesis inline on the main thread (the 25 s freeze). A checkpoint-path bootstrap whose
 *   ms scales with totalOps (not postOps) also FAILs the "flat in N" claim.
 */
'use strict';

(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else { root.ERP = root.ERP || {}; root.ERP.Bootstrap = factory(); }
}(typeof self !== 'undefined' ? self : this, function () {

  function _latestCheckpoint(db, ckptType) {
    var r = db.exec("SELECT id FROM kernel_ops WHERE op_type='" + ckptType + "' AND undone=0 ORDER BY id DESC LIMIT 1");
    return (r.length && r[0].values.length) ? { id: r[0].values[0][0] } : null;
  }

  async function run(db, opts) {
    opts = opts || {};
    var kernel = opts.kernel, pc = opts.periodClose;
    if (!kernel || !pc) throw new Error('Bootstrap.run: kernel + periodClose required');
    var now = opts.now || function () { return Date.now(); };
    var mobile = !!opts.mobile, recover = !!opts.recover;
    var ckptType = pc.CKPT_TYPE || 'PERIOD_CLOSE';

    var all = kernel.replayOps(db);                       // O(n) scan (cheap) — the FOLD is the cost, not this
    var totalOps = all.length;
    var ck = _latestCheckpoint(db, ckptType);

    // ── checkpoint-first (the enforced default) ──────────────────────────────────────────────────
    if (ck && !recover) {
      var t0 = now();
      var bf = pc.bootstrapFromCheckpoint(db, kernel);    // folds POST-checkpoint ops only — flat in N
      var ms = now() - t0;
      console.log('§BOOTSTRAP path=checkpoint ms=' + ms + ' postOps=' + bf.postCount +
        ' totalOps=' + totalOps + ' offMainThread=N (flat: folds open-period only)');
      return { path: 'checkpoint', ms: ms, postOps: bf.postCount, totalOps: totalOps,
        offMainThread: false, ok: true, refused: false, state: bf.current };
    }

    // ── genesis — explicit recovery OR no checkpoint. NEVER on the main thread on mobile. ─────────
    if (mobile && typeof opts.runInWorker !== 'function') {
      // Misconfiguration on mobile → REFUSE rather than freeze the main thread for the genesis fold.
      console.log('§BOOTSTRAP path=genesis ms=0 postOps=' + totalOps + ' totalOps=' + totalOps +
        ' offMainThread=N REFUSED=Y (mobile genesis needs a worker — refused to block main thread)');
      return { path: 'genesis', ms: 0, postOps: totalOps, totalOps: totalOps,
        offMainThread: false, ok: false, refused: true, state: null };
    }

    var g0 = now();
    var state;
    if (typeof opts.runInWorker === 'function') {
      state = await opts.runInWorker(all, {});            // off the main thread (Web Worker / worker_threads)
      var gms = now() - g0;
      console.log('§BOOTSTRAP path=genesis ms=' + gms + ' postOps=' + totalOps + ' totalOps=' + totalOps +
        ' offMainThread=Y (worker)' + (recover ? ' recover=Y' : ''));
      return { path: 'genesis', ms: gms, postOps: totalOps, totalOps: totalOps,
        offMainThread: true, ok: true, refused: false, state: state };
    }
    // desktop with no worker → inline genesis is tolerated (desktop 2.5 s ceiling, not the mobile cliff)
    state = pc.foldBalances(all, {}).bal;
    var dms = now() - g0;
    console.log('§BOOTSTRAP path=genesis ms=' + dms + ' postOps=' + totalOps + ' totalOps=' + totalOps +
      ' offMainThread=N (desktop inline, no worker)');
    return { path: 'genesis', ms: dms, postOps: totalOps, totalOps: totalOps,
      offMainThread: false, ok: true, refused: false, state: state };
  }

  return { run: run };
}));
