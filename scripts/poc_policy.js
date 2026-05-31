#!/usr/bin/env node
/**
 * poc_policy.js — lease-expiry + value-tiering acceptance
 *   (prompts/DISTRIBUTED_POC.md #6; DistributedERP.md §9-C/D, §4 G-LEASE-EXPIRY, §5.3).
 *   Proves the two policy guards that bound the offline edge:
 *     - G-LEASE-EXPIRY: a lease unexercised by t0+TTL expires and returns to the pool; the expiry
 *       is itself an ORDERED op (deterministic — decided from a passed-in `now`, never Date.now()).
 *     - value-tiering (§5.3): offline high-value → BLOCK; offline low-value → ALLOW + reconcile
 *       (becomes a receivable, ledger-native); online → ALLOW via CAS. The residual lands in §8.
 *
 *   All time is an INPUT (§7) — no Date.now()/Math.random() — so every run is byte-identical.
 *
 * Run: node scripts/poc_policy.js 2>&1 | tee build/erp/poc_policy.log
 */
'use strict';
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// ── lease pool ──
function expireLeases(pool, leases, now) {
  var ops = [];
  leases.forEach(function (L) {
    if (!L.exercised && !L.expired && (L.t0 + L.ttl) <= now) {
      L.expired = true; pool.units += L.qty;
      ops.push({ op_type: 'LEASE_EXPIRE', lease: L.id, qty: L.qty, at: now }); // ordered op
    }
  });
  return ops;
}

// ── value-tiering policy for a claim/spend ──
function claimPolicy(amount, online, threshold) {
  if (online) return { decision: 'ALLOW', via: 'CAS' };
  if (amount >= threshold) return { decision: 'BLOCK', via: 'offline-high-value' };
  return { decision: 'ALLOW', via: 'offline-low-value', reconcile: 'receivable' };
}

(function () {
  console.log('═══ POC-POLICY — lease-expiry (ordered) + value-tiering (offline) ═══\n');

  // ── lease-expiry ──
  var pool = { units: 10 };
  var leases = [
    { id: 'L1', qty: 3, t0: 1000, ttl: 300, exercised: false }, // exercised in time
    { id: 'L2', qty: 4, t0: 1000, ttl: 300, exercised: false }  // abandoned offline device
  ];
  pool.units -= 3 + 4;                       // both reserved at dispatch (G-RESERVATION)
  console.log('§POLICY leased pool=' + pool.units + ' (10 − 3 − 4)');
  // L1 exercised at t=1200 (within TTL); L2 abandoned.
  leases[0].exercised = true;

  var now = 1400;                            // > t0+ttl (1300) → L2 must expire; INPUT, not Date.now()
  var expOps = expireLeases(pool, leases, now);
  console.log('§POLICY expire now=' + now + ' ops=' + expOps.length + ' reclaimed=' + (expOps[0] ? expOps[0].qty : 0) + ' pool=' + pool.units);
  verdict(expOps.length === 1 && expOps[0].lease === 'L2' && pool.units === 7,
          'unexercised lease expires → qty returns to pool, expiry is an ordered op', 'pool=' + pool.units + ' (3 consumed by exercised L1, 4 reclaimed from L2)');

  // determinism: same inputs → same expiry decision (re-run on a fresh copy).
  var pool2 = { units: 3 }, leases2 = [{ id: 'L2', qty: 4, t0: 1000, ttl: 300, exercised: false }];
  var rep = expireLeases(pool2, leases2, 1400);
  verdict(rep.length === 1 && pool2.units === 7, 'expiry is deterministic from passed-in `now` (no Date.now)', 're-run reclaimed=' + (rep[0] ? rep[0].qty : 0));

  // ── value-tiering ──
  var thr = 100;
  var hi = claimPolicy(500, false, thr);     // offline, high value
  var lo = claimPolicy(10,  false, thr);     // offline, low value
  var on = claimPolicy(500, true,  thr);     // online, any value
  console.log('§POLICY tier offline-hi=' + hi.decision + ' offline-lo=' + lo.decision + '(' + lo.reconcile + ') online=' + on.decision + '(' + on.via + ')');
  verdict(hi.decision === 'BLOCK', 'offline high-value → BLOCK (like an offline card decline, §5.3)', '500≥' + thr);
  verdict(lo.decision === 'ALLOW' && lo.reconcile === 'receivable', 'offline low-value → ALLOW + reconcile (receivable, ledger-native)', lo.via);
  verdict(on.decision === 'ALLOW' && on.via === 'CAS', 'online → ALLOW via CAS (first-wins authority)', on.via);

  console.log('\n§POLICY ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — leases expire deterministically as ordered ops; offline claims are value-tiered, residual → the ledger'));
  process.exit(fails ? 1 : 0);
})();
