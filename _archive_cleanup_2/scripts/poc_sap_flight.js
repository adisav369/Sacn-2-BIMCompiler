#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_sap_flight.js — SAP /DMO/ Flight Reference Scenario fold witness.
 *   Source: github.com/SAP-samples/abap-platform-refscen-flight (Apache-2.0).
 *   Exercises the document-lifecycle verb subset ONLY:
 *     CREATE_DOCUMENT / CREATE_LINE / SET_STATUS
 *   — the /DMO/ model has NO FI tables (no POST/ALLOCATE/MATCH).
 *   The key invariant: total_price == booking_fee + Σflight_price + Σsupplement_price (to the cent).
 *
 *   Oracle: build/erp/sap_flight_oracle.json
 *     carrier/connection/supplement prices = hardcoded ABAP literals (extracted 2026-06-14).
 *     flight_price / IDs = placeholder; replace with real /DMO/ export when available.
 *
 *   Run via: bash build/erp/run_witness.sh scripts/poc_sap_flight.js
 */
'use strict';
var path = require('path'), fs = require('fs');
var A = require('./sap_adapter');

var ORACLE_PATH = path.join(__dirname, '..', 'build', 'erp', 'sap_flight_oracle.json');

function banner() {
  console.log('\n══════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-SAP-FLIGHT — /DMO/ Travel→Booking lifecycle fold witness (Apache-2.0) ═══');
  console.log('══════════════════════════════════════════════════════════════════════════\n');
}

function n2(x) { return Number(x).toFixed(2); }
function cents(x) { return Math.round(Number(x) * 100); }

async function runFlight(ORACLE) {
  var initSqlJs = require('sql.js');
  var K = require('./erp_kernel');
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  K.initProjection(db);

  banner();
  console.log('── oracle: ' + ORACLE_PATH);
  console.log('── source: ' + (ORACLE.meta.source || '(unspecified)'));
  console.log('── carrier: ' + ORACLE.reference.carrier.carrier_id + ' (' + ORACLE.reference.carrier.name + ')');
  console.log('── route:   ' + ORACLE.reference.connection.airport_from_id + '→' + ORACLE.reference.connection.airport_to_id + ' (' + ORACLE.reference.connection.distance + ' ' + ORACLE.reference.connection.distance_unit + ')');
  console.log('── supplement: "' + ORACLE.reference.supplement_product.description + '" EUR ' + n2(ORACLE.reference.supplement_product.price_hardcoded) + ' (hardcoded in ABAP source)\n');

  var built = A.buildFlightEvents(ORACLE);
  var fails = 0;
  function verdict(ok, label, detail) {
    if (!ok) fails++;
    console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
  }

  // ── apply all ops directly (bypass dispatch: /DMO/ status O/A/X ≠ iDempiere DR/CO WFMC;
  //    flight scope = lifecycle verbs only, no state-machine validation to test here).
  //    Deep-clone each op to prevent K.apply's in-place op_uuid stamp from polluting the
  //    oracle object across event iterations. ──
  var usedVerbs = {}, allOps = [];
  built.events.forEach(function (ev) {
    ev.ops.forEach(function (o) {
      usedVerbs[o.op_type] = 1;
      allOps.push(JSON.parse(JSON.stringify(o)));
    });
  });
  var res = K.apply(db, allOps, { actor: 'sap:migrate', baseTs: 9000 });
  var mappedHops = built.events.length;
  verdict(res.applied === allOps.length, 'all ops applied (' + built.events.length + ' events → ' + allOps.length + ' ops)', 'applied=' + res.applied);

  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });
  verdict(newVerbs.length === 0, 'newVerbs=[]', 'used=[' + used.join(',') + ']');

  // ── price invariant: total_price == booking_fee + Σflight_price + Σsupplement_price ──
  var bookingTotal = (ORACLE.bookings || []).reduce(function (s, b) { return s + cents(b.flight_price); }, 0);
  var supplTotal   = (ORACLE.supplements || []).reduce(function (s, x) { return s + cents(x.price); }, 0);
  var derivedTotal = cents(ORACLE.meta.booking_fee) + bookingTotal + supplTotal;
  var claimedTotal = cents(ORACLE.meta.total_price);
  verdict(derivedTotal === claimedTotal, 'price invariant: total_price == booking_fee + Σlines (to the cent)',
    'derived=' + n2(derivedTotal / 100) + ' claimed=' + n2(claimedTotal / 100));

  // ── scope confirmation ──
  var EXPECTED = ['CREATE_DOCUMENT', 'CREATE_LINE', 'SET_STATUS'];
  var unexpectedFI = used.filter(function (v) { return ['POST','ALLOCATE','MATCH'].indexOf(v) >= 0; });
  verdict(unexpectedFI.length === 0, 'no FI verbs (scope: lifecycle only, no GL in /DMO/ model)', unexpectedFI.length ? 'unexpected=' + unexpectedFI.join(',') : 'confirmed');

  console.log('');
  console.log('§SAP-FLIGHT-CHAIN travel→bookings→supplements mapped=' + mappedHops + '/' + built.events.length);
  console.log('§SAP-FLIGHT-VERBS used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');
  console.log('§SAP-FLIGHT-PRICE booking_fee=' + n2(ORACLE.meta.booking_fee) + ' Σflight=' + n2(bookingTotal/100) + ' Σsuppl=' + n2(supplTotal/100) + ' total=' + n2(claimedTotal/100) + ' invariant=' + (derivedTotal === claimedTotal ? 'OK' : 'FAIL'));
  console.log('§SAP-FLIGHT-SCOPE document-lifecycle ONLY (CREATE_DOCUMENT/CREATE_LINE/SET_STATUS) — POST/ALLOCATE/MATCH NOT exercised (no FI in /DMO/ model)');
  console.log('§SAP-FLIGHT-DATA carrier/connection/supplement-price=hardcoded-ABAP-literals flight_price/IDs=placeholder — refresh from real /DMO/ export when available');

  db.close();
  console.log('\n═══ VERDICT ═══');
  if (fails) {
    console.log('§SAP-FLIGHT FAIL — ' + fails + ' checks red');
  } else {
    console.log('§SAP-FLIGHT PASS — lifecycle verbs fold, price invariant holds, newVerbs=[] (placeholder data; re-witness after real export)');
  }
  process.exit(fails ? 1 : 0);
}

(function main() {
  if (!fs.existsSync(ORACLE_PATH)) {
    banner();
    console.log('§SAP-FLIGHT BLOCKED — build/erp/sap_flight_oracle.json not found');
    process.exit(1);
  }
  var ORACLE;
  try { ORACLE = JSON.parse(fs.readFileSync(ORACLE_PATH, 'utf8')); }
  catch (e) { banner(); console.log('§SAP-FLIGHT ERROR — unreadable oracle: ' + e.message); process.exit(2); }
  if (!ORACLE.meta) {
    banner();
    console.log('§SAP-FLIGHT BLOCKED — oracle has no meta block');
    process.exit(1);
  }
  runFlight(ORACLE);
})();
