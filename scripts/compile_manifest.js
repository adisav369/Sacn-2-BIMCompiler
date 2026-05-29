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

// ── P2: WfMC state machine — the iDempiere document engine (cited, not invented) ─
// Oracle: org.compiere.process.DocumentEngine (prepareIt/completeIt/voidIt/closeIt/
// reActivateIt/reverseCorrectIt/reverseAccrualIt + getValidActions). Status-changing
// transitions; AP/RJ/PO are status-neutral self-loops. Verbs are EXTRACTED from
// AD_Ref_List ref 135; the legal (from,action,to) wiring is this cited spec.
var WFMC_TRANSITIONS = [
  ['DR', 'PR', 'IP'],  // prepareIt: Drafted -> In Progress
  ['IN', 'PR', 'IP'],  // re-prepare an Invalid doc
  ['DR', 'CO', 'CO'],  // completeIt direct from Draft
  ['IP', 'CO', 'CO'],  // completeIt
  ['WC', 'CO', 'CO'],  // Wait-Complete -> Complete
  ['IP', 'WC', 'WC'],  // -> Waiting Confirmation
  ['DR', 'VO', 'VO'],  // voidIt
  ['IP', 'VO', 'VO'],
  ['IN', 'VO', 'VO'],
  ['CO', 'VO', 'VO'],
  ['WC', 'VO', 'VO'],
  ['WP', 'VO', 'VO'],
  ['DR', 'IN', 'IN'],  // invalidateIt
  ['IP', 'IN', 'IN'],
  ['IN', 'XL', 'IP'],  // unlockIt: Invalid -> In Progress
  ['CO', 'CL', 'CL'],  // closeIt
  ['CO', 'RE', 'IP'],  // reActivateIt
  ['CO', 'RC', 'RE'],  // reverseCorrectIt
  ['CO', 'RA', 'RE'],  // reverseAccrualIt
  ['IP', 'AP', 'IP'],  // approveIt   (sets IsApproved; status neutral)
  ['IP', 'RJ', 'IP'],  // rejectIt    (status neutral)
  ['CO', 'PO', 'CO']   // postIt      (status neutral)
];

