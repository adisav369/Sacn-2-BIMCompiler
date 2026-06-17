/**
 * seed_bim_attachment.js — DECLARE the BIM-set on Project 101 (the driving case).
 * Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.
 *
 * ⚠ DO NOT REMOVE — Scope: insert ONE standard AD_Attachment (DT-URL) marking C_Project 101
 * "Landcape for New Complex" (AD_Client/Org=11) as BIM-bearing, pointing at a REAL served viewer
 * building. Idempotent + re-runnable. The URL is our app's declaration (NOT oracle data), so this
 * MUST be re-applied AFTER any scripts/export_ad.sh regen of ad_seed.db. Read the log after running.
 * See prompts/BIM_EMBED_WINDOW_SESSION.md §B1. Witness: scripts/poc_bim_embed.js (W-BIM-EMBED-DECLARE).
 *
 * Usage: node scripts/seed_bim_attachment.js [path/to/ad_seed.db]
 *   default target = ~/bim-ootb/erp/ad_seed.db (the served app seed B2 loads).
 */
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');
var BimEmbed = require(path.join(__dirname, '..', 'build', 'erp', 'bim_embed.js'));

var SEED = process.argv[2] || path.join(process.env.HOME, 'bim-ootb', 'erp', 'ad_seed.db');
var C_PROJECT_TABLE_ID = 203;   // AD_Table.AD_Table_ID for C_Project (verified ad_seed.db)
var PROJECT_ID = 101;           // "Landcape for New Complex", AD_Client/Org = 11
// REAL served building (bim-ootb/viewer/buildings/Hospital_extracted.db); carries 4D schedule data.
var VIEWER_URL = '../viewer/viewer.html?db=buildings/Hospital_extracted.db&bld=Hospital';

(async function () {
  function S(m) { console.log(m); }
  if (!fs.existsSync(SEED)) { S('§SEED-BIM FAIL seed missing: ' + SEED); process.exit(1); }
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SEED)));

  var id = BimEmbed.insertBimSet(db, {
    adTableId: C_PROJECT_TABLE_ID, recordId: PROJECT_ID,
    name: 'Landscape Complex (Hospital model)', url: VIEWER_URL, clientId: 11, orgId: 11
  });
  var got = BimEmbed.bimSetFor(db, C_PROJECT_TABLE_ID, PROJECT_ID);
  fs.writeFileSync(SEED, Buffer.from(db.export()));

  S('§SEED-BIM inserted AD_Attachment id=' + id + ' on C_Project ' + PROJECT_ID +
    ' (table ' + C_PROJECT_TABLE_ID + ') → ' + (got ? got.url : 'NULL'));
  var ok = !!(got && got.url === VIEWER_URL);
  S('§SEED-BIM ' + (ok ? 'PASS' : 'FAIL') + ' seed=' + SEED);
  process.exit(ok ? 0 : 1);
})();
