// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * erp_runtime.js — the HOST LAYER that makes the abstract engine DATA-DRIVEN.
 *   Spec: docs/ERP.md §0.14–§0.15 (the compile→engine wire), prompts/ERP_RUNTIME_ENGINE.md T1.
 *
 * The debt this pays: erp_engine.js is pure, but the matcher's ordering/access and the
 * fan-out flags were still passed as JS LITERALS in the POC harness. `loadCell` sources
 * EVERY one of those opts from a rule record in erp_rules.db (Step 1 output) — so a cell's
 * behaviour comes from DATA the SystemAdmin edits, never code (§0.4–§0.5).
 *
 *   ordering (FIFO/LIFO/by-amount) ← MATCHPOLICY:<cell>   (editable, default FIFO logged)
 *   fan-out flags                  ← DOCPOLICY:<docTypeId> (the C_DocType decision table)
 *   access scope (allowOrgs+gate)  ← ACCESS:<roleId>       (ad_role+orgaccess+doc_action_access)
 *   guards                         ← Validation rules bound to the base table
 *
 * Binding-agnostic, same as the engine: the host injects `ruleQuery(sql) -> rows[]`, so the
 * SAME loadCell runs under sql.js (browser) AND better-sqlite3 (node tests). No Date.now /
 * Math.random / DOM / network — deterministic, replay/dry-run safe.
 */

// loadCell(ruleQuery, baseTable, action) -> assembled engine inputs, ALL from data.
//   ruleQuery: (sql) -> rows[]  over erp_rules.db (rules table).
function loadCell(ruleQuery, baseTable, action) {
  var cell = baseTable + ':' + action;
  var trace = [];

  // ── ordering policy ← editable MATCHPOLICY rule record (§0.5). default FIFO, LOGGED. ──
  var mp = ruleQuery("SELECT body FROM rules WHERE rule_key='MATCHPOLICY:" + cell + "' AND event_type='MatchPolicy'");
  var ordering, orderingSrc;
  if (mp.length) { ordering = JSON.parse(mp[0].body); orderingSrc = 'erp_rules'; }
  else { ordering = { order: 'FIFO', key: 'm_product_id', tol: 1e-4 }; orderingSrc = 'default-FIFO(no-rule)'; }
  trace.push('policy=' + ordering.order + '(src=' + orderingSrc + ')');

  // ── fan-out flags ← DOCPOLICY:<docTypeId> (resolved per-document, §0.5 decision table) ──
  function getPolicy(docTypeId) {
    var r = ruleQuery("SELECT body FROM rules WHERE rule_key='DOCPOLICY:" + docTypeId + "' AND event_type='DocPolicy'");
    return r.length ? JSON.parse(r[0].body) : {};
  }

  // ── access scope ← ACCESS:<roleId> (§0.8: narrows partition + gates may-run) ──
  // allowOrgs: a Set of org ids the role may see (the matcher partition narrowing), or
  //   null = ALL orgs (no narrowing) for an isaccessallorgs role. mayRun(docTypeId,action):
  //   allOrgs roles may fire anything; else the (docType,action) must be granted.
  function access(roleId) {
    var r = ruleQuery("SELECT body FROM rules WHERE rule_key='ACCESS:" + roleId + "' AND event_type='Access'");
    if (!r.length) return { found: false, name: null, allOrgs: false, allowOrgs: new Set(), mayRun: function () { return false; } };
    var a = JSON.parse(r[0].body);
    var orgSet = a.allOrgs ? null : new Set(a.allowOrgs || []);
    return {
      found: true, name: a.name, allOrgs: !!a.allOrgs, allowOrgs: orgSet,
      orgCount: a.allOrgs ? Infinity : (a.allowOrgs || []).length,
      mayRun: function (docTypeId, act) {
        if (a.allOrgs) return true;
        var acts = (a.actions && a.actions[docTypeId]) || [];
        return acts.indexOf(act) >= 0;
      }
    };
  }

  // ── guards ← Validation rules bound to the base table (predicates on the data edge) ──
  var guards = ruleQuery("SELECT rule_key, form, body FROM rules WHERE event_type='Validation' AND binding LIKE '" + baseTable + ".%'");

  return {
    cell: cell, baseTable: baseTable, action: action,
    ordering: ordering, orderingSrc: orderingSrc,
    getPolicy: getPolicy, access: access, guards: guards,
    trace: trace.join(' ')
  };
}

// matchOptsFromCell — turn a loaded cell + a role into the exact opts E.match() wants.
// idL/idR/qtyL/qtyR/partition/orgOf are STRUCTURE (which columns hold the identities) — the
// caller supplies them; ordering + allowOrgs are POLICY/ACCESS and come from the cell (data).
function matchOptsFromCell(cell, roleId, structure) {
  var acc = cell.access(roleId);
  var opts = Object.assign({}, structure, {
    order: cell.ordering.order,
    tol: cell.ordering.tol,
    allowOrgs: acc.allowOrgs            // Set | null(all) — the access-scoped partition
  });
  if (cell.ordering.key) opts.keyOf = function (r) { return r[cell.ordering.key]; };
  return { opts: opts, access: acc };
}

module.exports = { loadCell: loadCell, matchOptsFromCell: matchOptsFromCell };
