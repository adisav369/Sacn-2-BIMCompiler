// 41-room-volume-lens.spec.js — RP Task A/B: Room volume lens + Phase/Material highlight.
// Issue proved (Task A): the Room axis on a DB carrying IfcSpace bbox volumes draws
//   21 translucent room boxes and x-rays the model (NOT a contents isolate); tapping a
//   room focuses its box. §ROOM_LENS / §ROOM_SELECT are the value proofs.
// Issue proved (Task B): the Phase axis lazily runs Time Machine's REAL timeline
//   generator (window.tmGenerateTimeline → injectGantt → kernel_ops), lists phases in
//   construction order, and tapping a phase HIGHLIGHTS its elements + X-RAYS the rest
//   (NOT an isolate). §PHASE_LENS gen=real source=kernel_ops / §PHASE_SELECT prove it.
// Issue proved (Task C): the Material axis HIGHLIGHTS + x-rays (not isolate). §MAT_SELECT.
// Render gate: no §BBOX_KEEP (real geometry, not cubes) AND room boxes mounted.
// Whitebox-first: §-tagged console lines are the proof; Playwright drives wiring only.

const { test, expect } = require('@playwright/test');
const { openViewer } = require('../helpers/viewer');
const { ConsoleLogs } = require('../helpers/console-capture');

// Served Duplex: 1122 elems, 80 distinct geometry blob sizes (real meshes), 21 IfcSpace
// rows carrying center_*/size_* (room volume), 15 materials. Library + extracted both present.
const VOL = { db: '/dev/buildings/Duplex_extracted.db', lib: '/dev/buildings/Duplex_library.db', streamTimeout: 60000 };

