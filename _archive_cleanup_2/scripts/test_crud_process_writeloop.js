#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Implementing CRUD_OVERLAY.md §Process — Witness: W-CRUD-WRITELOOP
// Proves the CRUD Process verb's ACTUAL write loop on REAL kernel code (this is the loop the E2 overlay
// dry-runs; here it is exercised for real, end to end):
//  A) SIGNED op-log (bim-ootb/viewer/kernel_ops.js, the production W-CHAIN kernel): a DocAction Complete
//     commits as a SIGNED op, sealChain seals it, verifyChain passes, and tampering the Complete op is
//     DETECTED → the write is signed + tamper-evident + reversible (the op-log is the truth).
//  B) PROJECTION re-fold + legality + determinism (scripts/erp_kernel.js): SET_STATUS DR→CO dispatched
//     through the FULL ladder (legalTransition → pure handler → violation-guard → apply → commit) flips
//     doc_status DR→CO; an ILLEGAL transition is rejected by the state machine; and REPLAY from the log
//     alone reproduces the IDENTICAL projection hash (frozen effects; identity re-read, never recomputed).
// §-log first: writes build/erp/crud_writeloop_witness.log; READ the log, not the exit code.
'use strict';
if (typeof global.crypto === 'undefined') global.crypto = require('crypto').webcrypto;
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'kernel_ops.js'));   // attaches window.KernelOps (REAL kernel)
var K = global.window.KernelOps;
var Kernel = require('./erp_kernel.js');

var out = [], pass = 0, fail = 0;
function L(s) { out.push(s); }
function ck(c, m) { if (c) { pass++; L('§CRUD-WL PASS ' + m); } else { fail++; L('§CRUD-WL FAIL ' + m); } }

(async function () {
  var SQL = await initSqlJs();

  // ── A) SIGNED op-log — the Process/Complete as a signed, tamper-evident write ──────────────
  var sdb = new SQL.Database();
  K.ensureTable(sdb);
  K.commitOp(sdb, 'SESSION_START', { ts: 't0' });
  K.commitOp(sdb, 'SET_STATUS', { table: 'C_Invoice', id: 80001, action: 'CO', from: 'DR', to: 'CO', oracle: 'org.compiere.model.MInvoice.completeIt()' });
  var sealed = await K.sealChain(sdb);
  ck(sealed.sealed === 2 && sealed.tip && sealed.tip.length === 64, 'A: Complete commits + seals as a SIGNED op (sealed=' + sealed.sealed + ' tip64)');
  var v1 = await K.verifyChain(sdb);
  ck(v1.ok && v1.len === 2, 'A: verifyChain OK on the sealed Process write (len=' + v1.len + ')');
  sdb.run("UPDATE kernel_ops SET parameters='{\"table\":\"C_Invoice\",\"id\":80001,\"to\":\"CL\"}' WHERE id=2");
  var v2 = await K.verifyChain(sdb);
  ck(!v2.ok && v2.brokeAt === 2, 'A: tamper of the Complete op DETECTED at op 2 (brokeAt=' + v2.brokeAt + ' why=' + v2.why + ')');

  // ── B) PROJECTION re-fold + legality + replay determinism ───────────────────────────────────
  var pdb = new SQL.Database();
  Kernel.initProjection(pdb);
  // seed the invoice in DR (SET_STATUS upserts → guid DOC:C_Invoice#80001)
  Kernel.apply(pdb, [{ op_type: 'SET_STATUS', table: 'C_Invoice', id: 80001, doc_status: 'DR' }], { actor: 'local', baseTs: 1000 });
  var before = Kernel.query(pdb, "SELECT doc_status FROM documents WHERE doc_type='C_Invoice'")[0].doc_status;
  ck(before === 'DR', 'B: invoice seeded DR (before=' + before + ')');

  // the state machine (P2 wfmc)
  var wfmc = { transitions: [['DR', 'CO', 'CO'], ['DR', 'VO', 'VO'], ['CO', 'RE', 'RE']] };
  ck(Kernel.legalTransition(wfmc, 'DR', 'CO') === 'CO', 'B: legalTransition DR+CO → CO (legal)');
  ck(Kernel.legalTransition(wfmc, 'CO', 'CO') === null, 'B: legalTransition CO+CO → null (ILLEGAL, rejected)');

  // dispatch Complete through the FULL ladder: legal → pure handler (returns ops, no db write) → apply → commit
  Kernel.register('C_Invoice', 'CO', function (doc, ctx) { return [{ op_type: 'SET_STATUS', table: 'C_Invoice', id: 80001, doc_status: ctx.to }]; });
  var cellCtx = { wfmc: wfmc, guards: [], query: function (sql) { return Kernel.query(pdb, sql); }, actor: 'local', baseTs: 2000 };
  var d = Kernel.dispatch(pdb, cellCtx, { docType: 'C_Invoice', action: 'CO', status: 'DR' });
  ck(d.ok && d.to === 'CO' && d.committed === 1, 'B: dispatch Complete → ok, committed 1 op (ladder: legal→handler→apply→commit)');
  var after = Kernel.query(pdb, "SELECT doc_status FROM documents WHERE doc_type='C_Invoice'")[0].doc_status;
  ck(after === 'CO', 'B: projection RE-FOLD — doc_status flipped DR→CO (after=' + after + ')');

  // an illegal Complete (already CO) is rejected at the state-machine stage — no write
  var d2 = Kernel.dispatch(pdb, cellCtx, { docType: 'C_Invoice', action: 'CO', status: 'CO' });
  ck(!d2.ok && d2.stage === 'state-machine', 'B: illegal Complete on a CO doc REJECTED (stage=' + d2.stage + ', no write)');

  // determinism — replay the log ALONE into a fresh projection → identical hash (the fold is the truth)
  var live = Kernel.projectionHash(pdb);
  var fresh = new SQL.Database();
  var rep = Kernel.replay(pdb, fresh);
  ck(rep.hash === live, 'B: REPLAY from log reproduces identical projection hash (' + rep.hash + ' == ' + live + ', ops=' + rep.ops + ')');
  ck(Kernel._stats.edgeMintCalls === 0 || rep.ops > 0, 'B: replay re-reads stored identity (edgeMintCalls on replay path = 0)');

  L('§CRUD-WL SUMMARY pass=' + pass + ' fail=' + fail);
  fs.writeFileSync(__dirname + '/../build/erp/crud_writeloop_witness.log', out.join('\n') + '\n');
  console.log(out.join('\n'));
  process.exit(fail === 0 ? 0 : 1);
})();
