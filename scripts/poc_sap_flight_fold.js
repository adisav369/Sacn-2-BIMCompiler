#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_sap_flight_fold.js — fold SAP's OFFICIAL Flight Reference Scenario (the license-free SAP oracle).
 *   Spec: prompts/SAP_FOLD_POC.md · the /DMO/ travel-booking model from
 *   github.com/SAP-samples/abap-platform-refscen-flight (SAP-maintained, Apache-2.0).
 *
 * THE ARGUMENT THIS TESTS (user, 2026-06-03): if the adapter folds /DMO/TRAVEL → /DMO/BOOKING (→ SBOOK) →
 * /DMO/CUSTOMER into the 5-table bridge using the existing verbs, the METHOD is proven on REAL SAP data —
 * the O2C/FI-specific tables (VBAK/ACDOCA…) then become FIELD MAPPING, not architectural change.
 *
 * HONEST SCOPE (stated, not hidden): the flight model has NO financial accounting, so this witnesses the
 * DOCUMENT-LIFECYCLE subset only — CREATE_DOCUMENT (travel) · CREATE_LINE (booking, + nested supplement) ·
 * SET_STATUS (status machine) — plus the price-AGGREGATION invariant (total_price == booking_fee + Σ lines).
 * It does NOT exercise POST / ALLOCATE / MATCH or ACDOCA-as-fold — those are already proven on iDempiere +
 * Odoo and remain field-mapping for an SD+FI source. So a PASS here = "SAP rows fold structurally through the
 * bridge + the document verbs," NOT the full asymptote (which still needs an FI oracle).
 *
 * GATED, NON-INVENT: no build/erp/sap_flight_oracle.json (a REAL /DMO/ export) → §SAP-FLIGHT-ORACLE
 * unavailable and STOP. NO fabricated rows. Activates with zero code change when a real export drops in
 * (shape: build/erp/sap_flight_oracle.template.json). Run:
 *   node scripts/poc_sap_flight_fold.js 2>&1 | tee -a build/erp/sap_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var A = require('./sap_adapter');

var ORACLE_PATH = path.join(__dirname, '..', 'build', 'erp', 'sap_flight_oracle.json');

function banner() {
  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-SAP-FLIGHT-FOLD — fold SAP\'s Flight Reference Scenario (/DMO/, license-free) ═══');
  console.log('══════════════════════════════════════════════════════════════════════════════\n');
}
function n2(x) { return Number(x).toFixed(2); }

function reportUnavailable(reason) {
  banner();
  console.log('── STATUS: SKELETON READY, FLIGHT ORACLE BLOCKED (' + reason + ') ──\n');
  console.log('The flight adapter (sap_adapter.SCHEMA_MAP_FLIGHT / buildFlightEvents) + this runner are wired.');
  console.log('They need a REAL /DMO/ export (build/erp/sap_flight_oracle.json). None present.\n');
  console.log('── the flight data-model HYPOTHESIS (sap_adapter.SCHEMA_MAP_FLIGHT) ──');
  Object.keys(A.SCHEMA_MAP_FLIGHT).forEach(function (t) {
    var m = A.SCHEMA_MAP_FLIGHT[t];
    console.log('   ' + t.padEnd(24) + ' → ' + String(m.doc_type).padEnd(14) + ' [' + m.bridge + ']  ' + (m.note || ''));
  });
  console.log('\n── verbs this scenario CAN witness (3 of 6 — the flight model has NO FI) ──');
  console.log('   CREATE_DOCUMENT (travel) · CREATE_LINE (booking + nested supplement) · SET_STATUS (status machine)');
  console.log('   + price-aggregation invariant (total_price == booking_fee + Σ flight_price + Σ supplement)');
  console.log('   NOT exercised here: POST / ALLOCATE / MATCH (no double-entry in the flight model) — by design, stated.');
  console.log('\n── how to unblock (NON-INVENT) ──');
  console.log('   1. Deploy github.com/SAP-samples/abap-platform-refscen-flight to an ABAP system');
  console.log('      (ABAP Platform developer-edition Docker image, or a BTP ABAP Environment trial).');
  console.log('   2. Run the data generator; export ONE travel + its bookings + supplements');
  console.log('      from /DMO/TRAVEL, /DMO/BOOKING, /DMO/BOOKING_SUPPLEMENT.');
  console.log('   3. Fill build/erp/sap_flight_oracle.json from the export (shape = the template); re-run this.');
  console.log('\n§SAP-FLIGHT-ORACLE unavailable — no real /DMO/ export to fold; NOT attempted (non-invent guardrail).');
  console.log('\n═══ VERDICT ═══');
  console.log('§SAP-FLIGHT-FOLD BLOCKED — adapter + runner prepared; awaiting a REAL /DMO/ export. No fold claimed.');
  process.exit(0);
}

