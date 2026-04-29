// streaming.js — DB loading, building streaming, geometry cache
function setupStreaming(A) {
  A.streamQueue = [];
  A.streamIdx = 0;
  A.streaming = false;
  A.savedStreams = {};

  A.drawBuildingBoxes = function() {
    const rows = A.dbQuery(`
      SELECT m.building, m.discipline,
        MIN(t.center_x) - 5, MIN(t.center_y) - 5, MIN(t.center_z),
        MAX(t.center_x) + 5, MAX(t.center_y) + 5, MAX(t.center_z) + 3
      FROM elements_meta m
      JOIN element_transforms t ON t.guid = m.guid
      GROUP BY m.building, m.discipline
    `);
    if (!rows.length) return;

    for (const row of rows) {
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
      const rows = A.dbQuery(`
        SELECT m.guid, i.geometry_hash, m.material_rgba, m.discipline,
               t.center_x, t.center_y, t.center_z,
               t.rotation_x, t.rotation_y, t.rotation_z,
               m.storey, m.ifc_class
        FROM elements_meta m
        JOIN element_instances i ON m.guid = i.guid
        JOIN element_transforms t ON t.guid = m.guid
        WHERE m.building = ?
          AND i.geometry_hash IS NOT NULL
          AND m.ifc_class != 'IfcOpeningElement'
      `, [nearest]);
      if (!rows.length) {
        console.log(`[S192] §DS_EMPTY bld=${nearest} — no streamable elements`);
        return;
      }
      A.streamQueue = rows;
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
    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_streaming||'STREAMING {name} — {i}/{n} elements').replace('{name}',nearest).replace('{i}',A.streamIdx.toLocaleString()).replace('{n}',A.streamQueue.length.toLocaleString());
  };

  // ── S231: InstancedMesh batching ─────────────────────────────────────
  // Hashes with 2+ instances get ONE InstancedMesh (1 draw call).
  // Hashes with 1 instance stay as individual Mesh (pick/filter compatible).
  // Material dedup: one MeshPhongMaterial per unique RGBA string.
  // ── S232: Mobile merge — single-instance meshes grouped by storey|disc|rgba ──
  // Bakes transform into vertices, concatenates buffers → ~200 draw calls on mobile.
  A._matCache = {};
  A._instanceMeta = {};  // instancedMesh.id → [{guid,storey,disc,instanceIndex}, ...]
  A._instanceGuids = {}; // guid → {meshId, instanceIndex} for reverse lookup
  A._isMobile = (navigator.maxTouchPoints > 0 && window.screen.width < 1024);

  A._getMaterial = function(rgbaStr) {
    const key = rgbaStr || '_default';
    if (A._matCache[key]) return A._matCache[key];
    let r = 0.7, g = 0.7, b = 0.7, a = 1.0;
    if (rgbaStr && rgbaStr.includes(',')) {
      const parts = rgbaStr.split(',').map(Number);
      r = parts[0]; g = parts[1]; b = parts[2];
      if (parts.length >= 4 && parts[3] < 1.0) a = parts[3];
    }
    const opts = { color: new THREE.Color(r, g, b), flatShading: true };
    if (a < 1.0) { opts.transparent = true; opts.opacity = a; opts.side = THREE.DoubleSide; }
    const mat = new THREE.MeshPhongMaterial(opts);
    mat.userData.origOpacity = a;
    mat.userData.origSide = a < 1.0 ? THREE.DoubleSide : THREE.FrontSide;
    if (A.xrayOn) { mat.transparent = true; mat.opacity = 0.15; mat.side = THREE.DoubleSide; }
    if (A.wireOn) { mat.wireframe = true; }
    if (A.sectionOn) { mat.clippingPlanes = [A.sectionPlane]; mat.clipShadows = true; }
    A._matCache[key] = mat;
    return mat;
  };

  A.streamTick = function() {
    if (!A.streaming || !A.libDb || A.streamIdx >= A.streamQueue.length) {
      if (A.streaming && A.streamIdx >= A.streamQueue.length) {
        // ── Flush: build InstancedMesh for hashes with 2+ elements ──
        A._flushInstanced();
        A.streaming = false;
        if (A.activeBuilding) {
          A.buildingsRendered.add(A.activeBuilding);
          A.populateStoreys(A.activeBuilding);
          A.populateDiscs(A.activeBuilding);
        }
        document.getElementById('s-buildings-done').textContent = A.buildingsRendered.size;
        document.getElementById('s-active').textContent = (typeof _TRL!=='undefined'&&_TRL.ui_active_done||'{name} — DONE').replace('{name}',A.activeBuilding);
        document.getElementById('s-active').style.color = '#44cc44';
        document.getElementById('s-current-element').textContent = '';
        document.getElementById('s-progress').style.width = '100%';
        document.getElementById('s-progress').style.background = '#44cc44';
        A.updateHash();
        const iCount = Object.keys(A._instanceMeta).length;
        A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_done||'DONE — {name} {n} elements ({g} instanced groups). {b} building(s) rendered.').replace('{name}',A.activeBuilding).replace('{n}',A.streamedCount.toLocaleString()).replace('{g}',iCount).replace('{b}',A.buildingsRendered.size);
      }
      return;
    }

    // ── Phase 1: collect ALL elements, fetch ALL geometry (no scene.add yet) ──
    // Process large batches since we're not creating Three.js objects yet
    const batch = Math.min(2000, A.streamQueue.length - A.streamIdx);
    const hashesNeeded = new Set();

    for (let i = 0; i < batch; i++) {
      const row = A.streamQueue[A.streamIdx + i];
      const hash = row[1];
      if (hash && !A.meshCache[hash]) hashesNeeded.add(hash);
    }

    if (hashesNeeded.size > 0) {
      const hashList = [...hashesNeeded];
      let fetched = 0;
      // Fetch in chunks of 200 to avoid sql.js bind limit
      for (let ci = 0; ci < hashList.length; ci += 200) {
        const chunk = hashList.slice(ci, ci + 200);
        const ph = chunk.map(() => '?').join(',');
        for (const table of ['component_geometries', 'base_geometries']) {
          try {
            const stmt = A.libDb.prepare(
              `SELECT geometry_hash, vertices, faces FROM ${table} WHERE geometry_hash IN (${ph})`
            );
            stmt.bind(chunk);
            while (stmt.step()) {
              const [ghash, vBlob, fBlob] = stmt.get();
              if (vBlob && fBlob) {
                const geo = A.blobToGeometry(vBlob, fBlob);
                if (geo) { A.meshCache[ghash] = geo; fetched++; }
              }
            }
            stmt.free();
          } catch (e) {
            // Table doesn't exist — try next
          }
        }
      }
      if (fetched > 0) {
        console.log(`[S231] §BLOB_FETCH new=${fetched} total_cached=${Object.keys(A.meshCache).length}`);
      }
      if (fetched === 0 && hashesNeeded.size > 0) {
        console.warn(`[S231] §BLOB_MISS hashes=${hashesNeeded.size} — no geometry found in library`);
      }
    }

    // ── Phase 2: bucket elements by geometry_hash ──
    // (accumulate into A._pendingInstances for flush at end)
    if (!A._pendingInstances) A._pendingInstances = {};

    for (let i = 0; i < batch; i++) {
      const row = A.streamQueue[A.streamIdx + i];
      const [guid, hash, rgba, disc, cx, cy, cz, rotX, rotY, rotZ, storey, ifcClass] = row;
      if (!hash || !A.meshCache[hash]) continue;
      if (!A._pendingInstances[hash]) A._pendingInstances[hash] = [];
      A._pendingInstances[hash].push({ guid, rgba, disc, cx, cy, cz,
        rotX: rotX || 0, rotY: rotY || 0, rotZ: rotZ || 0,
        storey: storey || '', ifcClass });
      A.streamedCount++;
    }

    if (A.streamIdx === 0) {
      console.log(`[S231] §INSTANCED_STREAM batch=${batch} pending_hashes=${Object.keys(A._pendingInstances).length}`);
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

  // ── S231+S232: Flush pending → InstancedMesh (2+) or Mesh (1) or MergedMesh (mobile) ──
  A._flushInstanced = function() {
    if (!A._pendingInstances) return;
    const _m4 = new THREE.Matrix4();
    const _euler = new THREE.Euler();
    const _quat = new THREE.Quaternion();
    const _pos = new THREE.Vector3();
    const _scale = new THREE.Vector3(1, 1, 1);
    let instancedCount = 0, singleCount = 0, mergedCount = 0, drawCalls = 0;

    // ── S232: On mobile, bucket single-instance elements for merge ──
    const mergeBuckets = {};  // key: "storey|disc|rgba" → [{el, geo}, ...]

    for (const [hash, elements] of Object.entries(A._pendingInstances)) {
      const geo = A.meshCache[hash];
      if (!geo) continue;

      if (elements.length === 1 && A._isMobile) {
        // Mobile: bucket for merge instead of individual Mesh
        const el = elements[0];
        const key = (el.storey || '_') + '|' + (el.disc || '_') + '|' + (el.rgba || '_default');
        if (!mergeBuckets[key]) mergeBuckets[key] = [];
        mergeBuckets[key].push({ el, geo });
      } else if (elements.length === 1) {
        // Desktop: individual Mesh for full pick/filter compat
        const el = elements[0];
        const mat = A._getMaterial(el.rgba);
        const mesh = new THREE.Mesh(geo, mat);
        const pos = A.ifc2three(el.cx, el.cy, el.cz);
        mesh.position.set(pos.x, pos.y, pos.z);
        if (el.rotX || el.rotY || el.rotZ) {
          mesh.rotation.set(el.rotX, el.rotZ, -el.rotY);
        }
        mesh.userData.storey = el.storey;
        mesh.userData.disc = el.disc;
        mesh.userData.guid = el.guid;
        A.guidMap[mesh.id] = el.guid;
        if (A.activeStoreyFilter !== null && el.storey !== A.activeStoreyFilter) mesh.visible = false;
        if (A.hiddenDiscs.size > 0 && A.hiddenDiscs.has(el.disc)) mesh.visible = false;
        A.scene.add(mesh);
        singleCount++;
        drawCalls++;
      } else {
        // 2+ instances — InstancedMesh (both desktop and mobile)
        const mat = A._getMaterial(elements[0].rgba);
        const iMesh = new THREE.InstancedMesh(geo, mat, elements.length);
        iMesh.frustumCulled = false;
        const meta = [];

        for (let i = 0; i < elements.length; i++) {
          const el = elements[i];
          const pos = A.ifc2three(el.cx, el.cy, el.cz);
          _pos.set(pos.x, pos.y, pos.z);
          _euler.set(el.rotX, el.rotZ, -el.rotY);
          _quat.setFromEuler(_euler);
          _m4.compose(_pos, _quat, _scale);
          iMesh.setMatrixAt(i, _m4);

          meta.push({ guid: el.guid, storey: el.storey, disc: el.disc, instanceIndex: i });
          A._instanceGuids[el.guid] = { meshId: iMesh.id, instanceIndex: i };
          A.guidMap[iMesh.id + '_' + i] = el.guid;
        }
        iMesh.instanceMatrix.needsUpdate = true;
        iMesh.userData.isInstanced = true;
        iMesh.userData.hash = hash;
        A._instanceMeta[iMesh.id] = meta;
        A.scene.add(iMesh);
        instancedCount += elements.length;
        drawCalls++;
      }
    }

    // ── S232: Merge single-instance buckets on mobile ──
    if (A._isMobile) {
      for (const [key, items] of Object.entries(mergeBuckets)) {
        if (items.length === 0) continue;
        const [storey, disc, rgba] = key.split('|');

        // Bake transform into vertices and concatenate all geometries in this bucket
        let totalVerts = 0, totalIdx = 0;
        for (const item of items) {
          const srcPos = item.geo.attributes.position;
          totalVerts += srcPos.count;
          totalIdx += item.geo.index ? item.geo.index.count : srcPos.count;
        }

        const mergedPos = new Float32Array(totalVerts * 3);
        const mergedNorm = items[0].geo.attributes.normal ? new Float32Array(totalVerts * 3) : null;
        const mergedIdx = new Uint32Array(totalIdx);
        let vOff = 0, iOff = 0, vBase = 0;
        const _v = new THREE.Vector3();
        const _n = new THREE.Vector3();

        for (const item of items) {
          const el = item.el;
          const srcGeo = item.geo;
          const srcPos = srcGeo.attributes.position;
          const srcNorm = srcGeo.attributes.normal;
          const count = srcPos.count;

          // Build transform matrix for this element
          const pos = A.ifc2three(el.cx, el.cy, el.cz);
          _pos.set(pos.x, pos.y, pos.z);
          _euler.set(el.rotX, el.rotZ, -el.rotY);
          _quat.setFromEuler(_euler);
          _m4.compose(_pos, _quat, _scale);

          // Normal matrix (inverse transpose of upper 3x3)
          const _nm = new THREE.Matrix3().getNormalMatrix(_m4);

          // Bake positions
          for (let v = 0; v < count; v++) {
            _v.set(srcPos.getX(v), srcPos.getY(v), srcPos.getZ(v));
            _v.applyMatrix4(_m4);
            mergedPos[(vOff + v) * 3] = _v.x;
            mergedPos[(vOff + v) * 3 + 1] = _v.y;
            mergedPos[(vOff + v) * 3 + 2] = _v.z;
          }

          // Bake normals
          if (mergedNorm && srcNorm) {
            for (let v = 0; v < count; v++) {
              _n.set(srcNorm.getX(v), srcNorm.getY(v), srcNorm.getZ(v));
              _n.applyMatrix3(_nm).normalize();
              mergedNorm[(vOff + v) * 3] = _n.x;
              mergedNorm[(vOff + v) * 3 + 1] = _n.y;
              mergedNorm[(vOff + v) * 3 + 2] = _n.z;
            }
          }

          // Rebase indices
          if (srcGeo.index) {
            const srcIdx = srcGeo.index;
            for (let j = 0; j < srcIdx.count; j++) {
              mergedIdx[iOff + j] = srcIdx.getX(j) + vBase;
            }
            iOff += srcIdx.count;
          } else {
            for (let j = 0; j < count; j++) {
              mergedIdx[iOff + j] = vBase + j;
            }
            iOff += count;
          }
          vOff += count;
          vBase += count;
        }

        const mergedGeo = new THREE.BufferGeometry();
        mergedGeo.setAttribute('position', new THREE.BufferAttribute(mergedPos, 3));
        if (mergedNorm) mergedGeo.setAttribute('normal', new THREE.BufferAttribute(mergedNorm, 3));
        mergedGeo.setIndex(new THREE.BufferAttribute(mergedIdx, 1));

        const mat = A._getMaterial(rgba === '_default' ? null : rgba);
        const mesh = new THREE.Mesh(mergedGeo, mat);
        mesh.userData.storey = storey === '_' ? '' : storey;
        mesh.userData.disc = disc === '_' ? '' : disc;
        mesh.userData.isMerged = true;
        mesh.userData.mergedCount = items.length;
        if (A.activeStoreyFilter !== null && mesh.userData.storey !== A.activeStoreyFilter) mesh.visible = false;
        if (A.hiddenDiscs.size > 0 && A.hiddenDiscs.has(mesh.userData.disc)) mesh.visible = false;
        A.scene.add(mesh);
        mergedCount += items.length;
        drawCalls++;
      }
    }

    A._pendingInstances = {};
    const label = A._isMobile
      ? `[S232] §FLUSH instanced=${instancedCount} merged=${mergedCount} drawCalls=${drawCalls} (was ${instancedCount + mergedCount})`
      : `[S231] §FLUSH instanced=${instancedCount} single=${singleCount} drawCalls=${drawCalls} (was ${instancedCount + singleCount})`;
    console.log(label);
    document.getElementById('s-meshes').textContent = drawCalls.toLocaleString() + ' draw calls';
  };

  // DB init
  A.init = async function() {
    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_wasm||'Loading WebAssembly...');
    const SQL = await initSqlJs({
      locateFile: f => `https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.10.3/${f}`
    });

    if (A.CITY_URL) {
      await A.initCity(SQL);
      return;
    }

    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_fetching||'Fetching {url}...').replace('{url}',A.DB_URL);
    const dbBuf = await A.cachedFetch(A.DB_URL);
    A.db = new SQL.Database(new Uint8Array(dbBuf));
    console.log(`[S192] §DB_LOADED size=${(dbBuf.byteLength/1024/1024).toFixed(0)}MB`);
    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_db_loaded||'DB loaded ({size}MB). Querying...').replace('{size}',(dbBuf.byteLength/1024/1024).toFixed(0));

    const rows = A.dbQuery(`
      SELECT m.building, COUNT(*),
        AVG(t.center_x), AVG(t.center_y), AVG(t.center_z)
      FROM elements_meta m
      JOIN element_transforms t ON t.guid = m.guid
      GROUP BY m.building
    `);
    for (const row of rows) {
      A.buildingCentres[row[0]] = { ix: row[2], iy: row[3], iz: row[4], count: row[1] };
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

    const zRange = A.dbQuery(`SELECT MIN(center_z), MAX(center_z) FROM element_transforms`);
    if (zRange.length > 0) {
      const minZ = zRange[0][0];
      if (minZ == null) {
        console.log('[S220] §GROUND_SKIP element_transforms has no rows — no viewable geometry');
      } else {
        const groundY = (minZ - A.modelOffset.z) - 2;
        A.ground.position.y = groundY;
        A.ground.visible = true;
        console.log(`[S200] §GROUND minZ_ifc=${minZ.toFixed(1)} groundY=${groundY.toFixed(1)}`);
      }
    }

    const elemRows = A.dbQuery(`SELECT COUNT(*) FROM elements_meta`);
    A.totalElements = elemRows.length ? elemRows[0][0] : 0;
    const discRows = A.dbQuery(`SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC`);
    if (discRows.length > 0) {
      for (const r of discRows) {
        A.discCounts[r[0]] = r[1];
      }
    }

    A.updateHUD();
    A.populateBuildingList();
    A.drawBuildingBoxes();

    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_fetching||'Fetching {url}...').replace('{url}',A.LIB_URL);
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
      A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_lib_loaded||'Library loaded ({size}MB). Streaming nearest building...').replace('{size}',(libBuf.byteLength/1024/1024).toFixed(0));
    } catch (e) {
      console.log(`[S200] §LIB_FALLBACK using main DB for geometry (${e.message})`);
      A.libDb = A.db;
      A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_geom||'Using main DB for geometry. Streaming...');
    }

    const bboxQ = A.dbQuery(`
      SELECT MIN(center_x), MAX(center_x),
             MIN(center_y), MAX(center_y),
             MIN(center_z), MAX(center_z)
      FROM element_transforms
    `);
    let envW = 500, envD = 500, envH = 100;
    if (bboxQ.length > 0 && bboxQ[0][0] != null) {
      const [xMin, xMax, yMin, yMax, zMin, zMax] = bboxQ[0];
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
      (bboxQ[0]?.[0] + bboxQ[0]?.[1]) / 2 || 0,
      (bboxQ[0]?.[2] + bboxQ[0]?.[3]) / 2 || 0,
      (bboxQ[0]?.[4] + bboxQ[0]?.[5]) / 2 || 0
    );
    A.camera.position.set(ctr.x + dist * 0.6, ctr.y + dist * 0.8, ctr.z + dist * 0.6);
    A.camera.far = Math.max(10000, dist * 5);
    A.camera.updateProjectionMatrix();
    A.controls.target.set(ctr.x, ctr.y, ctr.z);
    A.controls.update();
    console.log(`[S203] §CAMERA envelope=${envW.toFixed(0)}x${envD.toFixed(0)}x${envH.toFixed(0)}m dist=${dist.toFixed(0)}m`);

    window._trueNorthAngle = 0;
    try {
      const tnRows = A.dbQuery("SELECT value FROM project_metadata WHERE key = 'true_north_angle'");
      if (tnRows.length > 0) {
        window._trueNorthAngle = parseFloat(tnRows[0][0]) || 0;
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

  // Clear — handles both Mesh and InstancedMesh
  A.clearStreamed = function() {
    const toRemove = A.collectMeshes(o => o.isMesh || o.isInstancedMesh);
    toRemove.forEach(obj => {
      A.scene.remove(obj);
      if (obj.geometry && !obj.userData.isInstanced) obj.geometry.dispose();
      if (obj.material && !obj.material._shared) obj.material.dispose();
    });
    A.streamedCount = 0;
    A.streaming = false;
    A.streamQueue = [];
    A.streamIdx = 0;
    A.activeBuilding = null;
    A.activeBuildingTotal = 0;
    A.buildingsRendered.clear();
    A._pendingInstances = {};
    A._instanceMeta = {};
    A._instanceGuids = {};
    A._matCache = {};
    document.getElementById('s-streamed').textContent = '0';
    document.getElementById('s-building-total').textContent = '0';
    document.getElementById('s-buildings-done').textContent = '0';
    document.getElementById('s-active').textContent = '—';
    document.getElementById('s-active').style.color = '#4fc3f7';
    console.log(`[S231] §CLEAR removed=${toRemove.length}`);
    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_status_search||'Cleared. Search and click a building to stream.');
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
    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_flew_to||'Flew to {name} ({n} elements)').replace('{name}',buildingName).replace('{n}',bc.count);

    if (A.libDb) {
      A.streamBuilding(buildingName);
    }
  };
}
