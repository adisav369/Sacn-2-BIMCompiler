#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-ROUTEWALK-FIXTURES-COLOUR scope (read this block first)
 * SCOPE: step C of the SampleHouse MEP benchmark (RESUME_MODELLER_FOCUS.md) — the 23 auto-placed fixtures must
 *   render in their DISCIPLINE colour (lights warm-yellow, fans cyan, sprinkler red, outlet amber, sanitary blue)
 *   instead of grey box proxies. The colour is the REAL material_rgba already on each fixture (RW_IFC_MAP.rgba);
 *   the work is to THREAD it through the signed fold so it survives scrub/history-replay.
 *
 *   The render itself (THREE material) needs a browser — that leg is the ?routewalk=sh live assertion. This
 *   headless witness proves the DATA + PLUMBING contract that feeds it, with ZERO invention:
 *       rwPlaceFixtures → f.rgba  →  rgbaHex (modeller)  →  parameters.color  →  foldInsert md.color  →  _buildMesh
 *   Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS (each names the issue it proves):
 *   C1 HAS_RGBA   — every one of the 23 SH fixtures carries a parseable 'r,g,b,a' material_rgba (no grey default).
 *   C2 HEX_ROUNDTRIP — the modeller's rgbaHex maps each rgba to a hex int from which r,g,b are exactly recovered.
 *   C3 DISCIPLINE — the recovered colours obey the readable convention AND are distinct per discipline (ELEC light
 *                   warm-yellow, ACMV fan/diffuser cyan, ELEC outlet/switch amber) — so a glance reads the system.
 *   C4 PERSISTED  — the SHIPPING code threads the colour through the fold so it survives a DB round-trip / scrub:
 *                   modeller writes parameters.color, foldInsert echoes parameters.color onto md.color, and
 *                   foldChainToScene prefers md.color over the one fold-level colour. (Proven by reading the code
 *                   that ships — NOT by asserting intent.)
 *   C5 DETERM     — same fixture → same hex on re-run.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
// The SHIPPING code = the bim-ootb worktree the modeller serves from; fall back to the bim-compiler backup copy.
var VIEWER = process.env.VIEWER_DIR || '/tmp/wt-mep-colour/viewer';
function pick(name) { var w = path.join(VIEWER, name); return fs.existsSync(w) ? w : path.join(ROOT, 'deploy/dev', name); }
var RW_SRC = pick('routewalker.js');
var MEP_DB = pick('mep_rw.db');
var LIB_SRC = pick('bonsai_library.js');
var KERNEL_SRC = pick('bonsai_kernel.js');
var MODELLER_SRC = pick('modeller.html');
var BLDG = 'SampleHouse';

function loadRW(SQL, mepBuf) {
  var src = fs.readFileSync(RW_SRC, 'utf8');
  return new Function('SQL', 'B', src + '\n_rwDb=new SQL.Database(new Uint8Array(B));_rwReady=true;\n' +
    'return { loadRooms: rwLoadRooms, placeFixtures: rwPlaceFixtures };')(SQL, mepBuf);
}

// BYTE-FOR-BYTE the conversion in modeller.html autoRouteMEP fixtures loop.
function rgbaHex(s) { var p = String(s || '').split(',').map(Number); var ch = function (i) { return Number.isFinite(p[i]) ? Math.max(0, Math.min(255, p[i] | 0)) : 180; }; return (ch(0) << 16) | (ch(1) << 8) | ch(2); }

