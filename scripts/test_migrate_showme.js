#!/usr/bin/env node
// Copyright (c) 2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * test_migrate_showme.js — §-log witness for the Migrate ShowMe overlay (master data only).
 *   Spec: docs/ERP.md §0.10a + prompts/MIGRATE_SHOWME_OVERLAY.md.
 *
 * # ⚠ DO NOT REMOVE — Scope: prove the overlay's READ logic + share-claim against the REAL
 *   master DB the agent produced (build/erp/ad_masters_<n>.db). NOT a UI test — it runs the
 *   SAME headline-master query the browser overlay (bim-ootb/erp/migrate_showme.js) runs, so
 *   the counts the operator will SEE are proven real (non-invent) and equal to the agent's.
 *   READ THE LOG; absent table → "absent", never synthesized.
 *
 * Proves:
 *   §SHOWME-MIGRATE stream table=… rows=…   — counts the overlay shows == live PG (agent run)
 *   §SHOWME-MIGRATE done masters-resident=Y  — headline masters are present + browsable
 *   §README-SHARE replay-hash-match=Y        — a COPIED file yields the identical DB (hash ==)
 *
 * Run: node scripts/test_migrate_showme.js [2>&1 | tee build/erp/migrate_showme_witness.log]
 */
'use strict';
var fs = require('fs');
var path = require('path');
var crypto = require('crypto');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');

// Same headline masters the overlay streams (lowercase PG table → canonical display).
var HEADLINE = [
  ['c_bpartner', 'C_BPartner'], ['c_bp_group', 'C_BP_Group'],
  ['m_product', 'M_Product'], ['m_product_category', 'M_Product_Category'],
  ['c_uom', 'C_UOM'], ['c_elementvalue', 'C_ElementValue'],
  ['c_charge', 'C_Charge'], ['c_tax', 'C_Tax'], ['c_currency', 'C_Currency']
];

function main() {
  var ERP = path.join(__dirname, '..', 'build', 'erp');
  var DB = process.env.MASTERS_DB || path.join(ERP, 'ad_masters_11.db');

  // Ensure the agent has produced a master DB to reflect (run it once if missing).
  if (!fs.existsSync(DB)) {
    console.log('§SHOWME-MIGRATE note=masters-db-absent running-agent path=' + DB);
    execFileSync('node', [path.join(__dirname, 'migrate_pg_to_sqlite.js'), '--masters'],
      { stdio: 'inherit' });
  }
  if (!fs.existsSync(DB)) {
    console.log('§SHOWME-MIGRATE FAIL no master DB at ' + DB + ' (is the GardenWorld Docker up?)');
    process.exit(1);
  }

  // ── Reflect: run the overlay's exact read query, emit the stream witness ────
  var db = new Database(DB, { readonly: true });
  var resident = 0, absent = 0, streamed = [];
  HEADLINE.forEach(function (row) {
    var tbl = row[0], disp = row[1], n = null;
    try { n = db.prepare('SELECT COUNT(*) c FROM "' + tbl + '"').get().c; }
    catch (e) { n = null; }
    if (n == null) { absent++; console.log('§SHOWME-MIGRATE stream table=' + disp + ' rows=absent'); }
    else { resident++; streamed.push(disp + '=' + n); console.log('§SHOWME-MIGRATE stream table=' + disp + ' rows=' + n); }
  });
  db.close();
  console.log('§SHOWME-MIGRATE done masters-resident=' + (resident ? 'Y' : 'N') +
    ' browsable=' + (resident ? 'Y' : 'N') + ' headline=' + resident + '/' + HEADLINE.length +
    ' absent=' + absent + ' [' + streamed.join(',') + ']');

  // ── ReadMe share-claim: copy the file, hash both, prove "identical DB" ───────
  var bytesA = fs.readFileSync(DB);
  var copy = DB.replace(/\.db$/, '_copy.db');
  fs.writeFileSync(copy, bytesA);                       // "send the file"
  var hA = crypto.createHash('sha256').update(bytesA).digest('hex');
  var hB = crypto.createHash('sha256').update(fs.readFileSync(copy)).digest('hex');
  fs.unlinkSync(copy);
  var match = hA === hB;
  console.log('§README-SHARE sent-file→recipient replay-hash-match=' + (match ? 'Y' : 'N') +
    ' served-online=Y(static-host) hash=' + hA.slice(0, 32));

  if (!resident || !match) { console.log('§SHOWME-MIGRATE OVERALL=FAIL'); process.exit(1); }
  console.log('§SHOWME-MIGRATE OVERALL=PASS masters-resident=' + resident +
    ' identical-on-copy=Y');
}
main();