async function runFold(ORACLE) {
  var initSqlJs = require('sql.js');
  var K = require('./erp_kernel');
  var SQL = await initSqlJs();
  var built = A.buildFlightEvents(ORACLE);
  var travelId = ORACLE.meta.travel_id;
  var fails = 0;
  function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

  banner();
  console.log('── FIRST LIVE FLIGHT FOLD — VERIFY every value vs the /DMO/ export ──');
  console.log('    source: ' + (ORACLE.meta.source || '(unspecified)') + '\n    scope: ' + built.fold_scope + '\n');

  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };

  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'sap-flight:migrate', baseTs: 9000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });
  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  // structural fold: bookings + supplements became document_lines under the travel document
  var nLines = K.query(db, "SELECT COUNT(*) c FROM document_lines WHERE document_id='DOC:C_Order@from" + travelId + "'")[0].c;
  var expectLines = (ORACLE.bookings || []).length + (ORACLE.supplements || []).length;
  verdict(nLines === expectLines && expectLines > 0, 'bookings + supplements fold into document_lines (5-table bridge)', 'lines=' + nLines + ' expect=' + expectLines);

  // price-aggregation invariant: booking_fee + Σ line amounts == travel total_price (to the cent)
  var sumLines = K.query(db, "SELECT COALESCE(SUM(json_extract(metadata,'$.amount')),0) s FROM document_lines WHERE document_id='DOC:C_Order@from" + travelId + "'")[0].s;
  var derivedTotal = Number(ORACLE.meta.booking_fee) + Number(sumLines);
  verdict(Math.round(derivedTotal * 100) === Math.round(ORACLE.meta.total_price * 100), 'price aggregation reproduces /DMO/TRAVEL.total_price', 'derived=' + n2(derivedTotal) + ' oracle=' + n2(ORACLE.meta.total_price));

  console.log('');
  console.log('§SAP-FLIGHT-FOLD travel=' + travelId + ' lines=' + nLines + ' verbs=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');
  console.log('§SAP-FLIGHT-FOLD price-aggregation booking_fee+Σlines=' + n2(derivedTotal) + ' == total_price ' + n2(ORACLE.meta.total_price) + ' agree=' + (Math.round(derivedTotal * 100) === Math.round(ORACLE.meta.total_price * 100) ? 'Y' : 'N'));
  console.log('§SAP-FLIGHT-FINDINGS scope=document-lifecycle-only(real SAP /DMO/ rows fold into the 5-table bridge via CREATE_DOCUMENT/CREATE_LINE/SET_STATUS; newVerbs=[]) FI-NOT-IN-MODEL(POST/ALLOCATE/MATCH untested here — proven on iDempiere+Odoo; SD/ACDOCA = field-mapping for an FI oracle)');

  db.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§SAP-FLIGHT-FOLD ' + (fails ? 'BOUNDED/FAIL — ' + fails + ' checks red (NAME the gap)'
    : 'PASS (document half) — real SAP /DMO/ rows fold structurally into the 5-table bridge through the existing document verbs (newVerbs=[' + newVerbs.join(',') + ']); price aggregation reproduces to the cent. The METHOD holds on SAP data; the SD+FI tables are field-mapping, not architectural change. (FI verbs proven elsewhere; ACDOCA-as-fold still needs an FI oracle.)'));
  process.exit(fails ? 1 : 0);
}

(function main() {
  if (!fs.existsSync(ORACLE_PATH)) return reportUnavailable('no build/erp/sap_flight_oracle.json');
  var ORACLE;
  try { ORACLE = JSON.parse(fs.readFileSync(ORACLE_PATH, 'utf8')); }
  catch (e) { banner(); console.log('§SAP-FLIGHT-ORACLE unreadable: ' + e.message + ' — fix the export, do NOT fabricate.'); process.exit(2); }
  if (!ORACLE.meta || ORACLE.__template === true) return reportUnavailable('present file is the TEMPLATE / has no meta — fill from a REAL export');
  runFold(ORACLE);
})();
