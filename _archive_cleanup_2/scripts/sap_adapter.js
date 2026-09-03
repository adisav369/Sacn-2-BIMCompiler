// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * sap_adapter.js — the SAP↔iDempiere migration adapter HYPOTHESIS (prompts/SAP_FOLD_POC.md Step 1).
 *   Spec: prompts/SAP_FOLD_POC.md (the asymptote falsifier) · docs/HolyGrail.md (migration solvent +
 *   the SAP boundary: "standard flows + extractable config; Z per engagement") · docs/ERP.md §0.12/§0.13/§0.17.
 *
 * STATUS: HYPOTHESIS, not a verified fold. This file is the SAP half of the cross-ERP data dictionary,
 * written BEFORE a real oracle exists — exactly as the Odoo "STARTER hypothesis" master-table map was
 * written before driving (see build/erp/odoo_fold.log). Every table/field below is a MAP TO VERIFY against
 * a real SAP export, NOT a trusted fact. The fold is NOT claimed until scripts/poc_sap_fold.js runs against
 * a real build/erp/sap_oracle.json (executed SAP rows) and emits a §SAP-FOLD verdict.
 *
 * CLEAN-ROOM (the hard part for SAP): SAP is CLOSED ABAP — no readable source, ORACLE-ONLY. This map is
 * built from SAP's PUBLIC data-dictionary table/field names (VBAK, ACDOCA, VBFA, …), never from ABAP source.
 * The adapter is inferred BLIND from output structure; the real behaviour is verified by diffing executed rows.
 *
 * NON-INVENT, hard rule: NO synthesised SAP rows, ever. A needed-but-absent capability is a NAMED finding
 * (code- or data-classified), NOT a fabrication. No oracle → poc_sap_fold.js stops at §SAP-ORACLE unavailable.
 *
 * The thesis is SHARP here precisely because S/4HANA's own redesign converged on our shape:
 *   • ACDOCA (Universal Journal) is already a single append-style line-item journal == our `journal` + op-log.
 *   • VBFA (document flow) is the derivation spine (§0.13) made an explicit predecessor/successor table.
 * So a system that redesigned ITSELF into "one journal + an explicit doc-flow graph" SHOULD fold cleanly —
 * confirmed or refuted on real rows, never assumed.
 */

// reuse the iDempiere/Odoo verb set verbatim — the falsifier asks if SAP needs MORE than these six.
var KNOWN_VERBS = ['CREATE_DOCUMENT', 'CREATE_LINE', 'SET_STATUS', 'ALLOCATE', 'MATCH', 'POST'];

