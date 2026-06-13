#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>. SPDX-License-Identifier: MIT
/**
 * ⚠ DO NOT REMOVE — poc_p4_buyside_live.js (P4 live buy-side fold witness, W-P4-BUYSIDE-LIVE)
 * Scope: upgrades poc_odoo_fold_3way.js (static oracle) to a LIVE connection — proves Odoo's
 *   P00011 procure-to-pay chain (PO → receipt → vendor bill → GL post → 3-way match) pulled LIVE
 *   from odoodemo still folds through the EXISTING 6 kernel verbs with newVerbs=[].
 * Claims:
 *   §EXTRACT     — 1 PO / 1 receipt (WH/IN/00006) / 1 bill (BILL/2026/06/0002) pulled from live Odoo
 *   §LIVE-STATIC — live totals == static oracle odoo_oracle_p2p.json (export is faithful to running instance)
 *   §BUY-FOLD    — all 4 P2P events (confirm PO / receive / vendor bill / post) committed via dispatch
 *   §MATCH       — 3-way match via erp_engine.match; receipt↔bill + PO↔bill pairs == live qty (no invented rows)
 *   §GL-BALANCE  — AP double-entry balanced (ΣDR==ΣCR) + total == live bill.amount_total (maxDiff=0c)
 *   §NEW-VERBS   — newVerbs=[] (Odoo procure-to-pay folds with the existing 6 verbs)
 *   §FALSIFIER   — corrupt partner to 'WRONG' → match produces 0 pairs (partner is the settlement partition key)
 * Log Mandate: READ build/erp/poc_p4_buyside_live.log before any conclusion.
 * Run: bash build/erp/run_witness.sh scripts/poc_p4_buyside_live.js
 */
'use strict';
var path = require('path'), fs = require('fs'), http = require('http');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var E = require('./erp_engine');
var A = require('./odoo_adapter');

var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle_p2p.json'), 'utf8'));
var HOST = 'localhost', PORT = 8069, DB = 'odoodemo', LOGIN = 'admin', PASSWORD = 'admin';

function rpc(s, m, a) {
  return new Promise(function (res, rej) {
    var b = JSON.stringify({ jsonrpc: '2.0', method: 'call', params: { service: s, method: m, args: a } });
    var req = http.request({ host: HOST, port: PORT, path: '/jsonrpc', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) } }, function (x) {
      var d = ''; x.on('data', function (c) { d += c; }); x.on('end', function () {
        try { var j = JSON.parse(d); j.error ? rej(new Error(JSON.stringify(j.error.data && j.error.data.message || j.error))) : res(j.result); } catch (e) { rej(e); } }); });
    req.on('error', rej); req.write(b); req.end();
  });
}
function n2(x) { return Number(x || 0).toFixed(2); }
function cents(x) { return Math.round(Number(x || 0) * 100); }

