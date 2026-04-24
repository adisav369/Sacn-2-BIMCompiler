// import.js — IFC Import: file picker → web-ifc worker → sql.js DBs → IndexedDB → viewer
// S220: "IFC to 5D in 60 seconds. On your phone. Zero install."

function setupImport(A) {
  const IMPORT_DB_NAME = 'bim_ootb_imports';
  const IMPORT_STORE = 'buildings';

  // ── IndexedDB for imported buildings ──
  function openImportDB() {
    return new Promise((resolve) => {
      const req = indexedDB.open(IMPORT_DB_NAME, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(IMPORT_STORE);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    });
  }

  function saveImport(key, value) {
    return openImportDB().then(db => {
      if (!db) return;
      return new Promise((resolve) => {
        const tx = db.transaction(IMPORT_STORE, 'readwrite');
        tx.objectStore(IMPORT_STORE).put(value, key);
        tx.oncomplete = () => resolve();
      });
    });
  }

  function getImport(key) {
    return openImportDB().then(db => {
      if (!db) return null;
      return new Promise((resolve) => {
        const tx = db.transaction(IMPORT_STORE, 'readonly');
        const req = tx.objectStore(IMPORT_STORE).get(key);
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => resolve(null);
      });
    });
  }

  function deleteImport(key) {
    return openImportDB().then(db => {
      if (!db) return;
      return new Promise((resolve) => {
        const tx = db.transaction(IMPORT_STORE, 'readwrite');
        tx.objectStore(IMPORT_STORE).delete(key);
        tx.oncomplete = () => resolve();
      });
    });
  }

  function listImports() {
    return openImportDB().then(db => {
      if (!db) return [];
      return new Promise((resolve) => {
        const tx = db.transaction(IMPORT_STORE, 'readonly');
        const store = tx.objectStore(IMPORT_STORE);
        const req = store.getAllKeys();
        req.onsuccess = () => {
          const keys = req.result;
          if (keys.length === 0) { resolve([]); return; }
          const items = [];
          let done = 0;
          for (const key of keys) {
            const r2 = store.get(key);
            r2.onsuccess = () => {
              items.push({ key, meta: r2.result ? r2.result.meta : null });
              done++;
              if (done === keys.length) resolve(items);
            };
            r2.onerror = () => { done++; if (done === keys.length) resolve(items); };
          }
        };
        req.onerror = () => resolve([]);
      });
    });
  }

  // ── Build sql.js DBs from extracted data ──
  function buildDatabases(SQL, data) {
    // Extracted DB — metadata, transforms, spatial structure
    const extDb = new SQL.Database();
    extDb.run(`CREATE TABLE IF NOT EXISTS project_metadata (key TEXT PRIMARY KEY, value TEXT)`);
    extDb.run(`INSERT INTO project_metadata VALUES ('project_name', ?), ('schema_version', 'IFC_IMPORT'), ('import_date', ?)`,
      [data.meta.name, new Date().toISOString()]);

    extDb.run(`CREATE TABLE IF NOT EXISTS elements_meta (
      guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT,
      storey TEXT, discipline TEXT, material_name TEXT, building TEXT
    )`);

    extDb.run(`CREATE TABLE IF NOT EXISTS element_transforms (
      guid TEXT PRIMARY KEY, m00 REAL, m01 REAL, m02 REAL, m03 REAL,
      m10 REAL, m11 REAL, m12 REAL, m13 REAL,
      m20 REAL, m21 REAL, m22 REAL, m23 REAL,
      m30 REAL, m31 REAL, m32 REAL, m33 REAL
    )`);

    extDb.run(`CREATE TABLE IF NOT EXISTS simple_qto (
      guid TEXT, measurement_type TEXT, total_quantity REAL, unit TEXT,
      total_cost_rm REAL, ifc_class TEXT
    )`);

    // Batch insert elements
    const stmtEl = extDb.prepare(`INSERT OR IGNORE INTO elements_meta VALUES (?,?,?,?,?,?,?)`);
    for (const el of data.elements) {
      stmtEl.run([el.guid, el.ifcClass, el.name, el.storey, el.discipline, el.material, data.meta.name]);
    }
    stmtEl.free();

    // Batch insert transforms
    const transformMap = {};
    for (const t of data.transforms) transformMap[t.guid] = t.matrix;

    const stmtTr = extDb.prepare(`INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
    for (const t of data.transforms) {
      const m = t.matrix;
      stmtTr.run([t.guid, m[0],m[1],m[2],m[3], m[4],m[5],m[6],m[7], m[8],m[9],m[10],m[11], m[12],m[13],m[14],m[15]]);
    }
    stmtTr.free();

    // Library DB — geometry BLOBs
    const libDb = new SQL.Database();
    libDb.run(`CREATE TABLE IF NOT EXISTS component_geometries (
      guid TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT
    )`);

    const stmtGeo = libDb.prepare(`INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)`);
    for (const g of data.geometries) {
      stmtGeo.run([g.guid, new Uint8Array(g.vertices), new Uint8Array(g.indices), data.meta.name]);
    }
    stmtGeo.free();

    // Export as ArrayBuffers
    const extBuf = extDb.export().buffer;
    const libBuf = libDb.export().buffer;
    extDb.close();
    libDb.close();

    return { extractedDb: extBuf, libraryDb: libBuf };
  }

  // ── Process IFC file ──
  A.importIFC = async function(file) {
    const status = document.getElementById('import-status');
    const progressBar = document.getElementById('import-progress-bar');
    const importZone = document.getElementById('import-zone');
    if (status) status.textContent = 'Reading file...';
    if (progressBar) { progressBar.style.width = '0%'; progressBar.parentElement.style.display = 'block'; }

    const sizeMB = (file.size / 1024 / 1024).toFixed(1);
    if (file.size > 200 * 1024 * 1024) {
      if (status) status.textContent = 'Very large file (' + sizeMB + 'MB) — may take a few minutes';
    } else if (file.size > 50 * 1024 * 1024) {
      if (status) status.textContent = 'Large file (' + sizeMB + 'MB) — please wait...';
    }

    const arrayBuffer = await file.arrayBuffer();
    console.log('[S220] §IMPORT_START file=' + file.name + ' size=' + sizeMB + 'MB');

    return new Promise((resolve, reject) => {
      const workerUrl = new URL('import_worker.js', location.href).href;
      const worker = new Worker(workerUrl);

      worker.onmessage = async function(e) {
        const msg = e.data;
        if (msg.type === 'progress') {
          if (status) status.textContent = msg.phase;
          if (progressBar) progressBar.style.width = msg.pct + '%';
          return;
        }
        if (msg.type === 'error') {
          console.log('[S220] §IMPORT_ERROR ' + msg.message);
          if (status) status.textContent = 'Import failed: ' + msg.message;
          if (progressBar) { progressBar.style.background = '#cc4444'; }
          worker.terminate();
          reject(new Error(msg.message));
          return;
        }
        if (msg.type === 'done') {
          if (status) status.textContent = 'Building databases...';
          console.log('[S220] §IMPORT_PARSED elements=' + msg.meta.elementCount + ' geom=' + msg.meta.geomCount);

          // Build sql.js DBs on main thread (sql.js already loaded)
          try {
            const SQL = await initSqlJs({ locateFile: f => 'https://sql.js.org/dist/' + f });
            const dbs = buildDatabases(SQL, msg);

            // Save to IndexedDB
            const record = {
              meta: msg.meta,
              extractedDb: dbs.extractedDb,
              libraryDb: dbs.libraryDb,
            };
            await saveImport(file.name, record);

            console.log('[S220] §IMPORT_SAVED key=' + file.name +
              ' ext=' + (dbs.extractedDb.byteLength / 1024).toFixed(0) + 'KB' +
              ' lib=' + (dbs.libraryDb.byteLength / 1024).toFixed(0) + 'KB');

            if (status) status.textContent = 'Imported ' + msg.meta.elementCount + ' elements';
            if (progressBar) { progressBar.style.width = '100%'; progressBar.style.background = '#44cc44'; }

            // Refresh card list
            if (A.renderImportCards) A.renderImportCards();

            worker.terminate();
            resolve(record);
          } catch(dbErr) {
            console.log('[S220] §IMPORT_DB_ERROR ' + dbErr.message);
            if (status) status.textContent = 'DB build failed: ' + dbErr.message;
            worker.terminate();
            reject(dbErr);
          }
        }
      };

      worker.onerror = function(err) {
        console.log('[S220] §IMPORT_WORKER_ERROR ' + err.message);
        if (status) status.textContent = 'Worker error: ' + err.message;
        worker.terminate();
        reject(err);
      };

      worker.postMessage({ arrayBuffer, filename: file.name }, [arrayBuffer]);
    });
  };

  // ── Open imported building in viewer ──
  A.openImported = async function(key) {
    const record = await getImport(key);
    if (!record) { alert('Building not found in storage'); return; }

    // Store DBs in session cache so viewer can load them
    const cacheDb = await A.openCacheDB();
    if (cacheDb) {
      const importDbUrl = 'import://' + key + '/extracted';
      const importLibUrl = 'import://' + key + '/library';
      await new Promise(resolve => {
        const tx = cacheDb.transaction(A.CACHE_STORE, 'readwrite');
        tx.objectStore(A.CACHE_STORE).put(record.extractedDb, importDbUrl);
        tx.objectStore(A.CACHE_STORE).put(record.libraryDb, importLibUrl);
        tx.oncomplete = resolve;
      });

      // Open viewer with import:// URLs — cachedFetch will find them in IndexedDB
      const viewerBase = location.href.replace(/[^/]*$/, '');
      const viewerUrl = viewerBase + 'sandbox/index.html?db=' +
        encodeURIComponent(importDbUrl) + '&lib=' + encodeURIComponent(importLibUrl);
      window.open(viewerUrl, '_blank');
    }
  };

  // ── Delete imported building ──
  A.deleteImported = async function(key) {
    await deleteImport(key);
    if (A.renderImportCards) A.renderImportCards();
    console.log('[S220] §IMPORT_DELETE key=' + key);
  };

  // ── Render import cards (for landing page) ──
  A.renderImportCards = async function() {
    const container = document.getElementById('my-buildings-grid');
    const section = document.getElementById('my-buildings-section');
    if (!container || !section) return;

    const imports = await listImports();
    if (imports.length === 0) {
      section.style.display = 'none';
      return;
    }
    section.style.display = 'block';
    container.innerHTML = '';

    const DISC_COLORS = {
      ARC: '#4488ff', STR: '#44cccc', MEP: '#44cc44',
      ELEC: '#cccc44', FP: '#cc8844', ACMV: '#cc4444',
      PLB: '#8844cc',
    };

    for (const item of imports) {
      if (!item.meta) continue;
      const card = document.createElement('div');
      card.className = 'card';
      const discs = item.meta.disciplines || {};
      const total = item.meta.elementCount || 0;
      const discBars = Object.entries(discs).map(([d, c]) => {
        const pct = Math.max(3, (c / total) * 100);
        const color = DISC_COLORS[d] || '#888';
        return '<span class="disc-bar" style="width:' + pct + '%;background:' + color + '" title="' + d + ': ' + c + '"></span>';
      }).join('');

      card.innerHTML =
        '<div class="name">' + item.meta.name + '</div>' +
        '<div class="meta">' +
          '<b>' + total.toLocaleString() + '</b> elements' +
          ' · ' + Object.keys(discs).join(', ') +
        '</div>' +
        '<div class="disc-bars">' + discBars + '</div>' +
        '<div style="display:flex;gap:6px;margin-top:10px">' +
          '<button class="open-btn" style="flex:1" data-key="' + item.key + '">Open</button>' +
          '<button class="open-btn" style="flex:0;padding:6px 10px;background:rgba(204,68,68,0.15);border-color:rgba(204,68,68,0.3);color:#cc4444" data-del="' + item.key + '">x</button>' +
        '</div>';

      card.querySelector('[data-key]').onclick = function() { A.openImported(this.dataset.key); };
      card.querySelector('[data-del]').onclick = function(e) {
        e.stopPropagation();
        if (confirm('Delete ' + item.meta.name + '?')) A.deleteImported(this.dataset.del);
      };
      container.appendChild(card);
    }
  };

  // ── Wire up drop zone + file picker ──
  const dropZone = document.getElementById('import-zone');
  const fileInput = document.getElementById('import-file-input');

  if (dropZone) {
    // Drag and drop (desktop)
    dropZone.addEventListener('dragover', function(e) {
      e.preventDefault();
      dropZone.style.borderColor = '#4fc3f7';
      dropZone.style.background = 'rgba(79,195,247,0.1)';
    });
    dropZone.addEventListener('dragleave', function() {
      dropZone.style.borderColor = 'rgba(79,195,247,0.3)';
      dropZone.style.background = 'rgba(79,195,247,0.04)';
    });
    dropZone.addEventListener('drop', function(e) {
      e.preventDefault();
      dropZone.style.borderColor = 'rgba(79,195,247,0.3)';
      dropZone.style.background = 'rgba(79,195,247,0.04)';
      const file = e.dataTransfer.files[0];
      if (file && /\.ifc$/i.test(file.name)) {
        A.importIFC(file);
      } else {
        document.getElementById('import-status').textContent = 'Please drop an .ifc file';
      }
    });

    // Click to browse (phone + desktop)
    dropZone.addEventListener('click', function() {
      if (fileInput) fileInput.click();
    });
  }

  if (fileInput) {
    fileInput.addEventListener('change', function() {
      const file = fileInput.files[0];
      if (file && /\.ifc$/i.test(file.name)) {
        A.importIFC(file);
      }
      fileInput.value = ''; // reset for re-import of same file
    });
  }

  // Render existing imports on load
  A.renderImportCards();
}
