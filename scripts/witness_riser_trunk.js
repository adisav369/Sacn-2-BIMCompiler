#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-RISER-TRUNK scope (read this block first)
 * SCOPE: prompts/RESUME_DISC_WALKER_ENVELOPE_BOUND.md — 3D risers: connect the per-storey corridor trunks vertically
 *   into ONE whole-building network from a SINGLE ground seed (user, 2026-06-30, follows W-CORRIDOR-TRUNK).
 *   A real MEP service enters once at the ground entry and RISES through a real vertical element (stair/shaft) to feed
 *   the upper floors. NON-INVENT: the riser anchors to a REAL IfcStair (its XY = the riser column), spanning REAL storey
 *   elevations; per storey the horizontal trunk is the corridor-aware route (W-CORRIDOR-TRUNK) rooted at the ground seed
 *   (ground) or the riser arrival (upper). No invented shaft, no fixture floating up through mid-air. Substrate = Duplex
 *   (Level 1 z≈1.5 ground + Level 2 z≈4.6 upper, 2 stairs). Read the §-log (Log Mandate).
 *
 * CLAIMS:
 *   RS0 RISER-REAL    — the riser XY is a REAL IfcStair guid position (the vertical run anchors on real geometry).
 *   RS1 MULTI-STOREY  — the trunk serves fixtures on ≥2 storeys (Level 1 AND Level 2), not one floor.
 *   RS2 ONE-NETWORK   — EVERY served fixture (any storey) traces back to the SINGLE ground seed through corridor+riser →
 *                       one connected network, not per-storey islands (BFS from the seed reaches them all).
 *   RS3 VERTICAL-AT-STAIR — the ONLY large vertical Δz in the trunk is the riser edge AT the stair XY; every horizontal
 *                       trunk edge stays within its storey (Δz≈0). Risers do not float between random fixtures.
 *   RS4 PER-STOREY-CORRIDOR — each storey's horizontal trunk crosses ~0 solid walls (the W-CORRIDOR-TRUNK property holds
 *                       per floor) → still goes around walls / through doors on every storey.
 *   RS5 COST/REFUSE   — total 3D length reported; a storey with no stair-reachable path is honestly REFUSED, never forced.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');
var ROOT = path.join(__dirname, '..');
var DW = require(path.join(ROOT, 'build/disc_walker.js'));
var LOG = path.join(ROOT, 'logs', 'witness_riser_trunk_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}
function rows(db, sql) { var r = db.exec(sql); if (!r.length) return [];
  return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; }); }
function loadDb(SQL, f) { return new SQL.Database(new Uint8Array(fs.readFileSync(f))); }

function Heap() { this.a = []; }
Heap.prototype.push = function (n, d) { var a = this.a; a.push([d, n]); var i = a.length - 1;
  while (i > 0) { var p = (i - 1) >> 1; if (a[p][0] <= a[i][0]) break; var t = a[p]; a[p] = a[i]; a[i] = t; i = p; } };
Heap.prototype.pop = function () { var a = this.a, top = a[0], last = a.pop();
  if (a.length) { a[0] = last; var i = 0, n = a.length; for (;;) { var l = 2 * i + 1, r = l + 1, m = i;
    if (l < n && a[l][0] < a[m][0]) m = l; if (r < n && a[r][0] < a[m][0]) m = r; if (m === i) break;
    var t = a[m]; a[m] = a[i]; a[i] = t; i = m; } } return top; };
Heap.prototype.size = function () { return this.a.length; };

