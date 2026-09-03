/**
 * poc_bim_embed.js — WITNESS W-BIM-EMBED-DECLARE (headless, §-log; read the log).
 * Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.
 *
 * Proves the DECLARE + DETECT of the BIM-in-window embed seam (prompts/BIM_EMBED_WINDOW_SESSION.md §B1):
 *   ISSUE PROVED — the renderer can decide "mount the BIM panel?" from the AD alone (a BIM-set
 *   AD_Attachment), with NO per-window code, and is honestly absent when a record owns no model.
 * Runs in-memory on a COPY of the shipped seed (never mutates it). Invoke:
 *   bash build/erp/run_witness.sh scripts/poc_bim_embed.js
 */
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');
var BimEmbed = require(path.join(__dirname, '..', 'build', 'erp', 'bim_embed.js'));

var SEED = process.argv[2] || path.join(process.env.HOME, 'bim-ootb', 'erp', 'ad_seed.db');
var C_PROJECT = 203;      // AD_Table_ID C_Project
var P_LANDSCAPE = 101;    // owns a BIM set after seeding
var P_STANDARD = 100;     // never gets one — must stay null (honest no-panel)
var URL = '../viewer/viewer.html?db=buildings/Hospital_extracted.db&bld=Hospital';

var log = [], fails = 0;
function S(m) { log.push(m); console.log(m); }
function verdict(ok, label, detail) {
  if (!ok) fails++;
  S('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

(async function () {
  if (!fs.existsSync(SEED)) { S('§W-BIM-EMBED-DECLARE FAIL seed missing: ' + SEED); process.exit(1); }
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SEED)));   // in-memory; not written back

  S('§W-BIM-EMBED-DECLARE — DECLARE+DETECT of the BIM-set AD flag');

  // 1) Honest absence: Standard project (never seeded) → no panel.
  verdict(BimEmbed.bimSetFor(db, C_PROJECT, P_STANDARD) === null,
    'Standard project 100 owns no BIM set → bimSetFor null (panel absent)');

  // 2) Clean slate on 101 (drop any pre-existing), confirm null before declaring.
  db.run("DELETE FROM ad_attachment WHERE ad_table_id=? AND record_id=? AND title LIKE 'BIM Set:%'",
    [C_PROJECT, P_LANDSCAPE]);
  verdict(BimEmbed.bimSetFor(db, C_PROJECT, P_LANDSCAPE) === null,
    'project 101 pre-declare → bimSetFor null');

  // 3) DECLARE: insert the BIM-set attachment (the SAME code path seed_bim_attachment.js uses).
  var id = BimEmbed.insertBimSet(db, {
    adTableId: C_PROJECT, recordId: P_LANDSCAPE,
    name: 'Landscape Complex (Hospital model)', url: URL, clientId: 11, orgId: 11
  });
  var got = BimEmbed.bimSetFor(db, C_PROJECT, P_LANDSCAPE);
  verdict(!!got, 'project 101 post-declare → bimSetFor resolves', 'id=' + id);
  verdict(!!got && got.url === URL, 'resolved URL == seeded viewer URL', got && got.url);
  verdict(!!got && /^BIM Set:/.test(got.title || ''), 'title carries the BIM-set sentinel', got && got.title);

  // 4) Well-formed standard AD_Attachment row (client/org=11, active, on table 254).
  var row = db.exec(
    "SELECT ad_client_id,ad_org_id,isactive,ad_table_id FROM ad_attachment WHERE ad_attachment_id=" + id)[0];
  var v = row && row.values[0];
  verdict(!!v && v[0] === 11 && v[1] === 11, 'row scoped to client/org 11', v && (v[0] + '/' + v[1]));
  verdict(!!v && v[2] === 'Y', 'row IsActive=Y', v && v[2]);

  // 5) Idempotent: re-declare → still exactly ONE BIM-set row on 101.
  BimEmbed.insertBimSet(db, { adTableId: C_PROJECT, recordId: P_LANDSCAPE,
    name: 'Landscape Complex (Hospital model)', url: URL, clientId: 11, orgId: 11 });
  verdict(BimEmbed.bimSetCount(db, C_PROJECT, P_LANDSCAPE) === 1,
    're-declare is idempotent → exactly 1 BIM-set row', 'count=' + BimEmbed.bimSetCount(db, C_PROJECT, P_LANDSCAPE));

  // 6) Generality (B4 in miniature): a DIFFERENT, non-Project table lights up from a flag alone —
  //    proves the detect is keyed off the AD, NOT 'if window==130'.
  var OTHER_TABLE = 991001, OTHER_REC = 7;
  BimEmbed.insertBimSet(db, { adTableId: OTHER_TABLE, recordId: OTHER_REC,
    name: 'Warehouse layout', url: '../viewer/viewer.html?db=buildings/Terminal_extracted.db&bld=Terminal',
    clientId: 11, orgId: 11 });
  verdict(!!BimEmbed.bimSetFor(db, OTHER_TABLE, OTHER_REC),
    'a non-Project table lights up from an AD flag alone (generality)', 'table=' + OTHER_TABLE);

  var pass = fails === 0;
  S('\n§W-BIM-EMBED-DECLARE ' + (pass ? 'PASS' : 'FAIL') + ' (fails=' + fails +
    ') — DECLARE+DETECT proven headless; panel mount + ?embed=1 = B2 (browser-gated, owed)');
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_bim_embed.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})();
