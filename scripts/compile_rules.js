#!/usr/bin/env node
/**
 * compile_rules.js — ERP Step 1: the Rule Compiler.
 *   Spec: prompts/ERP_RAW_MIGRATION.md (TASK Step 1) + docs/ERP.md §0.9–0.10.
 *
 * Reads LOCAL SQLite (build/erp/ad_full.db — NOT PG, NOT Java, like compile_manifest.js)
 * and PLACES logic as §0.9 rule records { eventType, form, body, binding } into
 * build/erp/erp_rules.db. Two honesty tiers (§0.10):
 *
 *   AUTO-EXTRACT (declarative/script/flag — deterministic, safe to automate):
 *     - AD_Rule        -> form=sql      (ruletype 'Q'); accounting/GL rules.
 *     - AD_Val_Rule    -> form=sql      (type 'S' code = WHERE-clause); validation/lookup.
 *     - C_DocType flags -> form=policy  (the editable preset knobs §0.9 completeIt branches on).
 *     - ad_workflow    -> form=table    (declarative process graph, 2nd source beside P2 WfMC).
 *     - Callout binding -> form=handler (the cell->handler WIRING; body is Java, see below).
 *
 *   HAND-PORT BACKLOG (procedural Java — NOT auto-translatable, §18.10):
 *     - Callout (org.compiere.callout.*) — 148 distinct class.method, each a stub w/ oracle ptr.
 *     - DocEvent product-scope handlers (§0.3 O2C/P2P/GL/inventory) — (baseTable,action) cells
 *       pointing at the confirmed local oracle (MOrder/MInvoice/MInOut/MPayment.completeIt),
 *       gravity-ranked by the §18.2 hub order. Bodies hand-ported later, diff-oracle verified.
 *
 * EXTRACT, never invent. Every rule record traces to a row in ad_full.db (or, for the
 * DocEvent backlog, to the confirmed §18.10 Java oracle + the C_DocType base table).
 *
 * READ THE LOG. Run: node scripts/compile_rules.js 2>&1 | tee build/erp/compile_rules.log
 */
'use strict';
var fs = require('fs');
var path = require('path');
var Database = require('better-sqlite3');

var SRC = process.env.ERP_AD_FULL ||
  path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var OUT = process.env.ERP_RULES_OUT ||
  path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');

// §18.10 confirmed oracle (local) — the iDempiere source tree. We index Java class
// basenames once so the backlog can honestly flag which oracle classes are present
// (callouts live across modules: org.adempiere.base.callout, org.idempiere.fa, …).
var ORACLE_ROOT = (process.env.ERP_ORACLE_ROOT || (process.env.HOME || '') +
  '/idempiere-dev-setup/idempiere');
function indexOracle(root) {
  var set = Object.create(null);
  if (!fs.existsSync(root)) return set;
  var stack = [root];
  while (stack.length) {
    var dir = stack.pop();
    var ents;
    try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { continue; }
    for (var i = 0; i < ents.length; i++) {
      var e = ents[i];
      if (e.isDirectory()) { stack.push(path.join(dir, e.name)); }
      else if (e.name.slice(-5) === '.java') { set[e.name.slice(0, -5)] = 1; }
    }
  }
  return set;
}
var ORACLE_CLASSES = indexOracle(ORACLE_ROOT);
function oraclePresent(dottedClass) {
  var clsName = dottedClass.slice(dottedClass.lastIndexOf('.') + 1);
  return ORACLE_CLASSES[clsName] ? 1 : 0;
}

// Product scope (§0.3) hot-cell DocEvent handlers: DocBaseType -> oracle + gravity.
// gravity follows §18.2 hub order (C_Order #1). action 'CO' (Complete) is the hot verb.
var PRODUCT_DOCEVENTS = [
  { base: 'C_Order', bases: ['SOO', 'POO'], oracle: 'MOrder.completeIt', gravity: 100, effect: 'createShipment/createInvoice fan-out (DocType flags)' },
  { base: 'C_Invoice', bases: ['ARI', 'API'], oracle: 'MInvoice.completeIt', gravity: 90, effect: 'matchInv + journal posting' },
  { base: 'M_InOut', bases: ['MMS', 'MMR'], oracle: 'MInOut.completeIt', gravity: 80, effect: 'storageOnHand + matchPO' },
  { base: 'C_Payment', bases: ['ARR', 'APP'], oracle: 'MPayment.completeIt', gravity: 70, effect: 'allocation + journal' },
  { base: 'GL_Journal', bases: ['GLJ'], oracle: 'MJournal.completeIt', gravity: 60, effect: 'GL posting' }
];

