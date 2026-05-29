#!/usr/bin/env node
/**
 * poc_wire.js — TASK 1 acceptance: the compile→engine WIRE (docs/ERP.md §0.14-0.15,
 *   prompts/ERP_RUNTIME_ENGINE.md T1). Re-runs the Sales→Ship POC with EVERY engine opt
 *   sourced from DATA (rule records) — zero JS literals for ordering / fan-out / access.
 *
 * Proves:
 *   WIRE-1 SETTLEMENT — M_MatchInv/M_MatchPO STILL 18/18 with ordering ← MATCHPOLICY and
 *          partition narrowed by allowOrgs ← ACCESS (role 102 covers the data org).
 *   WIRE-2 DERIVATION — completeOrder fan-out flags ← DOCPOLICY:<docTypeId> (real Y/Y), no
 *          JS override; shipment ops == oracle lines, exact.
 *   WIRE-3 POLICY-EDIT — flip the MATCHPOLICY rule record FIFO→LIFO (an edit to DATA) and
 *          show the pairing changes deterministically on an ambiguous fixture.
 *   WIRE-4 ACCESS — a role whose org-access excludes the data org → the matcher partition
 *          is empty (access composes INTO the engine, §0.8); a role with no doc-action
 *          grant → mayRun=N.
 *
 * The engine (erp_engine.js) + host (erp_runtime.js) are unchanged; this harness only
 * SUPPLIES data instead of literals. Run: node scripts/poc_wire.js 2>&1 | tee build/erp/poc_wire.log
 */
'use strict';
var fs = require('fs');
var path = require('path');
var os = require('os');
var Database = require('better-sqlite3');
var E = require('./erp_engine');
var R = require('./erp_runtime');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var ad = new Database(AD, { readonly: true });
var er = new Database(RULES, { readonly: true });
var all = function (db, sql, p) { return db.prepare(sql).all(p || {}); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// the host query the engine + runtime are agnostic to (better-sqlite3 binding over rules db).
function ruleQuery(sql) { return er.prepare(sql).all(); }

function diffPairs(label, mine, oracle, extra) {
  var key = function (p) { return p[0] + ':' + p[1]; };
  var oset = {}; oracle.forEach(function (p) { oset[key(p)] = 1; });
  var matched = mine.filter(function (p) { return oset[key(p)]; }).length;
  var missed = oracle.filter(function (p) { var m = {}; mine.forEach(function (x) { m[key(x)] = 1; }); return !m[key(p)]; }).length;
  verdict(missed === 0 && matched === oracle.length && mine.length === oracle.length, label,
    'oracle=' + oracle.length + ' mine=' + mine.length + ' matched=' + matched + ' missed=' + missed + (extra || ''));
  return matched;
}

console.log('═══ POC-WIRE — compile→engine, EVERY opt from data (§0.14-0.15) ═══\n');

// ── load the settlement cell once: ordering + access come from rule records ──
var inoutCell = R.loadCell(ruleQuery, 'M_InOut', 'CO');
console.log('loadCell(M_InOut,CO): ' + inoutCell.trace + ' guards=' + inoutCell.guards.length + '\n');

// ── WIRE-1. SETTLEMENT 18/18, ordering ← MATCHPOLICY, partition ← ACCESS ─────
console.log('── WIRE-1. SETTLEMENT: zero JS opts (policy←erp_rules, orgs←ad_role) ──');
// data carries the org (m_inout / c_invoice header) so access can scope it.
var shipLines = all(ad, "SELECT iol.m_inoutline_id, iol.m_product_id, iol.movementqty, io.c_bpartner_id bp, io.movementtype, io.ad_org_id org FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id");
var invLines = all(ad, "SELECT il.c_invoiceline_id, il.m_product_id, il.qtyinvoiced, inv.c_bpartner_id bp, inv.issotrx, inv.ad_org_id org FROM c_invoiceline il JOIN c_invoice inv ON inv.c_invoice_id=il.c_invoice_id");
var ordLines = all(ad, "SELECT ol.c_orderline_id, ol.m_product_id, ol.qtyordered, o.c_bpartner_id bp, o.issotrx, o.ad_org_id org FROM c_orderline ol JOIN c_order o ON o.c_order_id=ol.c_order_id");
var vendorReceipts = shipLines.filter(function (r) { return r.movementtype === 'V+'; });
var vendorInvoices = invLines.filter(function (r) { return r.issotrx === 'N'; });
var poLines = ordLines.filter(function (r) { return r.issotrx === 'N'; });

var ROLE = 103; // GardenWorld User — explicit org list incl. org 11 (the data org)
var miStruct = { idL: 'm_inoutline_id', idR: 'c_invoiceline_id', qtyL: 'movementqty', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } };
var poStruct = { idL: 'c_orderline_id', idR: 'c_invoiceline_id', qtyL: 'qtyordered', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } };

