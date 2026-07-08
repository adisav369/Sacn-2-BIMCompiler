#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-TRUE-MIDPOINT scope (read this block first)
 * SCOPE: prove the §BUG-A-TRUE-MIDPOINT fix (RESUME_DISC_WALKER_ENVELOPE_BOUND.md) — `hostBind()`'s SIDE branch
 *   built each host wall's centreline around `element_transforms.center_x/y`, which is the raw IFC placement-line
 *   ORIGIN, not the wall's true bbox midpoint (proven off by up to a wall's own half-length on real, non-centred
 *   walls). `_trueMidpoint()` recovers the real midpoint from the host's OWN mesh geometry
 *   (component_geometries via element_instances — mirrors scripts/test_orientation_proof.py's proven P2
 *   BBOX_RECONSTRUCT: R(rotation) @ local_mesh_bbox_corners + centre = world bbox) wherever that mesh exists,
 *   and reports `verified:false` (never silently trusts the raw centre) where it doesn't.
 *   NON-INVENT: reads this repo's own committed `deploy/buildings/*.db` only. No external/live-tree reads.
 *   Read the §-log after the run; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   T1 RECONSTRUCT-MATCH — `_trueMidpoint` on probe wall 2O2Fr$t4X7Zf8NOew3FNqI (Duplex) reproduces the
 *                          independently-derived true midpoint (8.5915,-9.1085) to float precision, verified:true,
 *                          and DIFFERS from the raw (wrong) center_y by the known 0.81m defect.
 *   T2 CONTAINMENT       — on Duplex/SampleHouse/SampleCastle (100% mesh coverage, measured), the FIXED hostBind
 *                          puts placements no worse outside the TRUE envelope than the OLD (pre-fix, reproduced
 *                          inline) algorithm, and every bound placement reports midVerified:true.
 *   T3 TERMINAL-HONESTY  — Terminal has no component_geometries table at all (measured); hostBind must report
 *                          EVERY host midVerified:false and unverifiedHosts===hostCount — never a false "clean"
 *                          containment read for a building that cannot actually be measured.
 *   T4 REGRESSION        — the existing hostBind witness suite (agnostic / shim-select / elec-hostbind / §DWG /
 *                          §DXG / walkback-mep) still passes 0-FAIL against this fix — additive, not a rewrite.
 *   T5 LIVE-EVIDENCE-DB  — reproduces the ORIGINAL 65/267-outside claim against the ACTUAL evidence DB
 *                          (~/bim-ootb/modeller/Duplex_extracted.db, read-only), not a differently-shaped
 *                          committed copy, and asserts the FIXED hostBind puts 0 outside on that same DB.
 *                          (Correction, 2026-07-09: an earlier pass of this witness only checked
 *                          deploy/buildings/Duplex_extracted.db, an older/flattened extraction that never
 *                          exercised the live DB's `base_geometries` table name -- silently making the fix look
 *                          complete while it did nothing on the DB the bug was measured against. Found by an
 *                          independent reviewer session, re-verified here, not taken on trust.)
 */
'use strict';
var fs = require('fs');
var path = require('path');
var os = require('os');
var initSqlJs = require('sql.js');
var execSync = require('child_process').execSync;

