// ⚠ DO NOT REMOVE — NINJA_MODE_LANE.md §Phase D: "Ninja mode" Create flow (whitebox, DOM-free).
// Drives the pill's Create controller end-to-end with NO browser: generate the starter sheet →
// preview the derived models → Emit & Install via PluginRegistry → assert windows reachable.
// Also runs the SAME flow on the full Ninja_HRMIS.xlsx (the equivalence anchor).
// Witness: §NINJA-PILL sheet=<name> tables=<n> install=ACTIVE
// (named poc_ninja_create to avoid clobbering the NinjaExcel reporting lane's poc_ninja_pill.js)
// Run via: bash build/erp/run_witness.sh scripts/poc_ninja_create.js
// Read the log after EVERY run.

'use strict';

var path = require('path');
var fs   = require('fs');

var initSqlJs    = require(path.join(__dirname, '..', 'node_modules', 'sql.js'));
var XLSX         = require(path.join(__dirname, '..', 'node_modules', 'xlsx', 'xlsx.js'));
var NinjaStarter = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_starter.js'));
var NinjaCreate  = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_create.js'));
var NinjaBundle  = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_bundle.js'));
var PR           = require(path.join(__dirname, '..', 'build', 'erp', 'plugin_registry.js'));

var SEED_DB_PATH = path.resolve(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var HRMIS_PATH   = path.resolve(__dirname, '..', '..', 'Projects', 'org.idempiere.ninja', 'templates', 'Ninja_HRMIS.xlsx');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// Build a PluginRegistry bound to a WRITABLE sql.js db (the pill's Create-mode host shape).
function makeRegistry(db) {
  NinjaBundle.makeWritableDbHost(db);
  db.run('CREATE TABLE IF NOT EXISTS kernel_ops (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER, op_type TEXT NOT NULL, parameters TEXT)');
  function appendOp(t, p) {
    db.run("INSERT INTO kernel_ops (timestamp, op_type, parameters) VALUES (0,'" + t + "','" +
      String(JSON.stringify(p || null)).replace(/'/g, "''") + "')");
    return db.exec('SELECT last_insert_rowid()')[0].values[0][0];
  }
  return PR.create({ engineVersion: '0.10.0', db: db, ops: { append: appendOp } });
}

function windowsReachable(db) {
  return Number(db.exec(
    'SELECT COUNT(*) FROM AD_Window w JOIN AD_Tab t ON t.AD_Window_ID=w.AD_Window_ID' +
    ' JOIN AD_Table tb ON tb.AD_Table_ID=t.AD_Table_ID' +
    " WHERE w.AD_Window_ID >= 7000000 AND w.IsActive='Y'"
  )[0].values[0][0]);
}

(async function main() {
  console.log('═══ W-NINJA-PILL — Ninja mode Create flow (whitebox, no DOM) ═══\n');

  var SQL = await initSqlJs({
    locateFile: function (f) { return path.join(__dirname, '..', 'node_modules', 'sql.js', 'dist', f); }
  });

  // ═══ Part 1 — the STARTER sheet (the user's "download a shell, self-learn" path) ════════════════
  console.log('── Part 1: starter sheet → preview → Emit & Install ──');
  var starterWb = NinjaStarter.starterWorkbook(XLSX);
  verdict(starterWb.SheetNames.indexOf('2_RO_ModelMaker') !== -1, 'starter has 2_RO_ModelMaker sheet', starterWb.SheetNames.join(','));

  var prev = NinjaCreate.previewSheet(starterWb, XLSX);
  verdict(!prev.error, 'preview parsed without error', prev.error || 'ok');
  verdict(prev.preview.length === 2, 'starter previews 2 tables', prev.preview.map(function (p) { return p.table; }).join(', '));

  // Verify the grammar tokens render in the preview (the self-learn point)
  var astAsset = prev.preview.find(function (p) { return p.table === 'AST_Asset'; });
  var byName = {}; astAsset.columns.forEach(function (c) { byName[c.name] = c.refType; });
  console.log('§ AST_Asset preview columns: ' + astAsset.columns.map(function (c) { return c.name + ':' + c.refType; }).join(', '));
  verdict(byName.PurchaseCost === 'number', 'A#PurchaseCost → number', byName.PurchaseCost);
  verdict(byName.PurchaseDate === 'date', 'D#PurchaseDate → date', byName.PurchaseDate);
  verdict(byName.IsInsured === 'yesno', 'Y#IsInsured → yesno', byName.IsInsured);
  verdict(byName.C_BPartner_ID === 'integer', '_ID → TableDir (integer)', byName.C_BPartner_ID);
  var astMaint = prev.preview.find(function (p) { return p.table === 'AST_Maintenance'; });
  verdict(astMaint.master === 'AST_Asset', 'AST_Maintenance master = AST_Asset (detail tab)', astMaint.master);

  var db1 = new SQL.Database(fs.readFileSync(SEED_DB_PATH));
  var reg1 = makeRegistry(db1);
  var res1 = await NinjaCreate.emitAndInstall(reg1, prev.model);
  verdict(res1.state === 'ACTIVE', 'starter Emit & Install → ' + res1.state, 'id=' + res1.id);
  var win1 = windowsReachable(db1);
  verdict(win1 === 2, 'starter windows reachable', win1 + '/2');
  console.log('§NINJA-PILL sheet=starter tables=' + res1.tables + ' install=' + res1.state);

  // ═══ Part 2 — the full HRMIS sheet (equivalence anchor: same flow, 18 tables) ═══════════════════
  console.log('\n── Part 2: Ninja_HRMIS.xlsx → preview → Emit & Install ──');
  var hrmisWb = XLSX.readFile(HRMIS_PATH);
  var prev2 = NinjaCreate.previewSheet(hrmisWb, XLSX, { format: 'romo', sheet: '2_RO_ModelMaker' });
  verdict(!prev2.error, 'HRMIS preview parsed', prev2.error || 'ok');
  console.log('§ HRMIS preview: ' + prev2.preview.length + ' tables, ' + prev2.warnings.length + ' warnings');

  // Filter to the 18 oracle tables (drop HR_Terminology — the all-L# row Java apply also drops)
  var ORACLE = new Set(['HR_HumanResourceManagement','HR_JobDesignation','HR_Trainer','HR_Rank',
    'HR_TrainingCourse','HR_ManpowerRequirement','HR_ProjectTaskForce','HR_EmployeeProvidentFund',
    'HR_Employment','HR_EmploymentHistory','HR_MedicalHIstory','HR_Payroll','HR_PayrollDetails',
    'HR_FamilyMembers','HR_Appraisal','HR_Training','HR_LeaveApplication','HR_AssistanceRequest']);
  prev2.model.tables = prev2.model.tables.filter(function (t) { return ORACLE.has(t.name); });

  var db2 = new SQL.Database(fs.readFileSync(SEED_DB_PATH));
  var reg2 = makeRegistry(db2);
  var res2 = await NinjaCreate.emitAndInstall(reg2, prev2.model);
  verdict(res2.state === 'ACTIVE', 'HRMIS Emit & Install → ' + res2.state, 'id=' + res2.id);
  var win2 = windowsReachable(db2);
  verdict(win2 === 18, 'HRMIS windows reachable', win2 + '/18');
  console.log('§NINJA-PILL sheet=Ninja_HRMIS tables=' + res2.tables + ' install=' + res2.state);

  // ═══ Part 3 — re-Create is idempotent (re-emit overwrites, no duplicate install) ════════════════
  console.log('\n── Part 3: re-Create idempotency ──');
  var res2b = await NinjaCreate.emitAndInstall(reg2, prev2.model);
  verdict(res2b.state === 'ACTIVE' && reg2.list().length === 1,
    're-emit replaces (1 bundle, still ACTIVE)', 'bundles=' + reg2.list().length);
  verdict(windowsReachable(db2) === 18, 'still 18 windows after re-emit (no dupes)', windowsReachable(db2) + '/18');

  console.log('');
  console.log('═══ ' + (fails === 0 ? '🟢 W-NINJA-PILL PASS' : '🔴 W-NINJA-PILL FAIL (' + fails + ')') + ' ═══');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) {
  console.log('🔴 W-NINJA-PILL THREW: ' + (e && e.stack || e));
  process.exit(1);
});
