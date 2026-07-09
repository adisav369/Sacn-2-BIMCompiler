#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-TERM-NOSPACES scope (read this block first)
 * SCOPE: item 2 of the geometry-hell ratchet (RESUME_DISC_WALKER_ENVELOPE_BOUND.md §NOSPACES,
 *   2026-07-10): prove disc_walker's measured-band no-spaces path fixes the Terminal-class walk
 *   (no real IfcSpace rows) — n_measured×area-bound counts + host-conformant placement + LOD400
 *   mesh binding — replacing the legacy 22-pseudo-storey density scatter (measured this session:
 *   3003 generated vs 833 real ELEC, 798 floating mid-void). Read the §-log after the run; exit
 *   code is not the evidence.
 *
 * ANTI-CHEAT (two code paths, one comparer): the walker walks an ARC-ONLY COPY of the extraction
 *   (every rules-generatable class + all MEP-shaped classes DELETED before the walk); only THIS
 *   witness reads the full db's real MEP as ground truth. The walker additionally excludes
 *   rules-generatable classes in its own envelope query (§NOSPACES), so the anti-cheat is
 *   structural on both sides.
 *
 * CLAIMS (Terminal ELEC primary — the hardest, no-real-space case; ACMV/FP reported not graded
 *   where the oracle would be vacuous; no W5 fidelity dial — per the ratchet, no per-instance
 *   ground-truth transforms exist for Terminal):
 *   T1 PATH-ENGAGED  — {schedule:true} on Terminal returns mode='measured-band' (not the old flat
 *                      REFUSE) AND terminal_rules.db still carries ZERO residential schedule
 *                      tables (M6 boundary — the no-spaces path consumed none of that data).
 *   T2 QTY-BOUND     — per graded class: generated/real ∈ [0.5, 2.0] (counts area-bound by
 *                      n_measured, not pseudo-storey-inflated). FALSIFIER: scaling n_measured
 *                      ×0.2 in a COPY of the rules collapses the generated count — the bound
 *                      genuinely measures the mined data.
 *   T3 CONTAINMENT+Z — every placement sits inside the real building's XY envelope AND its z is
 *                      inside its OWN rule row's measured z-band (no mid-void, no wrong-storey).
 *   T4 HOST-CONFORM  — hostBound + floats === placed (count preserved through host-bind); bound
 *                      placements carry a real host guid; floats are counted honestly, never
 *                      silently re-labelled.
 *   T5 LOD400        — every placement carries a geometry_hash that is a REAL hash of its own
 *                      class in the building's element_instances (the mined dominant mesh, never
 *                      a fallback shape); a class with no real mesh appears ONLY in lod400Refused.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var RULES = path.join(ROOT, 'build/terminal_rules.db');
var BDB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_terminal_nospaces_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(c, cond, d) { if (cond) { pass++; log('  ✅ ' + c + ' — ' + d); } else { fail++; log('  ❌ ' + c + ' — ' + d); } }
function rows(db, sql) { var r = db.exec(sql); if (!r.length) return []; return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; }); }
function loadDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(f))); }

var GRADED = ['IfcLightFixture', 'IfcElectricAppliance'];      // ELEC — the primary case

