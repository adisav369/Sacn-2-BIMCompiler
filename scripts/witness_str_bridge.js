#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-BRIDGE scope (read this block first)
 * SCOPE: Slice-7 witness — the BRIDGE that wires the STR walker into the live modeller's op-log
 *   (prompts/STR_ROUTEWALKING_SPEC.md §6 item 7). Proves: a building DB → walker state → a
 *   GEOM_GRID_MOVE → swReWalk → REAL kernel_ops rows + exceptions for the Outliner STR tab.
 *   Uses the UNMODIFIED deploy/dev/kernel_ops.js commit against a real sql.js DB (proves row
 *   compatibility, not a mock). Oracle = Terminal_extracted.db. Read the §-log (Log Mandate).
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   C1 INIT-REAL    — swbInit reads the REAL building's STR columns → walker state (158 cols,
 *                     18×10 grid, 108 girders) matching slices 1-2.
 *   C2 COMMIT-REAL  — swbOnGridMove commits the cascade via the REAL kernel_ops.commitOp; the
 *                     STR_REANCHOR/STR_RESPAN/STR_SIGNAL rows land in the kernel_ops table with
 *                     parseable params (NOT the leading GEOM_GRID_MOVE — the modeller owns that).
 *   C3 EXCEPTION-UI — the GREEN→RED exception is returned for the UI + a §STRWALK-EXCEPTION logged.
 *   C4 PROVENANCE   — every committed row's params carry provenance (folded in) ⇒ traceability
 *                     survives persistence; the signal row carries the cited source.
 *   C5 FOLD+TAB     — state folds (re-walked spans become current); swbTabData returns the STR
 *                     tab signal breakdown (RED/ORANGE/GREEN) for the Outliner.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
var BRIDGE = require(path.join(ROOT, 'deploy/dev/str_walker_bridge.js'));
var KERNEL_SRC = path.join(ROOT, 'deploy/dev/kernel_ops.js');
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_bridge_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

// Load the UNMODIFIED kernel_ops.js into a node scope (window shim; its IDB persist self-guards on APP).
function loadKernelOps() {
  var win = {};
  var fn = new Function('window', 'indexedDB', 'console', fs.readFileSync(KERNEL_SRC, 'utf8'));
  fn(win, undefined, console);
  return win.KernelOps;
}

(async function main() {
  log('═══ W-STR-BRIDGE — STR walker → live modeller op-log (real kernel_ops) ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  var KernelOps = loadKernelOps();
  KernelOps.ensureTable(db);
  var commit = function (t, p, i, o) { return KernelOps.commitOp(db, t, p, i, o); };

  // ── C1 INIT-REAL ──
  var st = BRIDGE.swbInit(db, {});
  var gridStr = st ? st.base.grid.xLines.length + '×' + st.base.grid.yLines.length : 'none';
  assert('C1 INIT-REAL',
    st && st.columnCount === 158 && gridStr === '18×10' && st.base.girders.length === 108,
    (st ? st.columnCount : 0) + ' columns, grid ' + gridStr + ', ' + (st ? st.base.girders.length : 0) + ' girders');

  // derive a grid edit that flips a GREEN girder past the cited limit (reuse slice-6 logic)
  var LIMIT = SW.SW_SPAN_RULES.STEEL.maxBeamSpan;
  var tgt = st.base.girders.filter(function (g) { return SW.swCheckGirder(g.span, {}).signal === 'GREEN'; })
    .sort(function (a, b) { return b.span - a.span; })[0];
  var axis = tgt.axis === 'Xline@' ? 'y' : 'x';
  var gm = { axis: axis, datum: tgt.toDatum, delta: +(LIMIT - tgt.span + 1.0).toFixed(2) };
  log('§BRIDGE simulate GEOM_GRID_MOVE ' + axis + '@' + gm.datum.toFixed(2) + ' Δ+' + gm.delta + 'm');

  var before = db.exec("SELECT COUNT(*) FROM kernel_ops")[0].values[0][0];
  var r = BRIDGE.swbOnGridMove(gm, commit, {});
  var after = db.exec("SELECT COUNT(*) FROM kernel_ops")[0].values[0][0];

  // ── C2 COMMIT-REAL ──
  var typeRows = db.exec("SELECT op_type, COUNT(*) FROM kernel_ops GROUP BY op_type")[0].values;
  var types = {}; typeRows.forEach(function (v) { types[v[0]] = v[1]; });
  var noGridMove = !types['GEOM_GRID_MOVE'];     // the bridge must NOT double-commit the grid edit
  var landed = (after - before) === r.committed && r.committed > 0;
  // params parse-check
  var parseOk = db.exec("SELECT parameters FROM kernel_ops WHERE op_type='STR_RESPAN'")[0].values.every(function (v) {
    try { JSON.parse(v[0]); return true; } catch (e) { return false; }
  });
  assert('C2 COMMIT-REAL',
    landed && noGridMove && parseOk,
    r.committed + ' STR ops committed to kernel_ops (' + JSON.stringify(types) + '), no double GRID_MOVE, params parse=' + parseOk);

  // ── C3 EXCEPTION-UI ──
  var redFlip = r.exceptions.filter(function (e) { return e.newSignal === 'RED'; });
  assert('C3 EXCEPTION-UI',
    r.exceptions.length >= 1 && redFlip.length >= 1,
    r.exceptions.length + ' exception(s) returned for UI; ' + redFlip.length + ' GREEN→RED');

  // ── C4 PROVENANCE (folded into persisted params) ──
  var sigRow = db.exec("SELECT parameters FROM kernel_ops WHERE op_type='STR_SIGNAL'");
  var sigParams = sigRow.length ? JSON.parse(sigRow[0].values[0][0]) : {};
  var reanchorRows = db.exec("SELECT parameters FROM kernel_ops WHERE op_type='STR_REANCHOR'")[0].values;
  var allProv = reanchorRows.every(function (v) { return JSON.parse(v[0]).provenance === 'derived:grid'; });
  assert('C4 PROVENANCE',
    allProv && sigParams.provenance === 'derived:regulatory' && /Eurocode|EN 199/.test(sigParams.source || ''),
    'reanchor rows provenance=derived:grid=' + allProv + '; signal row cited=' + /Eurocode|EN 199/.test(sigParams.source || ''));

  // ── C5 FOLD+TAB ──
  var tab = BRIDGE.swbTabData();
  var folded = tab && (tab.signals.RED >= 1) && (tab.signals.GREEN >= 1) && tab.girders === 108;
  assert('C5 FOLD+TAB',
    folded,
    'STR tab: grid ' + tab.grid + ', ' + tab.girders + ' girders, signals ' + JSON.stringify(tab.signals) + ' (re-walk folded in)');

  log('───────────────────────────────────────────────');
  log('W-STR-BRIDGE: ' + pass + ' PASS / ' + fail + ' FAIL');
  db.close();
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