// ── (A) SCHEMA MAP — SAP table → iDempiere 5-table bridge vocabulary (HYPOTHESIS, verify on export) ──
// "bridge" = which of the 5 projection tables; "doc_type" = the C_/M_ lingua-franca name (same interlingua
// every foreign ERP folds into). "key_fields" = the SAP columns we expect to read — VERIFY against the dump.
var SCHEMA_MAP = {
  // ── SD: sales order → delivery → billing ──
  // VBAK/VBAP carry typed columns[] (AD_GEN_FROM_DICTIONARY_SPEC §1 example) — the rest stay key_fields,
  // which the gen_ad expander renders as String + counts as untyped (visible debt, not silent).
  'VBAK': { bridge: 'documents',      doc_type: 'C_Order',     issotrx: 'Y', note: 'sales order header',  key_fields: ['VBELN', 'KUNNR', 'NETWR', 'WAERK', 'VBTYP'],
            columns: [ { name:'VBELN', ref:'String', len:10, key:true, identifier:true, label:'Sales Document' },
                       { name:'KUNNR', ref:'TableDir', len:10, label:'Sold-To' },
                       { name:'NETWR', ref:'Amount', len:0, label:'Net Value' },
                       { name:'WAERK', ref:'String', len:5, label:'Currency' },
                       { name:'VBTYP', ref:'List', len:1, label:'Doc Category' } ] },
  'VBAP': { bridge: 'document_lines', doc_type: 'C_OrderLine',               note: 'sales order item',    key_fields: ['VBELN', 'POSNR', 'MATNR', 'KWMENG', 'NETWR'],
            columns: [ { name:'VBELN', ref:'TableDir', len:10, key:true, label:'Sales Document' },
                       { name:'POSNR', ref:'Integer', len:6, identifier:true, label:'Item' },
                       { name:'MATNR', ref:'TableDir', len:18, label:'Material' },
                       { name:'KWMENG', ref:'Quantity', len:0, label:'Order Quantity' },
                       { name:'NETWR', ref:'Amount', len:0, label:'Net Value' } ] },
  'LIKP': { bridge: 'documents',      doc_type: 'M_InOut',                   note: 'delivery header',     key_fields: ['VBELN', 'KUNNR', 'VBTYP'] },
  'LIPS': { bridge: 'document_lines', doc_type: 'M_InOutLine', qty: 'movementqty', note: 'delivery item (goods issue)', key_fields: ['VBELN', 'POSNR', 'MATNR', 'LFIMG', 'VGBEL', 'VGPOS'] },
  'VBRK': { bridge: 'documents',      doc_type: 'C_Invoice',   issotrx: 'Y', note: 'billing header',      key_fields: ['VBELN', 'KUNRG', 'NETWR', 'MWSBK', 'WAERK', 'VBTYP'] },
  'VBRP': { bridge: 'document_lines', doc_type: 'C_InvoiceLine',             note: 'billing item',        key_fields: ['VBELN', 'POSNR', 'MATNR', 'FKIMG', 'NETWR', 'VGBEL'] },
  // ── FI: the accounting fold ──
  'BKPF': { bridge: 'journal',        doc_type: 'GL_Journal',  note: 'accounting doc header (ECC)',        key_fields: ['BUKRS', 'BELNR', 'GJAHR', 'BLART', 'AWKEY'] },
  'BSEG': { bridge: 'journal',        doc_type: 'Fact_Acct',   note: 'accounting doc line (ECC)',          key_fields: ['BUKRS', 'BELNR', 'GJAHR', 'BUZEI', 'SHKZG', 'DMBTR', 'HKONT', 'AUGBL'] },
  'ACDOCA':{ bridge: 'journal',       doc_type: 'Fact_Acct',   note: 'S/4HANA Universal Journal line (the strong claim: == our journal fold)', key_fields: ['RBUKRS', 'BELNR', 'GJAHR', 'DOCLN', 'RACCT', 'DRCRK', 'HSL', 'RLDNR', 'AWREF'] },
  // ── the derivation spine + settlement + inventory ──
  'VBFA': { bridge: '(flow-graph)',   doc_type: '(derivation-spine §0.13)', note: 'document flow: predecessor→successor edges — drives EVENT ORDER, not assumed', key_fields: ['VBELV', 'POSNV', 'VBELN', 'POSNN', 'VBTYP_N', 'RFMNG'] },
  'BSAD': { bridge: 'journal',        doc_type: 'C_AllocationHdr', note: 'cleared customer items — settlement (= ALLOCATE/MATCH via AUGBL clearing doc)', key_fields: ['BUKRS', 'KUNNR', 'BELNR', 'AUGBL', 'AUGDT', 'DMBTR', 'SHKZG'] },
  'MSEG': { bridge: 'items',          doc_type: 'M_Transaction', note: 'material doc segment (goods movement)', key_fields: ['MBLNR', 'ZEILE', 'MATNR', 'MENGE', 'SHKZG', 'BWART'] }
};

