// ⚠ DO NOT REMOVE — poc_ninja_extract.js — W-NINJA-EXTRACT witness
// Implementing NINJA_MODE_LANE.md §1 (extractModel — reverse-export)
// ISSUE PROVEN: round-trip a staged Ninja model OUT via extractModel and assert
//   the re-extracted user columns match the original parseSheet columns.
// Run via: bash build/erp/run_witness.sh scripts/poc_ninja_extract.js
// Read the log before conclusions. Non-invent: every row is from the staged DB.
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require(path.join(__dirname, '..', 'node_modules', 'sql.js'));
var XLSX      = require(path.join(__dirname, '..', 'node_modules', 'xlsx', 'xlsx.js'));
var NinjaModel = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_model.js'));
var NinjaStage = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_stage.js'));

var SEED_PATH    = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var FIXTURE_PATH = path.join(__dirname, '..', 'build', 'erp', 'fixtures', 'ninja_starter.xlsx');

(async function () {
  var log = []; function L(m) { console.log(m); log.push(m); }
  L('\n══ W-NINJA-EXTRACT — extractModel round-trip witness ══\n');

  if (!fs.existsSync(SEED_PATH))    { L('§W-NINJA-EXTRACT FAIL seed missing: ' + SEED_PATH); process.exit(1); }
  if (!fs.existsSync(FIXTURE_PATH)) { L('§W-NINJA-EXTRACT FAIL fixture missing: ' + FIXTURE_PATH); process.exit(1); }

  var SQL = await initSqlJs();

  // ── 1. Parse the scaffold fixture sheet ──
  var wb = XLSX.readFile(FIXTURE_PATH);
  var rows = XLSX.utils.sheet_to_json(wb.Sheets['2_RO_ModelMaker'], { header: 1, defval: '' });
  var model = NinjaModel.parseSheet(rows, { format: 'romo' });
  L('§PARSE bundleName=' + model.bundleName + ' tables=' + model.tables.length + ' warnings=' + model.warnings.length);
  if (!model.tables.length) { L('§W-NINJA-EXTRACT FAIL no tables parsed'); process.exit(1); }

  // pick first table as the round-trip target
  var orig = model.tables[0];
  L('§ORIG table=' + orig.name + ' master=' + orig.master + ' workflow=' + orig.workflow + ' kanban=' + orig.kanban);
  var origUserCols = orig.columns.filter(function (c) { return !c.isStandard; });
  L('§ORIG userCols=' + origUserCols.length + ' [' + origUserCols.map(function(c){return c.name+'('+c.refId+')'}).join(',') + ']');

  // ── 2. Stage into a fresh copy of ad_seed ──
  var db = new SQL.Database(fs.readFileSync(SEED_PATH));
  var staged = NinjaStage.stageModels(db, model, 0);
  L('§STAGED windows=' + staged.windows + ' tables=' + staged.tables + ' cols=' + staged.cols + ' tabs=' + staged.tabs + ' menus=' + staged.menus + ' skipped=' + staged.skipped);

  // find the window ID for orig.name
  var winRows = db.exec(
    "SELECT AD_Window_ID FROM AD_Window WHERE Name=? AND AD_Window_ID>=" + NinjaStage.NINJA_BASE,
    [orig.name.replace(/_/g,' ')]
  );
  if (!winRows.length || !winRows[0].values.length) {
    L('§W-NINJA-EXTRACT FAIL staged window not found for: ' + orig.name);
    process.exit(1);
  }
  var windowId = winRows[0].values[0][0];
  L('§WINDOW_ID=' + windowId);

  // ── 3. extractModel → reconstructed model ──
  var extracted = NinjaStage.extractModel(db, windowId);
  if (!extracted || !extracted.tables.length) {
    L('§W-NINJA-EXTRACT FAIL extractModel returned null/empty');
    process.exit(1);
  }
  var ext = extracted.tables[0];
  L('§EXTRACTED table=' + ext.name + ' master=' + ext.master + ' workflow=' + ext.workflow + ' kanban=' + ext.kanban);
  var extUserCols = ext.columns;
  L('§EXTRACTED userCols=' + extUserCols.length + ' [' + extUserCols.map(function(c){return c.name+'('+c.refId+')'}).join(',') + ']');

  // ── 4. Assert round-trip match ──
  var mismatches = [];
  origUserCols.forEach(function (oc, i) {
    var ec = extUserCols[i];
    if (!ec) { mismatches.push('missing col[' + i + ']=' + oc.name); return; }
    if (ec.name !== oc.name)    mismatches.push('name[' + i + '] orig=' + oc.name + ' got=' + ec.name);
    if (ec.refId !== oc.refId)  mismatches.push('refId[' + i + '] ' + oc.name + ' orig=' + oc.refId + ' got=' + ec.refId);
  });
  if (extUserCols.length !== origUserCols.length)
    mismatches.push('colCount orig=' + origUserCols.length + ' got=' + extUserCols.length);
  if (ext.name    !== orig.name)    mismatches.push('name orig=' + orig.name + ' got=' + ext.name);
  if (!!ext.master !== !!orig.master) mismatches.push('master orig=' + orig.master + ' got=' + ext.master);
  if (ext.workflow !== orig.workflow) mismatches.push('workflow orig=' + orig.workflow + ' got=' + ext.workflow);

  L('§NINJA-EXTRACT mismatches=' + mismatches.length + (mismatches.length ? ' [' + mismatches.join('; ') + ']' : ''));

  // ── §FALSIFIER: extractModel on a non-existent window must return null ──
  var ghost = NinjaStage.extractModel(db, 9999999);
  L('§FALSIFIER ghost-window=' + (ghost === null ? 'null=CORRECT' : 'NON-NULL=BROKEN'));
  var falsifierOk = ghost === null;

  var roundtrip = mismatches.length === 0 ? 'MATCH' : 'MISMATCH';
  L('\n§NINJA-EXTRACT roundtrip=' + roundtrip + ' §FALSIFIER=' + (falsifierOk ? 'LOAD-BEARING' : 'BROKEN'));

  var pass = roundtrip === 'MATCH' && falsifierOk;
  L('§W-NINJA-EXTRACT ' + (pass ? 'PASS' : 'FAIL'));
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_ninja_extract.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§W-NINJA-EXTRACT ERROR', e.message, e.stack); process.exit(2); });