(async function () {
  var fails = 0;
  function ok(c, m, d) { if (!c) fails++; console.log('  ' + (c ? '🟢' : '🔴') + ' ' + m + (d ? ' — ' + d : '')); }
  var log = []; function L(m) { console.log(m); log.push(m); }
  L('\n══ POC-P4-BUYSIDE-LIVE — fold Odoo P00011 from LIVE odoodemo (W-P4-BUYSIDE-LIVE) ══\n');

  // ── 1. authenticate ──
  var uid = await rpc('common', 'login', [DB, LOGIN, PASSWORD]);
  ok(!!uid, '§EXTRACT authenticated to live odoodemo', 'uid=' + uid);
  if (!uid) { L('§P4-BUYSIDE-LIVE FAIL auth'); process.exit(1); }
  var ex = function (m, me, a, k) { return rpc('object', 'execute_kw', [DB, uid, PASSWORD, m, me, a, k || {}]); };

  // ── 2. pull P00011 chain live ──
  var po = (await ex('purchase.order', 'search_read', [[['name', '=', 'P00011']]],
    { fields: ['id', 'name', 'state', 'partner_id', 'amount_total', 'amount_untaxed', 'invoice_ids'] }))[0];
  ok(!!po, '§EXTRACT PO P00011 found', po && 'id=' + po.id + ' state=' + po.state + ' partner=' + po.partner_id[1]);

  var pol = await ex('purchase.order.line', 'search_read', [[['order_id', '=', po.id]]],
    { fields: ['product_id', 'product_qty', 'qty_received', 'qty_invoiced', 'price_unit', 'price_subtotal'] });
  ok(pol.length > 0, '§EXTRACT PO lines found', pol.length + ' lines');

  var picks = await ex('stock.picking', 'search_read', [[['purchase_id', '=', po.id]]],
    { fields: ['id', 'name', 'state'] });
  var donePick = picks.filter(function (p) { return p.state === 'done'; })[0];
  ok(!!donePick, '§EXTRACT receipt (stock.picking state=done) found', donePick && donePick.name);

  var moves = await ex('stock.move', 'search_read', [[['picking_id', '=', donePick.id]]],
    { fields: ['product_id', 'quantity', 'state'] });
  ok(moves.length > 0, '§EXTRACT receipt moves found', moves.length + ' moves');

  ok(po.invoice_ids && po.invoice_ids.length > 0, '§EXTRACT vendor bill linked to PO', 'invoice_ids=' + (po.invoice_ids && po.invoice_ids.join(',')));
  var bill = (await ex('account.move', 'read', [po.invoice_ids],
    { fields: ['id', 'name', 'state', 'move_type', 'amount_total', 'amount_untaxed', 'amount_tax', 'payment_state'] }))[0];
  ok(!!bill && bill.state === 'posted', '§EXTRACT vendor bill posted', bill && bill.name + ' ' + bill.payment_state);

  var aml = await ex('account.move.line', 'search_read', [[['move_id', '=', bill.id]]],
    { fields: ['account_id', 'debit', 'credit', 'display_type', 'product_id', 'tax_line_id'] });
  ok(aml.length > 0, '§EXTRACT bill GL lines found', aml.length + ' lines');

  // ── 3. assemble oracle-shaped object from LIVE rows ──
  var live = {
    meta: { po: po.name, po_id: po.id, partner: po.partner_id[1], partner_id: po.partner_id[0],
            receipt: picks.filter(function (p) { return p.state === 'done'; }).map(function (p) { return p.name; }),
            bill: bill.name, bill_id: bill.id, source: 'live odoodemo (Odoo 17, :8069)' },
    po_lines: pol.map(function (l) { return { product: l.product_id[1], pid: l.product_id[0], ordered: l.product_qty, received: l.qty_received, invoiced: l.qty_invoiced, subtotal: l.price_subtotal }; }),
    receipt_moves: moves.map(function (m) { return { product: m.product_id[1], pid: m.product_id[0], qty: m.quantity }; }),
    bill: { name: bill.name, state: bill.state, amount_total: bill.amount_total, amount_untaxed: bill.amount_untaxed, amount_tax: bill.amount_tax, payment_state: bill.payment_state },
    bill_gl_lines: aml.filter(function (l) { return l.display_type !== 'line_section' && l.display_type !== 'line_note'; }).map(function (l) {
      return { account: l.account_id[1], debit: l.debit, credit: l.credit, is_tax: !!l.tax_line_id, display_type: l.display_type || 'product' };
    })
  };
  L('\n── live extract: PO=' + live.meta.po + ' lines=' + live.po_lines.length + ' receipt=' + live.meta.receipt.join(',') + ' moves=' + live.receipt_moves.length + ' bill=' + live.meta.bill + ' GL=' + live.bill_gl_lines.length + ' total=' + n2(live.bill.amount_total));

  // ── 4. §LIVE-STATIC — live == static oracle (export is faithful) ──
  L('\n── §LIVE-STATIC: live vs odoo_oracle_p2p.json ──');
  ok(n2(live.bill.amount_total) === n2(ORACLE.bill.amount_total), '§LIVE-STATIC bill total == static oracle', n2(live.bill.amount_total) + '==' + n2(ORACLE.bill.amount_total));
  ok(live.po_lines.length === ORACLE.po_lines.length, '§LIVE-STATIC PO line count == static oracle', live.po_lines.length + '==' + ORACLE.po_lines.length);
  ok(live.receipt_moves.length === ORACLE.receipt_moves.length, '§LIVE-STATIC receipt moves == static oracle', live.receipt_moves.length + '==' + ORACLE.receipt_moves.length);
  ok(live.bill_gl_lines.length === ORACLE.bill_gl_lines.length, '§LIVE-STATIC GL lines == static oracle', live.bill_gl_lines.length + '==' + ORACLE.bill_gl_lines.length);
  ok(live.meta.po === ORACLE.meta.po && live.meta.bill === ORACLE.meta.bill, '§LIVE-STATIC PO+bill names preserved', live.meta.po + '/' + live.meta.bill);

  // ── 5. §BUY-FOLD — fold through buildBuyEvents + kernel ──
  L('\n── §BUY-FOLD: fold live chain through adapter + kernel verbs ──');
  var built = A.buildBuyEvents(live);
  var SQL = await initSqlJs();
  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };
  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:p4-live', baseTs: 3000 + i * 100 }, ev.d);
    ok(d.ok, '§BUY-FOLD event ' + (i + 1) + ' (' + ev.name + ') committed', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });

  // ── 6. §MATCH — 3-way match via the EXISTING erp_engine.match ──
  L('\n── §MATCH: 3-way settlement (receipt↔bill + PO↔bill) via erp_engine.match ──');
  var realMatch = E.match, matcherCalls = 0;
  E.match = function () { matcherCalls++; return realMatch.apply(E, arguments); };
  var S = built.matchSets;
  var mOpts = { idL: 'id', idR: 'id', qtyL: 'qty', qtyR: 'qty', keyOf: function (r) { return r.pid; }, partition: function (r) { return r.bp; } };
  var miPairs = E.match(S.receiptLines, S.billLines, mOpts);
  var poPairs = E.match(S.poLines,      S.billLines, mOpts);
  var matchOps = [];
  miPairs.forEach(function (p) { matchOps.push({ op_type: 'MATCH', table: 'M_MatchInv', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'inv' }); });
  poPairs.forEach(function (p) { matchOps.push({ op_type: 'MATCH', table: 'M_MatchPO', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'po' }); });
  matchOps.forEach(function (o) { usedVerbs[o.op_type] = 1; });
  K.register('C_MatchPO', 'CO', function () { return matchOps; });
  var dM = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:p4-live', baseTs: 3500 }, { docType: 'C_MatchPO', action: 'CO', status: 'DR' });
  ok(dM.ok, '§MATCH event 5 (3-way match) committed', dM.ok ? 'ops=' + dM.applied : dM.stage + ':' + dM.reason);
  if (dM.ok) mappedHops++;
  ok(matcherCalls > 0, '§MATCH erp_engine.match genuinely invoked (not stubbed)', 'calls=' + matcherCalls);
  var nLines = live.po_lines.length;
  ok(miPairs.length === nLines && poPairs.length === nLines, '§MATCH pairs == PO lines (full 3-way)', 'receipt↔bill=' + miPairs.length + ' PO↔bill=' + poPairs.length + ' lines=' + nLines);
  var qtyAligned = live.po_lines.every(function (l) { return l.ordered === l.received && l.received === l.invoiced; });
  ok(qtyAligned, '§MATCH qty ordered==received==invoiced (full-qty 3-way)', live.po_lines.map(function (l) { return l.pid + ':o=' + l.ordered + '/r=' + l.received + '/i=' + l.invoiced; }).join(', '));
  E.match = realMatch;

  // ── 7. §GL-BALANCE — AP double-entry balanced + total == live bill (maxDiff=0c) ──
  L('\n── §GL-BALANCE: AP GL balanced + total == live bill.amount_total ──');
  var GLW = " WHERE source='DOC:C_Invoice#" + live.meta.bill_id + "' AND account_id IS NOT NULL";
  var mDR = K.query(db, 'SELECT COALESCE(SUM(amtacctdr),0) s FROM journal' + GLW)[0].s;
  var mCR = K.query(db, 'SELECT COALESCE(SUM(amtacctcr),0) s FROM journal' + GLW)[0].s;
  ok(cents(mDR) === cents(mCR), '§GL-BALANCE AP balanced (ΣDR==ΣCR)', 'ΣDR=' + n2(mDR) + ' ΣCR=' + n2(mCR));
  ok(cents(mDR) === cents(live.bill.amount_total), '§GL-BALANCE total == live bill.amount_total (maxDiff=0c)', 'ΣDR=' + n2(mDR) + ' bill=' + n2(live.bill.amount_total));
  // account-level diff vs live AML (the exact oracle)
  var oracleGL = {}; live.bill_gl_lines.forEach(function (l) { var k = l.account; if (!oracleGL[k]) oracleGL[k] = { dr: 0, cr: 0 }; oracleGL[k].dr += cents(l.debit); oracleGL[k].cr += cents(l.credit); });
  var derivedGL = {}; K.query(db, 'SELECT account_id a, COALESCE(SUM(amtacctdr),0) dr, COALESCE(SUM(amtacctcr),0) cr FROM journal' + GLW + ' GROUP BY account_id').forEach(function (r) { derivedGL[r.a] = { dr: cents(r.dr), cr: cents(r.cr) }; });
  var maxDiff = 0;
  Object.keys(oracleGL).forEach(function (k) { var d = derivedGL[k] || { dr: 0, cr: 0 }; maxDiff = Math.max(maxDiff, Math.abs(d.dr - oracleGL[k].dr), Math.abs(d.cr - oracleGL[k].cr)); });
  Object.keys(derivedGL).forEach(function (k) { if (!oracleGL[k]) maxDiff = Math.max(maxDiff, derivedGL[k].dr + derivedGL[k].cr); });
  ok(maxDiff === 0, '§GL-BALANCE account-level maxDiff=0c vs live AML', 'maxDiff=' + maxDiff + 'c accounts=' + Object.keys(oracleGL).length);

  // ── 8. §NEW-VERBS — Odoo buy-side folds with the existing 6 verbs ──
  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });
  ok(newVerbs.length === 0, '§NEW-VERBS newVerbs=[] — Odoo P2P folds with existing 6 verbs', 'used=[' + used.join(',') + ']');
  ok(mappedHops === 5, '§BUY-FOLD all 5 hops migrated (4 events + match)', mappedHops + '/5');

  // ── 9. §FALSIFIER — corrupt partner → match produces 0 pairs ──
  L('\n── §FALSIFIER: corrupt partner → match produces 0 pairs ──');
  var fakeSet = { poLines: [{ id: 'POL0', pid: S.poLines[0] && S.poLines[0].pid, qty: 20, bp: 'WRONG' }],
                  receiptLines: [{ id: 'RCV0', pid: S.receiptLines[0] && S.receiptLines[0].pid, qty: 20, bp: 'WRONG-PARTNER' }],
                  billLines: [{ id: 'BILL0', pid: S.billLines[0] && S.billLines[0].pid, qty: 20, bp: live.meta.partner_id }] };
  var fakeMI = E.match(fakeSet.receiptLines, fakeSet.billLines, mOpts);
  ok(fakeMI.length === 0, '§FALSIFIER corrupt partner → receipt↔bill produces 0 pairs (settlement boundary enforced)', 'fakeMI.length=' + fakeMI.length);

  db.close();
  L('\n§P4-BUYSIDE-LIVE chain mapped=' + mappedHops + '/5 verbs=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + '] matcherCalls=' + matcherCalls + ' maxDiff=' + maxDiff + 'c');
  var pass = fails === 0;
  L('§P4-BUYSIDE-LIVE ' + (pass ? 'PASS — Odoo P00011 procure-to-pay LIVE fold: newVerbs=[], all 5 hops, AP balanced, maxDiff=0c vs live AML, 3-way match via erp_engine.match, §FALSIFIER fires'
    : 'FAIL (' + fails + ' checks red)'));
  try { fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_p4_buyside_live.log'), log.join('\n')); } catch (e) {}
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§P4-BUYSIDE-LIVE ERROR', e.message, e.stack); process.exit(2); });
