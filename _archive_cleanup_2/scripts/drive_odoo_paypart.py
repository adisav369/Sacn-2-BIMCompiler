#!/usr/bin/env python3
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
"""
drive_odoo_paypart.py — Stage 2 (f2 partial PAYMENT): drive a fresh Odoo O2C chain to a
PARTIAL-payment state, then FREEZE the executed rows as a static oracle.
  Spec: prompts/ODOO_FOLD_POC.md (drive-then-freeze) · prompts/MIGRATION_CAMPAIGN_RESUME.md Stage 2 ·
        docs/ERP.md §0.12 (static oracle, no live system at replay).

NON-INVENT / CLEAN-ROOM: every id / amount / residual written to the oracle is READ BACK from Odoo's
EXECUTED rows (Odoo computes the residual; we extract it — we never compute or synthesise it). This
script drives the LIVE odoodemo over RPC; the frozen JSON it emits has NO live dependency at replay.

Scenario: reuse S00023's two lines (products 31, 21, qty 10 → invoice total 5002.50), drive
SO → deliver → invoice → post → register a PARTIAL payment of 3000.00. Odoo then leaves
amount_residual = 2002.50, payment_state = 'partial'. That residual is the witness the fold reproduces.

Run:  python3 scripts/drive_odoo_paypart.py 2>&1 | tee -a build/erp/odoo_fold.log
"""
import json, os, sys, urllib.request

URL = 'http://localhost:8069'; DB = 'odoodemo'; USER = 'admin'; PW = 'admin'
PARTNER = 11
LINES = [(31, 10), (21, 10)]      # (product_id, qty) — IDENTICAL to S00023 (non-invent reuse)
PARTIAL = 3000.0                  # the partial payment amount we register
PAY_DATE = '2026-06-03'
OUT = os.path.join(os.path.dirname(__file__), '..', 'build', 'erp', 'odoo_oracle_paypart.json')

def log(*a): print(*a, flush=True)

# JSON-RPC transport (handles null returns from action wizards — XML-RPC's allow_none=False does not).
def jsonrpc(service, method, args):
    body = json.dumps({'jsonrpc': '2.0', 'method': 'call',
                       'params': {'service': service, 'method': method, 'args': args}}).encode()
    req = urllib.request.Request(URL + '/jsonrpc', body, {'Content-Type': 'application/json'})
    resp = json.loads(urllib.request.urlopen(req, timeout=120).read())
    if 'error' in resp:
        raise RuntimeError(json.dumps(resp['error'].get('data', resp['error']))[:600])
    return resp['result']

uid = jsonrpc('common', 'authenticate', [DB, USER, PW, {}])
if not uid:
    log('§DRIVE-PAYPART FAIL auth'); sys.exit(1)
def call(model, method, args, kw=None):
    return jsonrpc('object', 'execute_kw', [DB, uid, PW, model, method, args, kw or {}])
log('§DRIVE-PAYPART auth uid=%s db=%s' % (uid, DB))

# ── 1) create + confirm the SO ──────────────────────────────────────────────
so_id = call('sale.order', 'create',
             [{'partner_id': PARTNER,
               'order_line': [(0, 0, {'product_id': p, 'product_uom_qty': q}) for p, q in LINES]}])
so = call('sale.order', 'read', [[so_id], ['name', 'amount_total', 'amount_untaxed', 'amount_tax']])[0]
so_name = so['name']
log('§DRIVE-PAYPART SO id=%s name=%s untaxed=%s tax=%s total=%s'
    % (so_id, so_name, so['amount_untaxed'], so['amount_tax'], so['amount_total']))
call('sale.order', 'action_confirm', [[so_id]])

# ── 2) deliver: set move quantities = ordered, then validate the picking ─────
so = call('sale.order', 'read', [[so_id], ['picking_ids']])[0]
for pid in so['picking_ids']:
    moves = call('stock.move', 'search_read', [[('picking_id', '=', pid)], ['id', 'product_uom_qty']])
    for m in moves:
        call('stock.move', 'write', [[m['id']], {'quantity': m['product_uom_qty']}])
    res = call('stock.picking', 'button_validate', [[pid]])
    if isinstance(res, dict) and res.get('res_model'):                 # a wizard came back
        ctx = res.get('context', {})
        wiz = call(res['res_model'], 'create', [{}], {'context': ctx})
        meth = 'process' if res['res_model'] != 'stock.backorder.confirmation' else 'process'
        call(res['res_model'], meth, [[wiz]], {'context': ctx})
    st = call('stock.picking', 'read', [[pid], ['name', 'state']])[0]
    log('§DRIVE-PAYPART picking=%s state=%s' % (st['name'], st['state']))

