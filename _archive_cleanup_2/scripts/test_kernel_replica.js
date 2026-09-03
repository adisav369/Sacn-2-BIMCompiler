#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * test_kernel_replica.js — W-REPLICA: the 3-host read-replica mockup (ERP.md §0.20 / W-PERSIST).
 *   Serves the SAME snapshot from 3 local origins (stand-ins for GH / OCI / here) + one down host.
 *
 *   Proves:
 *     §REPLICA-CONVERGE  — two good hosts both replay to the SAME computed tip == advertised tip.
 *     §REPLICA-DISPOSABLE— host A tip === host B tip: any reachable host yields identical state, so the
 *                          host is disposable (the LOG is the source of truth).
 *     §REPLICA-TAMPER    — a host serving an altered op is DETECTED: recomputed tip != advertised tip
 *                          (static-file integrity; full tamper-proofing = sign the tip, W-SIGN).
 *     §REPLICA-FAILOVER  — resolve() skips a down host and returns the next reachable one.
 *
 * Run: node scripts/test_kernel_replica.js 2>&1 | tee build/erp/test_kernel_replica.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path'), fs = require('fs'), http = require('http');
var initSqlJs = require('sql.js');
var KERNEL = path.join(process.env.HOME, 'bim-ootb', 'viewer', 'kernel_ops.js');
function freshK() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }
var RC = require(path.join(__dirname, '..', 'build', 'erp', 'erp_replica_client.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

function serve(port, jsonStr) {
  var s = http.createServer(function (req, res) {
    if (req.url.indexOf('/relay_snapshot.json') === 0) { res.setHeader('Content-Type', 'application/json'); res.end(jsonStr); }
    else { res.statusCode = 404; res.end(); }
  });
  return new Promise(function (r) { s.listen(port, function () { r(s); }); });
}

(async function () {
  console.log('═══ TEST-KERNEL-REPLICA — 3-host read-replica mockup (GH / OCI / here) ═══\n');
  if (typeof fetch === 'undefined') { console.log('   🔴 need Node 18+ (global fetch)'); process.exit(1); }
  var SQL = await initSqlJs();
  var snap = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'relay_snapshot.json'), 'utf8'));
  var tampered = JSON.parse(JSON.stringify(snap)); tampered.ops[1].parameters = '{"actor":"EVE","tampered":true}'; // advertised tip left intact

  var pGH = 8170 + (process.pid % 60), pOCI = pGH + 1, pHERE = pGH + 2, pBAD = pGH + 3;
  var sGH = await serve(pGH, JSON.stringify(snap));        // GH-mock (good)
  var sOCI = await serve(pOCI, JSON.stringify(snap));      // OCI-mock (good)
  var sBAD = await serve(pHERE, JSON.stringify(tampered)); // a host serving a tampered snapshot

  var base = 'http://localhost:';
  var client = RC.createReplicaClient([
    { name: 'GH',   base: base + pGH },
    { name: 'OCI',  base: base + pOCI },
    { name: 'EVIL', base: base + pHERE }
  ]);

  // ── fetch all + replay each through the real kernel ──
  console.log('§REPLICA — fetch all hosts + replay');
  var all = await client.fetchAll();
  async function replay(entry) { var K = freshK(); var db = new SQL.Database(); return client.replayAndVerify(db, K, entry.snapshot); }
  var gh = await replay(all[0]), oci = await replay(all[1]), evil = await replay(all[2]);

  // ── §REPLICA-CONVERGE + §REPLICA-DISPOSABLE ──
  verdict(gh.ok && oci.ok, 'GH + OCI both replay+verify OK', 'gh=' + gh.ok + ' oci=' + oci.ok);
  verdict(gh.computedTip === oci.computedTip, 'GH tip === OCI tip (convergence)', (gh.computedTip || '').slice(0, 12) + '…');
  verdict(gh.computedTip === snap.tip, 'computed tip === generator tip (host-independent)', (snap.tip || '').slice(0, 12) + '…');

  // ── §REPLICA-TAMPER (naive: op changed, advertised tip left stale) ──
  verdict(!evil.ok && !evil.matchesAdvertised, 'naive tamper DETECTED (computed != advertised)',
    'computed=' + (evil.computedTip || '').slice(0, 10) + ' advertised=' + (evil.advertisedTip || '').slice(0, 10));

  // ── §REPLICA-FORGE (sophisticated: op changed AND advertised tip recomputed to match — only the
  //    SIGNATURE catches it, because the forger has no controller private key) ──
  console.log('\n§REPLICA-FORGE — forger fixes the tip too, but cannot forge the signature');
  var forged = JSON.parse(JSON.stringify(tampered));
  forged.tip = evil.computedTip;     // attacker sets advertised tip = the (correct) tip of the tampered ops
  // forged.sig stays the ORIGINAL sig (valid only for the original tip) — no private key to re-sign
  var pFORGE = pBAD + 1; var sFORGE = await serve(pFORGE, JSON.stringify(forged));
  var fc = RC.createReplicaClient([{ name: 'FORGE', base: base + pFORGE }]);
  var fe = (await fc.fetchAll())[0];
  var fr = await replay(fe);
  verdict(fr.matchesAdvertised === true, 'forger made computed == advertised (recompute alone would PASS)', 'match=' + fr.matchesAdvertised);
  verdict(fr.sigValid === false && fr.ok === false, 'FORGE DETECTED by signature (no controller key)', 'sigValid=' + fr.sigValid + ' ok=' + fr.ok);
  await new Promise(function (r) { sFORGE.close(r); });

  // confirm the genuine snapshot's signature verifies (positive control)
  verdict(gh.sigValid === true, 'genuine snapshot signature VALID under pinned key', 'sigValid=' + gh.sigValid);

  // ── §REPLICA-FAILOVER ──
  console.log('\n§REPLICA-FAILOVER — skip a down host');
  var foClient = RC.createReplicaClient([{ name: 'DOWN', base: base + pBAD }, { name: 'GH', base: base + pGH }]);
  var picked = await foClient.resolve();
  verdict(picked.host === 'GH', 'resolve() skipped DOWN, picked GH', 'picked=' + picked.host);

  await new Promise(function (r) { sGH.close(r); }); await new Promise(function (r) { sOCI.close(r); }); await new Promise(function (r) { sBAD.close(r); });
  console.log('\n═══ ' + (fails ? '🔴 ' + fails + ' FAILED' : '🟢 ALL PASS') + ' ═══');
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
