// W-ROSETTA-CANVAS-SH — Drop the REAL SampleHouse into the REAL viewer canvas, measure to the ORIGINAL.
//
// THE PROOF (never forget): a real building survives the round-trip with ZERO DRIFT.
//   - canvas  = the actual viewer.html scene after loading the real served SampleHouse DB. Per element we read
//               its TRUE WORLD AABB CENTER from the rendered THREE meshes (individual + InstancedMesh + BatchedMesh,
//               via THREE's own getBoundingBoxAt/instance bbox · matrixWorld). Geometry extent — convention-free.
//   - original = reference/transient_rosetta/Ifc4_SampleHouse_extracted.db  elements_rtree (the raw-IFC extraction
//               = source of truth), AABB center brought into THREE space by the viewer's OWN ifc2three (scene.js:362).
//   - compare  = ALL-PAIRS RELATIVE OFFSET (the proven scripts/geo_verify.py method), worst mm.
//
// RESULTS 2026-06-21 — ZERO DRIFT (worst 0.000mm):
//   SampleHouse: 55 elements, 1485 pairs.   Duplex: 1085 elements, 588070 pairs.
//   (The early non-zero numbers were all MY measurement bugs — wrong instance/batched matrix, then IFC-vs-THREE
//   axis mismatch, then served-center convention — diagnosed and fixed; the buildings never drifted.)
//
// Usage:  node scripts/rosetta_canvas_sh.js [BuildingBase] [originalExtraction.db]
//   SampleHouse (default): node scripts/rosetta_canvas_sh.js
//   Duplex:                node scripts/rosetta_canvas_sh.js Duplex database/Stacked_Duplex.db
//
// NO FAKE DATA. NO INVENTED RECONSTRUCT. Real building, real canvas, real extraction.
// See memory feedback_rosetta_proof_real_building, scripts/geo_verify.py (canonical method).
'use strict';
const http = require('http'), fs = require('fs'), path = require('path');
const HOME = process.env.HOME;
const ROOT = path.join(HOME, 'bim-ootb');                      // deploy root — READ ONLY, never edited
const BLD = path.join(HOME, 'bim-compiler', 'deploy', 'buildings'); // real served DBs
// args: [servedBuildingBase] [originalExtractionDb]   default = SampleHouse
const BUILDING = process.argv[2] || 'SampleHouse';
const ORIG_DB = process.argv[3]
  ? path.resolve(process.argv[3])
  : path.join(HOME, 'bim-compiler', 'reference', 'transient_rosetta', 'Ifc4_SampleHouse_extracted.db');
const puppeteer = require(path.join(HOME, 'bim-compiler', 'node_modules', 'puppeteer'));
const Database = require(path.join(HOME, 'bim-compiler', 'node_modules', 'better-sqlite3'));
const MIME = { '.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.wasm':'application/wasm',
  '.json':'application/json','.css':'text/css','.map':'application/json','.db':'application/octet-stream','.bin':'application/octet-stream' };

// static server: rooted at bim-ootb (so ../erp ../common resolve); building DBs from real bim-compiler deploy
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]);
  const file = p.startsWith('/viewer/buildings/') ? path.join(BLD, p.slice('/viewer/buildings/'.length)) : path.join(ROOT, p);
  fs.readFile(file, (e, b) => {
    if (e) { console.log('  404 ' + p); r.writeHead(404); r.end('404 ' + p); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' }); r.end(b);
  });
});