// ── (B) STATE / FLOW MAP — SAP transition → the generic cell's verb (HYPOTHESIS, VBFA-led) ──────
// SAP status lives in header/item status (VBUK/VBUP in ECC; status fields/objects JEST/JSTO) AND is made
// explicit by the VBFA edges. The EVENT ORDER is READ from VBFA (predecessor→successor), never assumed.
var STATE_MAP = [
  { sap: 'VBAK created→confirmed',     verb: 'SET_STATUS',      cell: 'C_Order:CO',         note: 'order doc pre-exists in export → status flip; status via VBUK/VBUP or JEST' },
  { sap: 'VBAK→LIKP (delivery, VBFA)', verb: 'CREATE_DOCUMENT', cell: 'M_InOut:CO',         note: '+ CREATE_LINE per LIPS; goods issue (WBSTK) = the stock movement (MSEG)' },
  { sap: 'LIKP→VBRK (billing, VBFA)',  verb: 'CREATE_DOCUMENT', cell: 'C_Invoice:CO',       note: '+ CREATE_LINE per VBRP' },
  { sap: 'VBRK→BKPF/BSEG|ACDOCA (FI)', verb: 'POST',            cell: 'C_Invoice:POST',     note: 'GL double-entry; kernel enforces ΣDR==ΣCR (§13.1); SAP dr/cr = SHKZG S/H or ACDOCA DRCRK' },
  { sap: 'BSEG clearing (AUGBL)',      verb: 'ALLOCATE',        cell: 'C_AllocationHdr:CO', note: 'payment/clearing = settlement; partial clearing = ALLOCATE(amount<total) [cf. Odoo f2]' }
  // multi-document / FK-absent settlement → MATCH (the §0.14 matcher), exactly as the Odoo 3-way (f5) used it.
];

// the wfmc the kernel dispatch needs — the SAME generic transitions iDempiere + Odoo use (no SAP specifics).
var WFMC = { transitions: [['DR', 'CO', 'CO'], ['CO', 'POST', 'CO']] };

// ── NAMED DIVERGENCES — the fold must RECONCILE these, not hide them (report as findings) ───────
// Each is classified up front as in-scope (standard, extractable) or out-of-scope (Z/config per engagement).
var NAMED_DIVERGENCES = [
  { id: 'd1', what: 'ACDOCA (S/4) vs BKPF+BSEG (ECC)', scope: 'standard', plan: 'normalizeGLLine handles either shape; ACDOCA-as-fold is the strong claim (§SAP-FOLD acdoca-as-fold)' },
  { id: 'd2', what: 'dr/cr is an INDICATOR (BSEG.SHKZG S/H, ACDOCA.DRCRK) + positive amount, not signed columns', scope: 'standard', plan: 'normalize to amtacctdr/amtacctcr; POST still owns only ΣDR==ΣCR' },
  { id: 'd3', what: 'account determination SD→FI (T030/VKOA, condition technique)', scope: 'standard-config', plan: 'DERIVE from extractable config like Odoo f1; standard pricing in scope, Z-conditions NOT' },
  { id: 'd4', what: 'new-GL parallel ledgers (ACDOCA.RLDNR)', scope: 'leading-ledger-only', plan: 'fold the leading ledger (0L); parallel ledgers NAMED, not folded' },
  { id: 'd5', what: 'CO-PA / profitability segments, customer Z-tables & Z-code', scope: 'OUT-OF-SCOPE', plan: 'explicitly excluded — "Z per engagement"; any Z-behaviour that does not fold IS the finding' },
  { id: 'd6', what: 'SAP status model (VBUK/VBUP header/item status, status objects JEST/JSTO)', scope: 'standard', plan: 'infer the (status,transition) from the data; map to SET_STATUS' }
];

// ── GL line normalizer — SAP dr/cr indicator → the kernel POST shape {account_id, amtacctdr, amtacctcr} ──
// Accepts EITHER an ACDOCA line (RACCT/DRCRK/HSL) or a BSEG line (HKONT/SHKZG/DMBTR). 'S'=Soll=debit,
// 'H'=Haben=credit. HYPOTHESIS — verify field names + sign convention on the real export.
function normalizeGLLine(row) {
  var isAcdoca = row.RACCT != null || row.DRCRK != null || row.HSL != null;
  var account = isAcdoca ? row.RACCT : row.HKONT;
  var ind = isAcdoca ? row.DRCRK : row.SHKZG;            // 'S' debit / 'H' credit
  var amt = Number(isAcdoca ? row.HSL : row.DMBTR);
  var dr = ind === 'S' ? Math.abs(amt) : 0;
  var cr = ind === 'H' ? Math.abs(amt) : 0;
  return { account_id: String(account), amtacctdr: dr, amtacctcr: cr, role: row.role || null, sap_ind: ind };
}

