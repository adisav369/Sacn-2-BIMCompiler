#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_battery_aware.js — witness for CONSTRAINT_MITIGATION_LANE item 6 (battery-aware folding + persist + resume).
 *   Spec: docs/FoldEngineConstraints.md §4 P2 / §6  ·  Module: build/erp/battery_aware.js
 *
 * Issue this PROVES: NON-URGENT folds are deferred only when battery <20% AND discharging; URGENT folds
 *   (user commits, paint bootstrap) ALWAYS run; persist() is requested; the resume-asset manifest carries
 *   the WASM + checkpoint for an instant cold resume (no genesis, no re-download).
 * §FALSIFIER: an URGENT fold throttled at ANY battery level → FAIL (correctness/UX never sacrificed).
 *
 * READ THE LOG. Run: node scripts/poc_battery_aware.js
 */
'use strict';
var path = require('path');
var Battery = require(path.join(__dirname, '..', 'build', 'erp', 'battery_aware.js'));

var FAILS = [];
function check(name, cond, detail) { console.log((cond ? '   ✓ ' : '   ✗ ') + name + (detail ? ' — ' + detail : '')); if (!cond) FAILS.push(name); }

(async function () {
  console.log('═══ §BATTERY-AWARE — throttle non-urgent folds on low battery; never urgent (item 6) ═══');

  // ── throttle matrix ───────────────────────────────────────────────────────────────────────────
  var low = { level: 0.15, charging: false };      // 15%, discharging
  var lowCharging = { level: 0.15, charging: true };// 15%, charging
  var ok = { level: 0.80, charging: false };       // 80%, discharging

  check('background fold THROTTLED at 15% discharging', Battery.shouldThrottle(low, 'background') === true);
  check('background fold NOT throttled while charging', Battery.shouldThrottle(lowCharging, 'background') === false);
  check('background fold NOT throttled at 80%', Battery.shouldThrottle(ok, 'background') === false);

  // §FALSIFIER: urgent folds never throttled, even at 15% discharging.
  check('FALSIFIER — URGENT fold NEVER throttled (15% discharging)', Battery.shouldThrottle(low, 'urgent') === false);
  check('FALSIFIER — URGENT fold NEVER throttled (1% discharging)', Battery.shouldThrottle({ level: 0.01, charging: false }, 'urgent') === false);

  // ── readBattery (injected getter) ───────────────────────────────────────────────────────────────
  var fakeNav = function () { return Promise.resolve({ level: 0.42, charging: false }); };
  var b = await Battery.readBattery(fakeNav);
  check('readBattery reads level/charging from getter', b.level === 0.42 && b.charging === false && b.supported === true);
  var bNone = await Battery.readBattery(null);
  check('readBattery absent → safe default (level=1, not throttling)', bNone.supported === false && Battery.shouldThrottle(bNone, 'background') === false);

  // ── persist() ─────────────────────────────────────────────────────────────────────────────────
  var grantStorage = { persist: function () { return Promise.resolve(true); } };
  var denyStorage = { persist: function () { return Promise.resolve(false); } };
  var p1 = await Battery.requestPersist(grantStorage);
  check('persist granted reported', p1.supported === true && p1.persisted === true);
  var p2 = await Battery.requestPersist(denyStorage);
  check('persist denied reported (not crashed)', p2.supported === true && p2.persisted === false);
  var p3 = await Battery.requestPersist(null);
  check('persist unsupported reported', p3.supported === false);

  // ── resume assets (SW precache manifest for instant cold resume) ─────────────────────────────────
  var assets = Battery.resumeAssets();
  console.log('§BATTERY resumeAssets=[' + assets.join(', ') + ']');
  check('resume manifest carries the WASM (no re-download)', assets.some(function (a) { return /sql-wasm.*\.wasm$/.test(a); }));
  check('resume manifest carries the checkpoint (no genesis)', assets.some(function (a) { return /checkpoint/.test(a); }));
  check('resume manifest carries the T0 seed', assets.some(function (a) { return /ad_seed\.db$/.test(a); }));

  console.log('§BATTERY-AWARE OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
  process.exit(FAILS.length === 0 ? 0 : 1);
})();