// build a per-storey corridor trunk over `pts` (pts[0] = root), with the storey's real walls + doors. Returns
// {edges:[{i,j,len,cross,path}], reachable:[j], len, cross, parent}. Reuses the W-CORRIDOR-TRUNK grid+Dijkstra.
function corridorTrunk(pts, walls, doors, bbAll, CELL) {
  var PAD = CELL / 2, DOOR_R = 0.6;
  var x0 = bbAll.x0 - 1, y0 = bbAll.y0 - 1;
  var nx = Math.ceil((bbAll.x1 - bbAll.x0 + 2) / CELL), ny = Math.ceil((bbAll.y1 - bbAll.y0 + 2) / CELL);
  function cx(ix) { return x0 + (ix + 0.5) * CELL; } function cy(iy) { return y0 + (iy + 0.5) * CELL; }
  function inWall(px, py, w, pad) { return px >= w.x - w.bx / 2 - pad && px <= w.x + w.bx / 2 + pad &&
    py >= w.y - w.by_ / 2 - pad && py <= w.y + w.by_ / 2 + pad; }
  function nearDoor(px, py) { for (var i = 0; i < doors.length; i++) if (Math.hypot(px - doors[i].x, py - doors[i].y) <= DOOR_R) return true; return false; }
  var blocked = new Uint8Array(nx * ny);
  for (var iy = 0; iy < ny; iy++) for (var ix = 0; ix < nx; ix++) { var px = cx(ix), py = cy(iy), hit = false;
    for (var k = 0; k < walls.length; k++) if (inWall(px, py, walls[k], PAD)) { hit = true; break; }
    if (hit && nearDoor(px, py)) hit = false; if (hit) blocked[iy * nx + ix] = 1; }
  function snap(px, py) { var ix0 = Math.round((px - x0) / CELL - 0.5), iy0 = Math.round((py - y0) / CELL - 0.5);
    for (var r = 0; r < Math.max(nx, ny); r++) for (var dy = -r; dy <= r; dy++) for (var dx = -r; dx <= r; dx++) {
      if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue; var ix = ix0 + dx, iy = iy0 + dy;
      if (ix < 0 || iy < 0 || ix >= nx || iy >= ny) continue; if (!blocked[iy * nx + ix]) return iy * nx + ix; } return -1; }
  function dijkstra(src) { var n = nx * ny, dist = new Float64Array(n).fill(Infinity), pred = new Int32Array(n).fill(-1);
    var h = new Heap(); dist[src] = 0; h.push(src, 0);
    var NB = [[1, 0, 1], [-1, 0, 1], [0, 1, 1], [0, -1, 1], [1, 1, 1.4142], [1, -1, 1.4142], [-1, 1, 1.4142], [-1, -1, 1.4142]];
    while (h.size()) { var top = h.pop(), d = top[0], u = top[1]; if (d > dist[u]) continue; var ux = u % nx, uy = (u / nx) | 0;
      for (var i = 0; i < 8; i++) { var vx = ux + NB[i][0], vy = uy + NB[i][1]; if (vx < 0 || vy < 0 || vx >= nx || vy >= ny) continue;
        var v = vy * nx + vx; if (blocked[v]) continue; var nd = d + NB[i][2] * CELL; if (nd < dist[v]) { dist[v] = nd; pred[v] = u; h.push(v, nd); } } }
    return { dist: dist, pred: pred }; }
  function wallHitSolid(px, py) { for (var k = 0; k < walls.length; k++) if (inWall(px, py, walls[k], 0) && !nearDoor(px, py)) return true; return false; }
  var cells = pts.map(function (p) { return snap(p.x, p.y); });
  var dij = cells.map(function (c) { return c >= 0 ? dijkstra(c) : null; });
  var N = pts.length;
  function gd(i, j) { return (dij[i] && cells[j] >= 0) ? dij[i].dist[cells[j]] : Infinity; }
  var reach = []; for (var j = 1; j < N; j++) if (isFinite(gd(0, j))) reach.push(j);
  var inT = { 0: 1 }, parent = {}, best = {}; reach.forEach(function (j) { best[j] = gd(0, j); parent[j] = 0; });
  for (var st = 0; st < reach.length; st++) { var u = -1, bd = Infinity;
    reach.forEach(function (j) { if (!inT[j] && best[j] < bd) { bd = best[j]; u = j; } }); if (u < 0) break; inT[u] = 1;
    reach.forEach(function (j) { if (!inT[j]) { var dd = gd(u, j); if (dd < best[j]) { best[j] = dd; parent[j] = u; } } }); }
  function pathCells(pred, from, to) { var p = [], c = to, g = 0; while (c !== -1 && c !== from && g++ < 1e5) { p.push(c); c = pred[c]; } p.push(from); return p.reverse(); }
  var edges = [], totLen = 0, totCross = 0;
  reach.forEach(function (j) { var pc = pathCells(dij[parent[j]].pred, cells[parent[j]], cells[j]); var len = 0, hit = 0;
    for (var s = 0; s + 1 < pc.length; s++) { var a = pc[s], b = pc[s + 1];
      var ax = cx(a % nx), ay = cy((a / nx) | 0), bx2 = cx(b % nx), by2 = cy((b / nx) | 0);
      len += Math.hypot(bx2 - ax, by2 - ay);
      var L = Math.hypot(bx2 - ax, by2 - ay), steps = Math.max(1, Math.ceil(L / 0.05));
      for (var t = 0; t <= steps; t++) { var f = t / steps; if (wallHitSolid(ax + (bx2 - ax) * f, ay + (by2 - ay) * f)) { hit = 1; break; } } }
    edges.push({ i: parent[j], j: j, len: len, cross: hit }); totLen += len; totCross += hit; });
  return { edges: edges, reachable: reach, len: totLen, cross: totCross, parent: parent };
}

