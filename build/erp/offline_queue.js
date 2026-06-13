// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * offline_queue.js — CONSTRAINT_MITIGATION_LANE item 4: bounded offline queue + backoff relay.
 *   Spec: docs/FoldEngineConstraints.md §3 bottleneck 3 / §4 P1 / §6 (offline_queue_mb monitor).
 *   Wraps erp_relay_client.push — does NOT fork the relay verb; only adds the bound + retry policy.
 *
 * The bottleneck (§2 #6 / §3): a high-rate device offline for a long time queues 37.8 MB/day; unbounded if
 * the relay never reconnects → storage-quota exhaustion → write failure. Bound it:
 *   - cap at maxBytes (50 MB) OR maxAgeMs (7 days), whichever first;
 *   - on cap → force a LOCAL signed checkpoint (caller's onCap, e.g. closePeriod), then PURGE ops that are
 *     both FOLDED (into the checkpoint) AND RELAYED — but KEEP every unrelayed op (no data loss);
 *   - relay retry is exponential backoff, capped at backoffCapMs (5 min).
 *
 *   new ERP.OfflineQueue({ maxBytes, maxAgeMs, backoffCapMs, now })
 *     .enqueue(op)        -> { queued, bytes, ageMs, capFired }
 *     .markFolded(ids) · .markRelayed(ids)
 *     .purge()            -> { dropped, kept, keptUnrelayed }   // drops folded&relayed; KEEPS unrelayed
 *     .nextBackoff() · .resetBackoff()
 *     async .flush(relayClient) -> { pushed, relayed, failed, backoffMs }
 *
 * §FALSIFIER: purge() that drops ANY unrelayed op is data loss → MUST never happen (the witness asserts
 *   every unrelayed op survives a cap+purge). A backoff that exceeds backoffCapMs also FAILs.
 */
'use strict';

(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else { root.ERP = root.ERP || {}; root.ERP.OfflineQueue = factory(); }
}(typeof self !== 'undefined' ? self : this, function () {

  var MB = 1024 * 1024;

  function OfflineQueue(opts) {
    opts = opts || {};
    this.maxBytes = opts.maxBytes != null ? opts.maxBytes : 50 * MB;
    this.maxAgeMs = opts.maxAgeMs != null ? opts.maxAgeMs : 7 * 24 * 3600 * 1000;
    this.backoffBaseMs = opts.backoffBaseMs != null ? opts.backoffBaseMs : 1000;
    this.backoffCapMs = opts.backoffCapMs != null ? opts.backoffCapMs : 300000;   // 5 min
    this.now = opts.now || function () { return Date.now(); };
    this.onCap = opts.onCap || null;     // () => force a local checkpoint (caller wires closePeriod)
    this._q = [];                        // [{ op, bytes, ts, folded, relayed, id }]
    this._bytes = 0;
    this._seq = 0;
    this._attempts = 0;
    this.log = opts.log || function () {};
  }

  OfflineQueue.prototype._sizeOf = function (op) {
    try { return JSON.stringify(op).length; } catch (e) { return 64; }
  };
  OfflineQueue.prototype.bytes = function () { return this._bytes; };
  OfflineQueue.prototype.count = function () { return this._q.length; };
  OfflineQueue.prototype.oldestAgeMs = function () {
    if (!this._q.length) return 0;
    return this.now() - this._q[0].ts;
  };

  OfflineQueue.prototype.enqueue = function (op) {
    var b = this._sizeOf(op);
    var rec = { op: op, bytes: b, ts: this.now(), folded: false, relayed: false, id: ++this._seq };
    this._q.push(rec);
    this._bytes += b;
    var capFired = this._maybeCap();
    return { queued: rec.id, bytes: this._bytes, ageMs: this.oldestAgeMs(), capFired: capFired };
  };

  OfflineQueue.prototype._maybeCap = function () {
    var overBytes = this._bytes > this.maxBytes;
    var overAge = this.oldestAgeMs() > this.maxAgeMs;
    if (!overBytes && !overAge) return false;
    console.log('§OFFQ cap fired reason=' + (overBytes ? 'bytes' : 'age') +
      ' bytes=' + this._bytes + '/' + this.maxBytes + ' oldestAgeMs=' + this.oldestAgeMs() + '/' + this.maxAgeMs);
    if (typeof this.onCap === 'function') {
      this.onCap();                       // force a local signed checkpoint → caller marks folded ops
    }
    this.purge();
    return true;
  };

  OfflineQueue.prototype.markFolded = function (ids) {
    var s = {}; (ids || []).forEach(function (i) { s[i] = 1; });
    this._q.forEach(function (r) { if (s[r.id]) r.folded = true; });
  };
  OfflineQueue.prototype.markRelayed = function (ids) {
    var s = {}; (ids || []).forEach(function (i) { s[i] = 1; });
    this._q.forEach(function (r) { if (s[r.id]) r.relayed = true; });
  };
  // also mark by op_uuid (relay dedupes by uuid) — convenience for flush acks.
  OfflineQueue.prototype.markRelayedByUuid = function (uuids) {
    var s = {}; (uuids || []).forEach(function (u) { s[u] = 1; });
    this._q.forEach(function (r) { if (r.op && s[r.op.op_uuid]) r.relayed = true; });
  };

  // Drop ops that are BOTH folded AND relayed; KEEP everything else (esp. any unrelayed op).
  OfflineQueue.prototype.purge = function () {
    var before = this._q.length;
    var kept = [], bytes = 0, dropped = 0, keptUnrelayed = 0;
    this._q.forEach(function (r) {
      if (r.folded && r.relayed) { dropped++; return; }
      kept.push(r); bytes += r.bytes;
      if (!r.relayed) keptUnrelayed++;
    });
    this._q = kept; this._bytes = bytes;
    console.log('§OFFQ purge dropped=' + dropped + ' kept=' + kept.length + ' keptUnrelayed=' + keptUnrelayed +
      ' bytes=' + this._bytes + ' (before=' + before + ')');
    return { dropped: dropped, kept: kept.length, keptUnrelayed: keptUnrelayed };
  };

  OfflineQueue.prototype.nextBackoff = function () {
    var ms = Math.min(this.backoffBaseMs * Math.pow(2, this._attempts), this.backoffCapMs);
    this._attempts++;
    return ms;
  };
  OfflineQueue.prototype.resetBackoff = function () { this._attempts = 0; };

  // flush — push the UNRELAYED ops via the relay client; on success mark relayed + reset backoff,
  // on failure bump backoff (caller schedules the retry). Never drops on failure.
  OfflineQueue.prototype.flush = async function (relayClient) {
    var pending = this._q.filter(function (r) { return !r.relayed; });
    if (!pending.length) { this.resetBackoff(); return { pushed: 0, relayed: 0, failed: false, backoffMs: 0 }; }
    var ops = pending.map(function (r) { return r.op; });
    try {
      await relayClient.push(ops);
      var ids = pending.map(function (r) { return r.id; });
      this.markRelayed(ids);
      this.resetBackoff();
      console.log('§OFFQ flush pushed=' + ops.length + ' relayed=' + ops.length + ' backoffReset');
      return { pushed: ops.length, relayed: ops.length, failed: false, backoffMs: 0 };
    } catch (e) {
      var back = this.nextBackoff();
      console.log('§OFFQ flush FAILED pushed=0 keepingUnrelayed=' + pending.length + ' backoffMs=' + back + ' (' + e.message + ')');
      return { pushed: 0, relayed: 0, failed: true, backoffMs: back };
    }
  };

  return OfflineQueue;
}));
