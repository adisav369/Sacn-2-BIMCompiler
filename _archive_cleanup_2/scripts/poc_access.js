// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_access.js — W-ACCESS witness. Opens build/erp/ad_full.db (lowercase tables), builds REAL role
// contexts from ad_role + the *_access grant rows, and gates real windows/processes/forms/records.
// Implementing ERP_COVERAGE_MATRIX.md §AD_Role/§*_Access/§AccessLevel/§AD_EntityType — Witness: W-ACCESS
// Run: node scripts/poc_access.js 2>&1 | tee build/erp/poc_access.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');                                   // db-open idiom (poc_logic_eval.js:9)
var A = require(path.join(__dirname, '..', 'build', 'erp', 'ad_access.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : ''));
}

console.log('═══ W-ACCESS — AD role/access gate (real ad_full.db grant rows) ═══\n');

var db = new Database(DB_PATH, { readonly: true });

// ── Build TWO real role contexts straight from the AD ─────────────────────────────────────────────────
var admin = A.buildRole(db, 102);   // GardenWorld Admin (userLevel ' CO')
var user  = A.buildRole(db, 103);   // GardenWorld User  (userLevel '  O', restricted)
console.log('roles: Admin(' + admin.AD_Role_ID + ') userLevel="' + admin.userLevel + '" windows=' + admin.win.size +
            ' | User(' + user.AD_Role_ID + ') userLevel="' + user.userLevel + '" windows=' + user.win.size + '\n');

// ── 1 · §ACCESS_OK — a role WITH a grant is allowed (prove the row is real) ─────────────────────────────
console.log('— §ACCESS_OK (role with a real grant row is allowed) —');
// pick a window the User actually has a grant for (first key in its map).
var userWin = user.win.keys().next().value;
var winName = db.prepare('SELECT name FROM ad_window WHERE ad_window_id=?').get(userWin);
var okGate = user.gateWindow(userWin);
var grantRow = db.prepare("SELECT isreadwrite FROM ad_window_access WHERE ad_role_id=103 AND ad_window_id=? AND isactive='Y'").get(userWin);
console.log('§ACCESS_OK window=' + userWin + '("' + (winName && winName.name) + '") role=103 allowed=' + okGate.allowed +
            ' rw=' + okGate.readWrite + ' grant_isreadwrite=' + (grantRow && grantRow.isreadwrite));
verdict(okGate.allowed === true && !!grantRow, 'User allowed a window it has a real grant for', 'window=' + userWin);

// a process the User has a grant for
var userProc = user.proc.keys().next().value;
var pName = db.prepare('SELECT name FROM ad_process WHERE ad_process_id=?').get(userProc);
var okProc = user.gateProcess(userProc);
console.log('§ACCESS_OK process=' + userProc + '("' + (pName && pName.name) + '") role=103 allowed=' + okProc.allowed + ' rw=' + okProc.readWrite);
verdict(okProc.allowed === true, 'User allowed a process it has a real grant for', 'process=' + userProc);

// ── 2 · §ACCESS_DENY — restricted role denied a window it lacks a grant for + a record outside its org ──
console.log('\n— §ACCESS_DENY (no-grant window + wrong-org record, both proven against real rows) —');
// find a window Admin has but User lacks (real delta).
var adminWins = new Set(admin.win.keys());
var deniedWin = null;
for (var k of adminWins) { if (!user.win.has(k)) { deniedWin = k; break; } }
var dName = db.prepare('SELECT name FROM ad_window WHERE ad_window_id=?').get(deniedWin);
// prove there is genuinely NO active grant row for User on this window.
var noRow = db.prepare("SELECT count(*) AS c FROM ad_window_access WHERE ad_role_id=103 AND ad_window_id=? AND isactive='Y'").get(deniedWin).c;
var denyGate = user.gateWindow(deniedWin);
console.log('§ACCESS_DENY window=' + deniedWin + '("' + (dName && dName.name) + '") role=103 reason=' + denyGate.reason +
            ' allowed=' + denyGate.allowed + ' (real active grant rows for this role+window=' + noRow + ')');
verdict(denyGate.allowed === false && denyGate.reason === 'no-grant' && noRow === 0,
        'User DENIED a window it lacks a grant for (Admin has it)', 'window=' + deniedWin);

// wrong-org record scope: User's org set excludes org 12 (Store Central) — a record there is denied.
// AccessLevel '1' (Organization) table, e.g. C_Order; User userLevel has 'O' so canView passes, scope must fail.
var userOrgs = Array.from(user.orgs).join(',');
var rec = { AD_Client_ID: 11, AD_Org_ID: 12 };       // org 12 NOT in User's {11,50000,50001,50007}
var orgGate = user.gateRecord('1', rec);
console.log('§ACCESS_DENY record AD_Org_ID=12 role=103 reason=' + orgGate.reason + ' allowed=' + orgGate.allowed +
            ' (User org access={' + userOrgs + '})');
verdict(orgGate.allowed === false && orgGate.reason === 'wrong-org', 'User DENIED a record outside its org scope (org 12)', 'orgs={' + userOrgs + '}');

// and an in-scope record (org 11 HQ) is allowed — proves the scope check is not blanket-deny.
var okRec = user.gateRecord('1', { AD_Client_ID: 11, AD_Org_ID: 11 });
console.log('§ACCESS_OK record AD_Org_ID=11 role=103 reason=' + okRec.reason + ' allowed=' + okRec.allowed);
verdict(okRec.allowed === true, 'User allowed an in-scope record (org 11 in its set)', 'reason=' + okRec.reason);

// AccessLevel '4' (System only) record — User userLevel '  O' has no 'S' -> wrong-accesslevel.
var sysGate = user.gateRecord('4', { AD_Client_ID: 11, AD_Org_ID: 11 });
console.log('§ACCESS_DENY record accesslevel=4(SystemOnly) role=103 reason=' + sysGate.reason + ' (userLevel="' + user.userLevel + '")');
verdict(sysGate.allowed === false && sysGate.reason === 'wrong-accesslevel',
        'User (no S) DENIED a System-only record (canView faithful to MRole:2433)', 'level=4');

// ── 3 · §ACCESS_COVERAGE — counts of real AD rows the gate interprets WITHOUT error ─────────────────────
console.log('\n— §ACCESS_COVERAGE (every real grant row across the role set, interpreted without error) —');
var roleIds = db.prepare('SELECT ad_role_id FROM ad_role ORDER BY ad_role_id').all().map(function (r) { return r.ad_role_id; });
var winGated = 0, procGated = 0, formGated = 0, errs = 0;
roleIds.forEach(function (rid) {
  var ctx;
  try { ctx = A.buildRole(db, rid); } catch (e) { errs++; return; }
  // re-interpret EVERY active grant row for this role through the gate; verify it agrees with the source row.
  db.prepare("SELECT ad_window_id AS id, isreadwrite AS rw FROM ad_window_access WHERE ad_role_id=? AND isactive='Y'").all(rid).forEach(function (r) {
    var g = ctx.gateWindow(r.id); if (g.allowed && g.readWrite === (r.rw === 'Y')) winGated++; else errs++;
  });
  db.prepare("SELECT ad_process_id AS id, isreadwrite AS rw FROM ad_process_access WHERE ad_role_id=? AND isactive='Y'").all(rid).forEach(function (r) {
    var g = ctx.gateProcess(r.id); if (g.allowed && g.readWrite === (r.rw === 'Y')) procGated++; else errs++;
  });
  db.prepare("SELECT ad_form_id AS id, isreadwrite AS rw FROM ad_form_access WHERE ad_role_id=? AND isactive='Y'").all(rid).forEach(function (r) {
    var g = ctx.gateForm(r.id); if (g.allowed && g.readWrite === (r.rw === 'Y')) formGated++; else errs++;
  });
});
// AccessLevel coverage: every distinct ad_table.accesslevel interpreted by canView for both roles.
var levels = db.prepare("SELECT DISTINCT accesslevel AS l FROM ad_table WHERE accesslevel IS NOT NULL").all().map(function (r) { return r.l; });
var levelErrs = 0;
levels.forEach(function (l) { try { A.canView(user.userLevel, l); A.canView(admin.userLevel, l); } catch (e) { levelErrs++; } });
var tableRows = db.prepare("SELECT count(*) AS c FROM ad_table WHERE accesslevel IS NOT NULL").get().c;
var etCount = db.prepare("SELECT count(*) AS c FROM ad_entitytype WHERE isactive='Y'").get().c;
console.log('§ACCESS_COVERAGE roles=' + roleIds.length + ' windows_gated=' + winGated + ' processes_gated=' + procGated +
            ' forms_gated=' + formGated + ' accesslevels=' + levels.length + '(' + tableRows + ' ad_table rows) entitytypes=' + etCount + ' errors=' + (errs + levelErrs));
verdict(errs === 0 && levelErrs === 0,
        'gate interprets every real grant row + accesslevel without error (gate verdict == source row)',
        winGated + ' win + ' + procGated + ' proc + ' + formGated + ' form grants; ' + levels.length + ' accesslevels');

// ── 4 · §FALSIFIER — flip the gate OFF (ignore grants) → restricted role SEES the no-grant window → wrong ─
console.log('\n— §FALSIFIER (gate OFF = ignore grants → User would SEE a window it has no grant for) —');
function gateOff() { return { allowed: true, readWrite: true, reason: 'gate-disabled' }; }   // the static/no-security engine
var falsWin = deniedWin;                                  // the same real no-grant window from §2
var withGate = user.gateWindow(falsWin);                 // gate ON  -> DENY
var withoutGate = gateOff();                             // gate OFF -> visible
console.log('§FALSIFIER window=' + falsWin + ' role=103 gate_ON.allowed=' + withGate.allowed + '(' + withGate.reason +
            ')  gate_OFF.allowed=' + withoutGate.allowed + '  load_bearing=' + (withGate.allowed !== withoutGate.allowed));
verdict(withGate.allowed === false && withoutGate.allowed === true,
        'gate is LOAD-BEARING: ON=DENY no-grant window, OFF=visible — the grant check is real, not cosmetic',
        'a static engine cannot produce the DENY');

db.close();

console.log('\n' + (fails ? '🔴 ' + fails + ' FAIL' : '═══ ALL PASS ═══'));
process.exit(fails ? 1 : 0);
