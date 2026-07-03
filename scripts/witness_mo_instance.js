/**
 * W-MO-INSTANCE — the Modeller's per-building EDITABLE INSTANCE (the mo_<key> fork).
 *
 * Drives the SHIPPED OpLog (bonsai_oplog.js) + kernel_ops.js in node (browser globals stubbed) to prove
 * the §MO contract: each resident's signed edits fold into its OWN op-log instance (localStorage key
 * 'mo_<building>'), the loaded meta.db REFERENCE is never mutated by the op-log (separate store), and
 * switching buildings never cross-contaminates.
 *
 *   C1 setModelKey forks a fresh instance — open SampleHouse → mo_SampleHouse, 0 ops
 *   C2 a signed edit folds into THIS instance — commit → 1 row, chain verifies
 *   C3 switching buildings is ISOLATED — open Duplex → 0 ops (SampleHouse's edit not visible)
 *   C4 re-opening restores the building's OWN history — back to SampleHouse → 1 op (not Duplex's)
 *   C5 instances are SEPARATE byte stores — localStorage[mo_SampleHouse] != [mo_Duplex]
 *   C6 the REFERENCE is untouched — the op-log writes ONLY 'mo_*'/default keys, never a buildings/* ref key
 *
 * Non-invent: runs the REAL shipped engine; STR_REANCHOR ops (non-GEOM) make the scene-fold a no-op so
 * the signed kernel_ops chain is exercised without a THREE scene. Pass the outliner/viewer dir as argv[2].
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const VDIR = [process.argv[2], path.join(process.env.HOME || '', 'bim-ootb/viewer')].filter(Boolean).find(d => d && fs.existsSync(path.join(d, 'bonsai_oplog.js')));
if (!VDIR) { console.error('no viewer dir with bonsai_oplog.js (pass as argv[2])'); process.exit(2); }

// ── stub the browser globals the two IIFEs reference ───────────────────────────
const _ls = new Map();
const _written = [];   // ordered keys the op-log persisted under (for C6)
global.localStorage = {
  getItem: k => (_ls.has(k) ? _ls.get(k) : null),
  setItem: (k, v) => { _ls.set(k, v); _written.push(k); },
  removeItem: k => { _ls.delete(k); }
};
global.location = { href: 'file:///modeller.html' };
if (typeof global.crypto === 'undefined') global.crypto = require('crypto').webcrypto;
global.CustomEvent = class { constructor(t) { this.type = t; } };
global.window = {};
window.dispatchEvent = () => {};
window.initSqlJs = () => require(path.join(ROOT, 'node_modules/sql.js'))();   // ignore browser locateFile → node resolves its own wasm
window.Bonsai = { foldChainToScene: async () => ({ solids: 0, triangleCount: 0 }), clearKernelCache() {} };

// load the SHIPPED engine (browser IIFEs assign window.KernelOps + window.Bonsai.oplog)
(0, eval)(fs.readFileSync(path.join(VDIR, 'kernel_ops.js'), 'utf8'));
(0, eval)(fs.readFileSync(path.join(VDIR, 'bonsai_oplog.js'), 'utf8'));
const O = window.Bonsai.oplog;
const rows = () => O.db.exec("SELECT COUNT(*) FROM kernel_ops")[0].values[0][0];

let PASS = 0, FAIL = 0;
const ok = (c, m) => { (c ? PASS++ : FAIL++); console.log('  ' + (c ? '✅' : '❌') + ' ' + m); return c; };

(async () => {
  await O._ensureDb();                                                    // default key (the scratch model)

  const nSH0 = await O.setModelKey('mo_SampleHouse');
  ok(nSH0 === 0 && O._KEY === 'mo_SampleHouse' && rows() === 0, 'C1 FORK — open SampleHouse → key=mo_SampleHouse, 0 ops');

  const c = await O.commit({ op_type: 'STR_REANCHOR', parameters: { datum: 1.0, axis: 'x' } }, {});
  ok(c.verify === true && rows() === 1, 'C2 SIGNED-EDIT — commit folds into mo_SampleHouse → 1 row, chain verify=' + c.verify);

  const nDX = await O.setModelKey('mo_Duplex');
  ok(nDX === 0 && rows() === 0, 'C3 ISOLATED — open Duplex → mo_Duplex 0 ops (SampleHouse edit not visible)');
  await O.commit({ op_type: 'STR_RESPAN', parameters: { datum: 2.0, axis: 'y' } }, {});
  ok(rows() === 1, 'C3b — Duplex carries its own 1 op');

  await O.setModelKey('mo_SampleHouse');
  const back = rows();
  ok(back === 1, 'C4 RESTORE — re-open SampleHouse → its OWN 1 op restored (not Duplex), rows=' + back);
  const v = await window.KernelOps.verifyChain(O.db);
  ok(v.ok === true, 'C4b — restored SampleHouse chain still verifies=' + v.ok);

  const sSH = global.localStorage.getItem('mo_SampleHouse'), sDX = global.localStorage.getItem('mo_Duplex');
  ok(sSH && sDX && sSH !== sDX, 'C5 SEPARATE-STORES — mo_SampleHouse bytes != mo_Duplex bytes (' + (sSH ? sSH.length : 0) + ' vs ' + (sDX ? sDX.length : 0) + ')');

  // C6: the op-log persisted ONLY under default/mo_* keys — never under a reference (buildings/*) key.
  const refKeys = _written.filter(k => /buildings\//.test(k) || /_meta\.db$/.test(k) || /_extracted\.db$/.test(k));
  const allMo = _written.every(k => k === 'bonsai_model_v1' || /^mo_/.test(k));
  ok(refKeys.length === 0 && allMo, 'C6 REFERENCE-UNTOUCHED — op-log wrote only ' + [...new Set(_written)].join(',') + ' (no reference key)');

  console.log('─'.repeat(48));
  console.log('W-MO-INSTANCE: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  process.exit(FAIL ? 1 : 0);
})().catch(e => { console.error('THREW', e); process.exit(1); });
