#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-HOSTBIND-AGNOSTIC scope (read this block first)
 * SCOPE: Prove that `disc_walker.hostBind` is now HOST-TYPE-AGNOSTIC — it drives the host class + mount face from
 *   the shim percept instead of hard-targeting walls — by witnessing it on TWO real host types in ONE run:
 *     (A) ELEC outlets → IfcWall, mount=SIDE   (the SH anti-float fix, via the GENERALIZED path = regression).
 *     (B) SC vent grilles → IfcWindow, mount=CENTER  (the 13 real `vent. rooster` IfcDistributionElement on
 *         SampleCastle, host-bound to their same-storey window + a MEASURED vertical rise).
 *   This is the resolution of the §SC fork (RESUME_DISC_WALKER_ENVELOPE_BOUND.md §SC ADDENDUM 2026-06-30): SC is
 *   NOT a networked (class-1) ACMV oracle — there is no duct↔grille topology in the source — but its 13 grilles
 *   ARE a host-bound-standalone (class-2) PLACEMENT oracle. No vent extraction, no fabricated ducts.
 *
 *   NON-INVENT: walls & windows are REAL target geometry. The grille→window rule (host=IfcWindow, mount=CENTER,
 *   offset, same_storey) is MINED from the 13 real grilles, then APPLIED to reproduce them — this is SELF-
 *   CONSISTENCY (mined-then-applied-to-same), the SAME honesty status as the routing rules; reported as such,
 *   NOT cross-building generalization. A grille with no same-storey window in reach is REFUSED (counted), never
 *   forced onto a window. Read the §-log after the run; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   H0 MINED-RULE      — the grille→window mount rule (mount face + offset) is MINED from the 13 real grilles with
 *                        a TIGHT spread (MAD≈0), not a hand-picked constant. Records VENT_WINDOW_SHIM for promotion (disc-mapping deferred to the rule_shim projection).
 *   H1 WALL-REGRESSION — the generalized hostBind reproduces the wall/SIDE result UNCHANGED (SH ELEC: 36 bound,
 *                        median ≤ wall thickness, float→0). Generalization didn't break class-2-wall.
 *   H2 WINDOW-ASSOC    — the 13 grilles ARE host-bound to windows: N/13 sit within reach of a same-storey window
 *                        in plan (measured; honest count, the rest are not window-co-located → refused).
 *   H3 REPRODUCE       — host-binding the window-associated grilles reproduces their REAL position (XY + Z) within
 *                        tolerance (self-consistency walk-back against the real grilles).
 *   H4 NON-INVENT      — every bound grille carries a REAL window guid + snapDist ≤ reach; 0 fabricated hosts.
 *   H5 REFUSE          — grilles with no same-storey window in reach are REFUSED (counted), never forced.
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
var SC = path.join(ROOT, 'deploy/buildings/SampleCastle_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_hostbind_agnostic_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}
function rows(db, sql) {
  var r = db.exec(sql); if (!r.length) return [];
  return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; });
}
function loadDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(f))); }
function median(a) { if (!a.length) return Infinity; var s = a.slice().sort(function (x, y) { return x - y; }); return s[Math.floor(s.length / 2)]; }
function mad(a) { var m = median(a); return median(a.map(function (v) { return Math.abs(v - m); })); }
// §BUG-A-TRUE-MIDPOINT: element_transforms.center is NOT a reliable wall midpoint (RESUME_DISC_WALKER_
// ENVELOPE_BOUND.md) -- ground truth here must use the TRUE (mesh-reconstructed) midpoint via
// DW._trueMidpoint, same as hostBind() itself now does, or this checker stale-compares the FIXED code's
// output against the OLD/wrong wall line (verify-the-checker, not just the code under test).
function distToWalls(p, walls, bdb) {
  var best = Infinity;
  for (var i = 0; i < walls.length; i++) {
    var w = walls[i], horiz = w.bx >= w.by_ ? 0 : 1, hlen = (horiz === 0 ? w.bx : w.by_) / 2;
    var mid = (bdb && DW._trueMidpoint) ? DW._trueMidpoint(bdb, w.g, w) : { x: w.x, y: w.y };
    var a = [mid.x, mid.y], b = [mid.x, mid.y]; a[horiz] -= hlen; b[horiz] += hlen;
    var abx = b[0] - a[0], aby = b[1] - a[1], l2 = abx * abx + aby * aby;
    var t = l2 > 0 ? ((p.x - a[0]) * abx + (p.y - a[1]) * aby) / l2 : 0; t = t < 0 ? 0 : (t > 1 ? 1 : t);
    var d = Math.hypot(p.x - (a[0] + t * abx), p.y - (a[1] + t * aby));
    if (d < best) best = d;
  }
  return best;
}

