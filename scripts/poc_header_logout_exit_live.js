// ⚠ DO NOT REMOVE — W-HEADER-LOGOUT-EXIT (RESUME_IDMP_FIDELITY leg 2). Prove the header carries the iDempiere-
//   native LOGOUT (→ login) and the ⋯ rail carries EXIT (→ the landing page), per the FUNDAMENTAL LAW (the header
//   exposes only iDempiere-native controls; an app-shell "Exit to landing" lives in the pill rail).
//   Source oracle: iDempiere UserPanel.java:126 — a single "Logout" (LabelImageElement) → SessionManager.logout →
//   the login page. Our header mirrors it: the log-out door-arrow glyph (icons.js logOut) + click → showLogin('user').
//   1. #idmp-switch-btn is titled "Log out" (NOT the old "Switch role / Log out") and renders the logOut SVG glyph.
//   2. Clicking it returns to the login overlay (#idmp-login visible) — the logout-to-login affordance.
//   3. The ⋯ pill registry binds `home` to a handler navigating to ../index.html (the landing), and the pill is
//      labelled "Exit" (pills_idmp.json) — NOT "Engine view"/erp.html (the old engine-view target, now retired).
// §-log first. Run: ERP_ROOT=/tmp/wt-header/erp node scripts/poc_header_logout_exit_live.js
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
const SHOT = path.join(process.env.HOME, 'Pictures', 'Screenshots', 'header_logout_exit.png');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/idempiere.html';
  fs.readFile(path.join(ROOT, p), (e, b) => { if (e) { r.writeHead(404); r.end('404'); return; } r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b); });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  let pageErr = 0; pg.on('pageerror', e => { pageErr++; console.log('ERR ' + String(e).slice(0, 160)); });
  let pass = true; const fail = (m) => { pass = false; console.log('  ✗ FAIL: ' + m); };

  console.log('— Header LOGOUT + ⋯ EXIT (RESUME_IDMP_FIDELITY leg 2), GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-header', { timeout: 25000 }).catch(() => fail('no header'));

  // 1 · header LOGOUT button — title + glyph
  const lb = await pg.evaluate(() => {
    const b = document.getElementById('idmp-switch-btn'); if (!b) return { err: 'no #idmp-switch-btn' };
    return { title: b.title, hasSvg: !!b.querySelector('svg'), html: b.innerHTML.slice(0, 60) };
  });
  console.log('  §HEADER-LOGOUT button: ' + JSON.stringify(lb));
  if (lb.err) fail(lb.err);
  else { if (lb.title !== 'Log out') fail('header button title not "Log out": "' + lb.title + '"');
         if (/role|switch/i.test(lb.title)) fail('header button still advertises role-switch: "' + lb.title + '"');
         if (!lb.hasSvg) fail('header logout button missing the SVG glyph (icons.js logOut not injected)'); }

  // screenshot the LOGGED-IN header (logout glyph visible) BEFORE we click it away
  try { fs.mkdirSync(path.dirname(SHOT), { recursive: true }); await pg.screenshot({ path: SHOT.replace('.png', '_header.png'), clip: { x: 0, y: 0, width: 900, height: 64 } }); console.log('  header screenshot → ' + SHOT.replace('.png', '_header.png')); } catch (e) {}

  // 2 · click LOGOUT → login overlay visible
  await pg.click('#idmp-switch-btn').catch(() => fail('could not click logout'));
  await pg.waitForTimeout(300);
  const loginVisible = await pg.evaluate(() => { const o = document.getElementById('idmp-login'); if (!o) return false; const s = getComputedStyle(o); return s.display !== 'none' && s.visibility !== 'hidden'; });
  console.log('  §HEADER-LOGOUT click → login overlay visible=' + loginVisible);
  if (!loginVisible) fail('clicking Logout did not return to the login overlay');

  // 3 · EXIT pill — handler target + label (re-fetch the manifest in-page)
  const exit = await pg.evaluate(async () => {
    const acts = window.IdmpPillActions || {};
    const homeSrc = typeof acts.home === 'function' ? acts.home.toString() : '(not a fn)';
    let pillName = null;
    try { const j = await fetch('pills_idmp.json').then(r => r.json()); const p = (j.pills || j).find ? (j.pills || j).find(x => x.id === 'home') : null; pillName = p ? p.name : null; } catch (e) { pillName = 'ERR:' + e.message; }
    return { homeSrc: homeSrc.replace(/\s+/g, ' ').slice(0, 120), pillName };
  });
  console.log('  §EXIT-PILL home handler: ' + exit.homeSrc);
  console.log('  §EXIT-PILL label: "' + exit.pillName + '"');
  if (!/index\.html/.test(exit.homeSrc)) fail('Exit (home) handler does not navigate to index.html: ' + exit.homeSrc);
  if (/erp\.html|index2\.html/.test(exit.homeSrc)) fail('Exit handler still points at the old target (erp.html/index2.html): ' + exit.homeSrc);
  if (exit.pillName !== 'Exit') fail('the ⋯ pill is not labelled "Exit" (got "' + exit.pillName + '")');

  try { fs.mkdirSync(path.dirname(SHOT), { recursive: true }); await pg.screenshot({ path: SHOT }); console.log('  screenshot → ' + SHOT); } catch (e) { console.log('  (screenshot skipped: ' + e.message + ')'); }

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log(pass
    ? '🟢 W-HEADER-LOGOUT-EXIT PASS — header carries the iDempiere-native Logout (→ login overlay, door-arrow glyph); the ⋯ rail carries Exit → ../index.html (landing); old role-switch/engine-view targets retired; 0 pageerrors'
    : '🔴 W-HEADER-LOGOUT-EXIT FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
