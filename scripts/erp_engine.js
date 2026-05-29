/**
 * erp_engine.js — the §0.10 abstract engine, EXTRACTED into one module.
 *   Spec: docs/ERP.md §0.9-0.10 (dispatch-by-form), §0.5 (decision tables),
 *   §18 (edges) + this session's POC findings (settlement = partition+polarity+order).
 *
 * Separation contract (this is the fix for the "engine smeared across probes" debt):
 *   - PURE logic only. No DB binding imported. The host injects `query(sql) -> rows[]`
 *     so the SAME core runs under sql.js (browser) AND better-sqlite3 (node tests).
 *   - No Date.now / Math.random / DOM / network — deterministic (replay/dry-run safe).
 *
 * The engine knows TWO things, per the unified model:
 *   GUARDS  — a predicate over an edge (validation / access / state-legality) -> bool.
 *   GENERATE— a predicate that produces edges (the matcher, derivation verbs) -> ops[].
 * Everything dispatches by `form`.
 */
'use strict';

// ── Gap closers (the two breakages the probe found) ──────────────────────────
// @ctx@ substitution: iDempiere injects record/global context into rule SQL.
function resolveCtx(body, ctx) {
  var miss = [];
  var sql = String(body).replace(/@(#?\w+)@/g, function (m, k) {
    if (ctx[k] != null) { var v = ctx[k]; return typeof v === 'number' ? String(v) : "'" + String(v).replace(/'/g, "''") + "'"; }
    miss.push(k); return 'NULL';
  });
  return { sql: sql, miss: miss };
}
// PG -> SQLite dialect (sql.js is SQLite). Extend as new PG-isms surface.
function dialectShim(sql) {
  return String(sql)
    .replace(/\bleast\s*\(/gi, 'min(')
    .replace(/\bgreatest\s*\(/gi, 'max(')
    .replace(/::[a-z_]+/gi, '');
}

// ── GUARD: evaluate a predicate rule (form=sql) over an edge -> bool ──────────
function evalGuard(query, rule, ctx) {
  if (rule.form !== 'sql') return { ok: true, skipped: 'non-sql guard form=' + rule.form };
  var r = resolveCtx(rule.body, ctx);
  if (r.miss.length) return { ok: false, reason: 'unresolved-ctx', miss: r.miss };
  try {
    var rows = query(dialectShim(r.sql));
    return { ok: true, rows: rows };
  } catch (e) { return { ok: false, reason: 'sql-error', error: e.message }; }
}

// ── GENERATE: the GENERIC matcher — the whole Detail⋈Detail class ────────────
// One function for 3-way-match / allocation / costing / bank-rec. Edges are paired
// within a PARTITION (the trading-partner account, optionally narrowed by role/org
// access), on a KEY, with qty agreeing within TOLERANCE, disambiguated by an ORDERING
// POLICY (FIFO/LIFO/…), greedy first-fit. Returns [[idL,idR],…] — the settlement edges.
//
// opts = {
//   idL, idR        : field names for the line identities to pair
//   keyOf(row)      : the match key (default row.m_product_id)
//   qtyL, qtyR      : qty field names on left/right
//   tol             : qty tolerance (default 1e-4)
//   partition(row)  : the settlement partition key (e.g. row.bp)         REQUIRED
//   orgOf(row)      : the row's org (for access scoping)                 optional
//   allowOrgs       : Set of orgs the role may see; null/absent = all    optional
//   order           : 'FIFO'|'LIFO' over `dateOf`, or a comparator(a,b)  optional
//   dateOf(row)     : the date used by FIFO/LIFO                         optional
// }
function match(leftRows, rightRows, opts) {
  var keyOf = opts.keyOf || function (r) { return r.m_product_id; };
  var orgOf = opts.orgOf || function () { return null; };
  var dateOf = opts.dateOf || function () { return 0; };
  var tol = opts.tol == null ? 1e-4 : opts.tol;
  var allow = opts.allowOrgs || null;
  function visible(r) { return !allow || allow.has(orgOf(r)); }

  // comparator for disambiguation when >1 candidate matches. Type-agnostic compare so
  // ISO date STRINGS (how dates arrive from sql.js) order correctly, not just numbers.
  function asc(a, b) { var x = dateOf(a), y = dateOf(b); return x < y ? -1 : x > y ? 1 : 0; }
  var cmp = (typeof opts.order === 'function') ? opts.order
    : opts.order === 'LIFO' ? function (a, b) { return -asc(a, b); }
      : opts.order === 'FIFO' ? asc
        : null;

  // group visible right rows by partition.
  var byPart = {};
  rightRows.forEach(function (R, i) {
    if (!visible(R)) return;
    R.__k = i; var p = opts.partition(R);
    (byPart[p] = byPart[p] || []).push(R);
  });
  var used = {}, pairs = [];
  // process left rows in policy order too (stable, deterministic).
  var lefts = leftRows.filter(visible);
  if (cmp) lefts = lefts.slice().sort(cmp);
  lefts.forEach(function (L) {
    var cands = (byPart[opts.partition(L)] || []).filter(function (R) {
      return !used[R.__k] && keyOf(L) === keyOf(R) && Math.abs(L[opts.qtyL] - R[opts.qtyR]) <= tol;
    });
    if (!cands.length) return;
    if (cmp && cands.length > 1) cands = cands.slice().sort(cmp);
    var R = cands[0];
    used[R.__k] = 1;
    pairs.push([L[opts.idL], R[opts.idR]]);
  });
  return pairs;
}

// ── GENERATE: derivation verbs (a BOM derivation = order→child document) ─────
// Verbs return ops[]; the kernel applies + commitOps them (handlers never write).
function createShipment(order, lines) {
  var ops = [{ op_type: 'CREATE_DOCUMENT', table: 'M_InOut', source_id: order.c_order_id, movementtype: order.issotrx === 'Y' ? 'C-' : 'V+' }];
  lines.forEach(function (l) { ops.push({ op_type: 'CREATE_LINE', table: 'M_InOutLine', source_line_id: l.c_orderline_id, m_product_id: l.m_product_id, movementqty: l.qtyordered }); });
  return ops;
}
function createInvoice(order, lines) {
  var ops = [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: order.c_order_id, issotrx: order.issotrx }];
  lines.forEach(function (l) { ops.push({ op_type: 'CREATE_LINE', table: 'C_InvoiceLine', source_line_id: l.c_orderline_id, m_product_id: l.m_product_id, qtyinvoiced: l.qtyordered }); });
  return ops;
}
var VERBS = { createShipment: createShipment, createInvoice: createInvoice };

// ── The cell handler: decision-table over policy flags -> verb ops ───────────
// completeOrder = state op + (flag-gated) verb fan-out. The flags are DATA
// (erp_rules DOCPOLICY), the verbs are the small registry above.
function completeOrder(order, lines, policy) {
  var ops = [{ op_type: 'SET_STATUS', table: 'C_Order', id: order.c_order_id, doc_status: 'CO' }];
  if (policy.isautogenerateinout === 'Y') ops = ops.concat(VERBS.createShipment(order, lines));
  if (policy.isautogenerateinvoice === 'Y') ops = ops.concat(VERBS.createInvoice(order, lines));
  return ops;
}

module.exports = {
  resolveCtx: resolveCtx, dialectShim: dialectShim, evalGuard: evalGuard,
  match: match, VERBS: VERBS, completeOrder: completeOrder
};
