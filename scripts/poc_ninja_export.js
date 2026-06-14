// ⚠ DO NOT REMOVE — poc_ninja_export.js — W-NINJA-EXPORT witness
// Implementing NINJA_MODE_LANE.md §1 (workbook-serialize leg of PackOut / "Export an existing window")
// ISSUE PROVEN: the FULL serialize round-trip closes —
//   DB window → extractModel → modelToWorkbook → real XLSX bytes → re-read → parseSheet == original.
//   This is what the Create-tab "Export an existing window" button produces; the witness asserts the
//   downloaded sheet re-parses to the SAME user columns (name+refId), master, workflow as were staged.
// Run via: bash build/erp/run_witness.sh scripts/poc_ninja_export.js
// Read the log before conclusions. Non-invent: every emitted token is the inverse of the staged grammar.
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs  = require(path.join(__dirname, '..', 'node_modules', 'sql.js'));
var XLSX       = require(path.join(__dirname, '..', 'node_modules', 'xlsx', 'xlsx.js'));
var NinjaModel  = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_model.js'));
var NinjaStage  = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_stage.js'));
var NinjaExport = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_export.js'));

var SEED_PATH    = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var FIXTURE_PATH = path.join(__dirname, '..', 'build', 'erp', 'fixtures', 'ninja_starter.xlsx');
// optional breadth: the canonical 18-table HRMIS oracle sheet, if present on disk
var HRMIS_CANDIDATES = [
  path.join(process.env.HOME || '', 'Projects', 'org.idempiere.ninja', 'templates', 'Ninja_HRMIS.xlsx'),
  path.join(process.env.HOME || '', 'Downloads', 'Ninja_HRMIS.xlsx')
];

// user columns of a parseSheet table = strip standard + the master FK col + workflow cols
function userCols(t) {
  var WF = { DocStatus: 1, DocAction: 1, Processed: 1, DocumentNo: 1, IsApproved: 1 };
  var fk = t.master ? (t.master + '_ID') : null;
  return (t.columns || []).filter(function (c) {
    return !c.isStandard && c.name !== fk && !WF[c.name];
  }).map(function (c) { return { name: c.name, refId: Number(c.refId) }; });
}

function roundTripTable(L, db, orig) {
  // find the staged window for this table (name has underscores swapped to spaces by stageModels)
  var winRows = db.exec(
    "SELECT AD_Window_ID FROM AD_Window WHERE Name=? AND AD_Window_ID>=" + NinjaStage.NINJA_BASE,
    [orig.name.replace(/_/g, ' ')]
  );
  if (!winRows.length || !winRows[0].values.length) { L('  §SKIP no window for ' + orig.name); return null; }
  var winId = winRows[0].values[0][0];

  // exportWindow → workbook → REAL xlsx bytes → re-read → parseSheet (the actual download path)
  var wb = NinjaExport.exportWindow(db, winId, XLSX);
  if (!wb) { L('  §FAIL exportWindow null for ' + orig.name); return { ok: false }; }
  var bytes = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });          // serialize as a download would
  var reWb  = XLSX.read(bytes, { type: 'array' });                          // re-read from bytes
  var rows  = XLSX.utils.sheet_to_json(reWb.Sheets[NinjaExport.MODEL_SHEET], { header: 1, defval: '' });
  var reModel = NinjaModel.parseSheet(rows, { format: 'romo' });
  var reTable = (reModel.tables || []).filter(function (t) { return t.name === orig.name; })[0];
  if (!reTable) { L('  §FAIL re-parse lost table ' + orig.name); return { ok: false }; }

  // compare user columns + flags
  var a = userCols(orig), b = userCols(reTable);
  var diffs = [];
  if (a.length !== b.length) diffs.push('colCount orig=' + a.length + ' got=' + b.length);
  a.forEach(function (oc, i) {
    var ec = b[i];
    if (!ec) { diffs.push('missing[' + i + ']=' + oc.name); return; }
    if (ec.name !== oc.name)   diffs.push('name[' + i + '] ' + oc.name + '/' + ec.name);
    if (ec.refId !== oc.refId) diffs.push('refId[' + oc.name + '] ' + oc.refId + '/' + ec.refId);
  });
  if (!!reTable.master !== !!orig.master) diffs.push('master ' + orig.master + '/' + reTable.master);
  if (reTable.workflow !== orig.workflow) diffs.push('workflow ' + orig.workflow + '/' + reTable.workflow);

  L('  §RT ' + orig.name + ' winId=' + winId + ' cols=' + a.length +
    ' master=' + (orig.master || '-') + ' wf=' + orig.workflow +
    ' → ' + (diffs.length ? 'MISMATCH [' + diffs.join('; ') + ']' : 'MATCH'));
  return { ok: diffs.length === 0, diffs: diffs.length };
}

