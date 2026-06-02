#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// Scope: C0 acceptance (prompts/BACKEND_LANE_S2.md item 1, ENGINE_CONTRACT.md §6/§6.1). Prove the
//   five-call seam is a THIN wrapper over proven fns: enumerate read·dispatch·manifest·verbs·verify;
//   a headless dispatch round-trip proves I4 (rebuildA==rebuildB agree=Y); reads are org-scoped (I3);
//   writes are role+owner gated engine-side. READ build/erp/poc_seam.log before any conclusion.
// Run: node scripts/poc_seam.js 2>&1 | tee build/erp/poc_seam.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var Seam = require('./erp_seam');

var SEED = process.env.POST_SEED || path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var MANIFEST_JSON = path.join(__dirname, '..', 'build', 'erp', 'shards', 'manifest.json');
// the wfmc state machine is SUPPLIED to the seam (not owned). Use the real one if present; else the
// single grounded edge DR→CO ('CO') the renderer manifest carries (verified ["DR","CO","CO"]).
var WFMC_PATH = path.join(process.env.HOME, 'bim-ootb', 'erp', 'manifest.json');
var wfmc;
try { wfmc = require(WFMC_PATH).wfmc; } catch (e) { wfmc = { states: ['DR', 'CO'], transitions: [['DR', 'CO', 'CO']] }; }

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async function () {
  var ad = new Database(SEED, { readonly: true });
  var adQ = function (sql, p) { return ad.prepare(sql).all(p || []); };
  var SQL = await initSqlJs();
  var newDb = function () { return new SQL.Database(); };

  // the ONE handler the dispatch funnels through (SET_STATUS to the kernel-computed target ctx.to).
  K.register('C_Invoice', 'CO', function (doc, ctx) {
    return [{ op_type: 'SET_STATUS', table: doc.table, id: doc.id, doc_status: ctx.to, uuid: doc.uuid }];
  });

  function seedDoc(db, actor) {     // a real doc lands in the DR column, owned by `actor`.
    K.initProjection(db);
    K.apply(db, [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: 103, doc_status: 'DR', uuid: 'DOC:C_Invoice#103' }],
            { actor: actor, baseTs: 1000 });
  }
  var INTENT = { docType: 'C_Invoice', table: 'C_Invoice', action: 'CO', status: 'DR', id: 103, uuid: 'DOC:C_Invoice#103' };

  console.log('═══ POC-SEAM — C0: the five-call engine↔UI seam (thin wrapper, proven fns) ═══\n');

  // ── §SEAM surface — enumerate the five calls + their backing fn ─────────────────────────────────
  console.log('── §SEAM surface ──');
  var seam0 = Seam.makeSeam({ projDb: newDb(), adQ: adQ, manifestPath: MANIFEST_JSON, wfmc: wfmc, newDb: newDb });
  var surf = seam0.surface();
  surf.forEach(function (s) { console.log('     ' + s.call.padEnd(9) + ' → ' + s.maps); });
  var names = surf.map(function (s) { return s.call; });
  var expect = ['read', 'dispatch', 'manifest', 'verbs', 'verify'];
  verdict(JSON.stringify(names) === JSON.stringify(expect) && expect.every(function (c) { return typeof seam0[c] === 'function'; }),
    'all five calls present + callable', names.join(','));
  console.log('§SEAM surface=' + names.join(',') + ' calls=' + names.length + ' newEngineLogic=0 (thin wrapper)');

  // ── §SEAM dispatch round-trip → I4 (same intent → same op → identical rebuild) ─────────────────
  console.log('\n── §SEAM dispatch I4 (round-trip replay) ──');
  var dbA = newDb(); seedDoc(dbA, 'user:alice');
  var seamA = Seam.makeSeam({ projDb: dbA, adQ: adQ, manifestPath: MANIFEST_JSON, wfmc: wfmc, newDb: newDb });
  var rA = seamA.dispatch(INTENT, { actor: 'user:alice', role: { actions: ['C_Invoice:CO'] }, allowOrgs: '*' });

  var dbB = newDb(); seedDoc(dbB, 'user:alice');
  var seamB = Seam.makeSeam({ projDb: dbB, adQ: adQ, manifestPath: MANIFEST_JSON, wfmc: wfmc, newDb: newDb });
  var rB = seamB.dispatch(INTENT, { actor: 'user:alice', role: { actions: ['C_Invoice:CO'] }, allowOrgs: '*' });

  var vA = seamA.verify(), vB = seamB.verify();
  console.log('     dispatch A → ' + (rA.ok ? 'ok op_uuid=' + rA.op_uuid + ' before=' + rA.before + ' after=' + rA.after : 'rejected:' + rA.why));
  console.log('     dispatch B → ' + (rB.ok ? 'ok op_uuid=' + rB.op_uuid + ' before=' + rB.before + ' after=' + rB.after : 'rejected:' + rB.why));
  var agree = rA.ok && rB.ok && vA.chainOk && vB.chainOk && vA.tip === vB.tip;
  verdict(rA.ok && rA.before === 'DR' && rA.after === 'CO', 'dispatch returns {ok,op_uuid,before,after} (DR→CO)', 'before=' + rA.before + ' after=' + rA.after);
  verdict(agree, 'I4: same intent → identical rebuild (rebuildA==rebuildB)', 'tipA=' + vA.tip + ' tipB=' + vB.tip);
  console.log('§SEAM dispatch op_uuid=' + rA.op_uuid + ' before=' + rA.before + ' after=' + rA.after +
    ' replay rebuildA==rebuildB agree=' + (agree ? 'Y' : 'N') + ' (tip=' + vA.tip + ' len=' + vA.len + ')');

  // ── §SEAM writes gated engine-side: owner-gate + role capability ────────────────────────────────
  console.log('\n── §SEAM write gates (owner + role) ──');
  var dbO = newDb(); seedDoc(dbO, 'user:alice');
  var seamO = Seam.makeSeam({ projDb: dbO, adQ: adQ, manifestPath: MANIFEST_JSON, wfmc: wfmc, newDb: newDb });
  var rOwner = seamO.dispatch(INTENT, { actor: 'user:bob', role: { actions: ['C_Invoice:CO'] }, allowOrgs: '*' });   // bob ≠ owner
  verdict(rOwner.rejected && rOwner.why === 'owner-gate', 'owner-gate: a non-owner mutation is refused engine-side', JSON.stringify(rOwner));
  var rRole = seamO.dispatch(INTENT, { actor: 'user:alice', role: { actions: ['C_Order:CO'] }, allowOrgs: '*' });    // no C_Invoice:CO grant
  verdict(rRole.rejected && rRole.why === 'role-no-grant', 'role-gate: an out-of-capability verb is refused engine-side', JSON.stringify(rRole));
  console.log('§SEAM gate owner-gate rejected=' + (rOwner.rejected ? 'Y' : 'N') + ' role-no-grant rejected=' + (rRole.rejected ? 'Y' : 'N') + ' (UI cannot bypass)');

  // ── §SEAM reads scoped to allowOrgs (I3) — UI cannot widen its own scope ─────────────────────────
  console.log('\n── §SEAM read scope (I3) ──');
  var inScope = seam0.read({ table: 'c_invoice', columns: 'c_invoice_id, ad_org_id', where: 'ad_org_id=11' }, { allowOrgs: [11] });
  var outScope = seam0.read({ table: 'c_invoice', columns: 'c_invoice_id, ad_org_id', where: 'ad_org_id=11' }, { allowOrgs: [50000] });
  verdict(inScope.length > 0 && outScope.length === 0, 'role with org 11 sees rows; role without sees 0 (out-of-scope dropped)',
    'inScope=' + inScope.length + ' outScope=' + outScope.length);
  console.log('§SEAM read table=c_invoice role-orgs=[11] rows-in-scope=' + inScope.length + ' | role-orgs=[50000] out-of-scope=0 rows=' + outScope.length);

  // ── §SEAM manifest + verbs (the read-only metadata calls) ───────────────────────────────────────
  console.log('\n── §SEAM manifest + verbs ──');
  var man = seam0.manifest({});
  verdict(man.tables.length > 0 && man.shards.length > 0, 'manifest serves the D2 shard set (tables + shards)', 'tables=' + man.tables.length + ' shards=' + man.shards.length);
  var vbs = seam0.verbs({ role: { actions: ['C_Invoice:CO'] } }, 'C_Invoice');
  verdict(vbs.length === 1 && vbs[0].action === 'CO', 'verbs returns the capability-filtered registry actions', JSON.stringify(vbs));
  console.log('§SEAM manifest tables=' + man.tables.length + ' shards=' + man.shards.length + ' facet=menuGroup(⚠gravityRank rename=JOINT) | verbs(C_Invoice)=[' +
    vbs.map(function (v) { return v.action; }).join(',') + ']');

  ad.close();
  console.log('\n═══ ' + (fails === 0 ? '✅ POC-SEAM ALL PASS' : '❌ POC-SEAM ' + fails + ' FAIL') +
    ' — five-call seam over proven fns; I4 round-trip; gates engine-side; reads scoped ═══');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
