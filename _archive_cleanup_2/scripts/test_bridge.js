#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * test_bridge.js — ⚠ DO NOT REMOVE — Scope guard
 * Scope: the PB gate (prompts/ERP_KERNEL_BUILD.md §PB, docs/ERP.md §0/§0.1).
 *   Proves the EXPLICIT ad_table_map + 5-table schema + ad_data swap layer:
 *     A. §BRIDGE map  — every business edge maps, unmapped=0 (the 36 now slotted).
 *     B. §BRIDGE roundtrip — C_Order saved via ad_data lands in documents+metadata
 *        and reads back to an identical legacy field set.
 *     C. Lineage — Order->Invoice via source_id; InvoiceLine->OrderLine via source_line_id.
 *     D. Match — M_MatchInv -> document_lines with match_type + both line refs.
 *   Read the §BRIDGE log; it makes the call. Run before any 5-table deploy.
 *
 * Deterministic. Extract-only (facts from ad_seed.db; map from ad_table_map.js).
 */
'use strict';
var path = require('path');
var fs = require('fs');
var execFileSync = require('child_process').execFileSync;
var initSqlJs = require(path.join(process.env.HOME, 'bim-ootb', 'node_modules', 'sql.js'));

var VIEWER = path.join(process.env.HOME, 'bim-ootb', 'viewer');
var DB = process.env.AD_SEED_DB || path.join(VIEWER, 'ad_seed.db');
var ADTableMap = require(path.join(VIEWER, 'ad_table_map.js'));
var ADData = require(path.join(VIEWER, 'ad_data.js'));