var miWire = R.matchOptsFromCell(inoutCell, ROLE, miStruct);
var poWire = R.matchOptsFromCell(inoutCell, ROLE, poStruct);
var miPairs = E.match(vendorReceipts, vendorInvoices, miWire.opts);
var poPairs = E.match(poLines, vendorInvoices, poWire.opts);
var miOracle = all(ad, "SELECT m_inoutline_id, c_invoiceline_id FROM m_matchinv WHERE m_inoutline_id IS NOT NULL AND c_invoiceline_id IS NOT NULL").map(function (r) { return [r.m_inoutline_id, r.c_invoiceline_id]; });
var poOracle = all(ad, "SELECT c_orderline_id, c_invoiceline_id FROM m_matchpo WHERE c_orderline_id IS NOT NULL AND c_invoiceline_id IS NOT NULL").map(function (r) { return [r.c_orderline_id, r.c_invoiceline_id]; });
var miN = diffPairs('M_MatchInv reproduces oracle (opts from data)', miPairs, miOracle);
var poN = diffPairs('M_MatchPO reproduces oracle (opts from data)', poPairs, poOracle);
var orgN = miWire.access.allOrgs ? '*' : miWire.access.orgCount;
console.log('§WIRE cell=(M_InOut,CO) policy=' + inoutCell.ordering.order + '(src=' + inoutCell.orderingSrc + ')' +
  ' orgs=' + orgN + '(src=ad_role role=' + ROLE + ' ' + miWire.access.name + ')' +
  ' match=' + miN + '/' + miOracle.length + ' matchPO=' + poN + '/' + poOracle.length);

// ── WIRE-2. DERIVATION: fan-out flags ← DOCPOLICY (no JS override) ───────────
console.log('\n── WIRE-2. DERIVATION: completeOrder, flags ← DOCPOLICY:<docTypeId> ──');
var orderCell = R.loadCell(ruleQuery, 'C_Order', 'CO');
var soOrders = all(ad, "SELECT * FROM c_order WHERE issotrx='Y' AND c_order_id IN (SELECT c_order_id FROM m_inout)");
var derOk = 0;
soOrders.forEach(function (order) {
  var lines = all(ad, "SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=@oid", { oid: order.c_order_id });
  var policy = orderCell.getPolicy(order.c_doctype_id);   // <- from data, the real Y/Y flags
  var ops = E.completeOrder(order, lines, policy);
  var myShip = ops.filter(function (o) { return o.table === 'M_InOutLine'; }).map(function (o) { return o.m_product_id + ':' + o.movementqty; }).sort();
  var oracleShip = all(ad, "SELECT iol.m_product_id, iol.movementqty FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=@oid", { oid: order.c_order_id }).map(function (r) { return r.m_product_id + ':' + r.movementqty; }).sort();
  var ok = JSON.stringify(myShip) === JSON.stringify(oracleShip);
  if (ok) derOk++;
  verdict(ok, 'order ' + order.documentno + ' (docType ' + order.c_doctype_id + ' ino=' + policy.isautogenerateinout + ') ship ops==oracle', 'mine=' + myShip.length + ' oracle=' + oracleShip.length);
});
console.log('§WIRE derivation orders=' + soOrders.length + ' shipMatch=' + derOk + '/' + soOrders.length + ' (policy src=erp_rules DOCPOLICY, no JS override)');