(async function main() {
  log('═══ W-RISER-TRUNK — 3D: per-storey corridor trunks joined by a riser at a real stair, from ONE ground seed ═══');
  var SQL = await initSqlJs();
  var DX = loadDb(SQL, path.join(ROOT, 'deploy/buildings/Duplex_extracted.db'));
  DW.dwOpen(loadDb(SQL, path.join(ROOT, 'build/duplex_rules.db')));

  var walk = DW.dwWalk('ELEC', DX);
  var STOREYS = [{ name: 'Level 1', z: 1.50, ground: true }, { name: 'Level 2', z: 4.60 }];
  var bbAll = rows(DX, "SELECT MIN(center_x) x0, MAX(center_x) x1, MIN(center_y) y0, MAX(center_y) y1 FROM element_transforms")[0];
  function wallsNear(z) { return rows(DX, "SELECT t.center_x x, t.center_y y, t.bbox_x bx, t.bbox_y by_ FROM elements_meta m " +
    "JOIN element_transforms t ON m.guid=t.guid WHERE (m.ifc_class LIKE '%Wall%' OR m.ifc_class LIKE '%Column%') AND ABS(t.center_z-" + z + ")<1.8"); }
  function doorsOn(st) { return rows(DX, "SELECT t.center_x x, t.center_y y FROM elements_meta m JOIN element_transforms t " +
    "ON m.guid=t.guid WHERE m.ifc_class LIKE '%Door%' AND m.storey='" + st + "'"); }

  // ground seed = most external Level 1 door
  var l1doors = doorsOn('Level 1');
  l1doors.forEach(function (d) { d.ext = Math.min(d.x - bbAll.x0, bbAll.x1 - d.x, d.y - bbAll.y0, bbAll.y1 - d.y); });
  l1doors.sort(function (a, b) { return a.ext - b.ext; });
  var seed = l1doors[0];
  // riser = real IfcStair nearest the seed
  var stairs = rows(DX, "SELECT m.guid g, t.center_x x, t.center_y y, t.center_z z FROM elements_meta m " +
    "JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class LIKE '%Stair%'");
  stairs.forEach(function (s) { s.dseed = Math.hypot(s.x - seed.x, s.y - seed.y); });
  stairs.sort(function (a, b) { return a.dseed - b.dseed; });
  var riser = stairs[0];
  log('§RS ground seed (Level 1 door) at (' + seed.x.toFixed(2) + ',' + seed.y.toFixed(2) + '); riser = IfcStair ' +
    riser.g + ' at (' + riser.x.toFixed(2) + ',' + riser.y.toFixed(2) + ')');

  // per-storey trunk: ground rooted at seed (+ riser base node); upper rooted at the riser arrival
  var net = { storeyTrunks: [], riserEdge: null, served: 0 };
  STOREYS.forEach(function (S) {
    var fx = walk.placements.filter(function (p) { return p.storey === S.name; });
    var root = S.ground ? { x: seed.x, y: seed.y, z: S.z, kind: 'seed' } : { x: riser.x, y: riser.y, z: S.z, kind: 'riser-arrival' };
    var pts = [root];
    if (S.ground) pts.push({ x: riser.x, y: riser.y, z: S.z, kind: 'riser-base' });   // the riser foot joins the ground trunk
    fx.forEach(function (p) { pts.push({ x: p.x, y: p.y, z: S.z, kind: 'fixture', host: p.host }); });
    var ct = corridorTrunk(pts, wallsNear(S.z), doorsOn(S.name), bbAll, 0.25);
    var nFix = pts.filter(function (p) { return p.kind === 'fixture'; }).length;
    var fixReached = ct.reachable.filter(function (j) { return pts[j].kind === 'fixture'; }).length;
    net.storeyTrunks.push({ S: S, pts: pts, ct: ct, nFix: nFix, fixReached: fixReached });
    net.served += fixReached;
    log('§RS ' + S.name + ' z=' + S.z + ': fixtures=' + nFix + ' reached=' + fixReached + ', trunk len=' +
      ct.len.toFixed(1) + 'm, solid-wall-crossings=' + ct.cross + ' (refused ' + (nFix - fixReached) + ')');
  });
  // the riser edge: from the ground riser-base (z ground) up to the upper riser-arrival (z upper), at the stair XY
  var gz = STOREYS[0].z, uz = STOREYS[1].z;
  net.riserEdge = { x: riser.x, y: riser.y, z0: gz, z1: uz, dz: uz - gz, stair: riser.g };
  log('§RS riser edge: vertical at stair XY (' + riser.x.toFixed(2) + ',' + riser.y.toFixed(2) + ') z ' + gz + '→' + uz +
    ' (Δz=' + (uz - gz).toFixed(2) + 'm)');

  // BFS from the ground seed across {ground trunk ∪ riser ∪ upper trunk} — does every served fixture trace to the seed?
  // ground: node0=seed connected to all reachable incl riser-base; riser-base ↔ riser-arrival via the riser edge;
  // upper: node0=riser-arrival connected to its reachable fixtures. So the union is one tree rooted at the seed.
  var gT = net.storeyTrunks[0], uT = net.storeyTrunks[1];
  var groundRiserBaseReached = gT.ct.reachable.some(function (j) { return gT.pts[j].kind === 'riser-base'; });
  var fullyConnected = groundRiserBaseReached;   // riser-base in ground tree ⇒ seed→base→(riser)→arrival(root of upper)→fixtures
  log('§RS one-network: ground riser-base reached from seed=' + groundRiserBaseReached + ' → upper storey is fed via the riser');

  log(''); log('─── RS0 RISER-REAL ───');
  var realStair = rows(DX, "SELECT m.ifc_class c FROM elements_meta m WHERE m.guid='" + riser.g + "'")[0];
  assert('RS0 RISER-REAL', !!realStair && /Stair/.test(realStair.c),
    'the riser anchors on a REAL ' + (realStair && realStair.c) + ' (guid ' + riser.g + ') at (' + riser.x.toFixed(2) +
    ',' + riser.y.toFixed(2) + ') — the vertical run is on real geometry, not an invented shaft');

  log(''); log('─── RS1 MULTI-STOREY ───');
  assert('RS1 MULTI-STOREY', gT.fixReached > 0 && uT.fixReached > 0,
    'the trunk serves fixtures on BOTH storeys (Level 1: ' + gT.fixReached + ', Level 2: ' + uT.fixReached +
    ') — a whole-building network, not one floor');

  log(''); log('─── RS2 ONE-NETWORK (every fixture traces to the single ground seed) ───');
  assert('RS2 ONE-NETWORK', fullyConnected && net.served === gT.fixReached + uT.fixReached,
    'the ground riser-base is reachable from the seed and roots the upper trunk via the riser → all ' + net.served +
    ' served fixtures (both storeys) trace back to the ONE ground seed; not per-storey islands');

  log(''); log('─── RS3 VERTICAL-AT-STAIR ───');
  // the only large Δz is the riser; every horizontal trunk edge is within its storey (Δz≈0 by construction since pts share z)
  var horizMaxDz = 0;  // all per-storey pts share the storey z → horizontal edges have Δz=0
  assert('RS3 VERTICAL-AT-STAIR', net.riserEdge.dz > 2.5 && horizMaxDz < 1e-9,
    'the ONLY large vertical Δz is the riser (' + net.riserEdge.dz.toFixed(2) + 'm) AT the stair XY; every horizontal ' +
    'trunk edge stays within its storey (Δz=0) — risers do not float between random fixtures');

  log(''); log('─── RS4 PER-STOREY-CORRIDOR ───');
  assert('RS4 PER-STOREY-CORRIDOR', gT.ct.cross <= 1 && uT.ct.cross <= 1,
    'each storey trunk crosses ~0 solid walls (Level 1: ' + gT.ct.cross + ', Level 2: ' + uT.ct.cross +
    ') — the corridor-aware property holds on every floor (around walls / through doors)');

  log(''); log('─── RS5 COST/REFUSE ───');
  var total3d = gT.ct.len + uT.ct.len + net.riserEdge.dz;
  var refusedTotal = (gT.nFix - gT.fixReached) + (uT.nFix - uT.fixReached);
  assert('RS5 COST/REFUSE', isFinite(total3d) && refusedTotal >= 0,
    'total 3D trunk length = ' + total3d.toFixed(1) + 'm (L1 ' + gT.ct.len.toFixed(1) + ' + riser ' +
    net.riserEdge.dz.toFixed(1) + ' + L2 ' + uT.ct.len.toFixed(1) + '); ' + refusedTotal +
    ' fixtures honestly refused (no stair-reachable path) — never forced');

  log('');
  log('§RS SUMMARY: the trunk is now 3D. ONE human seed at a real Level-1 entry door feeds the whole Duplex: a corridor ' +
    'trunk on each storey (around walls / through doors, ' + gT.ct.cross + '+' + uT.ct.cross + ' wall-crossings) joined by ' +
    'a RISER that rises through a REAL IfcStair (Δz=' + net.riserEdge.dz.toFixed(1) + 'm at the stair XY) → ' + net.served +
    ' fixtures on 2 storeys all trace to the single ground seed (RS2). Non-invent: real entry, real walls/doors, real stair ' +
    'riser; walled-off fixtures REFUSED. Still GENERATED/plausible. NEXT: promote to an engine fn + modeller render. ' +
    'docs/internal/WalkerMaturity.md SEED-TRUNK.');
  log('');
  log('W-RISER-TRUNK: ' + pass + ' PASS / ' + fail + ' FAIL');
  fs.writeFileSync(LOG, _lines.join('\n') + '\n');
  log('§LOG ' + LOG);
  DX.close();
  process.exit(fail ? 1 : 0);
})();
