#!/usr/bin/env node
/* test_integ_postings_reconcile.js — §INTEG-POSTINGS-RECONCILE + §INTEG-SIGNER-REUSE
 *
 * Implementing prompts/ERP_SUBSTRATE_INTEGRATION.md Phase 1 — Witness: W-POSTINGS-RECONCILE.
 *
 * Issue this PROVES/DISPROVES: the frozen period-close contract (erp_period_close.foldBalances)
 * was witnessed only on the POC's SYNTHETIC posting shape `{account, cents}`. The LIVE app does
 * NOT emit that — it emits double-entry `POST` ops whose payload is
 *   parameters = { table, id, lines:[{account_id, amtacctdr, amtacctcr, ...}], acctschema }
 * (the real shape per ~/bim-ootb/erp/erp_kernel.js applyOne §13.1, ΣDR==ΣCR to the cent).
 * The closing balance of an account carried forward = Σ(amtacctdr) − Σ(amtacctcr) in cents.
 *
 * Falsifier: fold the REAL ops → closing balances must be (a) NON-EMPTY and EQUAL to the
 * hand-computed expected balances, and (b) fold[checkpoint + post-ckpt] == fold[full genesis]
 * to maxDiffCents===0. A fold that silently ignores POST ops gives empty balances → FAILS (a).
 *
 * §INTEG-SIGNER-REUSE: the checkpoint tip is signed via the APP signer (erp/erp_signer.js
 * makeSigner/sign/verify) — NOT erp_snapshot_sign's pinned DEMO controller key. The signature
 * verifies under the app signer's own public key.
 *
 * Read the log (exit code is not evidence). Run: node scripts/test_integ_postings_reconcile.js
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

var KERNEL = path.join(process.env.HOME, 'bim-compiler', 'build', 'erp', 'kernel_ops.js'); // reconciled canonical
var SIGNER = path.join(process.env.HOME, 'bim-ootb', 'erp', 'erp_signer.js');               // the REAL app signer
var PC     = require(path.join(__dirname, '..', 'build', 'erp', 'erp_period_close.js'));

function loadGlobalModule(p) { global.window = global.window || {}; delete require.cache[require.resolve(p)]; require(p); }
function freshKernel() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }

// faithful mirror of erp_postings.js cents() and the expected net-per-account balance b/f.
function cents(n) { return Math.round(Number(n || 0) * 100); }

var fail = 0;
function check(name, cond, detail) {
  console.log((cond ? '   🟢 ' : '   🔴 ') + name + (detail ? ' — ' + detail : ''));
  if (!cond) fail++;
}

// ── the REAL op stream the app emits (POST = double-entry; doc ops carry no balance) ────────────
// account_id keys mirror C_ValidCombination ids (TEXT in the journal); we fold by that key.
function postOp(table, id, lines) { return { table: table, id: id, lines: lines }; }
function bal(dr, cr) { return { amtacctdr: dr, amtacctcr: cr }; }

// Period 1 postings: a sales invoice (AR/Revenue) + its payment (Cash/AR). Plus a non-posting doc op.
var P1 = [
  { type: 'CREATE_DOCUMENT', params: { table: 'C_Invoice', source_id: 5001, doc_status: 'CO' } }, // no lines → skipped
  { type: 'POST', params: postOp('C_Invoice', 109, [
      { account_id: '101', role: 'AR',      amtacctdr: 434.13, amtacctcr: 0 },
      { account_id: '400', role: 'Revenue', amtacctdr: 0,      amtacctcr: 434.13 } ]) },
  { type: 'POST', params: postOp('C_Payment', 77, [
      { account_id: '108', role: 'Cash', amtacctdr: 434.13, amtacctcr: 0 },
      { account_id: '101', role: 'AR',   amtacctdr: 0,      amtacctcr: 434.13 } ]) },
  { type: 'POST', params: postOp('C_Invoice', 110, [
      { account_id: '101', role: 'AR',      amtacctdr: 1200.50, amtacctcr: 0 },
      { account_id: '400', role: 'Revenue', amtacctdr: 0,       amtacctcr: 1200.50 } ]) },
];
// Period 2 postings (after the checkpoint): collect invoice 110.
var P2 = [
  { type: 'POST', params: postOp('C_Payment', 78, [
      { account_id: '108', role: 'Cash', amtacctdr: 1200.50, amtacctcr: 0 },
      { account_id: '101', role: 'AR',   amtacctdr: 0,       amtacctcr: 1200.50 } ]) },
];

// hand-computed EXPECTED net-per-account balances (the operator's real Fact_Acct, in cents).
function expectedFromGenesis() {
  var b = {};
  P1.concat(P2).forEach(function (o) {
    if (o.type !== 'POST') return;
    o.params.lines.forEach(function (l) { b[l.account_id] = (b[l.account_id] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); });
  });
  return b;
}

(async function () {
  console.log('═══ §INTEG-POSTINGS-RECONCILE — real double-entry POST ops fold under closePeriod ═══');
  var SQL = await initSqlJs();

  // app signer: reuse erp/erp_signer.js (NOT the demo pinned key). Mint a fresh keypair (loadOrMint's
  // IndexedDB custody is browser-only — verified at Phase 2 wiring; makeSigner IS the app's sign path).
  loadGlobalModule(SIGNER);
  var ErpSigner = global.window.ErpSigner;
  check('app signer module loaded (erp/erp_signer.js)', !!(ErpSigner && ErpSigner.makeSigner && ErpSigner.loadOrMint),
        'makeSigner=' + (ErpSigner && typeof ErpSigner.makeSigner) + ' loadOrMint=' + (ErpSigner && typeof ErpSigner.loadOrMint));
  var kp = await ErpSigner.mintKeypair();
  var appSigner = ErpSigner.makeSigner(kp);
  // adapter onto closePeriod's signer contract {signTip, signed_by} — tip is a hex hash, app sign() takes hex.
  var signer = { signTip: function (tipHex) { return appSigner.sign(tipHex); }, signed_by: 'edge:app-signer' };

  function commitStream(K, db, ops) { ops.forEach(function (o) { K.commitOp(db, o.type, o.params); }); }

  // ── A. close period 1 on REAL POST ops → checkpoint carries the closing balances ────────────────
  var K = freshKernel();
  var db = new SQL.Database();
  commitStream(K, db, P1);
  var res = await PC.closePeriod(db, K, signer, { period: 1, ts: 9000 });
  var closing = res.closing_balances;
  var expectedP1 = (function () { var b = {}; P1.forEach(function (o) { if (o.type !== 'POST') return;
      o.params.lines.forEach(function (l) { b[l.account_id] = (b[l.account_id] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); }); return b; })();

  console.log('§INTEG-POSTINGS closingBalances=' + JSON.stringify(closing) + ' expectedP1=' + JSON.stringify(expectedP1));
  check('A1 closing balances are NON-EMPTY (POST ops were folded, not ignored)', Object.keys(closing).length > 0,
        'keys=' + Object.keys(closing).length);
  check('A2 closing balances == hand-computed net-per-account (DR−CR cents)', PC.balEqual(closing, expectedP1).equal,
        'maxDiff=' + PC.balEqual(closing, expectedP1).maxDiffCents + 'c');
  check('A3 double-entry: ΣbalSum == 0 (balanced)', PC.balSum(closing) === 0, 'balSum=' + PC.balSum(closing));
  check('A4 checkpoint signed by APP signer + verifies under its OWN pubkey', !!res.sig && (await appSigner.verify(res.tip, res.sig)),
        'signed_by=' + res.signed_by + ' sig=' + (res.sig ? res.sig.slice(0, 12) + '…' : 'none'));

  // ── B. THE FALSIFIER — fold[checkpoint + P2] == fold[full genesis] to the cent ──────────────────
  commitStream(K, db, P2);                                   // post-checkpoint activity
  var bf = PC.bootstrapFromCheckpoint(db, K);                // opening = ckpt balances, fold P2 forward
  var fromCkpt = bf.balances || bf.bal || bf.current || bf;  // tolerate return-shape

  // independent full-genesis fold (fresh kernel, NO close)
  var Kg = freshKernel(); var dbg = new SQL.Database();
  commitStream(Kg, dbg, P1.concat(P2));
  var genesis = PC.foldBalances(Kg.replayOps(dbg), null).bal;
  var expectAll = expectedFromGenesis();

  console.log('§INTEG-RECONCILE fromCheckpoint=' + JSON.stringify(fromCkpt) + ' genesis=' + JSON.stringify(genesis));
  var cmp = PC.balEqual(fromCkpt, genesis);
  check('B1 genesis fold == hand-computed expected', PC.balEqual(genesis, expectAll).equal, 'maxDiff=' + PC.balEqual(genesis, expectAll).maxDiffCents + 'c');
  check('B2 FALSIFIER: fold[checkpoint + P2] == fold[full genesis]', cmp.equal, 'maxDiffCents=' + cmp.maxDiffCents);
  check('B3 AR fully settled (account 101 nets to 0)', (genesis['101'] | 0) === 0, 'AR=' + (genesis['101'] | 0) + 'c');

  console.log((fail === 0 ? '\n═══ 🟢 ALL PASS — real POST ops fold = signed checkpoint = balance b/f, reconciled to 0c ═══'
                          : '\n═══ 🔴 ' + fail + ' CHECK(S) FAILED ═══'));
  process.exit(fail === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e && e.stack || e); process.exit(1); });
