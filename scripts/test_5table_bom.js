#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * test_5table_bom.js — ⚠ DO NOT REMOVE — Scope guard
 * Scope: the (c) Bridge GATE (docs/ERP.md §0). Does the 5-table runtime, reached
 *   via ad_table_map, hold EVERY relationship §BOM_TEST found in the real AD —
 *   losing none? Read the §5TBL log; it makes the call. Run before any 5-table build.
 *
 * THEORY UNDER TEST: mapping each AD table to a 5-table slot
 *   {documents, document_lines, items, containers, journal} is EXPRESSIVE ENOUGH:
 *   every classified edge (containment | derivation | citation) maps to a
 *   representable 5-table relationship. Acyclicity is NOT tested here — in 5-table
 *   it is a runtime instance invariant (no doc is its own ancestor via source_id),
 *   not a schema property (docs/ERP.md §0).
 *
 *   G1 mapping-completeness — unmappable edges ≈ 0 (else 5-table is lossy → findings).
 *   G2 hub preservation     — the C_Order group stays the #1 derivation hub.
 *
 * Deterministic. Extract-only. Reuses §BOM_TEST classification on ad_seed.db.
 */
'use strict';
var path = require('path');
var execFileSync = require('child_process').execFileSync;
var DB = process.env.AD_SEED_DB ||
  path.join(process.env.HOME, 'bim-ootb', 'viewer', 'ad_seed.db');
