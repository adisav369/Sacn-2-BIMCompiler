/**
 * bench_elements.js — Element count benchmark
 * Issue: What is BIM OOTB's element threshold on desktop and mobile?
 * Measures: streaming time, draw calls, first paint, memory, FPS
 *
 * Run: node deploy/dev/tests/bench_elements.js
 * Requires: Playwright installed (npx playwright install chromium)
 */
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE = 'https://red1oon.github.io/bim-ootb/viewer/viewer.html';
const OCI = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/buildings/';

// Buildings ordered by element count (ascending)
const BUILDINGS = [
  { name: 'SampleHouse',      elements: 60 },
  { name: 'Duplex',           elements: 218 },
  { name: 'HospitalAuckland', elements: 442 },
  { name: 'SmileyWest',       elements: 531 },
  { name: 'AC11Institute',    elements: 986 },
  { name: 'HospitalGarage',   elements: 1271 },
  { name: 'Esplanades',       elements: 1964 },
  { name: 'HITOS',            elements: 2079 },
  { name: 'Schependomlaan',   elements: 3284 },
  { name: 'SampleCastle',     elements: 3621 },
  { name: 'Molio',            elements: 3675 },
  { name: 'HHS_Office_Federated', elements: 6871 },
  { name: 'WBDG_Office',      elements: 7000 },
  { name: 'Ifc4_Revit',       elements: 11388 },
  { name: 'Clinic',           elements: 16114 },
  { name: 'Terminal',         elements: 48428 },
  { name: 'Hospital',         elements: 63415 },
  { name: 'LTU_AHouse',       elements: 122667 },
];

async function benchBuilding(browser, bld, timeout) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 } });
  const page = await ctx.newPage();

  const logs = [];
  page.on('console', msg => logs.push(msg.text()));
  page.on('pageerror', err => logs.push('PAGE_ERROR: ' + err.message));

  const url = `${BASE}?db=${encodeURIComponent(OCI + bld.name + '_extracted.db')}`;
  const t0 = Date.now();

  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });

    // Wait for streaming to complete — look for "DONE" in status or BATCHED_FLUSH
    var streamDone = false;
    var elapsed = 0;
    var firstPaint = 0;
    var streamMs = 0;

    while (elapsed < timeout) {
      await page.waitForTimeout(2000);
      elapsed = Date.now() - t0;

      // Check for first paint (first PROGRESSIVE_FLUSH or BATCHED_FLUSH)
      if (!firstPaint) {
        var hasPaint = logs.some(l => l.includes('PROGRESSIVE_FLUSH') || l.includes('BATCHED_FLUSH'));
        if (hasPaint) firstPaint = elapsed;
      }

      // Check for streaming done
      var done = logs.some(l => l.includes('— DONE') || l.includes('§DS_QUEUED') && logs.some(l2 => l2.includes('BATCHED_FLUSH') && l2.includes('mobile=false')));
      var hasDlod = logs.some(l => l.includes('§DLOD_ENABLE') || l.includes('§DLOD_REFS'));

      if (hasDlod || done) {
        streamMs = elapsed;
        streamDone = true;
        break;
      }

      // Check for errors
      var hasError = logs.some(l => l.includes('PAGE_ERROR') || l.includes('INIT_VIEWER_ERROR') || l.includes('context loss'));
      if (hasError) {
        return { name: bld.name, elements: bld.elements, status: 'ERROR', error: logs.find(l => l.includes('ERROR')), elapsed };
      }
    }

    if (!streamDone) {
      return { name: bld.name, elements: bld.elements, status: 'TIMEOUT', elapsed, firstPaint };
    }

    // Wait 2s for scene to settle, then measure FPS
    await page.waitForTimeout(2000);

    var metrics = await page.evaluate(() => {
      var result = {};
      // Draw calls from HUD
      var meshEl = document.getElementById('s-meshes');
      result.drawCalls = meshEl ? parseInt(meshEl.textContent.replace(/,/g, '')) : 0;
      // Streamed count
      var streamEl = document.getElementById('s-streamed');
      result.streamed = streamEl ? parseInt(streamEl.textContent.replace(/,/g, '')) : 0;
      // Scene children
      result.sceneChildren = window.APP ? window.APP.scene.children.length : 0;
      // Memory
      if (performance.memory) {
        result.heapMB = Math.round(performance.memory.usedJSHeapSize / 1024 / 1024);
        result.heapLimitMB = Math.round(performance.memory.jsHeapSizeLimit / 1024 / 1024);
      }
      // Renderer info
      if (window.APP && window.APP.renderer && window.APP.renderer.info) {
        var info = window.APP.renderer.info;
        result.triangles = info.render ? info.render.triangles : 0;
        result.geometries = info.memory ? info.memory.geometries : 0;
        result.textures = info.memory ? info.memory.textures : 0;
      }
      // Renderer type
      result.renderer = window.APP ? (window.APP._isWebGPU ? 'WebGPU' : 'WebGL') : '?';
      return result;
    });

    // Quick FPS test — orbit camera and measure
    var fps = await page.evaluate(() => {
      return new Promise(resolve => {
        var frames = 0;
        var t0 = performance.now();
        function count() {
          frames++;
          if (performance.now() - t0 < 3000) {
            requestAnimationFrame(count);
          } else {
            resolve(Math.round(frames / 3));
          }
        }
        // Trigger orbit to force renders
        if (window.APP && window.APP.controls) {
          window.APP.controls.autoRotate = true;
          window.APP.controls.autoRotateSpeed = 10;
        }
        requestAnimationFrame(count);
      });
    });

    // Stop rotation
    await page.evaluate(() => { if (window.APP && window.APP.controls) window.APP.controls.autoRotate = false; });

    return {
      name: bld.name,
      elements: bld.elements,
      status: 'OK',
      streamMs,
      firstPaint,
      fps,
      ...metrics
    };

  } catch(e) {
    return { name: bld.name, elements: bld.elements, status: 'ERROR', error: e.message, elapsed: Date.now() - t0 };
  } finally {
    await ctx.close();
  }
}