(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await puppeteer.launch({ headless: 'new',
    args: ['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const pg = await br.newPage();
  pg.on('console', m => { const t = m.text(); if (/^§(SINGLE_DB_HUD|CINE_GUIDMAP|S26)/.test(t)) console.log('  page> ' + t); });
  pg.on('pageerror', e => console.log('  ERR ' + String(e).slice(0,160)));

  await pg.goto(`http://localhost:${port}/viewer/viewer.html?db=buildings/${BUILDING}_extracted.db`, { waitUntil:'load', timeout:60000 });
  await pg.waitForFunction("window.APP && window.APP.scene", { timeout:30000 })
    .catch(async () => { const g = await pg.evaluate(() => Object.keys(window).filter(k=>/^(A|APP|THREE)$/.test(k)).map(k=>k+':'+(typeof window[k]))); console.log('  globals=' + JSON.stringify(g)); throw new Error('A.scene not ready'); });

  // wait for FULL stream: progressive flush plateaus (first paint at 500, then every 5000), so a 2-poll
  // "stable" is not enough. Require streaming finished AND count stable across 3 consecutive polls.
  await pg.waitForFunction(`(() => {
    const A = window.APP; if (!A || !A.scene) return false;
    let n = 0; A.scene.traverse(o => { if (o.userData && o.userData.guid && o.isMesh) n++;
      else if (o.isBatchedMesh && A._batchMeta && A._batchMeta[o.id]) n += A._batchMeta[o.id].length;
      else if (o.isInstancedMesh && A._instanceMeta && A._instanceMeta[o.id]) n += A._instanceMeta[o.id].length; });
    const streamDone = (A.streaming === false) || (A.streamQueue && A.streamIdx >= A.streamQueue.length);
    window.__h = (window.__h || []); window.__h.push(n); if (window.__h.length > 3) window.__h.shift();
    const stable = window.__h.length === 3 && window.__h[0] === n && window.__h[1] === n;
    return n > 0 && streamDone && stable;
  })()`, { timeout:90000, polling:700 });

  // Canvas measurement: per-guid TRUE WORLD AABB CENTER of the actual rendered geometry.
  // Convention-free (geometry extent, not a stored "center"). Uses THREE's own per-element bbox+matrix:
  //   instanced/individual: geometry.boundingBox · (matrixWorld · instanceMatrix)
  //   batched:              getBoundingBoxAt(slotId) · (matrixWorld · slotMatrix)
  const canvas = await pg.evaluate(() => {
    const app = window.APP, map = {};
    if (!app || !app.scene) return map;
    app.scene.updateMatrixWorld(true);
    const box = new THREE.Box3(), b2 = new THREE.Box3(), m4 = new THREE.Matrix4(), wm = new THREE.Matrix4(), c = new THREE.Vector3();
    function center(b){ b.getCenter(c); return [c.x, c.y, c.z]; }
    app.scene.traverse(function(obj) {
      if (!obj.userData) return;
      if (obj.userData.guid && obj.isMesh && !obj.isInstancedMesh && !obj.isBatchedMesh) {
        box.setFromObject(obj); if (isFinite(box.min.x)) map[obj.userData.guid] = center(box);
      } else if (obj.isBatchedMesh && app._batchMeta && app._batchMeta[obj.id]) {
        for (const meta of app._batchMeta[obj.id]) {
          try { obj.getBoundingBoxAt(meta.slotId, b2); obj.getMatrixAt(meta.slotId, m4);
            wm.multiplyMatrices(obj.matrixWorld, m4); b2.applyMatrix4(wm); map[meta.guid] = center(b2); } catch(e) {}
        }
      } else if (obj.isInstancedMesh && app._instanceMeta && app._instanceMeta[obj.id]) {
        if (!obj.geometry.boundingBox) obj.geometry.computeBoundingBox();
        const imetas = app._instanceMeta[obj.id];
        for (let idx = 0; idx < imetas.length; idx++) {
          try { obj.getMatrixAt(idx, m4); wm.multiplyMatrices(obj.matrixWorld, m4);
            b2.copy(obj.geometry.boundingBox).applyMatrix4(wm); map[imetas[idx].guid] = center(b2); } catch(e) {}
        }
      }
    });
    return { map: map, modelOffset: { x: app.modelOffset.x, y: app.modelOffset.y, z: app.modelOffset.z } };
  });
  const mo = canvas.modelOffset;

  await br.close(); server.close();

  // Original extraction AABB CENTER per guid (source of truth, real IFC extraction), in IFC coords.
  // Center = (min+max)/2 — matches the canvas placement point (viewer places each element at its center).
  // Then bring into THREE space with the viewer's OWN ifc2three (scene.js:362) + its actual modelOffset,
  // so both sides are in ONE frame. (The earlier "drift" was purely IFC-vs-THREE axis mismatch.)
  const ifc2three = (ix,iy,iz) => [ ix - mo.x, iz - mo.z, -(iy - mo.y) ];   // verbatim scene.js:362
  // GUID namespace normalization: some served DBs prefix GUIDs (e.g. SC = "S0_0_Schependomlaan_<ifcguid>") while
  // the raw extraction uses bare IFC GUIDs. Stripping the EXACT prefix (opt-in via GUID_PREFIX) compares the SAME
  // elements — NOT a fudge: verified that stripped served == bare input byte-for-byte (3284/3284). Default off → SH/DX unchanged.
  const GPFX = process.env.GUID_PREFIX || '';
  const norm = (g) => (GPFX && g.startsWith(GPFX)) ? g.slice(GPFX.length) : g;
  if (GPFX) console.log(`  §GUID-NORM stripping prefix "${GPFX}" from canvas+served GUIDs (verified pure namespace).`);
  const odb = new Database(ORIG_DB, { readonly:true });
  const orig = {};
  for (const row of odb.prepare('SELECT em.guid g,(r.minX+r.maxX)/2.0 x,(r.minY+r.maxY)/2.0 y,(r.minZ+r.maxZ)/2.0 z FROM elements_meta em JOIN elements_rtree r ON em.id=r.id').all())
    orig[norm(row.g)] = ifc2three(row.x, row.y, row.z);
  odb.close();
  const canvasMap = {};
  for (const k of Object.keys(canvas.map)) canvasMap[norm(k)] = canvas.map[k];

  // DEBUG: dump absolute canvas vs orig vs served(center, bbox) for first 6 matched guids
  const sdb = new Database(path.join(BLD, BUILDING + '_extracted.db'), { readonly:true });
  const served = {};
  for (const r of sdb.prepare('SELECT guid g,center_x cx,center_y cy,center_z cz,bbox_x bx,bbox_y by,bbox_z bz FROM element_transforms').all()) served[norm(r.g)]=r;
  sdb.close();
  const dbg = Object.keys(orig).filter(g=>g in canvasMap).slice(0,6);
  console.log('\n  DEBUG abs (m):  guid | canvas-pos | orig-center | served-center');
  for (const g of dbg) {
    const c=canvasMap[g], o=orig[g], s=served[g];
    const f=a=>a.map(v=>v.toFixed(2)).join(',');
    console.log(`   ${g.slice(-6)}  C[${f(c)}] O[${f(o)}] Sctr[${s.cx.toFixed(2)},${s.cy.toFixed(2)},${s.cz.toFixed(2)}]`);
  }

  // CLASS MAP (guid → ifc_class) from the extraction — for the per-class drift breakdown (defensive: missing
  // table/column → 'UNKNOWN', never throws). Read from the served db (already open above would have closed; reopen).
  const clsMap = {};
  try {
    const cdb = new Database(path.join(BLD, BUILDING + '_extracted.db'), { readonly:true });
    for (const r of cdb.prepare('SELECT guid g, ifc_class c FROM elements_meta').all()) clsMap[norm(r.g)] = r.c || 'UNKNOWN';
    cdb.close();
  } catch (e) { console.log(`  §CLASS-MAP unavailable (${e.message}) — breakdown will read UNKNOWN`); }

  // ALL-PAIRS RELATIVE OFFSET — proven geo_verify.py method (worst mm, cancels global recenter)
  const both = Object.keys(orig).filter(g => g in canvasMap);
  console.log(`\n  GUIDs: canvas=${Object.keys(canvasMap).length}  original=${Object.keys(orig).length}  matched=${both.length}`);
  // Per-ELEMENT drift attribution (typed arrays = no hashing in the O(n²) hot loop): how many >1mm pairs each
  // element participates in, and its worst pair-drift. A class that dominates these is the culprit to trace.
  const N = both.length, dCount = new Float64Array(N), dWorstE = new Float64Array(N);
  let total=0, match=0, drift=0, worst=0, worstPair='';
  for (let i=0;i<N;i++) for (let j=i+1;j<N;j++) {
    const a=both[i], b=both[j], c1=canvasMap[a], c2=canvasMap[b], e1=orig[a], e2=orig[b];
    let err=0; for (let k=0;k<3;k++){ const e=Math.abs((c2[k]-c1[k])-(e2[k]-e1[k]))*1000; if(e>err)err=e; }
    total++; if (err<=1.0) match++; else { drift++; if(err>worst){worst=err; worstPair=a+' <> '+b;}
      dCount[i]++; dCount[j]++; if(err>dWorstE[i])dWorstE[i]=err; if(err>dWorstE[j])dWorstE[j]=err; }
  }
  console.log(`  Pairs: ${total}  MATCH: ${match}  DRIFT: ${drift}  Worst: ${worst.toFixed(3)}mm`);
  if (worstPair) console.log(`  Worst at: ${worstPair}`);
  // §CLASS-DRIFT — aggregate the per-element attribution by ifc_class. `drifters` = elements with ≥1 >1mm pair;
  // `worst` = worst pair-drift any element of that class touches; `pairhits` = total drifting-pair incidences.
  // This LOCALISES a global RED to specific classes (e.g. recovered IfcBuildingElementPart / instanced-batched).
  if (drift > 0) {
    const byCls = {};
    for (let i=0;i<N;i++) {
      const cl = clsMap[both[i]] || 'UNKNOWN';
      const e = byCls[cl] || (byCls[cl] = { n:0, drifters:0, worst:0, pairhits:0 });
      e.n++; if (dCount[i] > 0) { e.drifters++; e.pairhits += dCount[i]; if (dWorstE[i] > e.worst) e.worst = dWorstE[i]; }
    }
    const rows = Object.entries(byCls).sort((a,b)=> b[1].worst - a[1].worst);
    console.log(`\n  §CLASS-DRIFT — per-ifc_class attribution of the ${drift} drifting pairs (sorted by worst):`);
    console.log(`     ${'ifc_class'.padEnd(26)} ${'elems'.padStart(6)} ${'drift'.padStart(6)} ${'worst(mm)'.padStart(10)} ${'pairhits'.padStart(9)}`);
    for (const [cl, e] of rows) {
      if (e.drifters === 0) continue;
      console.log(`     ${cl.padEnd(26)} ${String(e.n).padStart(6)} ${String(e.drifters).padStart(6)} ${e.worst.toFixed(1).padStart(10)} ${String(e.pairhits).padStart(9)}`);
    }
    const clean = rows.filter(([,e])=>e.drifters===0).map(([c])=>c);
    if (clean.length) console.log(`     §CLEAN (0 drift): ${clean.join(', ')}`);
    // §ROGUES — with all-pairs, a SMALL mislocated subset makes EVERY element pair-drift against it (why every
    // class shows drifters). Rank elements by drift-pair count: the rogues touch ~N pairs; innocents touch few.
    // Dump the top rogues with canvas-vs-orig offset (Δ relative to the building median, frame-invariant) so the
    // ACTUAL mislocation is visible, not just "everything drifts". This is where to trace, not the class table.
    const idx = Array.from({length:N}, (_,i)=>i).sort((a,b)=> dCount[b]-dCount[a]);
    // building median position (canvas & orig) to express each rogue's offset frame-invariantly
    const med = (arr,k)=>{ const v=both.map(g=>arr[g][k]).sort((x,y)=>x-y); return v[v.length>>1]; };
    const cm=[0,1,2].map(k=>med(canvasMap,k)), om=[0,1,2].map(k=>med(orig,k));
    console.log(`\n  §ROGUES — top elements by drifting-pair count (Δ = (canvas−medianCanvas) − (orig−medianOrig), mm):`);
    for (let r=0;r<Math.min(10,N);r++) {
      const i=idx[r]; if (dCount[i]===0) break; const g=both[i], c=canvasMap[g], o=orig[g];
      const d=[0,1,2].map(k=> (((c[k]-cm[k])-(o[k]-om[k]))*1000).toFixed(0));
      console.log(`     ${g.slice(-8)} ${ (clsMap[g]||'?').padEnd(22)} pairs=${String(dCount[i]).padStart(5)} worst=${dWorstE[i].toFixed(1).padStart(7)}mm  Δ=[${d.join(',')}]`);
    }
    const rogueShare = idx.slice(0,Math.min(50,N)).reduce((s,i)=>s+(dCount[i]>N*0.5?1:0),0);
    console.log(`     §ROGUE-COUNT ${rogueShare} element(s) drift against >50% of the building (the mislocated cluster); the rest pair-drift only against these.`);
  }
  // TRULY-REVEALING whitebox header — a session reads THIS and knows exactly what was proven, against what, how,
  // and what was NOT covered, without re-running or re-deriving the frame doctrine.
  console.log(`  §METHOD all-pairs RELATIVE-OFFSET (geo_verify) — translation/convention INVARIANT, no frame fudge.`);
  console.log(`  §ORACLE raw-IFC extraction ${path.basename(ORIG_DB)} (source of truth). NOT output.db — that is the COOKED`);
  console.log(`          output of Java's BOM exercise (it carries compile-introduced perturbations absent upstream).`);
  console.log(`  §COVERAGE canvas=${Object.keys(canvasMap).length} original=${Object.keys(orig).length} matched=${both.length} (unmatched = generated/no-GUID, not gated).`);
  console.log(`  §TOLERANCE pass ≤1.0mm per pair (not loosened). Gate = 0 drift over ALL pairs.`);
  if (drift===0 && total>0) console.log(`\n  §ROSETTA-CANVAS VERDICT: ${both.length} elements, ${total} pairs, ${worst.toFixed(3)}mm worst. ZERO DRIFT — the building survives drop→canvas faithful to the RAW extraction.`);
  else if (drift>0)         console.log(`\n  §ROSETTA-CANVAS VERDICT: ${drift} DRIFT(s). Worst ${worst.toFixed(3)}mm at ${worstPair}`);
  else                      console.log(`\n  §ROSETTA-CANVAS VERDICT: no pairs (wrong original DB? needs elements_rtree + matching GUIDs).`);
  process.exit(drift===0 && total>0 ? 0 : 1);
})().catch(e => { console.error('FATAL', e); process.exit(2); });
