// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_genesis_minimal.js — W-GENESIS-MINIMAL (SYSTEM_ADMIN_LANE.md §5 Layer 1).
 *
 * ISSUE THIS PROVES: can a tenant BORN purely from an op-logged genesis (no migration import) CREATE -> COMPLETE
 *   -> POST one sales invoice whose GL == the iDempiere oracle to the cent? If yes, the Initial Client Setup is
 *   re-expressible as a signed op-log birth (git-for-a-tenant) with NO loss of accounting correctness.
 *
 * METHOD (NON-INVENT, §-log first):
 *   1. birthTenant({clientName, currency=USD}) -> ordered hash-chained CREATE-op groups G1..G6 (genesis.js).
 *   2. foldGenesis -> a fresh in-memory tenant DB (the birth).
 *   3. CREATE one sales invoice ($100 net + $9 tax = $109) on the born tenant's OWN default BP/product/tax.
 *   4. COMPLETE via the shipped erp_engine.completeInvoice (state op DR->CO; consume the seam, no fork).
 *   5. POST via the shipped doc_poster.derivePostings + post_resolver (the SAME verbs the live Posting-Preview
 *      uses) -> assert balanced + accounts == oracle (12110 / 41000 / 21610) + amounts to the cent.
 *   6. Verify the op-log chain integrity + the signed head tip.
 *
 * ORACLE: idempiere_test default CoA — DR Accounts Receivable-Trade 12110 / CR Trade Revenue 41000 /
 *   CR Tax due 21610 (extracted live this session).
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var G = require('../build/erp/genesis.js');
var ENG = require('../build/erp/erp_engine.js');
var R = require('./post_resolver');
var DP = require('./doc_poster');

var pass = 0, fail = 0;
function check(ok, msg, got) { (ok ? pass++ : fail++); console.log((ok ? '  ✓ ' : '  ✗ FAIL ') + msg + (got !== undefined ? '  → ' + got : '')); }

console.log('═══ W-GENESIS-MINIMAL — a tenant BORN from an op-log posts a sales invoice == oracle to the cent ═══');
console.log('    genesis.birthTenant -> foldGenesis -> CREATE -> completeInvoice -> doc_poster.derivePostings\n');

// ── 1+2. BIRTH ───────────────────────────────────────────────────────────────────────────────────────────────
var born = G.birthTenant({ clientName: 'AcmeCo', currencyId: 100, currencyPrecision: 2, adminUser: 'jdoe', dateAcct: '2024-01-15' });
var db = new Database(':memory:');
var nOps = G.foldGenesis(born.groups, db);
console.log('§GENESIS born: ' + born.groups.length + ' groups, ' + nOps + ' CREATE ops, head=' + born.tip.slice(0, 12) + '…');
born.groups.forEach(function (g) { console.log('  · ' + g.label + ' — ' + g.ops.length + ' ops'); });

check(born.groups.length === 6, 'G1..G6 all present', born.groups.length + ' groups');
var chartCount = db.prepare("SELECT COUNT(*) n FROM c_elementvalue").get().n;
check(chartCount === 311, 'G3 folded the full default chart of accounts', chartCount + ' accounts');
var asId = born.refs.acctSchemaId;

// ── op-log chain integrity (recompute every tip) ──────────────────────────────────────────────────────────────
var crypto = require('crypto');
var parent = '0'.repeat(64), chainOk = true;
born.groups.forEach(function (g) {
  var tip = crypto.createHash('sha256').update(parent + JSON.stringify(g.ops)).digest('hex');
  if (tip !== g.tip || g.parent !== parent) chainOk = false;
  parent = tip;
});
check(chainOk && parent === born.tip, 'op-log hash-chain verifies (git-for-a-tenant)', born.tip.slice(0, 12) + '…');

// ── default-account wiring landed on the oracle values ──────────────────────────────────────────────────────
function evValueFor(col) {
  var vc = born.refs.vc[col];
  var r = db.prepare('SELECT ev.value v, ev.name n, ev.accounttype t FROM c_validcombination vc JOIN c_elementvalue ev ON ev.c_elementvalue_id=vc.account_id WHERE vc.c_validcombination_id=?').get(vc);
  return r;
}
var rcv = evValueFor('c_receivable_acct'), rev = evValueFor('p_revenue_acct'), tax = evValueFor('t_due_acct');
check(rcv && rcv.v === '12110', 'c_receivable_acct wired to oracle 12110 (' + (rcv && rcv.n) + ')', rcv && rcv.v);
check(rev && rev.v === '41000', 'p_revenue_acct wired to oracle 41000 (' + (rev && rev.n) + ')', rev && rev.v);
check(tax && tax.v === '21610', 't_due_acct wired to oracle 21610 (' + (tax && tax.n) + ')', tax && tax.v);