(function () {
  var pass = true;
  var fail = function (m) { pass = false; console.log('  ✗ ' + m); };

  initSqlJs().then(function (SQL) {
    console.log('§COL_SRC viewer=' + VIEWER + ' shipping=' + (RW_SRC.indexOf(VIEWER) === 0));
    var rw = loadRW(SQL, fs.readFileSync(MEP_DB));
    var fx = rw.placeFixtures(rw.loadRooms(BLDG));
    console.log('§COL_FX fixtures=' + fx.length);
    if (fx.length !== 23) fail('expected 23 SH fixtures, got ' + fx.length);

    // ─── C1: every fixture carries a real rgba ────────────────────────────────
    var noRgba = fx.filter(function (f) { return !/^\d+,\d+,\d+,\d+$/.test(String(f.rgba || '')); });
    console.log('§COL_C1 withRgba=' + (fx.length - noRgba.length) + '/' + fx.length);
    if (noRgba.length) fail('C1 ' + noRgba.length + ' fixtures missing a parseable rgba');

    // ─── C2: rgbaHex round-trips ──────────────────────────────────────────────
    var badHex = 0;
    fx.forEach(function (f) {
      var p = f.rgba.split(',').map(Number), h = rgbaHex(f.rgba);
      var r = (h >> 16) & 255, g = (h >> 8) & 255, b = h & 255;
      if (r !== p[0] || g !== p[1] || b !== p[2]) badHex++;
    });
    console.log('§COL_C2 roundTripOK=' + (fx.length - badHex) + '/' + fx.length +
      ' sample=' + fx.slice(0, 3).map(function (f) { return f.product + '#' + ('000000' + rgbaHex(f.rgba).toString(16)).slice(-6); }).join(' '));
    if (badHex) fail('C2 ' + badHex + ' fixtures lost colour through rgbaHex');

    // ─── C3: discipline convention + distinctness ─────────────────────────────
    // collect a representative hex per (disc,product)
    var byProd = {};
    fx.forEach(function (f) { byProd[f.product] = { disc: f.disc, hex: rgbaHex(f.rgba), rgb: f.rgba.split(',').map(Number) }; });
    var products = Object.keys(byProd);
    console.log('§COL_C3 products=' + products.map(function (k) { return k + '/' + byProd[k].disc + '#' + ('000000' + byProd[k].hex.toString(16)).slice(-6); }).join(' '));
    // light = warm yellow: R high, G high, B lower (R>=G>B)
    if (byProd.LIGHT) { var L = byProd.LIGHT.rgb; if (!(L[0] >= L[1] && L[1] > L[2])) fail('C3 LIGHT not warm-yellow ' + L); }
    // ceiling fan / diffuser = cyan: B>=G>R
    ['CEILING_FAN', 'SUPPLY_DIFFUSER', 'AIRCON_POINT'].forEach(function (k) {
      if (byProd[k]) { var C = byProd[k].rgb; if (!(C[2] >= C[1] && C[1] >= C[0])) fail('C3 ' + k + ' not cyan ' + C); }
    });
    // outlet/switch = amber: R high, B near-zero
    ['OUTLET', 'SWITCH'].forEach(function (k) {
      if (byProd[k]) { var A = byProd[k].rgb; if (!(A[0] >= 200 && A[2] < 80)) fail('C3 ' + k + ' not amber ' + A); }
    });
    // ELEC light vs ACMV fan must be visually distinct (different hex)
    if (byProd.LIGHT && byProd.CEILING_FAN && byProd.LIGHT.hex === byProd.CEILING_FAN.hex) fail('C3 light and fan share one colour — not readable');

    // ─── C4: the SHIPPING code actually threads it (survives scrub/replay) ─────
    var modeller = fs.readFileSync(MODELLER_SRC, 'utf8');
    var lib = fs.readFileSync(LIB_SRC, 'utf8');
    var kernel = fs.readFileSync(KERNEL_SRC, 'utf8');
    var c4a = /color:\s*rgbaHex\(\s*f\.rgba\s*\)/.test(modeller);                       // persisted in signed params
    var c4b = /color:\s*\(\s*P\.color\s*!=\s*null\s*\?\s*P\.color\s*:/.test(lib);        // foldInsert echoes it onto md.color
    var c4c = /color:\s*md\.color\s*!=\s*null\s*\?\s*md\.color\s*:\s*opts\.color/.test(kernel); // fold prefers per-mesh colour
    console.log('§COL_C4 paramColor=' + c4a + ' foldInsertEcho=' + c4b + ' perMeshWins=' + c4c);
    if (!c4a) fail('C4 modeller does not persist parameters.color = rgbaHex(f.rgba) (per-commit {color} would NOT survive a GEOM_INSERT re-fold)');
    if (!c4b) fail('C4 foldInsert does not echo parameters.color onto the mesh descriptor');
    if (!c4c) fail('C4 foldChainToScene does not prefer per-mesh md.color over the fold-level colour');

    // ─── C5: deterministic ────────────────────────────────────────────────────
    var fx2 = rw.placeFixtures(rw.loadRooms(BLDG));
    var same = fx2.length === fx.length && fx2.every(function (f, i) { return rgbaHex(f.rgba) === rgbaHex(fx[i].rgba); });
    console.log('§COL_C5 identical=' + same);
    if (!same) fail('C5 colour mapping is non-deterministic');

    console.log('§COL_VERDICT ' + (pass ? 'PASS' : 'FAIL') + ' claims=' + (pass ? '5/5' : '<5/5'));
    console.log('── W-ROUTEWALK-FIXTURES-COLOUR ' + (pass ? 'PASS' : 'FAIL') + ' ──');
    process.exit(pass ? 0 : 1);
  }).catch(function (e) {
    console.log('  ✗ EXCEPTION ' + (e && e.stack || e));
    console.log('── W-ROUTEWALK-FIXTURES-COLOUR FAIL ──');
    process.exit(1);
  });
})();
