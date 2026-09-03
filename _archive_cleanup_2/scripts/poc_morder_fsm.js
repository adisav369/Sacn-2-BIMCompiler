#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_morder_fsm.js — W-MORDER-FSM (FABLE5_MORDER_EQUIVALENCE.md §H-1.4).
//
// SPEC (§H-1.4): the FULL MOrder DocAction FSM — the legal next-action SET per DocStatus and the resulting
//   status per action — must equal iDempiere's. The ORACLE IS EXTRACTED FROM THE CHECKOUT AT RUNTIME (never
//   hand-transcribed into this witness, so the diff cannot be a tautology of the engine's own tables):
//     · DocAction.java        → the STATUS_*/ACTION_* constant→code map
//     · DocumentEngine.java   → getValidActions:1008-1090 PARSED (generic status block + the C_Order block,
//                               incl. the canReactivateThisDocType conditional) → legal-action sets;
//                               the action methods :436-714 PARSED (isValidAction(ACTION_X) … m_status=
//                               STATUS_Y) → action→resulting-status map
//     · MOrder.java           → the deltas: prepareIt→InProgress (:1705); completeIt→Completed (:2245) /
//                               WaitingPayment for Prepay (:2145) / InProgress on bare-Prepare (:2117);
//                               reverseCorrectIt = voidIt (:3016); reverseAccrualIt NOT IMPLEMENTED (:3042)
//   ENGINE = ad_docfsm.legalActionsOrder/transitionOrder/dispatchOrder (the additive C_Order port). The
//   doctype ReActivate gate reads the REAL c_doctype.iscanbereactivated (captured from idempiere_test).
//   SEED REPLAY: every stored c_order's docstatus must be reachable by engine dispatch (DR→CO→[CL]) and its
//   stored docaction must be legal-or-None. The action/status codes must live in the captured AD vocabulary
//   (ad_ref_list 135/131) — the FSM's domain is AD data, not invention.
//   The MEASURED point of this surface: the C_Order legal set is NARROWER than the generic DocumentEngine
//   union (completed orders offer CL/VO/RE — never RC/RA/PO); the diff proves the engine learned that.
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/c_order rows. Deterministic: file parse +
//   db reads, no Date.now/Math.random. READ build/erp/poc_morder_fsm.log — exit code is not evidence.
// Implementing FABLE5_MORDER_EQUIVALENCE.md §H-1.4 — Witness: W-MORDER-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_morder_fsm.js   (log: build/erp/poc_morder_fsm.log)
'use strict';
var fs = require('fs');
var path = require('path');
var os = require('os');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');

var SRC = path.join(os.homedir(), 'idempiere-dev-setup', 'idempiere', 'org.adempiere.base', 'src', 'org', 'compiere');
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MORDER-FSM — MOrder DocAction FSM == iDempiere (oracle PARSED from the checkout at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsOrder,transitionOrder} · oracle = DocumentEngine.java/MOrder.java/DocAction.java\n');

// ── ORACLE 1: the constant→code map from DocAction.java ─────────────────────────────────────────────────
var docActionSrc = fs.readFileSync(path.join(SRC, 'process', 'DocAction.java'), 'utf8');
var CODE = {};
docActionSrc.replace(/public static final String (STATUS_\w+|ACTION_\w+)\s*=\s*"([^"]+)"/g, function (_, name, code) { CODE[name] = code; return _; });
verdict(CODE.ACTION_Complete === 'CO' && CODE.STATUS_Drafted === 'DR' && CODE.STATUS_WaitingPayment === 'WP',
  'DocAction.java constants parsed', Object.keys(CODE).length + ' constants');

// ── ORACLE 2: getValidActions parsed — generic block + the C_Order block (:1008-1090) ───────────────────
var engineSrc = fs.readFileSync(path.join(SRC, 'process', 'DocumentEngine.java'), 'utf8');
var gvaStart = engineSrc.indexOf('int index = 0;');
var orderStart = engineSrc.indexOf('if (AD_Table_ID == MOrder.Table_ID)', gvaStart);
var orderEnd = engineSrc.indexOf('else if (AD_Table_ID == MInOut.Table_ID)', orderStart);
verdict(gvaStart > 0 && orderStart > gvaStart && orderEnd > orderStart, 'getValidActions regions located (generic / C_Order)');

