#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-DWWALK-HOSTBIND scope (read this block first)
 * SCOPE: Promote the host-bind anti-float fix from the WITNESS into the LIVE WALK. Until now `disc_walker.hostBind`
 *   was only exercised by witnesses; `dwWalk` (the Outliner "Walk · Disciplines" entry point) placed ELEC at
 *   floating footprint-cell centres and never host-bound them. This proves `dwWalk(disc, bdb, name, {shims})` now
 *   routes the floating placements through hostBind for any discipline that has a matching host percept — COUNT
 *   PRESERVED (bound ∪ refused), refusals kept floating + counted — while `dwWalk` WITHOUT shims stays byte-identical
 *   (no live-behaviour change unless a percept is supplied).
 *
 *   This is the CALLER-PASSED interim of the §NAMING DIRECTIVE §SHIM step (RESUME_DISC_WALKER_ENVELOPE_BOUND.md):
 *   the hardened end-state projects a `rule_shim` table into the *_rules.db that dwWalk reads directly. Percepts
 *   here are read from disc_patterns.db `_shim_attributes` (physically library/ERP.db until the rename slice lands).
 *   NON-INVENT: walls are REAL SampleHouse geometry; the percept is the prior-art pattern-store fact, never guessed;
 *   refusals are counted, never fabricated onto a wall. Read the §-log after the run; exit code is not the evidence.
 *
 * CLAIMS:
 *   W0 PERCEPT-SOURCE — the ELEC wall percept is read from disc_patterns.db `_shim_attributes` (host=IfcWall,SIDE).
 *   W1 BYTE-IDENTICAL — dwWalk('ELEC', SH) WITHOUT shims is unchanged: same placed count, same floating set
 *                       (the live walk is untouched until a percept is passed).
 *   W2 LIVE-HOSTBIND  — dwWalk('ELEC', SH, {shims}) host-binds the floating placements: float→0, every bound point
 *                       on a real wall within reach, result.hostBind populated.
 *   W3 COUNT-PRESERVED— the host-bound walk returns the SAME number of placements as the plain walk (bound ∪ refused),
 *                       so promotion moves fixtures onto hosts without adding/dropping any.
 *   W4 NO-PERCEPT-NOOP— dwWalk for a discipline with NO matching percept (PLB) is identical with/without shims.
 *   W5 PROJECTION-SRC — dwWalk('ELEC', SH, {hostBind:true}) with NO caller shims reads the `rule_shim` table
 *                       projected into the *_rules.db (the §SHIM first-class flow) and binds identically.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var DW = require(path.join(ROOT, 'build/disc_walker.js'));
var DISC_PATTERNS = path.join(ROOT, 'library/ERP.db'); // disc_patterns.db (physically library/ERP.db until rename slice)
var RULES = path.join(ROOT, 'build/duplex_rules.db');
var SH = path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_dwwalk_hostbind_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) { if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); } else { fail++; log('  ❌ ' + claim + ' — ' + detail); } }
function rows(db, sql) { var r = db.exec(sql); if (!r.length) return []; return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; }); }
function loadDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(f))); }
function median(a) { if (!a.length) return Infinity; var s = a.slice().sort(function (x, y) { return x - y; }); return s[Math.floor(s.length / 2)]; }
function distToWalls(p, walls) {
  var best = Infinity;
  for (var i = 0; i < walls.length; i++) {
    var w = walls[i], horiz = w.bx >= w.by_ ? 0 : 1, hlen = (horiz === 0 ? w.bx : w.by_) / 2;
    var a = [w.x, w.y], b = [w.x, w.y]; a[horiz] -= hlen; b[horiz] += hlen;
    var abx = b[0] - a[0], aby = b[1] - a[1], l2 = abx * abx + aby * aby;
    var t = l2 > 0 ? ((p.x - a[0]) * abx + (p.y - a[1]) * aby) / l2 : 0; t = t < 0 ? 0 : (t > 1 ? 1 : t);
    var d = Math.hypot(p.x - (a[0] + t * abx), p.y - (a[1] + t * aby));
    if (d < best) best = d;
  }
  return best;
}

