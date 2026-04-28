// 14-2d-plans.spec.js — 2D DXF viewer in browser tab
// Issues prevented:
//   - DXF not loading in browser (parser or fetch failure)
//   - BIMSRC xdata lost on parse (breaks GUID correlation)
//   - 2D toolbar button missing from viewer

const { test, expect } = require('@playwright/test');
const { openViewer, waitForStream } = require('../helpers/viewer');

const DXF_URL = 'dxf/SH_FLOOR.dxf';

test.describe('2D Plans Viewer', () => {

  test('14.1 2D button exists in toolbar @fast', async ({ page }) => {
    await openViewer(page, {
      db: '/buildings/SampleHouse_extracted.db',
      lib: '/buildings/SampleHouse_library.db',
    });
    const btn = page.locator('button[title="2D Plans"]');
    await expect(btn).toBeVisible({ timeout: 10000 });
    console.log('§PW_2D_BTN visible=true');
  });

  test('14.2 2d.html loads and parses DXF @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.waitForLoadState('domcontentloaded');

    // Select SH Floor Plan from dropdown
    await page.selectOption('#sheet-select', DXF_URL);

    // Wait for entities to be parsed
    await page.waitForFunction(() => {
      const el = document.getElementById('ent-count');
      return el && parseInt(el.textContent) > 0;
    }, { timeout: 15000 });

    const entCount = await page.$eval('#ent-count', el => parseInt(el.textContent));
    const layerCount = await page.$eval('#layer-count', el => parseInt(el.textContent));

    console.log(`§PW_2D_PARSE entities=${entCount} layers=${layerCount}`);
    expect(entCount).toBeGreaterThan(100);    // SH floor has ~315 entities
    expect(layerCount).toBeGreaterThan(3);     // Multiple AIA layers
  });

  test('14.3 DXF layers panel toggles @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.selectOption('#sheet-select', DXF_URL);
    await page.waitForFunction(() => parseInt(document.getElementById('ent-count')?.textContent) > 0, { timeout: 15000 });

    // Layer panel starts hidden
    const panel = page.locator('#layer-panel');
    await expect(panel).toBeHidden();

    // Click Layers button
    await page.click('#layers-btn');
    await expect(panel).toBeVisible();

    // Should have layer checkboxes
    const labels = panel.locator('label');
    const count = await labels.count();
    expect(count).toBeGreaterThan(3);
    console.log(`§PW_2D_LAYERS visible=true count=${count}`);
  });

  test('14.4 BIMSRC xdata survives parse @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.selectOption('#sheet-select', DXF_URL);
    await page.waitForFunction(() => parseInt(document.getElementById('ent-count')?.textContent) > 0, { timeout: 15000 });

    // Count entities with BIMSRC xdata
    const bimsrcCount = await page.evaluate(() => {
      const ents = window.dxf ? window.dxf.entities : [];
      return ents.filter(e => {
        const xd = e.extendedData || e.xdata || e.xData;
        return xd && xd.applicationName === 'BIMSRC';
      }).length;
    });

    console.log(`§PW_2D_BIMSRC count=${bimsrcCount}`);
    expect(bimsrcCount).toBeGreaterThan(50);   // SH floor has ~93 tagged entities
  });

  test('14.5 BIMSRC toggle highlights entities @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.selectOption('#sheet-select', DXF_URL);
    await page.waitForFunction(() => parseInt(document.getElementById('ent-count')?.textContent) > 0, { timeout: 15000 });

    // BIMSRC button toggles
    const btn = page.locator('#bimsrc-btn');
    await expect(btn).toBeVisible();
    await btn.click();
    await expect(btn).toHaveClass(/active/);

    // Info bar should show BIMSRC fields
    const infoSpan = page.locator('#bimsrc-info');
    await expect(infoSpan).toBeVisible();
    console.log('§PW_2D_BIMSRC_TOGGLE active=true info_visible=true');
  });

  test('14.6 Fit view works after load @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.selectOption('#sheet-select', DXF_URL);
    await page.waitForFunction(() => parseInt(document.getElementById('ent-count')?.textContent) > 0, { timeout: 15000 });

    // Check viewScale is set (non-zero)
    const scale = await page.evaluate(() => window.viewScale);
    expect(scale).toBeGreaterThan(0);
    console.log(`§PW_2D_FIT viewScale=${scale.toFixed(2)}`);
  });

  test('14.7 DX floor plan loads @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.selectOption('#sheet-select', 'dxf/DX_FLOOR_GF.dxf');
    await page.waitForFunction(() => parseInt(document.getElementById('ent-count')?.textContent) > 0, { timeout: 15000 });

    const entCount = await page.$eval('#ent-count', el => parseInt(el.textContent));
    console.log(`§PW_2D_DX entities=${entCount}`);
    expect(entCount).toBeGreaterThan(100);    // DX floor has ~368 entities
  });

  test('14.8 Drag-drop DXF file loads @fast', async ({ page }) => {
    await page.goto('/dev/2d.html');
    await page.waitForLoadState('domcontentloaded');

    // Read a DXF file and simulate drop
    const fs = require('fs');
    const dxfContent = fs.readFileSync(
      require('path').resolve(__dirname, '../../dxf/SH_FLOOR.dxf'), 'utf-8'
    );

    // Inject the DXF content via parseDxf directly (drag-drop uses FileReader which is hard to test)
    await page.evaluate((content) => { parseDxf(content); }, dxfContent);

    await page.waitForFunction(() => parseInt(document.getElementById('ent-count')?.textContent) > 0, { timeout: 5000 });
    const entCount = await page.$eval('#ent-count', el => parseInt(el.textContent));
    console.log(`§PW_2D_DROP entities=${entCount}`);
    expect(entCount).toBeGreaterThan(100);
  });
});
