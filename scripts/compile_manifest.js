#!/usr/bin/env node
/**
 * compile_manifest.js — Implementing prompts/ERP_KERNEL_MONSTERS.md §4
 *   Witness: ad_seed.db (compile-time input) → manifest.json (runtime payload)
 *
 * PRIME RULE: EXTRACT, do not invent. Every window/tab/field traces to a row
 * in ad_seed.db. The only editorial inputs are:
 *   - CURATED_WINDOWS  (which 7 business windows are Tier-0 bubbles)
 *   - BUSINESS_SEMANTICS (state machines / downstream / dropdowns) — these come
 *     verbatim from ERP_KERNEL_MONSTERS.md §4, not guessed here.
 *
 * Reads via the sqlite3 CLI (-json) — no node sqlite module needed.
 * Run:  node scripts/compile_manifest.js
 * Out:  <repo>/../bim-ootb/viewer/manifest.json
 *
 * Read the log after every run. Exit code is not evidence.
 */
'use strict';

var fs = require('fs');
var path = require('path');
var execFileSync = require('child_process').execFileSync;

// ── Inputs / outputs ────────────────────────────────────────────────────────
var DB = process.env.AD_SEED_DB ||
  path.join(process.env.HOME, 'bim-ootb', 'viewer', 'ad_seed.db');
var OUT = process.env.MANIFEST_OUT ||
  path.join(process.env.HOME, 'bim-ootb', 'viewer', 'manifest.json');

// ── Editorial: the 7 Tier-0 business windows (memory: ERP Instant Globe) ─────
// id → AD_Window_ID verified against ad_seed.db on 2026-05-29.
var CURATED_WINDOWS = [
  { id: 123, node: 'Partners' },
  { id: 140, node: 'Products' },
  { id: 143, node: 'Orders' },     // Sales Order
  { id: 167, node: 'Invoices' },   // Sales Invoice and Credit/Debit Note
  { id: 195, node: 'Payments' },   // Payment and Receipt
  { id: 169, node: 'Shipments' },  // Shipment (Customer)
  { id: 130, node: 'Projects' }
];

// ── AD_Reference_ID → manifest field type (verified id list from ad_seed) ────
var TYPE_BY_REF = {
  10: 'string', 11: 'integer', 12: 'amount', 13: 'id', 14: 'text',
  15: 'date', 16: 'datetime', 17: 'list', 18: 'table', 19: 'tableDirect',
  20: 'yesno', 21: 'location', 22: 'number', 28: 'button', 30: 'search',
  31: 'locator', 33: 'assignment', 40: 'url'
};
// Types whose ColumnName (X_ID) names a FK target table (iDempiere convention).
var FK_TYPES = { tableDirect: true, search: true, table: true };

// System / multi-tenant / audit columns. ERP_KERNEL_MONSTERS.md §3: multi-tenant
// columns "leave in ad_seed.db ... Don't load at runtime." Single-user browser
// ERP has no tenants/orgs, and audit is covered by kernel_ops. Dropping these
// also stops the kernel mandatory-check from demanding a Tenant on every record.
var SYSTEM_COLS = {
  AD_Client_ID: true, AD_Org_ID: true, IsActive: true,
  Created: true, CreatedBy: true, Updated: true, UpdatedBy: true
};

// FK heuristic note: target = ColumnName minus _ID (iDempiere convention, holds
// for the large majority). A few columns break it (e.g. SalesRep_ID → AD_User,
// not "SalesRep"). The runtime FK-drill should fall back to an AD_Column lookup
// when manifest.fk names a non-existent table. No hand-maintained alias map here
// — that would be editorial invention.

// Tab depth in the manifest: 0 = globe bubble, 1 = first orbit. Level 2+ tabs
// (accounting, deep children) are discovered from ad_seed.db via FK drill at
// runtime — they don't belong in the instant-load payload. Override with env.
var MAX_TAB_LEVEL = process.env.MAX_TAB_LEVEL != null
  ? Number(process.env.MAX_TAB_LEVEL) : 1;

// ── Business semantics — verbatim from ERP_KERNEL_MONSTERS.md §4 ─────────────
var BUSINESS_SEMANTICS = {
  state_machine: {
    C_Order:   ['Drafted', 'InProgress', 'Completed', 'Voided', 'Closed'],
    C_Invoice: ['Drafted', 'InProgress', 'Completed', 'Reversed', 'Voided'],
    C_Payment: ['Drafted', 'InProgress', 'Completed', 'Voided'],
    M_InOut:   ['Drafted', 'InProgress', 'Completed', 'Voided', 'Closed'],
    C_Project: ['Drafted', 'InProgress', 'Completed', 'Voided', 'Closed']
  },
  downstream: {
    C_Order:   ['C_Invoice', 'M_InOut'],
    C_Invoice: ['C_Payment'],
    M_InOut:   [],
    C_Payment: []
  },
  dropdowns: {
    DocStatus: [
      { value: 'DR', name: 'Drafted' },
      { value: 'IP', name: 'In Progress' },
      { value: 'CO', name: 'Completed' },
      { value: 'VO', name: 'Voided' },
      { value: 'CL', name: 'Closed' },
      { value: 'RE', name: 'Reversed' }
    ]
  }
};