(async function main() {
  log('═══ W-HOSTBIND-AGNOSTIC — host-type-agnostic hostBind on TWO real host types (ELEC→wall SIDE, grille→window CENTER) ═══');
  var SQL = await initSqlJs();

  // ════════════════════════════════════════════════════════════════════════════════════
  // (A) ELEC → WALL (SIDE) — regression of W-ELEC-HOSTBIND through the GENERALIZED path
  // ════════════════════════════════════════════════════════════════════════════════════
  var dp = loadDb(SQL, DISC_PATTERNS);
  var wshim = rows(dp, "SELECT * FROM _shim_attributes WHERE product_value LIKE 'ELEC%WALL%'")[0];
  dp.close();
  var shimWall = {
    host_ifc_class: wshim ? wshim.host_ifc_class : 'IfcWall',
    mount: wshim ? wshim.mount : 'SIDE',
    offset_m: wshim && wshim.offset_mm != null ? wshim.offset_mm / 1000 : 0,
    height_m: wshim && wshim.height_mm ? wshim.height_mm / 1000 : null,
    reach_m: 6
  };
  var rdb = loadDb(SQL, RULES), sh = loadDb(SQL, SH);
  DW.dwOpen(rdb);
  var sub = DW.substrate(sh);
  var storeyZ = {}; sub.forEach(function (st) { storeyZ[st.name] = st.z; });
  var placedElec = DW.place('ELEC', sub, sh).map(function (p) { p.storeyZ = storeyZ[p.storey]; return p; });
  var shWalls = rows(sh, "SELECT m.guid g, t.center_x x, t.center_y y, t.bbox_x bx, t.bbox_y by_, t.rotation_x rx, t.rotation_y ry, t.rotation_z rot FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class LIKE '%Wall%'");
  var wallThick = median(shWalls.map(function (w) { return Math.min(w.bx, w.by_); }));
  var beforeD = placedElec.map(function (p) { return distToWalls(p, shWalls, sh); });
  var beforeFloat = beforeD.filter(function (d) { return d > 0.6; }).length;
  var hbW = DW.hostBind(placedElec, sh, shimWall);
  var afterD = hbW.bound.map(function (p) { return distToWalls(p, shWalls, sh); });
  var afterFloat = afterD.filter(function (d) { return d > 0.6; }).length;
  var afterMedW = median(afterD);
  var shWallGuids = {};
  rows(sh, "SELECT guid g FROM elements_meta WHERE ifc_class LIKE '%Wall%'").forEach(function (r) { shWallGuids[r.g] = 1; });
  var badW = hbW.bound.filter(function (p) { return !p.host || !shWallGuids[p.host]; }).length;
  log('§HBA-WALL host=' + shimWall.host_ifc_class + ' mount=' + shimWall.mount + ' · ELEC placed=' + placedElec.length +
    ' float=' + beforeFloat + ' → bound=' + hbW.bound.length + ' refused=' + hbW.refused +
    ' afterFloat=' + afterFloat + ' median=' + afterMedW.toFixed(3) + 'm (wall½=' + (wallThick / 2).toFixed(3) + 'm)');
  assert('H1 WALL-REGRESSION',
    hbW.bound.length === 36 && afterFloat === 0 && afterMedW <= wallThick && badW === 0 &&
    (hbW.bound.length + hbW.refused) === placedElec.length,
    'generalized hostBind reproduces wall/SIDE: ' + hbW.bound.length + ' bound (=36), float ' + beforeFloat + '→0, median ' +
    afterMedW.toFixed(3) + 'm ≤ wall thickness ' + wallThick.toFixed(3) + 'm, 0 fabricated, bound+refused=placed');
  rdb.close(); sh.close();

  // ════════════════════════════════════════════════════════════════════════════════════
  // (B) SC VENT GRILLES → WINDOW (CENTER) — class-2 host-bound PLACEMENT walk-back
  // ════════════════════════════════════════════════════════════════════════════════════
  var sc = loadDb(SQL, SC);
  var grilles = rows(sc, "SELECT m.guid g, m.storey st, t.center_x x, t.center_y y, t.center_z z " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class='IfcDistributionElement'");
  var scWins = rows(sc, "SELECT m.guid g, m.storey st, t.center_x x, t.center_y y, t.center_z z, t.bbox_z bz " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class='IfcWindow'");
  var winGuids = {}; scWins.forEach(function (w) { winGuids[w.g] = w; });
  log('§HBA-GRILLE SC grilles(vent. rooster)=' + grilles.length + ' · windows=' + scWins.length);

  // ── H0 MINE the rule from the real grilles: nearest SAME-STOREY window in plan, measure dz to its centre ──
  var REACH = 0.6;
  var assoc = [], dzC = [], xySnap = [];
  grilles.forEach(function (gr) {
    var best = Infinity, bw = null;
    scWins.forEach(function (w) { if (w.st !== gr.st) return; var d = Math.hypot(gr.x - w.x, gr.y - w.y); if (d < best) { best = d; bw = w; } });
    if (bw && best <= REACH) { assoc.push({ gr: gr, w: bw, xy: best }); dzC.push(gr.z - bw.z); xySnap.push(best); }
  });
  var offCenter = median(dzC), spread = mad(dzC);
  log('§HBA-GRILLE MINED rule: host=IfcWindow mount=CENTER same_storey · ' + assoc.length + '/' + grilles.length +
    ' window-associated (XY≤' + REACH + 'm, median snap=' + median(xySnap).toFixed(3) + 'm) · z-rise off window centre median=' +
    offCenter.toFixed(3) + 'm MAD=' + spread.toFixed(3) + 'm');
  assert('H0 MINED-RULE',
    assoc.length >= 5 && spread <= 0.01 && isFinite(offCenter),
    'the grille→window rise is a TIGHT measured rule (' + assoc.length + ' samples, offset=' + offCenter.toFixed(3) +
    'm, MAD=' + spread.toFixed(3) + 'm ≤ 0.01) — VENT_WINDOW_SHIM | IfcWindow | CENTER | ' + Math.round(offCenter * 1000) + 'mm (promote to _shim_attributes)');

  // ── H2 WINDOW-ASSOC (count is the honest measured association) ──
  assert('H2 WINDOW-ASSOC',
    assoc.length > 0 && assoc.length <= grilles.length,
    assoc.length + '/' + grilles.length + ' grilles are window-co-located on their own storey (median plan snap ' +
    median(xySnap).toFixed(3) + 'm); the other ' + (grilles.length - assoc.length) + ' are not → will be refused (honest)');

  // ── APPLY the mined rule: feed the grilles' real XY+storey (known at placement) with z STRIPPED; hostBind recomputes z ──
  var shimWin = { host_ifc_class: 'IfcWindow', mount: 'CENTER', offset_m: offCenter, same_storey: true, reach_m: REACH };
  var placements = grilles.map(function (gr) { return { disc: 'ACMV', ifc_class: 'IfcDistributionElement', x: gr.x, y: gr.y, z: null, storey: gr.st, _truth: gr }; });
  var hbG = DW.hostBind(placements, sc, shimWin);
  log('§HBA-GRILLE hostBind: host=' + hbG.hostClass + ' mount=' + hbG.mount + ' → ' + hbG.bound.length + ' bound, ' + hbG.refused + ' refused');

  // ── H3 REPRODUCE — bound grille position matches the real grille (XY co-located + Z within tol) ──
  // match each bound result back to its truth grille by (host window + nearest real grille xy)
  var truthByXY = grilles.slice();
  var resid = [];
  hbG.bound.forEach(function (b) {
    var best = Infinity, tg = null;
    truthByXY.forEach(function (gr) { if (gr.st !== b.storey) return; var d = Math.hypot(gr.x - b.x, gr.y - b.y); if (d < best) { best = d; tg = gr; } });
    if (tg) resid.push({ xy: Math.hypot(tg.x - b.x, tg.y - b.y), dz: Math.abs(tg.z - b.z) });
  });
  var medXY = median(resid.map(function (r) { return r.xy; }));
  var medDZ = median(resid.map(function (r) { return r.dz; }));
  var maxDZ = Math.max.apply(null, resid.map(function (r) { return r.dz; }));
  log('§HBA-GRILLE REPRODUCE: bound vs real grilles — median XY resid=' + medXY.toFixed(3) + 'm, median |Δz|=' +
    medDZ.toFixed(3) + 'm, max |Δz|=' + maxDZ.toFixed(3) + 'm (self-consistency: mined-then-applied-to-same)');
  assert('H3 REPRODUCE',
    hbG.bound.length > 0 && medXY <= 0.05 && maxDZ <= 0.05,
    'host-bind reproduces the ' + hbG.bound.length + ' window-grilles: XY resid ' + medXY.toFixed(3) + 'm ≤ 0.05, max |Δz| ' +
    maxDZ.toFixed(3) + 'm ≤ 0.05 (SELF-CONSISTENCY — same status as mined-then-applied routing rules, NOT cross-building)');

  // ── H4 NON-INVENT — every bound grille carries a real window guid within reach ──
  var badG = hbG.bound.filter(function (b) { return !b.host || !winGuids[b.host] || b.snapDist > REACH; }).length;
  log('§HBA-GRILLE NON-INVENT: ' + (hbG.bound.length - badG) + '/' + hbG.bound.length + ' bound grilles carry a REAL window guid ≤ reach');
  assert('H4 NON-INVENT',
    badG === 0 && hbG.bound.length > 0,
    'every bound grille traces a real IfcWindow guid + snapDist ≤ ' + REACH + 'm (' + badG + ' bad); zero fabricated windows');

  // ── H5 REFUSE — non-window-co-located grilles refused, never forced ──
  log('§HBA-GRILLE REFUSE: ' + hbG.refused + ' grilles had no same-storey window ≤ ' + REACH + 'm → refused (counted, not forced onto a window)');
  assert('H5 REFUSE',
    (hbG.bound.length + hbG.refused) === grilles.length && hbG.refused === (grilles.length - assoc.length),
    'bound + refused = grilles (' + hbG.bound.length + '+' + hbG.refused + '=' + grilles.length + '); refusals == non-associated (' +
    (grilles.length - assoc.length) + '), never fabricated');
  sc.close();

  log('───────────────────────────────────────────────');
  log('§HBA SUMMARY: hostBind is HOST-AGNOSTIC. (A) ELEC→IfcWall/SIDE reproduces the anti-float fix UNCHANGED (' +
    hbW.bound.length + ' bound, float→0). (B) SC 13 grilles→IfcWindow/CENTER: ' + hbG.bound.length +
    ' bound to real same-storey windows reproducing position (XY ' + medXY.toFixed(3) + 'm, |Δz| ≤ ' + maxDZ.toFixed(3) +
    'm), ' + hbG.refused + ' honestly refused. SC = class-2 host-bound PLACEMENT oracle (NOT class-1 networked). No vent extraction, no fabricated ducts.');
  log('W-HOSTBIND-AGNOSTIC: ' + pass + ' PASS / ' + fail + ' FAIL');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
