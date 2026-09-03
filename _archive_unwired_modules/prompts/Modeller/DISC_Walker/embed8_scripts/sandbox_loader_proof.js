// sandbox_loader_proof.js — proves the REAL, unmodified real_geometry.js buildGeometryIndex(db, geoDb)
// correctly resolves a meta-db / shared-mesh-db split shape, BEFORE wiring any real resident.
// Reads real_geometry.js verbatim (no copy, no edit) from ~/bim-ootb/modeller/ — read-only require.
'use strict';
const fs = require('fs');
const path = require('path');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const RealGeometry = require('/home/red1/bim-ootb/modeller/real_geometry.js');

const SRC = process.argv[2] || path.join(__dirname, 'SampleHouse_all.db');

(async () => {
  const SQL = await initSqlJs();
  const allBuf = fs.readFileSync(SRC);
  const allDb = new SQL.Database(new Uint8Array(allBuf));

  // Split in-memory: metaDb = everything except component_geometries; meshDb = only component_geometries.
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

  const meshDb = new SQL.Database();
  const geoSql = allDb.exec("SELECT sql FROM sqlite_master WHERE type='table' AND name='component_geometries'")[0].values[0][0];
  meshDb.run(geoSql);
  const geoRows = allDb.exec('SELECT * FROM component_geometries')[0];
  const gStmt = meshDb.prepare('INSERT INTO component_geometries VALUES (?,?,?,?)');
  geoRows.values.forEach(v => gStmt.run(v));
  gStmt.free();

  console.log('§SANDBOX-SPLIT source=' + path.basename(SRC) + ' metaTables=' + tables.values.length + ' meshRows=' + geoRows.values.length);

  // ── Prove the REAL loader against the split shape ──
  const idx = RealGeometry.buildGeometryIndex(metaDb, meshDb);
  const guidCount = Object.keys(idx.byGuid).length;
  const resolvedCount = Object.keys(idx.resolved).length;
  const nullHashGuids = Object.values(idx.byGuid).filter(h => h == null).length;
  const nonNullHashes = Object.values(idx.byGuid).filter(h => h != null);
  const distinctNeeded = new Set(nonNullHashes).size;
  const unresolved = [...new Set(nonNullHashes)].filter(h => !idx.resolved[h]);

  console.log('§SANDBOX-LOADER table=' + idx.table + ' byGuid=' + guidCount + ' (nullHash=' + nullHashGuids + ')' +
    ' distinctHashesNeeded=' + distinctNeeded + ' resolved=' + resolvedCount + ' unresolved=' + unresolved.length);

  const pass = idx.table === 'component_geometries' && resolvedCount > 0 && unresolved.length === 0 &&
    resolvedCount === distinctNeeded;
  console.log(pass ? '✅ SANDBOX-LOADER-PROOF PASS — meta/mesh split resolves 1:1 via unmodified real_geometry.js'
                    : '❌ SANDBOX-LOADER-PROOF FAIL — unresolved=' + JSON.stringify(unresolved.slice(0, 5)));

  allDb.close(); metaDb.close(); meshDb.close();
  process.exit(pass ? 0 : 1);
})().catch(e => { console.error('§SANDBOX FATAL', e); process.exit(1); });
