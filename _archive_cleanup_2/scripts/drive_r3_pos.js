// drive_r3_pos.js — USER-DRIVE (not a witness): render the §ROUND 3 slim rim-drawer pay panel and
// screenshot it as a cashier sees it. Serves /tmp/wt-r3-pos/erp. Read the log + the PNGs after the run.
//   shots: 1 slim panel (rims collapsed) · 2 items-rim expanded · 3 replenish-rim expanded · 4 after a sale
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || '/tmp/wt-r3-pos/erp';
const SHOTS = process.env.HOME + '/Pictures/Screenshots';
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css', '.png': 'image/png' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/idempiere.html';
  // allow sibling ../viewer/* (sfx.js etc.) to resolve like the deployed layout
  let fp = path.join(ROOT, p);
  if (p.startsWith('/viewer/') || p.startsWith('/common/')) fp = path.join(ROOT, '..', p);
  fs.readFile(fp, (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch();
  const pg = await br.newContext({ viewport: { width: 412, height: 892 }, deviceScaleFactor: 2 }).then(c => c.newPage());
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^§POS/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 200)));
  const shot = (n) => pg.screenshot({ path: path.join(SHOTS, 'r3_pos_' + n + '.png') }).then(() => console.log('  📸 r3_pos_' + n + '.png'));

  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-tree .idmp-row.leaf', { state: 'attached', timeout: 25000 });
  await pg.evaluate(() => {
    const dock = document.getElementById('idmp-pill'), trig = document.querySelector('#idmp-pill-trigger,[data-pill-trigger]');
    if (dock && trig && getComputedStyle(dock).display === 'none') trig.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    document.getElementById('pill-pos').dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  });
  await pg.waitForSelector('.pos-card', { timeout: 15000 });

  // ring 3 items so the total + items-drawer have content
  await pg.click('.pos-card[data-pid="124"]');
  await pg.click('.pos-card[data-pid="124"]');
  const t2 = await pg.$$eval('.pos-card', ts => ts[1].getAttribute('data-pid'));
  await pg.click(`.pos-card[data-pid="${t2}"]`);

  // open the pay panel
  await pg.evaluate(() => document.getElementById('pos-pill-payment').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
  await pg.waitForSelector('#pos-float-panel.open', { timeout: 8000 });
  await pg.waitForTimeout(300);
  // assert the slim shape: rims present, drawers collapsed, total in panel
  const shape = await pg.evaluate(() => {
    const rt = document.getElementById('pos-rim-top'), rb = document.getElementById('pos-rim-bottom');
    const ib = document.querySelector('.pos-items-body'), rbody = document.querySelector('.pos-repl-body');
    const tot = document.getElementById('pos-float-total');
    return {
      rims: !!rt && !!rb, itemsCollapsed: ib && getComputedStyle(ib).display === 'none',
      replCollapsed: rbody && getComputedStyle(rbody).display === 'none',
      total: tot && tot.textContent, panelH: document.getElementById('pos-float-panel').getBoundingClientRect().height,
      rimTopTitle: rt && rt.title, rimBotTitle: rb && rb.title
    };
  });
  console.log('  §POS-R3-SHAPE rims=' + shape.rims + ' itemsCollapsed=' + shape.itemsCollapsed +
    ' replCollapsed=' + shape.replCollapsed + ' total=' + shape.total + ' panelH=' + Math.round(shape.panelH) +
    ' rimTop="' + shape.rimTopTitle + '" rimBot="' + shape.rimBotTitle + '"');
  await shot('1_slim');

  // expand the orange items rim
  await pg.evaluate(() => document.getElementById('pos-rim-top').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
  await pg.waitForTimeout(200); await shot('2_items');
  // collapse items, expand the green replenish rim
  await pg.evaluate(() => document.getElementById('pos-rim-top').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
  await pg.evaluate(() => document.getElementById('pos-rim-bottom').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
  await pg.waitForTimeout(200); await shot('3_replenish');
  // collapse it back to slim
  await pg.evaluate(() => document.getElementById('pos-rim-bottom').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));

  // §R3-A2 Pay OK-confirm: tap Pay → ARMS (no commit yet) → screenshot the confirm bar
  await pg.click('#pos-float-tender');
  await pg.waitForTimeout(200);
  const armed = await pg.evaluate(() => {
    const bar = document.getElementById('pos-confirm-bar'), ok = document.getElementById('pos-pay-ok');
    return { shown: bar && getComputedStyle(bar).display !== 'none', label: ok && ok.textContent,
             noReceiptYet: !/✓/.test((document.getElementById('pos-float-receipt') || {}).textContent || '') };
  });
  console.log('  §POS-R3-CONFIRM armed=' + armed.shown + ' okLabel="' + armed.label + '" noCommitYet=' + armed.noReceiptYet);
  await shot('4_pay_armed');

  // tap OK → commits the sale → receipt
  await pg.click('#pos-pay-ok');
  await pg.waitForFunction(() => { const e = document.getElementById('pos-float-receipt'); return e && e.textContent.includes('✓'); }, null, { timeout: 15000 }).catch(() => {});
  await pg.waitForTimeout(300); await shot('5_paid');
  const paid = await pg.$eval('#pos-float-receipt', e => e.textContent).catch(() => '');
  console.log('  §POS-R3-PAID receipt="' + paid.slice(0, 80) + '"');

  // §R3-B Previous Sales: re-open the panel, tap the green bottom rim → recall the day's sales
  await pg.evaluate(() => document.getElementById('pos-pill-payment').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
  await pg.waitForSelector('#pos-float-panel.open', { timeout: 8000 }).catch(() => {});
  await pg.evaluate(() => document.getElementById('pos-rim-bottom').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
  await pg.waitForTimeout(250);
  const prev = await pg.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('.pos-prevsale-row'));
    return { n: rows.length, first: rows[0] && rows[0].querySelector('span').textContent, hasRevert: !!(rows[0] && rows[0].querySelector('.pos-revert-btn')) };
  });
  console.log('  §POS-R3-PREVSALE rows=' + prev.n + ' first="' + prev.first + '" revertBtn=' + prev.hasRevert);
  await shot('6_prevsales');

  // close the receipt overlay if present (UX note: it overlaps the panel)
  await pg.evaluate(() => { var c = document.getElementById('pos-receipt-close'); if (c) c.click(); });
  await pg.waitForTimeout(150);

  // §P1.C EODA Close Cash: open the ⋯ dock → tap Close Cash → fold the day → EODA summary overlay
  await pg.evaluate(() => document.getElementById('pos-dock-trigger').click());
  await pg.waitForTimeout(150);
  await pg.evaluate(() => document.getElementById('pos-pill-closecash').click());
  await pg.waitForTimeout(500);
  const eoda = await pg.evaluate(() => {
    const ov = document.getElementById('pos-eoda-overlay');
    return { shown: !!ov, text: ov ? ov.textContent.replace(/\s+/g, ' ').slice(0, 160) : '' };
  });
  console.log('  §POS-R3-EODA overlayShown=' + eoda.shown + ' summary="' + eoda.text + '"');
  await shot('7_close_cash');

  console.log('  DONE — review r3_pos_{1_slim,2_items,3_prevsales_empty,4_pay_armed,5_paid,6_prevsales,7_close_cash}.png in ' + SHOTS);
  await br.close(); server.close();
  process.exit(0);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
