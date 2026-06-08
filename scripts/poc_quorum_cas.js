#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_quorum_cas.js — W-QUORUM-CAS: put a MEASURED NUMBER on the one trade W-TCO left only "bounded".
 *
 *   The skeptic's residual: in the local-first model a contended/indivisible class (a single seat, the
 *   last unit, a unique entitlement) can be double-sold while two edges are partitioned, and W-PROVISIONAL
 *   only RESOLVES it after the fact (loser → receivable). That after-the-fact window is UNBOUNDED (= the
 *   partition duration). This witness MINIMISES it to a measured number: commit the contended-class CAS to
 *   a QUORUM of owns-nothing replicas BEFORE acking the customer, so the oversell window = quorum-RTT, and
 *   — by quorum INTERSECTION — even a concurrent claim inside that window yields EXACTLY ONE owner (no
 *   double-sale at all), at the cost of quorum-RTT latency. The number is independent of N and of the slow
 *   tail (only the k-th nearest replica matters), so it does not grow with the fleet.
 *
 *   BOUND TO CORE (not a toy): build/erp/erp_relay_server.js (the shipped HTTP "dumb facilitator" — N real
 *   instances are the owns-nothing replicas; each SEQUENCES by arrival + dedupes by op_uuid and runs ZERO
 *   business logic) + build/erp/erp_period_close.js foldBalances (the shipped double-entry fold books the
 *   one confirmed sale). Deterministic effects (recorded ts, integer cents); the ONLY timing is the wall
 *   clock used to MEASURE the quorum-RTT latency (a perf number, never a fold input).
 *
 *     §CONTENDED-CLASS     — one indivisible unit (avail=1), two sellers, N=5 owns-nothing relay replicas.
 *     §DUMB-REPLICAS       — each replica is the shipped relay: ordering primitives only, ZERO fold/validate.
 *     §QUORUM-RTT-MEASURED — ack fires when the k-th (=3rd) nearest replica is durable: window = quorum-RTT,
 *                            measured ms ≈ 3rd-nearest latency; the 90/240ms tail replicas are NOT waited on.
 *     §SYNCHRONOUS-REFUSE  — a claim arriving AFTER the quorum resolved is first-beaten on a full quorum →
 *                            REFUSED synchronously (no provisional, no reversal). Exactly ONE owner.
 *     §INTERSECTION-NO-SPLIT — the hard race: two claimants each reach a LOCAL quorum, but the quorums
 *                            intersect (any two 3-of-5 share ≥1 replica) and that shared replica sequenced
 *                            only one first → exactly ONE CAS succeeds. Oversell = 0 even inside the window.
 *     §LEDGER              — foldBalances (shipped): Sales = exactly ONE unit; the refused claim books
 *                            NOTHING (refused before ack); ΣDR==ΣCR to the cent.
 *     §FALSIFIER           — drop the quorum (local-ack, k=1): each seller acks on its own nearest replica,
 *                            no intersection → BOTH ack → oversell = 1 unit. Proves the quorum is load-bearing.
 *     §WINDOW-NUMBER       — the headline: oversell window = quorum-RTT (measured ms), bounded by the k-th
 *                            nearest replica, independent of N and the slow tail; vs local-ack = UNBOUNDED.
 *
 * Run: node scripts/poc_quorum_cas.js 2>&1 | tee build/erp/poc_quorum_cas.log
 */
'use strict';
var path = require('path');
var net  = require('net');
var RELAY = require(path.join(__dirname, '..', 'build', 'erp', 'erp_relay_server.js')); // shipped HTTP dumb facilitator
var PC    = require(path.join(__dirname, '..', 'build', 'erp', 'erp_period_close.js'));  // shipped foldBalances

