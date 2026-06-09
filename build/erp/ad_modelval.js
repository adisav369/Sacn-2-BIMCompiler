// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ad_modelval.js — Model-validator timing-hook engine (W-MODELVAL). The "Model Validators" leg of the
// coverage matrix: dispatch registered validators around a record/document action at named TIMINGS
// (BEFORE/AFTER × NEW/SAVE/DELETE + the doc-timings PREPARE/COMPLETE/VOID). Faithful port of iDempiere's
// ModelValidationEngine.fireModelChange / fireDocValidate: a validator that returns a non-null error string
// ABORTS the operation. Self-contained, no kernel dep (mirrors ad_process.js / ad_callout.js shape).
//
// SEAM (docs/ERP_BACKEND_SEPARATION.md §2 A-4): this engine GATES an action (may BLOCK before-COMPLETE); it
// does NOT post. The GL derivation (A-1) is DOWNSTREAM and ignorant of why a doc was admitted — fireHooks
// runs FIRST on the doc-action path; only if it returns ok does derive(A-1)→fold(layer-3) proceed. A hook
// reads the record/ctx and returns a verdict; it never derives a posting or routes a workflow.
//
// EXTRACT, DON'T INVENT: the 3 registered classes come from ad_full.db (ad_modelvalidator) — their Java
// bodies are named-deferred (no port); the 2–3 hooks here are faithful ports of documented M*.beforeSave /
// completeIt invariants (qty>0; a doc must have lines), each computed from real record/db columns. An
// un-hooked (table,timing) is an explicit no-op result, never a hidden pass. Determinism: pure checks +
// read-only lookups, no Date.now/Math.random. Implementing ERP_COVERAGE_MATRIX.md §AD_ModelValidator /
// §beforeSave-afterSave (ranked GAP #9) — Witness: W-MODELVAL
(function (global) {
  'use strict';

  // iDempiere timing constants we model (ModelValidator TYPE_* + docValidate TIMING_*).
  var TIMINGS = ['BEFORE_NEW', 'AFTER_NEW', 'BEFORE_SAVE', 'AFTER_SAVE', 'BEFORE_DELETE', 'AFTER_DELETE',
                 'BEFORE_PREPARE', 'BEFORE_COMPLETE', 'AFTER_COMPLETE', 'BEFORE_VOID', 'AFTER_VOID'];

  // REGISTRY[table][timing] = [ {name, fn} ]  — fn(ctx, info) -> null | "error string" (iDempiere semantics).
  var REGISTRY = {};
  function registerValidator(table, timing, name, fn) {
    var t = table.toLowerCase();
    (REGISTRY[t] = REGISTRY[t] || {});
    (REGISTRY[t][timing] = REGISTRY[t][timing] || []).push({ name: name, fn: fn });
  }
  function registeredCount() {
    var n = 0, t, ti; for (t in REGISTRY) for (ti in REGISTRY[t]) n += REGISTRY[t][ti].length; return n;
  }

  // readValidators(db) — the AD source: the registered ModelValidator classes (Java bodies named-deferred).
  function readValidators(db) {
    return db.prepare('SELECT ad_modelvalidator_id,name,modelvalidationclass,entitytype FROM ad_modelvalidator ORDER BY ad_modelvalidator_id').all();
  }

  // installDefaultHooks — faithful ports of real M*.beforeSave / completeIt invariants.
  function installDefaultHooks() {
    // MOrderLine.beforeSave: a line must carry a positive quantity (TYPE_BEFORE_NEW/CHANGE).
    function qtyPositive(ctx, info) {
      var r = info.record || {};
      var q = Number(r.QtyOrdered != null ? r.QtyOrdered : r.qtyordered);
      if (!(q > 0)) return 'QtyOrdered must be > 0 (got ' + q + ')';
      return null;
    }
    registerValidator('C_OrderLine', 'BEFORE_SAVE', 'MOrderLine.qtyPositive', qtyPositive);
    registerValidator('C_InvoiceLine', 'BEFORE_SAVE', 'MInvoiceLine.qtyPositive', function (ctx, info) {
      var r = info.record || {}; var q = Number(r.QtyInvoiced != null ? r.QtyInvoiced : r.qtyinvoiced);
      return q > 0 ? null : 'QtyInvoiced must be > 0 (got ' + q + ')';
    });

    // MOrder.completeIt precondition: the document must have at least one line (docValidate BEFORE_COMPLETE).
    registerValidator('C_Order', 'BEFORE_COMPLETE', 'MOrder.hasLines', function (ctx, info) {
      var n = ctx.lineCount ? ctx.lineCount(info) : (info.lineCount || 0);
      return n > 0 ? null : 'Cannot complete an order with no lines';
    });
    // a second BEFORE_COMPLETE hook to prove ordered multi-hook dispatch (grand total must be non-negative).
    registerValidator('C_Order', 'BEFORE_COMPLETE', 'MOrder.totalNonNegative', function (ctx, info) {
      var r = info.record || {}; var g = Number(r.GrandTotal != null ? r.GrandTotal : r.grandtotal);
      return g >= 0 ? null : 'GrandTotal must be >= 0 (got ' + g + ')';
    });
  }

  // fireHooks(timing, info, ctx) — run every validator registered for (info.table, timing) IN ORDER; the
  // FIRST non-null error aborts (the iDempiere fireModelChange/fireDocValidate semantics). Returns
  // { timing, table, fired, ok, blocked?, error? }.
  function fireHooks(timing, info, ctx) {
    ctx = ctx || {};
    var t = (info.table || '').toLowerCase();
    var hooks = (REGISTRY[t] && REGISTRY[t][timing]) || [];
    for (var i = 0; i < hooks.length; i++) {
      var err;
      try { err = hooks[i].fn(ctx, info); }
      catch (e) { err = 'hook threw: ' + (e && e.message); }
      if (err) return { timing: timing, table: info.table, fired: i + 1, ok: false, blocked: hooks[i].name, error: err };
    }
    return { timing: timing, table: info.table, fired: hooks.length, ok: true };
  }

  // hooksFor(table) — which timings carry a hook (coverage introspection).
  function hooksFor(table) {
    var t = (table || '').toLowerCase(), r = REGISTRY[t] || {}, out = {};
    Object.keys(r).forEach(function (ti) { out[ti] = r[ti].map(function (h) { return h.name; }); });
    return out;
  }

  var API = {
    TIMINGS: TIMINGS, REGISTRY: REGISTRY, registerValidator: registerValidator, registeredCount: registeredCount,
    readValidators: readValidators, installDefaultHooks: installDefaultHooks, fireHooks: fireHooks, hooksFor: hooksFor
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;   // node witness
  if (typeof window !== 'undefined') window.AdModelVal = API;                   // browser
  else if (global) global.AdModelVal = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
