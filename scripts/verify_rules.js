#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * verify_rules.js — ERP Step 1 acceptance gate (prompts/ERP_RAW_MIGRATION.md Step 1).
 *
 * Proves, from the BUILT erp_rules.db (not from trust):
 *   1. Well-formedness — every rule record has a known event_type + form + non-empty
 *      binding; sql rules carry a body; handler rules are stubs (body NULL, oracle_ptr set).
 *   2. End-to-end byte-identical chain — AD_Rule.Script survived PG -> ad_full.db ->
 *      erp_rules.db unchanged (md5 vs PG), so the rule evaluator runs the real SQL.
 *   3. Product-scope portability — every §0.3 DocEvent handler stub points at an oracle
 *      class that EXISTS locally (the hand-port worklist is actionable).
 *   4. sql.js read — the PWA engine opens erp_rules.db and runs the dispatch-by-form
 *      queries the runtime evaluator (§0.10 abstract engine) will use.
 *
 * READ THE LOG. Run: node scripts/verify_rules.js 2>&1 | tee build/erp/verify_rules.log
 */
'use strict';
var fs = require('fs');
var path = require('path');
var crypto = require('crypto');
var execFileSync = require('child_process').execFileSync;
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');

var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var CONTAINER = process.env.ERP_PG_CONTAINER || 'postgres';
var PGDB = process.env.ERP_PG_DB || 'idempiere';
var PGUSER = process.env.ERP_PG_USER || 'adempiere';

var EVENT_TYPES = ['Callout', 'DocEvent', 'Validation', 'AccountingRule', 'DocPolicy', 'Workflow',
  'MatchPolicy', 'Access'];  // MatchPolicy/Access added by the compile→engine wiring (§0.14)
var FORMS = ['sql', 'expression', 'table', 'handler', 'policy'];

