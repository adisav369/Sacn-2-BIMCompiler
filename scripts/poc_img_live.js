// ⚠ DO NOT REMOVE — Scope guard (POS_GAP_CLOSE.md §G-2). Read the log after every run.
// W-IMG-LIVE — the browser-IDB half the headless img witnesses could NOT reach (named limit in
// poc_img_folder.log: Map adapter only) + the G-2 stub fix, proven on the LIVE page:
//   1. FOLDER=IDB — on idempiere.html the default ImgStore folder is IndexedDB-backed (kind='idb'),
//      not the headless Map.
//   2. ROUND-TRIP — a register-shaped photo (canvas → JPEG, the exact _downscaleCapture act) keyed
//      by its CONTENT ADDRESS ('sha256:' + SubtleCrypto digest of the decoded bytes — the shipped
//      keying, replacing the content-length stub) puts into the IDB folder and gets back BYTE-EQUAL.
//   3. TIER WALK — the SHIPPED window.ImgStore.resolveImage resolves full (key present, hash verifies)
//      → thumb (key absent, ledger thumb supplied) → none (nothing) — honest about what renders.
//   §FALSIFIER — a TAMPERED blob under a sha256 key is DETECTED (key ≠ recomputed hash):
//      resolveImage demotes to the thumb and reports tampered:true — never renders bytes the key
//      disowns, never fabricates.
//   4. SHIPPED WIRING — the served pos_lens.js carries the sha256 keying and NOT the 'thumb:'
//      length stub; idempiere.html pins img_store?v=2 / pos_lens?v=3; sw.js is v657 (the fix ships).
// Run: ERP_ROOT=/tmp/wt-imgkey/erp node scripts/poc_img_live.js  (default ROOT=~/bim-ootb/erp)
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/idempiere.html';
  fs.readFile(path.join(ROOT, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  pg.on('console', m => { const t = m.text(); if (/^§IMG/.test(t)) console.log('  ' + t); });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 200)));
  let pass = true;
  const fail = (m) => { pass = false; console.log('  ✗ ' + m); };

  console.log('— load idempiere.html (ImgStore script tag, no login needed)');
  await pg.goto(`http://localhost:${port}/idempiere.html`, { waitUntil: 'domcontentloaded' });
  await pg.waitForFunction(() => !!window.ImgStore, null, { timeout: 15000 }).catch(() => fail('window.ImgStore never loaded'));

  const r = await pg.evaluate(async () => {
    const out = {};
    // 1. the default folder on this page is the IDB adapter
    out.kind = window.ImgStore.createFolder().kind;
    // 2. a register-shaped photo: canvas → JPEG (the _downscaleCapture act), content-addressed
    const c = document.createElement('canvas'); c.width = 64; c.height = 64;
    const cx = c.getContext('2d'); cx.fillStyle = '#7a3'; cx.fillRect(0, 0, 64, 64);
    cx.fillStyle = '#fff'; cx.fillRect(8, 8, 24, 40);
    const dataUrl = c.toDataURL('image/jpeg', 0.82);
    const bin = atob(dataUrl.split(',')[1]); const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    const hex = b => { const v = new Uint8Array(b); let s = ''; for (let i = 0; i < v.length; i++) s += (v[i] < 16 ? '0' : '') + v[i].toString(16); return s; };
    const digestOf = blob => blob.arrayBuffer().then(buf => crypto.subtle.digest('SHA-256', buf)).then(hex);
    const key = 'sha256:' + hex(await crypto.subtle.digest('SHA-256', bytes));   // the SHIPPED keying
    out.key = key;
    const blob = new Blob([bytes], { type: 'image/jpeg' });
    out.put = await window.ImgStore.put(key, blob);
    const got = await window.ImgStore.get(key);
    out.gotBytes = got ? new Uint8Array(await got.arrayBuffer()).length : -1;
    out.bytesEqual = got ? hex(await crypto.subtle.digest('SHA-256', await got.arrayBuffer())) === key.slice(7) : false;
    // 3. the tier walk on the SHIPPED resolveImage (digestOf = SubtleCrypto, the pos_lens read path)
    const store = { get: window.ImgStore.get };
    const full = await window.ImgStore.resolveImage(store, key, () => 'THUMB', digestOf);
    const thumb = await window.ImgStore.resolveImage(store, 'sha256:' + 'ab'.repeat(32), () => 'THUMB', digestOf);
    const none = await window.ImgStore.resolveImage(store, 'sha256:' + 'cd'.repeat(32), null, digestOf);
    out.tiers = [full.tier, thumb.tier, none.tier].join('→');
    out.fullTampered = !!full.tampered;
    console.log('§IMG-LIVE folder=' + out.kind + ' put=' + (out.put ? 'Y' : 'N') + ' get=' + (out.bytesEqual ? 'Y' : 'N') +
      ' tier=' + full.tier + ' key=' + key.slice(0, 23) + '… bytes=' + out.gotBytes);
    console.log('§IMG-LIVE tiers=' + out.tiers + ' (full→thumb→none, the honest read ladder)');
    // §FALSIFIER: tamper — DIFFERENT bytes under the SAME sha256 key must be detected
    const evil = new Uint8Array(bytes); evil[0] = evil[0] ^ 0xff;
    await window.ImgStore.put(key, new Blob([evil], { type: 'image/jpeg' }));
    const tampered = await window.ImgStore.resolveImage(store, key, () => 'THUMB', digestOf);
    out.tamperDetected = tampered.tampered === true && tampered.tier === 'thumb' && tampered.src === 'THUMB';
    console.log('§IMG-TAMPER key=' + key.slice(0, 23) + '… detected=' + (tampered.tampered ? 'Y' : 'N') +
      ' tier=' + tampered.tier + ' (key ≠ recomputed hash — thumb keeps rendering, falsifier)');
    return out;
  }).catch(e => ({ err: String(e) }));

  if (r.err) fail('live probe failed: ' + r.err);
  else {
    if (r.kind !== 'idb') fail('folder is not IDB-backed on the live page: ' + r.kind);
    if (!r.put || !r.bytesEqual) fail('IDB put/get round-trip broken (put=' + r.put + ' bytesEqual=' + r.bytesEqual + ')');
    if (!/^sha256:[0-9a-f]{64}$/.test(r.key)) fail('key is not a sha256 content address: ' + r.key);
    if (r.tiers !== 'full→thumb→none') fail('tier walk wrong: ' + r.tiers);
    if (r.fullTampered) fail('verified full-tier read reported tampered');
    if (!r.tamperDetected) fail('§FALSIFIER: tampered blob NOT detected — content addressing is not load-bearing');
  }

  // 4. the SHIPPED wiring (static, against the served tree): stub gone, sha256 in, pins bumped
  console.log('— shipped wiring: stub out, sha256 in, ?v= pins + sw bumped');
  const lens = fs.readFileSync(path.join(ROOT, 'pos_lens.js'), 'utf8');
  const html = fs.readFileSync(path.join(ROOT, 'idempiere.html'), 'utf8');
  const sw = fs.readFileSync(path.join(ROOT, 'sw.js'), 'utf8');
  if (lens.includes("'thumb:' + (dataUrl.length)")) fail('the content-length stub is STILL in pos_lens.js');
  if (!lens.includes("'sha256:' + _hexOf(buf)")) fail('pos_lens.js does not ship the sha256 keying');
  if (!lens.includes('§IMG-TAMPER')) fail('pos_lens.js read path has no tamper log');
  if (!html.includes('img_store.js?v=2') || !html.includes('pos_lens.js?v=3')) fail('?v= pins not bumped in idempiere.html');
  if (!sw.includes("CACHE_VERSION = 'v657'")) fail('sw.js not bumped to v657');
  console.log('  §IMG-LIVE shipped stub=absent sha256-keying=present pins=img_store?v=2,pos_lens?v=3 sw=v657');

  console.log('\n' + (pass ? '🟢 W-IMG-LIVE PASS — the IDB folder round-trips a content-addressed register photo on the live page; resolveImage walks full→thumb→none honestly; a tampered blob is detected and demoted to thumb; the sha256 keying (not the length stub) is what ships.'
    : '🔴 W-IMG-LIVE FAIL'));
  await br.close(); server.close();
  process.exit(pass ? 0 : 1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