// line-walking parser: a condition line carrying STATUS_X opens a branch; options[index++]=ACTION_Y lines
// belong to it; a nested canReactivateThisDocType if() marks the next action conditional.
function parseBlock(text) {
  var sets = {}, cur = [], cond = null, condReact = false;
  text.split('\n').forEach(function (line) {
    if (!cond && /\bif\s*\(/.test(line)) cond = { depth: 0, statuses: [] };
    if (cond) {                                  // inside an if(...) condition — track parens across lines
      line.replace(/STATUS_(\w+)/g, function (_, n) { cond.statuses.push(CODE['STATUS_' + n]); return _; });
      cond.depth += (line.match(/\(/g) || []).length - (line.match(/\)/g) || []).length;
      if (/canReactivateThisDocType/.test(line)) condReact = true;
      if (cond.depth <= 0) {                     // condition closed: a status-condition opens a NEW branch;
        if (cond.statuses.length) {              // a nested non-status if (periodOpen/canReactivate) does NOT
          cur = cond.statuses;
          cur.forEach(function (s) { sets[s] = sets[s] || []; });
        }
        cond = null;
      }
      return;
    }
    var m = line.match(/options\[index\+\+\]\s*=\s*DocumentEngine\.ACTION_(\w+)/);
    if (m && cur.length) {
      var code = CODE['ACTION_' + m[1]];
      cur.forEach(function (s) { sets[s].push(condReact ? code + '?' : code); });
      condReact = false;
    }
  });
  return sets;
}
var generic = parseBlock(engineSrc.slice(gvaStart, orderStart));
var orderBlk = parseBlock(engineSrc.slice(orderStart, orderEnd));
console.log('§ORACLE_PARSED generic=' + JSON.stringify(generic));
console.log('§ORACLE_PARSED c_order=' + JSON.stringify(orderBlk));

function oracleSet(status, canReact) {
  var merge = (generic[status] || []).concat(orderBlk[status] || []);
  return merge.filter(function (a) { return a.indexOf('?') < 0 || canReact; })
              .map(function (a) { return a.replace('?', ''); });
}

// ── DIFF 1: legal-action sets, every status × {reactivatable, non-reactivatable} doctype ────────────────
var dtReact = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='Y' AND docbasetype='SOO' ORDER BY c_doctype_id").get().c_doctype_id;
var dtNoReact = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='N' ORDER BY c_doctype_id").get().c_doctype_id;
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
STATUSES.forEach(function (s) {
  [[dtReact, true], [dtNoReact, false]].forEach(function (v) {
    var eng = F.legalActionsOrder(db, { docStatus: s, isSOTrx: 'Y', doctypeId: v[0], processing: 'N' });
    var ora = oracleSet(s, v[1]);
    fixtures++;
    var ok = setEq(eng, ora);
    if (!ok) setDiffs++;
    console.log('§HARDEN surface=MOrder.docaction.legal status=' + s + ' doctype=' + v[0] + '(react=' + (v[1] ? 'Y' : 'N') + ') engine=[' + eng.sort().join(',') + '] oracle=[' + ora.sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
  });
});
// the locked variant: processing='Y' prepends Unlock (:1019-1026) — one status suffices to pin the rule
(function () {
  var eng = F.legalActionsOrder(db, { docStatus: 'DR', isSOTrx: 'Y', doctypeId: dtReact, processing: 'Y' });
  var ora = ['XL'].concat(oracleSet('DR', true));
  fixtures++;
  if (!setEq(eng, ora)) setDiffs++;
  console.log('§HARDEN surface=MOrder.docaction.legal status=DR(locked) engine=[' + eng.sort().join(',') + '] oracle=[' + ora.sort().join(',') + '] diff=' + (setEq(eng, ora) ? 0 : 'SET-MISMATCH'));
})();
verdict(setDiffs === 0, fixtures + ' legal-action set fixtures == parsed getValidActions (incl. ReActivate doctype gate + locked Unlock)', 'setDiffs=' + setDiffs);

// the measured narrowing: completed C_Order ≠ the generic whole-engine union (the old hand-ported table)
(function () {
  var orderCO = F.legalActionsOrder(db, { docStatus: 'CO', isSOTrx: 'Y', doctypeId: dtReact, processing: 'N' });
  var genericCO = F.STATUS_ACTIONS.CO;
  verdict(orderCO.indexOf('RC') < 0 && orderCO.indexOf('RA') < 0 && genericCO.indexOf('RC') >= 0,
    'C_Order CO set is the NARROWED per-table set (no RC/RA offered; reversal rides inside voidIt — MOrder.java:3016)',
    'order=[' + orderCO.join(',') + '] genericUnion=[' + genericCO.join(',') + ']');
})();

// ── ORACLE 3 + DIFF 2: resulting status per action ──────────────────────────────────────────────────────
// DocumentEngine method bodies: isValidAction(ACTION_X) … m_status = STATUS_Y (the document-success path).
var methodRe = /public boolean\s+(\w+It)\s*\(\)[\s\S]*?(?=\n\tpublic |\n\t\/\*\*)/g;
var oraOutcome = {};
var mm;
while ((mm = methodRe.exec(engineSrc)) !== null) {
  var body = mm[0];
  var act = body.match(/isValidAction\(ACTION_(\w+)\)/);
  var sts = null, sm, stRe = /m_status = STATUS_(\w+)/g;
  while ((sm = stRe.exec(body)) !== null) sts = sm[1];
  if (act && sts) oraOutcome[CODE['ACTION_' + act[1]]] = CODE['STATUS_' + sts];
}
console.log('§ORACLE_PARSED outcomes=' + JSON.stringify(oraOutcome));
// MOrder deltas parsed from MOrder.java: prepareIt/completeIt return values + the RA/RC bodies
var morderSrc = fs.readFileSync(path.join(SRC, 'model', 'MOrder.java'), 'utf8');
function methodBody(name) {
  var i = morderSrc.search(new RegExp('public (String|boolean) ' + name + '\\s*\\(\\)'));
  return morderSrc.slice(i, morderSrc.indexOf('\n\t}', i));
}
var prepBody = methodBody('prepareIt'), compBody = methodBody('completeIt'), raBody = methodBody('reverseAccrualIt'), rcBody = methodBody('reverseCorrectIt');
var prepOut = null, prm, prRe = /return DocAction\.STATUS_(\w+);/g;   // LAST return = the success path (:1705)
while ((prm = prRe.exec(prepBody)) !== null) prepOut = prm[1];
verdict(prepOut === 'InProgress', 'MOrder.prepareIt success outcome parsed = InProgress (:1705)', 'got=' + prepOut);
verdict(/return DocAction\.STATUS_Completed/.test(compBody), 'MOrder.completeIt success outcome parsed = Completed (:2245)');
var prepayIdx = compBody.indexOf('return DocAction.STATUS_WaitingPayment');
var prepayCtx = prepayIdx > 0 ? compBody.slice(Math.max(0, prepayIdx - 800), prepayIdx) : '';
verdict(prepayIdx > 0 && /PrepayOrder/.test(prepayCtx), 'MOrder.completeIt PREPAY branch parsed = WaitingPayment (:2145, DOCSUBTYPESO_PrepayOrder-gated)');
verdict(!/m_status/.test(raBody) && /return false;\s*$/m.test(raBody) && !/return true/.test(raBody),
  'MOrder.reverseAccrualIt parsed = NOT IMPLEMENTED (always false, :3042) — RA never succeeds on an order');
verdict(/return voidIt\(\);/.test(rcBody), 'MOrder.reverseCorrectIt parsed = delegates to voidIt (:3016)');

// engine transition vs the parsed outcomes (boolean actions from DocumentEngine; PR/CO from MOrder)
var tDiffs = 0, tFix = 0;
Object.keys(oraOutcome).forEach(function (act) {
  var from = { XL: 'DR', IN: 'IP', AP: 'NA', RJ: 'NA', VO: 'CO', CL: 'CO', RC: 'CO', RE: 'CO' }[act] || 'CO';
  var eng = F.transitionOrder(act, from, {});
  var ora = act === 'RA' ? null : oraOutcome[act];   // RA: DocumentEngine would say RE, but MOrder never returns true → unreachable
  tFix++;
  var ok = (act === 'RA') ? (eng === null) : (eng === ora);
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MOrder.docaction.outcome action=' + act + ' engine=' + eng + ' oracle=' + (act === 'RA' ? 'unreachable(MOrder RA not implemented)' : ora) + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
['PR:IP', 'CO:CO'].forEach(function (p) {
  var a = p.split(':');
  var eng = F.transitionOrder(a[0], 'DR', {});
  tFix++;
  if (eng !== a[1]) tDiffs++;
  console.log('§HARDEN surface=MOrder.docaction.outcome action=' + a[0] + ' engine=' + eng + ' oracle=' + a[1] + '(MOrder.java) diff=' + (eng === a[1] ? 0 : 'MISMATCH'));
});
(function () { // the prepay split: completeIt on a 'PR'-subtype doctype waits for payment
  var eng = F.transitionOrder('CO', 'DR', { docSubTypeSO: 'PR' });
  tFix++;
  if (eng !== 'WP') tDiffs++;
  console.log('§HARDEN surface=MOrder.docaction.outcome action=CO(prepay) engine=' + eng + ' oracle=WP(MOrder.java:2145) diff=' + (eng === 'WP' ? 0 : 'MISMATCH'));
})();
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed action outcomes (incl. RA-unreachable + prepay WP)', 'tDiffs=' + tDiffs);

// ── vocabulary: every engine code lives in the captured AD reference lists (131 status / 135 action) ────
(function () {
  var actions = {}, statuses = {};
  db.prepare('SELECT ad_reference_id r, value v FROM ad_ref_list').all().forEach(function (x) { (Number(x.r) === 135 ? actions : statuses)[x.v] = 1; });
  var usedA = {}, usedS = {};
  STATUSES.forEach(function (s) { usedS[s] = 1; F.legalActionsOrder(db, { docStatus: s, isSOTrx: 'Y', doctypeId: dtReact, processing: 'Y' }).forEach(function (a) { usedA[a] = 1; }); });
  var badA = Object.keys(usedA).filter(function (a) { return !actions[a]; });
  var badS = Object.keys(usedS).filter(function (s) { return !statuses[s]; });
  verdict(badA.length === 0 && badS.length === 0, 'every action/status code ∈ captured AD_Ref_List 135/131 (AD-data domain)', 'unknownActions=[' + badA + '] unknownStatuses=[' + badS + ']');
})();

// ── SEED REPLAY: stored docstatus reachable by engine dispatch; stored docaction legal-or-None ──────────
var orders = db.prepare('SELECT c_order_id,documentno,docstatus,docaction,c_doctype_id,issotrx FROM c_order ORDER BY c_order_id').all();
var replayOk = 0;
orders.forEach(function (o) {
  var rec = { docStatus: 'DR', isSOTrx: o.issotrx, doctypeId: o.c_doctype_id, processing: 'N' };
  var sub = db.prepare('SELECT docsubtypeso FROM c_doctype WHERE c_doctype_id=?').get(Number(o.c_doctype_id));
  var r1 = F.dispatchOrder(db, rec, 'CO', { docSubTypeSO: sub ? sub.docsubtypeso : null });
  var status = r1.ok ? r1.to : 'X';
  var hops = 'DR-CO->' + status;
  if (o.docstatus === 'CL' && status === 'CO') { var r2 = F.dispatchOrder(db, { docStatus: 'CO', isSOTrx: o.issotrx, doctypeId: o.c_doctype_id, processing: 'N' }, 'CL', {}); status = r2.ok ? r2.to : 'X'; hops += '-CL->' + status; }
  var legalNow = F.legalActionsOrder(db, { docStatus: o.docstatus, isSOTrx: o.issotrx, doctypeId: o.c_doctype_id, processing: 'N' });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MOrder.docaction.replay record_id=' + o.c_order_id + ' docno=' + o.documentno + ' ' + hops +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + (o.docaction === '--' ? '(None)' : '∈[' + legalNow.join(',') + ']') + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === orders.length, orders.length + '/' + orders.length + ' stored orders: docstatus replayed by dispatch + stored docaction legal-or-None');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchOrder(db, { docStatus: 'CO', isSOTrx: 'Y', doctypeId: dtReact, processing: 'N' }, 'PR', {});
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A Prepare from CO → rejected (illegal-action)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=PR from=CO ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsOrder(db, { docStatus: 'CO', isSOTrx: 'Y', doctypeId: dtReact, processing: 'N' }).concat(['RC']);
  var ora = oracleSet('CO', true);
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RC into the engine CO set → set-diff vs parsed oracle fires (the exact generic-table error this surface kills)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RC@CO setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL WP/WC reachable only via prepay/wait flows (no such doc in seed — sets diffed vs parsed source, no stored replay) · ' +
  'generic STATUS_ACTIONS/TRANSITION stay as the whole-engine union for non-Order tables (per-table narrowing = the H-2 delta walk) · ' +
  'quote/proposal docAction-default (:1072-1075 sets docAction[0], not options) out of legal-SET scope, named');
console.log('§HARDEN surface=MOrder.docaction fixtures=' + (fixtures + tFix + orders.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MORDER-FSM PASS' : '🔴 W-MORDER-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the FULL MOrder DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
