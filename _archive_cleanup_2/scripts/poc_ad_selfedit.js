#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — REFLEXIVE AD SELF-EDIT engine leg (FRONTEND_LANE_MASTER.md §PENDING WITNESS).
//   Read the log after every run.
// poc_ad_selfedit.js — W-AD-SELFEDIT: editing an AD_Field row (the DICTIONARY) re-folds the FORM STRUCTURE
//   with NO codegen / restart — the form is rebuilt by re-reading AD, not recompiled. This is the ENGINE half
//   of "modify the model live, like iDempiere": the renderer derives the form's displayed-field set from
//   AD_Field, and a signed edit to AD_Field.IsDisplayed changes that set on the very next fold.
//
//   SCOPE BOUNDARY (honest): this proves the engine-level reflexive rebuild — the AD projection the renderer
//   reads changes immediately after a signed dictionary edit. The LIVE-BROWSER leg (the DOM actually repainting
//   the form on the spot) is browser-gated and remains owed as W-AD-SELFEDIT-LIVE (a live-driven DOM probe).
//   What is NOT browser-gated — and is proven here — is that "rebuild" = re-fold, not recompile.
//
// ISSUES IT PROVES (named):
//   §SELFEDIT-BASE  — a tab's displayed-field set is folded from AD_Field (IsDisplayed='Y') — N fields before.
//   §SELFEDIT-HIDE  — a signed CRUD_UPDATE setting AD_Field.IsDisplayed Y→N on one field re-folds the set to
//                     N-1 fields (the hidden field drops); verifyChain ok.
//   §SELFEDIT-SHOW  — a follow-up edit (a NEW field's IsDisplayed N→Y) re-folds the set to grow — the rebuild
//                     is bidirectional, latest-wins, purely from the dictionary.
//   §FALSIFIER      — re-read WITHOUT the edits = the original set (the dictionary edit is load-bearing).
//
// NON-INVENT: tab + fields are REAL ad_seed.db rows; deterministic ids + baseTs.
// Implementing FRONTEND_LANE_MASTER.md §PENDING WITNESS (REFLEXIVE AD SELF-EDIT, engine leg) — Witness: W-AD-SELFEDIT
// Run: bash build/erp/run_witness.sh scripts/poc_ad_selfedit.js — then READ the log.
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var CRUD = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));

var SEED = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var TAB = 100;   // "Table" tab — 26 displayed fields (real ad_seed row)

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

// the renderer's form-field source: AD_Field rows for the tab → [{AD_Field_ID, Name, IsDisplayed}]
function fieldRows(db) {
  var r = db.exec("SELECT AD_Field_ID, Name, IsDisplayed FROM AD_Field WHERE AD_Tab_ID=" + TAB + " AND IsActive='Y' ORDER BY AD_Field_ID");
  if (!r.length) return [];
  return r[0].values.map(function (v) { return { AD_Field_ID: v[0], Name: v[1], IsDisplayed: v[2] }; });
}
// the FORM as the renderer would build it: the displayed-field set, folded through the op-log tip
function displayedSet(db, base) {
  var tip = CRUD.listTip(db, 'AD_Field', 'AD_Field_ID', base);
  return tip.rows.filter(function (r) { return r.IsDisplayed === 'Y'; }).map(function (r) { return r.AD_Field_ID; });
}

