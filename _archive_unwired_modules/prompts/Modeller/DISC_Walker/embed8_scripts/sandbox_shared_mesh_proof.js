// sandbox_shared_mesh_proof.js — proves the ACTUAL target shape: N buildings' component_geometries
// merged into ONE shared mesh.db (tagged by building, INSERT OR IGNORE), each building's OWN meta.db
// still resolves 1:1 against the SHARED file via the unmodified real_geometry.js loader.
'use strict';
const fs = require('fs');
const path = require('path');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const RealGeometry = require('/home/red1/bim-ootb/modeller/real_geometry.js');

const SCRATCH = __dirname;
const BUILDINGS = ['SampleHouse', 'Duplex'];

(async () => {
  const SQL = await initSqlJs();
  const metaDbs = {};
  const sharedMesh = new SQL.Database();
  let meshCreated = false;

  for (const b of BUILDINGS) {
    const allBuf = fs.readFileSync(path.join(SCRATCH, b + '_all.db'));
    const allDb = new SQL.Database(new Uint8Array(allBuf));

    const metaDb = new SQL.Database();
    const tables = allDb.exec("SELECT name, sql FROM sqlite_master WHERE type='table' AND name != 'component_geometries'")[0];
    tables.values.forEach(([name, sql]) => {
      metaDb.run(sql);
      const rows = allDb.exec('SELECT * FROM ' + name);
      if (rows.length && rows[0].values.length) {
        const cols = rows[0].columns;
        const ph = cols.map(() => '?').join(',');
        const stmt = metaDb.prepare('INSERT INTO ' + name + ' VALUES (' + ph + ')');
        rows[0].values.forEach(v => stmt.run(v));
        stmt.free();
      }
    });
    metaDbs[b] = metaDb;

    if (!meshCreated) {
      const geoSql = allDb.exec("SELECT sql FROM sqlite_master WHERE type='table' AND name='component_geometries'")[0].values[0][0];
      sharedMesh.run(geoSql);
      meshCreated = true;
    }
    const geoRows = allDb.exec('SELECT * FROM component_geometries')[0];
    const gStmt = sharedMesh.prepare('INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)');
    geoRows.values.forEach(v => gStmt.run(v));
    gStmt.free();
    console.log('§SHARED-MESH-MERGE building=' + b + ' addedRows=' + geoRows.values.length);
    allDb.close();
  }

  const totalMeshRows = sharedMesh.exec('SELECT COUNT(*) FROM component_geometries')[0].values[0][0];
  console.log('§SHARED-MESH-TOTAL rows=' + totalMeshRows);

  let allPass = true;
  for (const b of BUILDINGS) {
    const idx = RealGeometry.buildGeometryIndex(metaDbs[b], sharedMesh);
    const guidCount = Object.keys(idx.byGuid).length;
    const nonNullHashes = Object.values(idx.byGuid).filter(h => h != null);
    const distinctNeeded = new Set(nonNullHashes).size;
    const resolvedCount = Object.keys(idx.resolved).length;
    const unresolved = [...new Set(nonNullHashes)].filter(h => !idx.resolved[h]);
    const pass = resolvedCount === distinctNeeded && unresolved.length === 0 && resolvedCount > 0;
    allPass = allPass && pass;
    console.log((pass ? '✅' : '❌') + ' ' + b + ' vs SHARED mesh.db — byGuid=' + guidCount +
      ' distinctNeeded=' + distinctNeeded + ' resolved=' + resolvedCount + ' unresolved=' + unresolved.length);
  }

  console.log(allPass ? '✅ SANDBOX-SHARED-MESH-PROOF PASS — both buildings resolve 1:1 against ONE shared mesh.db'
                       : '❌ SANDBOX-SHARED-MESH-PROOF FAIL');
  Object.values(metaDbs).forEach(d => d.close());
  sharedMesh.close();
  process.exit(allPass ? 0 : 1);
})().catch(e => { console.error('§SANDBOX FATAL', e); process.exit(1); });
