// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ad_tabquery.js — AD_Tab query-scoping engine (W-TABQUERY). The "AD_Tab WhereClause / OrderByClause" legs of
// the coverage matrix: read AD_Tab.WhereClause / AD_Tab.OrderByClause and APPLY them as the tab's row filter +
// sort (exactly how iDempiere's GridTab builds its query). Self-contained, no kernel dep.
//
// SEAM (docs/ERP_BACKEND_SEPARATION.md §1): tab-query SCOPES the visible row set (which rows + what order).
// It is a read-only query modifier — it neither validates a field value (ad_valrule), derives one (ad_callout),
// nor gates an action (ad_modelval). Distinct concern; shares only the generic SQL-where-apply mechanism, which
// is INLINED here so the module stays standalone (the established ad_*.js pattern).
//
// EXTRACT, DON'T INVENT: the where-clause + order-by ARE AD data (ad_tab.whereclause / .orderbyclause). They
// run verbatim (after @token@ substitution) against the real db; an unsafe clause (;/comment) or unresolved
// token is reported, never silently passed. Determinism: read-only query, no Date/random. Implementing
// ERP_COVERAGE_MATRIX.md §AD_Tab·WhereClause / §AD_Tab·OrderByClause (ranked GAP #11) — Witness: W-TABQUERY
(function (global) {
  'use strict';

  function unsafe(sql) { return /;|--|\/\*|\bunion\b/i.test(String(sql || '')); }
  function quote(v) {
    if (v == null) return 'NULL';
    if (typeof v === 'number' || /^-?\d+(\.\d+)?$/.test(String(v))) return String(v);
    return "'" + String(v).replace(/'/g, "''") + "'";
  }
  // substitute @Field@/@#Global@ tokens; returns { sql, unresolved:[] }.
  function substitute(clause, ctx) {
    ctx = ctx || {}; var unresolved = [];
    var sql = String(clause).replace(/@([#$]?[A-Za-z0-9_]+)@/g, function (whole, name) {
      var key = name.replace(/^[#$]/, '');
      if (Object.prototype.hasOwnProperty.call(ctx, name)) return quote(ctx[name]);
      if (Object.prototype.hasOwnProperty.call(ctx, key)) return quote(ctx[key]);
      unresolved.push(name); return whole;
    });
    return { sql: sql, unresolved: unresolved };
  }

  function readTab(db, tabId) {
    var t = db.prepare(
      'SELECT t.ad_tab_id,t.name,tb.tablename,t.whereclause,t.orderbyclause ' +
      'FROM ad_tab t JOIN ad_table tb ON tb.ad_table_id=t.ad_table_id WHERE t.ad_tab_id=?').get(tabId);
    if (!t) throw new Error('no ad_tab ' + tabId);
    return { tabId: t.ad_tab_id, name: t.name, table: t.tablename.toLowerCase(),
             whereClause: t.whereclause || '', orderBy: t.orderbyclause || '' };
  }

  // applyWhere(db, tabId, opts) — apply AD_Tab.WhereClause as the row filter. opts.ctx for tokens,
  // opts.candidatePk {col,id} for a membership test (is this row in the tab's scope?).
  function applyWhere(db, tabId, opts) {
    opts = opts || {}; var tab = readTab(db, tabId);
    if (!tab.whereClause) return { tabId: tabId, table: tab.table, where: '', rows: null, deferred: 'no-whereclause' };
    if (unsafe(tab.whereClause)) return { tabId: tabId, table: tab.table, deferred: 'unsafe' };
    var sub = substitute(tab.whereClause, opts.ctx);
    if (sub.unresolved.length) return { tabId: tabId, table: tab.table, where: sub.sql, deferred: 'unresolved-tokens', unresolved: sub.unresolved };
    var rows = db.prepare('SELECT COUNT(*) AS n FROM ' + tab.table + ' WHERE ' + sub.sql).get().n;
    var out = { tabId: tabId, table: tab.table, where: sub.sql, rows: rows, ok: true };
    if (opts.candidatePk && opts.candidatePk.col) {
      out.member = !!db.prepare('SELECT 1 AS m FROM ' + tab.table + ' WHERE ' + opts.candidatePk.col + '=? AND (' + sub.sql + ')').get(opts.candidatePk.id);
    }
    return out;
  }

  // orderedKeys(db, tabId, pkCol) — apply AD_Tab.OrderByClause, return the pk list in tab order.
  function orderedKeys(db, tabId, pkCol) {
    var tab = readTab(db, tabId);
    if (!tab.orderBy) return { tabId: tabId, table: tab.table, deferred: 'no-orderby' };
    if (unsafe(tab.orderBy)) return { tabId: tabId, table: tab.table, deferred: 'unsafe' };
    var keys = db.prepare('SELECT ' + pkCol + ' AS k FROM ' + tab.table + ' ORDER BY ' + tab.orderBy).all().map(function (r) { return r.k; });
    return { tabId: tabId, table: tab.table, orderBy: tab.orderBy, keys: keys, rows: keys.length, ok: true };
  }

  function coverageScan(db) {
    return {
      tabsWithWhere: db.prepare("SELECT COUNT(*) AS n FROM ad_tab WHERE whereclause IS NOT NULL AND whereclause<>''").get().n,
      tabsWithOrderBy: db.prepare("SELECT COUNT(*) AS n FROM ad_tab WHERE orderbyclause IS NOT NULL AND orderbyclause<>''").get().n
    };
  }

  var API = { readTab: readTab, applyWhere: applyWhere, orderedKeys: orderedKeys, substitute: substitute, coverageScan: coverageScan };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  if (typeof window !== 'undefined') window.AdTabQuery = API; else if (global) global.AdTabQuery = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
