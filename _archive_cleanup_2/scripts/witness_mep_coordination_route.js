#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-MEP-COORD-ROUTE scope (read this block first)
 * SCOPE: prove the CoordinationHandler (mep_coordination.js / docs/MEP_COORDINATION_RULESET.md) is WIRED into
 *   RouteWalker — the discipline-aware clash gate + the discipline-vs-discipline arbiter — on the REAL
 *   SampleHouse. NON-INVENT: it acts (displaces a yielder) ONLY on VERIFIED rules; PENDING rules are logged
 *   advisory and nothing moves. Provenance = deep-research run wsog87r4b (2026-06-22). Read the §-log.
 *
 * CLAIMS (each names the issue it proves):
 *   S1 FP_STRUCT_ENFORCED — sprinkler pipes laid at REAL SH structural-wall faces VIOLATE the cited NFPA 13
 *      §18.4.9 50mm clearance (raw>0); rwClearStructure('FP',…) pushes every one off until resolved=0. The
 *      geometry (wall boxes) is SH's own ARC envelope; the clearance is cited; the move is enforced.
 *   S2 NONFP_TOUCH_OK — the SAME pipes routed as a NON-FP discipline flag STRICTLY FEWER (only the genuine
 *      penetrations): the discipline-aware gate adds the cited 50mm band ONLY for FP — no invented clearance
 *      for ELEC/ACMV vs structure (the existing touch-don't-penetrate behaviour is preserved). The FP−nonFP
 *      delta IS the NFPA clearance band: pipes 30mm off a wall face are fine for ELEC but too close for FP.
 *   S3 ADVISORY_NO_MOVE — real generated SH ACMV vs ELEC ceiling runs cross; rwCoordinate arbitrates
 *      (ACMV holds, ELEC yields) but because the priority ladder is PENDING (sequence REFUTED) it is ADVISORY:
 *      every crossing is logged with provenance and NOTHING is moved (resolved==raw). Honesty under non-invent.
 *   S4 PROVENANCE_GATED — every decision carries {enforce, source}: enforced ⟺ both who-yields AND separation
 *      are VERIFIED. FP/STRUCT enforces; ACMV/ELEC does not. The gate is the provenance, not a guess.
 *   S5 DETERMINISTIC — re-running yields byte-identical counts (no Math.random / Date in the path).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require(path.join(__dirname, '..', 'node_modules', 'sql.js'));

var ROOT = path.join(__dirname, '..');
var RW_SRC = path.join(ROOT, 'deploy/dev/routewalker.js');
var MEP_DB = path.join(ROOT, 'deploy/dev/mep_rw.db');
var COORD = require(path.join(ROOT, 'deploy/dev/mep_coordination.js'));
var BLDG = 'SampleHouse';

function loadRW(SQL, buf) {
  var src = fs.readFileSync(RW_SRC, 'utf8');
  return new Function('SQL', 'B', src + '\n_rwDb=new SQL.Database(new Uint8Array(B));_rwReady=true;\n' +
    'return { place: rwPlaceFixtures, loadRooms: rwLoadRooms, coordinate: rwCoordinate, ' +
    'clearStruct: rwClearStructure, arc: _rwLoadArcEnvelopeFromDb, segDist: _rwSegDist };')(SQL, buf);
}
// connect same-discipline fixtures into a nearest-neighbour ceiling run (the routed main for that service)
function runFromFixtures(fix) {
  var pts = fix.map(function (f) { return [f.x, f.y, f.z]; });
  pts.sort(function (a, b) { return a[0] - b[0] || a[1] - b[1]; });
  var segs = [];
  for (var i = 0; i < pts.length - 1; i++) segs.push({ from: pts[i].slice(), to: pts[i + 1].slice() });
  return segs;
}

(function () {
  var pass = true; var fail = function (m) { pass = false; console.log('  ✗ ' + m); };
  initSqlJs().then(function (SQL) {
    var buf = fs.readFileSync(MEP_DB);
    var rw = loadRW(SQL, buf);

    // ── REAL SampleHouse structural wall solids (its own ARC envelope, the clash-gate source) ──
    var wallBoxes = rw.arc(BLDG).filter(function (a) { return Math.min(a.w, a.d) < 0.5 && a.h > 1.5; });
    console.log('§WMR_WALLS building=' + BLDG + ' wallSolids=' + wallBoxes.length);
    if (!wallBoxes.length) { fail('no real wall solids — cannot test the structural gate'); }

    // ── S1/S2: lay a pipe 30mm OUTSIDE each real wall's thin face (no penetration, but inside the 50mm FP band) ──
    // 30mm > the touch tol (so a non-FP service is fine) but < the cited NFPA 50mm (so FP is flagged).
    var GAP = 0.03;
    function pipesNearWalls() {
      return wallBoxes.map(function (a) {
        if (a.w <= a.d) {                                   // wall thin in X → stand the pipe off the X face, run along Y
          var x = a.cx + (a.w / 2 + GAP);
          return { from: [x, a.cy - 0.3, a.cz], to: [x, a.cy + 0.3, a.cz] };
        }
        var y = a.cy + (a.d / 2 + GAP);                     // wall thin in Y → stand off the Y face, run along X
        return { from: [a.cx - 0.3, y, a.cz], to: [a.cx + 0.3, y, a.cz] };
      });
    }
    var fpSegs = pipesNearWalls();
    var r1 = rw.clearStruct(fpSegs, 'FP', wallBoxes, COORD);          // enforced 50mm
    console.log('§WMR_S1 FP vs STRUCT raw=' + r1.raw + ' resolved=' + r1.resolved + ' moved=' + r1.moved + ' tol=' + (r1.tol*1000).toFixed(0) + 'mm enforced=' + r1.enforced);
    if (!(r1.enforced && r1.raw > 0 && r1.resolved === 0 && r1.moved === r1.raw))
      fail('S1 FP↔STRUCT not enforced/cleared (raw=' + r1.raw + ' resolved=' + r1.resolved + ' moved=' + r1.moved + ')');

    var elecSegs = pipesNearWalls();                                       // SAME geometry, non-FP discipline
    var r2 = rw.clearStruct(elecSegs, 'ELEC', wallBoxes, COORD);      // touch allowed (no cited standoff)
    console.log('§WMR_S2 ELEC vs STRUCT raw=' + r2.raw + ' enforced=' + r2.enforced +
      ' (touch-don\'t-penetrate, no invented clearance) — FP−nonFP clearance-band delta=' + (r1.raw - r2.raw));
    if (!(r2.enforced === false && r2.raw < r1.raw))
      fail('S2 non-FP discipline not strictly fewer than FP (FP raw=' + r1.raw + ' nonFP raw=' + r2.raw + ' enforced=' + r2.enforced + ')');

    // ── S3/S4: real generated SampleHouse ACMV vs ELEC ceiling runs (discipline-vs-discipline) ──
    var rooms = rw.loadRooms(BLDG);
    var fx = rw.place(rooms, { buildingName: BLDG });
    var byDisc = {}; fx.forEach(function (f) { (byDisc[f.disc] = byDisc[f.disc] || []).push(f); });
    var routes = { ACMV: runFromFixtures(byDisc.ACMV || []), ELEC: runFromFixtures(byDisc.ELEC || []) };
    console.log('§WMR_ROUTES ACMV_fix=' + (byDisc.ACMV||[]).length + ' ELEC_fix=' + (byDisc.ELEC||[]).length +
      ' ACMV_segs=' + routes.ACMV.length + ' ELEC_segs=' + routes.ELEC.length);
    var r3 = rw.coordinate(routes, COORD);
    if (!r3) { fail('S3 rwCoordinate returned null'); }
    else {
      var acmvElec = r3.decisions.filter(function (d) { return (d.a==='ACMV'&&d.b==='ELEC')||(d.a==='ELEC'&&d.b==='ACMV'); })[0];
      console.log('§WMR_S3 disc-vs-disc raw=' + r3.rawClashes + ' resolved=' + r3.resolvedClashes +
        ' enforcedMoves=' + r3.enforced + ' advisoryClashes=' + r3.advisory +
        (acmvElec ? (' [ACMV×ELEC holds=' + acmvElec.holds + ' yields=' + acmvElec.yields + ' enforce=' + acmvElec.enforce + ']') : ' [no ACMV×ELEC crossing]'));
      // S3: advisory rule → NOTHING moved, resolved==raw
      if (r3.enforced !== 0) fail('S3 advisory rule wrongly enforced (moved=' + r3.enforced + ')');
      if (r3.rawClashes !== r3.resolvedClashes) fail('S3 advisory pass changed geometry (raw=' + r3.rawClashes + ' resolved=' + r3.resolvedClashes + ')');
      // S4: if ACMV×ELEC crossed at all, the decision must be ADVISORY (ladder PENDING) with ACMV holding
      if (acmvElec) {
        if (acmvElec.enforce !== false) fail('S4 ACMV×ELEC should be advisory (ladder PENDING)');
        if (acmvElec.holds !== 'ACMV') fail('S4 ACMV should hold over ELEC by rank');
      } else {
        console.log('  (note) ACMV and ELEC runs did not cross within 300mm on this SH — S4 asserted via handler');
      }
    }
    // S4 also asserted directly on the handler (provenance gate)
    var aFP = COORD.arbitrate('FP', 'STRUCT'), aAE = COORD.arbitrate('ACMV', 'ELEC');
    console.log('§WMR_S4 enforce FP/STRUCT=' + aFP.enforce + ' ACMV/ELEC=' + aAE.enforce);
    if (!(aFP.enforce === true && aAE.enforce === false)) fail('S4 provenance gate wrong (FP/STRUCT=' + aFP.enforce + ' ACMV/ELEC=' + aAE.enforce + ')');

    // ── S5: deterministic ──
    var fp2 = pipesNearWalls(); var r1b = rw.clearStruct(fp2, 'FP', wallBoxes, COORD);
    var det = r1b.raw === r1.raw && r1b.resolved === r1.resolved && r1b.moved === r1.moved;
    console.log('§WMR_S5 deterministic=' + det);
    if (!det) fail('S5 non-deterministic');

    console.log('\n  §WMR_VERDICT ' + (pass ? 'PASS' : 'FAIL') + ' claims=' + (pass ? '5/5' : '<5/5'));
    console.log('── W-MEP-COORD-ROUTE ' + (pass ? 'PASS' : 'FAIL') + ' ──');
    process.exit(pass ? 0 : 1);
  }).catch(function (e) { console.log('  ✗ EXCEPTION ' + (e && e.stack || e)); console.log('── W-MEP-COORD-ROUTE FAIL ──'); process.exit(1); });
})();