// ── 3. CREATE one sales invoice on the born tenant (the document tables = the CRUD session's verb; seeded here) ─
db.prepare('CREATE TABLE c_invoice (c_invoice_id, c_bpartner_id, grandtotal, issotrx, ispaid, dateinvoiced, docstatus)').run();
db.prepare('CREATE TABLE c_invoiceline (c_invoice_id, m_product_id, linenetamt)').run();
db.prepare('CREATE TABLE c_invoicetax (c_invoice_id, c_tax_id, taxamt)').run();
var invId = 9000001;
db.prepare("INSERT INTO c_invoice VALUES (?,?,?,?,?,?,?)").run(invId, born.refs.bpartnerId, 109.00, 'Y', 'N', '2024-01-15', 'DR');
db.prepare('INSERT INTO c_invoiceline VALUES (?,?,?)').run(invId, born.refs.productId, 100.00);
db.prepare('INSERT INTO c_invoicetax VALUES (?,?,?)').run(invId, born.refs.taxId, 9.00);
console.log('\n§CREATE invoice ' + invId + ': BP=' + born.refs.bpartnerId + ' net=100.00 tax=9.00 grandtotal=109.00');

// ── 4. COMPLETE (shipped verb) ────────────────────────────────────────────────────────────────────────────────
var ops = ENG.completeInvoice({ c_invoice_id: invId, issotrx: 'Y' }, [], {});
var stateOp = ops[0];
check(stateOp && stateOp.op_type === 'SET_STATUS' && stateOp.doc_status === 'CO', 'completeInvoice -> signed state op DR→CO', stateOp && stateOp.doc_status);
db.prepare('UPDATE c_invoice SET docstatus=? WHERE c_invoice_id=?').run('CO', invId);

// ── 5. POST (shipped verb) -> diff to oracle ────────────────────────────────────────────────────────────────
var gl = DP.derivePostings(db, { table: 'C_Invoice', id: invId }, asId, R);
console.log('\n§POST derivePostings (basis=' + gl.basis + ', balanced=' + gl.balanced + ', absent=' + JSON.stringify(gl.absent) + '):');
gl.lines.slice().sort(function (a, b) { return a.value < b.value ? -1 : 1; }).forEach(function (l) {
  console.log('   ' + l.value + ' ' + (l.name || '').padEnd(28) + ' DR ' + l.amtacctdr.toFixed(2).padStart(9) + '  CR ' + l.amtacctcr.toFixed(2).padStart(9));
});
function line(v) { return gl.lines.filter(function (l) { return l.value === v; })[0]; }
check(gl.basis === 'invoice', 'posted off the invoice (oracle basis)', gl.basis);
check(gl.absent.length === 0, 'no absent accounts — every token resolved from genesis rows', JSON.stringify(gl.absent));
check(gl.balanced && gl.sumDr === gl.sumCr, 'journal balances', 'Dr=' + (gl.sumDr / 100).toFixed(2) + ' Cr=' + (gl.sumCr / 100).toFixed(2));
var L12110 = line('12110'), L41000 = line('41000'), L21610 = line('21610');
check(L12110 && L12110.amtacctdr === 109.00 && L12110.amtacctcr === 0, 'DR 12110 Receivable = 109.00 (== oracle)', L12110 && L12110.amtacctdr);
check(L41000 && L41000.amtacctcr === 100.00 && L41000.amtacctdr === 0, 'CR 41000 Revenue = 100.00 (== oracle)', L41000 && L41000.amtacctcr);
check(L21610 && L21610.amtacctcr === 9.00 && L21610.amtacctdr === 0, 'CR 21610 Tax due = 9.00 (== oracle)', L21610 && L21610.amtacctcr);

// ── 6. signed head ─────────────────────────────────────────────────────────────────────────────────────────────
G.signHead(born.groups).then(async function (sig) {
  check(sig && sig.tip === born.tip && /^[0-9a-f]{128}$/.test(sig.sig), 'genesis head tip ECDSA-signed (signed bundle)', sig.sig.slice(0, 16) + '…');
  var verified = await sig.verify(sig.tip, sig.sig);
  check(verified === true, 'signature verifies against the controller key', verified);
  var tamperedTip = sig.tip.slice(0, -1) + (sig.tip.slice(-1) === '0' ? '1' : '0');   // guaranteed-different tip
  var tamperedVerified = await sig.verify(tamperedTip, sig.sig);
  check(tamperedVerified === false, 'a tampered tip is REJECTED (provenance holds)', tamperedVerified);
  console.log('\n═══ W-GENESIS-MINIMAL: ' + pass + ' PASS / ' + fail + ' FAIL ═══');
  console.log(fail === 0
    ? '✅ A tenant born from a signed op-log posts a sales invoice to the cent == iDempiere oracle. Initial Client Setup IS expressible as git-for-a-tenant — no accounting loss. Layer-1 minimal genesis WITNESSED.'
    : '❌ gaps above.');
  process.exit(fail === 0 ? 0 : 1);
}).catch(function (e) { console.error('signHead error', e); process.exit(1); });
