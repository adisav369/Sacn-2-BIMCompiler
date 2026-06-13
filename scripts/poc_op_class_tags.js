#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_op_class_tags.js — witness for CONSTRAINT_MITIGATION_LANE item 5 (tag shared op-classes for CAS).
 *   Spec: docs/FoldEngineConstraints.md §4 P1 / §1 finding 1  ·  Module: build/erp/op_class_tags.js
 *
 * Issue this PROVES: quorum-CAS (W-QUORUM-CAS) is routed ONLY to the indivisible shared classes
 *   (stock/credit), so the 99.9% of ordinary ops take the fast local-first commit — the quorum-RTT is paid
 *   only where an oversell is actually possible.
 * §FALSIFIER: (a) a KNOWN contended op returning needsQuorum=false = silent oversell risk → MUST be tagged;
 *   (b) tagging that routes >>0.1% of a realistic op-mix to CAS is too broad → defeats the optimisation.
 *
 * READ THE LOG. Run: node scripts/poc_op_class_tags.js
 */
'use strict';
var path = require('path');
var OpClass = require(path.join(__dirname, '..', 'build', 'erp', 'op_class_tags.js'));

var FAILS = [];
function check(name, cond, detail) { console.log((cond ? '   ✓ ' : '   ✗ ') + name + (detail ? ' — ' + detail : '')); if (!cond) FAILS.push(name); }
function op(type, table, extra) { return { op_type: type, parameters: JSON.stringify(Object.assign({ table: table }, extra || {})) }; }

console.log('═══ §OP-CLASS-TAG — quorum-CAS fires ONLY on tagged shared classes (item 5) ═══');

// ── KNOWN contended classes MUST be tagged (the oversell-risk falsifier) ──────────────────────────
var contended = [
  { o: op('ALLOCATE', null), label: 'stock ALLOCATE (one seat)' },
  { o: op('RESERVE_STOCK', null), label: 'RESERVE_STOCK' },
  { o: op('POST', 'M_Storage'), label: 'POST to M_Storage (on-hand)' },
  { o: op('CONSUME_CREDIT', null), label: 'CONSUME_CREDIT' },
  { o: op('POST', 'C_BPartner'), label: 'POST to C_BPartner (credit)' }
];
contended.forEach(function (c) {
  var k = OpClass.classOf(c.o);
  check('FALSIFIER — contended class tagged shared: ' + c.label, OpClass.needsQuorum(c.o) === true, 'tag=' + (k.tag || 'NONE'));
});

// ── ordinary ops must NOT take the CAS path (fast local-first) ────────────────────────────────────
var ordinary = [
  op('CREATE_DOCUMENT', 'C_Order'),
  op('POST', 'C_Invoice'),
  op('POST', 'C_Payment'),
  op('GRID_MOVE', null),
  op('VIEW_FILTER', null),
  op('POST', 'GL_Journal')
];
ordinary.forEach(function (o) {
  check('ordinary op fast-path (no quorum): ' + o.op_type + '/' + (JSON.parse(o.parameters).table || '-'),
    OpClass.needsQuorum(o) === false);
});

// ── realistic op-mix: ~0.1% shared. 1000 ops, 1 stock + 0 credit contended ────────────────────────
var mix = [];
for (var i = 0; i < 997; i++) mix.push(op('POST', 'C_Invoice'));
mix.push(op('CREATE_DOCUMENT', 'C_Order'));
mix.push(op('GRID_MOVE', null));
mix.push(op('ALLOCATE', null));                  // the ONE contended op in 1000
var casRouted = mix.filter(function (o) { return OpClass.needsQuorum(o); }).length;
var pct = (casRouted / mix.length * 100).toFixed(2);
console.log('§OP-CLASS-TAG mix=' + mix.length + ' casRouted=' + casRouted + ' (' + pct + '%) fastPath=' + (mix.length - casRouted));
check('FALSIFIER — CAS routes only the contended ~0.1% (tag not too broad)', casRouted === 1 && (casRouted / mix.length) <= 0.001 + 1e-9, casRouted + '/' + mix.length);

console.log('§OP-CLASS-TAG sharedClassesTagged=' + OpClass.SHARED.length + ' tags=[' +
  OpClass.SHARED.map(function (s) { return s.tag; }).filter(function (v, i, a) { return a.indexOf(v) === i; }).join(',') + ']');
console.log('§OP-CLASS-TAG OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
process.exit(FAILS.length === 0 ? 0 : 1);
