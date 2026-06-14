#!/usr/bin/env node
// ⚠ DO NOT REMOVE — extract_odoo_extras.js
// Step 1 of ODOO_QWEB_PRINT_FOLD.md §3 + ODOO_SERVER_ACTION_FOLD.md §3
// Pulls QWeb report defs (ir.actions.report + ir.ui.view arch) + server actions (ir.actions.server)
// from live Odoo 17 (odoodemo) into build/erp/odoo_extras.db.
// Run: node scripts/extract_odoo_extras.js 2>&1 | tee build/erp/extract_odoo_extras.log
// READ THE LOG before conclusions. NON-INVENT — every row is a real Odoo row.
'use strict';
var fs = require('fs'), path = require('path'), http = require('http');
var initSqlJs = require('sql.js');

var ODB = 'odoodemo', OL = 'admin', OP = 'admin';
var OUT = path.join(__dirname, '..', 'build', 'erp', 'odoo_extras.db');

function rpc(s, m, a) {
  return new Promise(function (res, rej) {
    var b = JSON.stringify({ jsonrpc: '2.0', method: 'call', params: { service: s, method: m, args: a } });
    var r = http.request({ host: 'localhost', port: 8069, path: '/jsonrpc', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) } }, function (x) {
      var d = ''; x.on('data', function (c) { d += c; }); x.on('end', function () {
        try { var j = JSON.parse(d); j.error ? rej(new Error(JSON.stringify(j.error.data && j.error.data.message || j.error))) : res(j.result); } catch (e) { rej(e); } }); });
    r.on('error', rej); r.write(b); r.end();
  });
}