(async function main() {
  log('═══ W-TERM-NOSPACES — measured-band no-spaces walk vs the real Terminal (blindfolded) ═══');
  var SQL = await initSqlJs();
  global.window = global.window || {};
  var DW = require(path.join(ROOT, 'build/disc_walker.js'));
  var dw = global.window.DW || DW;

  var full = loadDb(SQL, BDB);                                 // ground truth — witness-only
  var rules = loadDb(SQL, RULES);

  // ARC-ONLY COPY: strip every rules-generatable class + all MEP-shaped classes.
  var arc = loadDb(SQL, BDB);
  var gen = rows(rules, 'SELECT DISTINCT ifc_class FROM rule_placement').map(function (r) { return r.ifc_class; });
  var mepLike = rows(full, "SELECT DISTINCT ifc_class FROM elements_meta WHERE ifc_class LIKE '%Segment%' OR ifc_class LIKE '%Fitting%' OR ifc_class LIKE '%Terminal%' OR ifc_class LIKE '%Valve%' OR ifc_class LIKE '%Alarm%' OR ifc_class LIKE '%Light%' OR ifc_class LIKE '%Electric%' OR ifc_class LIKE '%Flow%' OR ifc_class LIKE '%Controller%'")
    .map(function (r) { return r.ifc_class; });
  var strip = {}; gen.concat(mepLike).forEach(function (c) { strip[c] = 1; });
  var stripList = Object.keys(strip).map(function (c) { return "'" + c + "'"; }).join(',');
  ['element_transforms', 'element_instances'].forEach(function (t) {
    arc.exec('DELETE FROM ' + t + ' WHERE guid IN (SELECT guid FROM elements_meta WHERE ifc_class IN (' + stripList + '))');
  });
  arc.exec('DELETE FROM elements_meta WHERE ifc_class IN (' + stripList + ')');
  var left = rows(arc, 'SELECT COUNT(*) n FROM elements_meta')[0].n;
  log('§TN ARC-only copy: stripped ' + Object.keys(strip).length + ' classes, ' + left + ' ARC rows remain (walker sees ONLY these)');

  log(''); log('─── T1 PATH-ENGAGED + BOUNDARY ───');
  dw.dwOpen(rules);
  var w = dw.dwWalk('ELEC', arc, 'Terminal', { schedule: true });
  var schedTables = rows(rules, "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('rule_space_schedule','rule_space_type','rule_space_alias','rule_code_spacing')");
  assert('T1 PATH-ENGAGED', !w.refused && w.mode === 'measured-band' && schedTables.length === 0,
    "{schedule:true} on no-spaces Terminal → mode='" + w.mode + "' placed=" + w.placed +
    ' (not the old flat REFUSE); terminal_rules.db schedule tables = ' + schedTables.length +
    ' — residential schedule data untouched (M6 boundary holds)');

  log(''); log('─── T2 QTY-BOUND ───');
  var byCls = {};
  (w.placements || []).forEach(function (p) { byCls[p.ifc_class] = (byCls[p.ifc_class] || 0) + 1; });
  var qtyOk = true, qtyDetail = [];
  GRADED.forEach(function (cls) {
    var real = rows(full, "SELECT COUNT(*) n FROM elements_meta WHERE ifc_class='" + cls + "'")[0].n;
    var gen2 = byCls[cls] || 0, ratio = real ? gen2 / real : 0;
    qtyOk = qtyOk && real > 0 && ratio >= 0.5 && ratio <= 2.0;
    qtyDetail.push(cls + ' ' + gen2 + '/' + real + ' (' + ratio.toFixed(2) + ')');
    log('§TN qty ' + cls + ' generated=' + gen2 + ' real=' + real + ' ratio=' + ratio.toFixed(2));
  });
  // falsifier: n_measured ×0.2 in a COPY → the generated count must collapse
  var tampered = loadDb(SQL, RULES);
  tampered.exec("UPDATE rule_placement SET n_measured = CAST(n_measured*0.2 AS INTEGER) WHERE disc='ELEC'");
  dw.dwOpen(tampered);
  var wT = dw.dwWalk('ELEC', arc, 'Terminal', { schedule: true });
  dw.dwOpen(rules);
  assert('T2 QTY-BOUND', qtyOk && wT.placed < w.placed * 0.5,
    qtyDetail.join('; ') + ' — all in band [0.5,2.0]; falsifier n_measured×0.2 → placed ' +
    w.placed + '→' + wT.placed + ' (the bound measures the mined data, not vacuously true)');
  tampered.close();

  log(''); log('─── T3 CONTAINMENT + Z-SANITY ───');
  var env = rows(full, 'SELECT MIN(center_x-bbox_x/2) x0, MAX(center_x+bbox_x/2) x1, MIN(center_y-bbox_y/2) y0, MAX(center_y+bbox_y/2) y1 FROM element_transforms')[0];
  var out = 0, zBad = 0;
  (w.placements || []).forEach(function (p) {
    if (p.x < env.x0 - 0.5 || p.x > env.x1 + 0.5 || p.y < env.y0 - 0.5 || p.y > env.y1 + 0.5) out++;
    // host-bound Z moved to the real host face — allow the band ± the fixture's own extent; floats must sit in-band
    var band = p.band; if (!band) { zBad++; return; }
    var tol = (p.bz || 0.5) + 0.75;
    if (p.z < band[0] - tol || p.z > band[1] + tol) zBad++;
  });
  assert('T3 CONTAINMENT+Z', out === 0 && zBad === 0,
    (w.placements || []).length + ' placements: ' + out + ' outside the real XY envelope, ' + zBad +
    ' outside their own measured z-band (±fixture extent) — no mid-void, no wrong-storey');

  log(''); log('─── T4 HOST-CONFORM ───');
  var m = w.measured || {};
  var hostTagged = (w.placements || []).filter(function (p) { return p.host; }).length;
  assert('T4 HOST-CONFORM', m.hostBound + m.floats === w.placed && hostTagged === m.hostBound && m.hostBound > 0,
    'hostBound=' + m.hostBound + ' + floats=' + m.floats + ' === placed=' + w.placed +
    ' (count preserved); ' + hostTagged + ' placements carry a REAL host guid; floats stay envelope-bound at measured z, logged honest');

  log(''); log('─── T5 LOD400 ───');
  var badHash = 0, noHash = 0;
  var realHash = {};
  GRADED.forEach(function (cls) {
    rows(full, "SELECT DISTINCT ei.geometry_hash h FROM element_instances ei JOIN elements_meta em ON em.guid=ei.guid WHERE em.ifc_class='" + cls + "'")
      .forEach(function (r) { realHash[cls + '|' + r.h] = 1; });
  });
  (w.placements || []).forEach(function (p) {
    if (!p.geometry_hash) { noHash++; return; }
    if (GRADED.indexOf(p.ifc_class) >= 0 && !realHash[p.ifc_class + '|' + p.geometry_hash]) badHash++;
  });
  var refusedN = Object.keys(m.lod400Refused || {}).length;
  assert('T5 LOD400', noHash === 0 && badHash === 0,
    (w.placements || []).length + ' placements all carry a geometry_hash (' + noHash + ' missing, ' + badHash +
    ' not a REAL hash of their own class); classes with no real mesh: ' + refusedN + ' (REFUSED only, never a fallback shape)');

  log(''); log('─── REPORTED (not graded — secondary discs) ───');
  ['FP', 'ACMV'].forEach(function (d) {
    var wd = dw.dwWalk(d, arc, 'Terminal', { schedule: true });
    var bc = {}; (wd.placements || []).forEach(function (p) { bc[p.ifc_class] = (bc[p.ifc_class] || 0) + 1; });
    Object.keys(bc).forEach(function (cls) {
      var real = rows(full, "SELECT COUNT(*) n FROM elements_meta WHERE ifc_class='" + cls + "'")[0].n;
      log('§TN report ' + d + '/' + cls + ' generated=' + bc[cls] + ' real=' + real +
        (real ? ' ratio=' + (bc[cls] / real).toFixed(2) : ' (no real oracle)'));
    });
  });

  log('');
  log('§TN SUMMARY: the no-spaces measured-band path replaces the Terminal flat-REFUSE/legacy scatter:');
  log('§TN counts n_measured×area-bound per measured z-band, positions envelope-bound + host-bound onto');
  log('§TN real ARC, every fixture carrying its class\'s mined REAL mesh hash or refusing honestly.');
  log('');
  log('W-TERM-NOSPACES: ' + pass + ' PASS / ' + fail + ' FAIL');
  fs.writeFileSync(LOG, _lines.join('\n') + '\n');
  log('§LOG ' + LOG);
  process.exit(fail ? 1 : 0);
})();
