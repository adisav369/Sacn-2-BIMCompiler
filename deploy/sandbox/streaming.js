// streaming.js — DB loading, building streaming, geometry cache
function setupStreaming(A) {
  A.streamQueue = [];
  A.streamIdx = 0;
  A.streaming = false;
  A.savedStreams = {};

  A.drawBuildingBoxes = function() {
    const rows = A.db.exec(`
      SELECT m.building, m.discipline,
        MIN(t.center_x) - 5, MIN(t.center_y) - 5, MIN(t.center_z),
        MAX(t.center_x) + 5, MAX(t.center_y) + 5, MAX(t.center_z) + 3
      FROM elements_meta m
      JOIN element_transforms t ON t.guid = m.guid
      GROUP BY m.building, m.discipline
    `);
    if (!rows.length) return;

    for (const row of rows[0].values) {
      const [bld, disc, minX, minY, minZ, maxX, maxY, maxZ] = row;
      const color = A.DISC_COLORS[disc] || A.DEFAULT_COLOR;
      const c = A.ifc2three((minX+maxX)/2, (minY+maxY)/2, (minZ+maxZ)/2);
      const sx = maxX - minX;
      const sy = maxZ - minZ;
      const sz = maxY - minY;
      if (sx < 0.1 || sy < 0.1 || sz < 0.1) continue;
      const geo = new THREE.BoxGeometry(sx, sy, sz);
      const edges = new THREE.EdgesGeometry(geo);
      const line = new THREE.LineSegments(edges,
        new THREE.LineBasicMaterial({ color, opacity: 0.6, transparent: true }));
      line.position.set(c.x, c.y, c.z);
      line.userData = { building: bld, discipline: disc };
      A.scene.add(line);
    }

    document.getElementById('s-buildings').textContent =
      Object.keys(A.buildingCentres).length.toLocaleString();
    document.getElementById('s-elements').textContent = A.totalElements.toLocaleString();
  };

  A.startStreaming = function() {
    let nearest = null, nearestDist = Infinity;
    for (const [name, bc] of Object.entries(A.buildingCentres)) {
      const t = A.ifc2three(bc.ix, bc.iy, bc.iz);
      const dx = t.x - A.camera.position.x;
      const dz = t.z - A.camera.position.z;
      const d = Math.sqrt(dx*dx + dz*dz);
      if (d < nearestDist) { nearestDist = d; nearest = name; }
    }
    if (!nearest) return;
    console.log(`[S192] §DS_AUTO_START bld=${nearest} dist=${nearestDist.toFixed(0)}m`);
    A.streamBuilding(nearest);
  };

  A.streamBuilding = function(nearest) {
    if (A.activeBuilding && A.streaming && A.streamIdx < A.streamQueue.length) {
      A.savedStreams[A.activeBuilding] = { queue: A.streamQueue, idx: A.streamIdx };
    }
    A.streaming = false;

    if (A.savedStreams[nearest]) {
      A.streamQueue = A.savedStreams[nearest].queue;
      A.streamIdx = A.savedStreams[nearest].idx;
      delete A.savedStreams[nearest];
      A.streaming = true;
      A.activeBuilding = nearest;
      A.activeBuildingTotal = A.streamQueue.length;
      console.log(`[S192] §DS_RESUME bld=${nearest} at=${A.streamIdx}/${A.streamQueue.length}`);
    } else {
      A.streamQueue = [];
      A.streamIdx = 0;
      const rows = A.db.exec(`
        SELECT m.guid, i.geometry_hash, m.material_rgba, m.discipline,
               t.center_x, t.center_y, t.center_z,
               t.rotation_x, t.rotation_y, t.rotation_z,
               m.storey, m.ifc_class
        FROM elements_meta m
        JOIN element_instances i ON m.guid = i.guid
        JOIN element_transforms t ON t.guid = m.guid
        WHERE m.building = '${nearest}'
          AND i.geometry_hash IS NOT NULL
          AND m.ifc_class != 'IfcOpeningElement'
      `);
      if (!rows.length) {
        console.log(`[S192] §DS_EMPTY bld=${nearest} — no streamable elements`);
        return;
      }
      A.streamQueue = rows[0].values;
      A.streamIdx = 0;
      A.streaming = true;
      A.activeBuilding = nearest;
      A.activeBuildingTotal = A.streamQueue.length;
      console.log(`[S192] §DS_QUEUED bld=${nearest} elements=${A.streamQueue.length}`);
    }
    document.getElementById('s-active').textContent = `${nearest}`;
    document.getElementById('s-building-total').textContent = A.activeBuildingTotal.toLocaleString();
    document.getElementById('s-progress').style.width = (A.streamIdx / A.streamQueue.length * 100).toFixed(1) + '%';
    document.getElementById('s-progress').style.background = '#4fc3f7';
    A.status.textContent = `STREAMING ${nearest} — ${A.streamIdx.toLocaleString()}/${A.streamQueue.length.toLocaleString()} elements`;
  };

  A.streamTick = function() {
    if (!A.streaming || !A.libDb || A.streamIdx >= A.streamQueue.length) {
      if (A.streaming && A.streamIdx >= A.streamQueue.length) {
        A.streaming = false;
        if (A.activeBuilding) {
          A.buildingsRendered.add(A.activeBuilding);
          A.populateStoreys(A.activeBuilding);
          A.populateDiscs(A.activeBuilding);
        }
        document.getElementById('s-buildings-done').textContent = A.buildingsRendered.size;
        document.getElementById('s-active').textContent = `${A.activeBuilding} — DONE`;
        document.getElementById('s-active').style.color = '#44cc44';
        document.getElementById('s-current-element').textContent = '';
        document.getElementById('s-progress').style.width = '100%';
        document.getElementById('s-progress').style.background = '#44cc44';
        A.updateHash();
        A.status.textContent = `DONE — ${A.activeBuilding} ${A.streamedCount.toLocaleString()} elements. ${A.buildingsRendered.size} building(s) rendered.`;
      }
      return;
    }

    const batch = Math.min(50, A.streamQueue.length - A.streamIdx);
    const hashesNeeded = new Set();

    for (let i = 0; i < batch; i++) {
      const row = A.streamQueue[A.streamIdx + i];
      const hash = row[1];
      if (hash && !A.meshCache[hash]) hashesNeeded.add(hash);
    }

    if (hashesNeeded.size > 0) {
      const hashList = [...hashesNeeded];
      const ph = hashList.map(() => '?').join(',');
      let fetched = 0;
      for (const table of ['component_geometries', 'base_geometries']) {
        if (fetched > 0) break;
        try {
          const stmt = A.libDb.prepare(
            `SELECT geometry_hash, vertices, faces FROM ${table} WHERE geometry_hash IN (${ph})`
          );
          stmt.bind(hashList);
          while (stmt.step()) {
            const [ghash, vBlob, fBlob] = stmt.get();
            if (vBlob && fBlob) {
              const geo = A.blobToGeometry(vBlob, fBlob);
              if (geo) { A.meshCache[ghash] = geo; fetched++; }
            }
          }
          stmt.free();
          if (fetched > 0) {
            console.log(`[S192] §BLOB_FETCH table=${table} new=${fetched} total_cached=${Object.keys(A.meshCache).length}`);
          }
        } catch (e) {
          // Table doesn't exist — try next
        }
      }
      if (fetched === 0 && hashesNeeded.size > 0) {
        console.warn(`[S192] §BLOB_MISS hashes=${hashesNeeded.size} — no geometry found in library`);
      }
    }

    for (let i = 0; i < batch; i++) {
      const row = A.streamQueue[A.streamIdx + i];
      const [guid, hash, rgba, disc, cx, cy, cz, rotX, rotY, rotZ, storey, ifcClass] = row;

      const geo = A.meshCache[hash];
      if (!geo) continue;

      let r, g, b, a = 1.0;
      const rgbaStr = rgba != null ? String(rgba) : '';
      if (rgbaStr && rgbaStr.includes(',')) {
        const parts = rgbaStr.split(',').map(Number);
        r = parts[0]; g = parts[1]; b = parts[2];
        if (parts.length >= 4 && parts[3] < 1.0) { a = parts[3]; }
      } else {
        r = 0.7; g = 0.7; b = 0.7;
      }

      const matOpts = { color: new THREE.Color(r, g, b), flatShading: true };
      if (a < 1.0) {
        matOpts.transparent = true;
        matOpts.opacity = a;
        matOpts.side = THREE.DoubleSide;
      }
      const mat = new THREE.MeshPhongMaterial(matOpts);
      mat.userData.origOpacity = a;
      mat.userData.origSide = a < 1.0 ? THREE.DoubleSide : THREE.FrontSide;
      if (A.xrayOn) { mat.transparent = true; mat.opacity = 0.15; mat.side = THREE.DoubleSide; }
      const mesh = new THREE.Mesh(geo, mat);

      const pos = A.ifc2three(cx, cy, cz);
      mesh.position.set(pos.x, pos.y, pos.z);

      const rx = rotX || 0, ry = rotY || 0, rz = rotZ || 0;
      if (rx || ry || rz) {
        mesh.rotation.set(rx, rz, -ry);
      }

      mesh.userData.storey = storey || '';
      mesh.userData.disc = disc || '';
      mesh.userData.guid = guid;
      A.guidMap[mesh.id] = guid;
      if (A.wireOn) { mat.wireframe = true; }
      if (A.sectionOn) { mat.clippingPlanes = [A.sectionPlane]; mat.clipShadows = true; }
      if (A.activeStoreyFilter !== null && mesh.userData.storey !== A.activeStoreyFilter) {
        mesh.visible = false;
      }
      if (A.hiddenDiscs.size > 0 && A.hiddenDiscs.has(disc)) {
        mesh.visible = false;
      }

      A.scene.add(mesh);
      A.streamedCount++;
    }

    if (A.streamIdx === 0) {
      let rotCount = 0, placedCount = 0, missCount = 0, matCount = 0, transCount = 0;
      const classCounts = {}, discCounts = {};
      for (let i = 0; i < batch; i++) {
        const row = A.streamQueue[i];
        const rx = row[7] || 0, ry = row[8] || 0, rz = row[9] || 0;
        if (rx || ry || rz) rotCount++;
        const geo = A.meshCache[row[1]];
        if (geo) {
          placedCount++;
          const vc = geo.attributes && geo.attributes.position ? geo.attributes.position.count : 0;
          // Log first 5 with full detail
          if (i < 5) {
            console.log(`[S220] §SAMPLE[${i}] class=${row[11]} rgba="${row[2]}" disc=${row[3]} verts=${vc} pos=(${Number(row[4]).toFixed(1)},${Number(row[5]).toFixed(1)},${Number(row[6]).toFixed(1)})`);
          }
        } else { missCount++; }
        const rgbaStr = row[2] != null ? String(row[2]) : '';
        if (rgbaStr && rgbaStr.includes(',')) {
          matCount++;
          const parts = rgbaStr.split(',').map(Number);
          if (parts.length >= 4 && parts[3] < 1.0) {
            transCount++;
            console.log(`[S220] §GLASS class=${row[11]} alpha=${parts[3]} rgba="${rgbaStr}" guid=${row[0].substring(0,12)}`);
          }
        }
        classCounts[row[11]] = (classCounts[row[11]] || 0) + 1;
        discCounts[row[3]] = (discCounts[row[3]] || 0) + 1;
      }
      console.log(`[S220] §PROOF first_batch=${batch} placed=${placedCount} miss=${missCount} with_material=${matCount} transparent=${transCount} rotation=${rotCount}`);
      console.log(`[S220] §PROOF_CLASSES ${Object.entries(classCounts).map(([c,n])=>c+':'+n).join(' ')}`);
      console.log(`[S220] §PROOF_DISCS ${Object.entries(discCounts).map(([d,n])=>d+':'+n).join(' ')}`);
    }

    A.streamIdx += batch;

    document.getElementById('s-streamed').textContent = A.streamedCount.toLocaleString();
    document.getElementById('s-meshes').textContent = Object.keys(A.meshCache).length.toLocaleString();
    if (A.activeBuildingTotal > 0) {
      document.getElementById('s-progress').style.width = Math.min(100, (A.streamIdx / A.streamQueue.length) * 100).toFixed(1) + '%';
    }
    const lastRow = A.streamQueue[A.streamIdx - 1];
    if (lastRow) {
      document.getElementById('s-current-element').textContent = lastRow[11] || '';
    }
  };

  // DB init
  A.init = async function() {
    A.status.textContent = 'Loading sql.js WebAssembly...';
    const SQL = await initSqlJs({
      locateFile: f => `https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.10.3/${f}`
    });

    if (A.CITY_URL) {
      await A.initCity(SQL);
      return;
    }

    A.status.textContent = `Fetching ${A.DB_URL}...`;
    const dbBuf = await A.cachedFetch(A.DB_URL);
    A.db = new SQL.Database(new Uint8Array(dbBuf));
    console.log(`[S192] §DB_LOADED size=${(dbBuf.byteLength/1024/1024).toFixed(0)}MB`);
    A.status.textContent = `DB loaded (${(dbBuf.byteLength/1024/1024).toFixed(0)}MB). Querying...`;

    const rows = A.db.exec(`
      SELECT m.building, COUNT(*),
        AVG(t.center_x), AVG(t.center_y), AVG(t.center_z)
      FROM elements_meta m
      JOIN element_transforms t ON t.guid = m.guid
      GROUP BY m.building
    `);
    if (rows.length > 0) {
      for (const row of rows[0].values) {
        A.buildingCentres[row[0]] = { ix: row[2], iy: row[3], iz: row[4], count: row[1] };
      }
    }
    console.log(`[S192] §BOOTSTRAP centres=${Object.keys(A.buildingCentres).length}`);

    const allIX = Object.values(A.buildingCentres).map(b => b.ix);
    const allIY = Object.values(A.buildingCentres).map(b => b.iy);
    const allIZ = Object.values(A.buildingCentres).map(b => b.iz);
    if (allIX.length) {
      A.modelOffset.x = (Math.min(...allIX) + Math.max(...allIX)) / 2;
      A.modelOffset.y = (Math.min(...allIY) + Math.max(...allIY)) / 2;
      A.modelOffset.z = (Math.min(...allIZ) + Math.max(...allIZ)) / 2;
    }
    console.log(`[S192] §OFFSET ifc=(${A.modelOffset.x.toFixed(0)}, ${A.modelOffset.y.toFixed(0)}, ${A.modelOffset.z.toFixed(0)})`);

    const zRange = A.db.exec(`SELECT MIN(center_z), MAX(center_z) FROM element_transforms`);
    if (zRange.length > 0) {
      const minZ = zRange[0].values[0][0];
      if (minZ == null) {
        console.log('[S220] §GROUND_SKIP element_transforms has no rows — no viewable geometry');
      } else {
        const groundY = (minZ - A.modelOffset.z) - 2;
        A.ground.position.y = groundY;
        A.ground.visible = true;
        console.log(`[S200] §GROUND minZ_ifc=${minZ.toFixed(1)} groundY=${groundY.toFixed(1)}`);
      }
    }

    const elemRows = A.db.exec(`SELECT COUNT(*) FROM elements_meta`);
    A.totalElements = elemRows[0]?.values[0][0] || 0;
    const discRows = A.db.exec(`SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC`);
    if (discRows.length > 0) {
      for (const r of discRows[0].values) {
        A.discCounts[r[0]] = r[1];
      }
    }

    A.updateHUD();
    A.populateBuildingList();
    A.drawBuildingBoxes();

    A.status.textContent = `Fetching ${A.LIB_URL}...`;
    try {
      const libBuf = await A.cachedFetch(A.LIB_URL);
      A.libDb = new SQL.Database(new Uint8Array(libBuf));
      console.log(`[S192] §LIB_LOADED size=${(libBuf.byteLength/1024/1024).toFixed(0)}MB`);
      try {
        const t = A.libDb.exec("SELECT COUNT(*) FROM component_geometries");
        console.log(`[S192] §LIB_TABLE component_geometries rows=${t[0].values[0][0]}`);
      } catch(e2) {
        console.log(`[S192] §LIB_TABLE component_geometries not found, trying base_geometries`);
        try {
          const t2 = A.libDb.exec("SELECT COUNT(*) FROM base_geometries");
          console.log(`[S192] §LIB_TABLE base_geometries rows=${t2[0].values[0][0]}`);
        } catch(e3) {
          console.log(`[S192] §LIB_ERROR no geometry table found`);
        }
      }
      A.status.textContent = `Library loaded (${(libBuf.byteLength/1024/1024).toFixed(0)}MB). Streaming nearest building...`;
    } catch (e) {
      console.log(`[S200] §LIB_FALLBACK using main DB for geometry (${e.message})`);
      A.libDb = A.db;
      A.status.textContent = `Using main DB for geometry. Streaming...`;
    }

    const bboxQ = A.db.exec(`
      SELECT MIN(center_x), MAX(center_x),
             MIN(center_y), MAX(center_y),
             MIN(center_z), MAX(center_z)
      FROM element_transforms
    `);
    let envW = 500, envD = 500, envH = 100;
    if (bboxQ.length > 0 && bboxQ[0].values[0][0] != null) {
      const [xMin, xMax, yMin, yMax, zMin, zMax] = bboxQ[0].values[0];
      envW = xMax - xMin;
      envD = yMax - yMin;
      envH = zMax - zMin;
    }
    const envelope = Math.max(envW, envD, envH);
    for (const bc of Object.values(A.buildingCentres)) {
      bc.envelope = envelope;
    }
    const dist = Math.max(80, envelope * 1.5);
    const ctr = A.ifc2three(
      (bboxQ[0]?.values[0][0] + bboxQ[0]?.values[0][1]) / 2 || 0,
      (bboxQ[0]?.values[0][2] + bboxQ[0]?.values[0][3]) / 2 || 0,
      (bboxQ[0]?.values[0][4] + bboxQ[0]?.values[0][5]) / 2 || 0
    );
    A.camera.position.set(ctr.x + dist * 0.6, ctr.y + dist * 0.8, ctr.z + dist * 0.6);
    A.camera.far = Math.max(10000, dist * 5);
    A.camera.updateProjectionMatrix();
    A.controls.target.set(ctr.x, ctr.y, ctr.z);
    A.controls.update();
    console.log(`[S203] §CAMERA envelope=${envW.toFixed(0)}x${envD.toFixed(0)}x${envH.toFixed(0)}m dist=${dist.toFixed(0)}m`);

    window._trueNorthAngle = 0;
    try {
      const tnRows = A.db.exec("SELECT value FROM project_metadata WHERE key = 'true_north_angle'");
      if (tnRows.length > 0 && tnRows[0].values.length > 0) {
        window._trueNorthAngle = parseFloat(tnRows[0].values[0][0]) || 0;
        console.log(`[S204] §TRUE_NORTH ${window._trueNorthAngle}° from grid Y`);
      }
    } catch(e) { /* no project_metadata table */ }

    if (A.libDb) {
      const hashParams = A.loadFromHash();
      if (hashParams && hashParams.bld && A.buildingCentres[hashParams.bld]) {
        if (hashParams.cx) {
          A.camera.position.set(Number(hashParams.cx), Number(hashParams.cy), Number(hashParams.cz));
          A.controls.target.set(Number(hashParams.tx), Number(hashParams.ty), Number(hashParams.tz));
          A.controls.update();
        }
        A.streamBuilding(hashParams.bld);
      } else {
        A.startStreaming();
      }
    }
  };

  // URL deep-link
  A.updateHash = function() {
    if (!A.activeBuilding) return;
    const p = A.camera.position;
    const t = A.controls.target;
    location.hash = `bld=${A.activeBuilding}&cx=${p.x.toFixed(0)}&cy=${p.y.toFixed(0)}&cz=${p.z.toFixed(0)}&tx=${t.x.toFixed(0)}&ty=${t.y.toFixed(0)}&tz=${t.z.toFixed(0)}`;
  };

  A.loadFromHash = function() {
    const h = location.hash.slice(1);
    if (!h) return null;
    const params = {};
    h.split('&').forEach(p => { const [k, v] = p.split('='); params[k] = v; });
    return params;
  };

  // Clear
  A.clearStreamed = function() {
    const toRemove = [];
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground) toRemove.push(obj);
    });
    toRemove.forEach(obj => {
      A.scene.remove(obj);
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) obj.material.dispose();
    });
    A.streamedCount = 0;
    A.streaming = false;
    A.streamQueue = [];
    A.streamIdx = 0;
    A.activeBuilding = null;
    A.activeBuildingTotal = 0;
    A.buildingsRendered.clear();
    document.getElementById('s-streamed').textContent = '0';
    document.getElementById('s-building-total').textContent = '0';
    document.getElementById('s-buildings-done').textContent = '0';
    document.getElementById('s-active').textContent = '—';
    document.getElementById('s-active').style.color = '#4fc3f7';
    console.log(`[S192] §CLEAR removed=${toRemove.length}`);
    A.status.textContent = 'Cleared. Search and click a building to stream.';
  };

  // Fly to building
  A.flyTo = function(buildingName) {
    const bc = A.buildingCentres[buildingName];
    if (!bc) return;
    const t = A.ifc2three(bc.ix, bc.iy, bc.iz);
    const dist = Math.max(50, Math.sqrt(bc.count) * 1.5);
    A.camera.position.set(t.x + dist * 0.7, t.y + dist * 1.0, t.z + dist * 0.7);
    A.controls.target.set(t.x, t.y, t.z);
    A.camera.far = Math.max(5000, dist * 10);
    A.camera.updateProjectionMatrix();
    A.controls.update();
    console.log(`[S192] §FLY_TO bld=${buildingName} three=(${t.x.toFixed(0)},${t.y.toFixed(0)},${t.z.toFixed(0)}) dist=${dist.toFixed(0)}`);
    document.getElementById('s-active').style.color = '#4fc3f7';
    document.getElementById('s-progress').style.width = '0%';
    document.getElementById('s-progress').style.background = '#4fc3f7';
    A.status.textContent = `Flew to ${buildingName} (${bc.count} elements)`;

    if (A.libDb) {
      A.streamBuilding(buildingName);
    }
  };
}