// Settlement cells run the generic Detail⋈Detail matcher (§0.14). Each gets an EDITABLE
// ordering-policy rule record (§0.5) so the FIFO/LIFO/by-amount knob is DATA the engine
// loads, not a JS literal in the harness. Default FIFO (logged). The matcher narrows its
// partition by role/org access (§0.8); the Access records below supply allowOrgs.
var SETTLEMENT_CELLS = [
  { cell: 'M_InOut:CO', key: 'm_product_id', effect: 'matchPO + matchInv (vendor receipt ⋈ invoice/PO line)' },
  { cell: 'C_Invoice:CO', key: 'm_product_id', effect: 'matchInv (vendor invoice ⋈ receipt line)' }
];
var MATCHPOLICY_DEFAULT = { order: 'FIFO', key: 'm_product_id', tol: 0.0001, dateField: 'movementdate' };

function main() {
  if (!fs.existsSync(SRC)) {
    console.error('§RULES FATAL — source not found: ' + SRC + ' (run migrate_pg_to_sqlite.js first)');
    process.exit(1);
  }
  var ad = new Database(SRC, { readonly: true });
  if (fs.existsSync(OUT)) fs.unlinkSync(OUT);
  var rdb = new Database(OUT);
  rdb.pragma('journal_mode = OFF');

  // ── §0.9 rule-record schema ───────────────────────────────────────────────
  rdb.exec(
    'CREATE TABLE rules (' +
    '  id INTEGER PRIMARY KEY,' +
    '  rule_key TEXT UNIQUE,' +       // stable identity, e.g. CALLOUT:C_Order.M_Product_ID
    '  event_type TEXT,' +           // Callout | DocEvent | Validation | AccountingRule | DocPolicy | Workflow
    '  form TEXT,' +                 // sql | expression | table | handler | policy
    '  binding TEXT,' +              // the cell: table.column | DocType:action | C_DocType:<name>
    '  body TEXT,' +                 // SQL/expression text (NULL for handler stub / policy=JSON)
    '  params TEXT,' +               // JSON: extra metadata
    '  source TEXT,' +               // ad_rule | ad_val_rule | ad_column.callout | c_doctype | ad_workflow | oracle:§18.10
    '  oracle_ptr TEXT,' +           // class.method for hand-port; NULL for declarative
    '  gravity_rank INTEGER DEFAULT 0,' +
    '  status TEXT,' +               // preset (ready) | stub (body unwritten)
    '  editable INTEGER DEFAULT 1' + // capability-first (§0.8): every rule ships editable
    ')');
  // Dev worklist: distinct procedural Java to hand-port, diff-oracle verified.
  rdb.exec(
    'CREATE TABLE handler_backlog (' +
    '  id INTEGER PRIMARY KEY,' +
    '  cell TEXT,' +                 // table.column (callout) | DocType:action (docevent)
    '  kind TEXT,' +                 // callout | docevent
    '  oracle_class TEXT,' +
    '  oracle_method TEXT,' +
    '  oracle_present INTEGER,' +    // 1 if the local Java file exists (verified)
    '  binding_count INTEGER,' +     // how many cells use this handler (callout gravity)
    '  gravity_rank INTEGER DEFAULT 0,' +
    '  status TEXT DEFAULT "unwritten",' +
    '  notes TEXT' +
    ')');

  var insRule = rdb.prepare('INSERT OR IGNORE INTO rules ' +
    '(rule_key,event_type,form,binding,body,params,source,oracle_ptr,gravity_rank,status) ' +
    'VALUES (@rule_key,@event_type,@form,@binding,@body,@params,@source,@oracle_ptr,@gravity_rank,@status)');
  var insBack = rdb.prepare('INSERT INTO handler_backlog ' +
    '(cell,kind,oracle_class,oracle_method,oracle_present,binding_count,gravity_rank,notes) ' +
    'VALUES (@cell,@kind,@oracle_class,@oracle_method,@oracle_present,@binding_count,@gravity_rank,@notes)');

  var n = { sql: 0, expr: 0, policy: 0, workflow: 0, callouts: 0, docevents: 0, matchpolicy: 0, access: 0 };

  var tx = rdb.transaction(function () {
    // 1) AD_Rule — SQL (ruletype 'Q') / expression ('S').
    ad.prepare('SELECT ad_rule_id,value,name,eventtype,ruletype,script FROM ad_rule WHERE script IS NOT NULL').all()
      .forEach(function (r) {
        var form = (r.ruletype === 'S') ? 'expression' : 'sql';
        insRule.run({
          rule_key: 'AD_RULE:' + r.ad_rule_id, event_type: 'AccountingRule', form: form,
          binding: r.value || ('AD_Rule:' + r.ad_rule_id), body: r.script,
          params: JSON.stringify({ name: r.name, eventtype: r.eventtype, ruletype: r.ruletype }),
          source: 'ad_rule', oracle_ptr: null, gravity_rank: 0, status: 'preset'
        });
        if (form === 'sql') n.sql++; else n.expr++;
      });

    // 2) AD_Val_Rule — SQL where-clause (type 'S'). Resolve binding = referencing columns.
    var valBind = {};
    ad.prepare("SELECT c.ad_val_rule_id vr, t.tablename||'.'||c.columnname col " +
      'FROM ad_column c JOIN ad_table t ON t.ad_table_id=c.ad_table_id ' +
      'WHERE c.ad_val_rule_id IS NOT NULL').all()
      .forEach(function (r) { (valBind[r.vr] = valBind[r.vr] || []).push(r.col); });
    ad.prepare('SELECT ad_val_rule_id,name,type,code FROM ad_val_rule WHERE code IS NOT NULL').all()
      .forEach(function (r) {
        var cols = valBind[r.ad_val_rule_id] || [];
        insRule.run({
          rule_key: 'VALRULE:' + r.ad_val_rule_id, event_type: 'Validation', form: 'sql',
          binding: cols.length ? cols[0] : ('AD_Val_Rule:' + r.ad_val_rule_id), body: r.code,
          params: JSON.stringify({ name: r.name, type: r.type, boundColumns: cols }),
          source: 'ad_val_rule', oracle_ptr: null, gravity_rank: cols.length, status: 'preset'
        });
        n.sql++;
      });

    // 3) C_DocType policy flags — the editable preset knobs (§0.9). form=policy, body=JSON.
    var dtCols = ad.prepare("SELECT name FROM pragma_table_info('c_doctype')").all()
      .map(function (r) { return r.name.toLowerCase(); });
    var FLAG_COLS = ['isautogenerateinout', 'isautogenerateinvoice', 'docsubtypeso', 'docsubtypeinv',
      'isssotrx', 'issotrx', 'isshipconfirm', 'ispickqaconfirm', 'isindexed', 'isdoccontrolled',
      'hascharges', 'isdefault', 'documentnote'].filter(function (c) { return dtCols.indexOf(c) >= 0; });
    var selCols = ['c_doctype_id', 'name', 'docbasetype', 'docsubtypeso'].filter(function (c) { return dtCols.indexOf(c) >= 0; })
      .concat(FLAG_COLS).filter(function (v, i, a) { return a.indexOf(v) === i; });
    ad.prepare('SELECT ' + selCols.join(',') + ' FROM c_doctype').all().forEach(function (r) {
      var flags = {}; FLAG_COLS.forEach(function (c) { flags[c] = r[c]; });
      insRule.run({
        rule_key: 'DOCPOLICY:' + r.c_doctype_id, event_type: 'DocPolicy', form: 'policy',
        binding: 'C_DocType:' + (r.name || r.c_doctype_id), body: JSON.stringify(flags),
        params: JSON.stringify({ docBaseType: r.docbasetype, name: r.name }),
        source: 'c_doctype', oracle_ptr: null, gravity_rank: 0, status: 'preset'
      });
      n.policy++;
    });

    // 4) ad_workflow — declarative process graph (summary). form=table.
    var nodeCnt = {}, edgeCnt = {};
    ad.prepare('SELECT ad_workflow_id, count(*) c FROM ad_wf_node GROUP BY ad_workflow_id').all()
      .forEach(function (r) { nodeCnt[r.ad_workflow_id] = r.c; });
    ad.prepare('SELECT n.ad_workflow_id, count(*) c FROM ad_wf_nodenext x ' +
      'JOIN ad_wf_node n ON n.ad_wf_node_id=x.ad_wf_node_id GROUP BY n.ad_workflow_id').all()
      .forEach(function (r) { edgeCnt[r.ad_workflow_id] = r.c; });
    ad.prepare('SELECT ad_workflow_id,value,name,workflowtype FROM ad_workflow').all().forEach(function (r) {
      insRule.run({
        rule_key: 'WF:' + r.ad_workflow_id, event_type: 'Workflow', form: 'table',
        binding: r.value || ('AD_Workflow:' + r.ad_workflow_id), body: null,
        params: JSON.stringify({ name: r.name, wftype: r.workflowtype,
          nodes: nodeCnt[r.ad_workflow_id] || 0, edges: edgeCnt[r.ad_workflow_id] || 0 }),
        source: 'ad_workflow', oracle_ptr: null, gravity_rank: 0, status: 'preset'
      });
      n.workflow++;
    });

    // 5) Callout bindings — cell->handler wiring. form=handler, body=NULL (procedural Java).
    var calloutCols = ad.prepare(
      "SELECT t.tablename tbl, c.columnname col, c.callout FROM ad_column c " +
      "JOIN ad_table t ON t.ad_table_id=c.ad_table_id " +
      "WHERE c.callout IS NOT NULL AND c.callout<>''").all();
    var handlerBind = {}; // class.method -> binding count
    calloutCols.forEach(function (r) {
      var cell = r.tbl + '.' + r.col;
      insRule.run({
        rule_key: 'CALLOUT:' + cell, event_type: 'Callout', form: 'handler',
        binding: cell, body: null,
        params: JSON.stringify({ callout: r.callout }),
        source: 'ad_column.callout', oracle_ptr: r.callout, gravity_rank: 0, status: 'stub'
      });
      n.callouts++;
      r.callout.split(';').forEach(function (cm) {
        cm = cm.trim(); if (cm) handlerBind[cm] = (handlerBind[cm] || 0) + 1;
      });
    });

    // 5b) handler_backlog — distinct callout class.method (the hand-port worklist).
    Object.keys(handlerBind).sort().forEach(function (cm) {
      var dot = cm.lastIndexOf('.');
      var cls = dot > 0 ? cm.slice(0, dot) : cm;
      var meth = dot > 0 ? cm.slice(dot + 1) : '';
      var present = oraclePresent(cls);
      insBack.run({
        cell: cm, kind: 'callout', oracle_class: cls, oracle_method: meth,
        oracle_present: present, binding_count: handlerBind[cm],
        gravity_rank: handlerBind[cm], notes: 'field onChange callout (hand-port)'
      });
    });

    // 6) DocEvent product-scope handler stubs (§18.10) + backlog. Both CO and VO cells.
    PRODUCT_DOCEVENTS.forEach(function (h) {
      var clsName = h.oracle.split('.')[0];
      var meth = h.oracle.split('.')[1];
      var present = oraclePresent('org.compiere.model.' + clsName);
      ['CO', 'VO'].forEach(function (action) {
        var cell = h.base + ':' + action;
        var g = h.gravity - (action === 'CO' ? 0 : 5);
        insRule.run({
          rule_key: 'DOCEVENT:' + cell, event_type: 'DocEvent', form: 'handler',
          binding: cell, body: null,
          params: JSON.stringify({ docBaseTypes: h.bases, effect: h.effect, action: action }),
          source: 'oracle:§18.10', oracle_ptr: h.oracle, gravity_rank: g, status: 'stub'
        });
        n.docevents++;
        insBack.run({
          cell: cell, kind: 'docevent', oracle_class: 'org.compiere.model.' + clsName,
          oracle_method: meth, oracle_present: present, binding_count: 1,
          gravity_rank: g, notes: h.effect + ' (action ' + action + ')'
        });
      });
    });

    // 7) MATCHPOLICY — editable ordering-policy rule per settlement cell (§0.5/§0.14).
    //    body=JSON the engine loads as E.match() opts; default FIFO (logged at runtime).
    SETTLEMENT_CELLS.forEach(function (s) {
      var pol = Object.assign({}, MATCHPOLICY_DEFAULT, { key: s.key });
      insRule.run({
        rule_key: 'MATCHPOLICY:' + s.cell, event_type: 'MatchPolicy', form: 'policy',
        binding: s.cell, body: JSON.stringify(pol),
        params: JSON.stringify({ effect: s.effect, default: MATCHPOLICY_DEFAULT.order }),
        source: 'seed:§0.5', oracle_ptr: null, gravity_rank: 0, status: 'preset'
      });
      n.matchpolicy++;
    });

    // 8) ACCESS — compile ad_role + ad_role_orgaccess + ad_document_action_access into one
    //    editable rule record per role (§0.8). body carries: allowOrgs (the matcher's
    //    partition narrowing) + the may-run gate {docTypeId -> [action codes]}. Capability
    //    -first: isaccessallorgs='Y' => allOrgs (no narrowing). Every opt traces to a row.
    var orgAccess = {};   // roleId -> [orgId]
    ad.prepare('SELECT ad_role_id, ad_org_id FROM ad_role_orgaccess WHERE isactive=\'Y\'').all()
      .forEach(function (r) { (orgAccess[r.ad_role_id] = orgAccess[r.ad_role_id] || []).push(r.ad_org_id); });
    var actAccess = {};   // roleId -> { doctypeId -> [code] } (ad_ref_list.value = action code)
    ad.prepare("SELECT daa.ad_role_id rid, daa.c_doctype_id dt, rl.value code " +
      "FROM ad_document_action_access daa JOIN ad_ref_list rl ON rl.ad_ref_list_id=daa.ad_ref_list_id " +
      "WHERE daa.isactive='Y'").all()
      .forEach(function (r) {
        var byRole = actAccess[r.rid] = actAccess[r.rid] || {};
        (byRole[r.dt] = byRole[r.dt] || []).push(r.code);
      });
    ad.prepare('SELECT ad_role_id, name, isaccessallorgs FROM ad_role').all().forEach(function (r) {
      var allOrgs = r.isaccessallorgs === 'Y';
      var body = {
        role: r.ad_role_id, name: r.name, allOrgs: allOrgs,
        allowOrgs: allOrgs ? null : (orgAccess[r.ad_role_id] || []).sort(function (a, b) { return a - b; }),
        actions: actAccess[r.ad_role_id] || {}   // {} => no explicit doc-action grants (gate closed unless allOrgs role)
      };
      insRule.run({
        rule_key: 'ACCESS:' + r.ad_role_id, event_type: 'Access', form: 'policy',
        binding: 'AD_Role:' + r.ad_role_id, body: JSON.stringify(body),
        params: JSON.stringify({ name: r.name, orgCount: allOrgs ? '*' : (orgAccess[r.ad_role_id] || []).length }),
        source: 'ad_role+ad_role_orgaccess+ad_document_action_access', oracle_ptr: null,
        gravity_rank: 0, status: 'preset'
      });
      n.access++;
    });
  });
  tx();

  ad.close();

  // ── self-verify + §-log ─────────────────────────────────────────────────
  var totalRules = rdb.prepare('SELECT count(*) c FROM rules').get().c;
  var stubs = rdb.prepare("SELECT count(*) c FROM rules WHERE status='stub'").get().c;
  var presets = rdb.prepare("SELECT count(*) c FROM rules WHERE status='preset'").get().c;
  var distinctHandlers = rdb.prepare('SELECT count(*) c FROM handler_backlog').get().c;
  var oracleFound = rdb.prepare('SELECT count(*) c FROM handler_backlog WHERE oracle_present=1').get().c;
  var extracted = n.sql + n.expr + n.policy + n.workflow;

  // round-trip spot check: AD_Rule body preserved into erp_rules.db.
  var sample = rdb.prepare("SELECT body FROM rules WHERE rule_key='AD_RULE:50001'").get();
  var bodyOk = sample && sample.body && sample.body.indexOf('Fact_Acct') >= 0;

  rdb.close();

  console.log('§RULES extracted=' + extracted + ' (sql=' + n.sql + ' expr=' + n.expr +
    ' policy=' + n.policy + ' workflow=' + n.workflow + ')' +
    ' callouts=' + n.callouts + ' docevents=' + n.docevents +
    ' matchpolicy=' + n.matchpolicy + ' access=' + n.access +
    ' handler-stubs=' + distinctHandlers + ' backlog=' + stubs);
  console.log('§RULES totalRecords=' + totalRules + ' presets=' + presets + ' stubs=' + stubs +
    ' distinctHandlers=' + distinctHandlers + ' oraclePresent=' + oracleFound + '/' + distinctHandlers);
  console.log('§RULES AD_Rule body preserved=' + (bodyOk ? 'OK' : 'FAIL') + ' out=' + OUT);
  if (!bodyOk) process.exit(1);
}

main();
