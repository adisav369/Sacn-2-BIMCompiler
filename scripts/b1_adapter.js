// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * b1_adapter.js — the SAP **Business One** (B1) ↔ iDempiere migration adapter (the SMB SAP target).
 *   Spec: docs/HolyGrail.md (migration solvent) · docs/ERP.md §0.12/§0.13/§0.17 · prompts/SAP_FOLD_POC.md.
 *
 * WHY B1, NOT S/4: B1 is SAP's SMB product — the SAME market as iDempiere/Odoo — and its model folds MORE
 * cleanly than S/4HANA's: every transaction auto-posts a plain double-entry Journal Entry (OJDT header /
 * JDT1 rows, with literal Debit + Credit columns), and masters/docs use the clean O<entity>/<entity>1
 * header/line convention. Extraction is the B1 **Service Layer** (OData/REST over HTTPS) or the DI-API —
 * HTTP, exactly like Odoo's JSON-RPC, so the install-side agent mirrors odoo_agent (delegate-to-install,
 * the browser never touches B1). (Refs: help.sap.com SDK tables OJDT/JDT1/OCRD/OITM/OINV/INV1; Service Layer.)
 *
 * STATUS: HYPOTHESIS (no live B1 to witness yet — we have live Odoo + iDempiere PG, no B1). Every table/field
 * is a MAP TO VERIFY against a real B1 Service-Layer export. NON-INVENT: no synthesised B1 rows — the fold is
 * claimed only when scripts/poc_b1_fold.js runs a REAL build/erp/b1_oracle.json. The reuse claim is the point:
 *   the SAME six verbs (iDempiere/Odoo/SAP-flight) fold B1 too — newVerbs=[] is the falsifiable prediction.
 */

// the universal verb set — the falsifier asks if B1 needs MORE than these six. (It should not.)
var KNOWN_VERBS = ['CREATE_DOCUMENT', 'CREATE_LINE', 'SET_STATUS', 'ALLOCATE', 'MATCH', 'POST'];

// ── (A) SCHEMA MAP — B1 table → iDempiere 5-table bridge vocabulary (HYPOTHESIS, verify on a SL export) ──
// B1 convention: O<X> = object/header table; <X>1 = its line table. "bridge" = which projection table;
// "doc_type" = the C_/M_ lingua-franca name every foreign ERP folds into (same interlingua as Odoo/SAP).
var SCHEMA_MAP = {
  // ── masters ──
  'OCRD': { bridge: 'documents',      doc_type: 'C_BPartner',  note: 'Business Partner master (customers/vendors/leads)', key_fields: ['CardCode', 'CardName', 'CardType', 'Currency', 'GroupCode'] },
  'OITM': { bridge: 'documents',      doc_type: 'M_Product',   note: 'Item master (products/services)',                   key_fields: ['ItemCode', 'ItemName', 'ItmsGrpCod', 'InvntItem', 'SellItem'] },
  'OACT': { bridge: 'documents',      doc_type: 'C_ElementValue', note: 'Chart of Accounts (G/L accounts)',               key_fields: ['AcctCode', 'AcctName', 'Postable', 'GroupMask'] },
  // ── SD: sales order → delivery → A/R invoice (header/line pairs) ──
  'ORDR': { bridge: 'documents',      doc_type: 'C_Order',     issotrx: 'Y', note: 'Sales Order header',  key_fields: ['DocEntry', 'DocNum', 'CardCode', 'DocTotal', 'DocCur', 'DocStatus'] },
  'RDR1': { bridge: 'document_lines', doc_type: 'C_OrderLine',               note: 'Sales Order line',    key_fields: ['DocEntry', 'LineNum', 'ItemCode', 'Quantity', 'LineTotal'] },
  'ODLN': { bridge: 'documents',      doc_type: 'M_InOut',                   note: 'Delivery header (goods issue)', key_fields: ['DocEntry', 'DocNum', 'CardCode', 'DocStatus'] },
  'DLN1': { bridge: 'document_lines', doc_type: 'M_InOutLine', qty: 'movementqty', note: 'Delivery line', key_fields: ['DocEntry', 'LineNum', 'ItemCode', 'Quantity', 'BaseEntry', 'BaseLine'] },
  'OINV': { bridge: 'documents',      doc_type: 'C_Invoice',   issotrx: 'Y', note: 'A/R Invoice header',  key_fields: ['DocEntry', 'DocNum', 'CardCode', 'DocTotal', 'VatSum', 'DocCur', 'DocStatus', 'TransId'] },
  'INV1': { bridge: 'document_lines', doc_type: 'C_InvoiceLine',             note: 'A/R Invoice line',    key_fields: ['DocEntry', 'LineNum', 'ItemCode', 'Quantity', 'LineTotal', 'BaseEntry'] },
  // ── FI: B1's journal = OJDT/JDT1 (the auto-posted Journal Entry); the clean double-entry fold ──
  'OJDT': { bridge: 'journal',        doc_type: 'GL_Journal',  note: 'Journal Entry header — one per posted transaction', key_fields: ['TransId', 'TransType', 'RefDate', 'BaseRef'] },
  'JDT1': { bridge: 'journal',        doc_type: 'Fact_Acct',   note: 'Journal Entry rows — literal Debit + Credit columns (the strong claim: == our journal fold)', key_fields: ['TransId', 'Line_ID', 'Account', 'ShortName', 'Debit', 'Credit', 'role'] },
  // ── settlement (incoming payment) ──
  'ORCT': { bridge: 'journal',        doc_type: 'C_AllocationHdr', note: 'Incoming Payment — clears the invoice (= ALLOCATE/MATCH)', key_fields: ['DocEntry', 'DocNum', 'CardCode', 'DocTotal', 'TransId'] }
};

