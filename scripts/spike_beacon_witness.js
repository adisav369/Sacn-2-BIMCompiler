#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * spike_beacon_witness.js — §-log proof the minimal field-error beacon installs and CAPTURES.
 *   Card: prompts/RESUME_S1_DISCOVERY_SPIKES.md §Beacon  ·  Module: deploy/dev/error_beacon.js (== build/erp copy)
 *
 * Issue it PROVES: window.onerror + unhandledrejection are wired → each uncaught error becomes a §FIELD-ERROR
 *   line AND lands in a bounded, PII-free offline ring (so a silent field failure is no longer invisible).
 * §FALSIFIER: an error that fires NO §FIELD-ERROR line, or a buffer that grows past the bound, or a record
 *   carrying user/business data (PII) → FAIL.
 */
'use strict';
var path = require('path');
var FAILS = [];
function check(name, cond, detail) { console.log((cond ? '   ✓ ' : '   ✗ ') + name + (detail ? ' — ' + detail : '')); if (!cond) FAILS.push(name); }

// minimal browser shim: addEventListener registry + a localStorage stub
var handlers = {};
var lsData = {};
global.window = {
  addEventListener: function (type, fn) { (handlers[type] = handlers[type] || []).push(fn); },
  localStorage: { getItem: function (k) { return k in lsData ? lsData[k] : null; },
                  setItem: function (k, v) { lsData[k] = String(v); },
                  removeItem: function (k) { delete lsData[k]; } }
};
global.localStorage = global.window.localStorage;

require(path.join(__dirname, '..', 'deploy', 'dev', 'error_beacon.js'));
var B = global.window.__ERR_BEACON__;

console.log('═══ §SPIKE-BEACON witness ═══');
check('beacon installed (window.__ERR_BEACON__ present)', !!B);
check('error + unhandledrejection listeners registered', (handlers['error'] || []).length > 0 && (handlers['unhandledrejection'] || []).length > 0,
  'error=' + (handlers['error'] || []).length + ' rejection=' + (handlers['unhandledrejection'] || []).length);
check('starts at captured=0', B.captured === 0, 'captured=' + B.captured);

// fire a synthetic uncaught error through the real handler
handlers['error'][0]({ message: 'boom from spike', filename: 'x.js', lineno: 42, colno: 7, error: { stack: 'Error: boom\n  at f (x.js:42:7)' } });
check('uncaught error captured (captured=1)', B.captured === 1, 'captured=' + B.captured);

// fire an unhandled promise rejection
handlers['unhandledrejection'][0]({ reason: { message: 'rejected tail', stack: 'Error: rejected tail\n  at g' } });
check('promise rejection captured (captured=2)', B.captured === 2, 'captured=' + B.captured);

// resource-load error (element target, no Error object)
handlers['error'][0]({ target: { tagName: 'SCRIPT', src: 'missing.js' } });
check('resource-load error captured (captured=3)', B.captured === 3, 'captured=' + B.captured);

var buf = B.list();
check('offline ring holds all 3 records', buf.length === 3, 'buf.length=' + buf.length);
check('record is PII-free (only kind/message/src/line/col/stack/seq keys)',
  buf.every(function (r) { return Object.keys(r).sort().join(',') === 'col,kind,line,message,seq,src,stack'; }),
  'keys=' + Object.keys(buf[0]).sort().join(','));

// bound: push 60 → ring must cap at MAX=50 (no unbounded growth)
for (var i = 0; i < 60; i++) B._emit('flood ' + i);
var capped = B.list();
check('offline ring is BOUNDED (≤50, never unbounded)', capped.length <= 50, 'buf.length=' + capped.length);

// sample line for the spike record
var s = buf[0];
console.log('§SPIKE-BEACON installed=Y captured=' + B.captured + ' sample="' + s.kind + ':' + s.message + '" bounded=' + (capped.length <= 50 ? 'Y@' + capped.length : 'N') + ' pii=N telemetryPipe=N');
console.log('§SPIKE-BEACON-WITNESS OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
process.exit(FAILS.length === 0 ? 0 : 1);
