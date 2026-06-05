#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>. SPDX-License-Identifier: MIT
/**
 * poc_b1_fold.js — fold a SAP **Business One** order-to-cash + journal chain through the SAME six kernel verbs.
 *   Spec: docs/HolyGrail.md (migration solvent) · scripts/b1_adapter.js (the B1↔bridge map).
 *
 * THE CLAIM THIS TESTS: B1's SD (ORDR→ODLN→OINV) + its auto-posted Journal Entry (OJDT/JDT1, explicit
 * Debit/Credit columns) fold into the 5-table bridge using CREATE_DOCUMENT/CREATE_LINE/SET_STATUS/POST(+ALLOCATE)
 * with newVerbs=[] and the invoice journal balancing ΣDr==ΣCr to the cent — i.e. B1 is field-mapping, not a new
 * engine, exactly as proven for iDempiere/Odoo/SAP-flight.
 *
 * GATED, NON-INVENT: no build/erp/b1_oracle.json (a REAL B1 Service-Layer export) → §B1-ORACLE unavailable and
 * STOP. No fabricated rows. The runner prints meta.source LOUDLY so a mock can never masquerade as real.
 * Run:  node scripts/poc_b1_fold.js 2>&1 | tee build/erp/b1_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var A = require('./b1_adapter');
var ORACLE_PATH = path.join(__dirname, '..', 'build', 'erp', 'b1_oracle.json');
function n2(x) { return Number(x).toFixed(2); }

function reportUnavailable(reason) {
  console.log('\n═══ POC-B1-FOLD — fold SAP Business One (SMB) order-to-cash + OJDT/JDT1 journal ═══\n');
  console.log('── STATUS: ADAPTER READY, B1 ORACLE BLOCKED (' + reason + ') ──\n');
  console.log('The B1 adapter (b1_adapter.SCHEMA_MAP / buildB1Events) + this runner are wired. They need a REAL');
  console.log('B1 Service-Layer export (build/erp/b1_oracle.json). None present.\n');
  console.log('── the B1 data-model map (b1_adapter.SCHEMA_MAP) ──');
  Object.keys(A.SCHEMA_MAP).forEach(function (t) {
    var m = A.SCHEMA_MAP[t];
    console.log('   ' + t.padEnd(6) + ' → ' + String(m.doc_type).padEnd(14) + ' [' + m.bridge + ']  ' + (m.note || ''));
  });
  console.log('\n── how to unblock (NON-INVENT) ──');
  console.log('   Export ONE completed B1 A/R cycle (sales order → delivery → A/R invoice → its OJDT/JDT1 journal,');
  console.log('   + optional incoming payment) via the B1 Service Layer (OData) or DI-API. Shape = b1_oracle.template.json.');
  console.log('\n§B1-ORACLE unavailable — no real B1 export to fold; NOT attempted (non-invent guardrail).');
  console.log('\n═══ VERDICT ═══\n§B1-FOLD BLOCKED — adapter + runner prepared; awaiting a REAL B1 export. No fold claimed.');
  process.exit(0);
}

async function runFold(ORACLE) {
  var initSqlJs = require('sql.js');
  var K = require('./erp_kernel');
  var SQL = await initSqlJs();
  var built = A.buildB1Events(ORACLE);
  var fails = 0;
  function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

  console.log('\n═══ POC-B1-FOLD — fold SAP Business One (SMB) order-to-cash + OJDT/JDT1 journal ═══\n');
  console.log('── B1 FOLD — VERIFY every value vs the Service-Layer export ──');
  console.log('    source: ' + (ORACLE.meta.source || '(unspecified)') + '\n    gl_source: ' + built.gl_source + '\n');

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };
  var usedVerbs = {}, mapped = 0;
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    K.register(ev.d.docType, ev.d.action, function () { return ev.ops; });   // per-event register (no cell clobber)
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'b1:migrate', baseTs: 7000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mapped++;
  });
  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  // journal balance: ΣDebit == ΣCredit (the double-entry invariant), straight from JDT1's columns
  var dr = (ORACLE.journal_lines || []).reduce(function (a, l) { return a + Number(l.Debit || 0); }, 0);
  var cr = (ORACLE.journal_lines || []).reduce(function (a, l) { return a + Number(l.Credit || 0); }, 0);
  // invoice lines folded into document_lines under the invoice document
  var nLines = K.query(db, "SELECT COUNT(*) c FROM document_lines WHERE document_id='DOC:C_Invoice@from" + ORACLE.meta.order + "'")[0].c;

  verdict(mapped === built.events.length, 'every B1 hop migrated', mapped + '/' + built.events.length);
  verdict(newVerbs.length === 0, 'newVerbs empty — B1 folds with the existing 6 verbs', 'used=[' + used.join(',') + ']');
  verdict(n2(dr) === n2(cr), 'OJDT/JDT1 journal balances (ΣDr==ΣCr)', n2(dr) + '==' + n2(cr));
  verdict(nLines === (ORACLE.invoice_lines || []).length && nLines > 0, 'invoice lines fold into document_lines', 'lines=' + nLines);

  console.log('');
  console.log('§B1-FOLD order=' + ORACLE.meta.order + ' invoice=' + ORACLE.meta.invoice + ' mapped=' + mapped + '/' + built.events.length +
    ' verbs=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');
  console.log('§B1-FOLD journal ΣDr ' + n2(dr) + ' == ΣCr ' + n2(cr) + ' agree=' + (n2(dr) === n2(cr) ? 'Y' : 'N') + ' (OJDT/JDT1 Debit/Credit columns)');
  console.log('§B1-FINDINGS B1(SMB) SD+FI folds via CREATE_DOCUMENT/CREATE_LINE/SET_STATUS/POST(+ALLOCATE); newVerbs=[' + newVerbs.join(',') +
    ']; OJDT/JDT1 = clean double-entry → drops straight into the journal/POST fold (cleaner than S/4 ACDOCA). Service-Layer (HTTP) extraction = same delegate-to-install agent as Odoo.');

  db.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§B1-FOLD ' + (fails ? 'BOUNDED/FAIL — ' + fails + ' checks red (NAME the gap)'
    : 'PASS — B1 order-to-cash + its OJDT/JDT1 journal fold through the existing six verbs (newVerbs=[]); journal balances to the cent. B1 is field-mapping, not a new engine — the migration-solvent thesis holds on the SMB SAP product.'));
  process.exit(fails ? 1 : 0);
}

(function main() {
  if (!fs.existsSync(ORACLE_PATH)) return reportUnavailable('no build/erp/b1_oracle.json');
  var ORACLE;
  try { ORACLE = JSON.parse(fs.readFileSync(ORACLE_PATH, 'utf8')); }
  catch (e) { console.log('§B1-ORACLE unreadable: ' + e.message + ' — fix the export, do NOT fabricate.'); process.exit(2); }
  if (!ORACLE.meta || ORACLE.__template === true) return reportUnavailable('present file is the TEMPLATE / has no meta — fill from a REAL export');
  runFold(ORACLE);
})();