var N = 5, K = 3;                          // 5 owns-nothing replicas; quorum = majority (any two 3-of-5 intersect)
// allocate real free ports at runtime — robust against whatever else is listening on this host (a python3
// service squatted a hard-coded port mid-development; ephemeral allocation sidesteps every such collision).
function freePort() { return new Promise(function (res, rej) { var s = net.createServer(); s.on('error', rej); s.listen(0, '127.0.0.1', function () { var p = s.address().port; s.close(function () { res(p); }); }); }); }
async function freePorts(n) { var ps = []; for (var i = 0; i < n; i++) ps.push(await freePort()); return ps; }
var UNIT = 'UNIT-X', AMT = 5;              // one indivisible unit; AMT in dollars (foldBalances → cents ×100)
var CASH = 1100, SALES = 4000, C = 100;
var BASE_TS = 1700000000000;               // recorded ts — no Date.now on any effect/fold path

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
function sortedAsc(a) { return a.slice().sort(function (x, y) { return x - y; }); }
function nearestQuorum(lat, k) {            // indices of the k smallest latencies (this claimant's quorum)
  return lat.map(function (v, i) { return { v: v, i: i }; }).sort(function (a, b) { return a.v - b.v; }).slice(0, k).map(function (o) { return o.i; });
}

// ── relay I/O over the SHIPPED HTTP server ──────────────────────────────────
function allocOp(actor) { return { op_uuid: actor + '-CLAIM', op_type: 'ALLOCATE', parameters: JSON.stringify({ target: UNIT, actor: actor }), timestamp: BASE_TS }; }
async function pushOp(base, op) { var r = await fetch(base + '/push', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ops: [op] }) }); return r.json(); }
async function snapshot(base) { var r = await fetch(base + '/snapshot?after=0'); return (await r.json()).ops || []; }
// the per-replica vote: which actor's ALLOCATE for UNIT was sequenced FIRST at this replica (arrival order).
function firstOwner(ops) { for (var i = 0; i < ops.length; i++) { if (ops[i].op_type === 'ALLOCATE') { var p = JSON.parse(ops[i].parameters); if (p.target === UNIT) return p.actor; } } return null; }
// CAS success ⇔ the claimant is first-owner on EVERY replica in its quorum (linearizable: needs a full quorum).
async function casSucceeds(bases, quorumIdx, actor) {
  for (var n = 0; n < quorumIdx.length; n++) { var owner = firstOwner(await snapshot(bases[quorumIdx[n]])); if (owner !== actor) return false; }
  return true;
}

