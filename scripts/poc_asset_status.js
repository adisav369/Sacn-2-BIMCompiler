#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_asset_status.js — W-ASSET-STATUS witness (NINJA_MODE_PILL.md ponder / ERPGuide §Create behaviour sample).
//
// PROVES the teaching sample (fixtures/plugins/asset_status_callout.mjs) actually fires THROUGH the real
// AdCallout.dispatch spine, BY the AD_Column.Callout wiring — i.e. structure (sheet-generated) + behaviour
// (hand-crafted JS) close the loop. The issue each assertion proves/disproves:
//   §1 SEAM        — does dispatch fire the named handler because AD_Column.Callout points at it? (the wiring)
//   §2 OFF→Starting— IsActive='N' derives Description='Starting' through dispatch (not by calling the fn direct)
//   §3 ON→Ready    — IsActive='Y' derives Description='Ready'
//   §4 FALSIFIER A — STRUCTURE WITHOUT BEHAVIOUR: before the bundle is started, the column names the callout but
//                    dispatch reports it ABSENT and derives nothing (proves the JS bundle is load-bearing)
//   §5 FALSIFIER B — BEHAVIOUR WITHOUT THE SEAM: a column whose AD_Column.Callout is unset never fires, even
//                    with the handler registered (proves the AD_Column.Callout line is what wires it)
//   §6 retract     — stop()/deactivate drops the handler; dispatch goes ABSENT again
//
// Run: node scripts/poc_asset_status.js 2>&1 | tee build/erp/poc_asset_status.log   (read the log; exit != evidence)
'use strict';
var path = require('path');
var url  = require('url');
var Database = require('better-sqlite3');

var ROOT = path.join(__dirname, '..');
var PR        = require(path.join(ROOT, 'build', 'erp', 'plugin_registry.js'));
var AdCallout = require(path.join(ROOT, 'build', 'erp', 'ad_callout.js'));
var BUNDLE    = path.join(ROOT, 'build', 'erp', 'fixtures', 'plugins', 'asset_status_callout.mjs');
var HANDLER   = 'com.acme.AssetCallout.statusFromActive';

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-ASSET-STATUS — Ninja Create behaviour sample (structure + crafted JS, one bundle) ═══\n');

// ── STRUCTURE (what Emit generates from the sheet) — a one-table AD slice with the callout WIRED on IsActive. ──
// dispatch reads ad_column.callout via better-sqlite3 .prepare().get(); we model exactly that minimal shape.
var db = new Database(':memory:');
db.exec(
  'CREATE TABLE ad_table  (ad_table_id INTEGER PRIMARY KEY, tablename TEXT);' +
  'CREATE TABLE ad_column (ad_column_id INTEGER PRIMARY KEY, ad_table_id INTEGER, columnname TEXT, callout TEXT);' +
  "INSERT INTO ad_table VALUES (7000001,'ACME_Asset');" +
  // IsActive carries the callout NAME — the ONE seam tying generated structure → hand-crafted behaviour.
  "INSERT INTO ad_column VALUES (7000010,7000001,'IsActive','" + HANDLER + "');" +
  "INSERT INTO ad_column VALUES (7000011,7000001,'Description',NULL);"
);
function deriveOn(record) {
  return AdCallout.dispatch(db, { table: 'ACME_Asset', column: 'IsActive', record: record }, {});
}

// ── Host glue (mirrors plugin_overlay / poc_plugin: the live ad_callout REGISTRY is the contribution target). ──
var opdb = new Database(':memory:');
opdb.exec('CREATE TABLE kernel_ops (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, op_type TEXT NOT NULL, parameters TEXT NOT NULL)');
var host = {
  engineVersion: '0.10.0',
  db: { query: function (sql, p) { return db.prepare(sql).all(p || []); } },
  ops: { append: function (t, p) { return opdb.prepare('INSERT INTO kernel_ops (timestamp,op_type,parameters) VALUES (?,?,?)').run(1700000000000, t, JSON.stringify(p || null)).lastInsertRowid; } },
  callout: AdCallout,
  import: function (p) { return import(url.pathToFileURL(p).href); }
};
var reg = PR.create(host);

