#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * erp_runtime_probe.js — "rush and see what breaks" vertical slice.
 *   Spec: docs/ERP.md §0.10 (the runtime rule evaluator / "abstract engine").
 *
 * Stands up the abstract engine on sql.js (THE PWA ENGINE) over the real compiled
 * corpus (ad_full.db = data, erp_rules.db = rules) and fires actual ERP cells to
 * find out how much of the ERP domain the PWA can already answer — and exactly
 * where it breaks. This is a DIAGNOSTIC, not production: every probe logs GREEN
 * (answered) or RED (broke, with the reason) so the next iteration is data-driven.
 *
 * The engine core (dispatchByForm) is written browser-portable — it would move to
 * the viewer verbatim. Run: node scripts/erp_runtime_probe.js 2>&1 | tee build/erp/probe.log
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');

// GardenWorld demo context (the globals iDempiere injects as @ctx@).
var GLOBAL_CTX = { AD_Client_ID: 11, AD_Org_ID: 11, '#AD_Client_ID': 11, '#AD_Org_ID': 11, AD_Language: 'en_US' };

function q1(db, sql) { var r = db.exec(sql); return r.length ? r[0].values[0][0] : null; }
function rows(db, sql) {
  var r = db.exec(sql); if (!r.length) return [];
  return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; });
}