(async function () {
  var log = []; function L(m) { console.log(m); log.push(m); }
  L('\n══ EXTRACT-ODOO-EXTRAS — QWeb reports + server actions from ' + ODB + ' ══\n');

  var uid = await rpc('common', 'login', [ODB, OL, OP]);
  if (!uid) { L('§EXTRACT-ODOO-EXTRAS FAIL auth'); process.exit(1); }
  var ex = function (model, meth, args, kw) {
    return rpc('object', 'execute_kw', [ODB, uid, OP, model, meth, args, kw || {}]);
  };

  // ── 1. QWeb report actions (ir.actions.report) ──
  L('── 1. ir.actions.report (QWeb) ──');
  var reports = await ex('ir.actions.report', 'search_read', [[]], {
    fields: ['id', 'name', 'model', 'report_name', 'report_type', 'paperformat_id'], limit: 200
  });
  L('§QWEB-REPORTS count=' + reports.length + ' (expected ~41)');
  reports.forEach(function (r) {
    L('   rep ' + r.id + ' | ' + r.report_type + ' | ' + r.model + ' | ' + r.name + ' | tpl=' + r.report_name);
  });

  // ── 2. QWeb view arch — fetch by xmlid key (report_name = external id of the template view) ──
  L('\n── 2. ir.ui.view arch for each report template ──');
  var viewArchMap = {};
  // Batch: search ir.ui.view where key in report_name list
  var tplKeys = reports.map(function (r) { return r.report_name; }).filter(Boolean);
  var views = await ex('ir.ui.view', 'search_read', [[['key', 'in', tplKeys], ['type', '=', 'qweb']]], {
    fields: ['id', 'key', 'arch_base', 'model'], limit: 500
  });
  views.forEach(function (v) { viewArchMap[v.key] = { id: v.id, arch: v.arch_base, model: v.model }; });
  L('§QWEB-VIEWS fetched=' + views.length + ' of ' + tplKeys.length + ' templates');
  var missed = tplKeys.filter(function (k) { return !viewArchMap[k]; });
  if (missed.length) L('§QWEB-VIEWS missed=' + missed.length + ': ' + missed.slice(0, 5).join(', '));

  // ── 3. Server actions (ir.actions.server) ──
  L('\n── 3. ir.actions.server ──');
  var srvActions = await ex('ir.actions.server', 'search_read', [[]], {
    fields: ['id', 'name', 'model_id', 'state', 'code', 'child_ids', 'binding_type'], limit: 300
  });
  L('§SRVACT-RAW count=' + srvActions.length + ' (expected ~64)');

  // classify by state
  var hist = {};
  srvActions.forEach(function (a) { hist[a.state] = (hist[a.state] || 0) + 1; });
  var histStr = Object.keys(hist).sort().map(function (k) { return k + '=' + hist[k]; }).join(' ');
  L('§SRVACT-CLASSIFY ' + histStr + ' total=' + srvActions.length);

  // ── 4. Cron actions (ir.cron) ──
  L('\n── 4. ir.cron ──');
  var crons = await ex('ir.cron', 'search_read', [[]], {
    fields: ['id', 'name', 'model_id', 'interval_type', 'interval_number', 'active'], limit: 200
  });
  L('§CRON count=' + crons.length + ' (expected ~18)');

  // ── 5. Write SQLite ──
  L('\n── 5. Write SQLite ──');
  var SQL = await initSqlJs();
  var db = new SQL.Database();

  db.run('CREATE TABLE qweb_report (id INTEGER PRIMARY KEY, name TEXT, model TEXT, report_name TEXT, report_type TEXT, paperformat TEXT, has_arch INTEGER)');
  db.run('CREATE TABLE qweb_view (id INTEGER PRIMARY KEY, key TEXT, model TEXT, arch TEXT)');
  db.run('CREATE TABLE server_action (id INTEGER PRIMARY KEY, name TEXT, model_name TEXT, state TEXT, binding_type TEXT, child_ids TEXT, code TEXT, is_declarative INTEGER)');
  db.run('CREATE TABLE cron_action (id INTEGER PRIMARY KEY, name TEXT, model_name TEXT, interval_type TEXT, interval_number INTEGER, active INTEGER)');

  var stmtRep = db.prepare('INSERT INTO qweb_report VALUES (?,?,?,?,?,?,?)');
  reports.forEach(function (r) {
    var pf = r.paperformat_id ? r.paperformat_id[1] : null;
    var hasArch = viewArchMap[r.report_name] ? 1 : 0;
    stmtRep.run([r.id, r.name, r.model, r.report_name, r.report_type, pf, hasArch]);
  });
  stmtRep.free();

  var stmtV = db.prepare('INSERT INTO qweb_view VALUES (?,?,?,?)');
  views.forEach(function (v) { stmtV.run([v.id, v.key, v.model, v.arch_base]); });
  stmtV.free();

  var DECLARATIVE = ['object_create', 'object_write', 'multi'];
  var stmtSA = db.prepare('INSERT INTO server_action VALUES (?,?,?,?,?,?,?,?)');
  srvActions.forEach(function (a) {
    var mn = a.model_id ? a.model_id[1] : null;
    var isDecl = DECLARATIVE.indexOf(a.state) >= 0 ? 1 : 0;
    var childJson = a.child_ids && a.child_ids.length ? JSON.stringify(a.child_ids) : null;
    stmtSA.run([a.id, a.name, mn, a.state, a.binding_type || null, childJson, a.code || null, isDecl]);
  });
  stmtSA.free();

  var stmtCron = db.prepare('INSERT INTO cron_action VALUES (?,?,?,?,?,?)');
  crons.forEach(function (c) {
    var mn = c.model_id ? c.model_id[1] : null;
    stmtCron.run([c.id, c.name, mn, c.interval_type, c.interval_number, c.active ? 1 : 0]);
  });
  stmtCron.free();

  fs.writeFileSync(OUT, Buffer.from(db.export()));
  var sz = fs.statSync(OUT).size;

  // ── 6. Summary ──
  var nRep = reports.length, nV = views.length, nSA = srvActions.length, nCron = crons.length;
  var nDecl = srvActions.filter(function (a) { return DECLARATIVE.indexOf(a.state) >= 0; }).length;
  var nDefer = nSA - nDecl;
  L('\n§EXTRACT-ODOO-EXTRAS DONE bytes=' + sz + ' path=' + path.basename(OUT));
  L('  qweb_report=' + nRep + ' qweb_view=' + nV + ' (arch_matched=' + nV + ')');
  L('  server_action=' + nSA + ' declarative=' + nDecl + ' deferred=' + nDefer + ' (' + histStr + ')');
  L('  cron=' + nCron);
  var pass = nRep > 0 && nSA > 0;
  L('\n§EXTRACT-ODOO-EXTRAS ' + (pass ? 'PASS' : 'FAIL'));
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'extract_odoo_extras.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§EXTRACT-ODOO-EXTRAS ERROR', e.message); process.exit(2); });
