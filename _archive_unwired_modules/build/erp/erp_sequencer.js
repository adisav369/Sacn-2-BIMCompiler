// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// erp_sequencer.js — Implementing ERP.md §0.20 (the async server domain) — Witness: W-REBASE
// The "Dumb Facilitator" — a sandbox model of the server-side Asynchronous Sequencing Relay.
// It assigns a CANONICAL TOTAL ORDER to ops and nothing else: it appends, dedupes by op_uuid, and
// hands back the ordered log. It runs ZERO business logic (no validation, no folding, no rule eval) —
// authority over EFFECTS stays in the deterministic kernel that replays this order on every device
// (docs/DistributedERP.md §0; LocalFirstPriorArt.md §6 — the SQLSync rebase borrow).
//
// This is the in-memory stub (no network/persistence) used by the W-REBASE witness. The real relay
// is the same contract over HTTP+durable append. Two properties Gemini's OpLogIngestServlet missed and
// this stub gets right: (1) IDEMPOTENT ingest keyed on op_uuid (re-push is a no-op — no double-apply),
// (2) op_uuid is the cross-device identity, NOT the local rowid (which collides 1,2,3… per device).
(function () {
  'use strict';

  function _clone(o) {
    var c = {}; for (var k in o) { if (Object.prototype.hasOwnProperty.call(o, k)) c[k] = o[k]; } return c;
  }

  // createSequencer — a fresh relay. The canonical log is append-only; `seq` is the total order.
  function createSequencer() {
    var log = [];                 // canonical ordered ops, each tagged with a monotonic `seq`
    var seen = Object.create(null); // op_uuid → true (idempotency set)

    return {
      // push — ingest an array of ops in the caller's order. Each op needs an op_uuid (the cross-device
      // identity). Unseen ops are appended with the next `seq`; already-seen op_uuids are skipped.
      // Returns {accepted, skipped, head}. DETERMINISTIC: no Date.now/Math.random (§7).
      push: function (ops) {
        var accepted = 0, skipped = 0;
        (ops || []).forEach(function (op) {
          if (!op || !op.op_uuid) { skipped++; return; }   // identity is mandatory at the seam
          if (seen[op.op_uuid]) { skipped++; return; }      // idempotent — never double-sequence
          seen[op.op_uuid] = true;
          var rec = _clone(op);
          rec.seq = log.length + 1;
          log.push(rec);
          accepted++;
        });
        console.log('§SEQ push accepted=' + accepted + ' skipped=' + skipped + ' head=' + log.length);
        return { accepted: accepted, skipped: skipped, head: log.length };
      },

      // snapshot — the authoritative ordered log (optionally only ops after `afterSeq`). Copies out so
      // callers can't mutate the canonical store.
      snapshot: function (afterSeq) {
        var from = afterSeq ? afterSeq : 0;
        return log.filter(function (o) { return o.seq > from; }).map(_clone);
      },

      head: function () { return log.length; },
      has:  function (uuid) { return !!seen[uuid]; }
    };
  }

  var _api = { createSequencer: createSequencer };
  if (typeof module !== 'undefined' && module.exports) { module.exports = _api; }
  if (typeof window !== 'undefined') { window.ErpSequencer = _api; }
  if (typeof console !== 'undefined') { console.log('§SEQ_LOADED v1 (dumb facilitator — idempotent by op_uuid)'); }
})();