// ── The abstract engine: substitute @ctx@, dispatch by form ──────────────────
function resolveContext(body, ctx) {
  var unresolved = [];
  var out = String(body).replace(/@(#?\w+)@/g, function (m, name) {
    if (ctx[name] !== undefined && ctx[name] !== null) {
      var v = ctx[name];
      return (typeof v === 'number') ? String(v) : "'" + String(v).replace(/'/g, "''") + "'";
    }
    unresolved.push(name); return m;
  });
  return { sql: out, unresolved: unresolved };
}
// Extract real table names referenced as Table.Column (for building a FROM).
function inferTables(sql, knownTables) {
  var seen = {}, list = [];
  var re = /\b([A-Za-z_]\w*)\s*\./g, m;
  while ((m = re.exec(sql))) {
    var t = m[1].toLowerCase();
    if (knownTables[t] && !seen[t]) { seen[t] = 1; list.push(t); }
  }
  return list;
}
function dispatchByForm(rule, dataDb, ctx, knownTables) {
  switch (rule.form) {
    case 'sql': {
      var r = resolveContext(rule.body, ctx);
      if (r.unresolved.length) return { status: 'RED', reason: 'unresolved-ctx', detail: r.unresolved.join(',') };
      try {
        var head = r.sql.replace(/^\s+/, '').slice(0, 6).toUpperCase();
        var isFull = (head.indexOf('SELECT') === 0 || head.indexOf('WITH') === 0);
        var sql, tabs = [];
        if (isFull) {
          sql = r.sql;                       // AccountingRule = full SELECT, run raw.
        } else {                              // Validation = WHERE fragment → infer FROM.
          tabs = inferTables(r.sql, knownTables);
          if (!tabs.length) return { status: 'RED', reason: 'no-from', detail: 'no known table in predicate' };
          sql = 'SELECT EXISTS(SELECT 1 FROM ' + tabs.join(',') + ' WHERE ' + r.sql + ' LIMIT 1) ok';
        }
        var val = q1(dataDb, sql);
        return { status: 'GREEN', reason: 'sql-ran', detail: (isFull ? 'fullSELECT' : 'tables=[' + tabs.join(',') + ']') + ' result=' + String(val).slice(0, 40) };
      } catch (e) { return { status: 'RED', reason: 'sql-error', detail: e.message }; }
    }
    case 'expression': {
      // deterministic JS sandbox (none in corpus yet, but the path exists).
      try { /* eslint-disable no-new-func */
        var fn = new Function('doc', 'ctx', '"use strict"; return (' + rule.body + ');');
        return { status: 'GREEN', reason: 'expr-ran', detail: String(fn(ctx.__doc || {}, ctx)) };
      } catch (e) { return { status: 'RED', reason: 'expr-error', detail: e.message }; }
    }
    case 'policy': { try { return { status: 'GREEN', reason: 'policy', detail: rule.body }; } catch (e) { return { status: 'RED', reason: 'bad-json' }; } }
    case 'table': return { status: 'AMBER', reason: 'decision-table/workflow ref (matcher not built)', detail: rule.params };
    case 'handler': return { status: 'RED', reason: 'handler-unimplemented', detail: 'oracle=' + rule.oracle_ptr };
    default: return { status: 'RED', reason: 'unknown-form', detail: rule.form };
  }
}

(async function () {
  var SQL = await initSqlJs();
  var data = new SQL.Database(fs.readFileSync(AD));     // runtime data store (PWA)
  var rdb = new SQL.Database(fs.readFileSync(RULES));    // compiled rule cluster (PWA)
  var knownTables = {};
  rows(data, "SELECT name FROM sqlite_master WHERE type='table'").forEach(function (r) { knownTables[r.name.toLowerCase()] = 1; });

  var tally = { GREEN: 0, RED: 0, AMBER: 0 }, breaks = {};
  function note(res) { tally[res.status] = (tally[res.status] || 0) + 1; if (res.status === 'RED') breaks[res.reason] = (breaks[res.reason] || 0) + 1; }

  console.log('═══ ERP RUNTIME PROBE — abstract engine on sql.js over the real corpus ═══\n');

  // ── Q1. Plain domain queries the PWA must answer (no rules, just sql.js) ────
  console.log('── Q1. Domain answers (pure sql.js over ad_full.db) ──');
  var orders = rows(data, "SELECT c_order_id,documentno,docstatus,grandtotal,CASE issotrx WHEN 'Y' THEN 'SO' ELSE 'PO' END kind FROM c_order ORDER BY c_order_id");
  console.log('§PROBE orders answered=' + orders.length + ' e.g. ' + orders.slice(0, 3).map(function (o) { return o.documentno + '(' + o.kind + '/' + o.docstatus + '/$' + o.grandtotal + ')'; }).join(' '));
  var lineage = rows(data, "SELECT o.documentno ord, i.documentno inv FROM c_order o LEFT JOIN c_invoice i ON i.c_order_id=o.c_order_id WHERE i.c_invoice_id IS NOT NULL");
  console.log('§PROBE lineage order→invoice answered=' + lineage.length + ' e.g. ' + lineage.slice(0, 3).map(function (l) { return l.ord + '→' + l.inv; }).join(' '));
  var openOrders = q1(data, "SELECT count(*) FROM c_order WHERE docstatus<>'CO'");
  console.log('§PROBE analytics open(non-CO) orders=' + openOrders + '  [GREEN — the read-side domain answers]\n');

  // ── Q2. Fire the C_Order:CO cell — what rules apply, dispatch each ──────────
  console.log('── Q2. Fire cell (C_Order, CO) — the hottest §18.2 hub ──');
  var theOrder = rows(data, "SELECT * FROM c_order WHERE c_order_id=100")[0]; // a SOO order, status CO
  var ctx = Object.assign({}, GLOBAL_CTX, theOrder, { __doc: theOrder });
  // 2a. policy (DocType flags) drives the fan-out decision
  var dtName = q1(data, "SELECT name FROM c_doctype WHERE c_doctype_id=" + theOrder.c_doctype_id);
  var pol = rows(rdb, "SELECT * FROM rules WHERE rule_key='DOCPOLICY:" + theOrder.c_doctype_id + "'")[0];
  if (pol) { var flags = JSON.parse(pol.body); note({ status: 'GREEN' });
    console.log('§PROBE cell policy DocType="' + dtName + '" autoInOut=' + flags.isautogenerateinout + ' autoInvoice=' + flags.isautogenerateinvoice + ' subType=' + flags.docsubtypeso + '  [GREEN — fan-out knobs readable]');
  } else console.log('§PROBE cell policy MISSING for doctype ' + theOrder.c_doctype_id + ' [RED]');
  // 2b. the DocEvent handler that actually completes the order
  var deh = rows(rdb, "SELECT * FROM rules WHERE binding='C_Order:CO' AND form='handler'")[0];
  var hres = dispatchByForm(deh, data, ctx, knownTables); note(hres);
  console.log('§PROBE cell handler C_Order:CO → ' + hres.status + ' (' + hres.reason + ') ' + hres.detail + '  [expected RED — hand-port backlog]\n');

  // ── Q3. Validation rules — run a sample through the engine ─────────────────
  console.log('── Q3. Validation rules (AD_Val_Rule, form=sql) — sample of 40 ──');
  var vsample = rows(rdb, "SELECT * FROM rules WHERE event_type='Validation' ORDER BY id LIMIT 40");
  var vt = { GREEN: 0, ctx: 0, sqlerr: 0 }, sqlerrEx = '';
  vsample.forEach(function (r) {
    var res = dispatchByForm(r, data, ctx, knownTables); note(res);
    if (res.status === 'GREEN') vt.GREEN++;
    else if (res.reason === 'unresolved-ctx') vt.ctx++;
    else { vt.sqlerr++; if (!sqlerrEx) sqlerrEx = r.binding + ': ' + res.detail; }
  });
  console.log('§PROBE validation sample=40 ran=' + vt.GREEN + ' blocked-on-ctx=' + vt.ctx + ' sql-error=' + vt.sqlerr);
  if (sqlerrEx) console.log('   ↳ first sql-error: ' + sqlerrEx);
  console.log('');

  // ── Q4. AccountingRule SQL — the 4 GL reconciliation rules, run natively ───
  console.log('── Q4. AccountingRule SQL (form=sql) — run all 4 in sql.js ──');
  var ar = rows(rdb, "SELECT * FROM rules WHERE event_type='AccountingRule'");
  ar.forEach(function (r) {
    var res = dispatchByForm(r, data, ctx, knownTables); note(res);
    console.log('§PROBE acctRule ' + r.binding + ' → ' + res.status + ' (' + res.reason + ') ' + String(res.detail).slice(0, 90));
  });
  console.log('');

  // ── Q5. Coverage map: of all cells/rules, how many can the engine serve? ───
  console.log('── Q5. Engine coverage over the WHOLE rule corpus (by form) ──');
  var byForm = rows(rdb, "SELECT form, count(*) c FROM rules GROUP BY form ORDER BY c DESC");
  byForm.forEach(function (f) {
    var serveable = (f.form === 'sql' || f.form === 'policy' || f.form === 'expression');
    console.log('§PROBE corpus form=' + f.form + ' count=' + f.c + ' → ' + (serveable ? 'engine-serveable' : (f.form === 'handler' ? 'NEEDS hand-port' : 'NEEDS matcher')));
  });

  data.close(); rdb.close();

  // ── Verdict ────────────────────────────────────────────────────────────────
  console.log('\n═══ VERDICT ═══');
  console.log('§PROBE tally GREEN=' + tally.GREEN + ' AMBER=' + tally.AMBER + ' RED=' + tally.RED);
  console.log('§PROBE breakages ' + JSON.stringify(breaks));
})();