// ── build the per-document-event op-GROUPS from a REAL oracle (pure shape map; VBFA-led order) ──
// HYPOTHESIS path: this runs ONLY when poc_sap_fold.js is handed a real build/erp/sap_oracle.json. It
// invents nothing — every qty/amount/account is read from the executed SAP rows. The event ORDER follows
// the VBFA edges (order→delivery→billing→FI→clearing). UNVERIFIED until a real oracle exercises it.
function buildSapEvents(oracle) {
  var orderId = oracle.meta.order, dlvId = oracle.meta.delivery, billId = oracle.meta.billing;

  var shipLines = (oracle.delivery_items || []).map(function (l, i) {
    return { op_type: 'CREATE_LINE', table: 'M_InOutLine', source_line_id: String(l.MATNR), line_no: i + 1, m_product_id: l.MATNR, movementqty: l.LFIMG };
  });
  var invLines = (oracle.billing_items || []).map(function (l, i) {
    return { op_type: 'CREATE_LINE', table: 'C_InvoiceLine', source_line_id: String(l.MATNR), line_no: i + 1, m_product_id: l.MATNR, qtyinvoiced: l.FKIMG, linenetamt: l.NETWR };
  });
  // FI posting lines: ACDOCA if present, else BSEG. POST owns only ΣDR==ΣCR (account determination is host
  // glue, §13.1 — or DERIVED from config per d3, cf. Odoo f1).
  var glRows = oracle.acdoca_lines && oracle.acdoca_lines.length ? oracle.acdoca_lines : (oracle.bseg_lines || []);
  var postLines = glRows.map(normalizeGLLine);

  return {
    wfmc: WFMC,
    gl_source: (oracle.acdoca_lines && oracle.acdoca_lines.length) ? 'ACDOCA' : 'BSEG',
    events: [
      { name: 'confirm order', d: { docType: 'C_Order', action: 'CO', status: 'DR' },
        ops: [{ op_type: 'SET_STATUS', table: 'C_Order', id: orderId, doc_status: 'CO' }] },
      { name: 'deliver (goods issue)', d: { docType: 'M_InOut', action: 'CO', status: 'DR' },
        ops: [{ op_type: 'CREATE_DOCUMENT', table: 'M_InOut', source_id: orderId, doc_status: 'CO' }].concat(shipLines) },
      { name: 'billing', d: { docType: 'C_Invoice', action: 'CO', status: 'DR' },
        ops: [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: orderId, doc_status: 'CO' }].concat(invLines) },
      { name: 'FI posting', d: { docType: 'C_Invoice', action: 'POST', status: 'CO' },
        ops: [{ op_type: 'POST', table: 'C_Invoice', id: billId, lines: postLines, acctschema: 1 }] },
      { name: 'clearing (payment)', d: { docType: 'C_AllocationHdr', action: 'CO', status: 'DR' },
        ops: [{ op_type: 'ALLOCATE', payment_id: 'clr' + billId, invoice_id: billId, amount: oracle.meta.clearing_amount }] }
    ]
  };
}

// the witness lines this adapter is DESIGNED to produce once a real oracle exists (printed by the gated
// runner so the intent is documented even before the oracle drops in).
var PLANNED_WITNESSES = [
  '§SAP-FOLD chain order→delivery→billing→fi-posting→clearing mapped=5/5 missing=0',
  '§SAP-FOLD verbs used=[…] newVerbs=[…]  ← name any verb the STANDARD SAP flow needs that iDempiere/Odoo did not',
  '§SAP-FOLD diff matched=K missed=0 extra=0 vs sap_oracle agree=Y  (billed qty, FI dr/cr lines, clearing, delivered qty — to the cent)',
  '§SAP-FOLD acdoca-as-fold lines=N reproduced=Y  ← the strong claim: our journal fold reproduces SAP\'s Universal-Journal lines',
  '§SAP-FINDINGS …  ← every divergence NAMED, standard (in scope) vs Z/config (out of scope)'
];