(async function () {
  var log = []; var _cl = console.log;
  console.log = function () { log.push(Array.prototype.join.call(arguments, ' ')); _cl.apply(console, arguments); };
  console.log('\n═══ W-AD-SELFEDIT — edit AD_Field → form re-folds (rebuild = re-read, not recompile) ═══\n');

  var SQL = await initSqlJs();
  if (!fs.existsSync(SEED)) { console.log('§W-AD-SELFEDIT FAIL seed missing'); process.exit(1); }
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SEED))); KO.ensureTable(db);

  var base = fieldRows(db);
  if (base.length < 3) { console.log('§W-AD-SELFEDIT FAIL tab has too few fields'); process.exit(1); }

  // ── §SELFEDIT-BASE ──
  var set0 = displayedSet(db, base);
  var N = set0.length;
  console.log('§SELFEDIT-BASE tab=' + TAB + ' displayedFields=' + N);
  verdict(N >= 3, '§SELFEDIT-BASE baseline displayed set folded from AD_Field', 'N=' + N);

  // pick a currently-DISPLAYED field to hide, and a currently-HIDDEN field to show
  var toHide = base.filter(function (r) { return r.IsDisplayed === 'Y'; })[0];
  var toShow = base.filter(function (r) { return r.IsDisplayed !== 'Y'; })[0];
  console.log('§SELFEDIT pick hide=' + (toHide && toHide.AD_Field_ID) + ' ("' + (toHide && toHide.Name) +
              '") show=' + (toShow && toShow.AD_Field_ID) + ' ("' + (toShow && toShow.Name) + '")');

  // ── §SELFEDIT-HIDE: signed edit IsDisplayed Y→N → set shrinks to N-1 ──
  var hideOp = {
    op_type: 'CRUD_UPDATE', op_uuid: 'ad-selfedit-hide',
    params: { table: 'AD_Field', id: toHide.AD_Field_ID, changes: { IsDisplayed: { old: 'Y', new: 'N' } }, actor: 100 }
  };
  var resH = await KO.commitGroup(db, [hideOp], { gid: 'selfedit-hide', baseTs: 1718200000000 });
  verdict(resH.committed, 'signed commitGroup (hide field)', 'gid=' + resH.gid);
  var chainH = await KO.verifyChain(db);
  verdict(chainH.ok, 'verifyChain ok after hide', 'len=' + chainH.len);

  var set1 = displayedSet(db, base);
  console.log('§SELFEDIT-HIDE displayedFields=' + set1.length + ' (was ' + N + ')');
  verdict(set1.length === N - 1 && set1.indexOf(toHide.AD_Field_ID) < 0,
    '§SELFEDIT-HIDE form re-folds WITHOUT the hidden field', 'N-1=' + (N - 1) + ' got=' + set1.length);

  // ── §SELFEDIT-SHOW: a NEW edit IsDisplayed N→Y → set grows again (bidirectional, latest-wins) ──
  var hasHidden = !!toShow;
  if (hasHidden) {
    var showOp = {
      op_type: 'CRUD_UPDATE', op_uuid: 'ad-selfedit-show',
      params: { table: 'AD_Field', id: toShow.AD_Field_ID, changes: { IsDisplayed: { old: 'N', new: 'Y' } }, actor: 100 }
    };
    var resS = await KO.commitGroup(db, [showOp], { gid: 'selfedit-show', baseTs: 1718200001000 });
    verdict(resS.committed, 'signed commitGroup (show field)', 'gid=' + resS.gid);
    var set2 = displayedSet(db, base);
    console.log('§SELFEDIT-SHOW displayedFields=' + set2.length);
    verdict(set2.length === N && set2.indexOf(toShow.AD_Field_ID) >= 0,
      '§SELFEDIT-SHOW form re-folds WITH the newly-shown field (net ' + N + ')', 'got=' + set2.length);
  } else {
    console.log('§SELFEDIT-SHOW skipped (tab has no hidden field to reveal)');
  }

  // ── §FALSIFIER: a fresh dictionary read WITHOUT the edits = original set ──
  var db2 = new SQL.Database(new Uint8Array(fs.readFileSync(SEED))); KO.ensureTable(db2);
  var setClean = displayedSet(db2, fieldRows(db2));
  verdict(setClean.length === N && setClean.indexOf(toHide.AD_Field_ID) >= 0,
    '§FALSIFIER clean read (no op-log) = original set — the edit is load-bearing', 'N=' + setClean.length);

  var pass = fails === 0;
  console.log('\n§W-AD-SELFEDIT ' + (pass ? 'PASS' : 'FAIL') + ' (fails=' + fails + ') — engine reflexive rebuild proven; live DOM repaint = W-AD-SELFEDIT-LIVE (browser-gated, owed)');
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_ad_selfedit.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§W-AD-SELFEDIT ERROR', e.message, e.stack); process.exit(2); });
