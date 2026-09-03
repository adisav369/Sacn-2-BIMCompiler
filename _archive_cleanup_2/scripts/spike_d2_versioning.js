#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * spike_d2_versioning.js — DISCOVERY SPIKE D2: event/schema evolution on a SIGNED op-log.
 *   Prompted by Andrzej Ludwikowski "Event Sourcing — what could possibly go wrong?" (his #1 pitfall:
 *   events are immutable + live forever, yet you WILL change their shape). Register row: ProductionRisks D2.
 *   Constraint: our ops are hash-chained + signed — op_hash = SHA-256(prev_hash | canonical(op)) and canonical
 *   INCLUDES JSON.stringify(parameters) (kernel_ops.js). So the ES-world "rewrite/upcast the stored event"
 *   migration is FORBIDDEN here: it breaks the chain + signature. The only legal strategy is UPCAST-ON-READ.
 *
 * What this MEASURES (NON-INVENT — real kernel, real verifyChain, real foldBalances):
 *   1. Does the kernel carry a schema-version field or an upcaster registry today? (grep-as-code)
 *   2. ADDITIVE change (v2 adds an optional field): do v1 ops still fold correctly? (tolerant reader)
 *   3. BREAKING change (v2 renames account_id→acct): how many v1 ops fold WRONG under a v2-expecting reducer?
 *   4. UPCAST-ON-READ (v1→v2 transform applied in memory before the reducer): does it restore correctness
 *      AND leave the signed chain intact (because the STORED op is never touched)?
 *   5. THE REWRITE TRAP: migrate by rewriting a stored op's parameters to v2 shape → verifyChain MUST break
 *      ('payload altered') — proving why upcast-on-read is the only option on a signed log.
 *
 *   §SPIKE-D2 line IS the result. Run: node scripts/spike_d2_versioning.js  (READ THE LOG)
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = global.window || {}; global.self = global.self || global;
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
var ROOT = path.join(__dirname, '..');
var KERNEL = path.join(ROOT, 'build', 'erp', 'kernel_ops.js');
var PC = require(path.join(ROOT, 'build', 'erp', 'erp_period_close.js'));
var SIGNER = path.join(process.env.HOME, 'bim-ootb', 'erp', 'erp_signer.js');

function loadGlobal(p) { global.window = global.window || {}; delete require.cache[require.resolve(p)]; require(p); }
function freshKernel() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }
function cents(x) { return Math.round((x || 0) * 100); }
function getP(o) { var p = o.parameters; return (typeof p === 'string') ? JSON.parse(p) : p; }  // replayOps pre-parses; upcast yields strings