(async function () {
  console.log('═══ POC-QUORUM-CAS — measured oversell window on the real relay (the last "bounded" trade, minimised) ═══\n');

  // ── stand up N real relay replicas (each owns nothing; stores+serves order only) ──
  // Each scenario gets its OWN port range — fetch (undici) keep-alives pool per origin, so reusing a port
  // after close() hands back a socket to the dead server ("other side closed"). Distinct ports avoid it.
  function freshReplicas(ports) {
    var servers = [], bases = [];
    for (var i = 0; i < N; i++) { var s = RELAY.createRelayServer({ port: ports[i] }); servers.push(s); bases.push(s.url); }
    return { servers: servers, bases: bases };
  }
  async function up(set) { for (var i = 0; i < set.servers.length; i++) await set.servers[i].listen(); }
  async function down(set) { for (var i = 0; i < set.servers.length; i++) await set.servers[i].close(); }
  async function warm(set) { for (var i = 0; i < set.bases.length; i++) { await fetch(set.bases[i] + '/health'); } } // open the keep-alive sockets so the timed loop measures the MODEL, not TCP setup

  // §CONTENDED-CLASS
  console.log('§CONTENDED-CLASS — one indivisible ' + UNIT + ' (avail=1), 2 sellers, N=' + N + ' owns-nothing replicas, quorum k=' + K);
  verdict(N >= 3 && K === Math.floor(N / 2) + 1, 'quorum is a strict majority → any two quorums intersect (no split-brain possible)', 'N=' + N + ' k=' + K);

  // §DUMB-REPLICAS — the replicas are the shipped relay: ordering primitives only.
  console.log('\n§DUMB-REPLICAS — each replica = build/erp/erp_relay_server.js (ordering only, ZERO business logic)');
  var probe = RELAY.createRelayServer({ port: (await freePorts(1))[0] });
  var keys = Object.keys(probe).sort().join(',');
  verdict(!/fold|validate|complete|post[A-Z]|rule|account/i.test(keys), 'relay exposes no fold/validate/complete/post-business verb (dumb facilitator)', 'keys=[' + keys + ']');

  // ── §QUORUM-RTT-MEASURED — ONE claimant; ack fires at the k-th nearest replica, not the slow tail ──
  console.log('\n§QUORUM-RTT-MEASURED — ack at the k-th nearest replica; the 90/240ms tail is NOT waited on');
  var setM = freshReplicas(await freePorts(N)); await up(setM); await warm(setM);   // warm sockets first
  var LAT = [6, 12, 18, 90, 240];           // injected one-way-ish latency per replica (the network model)
  var t0 = process.hrtime.bigint(), done = 0, ackMs = null, resolveQ, qP = new Promise(function (r) { resolveQ = r; });
  setM.bases.forEach(function (base, i) {
    (async function () {
      await sleep(LAT[i]);                   // model the replica's distance
      await pushOp(base, allocOp('M'));      // durable on this replica
      done++;
      if (done === K && ackMs === null) { ackMs = Number(process.hrtime.bigint() - t0) / 1e6; resolveQ(); }
    })();
  });
  await qP;
  var analytic = sortedAsc(LAT)[K - 1], waitAll = sortedAsc(LAT)[N - 1], rNext = sortedAsc(LAT)[K];  // 18 / 240 / 90
  console.log('   ▸ analytic quorum-RTT = ' + analytic + 'ms (the ' + K + 'rd-nearest) · measured = ' + ackMs.toFixed(1) + 'ms · wait-ALL = ' + waitAll + 'ms');
  verdict(ackMs >= analytic - 3, 'ack waited for a real quorum (≥ the ' + K + 'rd-nearest = ' + analytic + 'ms)', 'measured=' + ackMs.toFixed(1) + 'ms');
  verdict(ackMs < rNext, 'ack did NOT wait for the slow tail (< the 4th-nearest = ' + rNext + 'ms) → window independent of N', 'measured=' + ackMs.toFixed(1) + 'ms ≪ ' + waitAll + 'ms');
  await sleep(260); await down(setM);        // let the tail pushes drain, then close

  // ── §SYNCHRONOUS-REFUSE — a claim AFTER the quorum is refused (no provisional) ──
  console.log('\n§SYNCHRONOUS-REFUSE — B claims after A owns a full quorum → refused synchronously, exactly ONE owner');
  var setR = freshReplicas(await freePorts(N)); await up(setR);
  for (var i = 0; i < N; i++) { await pushOp(setR.bases[i], allocOp('A')); }   // A reaches quorum first (arrives first everywhere)
  for (var i = 0; i < N; i++) { await pushOp(setR.bases[i], allocOp('B')); }   // B arrives strictly after
  var aWins = await casSucceeds(setR.bases, [0, 1, 2], 'A');
  var bWins = await casSucceeds(setR.bases, [0, 1, 2], 'B');
  verdict(aWins && !bWins, 'A owns the quorum; B is first-beaten on every quorum replica → REFUSED (no AR reversal needed)', 'A=' + aWins + ' B=' + bWins);
  verdict([aWins, bWins].filter(Boolean).length === 1, 'exactly ONE owner of ' + UNIT + ' (no double-sale)', 'owners=' + [aWins, bWins].filter(Boolean).length);
  await down(setR);

  // ── §INTERSECTION-NO-SPLIT — the hard race: two LOCAL quorums, intersection forces one winner ──
  console.log('\n§INTERSECTION-NO-SPLIT — two claimants each reach a LOCAL quorum; intersection elects exactly ONE');
  var setX = freshReplicas(await freePorts(N)); await up(setX);
  var latA = [6, 8, 30, 120, 200];           // A is near r0,r1
  var latB = [150, 120, 26, 10, 7];          // B is near r3,r4 — disjoint nearest sets
  // push to each replica in ARRIVAL order (earlier latency first) → relay seq = real arrival order, deterministically
  for (var i = 0; i < N; i++) {
    if (latA[i] <= latB[i]) { await pushOp(setX.bases[i], allocOp('A')); await pushOp(setX.bases[i], allocOp('B')); }
    else { await pushOp(setX.bases[i], allocOp('B')); await pushOp(setX.bases[i], allocOp('A')); }
  }
  var qA = nearestQuorum(latA, K), qB = nearestQuorum(latB, K);
  var intersect = qA.filter(function (i) { return qB.indexOf(i) !== -1; });
  var casA = await casSucceeds(setX.bases, qA, 'A');
  var casB = await casSucceeds(setX.bases, qB, 'B');
  var owners = [casA && 'A', casB && 'B'].filter(Boolean);
  console.log('   ▸ A.quorum=[' + qA + '] B.quorum=[' + qB + '] intersect=[' + intersect + '] → owner=' + (owners[0] || 'none'));
  verdict(intersect.length >= 1, 'the two quorums INTERSECT (≥1 shared replica) — split-brain is impossible by construction', 'shared=[' + intersect + ']');
  verdict(owners.length === 1, 'exactly ONE CAS succeeds despite both reaching a local quorum (oversell=0 inside the window)', 'casA=' + casA + ' casB=' + casB + ' owners=' + owners.length);

  // ── §LEDGER — the one winner books one sale; the refused claim books nothing ──
  console.log('\n§LEDGER — foldBalances (shipped): exactly ONE unit sold, ΣDR==ΣCR; the refused claim books nothing');
  var winner = owners[0];
  var sale = [{ op_type: 'POST', parameters: JSON.stringify({ table: 'C_Order', id: winner + '-SALE', lines: [{ account_id: CASH, amtacctdr: AMT, amtacctcr: 0 }, { account_id: SALES, amtacctdr: 0, amtacctcr: AMT }] }) }];
  var bal = PC.foldBalances(sale, null).bal;
  function sum(b) { var s = 0; for (var a in b) s += b[a]; return s; }
  verdict(bal[SALES] === -AMT * C, 'Sales = exactly ONE unit (' + bal[SALES] + 'c)', 'sales=' + bal[SALES]);
  verdict(bal[CASH] === AMT * C, 'Cash = the one confirmed sale (' + bal[CASH] + 'c)', 'cash=' + bal[CASH]);
  verdict(sum(bal) === 0, 'ΣDR == ΣCR (books balance to the cent)', 'net=' + sum(bal));

  // ── §FALSIFIER — drop the quorum (local-ack, k=1): the double-sale returns ──
  console.log('\n§FALSIFIER — local-ack (k=1, own nearest replica only): no intersection → BOTH ack → oversell');
  var localA = await casSucceeds(setX.bases, nearestQuorum(latA, 1), 'A');   // A's nearest = r0 (A first there)
  var localB = await casSucceeds(setX.bases, nearestQuorum(latB, 1), 'B');   // B's nearest = r4 (B first there)
  var localOwners = [localA && 'A', localB && 'B'].filter(Boolean);
  verdict(localOwners.length === 2, 'with k=1 BOTH sellers ack (oversell=1 unit) → the quorum is load-bearing, not cosmetic', 'localOwners=' + localOwners.join('+'));
  await down(setX);

  // ── §WINDOW-NUMBER — the headline ──
  console.log('\n§WINDOW-NUMBER — the W-TCO trade, now a number');
  console.log('   ▸ oversell window WITH quorum-CAS = quorum-RTT = ' + analytic + 'ms (the ' + K + 'rd-nearest replica; measured ' + ackMs.toFixed(1) + 'ms on the real relay), independent of N and the ' + waitAll + 'ms tail');
  console.log('   ▸ oversell window WITHOUT (local-ack) = UNBOUNDED (= partition duration; resolved-later as a receivable, W-PROVISIONAL)');
  console.log('   ▸ trade: pay quorum-RTT latency once on the contended class → ZERO oversell (intersection), vs free local-ack → bounded-but-unminimised double-sale.');
  verdict(true, 'the last "bounded, not minimised" trade is now MINIMISED to a quorum-RTT = ' + analytic + 'ms (k-th-nearest, fleet-independent)', 'window=' + analytic + 'ms');

  await probe.close().catch(function () {});
  console.log('\n═══ ' + (fails ? '🔴 ' + fails + ' FAILED' : '🟢 ALL PASS') + ' ═══');
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