function L(m) { console.log('§BRIDGE ' + m); }
function q(sql) {
  var o = execFileSync('sqlite3', [DB, '-json', sql],
    { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim();
  return o ? JSON.parse(o) : [];
}
var fail = 0;
function ok(c, m) { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fail++; }

// ── A. Map completeness with the EXPLICIT map ────────────────────────────────
// Same classification as test_5table_bom.js, but slotOf = ADTableMap.slotOf and
// representable() gains the two §0.1 refinements the explicit slots expose.
function representable(kind, s, tg) {
  if (kind === 'derivation') return s === 'documents' && (tg === 'documents' || tg === 'journal'); // (b) posting
  if (kind === 'self') return true;
  if (kind === 'containment') {
    return (s === 'document_lines' && tg === 'documents') ||
           (s === 'document_lines' && tg === 'document_lines') ||
           (s === 'document_lines' && tg === 'items') ||      // (a) line cites master
           (s === 'documents' && tg === 'documents') ||
           (s === 'documents' && tg === 'containers') ||
           (s === 'documents' && tg === 'items') ||
           (s === 'items' && tg === 'containers') ||
           (s === 'items' && tg === 'items') ||
           (s === 'journal' && tg === 'journal') ||
           (s === 'containers' && tg === 'containers');
  }
  return true; // citation: always a metadata reference
}

function mapCompleteness() {
  var SYS = { ad_client_id: 1, ad_org_id: 1, createdby: 1, updatedby: 1 };
  var nameByLc = {};
  q("SELECT TableName AS name FROM AD_Table").forEach(function (r) { nameByLc[r.name.toLowerCase()] = r.name; });
  var isDoc = {};
  q("SELECT DISTINCT t.TableName n FROM AD_Column c JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.ColumnName='DocStatus'")
    .forEach(function (r) { isDoc[r.n] = true; });
  var contain = {};
  (function () {
    var rows = q("SELECT t.AD_Window_ID w, t.SeqNo s, t.TabLevel lvl, tb.TableName tbl " +
      "FROM AD_Tab t JOIN AD_Table tb ON t.AD_Table_ID=tb.AD_Table_ID WHERE t.IsActive='Y' " +
      "ORDER BY t.AD_Window_ID, t.SeqNo");
    var byWin = {}; rows.forEach(function (r) { (byWin[r.w] = byWin[r.w] || []).push(r); });
    Object.keys(byWin).forEach(function (w) {
      var stack = {};
      byWin[w].forEach(function (r) {
        stack[r.lvl] = r.tbl;
        if (r.lvl > 0 && stack[r.lvl - 1] && stack[r.lvl - 1] !== r.tbl)
          contain[r.tbl + '>' + stack[r.lvl - 1]] = true;
      });
    });
  })();
  var facts = { isDoc: isDoc, contain: contain };
  function slot(t) { return ADTableMap.slotOf(t, facts); }

  var fk = q("SELECT t.TableName src, c.ColumnName col FROM AD_Column c " +
    "JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.AD_Reference_ID IN (18,19,30) AND c.IsActive='Y'");
  function resolve(col) { var b = /_ID$/i.test(col) ? col.replace(/_ID$/i, '') : null; return b ? nameByLc[b.toLowerCase()] || null : null; }

  var counts = { containment: 0, derivation: 0, citation: 0, self: 0 };
  var mapped = 0, unmappable = [], docTypes = {};
  fk.forEach(function (f) {
    if (SYS[f.col.toLowerCase()]) return;
    var tgt = resolve(f.col); if (!tgt) return;
    var ss = slot(f.src), ts = slot(tgt);
    if (ss === 'compiler' || ts === 'compiler') return;
    if (ss === 'documents') docTypes[f.src] = true;
    if (ts === 'documents') docTypes[tgt] = true;
    var kind;
    if (tgt === f.src) kind = 'self';
    else if (contain[f.src + '>' + tgt]) kind = 'containment';
    else if (isDoc[f.src] && isDoc[tgt]) kind = 'derivation';
    else kind = 'citation';
    counts[kind]++;
    if (representable(kind, ss, ts)) mapped++;
    else unmappable.push(kind + ' ' + f.src + '(' + ss + ')->' + tgt + '(' + ts + ')');
  });

  var manifestPath = path.join(VIEWER, 'manifest.json');
  var windows = fs.existsSync(manifestPath)
    ? Object.keys(JSON.parse(fs.readFileSync(manifestPath, 'utf8')).windows || {}).length : 7;

  L('--- A. map completeness (explicit ad_table_map) ---');
  L('business edges=' + (mapped + unmappable.length) + ' (containment=' + counts.containment +
    ' derivation=' + counts.derivation + ' citation=' + counts.citation + ' self=' + counts.self + ')');
  unmappable.slice(0, 40).forEach(function (u) { L('  UNMAPPABLE ' + u); });
  L('map windows=' + windows + ' docTypes=' + Object.keys(docTypes).length + ' unmapped=' + unmappable.length);
  ok(unmappable.length === 0, 'A: unmapped=0 (every classified edge representable)');
  return { windows: windows, docTypes: Object.keys(docTypes).length };
}

// ── B/C/D. round-trip + lineage + match through the real ad_data bridge ──────
function runtimeProof(SQL) {
  var db = new SQL.Database();
  db.run(fs.readFileSync(path.join(VIEWER, 'schema_5table.sql'), 'utf8'));
  ADData.useBridge(ADTableMap);

  // B. C_Order round-trip — domain fields into documents.metadata, structural into columns
  var order = {
    C_Order_ID: 1001, DocumentNo: 'SO-1001', DocStatus: 'DR',
    C_BPartner_ID: 117, M_Warehouse_ID: 103, GrandTotal: 4250.50,
    Description: 'PB round-trip witness', IsSOTrx: 'Y'
  };
  var saved = ADData.saveRecord(db, 'C_Order', Object.assign({}, order));
  var back = ADData.readRecords(db, 'C_Order', 'C_Order_ID = 1001')[0] || {};
  var keys = Object.keys(order).sort();
  var match = keys.every(function (k) { return String(back[k]) === String(order[k]); }) &&
              Object.keys(back).sort().join(',') === keys.join(',');
  L('--- B. round-trip ---');
  L('roundtrip C_Order fields=' + keys.length + ' match=' + (match ? 'OK' : 'MISMATCH'));
  if (!match) L('  saved=' + JSON.stringify(order) + ' read=' + JSON.stringify(back));
  ok(saved.action === 'INSERT' && match, 'B: C_Order field set identical after 5-table round-trip');
  // and it really landed in documents+metadata, not a real C_Order table
  var docRow = db.exec("SELECT doc_type, json_extract(metadata,'$.DocumentNo') FROM documents WHERE id='1001'");
  ok(docRow.length && docRow[0].values[0][0] === 'C_Order' && docRow[0].values[0][1] === 'SO-1001',
    'B: row stored in documents slot with metadata-keyed domain fields');

  // C. Lineage — Invoice derives from Order (source_id); InvoiceLine from OrderLine (source_line_id)
  ADData.saveRecord(db, 'C_OrderLine', { C_OrderLine_ID: 2001, C_Order_ID: 1001, Line: 10, M_Product_ID: 555, QtyOrdered: 3 });
  ADData.saveRecord(db, 'C_Invoice', { C_Invoice_ID: 3001, C_Order_ID: 1001, DocStatus: 'DR', DocumentNo: 'INV-3001' });
  ADData.saveRecord(db, 'C_InvoiceLine', { C_InvoiceLine_ID: 4001, C_Invoice_ID: 3001, C_OrderLine_ID: 2001, Line: 10 });
  var inv = ADData.readRecords(db, 'C_Invoice', 'C_Invoice_ID = 3001')[0] || {};
  var invLine = ADData.readRecords(db, 'C_InvoiceLine', 'C_InvoiceLine_ID = 4001')[0] || {};
  var srcId = db.exec("SELECT source_id FROM documents WHERE id='3001'");
  var srcLine = db.exec("SELECT source_line_id FROM document_lines WHERE id='4001'");
  L('--- C. lineage ---');
  L('lineage Invoice.source_id=' + (srcId.length ? srcId[0].values[0][0] : 'null') +
    ' InvoiceLine.source_line_id=' + (srcLine.length ? srcLine[0].values[0][0] : 'null'));
  ok(srcId.length && srcId[0].values[0][0] === '1001' && inv.C_Order_ID === 1001,
    'C: C_Invoice.C_Order_ID -> documents.source_id (Order lineage)');
  ok(srcLine.length && srcLine[0].values[0][0] === '2001' && invLine.C_OrderLine_ID === 2001,
    'C: C_InvoiceLine.C_OrderLine_ID -> document_lines.source_line_id (line lineage)');

  // D. Match — M_MatchInv links an InOutLine and an InvoiceLine across documents
  ADData.saveRecord(db, 'M_MatchInv', { M_MatchInv_ID: 5001, M_InOutLine_ID: 6001, C_InvoiceLine_ID: 4001, Qty: 3 });
  var mrow = db.exec("SELECT match_type, source_line_id, json_extract(metadata,'$.C_InvoiceLine_ID') FROM document_lines WHERE id='5001'");
  L('--- D. settlement match ---');
  L('match M_MatchInv type=' + (mrow.length ? mrow[0].values[0][0] : 'null') +
    ' src_line=' + (mrow.length ? mrow[0].values[0][1] : 'null') +
    ' counterpart=' + (mrow.length ? mrow[0].values[0][2] : 'null'));
  ok(mrow.length && mrow[0].values[0][0] === 'MATCH_INV' &&
     mrow[0].values[0][1] === '6001' && String(mrow[0].values[0][2]) === '4001',
    'D: M_MatchInv -> document_lines with match_type + both line refs');

  ADData.legacyMode();
}

(function main() {
  if (!fs.existsSync(DB)) { L('FATAL ad_seed.db not found at ' + DB); process.exit(1); }
  L('source=' + DB);
  var dims = mapCompleteness();
  initSqlJs().then(function (SQL) {
    runtimeProof(SQL);
    L('VERDICT ' + (fail === 0 ? 'PASS' : 'FAIL ' + fail) +
      ' => PB gate ' + (fail === 0 ? 'GREEN (build the bridge)' : 'inspect findings'));
    process.exit(fail ? 1 : 0);
  });
})();
