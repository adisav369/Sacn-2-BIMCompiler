#!/usr/bin/env python3
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
"""
drive_odoo_acctcfg.py — Stage 2 (f1 account DERIVATION): extract Odoo's account-DETERMINATION CONFIG
(not the resolved invoice rows) so the fold can DERIVE the GL accounts and diff vs Odoo's actual posting.
  Spec: prompts/MIGRATION_CAMPAIGN_RESUME.md Stage 2 (f1) · docs/ERP.md §13.1 (POST owns ΣDR==ΣCR; the
        resolver is host glue) · prompts/ODOO_FOLD_POC.md (clean-room: learn from CONFIG, not source).

f1 was the standing honest bound: the sell-side fold took Odoo's RESOLVED accounts (400000/251000/121000)
as host data. This raises the claim — extract the determination CONFIG and DERIVE those accounts:
  • product line → income:  product.template.property_account_income_id, else product.category
                            .property_account_income_categ_id  (standard Odoo determination)
  • tax line    → tax acct: account.tax.invoice_repartition_line_ids where repartition_type='tax' → account_id
  • AR line     → receivable: res.partner.property_account_receivable_id
The 'posted_truth' block records the accounts Odoo ACTUALLY used (the diff target). NON-INVENT: every code
is read from the live odoodemo config; the derivation LOGIC is Odoo's documented model, learned clean-room.

Run:  python3 scripts/drive_odoo_acctcfg.py 2>&1 | tee -a build/erp/odoo_fold.log
"""
import json, os, urllib.request

URL = 'http://localhost:8069'; DB = 'odoodemo'; USER = 'admin'; PW = 'admin'
PAYPART = os.path.join(os.path.dirname(__file__), '..', 'build', 'erp', 'odoo_oracle_paypart.json')
OUT = os.path.join(os.path.dirname(__file__), '..', 'build', 'erp', 'odoo_oracle_acctderiv.json')

def log(*a): print(*a, flush=True)
def rpc(s, m, a):
    body = json.dumps({'jsonrpc': '2.0', 'method': 'call', 'params': {'service': s, 'method': m, 'args': a}}).encode()
    r = urllib.request.Request(URL + '/jsonrpc', body, {'Content-Type': 'application/json'})
    return json.loads(urllib.request.urlopen(r, timeout=120).read())['result']
uid = rpc('common', 'authenticate', [DB, USER, PW, {}])
def call(model, method, args, kw=None): return rpc('object', 'execute_kw', [DB, uid, PW, model, method, args, kw or {}])
def acct_name(v): return v[1] if v else None     # (id, 'CODE Name') -> 'CODE Name'

base = json.load(open(PAYPART))
inv_id = base['meta']['invoice_id']; partner_id = base['meta']['partner_id']
pids = [l['pid'] for l in base['sale_order_lines']]
log('§DRIVE-ACCTCFG invoice_id=%s partner=%s products=%s' % (inv_id, partner_id, pids))

# ── (1) per-product income determination: template, then category fallback ──
product_income = {}
for pid in pids:
    pp = call('product.product', 'read', [[pid], ['product_tmpl_id', 'categ_id']])[0]
    tmpl = call('product.template', 'read', [[pp['product_tmpl_id'][0]], ['property_account_income_id']])[0]
    cat = call('product.category', 'read', [[pp['categ_id'][0]], ['property_account_income_categ_id']])[0]
    product_income[pid] = {
        'tmpl_income': acct_name(tmpl['property_account_income_id']),          # None here → fall back
        'categ_income': acct_name(cat['property_account_income_categ_id'])     # 400000 Product Sales
    }
    log('§DRIVE-ACCTCFG product=%s tmpl_income=%s categ_income=%s'
        % (pid, product_income[pid]['tmpl_income'], product_income[pid]['categ_income']))

# ── (2) tax account: the sale 15% tax's repartition line of type 'tax' ──
tax = call('account.tax', 'search_read', [[('amount', '=', 15.0), ('type_tax_use', '=', 'sale')],
                                          ['name', 'amount', 'invoice_repartition_line_ids']])[0]
reps = call('account.tax.repartition.line', 'read', [tax['invoice_repartition_line_ids'], ['repartition_type', 'account_id']])
tax_account = next((acct_name(r['account_id']) for r in reps if r['repartition_type'] == 'tax'), None)
log('§DRIVE-ACCTCFG tax=%s amount=%s tax_account=%s' % (tax['name'], tax['amount'], tax_account))

# ── (3) receivable account: partner property ──
receivable = acct_name(call('res.partner', 'read', [[partner_id], ['property_account_receivable_id']])[0]['property_account_receivable_id'])
log('§DRIVE-ACCTCFG partner_receivable=%s' % receivable)

# ── posted truth (the diff target): the accounts Odoo ACTUALLY used, by role ──
posted = {'income': None, 'tax': None, 'receivable': None}
for r in base['invoice_gl_lines']:
    if r['display_type'] == 'product': posted['income'] = r['account']
    elif r['display_type'] == 'tax': posted['tax'] = r['account']
    elif r['display_type'] == 'payment_term': posted['receivable'] = r['account']
log('§DRIVE-ACCTCFG posted_truth income=%s tax=%s receivable=%s' % (posted['income'], posted['tax'], posted['receivable']))

oracle = {
    'meta': {
        'scenario': 'f1 account DETERMINATION: derive GL accounts from extracted Odoo config, diff vs posted',
        'invoice': base['meta']['invoice'], 'invoice_id': inv_id, 'partner_id': partner_id,
        'amount_untaxed': sum(l['subtotal'] for l in base['sale_order_lines']),
        'amount_tax': round(sum(l['subtotal'] for l in base['sale_order_lines']) * tax['amount'] / 100.0, 2),
        'amount_total': base['meta']['amount_total'],
        'tax_rate': tax['amount'],
        'source': 'odoodemo (Odoo 17) account-determination CONFIG, STATIC export — no live Odoo at replay (§0.12)'
    },
    'sale_order_lines': base['sale_order_lines'],
    'account_config': {                       # the determination INPUTS — the resolver reads ONLY these
        'product_income': {str(k): v for k, v in product_income.items()},
        'tax_account': tax_account, 'tax_rate': tax['amount'],
        'partner_receivable': receivable
    },
    'posted_truth': posted                    # the diff TARGET — what Odoo actually posted
}
json.dump(oracle, open(OUT, 'w'), indent=2)
log('§DRIVE-ACCTCFG oracle frozen -> %s' % os.path.relpath(OUT))