// ── (B) STATE / FLOW MAP — B1 transition → the generic cell's verb (HYPOTHESIS) ──────────────────
// B1 base/target links (RDR1.BaseEntry / DLN1.BaseEntry / INV1.BaseEntry) ARE the derivation spine
// (predecessor→successor), the equivalent of SAP VBFA — they drive the EVENT ORDER, never assumed.
var STATE_MAP = [
  { b1: 'ORDR open→confirmed',          verb: 'SET_STATUS',      cell: 'C_Order:CO',         note: 'order doc pre-exists in export → status flip (DocStatus O→C)' },
  { b1: 'ORDR→ODLN (delivery)',         verb: 'CREATE_DOCUMENT', cell: 'M_InOut:CO',         note: '+ CREATE_LINE per DLN1; goods issue' },
  { b1: 'ODLN→OINV (A/R invoice)',      verb: 'CREATE_DOCUMENT', cell: 'C_Invoice:CO',       note: '+ CREATE_LINE per INV1' },
  { b1: 'OINV auto-posts OJDT/JDT1',    verb: 'POST',            cell: 'C_Invoice:POST',     note: 'GL double-entry; kernel enforces ΣDR==ΣCR (§13.1); B1 carries explicit Debit/Credit columns' },
  { b1: 'ORCT clears OINV (payment)',   verb: 'ALLOCATE',        cell: 'C_AllocationHdr:CO', note: 'incoming payment = settlement; partial = ALLOCATE(amount<total)' }
];

// the wfmc the kernel dispatch needs — the SAME generic transitions iDempiere/Odoo/SAP use (no B1 specifics).
var WFMC = { transitions: [['DR', 'CO', 'CO'], ['CO', 'POST', 'CO']] };

// ── GL line normalizer — B1 JDT1 row → the kernel POST shape {account_id, amtacctdr, amtacctcr} ──
// B1 is the EASY case: JDT1 carries literal Debit and Credit numeric columns (no S/H indicator like SAP, no
// signed single column like Odoo). One side is 0. HYPOTHESIS — verify field names on the real SL export.
function normalizeJDT1Line(row) {
  var dr = Number(row.Debit || 0), cr = Number(row.Credit || 0);
  return { account_id: String(row.Account), amtacctdr: dr, amtacctcr: cr, role: row.role || null, party: row.ShortName || null };
}

// ── build the per-document-event op-GROUPS from a REAL B1 oracle (pure shape map; base-link order) ──
// Runs ONLY on a real build/erp/b1_oracle.json. Invents nothing — every qty/amount/account is a recorded
// B1 row. Event order: order→delivery→invoice→post(→clear), following the BaseEntry links.
function buildB1Events(oracle) {
  var so = oracle.meta.order, dlv = oracle.meta.delivery, inv = oracle.meta.invoice;

  var shipLines = (oracle.delivery_lines || []).map(function (l, i) {
    return { op_type: 'CREATE_LINE', table: 'M_InOutLine', source_line_id: String(l.ItemCode), line_no: i + 1, m_product_id: l.ItemCode, movementqty: l.Quantity };
  });
  var invLines = (oracle.invoice_lines || []).map(function (l, i) {
    return { op_type: 'CREATE_LINE', table: 'C_InvoiceLine', source_line_id: String(l.ItemCode), line_no: i + 1, m_product_id: l.ItemCode, qtyinvoiced: l.Quantity, linenetamt: l.LineTotal };
  });
  var postLines = (oracle.journal_lines || []).map(normalizeJDT1Line);

  var events = [
    { name: 'confirm order', d: { docType: 'C_Order', action: 'CO', status: 'DR' },
      ops: [{ op_type: 'SET_STATUS', table: 'C_Order', id: so, doc_status: 'CO' }] },
    { name: 'deliver (goods issue)', d: { docType: 'M_InOut', action: 'CO', status: 'DR' },
      ops: [{ op_type: 'CREATE_DOCUMENT', table: 'M_InOut', source_id: so, doc_status: 'CO' }].concat(shipLines) },
    { name: 'A/R invoice', d: { docType: 'C_Invoice', action: 'CO', status: 'DR' },
      ops: [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: so, doc_status: 'CO' }].concat(invLines) },
    { name: 'post journal (OJDT/JDT1)', d: { docType: 'C_Invoice', action: 'POST', status: 'CO' },
      ops: [{ op_type: 'POST', table: 'C_Invoice', id: inv, lines: postLines, acctschema: 1 }] }
  ];
  if (oracle.meta.payment_amount != null) {
    events.push({ name: 'incoming payment (clear)', d: { docType: 'C_AllocationHdr', action: 'CO', status: 'DR' },
      ops: [{ op_type: 'ALLOCATE', payment_id: 'rct' + inv, invoice_id: inv, amount: oracle.meta.payment_amount }] });
  }
  return { wfmc: WFMC, gl_source: 'OJDT/JDT1', events: events };
}

module.exports = {
  KNOWN_VERBS: KNOWN_VERBS, SCHEMA_MAP: SCHEMA_MAP, STATE_MAP: STATE_MAP, WFMC: WFMC,
  normalizeJDT1Line: normalizeJDT1Line, buildB1Events: buildB1Events
};
