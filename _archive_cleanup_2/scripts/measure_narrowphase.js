// measure_narrowphase.js — HONEST proof + measurement of real mesh-to-mesh clash narrowphase
// on the Terminal 48k building. Broadphase (R-tree bbox) → candidate pairs → narrowphase
// (three-mesh-bvh intersectsGeometry, true triangle-triangle) → confirmed vs false-positive.
// Replicates the viewer's exact element transform (ifc2three + euler(rotX,rotZ,-rotY) + unit scale).
// Proves: (a) narrowphase works on real geometry, (b) how many bbox clashes are false positives,
// (c) the time cost. Run: node measure_narrowphase.js
const { chromium } = require('/home/red1/bim-ootb/tests/node_modules/@playwright/test');
const { spawn } = require('child_process');

const SERVE_ROOT = '/home/red1';
const PORT = 8000;
const URL = `http://localhost:${PORT}/bim-ootb/viewer/viewer.html` +
  `?db=/bim-compiler/deploy/dev/buildings/Terminal_extracted.db` +
  `&lib=/bim-compiler/deploy/dev/buildings/Terminal_geo.db&bld=Terminal`;

(async () => {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', SERVE_ROOT], { stdio: 'ignore' });
  await new Promise(r => setTimeout(r, 1200));
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader'] });
  const page = await browser.newPage();
  page.setDefaultTimeout(360000);
  page.on('console', m => { const t = m.text(); if (t.includes('§NARROW')) console.log('  ' + t); });
  page.on('pageerror', e => console.log('  ERR ' + String(e).slice(0, 160)));

  try {
    console.log('\n═══ W-CLASH-NARROWPHASE — real mesh-to-mesh on Terminal 48k ═══\n');
    console.log('Loading Terminal… timeout 6min');
    await page.goto(URL, { waitUntil: 'domcontentloaded' });
    const t0 = Date.now(); let last = -1;
    while (Date.now() - t0 < 360000) {
      const st = await page.evaluate(() => {
        const A = window.APP;
        return A ? { ready: !A.streaming && A.streamedCount > 0, count: A.streamedCount || 0 } : null;
      }).catch(() => null);
      if (st && st.ready) break;
      if (st && st.count !== last) { last = st.count; if (st.count % 20000 < 2500) console.log('  …streaming ' + st.count.toLocaleString()); }
      await page.waitForTimeout(2000);
    }
    await page.waitForTimeout(3000);

    const result = await page.evaluate(async () => {
      const A = window.APP, THREE = window.THREE;
      const off = A.modelOffset;

      // ── build guid→hash (element_instances) and guid→xform (element_transforms) maps ──
      const hashOf = {};
      A.dbQuery("SELECT guid, geometry_hash FROM element_instances WHERE geometry_hash IS NOT NULL")
        .forEach(r => { hashOf[r[0]] = r[1]; });
      const xf = {};
      A.dbQuery("SELECT guid, center_x,center_y,center_z, rotation_x,rotation_y,rotation_z, bbox_x,bbox_y,bbox_z FROM element_transforms WHERE bbox_x IS NOT NULL")
        .forEach(r => { xf[r[0]] = { cx:r[1],cy:r[2],cz:r[3], rx:r[4],ry:r[5],rz:r[6], bx:r[7],by:r[8],bz:r[9] }; });

      // ── exact viewer transform: ifc2three + euler(rotX,rotZ,-rotY) + unit scale ──
      const _pos = new THREE.Vector3(), _euler = new THREE.Euler(), _quat = new THREE.Quaternion();
      const _one = new THREE.Vector3(1,1,1);
      function worldMat(x) {
        _pos.set(x.cx - off.x, x.cz - off.z, -(x.cy - off.y));
        _euler.set(x.rx||0, x.rz||0, -(x.ry||0));
        _quat.setFromEuler(_euler);
        return new THREE.Matrix4().compose(_pos, _quat, _one);
      }

      // ── ensure the R-tree exists (viewer builds it lazily only after a clash UI action) ──
      try {
        const ex = A.db.exec("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='elements_rtree'");
        const has = ex.length && ex[0].values[0][0] > 0;
        if (!has) {
          A.db.run("CREATE VIRTUAL TABLE elements_rtree USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ)");
          A.db.run("INSERT INTO elements_rtree SELECT rowid, center_x-bbox_x/2, center_x+bbox_x/2, center_y-bbox_y/2, center_y+bbox_y/2, center_z-bbox_z/2, center_z+bbox_z/2 FROM element_transforms WHERE bbox_x IS NOT NULL");
        }
        const cnt = A.db.exec("SELECT COUNT(*) FROM elements_rtree");
        console.log('§NARROW rtree_rows=' + (cnt.length ? cnt[0].values[0][0] : 0));
      } catch(e) { console.log('§NARROW rtree_build_err ' + e.message); }

      // ── BROADPHASE: R-tree bbox candidate pairs (cross-discipline, whole building) ──
      // pull every element's AABB once, R-tree query for overlaps. Cap candidates for timing.
      const guids = Object.keys(xf);
      const CAND_CAP = 4000;          // cap candidate pairs evaluated (enough for a fair rate)
      const cands = [];
      const seen = {};
      const tBroad = performance.now();
      for (let i = 0; i < guids.length && cands.length < CAND_CAP; i++) {
        const g = guids[i], a = xf[g];
        const minX=a.cx-a.bx/2, maxX=a.cx+a.bx/2, minY=a.cy-a.by/2, maxY=a.cy+a.by/2, minZ=a.cz-a.bz/2, maxZ=a.cz+a.bz/2;
        let rows;
        try {
          rows = A.dbQuery("SELECT t.guid FROM elements_rtree r JOIN element_transforms t ON r.id=t.rowid WHERE " +
            "r.maxX>="+minX+" AND r.minX<="+maxX+" AND r.maxY>="+minY+" AND r.minY<="+maxY+" AND r.maxZ>="+minZ+" AND r.minZ<="+maxZ);
        } catch(e) { continue; }
        for (let j = 0; j < rows.length; j++) {
          const gb = rows[j][0];
          if (gb === g) continue;
          const key = g < gb ? g+'|'+gb : gb+'|'+g;
          if (seen[key]) continue; seen[key] = 1;
          const b = xf[gb]; if (!b) continue;
          // confirm true AABB overlap (rtree is conservative)
          if (maxX<=b.cx-b.bx/2||minX>=b.cx+b.bx/2||maxY<=b.cy-b.by/2||minY>=b.cy+b.by/2||maxZ<=b.cz-b.bz/2||minZ>=b.cz+b.bz/2) continue;
          cands.push([g, gb]);
          if (cands.length >= CAND_CAP) break;
        }
      }
      const broadMs = performance.now() - tBroad;

      // ── re-fetch geometry for ONLY the candidate elements (tessellate-on-demand,
      //    exactly like IfcClash) — meshCache is released after streaming into BatchedMesh ──
      const needHashes = new Set();
      cands.forEach(([ga, gb]) => { if (hashOf[ga]) needHashes.add(hashOf[ga]); if (hashOf[gb]) needHashes.add(hashOf[gb]); });
      const hashList = [...needHashes];
      const geoCache = {};
      const tFetch = performance.now();
      const rdb = A._rangeDb, ldb = A.libDb;
      let hasNormals = true;
      try { if (rdb) await rdb.exec("SELECT normals FROM component_geometries LIMIT 0"); else ldb.exec("SELECT normals FROM component_geometries LIMIT 0"); }
      catch (e) { hasNormals = false; }
      const cols = hasNormals ? 'geometry_hash, vertices, faces, normals' : 'geometry_hash, vertices, faces';
      let builtGeo = 0;
      for (let ci = 0; ci < hashList.length; ci += 150) {
        const chunk = hashList.slice(ci, ci + 150);
        const ph = chunk.map(h => "'" + String(h).replace(/'/g, "''") + "'").join(',');
        for (const table of ['component_geometries', 'base_geometries']) {
          let res;
          try { res = rdb ? await rdb.exec(`SELECT ${cols} FROM ${table} WHERE geometry_hash IN (${ph})`)
                          : ldb.exec(`SELECT ${cols} FROM ${table} WHERE geometry_hash IN (${ph})`); }
          catch (e) { continue; }
          if (res && res.length > 0) {
            for (const row of res[0].values) {
              const gh = row[0], v = row[1], f = row[2], n = hasNormals ? (row[3] || null) : null;
              if (v && f && !geoCache[gh]) {
                const g = A.blobToGeometry(v, f, n);
                if (g) { try { g.computeBoundsTree(); } catch (e) {} geoCache[gh] = g; builtGeo++; }
              }
            }
          }
        }
      }
      const fetchMs = performance.now() - tFetch;
      console.log('§NARROW geo_refetch hashes=' + hashList.length + ' built=' + builtGeo +
        ' rangeDb=' + (!!rdb) + ' libDb=' + (!!ldb) + ' ms=' + fetchMs.toFixed(0));

      // ── NARROWPHASE: true mesh-to-mesh via three-mesh-bvh intersectsGeometry ──
      let confirmed = 0, falsePos = 0, noGeom = 0, tested = 0;
      const tNarrow = performance.now();
      for (let k = 0; k < cands.length; k++) {
        const [ga, gb] = cands[k];
        const ha = hashOf[ga], hb = hashOf[gb];
        const geoA = ha && geoCache[ha], geoB = hb && geoCache[hb];
        if (!geoA || !geoB || !geoA.boundsTree) { noGeom++; continue; }
        tested++;
        const mA = worldMat(xf[ga]), mB = worldMat(xf[gb]);
        const rel = mA.clone().invert().multiply(mB);   // geomB into geomA local frame
        let hit = false;
        try { hit = geoA.boundsTree.intersectsGeometry(geoB, rel); } catch(e) { noGeom++; tested--; continue; }
        if (hit) confirmed++; else falsePos++;
      }
      const narrowMs = performance.now() - tNarrow;

      const fpRate = tested ? (falsePos / tested * 100) : 0;
      console.log('§NARROW candidates=' + cands.length + ' broadphase_ms=' + broadMs.toFixed(0));
      console.log('§NARROW tested=' + tested + ' confirmed=' + confirmed + ' false_positive=' + falsePos +
        ' no_geom=' + noGeom + ' fp_rate=' + fpRate.toFixed(1) + '%');
      console.log('§NARROW narrowphase_ms=' + narrowMs.toFixed(0) +
        ' per_pair_ms=' + (tested ? (narrowMs/tested).toFixed(3) : 'n/a'));

      return { cands: cands.length, broadMs, tested, confirmed, falsePos, noGeom, fpRate, narrowMs };
    });

    console.log('\n── VERDICT ──');
    console.log('  Broadphase (bbox R-tree): ' + result.cands + ' candidate pairs in ' + result.broadMs.toFixed(0) + 'ms');
    console.log('  Narrowphase (mesh-to-mesh): ' + result.tested + ' tested in ' + result.narrowMs.toFixed(0) + 'ms (' +
      (result.tested ? (result.narrowMs/result.tested).toFixed(2) : '—') + 'ms/pair)');
    console.log('  Confirmed real clashes: ' + result.confirmed);
    console.log('  False positives caught (bbox said clash, geometry says clear): ' + result.falsePos +
      ' = ' + result.fpRate.toFixed(1) + '% of bbox hits');
    console.log('  (no geometry loaded for ' + result.noGeom + ' pairs — skipped, honest)');
    console.log('\n  → This is the gap narrowphase closes: bbox alone over-reports by ' + result.fpRate.toFixed(0) + '%.\n');

  } catch (e) {
    console.log('FATAL: ' + e.message + '\n' + (e.stack||'').slice(0,300));
  } finally {
    await browser.close();
    server.kill('SIGKILL');
    process.exit(0);
  }
})();
