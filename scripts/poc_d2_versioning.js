#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_d2_versioning.js — witness for the D2 REMEDY (schema_version + read-time upcaster registry).
 *   Module: build/erp/op_upcaster.js  ·  Spike that motivated it: scripts/spike_d2_versioning.js
 *   Closes the 3 🔴 D2 test-plan rows in docs/ProductionRisks.md, on the REAL kernel + REAL signer.
 *
 * Issue it PROVES (each names what it proves/disproves):
 *   W-D2-FREEZE   — old-manifest (v1) ops upcast→current fold to their ORIGINAL effect (history frozen, not
 *                   reinterpreted).  §FALSIFIER: any maxDiff>0 = a reinterpretation of history = FAIL.
 *   W-D2-CONVERGE — two clients holding a MIXED v1+v2 log fold to byte-identical balances (offline migration
 *                   doesn't diverge).  §FALSIFIER: maxDiff>0 between clients = divergence = FAIL.
 *   W-D2-REFUSE   — an op from a FUTURE/unknown schema is REFUSED LOUDLY (throws D2_UNSUPPORTED), never silently
 *                   misapplied.  §FALSIFIER: a future-version op that folds silently = FAIL.
 *   W-D2-SIGNED   — upcast is read-only: after folding upcasted ops the REAL signed chain still verifies (you
 *                   never rewrote a stored op).  §FALSIFIER: verifyChain not ok after upcast = FAIL.
 *   W-D2-TAMPER   — _sv lives INSIDE params (hashed), so an op's declared version is a SIGNED fact: forging it
 *                   on a stored op breaks the chain.  §FALSIFIER: version forgeable without breaking chain = FAIL.
 *
 * Run: node scripts/poc_d2_versioning.js   (READ THE LOG — §MON-style §-lines are the proof)
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = global.window || {}; global.self = global.self || global;
var path = require('path');
var initSqlJs = require('sql.js');
var ROOT = path.join(__dirname, '..');
var KERNEL = path.join(ROOT, 'build', 'erp', 'kernel_ops.js');
var PC = require(path.join(ROOT, 'build', 'erp', 'erp_period_close.js'));
var UP = require(path.join(ROOT, 'build', 'erp', 'op_upcaster.js'));
var SIGNER = path.join(process.env.HOME, 'bim-ootb', 'erp', 'erp_signer.js');

function loadGlobal(p) { global.window = global.window || {}; delete require.cache[require.resolve(p)]; require(p); }
function freshKernel() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }
function cents(x) { return Math.round((x || 0) * 100); }
function getP(o) { var p = o.parameters; return (typeof p === 'string') ? JSON.parse(p) : p; }