(async () => {
  console.log('§BENCH_START buildings=' + BUILDINGS.length);
  console.log('');

  const browser = await chromium.launch({
    headless: true,
    args: ['--disable-gpu-sandbox', '--disable-software-rasterizer']
  });

  var results = [];

  for (var i = 0; i < BUILDINGS.length; i++) {
    var bld = BUILDINGS[i];
    // Scale timeout with element count: min 30s, max 300s
    var timeout = Math.min(300000, Math.max(30000, bld.elements * 2));

    process.stdout.write(`[${i+1}/${BUILDINGS.length}] ${bld.name} (${bld.elements.toLocaleString()} elements)... `);

    var result = await benchBuilding(browser, bld, timeout);
    results.push(result);

    if (result.status === 'OK') {
      console.log(`${result.streamMs/1000}s stream | ${result.fps} FPS | ${result.drawCalls} draws | ${result.triangles?.toLocaleString()} tris | ${result.heapMB || '?'}MB heap`);
    } else {
      console.log(`${result.status} after ${(result.elapsed/1000).toFixed(1)}s` + (result.error ? ' — ' + result.error.substring(0, 80) : ''));
    }
  }

  await browser.close();

  // Summary table
  console.log('\n═══ BENCHMARK RESULTS ═══\n');
  console.log('| Building | Elements | Stream(s) | FPS | Draws | Triangles | Heap(MB) | Status |');
  console.log('|----------|----------|-----------|-----|-------|-----------|----------|--------|');
  for (var r of results) {
    if (r.status === 'OK') {
      console.log(`| ${r.name} | ${r.elements.toLocaleString()} | ${(r.streamMs/1000).toFixed(1)} | ${r.fps} | ${r.drawCalls.toLocaleString()} | ${(r.triangles||0).toLocaleString()} | ${r.heapMB || '?'} | ${r.renderer} |`);
    } else {
      console.log(`| ${r.name} | ${r.elements.toLocaleString()} | — | — | — | — | — | ${r.status} |`);
    }
  }

  // Find threshold
  var lastOk = results.filter(r => r.status === 'OK' && r.fps >= 30).pop();
  var firstSlow = results.find(r => r.status === 'OK' && r.fps < 30);
  var firstFail = results.find(r => r.status !== 'OK');

  console.log('\n§BENCH_THRESHOLD:');
  if (lastOk) console.log(`  Smooth (≥30 FPS): ${lastOk.name} at ${lastOk.elements.toLocaleString()} elements`);
  if (firstSlow) console.log(`  Slow (<30 FPS): ${firstSlow.name} at ${firstSlow.elements.toLocaleString()} elements (${firstSlow.fps} FPS)`);
  if (firstFail) console.log(`  Failed: ${firstFail.name} at ${firstFail.elements.toLocaleString()} elements`);
  if (!firstSlow && !firstFail) console.log(`  All buildings smooth — threshold not reached. Max tested: ${results[results.length-1].elements.toLocaleString()}`);

  console.log('\n§BENCH_DONE results=' + results.length);

  // Save results
  var outFile = '/tmp/bim_bench_results.json';
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
  console.log('Results saved: ' + outFile);
})();
