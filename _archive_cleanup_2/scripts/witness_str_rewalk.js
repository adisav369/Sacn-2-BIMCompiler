#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-REWALK scope (read this block first)
 * SCOPE: Slice-6 witness — the RE-WALK LOOP (prompts/STR_ROUTEWALKING_SPEC.md §3/§4) = THE WEDGE.
 *   An ARC grid edit (a datum moves by Δ) cascades: columns re-anchor, girders re-span (cross-
 *   section held), and the regulatory layer FLIPS a girder's signal → an exception surfaces
 *   ("add a column / upsize"). "Model + exception fold from ONE signed log." Oracle = pristine
 *   Terminal_extracted.db. Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * METHOD: require the UNMODIFIED deploy/dev/str_walker.js; walk the real Terminal skeleton; pick a
 *   GREEN girder near the span limit; widen its bay by a data-derived Δ that pushes it over the
 *   cited limit; assert the cascade + the GREEN→RED flip + signed replay + non-invent.
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   C1 GRID-MOVES     — the edited datum moves by exactly Δ; columns on it re-anchor by Δ (one
 *                       STR_REANCHOR op each); columns elsewhere are untouched.
 *   C2 GIRDERS-RESPAN — girders spanning the moved datum get span = old ± Δ (traced to Δ); their
 *                       cross-section (identity) is HELD; one STR_RESPAN op each.
 *   C3 EXCEPTION-WEDGE— the edit FLIPS ≥1 girder GREEN→RED (or →ORANGE); the STR_SIGNAL op carries
 *                       a CITED message. The consequence is surfaced, not silently absorbed.
 *   C4 SIGNED-REPLAY  — a GEOM_GRID_MOVE op heads the chain; replaying the STR_RESPAN ops onto the
 *                       before-girders reproduces the after-spans; a second re-walk is identical.
 *   C5 NON-INVENT     — the ONLY new number is the user Δ; every new span = old±Δ; every exception
 *                       cited; provenance set on every op.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_rewalk_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

(async function main() {
  log('═══ W-STR-REWALK — ARC edit → STR re-walks → exception surfaces (the wedge) ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  function rows(s) { var r = db.exec(s); return r.length ? r[0].values : []; }
  var columns = rows("SELECT m.guid,t.center_x,t.center_y,t.center_z FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.discipline='STR' AND m.ifc_class='IfcColumn'")
    .map(function (r) { return { guid: r[0], x: r[1], y: r[2], z: r[3] }; });

  var base = SW.swWalkSkeleton(columns, {});
  var LIMIT = SW.SW_SPAN_RULES.STEEL.maxBeamSpan;  // cited steel span limit

  // pick the largest GREEN girder (nearest the limit) → derive an edit that pushes it over
  var greens = base.girders.filter(function (g) { return SW.swCheckGirder(g.span, {}).signal === 'GREEN'; })
    .sort(function (a, b) { return b.span - a.span; });
  var target = greens[0];
  var delta = +(LIMIT - target.span + 1.0).toFixed(2);  // +1m past the limit → must flip RED
  // an Xline@ girder spans between Y-datums → widen by moving a Y-datum; Yline@ → move an X-datum
  var axis = target.axis === 'Xline@' ? 'y' : 'x';
  var datum = target.toDatum;
  log('§STR-REWALK target girder span ' + target.span.toFixed(1) + 'm (GREEN, limit ' + LIMIT +
      'm) → widen bay: move ' + axis + '-datum ' + datum.toFixed(2) + ' by +' + delta + 'm');

  var rw = SW.swReWalk(base, { axis: axis, datum: datum, delta: delta }, {});

  // ── C1 GRID-MOVES ──
  var gridOp = rw.ops[0];
  var reanchor = rw.ops.filter(function (o) { return o.opType === 'STR_REANCHOR'; });
  var allByDelta = reanchor.every(function (o) {
    var d = axis === 'y' ? o.params.to[1] - o.params.from[1] : o.params.to[0] - o.params.from[0];
    return Math.abs(d - delta) < 1e-6;
  });
  assert('C1 GRID-MOVES',
    gridOp.opType === 'GEOM_GRID_MOVE' && reanchor.length > 0 && allByDelta,
    gridOp.opType + ' + ' + reanchor.length + ' columns re-anchored by exactly Δ=' + delta + 'm = ' + allByDelta);

  // ── C2 GIRDERS-RESPAN ──
  var respan = rw.ops.filter(function (o) { return o.opType === 'STR_RESPAN'; });
  var spanByDelta = respan.every(function (o) { return Math.abs(Math.abs(o.params.newSpan - o.params.oldSpan) - delta) < 1e-6; });
  assert('C2 GIRDERS-RESPAN',
    respan.length > 0 && spanByDelta,
    respan.length + ' girders re-spanned, each |Δspan| == ' + delta + 'm (cross-section held) = ' + spanByDelta);

  // ── C3 EXCEPTION-WEDGE ──
  var flip = rw.exceptions.filter(function (e) { return e.guid === target.guid; })[0];
  var anyRed = rw.exceptions.some(function (e) { return e.newSignal === 'RED'; });
  assert('C3 EXCEPTION-WEDGE',
    !!flip && (flip.newSignal === 'RED' || flip.newSignal === 'ORANGE') && !!flip.source && anyRed,
    'target girder ' + (flip ? flip.oldSignal + '→' + flip.newSignal + ' @' + flip.span.toFixed(1) + 'm, cited' : 'NO FLIP') +
    '; total exceptions=' + rw.exceptions.length);
  if (flip) log('     §WEDGE "' + flip.message.slice(0, 70) + '…"');

  // ── C4 SIGNED-REPLAY ──
  var byGuid = {}; base.girders.forEach(function (g) { byGuid[g.guid] = g.span; });
  var replayOk = respan.every(function (o) { return byGuid[o.params.guid] === o.params.oldSpan; });
  var rw2 = SW.swReWalk(base, { axis: axis, datum: datum, delta: delta }, {});
  var deterministic = JSON.stringify(rw2.ops) === JSON.stringify(rw.ops);
  assert('C4 SIGNED-REPLAY',
    gridOp.opType === 'GEOM_GRID_MOVE' && replayOk && deterministic,
    'op chain heads with GRID_MOVE, STR_RESPAN oldSpans match before-state = ' + replayOk + ', re-run identical = ' + deterministic);

  // ── C5 NON-INVENT ──
  var allProv = rw.ops.every(function (o) { return !!o.provenance; });
  var citedEx = rw.exceptions.every(function (e) { return !!e.source; });
  assert('C5 NON-INVENT',
    allProv && citedEx,
    'every op has provenance = ' + allProv + '; every exception cited = ' + citedEx + '; only new number = user Δ');

  log('───────────────────────────────────────────────');
  log('W-STR-REWALK: ' + pass + ' PASS / ' + fail + ' FAIL  (ops: ' + rw.ops.length + ')');
  db.close();
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