(async function () {
  var fail = 0;
  function check(cond, msg) { if (!cond) { fail++; console.log('   ! FAIL ' + msg); } }

  var db = new Database(RULES, { readonly: true });

  // 1) Well-formedness.
  var rules = db.prepare('SELECT * FROM rules').all();
  var bad = { etype: 0, form: 0, binding: 0, sqlBody: 0, handlerStub: 0, policyJson: 0 };
  rules.forEach(function (r) {
    if (EVENT_TYPES.indexOf(r.event_type) < 0) bad.etype++;
    if (FORMS.indexOf(r.form) < 0) bad.form++;
    if (!r.binding) bad.binding++;
    if (r.form === 'sql' && (!r.body || !r.body.trim())) bad.sqlBody++;
    if (r.form === 'handler' && (r.body !== null || !r.oracle_ptr)) bad.handlerStub++;
    if (r.form === 'policy') { try { JSON.parse(r.body); } catch (e) { bad.policyJson++; } }
  });
  check(bad.etype === 0, 'unknown event_type x' + bad.etype);
  check(bad.form === 0, 'unknown form x' + bad.form);
  check(bad.binding === 0, 'empty binding x' + bad.binding);
  check(bad.sqlBody === 0, 'sql rule with no body x' + bad.sqlBody);
  check(bad.handlerStub === 0, 'handler not a clean stub x' + bad.handlerStub);
  check(bad.policyJson === 0, 'policy body not JSON x' + bad.policyJson);
  console.log('§RULES verify wellformed records=' + rules.length +
    ' badEventType=' + bad.etype + ' badForm=' + bad.form + ' emptyBinding=' + bad.binding +
    ' sqlNoBody=' + bad.sqlBody + ' badHandler=' + bad.handlerStub + ' badPolicyJSON=' + bad.policyJson);

  // 2) End-to-end byte-identical chain for AD_Rule (PG md5 vs erp_rules.body md5).
  var pgMd5 = {};
  execFileSync('docker', ['exec', CONTAINER, 'psql', '-U', PGUSER, '-d', PGDB, '-t', '-A', '-F', '\t',
    '-c', 'SELECT ad_rule_id, md5(script) FROM ad_rule WHERE script IS NOT NULL'],
    { encoding: 'utf8' }).trim().split('\n').forEach(function (l) {
      var p = l.split('\t'); pgMd5[p[0]] = p[1];
    });
  var chainN = 0, chainOk = 0;
  db.prepare("SELECT rule_key, body FROM rules WHERE rule_key LIKE 'AD_RULE:%'").all().forEach(function (r) {
    chainN++;
    var id = r.rule_key.split(':')[1];
    var m = crypto.createHash('md5').update(r.body, 'utf8').digest('hex');
    if (m === pgMd5[id]) chainOk++; else console.log('   ! chain ' + r.rule_key + ' md5 ' + m + ' != PG ' + pgMd5[id]);
  });
  check(chainN > 0 && chainOk === chainN, 'AD_Rule chain ' + chainOk + '/' + chainN);
  console.log('§RULES verify chain AD_Rule PG->ad_full->erp_rules byteIdentical=' + chainOk + '/' + chainN +
    ' => ' + (chainOk === chainN && chainN > 0 ? 'PASS' : 'FAIL'));

  // 3) Product-scope DocEvent portability — every stub's oracle class present locally.
  var de = db.prepare("SELECT cell, oracle_class, oracle_present FROM handler_backlog WHERE kind='docevent'").all();
  var deMissing = de.filter(function (r) { return !r.oracle_present; });
  check(de.length > 0 && deMissing.length === 0, 'product-scope oracle missing x' + deMissing.length);
  deMissing.forEach(function (r) { console.log('   ! docevent oracle absent ' + r.cell + ' -> ' + r.oracle_class); });
  var topCell = db.prepare("SELECT cell, gravity_rank FROM handler_backlog WHERE kind='docevent' ORDER BY gravity_rank DESC LIMIT 1").get();
  check(topCell && topCell.cell === 'C_Order:CO', 'hottest cell is ' + (topCell && topCell.cell) + ' (expected C_Order:CO)');
  console.log('§RULES verify docevents=' + de.length + ' oraclePresent=' + (de.length - deMissing.length) + '/' + de.length +
    ' hottest=' + (topCell && topCell.cell) + '@' + (topCell && topCell.gravity_rank) +
    ' => ' + (deMissing.length === 0 && topCell && topCell.cell === 'C_Order:CO' ? 'PASS' : 'FAIL'));
  db.close();

  // 4) sql.js read — the PWA engine + the runtime dispatch-by-form queries.
  var SQL = await initSqlJs();
  var sdb = new SQL.Database(fs.readFileSync(RULES));
  function one(sql) { try { var r = sdb.exec(sql); return r.length ? r[0].values[0][0] : 0; } catch (e) { fail++; console.log('   ! sql.js ' + sql + ' :: ' + e.message); return -1; } }
  // dispatch-by-form (what the §0.10 abstract engine asks at each cell):
  var bySql = one("SELECT count(*) FROM rules WHERE form='sql'");
  var byHandler = one("SELECT count(*) FROM rules WHERE form='handler'");
  var byPolicy = one("SELECT count(*) FROM rules WHERE form='policy'");
  var byTable = one("SELECT count(*) FROM rules WHERE form='table'");
  // cell lookup the kernel will do: rules at (C_Order, CO):
  var atCell = one("SELECT count(*) FROM rules WHERE binding='C_Order:CO'");
  console.log('§RULES verify sql.js dispatch sql=' + bySql + ' handler=' + byHandler +
    ' policy=' + byPolicy + ' table=' + byTable + ' rulesAt(C_Order:CO)=' + atCell);
  check(bySql > 0 && byHandler > 0 && byPolicy > 0 && atCell > 0, 'sql.js dispatch query returned empty');

  // 5) Wiring inputs (§0.14): the loadCell evaluator sources ordering + access from these.
  var mpCount = one("SELECT count(*) FROM rules WHERE event_type='MatchPolicy'");
  var mpFifo = one("SELECT count(*) FROM rules WHERE event_type='MatchPolicy' AND body LIKE '%\"order\":\"FIFO\"%'");
  var acCount = one("SELECT count(*) FROM rules WHERE event_type='Access'");
  var acAllOrgs = one("SELECT count(*) FROM rules WHERE event_type='Access' AND body LIKE '%\"allOrgs\":true%'");
  console.log('§RULES verify wiring matchpolicy=' + mpCount + ' (FIFO-default=' + mpFifo + ')' +
    ' access=' + acCount + ' (allOrgs-roles=' + acAllOrgs + ')');
  check(mpCount > 0 && mpFifo === mpCount, 'MatchPolicy records missing or not FIFO-default');
  check(acCount > 0, 'Access records missing');
  sdb.close();

  console.log('§RULES VERIFY ' + (fail ? 'FAIL (' + fail + ' checks failed)' : 'PASS — all checks green'));
  process.exit(fail ? 1 : 0);
})();