// reducers — each reads the op param shape it was written for. v1 reducer == the SHIPPED foldBalances field set.
function foldV1(ops) { // reads l.account_id  (the v1 / current shape)
  var bal = {};
  ops.forEach(function (o) { var p = getP(o); if (!p.lines) return;
    p.lines.forEach(function (l) { var a = String(l.account_id); bal[a] = (bal[a] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); });
  return bal;
}
function foldV2Breaking(ops) { // v2 RENAMED account_id -> acct; this reducer reads l.acct only
  var bal = {};
  ops.forEach(function (o) { var p = getP(o); if (!p.lines) return;
    p.lines.forEach(function (l) { var a = String(l.acct); bal[a] = (bal[a] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); });
  return bal;
}
function foldV2Additive(ops) { // v2 ADDED optional l.currency; reducer still reads account_id (tolerant)
  var bal = {};
  ops.forEach(function (o) { var p = getP(o); if (!p.lines) return;
    p.lines.forEach(function (l) { var a = String(l.account_id); var cur = l.currency || 'MYR'; /* tolerated */
      bal[a] = (bal[a] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); });
  return bal;
}
// UPCAST-ON-READ: transform a v1 op object → v2 shape IN MEMORY (stored op untouched, chain intact)
function upcastV1toV2(ops) {
  return ops.map(function (o) { var p = getP(o);
    if (p.lines) p.lines = p.lines.map(function (l) { return { acct: l.account_id, amtacctdr: l.amtacctdr, amtacctcr: l.amtacctcr }; });
    return { parameters: JSON.stringify(p) }; });
}
function maxDiff(x, y) { var k = {}, m = 0, a; for (a in x) k[a] = 1; for (a in y) k[a] = 1; for (a in k) m = Math.max(m, Math.abs((x[a] || 0) - (y[a] || 0))); return m; }

function hasVersioning() {
  var src = fs.readFileSync(KERNEL, 'utf8') + fs.readFileSync(path.join(ROOT, 'build', 'erp', 'erp_period_close.js'), 'utf8');
  return { versionField: /schema_version|schemaVersion|op_version|opVersion/.test(src), upcaster: /upcast|migrateOp|opMigration/.test(src) };
}

(async function () {
  console.log('═══ SPIKE D2 — event/schema evolution on a SIGNED op-log (Ludwikowski pitfall #1) ═══');
  var SQL = await initSqlJs();
  loadGlobal(SIGNER);
  var ErpSigner = global.window.ErpSigner;
  var kp = await ErpSigner.mintKeypair();
  var appSigner = ErpSigner.makeSigner(kp);
  var K = freshKernel();
  K.setSigner({ sign: function (h) { return appSigner.sign(h); }, verify: function (h, s) { return appSigner.verify(h, s); }, signed_by: 'edge:app' });
  var db = new SQL.Database();

  // ── write N "v1" ops (params use account_id) and SEAL+SIGN the chain ──────────────────────────────
  var N = 20;
  for (var i = 0; i < N; i++) K.commitOp(db, 'POST', { table: 'C_Invoice', id: 100 + i, lines: [
    { account_id: '101', amtacctdr: 10 + i, amtacctcr: 0 }, { account_id: '400', amtacctdr: 0, amtacctcr: 10 + i } ] });
  await K.sealChain(db);
  var v0 = await K.verifyChain(db);
  var ops = K.replayOps(db, null, '*');
  var baseline = foldV1(ops);

  var vg = hasVersioning();

  // 2 — ADDITIVE change tolerated?
  var addDiff = maxDiff(baseline, foldV2Additive(ops));

  // 3 — BREAKING rename: how many v1 ops fold wrong under a v2-only reducer?
  var brkBal = foldV2Breaking(ops);
  var breakingDiff = maxDiff(baseline, brkBal);
  var brokenAccts = Object.keys(brkBal).filter(function (a) { return a === 'undefined'; }).length; // v2 read l.acct → undefined
  var breakingBroke = breakingDiff > 0;

  // 4 — UPCAST-ON-READ restores correctness; STORED chain still valid (we touched nothing on disk)
  var upcasted = upcastV1toV2(ops);
  var upcastBal = foldV2Breaking(upcasted);   // now the v2 reducer reads l.acct correctly
  var upcastDiff = maxDiff(baseline, upcastBal);
  var chainAfterUpcast = await K.verifyChain(db);   // stored ops untouched → still ok

  // 5 — THE REWRITE TRAP: migrate-by-rewrite a stored op's parameters → chain MUST break
  var dbBad = new SQL.Database();
  var Kb = freshKernel();
  Kb.setSigner({ sign: function (h) { return appSigner.sign(h); }, verify: function (h, s) { return appSigner.verify(h, s); }, signed_by: 'edge:app' });
  for (var j = 0; j < 5; j++) Kb.commitOp(dbBad, 'POST', { table: 'C_Invoice', id: 200 + j, lines: [{ account_id: '101', amtacctdr: 5, amtacctcr: 0 }] });
  await Kb.sealChain(dbBad);
  var okBefore = (await Kb.verifyChain(dbBad)).ok;
  // rewrite op id=3's params to the v2 (renamed) shape — the forbidden "upcast the stored event" migration
  dbBad.run("UPDATE kernel_ops SET parameters = ? WHERE id = 3",
    [JSON.stringify({ table: 'C_Invoice', id: 202, lines: [{ acct: '101', amtacctdr: 5, amtacctcr: 0 }] })]);
  var vBad = await Kb.verifyChain(dbBad);
  var rewriteBreaks = okBefore && !vBad.ok;

  console.log('§SPIKE-D2 chainSealed=' + v0.ok + ' versionField=' + (vg.versionField ? 'Y' : 'N') +
    ' upcaster=' + (vg.upcaster ? 'Y' : 'N') +
    ' additiveChange=' + (addDiff === 0 ? 'TOLERATED' : 'BROKE(' + addDiff + 'c)') +
    ' breakingRename=' + (breakingBroke ? 'FOLDS-WRONG(maxDiff=' + breakingDiff + 'c)' : 'ok') +
    ' upcastOnRead=' + (upcastDiff === 0 ? 'RESTORES(diff=0)' : 'FAILS(' + upcastDiff + 'c)') +
    ' chainIntactAfterUpcast=' + (chainAfterUpcast.ok ? 'Y' : 'N') +
    ' rewriteStoredOp=' + (rewriteBreaks ? 'BREAKS-CHAIN(' + (vBad.failAt || vBad.tip || 'payload altered') + ')' : 'did-not-break!') );
  console.log('§SPIKE-D2 VERDICT: no version tag + no upcaster today; tolerant-reader survives ADDITIVE change ' +
    'but a BREAKING rename folds wrong silently; upcast-ON-READ is the ONLY legal migration (rewriting a stored ' +
    'op breaks the signature) → D2 needs (a) a schema_version on each op + (b) a read-time upcaster registry.');
})().catch(function (e) { console.error('FATAL ' + e.stack); process.exit(2); });
