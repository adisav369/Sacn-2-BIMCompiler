// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// Scope: W-POST-B3 §W-2 fixture capture (prompts/FABLE5_B3_POSTING_ORACLE.md). Straight copy of the
// COMMITTED scratch DB (idempiere_b3) after scripts/logic_oracle/PostingOracleTest.java drove the
// REAL compiled posters: the posted fact_acct for the 6 B-3 classes + the seed-doc source rows +
// the acct config their manifests read. TEXT fixture (JSON) — the DB-binary ban holds. NON-INVENT:
// every row below is read from the scratch PG; nothing is computed or authored here.
// Called by scripts/generate_post_oracle.sh; standalone: node scripts/capture_post_b3_fixture.js <db> <out.json>
'use strict';
var cp = require('child_process');
var fs = require('fs');
var path = require('path');

var DB = process.argv[2] || 'idempiere_b3';
var OUT = process.argv[3] || path.join(__dirname, '..', 'build', 'erp', 'oracle', 'post_b3_fixture.json');
var US = '\x1f';

function pgRows(sql) {
  var out = cp.execFileSync('docker', ['exec', 'postgres', 'psql', '-U', 'adempiere', '-d', DB,
    '-t', '-A', '-F', US, '-c', sql], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return out.split('\n').filter(function (s) { return s.length; }).map(function (l) { return l.split(US); });
}

// B-3 target ad_table_ids (ad_docfsm.js 0-seed band)
var IDS = '623,53137,53127,53275,53128,53121';

// table -> { cols: [...names as stored in fixture...], sql }
var CAPTURE = {
  // the ORACLE — extract_fact_acct.sh column shape, scoped to the B-3 classes
  fact_acct: {
    cols: ['fact_acct_id','ad_client_id','ad_org_id','c_acctschema_id','account_id','c_period_id',
           'ad_table_id','record_id','line_id','gl_category_id','c_tax_id','postingtype','c_currency_id',
           'amtsourcedr','amtsourcecr','amtacctdr','amtacctcr','qty','m_product_id','c_bpartner_id','description'],
    sql: "SELECT fact_acct_id, ad_client_id, ad_org_id, c_acctschema_id, account_id, c_period_id," +
         " ad_table_id, record_id, line_id, gl_category_id, c_tax_id, postingtype, c_currency_id," +
         " round(amtsourcedr,2), round(amtsourcecr,2), round(amtacctdr,2), round(amtacctcr,2)," +
         " round(qty,2), m_product_id, c_bpartner_id, replace(coalesce(description,''),chr(10),' ')" +
         " FROM adempiere.fact_acct WHERE ad_table_id IN (" + IDS + ") ORDER BY fact_acct_id"
  },
  // seed documents (source idempiere_test had ZERO rows in every one of these — the 0-seed premise —
  // so all rows in the clone are exactly the seed docs the oracle posted)
  a_asset_addition: {
    cols: ['a_asset_addition_id','a_asset_id','dateacct','a_sourcetype','a_capvsexp','assetsourceamt',
           'assetvalueamt','c_charge_id','m_product_id','c_invoice_id','c_invoiceline_id','c_project_id',
           'postingtype','docstatus','posted','ad_org_id','ad_client_id','c_currency_id'],
    sql: "SELECT a_asset_addition_id, a_asset_id, dateacct::date, a_sourcetype, a_capvsexp," +
         " round(assetsourceamt,2), round(assetvalueamt,2), coalesce(c_charge_id,0), coalesce(m_product_id,0)," +
         " coalesce(c_invoice_id,0), coalesce(c_invoiceline_id,0), coalesce(c_project_id,0)," +
         " postingtype, docstatus, posted, ad_org_id, ad_client_id, c_currency_id" +
         " FROM adempiere.a_asset_addition ORDER BY a_asset_addition_id"
  },
  a_asset_disposed: {
    cols: ['a_asset_disposed_id','a_asset_id','dateacct','a_disposed_method','a_disposal_amt',
           'a_accumulated_depr_delta','expense','postingtype','docstatus','posted','ad_org_id'],
    sql: "SELECT a_asset_disposed_id, a_asset_id, dateacct::date, a_disposed_method, round(a_disposal_amt,2)," +
         " round(a_accumulated_depr_delta,2), round(expense,2), postingtype, docstatus, posted, ad_org_id" +
         " FROM adempiere.a_asset_disposed ORDER BY a_asset_disposed_id"
  },
  a_asset_reval: {
    cols: ['a_asset_reval_id','a_asset_id','dateacct','a_asset_cost','a_accumulated_depr',
           'a_asset_cost_change','a_change_acumulated_depr','postingtype','docstatus','posted','ad_org_id'],
    sql: "SELECT a_asset_reval_id, a_asset_id, dateacct::date, round(a_asset_cost,2), round(a_accumulated_depr,2)," +
         " round(a_asset_cost_change,2), round(a_change_acumulated_depr,2), postingtype, docstatus, posted, ad_org_id" +
         " FROM adempiere.a_asset_reval ORDER BY a_asset_reval_id"
  },
  a_asset_transfer: {
    cols: ['a_asset_transfer_id','a_asset_id','c_acctschema_id','dateacct','a_asset_acct','a_asset_new_acct',
           'a_accumdepreciation_acct','a_accumdepreciation_new_acct','a_depreciation_acct','a_depreciation_new_acct',
           'postingtype','docstatus','posted','ad_org_id'],
    sql: "SELECT a_asset_transfer_id, a_asset_id, c_acctschema_id, dateacct::date, a_asset_acct, a_asset_new_acct," +
         " a_accumdepreciation_acct, a_accumdepreciation_new_acct, a_depreciation_acct, a_depreciation_new_acct," +
         " postingtype, docstatus, posted, ad_org_id" +
         " FROM adempiere.a_asset_transfer ORDER BY a_asset_transfer_id"
  },
  a_depreciation_entry: {
    cols: ['a_depreciation_entry_id','c_acctschema_id','dateacct','postingtype','a_entry_type',
           'docstatus','posted','ad_org_id','c_doctype_id'],
    sql: "SELECT a_depreciation_entry_id, c_acctschema_id, dateacct::date, postingtype, a_entry_type," +
         " docstatus, posted, ad_org_id, c_doctype_id" +
         " FROM adempiere.a_depreciation_entry ORDER BY a_depreciation_entry_id"
  },
  a_depreciation_exp: {
    cols: ['a_depreciation_exp_id','a_depreciation_entry_id','a_asset_id','c_acctschema_id','dr_account_id',
           'cr_account_id','expense','dateacct','processed','a_period','postingtype','a_entry_type','ad_org_id'],
    sql: "SELECT a_depreciation_exp_id, coalesce(a_depreciation_entry_id,0), a_asset_id, c_acctschema_id," +
         " dr_account_id, cr_account_id, round(expense,2), dateacct::date, processed, a_period, postingtype," +
         " a_entry_type, ad_org_id FROM adempiere.a_depreciation_exp ORDER BY a_depreciation_exp_id"
  },
  c_projectissue: {
    cols: ['c_projectissue_id','c_project_id','m_product_id','m_locator_id','movementqty','movementdate',
           'm_inoutline_id','s_timeexpenseline_id','posted','processed','ad_org_id','ad_client_id'],
    sql: "SELECT c_projectissue_id, c_project_id, m_product_id, m_locator_id, round(movementqty,2)," +
         " movementdate::date, coalesce(m_inoutline_id,0), coalesce(s_timeexpenseline_id,0), posted, processed," +
         " ad_org_id, ad_client_id FROM adempiere.c_projectissue ORDER BY c_projectissue_id"
  },
  // acct config the manifests read (a_asset_acct: 0 rows in source — all rows are the seed's;
  // validfrom carries the transfer's time-slice)
  a_asset_acct: {
    cols: ['a_asset_acct_id','a_asset_id','c_acctschema_id','postingtype','validfrom','a_asset_acct',
           'a_accumdepreciation_acct','a_depreciation_acct','a_disposal_loss_acct','a_disposal_revenue_acct',
           'a_reval_cost_offset_acct'],
    sql: "SELECT a_asset_acct_id, a_asset_id, c_acctschema_id, postingtype, coalesce(validfrom::date::text,''), a_asset_acct," +
         " a_accumdepreciation_acct, a_depreciation_acct, coalesce(a_disposal_loss_acct,0), coalesce(a_disposal_revenue_acct,0)," +
         " coalesce(a_reval_cost_offset_acct,0) FROM adempiere.a_asset_acct ORDER BY a_asset_acct_id"
  },
  a_asset_change: {
    cols: ['a_asset_change_id','a_asset_id','changetype','c_acctschema_id','postingtype','assetvalueamt',
           'assetbookvalueamt','assetaccumdepreciationamt'],
    sql: "SELECT a_asset_change_id, a_asset_id, changetype, coalesce(c_acctschema_id,0), postingtype," +
         " round(assetvalueamt,2), round(assetbookvalueamt,2), round(assetaccumdepreciationamt,2)" +
         " FROM adempiere.a_asset_change WHERE changetype='DIS' ORDER BY a_asset_change_id"
  },
  a_depreciation_workfile: {
    cols: ['a_depreciation_workfile_id','a_asset_id','c_acctschema_id','postingtype','a_asset_cost','a_accumulated_depr'],
    sql: "SELECT a_depreciation_workfile_id, a_asset_id, coalesce(c_acctschema_id,0), postingtype," +
         " round(a_asset_cost,2), round(a_accumulated_depr,2)" +
         " FROM adempiere.a_depreciation_workfile ORDER BY a_depreciation_workfile_id"
  },
  a_asset: {
    cols: ['a_asset_id','name','a_asset_group_id','m_product_id'],
    sql: "SELECT a_asset_id, name, a_asset_group_id, coalesce(m_product_id,0) FROM adempiere.a_asset ORDER BY a_asset_id"
  },
  c_project: {
    cols: ['c_project_id','projectcategory'],
    sql: "SELECT c_project_id, projectcategory FROM adempiere.c_project WHERE ad_client_id=11 ORDER BY c_project_id"
  },
  c_project_acct: {
    cols: ['c_project_id','c_acctschema_id','pj_asset_acct','pj_wip_acct'],
    sql: "SELECT c_project_id, c_acctschema_id, pj_asset_acct, pj_wip_acct FROM adempiere.c_project_acct" +
         " WHERE ad_client_id=11 ORDER BY c_project_id, c_acctschema_id"
  },
  // combination -> natural account map (fact_acct.account_id is the element id; every *_acct column
  // above is a C_ValidCombination id)
  c_validcombination: {
    cols: ['c_validcombination_id','account_id'],
    sql: "SELECT c_validcombination_id, account_id FROM adempiere.c_validcombination WHERE ad_client_id IN (0,11)" +
         " ORDER BY c_validcombination_id"
  },
  // project-issue cost basis + product-expense fallbacks (Doc_AssetAddition product-0 default =
  // default product category's P_Expense_Acct, ProductCost.getAccountDefault ORDER BY IsDefault DESC, Created)
  m_product: {
    cols: ['m_product_id','producttype','m_product_category_id','name'],
    sql: "SELECT m_product_id, producttype, m_product_category_id, name FROM adempiere.m_product" +
         " WHERE ad_client_id=11 ORDER BY m_product_id"
  },
  m_product_category: {
    cols: ['m_product_category_id','isdefault','created'],
    sql: "SELECT m_product_category_id, isdefault, created FROM adempiere.m_product_category WHERE ad_client_id=11" +
         " ORDER BY m_product_category_id"
  },
  m_product_category_acct: {
    cols: ['m_product_category_id','c_acctschema_id','p_expense_acct','p_asset_acct'],
    sql: "SELECT m_product_category_id, c_acctschema_id, coalesce(p_expense_acct,0), coalesce(p_asset_acct,0)" +
         " FROM adempiere.m_product_category_acct WHERE ad_client_id=11 ORDER BY m_product_category_id, c_acctschema_id"
  },
  m_cost: {
    cols: ['m_product_id','c_acctschema_id','m_costtype_id','m_costelement_id','currentcostprice'],
    sql: "SELECT m_product_id, c_acctschema_id, m_costtype_id, m_costelement_id, currentcostprice" +
         " FROM adempiere.m_cost WHERE ad_client_id=11 ORDER BY m_product_id, c_acctschema_id, m_costelement_id"
  },
  m_costelement: {
    cols: ['m_costelement_id','costingmethod','costelementtype'],
    sql: "SELECT m_costelement_id, coalesce(costingmethod,''), costelementtype FROM adempiere.m_costelement" +
         " WHERE ad_client_id IN (0,11) ORDER BY m_costelement_id"
  },
  c_acctschema: {
    cols: ['c_acctschema_id','c_currency_id','costingmethod','m_costtype_id'],
    sql: "SELECT c_acctschema_id, c_currency_id, costingmethod, m_costtype_id FROM adempiere.c_acctschema" +
         " WHERE ad_client_id=11 ORDER BY c_acctschema_id"
  },
  // Doc_AssetAddition posts in DOC currency (USD): schema-200000 (EUR) facts convert at the default
  // Spot rate — multiplyrate kept TEXT (exact NUMERIC, the W-ALLOC-FX precedent)
  // isactive captured, NOT filtered — MConversionRate.getRate:251 filters IsActive='Y' itself and in
  // this seed that clause (not client rank) is what excludes the 0.8006 twin row (corrected 2026-07-18)
  c_conversion_rate: {
    cols: ['c_currency_id','c_currency_id_to','c_conversiontype_id','multiplyrate','validfrom','validto','ad_client_id','ad_org_id','isactive'],
    sql: "SELECT c_currency_id, c_currency_id_to, c_conversiontype_id, multiplyrate, validfrom::date, validto::date," +
         " ad_client_id, ad_org_id, isactive" +
         " FROM adempiere.c_conversion_rate WHERE ad_client_id IN (0,11) ORDER BY c_currency_id, c_currency_id_to, validfrom"
  },
  c_conversiontype: {
    cols: ['c_conversiontype_id','isdefault'],
    sql: "SELECT c_conversiontype_id, isdefault FROM adempiere.c_conversiontype ORDER BY c_conversiontype_id"
  },
  c_charge_acct: {
    cols: ['c_charge_id','c_acctschema_id','ch_expense_acct'],
    sql: "SELECT c_charge_id, c_acctschema_id, ch_expense_acct FROM adempiere.c_charge_acct WHERE ad_client_id=11" +
         " ORDER BY c_charge_id, c_acctschema_id"
  }
};

var fixture = { db: DB, tables: {} };
Object.keys(CAPTURE).forEach(function (t) {
  var spec = CAPTURE[t];
  var rows = pgRows(spec.sql);
  rows.forEach(function (r) {
    if (r.length !== spec.cols.length) {
      throw new Error('capture ' + t + ': row width ' + r.length + ' != cols ' + spec.cols.length);
    }
  });
  fixture.tables[t] = { cols: spec.cols, rows: rows };
  console.log('§GEN-CAPTURE table=' + t + ' rows=' + rows.length);
});

var fa = fixture.tables.fact_acct.rows;
var dr = 0, cr = 0;
fa.forEach(function (r) { dr += Math.round(Number(r[15]) * 100); cr += Math.round(Number(r[16]) * 100); });
console.log('§GEN-CAPTURE fact_acct b3-classes rows=' + fa.length + ' ΣDRc=' + dr + ' ΣCRc=' + cr);
if (fa.length === 0) throw new Error('capture: 0 fact_acct rows for the B-3 classes — posting did not happen, fixture refused');

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(fixture, null, 1));
console.log('§GEN-CAPTURE fixture=' + OUT + ' bytes=' + fs.statSync(OUT).size);
