// measure_pick.js — HONEST measurement of element-pick latency on the Terminal 48k building.
// Reproduces the EXACT pick path from picking.js (collectMeshes + raycaster.intersectObjects,
// firstHitOnly=false) and times it. No assumptions — measures the real raycast cost.
// Usage: node measure_pick.js   (starts its own server on :8000 from /home/red1)
const { chromium } = require('/home/red1/bim-ootb/tests/node_modules/@playwright/test');
const { spawn } = require('child_process');

const SERVE_ROOT = '/home/red1';
const PORT = 8000;
const URL = `http://localhost:${PORT}/bim-ootb/viewer/viewer.html` +
  `?db=/bim-compiler/deploy/dev/buildings/Terminal_extracted.db` +
  `&lib=/bim-compiler/deploy/dev/buildings/Terminal_geo.db&bld=Terminal`;

(async () => {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', SERVE_ROOT],
    { stdio: 'ignore' });
  await new Promise(r => setTimeout(r, 1200));

  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader'] });
  const page = await browser.newPage();
  page.setDefaultTimeout(360000);
  page.on('console', m => { const t = m.text(); if (t.includes('§PICK_MEASURE')) console.log('  ' + t); });
  page.on('pageerror', e => console.log('  ERR ' + String(e).slice(0, 160)));

  try {
    console.log('\n═══ W-PICK-MEASURE — real pick latency on Terminal 48k ═══\n');
    console.log('Loading Terminal (48,428 elements)… timeout 6min');
    await page.goto(URL, { waitUntil: 'domcontentloaded' });

    // poll streaming status with progress, up to 6min
    const t0 = Date.now();
    let lastCount = -1;
    while (Date.now() - t0 < 360000) {
      const st = await page.evaluate(() => {
        const A = window.APP;
        return A ? { ready: !A.streaming && A.streamedCount > 0, streaming: !!A.streaming, count: A.streamedCount || 0 } : null;
      }).catch(() => null);
      if (st && st.ready) break;
      if (st && st.count !== lastCount) { lastCount = st.count; console.log('  …streaming ' + st.count.toLocaleString() + ' elements'); }
      await page.waitForTimeout(2000);
    }
    await page.waitForTimeout(3000);

    const result = await page.evaluate(() => {
      const A = window.APP;

      let bm = 0, bmSlots = 0, im = 0, imInst = 0, indiv = 0;
      for (const id in A._batchMeta) { bm++; bmSlots += A._batchMeta[id].length; }
      for (const id in A._instanceMeta) { im++; imInst += A._instanceMeta[id].length; }
      A.scene.traverse(o => { if (o.isMesh && o.userData.guid && !o.isBatchedMesh && !o.isInstancedMesh) indiv++; });

      const collect = () => A.collectMeshes(o =>
        (o.isMesh || o.isInstancedMesh || o.isBatchedMesh) && o.visible);

      function timePick(ndcX, ndcY) {
        A.mouse.x = ndcX; A.mouse.y = ndcY;
        A.raycaster.setFromCamera(A.mouse, A.camera);
        A.raycaster.firstHitOnly = false;
        const meshes = collect();
        const t0 = performance.now();
        const hits = A.raycaster.intersectObjects(meshes, false);
        const ms = performance.now() - t0;
        return { ms, hits: hits.length, meshCount: meshes.length };
      }

      const samples = [];
      let meshCount = 0;
      for (let gx = 0; gx < 5; gx++) {
        for (let gy = 0; gy < 5; gy++) {
          const r = timePick(-0.8 + gx * 0.4, -0.8 + gy * 0.4);
          samples.push(r.ms);
          meshCount = r.meshCount;
        }
      }
      samples.sort((a, b) => a - b);
      const median = samples[Math.floor(samples.length / 2)];
      const min = samples[0], max = samples[samples.length - 1];
      const mean = samples.reduce((s, v) => s + v, 0) / samples.length;

      console.log('§PICK_MEASURE meshes_raycast=' + meshCount +
        ' BatchedMesh=' + bm + '(' + bmSlots + ' slots) InstancedMesh=' + im + '(' + imInst + ') individual=' + indiv);
      console.log('§PICK_MEASURE streamed=' + A.streamedCount +
        ' median_ms=' + median.toFixed(1) + ' min_ms=' + min.toFixed(1) +
        ' max_ms=' + max.toFixed(1) + ' mean_ms=' + mean.toFixed(1) + ' samples=' + samples.length);

      return { median, min, max, mean, meshCount, bm, bmSlots, im, imInst, indiv, streamed: A.streamedCount };
    });

    console.log('\n── VERDICT ──');
    console.log('  Pick raycast median: ' + result.median.toFixed(1) + ' ms  (min ' +
      result.min.toFixed(1) + ' / max ' + result.max.toFixed(1) + ')');
    const verdict = result.median > 200 ? 'SLOW — fix warranted (bbox-prefilter)' :
                    result.median > 50 ? 'NOTICEABLE' : 'fast';
    console.log('  Assessment: ' + verdict);
    console.log('  (BatchedMesh raycast is unaccelerated in R184 — baseline before any fix)\n');

  } catch (e) {
    console.log('FATAL: ' + e.message);
  } finally {
    await browser.close();
    server.kill('SIGKILL');
    process.exit(0);
  }
})();
