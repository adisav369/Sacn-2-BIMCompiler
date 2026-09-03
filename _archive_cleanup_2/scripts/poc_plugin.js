#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_plugin.js — W-PLUGIN witness (prompts/PLUGIN_SYSTEM_LANE.md §Witness Contract). Drives the Fold-Engine
// plugin host (build/erp/plugin_registry.js) end-to-end against the canonical ad_full.db:
//   Phase A  lifecycle  — install/start three single-file .foldbundle modules; PLUGIN_* ops land in kernel_ops.
//   Phase A  resolveDeps— semver + topological sort; a dependency CYCLE is rejected (the §FALSIFIER).
//   Phase C  contributions — each started bundle has really contributed into the live engine registries:
//     C-1 callout uppercases M_Product.Name · C-2 validator REJECTs M_Production qty<0 · C-3 token resolves WIP.
// PURE host, injected glue: host.db.query (read-only sqlite), host.ops.append (real kernel_ops INSERT → opId).
// Run: node scripts/poc_plugin.js 2>&1 | tee build/erp/poc_plugin.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var url  = require('url');
var Database = require('better-sqlite3');

var ROOT = path.join(__dirname, '..');
var PR  = require(path.join(ROOT, 'build', 'erp', 'plugin_registry.js'));
var AdCallout  = require(path.join(ROOT, 'build', 'erp', 'ad_callout.js'));
var AdModelVal = require(path.join(ROOT, 'build', 'erp', 'ad_modelval.js'));
var PostResolver = require(path.join(ROOT, 'scripts', 'post_resolver.js'));
var DB_PATH = path.join(ROOT, 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-PLUGIN — Fold-Engine plugin host (install → start → contribute → audit) ═══\n');

// ── Host glue ─────────────────────────────────────────────────────────────────────────────────────────
var seed = new Database(DB_PATH, { readonly: true });

// kernel_ops op-log (real INSERTs so PLUGIN_* ops carry real opIds; in-memory mirror of the browser table).
var opdb = new Database(':memory:');
opdb.exec('CREATE TABLE kernel_ops (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, op_type TEXT NOT NULL, parameters TEXT NOT NULL)');
function appendOp(opType, params) {
  var info = opdb.prepare('INSERT INTO kernel_ops (timestamp, op_type, parameters) VALUES (?,?,?)')
                 .run(1700000000000, opType, JSON.stringify(params || null));
  return info.lastInsertRowid;
}

var host = {
  engineVersion: '0.10.0',
  db: { query: function (sql, params) { return seed.prepare(sql).all(params || []); } },
  ops: { append: appendOp },
  modelval: AdModelVal,
  callout: AdCallout,
  postTokens: PostResolver.TOKENS,
  import: function (p) { return import(url.pathToFileURL(p).href); }   // load a single-file ES-module bundle
};

var reg = PR.create(host);
var PLUGINS = path.join(ROOT, 'build', 'erp', 'fixtures', 'plugins');

// ═══ resolveDeps unit — semver + topo sort, and the cycle FALSIFIER ═══════════════════════════════════
console.log('── resolveDeps: semver-checked topological sort ──');
var order = PR.resolveDeps([
  { id: 'A', version: '1.0.0', requires: { B: '>=1.0.0' } },
  { id: 'B', version: '1.2.0', requires: {} },
  { id: 'C', version: '1.0.0', requires: { A: '>=1.0.0', B: '*' } }
]);
verdict(order.indexOf('B') < order.indexOf('A') && order.indexOf('A') < order.indexOf('C'),
  'topo order is dependency-first', 'order=[' + order.join(',') + ']');
var cycleRejected = false;
try { PR.resolveDeps([{ id: 'X', version: '1.0.0', requires: { Y: '*' } }, { id: 'Y', version: '1.0.0', requires: { X: '*' } }]); }
catch (e) { cycleRejected = /cycle/.test(e.message); console.log('   §PLUGIN-CYCLE rejected: ' + e.message); }
verdict(cycleRejected, 'dependency CYCLE is rejected (X↔Y)', 'FALSIFIER');
var conflictRejected = false;
try { PR.resolveDeps([{ id: 'P', version: '1.0.0', requires: { Q: '>=2.0.0' } }, { id: 'Q', version: '1.0.0', requires: {} }]); }
catch (e) { conflictRejected = /version conflict/.test(e.message); }
verdict(conflictRejected, 'semver conflict is rejected (needs Q>=2.0.0, have 1.0.0)');

main().then(function () {
  console.log('\n═══ ' + (fails === 0 ? '🟢 W-PLUGIN PASS' : '🔴 W-PLUGIN FAIL (' + fails + ')') + ' ═══');
  process.exit(fails === 0 ? 0 : 1);
}).catch(function (e) {
  console.log('🔴 W-PLUGIN THREW: ' + (e && e.stack || e));
  process.exit(1);
});

async function main() {
  AdCallout.installDefaultHandlers();   // the engine's own handlers are present; plugins ADD to them.

  // ═══ Phase A — install + start three bundles ════════════════════════════════════════════════════════
  console.log('\n── Phase A: install + start (lifecycle ops → kernel_ops) ──');
  var bundles = [
    path.join(PLUGINS, 'widget_callout.mjs'),
    path.join(PLUGINS, 'production_validator.mjs'),
    path.join(PLUGINS, 'wip_token.mjs')
  ];
  var ids = [];
  for (var i = 0; i < bundles.length; i++) {
    var ins = await reg.installBundle(bundles[i]);
    verdict(ins.opId != null && ins.state === 'INSTALLED', 'installed ' + ins.id, 'opId=' + ins.opId);
    var st = await reg.startBundle(ins.id);
    verdict(st.state === 'ACTIVE', 'started ' + ins.id + ' → ACTIVE');
    ids.push(ins.id);
  }

  // ═══ Phase C-1 — callout contribution: M_Product.Name uppercases ════════════════════════════════════
  console.log('\n── Phase C-1: callout plugin (M_Product.Name → upper) ──');
  var calloutFn = AdCallout.REGISTRY['com.example.WidgetCallout.upperName'];
  verdict(typeof calloutFn === 'function', 'plugin handler is live in ad_callout.REGISTRY');
  var d = calloutFn ? calloutFn({}, { record: { Name: 'widget' } }) : {};
  var upper = d && d.derived && d.derived.Name;
  verdict(upper === 'WIDGET', 'callout derived Name', 'widget → ' + upper);
  console.log('§PLUGIN-CALLOUT name=' + upper);

  // ═══ Phase C-2 — validator contribution: M_Production qty<0 REJECT ══════════════════════════════════
  console.log('\n── Phase C-2: validator plugin (M_Production BEFORE_SAVE) ──');
  var bad = AdModelVal.fireHooks('BEFORE_SAVE', { table: 'M_Production', record: { ProductionQty: -5 } }, {});
  var good = AdModelVal.fireHooks('BEFORE_SAVE', { table: 'M_Production', record: { ProductionQty: 10 } }, {});
  verdict(bad.ok === false && /must be >= 0/.test(bad.error || ''), 'qty=-5 BLOCKED', bad.blocked + ': ' + bad.error);
  verdict(good.ok === true, 'qty=10 PASSES (validator is load-bearing, not a blanket reject)');
  console.log('§PLUGIN-VALRULE M_Production qty<0 REJECT');

  // ═══ Phase C-3 — token contribution: {Production.WIP} → real seed account ═══════════════════════════
  console.log('\n── Phase C-3: token plugin ({Production.WIP} → WIP account) ──');
  var tok = PostResolver.TOKENS['{Production.WIP}'];
  verdict(!!tok, '{Production.WIP} token is live in post_resolver.TOKENS');
  var acct = tok && tok.resolved;
  verdict(acct && acct.id != null, 'token resolved to a REAL seed account (extracted, not invented)',
    acct ? ('id=' + acct.id + ' value=' + acct.value + ' name="' + acct.name + '"') : 'absent');
  if (acct) console.log('§PLUGIN-TOKEN   WIP=' + acct.id + ' value=' + acct.value + ' name="' + acct.name + '"');

  // ═══ Phase A — stop + uninstall (contributions retract) + op-log audit ══════════════════════════════
  console.log('\n── Phase A: stop + uninstall + audit ──');
  await reg.stopBundle('com.example.widget-callout');
  verdict(AdCallout.REGISTRY['com.example.WidgetCallout.upperName'] === undefined,
    'stop() retracted the callout contribution');
  await reg.uninstallBundle('com.example.widget-callout');
  verdict(reg.get('com.example.widget-callout') === null, 'uninstall() dropped the bundle from the registry');
  verdict(reg.list().length === 2, 'two bundles remain ACTIVE', reg.list().map(function (b) { return b.id + ':' + b.state; }).join(', '));

  var opRows = opdb.prepare("SELECT op_type, COUNT(*) c FROM kernel_ops WHERE op_type LIKE 'PLUGIN_%' GROUP BY op_type ORDER BY op_type").all();
  console.log('   op-log: ' + opRows.map(function (r) { return r.op_type + '×' + r.c; }).join(', '));
  var byType = {}; opRows.forEach(function (r) { byType[r.op_type] = r.c; });
  verdict(byType.PLUGIN_INSTALL === 3 && byType.PLUGIN_START === 3 && byType.PLUGIN_STOP === 1 && byType.PLUGIN_UNINSTALL === 1,
    'kernel_ops recorded the full PLUGIN_* lifecycle', '3 install / 3 start / 1 stop / 1 uninstall');
}
