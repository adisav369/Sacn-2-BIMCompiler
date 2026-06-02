// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * post_resolver.js — the §3.5.1 account resolver (HOST GLUE for the POST verb).
 *   Implements PLUGIN_ARCHITECTURE.md §13.5: a manifest charge token `{Master.Role}` resolves to a
 *   REAL account, keyed by (master id, c_acctschema_id), reading ONLY real columns from ad_seed.db.
 *
 * NON-INVENT: every account is READ from a master column; the resolver never picks an account. An
 *   absent (master, acctschema) pair falls back to c_acctschema_default; a still-absent value is
 *   reported `absent` (caller decides), NEVER synthesized.
 *
 * The resolved value IS the master column's verbatim contents (a C_ValidCombination id, e.g. 234 —
 *   mirroring gl_journalline). For traceability we ALSO resolve the underlying C_ElementValue
 *   (natural account) via c_validcombination.account_id, so the witness can cite the real account.
 */

// token → {table, col, keyCol, [via]} — the ONLY place a token maps to a real column (§3.5.1 grammar).
var TOKENS = {
  '{BPartner.Receivable}': { table: 'c_bp_customer_acct',      col: 'c_receivable_acct', keyCol: 'c_bpartner_id' },
  '{Product.Revenue}':     { table: 'm_product_category_acct', col: 'p_revenue_acct',    keyCol: 'm_product_category_id', via: 'product->category' },
  '{Tax.Due}':             { table: 'c_tax_acct',               col: 't_due_acct',        keyCol: 'c_tax_id' }
};

function one(db, sql, params) { var r = db.prepare(sql).get(params || {}); return r || null; }

// map a master row's resolved value (a C_ValidCombination id) to its natural C_ElementValue, for the log.
function elementOf(db, validCombinationId) {
  if (validCombinationId == null) return null;
  var r = one(db,
    'SELECT ev.c_elementvalue_id AS id, ev.value AS value, ev.name AS name, ev.accounttype AS accounttype ' +
    'FROM c_validcombination vc JOIN c_elementvalue ev ON ev.c_elementvalue_id=vc.account_id ' +
    'WHERE vc.c_validcombination_id=@id', { id: validCombinationId });
  return r; // null if the value is not a validcombination (caller logs as-is)
}

/**
 * resolve(db, token, masterId, acctschema) -> {token, master_id, acct, source_col, element, fallback}
 *   acct      — the real column value (the account to post to), or null if absent.
 *   source_col— "table.col" the value came from (provenance).
 *   element   — the underlying C_ElementValue row {id,value,name,accounttype} (traceability), or null.
 *   fallback  — true if the exact (master,acctschema) pair was absent and c_acctschema_default was used.
 */
function resolve(db, token, masterId, acctschema) {
  var spec = TOKENS[token];
  if (!spec) throw new Error('post_resolver: unknown token ' + token);

  // {Product.Revenue}: the manifest binds a PRODUCT; hop product -> category before the acct lookup.
  var lookupId = masterId, keyCol = spec.keyCol;
  if (spec.via === 'product->category') {
    var p = one(db, 'SELECT m_product_category_id AS cat FROM m_product WHERE m_product_id=@id', { id: masterId });
    if (!p) return { token: token, master_id: masterId, acct: null, source_col: spec.table + '.' + spec.col, element: null, fallback: false, absent: 'product ' + masterId };
    lookupId = p.cat;
  }

  var sql = 'SELECT ' + spec.col + ' AS acct FROM ' + spec.table + ' WHERE ' + keyCol + '=@k AND c_acctschema_id=@s';
  var row = one(db, sql, { k: lookupId, s: acctschema });
  var fallback = false;

  // §13.5 fallback: exact (master, acctschema) pair absent -> the schema's default acctschema row.
  if (!row) {
    var def = one(db, 'SELECT c_acctschema_id AS s FROM c_acctschema_default LIMIT 1');
    if (def && def.s !== acctschema) {
      row = one(db, sql, { k: lookupId, s: def.s });
      fallback = !!row;
    }
  }

  var acct = row ? row.acct : null;
  return {
    token: token, master_id: masterId, lookup_id: lookupId,
    acct: acct, source_col: spec.table + '.' + spec.col,
    element: elementOf(db, acct), fallback: fallback,
    absent: acct == null ? (spec.table + '.' + spec.col + ' [' + keyCol + '=' + lookupId + ', schema=' + acctschema + ']') : null
  };
}

module.exports = { resolve: resolve, elementOf: elementOf, TOKENS: TOKENS };