// ── (C) SECOND SOURCE — SAP's OFFICIAL Flight Reference Scenario (a REAL, license-free oracle) ────
// github.com/SAP-samples/abap-platform-refscen-flight (SAP-maintained, Apache-2.0). The /DMO/ travel-
// booking RAP model is real SAP demo data populated by a deterministic-ish data generator (the legacy
// SAPBC_DATA_GENERATOR / managed /DMO/CL_FLIGHT_DATA_GENERATOR). It UNBLOCKS the IDES-licence problem for
// the DOCUMENT-LIFECYCLE half of the fold — but it is HONESTLY PARTIAL: the flight model has NO financial
// accounting (no GL, no double-entry, no billing/clearing). So it exercises ONLY three of the six verbs:
//   CREATE_DOCUMENT (Travel) · CREATE_LINE (Booking, + nested Booking-Supplement) · SET_STATUS (status machine)
// plus the price-AGGREGATION invariant (Travel.total_price == booking_fee + Σ flight_price + Σ supplement).
// It does NOT exercise POST / ALLOCATE / MATCH or the ACDOCA-as-fold claim — those still need an SD+FI source
// (S/4HANA Best-Practices appliance, GBI teaching dataset, or a real IDES export). State that every time.
// Field names below are the data-model HYPOTHESIS — VERIFY against the real /DMO/ export.
var SCHEMA_MAP_FLIGHT = {
  // Flight carries typed columns[] — the /DMO/ model is PUBLIC (Apache-2.0 refscen-flight), so the types
  // are documented fact, not a guess. These exercise gen_ad's typed path end-to-end (untyped=0 for Flight).
  '/DMO/TRAVEL':          { bridge: 'documents',      doc_type: 'C_Order',     note: 'travel = the document/aggregate root', key_fields: ['travel_id', 'agency_id', 'customer_id', 'begin_date', 'end_date', 'booking_fee', 'total_price', 'currency_code', 'overall_status'],
            columns: [ { name:'travel_id', ref:'Integer', len:8, key:true, identifier:true, label:'Travel' },
                       { name:'agency_id', ref:'TableDir', len:8, label:'Agency' },
                       { name:'customer_id', ref:'TableDir', len:8, label:'Customer' },
                       { name:'begin_date', ref:'Date', len:7, label:'Begin Date' },
                       { name:'end_date', ref:'Date', len:7, label:'End Date' },
                       { name:'booking_fee', ref:'Amount', len:0, label:'Booking Fee' },
                       { name:'total_price', ref:'Amount', len:0, label:'Total Price' },
                       { name:'currency_code', ref:'String', len:5, label:'Currency' },
                       { name:'overall_status', ref:'List', len:1, label:'Status' } ] },
  '/DMO/BOOKING':         { bridge: 'document_lines', doc_type: 'C_OrderLine',  qty: 'one', note: 'booking = a line on the travel; amount = flight_price', key_fields: ['travel_id', 'booking_id', 'customer_id', 'carrier_id', 'connection_id', 'flight_date', 'flight_price', 'currency_code', 'booking_status'],
            columns: [ { name:'travel_id', ref:'TableDir', len:8, key:true, label:'Travel' },
                       { name:'booking_id', ref:'Integer', len:6, identifier:true, label:'Booking' },
                       { name:'customer_id', ref:'TableDir', len:8, label:'Customer' },
                       { name:'carrier_id', ref:'String', len:3, label:'Airline' },
                       { name:'connection_id', ref:'String', len:4, label:'Flight Number' },
                       { name:'flight_date', ref:'Date', len:7, label:'Flight Date' },
                       { name:'flight_price', ref:'Amount', len:0, label:'Flight Price' },
                       { name:'currency_code', ref:'String', len:5, label:'Currency' },
                       { name:'booking_status', ref:'List', len:1, label:'Status' } ] },
  '/DMO/BOOKING_SUPPLEMENT': { bridge: 'document_lines', doc_type: 'C_OrderLine', note: 'supplement = a NESTED line under a booking (BOM recursion)', key_fields: ['travel_id', 'booking_id', 'booking_supplement_id', 'supplement_id', 'price', 'currency_code'],
            columns: [ { name:'travel_id', ref:'TableDir', len:8, key:true, label:'Travel' },
                       { name:'booking_id', ref:'Integer', len:6, key:true, label:'Booking' },
                       { name:'booking_supplement_id', ref:'Integer', len:6, identifier:true, label:'Supplement' },
                       { name:'supplement_id', ref:'String', len:10, label:'Product' },
                       { name:'price', ref:'Amount', len:0, label:'Price' },
                       { name:'currency_code', ref:'String', len:5, label:'Currency' } ] }
};
// status machine (HYPOTHESIS — verify the codes on the export): overall O(open)/A(accepted)/X(cancelled);
// booking N(new)/B(booked)/X(cancelled).
var STATE_MAP_FLIGHT = [
  { sap: 'travel overall_status O→A', verb: 'SET_STATUS',      cell: 'C_Order:CO', note: 'accept travel = status flip' },
  { sap: 'travel created (rows exist)', verb: 'CREATE_DOCUMENT', cell: 'C_Order:CO', note: '+ CREATE_LINE per booking (+ nested supplement)' }
];
// the document-lifecycle subset this scenario CAN witness (no FI verbs — stated, not hidden).
function buildFlightEvents(oracle) {
  var travelId = oracle.meta.travel_id;
  var bookingLines = (oracle.bookings || []).map(function (b, i) {
    return { op_type: 'CREATE_LINE', table: 'C_OrderLine', source_line_id: String(b.booking_id), line_no: i + 1, m_product_id: b.connection_id, amount: b.flight_price, currency: b.currency_code };
  });
  var supplLines = (oracle.supplements || []).map(function (s, i) {
    return { op_type: 'CREATE_LINE', table: 'C_OrderLine', source_line_id: String(s.booking_id) + '-' + String(s.booking_supplement_id), line_no: 1000 + i, m_product_id: s.supplement_id, amount: s.price, currency: s.currency_code };
  });
  return {
    wfmc: WFMC,
    fold_scope: 'document-lifecycle ONLY (CREATE_DOCUMENT/CREATE_LINE/SET_STATUS) — NO POST/ALLOCATE/MATCH (flight model has no FI)',
    events: [
      { name: 'create travel', d: { docType: 'C_Order', action: 'CO', status: 'DR' },
        ops: [{ op_type: 'CREATE_DOCUMENT', table: 'C_Order', source_id: travelId, doc_status: 'CO' }].concat(bookingLines).concat(supplLines) },
      { name: 'accept travel', d: { docType: 'C_Order', action: 'CO', status: 'DR' },
        ops: [{ op_type: 'SET_STATUS', table: 'C_Order', id: travelId, doc_status: 'CO' }] }
    ]
  };
}

module.exports = {
  KNOWN_VERBS: KNOWN_VERBS, SCHEMA_MAP: SCHEMA_MAP, STATE_MAP: STATE_MAP, WFMC: WFMC,
  NAMED_DIVERGENCES: NAMED_DIVERGENCES, normalizeGLLine: normalizeGLLine, buildSapEvents: buildSapEvents,
  PLANNED_WITNESSES: PLANNED_WITNESSES,
  SCHEMA_MAP_FLIGHT: SCHEMA_MAP_FLIGHT, STATE_MAP_FLIGHT: STATE_MAP_FLIGHT, buildFlightEvents: buildFlightEvents
};