# ── 3) invoice via the standard wizard, then post ───────────────────────────
ctx = {'active_model': 'sale.order', 'active_ids': [so_id], 'active_id': so_id}
wiz = call('sale.advance.payment.inv', 'create', [{'advance_payment_method': 'delivered'}], {'context': ctx})
call('sale.advance.payment.inv', 'create_invoices', [[wiz]], {'context': ctx})
inv_id = call('sale.order', 'read', [[so_id], ['invoice_ids']])[0]['invoice_ids'][0]
call('account.move', 'action_post', [[inv_id]])
inv = call('account.move', 'read', [[inv_id],
           ['name', 'state', 'move_type', 'amount_untaxed', 'amount_tax', 'amount_total',
            'amount_residual', 'payment_state', 'invoice_origin']])[0]
log('§DRIVE-PAYPART invoice=%s state=%s total=%s residual(before pay)=%s pstate=%s'
    % (inv['name'], inv['state'], inv['amount_total'], inv['amount_residual'], inv['payment_state']))

# ── 4) register a PARTIAL payment of PARTIAL (Odoo computes the residual) ────
pctx = {'active_model': 'account.move', 'active_ids': [inv_id]}
reg = call('account.payment.register', 'create',
           [{'amount': PARTIAL, 'payment_date': PAY_DATE, 'payment_difference_handling': 'open'}],
           {'context': pctx})
act = call('account.payment.register', 'action_create_payments', [[reg]], {'context': pctx})
# read back the EXECUTED state — these are the oracle's witness values
inv = call('account.move', 'read', [[inv_id],
           ['name', 'amount_total', 'amount_residual', 'payment_state']])[0]
pay_ids = call('account.payment', 'search', [[('partner_id', '=', PARTNER)]], {'order': 'id desc', 'limit': 1})
pay = call('account.payment', 'read', [[pay_ids[0]], ['name', 'amount', 'state', 'payment_type']])[0]
log('§DRIVE-PAYPART payment=%s amount=%s state=%s | invoice residual(after)=%s pstate=%s'
    % (pay['name'], pay['amount'], pay['state'], inv['amount_residual'], inv['payment_state']))

# ── 5) read the invoice GL lines (same shape as odoo_oracle.json) ───────────
gl_rows = call('account.move.line', 'search_read',
               [[('move_id', '=', inv_id)],
                ['name', 'debit', 'credit', 'balance', 'display_type', 'tax_line_id', 'product_id', 'quantity', 'price_subtotal', 'account_id']],
               {'order': 'id'})
def acct_name(r):
    a = r.get('account_id'); return a[1] if a else None
invoice_gl_lines = [{
    'name': r['name'], 'account': acct_name(r), 'debit': r['debit'], 'credit': r['credit'],
    'balance': r['balance'], 'product': (r['product_id'][1] if r['product_id'] else None),
    'qty': r['quantity'], 'subtotal': r['price_subtotal'],
    'is_tax': bool(r['tax_line_id']), 'display_type': r['display_type']
} for r in gl_rows]

so_lines = call('sale.order.line', 'search_read',
                [[('order_id', '=', so_id)],
                 ['product_id', 'product_uom_qty', 'qty_delivered', 'qty_invoiced', 'price_unit', 'price_subtotal', 'price_total']],
                {'order': 'id'})
sale_order_lines = [{
    'product': l['product_id'][1], 'pid': l['product_id'][0], 'qty': l['product_uom_qty'],
    'qty_delivered': l['qty_delivered'], 'qty_invoiced': l['qty_invoiced'],
    'price_unit': l['price_unit'], 'subtotal': l['price_subtotal'], 'total': l['price_total']
} for l in so_lines]

oracle = {
    'meta': {
        'scenario': 'sell-side O2C with PARTIAL payment (f2): pay %s of invoice total, residual carried' % PARTIAL,
        'so': so_name, 'so_id': so_id,
        'invoice': inv['name'], 'invoice_id': inv_id,
        'partner_id': PARTNER,
        'payment': pay['name'], 'payment_amount': pay['amount'],
        'reconcile_amount': pay['amount'],          # the PARTIAL allocate amount
        'amount_total': inv['amount_total'],
        'amount_residual': inv['amount_residual'],  # Odoo's computed residual — the witness
        'payment_state': inv['payment_state'],      # expect 'partial'
        'source': 'odoodemo (Odoo 17), driven via RPC to a PARTIAL-payment state, STATIC export — no live Odoo at replay (ERP.md §0.12)'
    },
    'sale_order_lines': sale_order_lines,
    'invoice_gl_lines': invoice_gl_lines
}
with open(OUT, 'w') as f:
    json.dump(oracle, f, indent=2)
log('§DRIVE-PAYPART oracle frozen -> %s  (total=%s allocated=%s residual=%s pstate=%s)'
    % (os.path.relpath(OUT), inv['amount_total'], pay['amount'], inv['amount_residual'], inv['payment_state']))
