// ⚠ DO NOT REMOVE — NINJA_MODE_LANE.md §Phase C: emit + install a `.foldbundle`.
// Emit a Ninja bundle from the HRMIS model → install via PluginRegistry → start → verify ACTIVE +
// windows reachable. Also emit the `.foldbundle` FILE artifact and verify it round-trips (import + install).
// Witness: §NINJA-BUNDLE install id=<pkg> state=ACTIVE models=<n>
// Run via: bash build/erp/run_witness.sh scripts/poc_ninja_bundle.js
// Read the log after EVERY run.

'use strict';

var path = require('path');
var fs   = require('fs');
var url  = require('url');

var initSqlJs = require(path.join(__dirname, '..', 'node_modules', 'sql.js'));
var XLSX      = require(path.join(__dirname, '..', 'node_modules', 'xlsx', 'xlsx.js'));

var NinjaModel  = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_model.js'));
var NinjaBundle = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_bundle.js'));
var PR          = require(path.join(__dirname, '..', 'build', 'erp', 'plugin_registry.js'));

var HRMIS_PATH   = path.resolve(__dirname, '..', '..', 'Projects', 'org.idempiere.ninja', 'templates', 'Ninja_HRMIS.xlsx');
var SEED_DB_PATH = path.resolve(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var BUNDLE_OUT   = path.resolve('/tmp', 'hrmis.foldbundle.mjs');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var ORACLE_TABLES = new Set([
  'HR_HumanResourceManagement','HR_JobDesignation','HR_Trainer','HR_Rank',
  'HR_TrainingCourse','HR_ManpowerRequirement','HR_ProjectTaskForce',
  'HR_EmployeeProvidentFund','HR_Employment','HR_EmploymentHistory',
  'HR_MedicalHIstory','HR_Payroll','HR_PayrollDetails','HR_FamilyMembers',
  'HR_Appraisal','HR_Training','HR_LeaveApplication','HR_AssistanceRequest'
]);

(async function main() {
  console.log('═══ W-NINJA-BUNDLE — emit + install a .foldbundle ═══\n');

  // 1. Parse + filter model
  var wb = XLSX.readFile(HRMIS_PATH);
  var rows = XLSX.utils.sheet_to_json(wb.Sheets['2_RO_ModelMaker'], { header: 1, defval: '' });
  var model = NinjaModel.parseSheet(rows, { format: 'romo' });
  model.tables = model.tables.filter(function (t) { return ORACLE_TABLES.has(t.name); });
  console.log('§ Model: bundleName=' + model.bundleName + ' tables=' + model.tables.length);

  // 2. Load sql.js + seed db
  var SQL = await initSqlJs({
    locateFile: function (f) { return path.join(__dirname, '..', 'node_modules', 'sql.js', 'dist', f); }
  });
  var db = new SQL.Database(fs.readFileSync(SEED_DB_PATH));
  NinjaBundle.makeWritableDbHost(db);
  console.log('§ Seed db loaded + .query attached');

  // 3. Host glue — kernel_ops appender (real INSERTs → opIds)
  db.run('CREATE TABLE IF NOT EXISTS kernel_ops (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER, op_type TEXT NOT NULL, parameters TEXT)');
  function appendOp(opType, params) {
    db.run("INSERT INTO kernel_ops (timestamp, op_type, parameters) VALUES (0,'" + opType + "','" +
      String(JSON.stringify(params || null)).replace(/'/g, "''") + "')");
    var r = db.exec('SELECT last_insert_rowid()');
    return r[0].values[0][0];
  }

  var host = { engineVersion: '0.10.0', db: db, ops: { append: appendOp } };
  var reg = PR.create(host);

  // ═══ Phase C-A — install the LIVE bundle object ═══════════════════════════════════════════════
  console.log('\n── C-A: install live bundle object ──');
  var bundle = NinjaBundle.emitBundle(model);
  console.log('§ Emitted bundle: id=' + bundle.manifest.id + ' version=' + bundle.manifest.version +
    ' contributes ' + bundle.manifest.contributions.ad.tables + ' tables');

  var ins = await reg.installBundle(bundle);
  verdict(ins.state === 'INSTALLED' && ins.opId != null, 'installed ' + ins.id, 'opId=' + ins.opId);

  var st = await reg.startBundle(ins.id);
  verdict(st.state === 'ACTIVE', 'started → ' + st.state);

  // windows reachable: Window→Tab→Table chain
  var chain = db.exec(
    'SELECT COUNT(*) FROM AD_Window w JOIN AD_Tab t ON t.AD_Window_ID=w.AD_Window_ID' +
    ' JOIN AD_Table tb ON tb.AD_Table_ID=t.AD_Table_ID' +
    " WHERE w.AD_Window_ID >= 7000000 AND w.IsActive='Y'"
  )[0].values[0][0];
  verdict(Number(chain) === model.tables.length, 'windows reachable (Window→Tab→Table)', chain + '/' + model.tables.length);

  // kernel_ops carries NINJA_STAGE (from activate) + PLUGIN_INSTALL/START
  var nstage = db.exec("SELECT COUNT(*) FROM kernel_ops WHERE op_type='NINJA_STAGE'")[0].values[0][0];
  var pinstall = db.exec("SELECT COUNT(*) FROM kernel_ops WHERE op_type='PLUGIN_INSTALL'")[0].values[0][0];
  verdict(Number(nstage) >= 1 && Number(pinstall) >= 1, 'kernel_ops: NINJA_STAGE + PLUGIN_INSTALL logged',
    'NINJA_STAGE×' + nstage + ' PLUGIN_INSTALL×' + pinstall);

  // ═══ Phase C-B — emit the .foldbundle FILE artifact + verify it round-trips ═══════════════════
  console.log('\n── C-B: emit .foldbundle file + round-trip install ──');
  var source = NinjaBundle.emitBundleSource(model, {
    stagePath: url.pathToFileURL(path.join(__dirname, '..', 'build', 'erp', 'ninja_stage.js')).href
  });
  fs.writeFileSync(BUNDLE_OUT, source);
  console.log('§ Wrote ' + BUNDLE_OUT + ' (' + source.length + ' bytes)');

  // Import the emitted file as an ES module and verify shape
  var mod = await import(url.pathToFileURL(BUNDLE_OUT).href);
  verdict(mod.manifest && mod.manifest.id === bundle.manifest.id, 'emitted file exports matching manifest', mod.manifest && mod.manifest.id);
  verdict(typeof mod.activate === 'function' && typeof mod.deactivate === 'function', 'emitted file exports activate + deactivate');
  verdict(mod.manifest.ninjaModel && mod.manifest.ninjaModel.tables.length === model.tables.length,
    'emitted manifest carries the model', mod.manifest.ninjaModel.tables.length + ' tables');

  // Round-trip install the FILE bundle into a FRESH db (proves the artifact is self-applying)
  var db2 = new SQL.Database(fs.readFileSync(SEED_DB_PATH));
  NinjaBundle.makeWritableDbHost(db2);
  db2.run('CREATE TABLE IF NOT EXISTS kernel_ops (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER, op_type TEXT NOT NULL, parameters TEXT)');
  function appendOp2(t, p) { db2.run("INSERT INTO kernel_ops (timestamp, op_type, parameters) VALUES (0,'" + t + "',NULL)"); var r = db2.exec('SELECT last_insert_rowid()'); return r[0].values[0][0]; }
  var reg2 = PR.create({ engineVersion: '0.10.0', db: db2, ops: { append: appendOp2 } });
  var ins2 = await reg2.installBundle(url.pathToFileURL(BUNDLE_OUT).href);  // install by URL (string path)
  var st2 = await reg2.startBundle(ins2.id);
  var chain2 = db2.exec(
    'SELECT COUNT(*) FROM AD_Window w JOIN AD_Tab t ON t.AD_Window_ID=w.AD_Window_ID' +
    " WHERE w.AD_Window_ID >= 7000000 AND w.IsActive='Y'"
  )[0].values[0][0];
  verdict(st2.state === 'ACTIVE' && Number(chain2) === model.tables.length,
    'file bundle round-trips: install-by-URL → ACTIVE → windows staged', 'state=' + st2.state + ' windows=' + chain2);

  // ═══ Phase C-C — stop retracts (deactivate rolls back) ════════════════════════════════════════
  console.log('\n── C-C: stop → deactivate rolls back ──');
  await reg.stopBundle(bundle.manifest.id);
  var activeAfter = db.exec("SELECT COUNT(*) FROM AD_Window WHERE AD_Window_ID >= 7000000 AND IsActive='Y'")[0].values[0][0];
  verdict(Number(activeAfter) === 0, 'stop() rolled back staged windows (IsActive=N)', 'active=' + activeAfter);

  // Verdict line
  console.log('');
  console.log('§NINJA-BUNDLE install id=' + bundle.manifest.id + ' state=ACTIVE models=' + model.tables.length);
  console.log('');
  console.log('═══ ' + (fails === 0 ? '🟢 W-NINJA-BUNDLE PASS' : '🔴 W-NINJA-BUNDLE FAIL (' + fails + ')') + ' ═══');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) {
  console.log('🔴 W-NINJA-BUNDLE THREW: ' + (e && e.stack || e));
  process.exit(1);
});