// the CURRENT (v2) reducer — reads the v2 shape (l.acct). After upcast, every op presents this shape.
function foldV2(ops) {
  var bal = {};
  ops.forEach(function (o) { var p = getP(o); if (!p.lines) return;
    p.lines.forEach(function (l) { var a = String(l.acct); bal[a] = (bal[a] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); });
  return bal;
}
// the ORIGINAL v1 effect (reads l.account_id) — the frozen truth we must reproduce
function foldV1Original(ops) {
  var bal = {};
  ops.forEach(function (o) { var p = getP(o); if (!p.lines) return;
    p.lines.forEach(function (l) { var a = String(l.account_id); bal[a] = (bal[a] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); });
  return bal;
}
function maxDiff(x, y) { var k = {}, m = 0, a; for (a in x) k[a] = 1; for (a in y) k[a] = 1; for (a in k) m = Math.max(m, Math.abs((x[a] || 0) - (y[a] || 0))); return m; }

var FAILS = [];
function check(n, c, d) { console.log((c ? '   ✓ ' : '   ✗ ') + n + (d ? ' — ' + d : '')); if (!c) FAILS.push(n); }

(async function () {
  console.log('═══ §D2 — schema_version + upcaster registry (the forced remedy) ═══');
  var SQL = await initSqlJs();
  loadGlobal(SIGNER);
  var ErpSigner = global.window.ErpSigner;
  var kp = await ErpSigner.mintKeypair();
  var appSigner = ErpSigner.makeSigner(kp);
  var sgn = { sign: function (h) { return appSigner.sign(h); }, verify: function (h, s) { return appSigner.verify(h, s); }, signed_by: 'edge:app' };

  // ── register the v1→v2 upcaster: POST renamed line.account_id → line.acct ─────────────────────────
  UP.reset();
  UP.register('POST', 1, function (p) {                       // v1 → v2
    if (Array.isArray(p.lines)) p.lines = p.lines.map(function (l) {
      return { acct: l.account_id, amtacctdr: l.amtacctdr, amtacctcr: l.amtacctcr }; });
    return p;
  });
  console.log('§D2-SETUP current POST schema version = ' + UP.currentOf('POST') + ' (v1→v2 upcaster registered)');

  // ════════ W-D2-FREEZE — legacy v1 ops fold to their ORIGINAL effect after upcast ════════
  console.log('\n── W-D2-FREEZE: old-manifest ops replay to original effect ──');
  var K = freshKernel(); K.setSigner(sgn); var db = new SQL.Database();
  // LEGACY v1 ops: written the old way (no _sv stamp at all → versionOf=1), via the raw kernel
  for (var i = 0; i < 15; i++) K.commitOp(db, 'POST', { table: 'C_Invoice', id: 100 + i,
    lines: [{ account_id: '101', amtacctdr: 10 + i, amtacctcr: 0 }, { account_id: '400', amtacctdr: 0, amtacctcr: 10 + i }] });
  await K.sealChain(db);
  var legacy = K.replayOps(db, null, '*');
  var original = foldV1Original(legacy);                      // the frozen v1 truth
  var upcasted = UP.upcastAll(legacy);                        // upcast-on-read → v2 shape
  var afterUpcast = foldV2(upcasted);                         // fold with the CURRENT reducer
  var d1 = maxDiff(original, afterUpcast);
  console.log('§D2-FREEZE legacyOps=' + legacy.length + ' originalSum=' + JSON.stringify(original) + ' maxDiff=' + d1 + 'c');
  check('W-D2-FREEZE upcasted v1 ops == original v1 effect (history frozen)', d1 === 0, 'maxDiff=' + d1 + 'c');

  // ════════ W-D2-SIGNED — upcast is read-only → the signed chain still verifies ════════
  var v = await K.verifyChain(db);
  check('W-D2-SIGNED chain still ok after folding upcasted ops (stored op untouched)', v.ok === true, 'ok=' + v.ok);

  // ════════ W-D2-CONVERGE — two clients on a MIXED v1+v2 log converge ════════
  console.log('\n── W-D2-CONVERGE: mixed v1+v2 log folds identically on two clients ──');
  // client A: legacy v1 ops + NEW v2 ops (stamped via commitVersioned). client B: same stream, independent kernel.
  function buildMixed() {
    var Kx = freshKernel(); Kx.setSigner(sgn); var dx = new SQL.Database();
    for (var a = 0; a < 8; a++) Kx.commitOp(dx, 'POST', { table: 'C_Invoice', id: 300 + a,
      lines: [{ account_id: '101', amtacctdr: 5, amtacctcr: 0 }, { account_id: '400', amtacctdr: 0, amtacctcr: 5 }] });   // v1 legacy
    for (var b = 0; b < 8; b++) UP.commitVersioned(Kx, dx, 'POST', { table: 'C_Invoice', id: 400 + b,
      lines: [{ acct: '101', amtacctdr: 7, amtacctcr: 0 }, { acct: '400', amtacctdr: 0, amtacctcr: 7 }] });               // v2 native (stamped _sv=2)
    return Kx.replayOps(dx, null, '*');
  }
  var balA = foldV2(UP.upcastAll(buildMixed()));
  var balB = foldV2(UP.upcastAll(buildMixed()));
  var dconv = maxDiff(balA, balB);
  console.log('§D2-CONVERGE clientA=' + JSON.stringify(balA) + ' clientB=' + JSON.stringify(balB) + ' maxDiff=' + dconv + 'c');
  check('W-D2-CONVERGE two clients on mixed v1+v2 log converge (no divergence)', dconv === 0, 'maxDiff=' + dconv + 'c');
  check('W-D2-CONVERGE balances are non-trivial (the test actually folded value)', Object.keys(balA).length > 0 && balA['101'] !== 0);

  // ════════ W-D2-REFUSE — a FUTURE-version op is refused LOUDLY, never silently misapplied ════════
  console.log('\n── W-D2-REFUSE: an op from an unknown future schema is refused, not misapplied ──');
  var futureOp = { op_type: 'POST', parameters: JSON.stringify({ table: 'C_Invoice', id: 999, _sv: 99,
    lines: [{ acct: '101', amtacctdr: 1, amtacctcr: 0 }] }) };
  check('W-D2-REFUSE supports()=false for a future-version op', UP.supports(futureOp) === false);
  var threw = false, code = null;
  try { UP.upcast(futureOp); } catch (e) { threw = true; code = e.code; }
  check('W-D2-REFUSE upcast throws LOUDLY (D2_UNSUPPORTED), no silent fold', threw && code === 'D2_UNSUPPORTED', 'threw=' + threw + ' code=' + code);

  // ════════ W-D2-TAMPER — _sv is a SIGNED fact: forging an op's version on disk breaks the chain ════════
  console.log('\n── W-D2-TAMPER: declared version is signed (forging it breaks the chain) ──');
  var Kt = freshKernel(); Kt.setSigner(sgn); var dt = new SQL.Database();
  for (var t = 0; t < 4; t++) UP.commitVersioned(Kt, dt, 'POST', { table: 'C_Invoice', id: 500 + t,
    lines: [{ acct: '101', amtacctdr: 3, amtacctcr: 0 }] });
  await Kt.sealChain(dt);
  var okBefore = (await Kt.verifyChain(dt)).ok;
  // forge: flip a stored op's declared _sv 2 → 1 (a lie about which schema it was authored under)
  var row = dt.exec('SELECT id, parameters FROM kernel_ops WHERE op_type=\'POST\' ORDER BY id LIMIT 1');
  var pid = row[0].values[0][0], pp = JSON.parse(row[0].values[0][1]); pp._sv = 1;
  dt.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [JSON.stringify(pp), pid]);
  var vAfter = await Kt.verifyChain(dt);
  check('W-D2-TAMPER forging a stored op\'s _sv breaks the signed chain', okBefore && vAfter.ok === false, 'before=' + okBefore + ' after=' + vAfter.ok);

  // ════════ W-D2-DEFAULT-SEAM — after install(), the DEFAULT commitOp path auto-stamps _sv (no opt-in) ════════
  console.log('\n── W-D2-DEFAULT-SEAM: install() makes plain commitOp auto-stamp ──');
  UP.setCurrent('POST', 2);                                  // declare current POST schema = v2
  var Kd = freshKernel(); Kd.setSigner(sgn); var dd = new SQL.Database();
  var installed = UP.install(Kd);                            // ONE line — the default write seam
  Kd.commitOp(dd, 'POST', { table: 'C_Invoice', id: 600, lines: [{ acct: '101', amtacctdr: 1, amtacctcr: 0 }] });
  var stampedRow = getP(Kd.replayOps(dd, null, '*')[0]);     // replayOps pre-parses params
  check('W-D2-DEFAULT-SEAM install() wired the stamper', installed === true);
  check('W-D2-DEFAULT-SEAM plain commitOp auto-stamped _sv=2 (no commitVersioned needed)', stampedRow._sv === 2, '_sv=' + stampedRow._sv);
  await Kd.sealChain(dd);
  check('W-D2-DEFAULT-SEAM auto-stamped op still seals + verifies', (await Kd.verifyChain(dd)).ok === true);

  console.log('\n§D2-WITNESS OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
  process.exit(FAILS.length === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL ' + e.stack); process.exit(2); });