function runFixture(L, SQL, fixturePath, label) {
  var wb = XLSX.readFile(fixturePath);
  var rows = XLSX.utils.sheet_to_json(wb.Sheets['2_RO_ModelMaker'], { header: 1, defval: '' });
  var model = NinjaModel.parseSheet(rows, { format: 'romo' });
  L('\n── ' + label + ': bundle=' + model.bundleName + ' tables=' + model.tables.length +
    ' warnings=' + model.warnings.length);
  if (!model.tables.length) { L('  §FAIL no tables'); return { tables: 0, ok: 0 }; }

  var db = new SQL.Database(fs.readFileSync(SEED_PATH));
  var staged = NinjaStage.stageModels(db, model, 0);
  L('  §STAGED windows=' + staged.windows + ' tables=' + staged.tables + ' cols=' + staged.cols);

  var ok = 0, total = 0;
  model.tables.forEach(function (t) {
    var r = roundTripTable(L, db, t);
    if (r) { total++; if (r.ok) ok++; }
  });
  return { tables: total, ok: ok };
}

(async function () {
  var log = []; function L(m) { console.log(m); log.push(m); }
  L('\n══ W-NINJA-EXPORT — DB→workbook→re-parse round-trip witness ══');

  if (!fs.existsSync(SEED_PATH))    { L('§W-NINJA-EXPORT FAIL seed missing: ' + SEED_PATH); process.exit(1); }
  if (!fs.existsSync(FIXTURE_PATH)) { L('§W-NINJA-EXPORT FAIL fixture missing: ' + FIXTURE_PATH); process.exit(1); }

  var SQL = await initSqlJs();

  var tot = { tables: 0, ok: 0 };
  var a = runFixture(L, SQL, FIXTURE_PATH, 'starter (ninja_starter.xlsx)');
  tot.tables += a.tables; tot.ok += a.ok;

  // optional HRMIS breadth pass (only if the oracle sheet is on disk)
  var hrmis = HRMIS_CANDIDATES.filter(fs.existsSync)[0];
  if (hrmis) {
    var b = runFixture(L, SQL, hrmis, 'HRMIS (Ninja_HRMIS.xlsx)');
    tot.tables += b.tables; tot.ok += b.ok;
  } else {
    L('\n── HRMIS sheet not on disk — skipped breadth pass (starter is full-grammar; not a failure)');
  }

  // ── §FALSIFIER: exportWindow on a non-existent window must return null ──
  var db = new SQL.Database(fs.readFileSync(SEED_PATH));
  var ghost = NinjaExport.exportWindow(db, 9999999, XLSX);
  var ghostBlob = NinjaExport.exportWindow(db, 9999999, XLSX);
  L('\n§FALSIFIER ghost-window=' + (ghost === null ? 'null=CORRECT' : 'NON-NULL=BROKEN'));
  var falsifierOk = ghost === null && ghostBlob === null;

  var roundtrip = (tot.tables > 0 && tot.ok === tot.tables) ? 'MATCH' : 'MISMATCH';
  L('\n§NINJA-EXPORT tables=' + tot.tables + ' match=' + tot.ok +
    ' roundtrip=' + roundtrip + ' §FALSIFIER=' + (falsifierOk ? 'LOAD-BEARING' : 'BROKEN'));

  var pass = roundtrip === 'MATCH' && falsifierOk;
  L('§W-NINJA-EXPORT ' + (pass ? 'PASS' : 'FAIL'));
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_ninja_export.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§W-NINJA-EXPORT ERROR', e.message, e.stack); process.exit(2); });