// ── WIRE-3. POLICY-EDIT loop: flip MATCHPOLICY FIFO→LIFO (an edit to DATA) ───
console.log('\n── WIRE-3. POLICY-EDIT: flip MATCHPOLICY rule record, pairing changes ──');
(function () {
  // ambiguous fixture: one BPartner, one product, TWO candidate receipts (diff date), one
  // invoice. Real GardenWorld is unambiguous (18/18 either way), so we use a controlled
  // ambiguous case to PROVE the rule-record edit moves the pick. The ordering is NOT a JS
  // literal — it is read from the MATCHPOLICY:M_InOut:CO record via loadCell.
  var receipts = [{ id: 1, bp: 100, org: 11, m_product_id: 1, qty: 10, mdate: '2026-01-01' },
                  { id: 2, bp: 100, org: 11, m_product_id: 1, qty: 10, mdate: '2026-01-02' }];
  var invoices = [{ id: 10, bp: 100, org: 11, m_product_id: 1, qty: 10 }];
  var struct = { idL: 'id', idR: 'id', qtyL: 'qty', qtyR: 'qty', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; }, dateOf: function (r) { return r.mdate; } };

  function pickWith(rulesDb) {
    function rq(sql) { return rulesDb.prepare(sql).all(); }
    var cell = R.loadCell(rq, 'M_InOut', 'CO');
    var wire = R.matchOptsFromCell(cell, ROLE, struct);
    var pairs = E.match(invoices, receipts, wire.opts);   // invoice ⋈ receipts
    return { order: cell.ordering.order, src: cell.orderingSrc, receipt: pairs.length ? pairs[0][1] : null };
  }

  // FIFO from the shipped rule record (read-only er).
  var fifo = pickWith(er);
  // EDIT the rule record: copy erp_rules.db, UPDATE the MATCHPOLICY body FIFO->LIFO. In the
  // product this edit IS a kernel_ops op (§0.6); here we mutate a copy so the build artifact
  // stays pristine (gate determinism).
  var tmp = path.join(os.tmpdir(), 'erp_rules_wire_edit.db');
  fs.copyFileSync(RULES, tmp);
  var erW = new Database(tmp);
  erW.prepare("UPDATE rules SET body=json_set(body,'$.order','LIFO') WHERE rule_key='MATCHPOLICY:M_InOut:CO'").run();
  var lifo = pickWith(erW);
  erW.close(); fs.unlinkSync(tmp);

  var changed = (fifo.receipt !== lifo.receipt) ? 1 : 0;
  verdict(fifo.order === 'FIFO' && fifo.receipt === 1, 'FIFO record picks earliest receipt R1', 'order=' + fifo.order + ' src=' + fifo.src + ' chose=' + fifo.receipt);
  verdict(lifo.order === 'LIFO' && lifo.receipt === 2, 'edited record (LIFO) picks latest receipt R2', 'order=' + lifo.order + ' chose=' + lifo.receipt);
  verdict(changed === 1, 'rule-record edit deterministically changed the pairing', 'FIFO->R' + fifo.receipt + ' LIFO->R' + lifo.receipt);
  console.log('§WIRE policy-edit FIFO→LIFO pairsChanged=' + changed + ' (FIFO→R' + fifo.receipt + ' LIFO→R' + lifo.receipt + ', src=rule-record)');
})();

// ── WIRE-4. ACCESS: org-access narrows the partition; doc-action gates may-run ──
console.log('\n── WIRE-4. ACCESS: role scope composes INTO the engine (§0.8) ──');
(function () {
  // (a) a role scoped to org {0} cannot see the org-11 settlement data -> partition empty.
  var NOACCESS_ROLE = 0; // System Administrator — allowOrgs={0} in this seed; excludes org 11
  var acc0 = inoutCell.access(NOACCESS_ROLE);
  var wire0 = R.matchOptsFromCell(inoutCell, NOACCESS_ROLE, miStruct);
  var pairs0 = E.match(vendorReceipts, vendorInvoices, wire0.opts);
  verdict(pairs0.length === 0, 'access role=' + NOACCESS_ROLE + ' (orgs={' + (acc0.allowOrgs ? Array.from(acc0.allowOrgs).join(',') : '*') + '}) → partition empty → 0 matches', 'matched=' + pairs0.length + ' (data is org 11)');
  console.log('§WIRE access role=' + NOACCESS_ROLE + '(' + acc0.name + ') orgs-visible=0 match=' + pairs0.length + '/' + miOracle.length + ' (access narrows partition → empty)');

  // (b) a role with NO doc-action grant for the cell -> mayRun=N (the may-run gate, §0.8).
  var GATED_ROLE = 50004;  // Web Service Execution — actions={} in the compiled ACCESS record
  var gAcc = orderCell.access(GATED_ROLE);
  var mayGated = gAcc.mayRun(135, 'CO');     // POS Order DocType, Complete
  var mayOk = orderCell.access(ROLE).mayRun(135, 'CO');  // role 103 IS granted
  verdict(mayGated === false, 'role=' + GATED_ROLE + ' has no (C_Order POS,CO) grant → mayRun=N', 'mayRun=' + mayGated);
  verdict(mayOk === true, 'role=' + ROLE + ' IS granted (C_Order POS,CO) → mayRun=Y', 'mayRun=' + mayOk);
  console.log('§WIRE access role=' + GATED_ROLE + '(' + gAcc.name + ') mayRun(C_Order POS,CO)=N | role=' + ROLE + ' mayRun=Y');
})();

ad.close(); er.close();
console.log('\n═══ VERDICT ═══');
console.log('§WIRE ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — every engine opt sourced from data (ordering/fan-out/access), settlement 18/18'));
process.exit(fails ? 1 : 0);