var ROOT = path.join(__dirname, '..');
var DW = require(path.join(ROOT, 'build/disc_walker.js'));
var DUPLEX_RULES = path.join(ROOT, 'build/duplex_rules.db');
var TERMINAL_RULES = path.join(ROOT, 'build/terminal_rules.db');
var DUPLEX_LIVE = path.join(os.homedir(), 'bim-ootb/modeller/Duplex_extracted.db'); // read-only: the ACTUAL 65/267 evidence DB
var LOG = path.join(ROOT, 'logs', 'witness_true_midpoint_' +
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

// The PRE-FIX algorithm, reproduced inline (raw center, no true-midpoint recovery) — the falsifier baseline.
function oldSideBind(placements, hosts, shim) {
  var reach = shim.reach_m, off = shim.offset_m || 0;
  var lines = hosts.map(function (w) {
    var horiz = w.bx >= w.by_ ? 0 : 1;
    var hlen = (horiz === 0 ? w.bx : w.by_) / 2, thick = (horiz === 0 ? w.by_ : w.bx);
    var a = [w.x, w.y], b = [w.x, w.y]; a[horiz] -= hlen; b[horiz] += hlen;
    return { a: a, b: b, horiz: horiz, thick: thick, w: w };
  });
  var bound = [], refused = 0;
  placements.forEach(function (p) {
    var best = Infinity, bl = null, bpt = null;
    for (var i = 0; i < lines.length; i++) {
      var L = lines[i], abx = L.b[0] - L.a[0], aby = L.b[1] - L.a[1];
      var l2 = abx * abx + aby * aby;
      var t = l2 > 0 ? ((p.x - L.a[0]) * abx + (p.y - L.a[1]) * aby) / l2 : 0;
      t = t < 0 ? 0 : (t > 1 ? 1 : t);
      var cx = L.a[0] + t * abx, cy = L.a[1] + t * aby;
      var d = Math.hypot(p.x - cx, p.y - cy);
      if (d < best) { best = d; bl = L; bpt = [cx, cy]; }
    }
    if (!bl || best > reach) { refused++; return; }
    var perpx = p.x - bpt[0], perpy = p.y - bpt[1], pl = Math.hypot(perpx, perpy) || 1;
    var faceOff = bl.thick / 2 + off;
    var fx = bpt[0] + (perpx / pl) * faceOff, fy = bpt[1] + (perpy / pl) * faceOff;
    bound.push({ x: fx, y: fy, z: p.z, host: bl.w.g });
  });
  return { bound: bound, refused: refused };
}

// TRUE envelope: reconstruct every real element's world bbox from its own mesh (component_geometries) where
// available; fall back to center±bbox/2 only when no mesh exists (logged, never silently assumed reliable).
function trueEnvelope(bdb, marginM) {
  var els = rows(bdb, "SELECT t.center_x cx, t.center_y cy, t.center_z cz, COALESCE(t.bbox_x,0) bx, COALESCE(t.bbox_y,0) by_, " +
    "COALESCE(t.rotation_x,0) rx, COALESCE(t.rotation_y,0) ry, COALESCE(t.rotation_z,0) rot, t.guid guid FROM element_transforms t");
  var minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity, verified = 0;
  els.forEach(function (e) {
    var mid = DW._trueMidpoint ? DW._trueMidpoint(bdb, e.guid, { x: e.cx, y: e.cy, z: e.cz, rx: e.rx, ry: e.ry, rot: e.rot }) : null;
    var cx = (mid && mid.verified) ? mid.x : e.cx, cy = (mid && mid.verified) ? mid.y : e.cy;
    if (mid && mid.verified) verified++;
    minX = Math.min(minX, cx - e.bx / 2); maxX = Math.max(maxX, cx + e.bx / 2);
    minY = Math.min(minY, cy - e.by_ / 2); maxY = Math.max(maxY, cy + e.by_ / 2);
  });
  return { minX: minX - marginM, maxX: maxX + marginM, minY: minY - marginM, maxY: maxY + marginM, n: els.length, verified: verified };
}
function outsideCount(pts, env) {
  return pts.filter(function (p) { return p.x < env.minX || p.x > env.maxX || p.y < env.minY || p.y > env.maxY; }).length;
}

function runSubstrate(SQL, label, bdbPath, rulesPath, expectMesh) {
  log('─── ' + label + ' (' + bdbPath + ') ───');
  var bdb = loadDb(SQL, bdbPath), rdb = loadDb(SQL, rulesPath);
  DW.dwOpen(rdb);
  var sub = DW.substrate(bdb);
  var placed = DW.place('ELEC', sub, bdb);
  if (!placed.length) { log('  (no ELEC placements on this substrate — skipping)'); bdb.close(); rdb.close(); return null; }

  var hosts = rows(bdb, "SELECT m.guid g, t.center_x x, t.center_y y, t.bbox_x bx, t.bbox_y by_ FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class LIKE '%Wall%'");
  var meshCount = 0;
  try {
    meshCount = rows(bdb, "SELECT COUNT(*) c FROM element_instances ei JOIN component_geometries cg ON cg.geometry_hash=ei.geometry_hash")[0].c;
  } catch (e) { meshCount = 0; }
  log('  hosts=' + hosts.length + ' ELEC placed=' + placed.length + ' mesh-backed elements=' + meshCount);

  var env = trueEnvelope(bdb, 0.5);
  log('  TRUE envelope (n=' + env.n + ', ' + env.verified + ' mesh-verified, +0.5m margin): x∈[' + env.minX.toFixed(2) + ',' + env.maxX.toFixed(2) +
    '] y∈[' + env.minY.toFixed(2) + ',' + env.maxY.toFixed(2) + ']');

  var shim = { host_ifc_class: 'IfcWall', mount: 'SIDE', offset_m: 0, reach_m: 6 };
  var oldR = oldSideBind(placed, hosts, shim);
  var newR = DW.hostBind(placed, bdb, shim);
  var oldOut = outsideCount(oldR.bound, env), newOut = outsideCount(newR.bound, env);
  var allVerified = newR.bound.every(function (p) { return p.midVerified === true; });
  log('  OLD: ' + oldR.bound.length + ' bound, ' + oldOut + ' outside TRUE envelope');
  log('  NEW: ' + newR.bound.length + ' bound, ' + newOut + ' outside TRUE envelope, unverifiedHosts=' + newR.unverifiedHosts);

  // §REVIEWER-FINDING (2026-07-09): "0 outside before AND after" alone doesn't prove the fix was exercised —
  // the building envelope's own margin can absorb a real, non-trivial per-placement shift without ever
  // crossing "outside." Measure the actual per-host OLD-vs-NEW position shift directly so a vacuous pass
  // (nothing actually moved) is distinguishable from a real one (things moved, just stayed inside).
  var oldByHost = {}, newByHost = {};
  oldR.bound.forEach(function (o) { (oldByHost[o.host] = oldByHost[o.host] || []).push(o); });
  newR.bound.forEach(function (n) { (newByHost[n.host] = newByHost[n.host] || []).push(n); });
  var maxShift = 0, shiftedHost = null;
  Object.keys(newByHost).forEach(function (h) {
    var os = oldByHost[h], ns = newByHost[h];
    if (!os || !ns || os.length !== ns.length) return;
    for (var i = 0; i < ns.length; i++) {
      var d = Math.hypot(os[i].x - ns[i].x, os[i].y - ns[i].y);
      if (d > maxShift) { maxShift = d; shiftedHost = h; }
    }
  });
  log('  max per-placement OLD→NEW shift: ' + maxShift.toFixed(4) + 'm (host=' + shiftedHost + ')');

  if (expectMesh) {
    assert('T2 CONTAINMENT [' + label + ']', newOut <= oldOut,
      'OLD put ' + oldOut + '/' + oldR.bound.length + ' outside; NEW puts ' + newOut + '/' + newR.bound.length + ' outside (must not be worse)');
    assert('T2 ALL-VERIFIED [' + label + ']', newR.unverifiedHosts === 0 && allVerified,
      'unverifiedHosts=' + newR.unverifiedHosts + ', every bound placement midVerified=true (mesh-backed building)');
    log('  ⚠ NOTE [' + label + ']: max-shift=' + maxShift.toFixed(4) + 'm is reported, NOT asserted — a small/zero' +
      ' shift here is a legitimate "this building has no triggering wall" result, not a test failure. Read the' +
      ' number, don\'t assume containment-pass alone proves the fix was exercised.');
  } else {
    assert('T3 TERMINAL-HONESTY [' + label + ']', newR.unverifiedHosts === newR.hostCount && newR.hostCount > 0,
      'unverifiedHosts=' + newR.unverifiedHosts + ' === hostCount=' + newR.hostCount + ' (no mesh source exists — must not claim a verified containment read)');
  }
  bdb.close(); rdb.close();
  return { oldOut: oldOut, newOut: newOut, bound: newR.bound.length };
}

function runExisting(label, script) {
  try {
    var out = execSync('node ' + script, { cwd: ROOT, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
    var failMatch = out.match(/❌/g);
    var nFail = failMatch ? failMatch.length : 0;
    assert('T4 REGRESSION [' + label + ']', nFail === 0, nFail + ' failing assertions in ' + script);
  } catch (e) {
    var out2 = (e.stdout || '') + (e.stderr || '');
    var failMatch2 = out2.match(/❌/g);
    assert('T4 REGRESSION [' + label + ']', false, script + ' threw/non-zero exit — ' + (failMatch2 ? failMatch2.length + ' fails' : e.message.split('\n')[0]));
  }
}

initSqlJs().then(function (SQL) {
  log('═══ W-TRUE-MIDPOINT — §BUG-A-TRUE-MIDPOINT fix (RESUME_DISC_WALKER_ENVELOPE_BOUND.md) ═══');

  // ── T1 RECONSTRUCT-MATCH ──
  var dup = loadDb(SQL, path.join(ROOT, 'deploy/buildings/Duplex_extracted.db'));
  var probe = rows(dup, "SELECT center_x x, center_y y, center_z z, rotation_x rx, rotation_y ry, rotation_z rot FROM element_transforms WHERE guid='2O2Fr$t4X7Zf8NOew3FNqI'")[0];
  var mid = DW._trueMidpoint(dup, '2O2Fr$t4X7Zf8NOew3FNqI', probe);
  log('  probe wall raw center=(' + probe.x.toFixed(4) + ',' + probe.y.toFixed(4) + ') → true midpoint=(' + mid.x.toFixed(4) + ',' + mid.y.toFixed(4) + ') verified=' + mid.verified);
  assert('T1 RECONSTRUCT-MATCH', mid.verified && Math.abs(mid.x - 8.5915) < 0.001 && Math.abs(mid.y - (-9.1085)) < 0.001,
    'expected (8.5915,-9.1085), got (' + mid.x.toFixed(4) + ',' + mid.y.toFixed(4) + ')');
  assert('T1 DEFECT-CONFIRMED', Math.abs(mid.y - probe.y) > 0.5,
    'true midpoint differs from raw center_y by ' + Math.abs(mid.y - probe.y).toFixed(3) + 'm (known ~0.81m defect)');
  dup.close();

  // ── T2 CONTAINMENT (mesh-backed buildings) ──
  runSubstrate(SQL, 'Duplex', path.join(ROOT, 'deploy/buildings/Duplex_extracted.db'), DUPLEX_RULES, true);
  runSubstrate(SQL, 'SampleHouse', path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db'), DUPLEX_RULES, true);
  runSubstrate(SQL, 'SampleCastle', path.join(ROOT, 'deploy/buildings/SampleCastle_extracted.db'), DUPLEX_RULES, true);

  // ── T3 TERMINAL-HONESTY (no mesh source) ──
  runSubstrate(SQL, 'Terminal', path.join(ROOT, 'deploy/buildings/Terminal_extracted.db'), TERMINAL_RULES, false);

  // ── T5 LIVE-EVIDENCE-DB (the ACTUAL 65/267 DB, read-only, never edited) ──
  if (fs.existsSync(DUPLEX_LIVE)) {
    var live = loadDb(SQL, DUPLEX_LIVE), liveRdb = loadDb(SQL, DUPLEX_RULES);
    DW.dwOpen(liveRdb);
    var liveSub = DW.substrate(live);
    var livePlaced = DW.place('ELEC', liveSub, live);
    var liveHosts = rows(live, "SELECT m.guid g, t.center_x x, t.center_y y, t.bbox_x bx, t.bbox_y by_ FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class LIKE '%Wall%'");
    var liveEnv = trueEnvelope(live, 0.5);
    var liveOldOut = outsideCount(oldSideBind(livePlaced, liveHosts, { host_ifc_class: 'IfcWall', mount: 'SIDE', offset_m: 0, reach_m: 6 }).bound, liveEnv);
    var liveNew = DW.hostBind(livePlaced, live, { host_ifc_class: 'IfcWall', mount: 'SIDE', offset_m: 0, reach_m: 6 });
    var liveNewOut = outsideCount(liveNew.bound, liveEnv);
    log('  LIVE Duplex (' + DUPLEX_LIVE + '): envelope n=' + liveEnv.n + ' verified=' + liveEnv.verified +
      ' — OLD ' + liveOldOut + '/' + livePlaced.length + ' outside, NEW ' + liveNewOut + '/' + liveNew.bound.length +
      ' outside, unverifiedHosts=' + liveNew.unverifiedHosts + '/' + liveNew.hostCount);
    assert('T5 LIVE-EVIDENCE-DB REPRODUCE', liveOldOut > 50,
      'OLD reproduces the original defect on the LIVE DB (' + liveOldOut + ' outside, expected ~65)');
    assert('T5 LIVE-EVIDENCE-DB FIXED', liveNewOut === 0 && liveNew.unverifiedHosts === 0,
      'NEW puts ' + liveNewOut + ' outside (expect 0), unverifiedHosts=' + liveNew.unverifiedHosts + ' (expect 0) — fix resolves via base_geometries');
    live.close(); liveRdb.close();
  } else {
    log('  (⚠ ' + DUPLEX_LIVE + ' not found on this machine — T5 skipped, not a PASS)');
  }

  // ── T4 REGRESSION (existing suite unaffected) ──
  runExisting('hostbind-agnostic', 'scripts/witness_hostbind_agnostic.js');
  runExisting('shim-select', 'scripts/witness_shim_select.js');
  runExisting('elec-hostbind', 'scripts/witness_elec_hostbind.js');
  runExisting('dwwalk-hostbind', 'scripts/witness_dwwalk_hostbind.js');
  runExisting('walkback-mep', 'scripts/witness_walkback_mep.js');
  runExisting('DWG', 'build/witness_disc_walk_generalize.js');
  runExisting('DXG', 'build/witness_disc_walk_duplex_generalize.js');

  log('═══ SUMMARY: ' + pass + ' pass, ' + fail + ' fail ═══');
  fs.mkdirSync(path.dirname(LOG), { recursive: true });
  fs.writeFileSync(LOG, _lines.join('\n') + '\n');
  console.log('log: ' + LOG);
  process.exit(fail === 0 ? 0 : 1);
});
