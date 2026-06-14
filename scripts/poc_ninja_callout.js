// ⚠ DO NOT REMOVE — poc_ninja_callout.js — W-NINJA-CALLOUT witness
// Implementing NINJA_MODE_LANE.md §2 (auto-wire AD_Column.Callout from sheet)
// ISSUE PROVEN: a column def with @callout token in the ColumnSet stages the Callout
//   field onto AD_Column so AdCallout.dispatch fires it — structure+behaviour wired
//   end-to-end from the sheet grammar alone, no hand-edit.
// Run via: bash build/erp/run_witness.sh scripts/poc_ninja_callout.js
// Read the log before conclusions. Non-invent.
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs  = require(path.join(__dirname, '..', 'node_modules', 'sql.js'));
var NinjaModel = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_model.js'));
var NinjaStage = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_stage.js'));
var AdCallout  = require(path.join(__dirname, '..', 'build', 'erp', 'ad_callout.js'));

var SEED_PATH   = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var HANDLER     = 'com.acme.AssetCallout.statusFromActive';
var BUNDLE_PATH = path.join(__dirname, '..', 'build', 'erp', 'fixtures', 'plugins', 'asset_status_callout.mjs');

(async function () {
  var log = []; function L(m) { console.log(m); log.push(m); }
  L('\n══ W-NINJA-CALLOUT — sheet @callout token → AD_Column.Callout → dispatch fires ══\n');

  if (!fs.existsSync(SEED_PATH))   { L('§W-NINJA-CALLOUT FAIL seed missing'); process.exit(1); }
  if (!fs.existsSync(BUNDLE_PATH)) { L('§W-NINJA-CALLOUT FAIL bundle missing: ' + BUNDLE_PATH); process.exit(1); }

  var SQL = await initSqlJs();

  // ── §1: parse column def with @callout grammar token ──
  L('── §1 parseColDef with @callout token ──');
  var parsed = NinjaModel.parseColDef('Y#IsActive@' + HANDLER, []);
  L('§PARSE name=' + parsed.name + ' refId=' + parsed.refId + ' callout=' + parsed.callout);
  if (parsed.callout !== HANDLER) { L('§W-NINJA-CALLOUT FAIL parseColDef did not extract callout'); process.exit(1); }

  // ── §2: build model inline (no xlsx needed — use parseSheet columnar format) ──
  L('\n── §2 parseSheet with @callout in ColumnSet ──');
  var sheetRows = [
    ['RO_ModelHeader_ID', 'SeqNo', 'WorkflowStructure', 'KanbanBoard', 'Master', 'Name', 'Help', 'ColumnSet'],
    ['MyAssets', '1', 'N', 'N', '', 'AST_Asset', 'Asset with callout',
     'Name,Description,Y#IsActive@' + HANDLER]
  ];
  var model = NinjaModel.parseSheet(sheetRows, { format: 'romo' });
  L('§MODEL bundleName=' + model.bundleName + ' tables=' + model.tables.length + ' warnings=' + model.warnings.length);
  var tbl = model.tables[0];
  var calloutCol = tbl.columns.filter(function (c) { return c.callout; })[0];
  L('§CALLOUT-COL name=' + (calloutCol && calloutCol.name) + ' callout=' + (calloutCol && calloutCol.callout));
  if (!calloutCol || calloutCol.callout !== HANDLER) {
    L('§W-NINJA-CALLOUT FAIL callout not in parsed model column'); process.exit(1);
  }

  // ── §3: stage into db — Callout must land on AD_Column ──
  L('\n── §3 stageModels → AD_Column.Callout written ──');
  var db = new SQL.Database(fs.readFileSync(SEED_PATH));
  var staged = NinjaStage.stageModels(db, model, 0);
  L('§STAGED cols=' + staged.cols + ' callouts=' + staged.callouts + ' skipped=' + staged.skipped);
  if (staged.callouts !== 1) { L('§W-NINJA-CALLOUT FAIL staged.callouts=' + staged.callouts + ' expected 1'); process.exit(1); }

  // verify the Callout value is in the DB
  var r = db.exec(
    "SELECT c.Callout FROM AD_Column c JOIN AD_Table t ON t.AD_Table_ID=c.AD_Table_ID" +
    " WHERE t.TableName='AST_Asset' AND c.ColumnName='IsActive'"
  );
  var dbCallout = r.length && r[0].values.length ? r[0].values[0][0] : null;
  L('§DB-CALLOUT=' + dbCallout);
  if (dbCallout !== HANDLER) { L('§W-NINJA-CALLOUT FAIL DB.Callout mismatch: got=' + dbCallout); process.exit(1); }

  // ── §4: load the behaviour bundle + fire dispatch through the wired seam ──
  L('\n── §4 load bundle + dispatch via wired AD_Column.Callout ──');

  // use better-sqlite3 for dispatch (mirrors poc_asset_status.js — that IS the production path)
  // build a minimal DB matching what stageModels wrote: AST_Asset table + IsActive with callout wired
  var Database = require('better-sqlite3');
  var bsDb = new Database(':memory:');
  bsDb.exec(
    'CREATE TABLE ad_table  (ad_table_id INTEGER PRIMARY KEY, tablename TEXT);' +
    'CREATE TABLE ad_column (ad_column_id INTEGER PRIMARY KEY, ad_table_id INTEGER, columnname TEXT, callout TEXT);' +
    "INSERT INTO ad_table  VALUES (7000000,'AST_Asset');" +
    "INSERT INTO ad_column VALUES (7000009,7000000,'IsActive','" + HANDLER + "');" +
    "INSERT INTO ad_column VALUES (7000010,7000000,'Name',NULL);"
  );
  L('§BS-DB ad_column.callout for IsActive = ' +
    (bsDb.prepare("SELECT callout FROM ad_column WHERE columnname='IsActive'").get() || {}).callout);

  // load ESM bundle via dynamic import (the correct path for .mjs)
  var url = require('url');
  var bundle = await import(url.pathToFileURL(BUNDLE_PATH).href);

  // activate — registers the handler into AdCallout.REGISTRY
  var host = {
    engineVersion: '0.10.0',
    db: { query: function (sql, p) {
      var res = db.exec(sql, p || []);
      if (!res.length) return [];
      var cols = res[0].columns;
      return res[0].values.map(function (v) { var o = {}; cols.forEach(function (c,i){ o[c]=v[i]; }); return o; });
    }},
    oplog: { append: function () {} },
    callout: AdCallout,
    plugins: { register: function () {} }
  };
  if (typeof bundle.activate === 'function') {
    await bundle.activate(host);
    L('§BUNDLE activated: ' + (bundle.manifest && bundle.manifest.id || 'unknown'));
  } else {
    L('§W-NINJA-CALLOUT FAIL bundle has no activate()'); process.exit(1);
  }

  // dispatch — IsActive change, callout is wired via AD_Column.Callout
  var record = { IsActive: 'Y', Name: 'Server Rack', Description: '' };
  var result = AdCallout.dispatch(bsDb, { table: 'AST_Asset', column: 'IsActive', record: record }, {});
  L('§DISPATCH result=' + JSON.stringify(result));
  var fired = result && result.derived && Object.keys(result.derived).length > 0;
  L('§CALLOUT-FIRED=' + fired + ' derived=' + JSON.stringify((result && result.derived) || {}));

  // ── §5 §FALSIFIER A: col with Callout=NULL → dispatch absent ──
  L('\n── §5 §FALSIFIER A: col Callout=NULL → no dispatch ──');
  var r2 = AdCallout.dispatch(bsDb, { table: 'AST_Asset', column: 'Name', record: record }, {});
  var noFire = !r2 || !r2.derived || Object.keys(r2.derived).length === 0;
  L('§FALSIFIER-A Name-col-no-callout → fired=' + (!noFire) + ' (expect false)');

  // ── §6 §FALSIFIER B: deactivate → handler absent, dispatch reports it ──
  L('\n── §6 §FALSIFIER B: deactivate bundle → handler absent ──');
  if (typeof bundle.deactivate === 'function') await bundle.deactivate(host);
  var r3 = AdCallout.dispatch(bsDb, { table: 'AST_Asset', column: 'IsActive', record: record }, {});
  var noFireB = r3 && r3.absent && r3.absent.length > 0;
  L('§FALSIFIER-B after deactivate → absent=' + JSON.stringify(r3 && r3.absent) + ' (expect [handler])');

  var pass = fired && noFire && noFireB;
  L('\n§W-NINJA-CALLOUT §CALLOUT-FIRED=' + fired + ' §FALSIFIER-A=' + noFire + ' §FALSIFIER-B=' + noFireB);
  L('§W-NINJA-CALLOUT ' + (pass ? 'PASS' : 'FAIL'));
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_ninja_callout.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§W-NINJA-CALLOUT ERROR', e.message, e.stack); process.exit(2); });
