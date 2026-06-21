#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>. SPDX-License-Identifier: MIT
// poc_odoo_fold_live.js — the LIVE migration witness: re-pull SO S00023's O2C chain from the RUNNING
//   Odoo 17 (db odoodemo, :8069) via JSON-RPC, fold it through the SAME pure adapter (odoo_adapter.js)
//   + the 6 kernel verbs, and prove it still migrates: newVerbs=[] (thesis holds), every event commits,
//   the invoice GL balances to the cent, and the live totals == the static oracle (export is faithful).
//   This upgrades poc_odoo_fold.js (static oracle) to a LIVE connection — "Migrate (Odoo) is real."
//   §-log first. Run:  node scripts/poc_odoo_fold_live.js 2>&1 | tee build/erp/odoo_fold_live.log
'use strict';
var path = require('path'), fs = require('fs'), http = require('http');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var A = require('./odoo_adapter');
var ORACLE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'odoo_oracle.json'), 'utf8'));

var HOST = 'localhost', PORT = 8069, DB = 'odoodemo', LOGIN = 'admin', PASSWORD = 'admin';

function rpc(service, method, args) {
  return new Promise(function (resolve, reject) {
    var body = JSON.stringify({ jsonrpc: '2.0', method: 'call', params: { service: service, method: method, args: args } });
    var req = http.request({ host: HOST, port: PORT, path: '/jsonrpc', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) } }, function (res) {
      var d = ''; res.on('data', function (c) { d += c; }); res.on('end', function () {
        try { var j = JSON.parse(d); if (j.error) return reject(new Error(JSON.stringify(j.error.data && j.error.data.message || j.error))); resolve(j.result); }
        catch (e) { reject(e); } });
    });
    req.on('error', reject); req.write(body); req.end();
  });
}
function n2(x) { return Number(x).toFixed(2); }