test.describe('RP Task A/B/C — Room volume lens + Phase/Material highlight', () => {

  test('41.1 Room axis: 21 boxes + x-ray, no cubes; tap focuses a room @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, VOL);

    // RENDER GATE: real geometry, not bbox cubes. §BBOX_KEEP would mean cubes.
    const bboxKeep = logs.tagged('§BBOX_KEEP');
    console.log(`§PW_RENDER_GATE bboxKeep=${bboxKeep.length}`);
    expect(bboxKeep.length).toBe(0);

    // The volume probe must see the bbox columns populated.
    await page.evaluate(() => window.APP.openFindPanel());
    await page.waitForSelector('#find-axis-room', { state: 'attached', timeout: 15000 });
    const probe = logs.tagged('§LENS_PROBE').slice(-1)[0].text;
    console.log(`§PW_PROBE ${probe}`);
    expect(probe).toContain('roomVol=true');

    // Select the Room axis pill → boxes drawn + x-ray on.
    await page.evaluate(() => {
      const pill = document.getElementById('find-axis-room');
      pill.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(400);

    const lensLine = logs.assertTag('§ROOM_LENS').slice(-1)[0].text;
    console.log(`§PW_ROOM_LENS ${lensLine}`);
    const m = lensLine.match(/rooms=(\d+) xray=(\w+)/);
    expect(m).toBeTruthy();
    expect(+m[1]).toBe(21);          // 21 room boxes
    expect(m[2]).toBe('on');         // model x-rayed

    // Boxes actually mounted in the scene.
    const boxCount = await page.evaluate(() => {
      let n = 0;
      window.APP.scene.traverse(o => { if (o.userData && o.userData._roomBox) n++; });
      return n;
    });
    console.log(`§PW_ROOM_BOXES sceneBoxes=${boxCount}`);
    expect(boxCount).toBe(21);

    // X-ray actually engaged (opacity dropped on real materials).
    const xray = await page.evaluate(() => window.APP.xrayOn);
    expect(xray).toBe(true);

    // Tap a room in the list → §ROOM_SELECT with that room + box coords.
    await page.evaluate(() => {
      const span = [...document.querySelectorAll('#find-tree span')].find(s => /^[AB]\d{3}$|^R\d{3}$/.test(s.textContent));
      if (span) span.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(200);
    const selLine = logs.assertTag('§ROOM_SELECT').slice(-1)[0].text;
    console.log(`§PW_ROOM_SELECT ${selLine}`);
    expect(selLine).toMatch(/room="[^"]+" box=\([-\d.]+,[-\d.]+,[-\d.]+\)/);

    // "Show all" (isolate-bar reset) clears boxes + restores opacity.
    await page.waitForSelector('#find-showall-btn', { state: 'visible', timeout: 5000 });
    await page.click('#find-showall-btn');
    await page.waitForTimeout(200);
    const after = await page.evaluate(() => {
      let n = 0; window.APP.scene.traverse(o => { if (o.userData && o.userData._roomBox) n++; });
      return { boxes: n, xray: window.APP.xrayOn };
    });
    console.log(`§PW_ROOM_RESET boxes=${after.boxes} xray=${after.xray}`);
    expect(after.boxes).toBe(0);
    expect(after.xray).toBe(false);

    logs.assertNoErrors();
  });

  test('41.2 Phase axis: REAL kernel_ops timeline; tap → highlight + x-ray (no isolate) @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, VOL);

    await page.evaluate(() => window.APP.openFindPanel());
    await page.waitForSelector('#find-axis-phase', { state: 'attached', timeout: 15000 });

    // Select Phase → lazy run the REAL generator (window.tmGenerateTimeline → kernel_ops).
    await page.evaluate(() => {
      const pill = document.getElementById('find-axis-phase');
      pill.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(600);

    // The generator must have run (its §GANTT line) and the lens read kernel_ops.
    const gantt = logs.tagged('§GANTT');
    console.log(`§PW_GANTT lines=${gantt.length}`);
    expect(gantt.length).toBeGreaterThan(0);

    const phaseLines = logs.tagged('§PHASE_LENS');
    phaseLines.forEach(l => console.log(`  [page] ${l.text}`));
    const real = phaseLines.find(l => l.text.includes('gen=real source=kernel_ops') && /phases=(\d+)/.test(l.text) && +l.text.match(/phases=(\d+)/)[1] > 0);
    expect(real).toBeTruthy();
    const k = +real.text.match(/phases=(\d+)/)[1];
    console.log(`§PW_PHASE_LENS phases=${k}`);
    expect(k).toBeGreaterThan(0);

    // Phase rows listed in the tree.
    const rows = await page.evaluate(() => document.querySelectorAll('#find-tree span').length);
    expect(rows).toBeGreaterThan(0);

    // Tap a phase → HIGHLIGHT that phase + X-RAY the rest (NOT isolate).
    await page.evaluate(() => {
      const span = [...document.querySelectorAll('#find-tree span')].find(s => s.textContent && /[A-Za-z]/.test(s.textContent));
      if (span) span.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(300);
    const sel = logs.assertTag('§PHASE_SELECT').slice(-1)[0].text;
    console.log(`§PW_PHASE_SELECT ${sel}`);
    expect(sel).toMatch(/phase="[^"]+" elems=\d+ meshes=\d+ xray=on/);

    // Highlight lens → x-ray engaged, but NO isolate filter applied (rest stays visible).
    const state = await page.evaluate(() => ({
      xray: window.APP.xrayOn,
      iso: window.APP.activeGuidFilter ? window.APP.activeGuidFilter.size : 0,
    }));
    console.log(`§PW_PHASE_STATE xray=${state.xray} isolate=${state.iso}`);
    expect(state.xray).toBe(true);
    expect(state.iso).toBe(0);   // highlight, not isolate

    // "Show all" tears the highlight down (x-ray off, outline cleared).
    await page.waitForSelector('#find-showall-btn', { state: 'visible', timeout: 5000 });
    await page.click('#find-showall-btn');
    await page.waitForTimeout(200);
    const afterXray = await page.evaluate(() => window.APP.xrayOn);
    console.log(`§PW_PHASE_RESET xray=${afterXray}`);
    expect(afterXray).toBe(false);

    logs.assertNoErrors();
  });

  test('41.3 Material axis: tap → highlight + x-ray (no isolate) @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, VOL);

    await page.evaluate(() => window.APP.openFindPanel());
    await page.waitForSelector('#find-axis-material', { state: 'attached', timeout: 15000 });

    await page.evaluate(() => {
      const pill = document.getElementById('find-axis-material');
      pill.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(300);

    const groups = logs.tagged('§LENS_GROUPS').find(l => l.text.includes('lens=material'));
    expect(groups).toBeTruthy();

    // Tap a material → HIGHLIGHT + X-RAY (not isolate).
    await page.evaluate(() => {
      const span = [...document.querySelectorAll('#find-tree span')].find(s => s.textContent && /[A-Za-z]/.test(s.textContent));
      if (span) span.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(300);
    const sel = logs.assertTag('§MAT_SELECT').slice(-1)[0].text;
    console.log(`§PW_MAT_SELECT ${sel}`);
    expect(sel).toMatch(/material="[^"]+" elems=\d+ meshes=\d+ xray=on/);

    const state = await page.evaluate(() => ({
      xray: window.APP.xrayOn,
      iso: window.APP.activeGuidFilter ? window.APP.activeGuidFilter.size : 0,
    }));
    console.log(`§PW_MAT_STATE xray=${state.xray} isolate=${state.iso}`);
    expect(state.xray).toBe(true);
    expect(state.iso).toBe(0);

    logs.assertNoErrors();
  });
});