function q(sql) {
  var o = execFileSync('sqlite3', [DB, '-json', sql],
    { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim();
  return o ? JSON.parse(o) : [];
}
function L(m) { console.log('§5TBL ' + m); }
var SYS = { ad_client_id: 1, ad_org_id: 1, createdby: 1, updatedby: 1 };

// ── Load schema facts (same sources as §BOM_TEST) ────────────────────────────
var nameByLc = {};
q("SELECT TableName AS name FROM AD_Table").forEach(function (r) { nameByLc[r.name.toLowerCase()] = r.name; });
var isDoc = {};
q("SELECT DISTINCT t.TableName n FROM AD_Column c JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.ColumnName='DocStatus'")
  .forEach(function (r) { isDoc[r.n] = true; });

// containment pairs (child>parent) from AD_Tab — per §BOM_TEST
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

// ── ad_table_map: AD table -> 5-table slot (heuristic, the bridge draft) ─────
// Order of precedence matters.
function slotOf(t) {
  // Compiler input, NOT runtime storage (docs/ERP.md §3): the AD dictionary's own
  // tables, translations, security/workflow DEFINITIONS. These describe the model;
  // they are not business data and must not enter the 5-table runtime.
  if (/^AD_/.test(t) || /_Trl$/.test(t) || /^RV_/.test(t)) return 'compiler';   // dictionary + reporting views
  // journal = ledger ENTRIES only (Fact_Acct + GL_Journal batch/journal/line).
  // GL_Category / GL_Budget / GL_Element are accounting MASTER data → items.
  if (/^Fact_Acct|^GL_Journal/.test(t)) return 'journal';
  if (isDoc[t]) return 'documents';
  // a containment-child of a document, line-ish → document_lines
  if (/(Line|Tax|Trx|Allocation(Line)?)$/.test(t) &&
      Object.keys(contain).some(function (k) { return k.indexOf(t + '>') === 0 && isDoc[k.split('>')[1]]; }))
    return 'document_lines';
  if (/(Warehouse|Locator|Project|Campaign|Region|Country|^AD_Org|^AD_Client|^M_Warehouse|Period|Year|Calendar)/.test(t))
    return 'containers';
  return 'items';                                   // master / reference data
}

// ── Classify FKs (containment | derivation | citation | self) ────────────────
var fk = q("SELECT t.TableName src, c.ColumnName col FROM AD_Column c " +
  "JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.AD_Reference_ID IN (18,19,30) AND c.IsActive='Y'");
function resolve(col) { var b = /_ID$/i.test(col) ? col.replace(/_ID$/i, '') : null; return b ? nameByLc[b.toLowerCase()] || null : null; }

// 5-table representability of an edge given (kind, srcSlot, tgtSlot)
function representable(kind, s, tg) {
  if (kind === 'derivation') return s === 'documents' && tg === 'documents';      // documents.source_id
  if (kind === 'self')       return true;                                          // *.parent_id
  if (kind === 'containment') {
    return (s === 'document_lines' && tg === 'documents') ||                       // line.document_id
           (s === 'document_lines' && tg === 'document_lines') ||                  // line.source_line_id — lineage recurses to the line
           (s === 'documents'      && tg === 'documents') ||                       // sub-document
           (s === 'documents'      && tg === 'containers') ||                      // doc.container_id
           (s === 'documents'      && tg === 'items')      ||                      // misclassified cite (doc→partner, UI-nested) — stored as reference
           (s === 'items'          && tg === 'containers') ||                      // item in container
           (s === 'items'          && tg === 'items')      ||                      // items.parent_id — master is recursive
           (s === 'journal'        && tg === 'journal')    ||                      // ledger Batch→Journal→Line
           (s === 'containers'     && tg === 'containers');                        // container hierarchy
  }
  // citation = a leaf naming another row by id — ALWAYS storable as a reference
  // column / metadata-JSON id. Component citations are representable by definition.
  return true;
}

var counts = { containment: 0, derivation: 0, citation: 0, self: 0 };
var mapped = 0, unmappable = [], excludedCompiler = 0;
var derivInDeg = {};                                 // tgt docType -> Set(src)
fk.forEach(function (f) {
  if (SYS[f.col.toLowerCase()]) return;
  var tgt = resolve(f.col); if (!tgt) return;        // unresolved (heuristic limit) — not counted
  var ss = slotOf(f.src), ts = slotOf(tgt);
  if (ss === 'compiler' || ts === 'compiler') { excludedCompiler++; return; }  // §3: dictionary, not runtime
  var kind;
  if (tgt === f.src) kind = 'self';
  else if (contain[f.src + '>' + tgt]) kind = 'containment';
  else if (isDoc[f.src] && isDoc[tgt]) { kind = 'derivation'; (derivInDeg[tgt] = derivInDeg[tgt] || {})[f.src] = true; }
  else kind = 'citation';
  counts[kind]++;
  if (representable(kind, ss, ts)) mapped++;
  else unmappable.push(kind + ' ' + f.src + '(' + ss + ')->' + tgt + '(' + ts + ')');
});

// ── G1 mapping-completeness ──────────────────────────────────────────────────
var domain = mapped + unmappable.length;
L('source=' + DB);
L('--- G1 mapping-completeness (expressive enough?) ---');
L('excluded (AD/compiler metadata, §3 not runtime)=' + excludedCompiler);
L('business edges=' + domain + ' (containment=' + counts.containment + ' derivation=' +
  counts.derivation + ' citation=' + counts.citation + ' self=' + counts.self + ')');
L('mappable=' + mapped + ' (' + (100 * mapped / domain).toFixed(1) + '%) unmappable=' + unmappable.length);
var byKind = {}; unmappable.forEach(function (u) { var k = u.split(' ')[0]; byKind[k] = (byKind[k] || 0) + 1; });
L('unmappable by kind: ' + JSON.stringify(byKind));
unmappable.slice(0, 20).forEach(function (u) { L('  UNMAPPABLE ' + u); });

// ── G2 hub preservation (doc_type = source table, 1:1 — no merge) ────────────
var hubs = Object.keys(derivInDeg).map(function (d) { return [d, Object.keys(derivInDeg[d]).length]; })
  .sort(function (a, b) { return b[1] - a[1]; });
L('--- G2 hub preservation ---');
hubs.slice(0, 5).forEach(function (h) { L('  hub ' + h[0] + ' in-degree=' + h[1]); });
var top = hubs.length ? hubs[0][0] : null;
L('hubTop=' + top + ' (expect C_Order — §BOM_TEST T5)');

// ── Verdict ──────────────────────────────────────────────────────────────────
var g1 = unmappable.length === 0;
var g2 = top === 'C_Order';
L('VERDICT G1(complete)=' + (g1 ? 'PASS' : 'REVIEW ' + unmappable.length + ' edges') +
  ' G2(hubTop)=' + (g2 ? 'PASS' : 'FAIL') + ' => GATE ' + (g1 && g2 ? 'OPEN' : 'inspect findings'));
process.exit(0);