main().then(function () {
  console.log('\n═══ ' + (fails === 0 ? '🟢 W-ASSET-STATUS PASS' : '🔴 W-ASSET-STATUS FAIL (' + fails + ')') + ' ═══');
  process.exit(fails === 0 ? 0 : 1);
}).catch(function (e) { console.log('🔴 W-ASSET-STATUS THREW: ' + (e && e.stack || e)); process.exit(1); });

async function main() {
  // ═══ §4 FALSIFIER A — STRUCTURE WITHOUT BEHAVIOUR (bundle not yet started) ═══════════════════════════
  console.log('── §4 structure alone (handler not registered) → ABSENT, derives nothing ──');
  var pre = deriveOn({ IsActive: 'Y' });
  verdict(pre.absent.indexOf(HANDLER) !== -1 && pre.derived.Description === undefined,
    'column names callout but dispatch reports ABSENT (JS bundle is load-bearing)',
    'absent=[' + pre.absent.join(',') + ']');
  console.log('§ASSET-STATUS seam=wired behaviour=absent derived=' + JSON.stringify(pre.derived));

  // ═══ install + start the behaviour bundle (registers the handler into the live registry) ═════════════
  var ins = await reg.installBundle(BUNDLE);
  var st  = await reg.startBundle(ins.id);
  verdict(st.state === 'ACTIVE', 'behaviour bundle started → ACTIVE', ins.id);
  verdict(typeof AdCallout.REGISTRY[HANDLER] === 'function', 'handler is live in ad_callout.REGISTRY');

  // ═══ §1/§2/§3 — fire THROUGH dispatch by the AD_Column.Callout wiring ════════════════════════════════
  console.log('\n── §1-3 dispatch via AD_Column.Callout wiring ──');
  var off = deriveOn({ IsActive: 'N', Description: 'whatever' });
  verdict(off.fired.indexOf(HANDLER) !== -1, '§1 dispatch FIRED the named handler (the seam)', 'fired=[' + off.fired.join(',') + ']');
  verdict(off.derived.Description === 'Starting', '§2 IsActive=N → Description=Starting', String(off.derived.Description));
  console.log('§ASSET-STATUS IsActive=N derived.Description=' + off.derived.Description);
  var on = deriveOn({ IsActive: 'Y', Description: 'whatever' });
  verdict(on.derived.Description === 'Ready', '§3 IsActive=Y → Description=Ready', String(on.derived.Description));
  console.log('§ASSET-STATUS IsActive=Y derived.Description=' + on.derived.Description);

  // ═══ §5 FALSIFIER B — BEHAVIOUR WITHOUT THE SEAM (column with no AD_Column.Callout never fires) ═══════
  console.log('\n── §5 same handler registered, but a column WITHOUT the callout wired → never fires ──');
  var noSeam = AdCallout.dispatch(db, { table: 'ACME_Asset', column: 'Description', record: { IsActive: 'Y' } }, {});
  verdict(noSeam.fired.length === 0 && noSeam.derived.Description === undefined,
    'unwired column derives nothing (AD_Column.Callout line is what wires behaviour)', 'fired=[' + noSeam.fired.join(',') + ']');

  // ═══ §6 retract — stop()/deactivate removes the handler ══════════════════════════════════════════════
  console.log('\n── §6 stop() retracts the behaviour ──');
  await reg.stopBundle(ins.id);
  verdict(AdCallout.REGISTRY[HANDLER] === undefined, 'stop() dropped the handler');
  var after = deriveOn({ IsActive: 'Y' });
  verdict(after.absent.indexOf(HANDLER) !== -1 && after.derived.Description === undefined,
    'dispatch ABSENT again after stop (clean retract)', 'absent=[' + after.absent.join(',') + ']');
}
