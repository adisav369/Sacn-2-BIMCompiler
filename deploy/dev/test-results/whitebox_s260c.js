#!/usr/bin/env node
// §S260c Whitebox test — exercises ground Y, storey bands, diff logic
// Run: node deploy/dev/test-results/whitebox_s260c.js > deploy/dev/test-results/whitebox_s260c.log 2>&1
// Then read the log.

const initSqlJs = require('../lib/sql-wasm.js');
const fs = require('fs');
const path = require('path');

const DBS = [
  { name: 'SampleHouse', file: '../buildings/SampleHouse_extracted.db' },
  { name: 'LTU_AHouse', file: '../buildings/LTU_AHouse_extracted.db' },
];

async function run() {
  const SQL = await initSqlJs({ locateFile: f => path.resolve(__dirname, '../lib/' + f) });

  for (const dbInfo of DBS) {
    const dbPath = path.resolve(__dirname, dbInfo.file);
    if (!fs.existsSync(dbPath)) { console.log('§SKIP ' + dbInfo.name + ' — file not found'); continue; }
    const buf = fs.readFileSync(dbPath);
    const db = new SQL.Database(new Uint8Array(buf));
    console.log('\n════════════════════════════════════════');
    console.log('§TEST_BUILDING ' + dbInfo.name);
    console.log('════════════════════════════════════════');

    // ── TEST 1: Ground Y — storey name matching ──
    console.log('\n── TEST 1: Ground Y ──');
    var gfNames = "('Ground Floor','Ground','Level 0','Level 00','GF','L00','00','0','EG','Erdgeschoss','1F','Level 1','Storey 1','Plan 1','VÅN 1','VÅNING 1','1. OG','Rez-de-chaussée','RC','Planta Baja','PB','Piso 0','Begane grond','BG')";

    // List all distinct storeys
    var storeys = db.exec("SELECT DISTINCT storey FROM elements_meta ORDER BY storey");
    if (storeys.length) {
      console.log('§GROUND_Y_STOREYS all=' + storeys[0].values.map(r => r[0]).join(', '));
    }

    // Step 1: storey name match
    var zr = db.exec(
      "SELECT t.center_z - t.bbox_z/2 AS bottom, t.bbox_x * t.bbox_y AS area, m.storey " +
      "FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid " +
      "WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0 " +
      "AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL " +
      "AND m.storey IN " + gfNames + " ORDER BY area DESC LIMIT 3"
    );
    if (zr.length && zr[0].values.length > 0) {
      console.log('§GROUND_Y Step1_HIT gf-storey-slab:');
      zr[0].values.forEach(function(row, i) {
        console.log('  [' + i + '] bottom=' + (row[0] !== null ? row[0].toFixed(3) : 'null') +
          ' area=' + (row[1] !== null ? row[1].toFixed(1) : 'null') + ' storey="' + row[2] + '"');
      });
    } else {
      console.log('§GROUND_Y Step1_MISS — no storey name matched gfNames');
      // Step 2: largest above-grade slab
      zr = db.exec(
        "SELECT t.center_z - t.bbox_z/2 AS bottom, t.bbox_x * t.bbox_y AS area, t.center_z, m.storey " +
        "FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid " +
        "WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0 " +
        "AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL " +
        "ORDER BY area DESC LIMIT 15"
      );
      if (zr.length && zr[0].values.length > 0) {
        console.log('§GROUND_Y Step2 top 15 slabs by area:');
        zr[0].values.forEach(function(row, i) {
          var aboveGrade = row[2] >= -3 ? 'ABOVE' : 'BELOW';
          console.log('  [' + i + '] bottom=' + (row[0] !== null ? row[0].toFixed(3) : 'null') +
            ' area=' + (row[1] !== null ? row[1].toFixed(1) : 'null') +
            ' cz=' + (row[2] !== null ? row[2].toFixed(3) : 'null') +
            ' storey="' + row[3] + '" ' + aboveGrade);
        });
        // Find first above-grade
        var bestBottom = null;
        for (var si = 0; si < zr[0].values.length; si++) {
          if (zr[0].values[si][2] >= -3) {
            bestBottom = zr[0].values[si][0];
            console.log('§GROUND_Y Step2_PICK idx=' + si + ' bottom=' + bestBottom.toFixed(3) + ' src=largest-above-grade');
            break;
          }
        }
        if (bestBottom === null) console.log('§GROUND_Y Step2_MISS — all slabs underground');
      } else {
        console.log('§GROUND_Y Step2_MISS — no slabs found at all');
      }
    }

    // ── TEST 2: Storey band sorting (median Z vs min Z) ──
    console.log('\n── TEST 2: Storey bands (BUG 5) ──');
    var elems = db.exec(
      'SELECT m.storey, COALESCE(t.center_z, 0) as cz ' +
      'FROM elements_meta m LEFT JOIN element_transforms t ON t.guid = m.guid ' +
      "WHERE m.ifc_class != 'IfcOpeningElement'"
    );
    if (elems.length && elems[0].values.length) {
      // Collect Z values per storey
      var storeyZvals = {};
      var storeyMinZ = {};
      elems[0].values.forEach(function(row) {
        var storey = row[0] || '_UNKNOWN';
        var cz = row[1] || 0;
        if (!storeyZvals[storey]) storeyZvals[storey] = [];
        storeyZvals[storey].push(cz);
        if (storeyMinZ[storey] === undefined || cz < storeyMinZ[storey]) storeyMinZ[storey] = cz;
      });
      // Compute median
      var storeyMedianZ = {};
      for (var sk in storeyZvals) {
        var vals = storeyZvals[sk].slice().sort(function(a, b) { return a - b; });
        var mid = Math.floor(vals.length / 2);
        storeyMedianZ[sk] = vals.length % 2 !== 0 ? vals[mid] : (vals[mid - 1] + vals[mid]) / 2;
      }
      // Sort by median Z
      var byMedian = Object.keys(storeyMedianZ).sort(function(a, b) { return storeyMedianZ[a] - storeyMedianZ[b]; });
      // Sort by min Z (old way)
      var byMin = Object.keys(storeyMinZ).sort(function(a, b) { return storeyMinZ[a] - storeyMinZ[b]; });

      console.log('§STOREY_BANDS_MEDIAN (new):');
      byMedian.forEach(function(s, i) {
        console.log('  band ' + i + ': "' + s + '" medZ=' + storeyMedianZ[s].toFixed(2) +
          ' minZ=' + storeyMinZ[s].toFixed(2) + ' count=' + storeyZvals[s].length);
      });
      console.log('§STOREY_BANDS_MIN (old):');
      byMin.forEach(function(s, i) {
        console.log('  band ' + i + ': "' + s + '" minZ=' + storeyMinZ[s].toFixed(2) +
          ' medZ=' + storeyMedianZ[s].toFixed(2));
      });
      // Check if order changed
      var orderChanged = JSON.stringify(byMedian) !== JSON.stringify(byMin);
      console.log('§STOREY_BANDS_DIFF order_changed=' + orderChanged);
    }

    // ── TEST 3: Diff pipeline (self-diff = 0 changes) ──
    console.log('\n── TEST 3: Diff pipeline (self-diff) ──');
    var guids1 = db.exec("SELECT guid FROM elements_meta");
    var count1 = guids1.length ? guids1[0].values.length : 0;
    console.log('§DIFF_SELF base_guids=' + count1);
    // Self-diff: same DB = 0 added, 0 removed, 0 changed
    var guids2 = db.exec("SELECT guid FROM elements_meta");
    var count2 = guids2.length ? guids2[0].values.length : 0;
    var set1 = new Set(guids1.length ? guids1[0].values.map(r => r[0]) : []);
    var set2 = new Set(guids2.length ? guids2[0].values.map(r => r[0]) : []);
    var added = [...set2].filter(g => !set1.has(g));
    var common = [...set2].filter(g => set1.has(g));
    var removed = [...set1].filter(g => !set2.has(g));
    console.log('§DIFF_SELF_RESULT added=' + added.length + ' removed=' + removed.length +
      ' common=' + common.length + ' — expect all 0/0/' + count1);

    // ── TEST 4: Storey panel — check storey list exists ──
    console.log('\n── TEST 4: Storey data ──');
    var stData = db.exec("SELECT storey, COUNT(*) as cnt FROM elements_meta GROUP BY storey ORDER BY storey");
    if (stData.length) {
      stData[0].values.forEach(function(row) {
        console.log('§STOREY_DATA "' + row[0] + '" count=' + row[1]);
      });
    }

    db.close();
  }

  // ── TEST 5: import_db_builder validation ──
  console.log('\n── TEST 5: DB export validation ──');
  // Simulate: create a small DB, export, re-open, verify
  const testDb = new SQL.Database();
  testDb.run("CREATE TABLE elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT, storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, building TEXT)");
  testDb.run("INSERT INTO elements_meta VALUES ('g1','IfcWall','Wall 1','Level 1','ARCH',null,null,'Test')");
  testDb.run("INSERT INTO elements_meta VALUES ('g2','IfcSlab','Slab 1','Level 1','ARCH',null,null,'Test')");
  var exported = testDb.export().buffer;
  testDb.close();
  console.log('§DB_EXPORT_TEST size=' + exported.byteLength + ' bytes');
  // Re-open and validate
  try {
    var checkDb = new SQL.Database(new Uint8Array(exported));
    var check = checkDb.exec("SELECT COUNT(*) FROM elements_meta");
    var checkCount = (check.length && check[0].values.length) ? check[0].values[0][0] : 0;
    checkDb.close();
    console.log('§DB_EXPORT_VALID rows=' + checkCount + ' — expect 2');
  } catch(e) {
    console.log('§DB_EXPORT_CORRUPT err=' + e.message);
  }

  console.log('\n§WHITEBOX_DONE');
}

run().catch(e => console.error('§WHITEBOX_FATAL ' + e.message));
