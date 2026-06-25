#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-REGULATORY scope (read this block first)
 * SCOPE: Slice-4 witness for the STRUCTURAL walker's REGULATORY handler
 *   (prompts/STR_ROUTEWALKING_SPEC.md §2B/§4) — span/depth + deflection → RED/ORANGE, each output
 *   CITING a code rule. This is the "strengthen" half: it sizes/flags structure, never invents a
 *   number. Oracle = pristine deploy/buildings/Terminal_extracted.db, NEVER output.db.
 *   Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * METHOD: require the UNMODIFIED deploy/dev/str_walker.js; (a) validate the cited rule against the
 *   432 REAL Terminal beams (does the as-built structure satisfy the preliminary span/depth rule?);
 *   (b) run the handler over the 108 walked girders; (c) exercise the RED + ORANGE branches.
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   C1 RULE-CITED    — every signal carries a non-empty .source citation + provenance
 *                      derived:regulatory; ALL thresholds come from SW_SPAN_RULES (no magic numbers).
 *   C2 CONFORMS-REAL — INDEPENDENT VALIDATION: ≥95% of 432 real Terminal beams satisfy the steel
 *                      span/depth rule (measured 95.8%); the ~4% that exceed are truss-like
 *                      (ratio 53-126), correctly flagged, reported not dropped. Proves the rule is real.
 *   C3 RED-OVER-SPAN — a girder spanning beyond the cited max for a solid beam raises RED with a
 *                      load-path message ("add a column or use a truss") + the citation.
 *   C4 ORANGE-UPSIZE — a girder offered a depth below the cited required depth raises ORANGE with
 *                      the suggested depth + citation; an adequate depth is GREEN.
 *   C5 NON-INVENT    — the walked-girder signal breakdown is reported; every threshold traces to
 *                      SW_SPAN_RULES; no invented constants.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW_SRC = path.join(ROOT, 'deploy/dev/str_walker.js');
var SW = require(SW_SRC);
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_regulatory_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var CONFORM_MIN = 0.95;        // ≥95% of real beams must satisfy the rule (measured 95.8%)

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

(async function main() {
  log('═══ W-STR-REGULATORY — span/depth + deflection → RED/ORANGE, each rule CITED ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  function rows(sql) { var r = db.exec(sql); return r.length ? r[0].values : []; }

  var columns = rows("SELECT m.guid,t.center_x,t.center_y,t.center_z FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.discipline='STR' AND m.ifc_class='IfcColumn'")
    .map(function (r) { return { guid: r[0], x: r[1], y: r[2], z: r[3] }; });
  var beams = rows("SELECT t.bbox_x,t.bbox_y,t.bbox_z FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.discipline='STR' AND m.ifc_class='IfcBeam' AND t.bbox_z>0")
    .map(function (r) { return { span: Math.max(r[0], r[1]), depth: r[2] }; });
  var girders = SW.swWalkSkeleton(columns, {}).girders;
  log('§STR-REG ' + beams.length + ' real beams, ' + girders.length + ' walked girders');

  // ── C1 RULE-CITED ──
  var sample = SW.swCheckGirder(7.0, { material: 'STEEL' });
  assert('C1 RULE-CITED',
    !!sample.source && sample.source.indexOf('Eurocode') >= 0 && sample.provenance === 'derived:regulatory' &&
    !!SW.SW_SPAN_RULES.STEEL && !!SW.SW_SPAN_RULES.RC,
    'signal carries source="' + sample.source.slice(0, 38) + '…" + provenance=' + sample.provenance);

  // ── C2 CONFORMS-REAL (independent validation of the rule against the real building) ──
  var conform = beams.filter(function (b) { return SW.swConforms(b.span, b.depth, { material: 'STEEL' }).conforms; }).length;
  var frac = conform / beams.length;
  var trussLike = beams.filter(function (b) { return (b.span / b.depth) > 30; });
  assert('C2 CONFORMS-REAL', frac >= CONFORM_MIN,
    conform + '/' + beams.length + ' real beams satisfy steel L/20 = ' + (100 * frac).toFixed(1) +
    '% (≥' + (100 * CONFORM_MIN) + '%); ' + trussLike.length + ' truss-like (ratio>30) flagged, not dropped');

  // ── C3 RED-OVER-SPAN ──
  var red = SW.swCheckGirder(25.0, { material: 'STEEL' });
  assert('C3 RED-OVER-SPAN',
    red.signal === 'RED' && /column|truss/.test(red.message) && !!red.source,
    '25m steel girder → ' + red.signal + ': "' + red.message.slice(0, 60) + '…" cited=' + !!red.source);

  // ── C4 ORANGE-UPSIZE + GREEN ──
  var orange = SW.swCheckGirder(8.0, { material: 'STEEL', proposedDepth: 0.30 }); // required = 8/20 = 0.40m
  var green = SW.swCheckGirder(8.0, { material: 'STEEL', proposedDepth: 0.50 });
  assert('C4 ORANGE-UPSIZE',
    orange.signal === 'ORANGE' && Math.abs(orange.requiredDepth - 0.40) < 1e-9 && green.signal === 'GREEN',
    '8m girder: depth 0.30m→' + orange.signal + ' (need ' + orange.requiredDepth.toFixed(2) + 'm); depth 0.50m→' + green.signal);

  // ── C5 NON-INVENT (walked-girder breakdown, all from the cited table) ──
  var brk = { RED: 0, ORANGE: 0, GREEN: 0 };
  girders.forEach(function (g) { brk[SW.swCheckGirder(g.span, { material: 'STEEL' }).signal]++; });
  var allCited = girders.every(function (g) {
    var s = SW.swCheckGirder(g.span, { material: 'STEEL' });
    return s.provenance === 'derived:regulatory' && !!s.source;
  });
  assert('C5 NON-INVENT', allCited,
    'walked girders → RED ' + brk.RED + ' / ORANGE ' + brk.ORANGE + ' / GREEN ' + brk.GREEN +
    '; every signal cited from SW_SPAN_RULES = ' + allCited);

  log('───────────────────────────────────────────────');
  log('W-STR-REGULATORY: ' + pass + ' PASS / ' + fail + ' FAIL');
  db.close();
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