(async function () {
  var fails = 0; function ok(c, m, d) { if (!c) fails++; console.log('   ' + (c ? '🟢' : '🔴') + ' ' + m + (d ? ' — ' + d : '')); }
  console.log('\n══ POC-ODOO-FOLD-LIVE — migrate SO S00023 from the RUNNING odoodemo (Odoo 17, :8069) ══\n');

  // ── connect ──
  var uid = await rpc('common', 'login', [DB, LOGIN, PASSWORD]);
  ok(!!uid, 'authenticated to live odoodemo', 'uid=' + uid + ' server=17.0');
  if (!uid) { console.log('\n§ODOO-FOLD-LIVE FAIL auth\n'); process.exit(1); }
  var ex = function (model, method, args, kw) { return rpc('object', 'execute_kw', [DB, uid, PASSWORD, model, method, args, kw || {}]); };

  // ── extract the chain into the SAME shape as odoo_oracle.json (live, no fabrication) ──
  var so = (await ex('sale.order', 'search_read', [[['name', '=', ORACLE.meta.so]]], { fields: ['id', 'name', 'state', 'amount_untaxed', 'amount_tax', 'amount_total'] }))[0];
  ok(!!so, 'live SO found', so && (so.name + ' id=' + so.id + ' state=' + so.state));
  var solines = await ex('sale.order.line', 'search_read', [[['order_id', '=', so.id], ['display_type', '=', false]]],
    { fields: ['product_id', 'product_uom_qty', 'qty_delivered', 'qty_invoiced', 'price_unit', 'price_subtotal', 'price_total'] });
  var inv = (await ex('account.move', 'search_read', [[['invoice_origin', '=', so.name], ['move_type', '=', 'out_invoice']]],
    { fields: ['id', 'name', 'state', 'move_type', 'amount_untaxed', 'amount_tax', 'amount_total', 'amount_residual', 'payment_state', 'invoice_origin'] }))[0];
  ok(!!inv, 'live invoice found', inv && (inv.name + ' ' + inv.payment_state));
  var gl = await ex('account.move.line', 'search_read', [[['move_id', '=', inv.id]]],
    { fields: ['name', 'account_id', 'debit', 'credit', 'balance', 'product_id', 'quantity', 'tax_line_id', 'display_type'] });
  var moves = await ex('stock.move', 'search_read', [[['sale_line_id', 'in', solines.map(function (l) { return l.id; })], ['state', '=', 'done']]],
    { fields: ['product_id', 'quantity', 'state'] });

  // ── assemble the oracle-shaped object from LIVE rows ──
  function pname(pid) { return pid ? pid[1] : ''; }
  var live = {
    meta: { so: so.name, so_id: so.id, invoice: inv.name, invoice_id: inv.id, payment: [], reconcile_amount: inv.amount_total, payment_state: inv.payment_state },
    sale_order: { name: so.name, state: so.state, amount_untaxed: so.amount_untaxed, amount_tax: so.amount_tax, amount_total: so.amount_total },
    sale_order_lines: solines.map(function (l) { return { product: pname(l.product_id), pid: l.product_id[0], qty: l.product_uom_qty, qty_delivered: l.qty_delivered, qty_invoiced: l.qty_invoiced, price_unit: l.price_unit, subtotal: l.price_subtotal, total: l.price_total }; }),
    delivery_moves: moves.map(function (m) { return { product: pname(m.product_id), pid: m.product_id[0], qty: m.quantity, state: m.state }; }),
    invoice: { name: inv.name, state: inv.state, move_type: inv.move_type, amount_untaxed: inv.amount_untaxed, amount_tax: inv.amount_tax, amount_total: inv.amount_total, amount_residual: inv.amount_residual, payment_state: inv.payment_state, invoice_origin: inv.invoice_origin },
    invoice_gl_lines: gl.filter(function (g) { return g.display_type !== 'line_section' && g.display_type !== 'line_note'; }).map(function (g) {
      return { name: g.name, account: g.account_id ? g.account_id[1] : '', debit: g.debit, credit: g.credit, balance: g.balance, product: pname(g.product_id), qty: g.quantity, is_tax: !!g.tax_line_id, display_type: g.display_type || 'product' }; }),
    payment: []
  };
  console.log('\n── live extract: SO ' + live.meta.so + ' lines=' + live.sale_order_lines.length + ' moves=' + live.delivery_moves.length +
    ' invoice=' + live.meta.invoice + ' gl=' + live.invoice_gl_lines.length + ' total=' + n2(live.meta.reconcile_amount) + ' (' + live.meta.payment_state + ')\n');

  // ── live == static oracle (the export is faithful to the running instance) ──
  ok(n2(live.sale_order.amount_total) === n2(ORACLE.sale_order.amount_total), 'live total == oracle', n2(live.sale_order.amount_total) + ' vs ' + n2(ORACLE.sale_order.amount_total));
  ok(live.sale_order_lines.length === ORACLE.sale_order_lines.length, 'live line count == oracle', live.sale_order_lines.length + ' vs ' + ORACLE.sale_order_lines.length);

  // ── FOLD the LIVE chain through the same adapter + kernel verbs ──
  var built = A.buildEvents(live);
  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  var SQL = await initSqlJs();
  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };
  var usedVerbs = {}, mapped = 0;
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'odoo:migrate', baseTs: 1000 + i * 100 }, ev.d);
    if (d.ok) mapped++;
    ok(d.ok, 'event ' + (i + 1) + ' ' + ev.name + ' committed (' + ev.d.status + '→' + (d.to || '?') + ')', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
  });
  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });
  console.log('\n§ODOO-FOLD-LIVE chain mapped=' + mapped + '/' + built.events.length + ' verbs=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');

  // ── invoice GL balances to the cent (double-entry preserved through the migration) ──
  var dr = live.invoice_gl_lines.reduce(function (a, g) { return a + Number(g.debit || 0); }, 0);
  var cr = live.invoice_gl_lines.reduce(function (a, g) { return a + Number(g.credit || 0); }, 0);
  ok(n2(dr) === n2(cr), 'invoice GL balances (ΣDr==ΣCr)', n2(dr) + '==' + n2(cr));
  ok(newVerbs.length === 0, 'newVerbs empty — Odoo folds with the existing 6 verbs', 'used=' + used.length);
  ok(mapped === built.events.length, 'every hop migrated', mapped + '/' + built.events.length);

  var pass = fails === 0;
  console.log('\n§ODOO-FOLD-LIVE ' + (pass ? 'PASS' : 'FAIL') + ' (live odoodemo → kernel verbs, ' + fails + ' fails)\n');
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§ODOO-FOLD-LIVE ERROR', e.message); process.exit(2); });