// DocBaseType -> lifecycle base table. Oracle: iDempiere DocBaseType constants
// (org.compiere.model.X_C_DocType / MDocType). Unmapped types -> baseTable:null.
var DOC_BASE_TABLE = {
  SOO: 'C_Order', POO: 'C_Order', POR: 'C_Order', MQO: 'C_Order',
  ARI: 'C_Invoice', ARC: 'C_Invoice', API: 'C_Invoice', APC: 'C_Invoice', ARF: 'C_Invoice',
  ARR: 'C_Payment', APP: 'C_Payment',
  MMS: 'M_InOut', MMR: 'M_InOut',
  MMI: 'M_Inventory', MMM: 'M_Movement', MMP: 'M_Production',
  GLJ: 'GL_Journal', GLD: 'GL_JournalBatch',
  CMA: 'C_Cash', CMB: 'C_BankStatement', CMC: 'C_Cash',
  MOF: 'PP_Order', MOP: 'PP_Order', MCC: 'PP_Cost_Collector',
  DOO: 'DD_Order', PJI: 'C_ProjectIssue', HRP: 'HR_Process',
  FAA: 'A_Asset_Addition', FAD: 'A_Asset_Disposed',
  MXI: 'M_MatchInv', MXP: 'M_MatchPO'
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
    " t.SeqNo AS seq, tb.TableName AS tbl, t.IsSingleRow AS single," +
    " t.IsReadOnly AS ro, t.WhereClause AS wc, t.OrderByClause AS ob " +
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

    var tabOut = {
      name: t.name, table: t.tbl, level: t.level, seq: t.seq, fields: outFields
    };
    if (t.single === 'Y') tabOut.singleRow = true;
    if (t.ro === 'Y') tabOut.readonly = true;
    if (t.wc) tabOut.where = t.wc;
    if (t.ob) tabOut.orderBy = t.ob;
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

// ── P2: compile the shared WfMC machine + per-DocType definitions ────────────
// All 52 DocTypes share the same iDempiere document engine (the legal-cell graph);
// what differs per DocType is identity (baseTable/docBaseType/isSOTrx) and, later,
// the HANDLER behavior at each cell (P3b, §18.4/§18.6) — not the status wiring.
function buildWfmc() {
  var statusNames = {}, actionNames = {};
  q("SELECT Value v, Name n FROM AD_Ref_List WHERE AD_Reference_ID=131 AND IsActive='Y'")
    .forEach(function (r) { statusNames[r.v] = r.n; });
  q("SELECT Value v, Name n FROM AD_Ref_List WHERE AD_Reference_ID=135 AND IsActive='Y'")
    .forEach(function (r) { if (r.v !== '--') actionNames[r.v] = r.n; });
  // states = real DocStatus values (drop the synthetic '??' Unknown). Closure of the
  // transition table is checked by the harness, not asserted here.
  var states = Object.keys(statusNames).filter(function (s) { return s !== '??'; });
  var trans = WFMC_TRANSITIONS.filter(function (t) {
    return states.indexOf(t[0]) >= 0 && states.indexOf(t[2]) >= 0 && actionNames[t[1]];
  });
  return { states: states, statusNames: statusNames, actions: actionNames, transitions: trans };
}

function buildDocTypes() {
  // C_DocType_ID=0 is the "** New **" sentinel (blank DocBaseType, no process) —
  // not a real DocType; exclude it.
  var rows = q("SELECT C_DocType_ID id, Name name, DocBaseType bt, IsSOTrx so " +
    "FROM C_DocType WHERE IsActive='Y' AND C_DocType_ID>0 ORDER BY C_DocType_ID");
  var unmapped = 0;
  var out = rows.map(function (r) {
    var base = DOC_BASE_TABLE[r.bt] || null;
    if (!base) unmapped++;
    return { id: r.id, name: r.name, docBaseType: r.bt, baseTable: base, isSOTrx: r.so === 'Y' };
  });
  console.log('§MANIFEST doctype baseTable unmapped=' + unmapped + '/' + out.length +
    ' (exotic DocBaseTypes -> null; spine resolved)');
  return out;
}

// Derivation graph among lifecycle (document) tables, oriented forward + back-edges
// tagged settlement. Reuses §BOM_TEST classification (witness test_bom_theory T2/T3).
function buildDerivation() {
  var nameByLc = {};
  q("SELECT TableName n FROM AD_Table").forEach(function (r) { nameByLc[r.n.toLowerCase()] = r.n; });
  var isDoc = {};
  q("SELECT DISTINCT t.TableName n FROM AD_Column c JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.ColumnName='DocStatus'")
    .forEach(function (r) { isDoc[r.n] = true; });
  var contain = {};
  (function () {
    var rows = q("SELECT t.AD_Window_ID w, t.SeqNo s, t.TabLevel lvl, tb.TableName tbl " +
      "FROM AD_Tab t JOIN AD_Table tb ON t.AD_Table_ID=tb.AD_Table_ID WHERE t.IsActive='Y' ORDER BY t.AD_Window_ID, t.SeqNo");
    var byWin = {}; rows.forEach(function (r) { (byWin[r.w] = byWin[r.w] || []).push(r); });
    Object.keys(byWin).forEach(function (w) {
      var stack = {};
      byWin[w].forEach(function (r) {
        stack[r.lvl] = r.tbl;
        if (r.lvl > 0 && stack[r.lvl - 1] && stack[r.lvl - 1] !== r.tbl) contain[r.tbl + '>' + stack[r.lvl - 1]] = true;
      });
    });
  })();
  var SYS = { ad_client_id: 1, ad_org_id: 1, createdby: 1, updatedby: 1 };
  // §3: dictionary / reporting views / temp tables are COMPILER input, never runtime.
  // Without this, view tables (RV_*, *_v) and temp tables (T_*) carry DocStatus and
  // pollute the lifecycle derivation graph.
  function excluded(t) { return /^AD_/.test(t) || /_Trl$/.test(t) || /^RV_/.test(t) || /_v$/i.test(t) || /^T_/.test(t); }
  var fk = q("SELECT t.TableName src, c.ColumnName col FROM AD_Column c JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.AD_Reference_ID IN (18,19,30) AND c.IsActive='Y'");
  // derivation edge src->tgt means src has FK tgt_ID; lineage flows tgt(upstream)->src.
  var fwd = {};                         // forward adjacency: upstream -> [downstream]
  var edgeSeen = {};
  fk.forEach(function (f) {
    if (SYS[f.col.toLowerCase()]) return;
    if (!/_ID$/i.test(f.col)) return;
    var tgt = nameByLc[f.col.replace(/_ID$/i, '').toLowerCase()];
    if (!tgt || tgt === f.src) return;
    if (excluded(f.src) || excluded(tgt)) return;       // §3 compiler/view/temp, not runtime
    if (contain[f.src + '>' + tgt]) return;             // containment, not derivation
    if (!(isDoc[f.src] && isDoc[tgt])) return;          // doc<->doc only
    var k = tgt + '->' + f.src; if (edgeSeen[k]) return; edgeSeen[k] = true;
    (fwd[tgt] = fwd[tgt] || []).push(f.src);
  });
  // DFS classify: edge to a GRAY node = back-edge = settlement; rest = lineage.
  Object.keys(fwd).forEach(function (k) { fwd[k].sort(); });
  var color = {}, lineage = [], settlement = [];
  var nodes = Object.keys(fwd).sort();
  function dfs(u) {
    color[u] = 1;
    (fwd[u] || []).forEach(function (v) {
      if (color[v] === 1) settlement.push([u, v]);       // back-edge
      else { lineage.push([u, v]); if (!color[v]) dfs(v); }
    });
    color[u] = 2;
  }
  nodes.forEach(function (n) { if (!color[n]) dfs(n); });
  var downstream = {};
  lineage.forEach(function (e) { (downstream[e[0]] = downstream[e[0]] || []).push(e[1]); });
  Object.keys(downstream).forEach(function (k) { downstream[k].sort(); });
  console.log('§MANIFEST downstream tables=' + Object.keys(downstream).length +
    ' lineageEdges=' + lineage.length + ' settlement=' + settlement.length);
  return { downstream: downstream, settlement: settlement };
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

  // P2: compile WfMC machine + per-DocType definitions + extracted derivation graph.
  var wfmc = buildWfmc();
  var doctypes = buildDocTypes();
  var deriv = buildDerivation();
  console.log('§MANIFEST doctypes=' + doctypes.length + ' transitions=' + wfmc.transitions.length +
    ' states=' + wfmc.states.length);

  var manifest = {
    version: 2,
    generated: new Date().toISOString(),
    source: 'ad_seed.db',
    windows: windows,
    // P2 additive payload (all 52 DocTypes share `wfmc`; identity differs per type):
    wfmc: wfmc,
    doctypes: doctypes,
    downstream: deriv.downstream,            // EXTRACTED, replaces the §4 hardcode (superset shape)
    settlement: deriv.settlement,            // tagged back-edges (excluded from acyclicity)
    // legacy P0 keys kept for compatibility (no consumer today, behavior-preserving):
    state_machine: BUSINESS_SEMANTICS.state_machine,
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