// ── sqlite3 -json helper ─────────────────────────────────────────────────────
function q(sql) {
  var out = execFileSync('sqlite3', [DB, '-json', sql], {
    encoding: 'utf8', maxBuffer: 64 * 1024 * 1024
  }).trim();
  return out ? JSON.parse(out) : [];
}
function esc(s) { return String(s).replace(/'/g, "''"); }

// Strip trailing _ID → FK target table (C_BPartner_ID → C_BPartner).
function fkTarget(columnName) {
  return /_ID$/.test(columnName) ? columnName.replace(/_ID$/, '') : null;
}

// ── Build one window ─────────────────────────────────────────────────────────
function buildWindow(winId) {
  var winRow = q("SELECT Name FROM AD_Window WHERE AD_Window_ID=" + winId)[0];
  if (!winRow) { console.log('§MANIFEST WARN: window ' + winId + ' not found'); return null; }

  var tabs = q(
    "SELECT t.AD_Tab_ID AS tabId, t.Name AS name, t.TabLevel AS level," +
    " t.SeqNo AS seq, tb.TableName AS tbl " +
    "FROM AD_Tab t JOIN AD_Table tb ON t.AD_Table_ID=tb.AD_Table_ID " +
    "WHERE t.AD_Window_ID=" + winId + " AND t.IsActive='Y' " +
    "ORDER BY t.SeqNo"
  );

  var outTabs = [];
  var fieldTotal = 0;
  for (var i = 0; i < tabs.length; i++) {
    var t = tabs[i];
    if (t.level > MAX_TAB_LEVEL) continue;   // deeper tabs: FK-drilled at runtime

    // Parent FK: nearest preceding tab one level shallower → parentTable + _ID.
    var fk = null;
    if (t.level > 0) {
      for (var j = i - 1; j >= 0; j--) {
        if (tabs[j].level === t.level - 1) { fk = tabs[j].tbl + '_ID'; break; }
      }
    }

    // Full field contract — mirrors ad_parser.getFields() so the manifest can
    // replace the DB query without losing displayLogic / readonly / defaults.
    var fields = q(
      "SELECT f.Name AS label, c.ColumnName AS col, c.AD_Reference_ID AS ref," +
      " f.SeqNo AS seq, f.IsMandatory AS fMand, c.IsMandatory AS cMand," +
      " c.IsKey AS isKey, f.IsReadOnly AS ro, f.DisplayLogic AS dl," +
      " f.DefaultValue AS fDef, c.DefaultValue AS cDef," +
      " c.FieldLength AS len, c.IsIdentifier AS ident " +
      "FROM AD_Field f JOIN AD_Column c ON f.AD_Column_ID=c.AD_Column_ID " +
      "WHERE f.AD_Tab_ID=" + t.tabId +
      " AND f.IsDisplayed='Y' AND f.IsActive='Y' AND c.IsActive='Y' " +
      "ORDER BY f.SeqNo"
    );

    var outFields = [];
    for (var k = 0; k < fields.length; k++) {
      var f = fields[k];
      if (SYSTEM_COLS[f.col]) continue;           // multi-tenant/audit (§3)
      var type = TYPE_BY_REF[f.ref] || 'string';  // readability only; ad_ui maps ref→type
      if (type === 'button') continue;            // not a data field
      // Optional keys emitted only when truthy — keeps the payload small.
      var entry = {
        col: f.col,
        label: f.label,
        ref: Number(f.ref) || 10,                 // AD_Reference_ID — authoritative
        type: type,
        seq: f.seq
      };
      if (f.fMand === 'Y' || f.cMand === 'Y') entry.mandatory = true;
      if (f.ro === 'Y') entry.readonly = true;
      if (f.isKey === 'Y') entry.key = true;
      if (f.ident === 'Y') entry.identifier = true;
      if (f.dl) entry.dl = f.dl;                   // DisplayLogic expression
      var dflt = f.fDef || f.cDef;
      if (dflt) entry.def = dflt;
      if (f.len) entry.len = Number(f.len);
      // descriptions deliberately NOT carried — §3: help-panel lookup from ad_seed.db
      if (FK_TYPES[type]) {
        var tgt = fkTarget(f.col);
        if (tgt) entry.fk = tgt;
      }
      outFields.push(entry);
    }
    fieldTotal += outFields.length;

    var tabOut = { name: t.name, table: t.tbl, level: t.level, fields: outFields };
    if (fk) tabOut.fk = fk;
    outTabs.push(tabOut);
  }

  console.log('§MANIFEST win=' + winId + ' "' + winRow.Name + '" tabs=' +
    outTabs.length + ' fields=' + fieldTotal);

  return {
    name: winRow.Name,
    table: outTabs.length ? outTabs[0].table : null,
    tabs: outTabs
  };
}

// ── Main ─────────────────────────────────────────────────────────────────────
function main() {
  if (!fs.existsSync(DB)) {
    console.log('§MANIFEST FATAL: ad_seed.db not found at ' + DB);
    process.exit(1);
  }
  console.log('§MANIFEST source=' + DB);

  var windows = {};
  var totalFields = 0, totalTabs = 0;
  for (var i = 0; i < CURATED_WINDOWS.length; i++) {
    var cw = CURATED_WINDOWS[i];
    var w = buildWindow(cw.id);
    if (!w) continue;
    w.node = cw.node;
    windows[cw.id] = w;
    totalTabs += w.tabs.length;
    for (var t = 0; t < w.tabs.length; t++) totalFields += w.tabs[t].fields.length;
  }

  var manifest = {
    version: 1,
    generated: new Date().toISOString(),
    source: 'ad_seed.db',
    windows: windows,
    state_machine: BUSINESS_SEMANTICS.state_machine,
    downstream: BUSINESS_SEMANTICS.downstream,
    dropdowns: BUSINESS_SEMANTICS.dropdowns
  };

  var json = JSON.stringify(manifest, null, 2);
  fs.writeFileSync(OUT, json);

  var kb = (Buffer.byteLength(json) / 1024).toFixed(1);
  console.log('§MANIFEST DONE windows=' + Object.keys(windows).length +
    ' tabs=' + totalTabs + ' fields=' + totalFields + ' size=' + kb + 'KB');
  console.log('§MANIFEST out=' + OUT);
}

main();
