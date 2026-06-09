#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_tabquery.js — W-TABQUERY witness. Opens canonical build/erp/ad_full.db and drives build/erp/ad_tabquery.js
// over REAL AD_Tab rows: AD_Tab.WhereClause applied as the row filter (SO vs PO order tabs) + a membership
// falsifier (a PO order is excluded from the SO tab), and AD_Tab.OrderByClause applied as the sort.
// Implementing ERP_COVERAGE_MATRIX.md §AD_Tab·WhereClause / §AD_Tab·OrderByClause (GAP #11) — Witness: W-TABQUERY
// Run: node scripts/poc_tabquery.js 2>&1 | tee build/erp/poc_tabquery.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var T = require(path.join(__dirname, '..', 'build', 'erp', 'ad_tabquery.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-TABQUERY — AD_Tab WhereClause/OrderBy applied as the tab row filter + sort ═══\n');
var db = new Database(DB_PATH, { readonly: true });
var cov = T.coverageScan(db);
console.log('§TABQUERY_COVERAGE tabsWithWhere=' + cov.tabsWithWhere + ' tabsWithOrderBy=' + cov.tabsWithOrderBy);
verdict(cov.tabsWithWhere === 85 && cov.tabsWithOrderBy === 173, 'AD_Tab where/orderby populations match matrix (85 / 173)', 'where=' + cov.tabsWithWhere + ' orderby=' + cov.tabsWithOrderBy);

// ── WhereClause applied: tab 186 "Order" (IsSOTrx='Y') → 4 SO orders; tab 294 "Purchase Order" (=N) → 4 ──
console.log('\n── AD_Tab.WhereClause applied as the row filter ──');
var so = T.applyWhere(db, 186);
console.log('§TAB_FILTER tab=186 table=' + so.table + ' where="' + so.where + '" rows=' + so.rows);
verdict(so.ok && so.rows === 4, 'SO order tab (186) filters to 4 sales orders', 'rows=' + so.rows);
var po = T.applyWhere(db, 294);
console.log('§TAB_FILTER tab=294 where="' + po.where + '" rows=' + po.rows);
verdict(po.ok && po.rows === 4, 'PO order tab (294) filters to 4 purchase orders', 'rows=' + po.rows);

// §FALSIFIER: a PO order (104) is EXCLUDED from the SO tab's scope
console.log('\n── §FALSIFIER — tab scope is load-bearing ──');
var m = T.applyWhere(db, 186, { candidatePk: { col: 'c_order_id', id: 104 } });   // 104 is a PO
console.log('§FALSIFIER tab=186(SO) purchaseOrder=104 member=' + m.member + ' (must be false)');
verdict(m.member === false, 'PO order 104 is EXCLUDED from the SO tab (whereclause scopes the rows)', 'member=' + m.member);

// ── OrderByClause applied: M_Product tab orders by M_Product.Value (ascending) ──────────────────────────
console.log('\n── AD_Tab.OrderByClause applied as the sort ──');
var ptab = db.prepare("SELECT t.ad_tab_id FROM ad_tab t JOIN ad_table tb ON tb.ad_table_id=t.ad_table_id WHERE tb.tablename='M_Product' AND t.orderbyclause LIKE '%Value%' LIMIT 1").get();
var ord = T.orderedKeys(db, ptab.ad_tab_id, 'm_product_id');
var vals = ord.keys.slice(0, 5).map(function (id) { return db.prepare('SELECT value FROM m_product WHERE m_product_id=?').get(id).value; });
console.log('§TAB_ORDER tab=' + ptab.ad_tab_id + ' orderBy="' + ord.orderBy + '" rows=' + ord.rows + ' first5=' + JSON.stringify(vals));
var sorted = vals.slice().sort();
verdict(ord.ok && JSON.stringify(vals) === JSON.stringify(sorted), 'M_Product tab rows come out sorted by Value (ascending)', JSON.stringify(vals));

console.log('\n' + (fails === 0 ? '🟢 W-TABQUERY PASS' : '🔴 W-TABQUERY FAIL (' + fails + ')') +
  ' — AD_Tab where/orderby applied on canonical ad_full.db. Re-verdict AD_Tab WhereClause/OrderBy + GAP #11 (⛔→🟡).');
db.close();
process.exit(fails === 0 ? 0 : 1);