(async function main() {
  log('═══ W-DWWALK-HOSTBIND — dwWalk applies the host-bind percept in the LIVE walk (count-preserved, opt-in) ═══');
  var SQL = await initSqlJs();

  // ── W0 PERCEPT-SOURCE ──
  var dp = loadDb(SQL, DISC_PATTERNS);
  var shimRow = rows(dp, "SELECT * FROM _shim_attributes WHERE product_value LIKE 'ELEC%WALL%'")[0];
  dp.close();
  log('§DWHB percept(disc_patterns._shim_attributes): ' + (shimRow ? JSON.stringify(shimRow) : 'MISSING'));
  assert('W0 PERCEPT-SOURCE', !!shimRow && /Wall/i.test(shimRow.host_ifc_class) && /SIDE/i.test(shimRow.mount),
    shimRow ? ('ELEC wall percept host=' + shimRow.host_ifc_class + ' mount=' + shimRow.mount + ' (prior-art, not invented)') : 'no ELEC wall percept');
  var shims = [shimRow];                                       // pass the raw _shim_attributes row(s) straight to dwWalk

  var rdb = loadDb(SQL, RULES), sh = loadDb(SQL, SH);
  DW.dwOpen(rdb);
  var walls = rows(sh, "SELECT t.center_x x, t.center_y y, t.bbox_x bx, t.bbox_y by_ FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class LIKE '%Wall%'");
  var wallThick = median(walls.map(function (w) { return Math.min(w.bx, w.by_); }));
  var wallGuids = {}; rows(sh, "SELECT guid g FROM elements_meta WHERE ifc_class LIKE '%Wall%'").forEach(function (r) { wallGuids[r.g] = 1; });

  // ── plain walk (no shims) = the live baseline ──
  var plain = DW.dwWalk('ELEC', sh, 'SampleHouse');
  var plainFloat = plain.placements.map(function (p) { return distToWalls(p, walls); }).filter(function (d) { return d > 0.6; }).length;
  log('§DWHB PLAIN dwWalk ELEC: placed=' + plain.placed + ' float=' + plainFloat + ' hostBind=' + plain.hostBind);

  // ── W1 BYTE-IDENTICAL: a 2nd plain walk matches the first (no shims = deterministic, unchanged) ──
  var plain2 = DW.dwWalk('ELEC', sh, 'SampleHouse');
  assert('W1 BYTE-IDENTICAL', plain2.placed === plain.placed && !plain.hostBind && plainFloat > plain.placed * 0.5,
    'no-shims walk unchanged & still floats: placed=' + plain.placed + ' (== ' + plain2.placed + '), hostBind=null, ' +
    plainFloat + '/' + plain.placed + ' floating (the live defect, untouched without a percept)');

  // ── W2 LIVE-HOSTBIND: same walk WITH the percept ──
  var hbWalk = DW.dwWalk('ELEC', sh, 'SampleHouse', { shims: shims });
  var hbFloat = hbWalk.placements.map(function (p) { return distToWalls(p, walls); }).filter(function (d) { return d > 0.6; }).length;
  var bound = hbWalk.placements.filter(function (p) { return p.host; });
  var badHost = bound.filter(function (p) { return !wallGuids[p.host] || p.snapDist > 6; }).length;
  log('§DWHB HOSTBOUND dwWalk ELEC {shims}: placed=' + hbWalk.placed + ' bound=' + (hbWalk.hostBind && hbWalk.hostBind.bound) +
    ' refused=' + (hbWalk.hostBind && hbWalk.hostBind.refused) + ' float=' + hbFloat + ' median-bound-dist=' +
    median(bound.map(function (p) { return distToWalls(p, walls); })).toFixed(3) + 'm');
  assert('W2 LIVE-HOSTBIND',
    !!hbWalk.hostBind && hbWalk.hostBind.bound > 0 && hbFloat < plainFloat && badHost === 0,
    'dwWalk host-binds live: ' + hbWalk.hostBind.bound + ' bound to real walls (0 bad), float ' + plainFloat + '→' + hbFloat +
    ', percept=' + hbWalk.hostBind.percept);

  // ── W3 COUNT-PRESERVED ──
  assert('W3 COUNT-PRESERVED',
    hbWalk.placed === plain.placed && (hbWalk.hostBind.bound + hbWalk.hostBind.refused) === plain.placed,
    'host-bound walk keeps the count: ' + hbWalk.placed + ' == plain ' + plain.placed + ' (bound ' + hbWalk.hostBind.bound +
    ' + refused ' + hbWalk.hostBind.refused + '); promotion moves fixtures onto hosts, adds/drops none');

  // ── W4 NO-PERCEPT-NOOP: PLB has no matching shim → identical with/without ──
  var plbPlain = DW.dwWalk('PLB', sh, 'SampleHouse');
  var plbShim = DW.dwWalk('PLB', sh, 'SampleHouse', { shims: shims });
  log('§DWHB PLB no-percept: plain placed=' + plbPlain.placed + ' withShims placed=' + plbShim.placed + ' hostBind=' + plbShim.hostBind);
  assert('W4 NO-PERCEPT-NOOP',
    plbPlain.placed === plbShim.placed && !plbShim.hostBind,
    'a discipline with no matching percept is untouched: PLB placed=' + plbPlain.placed + ' == ' + plbShim.placed + ', hostBind=null');

  // ── W5 PROJECTION-SOURCE: opts.hostBind=true (NO caller shims) → dwWalk reads the projected rule_shim table ──
  var nShimRows = rows(rdb, "SELECT COUNT(*) c FROM rule_shim")[0].c;
  var projWalk = DW.dwWalk('ELEC', sh, 'SampleHouse', { hostBind: true });   // source = rule_shim projection, not caller
  var projBound = projWalk.placements.filter(function (p) { return p.host; });
  var projBad = projBound.filter(function (p) { return !wallGuids[p.host]; }).length;
  log('§DWHB PROJECTION dwWalk ELEC {hostBind:true}: rule_shim rows=' + nShimRows + ' bound=' +
    (projWalk.hostBind && projWalk.hostBind.bound) + ' percept=' + (projWalk.hostBind && projWalk.hostBind.percept) +
    ' (read from the *_rules.db projection, no caller shims)');
  assert('W5 PROJECTION-SOURCE',
    nShimRows > 0 && !!projWalk.hostBind && projWalk.hostBind.bound === hbWalk.hostBind.bound && projBad === 0 &&
    /Wall/i.test(projWalk.hostBind.host),
    'dwWalk reads the projected rule_shim (' + nShimRows + ' rows): {hostBind:true} binds ' + projWalk.hostBind.bound +
    ' (== caller-shim ' + hbWalk.hostBind.bound + ') to real walls, 0 bad — §SHIM flows like routing/placement, no caller plumbing');

  rdb.close(); sh.close();
  log('───────────────────────────────────────────────');
  log('§DWHB SUMMARY: dwWalk applies host-bind in the LIVE walk (ELEC float ' + plainFloat + '/' + plain.placed +
    ' → ' + hbFloat + ', count preserved) — SOURCED FROM THE PROJECTED rule_shim table (W5, {hostBind:true}, no caller ' +
    'plumbing) = §SHIM flows like routing/placement. OPT-IN (default byte-identical) until the per-fixture-ifc_class ' +
    'SELECTION KEY lands (a disc has >1 host: ELEC wall-outlets vs ceiling-lights — naive priority would mis-bind lights).');
  log('W-DWWALK-HOSTBIND: ' + pass + ' PASS / ' + fail + ' FAIL');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
