#!/usr/bin/env node
/**
 * test_kernel_chain.js — exercises the REAL bim-ootb/viewer/kernel_ops.js (not a copy) to confirm
 *   the wired W-CHAIN seal/verify works under crypto.subtle (same algorithm proven in poc_chain.js).
 *   Shims the browser globals (window/indexedDB/APP); crypto.subtle + TextEncoder are native in Node 20+.
 * Run: node scripts/test_kernel_chain.js 2>&1 | tee build/erp/test_kernel_chain.log
 */
'use strict';
// Node 18 doesn't expose `crypto` as a bare global (browsers do). Shim it for the test only —
// production runs in the browser where crypto.subtle is native.
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} };
global.APP = global.window.APP;   // bare APP ref inside _persistToIdb (browser global)
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path');
var initSqlJs = require('sql.js');
require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'kernel_ops.js'));  // attaches window.KernelOps
var K = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async function () {
  console.log('═══ TEST-KERNEL-CHAIN — real kernel_ops.js seal/verify under crypto.subtle ═══\n');
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  K.ensureTable(db);
  K.commitOp(db, 'SESSION_START', { ts: 't0' });
  K.commitOp(db, 'SELL', { item: 'burger', qty: 1 });
  K.commitOp(db, 'SELL', { item: 'cola', qty: 2 });

  var s = await K.sealChain(db);
  verdict(s.sealed === 3 && s.tip && s.tip.length === 64, 'sealChain seals all 3 ops (real code, crypto.subtle)', 'sealed=' + s.sealed);

  var v = await K.verifyChain(db);
  verdict(v.ok && v.len === 3, 'verifyChain → OK on clean sealed log', 'ok=' + v.ok + ' len=' + v.len);

  // tamper the last op's parameters
  db.run("UPDATE kernel_ops SET parameters='{\"item\":\"cola\",\"qty\":9999}' WHERE id=3");
  var v2 = await K.verifyChain(db);
  verdict(!v2.ok && v2.brokeAt === 3 && v2.why === 'payload altered', 'verifyChain detects tamper at exactly op 3', 'brokeAt=' + v2.brokeAt + ' why=' + v2.why);

  console.log('\n§KRN_TEST ' + (fails ? 'FAIL — ' + fails + ' red' : 'PASS — production kernel_ops.js seals + verifies + detects tamper (W-CHAIN live)'));
  process.exit(fails ? 1 : 0);
})();
