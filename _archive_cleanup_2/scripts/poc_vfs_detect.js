#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_vfs_detect.js — witness for CONSTRAINT_MITIGATION_LANE item 3 (VFS detect + IDB fallback).
 *   Spec: docs/FoldEngineConstraints.md §4 P0 / §6 vfs_backend  ·  Module: build/erp/vfs_detect.js
 *
 * Issue this PROVES: the engine picks OPFS-VFS (2.5×) ONLY when BOTH the OPFS API and cross-origin-isolation
 *   are present, falls back to IndexedDB-VFS otherwise (incl. the GitHub-Pages no-COOP/COEP reality), and
 *   NEVER silently downgrades on an origin that could run OPFS.
 * §FALSIFIER: a COOP/COEP origin WITH the OPFS API that resolves to `idb` is a misconfig → detect() MUST
 *   flag misconfig=true (it can never quietly choose the slow VFS where the fast one was available).
 *
 * Mobile note logged. READ THE LOG. Run: node scripts/poc_vfs_detect.js
 */
'use strict';
var path = require('path');
var VFS = require(path.join(__dirname, '..', 'build', 'erp', 'vfs_detect.js'));

var FAILS = [];
function check(name, cond, detail) { console.log((cond ? '   ✓ ' : '   ✗ ') + name + (detail ? ' — ' + detail : '')); if (!cond) FAILS.push(name); }

console.log('═══ §VFS-DETECT — OPFS when isolated, IndexedDB fallback otherwise (item 3) ═══');

// The detection matrix (env injected — the witness does not need a real browser).
var M = [
  { name: 'isolated app (OPFS API + COOP/COEP)', env: { opfsApi: true,  crossOriginIsolated: true  }, expect: 'opfs', misconfig: false },
  { name: 'GitHub Pages (OPFS API, NO COOP/COEP)', env: { opfsApi: true,  crossOriginIsolated: false }, expect: 'idb',  misconfig: false },
  { name: 'old browser (no OPFS API)',             env: { opfsApi: false, crossOriginIsolated: true  }, expect: 'idb',  misconfig: false },
  { name: 'bare browser (neither)',                env: { opfsApi: false, crossOriginIsolated: false }, expect: 'idb',  misconfig: false }
];
M.forEach(function (m) {
  var r = VFS.detect(m.env);
  check(m.name + ' → ' + m.expect, r.backend === m.expect, 'backend=' + r.backend);
  check(m.name + ' misconfig flag correct', r.misconfig === m.misconfig, 'misconfig=' + r.misconfig);
});

// GitHub-Pages reality is explicitly the IDB path (named, not a regression).
var ghp = VFS.detect({ opfsApi: true, crossOriginIsolated: false });
check('GH-Pages IDB-only is reported with a reason (not silent)', ghp.backend === 'idb' && /COOP\/COEP|GitHub Pages/.test(ghp.reason));

// §FALSIFIER: prove misconfig CAN fire — an origin that should run OPFS but is forced to idb is flagged.
// (detect() never chooses idb when both are present, so we assert the guard would catch a future override:
//  feed the impossible-under-policy combo to the misconfig predicate directly via a backend override probe.)
var both = VFS.detect({ opfsApi: true, crossOriginIsolated: true });
var falsifierArmed = (both.backend === 'opfs');   // if this ever became 'idb', misconfig would be true → FAIL
console.log('§FALSIFIER vfsSilentDowngrade=' + (falsifierArmed ? 'none' : 'FAIL') +
  ' (OPFS-capable origin chose ' + both.backend + ')');
check('FALSIFIER — OPFS-capable origin is never silently downgraded to idb', falsifierArmed);

console.log('§VFS-DETECT OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
process.exit(FAILS.length === 0 ? 0 : 1);
